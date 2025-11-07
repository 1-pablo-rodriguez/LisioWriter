package writer.ui.editor;

import javax.swing.text.NavigationFilter;
import javax.swing.text.Position.Bias;
import javax.swing.text.JTextComponent;
import javax.swing.text.Document;

/**
 * NavigationFilter qui:
 *  - empêche le caret d'être à 0 (si doc non vide, on force >=1)
 *  - empêche le caret de se placer entre '\n' et '⠿'
 *    et saute le couple \n⠿ selon la direction (←/→).
 */
public final class NoGapAcrossBrailleNavigationFilter extends NavigationFilter {

    private static final char BRAILLE_CH = '\u283F';

    private final NavigationFilter delegate;
    private final JTextComponent editor;

    public NoGapAcrossBrailleNavigationFilter(JTextComponent editor, NavigationFilter delegate) {
        this.editor = editor;
        this.delegate = delegate;
    }

    @Override
    public void setDot(FilterBypass fb, int dot, Bias bias) {
        dot = adjustDot(dot);
        if (delegate != null) delegate.setDot(fb, dot, bias);
        else super.setDot(fb, dot, bias);
    }

    @Override
    public void moveDot(FilterBypass fb, int dot, Bias bias) {
        dot = adjustDot(dot);
        if (delegate != null) delegate.moveDot(fb, dot, bias);
        else super.moveDot(fb, dot, bias);
    }

    /** Applique les règles: pas de 0 (si non vide) et pas "entre \\n et ⠿". */
    private int adjustDot(int dot) {
        try {
            Document doc = editor.getDocument();
            int len = doc.getLength();

            // 1) Interdit 0 si le document n'est pas vide
            if (len > 0 && dot <= 0) dot = 1;

            // 2) Interdit la "fente" entre '\n' et '⠿'
            if (dot > 0 && dot < len) {
                char prev = doc.getText(dot - 1, 1).charAt(0);
                char next = doc.getText(dot, 1).charAt(0);

                if (prev == '\n' && next == BRAILLE_CH) {
                    // On choisit où atterrir selon la direction:
                    // - si on vient de la droite (caret actuel >= dot), on saute AVANT le \n⠿
                    // - sinon on vient de la gauche, on saute APRES le ⠿
                    int cur = safeCaretPosition(editor, len);

                    if (cur >= dot) {
                        // Aller "devant \n⠿" = avant le \n
                        int before = dot - 1; // position avant '\n'
                        // Si CRLF, on recule encore d'un pour être avant le '\r'
                        if (before - 1 >= 0 && doc.getText(before - 1, 1).charAt(0) == '\r') {
                            before = before - 1;
                        }
                        return before;
                    } else {
                        // Aller "après \n⠿" = juste après le ⠿ (on ne saute pas l'espace qui suivrait)
                        return dot + 1;
                    }
                }
            }
        } catch (Exception ignore) {
            // en cas de souci, on ne modifie pas dot
        }
        return dot;
    }

    private static int safeCaretPosition(JTextComponent c, int len) {
        int p = c.getCaretPosition();
        if (p < 0) return 0;
        if (p > len) return len;
        return p;
    }
}