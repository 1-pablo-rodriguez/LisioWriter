package writer;

import javax.swing.JTextArea;

public class champCoAuteur {
	
	public champCoAuteur(JTextArea editorPane) {
		int positionCurseur = editorPane.getCaretPosition();
		String selectedText = editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@Coauteur";
             editorPane.replaceRange(newText, positionCurseur, positionCurseur);
             editorPane.setCaretPosition(positionCurseur);
		}else {
			
		}
	}
}
