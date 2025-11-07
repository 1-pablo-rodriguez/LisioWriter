package writer.ui.editor;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Action Delete intelligente (touche Suppr).
 * Règle clé :
 *  - On supprime toujours un saut de ligne avec le ⠿ qui suit (et l’espace juste après ⠿ s’il existe).
 *  - On NE PEUT PAS supprimer un '\n' (ou "\r\n") s’il n’est pas suivi d’un ⠿.
 * Autres règles (comme avant) : suppression des tokens en tête si la ligne commence par ⠿, etc.
 */
@SuppressWarnings("serial")
public class SmartDeleteAction extends AbstractAction {

    private static final char BRAILLE_CH = '\u283F';

    private final JTextComponent editorPane;
    private final javax.swing.Action fallback;

    // Pattern pour détecter un préfixe braille sur la ligne
    private static final Pattern LEADING_BRAILLE = Pattern.compile("^\\s*\\u283F\\s*");

    // Patterns réutilisés pour tokens en tête de ligne
    private static final Pattern TITLE_WITH_SPACE = Pattern.compile("^#\\d+\\.\\s");
    private static final Pattern TITLE_NO_SPACE   = Pattern.compile("^#\\d+\\.");
    private static final Pattern SPECIAL_TITLE_WITH_SPACE = Pattern.compile("^#([PS])\\.\\s");
    private static final Pattern SPECIAL_TITLE_NO_SPACE   = Pattern.compile("^#([PS])\\.");
    private static final Pattern AT_TOKEN = Pattern.compile("^@\\S*"); // token @... (jusqu'au 1er espace)

    public SmartDeleteAction(JTextComponent editorPane, javax.swing.Action fallback) {
        super("bw-smart-delete");
        this.editorPane = editorPane;
        this.fallback = fallback;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            int selStart = editorPane.getSelectionStart();
            int selEnd   = editorPane.getSelectionEnd();

            // 1) sélection -> supprimer
            if (selEnd > selStart) {
                editorPane.replaceSelection("");
                return;
            }

            int pos = editorPane.getCaretPosition();
            Document doc = editorPane.getDocument();
            int docLen = doc.getLength();

            // 2) Cas "Suppr" juste avant un saut de ligne : ON NE SUPPRIME QUE SI ⠿ SUIT IMMÉDIATEMENT
            //    - Unix:  "\n⠿"
            //    - Windows: "\r\n⠿"
            if (pos < docLen) {
                // a) CRLF
                if (pos + 2 < docLen && doc.getText(pos, 2).equals("\r\n")) {
                    char after = doc.getText(pos + 2, 1).charAt(0);
                    if (after == BRAILLE_CH) {
                        int delStart = pos;      // sur '\r'
                        int delEnd   = pos + 3;  // après ⠿
                        // avaler un espace juste après ⠿ si présent
                        if (delEnd < docLen && doc.getText(delEnd, 1).charAt(0) == ' ') {
                            delEnd++;
                        }
                        doc.remove(delStart, delEnd - delStart);
                        editorPane.setCaretPosition(Math.max(0, delStart));
                        return;
                    } else {
                        // \r\n non suivi d'un ⠿ -> interdit
                        Toolkit.getDefaultToolkit().beep();
                        return;
                    }
                }
                // b) LF seul
                if (doc.getText(pos, 1).charAt(0) == '\n') {
                    if (pos + 1 < docLen && doc.getText(pos + 1, 1).charAt(0) == BRAILLE_CH) {
                        int delStart = pos;      // sur '\n'
                        int delEnd   = pos + 2;  // après ⠿
                        if (delEnd < docLen && doc.getText(delEnd, 1).charAt(0) == ' ') {
                            delEnd++;
                        }
                        doc.remove(delStart, delEnd - delStart);
                        editorPane.setCaretPosition(Math.max(0, delStart));
                        return;
                    } else {
                        // \n non suivi d'un ⠿ -> interdit
                        Toolkit.getDefaultToolkit().beep();
                        return;
                    }
                }
            }

            // 3) Cas : caret placé sur le caractère ⠿ (Delete doit avaler la newline précédente + ce ⠿)
            if (pos < docLen) {
                char chAtPos = doc.getText(pos, 1).charAt(0);
                if (chAtPos == BRAILLE_CH) {
                    // Rechercher en arrière le \n (et gérer CRLF)
                    int j = pos - 1;
                    // sauter espaces entre \n et ⠿ (on autorise " \t")
                    while (j >= 0) {
                        char cj = doc.getText(j, 1).charAt(0);
                        if (cj == ' ' || cj == '\t') { j--; continue; }
                        break;
                    }
                    if (j >= 0) {
                        char before = doc.getText(j, 1).charAt(0);
                        if (before == '\n' || before == '\r') {
                            int nlStart = j;
                            if (before == '\n' && j - 1 >= 0 && doc.getText(j - 1, 1).charAt(0) == '\r') {
                                nlStart = j - 1; // CRLF
                            }
                            int delStart = nlStart;
                            int delEnd   = pos + 1; // après ⠿
                            // avaler un espace juste après ⠿ si présent
                            if (delEnd < docLen && doc.getText(delEnd, 1).charAt(0) == ' ') {
                                delEnd++;
                            }
                            doc.remove(delStart, delEnd - delStart);
                            editorPane.setCaretPosition(Math.max(0, delStart));
                            return;
                        } else {
                            // ⠿ isolé (pas de newline juste avant) -> interdit
                            Toolkit.getDefaultToolkit().beep();
                            return;
                        }
                    } else {
                        // début de doc et ⠿ sans newline avant -> interdit
                        Toolkit.getDefaultToolkit().beep();
                        return;
                    }
                }
            }

            // 4) [tab] si caret juste avant
            if (pos + 5 <= docLen) {
                String next5 = doc.getText(pos, 5);
                if ("[tab]".equals(next5)) {
                    doc.remove(pos, 5);
                    return;
                }
            }

            // 5) Codes en tête de ligne (puce / titres / @token) : seulement si la ligne commence par ⠿
            try {
                int lineStart = javax.swing.text.Utilities.getRowStart(editorPane, pos);
                if (lineStart >= 0) {
                    int maxRead = Math.min(1024, docLen - lineStart);
                    String line = doc.getText(lineStart, maxRead).replaceAll("\\R.*$", "");

                    // vérifier la marque braille en tête de ligne
                    Matcher lead = LEADING_BRAILLE.matcher(line);
                    int leadLen = lead.find() ? lead.end() : 0;

                    if (leadLen > 0) {
                        String normalized = line.substring(leadLen);
                        int tokenDocStart = lineStart + leadLen;

                        // ---- puce "-." (avec ou sans espace)
                        if (normalized.startsWith("-.")) {
                            int withSpaceLen = (normalized.length() > 2 && normalized.charAt(2) == ' ') ? 3 : 2;
                            if (pos == tokenDocStart) { // Delete juste avant le token
                                doc.remove(tokenDocStart, withSpaceLen);
                                return;
                            }
                        }

                        // ---- titres "#123." / "#123. "
                        Matcher mWithSpace = TITLE_WITH_SPACE.matcher(normalized);
                        Matcher mNoSpace   = TITLE_NO_SPACE.matcher(normalized);
                        int lenWith = mWithSpace.find() ? mWithSpace.end() : 0;
                        int lenNo   = mNoSpace.find() ? mNoSpace.end() : 0;
                        if ((lenNo > 0 && pos == tokenDocStart) || (lenWith > 0 && pos == tokenDocStart)) {
                            int removeLen = (lenWith > 0) ? lenWith : lenNo;
                            doc.remove(tokenDocStart, removeLen);
                            return;
                        }

                        // ---- titres spéciaux "#P." / "#S."
                        Matcher msWith = SPECIAL_TITLE_WITH_SPACE.matcher(normalized);
                        Matcher msNo   = SPECIAL_TITLE_NO_SPACE.matcher(normalized);
                        int msLenWith = msWith.find() ? msWith.end() : 0;
                        int msLenNo   = msNo.find() ? msNo.end() : 0;
                        if ((msLenNo > 0 && pos == tokenDocStart) || (msLenWith > 0 && pos == tokenDocStart)) {
                            int removeLen = (msLenWith > 0) ? msLenWith : msLenNo;
                            doc.remove(tokenDocStart, removeLen);
                            return;
                        }

                        // ---- @token
                        Matcher mat = AT_TOKEN.matcher(normalized);
                        if (mat.find() && pos == tokenDocStart) {
                            int tokenLen = mat.end();
                            doc.remove(tokenDocStart, tokenLen);
                            return;
                        }
                    }
                }
            } catch (BadLocationException ignoreLine) {
                // fallback après
            }

            // 6) Dernier contrôle : si le caractère suivant est un ⠿ isolé (pas précédé d'une newline)
            if (pos < docLen) {
                char nextChar = doc.getText(pos, 1).charAt(0);
                if (nextChar == BRAILLE_CH) {
                    boolean prevIsNewline = false;
                    if (pos - 1 >= 0) {
                        char before = doc.getText(pos - 1, 1).charAt(0);
                        if (before == '\n' || before == '\r') prevIsNewline = true;
                    }
                    if (!prevIsNewline) {
                        Toolkit.getDefaultToolkit().beep();
                        return;
                    }
                }
            }

            // 7) fallback : Delete par défaut
            if (fallback != null) {
                fallback.actionPerformed(e);
            } else if (pos < docLen) {
                doc.remove(pos, 1);
            }

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }
}