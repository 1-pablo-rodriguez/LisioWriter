package writer.ui.editor;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.text.Document;
import javax.swing.text.Utilities;

@SuppressWarnings("serial")
public final class DeletePrevWordAction extends AbstractAction {
    private final writer.ui.NormalizingTextPane comp;

    public DeletePrevWordAction(writer.ui.NormalizingTextPane comp) {
        super("bw-delete-prev-word");
        this.comp = comp;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            final Document doc = comp.getDocument();

            // 1) S'il y a une sélection : on supprime la sélection
            int selStart = comp.getSelectionStart();
            int selEnd   = comp.getSelectionEnd();
            if (selStart != selEnd) {
                doc.remove(selStart, selEnd - selStart);
                return;
            }

            // 2) Sinon : supprime le mot à gauche (et les espaces juste avant le caret)
            int pos = comp.getCaretPosition();
            if (pos <= 0) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            // Reculer d'abord à travers les espaces qui précèdent le caret
            int i = pos;
            while (i > 0) {
                char ch = doc.getText(i - 1, 1).charAt(0);
                if (Character.isWhitespace(ch)) i--;
                else break;
            }

            // Trouver le début du mot précédent (depuis i)
            int start = Utilities.getWordStart(comp, i);
            // Si on est déjà en début de mot, recule au mot précédent
            if (start == i) {
                int prev = Utilities.getPreviousWord(comp, i);
                start = Math.min(start, prev);
            }

            // Supprimer de 'start' jusqu'à la position initiale du caret
            if (start < pos) doc.remove(start, pos - start);
            else Toolkit.getDefaultToolkit().beep();

        } catch (Exception ex) {
            Toolkit.getDefaultToolkit().beep();
        }
    }
}