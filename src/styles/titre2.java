package styles;

import javax.swing.text.BadLocationException;

import writer.blindWriter;

public class titre2 {
	public titre2() {
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
             if (lineText.trim().matches("^#[3-9]\\..*")) {
                 // Remplacer tous les # excédentaires en ne gardant que le premier
                 String newText = lineText.replaceFirst("^#[3-9]\\.", "#2. ");
                 blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
        		 blindWriter.editorPane.setCaretPosition(caretPosition);
                 sound();
                 return;
             }
             
 	        if (lineText.trim().matches("^#P\\..*")) {
	        	 String newText = lineText.replaceFirst("^#P\\.\\s*", "#2. ");
		            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
	          	 	blindWriter.editorPane.setCaretPosition(caretPosition);
		            sound();
	            return;
	        }
  	       if (lineText.trim().matches("^#S\\..*")) {
	        	 String newText = lineText.replaceFirst("^#S\\.\\s*", "#2. ");
	            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
      	 	blindWriter.editorPane.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	  	     if (lineText.trim().matches("^-\\..*")) {
	        	 String newText = lineText.replaceFirst("^-\\.\\s*", "#2. ");
	            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
	  	 	blindWriter.editorPane.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }
	             
             if (lineText.trim().matches("^#2\\..*")) {
            	 sound();
                 return;
             }
             if (lineText.trim().matches("^#1\\..*")) {
                 // Cas où la ligne commence par un seul #
            	 String newText = lineText.replaceFirst("^#1\\.\\s*", "#2. ");
            	 blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
        		 blindWriter.editorPane.setCaretPosition(caretPosition);
            	 sound();
                 return;
             } 
             if (lineText.trim().matches("^[^#].*")) {
            	 blindWriter.editorPane.insert("#2. ", lineStart);
        		 blindWriter.editorPane.setCaretPosition(caretPosition);
            	 sound();
                 return;
             }

           
            
         } catch (BadLocationException ex) {
             ex.printStackTrace();
         }
	}
	
	private void sound() {
		blindWriter.announceCaretLine(false, true,"Paragraphe en Titre 2");
	}
}
