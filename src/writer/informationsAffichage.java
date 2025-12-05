package writer;

import writer.model.Affiche;
import writer.ui.EditorFrame;
import writer.ui.NormalizingTextPane;
import writer.util.WordCounter;   // ← AJOUT

public class informationsAffichage {
    EditorFrame parent;

    public informationsAffichage(EditorFrame parent) {
        this.parent = parent;
        StringBuilder message = new StringBuilder(256);
        char c = ' ';

        try {
            Affiche vue = parent.getAffichage();

            if (vue == Affiche.TEXTE1 || vue == Affiche.TEXTE2) {
                NormalizingTextPane editor = (NormalizingTextPane) parent.getEditor();
                // on demande au NormalizingTextPane d'assurer l'état normalisé
                editor.normalizeDocumentContent();

                javax.swing.text.Document doc = editor.getDocument();
                javax.swing.text.Element root = (doc != null) ? doc.getDefaultRootElement() : null;
                TextStats all = computeStats(doc, root);

                String fileName = (commandes.nameFile != null && !commandes.nameFile.isBlank())
                                  ? commandes.nameFile + ".bwr" : "Sans nom.bwr";
                String folder   = (commandes.currentDirectory != null)
                                  ? commandes.currentDirectory.getName() : "Dossier inconnu";

                boolean editable = editor != null && editor.isEditable();
                
                message.append(c).append("Fichier : ").append(fileName).append(" ↓");
                message.append("\n").append(c).append("Dossier : ").append(folder).append(" ↓");
                message.append(editable ? "\n"+c+"Mode éditable. ↓" : "\n"+c+"Mode en lecture seule. ↓");
                message.append("\nSTATISTIQUES ↓");
                message.append("\n").append(c).append("Mots : ").append(all.words).append(" ↓");
                message.append("\n").append(c).append("Phrases : ").append(all.sentences).append(" ↓");
                message.append("\n").append(c).append("Paragraphes : ").append(all.lines).append(" ↓");
                message.append("\n").append(c).append("Caract. (avec espaces) : ").append(all.charsAll).append(" ↓");
                message.append("\n").append(c).append("Caract. (sans espaces) : ").append(all.charsNoSpaces).append(" ↓");
                message.append("\n").append(c).append("DOC. & AIDES");
                message.append("\n").append(c).append("Documentation : ALT+A ↓");
                message.append("\n").append(c).append("Fenêtre 1 : ALT+B ↓");
                message.append("\n").append(c).append("Fenêtre 2 : ALT+C");

            } else if (vue == Affiche.DOCUMENTATION) {
                message.append(c).append("Documentation de LisioWriter. ↓");
                message.append("\n").append(c).append("Touch. F6 pour naviguer•↓");
                message.append("\n").append(c).append("Doc. & Aides");
                message.append("\n").append(c).append("Documentation : ALT+A ↓");
                message.append("\n").append(c).append("Fenêtre 1 : ALT+B ↓");
                message.append("\n").append(c).append("Fenêtre 2 : ALT+C");
            }

        } catch (Exception ignore) {
            message = new StringBuilder("Informations indisponibles pour le moment.");
        }

        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(parent);
        dia.InfoDialog.show(owner, "Information", message.toString(), parent.getAffichage());
    }

    // --- Utilitaires statistiques ---
    private static final java.util.Locale LOCALE_FR = java.util.Locale.FRENCH;

    private static final class TextStats {
        final int charsAll;         // caractères (espaces inclus)
        final int charsNoSpaces;    // caractères (sans espaces)
        final int words;            // nb de mots
        final int sentences;        // nb de phrases
        final int lines;            // nb de lignes physiques

        TextStats(int cAll, int cNoSp, int w, int s, int l, int p) {
            this.charsAll = cAll;
            this.charsNoSpaces = cNoSp;
            this.words = w;
            this.sentences = s;
            this.lines = l;
            // p (paragraphes sémantiques) calculé mais pas stocké pour l’instant
        }
    }

    /**
     * computeStats :
     * - normalise quelques caractères invisibles,
     * - supprime uniquement le préfixe braille au début de chaque ligne,
     * - calcule statistiques (mots, phrases, lignes physiques, etc.).
     */
    private static TextStats computeStats(javax.swing.text.Document doc, javax.swing.text.Element root)
            throws javax.swing.text.BadLocationException {

        final String raw = (doc == null) ? "" : doc.getText(0, doc.getLength());
        if (raw == null || raw.isEmpty()) {
            return new TextStats(0, 0, 0, 0, 0, 0);
        }

        // 1) Normaliser EOL vers '\n' et retirer BOM/ZWSP/Marks invisibles
        String norm = raw.replace("\r\n", "\n").replace("\r", "\n")
                         .replace("\uFEFF", "")  // BOM
                         .replace("\u200B", "")  // ZWSP
                         .replace("\u200E", "")  // LRM
                         .replace("\u200F", ""); // RLM

        // 2) Supprimer le préfixe braille uniquement en début de ligne (ex: "   ¶ ")
        String cleaned = norm.replaceAll("(?m)^\\s*\\u00B6\\s*", "");

        // 3) Caractères
        int charsAll = cleaned.length();
        int charsNoSpaces = cleaned.replaceAll("\\s+", "").length();

        // 4) Mots : on utilise WordCounter pour être cohérent avec AnnouncePositionAction
        int words = WordCounter.countWords(cleaned);

        // 5) Phrases (BreakIterator FR)
        int sentences = 0;
        java.text.BreakIterator sb = java.text.BreakIterator.getSentenceInstance(LOCALE_FR);
        sb.setText(cleaned);
        int s = sb.first();
        for (int e = sb.next(); e != java.text.BreakIterator.DONE; s = e, e = sb.next()) {
            if (hasAlphaNum(cleaned, s, e)) sentences++;
        }

        // 6) Lignes physiques : split sur \n (toutes, y compris lignes vides)
        String[] linesArr = cleaned.split("\\R", -1);
        int lines = linesArr.length;

        // 7) Paragraphes sémantiques (blocs séparés par >=1 ligne vide)
        int paragraphs = 0;
        for (String block : cleaned.split("\\R{2,}")) {
            if (!block.isBlank()) paragraphs++;
        }
        if (paragraphs == 0 && !cleaned.isBlank()) paragraphs = 1;

        return new TextStats(charsAll, charsNoSpaces, words, sentences, lines, paragraphs);
    }

    private static boolean hasAlphaNum(String t, int start, int end) {
        for (int i = start; i < end && i < t.length(); i++) {
            if (Character.isLetterOrDigit(t.charAt(i))) return true;
        }
        return false;
    }
}
