package writer.ui.editor;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;

import writer.ui.NormalizingTextPane;
import writer.ui.EditorFrame;
import writer.ui.text.CorrectionSynthase;
import dia.InfoDialog; // ou le chemin réel de ta boîte d’info
/**
 * Class action permettant de corriger la synthaxe des codes LisioWriter
 * Accissible depuis le menu Edition
 * @author pabr6
 *
 */
@SuppressWarnings("serial")
public final class ApplyCorrectionSynthaseAction extends AbstractAction implements Action {

    private final NormalizingTextPane editor;
    private final EditorFrame parent;

    public ApplyCorrectionSynthaseAction(EditorFrame parent, NormalizingTextPane editor) {
        super("Corriger la synthèse (codes LisioWriter)");
        this.parent = parent;
        this.editor = editor;

        // Texte que le lecteur d’écran peut annoncer
        putValue(SHORT_DESCRIPTION,
                "Corrige les erreurs classiques de saisie du code LisioWriter.");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (editor == null) return;

        String original = editor.getText();
        if (original == null || original.isEmpty()) {
            InfoDialog.show(parent.getWindow(),
                    "Correction de synthèse",
                    "Le document est vide, aucune correction appliquée.",parent.getAffichage());
            return;
        }

        // On garde la position du caret pour ne pas désorienter l’utilisateur
        int caret = editor.getCaretPosition();

        String corrected = CorrectionSynthase.corrigerSynthase(original);

        if (corrected.equals(original)) {
            InfoDialog.show(parent.getWindow(),
                    "Correction de synthèse",
                    "Aucune correction nécessaire.",parent.getAffichage());
            return;
        }

        editor.setText(corrected);

        // On restaure le caret le plus près possible
        if (caret <= corrected.length()) {
            editor.setCaretPosition(caret);
        } else {
            editor.setCaretPosition(corrected.length());
        }

        InfoDialog.show(parent.getWindow(),
                "Correction de synthèse",
                "Les corrections LisioWriter ont été appliquées.",parent.getAffichage());
    }
}
