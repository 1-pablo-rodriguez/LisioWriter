package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import writer.ui.text.Lines;

public class VersDroite extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final JTextComponent editorPane;

	// Constructeur
	public VersDroite(JTextComponent editorPane) {
	    super("VersDroite");
	    this.editorPane = editorPane;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
	        // Récupérer la position actuelle du curseur
	        int caretPosition = this.editorPane.getCaretPosition();
	        
	        // Obtenir l'index de la ligne actuelle
	        int currentLine = Lines.getLineEndOffset(this.editorPane, caretPosition);

	        // Récupérer le texte complet
	        String text = this.editorPane.getText();
	        
	        // Diviser le texte en lignes
	        String[] lines = text.split("\n");

	        // Vérifier que la ligne actuelle existe bien
	        if (currentLine < lines.length) {
	            // Calculer la longueur de la ligne actuelle pour obtenir la position de fin
	        	int lineStartOffset = Lines.getLineStartOffset(this.editorPane, currentLine);
	            int lineEndOffset = lineStartOffset + lines[currentLine].length();
	            
	            // Placer le curseur à la fin de la ligne
	            this.editorPane.setCaretPosition(lineEndOffset);
	        }
	    } catch (BadLocationException e1) {
	        e1.printStackTrace();
	    }
		
	}

}
