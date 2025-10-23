package writer;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import java.awt.*;
import javax.swing.text.*;

public final class ParagraphHighlighter {

    // Peinture bleu clair (modifiable si tu veux une autre teinte)
    private static final Highlighter.HighlightPainter PARAGRAPH_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(Color.BLACK); // light blue Color(173, 216, 230)

    // État interne
    private static Object lastHighlightTag = null;
    private static int lastStart = -1;
    private static int lastEnd = -1;

    private ParagraphHighlighter() {}

    /**
     * - Active le surlignage automatique du paragraphe courant dans un JTextArea.
     * - Surligne en Gris plus clair le paragraphe où se trouve le caret.
     * - Retire le surlignage dès que le caret passe dans un autre paragraphe.
     * - Retire le surlignage quand l'éditeur perd le focus.
     */
    public static void install(JTextArea editorPane) {
        if (editorPane == null) return;

        // 1) Met en place un CaretListener pour mettre à jour le surlignage à chaque mouvement du caret.
        CaretListener cl = new CaretListener() {
            @Override
            public void caretUpdate(CaretEvent e) {
                highlightCurrentParagraph(editorPane);
            }
        };
        editorPane.addCaretListener(cl);

        // 2) Nettoie le surlignage à la perte de focus (facultatif mais plus propre)
        editorPane.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                clearHighlight(editorPane);
            }
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                highlightCurrentParagraph(editorPane);
            }
        });

        // 3) Premier passage : surligner le paragraphe courant
        SwingUtilities.invokeLater(() -> highlightCurrentParagraph(editorPane));
    }

    /** Retire le comportement et nettoie le surlignage (si tu veux le désactiver proprement). */
    public static void uninstall(JTextArea editorPane) {
        if (editorPane == null) return;
        clearHighlight(editorPane);
        // Si tu avais gardé des références aux listeners ajoutés, tu pourrais les retirer ici.
        // (Ici on a laissé simple : tu peux recréer l'éditeur ou ignorer uninstall si non nécessaire.)
    }

    /** Calcule et surligne le paragraphe courant. */
    private static void highlightCurrentParagraph(JTextArea editorPane) {
        try {
            String text = editorPane.getText();
            if (text == null || text.isEmpty()) {
                clearHighlight(editorPane);
                return;
            }

            int caret = editorPane.getCaretPosition();
            caret = Math.max(0, Math.min(caret, text.length()));

            // Début de paragraphe : juste après le dernier séparateur de ligne avant le caret
            int start = lastIndexOfLineBreak(text, Math.max(0, caret - 1)) + 1;

            // Fin de paragraphe : au prochain séparateur de ligne après le caret (ou fin de texte)
            int end = nextIndexOfLineBreak(text, caret);
            if (end == -1) end = text.length();

            // Si le paragraphe est le même que précédemment, ne rien refaire
            if (start == lastStart && end == lastEnd) return;

            // Remettre à zéro et surligner le nouveau paragraphe
            Highlighter hl = editorPane.getHighlighter();
            if (lastHighlightTag != null) {
                hl.removeHighlight(lastHighlightTag);
                lastHighlightTag = null;
            }

            if (start < end) {
                lastHighlightTag = hl.addHighlight(start, end, PARAGRAPH_PAINTER);
                lastStart = start;
                lastEnd = end;
            } else {
                // Paragraphe vide / indices incohérents
                clearHighlight(editorPane);
            }
        } catch (BadLocationException ex) {
            clearHighlight(editorPane);
        }
    }

    /** Retire le surlignage actuel et réinitialise l’état. */
    private static void clearHighlight(JTextArea editorPane) {
        Highlighter hl = editorPane.getHighlighter();
        if (lastHighlightTag != null) {
            hl.removeHighlight(lastHighlightTag);
            lastHighlightTag = null;
        }
        lastStart = -1;
        lastEnd = -1;
    }

    /** Cherche le dernier saut de ligne (\n ou \r) avant ou à 'from'. Retourne -1 si aucun. */
    private static int lastIndexOfLineBreak(String text, int from) {
        int iN = text.lastIndexOf('\n', from);
        int iR = text.lastIndexOf('\r', from);
        return Math.max(iN, iR);
    }

    /** Cherche le premier saut de ligne (\n ou \r) à partir de 'from'. Retourne -1 si aucun. */
    private static int nextIndexOfLineBreak(String text, int from) {
        int iN = text.indexOf('\n', from);
        int iR = text.indexOf('\r', from);
        if (iN == -1) return iR;
        if (iR == -1) return iN;
        return Math.min(iN, iR);
    }
}

