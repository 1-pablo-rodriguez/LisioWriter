package styles;

import javax.swing.text.BadLocationException;

import writer.blindWriter;

public class bodyTexte {
	public bodyTexte() {
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
             
             
             if (lineText.trim().matches("^#[0-9]\\..*")) {
            	 String newText = lineText.replaceFirst("^#[0-9]\\.", "");
            	 newText = newText.replaceFirst("^\\s+", "");
                 blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
        		 blindWriter.editorPane.setCaretPosition(caretPosition);
        		 sound();
		         return;
             }

             if (lineText.trim().matches("^#P\\..*")) {
	        	 String newText = lineText.replaceFirst("^#P\\.", "");
	        	 newText = newText.replaceFirst("^\\s+", "");
		         blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
	          	 blindWriter.editorPane.setCaretPosition(caretPosition);
		         sound();
		         return;
	        }
   	       if (lineText.trim().matches("^#S\\..*")) {
	        	 String newText = lineText.replaceFirst("^#S\\.", "");
	        	 newText = newText.replaceFirst("^\\s+", "");
	            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
	            blindWriter.editorPane.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }   
   	       if (lineText.trim().matches("^-\\..*")) {
	        	 String newText = lineText.replaceFirst("^-\\.", "");
	        	 newText = newText.replaceFirst("^\\s+", "");
	            blindWriter.editorPane.replaceRange(newText, lineStart, lineEnd);
	            blindWriter.editorPane.setCaretPosition(caretPosition);
	            sound();
	            return;
	        } 
         } catch (BadLocationException ex) {
             ex.printStackTrace();
         }
	}
	
	private void sound() {
		blindWriter.announceCaretLine(false, true,"Paragraphe en corps de texte.");
	}
}
