package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.text.JTextComponent;

public class openSearchDialog extends AbstractAction{
	private static final long serialVersionUID = 1L;
	JTextComponent editor;
	public openSearchDialog(JTextComponent editor) {
		this.editor = editor;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		new writer.openSearchDialog(editor);
	}
}
