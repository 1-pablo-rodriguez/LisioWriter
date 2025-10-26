package styles;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import writer.ui.EditorApi;

/**
 * Style : Titre 5
 *
 * Applique la balise "#5. " au début de la ligne courante,
 * en corrigeant les niveaux ou symboles précédents si nécessaire.
 */
public class titre5 {

    private final EditorApi ctx;

    public titre5(EditorApi ctx) {
        this.ctx = ctx;
    }

    public void appliquer() {
        try {
            JTextArea editor = ctx.getEditor();

            // Obtenez la position du curseur
            int caretPosition = editor.getCaretPosition();

            // Trouvez la ligne actuelle
            int line = editor.getLineOfOffset(caretPosition);

            // Obtenez les offsets de début et de fin de la ligne
            int lineStart = editor.getLineStartOffset(line);
            int lineEnd = editor.getLineEndOffset(line);

            // Extraire le texte de la ligne
            String lineText = editor.getText(lineStart, lineEnd - lineStart);

            // --- Cas 1 : Titre supérieur (#6 à #9)
            if (lineText.trim().matches("^#[6-9]\\..*")) {
                String newText = lineText.replaceFirst("^#[6-9]\\.\\s*", "#5. ");
                editor.replaceRange(newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 2 : Paragraphe (#P.)
            if (lineText.trim().matches("^#P\\..*")) {
                String newText = lineText.replaceFirst("^#P\\.\\s*", "#5. ");
                editor.replaceRange(newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 3 : Sous-partie (#S.)
            if (lineText.trim().matches("^#S\\..*")) {
                String newText = lineText.replaceFirst("^#S\\.\\s*", "#5. ");
                editor.replaceRange(newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 4 : Liste (-.)
            if (lineText.trim().matches("^-\\..*")) {
                String newText = lineText.replaceFirst("^-\\.\\s*", "#5. ");
                editor.replaceRange(newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 5 : déjà un Titre 5
            if (lineText.trim().matches("^#5\\..*")) {
                sound();
                return;
            }

            // --- Cas 6 : ancien titre (1 à 4)
            if (lineText.trim().matches("^#[1-4]\\..*")) {
                String newText = lineText.replaceFirst("^#[1-4]\\.\\s*", "#5. ");
                editor.replaceRange(newText, lineStart, lineEnd);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

            // --- Cas 7 : ligne sans balise
            if (lineText.trim().matches("^[^#].*")) {
                editor.insert("#5. ", lineStart);
                editor.setCaretPosition(caretPosition);
                sound();
                return;
            }

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void sound() {
        // 🔊 Message temporaire (remplacera announceCaretLine plus tard)
        ctx.showInfo("Titre 5", "Paragraphe en Titre 5");
    }
}
