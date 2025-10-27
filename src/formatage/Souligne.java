package formatage;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;

import writer.ui.EditorApi;

public class Souligne {
	
	private final EditorApi ctx;

    public Souligne(EditorApi ctx) {
        this.ctx = ctx;
    }
	
    public void  appliquer() {
    	JTextArea editor = ctx.getEditor();
    	
        final JTextComponent area = editor;
        if (area == null) return;

        try {
            Document doc = area.getDocument();
            int start = area.getSelectionStart();
            int end   = area.getSelectionEnd();

            // Pas de sélection ? on prend le mot sous le curseur
            if (start == end) {
                start = Utilities.getWordStart(area, area.getCaretPosition());
                end   = Utilities.getWordEnd(area,   area.getCaretPosition());
            }
            if (start < 0 || end <= start) return;

            String text = doc.getText(start, end - start);

            // Vérifie si déjà entouré par __
            boolean hasPrefix = (start >= 2) &&
                    doc.getText(start - 2, 2).equals("__");
            boolean hasSuffix = (end + 2 <= doc.getLength()) &&
                    doc.getText(end, 2).equals("__");

            if (hasPrefix && hasSuffix) {
                // Retirer les __ (suffixe d'abord, puis préfixe)
                replace(doc, end, end + 2, "");      // enlève __ après
                replace(doc, start - 2, start, "");  // enlève __ avant
                area.select(start - 2, end - 2);     // sélectionne le texte “nu”
            } else {
                // Ajouter __ autour de la sélection
                String wrapped = "__" + text + "__";
                replace(doc, start, end, wrapped);
                area.select(start, start + wrapped.length()); // inclut les délimiteurs
            }
        } catch (Exception ex) {
            System.err.println("Souligne __ : " + ex.getMessage());
        }
    }

    private static void replace(Document doc, int start, int end, String s) throws BadLocationException {
        int len = Math.max(0, end - start);
        if (len > 0) doc.remove(start, len);
        if (s != null && !s.isEmpty()) doc.insertString(start, s, null);
    }
}
