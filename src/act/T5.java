package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.ui.EditorApi;

public class T5 extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final EditorApi ctx;

	// Constructeur
	public T5(EditorApi ctx) {
	    super("Titre 5");
	    this.ctx = ctx;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		new styles.titre5(ctx).appliquer();
	}
}
