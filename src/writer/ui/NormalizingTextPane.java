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
        // Normalise le texte inséré via replaceSelection (coller, etc.)
        //super.replaceSelection(norm(content));
   	 super.replaceSelection(content);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
    	// Pour forcer à prendre toute la largeur de l'éditeur
//    	if (getParent() instanceof javax.swing.JViewport viewport) {
//            return getUI().getPreferredSize(this).width <= viewport.getWidth();
//        }
        // Désactive le suivi de la largeur du viewport → pas de wrap visuel
        return false;
    }

    
    /**
     * Nettoie le Document courant en remplaçant son contenu par une version
     * normalisée (CR/CRLF -> LF). Utile pour rendre offsets et modelToView
     * cohérents. Appeler avec précaution (peut déclencher DocumentFilter).
     */
    public void normalizeDocumentContent() {
        Document doc = getDocument();
        try {
            String original = doc.getText(0, doc.getLength());
            String normalized = (original == null) ? null : norm(original);
            if (normalized == null) normalized = "";

            if (!normalized.equals(original)) {
                int oldCaret = getCaretPosition();

                // Remplacement "safe" : retirer tout puis insérer la version normalisée
                // (si tu as un DocumentFilter qui modifie le texte, tu peux devoir
                // adapter cette approche pour déléguer proprement).
                doc.remove(0, doc.getLength());
                doc.insertString(0, normalized, null);

                // Restituer un caret valide
                int newCaret = Math.max(0, Math.min(oldCaret, doc.getLength()));
                setCaretPosition(newCaret);
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Installe un DocumentFilter qui normalise toute insertion/remplacement.
     * Recommandé si tu veux empêcher l'apparition de CR dans le Document.
     */
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
        // Pendant le super-constructeur, stickyUndoListeners peut être null → on fait simple.
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
        // lazy init au cas où (sécurité si constructeur super a déjà appelé setDocument)
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
