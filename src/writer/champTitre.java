package writer;

public class champTitre {
	public champTitre() {
//		outils.removeCarriageReturns(blindWriter.editorPane.getText());
		int positionCurseur = blindWriter.editorPane.getCaretPosition();
		String selectedText = blindWriter.editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@Titre";
             blindWriter.editorPane.replaceRange(newText, positionCurseur, positionCurseur);
             blindWriter.editorPane.setCaretPosition(positionCurseur);
             //"Le champs titre est inséré."
		}else {
			 
		}
	}
}
