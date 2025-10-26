package styles;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

import writer.ui.EditorApi;

public class listeNonNumero {
	private final EditorApi ctx;

    public listeNonNumero(EditorApi ctx) {
        this.ctx = ctx;
    }
	
	public void appliquer() {
		try {
			JTextArea editor = ctx.getEditor();

            // Obtenez la position du curseur
            int caretPosition = editor.getCaretPosition();

            // Trouvez la ligne actuelle
            int line = editor.getLineOfOffset(caretPosition);

            // Obtenez les offsets de début et de fin de la ligne
            int lineStart = editor.getLineStartOffset(line);
            int lineEnd = editor.getLineEndOffset(line);

            // Extraire le texte de la ligne
            String lineText = editor.getText(lineStart, lineEnd - lineStart);
	        
	        
	     // Utilisation d'une expression régulière pour gérer les différents cas
	        if (lineText.trim().matches("^#[1-9]\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^#[0-9]\\.\\s*", "-. ");
	            editor.replaceRange(newText, lineStart, lineEnd);
	            editor.setCaretPosition(caretPosition);
	            return;
	        }
	        if (lineText.trim().matches("^#S\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^#S\\.\\s*", "-. ");
	            editor.replaceRange(newText, lineStart, lineEnd);
	            editor.setCaretPosition(caretPosition);
	            return;
	        }
	        
	        if (lineText.trim().matches("^-\\..*")) {
	            sound();
	            return;
	        }
 
	        if (lineText.trim().matches("^[^#].*")) {
	        	editor.insert("-. ", lineStart);
	        	editor.setCaretPosition(caretPosition);
		       	 return;
	        }
	    } catch (BadLocationException ex) {
	        ex.printStackTrace();
	    }
	}
	private void sound() {
		ctx.showInfo("Liste", "Paragraphe liste non numérotée.");
	}
}
