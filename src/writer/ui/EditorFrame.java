package writer.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import writer.commandes;

@SuppressWarnings("serial")
public class EditorFrame extends JFrame implements EditorApi {

    private final JTextArea editorPane = new JTextArea(); // <-- CHAMP RÉEL
    private JMenuItem dernierMenuUtilise = null;
    public boolean isModified = false;
	public writer.spell.SpellCheckLT spell;
	private final static javax.swing.undo.UndoManager undoManager = new javax.swing.undo.UndoManager();
	   
	private final static Action undoAction = new AbstractAction("Annuler") {
        @Override public void actionPerformed(ActionEvent e) {
            try {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                }
            } finally { updateUndoRedoState(); }
        }
    };
    
    private final static Action redoAction = new AbstractAction("Rétablir") {
        @Override public void actionPerformed(ActionEvent e) {
            try {
                if (undoManager.canRedo()) {
                    undoManager.redo();
                    //announceCaretLine(true, true, "Rétabli");
                }
            } finally { updateUndoRedoState(); }
        }
    };
    
    
    private static void updateUndoRedoState() {
        undoAction.setEnabled(undoManager.canUndo());
        redoAction.setEnabled(undoManager.canRedo());
    }
	
    public EditorFrame() {
        super("blindWriter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Mise en place simple et sûre (tu pourras remettre ton EditorUI.installInto(...) plus tard si besoin)
        JScrollPane scroll = new JScrollPane(editorPane);
        getContentPane().add(scroll, BorderLayout.CENTER);

        // Barre de menus (package writer.ui.menu)
        setJMenuBar(writer.ui.menu.MenuBarFactory.create(this));

        pack();
        setLocationRelativeTo(null);
    }

    @Override public JFrame getWindow() { return this; }
    
    @Override public JTextArea getEditor() { return this.editorPane; }
   
    @Override public void setModified(boolean modified) { 
    	this.isModified = modified;
	     this.updateWindowTitle(); 
    }
	
    @Override public void updateWindowTitle() { 
		 String base = "blindWriter";
	     String name = (commandes.nameFile != null && !commandes.nameFile.isBlank())
	         ? commandes.nameFile
	         : "Nouveau";
	     StringBuilder title = new StringBuilder(base).append(" — ").append(name);
	     if (commandes.currentDirectory != null) {
	         // n'affiche que le nom du dossier parent pour ne pas surcharger
	         java.io.File dir = new java.io.File(commandes.currentDirectory.toString());
	         String folder = dir.getName().isBlank() ? dir.getPath() : dir.getName();
	         title.append(" (").append(folder).append(")");
	     }
	     if (this.isModified) title.insert(0, "*"); // préfixe * quand non sauvegardé
	     this.setTitle(title.toString());
	}
	
	@Override public void clearSpellHighlightsAndFocusEditor() {
        if (this.spell != null) {
        	this.spell.clearHighlights();
        }
        this.editorPane.requestFocusInWindow();
    }
	
    @Override public void showInfo(String title, String message) {
        dia.InfoDialog.show(this, title, message);
    }
    
    @Override public void addItemChangeListener(JMenuItem menuItem) {
    	 menuItem.addChangeListener(e -> {
             // Vérifier si le menu est actuellement armé (surligné)
             if (menuItem.isArmed()) {
                 // Ne s'exécute que si le menu actuel est différent du dernier utilisé
                 if (menuItem != dernierMenuUtilise) {
                         // Mettre à jour le dernier menu utilisé
                         dernierMenuUtilise = menuItem;
                     }
                 }
         });
    }

	
    @SuppressWarnings({ "unused" })
    private final class ToggleEditAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            boolean editable = EditorFrame.this.editorPane.isEditable();
            EditorFrame.this.editorPane.setEditable(!editable);
            String msg = editable ? "Édition bloquée." : "Édition activée.";
            java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(EditorFrame.this.editorPane);
            dia.InfoDialog.show(owner, "Mode édition", msg);
        }
    }

	
		
}
    


