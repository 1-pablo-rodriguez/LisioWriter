package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.text.JTextComponent;

@SuppressWarnings("serial")
public class ToggleEditAction extends AbstractAction{

	private final JTextComponent editorPane;

	// Constructeur
	public ToggleEditAction(JTextComponent editorPane) {
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
