package maj;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;

@SuppressWarnings("serial")
public class UpdateDialog extends JDialog {

    private final JLabel info = new JLabel();
    private final JTextArea releaseNotes = new JTextArea(10, 40);
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton downloadBtn = new JButton("Télécharger et installer");
    private final JButton cancelBtn = new JButton("Fermer");

    private final AutoUpdater updater;
    private final AutoUpdater.UpdateInfo infoObj;
    private DownloadWorker worker;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile int currentPct = 0;
    private int lastBeepBucket = -1; // -1 pour forcer un premier bip
    
    
    
    public UpdateDialog(Frame owner, AutoUpdater updater, AutoUpdater.UpdateInfo infoObj) {
        super(owner, "Mise à jour LisioWriter", true);
        this.updater = updater;
        this.infoObj = infoObj;

        setLayout(new BorderLayout(8, 8));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        info.setFocusable(true);
        releaseNotes.setEditable(false);
        releaseNotes.setLineWrap(true);
        releaseNotes.setWrapStyleWord(true);
        releaseNotes.setCaretPosition(0);
        releaseNotes.setFocusable(true);
        progress.setFocusable(false);

        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(downloadBtn);
        p.add(cancelBtn);

        add(info, BorderLayout.NORTH);
        add(new JScrollPane(releaseNotes), BorderLayout.CENTER);
        add(progress, BorderLayout.SOUTH);
        add(p, BorderLayout.PAGE_END);

        pack();
        setLocationRelativeTo(owner);

        // Navigation clavier
        getRootPane().registerKeyboardAction(e -> cancelAndClose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Gérer Entrée manuellement : active le bouton qui a le focus
        getRootPane().setFocusTraversalKeysEnabled(true);
        KeyAdapter enterHandler = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    Object src = e.getSource();
                    if (src == downloadBtn && downloadBtn.isEnabled()) startDownload();
                    else if (src == cancelBtn) cancelAndClose();
                }
            }
        };
        downloadBtn.addKeyListener(enterHandler);
        cancelBtn.addKeyListener(enterHandler);

        addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                downloadBtn.requestFocusInWindow();
            }
        });

        // 🔍 Vérifie si version déjà à jour
        String current = updater.getCurrentVersion();
        if (current != null && infoObj.version != null &&
            infoObj.version.trim().equalsIgnoreCase(current.trim())) {

            speak("Aucune mise à jour disponible. Vous utilisez déjà la version " + current + ".");
            setTitle("Aucune mise à jour disponible");
            info.setText("Vous utilisez déjà la dernière version (" + current + ").");
            releaseNotes.setText("Aucune nouvelle version n’est disponible pour le moment.");
            downloadBtn.setEnabled(false);
            progress.setVisible(false);

            // Fermer avec Entrée ou Échap
            cancelBtn.requestFocusInWindow();
            cancelBtn.setText("Fermer");
            cancelBtn.addActionListener(e -> cancelAndClose());
            return;
        }

        // Message normal si mise à jour disponible
        info.setText("Nouvelle version " + infoObj.version + " disponible. Voulez-vous la télécharger ?");
        releaseNotes.setText(infoObj.releaseNotes != null ? infoObj.releaseNotes : "Notes de version indisponibles.");

        downloadBtn.addActionListener(e -> startDownload());
        cancelBtn.addActionListener(e -> cancelAndClose());
    }

    private void cancelAndClose() {
        cancelDownload();
        setVisible(false);
        dispose();
    }

    private void startDownload() {
        if (!downloadBtn.isEnabled()) return;
        downloadBtn.setEnabled(false);
        cancelBtn.setEnabled(true);

        progress.setIndeterminate(infoObj.size <= 0);
        progress.setMaximum(100);
        cancelled.set(false);

        Toolkit.getDefaultToolkit().beep(); // bip immédiat d’amorce
        speak("Téléchargement en cours. Des bips indiqueront la progression.");

        worker = new DownloadWorker();
        worker.execute();
    }


    private void cancelDownload() {
        cancelled.set(true);
        if (worker != null) worker.cancel(true);
        speak("Téléchargement annulé.");
    }

    private void speak(String text) {
        System.out.println("[TTS] " + text);
    }

    /** Téléchargement + vérif + install */
    private class DownloadWorker extends SwingWorker<Path, Integer> {
        @Override
        protected Path doInBackground() throws Exception {
            
            Path installer = updater.downloadWithProgress(infoObj.url, infoObj.size, new ProgressListener() {
                @Override
                public void onProgress(long bytesRead, long totalBytes) {
                    if (cancelled.get()) return;
                    int pct = totalBytes > 0 ? (int)(bytesRead * 100 / totalBytes) : 0;
                    publish(pct);
                }
                @Override
                public void onStatus(String status) {
                    speak(status);
                }
            });

            if (installer == null || cancelled.get()) return null;

            speak("Vérification du fichier téléchargé.");
            boolean ok = updater.verifySha256(installer, infoObj.sha256);
            if (!ok) {
                speak("Échec de la vérification. Fichier supprimé.");
                Files.deleteIfExists(installer);
                return null;
            }

            Toolkit.getDefaultToolkit().beep();
            Toolkit.getDefaultToolkit().beep();
            Toolkit.getDefaultToolkit().beep();

            speak("Téléchargement terminé. Installation en cours.");
            Thread.sleep(1000);
            updater.runInstaller(installer, true);
            return installer;
        }

        @Override
        protected void done() {
            try {
                Path result = get();
                if (result != null) {
                    speak("Installation terminée. L’application va se fermer.");
                    System.exit(0);
                } else {
                    downloadBtn.setEnabled(true);
                    speak("Mise à jour annulée ou erreur.");
                }
            } catch (Exception e) {
                speak("Erreur pendant la mise à jour : " + e.getMessage());
                e.printStackTrace();
                downloadBtn.setEnabled(true);
            }
        }


        @Override
        protected void process(java.util.List<Integer> chunks) {
            if (chunks == null || chunks.isEmpty()) return;
            currentPct = chunks.get(chunks.size() - 1);

            // MAJ barre
            progress.setIndeterminate(false);
            progress.setValue(currentPct);

            // Bip toutes les 5 %
            int bucket = currentPct / 5;
            if (bucket != lastBeepBucket) {
                Toolkit.getDefaultToolkit().beep();
                lastBeepBucket = bucket;
            }
        }

        
        

    }
}
