package writer.ui.editor;

import java.util.regex.Pattern;

/**
 * Utilitaires pour préfixer chaque paragraphe d'un texte avec un marqueur braille.
 *
 * Fournit :
 *  - prefixParagraphsWithBrailleMark(text, mark) : ajoute le marqueur uniquement si la ligne
 *    n'en possède pas déjà un (test simple startsWith).
 *
 *  - prefixParagraphsWithBrailleMarkPreserveIndent(text, mark) : même chose mais si la ligne
 *    commence par des espaces/tabs, le marqueur est inséré après l'indentation.
 */
public final class BraillePrefixer {
	
	// Caractère braille et regex "commence déjà par ¶ (après espaces éventuels)"
    private static final char BRAILLE = '\u00B6';
    private static final Pattern LEADING_BRAILLE = Pattern.compile("^\\s*\\u00B6\\s*");


    private BraillePrefixer() {}

    /**
     * Préfixe chaque ligne non vide par {@code mark} si elle n'en est pas déjà préfixée.
     * Conserve exactement les retours à la ligne (utilise split("\n", -1)).
     *
     * Exemple : prefixParagraphsWithBrailleMark("Ligne\n\nAutre", '\u00B6')
     *
     * @param text texte source (peut contenir \n)
     * @param mark caractère braille à insérer
     * @return texte transformé (identique si aucune modification)
     */
    public static String prefixParagraphsWithBrailleMark(String text, char mark) {
        if (text == null || text.isEmpty()) return text;
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + lines.length);
        String markStr = String.valueOf(mark);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                out.append(line);
            } else {
                if (!line.startsWith(markStr)) {
                    out.append(markStr).append(line);
                } else {
                    out.append(line);
                }
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    /**
     * Variante qui préserve l'indentation :
     * si la ligne commence par des espaces/tabs, le marqueur est inséré après cette indentation.
     * Si la ligne est vide, rien n'est inséré.
     *
     * Exemple :
     * "    texte" -> "    ¶texte"  (si mark = '\u00B6')
     *
     * @param text texte source
     * @param mark caractère braille
     * @return texte transformé
     */
    public static String prefixParagraphsWithBrailleMarkPreserveIndent(String text, char mark) {
        if (text == null || text.isEmpty()) return text;
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + lines.length);
        String markStr = String.valueOf(mark);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                out.append(line);
            } else {
                // calculer indentation (espaces et tabulations)
                int pos = 0;
                while (pos < line.length()) {
                    char c = line.charAt(pos);
                    if (c == ' ' || c == '\t') pos++; else break;
                }
                // si le marqueur est déjà présent juste après l'indentation, ne rien faire
                if (pos < line.length() && line.startsWith(markStr, pos)) {
                    out.append(line);
                } else {
                    out.append(line, 0, pos).append(markStr).append(line.substring(pos));
                }
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }
    
    
    /**
     * Méthode utilisée pour importation des fichiers docx, .odt, .txt, .html
     * Ajoute "¶ " au début de chaque paragraphe non vide du bloc de texte,
     * sauf si le paragraphe commence déjà par ¶.
     * - Conserve les lignes vides telles quelles
     * - Gère CRLF / CR / LF
     */
    public static String addBrailleAtParagraphStarts(String text) {
        // normalise les fins de ligne pour itérer proprement
        String norm = text.replace("^\s?\n+", "").replace("\n\n", "\n").replace("\r+", "\n").replace('\r', '\n');
        String[] lines = norm.split("\n", -1); // -1 pour conserver les vides de fin
        StringBuilder out = new StringBuilder(norm.length() + lines.length * 2);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (LEADING_BRAILLE.matcher(line).find()) {
                out.append(line); // déjà préfixé : ne pas dupliquer
            } else {
                out.append(BRAILLE).append(line);
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }
    
    
    
}