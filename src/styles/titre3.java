package styles;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import writer.ui.EditorApi;
import writer.ui.text.Lines;

/**
 * Style : Titre 3
 * 
 * Cette classe applique la balise "#3. " au début de la ligne courante,
 * en corrigeant les niveaux ou symboles précédents si nécessaire.
 */
public class titre3 {

    private final EditorApi ctx;

    public titre3(EditorApi ctx) {
        this.ctx = ctx;
    }

    public void appliquer() {
        try {
            JTextComponent editor = ctx.getEditor();

            // Obtenez la position du curseur
 			int caretPosition = editor.getCaretPosition();
 			
 			// Trouvez la ligne actuelle
 			int line = Lines.getLineOfOffset(editor, caretPosition);
 			
 			// Obtenez les offsets de début et de fin de la ligne
 			int lineStart = Lines.getLineStartOffset(editor, line); 
 			int lineEnd =  Lines.getLineEndOffset(editor, line);

            // Extraire le texte de la ligne
            String lineText = editor.getText(lineStart, lineEnd - lineStart);

            // --- Cas 1 : Titre supérieur (#4 à #9)
            if (lineText.trim().matches("^#[4-9]\\..*")) {
                String newText = lineText.replaceFirst("^#[4-9]\\.\\s*", "#3. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 2 : Paragraphe (#P.)
            if (lineText.trim().matches("^#P\\..*")) {
                String newText = lineText.replaceFirst("^#P\\.\\s*", "#3. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 3 : Sous-partie (#S.)
            if (lineText.trim().matches("^#S\\..*")) {
                String newText = lineText.replaceFirst("^#S\\.\\s*", "#3. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 4 : Liste (-.)
            if (lineText.trim().matches("^-\\..*")) {
                String newText = lineText.replaceFirst("^-\\.\\s*", "#3. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 5 : déjà un Titre 3
            if (lineText.trim().matches("^#3\\..*")) {
                sound();
                return;
            }

            // --- Cas 6 : ancien titre 1 ou 2
            if (lineText.trim().matches("^#[1-2]\\..*")) {
                String newText = lineText.replaceFirst("^#[1-2]\\.\\s*", "#3. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 7 : ligne sans balise
            if (lineText.trim().matches("^[^#].*")) {
            	Lines.insert(editor, "#3. ", lineStart);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void sound() {
        // 🔊 À adapter si announceCaretLine est ajouté plus tard dans EditorApi
        ctx.showInfo("Titre 3", "Paragraphe en Titre 3");
        // ou plus tard : ctx.announceCaretLine(false, true, "Paragraphe en Titre 3");
    }
}
