package writer;

public class champAuteur {
	public champAuteur() {
//		outils.removeCarriageReturns(blindWriter.editorPane.getText());
		int positionCurseur = blindWriter.editorPane.getCaretPosition();
		String selectedText = blindWriter.editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@Auteur";
             blindWriter.editorPane.replaceRange(newText, positionCurseur, positionCurseur);
             blindWriter.editorPane.setCaretPosition(positionCurseur);
             //"Le champs auteur est inséré."
		}else {

		}
	}
}
