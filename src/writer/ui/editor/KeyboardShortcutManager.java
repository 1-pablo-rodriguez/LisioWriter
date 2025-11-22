package writer.ui.editor;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

import act.Gras;
import act.Italique;
import act.SautPage;
import act.T1;
import act.T2;
import act.T3;
import act.T4;
import act.T5;
import act.ToggleEditAction;
import act.VersBas;
import act.VersDroite;
import act.VersGauche;
import act.VersHaut;
import act.actCheckDoc;
import act.actCheckWindow;
import act.citation;
import act.informations;
import act.openSearchDialog;
import act.ouvrirNavigateurT1;
import act.textBody;
import dia.HtmlBrowserDialog;
import dia.WikipediaSearchDialog;
import writer.editor.InsertUnorderedBulletAction;
import writer.ui.EditorFrame;

/**
 * Gestion centralisée des raccourcis clavier pour l’éditeur LisioWriter.
 * Allège EditorFrame et regroupe les KeyBindings au même endroit.
 */
public class KeyboardShortcutManager {

    private final EditorFrame frame;
    private final writer.ui.NormalizingTextPane editorPane;

    public KeyboardShortcutManager(EditorFrame frame, writer.ui.NormalizingTextPane editorPane) {
        this.frame = frame;
        this.editorPane = editorPane;
    }
    

    /** Configure tous les raccourcis clavier de LisioWriter. */
    @SuppressWarnings("serial")
	public void installShortcuts() {
        addKeyBinding(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK, "paragraphe", new textBody(frame));
        addKeyBinding(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK, "T1", new T1(frame));
        addKeyBinding(KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK, "T2", new T2(frame));
        addKeyBinding(KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK, "T3", new T3(frame));
        addKeyBinding(KeyEvent.VK_4, InputEvent.CTRL_DOWN_MASK, "T4", new T4(frame));
        addKeyBinding(KeyEvent.VK_5, InputEvent.CTRL_DOWN_MASK, "T5", new T5(frame));

        addKeyBinding(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK, "gras", new Gras(frame));
        addKeyBinding(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK, "italique", new Italique(frame));

        addKeyBinding(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK, "versHaut", new VersHaut(editorPane));
        addKeyBinding(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK, "versBas", new VersBas(editorPane));
        addKeyBinding(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK, "versDroite", new VersDroite(editorPane));
        addKeyBinding(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK, "versGauche", new VersGauche(editorPane));

        addKeyBinding(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK, "sautPage", new SautPage(frame));
//      addKeyBinding(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, "citation", new citation(frame));
        addKeyBinding(KeyEvent.VK_F6, 0, "navigateurT1", new ouvrirNavigateurT1(frame));
        addKeyBinding(KeyEvent.VK_F1, 0, "Informations", new informations(frame));
        addKeyBinding(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK, "rechercher", new openSearchDialog(editorPane));

        // === PUCES ===
        Action puceAction = new InsertUnorderedBulletAction(editorPane);
        addKeyBinding(KeyEvent.VK_DEAD_GRAVE, InputEvent.CTRL_DOWN_MASK, "puceTexte.fr", puceAction);

//	     // 2) En plus, bind sur le caractère 'è' + Ctrl (certaines JRE/IMEs envoient le caractère directement)
//	     editorPane.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke('è', InputEvent.CTRL_DOWN_MASK), "puceTexte.fr");
//	     editorPane.getActionMap().put("puceTexte.fr", puceAction);
        
	     // === AUTRES ===
        addKeyBinding(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK, "toggleEdit", new ToggleEditAction(editorPane));
        addKeyBinding(KeyEvent.VK_F2, 0, "announceHeadingsAround", frame.actAnnouncePosition());
        addKeyBinding(KeyEvent.VK_F3, 0, "gotoNextHeading", frame.actGotoNextHeading());
        addKeyBinding(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK, "gotoPrevHeading", frame.actGotoPrevHeading());

        // === ZOOM ===
        addKeyBinding(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK, "zoomIn",
        	    new AbstractAction() { public void actionPerformed(ActionEvent e) { frame.zoomIn(); } });
        addKeyBinding(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK, "zoomInNumPad",
        	    new AbstractAction() { public void actionPerformed(ActionEvent e) { frame.zoomIn(); } });

    	addKeyBinding(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK, "zoomOut",
        	    new AbstractAction() { public void actionPerformed(ActionEvent e) { frame.zoomOut(); } });

    	addKeyBinding(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK, "zoomOutNumPad",
        	    new AbstractAction() { public void actionPerformed(ActionEvent e) { frame.zoomOut(); } });

    	addKeyBinding(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK, "zoomReset",
        	    new AbstractAction() { public void actionPerformed(ActionEvent e) { frame.zoomReset(); } });


        // === Vérification orthographique ===
        addKeyBinding(KeyEvent.VK_F7, InputEvent.CTRL_DOWN_MASK, "actCheckDoc", new actCheckDoc(frame));
        addKeyBinding(KeyEvent.VK_F7, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, "actCheckWindow", new actCheckWindow(frame));

        // === Marque-pages ===
        addKeyBinding(KeyEvent.VK_F2, InputEvent.CTRL_DOWN_MASK, "bmToggle", new BookmarkToggleAction(frame));
        
        addKeyBinding(KeyEvent.VK_F4, 0, "bmNext",
        	    new AbstractAction() { public void actionPerformed(ActionEvent e) { frame.getBookmarks().goNext(); } });

        addKeyBinding(KeyEvent.VK_F4, InputEvent.SHIFT_DOWN_MASK, "bmPrev",
        	    new AbstractAction() { public void actionPerformed(ActionEvent e) { frame.getBookmarks().goPrev(); } });

        addKeyBinding(KeyEvent.VK_F5, 0,"bmEditNote",
    	    new AbstractAction() {
    	        public void actionPerformed(ActionEvent e) {
    	            var bm = frame.getBookmarks();
    	            if (bm != null)
    	                bm.editNoteForNearest(SwingUtilities.getWindowAncestor(editorPane));
    	        }
    	    });

        addKeyBinding(KeyEvent.VK_F8, 0, "openWikipediaSearch",
    	    new AbstractAction() {
    	        public void actionPerformed(ActionEvent e) {
    	            SwingUtilities.invokeLater(() ->
    	                WikipediaSearchDialog.open(frame, url ->
    	                    new HtmlBrowserDialog(frame, frame.getEditor(), url)
    	                )
    	            );
    	        }
    	    });
    }

    /** Utilitaire pour associer une touche à une action (avec garde menu fermé). */
    @SuppressWarnings("serial")
	private void addKeyBinding(int keyCode, int modifier, String name, Action action) {
        KeyStroke ks = KeyStroke.getKeyStroke(keyCode, modifier);
        JRootPane rp = frame.getRootPane();

        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        rp.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Vérifie qu’aucun menu n’est ouvert avant d’exécuter
                if (!isMenuOpen()) action.actionPerformed(e);
            }
        });
    }

    private boolean isMenuOpen() {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        return path != null && path.length > 0;
    }

}