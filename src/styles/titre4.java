package styles;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import writer.ui.EditorApi;
import writer.ui.text.Lines;

/**
 * Style : Titre 4
 *
 * Cette classe applique la balise "#4. " au début de la ligne courante,
 * en corrigeant les niveaux ou symboles précédents si nécessaire.
 */
public class titre4 {

    private final EditorApi ctx;

    public titre4(EditorApi ctx) {
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

            // --- Cas 1 : Titre supérieur (#5 à #9)
            if (lineText.trim().matches("^#[5-9]\\..*")) {
                String newText = lineText.replaceFirst("^#[5-9]\\.\\s*", "#4. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 2 : Paragraphe (#P.)
            if (lineText.trim().matches("^#P\\..*")) {
                String newText = lineText.replaceFirst("^#P\\.\\s*", "#4. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 3 : Sous-partie (#S.)
            if (lineText.trim().matches("^#S\\..*")) {
                String newText = lineText.replaceFirst("^#S\\.\\s*", "#4. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 4 : Liste (-.)
            if (lineText.trim().matches("^-\\..*")) {
                String newText = lineText.replaceFirst("^-\\.\\s*", "#4. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 5 : déjà un Titre 4
            if (lineText.trim().matches("^#4\\..*")) {
                sound();
                return;
            }

            // --- Cas 6 : ancien titre (1 à 3)
            if (lineText.trim().matches("^#[1-3]\\..*")) {
                String newText = lineText.replaceFirst("^#[1-3]\\.\\s*", "#4. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 7 : ligne sans balise
            if (lineText.trim().matches("^[^#].*")) {
            	Lines.insert(editor, "#4. ", lineStart);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void sound() {
        // 🔊 Pour l’instant utilise une info simple ; remplace plus tard par ctx.announceCaretLine()
        ctx.showInfo("Titre 4", "Paragraphe en Titre 4");
    }
}
