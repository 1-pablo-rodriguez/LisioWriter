package act;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import writer.ui.EditorApi;

/**
 * Action : Insertion d'un saut de page
 */
public class SautPage extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private final EditorApi ctx;

    //Constructeur
    public SautPage(EditorApi ctx) {
        super("Saut de page manuel");
        this.ctx = ctx;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (ctx == null) {
            System.err.println("Erreur: contexte EditorApi nul dans SautPage");
            return;
        }
        new page.sautPage(ctx).appliquer();
    }
}