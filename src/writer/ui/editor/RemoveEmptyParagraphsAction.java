package writer.ui.editor;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;

import writer.ui.EditorFrame;
import writer.ui.NormalizingTextPane;
import writer.util.TextCleaningUtils;

@SuppressWarnings("serial")
public final class RemoveEmptyParagraphsAction extends AbstractAction implements Action {

    private final NormalizingTextPane editor;
    private final EditorFrame parent; // pour la boîte d’info

    public RemoveEmptyParagraphsAction(EditorFrame parent, NormalizingTextPane editor) {
        super("Supprimer les paragraphes vides");
        this.parent = parent;
        this.editor = editor;

        // Texte accessible pour lecteurs d’écran (optionnel si tu gères ça ailleurs)
        putValue(SHORT_DESCRIPTION, "Supprime tous les paragraphes vides du document.");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (editor == null) return;

        String text = editor.getText();
        TextCleaningUtils.Result res = TextCleaningUtils.removeEmptyParagraphsAndCount(text);

        if (res.removedCount == 0) {
            // Petite annonce : rien à faire
            dia.InfoDialog.show(parent.getWindow(),
                    "Nettoyage des paragraphes vides",
                    "Aucun paragraphe vide trouvé.",parent.getAffichage());
            return;
        }

        editor.setText(res.cleanedText);

        // Annonce claire pour NVDA / JAWS / etc.
        String msg = res.removedCount == 1
                ? "1 paragraphe vide supprimé."
                : res.removedCount + " paragraphes vides supprimés.";

        dia.InfoDialog.show(parent.getWindow(),
                "Nettoyage des paragraphes vides",
                msg, parent.getAffichage());
        parent.getEditor().setCaretPosition(1);
    }
}