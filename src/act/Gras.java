package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.ui.EditorApi;

public class Gras extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final EditorApi ctx;

	// Constructeur
	public Gras(EditorApi ctx) {
	    super("Gras");
	    this.ctx = ctx;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		new formatage.Gras(ctx).appliquer();
	}

}
