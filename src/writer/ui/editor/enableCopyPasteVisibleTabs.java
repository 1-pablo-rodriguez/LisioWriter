package writer.ui.editor;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;

/**
 * Utilitaire : gère le comportement "[tab]" visible, collage transformant les tabulations,
 * et ajoute le caractère braille ¶ au début de chaque paragraphe collé (si absent).
 *
 * Appel : enableCopyPasteVisibleTabs.enableVisibleTabs(myTextComponent);
 */
public final class enableCopyPasteVisibleTabs {

    private enableCopyPasteVisibleTabs() {}

    // Caractère braille et regex "commence déjà par ¶ (après espaces éventuels)"
    private static final char BRAILLE = '\u00B6';
    private static final Pattern LEADING_BRAILLE = Pattern.compile("^\\s*\\u00B6\\s*");

    @SuppressWarnings("serial")
    public static void enableVisibleTabs(final writer.ui.NormalizingTextPane editor) {
        if (editor == null) return;

        // 1) Le TAB ne doit pas déplacer le focus
        editor.setFocusTraversalKeysEnabled(false);

        // 2) Quand l'utilisateur TAPE TAB -> insérer "[tab]"
        InputMap im = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = editor.getActionMap();

        im.put(KeyStroke.getKeyStroke("TAB"), "bw-insert-tab-tag");
        im.put(KeyStroke.getKeyStroke("shift TAB"), "bw-insert-tab-tag");
        am.put("bw-insert-tab-tag", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                editor.replaceSelection("[tab]");
            }
        });

        // 3) Intercepter COLLER (Ctrl+V, Shift+Insert)
        im.put(KeyStroke.getKeyStroke("ctrl V"), "bw-paste-visible-tabs");
        im.put(KeyStroke.getKeyStroke("shift INSERT"), "bw-paste-visible-tabs");
        am.put("bw-paste-visible-tabs", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                try {
                    Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                    if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        String s = (String) cb.getData(DataFlavor.stringFlavor);
                        s = mapPaste(s); // \t -> [tab], puis préfixe ¶ au début de chaque paragraphe
                        editor.replaceSelection(s);
                        return;
                    }
                } catch (Exception ignore) {}
                // Fallback : comportement standard
                editor.paste();
            }
        });

        // 4) TransferHandler : couvre menu contextuel / DnD / coller système
        final TransferHandler baseTH = editor.getTransferHandler();
        editor.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(JComponent comp, DataFlavor[] flavors) {
                if (baseTH != null) {
                    try {
                        Method m = baseTH.getClass().getMethod("canImport", JComponent.class, DataFlavor[].class);
                        Object r = m.invoke(baseTH, comp, flavors);
                        if (r instanceof Boolean && (Boolean) r) return true;
                    } catch (NoSuchMethodException nsme) {
                        // fallback to direct call below
                    } catch (Throwable ignored) {}
                }
                for (DataFlavor f : flavors) if (DataFlavor.stringFlavor.equals(f)) return true;
                return false;
            }

            @Override
            public boolean importData(JComponent comp, Transferable t) {
                try {
                    if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String s = (String) t.getTransferData(DataFlavor.stringFlavor);
                        s = mapPaste(s);
                        if (comp instanceof writer.ui.NormalizingTextPane) {
                            ((writer.ui.NormalizingTextPane) comp).replaceSelection(s);
                            return true;
                        }
                    }
                } catch (Exception ignore) {}
                try {
                    return baseTH != null && baseTH.importData(comp, t);
                } catch (Throwable ignored) { return false; }
            }

            // délégation safe pour createTransferable (important pour Ctrl+C / Ctrl+X)
            @Override
            protected Transferable createTransferable(JComponent c) {
                if (baseTH != null) {
                    try {
                        Method m = baseTH.getClass().getDeclaredMethod("createTransferable", JComponent.class);
                        m.setAccessible(true);
                        Object res = m.invoke(baseTH, c);
                        if (res instanceof Transferable) return (Transferable) res;
                    } catch (Throwable ignored) {}
                }
                if (c instanceof writer.ui.NormalizingTextPane) {
                    String sel = ((writer.ui.NormalizingTextPane) c).getSelectedText();
                    if (sel != null) return new StringSelection(sel);
                }
                return null;
            }

            @Override
            public int getSourceActions(JComponent c) {
                if (baseTH != null) {
                    try {
                        Method m = baseTH.getClass().getMethod("getSourceActions", JComponent.class);
                        Object r = m.invoke(baseTH, c);
                        if (r instanceof Integer) return (Integer) r;
                    } catch (Throwable ignored) {}
                }
                return COPY; // fallback raisonnable
            }

            @Override
            public void exportToClipboard(JComponent comp, Clipboard clip, int action) throws IllegalStateException {
                if (baseTH != null) {
                    try {
                        Method m = baseTH.getClass().getMethod("exportToClipboard", JComponent.class, Clipboard.class, int.class);
                        m.invoke(baseTH, comp, clip, action);
                        return;
                    } catch (Throwable ignored) {}
                }
                Transferable t = createTransferable(comp);
                if (t != null) clip.setContents(t, null);
            }
        });

        // 5) DocumentFilter : couvre insertions programmées, replace, etc.
        Document doc = editor.getDocument();
        if (doc instanceof AbstractDocument) {
            AbstractDocument ad = (AbstractDocument) doc;
            ad.setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offs, String str, AttributeSet a)
                        throws BadLocationException {
                    super.insertString(fb, offs, mapPaste(str), a);
                }
                @Override
                public void replace(FilterBypass fb, int offs, int len, String str, AttributeSet a)
                        throws BadLocationException {
                    super.replace(fb, offs, len, mapPaste(str), a);
                }
            });
        }
    }

    // 1) \t -> [tab]  2) préfixe braille ¶ en tête de chaque paragraphe non vide
    private static String mapPaste(String s) {
        if (s == null || s.isEmpty()) return s;
        String withTabs = mapTabs(s);
        return addBrailleAtParagraphStarts(withTabs);
    }

    // remplace \t par [tab] (utilitaire)
    private static String mapTabs(String s) {
        return (s == null) ? null : s.replace("\t", "[tab] ");
    }

    /**
     * Ajoute "¶ " au début de chaque paragraphe non vide du bloc de texte,
     * sauf si le paragraphe commence déjà par ¶.
     * - Conserve les lignes vides telles quelles
     * - Gère CRLF / CR / LF
     */
    private static String addBrailleAtParagraphStarts(String text) {
        // normalise les fins de ligne pour itérer proprement
        String norm = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = norm.split("\n", -1); // -1 pour conserver les vides de fin
        StringBuilder out = new StringBuilder(norm.length() + lines.length * 2);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (i==0) {
                out.append(line); // conserve les lignes vides/espaces
            } else if (line.isBlank()) {
            	out.append(BRAILLE).append(line);
            } else if (LEADING_BRAILLE.matcher(line).find()) {
                out.append(line); // déjà préfixé : ne pas dupliquer
            } else {
                out.append(BRAILLE).append(' ').append(line);
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }
}