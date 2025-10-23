package act;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

public class openSearchDialog extends AbstractAction{
	private static final long serialVersionUID = 1L;
	@Override
	public void actionPerformed(ActionEvent e) {
		new writer.openSearchDialog();
	}
}
