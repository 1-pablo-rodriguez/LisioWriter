package formatage;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Utilities;

import writer.ui.EditorApi;

public class SouligneItalic {
	private final EditorApi ctx;

    public SouligneItalic(EditorApi ctx) {
        this.ctx = ctx;
    }
    public void appliquer() {
    	writer.ui.NormalizingTextPane editor = ctx.getEditor();
    	
        final writer.ui.NormalizingTextPane area = editor;
        if (area == null) return;

        try {
            Document doc = area.getDocument();
            int start = area.getSelectionStart();
            int end   = area.getSelectionEnd();

            // Aucune sélection ? prend le mot sous le curseur
            if (start == end) {
                start = Utilities.getWordStart(area, area.getCaretPosition());
                end   = Utilities.getWordEnd(area,   area.getCaretPosition());
            }
            if (start < 0 || end <= start) return;

            // Ne pas englober les espaces au bord de la sélection
            int s = trimLeft(doc, start, end);
            int e = trimRight(doc, start, end);
            if (e <= s) return;

            String text = doc.getText(s, e - s);

            final String PREFIX = "_^";
            final String SUFFIX = "^_";
            final int L = 2; // longueur de chaque délimiteur

            boolean hasPrefix = (s >= L) &&
                    doc.getText(s - L, L).equals(PREFIX);
            boolean hasSuffix = (e + L <= doc.getLength()) &&
                    doc.getText(e, L).equals(SUFFIX);

            if (hasPrefix && hasSuffix) {
                // Retirer ^_ puis _^ (suffixe d'abord)
                replace(doc, e, e + L, "");      // enlève "^_"
                replace(doc, s - L, s, "");      // enlève "_^"
                // Sélectionner le texte “nu”
                area.select(s - L, e - L);
            } else {
                // Ajouter _^ ... ^_
                String wrapped = PREFIX + text + SUFFIX;
                replace(doc, s, e, wrapped);
                // Sélection finale (avec marqueurs) :
                area.select(s, s + wrapped.length());
                // Si tu préfères ne sélectionner que l'intérieur :
                // area.select(s + L, s + wrapped.length() - L);
            }
        } catch (Exception ex) {
            System.err.println("SouligneItalic _^ ^_ : " + ex.getMessage());
        }
    }

    private static void replace(Document doc, int start, int end, String s) throws BadLocationException {
        int len = Math.max(0, end - start);
        if (len > 0) doc.remove(start, len);
        if (s != null && !s.isEmpty()) doc.insertString(start, s, null);
    }

    private static int trimLeft(Document doc, int start, int end) throws BadLocationException {
        int i = start;
        while (i < end) {
            char c = doc.getText(i, 1).charAt(0);
            if (!Character.isWhitespace(c)) break;
            i++;
        }
        return i;
    }

    private static int trimRight(Document doc, int start, int end) throws BadLocationException {
        int i = end;
        while (i > start) {
            char c = doc.getText(i - 1, 1).charAt(0);
            if (!Character.isWhitespace(c)) break;
            i--;
        }
        return i;
    }
}
