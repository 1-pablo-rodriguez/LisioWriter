package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JTextArea;

@SuppressWarnings("serial")
public class ToggleEditAction extends AbstractAction{

	private final JTextArea editorPane;

	// Constructeur
	public ToggleEditAction(JTextArea editorPane) {
	    super("actCheckWindow");
	    this.editorPane = editorPane;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		 boolean editable = editorPane.isEditable();
	       editorPane.setEditable(!editable);
	       String msg = editable ? "Édition bloquée." : "Édition activée.";
	       java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
	       dia.InfoDialog.show(owner, "Mode édition", msg);
	}

}
