package writer;

public class tableMatieres {
	public tableMatieres() {
		int positionCurseur = blindWriter.editorPane.getCaretPosition();
		String selectedText = blindWriter.editorPane.getSelectedText();
        
		if(selectedText==null) {
			 String newText = "@TOC;niveau_max=5;Titre=Table des matières";
             blindWriter.editorPane.replaceRange(newText, positionCurseur, positionCurseur);
             blindWriter.editorPane.setCaretPosition(positionCurseur);
		}else {
			//"Vous ne pouvez pas sélectionner un mot ou un texte pour insérer la table des matières."
		}
	}
}
