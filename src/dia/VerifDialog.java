package dia;

import java.awt.*;
import java.awt.event.KeyEvent;
import javax.swing.*;

@SuppressWarnings("serial")
public final class VerifDialog extends JDialog {
    private final JLabel label = new JLabel("Vérification en cours…", SwingConstants.LEADING);
    private final JProgressBar bar = new JProgressBar();
    private javax.swing.Timer showTimer;

    // Préférences visuelles (persistantes sur la session)
    private static float zoom = 1.2f;           // départ légèrement agrandi
    private static boolean highContrast = true; // contraste élevé par défaut
    private static final float BASE_FONT_PT = 18f;

    private VerifDialog(Window owner, String message) {
        super(owner, "Veuillez patienter", ModalityType.MODELESS); // non bloquant
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setResizable(true);
        setAlwaysOnTop(true);

        // --- Contenu ---
        label.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.getAccessibleContext().setAccessibleName(message != null ? message : "Vérification en cours…");
        label.getAccessibleContext().setAccessibleDescription("Vérification du document en cours");

        bar.setIndeterminate(true);
        bar.setStringPainted(true); // affiche aussi le message dans la barre (plus visible)
        bar.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        // Épaissir la barre (visuel)
        bar.setPreferredSize(new Dimension(420, Math.max(18, bar.getPreferredSize().height + 6)));

        JPanel p = new JPanel(new BorderLayout());
        p.add(label, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);

        setContentPane(p);
        // Bordure épaisse bien contrastée
        getRootPane().setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80), 3),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));

        // Échap : on garde NO-OP (on ne ferme pas pendant l’opération)
        getRootPane().registerKeyboardAction(e -> { /* no-op */ },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Raccourcis accessibilité
        addZoomBindings(getRootPane());     // Ctrl +/−/0
        addHighContrastBinding(getRootPane()); // H

        setMessage(message);
        applyVisualPrefs(); // thème + zoom

        pack();
        // Taille confortable + minimum
        int w = Math.max(520, getWidth());
        int h = Math.max(160, getHeight());
        setSize(new Dimension(w, h));
        setMinimumSize(new Dimension(420, 140));
        setLocationRelativeTo(owner);
    }

    public static VerifDialog showNow(Window owner, String message) {
        VerifDialog d = new VerifDialog(owner, message);
        d.showTimer = new javax.swing.Timer(60, e -> {
            if (d.isDisplayable()) d.setVisible(true); // évite scintillement / flash
        });
        d.showTimer.setRepeats(false);
        d.showTimer.start();
        return d;
    }

    public void setMessage(String message) {
        String txt = (message != null && !message.isBlank()) ? message : "Vérification en cours…";
        label.setText(txt);
        bar.setString(txt); // le même message au centre de la barre
        label.getAccessibleContext().setAccessibleName(txt);
    }

    public void close() {
        SwingUtilities.invokeLater(() -> {
            if (showTimer != null && showTimer.isRunning()) showTimer.stop();
            setVisible(false);
            dispose();
        });
    }

    // ---------- Accessibilité visuelle ----------

    private void applyVisualPrefs() {
        float pt = BASE_FONT_PT * zoom;

        Font fLabel = label.getFont().deriveFont(Font.BOLD, pt);
        Font fBar   = bar.getFont().deriveFont(pt * 0.95f);

        label.setFont(fLabel);
        bar.setFont(fBar);

        if (highContrast) {
            Color bg = new Color(20, 20, 20);
            Color fg = new Color(240, 240, 240);
            Color frame = new Color(200, 200, 200);

            getContentPane().setBackground(bg);
            label.setForeground(fg);
            label.setBackground(bg);
            bar.setForeground(fg);
            bar.setBackground(new Color(40, 40, 40));
            getRootPane().setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(frame, 3),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        } else {
            // Couleurs UI par défaut
            Color bg = UIManager.getColor("Panel.background");
            Color fg = UIManager.getColor("Label.foreground");

            getContentPane().setBackground(bg);
            label.setForeground(fg != null ? fg : Color.BLACK);
            label.setBackground(bg);
            bar.setForeground(UIManager.getColor("ProgressBar.foreground"));
            bar.setBackground(UIManager.getColor("ProgressBar.background"));
            getRootPane().setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(120, 120, 120), 2),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        }
    }

    private void addZoomBindings(JComponent c) {
        int CTRL = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        // Ctrl + '+', Ctrl + Numpad '+'
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, CTRL), "zoom_in");
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ADD, CTRL), "zoom_in");

        // Ctrl + '-'
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, CTRL), "zoom_out");
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, CTRL), "zoom_out");

        // Ctrl + '0' (reset)
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_0, CTRL), "zoom_reset");

        c.getActionMap().put("zoom_in", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                zoom = Math.min(3.0f, zoom + 0.1f);
                applyVisualPrefs();
            }
        });
        c.getActionMap().put("zoom_out", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                zoom = Math.max(0.6f, zoom - 0.1f);
                applyVisualPrefs();
            }
        });
        c.getActionMap().put("zoom_reset", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                zoom = 1.0f;
                applyVisualPrefs();
            }
        });
    }

    private void addHighContrastBinding(JComponent c) {
        // Touche 'H' pour basculer haut contraste
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_H, 0), "toggle_hc");
        c.getActionMap().put("toggle_hc", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                highContrast = !highContrast;
                applyVisualPrefs();
            }
        });
    }
}
