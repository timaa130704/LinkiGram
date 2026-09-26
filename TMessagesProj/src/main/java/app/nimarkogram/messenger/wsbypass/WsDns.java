package app.nimarkogram.messenger.wsbypass;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * DNS resolution for the relay auth requests, independent of the system resolver.
 *
 * The relay WebSocket handshake resolves through RawWebSocket, which has a cache
 * and a dedicated pool, because the platform resolver was returning "resolver
 * saturated" under the app's connection load. The auth requests did not go
 * through that path -- they use HttpURLConnection, so they resolve on whatever
 * thread calls them, competing with everything else.
 *
 * That is what killed the credential on the device. The name is fine: ping
 * resolves calls.nimarko.org and curl gets a 200 from the same handset. Only
 * the in-app lookup fails, with "No address associated with hostname", i.e.
 * the resolver answered with zero addresses rather than an error. Under load
 * the platform resolver does that, and the auth call then gave up and the
 * client ran with no X-Cred for the whole session.
 *
 * So this asks a resolver directly. The system resolver is still tried first
 * because it is usually right and respects the user's split-DNS setup; the
 * direct query is the fallback that does not depend on it. A successful
 * lookup from either path also primes the platform cache, so the
 * HttpURLConnection that follows resolves from memory.
 */
public final class WsDns {

    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            1, 2, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "wsbypass-dns");
                t.setDaemon(true);
                return t;
            });

    /** Direct-query servers, used when the platform resolver comes back empty. */
    private static final String[] FALLBACK_SERVERS = {
            "8.8.8.8", "8.8.4.4", "1.1.1.1", "9.9.9.9",
    };

    private static final int ATTEMPTS = 6;
    private static final long BACKOFF_MS = 200L;
    private static final int DNS_PORT = 53;
    private static final int DNS_TIMEOUT_MS = 2500;

    private WsDns() {
    }

    /**
     * Resolve {@code host}, retrying while the resolver answers with nothing.
     *
     * @return true if the name resolved at least once
     */
    public static boolean warm(String host) {
        if (host == null || host.isEmpty()) return false;
        InetAddress literal = literal(host);
        if (literal != null) return true;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            if (resolveOnce(host)) {
                WsBypassCore.logAlways("dns: resolved " + host + " on attempt "
                        + (attempt + 1) + " via " + lastPath);
                return true;
            }
            try {
                Thread.sleep(BACKOFF_MS * (attempt + 1));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        WsBypassCore.logAlways("dns: giving up on " + host + " after "
                + ATTEMPTS + " attempts");
        return false;
    }

    private static volatile String lastPath = "?";

    private static boolean resolveOnce(String host) {
        try {
            InetAddress[] addresses = EXECUTOR
                    .submit(() -> InetAddress.getAllByName(host))
                    .get(10, TimeUnit.SECONDS);
            if (addresses != null && addresses.length > 0) {
                lastPath = "platform";
                return true;
            }
            lastPath = "platform-empty";
        } catch (Throwable ignored) {
            lastPath = "platform-failed";
        }
        boolean direct = queryDirect(host);
        if (direct) lastPath = "direct";
        return direct;
    }

    private static InetAddress literal(String host) {
        try {
            if (host.indexOf(':') >= 0) {
                return InetAddress.getByName(host);
            }
            // Reject anything that is not four decimal octets, so a failed
            // getByName here cannot turn into a second DNS query.
            String[] parts = host.split("\\.");
            if (parts.length != 4) return null;
            int value = 0;
            for (String part : parts) {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) return null;
                value = (value << 8) | octet;
            }
            byte[] raw = new byte[] {
                    (byte) (value >>> 24), (byte) (value >>> 16),
                    (byte) (value >>> 8), (byte) value,
            };
            return InetAddress.getByAddress(raw);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Minimal A/AAAA query. Deliberately not built on the platform resolver,
     * since that is the thing being worked around.
     */
    private static boolean queryDirect(String host) {
        for (String server : FALLBACK_SERVERS) {
            try {
                for (InetAddress address : ask(server, host, false)) {
                    // Prime the platform cache so the caller's own lookup hits
                    // memory instead of asking again.
                    InetAddress.getByAddress(address.getAddress());
                    return true;
                }
                for (InetAddress address : ask(server, host, true)) {
                    InetAddress.getByAddress(address.getAddress());
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static List<InetAddress> ask(String server, String host, boolean ipv6)
            throws Exception {
        int id = 0x4E4D & 0xFFFF;
        byte[] qname = encodeName(host);
        int qtype = ipv6 ? 28 : 1;

        byte[] query = new byte[12 + qname.length + 4];
        query[0] = (byte) (id >>> 8);
        query[1] = (byte) id;
        query[2] = 0x01;                       // recursion desired
        query[5] = 0x01;                       // one question
        System.arraycopy(qname, 0, query, 12, qname.length);
        int at = 12 + qname.length;
        query[at] = (byte) (qtype >>> 8);
        query[at + 1] = (byte) qtype;
        query[at + 2] = 0x00;
        query[at + 3] = 0x01;                  // class IN

        DatagramSocket socket = new DatagramSocket();
        try {
            socket.setSoTimeout(DNS_TIMEOUT_MS);
            socket.send(new DatagramPacket(query, query.length,
                    InetAddress.getByName(server), DNS_PORT));
            byte[] buffer = new byte[1500];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            return parseAnswer(buffer, reply.getLength(), id, ipv6);
        } finally {
            socket.close();
        }
    }

    private static byte[] encodeName(String host) {
        List<byte[]> labels = new ArrayList<>();
        for (String label : host.split("\\.")) {
            byte[] raw = label.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            if (raw.length == 0 || raw.length > 63) {
                return new byte[] { 0 };
            }
            labels.add(raw);
        }
        byte[] out = new byte[labels.size() * 2 + 1];
        int at = 0;
        for (byte[] label : labels) {
            out[at++] = (byte) label.length;
            System.arraycopy(label, 0, out, at, label.length);
            at += label.length;
        }
        out[at] = 0;
        return out;
    }

    private static List<InetAddress> parseAnswer(byte[] buf, int length, int id, boolean ipv6)
            throws Exception {
        List<InetAddress> out = new ArrayList<>();
        if (length < 12) return out;
        if (((buf[0] & 0xFF) << 8 | (buf[1] & 0xFF)) != id) return out;
        if ((buf[3] & 0x0F) != 0) return out;            // rcode != NOERROR

        int questions = buf[4] & 0xFF;
        int answers = buf[6] & 0xFF;
        int at = 12;
        for (int i = 0; i < questions; i++) {
            at = skipName(buf, at);
            at += 4;
        }
        for (int i = 0; i < answers && at + 10 <= length; i++) {
            at = skipName(buf, at);
            int type = buf[at] & 0xFF | (buf[at + 1] & 0xFF) << 8;
            int rdlen = buf[at + 8] & 0xFF | (buf[at + 9] & 0xFF) << 8;
            at += 10;
            if (at + rdlen > length) break;
            if (type == (ipv6 ? 28 : 1) && rdlen == (ipv6 ? 16 : 4)) {
                out.add(InetAddress.getByAddress(java.util.Arrays.copyOfRange(buf, at, at + rdlen)));
            }
            at += rdlen;
        }
        return out;
    }

    private static int skipName(byte[] buf, int at) {
        while (at < buf.length) {
            int len = buf[at] & 0xFF;
            if (len == 0) return at + 1;
            if ((len & 0xC0) == 0xC0) return at + 2;
            at += len + 1;
        }
        return at;
    }
}
