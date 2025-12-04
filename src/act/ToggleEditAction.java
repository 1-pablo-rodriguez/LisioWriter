package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.ui.EditorFrame;

@SuppressWarnings("serial")
public class ToggleEditAction extends AbstractAction{

	private final EditorFrame frame;

	// Constructeur
	public ToggleEditAction(EditorFrame frame) {
	    super("actCheckWindow");
	    this.frame = frame;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		 boolean editable = frame.getEditor().isEditable();
		 frame.getEditor().setEditable(!editable);
	       String msg = editable ? "Édition bloquée." : "Édition activée.";
	       java.awt.Window owner = frame.getWindow();
	       dia.InfoDialog.show(owner, "Mode édition", msg, frame.getAffichage());
	}

}
