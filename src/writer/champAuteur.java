package writer;

import writer.ui.EditorFrame;
import writer.ui.text.Lines;

public class champAuteur {
	
	public champAuteur(EditorFrame frame) {
		writer.ui.NormalizingTextPane editorPane = frame.getEditor();
		int positionCurseur = editorPane.getCaretPosition();
		String selectedText = editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@Auteur";
			 Lines.replaceRange(editorPane, newText, positionCurseur, positionCurseur);
             editorPane.setCaretPosition(positionCurseur);
             //"Le champs auteur est inséré."
		}else {

		}
	}
}
