package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;

import writer.ui.EditorFrame;
import writer.ui.editor.FastHighlighter;
import writer.ui.editor.LinkTokenIndexer;

@SuppressWarnings("serial")
public class convertAtLink extends AbstractAction {
    private final writer.ui.NormalizingTextPane editorPane;
    private final EditorFrame frame;

    // Constructeur
    public convertAtLink(writer.ui.NormalizingTextPane editorPane, EditorFrame frame) {
        super("removeLinks");
        this.editorPane = editorPane;
        this.frame= frame;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            // Sauvegarde le caret pour ne pas surprendre l’utilisateur
            int caret = editorPane.getCaretPosition();

            // Conversion (pose aussi la map numero→URL dans le Document)
            int n = LinkTokenIndexer.convertAtLinksToTokens(editorPane);

            // Restaure le caret proprement
            int max = editorPane.getDocument().getLength();
            editorPane.setCaretPosition(Math.min(Math.max(0, caret), max));

            FastHighlighter.rehighlightAll(editorPane);
           
            // Petit feedback
            if (n > 0) {
            	java.awt.Window owner = SwingUtilities.getWindowAncestor(editorPane);
            	dia.InfoDialog.show(owner, "Conversion des liens", n + " lien(s) converti(s).");
            } else {
                java.awt.Toolkit.getDefaultToolkit().beep();
            }

        } catch (Exception ex) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }
}