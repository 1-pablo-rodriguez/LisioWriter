package dia;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.DefaultCaret;

@SuppressWarnings("serial")
public final class InfoDialog extends JDialog {
    private final JTextArea textArea = new JTextArea();
    private final JScrollPane scroll = new JScrollPane(textArea,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    // État zoom/contraste partagé entre ouvertures
    private static float zoom = 1.0f;            // 1.0 = taille de base
    private static boolean highContrast = true;  // par défaut ON pour mal-voyants

    // Taille de base lisible; sera multipliée par zoom
    private static final float BASE_FONT_PT = 36f;

    public InfoDialog(Window owner, String title, String message) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        // --- Zone lisible par SR et barre braille ---
        textArea.setEditable(false);
        textArea.setFocusable(true);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // (Optionnel) si tu as un styler de caret haut contraste :
        try {
            Class<?> styler = Class.forName("writer.CaretStyler");
            styler.getMethod("applyHighVisibility", JTextArea.class).invoke(null, textArea);
        } catch (Exception ignore) { /* pas grave si absent */ }

        // Accessibilité
        textArea.getAccessibleContext().setAccessibleName(title != null ? title : "Information");
        textArea.getAccessibleContext().setAccessibleDescription("Message d'information: " + (message != null ? message : ""));

        // Texte
        textArea.setText(message != null ? message : "");
        
        // Curseur visible (utile pour loupe/braille)
        DefaultCaret caret = new DefaultCaret();
        caret.setBlinkRate(500);
        textArea.setCaret(caret);
        textArea.setCaretPosition(0);
        caret.setBlinkRate(500);
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE); // ← évite les « sauts » à la fin
        textArea.setCaret(caret);

        // Après textArea.setText(...)
        textArea.setCaretPosition(0);
        textArea.setSelectionStart(0);
        textArea.setSelectionEnd(0);

        // ... à la toute fin du constructeur (après pack() et setLocationRelativeTo(owner))
        SwingUtilities.invokeLater(() -> {
            textArea.requestFocusInWindow();
            textArea.setCaretPosition(0);        // ← reforce une fois la fenêtre visible
            textArea.setSelectionStart(0);
            textArea.setSelectionEnd(0);
            scroll.getVerticalScrollBar().setValue(0); // remonte le scroll au tout début
        });


        // Scroll pane : bordure nette et focus clair
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120,120,120)),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));

        // Boutons
        JButton ok = new JButton("OK");
        ok.setMnemonic(KeyEvent.VK_O);
        ok.addActionListener(e -> dispose());

        JButton copy = new JButton("Copier");
        copy.setMnemonic(KeyEvent.VK_C);
        copy.addActionListener(e -> {
            StringSelection sel = new StringSelection(textArea.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            ok.requestFocusInWindow(); // évite rester dans la zone texte
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(copy);
        buttons.add(ok);

        // Contenu
        JPanel content = new JPanel(new BorderLayout());
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        // Raccourcis : ESC ferme, Entrée = OK
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(ok);

        // Zoom clavier : Ctrl +/−/0
        addZoomBindings(content);

        // Basculer haut contraste (H)
        addHighContrastBinding(content);

        // Appliquer thème & zoom
        applyVisualPrefs();
        
     // Appliquer thème & zoom
     // -> détecter d'abord le DPI / valeur initiale de zoom, ensuite appliquer applyVisualPrefs()
     // Appliquer thème & zoom
     // -> détecter d'abord le DPI / valeur initiale de zoom, ensuite appliquer applyVisualPrefs()
     int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
     // Ajustements par palier (les cas les plus élevés en premier)
     if (dpi >= 192) {
         // écran très haute-DPI (ex. 200%)
         zoom = Math.max(zoom, 1.6f);  // ne diminue pas si l'utilisateur avait déjà augmenté zoom
     } else if (dpi >= 144) {
         // écran haute-DPI (ex. 150%)
         zoom = Math.max(zoom, 1.3f);
     } else {
         // garde la valeur actuelle si l'utilisateur a déjà zoomé dans une session précédente,
         // sinon assure un minimum raisonnable
         zoom = Math.max(zoom, 1.0f);
     }

     // Optionnel : forcer une taille initiale plus grande indépendamment du DPI
     // Décommente si tu veux toujours démarrer plus gros (ex. 1.4x)
     // zoom = Math.max(zoom, 1.4f);

     // Maintenant on applique les préférences visuelles (police & contraste)
     applyVisualPrefs();

     pack(); // calcule les tailles selon la police appliquée

     // Taille confortable par défaut (on peut ensuite réduire/agrandir avec Ctrl +/-)
     setSize(new Dimension(Math.max(930, getWidth()), Math.max(630, getHeight())));
     setLocationRelativeTo(owner);

     // Focus sur la zone lisible pour la braille
     SwingUtilities.invokeLater(() -> textArea.requestFocusInWindow());


    }

    public static void show(Window owner, String title, String message) {
    	StringBuilder msg = new StringBuilder();
    	msg.append("F1-").append(message).append(" ↓");
    	msg.append("\nFERMER INFO. ↓");
        msg.append("\n• Échappe ou Entrée");
        InfoDialog d = new InfoDialog(owner, title, msg.toString());
        d.setVisible(true);
    }

    // ---------- Helpers accessibilité ----------

    private void applyVisualPrefs() {
        // Police grande + zoom
        float pt = BASE_FONT_PT * zoom;
        Font base = textArea.getFont().deriveFont(pt);
        textArea.setFont(base);

        // Haut contraste
        if (highContrast) {
            textArea.setForeground(new Color(240, 240, 240));
            textArea.setBackground(new Color(20, 20, 20));
            scroll.getViewport().setBackground(textArea.getBackground());
            getContentPane().setBackground(new Color(20, 20, 20));
        } else {
            textArea.setForeground(UIManager.getColor("TextArea.foreground"));
            textArea.setBackground(UIManager.getColor("TextArea.background"));
            scroll.getViewport().setBackground(textArea.getBackground());
            getContentPane().setBackground(UIManager.getColor("Panel.background"));
        }
    }

    private void addZoomBindings(JComponent c) {
        // Ctrl + '+'
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "zoom_in");
        // Ctrl + Numpad '+'
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ADD, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "zoom_in");
        // Ctrl + '-'
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "zoom_out");
        // Ctrl + Numpad '-'
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "zoom_out");
        // Ctrl + '0' (reset)
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_0, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "zoom_reset");

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
        // Touche 'H' pour basculer haut contraste (sans modifieurs)
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
