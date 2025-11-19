package writer.ui.editor;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;

/**
 * Action Entrée "braille" :
 * - Position 0 : insère "¶\n" en tête du document. N'ajoute un ¶ au début de la ligne suivante
 *   QUE si le document ne démarrait pas déjà par ¶.
 * - Cas spécial : si le caret est exactement entre une fin de ligne et un ¶,
 *   insère "¶" au début de la ligne courante puis le séparateur d'origine,
 *   ce qui pousse le ¶ existant au début du paragraphe suivant, en préservant \r\n si présent.
 * - Cas général : insère "\n¶" (ou "\n¶ ") à la position du caret.
 */
@SuppressWarnings("serial")
public class EnterBrailleInsertAction2 extends AbstractAction {
    private static final char   BRAILLE_CH = '\u00B6';
    private static final String BRAILLE    = String.valueOf(BRAILLE_CH);

    private final writer.ui.NormalizingTextPane editor;
    private final Action fallback;
    private final String insert; // "\n¶" ou "\n¶ "

    /**
     * @param editor composant cible (writer.ui.NormalizingTextPane)
     * @param fallback action de repli si insertion échoue (peut être null)
     * @param withTrailingSpace true pour insérer "\n¶ " au lieu de "\n¶"
     */
    public EnterBrailleInsertAction2(writer.ui.NormalizingTextPane editor, Action fallback, boolean withTrailingSpace) {
        this.editor = editor;
        this.fallback = fallback;
        this.insert = withTrailingSpace ? "\n" + BRAILLE + " " : "\n" + BRAILLE;
        putValue(NAME, "BrailleInsert");
    }

    /** Constructeur pratique sans espace et sans fallback */
    public EnterBrailleInsertAction2(writer.ui.NormalizingTextPane editor) {
        this(editor, null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Document doc = editor.getDocument();
            int caret = editor.getCaretPosition();

            // 1) Si une sélection existe, la supprimer d'abord
            int selStart = editor.getSelectionStart();
            int selEnd   = editor.getSelectionEnd();
            if (selStart != selEnd) {
                doc.remove(selStart, selEnd - selStart);
                caret = selStart;
            }

            // 2) Position 0
            if (caret == 0) {
                final boolean withSpace = insert.endsWith(" ");
                final String head = withSpace ? BRAILLE + " " : BRAILLE;

                // Le doc commençait-il déjà par ¶ ?
                boolean hadLeadingBraille = false;
                if (doc.getLength() > 0) {
                    char first = doc.getText(0, 1).charAt(0);
                    hadLeadingBraille = (first == BRAILLE_CH);
                }

                // Insère "¶\n" en tête
                doc.insertString(0, head + "\n", null);

                // Si le doc ne commençait PAS par ¶, on ajoute ¶ (et espace éventuel) au début de la 2e ligne
                if (!hadLeadingBraille) {
                    int secondLineStart = head.length() + 1; // après "¶[ ]\n"
                    doc.insertString(secondLineStart, head, null);
                }

                // Place le caret après le ¶ (et l'espace éventuel) de la 1re ligne
                editor.setCaretPosition(head.length());
                return;
            }

            // 3) Cas spécial : caret entre fin de ligne et ¶
            if (handleCaretBetweenEOLAndBraille(doc, caret)) {
                return;
            }

            // 4) Cas général : insère l'empreinte ("\n¶" ou "\n¶ ")
            doc.insertString(caret, insert, null);
            editor.setCaretPosition(Math.min(doc.getLength(), caret + insert.length()));

        } catch (BadLocationException ex) {
            if (fallback != null) {
                fallback.actionPerformed(e);
            }
        }
    }

    /**
     * Gère le cas "caret entre fin de ligne et ¶".
     * Exemple : "...\\n¶Texte" (caret juste entre \\n et ¶).
     * Effet : insère "¶[ ]" + sep (sep = "\n" ou "\r\n") avant le ¶ existant,
     *         ce qui pousse ce ¶ au début du paragraphe suivant.
     *
     * @return true si traité, false sinon.
     */
    private boolean handleCaretBetweenEOLAndBraille(Document doc, int caret) throws BadLocationException {
        if (caret <= 0 || caret >= doc.getLength()) return false;

        char prev = doc.getText(caret - 1, 1).charAt(0); // char précédent le caret
        char next = doc.getText(caret, 1).charAt(0);     // char suivant le caret

        final boolean withSpace = insert.endsWith(" ");
        final String head = withSpace ? BRAILLE + " " : BRAILLE;

        // --- Cas "\n¶" (Unix) ou "\r\n¶" (Windows) avec caret placé après \n
        if (prev == '\n' && next == BRAILLE_CH) {
            String sep = "\n";
            // Si juste avant \n il y a \r, on est en CRLF
            if (caret >= 2) {
                String maybeCRLF = doc.getText(caret - 2, 2);
                if ("\r\n".equals(maybeCRLF)) sep = "\r\n";
            }
            doc.insertString(caret, head + sep, null);
            editor.setCaretPosition(Math.min(doc.getLength(), caret + head.length()));
            return true;
        }

        // --- Variante défensive : caret juste après '\r' dans "\r\n¶"
        if (prev == '\r' && caret + 1 < doc.getLength()) {
            String tri = doc.getText(caret - 1, Math.min(3, doc.getLength() - (caret - 1)));
            if (tri.length() == 3 && tri.charAt(0) == '\r' && tri.charAt(1) == '\n' && tri.charAt(2) == BRAILLE_CH) {
                doc.insertString(caret, head + "\r\n", null);
                editor.setCaretPosition(Math.min(doc.getLength(), caret + head.length()));
                return true;
            }
        }

        return false;
    }

    /** Factory : récupère l'action "insertBreak" par défaut pour servir de fallback. */
    public static EnterBrailleInsertAction2 createWithDefaultFallback(writer.ui.NormalizingTextPane editor, boolean withTrailingSpace) {
        Action defaultInsert = editor.getActionMap().get(DefaultEditorKit.insertBreakAction);
        return new EnterBrailleInsertAction2(editor, defaultInsert, withTrailingSpace);
    }
}