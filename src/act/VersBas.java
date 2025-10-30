package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

public class VersBas extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final JTextComponent editorPane;

	// Constructeur
	public VersBas(JTextComponent editorPane) {
	    super("VersBas");
	    this.editorPane = editorPane;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
            // Récupérer la position actuelle du curseur
            int caretPosition = this.editorPane.getCaretPosition();
            // Obtenir l'index de la ligne actuelle du curseur
            int currentLine = this.editorPane.getLineOfOffset(caretPosition);

            // Récupérer le texte complet
            String text = this.editorPane.getText();
            // Diviser le texte en lignes
            String[] lines = text.split("\n");
            int cursorPosition = lines.length;

            // Parcourir les lignes à partir de la position actuelle du curseur et remonter
            for (int i = currentLine+1; i < lines.length; i++) {
                if (lines[i].trim().startsWith("#")) {
                    // Récupérer la position du curseur au début de cette ligne
                    cursorPosition = this.editorPane.getLineStartOffset(i);
                    break;
                }
            }

            // Si aucune ligne ne commence par un #, placer le curseur au début du texte
            this.editorPane.setCaretPosition(cursorPosition);
        } catch (BadLocationException e1) {
            e1.printStackTrace();
        }
		
	}

}
