package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;

import writer.ui.text.Lines;

public class VersGauche extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final writer.ui.NormalizingTextPane editorPane;

	// Constructeur
	public VersGauche(writer.ui.NormalizingTextPane editorPane) {
	    super("VersGauche");
	    this.editorPane = editorPane;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
	        // Récupérer la position actuelle du curseur
	        int caretPosition = this.editorPane.getCaretPosition();
	        
	        // Obtenir l'index de la ligne actuelle
	        int currentLine = Lines.getLineOfOffset(editorPane, caretPosition);

	        // Vérifier que la ligne actuelle existe
	        if (currentLine >= 0) {
	            // Calculer la position de début de la ligne actuelle
	        	int lineStartOffset = Lines.getLineStartOffset(editorPane, currentLine);
	            
	            // Placer le curseur au début de la ligne
	            this.editorPane.setCaretPosition(lineStartOffset);
	        }
	    } catch (BadLocationException e1) {
	        e1.printStackTrace();
	    }
	}
}
