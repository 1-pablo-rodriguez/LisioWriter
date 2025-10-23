package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class Italique extends AbstractAction{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public void actionPerformed(ActionEvent e) {
		new formatage.Italique();
	}

}
