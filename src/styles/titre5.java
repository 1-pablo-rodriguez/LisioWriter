package styles;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import writer.ui.EditorApi;
import writer.ui.text.Lines;

public class titre5 {
    private static final char BRAILLE = '\u00B6';

    // ^\s*¶\s* → capture et normalise le préfixe braille
    private static final Pattern LEADING_BRAILLE = Pattern.compile("^\\s*\\u00B6\\s*");
    // tokens à convertir en #5.
    private static final Pattern HN_6_9 = Pattern.compile("^#([6-9])\\.\\s*");
    private static final Pattern H1     = Pattern.compile("^#1\\.\\s*");
    private static final Pattern H2     = Pattern.compile("^#2\\.\\s*");
    private static final Pattern H3     = Pattern.compile("^#3\\.\\s*");
    private static final Pattern H4     = Pattern.compile("^#4\\.\\s*");
    private static final Pattern HP     = Pattern.compile("^#P\\.\\s*");
    private static final Pattern HS     = Pattern.compile("^#S\\.\\s*");
    private static final Pattern BULLET = Pattern.compile("^-\\.\\s*");
    private static final Pattern H5_ANY = Pattern.compile("^#5\\.\\s*"); // normalisation #5.
    private static final Pattern NOT_H  = Pattern.compile("^(?!#).+");   // ne commence pas par '#'

    private final EditorApi ctx;

    public titre5(EditorApi ctx) { this.ctx = ctx; }

    public void appliquer() {
        try {
            var editor = ctx.getEditor();
            int caretPosition = editor.getCaretPosition();

            int line      = Lines.getLineOfOffset(editor, caretPosition);
            int lineStart = Lines.getLineStartOffset(editor, line);
            int lineEnd   = Lines.getLineEndOffset(editor, line);

            String raw      = editor.getText(lineStart, lineEnd - lineStart);
            String lineText = raw.replaceFirst("\\R$", ""); // travailler sans le \r?\n final

            // --- Normaliser le préfixe ¶ (colonne 0, sans espace derrière)
            String after;
            Matcher mLead = LEADING_BRAILLE.matcher(lineText);
            if (mLead.find()) {
                after = lineText.substring(mLead.end()); // ce qui suit le ¶ + espaces
            } else {
                after = lineText; // pas de ¶ → on l’ajoutera à la reconstruction
            }

            // --- Forcer / normaliser #5.
            String newAfter;
            if (after.strip().isEmpty()) {
                // ligne contenant seulement ¶ (+ espaces)
                newAfter = "#5. ";
            } else if (HN_6_9.matcher(after).find()) {
                newAfter = HN_6_9.matcher(after).replaceFirst("#5. ");
            } else if (H1.matcher(after).find()) {
                newAfter = H1.matcher(after).replaceFirst("#5. ");
            } else if (H2.matcher(after).find()) {
                newAfter = H2.matcher(after).replaceFirst("#5. ");
            } else if (H3.matcher(after).find()) {
                newAfter = H3.matcher(after).replaceFirst("#5. ");
            } else if (H4.matcher(after).find()) {
                newAfter = H4.matcher(after).replaceFirst("#5. ");
            } else if (HP.matcher(after).find()) {
                newAfter = HP.matcher(after).replaceFirst("#5. ");
            } else if (HS.matcher(after).find()) {
                newAfter = HS.matcher(after).replaceFirst("#5. ");
            } else if (BULLET.matcher(after).find()) {
                newAfter = BULLET.matcher(after).replaceFirst("#5. ");
            } else if (H5_ANY.matcher(after).find()) {
                // normaliser "#5." -> "#5. "
                newAfter = H5_ANY.matcher(after).replaceFirst("#5. ");
            } else if (NOT_H.matcher(after).find()) {
                // pas de balise en tête → préfixer
                newAfter = "#5. " + after.stripLeading();
            } else {
                newAfter = after; // déjà propre
            }

            // --- Recomposer avec ¶ en tout début
            String newLine = BRAILLE + newAfter;

            // Restaurer la fin de ligne d'origine
            String trailingNL = raw.endsWith("\r\n") ? "\r\n" : (raw.endsWith("\n") ? "\n" : "");
            String finalLine  = newLine + trailingNL;

            // Appliquer la modification
            Lines.replaceRange(editor, finalLine, lineStart, lineEnd);

            // Conserver la position du caret
            editor.setCaretPosition(Math.min(editor.getDocument().getLength(), caretPosition));

            sound();

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void sound() {
        ctx.showInfo("Titre 5", "Paragraphe en Titre 5");
    }
}