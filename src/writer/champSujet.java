package writer;

import javax.swing.JTextArea;

public class champSujet {
	public champSujet(JTextArea editorPane) {

		int positionCurseur = editorPane.getCaretPosition();
		String selectedText = editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@Sujet";
			 editorPane.replaceRange(newText, positionCurseur, positionCurseur);
			 editorPane.setCaretPosition(positionCurseur);
             //"Le champs sujet est inséré."
		}else {
			
		}
	}
}
