package styles;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

import writer.ui.EditorApi;

public class titrePrincipale {
	private final EditorApi ctx;

    public titrePrincipale(EditorApi ctx) {
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
	            String newText = lineText.replaceFirst("^#[0-9]\\.\\s*", "#P. ");
	            editor.replaceRange(newText, lineStart, lineEnd);
	            editor.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        if (lineText.trim().matches("^#S\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^#S\\.\\s*", "#P. ");
	            editor.replaceRange(newText, lineStart, lineEnd);
	            editor.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        
	        if (lineText.trim().matches("^-\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^-\\.\\s*", "#P. ");
	            editor.replaceRange(newText, lineStart, lineEnd);
	            editor.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        
	        if (lineText.trim().matches("^#P\\..*")) {
	            sound();
	            return;
	        }
 
	        if (lineText.trim().matches("^[^#].*")) {
	        	editor.insert("#P. ", lineStart);
	        	editor.setCaretPosition(caretPosition);
		       	sound();
		       	return;
	        }
	    } catch (BadLocationException ex) {
	        ex.printStackTrace();
	    }
	}
	private void sound() {
		ctx.showInfo("Titre Principal", "Paragraphe en titre principal.");
	}
}
