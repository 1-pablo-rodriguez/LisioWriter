package writer.ui;

import javax.swing.*;
import java.awt.*;
import writer.blindWriter;

/** Monte l’UI de l’éditeur dans une JFrame existante (étape A, zéro risque). */
public final class EditorUI {
    private EditorUI() {}

    public static void installInto(JFrame host) {
        // -- Crée l’éditeur + scroll et les place au centre
        blindWriter.editorPane = new JTextArea();

        // Si tu as une config d’éditeur dans blindWriter, applique-la ici
        if (host instanceof blindWriter bw) {
            // ta méthode existante : police, wrap, marges, etc.
            bw.setupEditorPane();

            // raccourcis et menus existants (inchangés)
            bw.configureKeyboardShortcuts();
            bw.setJMenuBar(blindWriter.createMenuBar());
            blindWriter.applyMenuFontTree(bw.getJMenuBar());
        }

        blindWriter.scrollPane = new JScrollPane(blindWriter.editorPane);
        host.getContentPane().add(blindWriter.scrollPane, BorderLayout.CENTER);

        host.validate();
    }
}
