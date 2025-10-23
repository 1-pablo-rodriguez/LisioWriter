package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.commandes;
import writer.playSound;

public class select_CANCEL extends AbstractAction{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		System.out.println("ANNNULER obtient le focus !");
		if(commandes.audioActif) new playSound(commandes.getPathApp + "/bouton_CANCEL.wav");
	}

}
