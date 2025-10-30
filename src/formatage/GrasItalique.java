package formatage;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;

import writer.ui.EditorApi;

public class GrasItalique {
	private final EditorApi ctx;

    public GrasItalique(EditorApi ctx) {
        this.ctx = ctx;
    }
    public void appliquer() {
    	JTextComponent editor = ctx.getEditor();
    	
        final JTextComponent area = editor;
        if (area == null) return;

        try {
            Document doc = area.getDocument();
            int start = area.getSelectionStart();
            int end   = area.getSelectionEnd();

            // Si aucune sélection, prendre le mot sous le curseur
            if (start == end) {
                start = Utilities.getWordStart(area, area.getCaretPosition());
                end   = Utilities.getWordEnd(area,   area.getCaretPosition());
            }
            if (start < 0 || end <= start) return;

            String text = doc.getText(start, end - start);

            // Délimiteurs asymétriques
            final String PREFIX = "*^";
            final String SUFFIX = "^*";
            final int L = 2; // longueur des deux délimiteurs

            boolean hasPrefix = (start >= L) &&
                    doc.getText(start - L, L).equals(PREFIX);
            boolean hasSuffix = (end + L <= doc.getLength()) &&
                    doc.getText(end, L).equals(SUFFIX);

            if (hasPrefix && hasSuffix) {
                // --- Retirer *^ ... ^* (d'abord suffixe, puis préfixe) ---
                replace(doc, end, end + L, "");        // retire "^*"
                replace(doc, start - L, start, "");    // retire "*^"
                // Sélectionner le texte “nu”
                area.select(start - L, end - L);
            } else {
                // --- Ajouter *^ ... ^* ---
                String wrapped = PREFIX + text + SUFFIX;
                replace(doc, start, end, wrapped);
                // Inclure les marqueurs dans la sélection (optionnel)
                area.select(start, start + wrapped.length());
            }
        } catch (Exception ex) {
            System.err.println("GraItalique *^ ^* : " + ex.getMessage());
        }
    }

    private static void replace(Document doc, int start, int end, String s) throws BadLocationException {
        int len = Math.max(0, end - start);
        if (len > 0) doc.remove(start, len);
        if (s != null && !s.isEmpty()) doc.insertString(start, s, null);
    }
}
