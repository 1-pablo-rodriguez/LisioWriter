package writer.ui.editor;

import javax.swing.text.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coloration incrémentale ultra-localisée :
 * - Recolorise UNIQUEMENT le paragraphe courant.
 * - Si l'édition traverse un '\n', recolorise aussi le paragraphe d'arrivée (et pas plus).
 * - Optionnel : rehighlightAll(editor) après un gros setText().
 */
public final class FastHighlighter {

    private FastHighlighter() {}

    // ---------- Styles réutilisables ----------
    private static AttributeSet style(Color fg, boolean bold, boolean italic, boolean underline) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        if (fg != null) StyleConstants.setForeground(s, fg);
        StyleConstants.setBold(s, bold);
        StyleConstants.setItalic(s, italic);
        StyleConstants.setUnderline(s, underline);
        return s;
    }

    private static final AttributeSet ST_NORMAL  = style(Color.WHITE,            false, false, false);
    private static final AttributeSet ST_CODE    = style(new Color(255,180,80),  true,  false, false);
    private static final AttributeSet ST_NOTE    = style(new Color(200,150,255), false, true,  false);
    private static final AttributeSet ST_LINK    = style(new Color( 80,170,255), false, false, true);
    private static final AttributeSet ST_PREFIX  = style(new Color(180,180,180), false, false, false);
    private static final AttributeSet ST_IMG     = style(new Color(  0,220,100), true,  false, false);
    private static final AttributeSet ST_BRAILLE = style(Color.YELLOW,           true,  false, false);

    // ---------- Regex précompilées ----------
    private static final String BRAILLE = "\u283F";
    private static final Pattern PT_TITLE   = Pattern.compile("(?m)^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?#([1-5PSps])\\.");
    private static final Pattern PT_LIST    = Pattern.compile("(?m)^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?-\\.");
    private static final Pattern PT_LISTNUM = Pattern.compile("(?m)^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?[1-9]\\d*\\.");
    private static final Pattern PT_TAB     = Pattern.compile("(?i)\\[tab\\]");
    private static final Pattern PT_SPECIAL = Pattern.compile("(__|_\\*|_\\^|\\*\\^|\\*\\*|\\^\\^|\\^¨|_¨|¨_|\\^\\*|\\^_|¨\\^|\\*_)");
    private static final Pattern PT_NOTE    = Pattern.compile("@\\(([^)]+)\\)");
    private static final Pattern PT_PAGE    = Pattern.compile("(?i)@saut\\s+de\\s+page(\\s+manuel)?");
    private static final Pattern PT_LINK    = Pattern.compile("@\\[([^\\]]+?):\\s*(https?://[^\\s\\]]+)\\]");
    private static final Pattern PT_IMG     = Pattern.compile("!\\[([^\\]]*?):\\s*([^\\]]+)\\]");
    private static final Pattern PT_BRAILLE_PREFIX = Pattern.compile("(?m)^\\s*(" + Pattern.quote(BRAILLE) + ")");

    // Balises de tableau
    private static final Pattern PT_T_OPEN  = Pattern.compile("(?m)^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?@t\\s*$");
    private static final Pattern PT_T_CLOSE = Pattern.compile("(?m)^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?@/t\\s*$");


    /** À appeler UNE FOIS après création de l’éditeur. */
    public static void install(writer.ui.NormalizingTextPane editor) {
        StyledDocument doc = editor.getStyledDocument();
        if (doc instanceof AbstractDocument adoc) {
            adoc.setDocumentFilter(new DF(editor));
        }
        // premier passage : tout le doc (optionnel, utile au démarrage)
        try {
            rehighlight(editor, 0, doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    /** Optionnel : à appeler après un gros setText()/import pour une passe globale. */
    public static void rehighlightAll(writer.ui.NormalizingTextPane editor) {
        try {
            StyledDocument doc = editor.getStyledDocument();
            Element root = doc.getDefaultRootElement();
            int n = root.getElementCount();
            for (int i = 0; i < n; i++) {
                Element para = root.getElement(i);
                int start = para.getStartOffset();
                int end   = Math.min(para.getEndOffset(), doc.getLength());
                rehighlight(editor, start, end); // ta routine “par ligne”
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }


    // ---------- DocumentFilter incrémental ultra-local ----------
    private static final class DF extends DocumentFilter {
        private final writer.ui.NormalizingTextPane editor;
        DF(writer.ui.NormalizingTextPane editor){ this.editor = editor; }

        @Override public void insertString(FilterBypass fb, int offset, String str, AttributeSet a)
                throws BadLocationException {
            fb.insertString(offset, str, a);
            int addLen = (str == null ? 0 : str.length());
            dirtyMinimal(editor, offset, addLen);
        }

        @Override public void remove(FilterBypass fb, int offset, int length)
                throws BadLocationException {
            fb.remove(offset, length);
            dirtyMinimal(editor, offset, Math.max(1, length));
        }

        @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            fb.replace(offset, length, text, attrs);
            int addLen = Math.max(length, (text == null ? 0 : text.length()));
            dirtyMinimal(editor, offset, addLen);
        }

        /**
         * Ne recolorise que :
         *  - le paragraphe contenant 'offset'
         *  - et si l'opération traverse des '\n', le paragraphe d'arrivée.
         */
        private void dirtyMinimal(writer.ui.NormalizingTextPane ed, int offset, int span)
                throws BadLocationException {
            StyledDocument doc = ed.getStyledDocument();
            Element root = doc.getDefaultRootElement();
            int len = doc.getLength();

            int startLine = root.getElementIndex(Math.max(0, Math.min(offset, len)));
            int lastTouched = Math.max(0, Math.min(len, offset + Math.max(0, span - 1)));
            int endLine = root.getElementIndex(lastTouched);

            rehighlightLine(ed, startLine);
            if (endLine != startLine) rehighlightLine(ed, endLine);
        }
    }

    // ---------- Utilitaires de recoloration ----------
    private static void rehighlightLine(writer.ui.NormalizingTextPane editor, int lineIndex)
            throws BadLocationException {
        StyledDocument doc = editor.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        int count = root.getElementCount();
        if (count == 0) return;

        if (lineIndex < 0) lineIndex = 0;
        if (lineIndex >= count) lineIndex = count - 1;

        Element para = root.getElement(lineIndex);
        int start = para.getStartOffset();
        int end   = Math.min(para.getEndOffset(), doc.getLength()); // borne haute sûre
        if (end <= start) return;

        rehighlight(editor, start, end);
    }

    /** Recolorise uniquement [start, end). */
    private static void rehighlight(writer.ui.NormalizingTextPane editor, int start, int end)
            throws BadLocationException {
        StyledDocument doc = editor.getStyledDocument();
        end = Math.min(end, doc.getLength());
        if (end <= start) return;

        // 1) Reset local à neutre
        doc.setCharacterAttributes(start, end - start, ST_NORMAL, true);

        // 2) Lecture de la fenêtre (un paragraphe / une ligne logique)
        final String s = getTextRange(doc, start, end);

        // -------------------------------
        // 3) Bloc TABLES (Fix B : par ligne)
        // -------------------------------
        // a) Trim à gauche pour tester le début réel
        int idx = 0;
        while (idx < s.length() && Character.isWhitespace(s.charAt(idx))) idx++;

        // b) Braille en tête de ligne ? (on le colorise et on avance l'index de départ)
        if (idx < s.length() && s.startsWith(BRAILLE, idx)) {
            doc.setCharacterAttributes(start + idx, 1, ST_BRAILLE, false);
            idx++;
            while (idx < s.length() && Character.isWhitespace(s.charAt(idx))) idx++;
        }

        // c) Sous-chaîne "logique" de début de ligne (après espaces + braille)
        String head = (idx < s.length()) ? s.substring(idx) : "";

        // d) Marqueurs exacts @t et @/t (ligne de contrôle de tableau)
        //    -> Si tu as déjà défini PT_T_OPEN/PT_T_CLOSE comme "ligne entière", tu peux aussi faire :
        //       if (PT_T_OPEN.matcher(s).matches() || PT_T_CLOSE.matcher(s).matches()) { ... }
        //    Ici, on reste simple et strict : @t ou @/t + espaces de fin uniquement.
        if (PT_T_OPEN.matcher(s).matches() || PT_T_CLOSE.matcher(s).matches()) {
            // Colorise toute la ligne (fenêtre) en style "code"
            doc.setCharacterAttributes(start, end - start, ST_CODE, false);
        } else {
            // e) Lignes de tableau : | (ligne) ou |! (en-tête)
            if (!head.isEmpty() && head.charAt(0) == '|') {
                if (head.length() >= 2 && head.charAt(1) == '!') {
                    // "|!" au début → colorise ces 2 caractères
                    doc.setCharacterAttributes(start + idx, 2, ST_CODE, false);
                    // puis tous les '|' non échappés à partir de idx+2
                    colorizeUnescapedPipes(doc, s, start, idx + 2);
                } else {
                    // "|" simple au début
                    doc.setCharacterAttributes(start + idx, 1, ST_CODE, false);
                    colorizeUnescapedPipes(doc, s, start, idx + 1);
                }
            }
        }

        // --------------------------------
        // 4) Autres patterns (hors "bloc tables" ci-dessus)
        // --------------------------------
        applyPattern(doc, s, start, PT_TITLE,   ST_CODE);
        applyPattern(doc, s, start, PT_LIST,    ST_CODE);
        applyPattern(doc, s, start, PT_LISTNUM, ST_CODE);
        applyPattern(doc, s, start, PT_TAB,     ST_CODE);
        applyPattern(doc, s, start, PT_SPECIAL, ST_CODE);
        applyPattern(doc, s, start, PT_NOTE,    ST_NOTE);
        applyPattern(doc, s, start, PT_PAGE,    ST_NOTE);
        applyPattern(doc, s, start, PT_IMG,     ST_IMG);

        // 5) Liens : préfixe + URL
        Matcher lm = PT_LINK.matcher(s);
        while (lm.find()) {
            int fullStart = start + lm.start();
            int urlStart  = start + lm.start(2);
            int urlEnd    = start + lm.end(2);
            if (urlStart > fullStart) {
                doc.setCharacterAttributes(fullStart, urlStart - fullStart, ST_PREFIX, false);
            }
            doc.setCharacterAttributes(urlStart, urlEnd - urlStart, ST_LINK, false);
        }

        // 6) Braille en début de paragraphe (hors tableaux) pour uniformité
        applyPattern(doc, s, start, PT_BRAILLE_PREFIX, ST_BRAILLE);

        // 7) Rafraîchissement
        editor.repaint();
    }


    /** Colorise tous les '|' non échappés (\\|) à partir de 'from' dans la chaîne 's'. */
    private static void colorizeUnescapedPipes(StyledDocument doc, String s, int base, int from) {
        boolean esc = false;
        for (int i = from; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (ch == '\\') { esc = true; continue; }
            if (ch == '|') {
                doc.setCharacterAttributes(base + i, 1, ST_CODE, false);
            }
        }
    }

    private static void applyPattern(StyledDocument doc, String s, int base, Pattern p, AttributeSet st) {
        Matcher m = p.matcher(s);
        while (m.find()) {
            doc.setCharacterAttributes(base + m.start(), m.end() - m.start(), st, false);
        }
    }

    private static String getTextRange(Document doc, int start, int end) throws BadLocationException {
        Segment seg = new Segment();
        doc.getText(start, end - start, seg);
        return new String(seg.array, seg.offset, seg.count);
    }
    
 // --- Recolorise le paragraphe qui contient le caret (sans modifier le texte)
    public static void rehighlightParagraphAtCaret(writer.ui.NormalizingTextPane editor) {
        try {
            StyledDocument doc = editor.getStyledDocument();
            Element root = doc.getDefaultRootElement();

            // offset sûr
            int pos = safeOffset(doc, editor.getCaretPosition());
            int line = root.getElementIndex(pos);

            rehighlightLine(editor, line);
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }


    /**
     * Active/désactive la recolorisation sur déplacement de caret.
     * On ne recolorise que si la ligne a changé (anti-spam), et on recolorise aussi quand l'éditeur prend le focus.
     */
    public static void enableCaretRecolor(writer.ui.NormalizingTextPane editor, boolean enable) {
        final String KEY = "fh.caretListenerInstalled";
        if (enable) {
            if (Boolean.TRUE.equals(editor.getClientProperty(KEY))) return;

            javax.swing.event.CaretListener cl = e -> {
                // différer pour éviter les races avec les updates de l’arbre d’éléments
                javax.swing.SwingUtilities.invokeLater(() -> {
                    try {
                        StyledDocument doc = editor.getStyledDocument();
                        Element root = doc.getDefaultRootElement();

                        int pos  = safeOffset(doc, e.getDot());
                        int line = root.getElementIndex(pos);

                        Integer last = (Integer) editor.getClientProperty("fh.lastLine");
                        if (last != null && last == line) return;

                        editor.putClientProperty("fh.lastLine", line);
                        rehighlightLine(editor, line);
                    } catch (BadLocationException ex2) {
                        ex2.printStackTrace();
                    }
                });
            };
            editor.putClientProperty("fh.caretListener", cl);
            editor.addCaretListener(cl);

            java.awt.event.FocusListener fl = new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) {
                    javax.swing.SwingUtilities.invokeLater(() -> rehighlightParagraphAtCaret(editor));
                }
            };
            editor.putClientProperty("fh.focusListener", fl);
            editor.addFocusListener(fl);

            editor.putClientProperty(KEY, Boolean.TRUE);
            javax.swing.SwingUtilities.invokeLater(() -> rehighlightParagraphAtCaret(editor));

        } else {
            Object o1 = editor.getClientProperty("fh.caretListener");
            if (o1 instanceof javax.swing.event.CaretListener cl) editor.removeCaretListener(cl);
            Object o2 = editor.getClientProperty("fh.focusListener");
            if (o2 instanceof java.awt.event.FocusListener fl) editor.removeFocusListener(fl);
            editor.putClientProperty(KEY, Boolean.FALSE);
        }
    }

    
    private static int safeOffset(Document doc, int pos) {
        int len = doc.getLength();
        if (len == 0) return 0;       // doc vide → 0
        if (pos < 0) return 0;
        if (pos >= len) return len - 1; // ne jamais pointer au-delà du dernier char
        return pos;
    }


}