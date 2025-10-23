package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class lectureDepuisCurseur extends AbstractAction{

	 private static final long serialVersionUID = 1L;
	    @Override
	    public void actionPerformed(ActionEvent e) {
	        // Utiliser la chaîne passée en argument
	    	 new writer.lecteurDepuisCurseur();
	    }
}
