package dia;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import act.select_CANCEL;
import act.select_OK;
import writer.commandes;
import writer.ui.EditorFrame;
import writer.ui.NormalizingTextPane;

@SuppressWarnings("serial")
public final class BoiteRenameFile extends JDialog {

    private final JTextField nameField = new JTextField(24);
    private final JLabel status = new JLabel(" "); // zone d’état pour SR/braille
    private final JButton ok = new JButton("OK");
    private final JButton cancel = new JButton("Annuler");
    // —— Low-vision prefs (peuvent être lues d'un fichier plus tard)
    private float uiScale = 1.25f;     // 1.0 = normal, 1.25 = +25%, etc.
    private boolean highContrast = true; // thème contraste fort

    // autorise lettres, chiffres, espace, _ - . (souvent utiles dans un nom)
    private static final Pattern ALLOWED = Pattern.compile("[a-zA-Z0-9 _.-]+");

    
    public BoiteRenameFile(EditorFrame parent) {
        super((Frame) null, "Modifier le nom de fichier", true);
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));

        // ---- Bandeau d’information court et utile
        String current = (commandes.nameFile == null) ? "" : commandes.nameFile;
        JLabel intro = new JLabel("Nom actuel : " + current);
        intro.getAccessibleContext().setAccessibleName("Nom actuel " + current);
        add(intro, BorderLayout.NORTH);

        // ---- Centre : étiquette + champ
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel("Nom de fichier :");
        nameLabel.setDisplayedMnemonic(0);                      // Alt+N
        nameLabel.setLabelFor(nameField);

        nameField.setText(current);
        nameField.selectAll();                                    // prêt à renommer
        nameField.getAccessibleContext().setAccessibleName("Nom de fichier");
        nameField.getAccessibleContext().setAccessibleDescription(null);

        // Validation par DocumentFilter (meilleur que KeyListener)
        ((AbstractDocument) nameField.getDocument())
                .setDocumentFilter(new FilteringDocumentFilter());

        // zone d’état lisible par SR/braille (courte)
        status.getAccessibleContext().setAccessibleName(null);
        status.setFocusable(false);

        center.add(nameLabel);
        center.add(Box.createVerticalStrut(6));
        center.add(nameField);
        center.add(Box.createVerticalStrut(8));
        center.add(status);
        add(center, BorderLayout.CENTER);

        // ---- Bas : boutons
        ok.setMnemonic(0);      // Alt+O
        cancel.setMnemonic(0);  // Alt+A

        ok.getAccessibleContext().setAccessibleDescription(null);
        cancel.getAccessibleContext().setAccessibleDescription(null);

        ok.addActionListener(e -> doValidateAndClose());
        cancel.addActionListener(e -> {
            new select_CANCEL().actionPerformed(null); // si vous tenez à conserver ces actions
            dispose();
        });

        JPanel south = new JPanel();
        south.add(ok);
        south.add(cancel);
        add(south, BorderLayout.SOUTH);

        // Entrée = OK
        getRootPane().setDefaultButton(ok);

        // Échap = Annuler
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                new select_CANCEL().actionPerformed(null);
                SwingUtilities.invokeLater(() -> {
                    parent.requestFocus();
                    parent.getEditor().requestFocusInWindow();
                });
                dispose();
            }
        });
        
        applyLowVisionStyling(getContentPane(), uiScale, highContrast);
        installZoomKeyBindings(); // Ctrl+ + / Ctrl+ - / Ctrl+ 0

        // Dimension / placement
        pack();
        setLocationRelativeTo(null);

        // Focus direct dans le champ (NVDA annoncera l’étiquette + contenu)
        SwingUtilities.invokeLater(nameField::requestFocusInWindow);
        setVisible(true);
    }

    private void doValidateAndClose() {
        final String texte = nameField.getText().trim();
        if (texte.isEmpty()) {
            feedbackError("Le nom ne peut pas être vide.");
           
            return;
        }
        // (optionnel) vous pouvez ajouter d'autres règles (taille max, pas d'espace initial, etc.)

        // ok
        new select_OK().actionPerformed(null);
        commandes.nameFile = texte;
        if (commandes.nodeblindWriter != null) {
            commandes.nodeblindWriter.getAttributs().put("filename", texte);
        }
        dispose();
    }

    private void feedbackError(String msg) {
        status.setText(msg);
        Toolkit.getDefaultToolkit().beep();
        // astuce : forcer NVDA à relire le message si identique
        status.getAccessibleContext().setAccessibleName("Message d'état : " + msg);
    }

    /** Filtre qui refuse toute insertion contenant des caractères non autorisés. */
    private static final class FilteringDocumentFilter extends DocumentFilter {
        @Override public void insertString(FilterBypass fb, int offs, String str, AttributeSet a) throws BadLocationException {
            if (isAllowed(str)) super.insertString(fb, offs, str, a);
            else Toolkit.getDefaultToolkit().beep();
        }
        @Override public void replace(FilterBypass fb, int offs, int len, String text, AttributeSet a) throws BadLocationException {
            if (isAllowed(text)) super.replace(fb, offs, len, text, a);
            else Toolkit.getDefaultToolkit().beep();
        }
        private boolean isAllowed(String s) { return s == null || ALLOWED.matcher(s).matches(); }
    }
    
 // ——— Applique taille de police + couleurs + caret épais ———
    private void applyLowVisionStyling(java.awt.Component root, float scale, boolean hc) {
        if (scale < 1f) scale = 1f;

        // 1) Police
        applyFontScaleRec(root, scale);

        // 2) Espacement/padding pour lisibilité
        if (root instanceof JComponent jc) {
            var b = jc.getBorder();
            if (b == null || b instanceof javax.swing.border.EmptyBorder) {
                int pad = Math.round(12 * scale);
                jc.setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
            }
        }

        // 3) Couleurs contraste fort (option)
        if (hc) applyHighContrastColors(root);

        // 4) Caret plus visible dans les zones éditables
        installThickCaret(root);
    }

    // Mise à l’échelle récursive des polices
    private void applyFontScaleRec(java.awt.Component c, float scale) {
        if (c == null) return;
        var f = c.getFont();
        if (f != null) c.setFont(f.deriveFont(f.getSize2D() * scale));

        if (c instanceof java.awt.Container cont) {
            for (var child : cont.getComponents()) applyFontScaleRec(child, scale);
        }
    }

    // Thème contraste fort local (noir/gris foncé sur blanc cassé, ou inverse si L&F sombre)
    private void applyHighContrastColors(java.awt.Component c) {
        java.awt.Color bgLight = new java.awt.Color(0xFFFFFF);
        java.awt.Color fgDark  = new java.awt.Color(0x111111);

        if (c instanceof writer.ui.NormalizingTextPane tc) {
            tc.setBackground(bgLight);
            tc.setForeground(fgDark);
            tc.setCaretColor(fgDark);
            tc.setSelectionColor(new java.awt.Color(0xCCE7FF)); // bleu clair très lisible
            tc.setSelectedTextColor(java.awt.Color.BLACK);
            // bordure focus bien visible
            tc.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(0x0066CC), 2),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
            ));
        } else if (c instanceof JButton jb) {
            jb.setBackground(new java.awt.Color(0xF2F2F2));
            jb.setForeground(fgDark);
            jb.setFocusPainted(true);
            jb.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(0x444444), 2),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        } else if (c instanceof JLabel jl) {
            jl.setForeground(fgDark);
        } else if (c instanceof JComponent jc) {
            jc.setBackground(bgLight);
            jc.setForeground(fgDark);
        }
        if (c instanceof java.awt.Container cont) {
            for (var child : cont.getComponents()) applyHighContrastColors(child);
        }
    }

    // Caret épais et clignotant de façon stable
    private void installThickCaret(java.awt.Component c) {
        if (c instanceof writer.ui.NormalizingTextPane tc) {
            tc.setCaret(new ThickCaret());        // classe interne ci-dessous
            tc.getCaret().setBlinkRate(500);      // 2 Hz
        }
        if (c instanceof java.awt.Container cont) {
            for (var child : cont.getComponents()) installThickCaret(child);
        }
    }

    // Caret épais (2–3 px) très lisible
    static final class ThickCaret extends javax.swing.text.DefaultCaret {
        private static final int WIDTH = 2; // augmente à 3 si besoin
        @Override
        public void paint(java.awt.Graphics g) {
            if (!isVisible()) return;
            try {
            	writer.ui.NormalizingTextPane comp = (NormalizingTextPane) getComponent();
                @SuppressWarnings("deprecation")
				var r = comp.modelToView(getDot());
                if (r == null) return;
                g.setColor(comp.getCaretColor());
                int x = r.x, y = r.y, h = r.height;
                g.fillRect(x, y, WIDTH, h);
            } catch (javax.swing.text.BadLocationException ignore) {}
        }
    }

    // Raccourcis de zoom : Ctrl+ + / Ctrl+ - / Ctrl+ 0
    private void installZoomKeyBindings() {
        var im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var am = getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS,  KeyEvent.CTRL_DOWN_MASK), "zoomIn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD,   KeyEvent.CTRL_DOWN_MASK), "zoomIn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,KeyEvent.CTRL_DOWN_MASK), "zoomIn"); // AZERTY

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK), "zoomOut");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, KeyEvent.CTRL_DOWN_MASK), "zoomOut");

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, KeyEvent.CTRL_DOWN_MASK), "zoomReset");

        am.put("zoomIn", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            uiScale = Math.min(2.0f, uiScale + 0.1f);
            applyLowVisionStyling(getContentPane(), uiScale, highContrast);
            pack();
        }});
        am.put("zoomOut", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            uiScale = Math.max(1.0f, uiScale - 0.1f);
            applyLowVisionStyling(getContentPane(), uiScale, highContrast);
            pack();
        }});
        am.put("zoomReset", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            uiScale = 1.25f;
            applyLowVisionStyling(getContentPane(), uiScale, highContrast);
            pack();
        }});
    }
}
