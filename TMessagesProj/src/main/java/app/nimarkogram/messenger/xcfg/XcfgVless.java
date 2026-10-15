package app.nimarkogram.messenger.xcfg;

/*
 * Разбор vless:// и сборка конфига ядра Xray для Telegram.
 * Порт _parse_vless / _vless_outbound / _build_tg_config из плагина X-CFG
 * (автор @x_vare, GPL).
 */
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class XcfgVless {

    public static class Parsed {
        public String uuid;
        public String address;
        public int port = 443;
        public String encryption;
        public String flow;
        public String network;
        public String security;
        public String sni;
        public String fp;
        public String pbk;
        public String sid;
        public String spx;
        public String path;
        public String host;
        public String alpn;
        public String mode;
        public String extra;
        public String serviceName;
        public String remark;
    }

    private XcfgVless() {
    }

    /** vless://uuid@host:port?…#remark → Parsed (как XrayConfigBuilder.parseVless в X-CFG). */
    public static Parsed parse(String link) {
        try {
            String s = link.trim();
            if (!s.toLowerCase().startsWith("vless://")) {
                return null;
            }
            s = s.substring(8);
            int at = s.lastIndexOf('@');
            if (at <= 0) {
                return null;
            }
            String uuid = URLDecoder.decode(s.substring(0, at), "UTF-8");
            String rest = s.substring(at + 1);
            String remark = "";
            int hash = rest.indexOf('#');
            if (hash >= 0) {
                remark = URLDecoder.decode(rest.substring(hash + 1), "UTF-8");
                rest = rest.substring(0, hash);
            }
            Map<String, String> q = new HashMap<>();
            int qm = rest.indexOf('?');
            if (qm >= 0) {
                String qs = rest.substring(qm + 1);
                rest = rest.substring(0, qm);
                for (String kv : qs.split("&")) {
                    int eq = kv.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String v = URLDecoder.decode(kv.substring(eq + 1), "UTF-8");
                    if (!v.isEmpty()) {
                        q.put(kv.substring(0, eq), v);
                    }
                }
            }
            String address;
            int port = 443;
            if (rest.startsWith("[")) {
                int cb = rest.indexOf(']');
                if (cb < 0) {
                    return null;
                }
                address = rest.substring(1, cb);
                if (cb + 1 < rest.length() && rest.charAt(cb + 1) == ':') {
                    port = Integer.parseInt(rest.substring(cb + 2));
                }
            } else {
                int colon = rest.lastIndexOf(':');
                if (colon >= 0) {
                    address = rest.substring(0, colon);
                    port = Integer.parseInt(rest.substring(colon + 1));
                } else {
                    address = rest;
                }
            }
            if (address == null || address.isEmpty() || uuid == null || uuid.isEmpty()) {
                return null;
            }
            Parsed p = new Parsed();
            p.uuid = uuid;
            p.address = address;
            p.port = port;
            p.encryption = q.containsKey("encryption") ? q.get("encryption") : "none";
            p.flow = q.get("flow");
            p.network = q.containsKey("type") ? q.get("type") : "tcp";
            p.security = q.containsKey("security") ? q.get("security") : "none";
            p.sni = q.get("sni");
            p.fp = q.get("fp");
            p.pbk = q.get("pbk");
            p.sid = q.get("sid");
            p.spx = q.get("spx");
            p.path = q.get("path");
            p.host = q.get("host");
            p.alpn = q.get("alpn");
            p.mode = q.get("mode");
            p.extra = q.get("extra");
            p.serviceName = q.get("serviceName");
            p.remark = remark;
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private static String nz(String s, String def) {
        return s == null ? def : s;
    }

    public static JSONObject outbound(String tag, Parsed p) throws JSONException {
        String net = nz(p.network, "tcp").toLowerCase();
        if (net.equals("splithttp")) {
            net = "xhttp";
        }
        JSONObject stream = new JSONObject();
        stream.put("network", net);
        if ("reality".equals(p.security)) {
            JSONObject reality = new JSONObject();
            reality.put("serverName", nz(p.sni, p.address));
            reality.put("fingerprint", nz(p.fp, "chrome"));
            reality.put("publicKey", nz(p.pbk, ""));
            reality.put("shortId", nz(p.sid, ""));
            reality.put("spiderX", nz(p.spx, ""));
            stream.put("security", "reality");
            stream.put("realitySettings", reality);
        } else if ("tls".equals(p.security)) {
            JSONObject tls = new JSONObject();
            tls.put("serverName", p.sni != null ? p.sni : (p.host != null ? p.host : p.address));
            tls.put("fingerprint", nz(p.fp, "chrome"));
            if (p.alpn != null && !p.alpn.isEmpty()) {
                JSONArray alpn = new JSONArray();
                for (String a : p.alpn.split(",")) {
                    if (a.trim().length() > 0) {
                        alpn.put(a.trim());
                    }
                }
                tls.put("alpn", alpn);
            }
            stream.put("security", "tls");
            stream.put("tlsSettings", tls);
        } else {
            stream.put("security", "none");
        }
        if (net.equals("ws")) {
            JSONObject ws = new JSONObject();
            ws.put("path", nz(p.path, "/"));
            if (p.host != null && !p.host.isEmpty()) {
                ws.put("host", p.host);
            }
            stream.put("wsSettings", ws);
        } else if (net.equals("grpc")) {
            JSONObject grpc = new JSONObject();
            grpc.put("serviceName", p.serviceName != null ? p.serviceName : nz(p.path, ""));
            stream.put("grpcSettings", grpc);
        } else if (net.equals("httpupgrade")) {
            JSONObject hu = new JSONObject();
            hu.put("path", nz(p.path, "/"));
            if (p.host != null && !p.host.isEmpty()) {
                hu.put("host", p.host);
            }
            stream.put("httpupgradeSettings", hu);
        } else if (net.equals("xhttp")) {
            JSONObject xs = new JSONObject();
            xs.put("path", nz(p.path, "/"));
            xs.put("mode", nz(p.mode, "auto"));
            if (p.host != null && !p.host.isEmpty()) {
                xs.put("host", p.host);
            }
            if (p.extra != null && !p.extra.isEmpty()) {
                try {
                    xs.put("extra", new JSONObject(p.extra));
                } catch (Exception ignore) {
                }
            }
            stream.put("xhttpSettings", xs);
        }
        JSONObject sockopt = new JSONObject();
        sockopt.put("tcpNoDelay", true);
        sockopt.put("domainStrategy", "UseIPv4");
        stream.put("sockopt", sockopt);
        JSONObject user = new JSONObject();
        user.put("id", p.uuid);
        user.put("encryption", nz(p.encryption, "none"));
        if (p.flow != null && !p.flow.isEmpty()) {
            user.put("flow", p.flow);
        }
        JSONObject vnext = new JSONObject();
        vnext.put("address", p.address);
        vnext.put("port", p.port);
        vnext.put("users", new JSONArray().put(user));
        return new JSONObject()
                .put("tag", tag)
                .put("protocol", "vless")
                .put("settings", new JSONObject().put("vnext", new JSONArray().put(vnext)))
                .put("streamSettings", stream);
    }

    /** Конфиг ядра для Telegram: SOCKS5 127.0.0.1 с паролем → серверы подписки.
     *  selected = -1 — авто (ядро меряет и ведёт через самый быстрый живой);
     *  selected >= 0 — только выбранный сервер (индекс в списке links). */
    public static String buildConfig(List<String> links, int port, String user, String password, int selected) throws JSONException {
        List<Parsed> nodes = new ArrayList<>();
        for (String l : links) {
            Parsed p = parse(l);
            if (p != null) {
                nodes.add(p);
            }
        }
        if (selected >= nodes.size()) {
            selected = -1;
        }
        JSONArray outbounds = new JSONArray();
        for (int i = 0; i < nodes.size(); i++) {
            outbounds.put(outbound("p" + i, nodes.get(i)));
        }
        outbounds.put(new JSONObject()
                .put("tag", "direct")
                .put("protocol", "freedom")
                .put("settings", new JSONObject().put("domainStrategy", "UseIPv4")));
        JSONObject account = new JSONObject();
        account.put("user", user);
        account.put("pass", password);
        JSONObject socks = new JSONObject();
        socks.put("auth", "password");
        socks.put("accounts", new JSONArray().put(account));
        socks.put("udp", true);
        socks.put("ip", "127.0.0.1");
        JSONObject inbound = new JSONObject();
        inbound.put("tag", "tg-in");
        inbound.put("listen", "127.0.0.1");
        inbound.put("port", port);
        inbound.put("protocol", "socks");
        inbound.put("settings", socks);
        JSONArray rules = new JSONArray();
        rules.put(new JSONObject()
                .put("type", "field")
                .put("ip", new JSONArray().put("127.0.0.0/8"))
                .put("outboundTag", "direct"));
        JSONObject routing = new JSONObject();
        routing.put("domainStrategy", "AsIs");
        routing.put("rules", rules);
        JSONObject cfg = new JSONObject();
        cfg.put("log", new JSONObject().put("loglevel", "warning"));
        cfg.put("inbounds", new JSONArray().put(inbound));
        cfg.put("outbounds", outbounds);
        cfg.put("routing", routing);
        if (selected >= 0) {
            // выбранный сервер: без балансера, трафик только в него
            rules.put(new JSONObject()
                    .put("type", "field")
                    .put("network", "tcp,udp")
                    .put("outboundTag", "p" + selected));
        } else if (nodes.size() > 1) {
            // несколько серверов — ядро само меряет их раз в минуту и ведёт трафик через самый быстрый живой
            cfg.put("observatory", new JSONObject()
                    .put("subjectSelector", new JSONArray().put("p"))
                    .put("probeUrl", "https://www.gstatic.com/generate_204")
                    .put("probeInterval", "1m")
                    .put("enableConcurrency", true));
            routing.put("balancers", new JSONArray().put(new JSONObject()
                    .put("tag", "best")
                    .put("selector", new JSONArray().put("p"))
                    .put("strategy", new JSONObject().put("type", "leastPing"))
                    .put("fallbackTag", "p0")));
            rules.put(new JSONObject()
                    .put("type", "field")
                    .put("network", "tcp,udp")
                    .put("balancerTag", "best"));
        } else {
            rules.put(new JSONObject()
                    .put("type", "field")
                    .put("network", "tcp,udp")
                    .put("outboundTag", "p0"));
        }
        return cfg.toString();
    }
}
