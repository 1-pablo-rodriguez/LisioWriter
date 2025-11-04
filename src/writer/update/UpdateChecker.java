package writer.update;

import java.awt.Toolkit;
import java.awt.Window;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import writer.ui.EditorApi;

public final class UpdateChecker {

    private UpdateChecker() {}

    /** Lance la vérification en tâche de fond et gère les dialogues. */
    public static void checkNow(EditorApi ctx) {
        Toolkit.getDefaultToolkit().beep(); // feedback immédiat

        new SwingWorker<AutoUpdater.UpdateInfo, Void>() {
            private final AutoUpdater updater =
                new AutoUpdater(
                    "https://raw.githubusercontent.com/1-pablo-rodriguez/LisioWriter/refs/heads/main/updates.json",
                    writer.util.AppInfo.getAppVersion()
                );

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
                        dia.InfoDialog.show(owner, "Mise à jour", "Impossible de vérifier les mises à jour.");
                        return;
                    }

                    int cmp = AutoUpdater.versionCompare(info.version, updater.getCurrentVersion());
                    if (cmp > 0) {
                        UpdateDialog dlg = new UpdateDialog(owner, updater, info);
                        dlg.setVisible(true);
                    } else if (cmp == 0) {
                        Toolkit.getDefaultToolkit().beep();
                        dia.InfoDialog.show(owner, "Mise à jour",
                                "Vous avez déjà la dernière version : " + updater.getCurrentVersion());
                    } else {
                        Toolkit.getDefaultToolkit().beep();
                        dia.InfoDialog.show(owner, "Mise à jour",
                                "Votre version (" + updater.getCurrentVersion() + ") est plus récente que celle du serveur (" + info.version + ")");
                    }
                } catch (Exception ex) {
                    Toolkit.getDefaultToolkit().beep();
                    Window owner = SwingUtilities.getWindowAncestor(ctx.getEditor());
                    dia.InfoDialog.show(owner, "Mise à jour", "Erreur lors de la vérification : " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }.execute();
    }
}