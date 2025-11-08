package writer;

import java.awt.Toolkit;
// Presse-papiers
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;

public final class WordSelectOnShiftRight {

    private static final String ACTION_EXTEND_RIGHT = "select-word-right";
    private static final String ACTION_SHRINK_LEFT  = "shrink-word-left";
    private static final String ACTION_PARA_DOWN    = "select-paragraph-down";

    private WordSelectOnShiftRight() {}

    /** Installe :
     *  - Shift+→ : étendre la sélection d'un mot vers la droite (et dire le mot ajouté),
     *  - Shift+← : réduire d'un mot depuis la droite (et dire le mot retiré),
     *  - Shift+↓ : sélectionner le paragraphe courant, puis ajouter le paragraphe suivant à chaque appui.
     *  À chaque fois, copie la sélection complète dans le presse-papiers.
     */
    @SuppressWarnings("serial")
	public static void install(final writer.ui.NormalizingTextPane comp) {
        if (comp == null) return;

        InputMap im = comp.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = comp.getActionMap();

        // Étendre par mot vers la droite
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK), ACTION_EXTEND_RIGHT);
        am.put(ACTION_EXTEND_RIGHT, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                extendSelectionOneWordRight(comp);
            }
        });

        // Réduire d'un mot depuis la droite
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK), ACTION_SHRINK_LEFT);
        am.put(ACTION_SHRINK_LEFT, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                shrinkSelectionOneWordFromRight(comp);
            }
        });

        // Sélectionner / étendre par paragraphe vers le bas
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK), ACTION_PARA_DOWN);
        am.put(ACTION_PARA_DOWN, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                extendSelectionOneParagraphDown(comp);
            }
        });
    }

    // ---------------------- Shift+↓ : paragraphes ----------------------

    /** Si pas de sélection → sélectionne le paragraphe courant.
     *  Sinon → normalise la sélection aux bornes de paragraphes et ajoute le paragraphe suivant.
     *  Annonce un extrait du paragraphe ajouté et copie la sélection complète.
     */
    private static void extendSelectionOneParagraphDown(writer.ui.NormalizingTextPane comp) {
        try {
            Document doc = comp.getDocument();
            int len = doc.getLength();
            if (len <= 0) return;

            String text = doc.getText(0, len);
            Caret caret = comp.getCaret();
            int dot = caret.getDot();
            int mark = caret.getMark();

            if (dot == mark) {
                // Pas de sélection → sélectionner le paragraphe courant
                int pos = clamp(text, dot);
                int pStart = paragraphStart(text, pos);
                int pEnd   = paragraphEnd(text, pos);
                if (pStart >= 0 && pEnd > pStart) {
                    comp.select(pStart, pEnd);
                    speakParagraphAdded(excerpt(text, pStart, pEnd));
                    copySelectionToClipboard(comp);
                }
                return;
            }

            // Sélection existante → normaliser aux paragraphes et/ou étendre au suivant
            int left  = Math.min(dot, mark);
            int right = Math.max(dot, mark);

            int normLeft  = paragraphStart(text, left);
            int normRight = paragraphEnd(text, Math.max(left, right - 1));

            boolean alreadyParagraphAligned = (left == normLeft) && (right == normRight);

            if (!alreadyParagraphAligned) {
                // 1er appui : on aligne proprement sur paragraphes
                caret.setDot(normLeft);
                caret.moveDot(normRight);
                speakParagraphSelected(excerpt(text, normLeft, normRight));
                copySelectionToClipboard(comp);
                return;
            }

            // Déjà aligné → on ajoute le paragraphe suivant
            int nextStart = skipLineBreaks(text, normRight);
            if (nextStart < 0 || nextStart >= len) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            int nextEnd = paragraphEnd(text, nextStart);
            caret.setDot(normLeft);
            caret.moveDot(nextEnd);

            speakParagraphAdded(excerpt(text, nextStart, nextEnd));
            copySelectionToClipboard(comp);

        } catch (BadLocationException ex) {
            // ignore
        }
    }

    // ---------------------- Shift+→ : mots vers la droite ----------------------

    private static void extendSelectionOneWordRight(writer.ui.NormalizingTextPane comp) {
        try {
            Document doc = comp.getDocument();
            int len = doc.getLength();
            if (len <= 0) return;

            String text = doc.getText(0, len);
            Caret caret = comp.getCaret();
            int dot = caret.getDot();
            int mark = caret.getMark();

            if (dot == mark) {
                int pos = Math.min(Math.max(dot, 0), len);
                int start, end;

                if (!isWordCharAt(text, pos)) {
                    int nextStart = nextWordStart(text, pos);
                    if (nextStart == -1) { Toolkit.getDefaultToolkit().beep(); return; }
                    start = nextStart;
                    end   = wordEnd(text, nextStart);
                } else {
                    start = wordStart(text, pos);
                    end   = wordEnd(text, pos);
                }

                if (start < end) {
                    comp.select(start, end);
                    speakAddedWord(safeSubstring(text, start, end));
                    copySelectionToClipboard(comp);
                }
                return;
            }

            int left  = Math.min(dot, mark);
            int right = Math.max(dot, mark);

            int nextStart = nextWordStart(text, right);
            if (nextStart == -1) {
                int end = right < len ? wordEnd(text, right) : len;
                if (end > right) {
                    caret.setDot(left);
                    caret.moveDot(end);
                    String lastWord = safeSubstring(text, wordStart(text, Math.min(end - 1, len - 1)), end);
                    speakAddedWord(lastWord);
                    copySelectionToClipboard(comp);
                } else {
                    Toolkit.getDefaultToolkit().beep();
                }
                return;
            }

            int nextEnd = wordEnd(text, nextStart);
            caret.setDot(left);
            caret.moveDot(nextEnd);
            speakAddedWord(safeSubstring(text, nextStart, nextEnd));
            copySelectionToClipboard(comp);

        } catch (BadLocationException ex) {
            // ignore
        }
    }

    // ---------------------- Shift+← : retirer un mot à droite ----------------------

    private static void shrinkSelectionOneWordFromRight(writer.ui.NormalizingTextPane comp) {
        try {
            Document doc = comp.getDocument();
            int len = doc.getLength();
            if (len <= 0) return;

            String text = doc.getText(0, len);
            Caret caret = comp.getCaret();
            int dot = caret.getDot();
            int mark = caret.getMark();

            if (dot == mark) { Toolkit.getDefaultToolkit().beep(); return; }

            int left  = Math.min(dot, mark);
            int right = Math.max(dot, mark);

            int endSearch = Math.max(left, right - 1);
            while (endSearch >= left && !isWordCharAt(text, endSearch)) endSearch--;
            if (endSearch < left) { Toolkit.getDefaultToolkit().beep(); return; }

            int removedStart = wordStart(text, endSearch);
            int removedEnd   = wordEnd(text, removedStart);
            int newRight = removedStart;

            caret.setDot(left);
            caret.moveDot(newRight);

            speakRemovedWord(safeSubstring(text, removedStart, removedEnd));
            copySelectionToClipboard(comp);

        } catch (BadLocationException ex) {
            // ignore
        }
    }

    // ---------------------- Helpers paragraphes ----------------------

    private static int paragraphStart(String text, int pos) {
        pos = clamp(text, pos);
        int iN = text.lastIndexOf('\n', Math.max(0, pos - 1));
        int iR = text.lastIndexOf('\r', Math.max(0, pos - 1));
        return Math.max(iN, iR) + 1; // -1 -> 0
    }

    private static int paragraphEnd(String text, int pos) {
        pos = clamp(text, pos);
        int iN = text.indexOf('\n', pos);
        int iR = text.indexOf('\r', pos);
        int end;
        if (iN == -1 && iR == -1) end = text.length();
        else if (iN == -1) end = iR;
        else if (iR == -1) end = iN;
        else end = Math.min(iN, iR);
        return Math.max(0, end);
    }

    /** Avance depuis 'pos' pour sauter \r et \n consécutifs ; retourne -1 si fin de texte. */
    private static int skipLineBreaks(String text, int pos) {
        int n = text.length();
        int i = Math.max(0, Math.min(pos, n));
        while (i < n && (text.charAt(i) == '\n' || text.charAt(i) == '\r')) i++;
        return (i < n) ? i : -1;
    }

    // ---------------------- Helpers mots ----------------------

    private static boolean isWordChar(char ch) {
        if (Character.isLetterOrDigit(ch)) return true;
        switch (ch) {
            case '\'': case '’': case '-': case '_':
                return true;
            default:
                return Character.getType(ch) == Character.NON_SPACING_MARK
                    || "àâäéèêëîïôöùûüçÀÂÄÉÈÊËÎÏÔÖÙÛÜÇ".indexOf(ch) >= 0;
        }
    }

    private static boolean isWordCharAt(String text, int i) {
        if (i < 0 || i >= text.length()) return false;
        return isWordChar(text.charAt(i));
    }

    private static int wordStart(String text, int i) {
        i = clamp(text, i);
        if (!isWordCharAt(text, i) && i > 0 && isWordCharAt(text, i - 1)) i--;
        while (i > 0 && isWordCharAt(text, i - 1)) i--;
        return i;
    }

    private static int wordEnd(String text, int i) {
        i = clamp(text, i);
        while (i < text.length() && isWordCharAt(text, i)) i++;
        return i;
    }

    private static int nextWordStart(String text, int i) {
        int n = text.length();
        i = Math.max(0, Math.min(i, n));
        while (i < n && isWordCharAt(text, i)) i++;
        while (i < n && !isWordCharAt(text, i)) i++;
        return (i < n) ? i : -1;
    }

    // ---------------------- Utilitaires communs ----------------------

    private static int clamp(String text, int i) {
        int n = text.length();
        return Math.max(0, Math.min(i, Math.max(0, n - 1)));
    }

    private static String safeSubstring(String s, int a, int b) {
        a = Math.max(0, Math.min(a, s.length()));
        b = Math.max(0, Math.min(b, s.length()));
        if (b <= a) return "";
        return s.substring(a, b);
    }

    /** Extrait un petit extrait lisible (début du paragraphe, limité). */
    private static String excerpt(String text, int start, int end) {
        String s = safeSubstring(text, start, end).trim();
        if (s.length() > 140) s = s.substring(0, 140) + "...";
        return s;
    }

     private static void speakAddedWord(String word) {
        speak("Dernier mot sélectionné : ", word);
    }
    private static void speakRemovedWord(String word) {
        speak("Mot désélectionné : ", word);
    }
    private static void speakParagraphAdded(String snippet) {
        speak("Paragraphe ajouté : ", snippet);
    }
    private static void speakParagraphSelected(String snippet) {
        speak("Paragraphe sélectionné : ", snippet);
    }
    private static void speak(String prefix, String content) {
        if (content == null) return;
        content = content.trim();
        if (content.isEmpty()) return;
    }

    // ---------------------- Presse-papiers ----------------------

    private static void copySelectionToClipboard(writer.ui.NormalizingTextPane comp) {
        String sel = comp.getSelectedText();
        if (sel == null) sel = "";
        copySelectionToClipboard(sel, 0);
    }
    private static void copySelectionToClipboard(String sel, int attempt) {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(new StringSelection(sel), null);
        } catch (IllegalStateException busy) {
            if (attempt < 3) {
                new javax.swing.Timer(120, ev -> {
                    ((javax.swing.Timer) ev.getSource()).stop();
                    copySelectionToClipboard(sel, attempt + 1);
                }).start();
            }
        } catch (Throwable ignored) {}
    }
}
