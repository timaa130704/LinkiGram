package app.exteraless.player;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** Musixmatch richsync timing, as exposed by Liner's lyrics API. */
final class WordLyricsParser {
    static Lyrics parseStructured(String raw, int source, String provider) {
        if (raw.length() > 1024 * 1024) return null;
        if (raw.trim().startsWith("<")) return parseXml(raw, source, provider);
        if (raw.matches("(?s)^\\s*\\[\\d+,\\d+].*")) return parseQrc(raw, source, provider);
        if (!raw.trim().matches("(?s)^\\[\\s*\\{.*")) return null;
        try {
            JSONArray rows = new JSONArray(raw);
            ArrayList<Lyrics.Line> lines = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!row.has("ts") || !row.has("l")) {
                    JSONObject subtitleTime = row.optJSONObject("time");
                    if (subtitleTime == null || !subtitleTime.has("total")) return null;
                    lines.add(new Lyrics.Line(Math.max(0, Math.round(subtitleTime.getDouble("total") * 1000)), row.optString("text")));
                    continue;
                }
                long time = Math.round(row.getDouble("ts") * 1000);
                JSONArray tokens = row.getJSONArray("l");
                StringBuilder text = new StringBuilder();
                ArrayList<Lyrics.Word> words = new ArrayList<>();
                for (int j = 0; j < tokens.length(); j++) {
                    JSONObject token = tokens.getJSONObject(j);
                    String value = token.optString("c", "");
                    int start = text.length(); text.append(value);
                    long offset = Math.round(token.optDouble("o", 0) * 1000);
                    if (!value.isEmpty()) words.add(new Lyrics.Word(Math.max(0, offset >= time ? offset : time + offset), start, text.length()));
                }
                lines.add(new Lyrics.Line(Math.max(0, time), text.toString(), words));
            }
            lines.sort((a, b) -> Long.compare(a.time, b.time));
            return lines.isEmpty() ? null : Lyrics.synced(lines, source, provider);
        } catch (Exception ignore) { return null; }
    }
    private static long time(String value) {
        if (value == null || value.isEmpty()) return -1;
        try {
            if (value.endsWith("ms")) return Math.round(Double.parseDouble(value.substring(0, value.length() - 2)));
            if (value.endsWith("s")) return Math.round(Double.parseDouble(value.substring(0, value.length() - 1)) * 1000);
            String[] parts = value.split(":"); double seconds = 0;
            for (String part : parts) seconds = seconds * 60 + Double.parseDouble(part);
            return Math.round(seconds * 1000);
        } catch (Exception ignore) { return -1; }
    }
    private static Lyrics parseXml(String raw, int source, String provider) {
        try {
            org.xmlpull.v1.XmlPullParser xml = android.util.Xml.newPullParser();
            xml.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            xml.setInput(new java.io.StringReader(raw));
            ArrayList<Lyrics.Line> lines = new ArrayList<>();
            StringBuilder text = null;
            ArrayList<Lyrics.Word> words = null;
            long lineTime = -1, wordTime = -1; int wordStart = -1, spanDepth = -1;
            int event;
            while ((event = xml.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.DOCDECL) return null;
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if ("Lyric_1".equals(xml.getName())) {
                        String content = xml.getAttributeValue(null, "LyricContent");
                        if (content != null) return parseQrc(content, source, provider);
                    }
                    if ("p".equals(xml.getName())) {
                        text = new StringBuilder(); words = new ArrayList<>();
                        lineTime = time(xml.getAttributeValue(null, "begin"));
                    } else if (text != null && "span".equals(xml.getName())) {
                        long begin = time(xml.getAttributeValue(null, "begin"));
                        if (begin >= 0 && wordStart < 0) { wordTime = begin; wordStart = text.length(); spanDepth = xml.getDepth(); }
                    } else if (text != null && "br".equals(xml.getName())) text.append('\n');
                } else if (event == org.xmlpull.v1.XmlPullParser.TEXT && text != null) {
                    text.append(xml.getText());
                } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG && text != null) {
                    if ("span".equals(xml.getName()) && wordStart >= 0 && xml.getDepth() == spanDepth) {
                        if (text.length() > wordStart) words.add(new Lyrics.Word(wordTime, wordStart, text.length()));
                        wordStart = -1; spanDepth = -1;
                    } else if ("p".equals(xml.getName())) {
                        if (lineTime < 0 && !words.isEmpty()) lineTime = words.get(0).time;
                        if (lineTime >= 0) lines.add(new Lyrics.Line(lineTime, text.toString(), words));
                        text = null; words = null; wordStart = -1;
                    }
                }
            }
            lines.sort((a, b) -> Long.compare(a.time, b.time));
            return lines.isEmpty() ? null : Lyrics.synced(lines, source, provider);
        } catch (Exception ignore) { return null; }
    }
    private static Lyrics parseQrc(String raw, int source, String provider) {
        try {
            java.util.regex.Pattern rowPattern = java.util.regex.Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
            java.util.regex.Pattern wordPattern = java.util.regex.Pattern.compile("(.*?)\\((\\d+),(\\d+)\\)");
            ArrayList<Lyrics.Line> lines = new ArrayList<>();
            for (String row : raw.split("\\r?\\n")) {
                java.util.regex.Matcher line = rowPattern.matcher(row.trim()); if (!line.matches()) continue;
                long begin = Long.parseLong(line.group(1));
                StringBuilder text = new StringBuilder(); ArrayList<Lyrics.Word> words = new ArrayList<>();
                java.util.regex.Matcher word = wordPattern.matcher(line.group(3));
                int cursor = 0;
                while (word.find()) {
                    int start = text.length(); text.append(word.group(1));
                    words.add(new Lyrics.Word(Long.parseLong(word.group(2)), start, text.length()));
                    cursor = word.end();
                }
                text.append(line.group(3).substring(cursor));
                lines.add(new Lyrics.Line(begin, text.toString(), words));
            }
            lines.sort((a, b) -> Long.compare(a.time, b.time));
            return lines.isEmpty() ? null : Lyrics.synced(lines, source, provider);
        } catch (Exception ignore) { return null; }
    }
    private WordLyricsParser() { }
}
