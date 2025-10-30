package writer.ui.editor;

import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;

import writer.ui.EditorFrame;
import writer.bookmark.BookmarkManager;
import dia.InfoDialog;

/**
 * Action associée à Ctrl+F2 : ajoute ou supprime un marque-page
 * et affiche une boîte d'information.
 */
@SuppressWarnings("serial")
public class BookmarkToggleAction extends AbstractAction {

    private final EditorFrame frame;

    public BookmarkToggleAction(EditorFrame frame) {
        super("BookmarkToggle");
        this.frame = frame;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        BookmarkManager bookmarks = frame.getBookmarks();
        if (bookmarks == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        boolean added = bookmarks.toggleHere();
        frame.setModified(true);

        Window owner = SwingUtilities.getWindowAncestor(frame.getEditor());
        String message;
        String title = "Marque-page";

        if (added) {
            // Ouvre une fenêtre pour éditer la note du nouveau marque-page
            bookmarks.editNoteForNearest(owner);
            message = "Marque-page ajouté.";
        } else {
            message = "Marque-page supprimé.";
        }

        InfoDialog.show(owner, title, message);
        frame.setModified(true);
    }
}
