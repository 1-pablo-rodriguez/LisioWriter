package writer;

public class noteBasPage {
	public noteBasPage() {
		int positionCurseur = blindWriter.editorPane.getCaretPosition();
		String selectedText = blindWriter.editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@(auteur : - titre : - année : - pages : )";
			 //"La note de bas de pages est insérée. Vous devez modifier le texte entre les parenthèses pour modifier le texte de la note de bas de page."
             blindWriter.editorPane.replaceRange(newText, positionCurseur, positionCurseur);
             blindWriter.editorPane.setCaretPosition(positionCurseur);
		}else {
			//"Vous ne pouvez pas sélectionner un mot ou un texte pour insérer une note de bas de page."
		}
	}
}
