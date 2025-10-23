package maj;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

/**
 * AutoUpdater : fetch metadata, download with progress callback,
 * verify sha256 and run installer.
 *
 * Remplace ton fichier existant par celui-ci (même package : maj).
 */
public class AutoUpdater {

    private final String metadataUrl; // ex: https://updates.exemple.com/updates.json
    @SuppressWarnings("unused")
	private final String currentVersion; // ex: "1.0.6"

    public AutoUpdater(String metadataUrl, String currentVersion) {
        this.metadataUrl = metadataUrl;
        // utiliser la version passée en paramètre (fallback vers blindWriter si null)
        this.currentVersion = (currentVersion != null && !currentVersion.isBlank())
                              ? currentVersion
                              : writer.blindWriter.getAppVersion();
    }


    /**
     * Vérifie metadata et renvoie UpdateInfo (public pour pouvoir l'utiliser depuis l'UI).
     */
    public UpdateInfo fetchMetadata() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(metadataUrl))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;

        JsonReader jr = Json.createReader(new StringReader(resp.body()));
        JsonObject o = jr.readObject();
        jr.close();

        UpdateInfo info = new UpdateInfo();
        info.version = o.getString("version");
        info.url = o.getString("url");
        info.sha256 = o.getString("sha256");
        info.size = o.containsKey("size") ? o.getJsonNumber("size").longValue() : -1L;
        info.releaseNotes = o.containsKey("releaseNotes") ? o.getString("releaseNotes") : "";
        return info;
    }

    /**
     * Wrapper rétro-compatible : version sans listener
     */
    public Path downloadWithProgress(String url, long expectedSize) {
        return downloadWithProgress(url, expectedSize, null);
    }

 // shared client (mettre en haut de la classe)
    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15))
            .proxy(ProxySelector.getDefault()) // utilise le proxy système si configuré
            .build();

    public Path downloadWithProgress(String url, long expectedSize, ProgressListener listener) {
        System.out.println("[AutoUpdater] Start download: " + url + " expectedSize=" + expectedSize);
        try {
            // 1) Test HEAD to check connectivity and server response
            try {
                HttpRequest headReq = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<Void> headResp = SHARED_HTTP_CLIENT.send(headReq, HttpResponse.BodyHandlers.discarding());
                System.out.println("[AutoUpdater] HEAD response: " + headResp.statusCode());
                if (headResp.statusCode() >= 400) {
                    if (listener != null) listener.onStatus("Serveur renvoie code " + headResp.statusCode());
                    return null;
                }
            } catch (HttpTimeoutException t) {
                System.err.println("[AutoUpdater] HEAD timeout: " + t.getMessage());
                if (listener != null) listener.onStatus("Timeout lors de la vérification du serveur");
                // continuer quand même vers GET si tu veux, ou return null;
            } catch (Exception ex) {
                System.err.println("[AutoUpdater] HEAD error: " + ex.getClass().getName() + " : " + ex.getMessage());
                if (listener != null) listener.onStatus("Erreur de connexion: " + ex.getMessage());
                return null;
            }

            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<InputStream> resp = SHARED_HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            System.out.println("[AutoUpdater] GET response code: " + resp.statusCode());
            if (resp.statusCode() != 200) {
                if (listener != null) listener.onStatus("Erreur réseau: code " + resp.statusCode());
                return null;
            }

            Path tmp = Files.createTempFile("blindWriter-update-", ".exe");
            System.out.println("[AutoUpdater] temp file: " + tmp.toAbsolutePath());
            try (InputStream in = resp.body();
                 OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buf = new byte[8192];
                long read = 0;
                int r;
                long lastReportPct = -1;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                    read += r;
                    if (listener != null) {
                        listener.onProgress(read, expectedSize);
                        if (expectedSize > 0) {
                            int pct = (int) (read * 100 / expectedSize);
                            if (pct % 5 == 0 && pct != lastReportPct) {
                                listener.onStatus("Téléchargement " + pct + " pourcent");
                                lastReportPct = pct;
                            }
                        }
                    }
                }
            }
            if (listener != null) listener.onStatus("Téléchargement terminé");
            return tmp;
        } catch (Exception e) {
            String err = e.getClass().getName() + ": " + e.getMessage();
            System.err.println("[AutoUpdater] download failed: " + err);
            e.printStackTrace();
            if (listener != null) listener.onStatus("Erreur: " + err);
            return null;
        }
    }
    
    
    /**
     * Vérifie SHA-256 (public pour l'UI).
     */
    public boolean verifySha256(Path file, String expectedHex) {
        try (InputStream fis = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int r;
            while ((r = fis.read(buf)) != -1) md.update(buf, 0, r);
            byte[] digest = md.digest();
            String hex = HexFormat.of().formatHex(digest);
            return hex.equalsIgnoreCase(expectedHex);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

//    /**
//     * Lance l'installateur (public).
//     */
//    public Process runInstaller(Path installer) throws java.io.IOException {
//        String exe = installer.toAbsolutePath().toString();
//        boolean isExe = exe.toLowerCase().endsWith(".exe");
//        boolean isBat = exe.toLowerCase().endsWith(".bat");
//
//        // Arguments Inno (commence par NON-silencieux pour tester visuellement)
//        // Quand tout est OK, repasse à /SILENT ou /VERYSILENT si tu veux.
//        String[] innoArgs = new String[] {
//            //"/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART"
//        };
//
//        ProcessBuilder pb;
//        if (isExe || isBat) {
//            // cmd /c start "" "chemin" args...
//            // start : détache + laisse le shell Windows gérer l’UAC/SmartScreen
//            String[] base = new String[] { "cmd.exe", "/c", "start", "", "\"" + exe + "\"" };
//            String[] cmd = new String[base.length + innoArgs.length];
//            System.arraycopy(base, 0, cmd, 0, base.length);
//            System.arraycopy(innoArgs, 0, cmd, base.length, innoArgs.length);
//
//            pb = new ProcessBuilder(cmd);
//            pb.directory(installer.getParent().toFile()); // bon working dir
//            pb.inheritIO(); // log dans la console si .bat
//            System.out.println("[AutoUpdater] Launching installer via shell: " + String.join(" ", cmd));
//            return pb.start();
//        } else {
//            // Fallback : ouverture par association
//            java.awt.Desktop.getDesktop().open(installer.toFile());
//            return null;
//        }
//    }
    
    public Process runInstaller(Path installer) throws java.io.IOException {
        String exe = installer.toAbsolutePath().toString();

        // détaché + compatible UAC/SmartScreen
        return new ProcessBuilder(
            "cmd.exe", "/c", "start", "", // "" = titre de fenêtre
            "\"" + exe + "\"",
            "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART",
            "/LOG=" + System.getProperty("user.home") + "\\AppData\\Local\\blindWriter\\install.log"
            // Optionnel: forcer le dossier d'install sans UAC si tu installes sous %LOCALAPPDATA%
            // , "/DIR=" + System.getenv("LOCALAPPDATA") + "\\blindWriter"
        )
        .directory(installer.getParent().toFile())
        .inheritIO()
        .start();
    }

    // simple semantic version comparator
    public static int versionCompare(String v1, String v2) {
        String[] a1 = v1.split("[.-]");
        String[] a2 = v2.split("[.-]");
        int n = Math.max(a1.length, a2.length);
        for (int i = 0; i < n; i++) {
            int x = i < a1.length ? parsePart(a1[i]) : 0;
            int y = i < a2.length ? parsePart(a2[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }
    private static int parsePart(String s) {
        try { return Integer.parseInt(s); } catch (Exception ex) { return 0; }
    }

    /**
     * Data object public pour être utilisé par l'UI.
     */
    public static class UpdateInfo {
        public String version;
        public String url;
        public String sha256;
        public long size;
        public String releaseNotes;
    }
    
 // getter simple
    public String getCurrentVersion() {
        return currentVersion;
    }

    /**
     * Retourne true si la version distante (info.version) est strictement plus récente
     * que la version locale courante (this.currentVersion).
     */
    public boolean isUpdateAvailable(UpdateInfo info) {
        if (info == null || info.version == null) return false;
        return versionCompare(info.version, this.currentVersion) > 0;
    }
    
}
