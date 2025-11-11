package writer.ui.editor;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * Convertit les liens Wikipédia de la forme:
 *   @[label : https://fr.wikipedia.org/...]
 * en jetons [Lien1], [Lien2], ...
 * + garde une Map<Integer, String> (numero -> URL) dans le Document.
 * + Ctrl+Entrée sur [LienN] insère l’article dans l’éditeur.
 */
public final class WikipediaLinkIndexer {

    private WikipediaLinkIndexer() {}

    /** Clé de propriété posée sur le Document pour retrouver la Map. */
    public static final String PROP_WIKI_MAP = "lisio.wikiLinkMap";

    /** Pattern pour capturer les notations @[[texte : url]] */
    // Autorise espaces facultatifs autour de ':' ; exige fr.wikipedia.org
    private static final Pattern P_AT_LINK = Pattern.compile(
            "@\\[\\s*([^\\]:]+?)\\s*:\\s*(https?://fr\\.wikipedia\\.org/[^\\]]+?)\\s*\\]"
    );

    /** Pattern pour reconnaître [LienN] ou [lienN] (avec ou sans espace). */
    private static final Pattern P_TOKEN = Pattern.compile(
            "\\[(?:Lien|lien)\\s*(\\d+)]"
    );

    /** Interface que TU dois implémenter pour insérer l’article dans l’éditeur. */
    public interface WikipediaImporter {
        /**
         * Insère l’article correspondant à l’URL dans editor.
         * Tu peux ici appeler ta méthode existante (par ex. insertIntoEditor(...)).
         */
        void importIntoEditor(JTextComponent editor, String url);
    }

    /**
     * 1) Parcourt le texte de l’éditeur, remplace tous les
     *    @[label : https://fr.wikipedia.org/...]
     *    par [Lien1], [Lien2], ...
     * 2) Crée/stocke la Map numero->URL dans le Document.
     * 3) Installe le raccourci Ctrl+Entrée.
     *
     * Idempotent côté raccourci (réinstalle la même action proprement).
     *
     * @return le nombre de liens indexés.
     */
    public static int indexLinksAndInstallShortcut(JTextComponent editor, WikipediaImporter importer) {
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(importer, "importer");

        // Étape 1 : indexation + remplacement
        int count = indexLinksInDocument(editor);

        // Étape 2 : binding Ctrl+Entrée
        installCtrlEnterAction(editor, importer);

        return count;
    }

    /**
     * Remplace tous les @[..: https://fr.wikipedia.org/...]
     * par [LienN] et dépose la Map<N, URL> dans Document.
     */
    public static int indexLinksInDocument(JTextComponent editor) {
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
                // Remplacement par [LienN]
                m.appendReplacement(sb, Matcher.quoteReplacement("[Lien" + n + "]"));
            }
            m.appendTail(sb);

            if (n > 0) {
                // Un seul setText -> 1 seule édition dans l'historique d’annulation
                editor.setText(sb.toString());
                // Stocke la map sur le Document
                doc.putProperty(PROP_WIKI_MAP, map);
            } else {
                // Même s’il n’y a plus de @[...], on garde la map éventuelle existante
                // mais on l’efface si elle ne correspond plus
                doc.putProperty(PROP_WIKI_MAP, map);
            }

            return n;
        } catch (Exception ex) {
            ex.printStackTrace();
            return 0;
        }
    }

    /**
     * Installe l’action Ctrl+Entrée sur l’éditeur :
     * si le caret est sur un jeton [LienN], on ouvre N dans l’éditeur via importer.
     */
    @SuppressWarnings("serial")
	public static void installCtrlEnterAction(JTextComponent editor, WikipediaImporter importer) {
        final String ACTION_KEY = "lisio.openWikiToken";
        Action action = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openTokenUnderCaret(editor, importer);
            }
        };

        InputMap im = editor.getInputMap(JComponent.WHEN_FOCUSED);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), ACTION_KEY);
        editor.getActionMap().put(ACTION_KEY, action);
    }

    /**
     * Si le caret est à l’intérieur d’un jeton [LienN], récupère N,
     * trouve l’URL dans la Map et appelle importer.importIntoEditor(...).
     */
    public static void openTokenUnderCaret(JTextComponent editor, WikipediaImporter importer) {
        try {
            int pos = editor.getCaretPosition();
            String text = editor.getDocument().getText(0, editor.getDocument().getLength());

            // Trouver les bornes du token entourant le caret : '[' à gauche, ']' à droite
            int left = findPrevChar(text, pos, '[');
            int right = findNextChar(text, pos, ']');
            if (left < 0 || right < 0 || right <= left) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            String candidate = text.substring(left, right + 1); // inclut [ ... ]
            Matcher mt = P_TOKEN.matcher(candidate);
            if (!mt.matches()) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            int num = Integer.parseInt(mt.group(1));
            @SuppressWarnings("unchecked")
            Map<Integer, String> map = (Map<Integer, String>) editor.getDocument().getProperty(PROP_WIKI_MAP);
            if (map == null || !map.containsKey(num)) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            String url = map.get(num);
            if (url == null || url.isBlank()) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            // Appelle ton importeur pour insérer l’article directement dans l’éditeur
            importer.importIntoEditor(editor, url);

        } catch (Exception ex) {
            ex.printStackTrace();
            Toolkit.getDefaultToolkit().beep();
        }
    }

    // --- helpers privés ---

    private static int findPrevChar(String s, int from, char target) {
        int i = Math.min(Math.max(0, from), s.length());
        for (int p = i - 1; p >= 0; p--) {
            char c = s.charAt(p);
            if (c == target) return p;
            // Petit garde-fou : si on trouve un ']' avant '[', on sort
            if (c == ']') break;
        }
        return -1;
    }

    private static int findNextChar(String s, int from, char target) {
        int i = Math.min(Math.max(0, from), s.length());
        for (int p = i; p < s.length(); p++) {
            char c = s.charAt(p);
            if (c == target) return p;
            // Si on croise un '[' après, ce n’est plus le même token
            if (c == '[' && target == ']') break;
        }
        return -1;
    }
}