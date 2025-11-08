package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class openSearchDialog extends AbstractAction{
	private static final long serialVersionUID = 1L;
	writer.ui.NormalizingTextPane editor;
	public openSearchDialog(writer.ui.NormalizingTextPane editor) {
		this.editor = editor;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		new writer.openSearchDialog(editor);
	}
}
