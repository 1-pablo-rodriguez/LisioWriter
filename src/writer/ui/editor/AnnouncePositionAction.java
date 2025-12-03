package writer.ui.editor;

import java.awt.event.ActionEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import javax.swing.text.Element;

import writer.ui.NormalizingTextPane;
import writer.util.WordCounter;

/**
 * Fenêtre permettant de se repérer dans le texte.
 * Gère les titres qui peuvent être préfixés par le caractère braille U+283F
 * ou par des espaces en début de ligne.
 */
@SuppressWarnings("serial")
public final class AnnouncePositionAction extends AbstractAction implements Action {
    private final NormalizingTextPane editor;

    // Motif "#<niveau>. <texte>" (appliqué SUR LA LIGNE NETTOYÉE)
    private static final Pattern HEADING_PATTERN =
            Pattern.compile("^#([1-6])\\.\\s+(.+?)\\s*$");

    public AnnouncePositionAction(writer.ui.NormalizingTextPane editor) {
        super("Position dans le texte");
        this.editor = (NormalizingTextPane) editor;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final javax.swing.text.Document doc = editor.getDocument();
        final int caretPara = safeParagraphIndexAt(doc, editor.getCaretPosition());

        final HeadingFound above = findEnclosingHeading();         // premier titre au-dessus
        final HeadingFound below = findNextHeadingStrictlyBelow(); // vrai suivant

        // --- 1) Calcul du pourcentage global (déjà existant) ---
        int totalWords = 0;
        int wordsBefore = 0;
        try {
            String full = doc.getText(0, doc.getLength());
            totalWords = WordCounter.countWords(full);
            int caretPos = Math.max(0, Math.min(editor.getCaretPosition(), full.length()));
            if (caretPos > 0 && totalWords > 0) {
                String before = full.substring(0, caretPos);
                wordsBefore = WordCounter.countWords(before);
            }
        } catch (Exception ex) {
            // ignore - si erreur on laisse counts à 0
        }

        double pctGlobal = (totalWords == 0) ? 0.0 : (100.0 * wordsBefore / totalWords);
        pctGlobal = Math.rint(pctGlobal * 10.0) / 10.0;

        // --- 2) Calcul du pourcentage depuis le titre au-dessus ---
        int sectionWordsTotal = 0;
        int sectionWordsBefore = 0;
        double pctSection = 0.0;

        try {
            if (above != null) {
                Element root = doc.getDefaultRootElement();

                // paragraphe du titre au-dessus
                int aboveIdx = Math.max(0, above.paraIndex - 1);
                if (aboveIdx < root.getElementCount()) {
                    Element aboveEl = root.getElement(aboveIdx);
                    int startOffset = aboveEl.getStartOffset();

                    // fin de la section = début du titre suivant, ou fin du doc
                    int endOffset;
                    if (below != null) {
                        int belowIdx = Math.max(0, below.paraIndex - 1);
                        if (belowIdx < root.getElementCount()) {
                            Element belowEl = root.getElement(belowIdx);
                            endOffset = belowEl.getStartOffset();
                        } else {
                            endOffset = doc.getLength();
                        }
                    } else {
                        endOffset = doc.getLength();
                    }

                    endOffset = Math.max(startOffset, Math.min(endOffset, doc.getLength()));

                    if (endOffset > startOffset) {
                        String sectionText = doc.getText(startOffset, endOffset - startOffset);
                        sectionWordsTotal = WordCounter.countWords(sectionText);

                        int caret = Math.max(startOffset,
                                Math.min(editor.getCaretPosition(), endOffset));
                        if (caret > startOffset && sectionWordsTotal > 0) {
                            String beforeSection = doc.getText(startOffset, caret - startOffset);
                            sectionWordsBefore = WordCounter.countWords(beforeSection);
                            pctSection = 100.0 * sectionWordsBefore / sectionWordsTotal;
                            pctSection = Math.rint(pctSection * 10.0) / 10.0;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // en cas de souci, on laisse pctSection à 0 et on n'affichera rien
            sectionWordsTotal = 0;
            sectionWordsBefore = 0;
        }

        String c = "INFO. ";
        StringBuilder msg = new StringBuilder(256);

        // ligne 1 : pourcentage global
        msg.append(String.format("Lecture réalisée : %.1f%% (%d / %d mots).",
                pctGlobal, wordsBefore, totalWords)).append(" ↓\n");

        // ligne 2 : paragraphe courant
        msg.append(c).append("Curseur dans le paragraphe ").append(caretPara).append(" ↓\n");

        // ligne 3 : titre au-dessus
        msg.append(c).append(formatHeadingLine("Titre au-dessus : ", above)).append(" ↓\n");

        // nouvelle ligne : pourcentage depuis le titre au-dessus (si dispo)
        if (above != null && sectionWordsTotal > 0) {
            msg.append(c).append(String.format(
                    "Lecture depuis ce titre : %.1f%% (%d / %d mots).",
                    pctSection, sectionWordsBefore, sectionWordsTotal
            )).append(" ↓\n");
        }

        // ligne 4 : titre suivant
        msg.append(c).append(formatHeadingLine("Titre suivant : ", below)).append(" ↓\n");

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
            int lineIdx = root.getElementIndex(Math.max(0, Math.min(caret, doc.getLength() - 1))) - 1;

            for (int i = lineIdx; i >= 0; i--) {
                String line = lineText(doc, root.getElement(i));
                String cleaned = cleanLeadingPiedDeMoucheAndSpaces(line);
                Matcher m = HEADING_PATTERN.matcher(cleaned);
                if (m.matches()) {
                    int lvl = Integer.parseInt(m.group(1));
                    String tx = m.group(2).trim();
                    return new HeadingFound("#" + lvl + ". ", tx, i + 1);
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
            int currIdx = Math.max(0, root.getElementIndex(Math.min(caret, doc.getLength() - 1)));

            // Si la ligne courante est un titre, on commence la recherche APRÈS
            int startIdx = currIdx;
            String currentLine = lineText(doc, root.getElement(currIdx));
            String currentClean = cleanLeadingPiedDeMoucheAndSpaces(currentLine);
            if (HEADING_PATTERN.matcher(currentClean).matches()) {
                startIdx = currIdx + 1;
            }

            for (int i = startIdx; i < root.getElementCount(); i++) {
                String line = lineText(doc, root.getElement(i));
                String cleaned = cleanLeadingPiedDeMoucheAndSpaces(line);
                Matcher m = HEADING_PATTERN.matcher(cleaned);
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
        String s = doc.getText(start, Math.max(0, end - start));
        // enlever le terminator de ligne éventuel à la fin
        return s.replaceAll("\\R$", "");
    }

    /**
     * Nettoie uniquement le préfixe : supprime espaces initiaux ET le caractère braille
     * s'il est présent (ex: "   ¶ #1. Titre" => "#1. Titre").
     *
     * Ne touche pas aux caractères braille qui se trouvent au milieu de la ligne.
     */
    private static String cleanLeadingPiedDeMoucheAndSpaces(String line) {
        if (line == null) return "";
        // supprime espaces initiaux puis éventuel U+00B6 et espaces qui suivent
        return line.replaceFirst("(?m)^\\s*(?:\\u00B6\\s*)?", "");
    }

    private String formatHeadingLine(String prefix, HeadingFound h) {
        if (h == null) return prefix + "Aucun titre détecté.";
        return String.format("%s%s %s (paragraphe %d)", prefix, h.levelLabel, h.text, h.paraIndex);
    }
}
