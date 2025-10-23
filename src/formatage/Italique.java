package formatage;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;
import writer.blindWriter;

public class Italique {
    public Italique() {
        final JTextComponent area = blindWriter.editorPane;
        if (area == null) return;

        try {
            Document doc = area.getDocument();
            int start = area.getSelectionStart();
            int end   = area.getSelectionEnd();

            // No selection? toggle the word under the caret
            if (start == end) {
                start = Utilities.getWordStart(area, area.getCaretPosition());
                end   = Utilities.getWordEnd(area,   area.getCaretPosition());
            }
            if (start < 0 || end <= start) return;

            String text = doc.getText(start, end - start);

            boolean hasPrefix = (start >= 2) &&
                    doc.getText(start - 2, 2).equals("^^");
            boolean hasSuffix = (end + 2 <= doc.getLength()) &&
                    doc.getText(end, 2).equals("^^");

            if (hasPrefix && hasSuffix) {
                // Remove ^^ (suffix first, then prefix) using Document ops
                replace(doc, end, end + 2, "");      // remove suffix
                replace(doc, start - 2, start, "");  // remove prefix
                // select the unwrapped text
                area.select(start - 2, end - 2);
            } else {
                // Add ^^ around the selection
                String wrapped = "^^" + text + "^^";
                replace(doc, start, end, wrapped);
                area.select(start, start + wrapped.length());
            }
        } catch (Exception ex) {
            System.err.println("Italique ^^ : " + ex.getMessage());
        }
    }

    private static void replace(Document doc, int start, int end, String s) throws BadLocationException {
        int len = Math.max(0, end - start);
        if (len > 0) doc.remove(start, len);
        if (s != null && !s.isEmpty()) doc.insertString(start, s, null);
    }
}
