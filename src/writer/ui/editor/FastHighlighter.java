package writer.ui.editor;

import javax.swing.text.*;
import java.awt.*;
import java.nio.CharBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coloration incrémentale ultra-localisée, sans new String :
 * - Recolorise UNIQUEMENT le paragraphe courant (+ celui d’arrivée si on traverse un '\n')
 * - Passe CharSequence (Segment) pour matcher sans copier
 * - Court-circuits "par sentinelles" pour éviter les regex inutiles
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
    private static AttributeSet styleRich(Boolean bold, Boolean italic, Boolean underline,
                                          Boolean sub, Boolean sup, Color fg) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        if (fg != null) StyleConstants.setForeground(s, fg);
        if (bold   != null) StyleConstants.setBold(s, bold);
        if (italic != null) StyleConstants.setItalic(s, italic);
        if (underline != null) StyleConstants.setUnderline(s, underline);
        if (sub != null) StyleConstants.setSubscript(s, sub);
        if (sup != null) StyleConstants.setSuperscript(s, sup);
        return s;
    }

    private static final AttributeSet ST_NORMAL   = style(Color.WHITE,            false, false, false);
    private static final AttributeSet ST_CODE     = style(new Color(255,180,80),  true,  false, false);
    private static final AttributeSet ST_NOTE     = style(new Color(200,150,255), false, true,  false);
    private static final AttributeSet ST_LINK     = style(new Color( 80,170,255), false, false, true);
    private static final AttributeSet ST_PREFIX   = style(new Color(180,180,180), false, false, false);
    private static final AttributeSet ST_IMG      = style(new Color(  0,220,100), true,  false, false);
    private static final AttributeSet ST_BRAILLE  = style(Color.YELLOW,           true,  false, false);

    // Styles de formats texte
    private static final AttributeSet FMT_B       = styleRich(true,  null,  null,  null,  null,  null);
    private static final AttributeSet FMT_I       = styleRich(null,  true,  null,  null,  null,  null);
    private static final AttributeSet FMT_U       = styleRich(null,  null,  true,  null,  null,  null);
    private static final AttributeSet FMT_BI      = styleRich(true,  true,  null,  null,  null,  null);
    private static final AttributeSet FMT_BU      = styleRich(true,  null,  true,  null,  null,  null);
    private static final AttributeSet FMT_IU      = styleRich(null,  true,  true,  null,  null,  null);
    private static final AttributeSet FMT_SUB     = styleRich(null,  null,  null,  true,  null,  null);
    private static final AttributeSet FMT_SUP     = styleRich(null,  null,  null,  null,  true,  null);
    private static final AttributeSet FMT_MARK    = style(new Color(150,150,150), false, false, false); // marqueurs gris

    // ---------- Regex précompilées (structure) ----------
    private static final String BRAILLE = "\u00B6";
    private static final Pattern PT_TITLE        = Pattern.compile("(?m)^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?#([1-5PSps])\\.");
    private static final Pattern PT_LIST         = Pattern.compile("(?m)^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?-\\.");
    private static final Pattern PT_LISTNUM      = Pattern.compile("(?m)^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?[1-9]\\d*\\.");
    private static final Pattern PT_TAB          = Pattern.compile("(?i)\\[tab\\]");
    private static final Pattern PT_SPECIAL      = Pattern.compile("(__|_\\*|_\\^|\\*\\^|\\*\\*|\\^\\^|\\^¨|_¨|¨_|\\^\\*|\\^_|¨\\^|\\*_)");
    private static final Pattern PT_NOTE         = Pattern.compile("@\\(([^)]+)\\)");
    private static final Pattern PT_PAGE         = Pattern.compile("(?i)@saut\\s+de\\s+page(\\s+manuel)?");
    private static final Pattern PT_LINK         = Pattern.compile("@\\[([^\\]]+?):\\s*(https?://[^\\s\\]]+)\\]");
    private static final Pattern PT_IMG          = Pattern.compile("!\\[([^\\]]*?):\\s*([^\\]]+)\\]");
    private static final Pattern PT_BRAILLE_PREF = Pattern.compile("(?m)^\\s*(" + Pattern.quote(BRAILLE) + ")");
    private static final Pattern PT_TOKEN_LINK   = Pattern.compile("\\[(?:Lien|lien)\\s*\\d+]");

    // Balises de tableau
    private static final Pattern PT_T_OPEN  = Pattern.compile("(?m)^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?@t\\s*$");
    private static final Pattern PT_T_CLOSE = Pattern.compile("(?m)^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?@/t\\s*$");

    /** À appeler UNE FOIS après création de l’éditeur. */
    public static void install(writer.ui.NormalizingTextPane editor) {
        StyledDocument doc = editor.getStyledDocument();
        if (doc instanceof AbstractDocument adoc) {
            adoc.setDocumentFilter(new DF(editor));
        }
        try {
            withUndoSuspended(doc, () -> {
                try { rehighlight(editor, 0, doc.getLength()); }
                catch (BadLocationException e) { throw new RuntimeException(e); }
            });
        } catch (RuntimeException ex) {
            ex.printStackTrace();
        }
    }

    /** Optionnel : passe globale après gros setText()/import. */
    public static void rehighlightAll(writer.ui.NormalizingTextPane editor) {
        try {
            StyledDocument doc = editor.getStyledDocument();
            Element root = doc.getDefaultRootElement();
            withUndoSuspended(doc, () -> {
                int n = root.getElementCount();
                for (int i = 0; i < n; i++) {
                    Element para = root.getElement(i);
                    int start = para.getStartOffset();
                    int end   = Math.min(para.getEndOffset(), doc.getLength());
                    try { rehighlight(editor, start, end); }
                    catch (BadLocationException e) { throw new RuntimeException(e); }
                }
            });
        } catch (RuntimeException ex) {
            ex.printStackTrace();
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

        /** Recolorise : ligne de départ (+ ligne d'arrivée si on traverse un '\n'). */
        private void dirtyMinimal(writer.ui.NormalizingTextPane ed, int offset, int span)
                throws BadLocationException {
            StyledDocument doc = ed.getStyledDocument();
            Element root = doc.getDefaultRootElement();
            int len = doc.getLength();

            int startLine   = root.getElementIndex(Math.max(0, Math.min(offset, len)));
            int lastTouched = Math.max(0, Math.min(len, offset + Math.max(0, span - 1)));
            int endLine     = root.getElementIndex(lastTouched);

            withUndoSuspended(doc, () -> {
                try {
                    rehighlightLine(ed, startLine);
                    if (endLine != startLine) rehighlightLine(ed, endLine);
                } catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
            });
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
        int end   = Math.min(para.getEndOffset(), doc.getLength());
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

        // 2) Lecture de la fenêtre (un paragraphe / une ligne logique) en CharSequence
        final CharSequence s = getTextSlice(doc, start, end);

        // 3) Début logique (trim gauche + braille éventuel)
        int idx = 0;
        final int N = s.length();
        while (idx < N && Character.isWhitespace(s.charAt(idx))) idx++;

        if (idx < N && startsWithAt(s, BRAILLE, idx)) {
            doc.setCharacterAttributes(start + idx, 1, ST_BRAILLE, false);
            idx++;
            while (idx < N && Character.isWhitespace(s.charAt(idx))) idx++;
        }

        // 3bis) Sentinelles → coupe les regex inutiles
        final boolean hasAt       = indexOf(s, '@', 0) >= 0;
        final boolean hasPipe     = indexOf(s, '|', 0) >= 0;
        final boolean hasBracket  = indexOf(s, '[', 0) >= 0; // [tab], [LienN], ![...]
        final boolean hasBang     = indexOf(s, '!', 0) >= 0; // ![...] image
        final boolean hasFmtChars = (indexOf(s, '*', 0) >= 0) || (indexOf(s, '^', 0) >= 0)
                                 || (indexOf(s, '_', 0) >= 0) || (indexOf(s, '¨', 0) >= 0);
        final char head0          = (idx < N ? s.charAt(idx) : '\0');
        final boolean headLooksListOrTitle =
                head0 == '#' || head0 == '-' || (head0 >= '0' && head0 <= '9');

        // 4) Tables : lignes spéciales @t/@/t (si et seulement si '@' présent)
        if (hasAt && (PT_T_OPEN.matcher(s).matches() || PT_T_CLOSE.matcher(s).matches())) {
            doc.setCharacterAttributes(start, end - start, ST_CODE, false);
        } else {
            // 4b) Lignes de tableau commençant par '|' ou '|!'
            if (head0 == '|') {
                if (idx + 1 < N && s.charAt(idx + 1) == '!') {
                    doc.setCharacterAttributes(start + idx, 2, ST_CODE, false);
                    colorizeUnescapedPipes(doc, s, start, idx + 2);
                } else {
                    doc.setCharacterAttributes(start + idx, 1, ST_CODE, false);
                    colorizeUnescapedPipes(doc, s, start, idx + 1);
                }
            }
        }

        // 5) Titres / listes (seulement si le début logique le suggère)
        if (headLooksListOrTitle) {
            applyPattern(doc, s, start, PT_TITLE,   ST_CODE);
            applyPattern(doc, s, start, PT_LIST,    ST_CODE);
            applyPattern(doc, s, start, PT_LISTNUM, ST_CODE);
        }

        // 6) Tokens entre crochets (si '[' présent)
        if (hasBracket) {
            applyPattern(doc, s, start, PT_TAB,        ST_CODE);
            applyPattern(doc, s, start, PT_TOKEN_LINK, ST_LINK);
        }
        // 6b) Image ![...]
        if (hasBang && hasBracket) {
            applyPattern(doc, s, start, PT_IMG, ST_IMG);
        }

        // 7) Choses au '@' (si '@' présent)
        if (hasAt) {
            applyPattern(doc, s, start, PT_NOTE, ST_NOTE);
            applyPattern(doc, s, start, PT_PAGE, ST_NOTE);

            // Liens @[...] avec groupes (préfixe + URL)
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
        }

        // 8) Formats de texte (si on voit au moins un char de format)
        if (hasFmtChars) {
            // simples
            applyDelimited(doc, s, start, "**", "**", FMT_B,  true);
            applyDelimited(doc, s, start, "^^", "^^", FMT_I,  true);
            applyDelimited(doc, s, start, "__", "__", FMT_U,  true);

            // combinaisons
            applyDelimited(doc, s, start, "*^", "^*", FMT_BI, true); // *^...^*
            applyDelimited(doc, s, start, "_*", "*_", FMT_BU, true); // _*...*_
            applyDelimited(doc, s, start, "_^", "^_", FMT_IU, true); // _^...^_

            // indice / exposant :  _¨  ¨_   et   ^¨  ¨^
            applyDelimited(doc, s, start, "_¨", "¨_", FMT_SUB, true); // H_¨2¨_O
            applyDelimited(doc, s, start, "^¨", "¨^", FMT_SUP, true); // m^¨3¨^
        }

        // 9) Braille en début de paragraphe (hors tableaux) pour uniformité
        applyPattern(doc, s, start, PT_BRAILLE_PREF, ST_BRAILLE);
    }

    // -------- Formats : moteur de délimiteurs --------

    /** Applique attrs au contenu entre open/close, et grise les marqueurs si mark=true. Non-gourmand, sans allocation. */
    private static void applyDelimited(StyledDocument doc, CharSequence s, int base,
                                       String open, String close, AttributeSet attrs, boolean mark) {
        final int N = s.length();
        final int OL = open.length(), CL = close.length();
        int i = indexOfSeq(s, open, 0);
        while (i >= 0) {
            int j = indexOfSeq(s, close, i + OL);
            if (j < 0) break; // pas de fermeture → on s'arrête
            int contentStart = i + OL;
            int contentLen   = j - contentStart;
            if (contentLen > 0) {
                // contenu
                doc.setCharacterAttributes(base + contentStart, contentLen, attrs, false);
                if (mark) {
                    // marqueurs en gris (optionnel, lisible)
                    doc.setCharacterAttributes(base + i, OL, FMT_MARK, false);
                    doc.setCharacterAttributes(base + j, CL, FMT_MARK, false);
                }
            }
            i = indexOfSeq(s, open, j + CL); // continue après le close
        }
    }

    /** Colorise tous les '|' non échappés (\\|) à partir de 'from'. */
    private static void colorizeUnescapedPipes(StyledDocument doc, CharSequence s, int base, int from) {
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

    private static void applyPattern(StyledDocument doc, CharSequence s, int base, Pattern p, AttributeSet st) {
        Matcher m = p.matcher(s); // Pattern marche sur CharSequence
        while (m.find()) {
            doc.setCharacterAttributes(base + m.start(), m.end() - m.start(), st, false);
        }
    }

    /** Wrappe un Segment en CharSequence sans copier (pas de new String). */
    private static CharSequence getTextSlice(Document doc, int start, int end) throws BadLocationException {
        Segment seg = new Segment();
        doc.getText(start, end - start, seg);
        return CharBuffer.wrap(seg.array, seg.offset, seg.count);
    }

    // Utils CharSequence
    private static boolean startsWithAt(CharSequence s, String needle, int off) {
        int n = needle.length();
        if (off < 0 || off + n > s.length()) return false;
        for (int i = 0; i < n; i++) if (s.charAt(off + i) != needle.charAt(i)) return false;
        return true;
    }
    private static int indexOf(CharSequence s, char ch, int from) {
        int n = s.length();
        for (int i = Math.max(0, from); i < n; i++) if (s.charAt(i) == ch) return i;
        return -1;
    }
    private static int indexOfSeq(CharSequence s, String pat, int from) {
        int n = s.length(), m = pat.length();
        if (m == 0) return from;
        for (int i = Math.max(0, from); i + m <= n; i++) {
            int k = 0;
            while (k < m && s.charAt(i + k) == pat.charAt(k)) k++;
            if (k == m) return i;
        }
        return -1;
    }

    // --- Recolorise le paragraphe qui contient le caret (sans modifier le texte)
    public static void rehighlightParagraphAtCaret(writer.ui.NormalizingTextPane editor) {
        try {
            StyledDocument doc = editor.getStyledDocument();
            Element root = doc.getDefaultRootElement();
            int pos  = safeOffset(doc, editor.getCaretPosition());
            int line = root.getElementIndex(pos);
            rehighlightLine(editor, line);
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    /** Active/désactive la recolorisation sur déplacement de caret. */
    public static void enableCaretRecolor(writer.ui.NormalizingTextPane editor, boolean enable) {
        final String KEY = "fh.caretListenerInstalled";
        if (enable) {
            if (Boolean.TRUE.equals(editor.getClientProperty(KEY))) return;
            javax.swing.event.CaretListener cl = e ->
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
                    } catch (BadLocationException ex2) { ex2.printStackTrace(); }
                });
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
        if (len == 0) return 0;
        if (pos < 0) return 0;
        if (pos >= len) return len - 1;
        return pos;
    }

    /** Exécute r avec l’Undo suspendu pour ce Document. */
    private static void withUndoSuspended(StyledDocument doc, Runnable r) {
        Object prev = doc.getProperty("fh.suspendUndo");
        try {
            doc.putProperty("fh.suspendUndo", Boolean.TRUE);
            r.run();
        } finally {
            doc.putProperty("fh.suspendUndo", prev);
        }
    }
}
