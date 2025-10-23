package styles;

import javax.swing.text.BadLocationException;

import writer.blindWriter;

public class sousTitre {
	
	public sousTitre() {
		try {
			 // Obtenez la position du curseur
	        int caretPosition = blindWriter.editorPane.getCaretPosition();
	
	        // Trouvez la ligne actuelle
	        int line = blindWriter.editorPane.getLineOfOffset(caretPosition);
	
	        // Obtenez les offsets de début et de fin de la ligne
	        int lineStart = blindWriter.editorPane.getLineStartOffset(line);
	        int lineEnd = blindWriter.editorPane.getLineEndOffset(line);
	
	        // Extraire le texte de la ligne
	        String lineText = blindWriter.editorPane.getText(lineStart, lineEnd - lineStart);
	        
	     // Utilisation d'une expression régulière pour gérer les différents cas
	        if (lineText.trim().matches("^#[1-9]\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^#[0-9]\\.\\s*", "#S. ");
	            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
          	 	blindWriter.editorPane.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        
	        if (lineText.trim().matches("^#P\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^#P\\.\\s*", "#S. ");
	            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
          	 	blindWriter.editorPane.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        
	        if (lineText.trim().matches("^-\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^-\\.\\s*", "#S. ");
	            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
          	 	blindWriter.editorPane.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        
	        
	        if (lineText.trim().matches("^#S\\..*")) {
	            sound();
	            return;
	        }
 
	        if (lineText.trim().matches("^[^#].*")) {
	        	 blindWriter.editorPane.insert("#S. ", lineStart);
       		 blindWriter.editorPane.setCaretPosition(caretPosition);
		       	 sound();
		       	 return;
	        }
	    } catch (BadLocationException ex) {
	        ex.printStackTrace();
	    }
	}
	private void sound() {
		blindWriter.announceCaretLine(false, true,"Paragraphe en sous titre.");
	}
}
