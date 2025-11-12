package writer.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;

public final class AutoUpdater {

    public static final class UpdateInfo {
        public final String version;
        public final String notes;       // map releaseNotes|notes
        public final String downloadUrl; // map url|downloadUrl
        public final String channel;     // ex: stable
        public final String sha256;      // checksum (optionnel)
        public final long   size;        // en octets (optionnel)

        public UpdateInfo(String version, String notes, String downloadUrl,
                          String channel, String sha256, long size) {
            this.version = version;
            this.notes = notes;
            this.downloadUrl = downloadUrl;
            this.channel = channel;
            this.sha256 = sha256;
            this.size = size;
        }
    }

    private final String metadataUrl;
    private final String currentVersion;

    public AutoUpdater(String metadataUrl, String currentVersion) {
        this.metadataUrl = metadataUrl;
        this.currentVersion = currentVersion;
    }

    public String getCurrentVersion() { return currentVersion; }

    public UpdateInfo fetchMetadata() throws Exception {
        String json = Jsoup.connect(metadataUrl)
                .ignoreContentType(true)
                .userAgent("LisioWriter/AutoUpdater")
                .timeout(10000)
                .get()
                .body()
                .text();

        // Champs tolérants aux variantes de clé
        String version  = extract(json, "\"version\"\\s*:\\s*\"([^\"]+)\"");
        String notes = extract(json, "\"releaseNotes\"\\s*:\\s*\"([^\"]*)\"");
        String url = extract(json, "\"url\"\\s*:\\s*\"([^\"]+)\"");
        String channel  = extract(json, "\"channel\"\\s*:\\s*\"([^\"]+)\"");
        String sha256   = extract(json, "\"sha256\"\\s*:\\s*\"([^\"]+)\"");
        Long   size     = extractLong(json, "\"size\"\\s*:\\s*(\\d+)");

        if (version == null && url == null && notes == null) return null;

        return new UpdateInfo(
            nn(version, ""),
            nn(unescape(notes), ""),
            nn(url, ""),
            nn(channel, ""),
            nn(sha256, ""),
            size == null ? -1L : size
        );
    }

    public static int versionCompare(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String[] a = v1.split("\\.");
        String[] b = v2.split("\\.");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = (i < a.length) ? parseIntSafe(a[i]) : 0;
            int y = (i < b.length) ? parseIntSafe(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    // ---- helpers ----
    private static String extract(String s, String regex) {
        if (s == null) return null;
        Matcher m = Pattern.compile(regex).matcher(s);
        return m.find() ? m.group(1) : null;
    }
    private static Long extractLong(String s, String regex) {
        String g = extract(s, regex);
        if (g == null) return null;
        try { return Long.parseLong(g); } catch (Exception ignore) { return null; }
    }
    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9-]", "")); }
        catch (Exception ignore) { return 0; }
    }
    private static String nn(String v, String def) { return (v == null) ? def : v; }
    private static String unescape(String s) {
        if (s == null) return null;
        return s.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"");
    }
}