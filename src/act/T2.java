package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.ui.EditorApi;

public class T2 extends AbstractAction{

	private static final long serialVersionUID = 1L;
	private final EditorApi ctx;

	// Constructeur
	public T2(EditorApi ctx) {
	    super("Titre 2");
	    this.ctx = ctx;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (ctx == null) {
            System.err.println("Erreur: contexte EditorApi nul dans T1");
            return;
        }
        new styles.titre2(ctx).appliquer();
	}
}
