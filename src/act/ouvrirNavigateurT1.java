package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import dia.navigateurT1;

public class ouvrirNavigateurT1 extends AbstractAction{

	private static final long serialVersionUID = 1L;

	@Override
	public void actionPerformed(ActionEvent e) {
		new navigateurT1();
	}
	

}
