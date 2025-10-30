package styles;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import writer.ui.EditorApi;
import writer.ui.text.Lines;

public class titrePrincipale {
	private final EditorApi ctx;

    public titrePrincipale(EditorApi ctx) {
        this.ctx = ctx;
    }
	
    public void appliquer() {
		try {
			 	JTextComponent editor = ctx.getEditor();
			 	// Obtenez la position du curseur
				int caretPosition = editor.getCaretPosition();
				
	            // Obtenez la position du curseur
			 	int line = Lines.getLineOfOffset(editor, caretPosition);
				
				// Obtenez les offsets de début et de fin de la ligne
				int lineStart = Lines.getLineStartOffset(editor, line); 
				int lineEnd =  Lines.getLineEndOffset(editor, line);

	            // Extraire le texte de la ligne
	            String lineText = editor.getText(lineStart, lineEnd - lineStart);
	        
	     // Utilisation d'une expression régulière pour gérer les différents cas
	        if (lineText.trim().matches("^#[1-9]\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^#[0-9]\\.\\s*", "#P. ");
	            Lines.replaceRange(editor, newText, lineStart, lineEnd);
	            editor.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        if (lineText.trim().matches("^#S\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^#S\\.\\s*", "#P. ");
	            Lines.replaceRange(editor, newText, lineStart, lineEnd);
	            editor.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        
	        if (lineText.trim().matches("^-\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^-\\.\\s*", "#P. ");
	            Lines.replaceRange(editor, newText, lineStart, lineEnd);
	            editor.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        
	        if (lineText.trim().matches("^#P\\..*")) {
	            sound();
	            return;
	        }
 
	        if (lineText.trim().matches("^[^#].*")) {
	        	Lines.insert(editor, "#P. ", lineStart);
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
