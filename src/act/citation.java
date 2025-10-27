package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.ui.EditorApi;

public class citation extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final EditorApi ctx;

	 // Constructeur
	public citation(EditorApi ctx) {
	    super("citation");
	    this.ctx = ctx;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		new writer.noteBasPage(ctx).appliquer();		
	}

}
