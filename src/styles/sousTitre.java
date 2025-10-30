package styles;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import writer.ui.EditorApi;
import writer.ui.text.Lines;

public class sousTitre {
	private final EditorApi ctx;

    public sousTitre(EditorApi ctx) {
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
	        
	     // Utilisation d'une expression régulière pour gérer les différents cas
	        if (lineText.trim().matches("^#[1-9]\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^#[0-9]\\.\\s*", "#S. ");
	            Lines.replaceRange(editor, newText, lineStart, lineEnd);
	            editor.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        
	        if (lineText.trim().matches("^#P\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^#P\\.\\s*", "#S. ");
	            Lines.replaceRange(editor, newText, lineStart, lineEnd);
	            editor.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        
	        if (lineText.trim().matches("^-\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^-\\.\\s*", "#S. ");
	            Lines.replaceRange(editor, newText, lineStart, lineEnd);
	            editor.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        
	        
	        if (lineText.trim().matches("^#S\\..*")) {
	            sound();
	            return;
	        }
 
	        if (lineText.trim().matches("^[^#].*")) {
	        	Lines.insert(editor, "#S. ", lineStart);
	        	editor.setCaretPosition(caretPosition);
		       	 sound();
		       	 return;
	        }
	    } catch (BadLocationException ex) {
	        ex.printStackTrace();
	    }
	}
	private void sound() {
		ctx.showInfo("Sous Titre", "Paragraphe en sous titre.");
	}
}
