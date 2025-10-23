package writer;

public class champCoAuteur {
	public champCoAuteur() {
//		outils.removeCarriageReturns(blindWriter.editorPane.getText());
		int positionCurseur = blindWriter.editorPane.getCaretPosition();
		String selectedText = blindWriter.editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@Coauteur";
             blindWriter.editorPane.replaceRange(newText, positionCurseur, positionCurseur);
             blindWriter.editorPane.setCaretPosition(positionCurseur);
             //"Le champs co-auteur est inséré."
		}else {
			
		}
	}
}
