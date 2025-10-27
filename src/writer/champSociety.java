package writer;

import javax.swing.JTextArea;

public class champSociety {
	public champSociety(JTextArea editorPane) {

		int positionCurseur = editorPane.getCaretPosition();
		String selectedText = editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@Société";
			 editorPane.replaceRange(newText, positionCurseur, positionCurseur);
			 editorPane.setCaretPosition(positionCurseur);
             //"Le champs société est inséré."
		}else {

		}
	}
}
