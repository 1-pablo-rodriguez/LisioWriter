package writer.editor;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Utilities;

/**
 * Action Swing : insère ou retire le préfixe "-. " au début du paragraphe courant.
 * <p>
 * - Si la ligne commence par "-. ", le supprime (toggle OFF)
 * - Si la ligne commence par "n. ", remplace par "-. " (toggle type)
 * - Sinon, insère "-. " après l'indentation existante
 */
public final class InsertUnorderedBulletAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private final writer.ui.NormalizingTextPane editor;

    public InsertUnorderedBulletAction(writer.ui.NormalizingTextPane editor) {
        super("Liste non numérotée");
        this.editor = editor;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Document doc = editor.getDocument();
            int caret = editor.getCaretPosition();

            int lineStart = Utilities.getRowStart(editor, caret);
            int lineEnd   = Utilities.getRowEnd(editor, caret);
            if (lineStart < 0 || lineEnd < lineStart) return;

            String line = doc.getText(lineStart, lineEnd - lineStart);

            // --- Calcul de l'indentation ---
            int i = 0;
            while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
            int insertPos = lineStart + i;

            // --- Cas 1 : déjà en liste non numérotée => toggle OFF ---
            if (line.startsWith("-. ", i)) {
                doc.remove(insertPos, 3);
                if (caret >= insertPos) editor.setCaretPosition(Math.max(lineStart, caret - 3));
                return;
            }

            // --- Cas 2 : ligne numérotée "n. " => convertir en "-. " ---
            int j = i;
            while (j < line.length() && Character.isDigit(line.charAt(j))) j++;
            if (j > i && j + 2 <= line.length()
                    && line.charAt(j) == '.'
                    && line.charAt(j + 1) == ' ') {
                int removeLen = (j + 2) - i; // longueur de "n. "
                doc.remove(insertPos, removeLen);
                doc.insertString(insertPos, "-. ", null);

                if (caret >= insertPos) {
                    int delta = "-. ".length() - removeLen;
                    editor.setCaretPosition(Math.max(lineStart, caret + delta));
                }
                return;
            }

            // --- Cas 3 : ligne normale => insérer "-. " après indentation ---
            doc.insertString(insertPos, "-. ", null);
            if (caret >= insertPos) editor.setCaretPosition(caret + 3);

        } catch (BadLocationException ex) {
            // Silencieux en cas d’erreur minime (ex. offset invalide pendant la saisie)
        }
    }
}
