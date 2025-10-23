package styles;

import javax.swing.text.BadLocationException;

import writer.blindWriter;

public class titre3 {
	public titre3() {
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
	        if (lineText.trim().matches("^#[4-9]\\..*")) {
	            // Remplacer tous les # excédentaires en ne gardant que le premier
	            String newText = lineText.replaceFirst("^#[4-9]\\.\\s*", "#3. ");
	            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
	       		blindWriter.editorPane.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        if (lineText.trim().matches("^#P\\..*")) {
	        	 String newText = lineText.replaceFirst("^#P\\.\\s*", "#3. ");
		            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
	          	 	blindWriter.editorPane.setCaretPosition(caretPosition);
		            sound();
	            return;
	        }
 	       if (lineText.trim().matches("^#S\\..*")) {
	        	 String newText = lineText.replaceFirst("^#S\\.\\s*", "#3. ");
	            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
        	 	blindWriter.editorPane.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
 	      if (lineText.trim().matches("^-\\..*")) {
	        	 String newText = lineText.replaceFirst("^-\\.\\s*", "#3. ");
	            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
     	 	blindWriter.editorPane.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	        if (lineText.trim().matches("^#3\\..*")) {
	            // Cas où la ligne commence déjà par ###
	            sound();
	            return;
	        }
	        if (lineText.trim().matches("^#[1-2]\\..*")) {
	            // Cas où la ligne commence par ##
	        	String newText = lineText.replaceFirst("^#[1-2]\\.\\s*", "#3. ");
		           blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
      		    blindWriter.editorPane.setCaretPosition(caretPosition);
	            sound();
	            return;
	        } 
	        if (lineText.trim().matches("^[^#].*")) {
		       	 blindWriter.editorPane.insert("#3. ", lineStart);
				 blindWriter.editorPane.setCaretPosition(caretPosition);
		       	 sound();
		       	 return;
	        }
	    } catch (BadLocationException ex) {
	        ex.printStackTrace();
	    }
	}
	
	private void sound() {
		blindWriter.announceCaretLine(false, true,"Paragraphe en Titre 3");
	}
}
