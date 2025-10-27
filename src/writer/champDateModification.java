package writer;

import javax.swing.JTextArea;

public class champDateModification {
	public champDateModification(JTextArea editorPane) {

		int positionCurseur = editorPane.getCaretPosition();
		String selectedText = editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@Date";
			 editorPane.replaceRange(newText, positionCurseur, positionCurseur);
			 editorPane.setCaretPosition(positionCurseur);
             //"Le champs date est inséré."
		}else {

		}
	}
}
