package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

@SuppressWarnings("serial")
public class ToggleEditAction extends AbstractAction{

	private final writer.ui.NormalizingTextPane editorPane;

	// Constructeur
	public ToggleEditAction(writer.ui.NormalizingTextPane editorPane) {
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
