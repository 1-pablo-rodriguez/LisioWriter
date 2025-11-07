package writer.ui.editor;

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

    private BraillePrefixer() {}

    /**
     * Préfixe chaque ligne non vide par {@code mark} si elle n'en est pas déjà préfixée.
     * Conserve exactement les retours à la ligne (utilise split("\n", -1)).
     *
     * Exemple : prefixParagraphsWithBrailleMark("Ligne\n\nAutre", '\u283F')
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
     * "    texte" -> "    ⠿texte"  (si mark = '\u283F')
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
}