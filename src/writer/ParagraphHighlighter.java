package writer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

public final class ParagraphHighlighter {

    private static final Color PARAGRAPH_COLOR = new Color(0, 0, 0, 60);

    private static Object lastHighlightTag = null;
    private static int lastStart = -1;
    private static int lastEnd = -1;

    private ParagraphHighlighter() {}

    public static void install(JTextPane editorPane) {
        if (editorPane == null) return;

        CaretListener cl = e -> SwingUtilities.invokeLater(() -> highlightCurrentParagraph(editorPane));
        editorPane.addCaretListener(cl);

        editorPane.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { clearHighlight(editorPane); }
            @Override public void focusGained(java.awt.event.FocusEvent e) { highlightCurrentParagraph(editorPane); }
        });

        SwingUtilities.invokeLater(() -> highlightCurrentParagraph(editorPane));
    }

    public static void uninstall(JTextPane editorPane) {
        if (editorPane == null) return;
        clearHighlight(editorPane);
    }

    /** Calcule et surligne le paragraphe courant avec précision, sans décalage vertical. */
    private static void highlightCurrentParagraph(JTextPane editorPane) {
        try {
            if (editorPane == null) return;

            javax.swing.text.Document doc = editorPane.getDocument();
            if (doc.getLength() == 0) {
                clearHighlight(editorPane);
                return;
            }

            int caret = editorPane.getCaretPosition();
            javax.swing.text.Element para = javax.swing.text.Utilities.getParagraphElement(editorPane, caret);
            if (para == null) {
                clearHighlight(editorPane);
                return;
            }

            int start = para.getStartOffset();
            int end = Math.max(start, para.getEndOffset() - 1); // évite d’inclure le \n

            if (start == lastStart && end == lastEnd) return;

            Highlighter hl = editorPane.getHighlighter();

            if (lastHighlightTag != null) {
                hl.removeHighlight(lastHighlightTag);
                lastHighlightTag = null;
            }

            if (start < end) {
                Highlighter.HighlightPainter painter = new ParagraphPainter(PARAGRAPH_COLOR);
                lastHighlightTag = hl.addHighlight(start, end, painter);
                lastStart = start;
                lastEnd = end;
            } else {
                clearHighlight(editorPane);
            }
        } catch (BadLocationException ex) {
            clearHighlight(editorPane);
        }
    }

    /** Retire le surlignage actuel. */
    private static void clearHighlight(JTextPane editorPane) {
        Highlighter hl = editorPane.getHighlighter();
        if (lastHighlightTag != null) {
            hl.removeHighlight(lastHighlightTag);
            lastHighlightTag = null;
        }
        lastStart = -1;
        lastEnd = -1;
    }

    /** ✅ Peint chaque ligne du paragraphe sur toute la largeur, sans décalage */
    /** 🎨 Surlignage adaptatif avec dégradé vertical doux */
    private static class ParagraphPainter extends DefaultHighlighter.DefaultHighlightPainter {
        ParagraphPainter(Color unused) { super(unused); }

        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            if (bounds == null) return;

            java.awt.Rectangle r = bounds.getBounds();

            // --- Détection automatique du thème ---
            Color bg = c.getBackground();
            int brightness = (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3;
            boolean darkTheme = brightness < 128;

            // --- Couleur principale (selon le thème) ---
            Color baseColor = darkTheme
                    ? new Color(255, 255, 255, 30) // clair sur fond sombre
                    : new Color(0, 0, 0, 25);      // foncé sur fond clair

            // --- Dégradé vertical (haut plus transparent que bas) ---
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
            java.awt.GradientPaint grad = new java.awt.GradientPaint(
                    0, r.y, new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 0),
                    0, r.y + r.height, baseColor
            );

            g2.setPaint(grad);
            g2.fillRect(0, r.y, c.getWidth(), r.height);
        }
    }

}
