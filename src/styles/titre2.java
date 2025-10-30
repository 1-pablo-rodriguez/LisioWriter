package styles;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import writer.ui.EditorApi;
import writer.ui.text.Lines;

/**
 * Style : Titre 2
 * 
 * Cette classe applique la balise "#2. " au début de la ligne courante,
 * en corrigeant les niveaux ou symboles précédents si nécessaire.
 */
public class titre2 {

    private final EditorApi ctx;

    public titre2(EditorApi ctx) {
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

            // --- Cas 1 : déjà un titre supérieur (#3. à #9.)
            if (lineText.trim().matches("^#[3-9]\\..*")) {
                String newText = lineText.replaceFirst("^#[3-9]\\.", "#2. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 2 : paragraphe (#P.)
            if (lineText.trim().matches("^#P\\..*")) {
                String newText = lineText.replaceFirst("^#P\\.\\s*", "#2. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 3 : sous-partie (#S.)
            if (lineText.trim().matches("^#S\\..*")) {
                String newText = lineText.replaceFirst("^#S\\.\\s*", "#2. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 4 : liste (-.)
            if (lineText.trim().matches("^-\\..*")) {
                String newText = lineText.replaceFirst("^-\\.\\s*", "#2. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 5 : déjà un titre 2
            if (lineText.trim().matches("^#2\\..*")) {
                sound();
                return;
            }

            // --- Cas 6 : c’était un titre 1
            if (lineText.trim().matches("^#1\\..*")) {
                String newText = lineText.replaceFirst("^#1\\.\\s*", "#2. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 7 : ligne sans balise
            if (lineText.trim().matches("^[^#].*")) {
            	Lines.insert(editor, "#2. ", lineStart);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void sound() {
        // 🔊 Utilise ton interface pour annoncer (à adapter quand announceCaretLine sera déplacé)
        ctx.showInfo("Titre 2", "Paragraphe en Titre 2");
        // ou plus tard : ctx.announceCaretLine(false, true, "Paragraphe en Titre 2");
    }
}
