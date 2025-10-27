package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

public class VersGauche extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final JTextArea editorPane;

	// Constructeur
	public VersGauche(JTextArea editorPane) {
	    super("VersGauche");
	    this.editorPane = editorPane;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
	        // Récupérer la position actuelle du curseur
	        int caretPosition = this.editorPane.getCaretPosition();
	        
	        // Obtenir l'index de la ligne actuelle
	        int currentLine = this.editorPane.getLineOfOffset(caretPosition);

	        // Vérifier que la ligne actuelle existe
	        if (currentLine >= 0) {
	            // Calculer la position de début de la ligne actuelle
	            int lineStartOffset = this.editorPane.getLineStartOffset(currentLine);
	            
	            // Placer le curseur au début de la ligne
	            this.editorPane.setCaretPosition(lineStartOffset);
	        }
	    } catch (BadLocationException e1) {
	        e1.printStackTrace();
	    }
	}
}
