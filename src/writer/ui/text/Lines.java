package writer.ui.text;

import javax.swing.text.*;

/*
 * Class permettant de placer le cursseur dans le document.
 */
public final class Lines {

    private Lines() {}

    /** Nombre de lignes (éléments) dans le Document. */
    public static int getLineCount(JTextComponent c) {
        Document doc = c.getDocument();
        Element root = doc.getDefaultRootElement();
        return root.getElementCount();
    }

    /** Index de la ligne (0-based) qui contient l’offset donné. */
    public static int getLineOfOffset(JTextComponent c, int offset) throws BadLocationException {
        Document doc = c.getDocument();
        int len = doc.getLength();
        if (offset < 0 || offset > len) throw new BadLocationException("offset", offset);
        Element root = doc.getDefaultRootElement();
        return root.getElementIndex(offset);
    }

    /** Offset du début de la ligne (0-based). */
    public static int getLineStartOffset(JTextComponent c, int line) throws BadLocationException {
        Element root = c.getDocument().getDefaultRootElement();
        if (line < 0 || line >= root.getElementCount()) throw new BadLocationException("line", line);
        return root.getElement(line).getStartOffset();
    }

    /** Offset de fin de ligne (attention : souvent = position après le '\n'). */
    public static int getLineEndOffset(JTextComponent c, int line) throws BadLocationException {
        Element root = c.getDocument().getDefaultRootElement();
        if (line < 0 || line >= root.getElementCount()) throw new BadLocationException("line", line);
        return root.getElement(line).getEndOffset();
    }

    /** Ligne courante (0-based) du caret. */
    public static int getCaretLine(JTextComponent c) {
        try {
            return getLineOfOffset(c, c.getCaretPosition());
        } catch (BadLocationException e) {
            return 0;
        }
    }

    /** Colonne vis-à-vis du début de la ligne. */
    public static int getCaretColumn(JTextComponent c) {
        try {
            int caret = c.getCaretPosition();
            int line = getLineOfOffset(c, caret);
            int start = getLineStartOffset(c, line);
            return Math.max(0, caret - start);
        } catch (BadLocationException e) {
            return 0;
        }
    }

    /** Va au début de la ligne courante. */
    public static void moveCaretToLineStart(JTextComponent c) {
        try {
            int line = getCaretLine(c);
            c.setCaretPosition(getLineStartOffset(c, line));
        } catch (BadLocationException ignore) {}
    }

    /** Va à la fin logique de la ligne courante (juste avant le '\n' s’il existe). */
    public static void moveCaretToLineEnd(JTextComponent c) {
        try {
            int line = getCaretLine(c);
            int end = getLineEndOffset(c, line);
            // Heuristique : si le doc a un '\n' à end-1, place le caret avant.
            int pos = Math.max(0, Math.min(end - 1, c.getDocument().getLength()));
            c.setCaretPosition(pos);
        } catch (BadLocationException ignore) {}
    }
    
    /**
     * Remplace le texte de start (inclus) à end (exclu) par 'str' comme JTextArea.replaceRange.
     * - Si str est null, on insère une chaîne vide.
     * - Met le caret à start + str.length() et efface la sélection.
     */
    public static void replaceRange(JTextComponent c, String str, int start, int end) {
        if (c == null) return;
        String s = (str == null) ? "" : str;

        Document doc = c.getDocument();
        int len = doc.getLength();

        // Normalisation des bornes
        int a = Math.max(0, Math.min(start, end));
        int b = Math.max(0, Math.max(start, end));
        a = Math.min(a, len);
        b = Math.min(b, len);

        try {
            // Équivalent replace: remove + insert (JTextComponent n'a pas replaceRange)
            if (b > a) doc.remove(a, b - a);
            if (!s.isEmpty()) doc.insertString(a, s, null);

            // Position du caret comme JTextArea.replaceRange
            c.setCaretPosition(a + s.length());
        } catch (BadLocationException ex) {
            // Garde-fou: en cas d’indices invalides, on bip sans casser l'app
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    /**
     * Remplace la sélection courante par 'str' (comme replaceSelection de JTextComponent),
     * mais utilisable partout via Lines.* avec la même API.
     */
    public static void replaceSelection(JTextComponent c, String str) {
        if (c == null) return;
        int a = Math.min(c.getSelectionStart(), c.getSelectionEnd());
        int b = Math.max(c.getSelectionStart(), c.getSelectionEnd());
        replaceRange(c, str, a, b);
    }
    
    
    /**
     * Insère 'str' à la position 'pos' comme JTextArea.insert.
     * - Si str est null, rien n’est inséré.
     * - Le caret est placé juste après le texte inséré.
     */
    public static void insert(JTextComponent c, String str, int pos) {
        if (c == null || str == null || str.isEmpty()) return;

        Document doc = c.getDocument();
        int len = doc.getLength();
        int p = Math.max(0, Math.min(pos, len));

        try {
            doc.insertString(p, str, null);
            c.setCaretPosition(p + str.length());
        } catch (BadLocationException ex) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    /**
     * Ajoute 'str' à la fin du document (équivalent simple de JTextArea.append).
     * Place le caret après le texte ajouté.
     */
    public static void append(JTextComponent c, String str) {
        if (c == null || str == null || str.isEmpty()) return;
        Document doc = c.getDocument();
        insert(c, str, doc.getLength());
    }

    /**
     * Supprime la plage [start, end) si elle est valide.
     * Le caret est placé à 'start'.
     */
    public static void deleteRange(JTextComponent c, int start, int end) {
        if (c == null) return;
        Document doc = c.getDocument();
        int len = doc.getLength();
        int a = Math.max(0, Math.min(start, end));
        int b = Math.max(0, Math.max(start, end));
        a = Math.min(a, len);
        b = Math.min(b, len);
        if (b <= a) return;

        try {
            doc.remove(a, b - a);
            c.setCaretPosition(a);
        } catch (BadLocationException ex) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    /**
     * Récupère le texte de [start, end) en sécurité.
     */
    public static String getTextRange(JTextComponent c, int start, int end) {
        if (c == null) return "";
        Document doc = c.getDocument();
        int len = doc.getLength();
        int a = Math.max(0, Math.min(start, end));
        int b = Math.max(0, Math.max(start, end));
        a = Math.min(a, len);
        b = Math.min(b, len);
        if (b <= a) return "";
        try {
            return doc.getText(a, b - a);
        } catch (BadLocationException ex) {
            return "";
        }
    }
    
}