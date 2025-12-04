package writer.update;

import java.awt.Toolkit;
import java.awt.Window;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import writer.ui.EditorApi;

public final class UpdateChecker {

    // URL utilisée aussi par checkNow()
    private static final String UPDATES_URL =
        "https://raw.githubusercontent.com/1-pablo-rodriguez/LisioWriter/refs/heads/main/updates.json";

    public UpdateChecker() {}

    /** API silencieuse : renvoie true si une nouvelle version est disponible, sinon false. */
    public static boolean hasNewVersion() {
        try {
            AutoUpdater updater = new AutoUpdater(
                UPDATES_URL,
                writer.util.AppInfo.getAppVersion()
            );
            AutoUpdater.UpdateInfo info = updater.fetchMetadata();
            if (info == null || info.version == null || info.version.isBlank()) {
                return false;
            }
            int cmp = AutoUpdater.versionCompare(info.version, updater.getCurrentVersion());
            return (cmp > 0);
        } catch (Exception e) {
            // Silencieux : en cas d'erreur réseau/JSON, on considère "pas de nouvelle version"
            return false;
        }
    }

    /** Variante pratique si tu veux aussi récupérer les métadonnées quand il y a une MAJ. */
    public static UpdateAvailability checkAvailability() {
        try {
            AutoUpdater updater = new AutoUpdater(
                UPDATES_URL,
                writer.util.AppInfo.getAppVersion()
            );
            AutoUpdater.UpdateInfo info = updater.fetchMetadata();
            if (info == null || info.version == null || info.version.isBlank()) {
                return new UpdateAvailability(false, null);
            }
            int cmp = AutoUpdater.versionCompare(info.version, updater.getCurrentVersion());
            return new UpdateAvailability(cmp > 0, info);
        } catch (Exception e) {
            return new UpdateAvailability(false, null);
        }
    }

    /** Résultat optionnel : booléen + metadata (URL, notes, etc.) */
    public static final class UpdateAvailability {
        public final boolean hasNewVersion;
        public final AutoUpdater.UpdateInfo info;
        public UpdateAvailability(boolean hasNewVersion, AutoUpdater.UpdateInfo info) {
            this.hasNewVersion = hasNewVersion;
            this.info = info;
        }
    }

    /** Vérification avec UI (inchangée) */
    public static void checkNow(EditorApi ctx) {
        Toolkit.getDefaultToolkit().beep(); // feedback immédiat

        new SwingWorker<AutoUpdater.UpdateInfo, Void>() {
            private final AutoUpdater updater =
                new AutoUpdater(UPDATES_URL, writer.util.AppInfo.getAppVersion());

            @Override
            protected AutoUpdater.UpdateInfo doInBackground() throws Exception {
                return updater.fetchMetadata();
            }

            @Override
            protected void done() {
                try {
                    AutoUpdater.UpdateInfo info = get();
                    Window owner = SwingUtilities.getWindowAncestor(ctx.getEditor());

                    if (info == null || info.version == null || info.version.isBlank()) {
                        Toolkit.getDefaultToolkit().beep();
                        ctx.showInfo("Mise à jour", "Impossible de vérifier les mises à jour.");
                        return;
                    }

                    int cmp = AutoUpdater.versionCompare(info.version, updater.getCurrentVersion());
                    if (cmp > 0) {
                        UpdateDialog dlg = new UpdateDialog(owner, updater, info);
                        dlg.setVisible(true);
                    } else if (cmp == 0) {
                        Toolkit.getDefaultToolkit().beep();
                        ctx.showInfo("Mise à jour",
                                "Vous avez déjà la dernière version : " + updater.getCurrentVersion());
                    } else {
                        Toolkit.getDefaultToolkit().beep();
                        ctx.showInfo("Mise à jour",
                                "Votre version (" + updater.getCurrentVersion() + ") est plus récente que celle du serveur (" + info.version + ")");
                    }
                } catch (Exception ex) {
                    Toolkit.getDefaultToolkit().beep();
                    ctx.showInfo("Mise à jour", "Erreur lors de la vérification : " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }.execute();
    }
}