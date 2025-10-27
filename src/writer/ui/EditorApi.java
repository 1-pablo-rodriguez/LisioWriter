// writer/ui/EditorApi.java
package writer.ui;

import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.undo.UndoManager;

import writer.bookmark.BookmarkManager;

public interface EditorApi {
    JFrame getWindow();           // parent pour dialogs
    JTextArea getEditor();        // l’éditeur
    JScrollPane getScrollPane();	// Le scroll de l'éditeur
    writer.spell.SpellCheckLT getSpell();  //Correcteur
    Boolean isModifier();

    
    UndoManager getUndoManager();	// Gestion des Annuler Retirer
    BookmarkManager getBookmarks();	// Marque-page
    
    javax.swing.Action getUndoAction();
    javax.swing.Action getRedoAction();
    
    // Façade "héritée" (pour ne plus toucher à blindWriter depuis les menus)
    void setModified(boolean modified);
    void setBookMarkMannager(BookmarkManager bookmark);
    void updateWindowTitle();
    void clearSpellHighlightsAndFocusEditor();
    void showInfo(String title, String message);
    void addItemChangeListener(JMenuItem item);
    void afficheDocumentation();
    void AfficheTexte();
    void AfficheManuel();
    void sauvegardeTemporaire();
    
    void zoomIn();
    void zoomOut();
    void zoomReset();
    
    
    Action actAnnouncePosition();
    
    Action actGotoNextHeading();
    
    Action actGotoPrevHeading();
    
    
}
