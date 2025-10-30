package writer;

import javax.swing.text.JTextComponent;

import writer.model.Affiche;
import writer.ui.EditorFrame;

public class informationsAffichage {
	EditorFrame parent;
    public informationsAffichage(EditorFrame parent) {
    	this.parent = parent;
        StringBuilder message = new StringBuilder(128);

        try {
            Affiche vue = parent.getAffichage();

            if (vue == Affiche.TEXTE) {
            	JTextComponent editor = parent.getEditor();
            	
            	javax.swing.text.Document doc = editor.getDocument();
                javax.swing.text.Element root = doc.getDefaultRootElement();
                TextStats all = computeStats(doc, root);
          	
                String fileName = (commandes.nameFile != null && !commandes.nameFile.isBlank())
                                  ? commandes.nameFile + ".bwr" : "Sans nom.bwr";
                String folder   = (commandes.currentDirectory != null)
                                  ? commandes.currentDirectory.getName() : "Dossier inconnu";

                boolean editable = editor != null && editor.isEditable();
                boolean liveSpell = commandes.verificationOrthoGr; // ta variable existante

                message.append("INFO. & STAT. ↓");
                message.append("\n• Fichier : ").append(fileName).append(" ↓");
                message.append("\n•Dossier : ").append(folder).append(" ↓");
                message.append(editable ? "\n• Mode éditable. ↓" : "\n• Mode en lecture seule. ↓");
                message.append(liveSpell ? "\n• Vérif. frappe activée. ↓"
                                         : "\n• Vérif. frappe désactivée. ↓");
                message.append("\nSTAT. document : ↓");
                message.append("\n• Mots : ").append(all.words).append(" ↓");
                message.append("\n• Phrases : ").append(all.sentences).append(" ↓");
                message.append("\n• Paragraphes : ").append(all.paragraphs).append(" ↓");
                message.append("\n• Lignes : ").append(all.lines).append(" ↓");
                message.append("\n• Caract. (avec espaces) : ").append(all.charsAll).append(" ↓");
                message.append("\n• Caract. (sans espaces) : ").append(all.charsNoSpaces).append(" ↓");
                message.append("\nDOC. & AIDES");
                message.append("\n• Documentation : ALT+A ↓");
                message.append("\n• Manuel b.book : ALT+C ↓"); 
                message.append("\n• Votre fichier : ALT+B ↓");

            } else if (vue == Affiche.DOCUMENTATION) {
            	message.append("F1-INFO. Documentation de blindWriter. ↓");
            	message.append("\n Touch. F6 pour naviguer•↓");
            	message.append("\nDoc. & Aides");
                message.append("\n• Documentation : ALT+A ↓");
                message.append("\n• Manuel b.book : ALT+C ↓"); 
                message.append("\n• Votre fichier : ALT+B ↓");
            } else if (vue == Affiche.MANUEL) {
            	message.append("F1-Info. Manuel du b.book. ↓");
            	message.append("\nDoc. & Aides");
                message.append("\n• Documentation : ALT+A ↓");
                message.append("\n• Manuel b.book : ALT+C ↓"); 
                message.append("\n• Votre fichier : ALT+B ↓");
            }

        } catch (Exception ignore) {
            // on évite toute exception dans ce chemin d’annonce
            message = new StringBuilder("Informations indisponibles pour le moment.");
        }
        
        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(parent);
        dia.InfoDialog.show(owner, "Information", message.toString());

    }
    
 // --- Utilitaires statistiques ---
    private static final java.util.Locale LOCALE_FR = java.util.Locale.FRENCH;

    private static final class TextStats {
        final int charsAll;         // caractères (espaces inclus)
        final int charsNoSpaces;    // caractères (sans espaces)
        final int words;            // nb de mots (itérateur de mots)
        final int sentences;        // nb de phrases
        final int lines;            // nb de lignes (éléments du Document)
        final int paragraphs;       // nb de paragraphes "blocs" (séparés par ≥1 ligne vide)

        TextStats(int cAll, int cNoSp, int w, int s, int l, int p) {
            this.charsAll = cAll; this.charsNoSpaces = cNoSp;
            this.words = w; this.sentences = s; this.lines = l; this.paragraphs = p;
        }
    }

    private static TextStats computeStats(javax.swing.text.Document doc, javax.swing.text.Element root) throws javax.swing.text.BadLocationException {
        final String text = doc.getText(0, doc.getLength());
        final int charsAll = text.length();
        final int charsNoSpaces = text.replaceAll("\\s+", "").length();

        // Compte des mots : BreakIterator FR + filtre lettres/chiffres
        int words = 0;
        java.text.BreakIterator wb = java.text.BreakIterator.getWordInstance(LOCALE_FR);
        wb.setText(text);
        int s = wb.first();
        for (int e = wb.next(); e != java.text.BreakIterator.DONE; s = e, e = wb.next()) {
            if (hasAlphaNum(text, s, e)) words++;
        }

        // Compte des phrases : BreakIterator FR
        int sentences = 0;
        java.text.BreakIterator sb = java.text.BreakIterator.getSentenceInstance(LOCALE_FR);
        sb.setText(text);
        s = sb.first();
        for (int e = sb.next(); e != java.text.BreakIterator.DONE; s = e, e = sb.next()) {
            // ignore “phrases” vides (ex : multiple sauts)
            if (hasAlphaNum(text, s, e)) sentences++;
        }

        // Lignes = éléments racine (PlainDocument) / ou repli via split
        int lines = (root != null) ? root.getElementCount() : text.split("\\R", -1).length;

        // Paragraphes "blocs" = segments séparés par ≥1 ligne vide
        int paragraphs = 0;
        for (String block : text.split("\\R{2,}")) { // 1+ ligne vide => séparateur de paragraphe
            if (!block.isBlank()) paragraphs++;
        }
        if (paragraphs == 0 && !text.isBlank()) paragraphs = 1;

        return new TextStats(charsAll, charsNoSpaces, words, sentences, lines, paragraphs);
    }

    private static boolean hasAlphaNum(String t, int start, int end) {
        for (int i = start; i < end && i < t.length(); i++) {
            if (Character.isLetterOrDigit(t.charAt(i))) return true;
        }
        return false;
    }

    

}
