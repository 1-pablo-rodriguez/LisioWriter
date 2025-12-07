package writer.ui;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

@SuppressWarnings("serial")
public class NormalizingTextPane extends javax.swing.JTextPane {
    private static final Pattern EOL = Pattern.compile("\\r\\r?\\n|\\r");
    
    // Peut être null au tout début (super() pas fini) → on gère ça dans setDocument(...)
    private List<UndoableEditListener> stickyUndoListeners = new CopyOnWriteArrayList<>();

    // --- NOUVEAU : flag de wrap ---
    private boolean lineWrapEnabled = false;

    // --- NOUVEAU : setter public ---
    public void setLineWrap(boolean enabled) {
        this.lineWrapEnabled = enabled;
        // Recalcul de la mise en page
        revalidate();
        repaint();
    }

    // (optionnel si tu veux l'utiliser ailleurs)
    public boolean isLineWrapEnabled() {
        return lineWrapEnabled;
    }

    private static String norm(String s) {
        return (s == null) ? null : EOL.matcher(s).replaceAll("\n");
    }

    @Override
    public String getText() {
        String t = super.getText();
        return (t == null) ? null : norm(t);
    }

    @Override
    public String getText(int offset, int length) throws BadLocationException {
        String t = super.getText(offset, length);
        return (t == null) ? null : norm(t);
    }

    @Override
    public void setText(String t) {
        // Normalise avant d'écrire dans le document
        super.setText(norm(t));
    }

    @Override
    public void replaceSelection(String content) {
        //super.replaceSelection(norm(content));
        super.replaceSelection(content);
    }

    
    @Override
    public boolean getScrollableTracksViewportWidth() {
        // Si true → le composant suit la largeur du viewport → wrap
        // Si false → largeur préférée utilisée → scroll horizontal possible
        return lineWrapEnabled;
    }


    /**
     * Nettoie le Document courant...
     */
    public void normalizeDocumentContent() {
        Document doc = getDocument();
        try {
            String original = doc.getText(0, doc.getLength());
            String normalized = (original == null) ? null : norm(original);
            if (normalized == null) normalized = "";

            if (!normalized.equals(original)) {
                int oldCaret = getCaretPosition();

                doc.remove(0, doc.getLength());
                doc.insertString(0, normalized, null);

                int newCaret = Math.max(0, Math.min(oldCaret, doc.getLength()));
                setCaretPosition(newCaret);
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    public void installEOLDocumentFilter() {
        Document d = getDocument();
        if (d instanceof AbstractDocument) {
            ((AbstractDocument) d).setDocumentFilter(new javax.swing.text.DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offset, String string, javax.swing.text.AttributeSet attr)
                        throws BadLocationException {
                    super.insertString(fb, offset, norm(string), attr);
                }

                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs)
                        throws BadLocationException {
                    super.replace(fb, offset, length, norm(text), attrs);
                }

                @Override
                public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                    super.remove(fb, offset, length);
                }
            });
        }
    }

    @Override
    public void setDocument(Document newDoc) {
        if (stickyUndoListeners == null) {
            super.setDocument(newDoc);
            return;
        }
        Document old = getDocument();
        if (old != null) {
            for (UndoableEditListener l : stickyUndoListeners) {
                old.removeUndoableEditListener(l);
            }
        }
        super.setDocument(newDoc);
        if (newDoc != null) {
            for (UndoableEditListener l : stickyUndoListeners) {
                newDoc.addUndoableEditListener(l);
            }
        }
    }
    
    public void addStickyUndoableEditListener(UndoableEditListener l) {
        if (l == null) return;
        if (stickyUndoListeners == null) stickyUndoListeners = new CopyOnWriteArrayList<>();
        stickyUndoListeners.add(l);
        Document d = getDocument();
        if (d != null) d.addUndoableEditListener(l);
    }

    public void removeStickyUndoableEditListener(UndoableEditListener l) {
        if (l == null) return;
        if (stickyUndoListeners != null) stickyUndoListeners.remove(l);
        Document d = getDocument();
        if (d != null) d.removeUndoableEditListener(l);
    }
    
    
}
