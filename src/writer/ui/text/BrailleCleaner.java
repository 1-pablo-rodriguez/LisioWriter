package writer.ui.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nettoyage des marqueurs braille ¶ (U+283F) avant export.
 *
 * Règles :
 *  1) En début de ligne : supprime ¶ et les blancs adjacents (espaces Unicode + tab) ;
 *     gère aussi le cas de plusieurs ¶ consécutifs.
 *  2) Ailleurs dans la ligne : supprime uniquement le caractère ¶.
 *
 * Toutes les méthodes sont thread-safe et sans état.
 */
public final class BrailleCleaner {

    private BrailleCleaner() {}

    /** Caractère braille (U+283F). */
    public static final char BRAILLE = '\u00B6';

    /**
     * En-tête de ligne : on retire un ou plusieurs blocs « blancs* ¶ blancs* »
     * Ex.: "  ¶  ¶  Titre" -> "Titre"
     *
     * (?m) active le mode multi-ligne ( ^ = début de CHAQUE ligne ).
     */
    private static final Pattern LEADING_BRAILLE =
            Pattern.compile("(?m)^(?:[\\p{Zs}\\t]*\\u00B6[\\p{Zs}\\t]*)+");

    /** Détection simple. */
    public static boolean containsBraille(String s) {
        return s != null && s.indexOf(BRAILLE) >= 0;
    }

    /**
     * Nettoyage simple : applique les deux règles.
     */
    public static String clean(String src) {
        if (src == null || src.isEmpty()) return src;
        // 1) Débuts de lignes
        String s = LEADING_BRAILLE.matcher(src).replaceAll("");
        // 2) Occurrences restantes
        return s.replace(String.valueOf(BRAILLE), "");
    }

    /**
     * Nettoyage + comptage des ¶ supprimés.
     * Utile pour journaliser ou vérifier l'effet du pré-traitement.
     */
    public static Result cleanAndCount(String src) {
        if (src == null || src.isEmpty()) return new Result(src, 0, 0);

        // Compte total ¶ avant nettoyage
        int totalBefore = countChar(src, BRAILLE);

        // Compte ¶ situés dans les segments "début de ligne" (supprimés avec espaces adjacents)
        int headCount = 0;
        Matcher m = LEADING_BRAILLE.matcher(src);
        while (m.find()) {
            headCount += countChar(m.group(), BRAILLE);
        }

        // Nettoie
        String leadingCleaned = m.replaceAll("");
        String fullyCleaned = leadingCleaned.replace(String.valueOf(BRAILLE), "");

        // Les ¶ restants en milieu de ligne retirés = total - headCount - restants (qui doit être 0 après replace)
        int totalAfter = countChar(fullyCleaned, BRAILLE); // doit être 0, par principe
        int middleCount = Math.max(0, (totalBefore - headCount - totalAfter));

        return new Result(fullyCleaned, headCount, middleCount);
    }

    /**
     * Nettoie un StringBuilder (remplacement par le résultat nettoyé).
     */
    public static void cleanInto(StringBuilder sb) {
        Objects.requireNonNull(sb, "sb");
        if (sb.length() == 0) return;
        String cleaned = clean(sb.toString());
        sb.setLength(0);
        sb.append(cleaned);
    }

    /**
     * Nettoie une liste de lignes en appliquant les mêmes règles.
     * On garde la même taille de liste.
     */
    public static List<String> cleanLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return lines;
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                out.add(line);
                continue;
            }
            // 1) tête de ligne
            String head = LEADING_BRAILLE.matcher(line).replaceAll("");
            // 2) occurrences restantes dans la ligne
            out.add(head.replace(String.valueOf(BRAILLE), ""));
        }
        return out;
    }

    // --- Helpers ---

    private static int countChar(CharSequence s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    /** Résultat détaillé du nettoyage. */
    public static final class Result {
        /** Chaîne nettoyée. */
        public final String cleaned;
        /** Nombre de ¶ supprimés en tête de ligne (avec blancs). */
        public final int removedAtLineStart;
        /** Nombre de ¶ supprimés ailleurs dans les lignes. */
        public final int removedInLine;

        public Result(String cleaned, int removedAtLineStart, int removedInLine) {
            this.cleaned = cleaned;
            this.removedAtLineStart = removedAtLineStart;
            this.removedInLine = removedInLine;
        }

        /** Total ¶ supprimés. */
        public int totalRemoved() { return removedAtLineStart + removedInLine; }

        @Override public String toString() {
            return "BrailleCleaner.Result{total=" + totalRemoved()
                    + ", head=" + removedAtLineStart
                    + ", middle=" + removedInLine + "}";
        }
    }
}
