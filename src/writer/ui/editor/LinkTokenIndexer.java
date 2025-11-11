package writer.ui.editor;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remplace dans le texte tous les liens LisioWriter:
 *    @[Titre : https://...]
 * par des jetons [Lien1], [Lien2], ...
 * et stocke une Map<Integer, String> (numero -> URL) dans le Document.
 *
 * Par défaut on convertit les liens Wikipédia (wikipedia.org). Tu peux élargir le pattern si besoin.
 */
public final class LinkTokenIndexer {

    private LinkTokenIndexer() {}

    /** Clé de propriété du Document pour la map numéro→URL. */
    public static final String PROP_LINK_MAP = "bw.link.token.map";

    /** Pattern des liens LisioWriter. On restreint ici à Wikipédia. */
    private static final Pattern P_AT_LINK = Pattern.compile(
            "@\\[\\s*([^\\]:]+?)\\s*:\\s*(https?://(?:[a-z]{2}\\.)?wikipedia\\.org/[^\\]\\s]+)\\s*\\]"
    );
    // Si tu veux convertir TOUS les @[…], remplace la ligne au-dessus par :
    // private static final Pattern P_AT_LINK = Pattern.compile("@\\[\\s*([^\\]:]+?)\\s*:\\s*(https?://[^\\]\\s]+)\\s*\\]");

    /**
     * Convertit et renvoie le nombre de liens transformés.
     * Efface et remplit PROP_LINK_MAP à chaque appel (idempotent).
     */
    public static int convertAtLinksToTokens(JTextComponent editor) {
        try {
            Document doc = editor.getDocument();
            String text = doc.getText(0, doc.getLength());

            Matcher m = P_AT_LINK.matcher(text);
            StringBuffer sb = new StringBuffer(text.length());

            LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
            int n = 0;

            while (m.find()) {
                String url = m.group(2).trim();
                n++;
                map.put(n, url);
                m.appendReplacement(sb, Matcher.quoteReplacement("[Lien" + n + "]"));
            }
            m.appendTail(sb);

            // Met à jour le texte uniquement si nécessaire
            if (n > 0) {
                editor.setText(sb.toString());
            }

            // Dépose la map (même vide) dans le Document
            doc.putProperty(PROP_LINK_MAP, map);

            return n;
        } catch (Exception ex) {
            ex.printStackTrace();
            return 0;
        }
    }

    /** Récupérer la map depuis le Document (peut être vide mais non null si l’indexeur est passé). */
    @SuppressWarnings("unchecked")
    public static Map<Integer, String> getLinkMap(Document doc) {
        Object o = doc.getProperty(PROP_LINK_MAP);
        if (o instanceof Map<?, ?>) return (Map<Integer, String>) o;
        return Map.of();
    }
}