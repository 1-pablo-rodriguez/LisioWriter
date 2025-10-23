package writer;

public class lectureParagraphe {
	StringBuilder message = new StringBuilder();
	

	public lectureParagraphe() {

		 try {
             // Obtenir la position du curseur
             int caretPosition = writer.blindWriter.editorPane.getCaretPosition();
             
             // Obtenir le numéro de ligne où se trouve le curseur
             int lineNumber = writer.blindWriter.editorPane.getLineOfOffset(caretPosition);
             
             // Obtenir les offsets de début et fin de la ligne
             int startOffset = writer.blindWriter.editorPane.getLineStartOffset(lineNumber);
             int endOffset = writer.blindWriter.editorPane.getLineEndOffset(lineNumber);
             
             // Extraire le texte de la ligne courante
             String lineText = writer.blindWriter.editorPane.getText(startOffset, endOffset - startOffset);
             
             if(lineText==null)  lineText = "Ce paragraphe est vide." ;
             if(lineText.isBlank()) lineText = "Ce paragraphe ne contient pas de texte.";
             
             lineText = new TraitementSonPourTTS(lineText).returnTexte;
             
         } catch (Exception ex) {
             ex.printStackTrace();
         }
    	 
	
	}

	public StringBuilder getMessage() {
		return message;
	}
	
}
