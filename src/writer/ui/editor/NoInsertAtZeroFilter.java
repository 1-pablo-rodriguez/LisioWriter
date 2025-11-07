package writer.ui.editor;

import java.awt.Toolkit;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Refuse toute insertion de caractères à l’offset 0. */
public final class NoInsertAtZeroFilter extends DocumentFilter {

    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
            throws BadLocationException {
        if (string != null && !string.isEmpty() && offset == 0) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        super.insertString(fb, offset, string, attr);
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        // Si on remplace au début ET qu’on insère quelque chose => interdit
        if (text != null && !text.isEmpty() && offset == 0) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        super.replace(fb, offset, length, text, attrs);
    }
}