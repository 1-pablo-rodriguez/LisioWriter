package writer.ui;

import java.awt.BorderLayout;

import javax.swing.JMenuBar;
import javax.swing.JScrollPane;

/**
 * Monte l’interface principale de l’éditeur (zone de texte + scroll)
 * dans une JFrame donnée.
 */
public final class EditorUI {

    private EditorUI() {
        // classe utilitaire, pas d’instanciation
    }

    /**
     * Installe l’éditeur (zone de texte + raccourcis + menu) dans la fenêtre fournie.
     * @param frame Fenêtre principale (doit être un EditorFrame)
     */
    public static void installInto(EditorFrame frame) {
        // --- Crée l’éditeur et le ScrollPane
    	writer.ui.NormalizingTextPane editor = frame.getEditor();
        JScrollPane scroll = new JScrollPane(editor);

        // --- Mise en page
        frame.getContentPane().add(scroll, BorderLayout.CENTER);

        // --- Configuration de l’éditeur (marges, polices, etc.)
        frame.setupEditorPane();

        // --- Raccourcis clavier (via la nouvelle classe KeyboardShortcutManager)
        new writer.ui.editor.KeyboardShortcutManager(frame, frame.getEditor()).installShortcuts();

        // --- Barre de menus
        JMenuBar bar = writer.ui.menu.MenuBarFactory.create(frame);
        frame.setJMenuBar(bar);

        // --- Validation finale de la fenêtre
        frame.validate();
    }
}
