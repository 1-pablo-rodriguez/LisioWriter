package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.ui.EditorApi;

public class Italique extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final EditorApi ctx;

	// Constructeur
	public Italique(EditorApi ctx) {
	    super("Italique");
	    this.ctx = ctx;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		new formatage.Italique(ctx).appliquer();
	}

}
