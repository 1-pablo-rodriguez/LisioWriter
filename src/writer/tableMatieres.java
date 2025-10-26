package writer;

import writer.ui.EditorApi;

public class tableMatieres {
	
	private final EditorApi ctx;

	public tableMatieres(EditorApi ctx) {
	    this.ctx = ctx;
	}
	
	public void appliquer() {
		var editor = ctx.getEditor();
		
		int positionCurseur = editor.getCaretPosition();
		String selectedText = editor.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@TOC;niveau_max=5;Titre=Table des matières";
			 editor.replaceRange(newText, positionCurseur, positionCurseur);
			 editor.setCaretPosition(positionCurseur);
		}else {
			//"Vous ne pouvez pas sélectionner un mot ou un texte pour insérer la table des matières."
		}
	}
}
