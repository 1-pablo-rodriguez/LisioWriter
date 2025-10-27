package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import dia.navigateurT1;
import writer.ui.EditorFrame;

public class ouvrirNavigateurT1 extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final EditorFrame parent;

	// Constructeur
	public ouvrirNavigateurT1(EditorFrame parent) {
	    super("ouvrirNavigateurT1");
	    this.parent = parent;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		new navigateurT1(parent);
	}
	

}
