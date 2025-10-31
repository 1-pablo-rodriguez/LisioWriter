package writer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

public final class ParagraphHighlighter {

    // Peinture : fond sombre semi-transparent (tu peux changer la couleur)
    private static final Color PARAGRAPH_COLOR = new Color(0, 0, 0, 60);

    // État interne
    private static Object lastHighlightTag = null;
    private static int lastStart = -1;
    private static int lastEnd = -1;

    private ParagraphHighlighter() {}

    public static void install(JTextPane editorPane) {
        if (editorPane == null) return;

        // --- CaretListener pour actualiser le surlignage ---
        CaretListener cl = e -> highlightCurrentParagraph(editorPane);
        editorPane.addCaretListener(cl);

        // --- Nettoyage à la perte de focus ---
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

    /** Calcule et surligne le paragraphe courant avec des coordonnées 2D précises. */
    private static void highlightCurrentParagraph(JTextPane editorPane) {
        try {
            String text = editorPane.getText();
            if (text == null || text.isEmpty()) {
                clearHighlight(editorPane);
                return;
            }

            int caret = editorPane.getCaretPosition();
            caret = Math.max(0, Math.min(caret, text.length()));

            // Début / fin du paragraphe
            int start = lastIndexOfLineBreak(text, Math.max(0, caret - 1)) + 1;
            int end = nextIndexOfLineBreak(text, caret);
            if (end == -1) end = text.length();

            if (start == lastStart && end == lastEnd) return;

            // Supprimer le surlignage précédent
            Highlighter hl = editorPane.getHighlighter();
            if (lastHighlightTag != null) {
                hl.removeHighlight(lastHighlightTag);
                lastHighlightTag = null;
            }

            if (start < end) {
                // ✅ Crée un painter personnalisé
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

    /** Cherche le dernier saut de ligne avant ou à 'from'. Gère \r\n correctement. */
    private static int lastIndexOfLineBreak(String text, int from) {
        int idx = -1;
        for (int i = from; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                idx = i;
                // si c’est un \n précédé d’un \r, saute les deux
                if (i > 0 && text.charAt(i - 1) == '\r') idx = i - 1;
                break;
            }
        }
        return idx;
    }

    /** Cherche le premier saut de ligne à partir de 'from'. Gère \r\n correctement. */
    private static int nextIndexOfLineBreak(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                // si c’est un \r suivi d’un \n, saute les deux
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') return i + 1;
                return i;
            }
        }
        return -1;
    }


    /** ✅ Peint le fond du paragraphe sur toute la largeur avec modelToView2D */
    private static class ParagraphPainter extends DefaultHighlighter.DefaultHighlightPainter {
        private final Color color;
        ParagraphPainter(Color c) { super(c); this.color = c; }

        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                Rectangle2D r0 = c.modelToView2D(p0);
                Rectangle2D r1 = c.modelToView2D(p1);
                if (r0 == null || r1 == null) return;

                double y = r0.getY();
                double height = (r1.getY() + r1.getHeight()) - r0.getY();
                double width = c.getWidth();

                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(color);
                g2.fill(new Rectangle2D.Double(0, y, width, height));

            } catch (BadLocationException ex) {
                // ignore
            }
        }
    }
}
