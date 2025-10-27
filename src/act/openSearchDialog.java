package act;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JTextArea;

public class openSearchDialog extends AbstractAction{
	private static final long serialVersionUID = 1L;
	JTextArea editor;
	public openSearchDialog(JTextArea editor) {
		this.editor = editor;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		new writer.openSearchDialog(editor);
	}
}
