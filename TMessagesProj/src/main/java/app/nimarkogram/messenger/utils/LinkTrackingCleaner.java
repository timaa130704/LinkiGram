package app.nimarkogram.messenger.utils;

import android.net.Uri;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes common analytics parameters while preserving the destination URL. */
public final class LinkTrackingCleaner {
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>\\\"']+");
    private LinkTrackingCleaner() {
    }

    public static Uri clean(Uri uri) {
        if (uri == null || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            return uri;
        }
        String query = uri.getEncodedQuery();
        if (query == null || query.isEmpty()) {
            return uri;
        }

        StringBuilder kept = new StringBuilder(query.length());
        boolean changed = false;
        for (String parameter : query.split("&", -1)) {
            String key = parameter;
            int equals = key.indexOf('=');
            if (equals >= 0) {
                key = key.substring(0, equals);
            }
            key = Uri.decode(key).toLowerCase(Locale.ROOT);
            if (isTrackingParameter(key)) {
                changed = true;
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(parameter);
        }
        if (!changed) {
            return uri;
        }
        return uri.buildUpon().clearQuery()
                .encodedQuery(kept.length() == 0 ? null : kept.toString())
                .build();
    }

    public static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer(text.length());
        while (matcher.find()) {
            String candidate = matcher.group();
            int end = candidate.length();
            while (end > 0 && ".,;:!?)]}".indexOf(candidate.charAt(end - 1)) >= 0) {
                end--;
            }
            String url = candidate.substring(0, end);
            String suffix = candidate.substring(end);
            String cleaned = clean(Uri.parse(url)).toString() + suffix;
            matcher.appendReplacement(result, Matcher.quoteReplacement(cleaned));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean isTrackingParameter(String key) {
        return key.startsWith("utm_") || key.startsWith("ga_")
                || key.equals("fbclid") || key.equals("gclid")
                || key.equals("dclid") || key.equals("gbraid")
                || key.equals("wbraid") || key.equals("msclkid")
                || key.equals("twclid") || key.equals("igshid")
                || key.equals("mc_cid") || key.equals("mc_eid")
                || key.equals("_hsenc") || key.equals("_hsmi")
                || key.equals("mkt_tok") || key.equals("yclid")
                || key.equals("vero_id") || key.equals("oly_anon_id")
                || key.equals("oly_enc_id");
    }
}
