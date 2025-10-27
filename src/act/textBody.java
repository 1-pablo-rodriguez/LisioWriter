package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.ui.EditorApi;

public class textBody extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final EditorApi ctx;

	// Constructeur
	public textBody(EditorApi ctx) {
	    super("textBody");
	    this.ctx = ctx;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		new styles.bodyTexte(ctx).appliquer();	
	}

}
