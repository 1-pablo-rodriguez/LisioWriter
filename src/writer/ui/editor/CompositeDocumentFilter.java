package writer.ui.editor;

import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Compose deux DocumentFilter dans l’ordre: first puis second. */
public final class CompositeDocumentFilter extends DocumentFilter {
    private final DocumentFilter first, second;

    public CompositeDocumentFilter(DocumentFilter first, DocumentFilter second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public void insertString(FilterBypass fb, int offset, String string, javax.swing.text.AttributeSet attr)
            throws BadLocationException {
        first.insertString(new BypassChain(fb, second), offset, string, attr);
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs)
            throws BadLocationException {
        first.replace(new BypassChain(fb, second), offset, length, text, attrs);
    }

    @Override
    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
        first.remove(new BypassChain(fb, second), offset, length);
    }

    // Petit bypass qui délègue toutes les opérations au second filter
    private static final class BypassChain extends DocumentFilter.FilterBypass {
        private final FilterBypass fb;
        private final DocumentFilter next;

        BypassChain(FilterBypass fb, DocumentFilter next) {
            this.fb = fb;
            this.next = next;
        }

        @Override public javax.swing.text.Document getDocument() { return fb.getDocument(); }
        @Override public void remove(int offset, int length) throws BadLocationException { next.remove(fb, offset, length); }
        @Override public void insertString(int offset, String string, javax.swing.text.AttributeSet attr) throws BadLocationException { next.insertString(fb, offset, string, attr); }
        @Override public void replace(int offset, int length, String text, javax.swing.text.AttributeSet attrs) throws BadLocationException { next.replace(fb, offset, length, text, attrs); }
    }
}