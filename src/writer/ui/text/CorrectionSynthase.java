package writer.ui.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaire de correction pour les synthèses :
 * - force les codes #1., #2., #3., #4., #5., #P. et #S. à commencer
 *   en début de paragraphe (ligne nouvelle avec ¶),
 * - corrige les variantes tapées à la main :
 *   "#p  texte" → "¶ #P. texte"
 *   "#s  texte" → "¶ #S. texte",
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
     * éventuellement "¶" + espaces, puis "#[1-5PS].".
     * (ex. "#1.", "#2.", "#P.", "#S.")
     */
    private static final Pattern PATTERN_PAR_TITRE =
            Pattern.compile("(?mi)^\\s*(?:" + PARAGRAPH_MARK + "\\s*)?#([1-5PS]\\.)");

    /**
     * Variantes tapées à la main pour les titres #P. et #S.
     * Exemples de lignes corrigées :
     *   "#p  Proverbe"      → "¶ #P. Proverbe"
     *   "#P Proverbe"       → "¶ #P. Proverbe"
     *   "   ¶ #p Proverbe"  → "¶ #P. Proverbe"
     *   "#s  Synonyme"      → "¶ #S. Synonyme"
     *   "#S Synonyme"       → "¶ #S. Synonyme"
     */
    private static final Pattern PATTERN_TITRE_P =
            Pattern.compile("(?mi)^\\s*(?:" + PARAGRAPH_MARK + "\\s*)?#p\\.?\\s+(.*)$");

    private static final Pattern PATTERN_TITRE_S =
            Pattern.compile("(?mi)^\\s*(?:" + PARAGRAPH_MARK + "\\s*)?#s\\.?\\s+(.*)$");

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
     * Motif pour repérer un marqueur @saut / @saut de page placé dans une ligne.
     *
     * Exemples gérés :
     *   "¶ texte1 @saut texte2"
     *   "¶ texte1 @saut de page texte2"
     *   "¶ #3. Titre @saut de page"
     *   "¶ @saut"
     *   "¶ @SAUT"
     */
    private static final Pattern PATTERN_INLINE_PAGE_BREAK =
            Pattern.compile("(?mi)^" + PARAGRAPH_MARK + " (.*?)\\s*" +
                            "(@saut(?:\\s+de\\s+page)?)" +
                            "(?:[ \\t]+([^\\r\\n]+))?$");

    private CorrectionSynthase() {
        // utilitaire statique
    }

    /**
     * Corrige le texte :
     *  0) corrige les titres #p / #s en lignes de titres normalisées "¶ #P. ..." / "¶ #S. ...";
     *  1) insère un saut de ligne et le marqueur de paragraphe
     *     avant les codes #1., #2., #3., #4., #5., #P. et #S. qui ne sont pas
     *     au début d'un paragraphe (y compris #p / #s, avec ou sans point) ;
     *  2) normalise les lignes de titres "#x." en "¶ #x." en début de ligne ;
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

        // --- 0) Corriger les titres #p / #s vers #P. / #S. en début de ligne ---
        text = normaliserTitresPS(text);

        // --- 1) Forcer les #x. (#1..#5, #P/#S avec ou sans point) à début de paragraphe ---
        StringBuilder out = new StringBuilder(text.length() + 128);
        final int len = text.length();
        int i = 0;

        while (i < len) {
            char ch = text.charAt(i);

            if (ch == '#' && i + 1 < len) {
                char c1 = text.charAt(i + 1);
                char uc1 = Character.toUpperCase(c1);

                // --- case 1 : titres numériques #1. .. #5. ---
                if (c1 >= '1' && c1 <= '5') {
                    if (i + 2 < len && text.charAt(i + 2) == '.') {
                        if (!isAtParagraphStart(text, i)) {
                            out.append('\n').append(PARAGRAPH_MARK).append(' ');
                        }
                        out.append('#').append(c1).append('.');
                        i += 3;
                        continue;
                    }
                    // si pas de point après un chiffre, on laisse tel quel
                }

                // --- case 2 : #P / #p / #S / #s, avec ou sans point ---
                if (uc1 == 'P' || uc1 == 'S') {
                    boolean hasDot = (i + 2 < len && text.charAt(i + 2) == '.');

                    if (!isAtParagraphStart(text, i)) {
                        out.append('\n').append(PARAGRAPH_MARK).append(' ');
                    }

                    // on normalise toujours en #P. ou #S.
                    out.append('#').append(uc1).append('.');
                    i += hasDot ? 3 : 2; // on saute '#' + lettre (+ éventuellement '.')
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
            // Remplacement forcé par : "¶ #$1" (où $1 est "1.", "2.", "P.", "S.", etc.)
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
     * Corrige les titres saisis sous la forme "#p texte" ou "#s texte"
     * (avec ou sans "¶" et avec ou sans ".") en lignes normalisées :
     *   "¶ #P. texte" ou "¶ #S. texte".
     */
    private static String normaliserTitresPS(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 1) Titres "#p ..." → "¶ #P. ..."
        Matcher m = PATTERN_TITRE_P.matcher(text);
        StringBuffer sb = new StringBuffer(text.length() + 64);

        while (m.find()) {
            String contenu = m.group(1).trim(); // texte après "#p"
            String replacement = PARAGRAPH_MARK + " #P. " + contenu;
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);

        text = sb.toString();

        // 2) Titres "#s ..." → "¶ #S. ..."
        m = PATTERN_TITRE_S.matcher(text);
        sb = new StringBuffer(text.length() + 64);

        while (m.find()) {
            String contenu = m.group(1).trim(); // texte après "#s"
            String replacement = PARAGRAPH_MARK + " #S. " + contenu;
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);

        return sb.toString();
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
     */
    public static String separerMarqueursTableEnDebutParagraphe(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher m = PATTERN_INLINE_TABLE_MARKER.matcher(text);
        StringBuffer sb = new StringBuffer(text.length() + 64);

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
	 * Force le marqueur @saut / @saut de page à début de paragraphe
	 * et conserve le texte avant et après sur des paragraphes séparés.
	 *
	 * Exemples :
	 *   "¶ texte1 @saut texte2"
	 *     → "¶ texte1"
	 *       "¶ @saut de page."
	 *       "¶ texte2"
	 *
	 *   "¶ #3. Locution nominale @saut de page"
	 *     → "¶ #3. Locution nominale"
	 *       "¶ @saut de page."
	 *
	 *   "¶ @saut"
	 *     → "¶ @saut de page."
	 */
	public static String separerSautDePageEnDebutParagraphe(String text) {
	    if (text == null || text.isEmpty()) {
	        return text;
	    }
	
	    Matcher m = PATTERN_INLINE_PAGE_BREAK.matcher(text);
	    StringBuffer sb = new StringBuffer(text.length() + 64);
	
	    while (m.find()) {
	        String avant   = m.group(1); // texte avant le marqueur (peut être vide)
	        @SuppressWarnings("unused")
			String brutTag = m.group(2); // "@saut", "@saut de page", "@saut de page."
	        String apres   = m.group(3); // texte après le marqueur (peut être null)
	
	        String trimmedAvant = avant == null ? "" : avant.trim();
	        String trimmedApres = apres == null ? "" : apres.trim();
	
	        // Normalisation du marqueur : toujours "@saut de page."
	        String marker = "@saut de page";
	
	        StringBuilder replacement = new StringBuilder();
	
	        // 1) texte avant, s'il existe
	        if (!trimmedAvant.isEmpty()) {
	            replacement.append(PARAGRAPH_MARK).append(' ')
	                       .append(trimmedAvant)
	                       .append('\n');
	        }
	
	        // 2) le paragraphe avec le marqueur
	        replacement.append(PARAGRAPH_MARK).append(' ')
	                   .append(marker);
	
	        // 3) texte après, s'il existe
	        if (!trimmedApres.isBlank()) {
	            replacement.append('\n')
	                       .append(PARAGRAPH_MARK).append(' ')
	                       .append(trimmedApres);
	        }
	
	        m.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
	    }
	    m.appendTail(sb);
	
	    return sb.toString();
	}

    /**
     * Normalise l'espace après "-." :
     *  "-.avoir"    → "-. avoir"
     *  "-.   avoir" → "-. avoir"
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
     */
    public static String separerListesNumerotees(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String[] lines = text.split("\\r?\\n", -1); // garder les lignes vides
        StringBuilder sb = new StringBuilder(text.length() + 64);
        boolean firstOut = true;

        final String prefix = PARAGRAPH_MARK + " -. ";

        for (String line : lines) {
            if (line.startsWith(prefix)) {
                String rest = line.substring(prefix.length());
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

                if (!emittedAny) {
                    if (!firstOut) {
                        sb.append('\n');
                    }
                    sb.append(line);
                    firstOut = false;
                }

            } else {
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
     * Nettoie les séquences de codes de mise en forme sans texte.
     */
    public static String nettoyerCodesVides(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        text = text.replaceAll("\\^{3,}", "");     // ^^^, ^^^^, ...
        text = text.replaceAll("\\*{4,}", "");     // ****, *****, ...
        text = text.replaceAll("_\\*{2}_", "");    // _**_
        text = text.replaceAll("_\\^{2}_", "");    // _^^_
        text = text.replaceAll("\\^\\^(\\s*)\\^\\^", "");  // ^^   ^^
        text = text.replaceAll("\\*\\*(\\s*)\\*\\*", "");  // **   **
        text = text.replaceAll("(\\*|\\^|_)([\\s\\*\\^_]*)(\\1)", ""); // *   *, ^   ^, _ * ^ _
        text = text.replaceAll(" {2,}", " ");      // espaces multiples → un seul

        return text;
    }

    /**
     * Indique si la position donnée (indice du '#') est en début de paragraphe.
     */
    private static boolean isAtParagraphStart(String text, int pos) {
        if (pos == 0) {
            return true;
        }

        int lineStart = pos - 1;
        while (lineStart >= 0) {
            char c = text.charAt(lineStart);
            if (c == '\n' || c == '\r') {
                lineStart++;
                break;
            }
            lineStart--;
        }

        if (lineStart < 0) {
            lineStart = 0;
        }

        for (int i2 = lineStart; i2 < pos; i2++) {
            char c = text.charAt(i2);

            if (c == PARAGRAPH_MARK) {
                continue;
            }

            if (!Character.isWhitespace(c)) {
                return false;
            }
        }

        return true;
    }
}