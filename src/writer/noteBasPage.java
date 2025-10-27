package writer;

import writer.ui.EditorApi;

public class noteBasPage {
	private final EditorApi ctx;

	public noteBasPage(EditorApi ctx) {
	    this.ctx = ctx;
	}
	
	public void appliquer() {
		var editor = ctx.getEditor();
	
		int positionCurseur = editor.getCaretPosition();
		String selectedText = editor.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@(auteur : - titre : - année : - pages : )";
			 //"La note de bas de pages est insérée. Vous devez modifier le texte entre les parenthèses pour modifier le texte de la note de bas de page."
			 editor.replaceRange(newText, positionCurseur, positionCurseur);
			 editor.setCaretPosition(positionCurseur);
		}else {
			//"Vous ne pouvez pas sélectionner un mot ou un texte pour insérer une note de bas de page."
		}
	}
}
