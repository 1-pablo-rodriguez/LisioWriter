package writer.ui.editor;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

@SuppressWarnings("serial")
public final class DeleteParagraphForwardAction extends AbstractAction {
    private final JTextComponent comp;

    public DeleteParagraphForwardAction(JTextComponent comp) {
        super("bw-delete-paragraph-forward");
        this.comp = comp;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            final Document doc = comp.getDocument();
            if (doc.getLength() == 0) { Toolkit.getDefaultToolkit().beep(); return; }

            // On travaille depuis la position du caret (ignore la sélection)
            final int caret = Math.max(0, Math.min(comp.getCaretPosition(), doc.getLength()));
            final Element root = doc.getDefaultRootElement();
            final int paraIdx = Math.max(0, root.getElementIndex(caret));
            final Element paraEl = root.getElement(paraIdx);
            if (paraEl == null) { Toolkit.getDefaultToolkit().beep(); return; }

            final int start = paraEl.getStartOffset();
            // Limite supérieure = début du paragraphe suivant, ou fin de doc si dernier
            final int nextStart = (paraIdx + 1 < root.getElementCount())
                    ? root.getElement(paraIdx + 1).getStartOffset()
                    : doc.getLength();

            final int len = Math.max(0, nextStart - start);
            if (len == 0) { Toolkit.getDefaultToolkit().beep(); return; }

            // Supprimer le paragraphe (incluant son saut de ligne si présent)
            doc.remove(start, len);

            // Placer le caret au début du "nouveau" paragraphe à cette position
            comp.setCaretPosition(Math.min(start, doc.getLength()));
            comp.requestFocusInWindow();

        } catch (Exception ex) {
            Toolkit.getDefaultToolkit().beep();
        }
    }
}