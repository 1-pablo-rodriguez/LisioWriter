package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.text.JTextComponent;

@SuppressWarnings("serial")
public class removeLinks extends AbstractAction{
	private final JTextComponent editorPane;

	// Constructeur
	public removeLinks(JTextComponent editorPane) {
	    super("removeLinks");
	    this.editorPane = editorPane;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
	        javax.swing.text.Document doc = editorPane.getDocument();
	        String text = doc.getText(0, doc.getLength());

	        // Expression régulière pour les liens LisioWriter : @[texte : URL]
	        String cleaned = text.replaceAll("@\\[[^\\]:]+\\s*:\\s*https?://[^\\]]+\\]", "");

	        // Remplace le contenu de l’éditeur
	        doc.remove(0, doc.getLength());
	        doc.insertString(0, cleaned, null);

	        editorPane.setCaretPosition(0);
	        editorPane.requestFocusInWindow();

	        System.out.println("✅ Tous les liens ont été supprimés du texte.");

	    } catch (Exception ex) {
	        ex.printStackTrace();
	    }
		
	}

}
