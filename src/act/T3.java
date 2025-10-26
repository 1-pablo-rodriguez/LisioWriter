package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.ui.EditorApi;

public class T3 extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final EditorApi ctx;

	// Constructeur
	public T3(EditorApi ctx) {
	    super("Titre 3");
	    this.ctx = ctx;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		new styles.titre3(ctx).appliquer();
	}
	

}
