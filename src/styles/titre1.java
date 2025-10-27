package styles;

import javax.swing.text.BadLocationException;
import writer.ui.EditorApi;

public class titre1 {
    private final EditorApi ctx;

    public titre1(EditorApi ctx) {
        this.ctx = ctx;
    }

    public void appliquer() {
        try {
            var editor = ctx.getEditor();
            int caretPosition = editor.getCaretPosition();
            int line = editor.getLineOfOffset(caretPosition);
            int lineStart = editor.getLineStartOffset(line);
            int lineEnd = editor.getLineEndOffset(line);
            String lineText = editor.getText(lineStart, lineEnd - lineStart);

            if (lineText.trim().matches("^#[2-9]\\..*")) {
                String newText = lineText.replaceFirst("^#[2-9]\\.\\s*", "#1. ");
                editor.replaceRange(newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }
            if (lineText.trim().matches("^#P\\..*")) {
                String newText = lineText.replaceFirst("^#P\\.\\s*", "#1. ");
                editor.replaceRange(newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }
            if (lineText.trim().matches("^#S\\..*")) {
                String newText = lineText.replaceFirst("^#S\\.\\s*", "#1. ");
                editor.replaceRange(newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }
            if (lineText.trim().matches("^-\\..*")) {
                String newText = lineText.replaceFirst("^-\\.\\s*", "#1. ");
                editor.replaceRange(newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }
            if (lineText.trim().matches("^[^#].*")) {
                editor.insert("#1. ", lineStart);
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
