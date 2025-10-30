package styles;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import writer.ui.EditorApi;
import writer.ui.text.Lines;

public class bodyTexte {
	private final EditorApi ctx;

    public bodyTexte(EditorApi ctx) {
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
			int lineEnd =  Lines.getLineEndOffset(editor, line) ;
			
			// Extraire le texte de la ligne
			String lineText = editor.getText(lineStart, lineEnd - lineStart);
             
             
             if (lineText.trim().matches("^#[0-9]\\..*")) {
            	 String newText = lineText.replaceFirst("^#[0-9]\\.", "");
            	 newText = newText.replaceFirst("^\\s+", "");
            	 Lines.replaceRange(editor, newText, lineStart, lineEnd);
            	 editor.setCaretPosition(caretPosition);
        		 sound();
		         return;
             }

             if (lineText.trim().matches("^#P\\..*")) {
	        	 String newText = lineText.replaceFirst("^#P\\.", "");
	        	 newText = newText.replaceFirst("^\\s+", "");
	        	 Lines.replaceRange(editor, newText, lineStart, lineEnd);
	        	 editor.setCaretPosition(caretPosition);
		         sound();
		         return;
	        }
   	       if (lineText.trim().matches("^#S\\..*")) {
	        	 String newText = lineText.replaceFirst("^#S\\.", "");
	        	 newText = newText.replaceFirst("^\\s+", "");
	        	 Lines.replaceRange(editor, newText, lineStart, lineEnd);
	        	 editor.setCaretPosition(caretPosition);
	            sound();
	            return;
	        }   
   	       if (lineText.trim().matches("^-\\..*")) {
	        	 String newText = lineText.replaceFirst("^-\\.", "");
	        	 newText = newText.replaceFirst("^\\s+", "");
	        	 Lines.replaceRange(editor, newText, lineStart, lineEnd);
	        	 editor.setCaretPosition(caretPosition);
	            sound();
	            return;
	        } 
         } catch (BadLocationException ex) {
             ex.printStackTrace();
         }
	}
	
	private void sound() {
		ctx.showInfo("Corps de texte", "Paragraphe en corps de texte.");
	}
}
