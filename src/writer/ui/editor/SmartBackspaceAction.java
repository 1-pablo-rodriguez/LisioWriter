package writer.ui.editor;

import java.awt.event.ActionEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * Action Backspace intelligente :
 * - supprime [tab]
 * - supprime "-. " (puce)
 * - supprime "#N. " (titres numérotés)
 * - supprime "#P. " ou "#S. " (titres spéciaux)
 * - sinon comportement standard
 */
@SuppressWarnings("serial")
public class SmartBackspaceAction extends AbstractAction {

    private final JTextComponent editorPane;
    private final javax.swing.Action fallback;

    public SmartBackspaceAction(JTextComponent editorPane, javax.swing.Action fallback) {
        super("bw-smart-backspace");
        this.editorPane = editorPane;
        this.fallback = fallback;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            int selStart = editorPane.getSelectionStart();
            int selEnd   = editorPane.getSelectionEnd();

            // 1️⃣ Supprime une sélection complète
            if (selEnd > selStart) {
                editorPane.replaceSelection("");
                return;
            }

            int pos = editorPane.getCaretPosition();
            Document doc = editorPane.getDocument();

            // 2️⃣ Supprimer un bloc [tab]
            if (pos >= 5) {
                String prev = doc.getText(pos - 5, 5);
                if ("[tab]".equals(prev)) {
                    doc.remove(pos - 5, 5);
                    return;
                }
            }

            // 3️⃣ Début de la ligne actuelle
            int lineStart = javax.swing.text.Utilities.getRowStart(editorPane, pos);
            if (lineStart >= 0) {
                int lineLen = pos - lineStart;
                String prefix = doc.getText(lineStart, Math.min(lineLen, 8));

                // Puce "-. "
                if (prefix.startsWith("-. ")) {
                    doc.remove(lineStart, 3);
                    return;
                }

                // Titre numéroté "#1. ", "#23. ", etc.
                Matcher m = Pattern.compile("^#\\d+\\.\\s").matcher(prefix);
                if (m.find()) {
                    doc.remove(lineStart, m.end());
                    return;
                }

                // Titre spécial "#P. " ou "#S. "
                if (prefix.matches("^#([PS])\\.\\s")) {
                    doc.remove(lineStart, 4);
                    return;
                }
            }

            // 4️⃣ Sinon, backspace normal
            if (fallback != null) {
                fallback.actionPerformed(e);
            } else if (pos > 0) {
                doc.remove(pos - 1, 1);
            }

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }
}
