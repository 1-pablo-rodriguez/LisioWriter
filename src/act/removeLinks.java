package act;

import java.awt.event.ActionEvent;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import writer.ui.EditorFrame;

@SuppressWarnings("serial")
public class removeLinks extends AbstractAction {
    private final JTextComponent editorPane;
    private final EditorFrame frame;

    // @[texte : URL]
    private static final Pattern LINK = Pattern.compile("@\\[[^\\]:]+\\s*:\\s*https?://[^\\]]+\\]");

    // Constructeur
    public removeLinks(writer.ui.NormalizingTextPane editorPane, EditorFrame frame) {
        super("removeLinks");
        this.editorPane = editorPane;
        this.frame = frame;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            final Document doc = editorPane.getDocument();
            final int len = doc.getLength();
            if (len == 0) return; // rien à faire, rien dans l’historique

            final String text = doc.getText(0, len);
            final String cleaned = LINK.matcher(text).replaceAll("");

            if (cleaned.equals(text)) return; // pas de changement -> pas d’édition

            // (optionnel) nettoyer les highlights pour éviter des offsets périmés
            try { editorPane.getHighlighter().removeAllHighlights(); } catch (Exception ignore) {}

            // --- ÉDITION SANS HISTORIQUE ---
            frame.runWithoutUndo(() -> {
                try {
                    if (doc instanceof AbstractDocument ad) {
                        ad.replace(0, len, cleaned, null); // une seule édition atomique
                    } else {
                        doc.remove(0, len);
                        doc.insertString(0, cleaned, null);
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            // Replacer le caret après relayout, de façon safe
            SwingUtilities.invokeLater(() -> {
                try {
                    int L = editorPane.getDocument().getLength();
                    editorPane.setCaretPosition(L == 0 ? 0 : 1);
                    editorPane.requestFocusInWindow();
                } catch (Exception ignore) {}
            });

            System.out.println("✅ Tous les liens ont été supprimés du texte (sans historique).");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}