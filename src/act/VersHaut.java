package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;

import writer.blindWriter;

public class VersHaut extends AbstractAction{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
            // Récupérer la position actuelle du curseur
            int caretPosition = blindWriter.editorPane.getCaretPosition();
            // Obtenir l'index de la ligne actuelle du curseur
            int currentLine = blindWriter.editorPane.getLineOfOffset(caretPosition);

            // Récupérer le texte complet
            String text = blindWriter.editorPane.getText();
            // Diviser le texte en lignes
            String[] lines = text.split("\n");
            int cursorPosition = 0;

            // Parcourir les lignes à partir de la position actuelle du curseur et remonter
            for (int i = currentLine-1; i >= 0; i--) {
                if (lines[i].trim().startsWith("#")) {
                    // Récupérer la position du curseur au début de cette ligne
                    cursorPosition = blindWriter.editorPane.getLineStartOffset(i);
                    break;
                }
            }

            // Si aucune ligne ne commence par un #, placer le curseur au début du texte
            blindWriter.editorPane.setCaretPosition(cursorPosition);
        } catch (BadLocationException e1) {
            e1.printStackTrace();
        }
		
	}

}
