package maj;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Boîte de dialogue de mise à jour.
 * Appelle AutoUpdater pour télécharger / vérifier / lancer l'installateur.
 */
@SuppressWarnings("serial")
public class UpdateDialog extends JDialog {

    private final JLabel info = new JLabel();
    private final JTextArea releaseNotes = new JTextArea(10, 40);
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton downloadBtn = new JButton("Télécharger et installer");
    private final JButton cancelBtn = new JButton("Annuler");

    private final AutoUpdater updater;
    private final AutoUpdater.UpdateInfo infoObj;
    private DownloadWorker worker;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public UpdateDialog(Frame owner, AutoUpdater updater, AutoUpdater.UpdateInfo infoObj) {
        super(owner, "Mise à jour disponible: " + infoObj.version, true);
        this.updater = updater;
        this.infoObj = infoObj;

        setLayout(new BorderLayout(8,8));
        info.setText("Version disponible : " + infoObj.version + ". Voulez-vous télécharger ?");
        releaseNotes.setText(infoObj.releaseNotes != null ? infoObj.releaseNotes : "");
        releaseNotes.setEditable(false);
        releaseNotes.setLineWrap(true);
        releaseNotes.setWrapStyleWord(true);
        releaseNotes.setCaretPosition(0);

        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(downloadBtn);
        p.add(cancelBtn);

        add(info, BorderLayout.NORTH);
        add(new JScrollPane(releaseNotes), BorderLayout.CENTER);
        add(progress, BorderLayout.SOUTH);
        add(p, BorderLayout.PAGE_END);

        pack();
        setLocationRelativeTo(owner);

        // Accessibilité : focus et annonce vocale
        addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                downloadBtn.requestFocusInWindow();
                speak("Nouvelle mise à jour disponible. Appuyez sur Télécharger pour lancer la mise à jour.");
            }
            public void windowClosing(WindowEvent e) {
                cancelDownload();
            }
        });

        cancelBtn.addActionListener(e -> {
            cancelDownload();
            setVisible(false);
        });

        downloadBtn.addActionListener(e -> {
            System.out.println("[UPDATE] downloadBtn clicked");
            downloadBtn.setEnabled(false);
            cancelBtn.setEnabled(true);
            startDownload();
        });

    }

    private void startDownload() {
    	if (infoObj.size <= 0) {
    	    progress.setIndeterminate(true);
    	} else {
    	    progress.setIndeterminate(false);
    	    progress.setMaximum(100);
    	}
        System.out.println("[UPDATE] startDownload()");
        cancelled.set(false);
        worker = new DownloadWorker();
        worker.execute();
    }


    private void cancelDownload() {
        cancelled.set(true);
        if (worker != null) worker.cancel(true);
        speak("Téléchargement annulé.");
    }

    public void setProgress(int pct) {
        SwingUtilities.invokeLater(() -> {
            progress.setValue(pct);
        });
    }

    // Placeholder TTS — remplace par ton moteur NVDA / TTS
    private void speak(String text) {
        // Ex: TtsManager.speak(text);
        System.out.println("[TTS] " + text);
    }

    /**
     * SwingWorker qui effectue le téléchargement, la vérification et le lancement de l'installateur.
     */
    private class DownloadWorker extends SwingWorker<Path, Integer> {

        @Override
        protected Path doInBackground() throws Exception {
            speak("Téléchargement en cours...");
            System.out.println("[UPDATE] DownloadWorker.doInBackground() - url=" + infoObj.url + " size=" + infoObj.size);
            // Appel au AutoUpdater.downloadWithProgress (doit être public / package-private)
            Path installer = updater.downloadWithProgress(infoObj.url, infoObj.size, new ProgressListener() {
            	@Override
                public void onProgress(long bytesRead, long totalBytes) {
                    if (cancelled.get()) return;
                    int pct = totalBytes > 0 ? (int)(bytesRead * 100 / totalBytes) : -1;
                    if (pct >= 0) publish(pct);
                }
                @Override
                public void onStatus(String status) {
                    speak(status);
                }
            });

            if (installer == null) {
                speak("Erreur lors du téléchargement.");
                return null;
            }
            if (cancelled.get()) {
                // supprimer fichier temporaire si annulé
                try { Files.deleteIfExists(installer); } catch (Exception ignored) {}
                return null;
            }

            speak("Vérification du fichier...");
            boolean ok = updater.verifySha256(installer, infoObj.sha256);
            if (!ok) {
                speak("La vérification du fichier a échoué. Le fichier sera supprimé.");
                try { Files.deleteIfExists(installer); } catch (Exception ignored) {}
                return null;
            }
            speak("Vérification OK. L'installation va démarrer. L'application va se fermer.");
            // Donne un petit délai pour que la TTS annonce avant de lancer
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}

            // Lance l'installateur. runInstaller doit être public et gérer les flags
            try {
                updater.runInstaller(installer);
            } catch (Exception ex) {
                speak("Impossible de lancer l'installateur: " + ex.getMessage());
                ex.printStackTrace();
                return null;
            }

            return installer;
        }

        @Override
        protected void process(java.util.List<Integer> chunks) {
            if (chunks == null || chunks.isEmpty()) return;
            int last = chunks.get(chunks.size() - 1);
            if (last >= 0) setProgress(last);
        }

        @Override
        protected void done() {
            try {
                Path result = get(); // récupère le path ou null
                if (result != null) {
                    // on quitte l'appli pour laisser l'installateur remplacer les fichiers
                    // Attention : si tu veux sauvegarder documents, le faire avant
                	try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    System.exit(0);
                } else {
                    // erreur ou annulation — réactive le bouton
                    downloadBtn.setEnabled(true);
                }
            } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                e.printStackTrace();
                downloadBtn.setEnabled(true);
            }
        }
    }
}
