package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.informationsAffichage;
import writer.ui.EditorFrame;

public class informations extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final EditorFrame parent;

	// Constructeur
	public informations(EditorFrame parent) {
	    super("informations");
	    this.parent = parent;
	}
	   
    public void actionPerformed(ActionEvent e) {
    	new informationsAffichage(parent);
    }
}
