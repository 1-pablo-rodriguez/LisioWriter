package writer.ui.editor;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;

@SuppressWarnings("serial")
public final class DeleteNextWordAction extends AbstractAction {
    private final JTextComponent comp;

    public DeleteNextWordAction(JTextComponent comp) {
        super("bw-delete-next-word");
        this.comp = comp;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            final Document doc = comp.getDocument();

            // 1) S'il y a une sélection, on la supprime
            int selStart = comp.getSelectionStart();
            int selEnd   = comp.getSelectionEnd();
            if (selStart != selEnd) {
                doc.remove(selStart, selEnd - selStart);
                return;
            }

            // 2) Sinon, on supprime vers l'avant jusqu'à la fin du mot (et espaces qui suivent)
            int pos = comp.getCaretPosition();
            if (pos >= doc.getLength()) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            // D'abord, tente la fin du mot courant
            int end = Utilities.getWordEnd(comp, pos);

            // Si on est déjà en fin de mot (ou dans des blancs), saute au prochain mot
            if (end == pos) {
                int next = Utilities.getNextWord(comp, pos);
                end = Math.max(end, next);
            }

            // Optionnel : avale les espaces (et tab/retours) qui suivent le mot
            int to = end;
            int len = doc.getLength();
            // On lit par petits blocs pour éviter de charger tout le doc
            while (to < len) {
                String s = doc.getText(to, Math.min(64, len - to));
                int i = 0;
                while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
                to += i;
                if (i < s.length()) break; // tombé sur un non-espace
            }

            // Supprime du caret jusqu'au point calculé
            if (to > pos) doc.remove(pos, to - pos);
            else Toolkit.getDefaultToolkit().beep();

        } catch (Exception ex) {
            Toolkit.getDefaultToolkit().beep();
        }
    }
}