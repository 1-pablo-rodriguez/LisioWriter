package writer.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Permet de supprimer les paragraphes vides.
 * @author pabr6
 *
 */
public final class TextCleaningUtils {

    // Ligne ne contenant que "¶" et des espaces, avec le saut de ligne suivant
    private static final Pattern EMPTY_PARAGRAPH_LINE =
            Pattern.compile("(?m)^¶\\s*$(\\r?\\n)?");

    private TextCleaningUtils() {
        // utilitaire
    }

    /**
     * Supprime tous les paragraphes vides (ligne ne contenant que ¶ + espaces).
     * @param text texte complet de l’éditeur
     * @return texte nettoyé
     */
    public static String removeEmptyParagraphs(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = EMPTY_PARAGRAPH_LINE.matcher(text);
        return m.replaceAll("");
    }

    /**
     * Variante qui retourne aussi le nombre de suppressions effectuées.
     */
    public static Result removeEmptyParagraphsAndCount(String text) {
        if (text == null || text.isEmpty()) return new Result(text, 0);

        Matcher m = EMPTY_PARAGRAPH_LINE.matcher(text);
        int count = 0;
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            count++;
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);

        return new Result(sb.toString(), count);
    }

    public static final class Result {
        public final String cleanedText;
        public final int removedCount;

        public Result(String cleanedText, int removedCount) {
            this.cleanedText = cleanedText;
            this.removedCount = removedCount;
        }
    }
}