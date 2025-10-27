package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.ui.EditorApi;

/**
 * Action : Insertion d'un Titre 1
 */
public class T1 extends AbstractAction{

	 private static final long serialVersionUID = 1L;
	 private final EditorApi ctx;

	 // Constructeur
	public T1(EditorApi ctx) {
	    super("Titre 1");
	    this.ctx = ctx;
	}
	 
	@Override
	public void actionPerformed(ActionEvent e) {
		 if (ctx == null) {
	            System.err.println("Erreur: contexte EditorApi nul dans T1");
	            return;
	        }
	        new styles.titre1(ctx).appliquer();
	    }
	}

