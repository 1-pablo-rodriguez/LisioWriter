package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class SautPage extends AbstractAction{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public void actionPerformed(ActionEvent e) {
		new page.sautPage();		
	}

}
