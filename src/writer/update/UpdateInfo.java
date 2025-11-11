package writer.update;

/** POJO pour les infos du JSON */
public final class UpdateInfo {
    public String channel;
    public String version;
    public String url;
    public String sha256;
    public long   size;
    public String releaseNotes;

    @Override public String toString() {
        return "UpdateInfo{channel=" + channel + ", version=" + version + ", url=" + url + "}";
    }
}

/** Utils de comparaison sémantique 1.2.3 vs 1.10.0 (numérique, sans pré-release) */
final class VersionUtils {
    private VersionUtils(){}

    /** renvoie <0 si a<b ; 0 si égal ; >0 si a>b */
    public static int compare(String a, String b) {
        String[] pa = normalize(a).split("\\.");
        String[] pb = normalize(b).split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int ai = i < pa.length ? parseInt(pa[i]) : 0;
            int bi = i < pb.length ? parseInt(pb[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static String normalize(String v) {
        return (v == null) ? "0" : v.trim().replaceFirst("^[vV]", "");
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
