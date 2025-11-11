package writer.ui.editor;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
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

    @SuppressWarnings("unused")
	private final EditorFrame parent;          // JFrame owner
    private final NormalizingTextPane editor;  // ton éditeur

    public OpenLinkAtCaretAction(EditorFrame parent) {
        super("bw-open-link");
        this.parent = Objects.requireNonNull(parent, "parent must not be null");
        JTextComponent ed = Objects.requireNonNull(parent.getEditor(), "parent.getEditor() must not be null");
        // on s’assure du type attendu par HtmlBrowserDialog
        this.editor = (ed instanceof NormalizingTextPane) ? (NormalizingTextPane) ed
                                                          : null;
        if (this.editor == null) {
            throw new IllegalStateException("Editor must be a NormalizingTextPane");
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            final int pos = editor.getCaretPosition();
            final Document doc = editor.getDocument();
            final String text = doc.getText(0, doc.getLength());

            // 1) Si caret dans une URL de lien LisioWriter @[Titre : URL]
            String[] link = findAtLinkUnderCaret(text, pos);
            if (link != null) { open(link[0], link[1]); return; }

            // 2) Sinon, si caret sur une URL brute
            String raw = findRawUrlUnderCaret(text, pos);
            if (raw != null) { open(raw, raw); return; }

            Toolkit.getDefaultToolkit().beep();
        } catch (Exception ex) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

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

    /** Ouvre via ta boîte HtmlBrowserDialog (EDT). */
    // OpenLinkAtCaretAction.java
    private void open(String title, String url) {
        System.out.println("Import direct Wikipédia : " + title + " → " + url);

        // Option : restreindre au domaine Wikipédia si tu veux
        // if (!url.contains("wikipedia.org")) { Toolkit.getDefaultToolkit().beep(); return; }

        // Appel direct sans ouvrir de JDialog
        dia.HtmlBrowserDialog.insertArticleDirect(editor, url);
    }
}