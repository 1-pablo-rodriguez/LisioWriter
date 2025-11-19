package styles;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import writer.ui.EditorApi;
import writer.ui.text.Lines;

public class bodyTexte {
    private static final char BRAILLE = '\u00B6';

    // ^\s*¶\s* → capture et normalise le préfixe braille
    private static final Pattern LEADING_BRAILLE = Pattern.compile("^\\s*\\u00B6\\s*");

    // Codes à supprimer en tête (après ¶)
    private static final Pattern HN_1_9 = Pattern.compile("^#([1-9])\\.\\s*");
    private static final Pattern HP     = Pattern.compile("^#P\\.\\s*");
    private static final Pattern HS     = Pattern.compile("^#S\\.\\s*");
    private static final Pattern BULLET = Pattern.compile("^-\\.\\s*");

    private final EditorApi ctx;

    public bodyTexte(EditorApi ctx) { this.ctx = ctx; }

    public void appliquer() {
        try {
            var editor = ctx.getEditor();
            int caretPosition = editor.getCaretPosition();

            int line      = Lines.getLineOfOffset(editor, caretPosition);
            int lineStart = Lines.getLineStartOffset(editor, line);
            int lineEnd   = Lines.getLineEndOffset(editor, line);

            String raw      = editor.getText(lineStart, lineEnd - lineStart);
            String lineText = raw.replaceFirst("\\R$", ""); // travailler sans le \r?\n final

            // 1) Normalise ¶ en colonne 0
            String after;
            Matcher mLead = LEADING_BRAILLE.matcher(lineText);
            if (mLead.find()) {
                after = lineText.substring(mLead.end()); // contenu après le ¶ (+ espaces)
            } else {
                // pas de ¶ → on l’ajoutera à la reconstruction
                after = lineText;
            }

            // 2) Supprime tout code de tête (#n., #P., #S., -.) + espaces
            String newAfter = after;
            if (HN_1_9.matcher(newAfter).find()) {
                newAfter = HN_1_9.matcher(newAfter).replaceFirst("");
            } else if (HP.matcher(newAfter).find()) {
                newAfter = HP.matcher(newAfter).replaceFirst("");
            } else if (HS.matcher(newAfter).find()) {
                newAfter = HS.matcher(newAfter).replaceFirst("");
            } else if (BULLET.matcher(newAfter).find()) {
                newAfter = BULLET.matcher(newAfter).replaceFirst("");
            }
            // Nettoyage d’un éventuel surplus d’espaces en tête
            newAfter = newAfter.stripLeading();

            // 3) Recompose : garder uniquement ¶ (et le texte si présent)
            final String newLine = newAfter.isEmpty() ? String.valueOf(BRAILLE)
                                                      : BRAILLE + " " + newAfter;

            // 4) Restaure la fin de ligne d’origine
            String trailingNL = raw.endsWith("\r\n") ? "\r\n" : (raw.endsWith("\n") ? "\n" : "");
            String finalLine  = newLine + trailingNL;

            // 5) Applique
            Lines.replaceRange(editor, finalLine, lineStart, lineEnd);

            // Conserver la position du caret
            editor.setCaretPosition(Math.min(editor.getDocument().getLength(), caretPosition));

            sound();

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void sound() {
        ctx.showInfo("Corps de texte", "Paragraphe en corps de texte.");
    }
}