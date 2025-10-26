package writer;

import javax.swing.JTextArea;

public class outils {
	

	// Supprime les retours chariot
	public static String  removeCarriageReturns(JTextArea editorPane) {
	    // Remplacer tous les '\r' par une chaîne vide
	    String cleanedText = editorPane.getText().replace("\r", "\n");
	    cleanedText = editorPane.getText().replace("\n\n", "\n");
	    return cleanedText;
	}
	
	public static void  afficheReturns(JTextArea editorPane) {
	    String text = editorPane.getText();
	    String cleanedText = text.replace("\r", "R\r").replace("\r\n","R\n").replace("\n","R\n");
	    editorPane.setText(cleanedText);
	}
	
	
	
}
