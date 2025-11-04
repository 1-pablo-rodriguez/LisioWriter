package writer.update;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

@SuppressWarnings("serial")
public final class UpdateDialog extends JDialog {

    // Réglages lisibilité
    private static final int    BASE_FONT_SIZE   = 20;  // taille de base lisible
    private static final String UI_FONT_FAMILY   = "Arial";
    private static final Color  BG               = new Color(45,45,45);
    private static final Color  FG               = Color.WHITE;
    private static final Color  ACCENT           = new Color(255, 200, 80);

    private JTextArea area;
    private JButton btnDownload, btnLater;

    public UpdateDialog(java.awt.Window owner,
                        AutoUpdater updater,
                        AutoUpdater.UpdateInfo info) {
        super(owner, "Mise à jour disponible", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(0, 0));
        setResizable(true);
        getContentPane().setBackground(BG);

        // ==== En-tête contrasté et lisible ====
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG.darker());
        header.setBorder(new EmptyBorder(14, 18, 10, 18));

        JLabel title = new JLabel("Mise à jour disponible");
        title.setForeground(ACCENT);
        title.setFont(new Font(UI_FONT_FAMILY, Font.BOLD, BASE_FONT_SIZE + 6));

        JLabel subtitle = new JLabel(
            "Nouvelle version : " + nz(info.version) +
            "    |    Version installée : " + nz(updater.getCurrentVersion())
        );
        subtitle.setForeground(FG);
        subtitle.setFont(new Font(UI_FONT_FAMILY, Font.PLAIN, BASE_FONT_SIZE));

        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        // ==== Zone texte accessible (JTextArea RO) ====
        StringBuilder txt = new StringBuilder(256);
        if (info.channel != null && !info.channel.isBlank()) {
            txt.append("Canal : ").append(info.channel).append('\n');
        }
        if (info.size > 0) {
            txt.append("Taille : ").append(String.format("%,d", info.size)).append(" octets").append('\n');
        }
        if (info.sha256 != null && !info.sha256.isBlank()) {
            txt.append("SHA-256 : ").append(info.sha256).append('\n');
        }
        if (info.notes != null && !info.notes.isBlank()) {
            if (txt.length() > 0) txt.append('\n');
            txt.append("Notes de version").append('\n');
            txt.append("────────────────────────────────").append('\n');
            txt.append(info.notes).append('\n');
        }

        area = new JTextArea(txt.toString());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        area.setFont(new Font(UI_FONT_FAMILY, Font.PLAIN, BASE_FONT_SIZE));
        area.setBackground(BG);
        area.setForeground(FG);
        area.setSelectionColor(new Color(80,120,255));
        area.setSelectedTextColor(Color.BLACK);
        area.setBorder(new EmptyBorder(10, 16, 10, 16));
        area.getAccessibleContext().setAccessibleName("Informations de mise à jour");
        area.getAccessibleContext().setAccessibleDescription(
            "Utilisez les flèches pour lire. Tab pour aller aux boutons. Ctrl plus ou moins pour zoomer."
        );

        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createMatteBorder(1,0,1,0, BG.darker()));
        sp.getViewport().setBackground(BG);
        sp.setPreferredSize(new Dimension(720, 380));
        add(sp, BorderLayout.CENTER);

        // ==== Boutons larges et lisibles ====
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        buttons.setBackground(BG);
        buttons.setBorder(new EmptyBorder(8, 12, 12, 12));

        btnDownload = new JButton("Télécharger");
        btnLater    = new JButton("Plus tard");
        for (JButton b : new JButton[]{btnDownload, btnLater}) {
            b.setFont(new Font(UI_FONT_FAMILY, Font.BOLD, BASE_FONT_SIZE));
            b.setMargin(new Insets(10, 18, 10, 18));
        }
        btnDownload.setMnemonic(KeyEvent.VK_T);
        btnLater.setMnemonic(KeyEvent.VK_P);

        btnDownload.getAccessibleContext().setAccessibleDescription("Ouvrir le lien de téléchargement dans le navigateur.");
        btnLater.getAccessibleContext().setAccessibleDescription("Fermer la fenêtre de mise à jour.");

        buttons.add(btnDownload);
        buttons.add(btnLater);
        add(buttons, BorderLayout.SOUTH);

        // ==== Actions ====
        btnDownload.addActionListener(e -> {
            System.out.println("[Update] downloadUrl = " + info.downloadUrl);
            if (info.downloadUrl == null || info.downloadUrl.isBlank()) {
                Toolkit.getDefaultToolkit().beep();
                JOptionPane.showMessageDialog(this,
                    "URL de téléchargement vide ou manquante (champ \"url\" du updates.json).",
                    "Mise à jour", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                String suggested = info.downloadUrl.substring(info.downloadUrl.lastIndexOf('/') + 1);
                if (suggested.isBlank()) suggested = "LisioWriter-setup.exe";

                // Fichier : dossier temporaire (pas de choix utilisateur)
                java.nio.file.Path target = java.nio.file.Files.createTempFile(
                        "LisioWriter-update-", "-" + suggested);

                Long size  = (info.size > 0) ? info.size : null;
                String sha = (info.sha256 != null && !info.sha256.isBlank()) ? info.sha256 : null;

                // Téléchargement ACCESSIBLE + callback fin
                writer.update.DownloadDialog.startWithCallback(
                    this,
                    info.downloadUrl,
                    target,
                    size,
                    sha,
                    (java.nio.file.Path downloaded) -> {
                        try {
                            // Lance l’install silencieuse via l’utilitaire
                            InstallerUtil.runInstallerSilent(downloaded);

                            // Ferme l'application pour laisser l’installateur travailler
                            System.exit(0);

                        } catch (Exception ex) {
                            Toolkit.getDefaultToolkit().beep();
                            JOptionPane.showMessageDialog(this,
                                "Impossible de lancer l'installation silencieuse : " + ex.getMessage(),
                                "Mise à jour", JOptionPane.ERROR_MESSAGE);
                            ex.printStackTrace();
                        }
                    }
                );

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Erreur : " + ex.getMessage(),
                    "Téléchargement", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });


        btnLater.addActionListener(e -> dispose());

        // ==== Comportements clavier ====
        // Entrée par défaut = Annuler (Plus tard)
        getRootPane().setDefaultButton(btnLater);

        // Échap pour fermer
        getRootPane().registerKeyboardAction(
            ev -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Ctrl+U = Télécharger
        getRootPane().registerKeyboardAction(
            ev -> btnDownload.doClick(),
            KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // === Zoom clavier pour malvoyants (Ctrl + / - / 0) ===
        getRootPane().registerKeyboardAction(
            ev -> adjustZoom(+2),
            KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), // Ctrl+'+' (clavier principal)
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        getRootPane().registerKeyboardAction(
            ev -> adjustZoom(+2),
            KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK),    // Ctrl+'+' (pavé num.)
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        getRootPane().registerKeyboardAction(
            ev -> adjustZoom(-2),
            KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK),  // Ctrl+'-'
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        getRootPane().registerKeyboardAction(
            ev -> resetZoom(),
            KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK),      // Ctrl+'0'
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        pack();
        setLocationRelativeTo(owner);

        // Focus immédiat pour lecture Braille
        SwingUtilities.invokeLater(() -> area.requestFocusInWindow());
    }

    // ==== Zoom helpers ====
    private void adjustZoom(int delta) {
        Font f = area.getFont();
        int newSize = Math.max(14, Math.min(48, f.getSize() + delta));
        area.setFont(f.deriveFont((float)newSize));
        btnDownload.setFont(btnDownload.getFont().deriveFont((float)newSize));
        btnLater.setFont(btnLater.getFont().deriveFont((float)newSize));
        revalidate();
    }
    private void resetZoom() {
        area.setFont(new Font(UI_FONT_FAMILY, Font.PLAIN, BASE_FONT_SIZE));
        btnDownload.setFont(new Font(UI_FONT_FAMILY, Font.BOLD, BASE_FONT_SIZE));
        btnLater.setFont(new Font(UI_FONT_FAMILY, Font.BOLD, BASE_FONT_SIZE));
        revalidate();
    }

    private static String nz(String s) { return (s == null) ? "" : s; }
}