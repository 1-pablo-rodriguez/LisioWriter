package writer;

public class champDateModification {
	public champDateModification() {
//		outils.removeCarriageReturns(blindWriter.editorPane.getText());
		int positionCurseur = blindWriter.editorPane.getCaretPosition();
		String selectedText = blindWriter.editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@Date";
             blindWriter.editorPane.replaceRange(newText, positionCurseur, positionCurseur);
             blindWriter.editorPane.setCaretPosition(positionCurseur);
             //"Le champs date est inséré."
		}else {

		}
	}
}
