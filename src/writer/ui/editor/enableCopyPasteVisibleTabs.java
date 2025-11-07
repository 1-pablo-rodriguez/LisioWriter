package writer.ui.editor;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;

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
import javax.swing.text.JTextComponent;

/**
 * Utilitaire : gère le comportement "[tab]" visible, collage transformant les tabulations,
 * et un TransferHandler qui restaure Ctrl+C / Ctrl+X / Ctrl+V correctement (avec fallback).
 *
 * Appel : VisibleTabs.enableVisibleTabs(myTextComponent);
 */
public final class enableCopyPasteVisibleTabs {

    private enableCopyPasteVisibleTabs() {}

    @SuppressWarnings("serial")
	public static void enableVisibleTabs(final JTextComponent editor) {
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
                        s = mapTabs(s);             // \t -> [tab]
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
                        s = mapTabs(s);
                        if (comp instanceof JTextComponent) {
                            ((JTextComponent) comp).replaceSelection(s);
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
                if (c instanceof JTextComponent) {
                    String sel = ((JTextComponent) c).getSelectedText();
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
                    super.insertString(fb, offs, mapTabs(str), a);
                }
                @Override
                public void replace(FilterBypass fb, int offs, int len, String str, AttributeSet a)
                        throws BadLocationException {
                    super.replace(fb, offs, len, mapTabs(str), a);
                }
            });
        }
    }

    // remplace \t par [tab] (utilitaire)
    private static String mapTabs(String s) {
        return (s == null) ? s : s.replace("\t", "[tab] ");
    }
}

