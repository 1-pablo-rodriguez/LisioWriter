package writer.util;

import java.io.InputStream;
import java.util.Properties;

/**
 * Informations générales sur l'application blindWriter
 * (version, auteur, etc.)
 */
public final class AppInfo {

    private AppInfo() {}

    public static String getAppVersion() {
        String v = null;

        Package p = AppInfo.class.getPackage();
        if (p != null) v = p.getImplementationVersion();
        if (v != null && !v.isBlank()) return v;

        try (InputStream in = AppInfo.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                v = props.getProperty("app.version");
                if (v != null && !v.isBlank()) return v;
            }
        } catch (Exception ignore) {}

        return "dev";
    }
}