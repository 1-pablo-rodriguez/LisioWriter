package writer.ui.editor;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * Action Backspace intelligente :
 * - supprime [tab] (si caret juste après)
 * - supprime "-." (puce) seulement si la ligne commence par ⠿, le caret doit être juste après "-." ou "-. "
 * - supprime "#N." (titres numérotés) seulement si la ligne commence par ⠿, le caret doit être juste après "#N." ou "#N. "
 * - supprime "#P." / "#S." de même manière
 * - supprime les tokens "@xxx" en début de ligne (si caret juste après le token)
 * - supprime la séquence newline+⠿ ("\n⠿" ou "\r\n⠿") si le caret est juste après → joint les paragraphes
 * - empêche la suppression d'un caractère ⠿ isolé (sans newline juste avant)
 * - sinon fallback (backspace par défaut)
 */
@SuppressWarnings("serial")
public class SmartBackspaceAction extends AbstractAction {

    private final JTextComponent editorPane;
    private final javax.swing.Action fallback;

    // Pattern pour détecter une séquence "\r\n⠿[ ]" ou "\n⠿[ ]" juste avant le caret
    private static final Pattern PREV_BRAILLE_SEQ = Pattern.compile("(?:\\r\\n|\\n)\\s*\\u283F\\s?$");

    // Pattern pour détecter un préfixe braille sur la ligne
    private static final Pattern LEADING_BRAILLE = Pattern.compile("^\\s*\\u283F\\s*");

    // Patterns réutilisés
    private static final Pattern TITLE_WITH_SPACE = Pattern.compile("^#\\d+\\.\\s");
    private static final Pattern TITLE_NO_SPACE   = Pattern.compile("^#\\d+\\.");
    private static final Pattern SPECIAL_TITLE_WITH_SPACE = Pattern.compile("^#([PS])\\.\\s");
    private static final Pattern SPECIAL_TITLE_NO_SPACE   = Pattern.compile("^#([PS])\\.");
    private static final Pattern AT_TOKEN = Pattern.compile("^@\\S*"); // token @... (jusqu'au premier espace)

    public SmartBackspaceAction(JTextComponent editorPane, javax.swing.Action fallback) {
        super("bw-smart-backspace");
        this.editorPane = editorPane;
        this.fallback = fallback;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            int selStart = editorPane.getSelectionStart();
            int selEnd   = editorPane.getSelectionEnd();

            // 1) Supprimer une sélection complète
            if (selEnd > selStart) {
                editorPane.replaceSelection("");
                return;
            }

            int pos = editorPane.getCaretPosition();
            Document doc = editorPane.getDocument();
            
            // 2a) Cas "caret entre \\n et ⠿" : si le caret est juste APRÈS le saut de ligne
            //          et juste AVANT la marque ⠿, on supprime le groupe entier (\n⠿).
            if (pos > 0 && pos < doc.getLength()) {
            	char prev = doc.getText(pos - 1, 1).charAt(0);
            	// --- Cas simple: "\n⠿"
            	if (prev == '\n') {
            		char next = doc.getText(pos, 1).charAt(0);
            		if (next == '\u283F') { // ⠿
            			doc.remove(pos - 1, 2);       // supprime \n + ⠿
            			editorPane.setCaretPosition(Math.max(0, pos - 1));
            			return;
            			}
            		}
            	// --- Cas Windows: "\r\n⠿"
            	if (prev == '\r' && pos < doc.getLength()) {
            		// on attend "\r\n⠿" à partir de pos-1
            		int remain = Math.min(3, doc.getLength() - (pos - 1));
            		if (remain >= 3) {
            			String tri = doc.getText(pos - 1, 3); // "\r\n⠿" ?
            			if (tri.charAt(0) == '\r' && tri.charAt(1) == '\n' && tri.charAt(2) == '\u283F') {
            				doc.remove(pos - 1, 3);           // supprime \r + \n + ⠿
            				editorPane.setCaretPosition(Math.max(0, pos - 1));
            				return;
            				}
            			}
            		}
            	}

            // 2b) Si le caret est juste après une séquence newline + braille (ex: "\n⠿" ou "\r\n⠿"),
            //    supprimer ENTIEREMENT la séquence (newline + ⠿ [+ espace opt.]) -> joint les paragraphes.
            if (pos > 0) {
                int lookback = Math.min(256, pos); // fenêtre raisonnable
                int windowStart = pos - lookback;
                String context = doc.getText(windowStart, lookback);
                Matcher mSeq = PREV_BRAILLE_SEQ.matcher(context);
                if (mSeq.find() && mSeq.end() == context.length()) {
                    int matchStartInDoc = windowStart + mSeq.start();
                    int matchLen = mSeq.end() - mSeq.start();
                    doc.remove(matchStartInDoc, matchLen);
                    editorPane.setCaretPosition(Math.max(0, Math.min(matchStartInDoc, doc.getLength())));
                    return;
                }
            }

            // 3) Supprimer un bloc [tab] — seulement si caret est immédiatement après "[tab]"
            if (pos >= 5) {
                String prev = doc.getText(pos - 5, 5);
                if ("[tab]".equals(prev)) {
                    doc.remove(pos - 5, 5);
                    return;
                }
            }

            // 4) Gestion des codes en début de ligne (puce, titres, @token)
            try {
                int lineStart = javax.swing.text.Utilities.getRowStart(editorPane, pos);
                if (lineStart >= 0) {
                    int docLen = doc.getLength();
                    int maxRead = Math.min(1024, Math.max(1, docLen - lineStart));
                    String line = doc.getText(lineStart, maxRead).replaceAll("\\R.*$", ""); // lire la ligne entière (sécurisé)
                    // déterminer si la ligne commence par la marque braille
                    Matcher lead = LEADING_BRAILLE.matcher(line);
                    int leadLen = lead.find() ? lead.end() : 0;

                    if (leadLen > 0) {
                        String normalized = line.substring(leadLen); // ligne sans la marque braille en tête

                        // ------- PUCE "-." (accepte "-." ou "-. ")
                        if (normalized.startsWith("-.")) {
                            int noSpaceLen = 2;
                            int withSpaceLen = (normalized.length() > 2 && normalized.charAt(2) == ' ') ? 3 : 2;
                            int tokenEndNoSpace = lineStart + leadLen + noSpaceLen;
                            int tokenEndWithSpace = lineStart + leadLen + withSpaceLen;
                            if (pos == tokenEndNoSpace || pos == tokenEndWithSpace) {
                                // supprimer le token (sans toucher au ⠿)
                                doc.remove(lineStart + leadLen, withSpaceLen);
                                return;
                            }
                        }

                        // ------- TITRES numérqiues "#123." ou "#123. "
                        Matcher mWithSpace = TITLE_WITH_SPACE.matcher(normalized);
                        Matcher mNoSpace   = TITLE_NO_SPACE.matcher(normalized);
                        int lenWith = 0, lenNo = 0;
                        if (mWithSpace.find()) lenWith = mWithSpace.end();
                        if (mNoSpace.find())   lenNo   = mNoSpace.end();
                        // autoriser position juste après la forme sans espace OU juste après la forme avec espace
                        if (lenNo > 0) {
                            int tokenEndNo = lineStart + leadLen + lenNo;
                            if (pos == tokenEndNo) {
                                // si la version with-space existe et que caret était juste après le dot but before space,
                                // on still want to delete the token (we remove the with-space length if present)
                                int removeLen = (lenWith > 0) ? lenWith : lenNo;
                                doc.remove(lineStart + leadLen, removeLen);
                                return;
                            }
                        }
                        if (lenWith > 0) {
                            int tokenEndWith = lineStart + leadLen + lenWith;
                            if (pos == tokenEndWith) {
                                doc.remove(lineStart + leadLen, lenWith);
                                return;
                            }
                        }

                        // ------- TITRES spéciaux "#P." / "#S."
                        Matcher msWith = SPECIAL_TITLE_WITH_SPACE.matcher(normalized);
                        Matcher msNo   = SPECIAL_TITLE_NO_SPACE.matcher(normalized);
                        int msLenWith = msWith.find() ? msWith.end() : 0;
                        int msLenNo   = msNo.find() ? msNo.end() : 0;
                        if (msLenNo > 0) {
                            int tokenEndNo = lineStart + leadLen + msLenNo;
                            if (pos == tokenEndNo) {
                                int removeLen = (msLenWith > 0) ? msLenWith : msLenNo;
                                doc.remove(lineStart + leadLen, removeLen);
                                return;
                            }
                        }
                        if (msLenWith > 0) {
                            int tokenEndWith = lineStart + leadLen + msLenWith;
                            if (pos == tokenEndWith) {
                                doc.remove(lineStart + leadLen, msLenWith);
                                return;
                            }
                        }

                        // ------- @token (ex: @saut) : supprime le token ssi caret juste après le token
                        Matcher mat = AT_TOKEN.matcher(normalized);
                        if (mat.find()) {
                            int tokenLen = mat.end();
                            int tokenEndPos = lineStart + leadLen + tokenLen;
                            if (pos == tokenEndPos) {
                                doc.remove(lineStart + leadLen, tokenLen);
                                return;
                            }
                        }
                    }
                }
            } catch (BadLocationException ignoreLine) {
                // fallback après
            }

            // 5) Avant fallback : si le caractère précédent est ⠿ isolé (sans newline avant) -> refuser
            if (pos > 0) {
                char prevChar = doc.getText(pos - 1, 1).charAt(0);
                if (prevChar == '\u283F') {
                    boolean prevHasNewline = false;
                    if (pos - 2 >= 0) {
                        char chBefore = doc.getText(pos - 2, 1).charAt(0);
                        if (chBefore == '\n' || chBefore == '\r') prevHasNewline = true;
                    }
                    if (!prevHasNewline) {
                        Toolkit.getDefaultToolkit().beep();
                        return;
                    }
                }
            }

            // 6) fallback
            if (fallback != null) {
                fallback.actionPerformed(e);
            } else if (pos > 0) {
                doc.remove(pos - 1, 1);
            }

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }
}
