package writer.ui.editor;

import java.awt.event.ActionEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

/**
 * Fenêtre permettant de se répère dans le texte.
 * @author pabr6
 *
 */
@SuppressWarnings("serial")
public final class AnnouncePositionAction extends AbstractAction implements Action {
    private final JTextComponent editor;

    // Motif "#<niveau>. <texte>"
    private static final Pattern HEADING_PATTERN =
            Pattern.compile("^#([1-6])\\.\\s+(.+?)\\s*$");

    // Motif pour compter les mots : lettres + éventuellement ' ’ ou - reliés (gère l'enfant, porte-feuille, etc.)
    private static final Pattern WORD_PATTERN =
            Pattern.compile("\\p{L}+(?:['’\\-]\\p{L}+)*", Pattern.UNICODE_CHARACTER_CLASS);

    public AnnouncePositionAction(JTextComponent editor) {
        super("Position dans le texte");
        this.editor = editor;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final javax.swing.text.Document doc = editor.getDocument();
        final int caretPara = safeParagraphIndexAt(doc, editor.getCaretPosition());

        final HeadingFound above = findEnclosingHeading();    // premier titre au-dessus
        final HeadingFound below = findNextHeadingStrictlyBelow(); // VRAI suivant

        // calcul pourcentage de lecture (mots)
        int totalWords = 0;
        int wordsBefore = 0;
        try {
            String full = doc.getText(0, doc.getLength());
            totalWords = countWordsIn(full);
            int caretPos = Math.max(0, Math.min(editor.getCaretPosition(), full.length()));
            if (caretPos > 0 && totalWords > 0) {
                String before = full.substring(0, caretPos);
                wordsBefore = countWordsIn(before);
            }
        } catch (Exception ex) {
            // ignore - si erreur on laisse counts à 0
        }

        
        char p = '\u00B6';
        
        double pct = (totalWords == 0) ? 0.0 : (100.0 * wordsBefore / totalWords);

        StringBuilder msg = new StringBuilder(128);
        msg.append(String.format("Lecture réalisé : %.1f%% (%d / %d mots).",
                Math.rint(pct * 10.0) / 10.0, wordsBefore, totalWords) + " ↓\n");
        msg.append(formatHeadingLine("Titre au-dessus : ", above)).append(" ↓\n");
        msg.append(formatHeadingLine("Titre suivant : ", below)).append(" ↓\n");
        msg.append("Curseur dans le ").append(p).append(" : ").append(caretPara);

        // ligne pourcentage
        

        java.awt.Window owner = SwingUtilities.getWindowAncestor(editor);
        dia.InfoDialog.show(owner, "Position dans le texte", msg.toString());
    }

    // ---------- Helpers locaux ----------
    private static int safeParagraphIndexAt(javax.swing.text.Document doc, int pos) {
        try { return paragraphIndexAt(doc, pos); } catch (Exception ex) { return -1; }
    }
    private static int paragraphIndexAt(javax.swing.text.Document doc, int offset) {
        Element root = doc.getDefaultRootElement();
        int idx = root.getElementIndex(Math.max(0, Math.min(offset, doc.getLength())));
        return idx + 1; // 1-based
    }

    static final class HeadingFound {
        final String levelLabel;
        final String text;
        final int paraIndex;
        HeadingFound(String levelLabel, String text, int paraIndex) {
            this.levelLabel = levelLabel; this.text = text; this.paraIndex = paraIndex;
        }
    }

    /** Premier titre AU-DESSUS du caret (ignore la ligne courante). */
    private HeadingFound findEnclosingHeading() {
        try {
            javax.swing.text.Document doc = editor.getDocument();
            Element root = doc.getDefaultRootElement();

            int caret = Math.max(0, editor.getCaretPosition() - 1); // ignore la ligne courante
            int lineIdx = Math.max(0, root.getElementIndex(caret)) - 1;

            for (int i = lineIdx; i >= 0; i--) {
                String line = lineText(doc, root.getElement(i));
                var m = HEADING_PATTERN.matcher(line);
                if (m.matches()) {
                    int lvl = Integer.parseInt(m.group(1));
                    String tx = m.group(2).trim();
                    return new HeadingFound("#" + lvl+". ", tx, i + 1);
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    /** VRAI titre EN-DESSOUS : si le caret est sur un titre, on saute ce paragraphe. */
    private HeadingFound findNextHeadingStrictlyBelow() {
        try {
            javax.swing.text.Document doc = editor.getDocument();
            Element root = doc.getDefaultRootElement();

            int caret = Math.max(0, editor.getCaretPosition());
            int currIdx = Math.max(0, root.getElementIndex(caret));

            // Si la ligne courante est un titre, on commence la recherche APRÈS
            int startIdx = currIdx;
            String currentLine = lineText(doc, root.getElement(currIdx));
            if (HEADING_PATTERN.matcher(currentLine).matches()) {
                startIdx = currIdx + 1;
            }

            for (int i = startIdx; i < root.getElementCount(); i++) {
                String line = lineText(doc, root.getElement(i));
                var m = HEADING_PATTERN.matcher(line);
                if (m.matches()) {
                    int lvl = Integer.parseInt(m.group(1));
                    String tx = m.group(2).trim();
                    return new HeadingFound("#" + lvl + ". ", tx, i + 1);
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static String lineText(javax.swing.text.Document doc, Element lineEl) throws Exception {
        int start = lineEl.getStartOffset();
        int end   = Math.min(lineEl.getEndOffset(), doc.getLength());
        return doc.getText(start, end - start).replaceAll("\\R$", "");
    }

    private String formatHeadingLine(String prefix, HeadingFound h) {
        if (h == null) return prefix + "Aucun titre détecté.";
        char p = '\u00B6';
        return String.format("%s%s %s ("+p+" %d)", prefix, h.levelLabel, h.text, h.paraIndex);
    }

    // ---------- comptage de mots ----------
    private static int countWordsIn(String s) {
        if (s == null || s.isBlank()) return 0;
        Matcher m = WORD_PATTERN.matcher(s);
        int c = 0;
        while (m.find()) c++;
        return c;
    }
}
