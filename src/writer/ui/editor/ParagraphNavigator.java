package writer.ui.editor;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import javax.swing.*;
import javax.swing.text.*;

/**
 * Navigation par paragraphes pour writer.ui.NormalizingTextPane :
 *  - ↓ : début du paragraphe suivant
 *  - ↑ : début du paragraphe précédent
 *  - Shift+↓ / Shift+↑ : idem en étendant la sélection
 */
public final class ParagraphNavigator {

    private ParagraphNavigator() {}

    /** Installe les raccourcis ↑/↓ (et Shift+↑/↓) sur l'éditeur. */
    @SuppressWarnings("serial")
	public static void install(writer.ui.NormalizingTextPane editor) {
        InputMap im = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = editor.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "bw-next-paragraph-start");
        am.put("bw-next-paragraph-start", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                goToNextParagraphStart(editor, false);
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "bw-prev-paragraph-start");
        am.put("bw-prev-paragraph-start", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                goToPrevParagraphStart(editor, false);
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK), "bw-select-next-paragraph-start");
        am.put("bw-select-next-paragraph-start", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                goToNextParagraphStart(editor, true);
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK), "bw-select-prev-paragraph-start");
        am.put("bw-select-prev-paragraph-start", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                goToPrevParagraphStart(editor, true);
            }
        });
    }

    /** Déplace (ou étend) la sélection au début du paragraphe suivant. */
    public static void goToNextParagraphStart(writer.ui.NormalizingTextPane ed, boolean extendSelection) {
        Document doc = ed.getDocument();
        Element root = doc.getDefaultRootElement();
        int idx = currentParagraphIndex(ed);
        int target = Math.min(idx + 1, root.getElementCount() - 1);
        gotoParagraphStart(ed, target, extendSelection);
    }

    /** Déplace (ou étend) la sélection au début du paragraphe précédent. */
    public static void goToPrevParagraphStart(writer.ui.NormalizingTextPane ed, boolean extendSelection) {
        int idx = currentParagraphIndex(ed);
        int target = Math.max(idx - 1, 0);
        gotoParagraphStart(ed, target, extendSelection);
    }

    // ====== internes ======

    private static int currentParagraphIndex(writer.ui.NormalizingTextPane ed) {
        Document doc = ed.getDocument();
        Element root = doc.getDefaultRootElement();
        int pos = Math.max(0, Math.min(ed.getCaretPosition(), doc.getLength()));
        return root.getElementIndex(pos);
    }

    private static void gotoParagraphStart(writer.ui.NormalizingTextPane ed, int targetParaIndex, boolean extendSelection) {
        try {
            Document doc = ed.getDocument();
            Element root = doc.getDefaultRootElement();
            if (root.getElementCount() == 0) return;

            int idx = Math.max(0, Math.min(targetParaIndex, root.getElementCount() - 1));
            int pos = root.getElement(idx).getStartOffset();

            if (extendSelection) ed.moveCaretPosition(pos);
            else ed.setCaretPosition(pos);

            Rectangle2D r2 = ed.modelToView2D(pos);
            if (r2 != null) ed.scrollRectToVisible(new Rectangle(r2.getBounds()));
        } catch (BadLocationException ignore) {}
    }
}