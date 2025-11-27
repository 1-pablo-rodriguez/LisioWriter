package writer.ui.editor;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.text.*;

@SuppressWarnings("serial")
public class EnterPiedDeMoucheInsertAction extends AbstractAction {
    private static final char   BRAILLE_CH = '\u00B6';
    private static final String BRAILLE    = String.valueOf(BRAILLE_CH);

    private final writer.ui.NormalizingTextPane editor;
    private final Action fallback;
    @SuppressWarnings("unused")
	private final boolean withSpace;
    private final String head;   // "¶" ou "¶ "
    private final String insert; // "\n¶" ou "\n¶ "

    public EnterPiedDeMoucheInsertAction(writer.ui.NormalizingTextPane editor, Action fallback, boolean withTrailingSpace) {
        this.editor = editor;
        this.fallback = fallback;
        this.withSpace = withTrailingSpace;
        this.head = withTrailingSpace ? BRAILLE + " " : BRAILLE;
        this.insert = "\n" + head;
        putValue(NAME, "BrailleInsert");
    }

    public EnterPiedDeMoucheInsertAction(writer.ui.NormalizingTextPane editor) {
        this(editor, null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final Document doc = editor.getDocument();
        try {
            // 1) Sélection : remplacer en 1 coup (plus rapide que remove+insert)
            int selStart = editor.getSelectionStart();
            int selEnd   = editor.getSelectionEnd();
            if (selStart != selEnd) {
                if (doc instanceof AbstractDocument ad) {
                    ad.replace(selStart, selEnd - selStart, "", null);
                } else {
                    doc.remove(selStart, selEnd - selStart);
                }
                editor.setCaretPosition(selStart);
            }

            int caret = editor.getCaretPosition();
            final int len = doc.getLength();

            // 2) Début de doc : une seule insertion (¶\n[¶])
            if (caret == 0) {
                boolean hadLeadingBraille = (len > 0) && (charAt(doc, 0) == BRAILLE_CH);
                String toInsert = hadLeadingBraille ? head + "\n" : head + "\n" + head;
                doc.insertString(0, toInsert, null);
                editor.setCaretPosition(head.length());
                return;
            }

            // 3) Caret entre fin de ligne et ¶ (préserve CRLF)
            if (caret > 0 && caret < len) {
                char prev = charAt(doc, caret - 1);
                char next = charAt(doc, caret);

                if (prev == '\n' && next == BRAILLE_CH) {
                    String sep = (caret >= 2 && charAt(doc, caret - 2) == '\r') ? "\r\n" : "\n";
                    doc.insertString(caret, head + sep, null);
                    editor.setCaretPosition(caret + head.length() + sep.length());
                    return;
                }

                if (prev == '\r' && caret + 1 < len) {
                    char c0 = charAt(doc, caret);     // \n ?
                    char c1 = (caret + 1 < len) ? charAt(doc, caret + 1) : 0; // ¶ ?
                    if (c0 == '\n' && c1 == BRAILLE_CH) {
                        doc.insertString(caret, head + "\r\n", null);
                        editor.setCaretPosition(caret + head.length() + 2);
                        return;
                    }
                }
            }

            // 4) Cas général : un insert simple
            doc.insertString(caret, insert, null);
            editor.setCaretPosition(caret + insert.length());

        } catch (BadLocationException ex) {
            if (fallback != null) fallback.actionPerformed(e);
        }
    }

    private static char charAt(Document d, int pos) throws BadLocationException {
        Segment s = new Segment();
        d.getText(pos, 1, s);
        return s.array[s.offset];
    }

    public static EnterPiedDeMoucheInsertAction createWithDefaultFallback(writer.ui.NormalizingTextPane editor, boolean withTrailingSpace) {
        Action def = editor.getActionMap().get(DefaultEditorKit.insertBreakAction);
        return new EnterPiedDeMoucheInsertAction(editor, def, withTrailingSpace);
    }
}