package writer;

public class lecteurDepuisCurseur {

	public lecteurDepuisCurseur() {
        try {
            // Obtenir la position actuelle du curseur
            int caretPosition = writer.blindWriter.editorPane.getCaretPosition();
            
            // Obtenir le numéro de ligne où se trouve le curseur
            int lineNumber = writer.blindWriter.editorPane.getDocument().getDefaultRootElement().getElementIndex(caretPosition);
            
            // Obtenir les offsets de début et de fin de la ligne
            int startOffset = writer.blindWriter.editorPane.getDocument().getDefaultRootElement().getElement(lineNumber).getStartOffset();
            int endOffset = writer.blindWriter.editorPane.getDocument().getDefaultRootElement().getElement(lineNumber).getEndOffset();
            
            // Extraire le texte de la ligne courante
            String lineText = writer.blindWriter.editorPane.getText(startOffset, endOffset - startOffset);

            // Calculer l'index relatif à la position du curseur dans la ligne
            int relativeCaretPosition = caretPosition - startOffset;
            
            // Lire le texte restant à partir de la position du curseur
            String remainingText = "";
            if (relativeCaretPosition < lineText.length()) {
                remainingText = lineText.substring(relativeCaretPosition).trim();
            }

            // Gestion des cas où il n'y a pas de texte à lire
            if (remainingText.isEmpty()) {
                remainingText = "Il n'y a pas de texte à lire.";
            }

            // Préparer le texte pour TTS
            remainingText = new TraitementSonPourTTS(remainingText).returnTexte;
            

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


	
}
