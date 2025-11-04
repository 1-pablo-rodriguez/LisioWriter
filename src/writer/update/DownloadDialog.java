package writer.update;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Consumer;

@SuppressWarnings("serial")
public final class DownloadDialog extends JDialog implements PropertyChangeListener {

    // --- UI lisible / accessible ---
    private static final int BASE_FONT_SIZE = 20;
    private static final String UI_FONT = "Arial";
    private static final Color BG = new Color(45, 45, 45);
    private static final Color FG = Color.WHITE;

    // --- HttpClient partagé ---
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15))
            .proxy(ProxySelector.getDefault())
            .build();

    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JTextArea status = new JTextArea();
    private final JButton btnCancel = new JButton("Annuler");
    private final JButton btnOpen = new JButton("Ouvrir le fichier");
    private final JButton btnFolder = new JButton("Ouvrir le dossier");

    private DownloadWorker worker;
    private Consumer<Path> onSuccess; // callback quand terminé OK

    private DownloadDialog(Window owner) {
        super(owner, "Téléchargement en cours", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);
        setResizable(true);

        // En-tête
        JLabel title = new JLabel("Téléchargement en cours…");
        title.setForeground(new Color(255, 200, 80));
        title.setFont(new Font(UI_FONT, Font.BOLD, BASE_FONT_SIZE + 6));
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(BG.darker());
        head.setBorder(new EmptyBorder(12, 16, 8, 16));
        head.add(title, BorderLayout.CENTER);
        add(head, BorderLayout.NORTH);

        // Zone de statut (braille-friendly)
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setBackground(BG);
        status.setForeground(FG);
        status.setFont(new Font(UI_FONT, Font.PLAIN, BASE_FONT_SIZE));
        status.setCaretPosition(0);
        status.setBorder(new EmptyBorder(10, 16, 10, 16));
        status.getAccessibleContext().setAccessibleName("Statut du téléchargement");
        status.getAccessibleContext().setAccessibleDescription("Utilisez les flèches pour lire le pourcentage et la vitesse.");

        JScrollPane sp = new JScrollPane(status);
        sp.getViewport().setBackground(BG);
        sp.setBorder(BorderFactory.createEmptyBorder());
        add(sp, BorderLayout.CENTER);

        // Barre de progression
        progress.setStringPainted(true);
        progress.setFont(new Font(UI_FONT, Font.BOLD, BASE_FONT_SIZE));
        progress.setBackground(BG);
        JPanel progPanel = new JPanel(new BorderLayout());
        progPanel.setBackground(BG);
        progPanel.setBorder(new EmptyBorder(0, 16, 8, 16));
        progPanel.add(progress, BorderLayout.CENTER);
        add(progPanel, BorderLayout.SOUTH);

        // Boutons (utiles surtout si tu veux déboguer/ouvrir le fichier)
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        buttons.setBackground(BG);
        for (JButton b : new JButton[]{btnFolder, btnOpen, btnCancel}) {
            b.setFont(new Font(UI_FONT, Font.BOLD, BASE_FONT_SIZE));
        }
        btnOpen.setEnabled(false);
        btnFolder.setEnabled(false);
        btnCancel.setMnemonic(KeyEvent.VK_A);
        btnOpen.setMnemonic(KeyEvent.VK_O);
        btnFolder.setMnemonic(KeyEvent.VK_D);
        buttons.add(btnFolder);
        buttons.add(btnOpen);
        buttons.add(btnCancel);
        add(buttons, BorderLayout.PAGE_END);

        // Comportements clavier
        getRootPane().setDefaultButton(btnCancel);
        getRootPane().registerKeyboardAction(ev -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Actions boutons
        btnCancel.addActionListener(e -> {
            if (worker != null) worker.cancel(true);
            dispose();
        });
        btnOpen.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(worker.outputFile().toFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Impossible d’ouvrir : " + ex.getMessage());
            }
        });
        btnFolder.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(worker.outputFile().getParent().toFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Impossible d’ouvrir le dossier : " + ex.getMessage());
            }
        });

        setPreferredSize(new Dimension(760, 420));
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(() -> status.requestFocusInWindow());
    }

    /** Démarrage simple (sans callback). */
    public static void start(Window owner, String url, Path target, Long expectedSize, String sha256Hex) {
        DownloadDialog dlg = new DownloadDialog(owner);
        dlg.worker = new DownloadWorker(url, target, expectedSize, sha256Hex);
        dlg.worker.addPropertyChangeListener(dlg);
        Toolkit.getDefaultToolkit().beep();
        dlg.worker.execute();     // lance en arrière-plan
        dlg.setVisible(true);     // modal
    }

    /** Démarrage avec callback (fermeture auto + onSuccess). */
    public static void startWithCallback(
            Window owner,
            String url,
            Path target,
            Long expectedSize,
            String sha256Hex,
            Consumer<Path> onSuccess
    ) {
        DownloadDialog dlg = new DownloadDialog(owner);
        dlg.onSuccess = onSuccess;
        dlg.worker = new DownloadWorker(url, target, expectedSize, sha256Hex);
        dlg.worker.addPropertyChangeListener(dlg);
        Toolkit.getDefaultToolkit().beep();
        dlg.worker.execute();
        dlg.setVisible(true);
    }

    // --- Réception des events du worker (EDT) ---
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        switch (evt.getPropertyName()) {
            case "progress" -> {
                int p = (Integer) evt.getNewValue();
                progress.setValue(p);
                progress.setString(p + " %");
            }
            case "status" -> {
                status.setText((String) evt.getNewValue());
                status.setCaretPosition(status.getDocument().getLength());
            }
            case "doneOk" -> {
                Toolkit.getDefaultToolkit().beep();
                Path downloaded = worker.outputFile();
                // auto-fermeture + callback
                dispose();
                if (onSuccess != null && downloaded != null) {
                    try { onSuccess.accept(downloaded); } catch (Exception ignore) {}
                }
            }
            case "doneFail" -> {
                Toolkit.getDefaultToolkit().beep();
                dispose();
            }
        }
    }

    // ================== Worker (HttpClient) ==================
    private static final class DownloadWorker extends SwingWorker<Boolean, Void> {
        private final String url;
        private final Path dest;
        private final Long expectedSize;
        private final String sha256Expected; // hex ou null

        private long totalBytes = 0L;
        private long contentLen = -1L;
        private long t0 = System.nanoTime();

        DownloadWorker(String url, Path dest, Long expectedSize, String sha256Expected) {
            this.url = url;
            this.dest = dest;
            this.expectedSize = expectedSize;
            this.sha256Expected = (sha256Expected == null || sha256Expected.isBlank()) ? null : sha256Expected;
        }

        Path outputFile() { return dest; }

        @Override
        protected Boolean doInBackground() {
            try {
                firePropertyChange("status", null, "Connexion à " + url + " …");

                // HEAD (optionnel) : tenter d'obtenir la taille
                try {
                    HttpRequest head = HttpRequest.newBuilder(URI.create(url))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(10))
                            .build();
                    HttpResponse<Void> headResp = HTTP.send(head, HttpResponse.BodyHandlers.discarding());
                    if (headResp.statusCode() >= 400) {
                        firePropertyChange("status", null, "Serveur renvoie code " + headResp.statusCode());
                    }
                    String cl = headResp.headers().firstValue("Content-Length").orElse(null);
                    if (cl != null) contentLen = Long.parseLong(cl);
                } catch (Exception ignore) { /* on continuera sans */ }

                // fallback sur expectedSize si HEAD n'a rien donné
                if (contentLen <= 0 && expectedSize != null && expectedSize > 0) contentLen = expectedSize;

                // GET
                HttpRequest get = HttpRequest.newBuilder(URI.create(url)).GET().build();
                HttpResponse<InputStream> resp = HTTP.send(get, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) {
                    firePropertyChange("status", null, "Erreur réseau: code " + resp.statusCode());
                    return false;
                }

                // Téléchargement
                Files.createDirectories(dest.getParent());
                try (InputStream in = resp.body();
                     OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        if (isCancelled()) return false;
                        out.write(buf, 0, r);
                        totalBytes += r;
                        updateProgress();
                    }
                }

                // Vérif SHA-256 si fournie
                if (sha256Expected != null) {
                    String got = sha256(dest);
                    if (!sha256Expected.equalsIgnoreCase(got)) {
                        firePropertyChange("status", null,
                                "Erreur : SHA-256 différent (attendu " + sha256Expected + ", reçu " + got + ")");
                        return false;
                    }
                }

                return true;

            } catch (Exception ex) {
                firePropertyChange("status", null, "Erreur : " + ex.getMessage());
                ex.printStackTrace();
                return false;
            }
        }

        private void updateProgress() {
            long now = System.nanoTime();
            double dt = (now - t0) / 1e9;
            double speed = (dt > 0) ? (totalBytes / dt) : 0.0;
            int pct = (contentLen > 0) ? (int) Math.min(100, (totalBytes * 100 / contentLen)) : 0;

            setProgress(pct); // déclenche l'event "progress"

            String humanTotal = humanBytes(totalBytes);
            String humanLen = (contentLen > 0) ? humanBytes(contentLen) : "inconnu";
            String humanSpd = humanBytes((long) speed) + "/s";

            StringBuilder sb = new StringBuilder(160);
            sb.append("Téléchargé : ").append(humanTotal).append(" / ").append(humanLen)
              .append("  (").append(pct).append(" %)  ")
              .append("Vitesse : ").append(humanSpd);
            firePropertyChange("status", null, sb.toString());
        }

        @Override
        protected void done() {
            boolean ok = false;
            try { ok = get(); } catch (Exception ignore) {}
            if (ok) firePropertyChange("status", null, "Téléchargement terminé : " + dest.getFileName());
            firePropertyChange(ok ? "doneOk" : "doneFail", null, null);
        }

        private static String sha256(Path file) throws Exception {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = fis.read(buf)) != -1) md.update(buf, 0, r);
            }
            return HexFormat.of().formatHex(md.digest());
        }

        private static String humanBytes(long n) {
            if (n < 1024) return n + " o";
            int exp = (int) (Math.log(n) / Math.log(1024));
            String pre = "KMGTPE".charAt(exp - 1) + "";
            return String.format(Locale.FRANCE, "%.1f %sio", n / Math.pow(1024, exp), pre);
        }
    }
}
