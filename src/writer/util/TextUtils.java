package writer.util;

import javax.swing.JTextArea;

/**
 * Fonctions utilitaires pour l'analyse du texte dans l'éditeur.
 */
public final class TextUtils {

    private TextUtils() {} // classe statique

    /**
     * Recherche une URL commençant par http(s) autour du curseur.
     * @param area le composant texte (ex. ctx.getEditor())
     * @return l'URL trouvée ou null si aucune
     */
    public static String findUrlAtCaret(JTextArea area) {
        if (area == null) return null;

        try {
            int pos = area.getCaretPosition();
            String text = area.getText();

            // cherche le "http" le plus proche avant le caret
            int s = Math.max(0, text.lastIndexOf("http", pos - 1));
            if (s < 0) return null;

            int e = s;
            while (e < text.length()
                    && !Character.isWhitespace(text.charAt(e))
                    && text.charAt(e) != ')' && text.charAt(e) != '"') {
                e++;
            }

            return text.substring(s, e);
        } catch (Exception ex) {
            return null;
        }
    }
}
