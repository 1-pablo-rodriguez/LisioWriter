package styles;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import writer.ui.EditorApi;
import writer.ui.text.Lines;

public class titre1 {
    private static final char BRAILLE = '\u00B6';

    // ^\s*¶\s* → capture et normalise le préfixe braille
    private static final Pattern LEADING_BRAILLE = Pattern.compile("^\\s*\\u00B6\\s*");
    // tokens de tête à convertir en #1.
    private static final Pattern HN_2_9 = Pattern.compile("^#([2-9])\\.\\s*");
    private static final Pattern HP     = Pattern.compile("^#P\\.\\s*");
    private static final Pattern HS     = Pattern.compile("^#S\\.\\s*");
    private static final Pattern BULLET = Pattern.compile("^-\\.\\s*");
    private static final Pattern H1_ANY = Pattern.compile("^#1\\.\\s*"); // normalisation de #1.
    private static final Pattern NOT_H  = Pattern.compile("^(?!#).+");   // “pas un # en tête”

    private final EditorApi ctx;

    public titre1(EditorApi ctx) { this.ctx = ctx; }

    public void appliquer() {
        try {
            var editor = ctx.getEditor();
            int caretPosition = editor.getCaretPosition();

            int line = Lines.getLineOfOffset(editor, caretPosition);
            int lineStart = Lines.getLineStartOffset(editor, line);
            int lineEnd   = Lines.getLineEndOffset(editor, line);

            String raw = editor.getText(lineStart, lineEnd - lineStart);
            // Retire un éventuel \r ou \n final pour travailler “au propre”
            String lineText = raw.replaceFirst("\\R$", "");

            // --- Normalisation du préfixe braille en colonne 0 (sans espace derrière)
            String after; // contenu après le ¶ normalisé
            Matcher mLead = LEADING_BRAILLE.matcher(lineText);
            if (mLead.find()) {
                after = lineText.substring(mLead.end()); // tout ce qui suit le(s) ¶ + espaces
            } else {
                after = lineText; // pas de ¶ : on l’ajoutera à la reconstruction
            }

            // --- Si la ligne ne contient QUE le ¶ (ou ¶ + espaces), on force "#1. "
            String newAfter;
            if (after.strip().isEmpty()) {
                newAfter = "#1. ";
            } else if (HN_2_9.matcher(after).find()) {
                newAfter = HN_2_9.matcher(after).replaceFirst("#1. ");
            } else if (HP.matcher(after).find()) {
                newAfter = HP.matcher(after).replaceFirst("#1. ");
            } else if (HS.matcher(after).find()) {
                newAfter = HS.matcher(after).replaceFirst("#1. ");
            } else if (BULLET.matcher(after).find()) {
                newAfter = BULLET.matcher(after).replaceFirst("#1. ");
            } else if (H1_ANY.matcher(after).find()) {
                // normalise "#1." -> "#1. "
                newAfter = H1_ANY.matcher(after).replaceFirst("#1. ");
            } else if (NOT_H.matcher(after).find()) {
                // ne commence pas par # → on préfixe
                newAfter = "#1. " + after.stripLeading();
            } else {
                // déjà propre
                newAfter = after;
            }

            // --- Recompose la ligne en forçant le ¶ tout au début
            String newLine = BRAILLE + newAfter;

            // Si la ligne d’origine finissait par \r?\n, on le remet
            String trailingNL = raw.endsWith("\r\n") ? "\r\n" : (raw.endsWith("\n") ? "\n" : "");
            String finalLine = newLine + trailingNL;

            // Applique
            Lines.replaceRange(editor, finalLine, lineStart, lineEnd);

            // Restaure la position du caret (option simple)
            editor.setCaretPosition(Math.min(editor.getDocument().getLength(), caretPosition));

            sound();

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void sound() {
        ctx.showInfo("Titre 1", "Paragraphe en Titre 1");
    }
}