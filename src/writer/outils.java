package writer;

public class outils {

	// Supprime les retour chariot
	public static String  removeCarriageReturns(String text) {
	    // Remplacer tous les '\r' par une chaîne vide
	    String cleanedText = text.replace("\r", "\n");
	    cleanedText = text.replace("\n\n", "\n");
	    return cleanedText;
	}
	
	public static void  afficheReturns() {
	    String text = blindWriter.editorPane.getText();
	    String cleanedText = text.replace("\r", "R\r").replace("\r\n","R\n").replace("\n","R\n");
	    blindWriter.editorPane.setText(cleanedText);
	}
	
	
	
}
