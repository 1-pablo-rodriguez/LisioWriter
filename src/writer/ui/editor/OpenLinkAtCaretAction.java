package writer.ui.editor;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import writer.ui.EditorFrame;
import writer.ui.NormalizingTextPane;

@SuppressWarnings("serial")
public final class OpenLinkAtCaretAction extends AbstractAction {

    // @[Titre : https://exemple.com]
    private static final Pattern AT_LINK = Pattern.compile(
        "@\\[\\s*([^\\]\\:]+?)\\s*:\\s*(https?://[^\\]\\s]+)\\s*\\]"
    );

    // URL brute
    private static final Pattern RAW_URL = Pattern.compile(
        "(?i)\\b((?:https?|ftp)://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)"
    );

    // Jeton [LienN] ou [lien 12]
    private static final Pattern TOKEN_LINK = Pattern.compile("\\[(?:Lien|lien)\\s*(\\d+)]");

    @SuppressWarnings("unused")
	private final EditorFrame parent;          // JFrame owner
    private final NormalizingTextPane editor;  // ton éditeur

    public OpenLinkAtCaretAction(EditorFrame parent) {
        super("bw-open-link");
        this.parent = Objects.requireNonNull(parent, "parent must not be null");
        JTextComponent ed = Objects.requireNonNull(parent.getEditor(), "parent.getEditor() must not be null");
        this.editor = (ed instanceof NormalizingTextPane) ? (NormalizingTextPane) ed : null;
        if (this.editor == null) {
            throw new IllegalStateException("Editor must be a NormalizingTextPane");
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            final int pos  = editor.getCaretPosition();
            final Document doc = editor.getDocument();
            final String text = doc.getText(0, doc.getLength());

            // 0) Si caret dans un jeton [LienN], résoudre via la map du Document
            Integer n = findTokenNumberUnderCaret(text, pos);
            if (n != null) {
                Map<Integer,String> map = LinkTokenIndexer.getLinkMap(doc);
                String url = (map != null) ? map.get(n) : null;
                if (url != null && !url.isBlank()) {
                    open("Lien " + n, url);
                    return;
                }
                // Si pas de map ou numéro absent → beep et on continue la détection classique
                Toolkit.getDefaultToolkit().beep();
            }

            // 1) caret dans une URL de lien LisioWriter @[Titre : URL]
            String[] link = findAtLinkUnderCaret(text, pos);
            if (link != null) { open(link[0], link[1]); return; }

            // 2) caret sur une URL brute
            String raw = findRawUrlUnderCaret(text, pos);
            if (raw != null) { open(raw, raw); return; }

            Toolkit.getDefaultToolkit().beep();
        } catch (Exception ex) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    // --- Détections ---

    private static String[] findAtLinkUnderCaret(String text, int caret) {
        Matcher m = AT_LINK.matcher(text);
        while (m.find()) {
            int urlStart = m.start(2), urlEnd = m.end(2);
            if (caret >= urlStart && caret <= urlEnd) {
                return new String[]{ m.group(1).trim(), m.group(2).trim() };
            }
        }
        return null;
    }

    private static String findRawUrlUnderCaret(String text, int caret) {
        Matcher m = RAW_URL.matcher(text);
        while (m.find()) {
            int start = m.start(1), end = m.end(1);
            if (caret >= start && caret <= end) return m.group(1).trim();
        }
        return null;
    }

    /** Retourne le numéro N si le caret est *à l’intérieur* d’un token [LienN], sinon null. */
    private static Integer findTokenNumberUnderCaret(String text, int caret) {
        // Cherche le '[' le plus proche à gauche et le ']' le plus proche à droite
        int left = findPrevChar(text, caret, '[');
        int right = findNextChar(text, caret, ']');
        if (left < 0 || right < 0 || right <= left) return null;

        String candidate = text.substring(left, right + 1); // inclut les crochets
        Matcher mt = TOKEN_LINK.matcher(candidate);
        if (!mt.matches()) return null;

        try {
            return Integer.parseInt(mt.group(1));
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private static int findPrevChar(String s, int from, char target) {
        int i = Math.min(Math.max(0, from), s.length());
        for (int p = i - 1; p >= 0; p--) {
            char c = s.charAt(p);
            if (c == target) return p;
            if (c == ']') break; // on est entré dans un autre token
        }
        return -1;
    }

    private static int findNextChar(String s, int from, char target) {
        int i = Math.min(Math.max(0, from), s.length());
        for (int p = i; p < s.length(); p++) {
            char c = s.charAt(p);
            if (c == target) return p;
            if (c == '[' && target == ']') break; // nouveau token, stop
        }
        return -1;
    }
   
    /** Ouvre un lien :
     *  - Wikipédia → import direct dans l’éditeur
     *  - sinon     → navigateur par défaut
     */
    private void open(String title, String url) {
        if (isWikipediaUrl(url)) {
            System.out.println("Import direct Wikipédia : " + title + " → " + url);
            dia.HtmlBrowserDialog.insertArticleDirect(editor, url);
        } else {
            System.out.println("Ouverture navigateur : " + title + " → " + url);
            openInBrowser(url);
        }
    }

    /** Teste si l’URL est de la forme https://xx.wikipedia.org/... */
    private boolean isWikipediaUrl(String url) {
        if (url == null) return false;

        // Si tu veux respecter *strictement* "https://??.wikipedia.org*"
        // (2 lettres de langue exactement) :
        // return url.matches("^https://[a-zA-Z]{2}\\.wikipedia\\.org.*$");

        // Version plus souple : langue de longueur variable (fr, en, frp, zh-min-nan, etc.)
        return url.matches("^https://[a-zA-Z-]+\\.wikipedia\\.org.*$");
    }

    /** Ouverture dans le navigateur par défaut */
    private void openInBrowser(String url) {
        if (!Desktop.isDesktopSupported()) {
            System.err.println("Desktop non supporté, impossible d’ouvrir le navigateur.");
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
