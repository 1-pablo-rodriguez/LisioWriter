package dia;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
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
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Element;

import writer.model.Affiche;

@SuppressWarnings("serial")
public final class InfoDialog extends JDialog {
    private final JTextArea textArea = new JTextArea();
    private final JScrollPane scroll = new JScrollPane(textArea,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    // ou HORIZONTAL_SCROLLBAR_ALWAYS si tu veux la voir tout le temps

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
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(false);
        textArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // (Optionnel) si tu as un styler de caret haut contraste :
        try {
            Class<?> styler = Class.forName("writer.CaretStyler");
            styler.getMethod("applyHighVisibility", JTextArea.class).invoke(null, textArea);
        } catch (Exception ignore) { /* pas grave si absent */ }


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

     
     addParagraphJumpBindings(textArea);
     
     pack(); // calcule les tailles selon la police appliquée

  // largeur fixe (par ex. adaptée à la plage braille / confort écran)
     int width  = 930;                    // choisis ta valeur "idéale"
     int height = Math.max(630, getHeight());  // au moins 630 de haut

     setSize(new Dimension(width, height));
     setLocationRelativeTo(owner);

     // Focus sur la zone lisible pour la braille
     SwingUtilities.invokeLater(() -> textArea.requestFocusInWindow());


    }

    public static void show(Window owner, String title, String message, Affiche f) {
    	StringBuilder msg = new StringBuilder();
    	String c = "F1. ";
    	if(f==Affiche.TEXTE2) c="F2. ";
    	msg.append(c).append(message).append(" ↓");
        msg.append("\n").append(c).append("Échap ou Entrée.");
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
    
    private void addParagraphJumpBindings(JTextArea ta) {
        InputMap im = ta.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = ta.getActionMap();

        // Remplace le comportement par défaut des flèches
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "jumpNextParagraph");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),   "jumpPrevParagraph");

        am.put("jumpNextParagraph", new AbstractAction() {
            @SuppressWarnings("deprecation")
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                javax.swing.text.Document doc = ta.getDocument();
                Element root = doc.getDefaultRootElement();

                // clamp caret pos into a safe offset (doc.getLength() peut être 0)
                int lastOffset = Math.max(0, doc.getLength() - 1);
                int caretPos = Math.max(0, Math.min(ta.getCaretPosition(), lastOffset));
                int curr = root.getElementIndex(caretPos);

                int target = curr + 1;
                // skip empty paragraphs
                while (target < root.getElementCount()) {
                    Element el = root.getElement(target);
                    int start = el.getStartOffset();
                    int end = Math.min(el.getEndOffset(), doc.getLength());
                    try {
                        String txt = doc.getText(start, Math.max(0, end - start)).trim();
                        if (!txt.isEmpty()) break;
                    } catch (BadLocationException ex) { break; }
                    target++;
                }
                if (target >= root.getElementCount()) return; // rien à faire

                Element targetEl = root.getElement(target);
                int newPos = Math.max(0, Math.min(targetEl.getStartOffset(), doc.getLength()));
                ta.setCaretPosition(newPos);
                try { ta.getHighlighter().removeAllHighlights(); } catch (Exception ignore) {}
                // scroll to make caret visible
                try {
                    Rectangle r = ta.modelToView(newPos);
                    if (r != null) ta.scrollRectToVisible(r);
                } catch (BadLocationException ignore) {}
            }
        });

        am.put("jumpPrevParagraph", new AbstractAction() {
            @SuppressWarnings("deprecation")
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                javax.swing.text.Document doc = ta.getDocument();
                Element root = doc.getDefaultRootElement();

                // clamp caret pos into a safe offset
                int lastOffset = Math.max(0, doc.getLength() - 1);
                int caretPos = Math.max(0, Math.min(ta.getCaretPosition(), lastOffset));
                int curr = root.getElementIndex(caretPos);

                int target = curr - 1;
                // skip empty paragraphs (descend vers le précédent non vide)
                while (target >= 0) {
                    Element el = root.getElement(target);
                    int start = el.getStartOffset();
                    int end = Math.min(el.getEndOffset(), doc.getLength());
                    try {
                        String txt = doc.getText(start, Math.max(0, end - start)).trim();
                        if (!txt.isEmpty()) break;
                    } catch (BadLocationException ex) { break; }
                    target--;
                }
                if (target < 0) return; // pas de précédent

                Element targetEl = root.getElement(target);
                int newPos = Math.max(0, Math.min(targetEl.getStartOffset(), doc.getLength()));
                ta.setCaretPosition(newPos);
                try { ta.getHighlighter().removeAllHighlights(); } catch (Exception ignore) {}
                try {
                    Rectangle r = ta.modelToView(newPos);
                    if (r != null) ta.scrollRectToVisible(r);
                } catch (BadLocationException ignore) {}
            }
        });
    }

    
    
    
}
