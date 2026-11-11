package app.nimarkogram.messenger.nfc;

import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Linki-Share payload: the Telegram user id plus a display name.
 *
 * The payload is a URI that any reader can parse without our code, for example
 * <pre>linkigram://linki-share?id=123456789&amp;n=Alex</pre>
 */
public final class LinkiSharePayload {

    public static final String SCHEME = "linkigram";
    public static final String HOST = "linki-share";

    public final long userId;
    public final String name;
    public final String username;

    public LinkiSharePayload(long userId, String name) {
        this(userId, name, null);
    }

    public LinkiSharePayload(long userId, String name, String username) {
        this.userId = userId;
        this.name = name == null ? "" : name;
        this.username = username == null ? "" : username;
    }

    public String toUri() {
        final StringBuilder out = new StringBuilder(SCHEME).append("://").append(HOST)
                .append("?id=").append(userId);
        if (!name.isEmpty()) {
            out.append("&n=").append(encode(name));
        }
        if (!username.isEmpty()) {
            out.append("&u=").append(encode(username));
        }
        return out.toString();
    }

    public static LinkiSharePayload fromUri(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        final Uri uri;
        try {
            uri = Uri.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
        if (uri == null || !SCHEME.equals(uri.getScheme()) || !HOST.equals(uri.getHost())) {
            return null;
        }
        final String id = uri.getQueryParameter("id");
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        final long userId;
        try {
            userId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
        if (userId <= 0) {
            return null;
        }
        return new LinkiSharePayload(userId, decode(uri.getQueryParameter("n")), decode(uri.getQueryParameter("u")));
    }

    /** The payload of the current account, or null when no account is authorized. */
    public static LinkiSharePayload current(int account) {
        final UserConfig config = UserConfig.getInstance(account);
        if (config == null || !config.isClientActivated()) {
            return null;
        }
        final long selfId = config.getClientUserId();
        if (selfId == 0) {
            return null;
        }
        String name = "";
        String username = "";
        final TLRPC.User self = config.getCurrentUser();
        if (self != null) {
            name = self.first_name == null ? "" : self.first_name;
            if (self.last_name != null) {
                name = name.isEmpty() ? self.last_name : name + " " + self.last_name;
            }
            if (name.isEmpty() && self.username != null) {
                name = self.username;
            }
            if (self.username != null) {
                username = self.username;
            }
        }
        return new LinkiSharePayload(selfId, name, username);
    }

    private static String encode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static String decode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}