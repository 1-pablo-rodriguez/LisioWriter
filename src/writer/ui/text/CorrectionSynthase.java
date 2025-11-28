package writer.ui.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaire de correction pour les synthèses :
 * - force les codes #1., #2., #3., #4. et #5. à commencer
 *   en début de paragraphe (ligne nouvelle avec ¶),
 * - normalise l'espace entre ¶ et le texte (titres, @t, texte normal) :
 *   ¶ doit être le premier caractère de la ligne, suivi d'un seul espace,
 * - normalise aussi l'espace après "-." → exactement " -. ".
 */
public final class CorrectionSynthase {

    /** Marqueur de paragraphe utilisé dans l’application. */
    private static final char PARAGRAPH_MARK = '\u00B6';

    /**
     * Titre : début de ligne (MULTILINE), espaces éventuels,
     * éventuellement "¶" + espaces, puis "#[1-5].".
     */
    private static final Pattern PATTERN_PAR_TITRE =
            Pattern.compile("(?m)^\\s*(?:\u00B6\\s*)?#([1-5]\\.)");

    /**
     * Paragraphe LisioWriter :
     * début de ligne, espaces éventuels, puis ¶, puis espaces éventuels.
     */
    private static final Pattern PATTERN_PARAGRAPH_PMARK =
            Pattern.compile("(?m)^[ \\t]*" + PARAGRAPH_MARK + "[ \\t]*");

    /**
     * Motif pour "-." suivi de blancs puis d'un caractère non blanc.
     * Exemple : "-.avoir" ; "-.   avoir" → on veut "-. avoir".
     */
    private static final Pattern PATTERN_TIRET_POINT =
            Pattern.compile("-\\.\\s*(?=\\S)");

    private CorrectionSynthase() {
        // utilitaire statique
    }

    /**
     * Corrige le texte :
     *  1) insère un saut de ligne et le marqueur de paragraphe
     *     avant les codes #1., #2., #3., #4. et #5. qui ne sont pas
     *     au début d'un paragraphe ;
     *  2) normalise les titres "#x." en "¶ #x." en début de ligne ;
     *  3) normalise tous les paragraphes commençant par ¶ pour
     *     qu'ils aient exactement "¶ " en début de ligne ;
     *  4) normalise l'espace après "-." → exactement " -. ".
     *
     * @param text le texte d'entrée (peut être null)
     * @return le texte corrigé (ou le texte d'origine si null/vide)
     */
    public static String corrigerSynthase(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // --- 1) Forcer les #x. à début de paragraphe (insertion de "\n¶ ") ---
        StringBuilder out = new StringBuilder(text.length() + 128);
        final int len = text.length();
        int i = 0;

        while (i < len) {
            char ch = text.charAt(i);

            // Détection d'un motif #x. (x entre 1 et 5)
            if (ch == '#' && i + 2 < len) {
                char digit = text.charAt(i + 1);
                char dot   = text.charAt(i + 2);

                if (digit >= '1' && digit <= '5' && dot == '.') {
                    // On a un motif #1. .. #5.
                    if (!isAtParagraphStart(text, i)) {
                        // pas en début de paragraphe → on insère un saut de paragraphe
                        out.append('\n').append(PARAGRAPH_MARK).append(' ');
                    }

                    // On recopie le motif tel quel
                    out.append('#').append(digit).append('.');
                    i += 3;
                    continue;
                }
            }

            // Cas général : on recopie le caractère
            out.append(ch);
            i++;
        }

        String base = out.toString();

        // --- 2) Normaliser les lignes de titres "#x." en début de ligne → "¶ #x." ---
        Matcher m = PATTERN_PAR_TITRE.matcher(base);
        StringBuffer sb = new StringBuffer(base.length());

        while (m.find()) {
            // Remplacement forcé par : "¶ #x."
            m.appendReplacement(sb, PARAGRAPH_MARK + " #$1");
        }
        m.appendTail(sb);

        String withTitles = sb.toString();

        // --- 3) Normaliser tous les paragraphes commençant par ¶ → "¶ " ---
        String withPMarks = normaliserEspacesApresPiedDeMouche(withTitles);

        // --- 4) Normaliser l'espace après "-." → " -. " ---
        String finalText = normaliserEspaceApresTiretPoint(withPMarks);

        return finalText;
    }

    /**
     * Normalise tous les paragraphes dont la ligne commence par le caractère ¶ :
     *  - ¶ devient le tout premier caractère de la ligne ;
     *  - exactement un espace après ¶.
     */
    public static String normaliserEspacesApresPiedDeMouche(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return PATTERN_PARAGRAPH_PMARK
                .matcher(text)
                .replaceAll(String.valueOf(PARAGRAPH_MARK) + " ");
    }

    /**
     * Normalise l'espace après "-." :
     *  "-.avoir"    → "-. avoir"
     *  "-.   avoir" → "-. avoir"
     *  (ne touche pas un "-." tout seul en fin de ligne)
     */
    public static String normaliserEspaceApresTiretPoint(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return PATTERN_TIRET_POINT
                .matcher(text)
                .replaceAll("-. ");
    }

    /**
     * Indique si la position donnée (indice du '#') est en début de paragraphe.
     */
    private static boolean isAtParagraphStart(String text, int pos) {
        // Si on est au tout début du texte
        if (pos == 0) {
            return true;
        }

        // Chercher le dernier saut de ligne avant pos
        int lineStart = pos - 1;
        while (lineStart >= 0) {
            char c = text.charAt(lineStart);
            if (c == '\n' || c == '\r') {
                lineStart++; // début logique de la ligne = après \n ou \r
                break;
            }
            lineStart--;
        }

        // Si aucun \n/\r trouvé, début de ligne = 0
        if (lineStart < 0) {
            lineStart = 0;
        }

        // Vérifier s'il n'y a que des blancs et/ou un ¶ avant le '#'
        for (int i2 = lineStart; i2 < pos; i2++) {
            char c = text.charAt(i2);

            if (c == PARAGRAPH_MARK) {
                // On autorise ¶ dans la marge
                continue;
            }

            if (!Character.isWhitespace(c)) {
                // Vrai texte trouvé → pas début de paragraphe
                return false;
            }
        }

        return true;
    }
}
