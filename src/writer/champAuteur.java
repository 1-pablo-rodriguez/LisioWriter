package writer;

import javax.swing.text.JTextComponent;

import writer.ui.EditorFrame;

public class champAuteur {
	
	public champAuteur(EditorFrame frame) {
		JTextComponent editorPane = frame.getEditor();
//		outils.removeCarriageReturns(blindWriter.editorPane.getText());
		int positionCurseur = editorPane.getCaretPosition();
		String selectedText = editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@Auteur";
             editorPane.replaceRange(newText, positionCurseur, positionCurseur);
             editorPane.setCaretPosition(positionCurseur);
             //"Le champs auteur est inséré."
		}else {

		}
	}
}
