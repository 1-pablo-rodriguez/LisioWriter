package styles;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import writer.ui.EditorApi;
import writer.ui.text.Lines;

public class listeNonNumero {
    private static final char BRAILLE = '\u283F';

    // ^\s*⠿\s* → capture et normalise le préfixe braille
    private static final Pattern LEADING_BRAILLE = Pattern.compile("^\\s*\\u283F\\s*");

    // Tokens à convertir en "-. "
    private static final Pattern HN_1_9 = Pattern.compile("^#([1-9])\\.\\s*");
    private static final Pattern HP     = Pattern.compile("^#P\\.\\s*");
    private static final Pattern HS     = Pattern.compile("^#S\\.\\s*");
    private static final Pattern BULLET_ANY = Pattern.compile("^-\\.\\s*"); // normalisation de la puce
    private static final Pattern NOT_MARKED = Pattern.compile("^(?!#|-\\.).+"); // ne commence pas par # ou -.

    private final EditorApi ctx;

    public listeNonNumero(EditorApi ctx) { this.ctx = ctx; }

    public void appliquer() {
        try {
            var editor = ctx.getEditor();
            int caretPosition = editor.getCaretPosition();

            int line      = Lines.getLineOfOffset(editor, caretPosition);
            int lineStart = Lines.getLineStartOffset(editor, line);
            int lineEnd   = Lines.getLineEndOffset(editor, line);

            String raw      = editor.getText(lineStart, lineEnd - lineStart);
            String lineText = raw.replaceFirst("\\R$", ""); // travailler sans le \r?\n final

            // --- Normaliser le préfixe ⠿ (colonne 0, sans espace derrière)
            String after;
            Matcher mLead = LEADING_BRAILLE.matcher(lineText);
            if (mLead.find()) {
                after = lineText.substring(mLead.end()); // après ⠿ + espaces
            } else {
                after = lineText; // pas de ⠿ → on l’ajoutera à la reconstruction
            }

            // --- Forcer / normaliser "-. "
            String newAfter;
            if (after.strip().isEmpty()) {
                // ligne contenant seulement ⠿ (+ espaces)
                newAfter = "-. ";
            } else if (HN_1_9.matcher(after).find()) {
                newAfter = HN_1_9.matcher(after).replaceFirst("-. ");
            } else if (HP.matcher(after).find()) {
                newAfter = HP.matcher(after).replaceFirst("-. ");
            } else if (HS.matcher(after).find()) {
                newAfter = HS.matcher(after).replaceFirst("-. ");
            } else if (BULLET_ANY.matcher(after).find()) {
                // normaliser "-." → "-. "
                newAfter = BULLET_ANY.matcher(after).replaceFirst("-. ");
            } else if (NOT_MARKED.matcher(after).find()) {
                // pas de balise en tête → préfixer
                newAfter = "-. " + after.stripLeading();
            } else {
                newAfter = after; // déjà propre
            }

            // --- Recomposer avec ⠿ en tout début
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
        ctx.showInfo("Liste", "Paragraphe en liste non numérotée.");
    }
}