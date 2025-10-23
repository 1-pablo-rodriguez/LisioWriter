package writer;

public class champSujet {
	public champSujet() {
//		outils.removeCarriageReturns(blindWriter.editorPane.getText());
		int positionCurseur = blindWriter.editorPane.getCaretPosition();
		String selectedText = blindWriter.editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@Sujet";
             blindWriter.editorPane.replaceRange(newText, positionCurseur, positionCurseur);
             blindWriter.editorPane.setCaretPosition(positionCurseur);
             //"Le champs sujet est inséré."
		}else {
			
		}
	}
}
