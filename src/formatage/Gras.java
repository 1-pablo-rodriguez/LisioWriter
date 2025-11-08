package formatage;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Utilities;

import writer.ui.EditorApi;

public class Gras {
	private final EditorApi ctx;

    public Gras(EditorApi ctx) {
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

            // Si aucune sélection : prendre le mot sous le curseur
            if (start == end) {
                start = Utilities.getWordStart(area, area.getCaretPosition());
                end   = Utilities.getWordEnd(area,   area.getCaretPosition());
            }
            if (start < 0 || end <= start) return;

            // Facultatif : ne pas englober les espaces en bout de sélection
            int s = trimLeft(doc, start, end);
            int e = trimRight(doc, start, end);
            if (e <= s) return;

            String text = doc.getText(s, e - s);

            final String MARK = "**";
            final int L = MARK.length();

            boolean hasPrefix = (s >= L) &&
                    doc.getText(s - L, L).equals(MARK);
            boolean hasSuffix = (e + L <= doc.getLength()) &&
                    doc.getText(e, L).equals(MARK);

            if (hasPrefix && hasSuffix) {
                // --- Retirer ** ... ** (suffixe d'abord, puis préfixe) ---
                replace(doc, e, e + L, "");      // enlève ** après
                replace(doc, s - L, s, "");      // enlève ** avant
                // Rétablir la sélection exactement sur le texte « nu »
                area.select(s - L, e - L);
            } else {
                // --- Ajouter ** ... ** ---
                String wrapped = MARK + text + MARK;
                replace(doc, s, e, wrapped);
                // Sélectionner l'ensemble (marqueurs inclus)
                area.select(s, s + wrapped.length());
            }
        } catch (Exception ex) {
            System.err.println("Gras ** : " + ex.getMessage());
        }
    }

    // Remplace [start, end) par s dans le Document
    private static void replace(Document doc, int start, int end, String s) throws BadLocationException {
        int len = Math.max(0, end - start);
        if (len > 0) doc.remove(start, len);
        if (s != null && !s.isEmpty()) doc.insertString(start, s, null);
    }

    // Évite d’englober les espaces au début
    private static int trimLeft(Document doc, int start, int end) throws BadLocationException {
        int i = start;
        while (i < end) {
            char c = doc.getText(i, 1).charAt(0);
            if (!Character.isWhitespace(c)) break;
            i++;
        }
        return i;
    }

    // Évite d’englober les espaces à la fin
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
