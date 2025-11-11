package act;

import java.awt.event.ActionEvent;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;

import writer.ui.EditorFrame;
import writer.ui.editor.FastHighlighter;

@SuppressWarnings("serial")
public class removeLinks extends AbstractAction {
    private final writer.ui.NormalizingTextPane editorPane;
    private final EditorFrame frame;

    // @[texte : URL]
    private static final Pattern LINK  = Pattern.compile("@\\[[^\\]:]+\\s*:\\s*https?://[^\\]]+\\]");

    // [Lien numéro]  → accepte [Lien1], [Lien 2], [lien3]…
    private static final Pattern TOKEN = Pattern.compile("\\[(?:Lien|lien)\\s*\\d+]");

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
            if (len == 0) return;

            final String text = doc.getText(0, len);

            // 1) supprime @[…] puis [LienN]
            String cleaned = LINK.matcher(text).replaceAll("");
            cleaned = TOKEN.matcher(cleaned).replaceAll("");

            // 2) petite normalisation des espaces/lignes après suppression
            cleaned = cleaned
                    .replaceAll("[ \\t]{2,}", " ")          // compresse espaces multiples
                    .replaceAll("[ \\t]+(\\R)", "$1")       // supprime espace en fin de ligne
                    .replaceAll("(\\R){3,}", "$1$1");       // max 2 sauts d’affilée

            if (cleaned.equals(text)) return; // rien n’a changé

            try { editorPane.getHighlighter().removeAllHighlights(); } catch (Exception ignore) {}

            // 3) ÉDITION SANS HISTORIQUE
            final String newText = cleaned;   // copie finale pour la lambda

            frame.runWithoutUndo(() -> {
                try {
                    if (doc instanceof AbstractDocument ad) {
                        ad.replace(0, len, newText, null); // remplace en un seul coup
                    } else {
                        doc.remove(0, len);
                        doc.insertString(0, newText, null);
                    }
                    // purge la map des liens tokenisés (les [LienN] ont disparu)
                    doc.putProperty(writer.ui.editor.LinkTokenIndexer.PROP_LINK_MAP, null);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            // 4) caret + recolorisation (sans historique)
            SwingUtilities.invokeLater(() -> {
                try {
                    int L = editorPane.getDocument().getLength();
                    editorPane.setCaretPosition(L == 0 ? 0 : Math.min(1, L)); // évite pos 0 si tu as cette contrainte
                    editorPane.requestFocusInWindow();

                    frame.runWithoutUndo(() -> FastHighlighter.rehighlightAll(editorPane));
                } catch (Exception ignore) {}
            });

            System.out.println("✅ Tous les liens (@[…] et [LienN]) ont été supprimés du texte (sans historique).");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}