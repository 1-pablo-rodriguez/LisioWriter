package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import writer.informationsAffichage;

public class informations extends AbstractAction{

	 private static final long serialVersionUID = 1L;
	    @Override
	    public void actionPerformed(ActionEvent e) {
	    	new informationsAffichage();
	    }
}
