package writer.ui.editor;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

@SuppressWarnings("serial")
public final class DeleteParagraphBackwardAction extends AbstractAction {
    private final JTextComponent comp;

    public DeleteParagraphBackwardAction(JTextComponent comp) {
        super("bw-delete-paragraph-backward");
        this.comp = comp;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            final Document doc = comp.getDocument();
            if (doc.getLength() == 0) { Toolkit.getDefaultToolkit().beep(); return; }

            final int caret = Math.max(0, Math.min(comp.getCaretPosition(), doc.getLength()));
            final Element root = doc.getDefaultRootElement();
            final int paraIdx = Math.max(0, root.getElementIndex(caret));
            final Element paraEl = root.getElement(paraIdx);
            if (paraEl == null) { Toolkit.getDefaultToolkit().beep(); return; }

            // Repères du paragraphe courant
            final int start = paraEl.getStartOffset();
            final int nextStart = (paraIdx + 1 < root.getElementCount())
                    ? root.getElement(paraIdx + 1).getStartOffset()
                    : doc.getLength();
            final int len = Math.max(0, nextStart - start);
            if (len == 0) { Toolkit.getDefaultToolkit().beep(); return; }

            // Calcul de la future position du caret (fin du paragraphe précédent)
            int newCaret;
            if (paraIdx > 0) {
                Element prev = root.getElement(paraIdx - 1);
                int prevEnd = Math.min(prev.getEndOffset(), doc.getLength());
                // place juste avant le saut de ligne du paragraphe précédent
                newCaret = Math.max(0, prevEnd - 1);
            } else {
                // pas de paragraphe précédent → début de document
                newCaret = 0;
            }

            // Supprimer le paragraphe courant (incluant son saut de ligne)
            doc.remove(start, len);

            // Placer le caret
            comp.setCaretPosition(Math.min(newCaret, doc.getLength()));
            comp.requestFocusInWindow();

        } catch (Exception ex) {
            Toolkit.getDefaultToolkit().beep();
        }
    }
}