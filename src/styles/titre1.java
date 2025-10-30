package styles;

import javax.swing.text.BadLocationException;
import writer.ui.EditorApi;
import writer.ui.text.Lines;

public class titre1 {
    private final EditorApi ctx;

    public titre1(EditorApi ctx) {
        this.ctx = ctx;
    }

    public void appliquer() {
        try {
            var editor = ctx.getEditor();
            // Obtenez la position du curseur
			int caretPosition = editor.getCaretPosition();
			
			// Trouvez la ligne actuelle
			int line = Lines.getLineOfOffset(editor, caretPosition);
			
			// Obtenez les offsets de début et de fin de la ligne
			int lineStart = Lines.getLineStartOffset(editor, line); 
			int lineEnd =  Lines.getLineEndOffset(editor, line);
         			
            String lineText = editor.getText(lineStart, lineEnd - lineStart);

            if (lineText.trim().matches("^#[2-9]\\..*")) {
                String newText = lineText.replaceFirst("^#[2-9]\\.\\s*", "#1. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }
            if (lineText.trim().matches("^#P\\..*")) {
                String newText = lineText.replaceFirst("^#P\\.\\s*", "#1. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }
            if (lineText.trim().matches("^#S\\..*")) {
                String newText = lineText.replaceFirst("^#S\\.\\s*", "#1. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }
            if (lineText.trim().matches("^-\\..*")) {
                String newText = lineText.replaceFirst("^-\\.\\s*", "#1. ");
                Lines.replaceRange(editor, newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }
            if (lineText.trim().matches("^[^#].*")) {
            	Lines.insert(editor, "#1. ", lineStart);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            editor.setCaretPosition(caretPosition);

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void sound() {
    	 ctx.showInfo("Titre 1", "Paragraphe en Titre 1");
    }
}
