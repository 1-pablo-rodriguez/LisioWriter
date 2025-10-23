package formatage;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;
import writer.blindWriter;

public class Indice {
    public Indice() {
        final JTextComponent area = blindWriter.editorPane;
        if (area == null) return;

        try {
            Document doc = area.getDocument();
            int start = area.getSelectionStart();
            int end   = area.getSelectionEnd();

            // Aucune sélection ? prendre le mot sous le curseur
            if (start == end) {
                start = Utilities.getWordStart(area, area.getCaretPosition());
                end   = Utilities.getWordEnd(area,   area.getCaretPosition());
            }
            if (start < 0 || end <= start) return;

            // Ne pas englober les espaces
            int s = trimLeft(doc, start, end);
            int e = trimRight(doc, start, end);
            if (e <= s) return;

            String text = doc.getText(s, e - s);

            // Délimiteurs : _¨ ... ¨_
            final String PREFIX = "_\u00A8"; // "_¨"
            final String SUFFIX = "\u00A8_"; // "¨_"
            final int L = 2;

            boolean hasPrefix = (s >= L) &&
                    doc.getText(s - L, L).equals(PREFIX);
            boolean hasSuffix = (e + L <= doc.getLength()) &&
                    doc.getText(e, L).equals(SUFFIX);

            if (hasPrefix && hasSuffix) {
                // Retirer (suffixe d'abord)
                replace(doc, e, e + L, "");      // enlève "¨_"
                replace(doc, s - L, s, "");      // enlève "_¨"
                area.select(s - L, e - L);       // texte “nu”
            } else {
                // Ajouter
                String wrapped = PREFIX + text + SUFFIX;
                replace(doc, s, e, wrapped);
                area.select(s, s + wrapped.length()); // ou s+L .. -L si tu veux l’intérieur
            }
        } catch (Exception ex) {
            System.err.println("Indice _¨ ¨_ : " + ex.getMessage());
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
