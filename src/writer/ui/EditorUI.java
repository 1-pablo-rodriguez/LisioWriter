package writer.ui;

import javax.swing.*;
import java.awt.*;

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
        JTextArea editor = frame.getEditor();
        JScrollPane scroll = new JScrollPane(editor);

        // --- Mise en page
        frame.getContentPane().add(scroll, BorderLayout.CENTER);

        // --- Configuration de l’éditeur (marges, polices, etc.)
        frame.setupEditorPane();

        // --- Raccourcis clavier
        frame.configureKeyboardShortcuts();

        // --- Barre de menus
        JMenuBar bar = writer.ui.menu.MenuBarFactory.create(frame);
        frame.setJMenuBar(bar);

        // --- Validation finale de la fenêtre
        frame.validate();
    }
}
