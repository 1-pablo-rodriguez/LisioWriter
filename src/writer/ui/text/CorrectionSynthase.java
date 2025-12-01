package writer.ui.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaire de correction pour les synthèses :
 * - force les codes #1., #2., #3., #4. et #5. à commencer
 *   en début de paragraphe (ligne nouvelle avec ¶),
 * - normalise l'espace entre ¶ et le texte (titres, @t, texte normal) :
 *   ¶ doit être le premier caractère de la ligne, suivi d'un seul espace,
 * - force les marqueurs de tableau @t et @/t à commencer un paragraphe,
 * - force @saut de page à commencer un paragraphe,
 * - force chaque liste "-." à n'avoir qu'un seul item par paragraphe,
 * - normalise aussi l'espace après "-." → exactement " -. ",
 * - supprime certains codes de mise en forme vides (^^^^, ****, _**_, _^^_, etc.).
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

    /**
     * Motif pour repérer un marqueur @t ou @/t placé en fin de ligne après du texte.
     * Exemple : "¶ un texte @t" → on veut :
     *   "¶ un texte"
     *   "¶ @t"
     */
    private static final Pattern PATTERN_INLINE_TABLE_MARKER =
            Pattern.compile("(?m)^" + PARAGRAPH_MARK + " (.*?)(\\s+)(@/?t\\b.*)$");

    /**
     * Motif pour repérer un marqueur @saut de page placé en fin de ligne après du texte.
     * Exemple : "¶ #3. Locution nominale @saut de page" → on veut :
     *   "¶ #3. Locution nominale"
     *   "¶ @saut de page"
     */
    private static final Pattern PATTERN_INLINE_PAGE_BREAK =
            Pattern.compile("(?m)^" + PARAGRAPH_MARK + " (.*?)(\\s+)(@saut\\s+de\\s+page\\b.*)$");

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
     *  4) force @t et @/t à début de paragraphe (séparation de lignes) ;
     *  5) force @saut de page à début de paragraphe (séparation de lignes) ;
     *  6) normalise l'espace après "-." → " -. " ;
     *  7) force chaque item "-." à être sur sa propre ligne "¶ -." ;
     *  8) supprime certains codes de mise en forme vides :
     *     ^^^^, ****, _**_, _^^_, "^^   ^^", "**   **", etc.
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
            // Remplacement forcé par : "¶ #$1" (où $1 est "1.", "2.", etc.)
            m.appendReplacement(sb, PARAGRAPH_MARK + " #$1");
        }
        m.appendTail(sb);

        String withTitles = sb.toString();

        // --- 3) Normaliser tous les paragraphes commençant par ¶ → "¶ " ---
        String withPMarks = normaliserEspacesApresPiedDeMouche(withTitles);

        // --- 4) Forcer @t et @/t à début de paragraphe ---
        String withTableMarkers = separerMarqueursTableEnDebutParagraphe(withPMarks);

        // --- 5) Forcer @saut de page à début de paragraphe ---
        String withPageBreaks = separerSautDePageEnDebutParagraphe(withTableMarkers);

        // --- 6) Normaliser l'espace après "-." → " -. " ---
        String withDashDot = normaliserEspaceApresTiretPoint(withPageBreaks);

        // --- 7) Forcer chaque item "-." à être sur sa propre ligne ---
        String withLists = separerListesNumerotees(withDashDot);

        // --- 8) Nettoyer les codes vides de type ^^^^, ****, _**_, _^^_, etc. ---
        String finalText = nettoyerCodesVides(withLists);

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
     * Force les marqueurs de tableau @t et @/t à début de paragraphe.
     *
     * Exemples :
     *   "¶ un texte @t"   → "¶ un texte\n¶ @t"
     *   "¶ un texte  @/t" → "¶ un texte\n¶ @/t"
     *
     * Si la ligne est déjà "¶ @t" ou "¶ @/t", elle n'est pas modifiée.
     */
    public static String separerMarqueursTableEnDebutParagraphe(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher m = PATTERN_INLINE_TABLE_MARKER.matcher(text);
        StringBuffer sb = new StringBuffer(text.length() + 32);

        while (m.find()) {
            String avant    = m.group(1); // texte avant le marqueur
            String marqueur = m.group(3); // "@t" ou "@/t" + éventuel texte

            String replacement;
            String trimmedAvant   = avant.trim();
            String trimmedMarker  = marqueur.trim();

            if (trimmedAvant.isEmpty()) {
                // ligne du type "¶ @t" ou "¶   @t" : on normalise en "¶ @t"
                replacement = PARAGRAPH_MARK + " " + trimmedMarker;
            } else {
                // cas "¶ du texte @t" → "¶ du texte\n¶ @t"
                replacement = PARAGRAPH_MARK + " " + trimmedAvant
                            + "\n"
                            + PARAGRAPH_MARK + " " + trimmedMarker;
            }

            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);

        return sb.toString();
    }

    /**
     * Force le marqueur @saut de page à début de paragraphe.
     *
     * Exemple :
     *   "¶ #3. Locution nominale @saut de page"
     * devient :
     *   "¶ #3. Locution nominale"
     *   "¶ @saut de page"
     *
     * Si la ligne est déjà "¶ @saut de page", elle est simplement normalisée.
     */
    public static String separerSautDePageEnDebutParagraphe(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher m = PATTERN_INLINE_PAGE_BREAK.matcher(text);
        StringBuffer sb = new StringBuffer(text.length() + 32);

        while (m.find()) {
            String avant    = m.group(1); // texte avant le marqueur
            String marqueur = m.group(3); // "@saut de page" + éventuel texte

            String replacement;
            String trimmedAvant   = avant.trim();
            String trimmedMarker  = marqueur.trim(); // ex : "@saut de page"

            if (trimmedAvant.isEmpty()) {
                // ligne du type "¶ @saut de page" ou "¶   @saut de page"
                replacement = PARAGRAPH_MARK + " " + trimmedMarker;
            } else {
                // cas "¶ du texte @saut de page" → "¶ du texte\n¶ @saut de page"
                replacement = PARAGRAPH_MARK + " " + trimmedAvant
                            + "\n"
                            + PARAGRAPH_MARK + " " + trimmedMarker;
            }

            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);

        return sb.toString();
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
     * Sépare les listes numérotées "-." pour que chaque item ait sa propre ligne.
     *
     * Exemple :
     *   "¶ -. mon texte-. Mon autre texte"
     * devient :
     *   "¶ -. mon texte"
     *   "¶ -. Mon autre texte"
     *
     * On ne traite que les lignes qui commencent déjà par "¶ -. ".
     */
    public static String separerListesNumerotees(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String[] lines = text.split("\\r?\\n", -1); // garder les lignes vides
        StringBuilder sb = new StringBuilder(text.length() + 32);
        boolean firstOut = true;

        final String prefix = PARAGRAPH_MARK + " -. ";

        for (String line : lines) {
            if (line.startsWith(prefix)) {
                // On isole le contenu après "¶ -. "
                String rest = line.substring(prefix.length());

                // On coupe sur les occurrences de "-." (avec éventuellement des espaces autour)
                String[] parts = rest.split("\\s*-\\.\\s*", -1);

                boolean emittedAny = false;
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    if (!firstOut) {
                        sb.append('\n');
                    }
                    sb.append(prefix).append(trimmed);
                    firstOut = false;
                    emittedAny = true;
                }

                // Si pour une raison quelconque on n'a rien émis, on garde la ligne telle quelle
                if (!emittedAny) {
                    if (!firstOut) {
                        sb.append('\n');
                    }
                    sb.append(line);
                    firstOut = false;
                }

            } else {
                // ligne normale, inchangée
                if (!firstOut) {
                    sb.append('\n');
                }
                sb.append(line);
                firstOut = false;
            }
        }

        return sb.toString();
    }

    /**
     * Nettoie les séquences de codes de mise en forme sans texte :
     *  - ^^^, ^^^^, etc. (3 ^ ou plus)
     *  - ****, *****, etc. (4 * ou plus)
     *  - _**_
     *  - _^^_
     *  - "^^   ^^" (deux paires de ^^ séparées uniquement par des blancs)
     *  - "**   **" (deux paires de ** séparées uniquement par des blancs)
     */
    public static String nettoyerCodesVides(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // 1) Séquences de 3 ^ ou plus : "^^^", "^^^^", etc.
        // (n'affecte pas "^^texte^^" qui n'a que 2 ^ de chaque côté)
        text = text.replaceAll("\\^{3,}", "");

        // 2) Séquences de 4 * ou plus : "****", "*****", etc.
        // (n'affecte pas "**gras**" qui n'a jamais 4 * de suite)
        text = text.replaceAll("\\*{4,}", "");

        // 3) motifs stricts _**_ et _^^_ (balises sans contenu)
        text = text.replaceAll("_\\*{2}_", "");
        text = text.replaceAll("_\\^{2}_", "");

        // 4) cas comme "^^   ^^" : deux paires de ^^ séparées uniquement par des blancs
        text = text.replaceAll("\\^\\^(\\s*)\\^\\^", "");

        // 5) cas comme "**   **" : deux paires de ** séparées uniquement par des blancs
        text = text.replaceAll("\\*\\*(\\s*)\\*\\*", "");
        
        //Supprimer les blocs entre *...*, ^...^, _..._, quand il n'y a QUE espaces ou balises dedans
        text = text.replaceAll("(\\*|\\^|_)([\\s\\*\\^_]*)(\\1)","");

        // 6) On peut en profiter pour nettoyer les espaces multiples créés par ces suppressions
        text = text.replaceAll(" {2,}", " ");

        return text;
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