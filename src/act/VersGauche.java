package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;

import writer.blindWriter;

public class VersGauche extends AbstractAction{

	private static final long serialVersionUID = 1L;

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
	        // Récupérer la position actuelle du curseur
	        int caretPosition = blindWriter.editorPane.getCaretPosition();
	        
	        // Obtenir l'index de la ligne actuelle
	        int currentLine = blindWriter.editorPane.getLineOfOffset(caretPosition);

	        // Vérifier que la ligne actuelle existe
	        if (currentLine >= 0) {
	            // Calculer la position de début de la ligne actuelle
	            int lineStartOffset = blindWriter.editorPane.getLineStartOffset(currentLine);
	            
	            // Placer le curseur au début de la ligne
	            blindWriter.editorPane.setCaretPosition(lineStartOffset);
	        }
	    } catch (BadLocationException e1) {
	        e1.printStackTrace();
	    }
	}
}
