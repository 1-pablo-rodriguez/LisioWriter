package writer.editor; // ou writer.text selon ton organisation

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;

/**
 * Filtre de document : gère la continuation automatique des listes.
 * - Ajoute automatiquement "n. " ou "-. " après un retour à la ligne
 * - Renumérote les listes numérotées après insertion ou suppression
 */
public final class AutoListContinuationFilter extends DocumentFilter {

	private final JTextComponent area;
    private boolean renumbering = false; // garde-fou contre les réentrées

    public AutoListContinuationFilter(JTextComponent area) {
        this.area = area;
    }

    @Override
    public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
            throws BadLocationException {
        String augmented = maybeAugment(text, offset, fb);
        super.insertString(fb, offset, augmented, attrs);

        // Si on vient d'insérer un "\n" + préfixe "n. ", tenter la renumérotation
        if (!renumbering && augmented != null && augmented.indexOf('\n') >= 0) {
            tryRenumberTailAfterInsertion(fb);
        }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        String augmented = maybeAugment(text, offset, fb);
        super.replace(fb, offset, length, augmented, attrs);

        if (!renumbering && augmented != null && augmented.indexOf('\n') >= 0) {
            tryRenumberTailAfterInsertion(fb);
        }
    }

    @Override
    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
        // Supprimer d'abord
        super.remove(fb, offset, length);

        // Puis renuméroter si on a supprimé dans une liste numérotée
        if (!renumbering) {
            tryRenumberTailAfterDeletion(fb, offset);
        }
    }

    // --- Méthodes internes (inchangées de ton code d’origine) ---
    private String maybeAugment(String text, int offset, FilterBypass fb) throws BadLocationException {
        if (text == null) return null;
        int nl = text.indexOf('\n');
        if (nl < 0) return text; // on ne s’active qu’au retour chariot

        String prefix = getContinuationPrefix(fb.getDocument(), offset);
        if (prefix == null) return text;

        // Insère le préfixe juste après le premier '\n'
        StringBuilder sb = new StringBuilder(text.length() + prefix.length());
        sb.append(text, 0, nl + 1).append(prefix);
        if (nl + 1 < text.length()) sb.append(text, nl + 1, text.length());
        return sb.toString();
    }

    
    /**
     * Après insertion du "\n" + préfixe, si la ligne suivante est déjà numérotée
     * avec la même indentation, renuméroter tout le bloc en aval (+1, +1, ...).
     */
    private void tryRenumberTailAfterInsertion(FilterBypass fb) {
        try {
            Document doc = fb.getDocument();

            // Ligne courante = la nouvelle ligne insérée (où est le caret)
            int caret = area.getCaretPosition();
            int curStart = Utilities.getRowStart(area, caret);
            int curEnd   = Utilities.getRowEnd(area, caret);
            if (curStart < 0 || curEnd < curStart) return;

            String curLine = doc.getText(curStart, curEnd - curStart);

            // Indentation + tête de ligne
            int i = 0;
            while (i < curLine.length() && Character.isWhitespace(curLine.charAt(i))) i++;
            String indent = curLine.substring(0, i);
            String head   = curLine.substring(i);

            // On ne renumérote que si la nouvelle ligne est bien "n. " (pas "-. ")
            int j = 0;
            while (j < head.length() && Character.isDigit(head.charAt(j))) j++;
            if (j == 0 || j + 2 > head.length()
                    || head.charAt(j) != '.'
                    || head.charAt(j + 1) != ' ') {
                return;
            }

            int currentNumber = Integer.parseInt(head.substring(0, j));
            int nextWanted = currentNumber + 1;

            // Position de la ligne suivante
            int nextStart = curEnd + 1;
            if (nextStart > doc.getLength()) return;

            renumbering = true;

            int p = nextStart;
            while (p <= doc.getLength()) {
                int s = Utilities.getRowStart(area, p);
                if (s < 0) break;
                int e = Utilities.getRowEnd(area, s);
                if (e < s) break;

                String line = doc.getText(s, e - s);

                // Même indentation ?
                int a = 0;
                while (a < line.length() && Character.isWhitespace(line.charAt(a))) a++;
                String indent2 = line.substring(0, a);
                if (!indent2.equals(indent)) break; // autre bloc

                // Motif "^\s*\d+\. "
                int b = a;
                while (b < line.length() && Character.isDigit(line.charAt(b))) b++;
                if (b == a || b + 2 > line.length()
                        || line.charAt(b) != '.'
                        || line.charAt(b + 1) != ' ') {
                    break; // fin de la séquence numérotée
                }

                String tail = line.substring(b + 2); // après "n. "
                String newHead = indent + nextWanted + ". ";
                String newLine = newHead + tail;

                // Remplacer la ligne par sa version renumérotée
                fb.replace(s, e - s, newLine, null);

                nextWanted++; // +1 pour la suivante

                // Avancer à la prochaine ligne (recalcule car longueur modifiée)
                int newEnd = Utilities.getRowEnd(area, s);
                if (newEnd < 0) break;
                p = newEnd + 1;
            }
        } catch (BadLocationException ex) {
            // On ignore silencieusement : pas de crash en saisie
        } finally {
            renumbering = false;
        }
    }
    
    
    private String getContinuationPrefix(Document doc, int offset) throws BadLocationException {
        Element root = doc.getDefaultRootElement();
        int line = root.getElementIndex(offset);
        Element lineElem = root.getElement(line);

        int start = lineElem.getStartOffset();
        int end = lineElem.getEndOffset();
        String text = doc.getText(start, end - start).replaceAll("\\R$", "");

        // exemple : " 1. texte" ou " -. texte"
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        int j = i;
        while (j < text.length() && Character.isDigit(text.charAt(j))) j++;

        // cas "n. "
        if (j > i && j + 1 < text.length() && text.charAt(j) == '.' && text.charAt(j + 1) == ' ') {
            int current = Integer.parseInt(text.substring(i, j));
            return text.substring(0, i) + (current + 1) + ". ";
        }

        // cas "-. "
        if (i + 2 <= text.length() && text.startsWith("-. ", i)) {
            return text.substring(0, i) + "-. ";
        }

        return null;
    }

    /**
     * Après suppression (ligne entière, retour chariot, ou bloc), si la ligne
     * désormais suivante est une "n. " avec la même indentation que l’item
     * précédent, on renumérote tout le bloc (décrément implicite : ancre+1, ancre+2…).
     * Si aucun item précédent n’existe au même niveau, on redémarre à 1.
     */
    private void tryRenumberTailAfterDeletion(FilterBypass fb, int offset) {
        try {
            Document doc = fb.getDocument();

            // Première ligne potentiellement à renuméroter = celle qui commence à 'offset'
            int nextStart = Utilities.getRowStart(area, Math.min(offset, doc.getLength()));
            if (nextStart < 0) return;

            int nextEnd = Utilities.getRowEnd(area, nextStart);
            if (nextEnd < nextStart) return;

            String nextLine = doc.getText(nextStart, nextEnd - nextStart);

            // Indentation + motif "n. " sur la ligne suivante ?
            int i = 0;
            while (i < nextLine.length() && Character.isWhitespace(nextLine.charAt(i))) i++;
            String indent = nextLine.substring(0, i);
            String head   = nextLine.substring(i);

            int j = 0;
            while (j < head.length() && Character.isDigit(head.charAt(j))) j++;
            if (j == 0 || j + 2 > head.length()
                    || head.charAt(j) != '.'
                    || head.charAt(j + 1) != ' ') {
                return; // pas une ligne numérotée : rien à faire
            }

            // Chercher l'item précédent pour connaître le "prochain attendu"
            int wanted = 1; // par défaut, on redémarre à 1
            if (nextStart > 0) {
                int ancStart = Utilities.getRowStart(area, nextStart - 1);
                int ancEnd   = Utilities.getRowEnd(area, ancStart);
                if (ancStart >= 0 && ancEnd >= ancStart) {
                    String ancLine = doc.getText(ancStart, ancEnd - ancStart);
                    int a = 0;
                    while (a < ancLine.length() && Character.isWhitespace(ancLine.charAt(a))) a++;
                    String ancIndent = ancLine.substring(0, a);
                    String ancHead   = ancLine.substring(a);

                    int b = 0;
                    while (b < ancHead.length() && Character.isDigit(ancHead.charAt(b))) b++;
                    if (b > 0 && b + 2 <= ancHead.length()
                            && ancHead.charAt(b) == '.'
                            && ancHead.charAt(b + 1) == ' '
                            && ancIndent.equals(indent)) {
                        try {
                            int ancNum = Integer.parseInt(ancHead.substring(0, b));
                            wanted = ancNum + 1;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // Renuméroter à partir de nextStart : wanted, wanted+1, ...
            renumbering = true;

            int p = nextStart;
            int nextWanted = wanted;
            while (p <= doc.getLength()) {
                int s = Utilities.getRowStart(area, p);
                if (s < 0) break;
                int e = Utilities.getRowEnd(area, s);
                if (e < s) break;

                String line = doc.getText(s, e - s);

                int a = 0;
                while (a < line.length() && Character.isWhitespace(line.charAt(a))) a++;
                String indent2 = line.substring(0, a);
                if (!indent2.equals(indent)) break; // on sort du bloc

                int b = a;
                while (b < line.length() && Character.isDigit(line.charAt(b))) b++;
                if (b == a || b + 2 > line.length()
                        || line.charAt(b) != '.'
                        || line.charAt(b + 1) != ' ') {
                    break; // plus une ligne numérotée
                }

                String tail = line.substring(b + 2);
                String newLine = indent + nextWanted + ". " + tail;
                fb.replace(s, e - s, newLine, null);

                nextWanted++;

                int newEnd = Utilities.getRowEnd(area, s);
                if (newEnd < 0) break;
                p = newEnd + 1;
            }
        } catch (BadLocationException ex) {
            // pas de crash
        } finally {
            renumbering = false;
        }
    }
}
