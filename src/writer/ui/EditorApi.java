// writer/ui/EditorApi.java
package writer.ui;

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;

public interface EditorApi {
    JFrame getWindow();           // parent pour dialogs
    JTextArea getEditor();        // l’éditeur
    // ajoute ici ce que tes menus utilisent réellement :
    // - services (import/export, mise à jour, signets, orthographe…)
    // - préférences / thème / zoom
    // - chemins récents, etc.
    // Exemples :
    // PreferencesService prefs();
    // ExportService export();
    // ImportService importSvc();
    // BookmarkService bookmarks();
    // SpellService spell();
    
    // Façade "héritée" (pour ne plus toucher à blindWriter depuis les menus)
    void setModified(boolean modified);
    void updateWindowTitle();
    void clearSpellHighlightsAndFocusEditor();
    void showInfo(String title, String message);
    void addItemChangeListener(JMenuItem item);
   

    
    
}
