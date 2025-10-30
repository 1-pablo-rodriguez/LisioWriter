package writer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.undo.UndoManager;

import act.ToggleEditAction;
import dia.HtmlBrowserDialog;
import dia.WikipediaSearchDialog;
import writer.CaretStyler;
import writer.ParagraphHighlighter;
import writer.WordSelectOnShiftRight;
import writer.commandes;
import writer.bookmark.BookmarkManager;
import writer.editor.AutoListContinuationFilter;
import writer.editor.InsertUnorderedBulletAction;
import writer.model.Affiche;
import writer.spell.SpellCheckLT;
import writer.util.IconLoader;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.datatransfer.*;

@SuppressWarnings("serial")
public class EditorFrame extends JFrame implements EditorApi {

    // === CHAMPS PRINCIPAUX ===
    private final JTextPane editorPane = new JTextPane(); // Migration vers private JTextPane editor;
    private final UndoManager undoManager = new UndoManager();
    private final Action undoAction;
    private final Action redoAction;
    private JMenuItem dernierMenuUtilise = null;
    private boolean isModified = false;
    private SpellCheckLT spell;
    private BookmarkManager bookmarks;
    // --- Motif unique : "#<niveau>. <texte>" strictement en début de ligne ---
  	private static final Pattern HEADING_PATTERN = Pattern.compile("^#([1-6])\\.\\s+(.+?)\\s*$");
  	private Affiche affichage = Affiche.TEXTE;
  	public static int positionCurseurSauv = 0;
  	// --- Zoom éditeur ---
 	private static String EDITOR_FONT_FAMILY = "Arial";
 	@SuppressWarnings("unused")
	private static int    EDITOR_FONT_SIZE   = 42;
 	private static final int FONT_MIN = 14, FONT_MAX = 130, FONT_STEP = 2;
  	private float editorFontSize = 34f;
  	private final JScrollPane scrollPane;


    // === CONSTRUCTEUR ===
    public EditorFrame() {
        super("blindWriter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- CONFIGURATION DE L'ÉDITEUR ---
        scrollPane = new JScrollPane(editorPane);
        // Pour forcer le wrap vertical proprement dans le viewport :
        editorPane.setEditorKit(new WrapEditorKit());
        
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        // Attache le gestionnaire Undo/Redo
        editorPane.getDocument().addUndoableEditListener(new UndoableEditListener() {
            @Override
            public void undoableEditHappened(UndoableEditEvent e) {
                undoManager.addEdit(e.getEdit());
                updateUndoRedoState();
            }
        });
        
        // --- ICON APP ----
        setIconImage(IconLoader.load(Icons.APP).getImage());

        // --- CRÉATION DES ACTIONS ANNULER / RÉTABLIR ---
        undoAction = new AbstractAction("Annuler") {
            @Override public void actionPerformed(ActionEvent e) {
                if (undoManager.canUndo()) undoManager.undo();
                updateUndoRedoState();
            }
        };
        redoAction = new AbstractAction("Rétablir") {
            @Override public void actionPerformed(ActionEvent e) {
                if (undoManager.canRedo()) undoManager.redo();
                updateUndoRedoState();
            }
        };
        this.undoManager.setLimit(10000);
        
        // ---  Correcteur en temps réel désactivé ---
        try {
            this.spell = writer.spell.SpellCheckLT.attach(this.editorPane);
            this.spell.setRealtime(commandes.verificationOrthoGr);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        
        // --- ECHAP pour fermer les menus ou ignore ---
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeMenusOrStop");

		getRootPane().getActionMap().put("closeMenusOrStop", new AbstractAction() {
		    @Override
		    public void actionPerformed(ActionEvent e) {
		        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
		        if (path != null && path.length > 0) {
		            MenuSelectionManager.defaultManager().clearSelectedPath();
		        }
		    }
		});
        
        
        // --- ZOOM au CTRL+molette ---
        editorPane.addMouseWheelListener(e -> {
            if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                if (e.getWheelRotation() < 0) zoomIn(); else zoomOut();
                e.consume();
            }
        });

        // --- RACCOURCIS CLAVIER DIRECTS ---
        InputMap im = this.editorPane.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = this.editorPane.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "Undo");
        am.put("Undo", undoAction);

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK), "Redo");
        am.put("Redo", redoAction);
        
        // Key binding: TAB et Shift+TAB pour saisir à la place [Tab]
        im.put(KeyStroke.getKeyStroke("TAB"), "bw-insert-tab-tag");
        im.put(KeyStroke.getKeyStroke("shift TAB"), "bw-insert-tab-tag");
        am.put("bw-insert-tab-tag", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
            	editorPane.replaceSelection("[tab] ");
            }
        });
        
        // Récupère l’action Backspace par défaut (supprime le caractère précédent)
        Action defaultBackspace = this.editorPane.getActionMap().get(DefaultEditorKit.deletePrevCharAction);

		// Remappe BACK_SPACE vers notre action “intelligente”
        this.editorPane.getInputMap().put(KeyStroke.getKeyStroke("BACK_SPACE"), "bw-smart-backspace");
		
        this.editorPane.getActionMap().put("bw-smart-backspace", new AbstractAction() {
		    @Override public void actionPerformed(ActionEvent e) {
		        try {
		            int selStart = editorPane.getSelectionStart();
		            int selEnd   = editorPane.getSelectionEnd();
		
		            // 1) S'il y a une sélection, on la supprime comme d'habitude
		            if (selEnd > selStart) {
		            	editorPane.replaceSelection("");
		                return;
		            }
		
		            // 2) Aucun texte sélectionné : on regarde si juste avant le caret il y a "[tab]"
		            int pos = editorPane.getCaretPosition();
		            if (pos >= 5) {
		                String prev = editorPane.getDocument().getText(pos - 5, 5);
		                if ("[tab]".equals(prev)) {
		                	editorPane.getDocument().remove(pos - 5, 5); // supprime tout le bloc
		                    return;
		                }
		            }
		
		            // 3) Sinon, comportement normal du Backspace
		            if (defaultBackspace != null) {
		                defaultBackspace.actionPerformed(e);
		            } else {
		                // fallback minimal si l’action par défaut est introuvable
		                if (pos > 0) editorPane.getDocument().remove(pos - 1, 1);
		            }
		        } catch (BadLocationException ex) {
		            // ignore en pratique
		        }
		    }
		});
        
        // Fitre automatique pour que toute tabulation soit ue comme [Tab] dans l'éditeur.
        enableVisibleTabs(this.editorPane);

        // --- BARRE DE MENUS ---
        setJMenuBar(writer.ui.menu.MenuBarFactory.create(this));
        
    	// --- Applique les marges pour le texte ---
    	this.editorPane.addCaretListener(ev ->
    	SwingUtilities.invokeLater(() -> ensureCaretHorizontalMargins(108, 108))
			);
    	
    	// Garde le caret (le mot courant) visible avec une marge
  	    editorPane.addCaretListener(ev -> {
  	        // Petit defer pour laisser Swing finir le déplacement de caret
  	        SwingUtilities.invokeLater(() -> ensureWordVisibleWithMargin(108, 108)); // 108 px à gauche/droite, 108 px en vertical
  	    });
    	
        // --- FOCUS INITIAL ---
        SwingUtilities.invokeLater(this.editorPane::requestFocusInWindow);

        // Le TAB ne doit pas changer le focus lorsque le focus est dans l'éditeur
        this.editorPane.setFocusTraversalKeysEnabled(false);
        
    	// --- Setup de la frame ---
    	setupEditorPane();
    	
    	// --- Ajoute les raccourcis clavier ---
    	configureKeyboardShortcuts();

    	// --- Mise à jour du titre de la fenêtre si modifier ---
    	updateWindowTitle();
    	
    	// --- Adaptation de la taille de la frame à son contenu ---
    	pack();
         
         // --- Positionnement de la frame ---
         setLocationRelativeTo(null);
         
     	// --- Maximise la fenêtre ---
     	setExtendedState(JFrame.MAXIMIZED_BOTH);
    }
    
    // --- CONFIGURATION EDITORPANE ---
  	public void setupEditorPane() { 	    
  	    // --- Apparence & confort de saisie (on GARDE) ---
  	    this.editorPane.setForeground(Color.WHITE);
  	    this.editorPane.setBackground(new Color(45, 45, 45));

        // Surlignage de paragraphe, style du caret, sélection par mot
        ParagraphHighlighter.install(this.editorPane);
        CaretStyler.install(this.editorPane, new Color(255, 120, 120), 2, 500);
        WordSelectOnShiftRight.install(this.editorPane);
        
  	    
  	    //editorPane.setFont(new Font("Arial", Font.PLAIN, 34));
  	    applyEditorFont();
  	    String Texte = commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenuAvecTousLesContenusDesEnfants();
  	    Texte = "";
  	    this.editorPane.setText(Texte);
  	    commandes.nameFile = commandes.nodeblindWriter.getAttributs().get("filename");
  	    this.editorPane.setCaretPosition(0);

  	    // marquer le doc comme "modifié" à la moindre modification utilisateur
  	    this.editorPane.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
  	       @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { setModified(true); }
  	       @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { setModified(true); }
  	       @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { setModified(true); }
  	   });


  	   // --- AJOUTE LES CLASS QUI PERMETTENT LES LISTES PUCES OU NULEROTES ---
  	    this.editorPane.getAccessibleContext().setAccessibleName("Zone de texte.");
  	    ((AbstractDocument) editorPane.getDocument()).setDocumentFilter(new AutoListContinuationFilter(this.editorPane));


  	}
    
  	
  	// --- Vérifier si le frame a un menu visible ----
    private boolean isMenuOpen() {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        return path.length > 0;
    }
    
    
    // === AJOUTE LES RACCOURCIS ===
    private void addKeyBinding(int keyCode, int modifier, String actionName, Action action) {
        KeyStroke ks = KeyStroke.getKeyStroke(keyCode, modifier);

        // 1) binder UNE SEULE FOIS, au root pane
        JRootPane rp = getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, actionName);

        // 2) action unique, avec la garde isMenuOpen()
        rp.getActionMap().put(actionName, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!isMenuOpen()) action.actionPerformed(e);
            }
        });
    }
    
    public void configureKeyboardShortcuts() {
        // Conserver uniquement les raccourcis spécifiques à l'éditeur, sans conflit avec le menu
        addKeyBinding(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK, "paragraphe", new act.textBody(this));
        addKeyBinding(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK, "T1", new act.T1(this));
        addKeyBinding(KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK, "T2", new act.T2(this));
        addKeyBinding(KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK, "T3", new act.T3(this));
        addKeyBinding(KeyEvent.VK_4, InputEvent.CTRL_DOWN_MASK, "T4", new act.T4(this));
        addKeyBinding(KeyEvent.VK_5, InputEvent.CTRL_DOWN_MASK, "T5", new act.T5(this));
        addKeyBinding(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK, "gras", new act.Gras(this));
        addKeyBinding(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK, "italique", new act.Italique(this));
        addKeyBinding(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK, "versHaut", new act.VersHaut(this.editorPane));
        addKeyBinding(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK, "versBas", new act.VersBas(this.editorPane));
        addKeyBinding(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK, "versDroite", new act.VersDroite(this.editorPane));
        addKeyBinding(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK, "versGauche", new act.VersGauche(this.editorPane));
        addKeyBinding(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK, "sautPage", new act.SautPage(this));
        addKeyBinding(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, "citation", new act.citation(this));
        addKeyBinding(KeyEvent.VK_F6, 0, "navigateurT1", new act.ouvrirNavigateurT1(this));
        addKeyBinding(KeyEvent.VK_F1, 0, "Informations", new act.informations(this));
        addKeyBinding(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK, "rechercher", new act.openSearchDialog(this.editorPane));
        
        Action puceAction = new InsertUnorderedBulletAction(this.editorPane);
        // US : Ctrl+Shift+.
        addKeyBinding(KeyEvent.VK_PERIOD,InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,"puceTexte.us",puceAction);

        // FR (AZERTY) : Ctrl+Shift+.  => physiquement Ctrl+Shift+; (car '.' = Shift+;)
        addKeyBinding(KeyEvent.VK_SEMICOLON, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,"puceTexte.fr",puceAction);

        // Pavé numérique : Ctrl+Decimal
        addKeyBinding(KeyEvent.VK_DECIMAL,InputEvent.CTRL_DOWN_MASK,"puceTexte.numPad",puceAction);

        // Bonus robuste (alternative facile à dire) : Ctrl+Shift+L
        addKeyBinding(KeyEvent.VK_L,InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,"puceTexte.alt", puceAction);
 
       
        // Bloque ou active la modification editorpane
        addKeyBinding(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK, "toggleEdit", new ToggleEditAction(this.editorPane));
        
        // ——— Raccourci F2 : annonce les titres autour du caret ———
        addKeyBinding(KeyEvent.VK_F2, 0, "announceHeadingsAround", actAnnouncePosition);
        // ——— Raccourci F3 : Pour aller sur le titre suivant ———
        addKeyBinding(KeyEvent.VK_F3, 0, "gotoNextHeading", actGotoNextHeading);
        // ——— Raccourci SHIFT+F3 : Pour aller sur le titre précédent ———
        addKeyBinding(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK, "gotoPrevHeading", actGotoPrevHeading);

     // --- Zoom avant ---
        addKeyBinding(KeyEvent.VK_PLUS,     InputEvent.CTRL_DOWN_MASK, "zoomIn1",
            new AbstractAction(){ public void actionPerformed(ActionEvent e){ zoomIn(); }});
        addKeyBinding(KeyEvent.VK_ADD,      InputEvent.CTRL_DOWN_MASK, "zoomIn3",
            new AbstractAction(){ public void actionPerformed(ActionEvent e){ zoomIn(); }});

        // --- Zoom arrière ---
        addKeyBinding(KeyEvent.VK_MINUS,    InputEvent.CTRL_DOWN_MASK, "zoomOut1",
            new AbstractAction(){ public void actionPerformed(ActionEvent e){ zoomOut(); }});
        addKeyBinding(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK, "zoomOut2",
            new AbstractAction(){ public void actionPerformed(ActionEvent e){ zoomOut(); }});

        // --- Réinitialisation ---
        addKeyBinding(KeyEvent.VK_EQUALS,        InputEvent.CTRL_DOWN_MASK, "zoomReset",
            new AbstractAction(){ public void actionPerformed(ActionEvent e){ zoomReset(); }});
    	
    	// Actifs seulement quand le caret est dans l'éditeur
    	editorPane.getInputMap(JComponent.WHEN_FOCUSED).put(
    	    KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.CTRL_DOWN_MASK),
    	    "actCheckDoc");
    	editorPane.getActionMap().put("actCheckDoc", new act.actCheckDoc(this));

    	editorPane.getInputMap(JComponent.WHEN_FOCUSED).put(
    	    KeyStroke.getKeyStroke(KeyEvent.VK_F7,
    	        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
    	    "actCheckWindow");
    	editorPane.getActionMap().put("actCheckWindow", new act.actCheckWindow(this));
    	
    	// Ctrl+F2 : toggle marque-page sur la ligne
    	addKeyBinding(KeyEvent.VK_F2, InputEvent.CTRL_DOWN_MASK, "bmToggle", new AbstractAction() {
    	    @Override public void actionPerformed(ActionEvent e) {
    	    	var m = bookmarks;
    	        if (m == null) { java.awt.Toolkit.getDefaultToolkit().beep(); return; }
    	        boolean added = bookmarks.toggleHere();
    	        setModified(true);
    	        if (added) {
    	            m.editNoteForNearest(javax.swing.SwingUtilities.getWindowAncestor(editorPane));
    			    String message = (added ? "Marque-page ajouté." : "Marque-page supprimé.");
    			    java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
    			    dia.InfoDialog.show(owner, "Information", message);
    			    setModified(true);
    	        } else {
    	            dia.InfoDialog.show(javax.swing.SwingUtilities.getWindowAncestor(editorPane), "Marque-page", "Marque-page supprimé.");
    	        }
    	    }
    	});

    	// F4 : suivant ; Shift+F4 : précédent
    	addKeyBinding(KeyEvent.VK_F4, 0, "bmNext", new AbstractAction() {
    	    @Override public void actionPerformed(ActionEvent e) {
    	    	var m = bookmarks;
    	        if (m == null) { java.awt.Toolkit.getDefaultToolkit().beep(); return; }
    	        bookmarks.goNext();
    	    }
    	});
    	addKeyBinding(KeyEvent.VK_F4, InputEvent.SHIFT_DOWN_MASK, "bmPrev", new AbstractAction() {
    	    @Override public void actionPerformed(ActionEvent e) {
    	    	var m = bookmarks;
    	        if (m == null) { java.awt.Toolkit.getDefaultToolkit().beep(); return; }
    	        bookmarks.goPrev();
    	    }
    	});
    	
    	// Ajoute une note au marque-page
    	addKeyBinding(KeyEvent.VK_F2, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK,
    		    "bmEditNote", new AbstractAction() {
    		        @Override public void actionPerformed(ActionEvent e) {
    		            var m = bookmarks;
    		            if (m == null) { java.awt.Toolkit.getDefaultToolkit().beep(); return; }
    		            java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
    		            m.editNoteForNearest(owner);
    		        }
    		    }
    		);
    	
    	EditorFrame frame = this; // capture l'instance actuelle
    	addKeyBinding(KeyEvent.VK_F8, 0, "openWikipediaSearch", new AbstractAction() {
    	    @Override
    	    public void actionPerformed(ActionEvent e) {
    	        SwingUtilities.invokeLater(() -> {
    	            // Ouvre la fenêtre de recherche Wikipédia (saisie du mot)
    	            WikipediaSearchDialog.open(frame, url -> {
    	                // Quand l’utilisateur valide la recherche :
    	                new HtmlBrowserDialog(frame, frame.getEditor(), url);
    	            });
    	        });
    	    }
    	});
    }
    

    // ===  GESTION ET ACCÈS AUX SERVICES ===
    @Override
    public JFrame getWindow() { return this; }

    @Override
    public javax.swing.text.JTextComponent getEditor() { return this.editorPane; }

    @Override
	public JScrollPane getScrollPane() { return this.scrollPane; }

	@Override
    public void setModified(boolean modified) {
        this.isModified = modified;
        updateWindowTitle();
    }
    @Override
	public Boolean isModifier() { return this.isModified; }

	@Override
    public void updateWindowTitle() {
        String base = "blindWriter";
        String name = (commandes.nameFile != null && !commandes.nameFile.isBlank())
                ? commandes.nameFile
                : "Nouveau";

        StringBuilder title = new StringBuilder(base).append(" — ").append(name);

        if (commandes.currentDirectory != null) {
            java.io.File dir = new java.io.File(commandes.currentDirectory.toString());
            String folder = dir.getName().isBlank() ? dir.getPath() : dir.getName();
            title.append(" (").append(folder).append(")");
        }
        if (isModified) title.insert(0, "*");
        setTitle(title.toString());
    }

    @Override
    public void clearSpellHighlightsAndFocusEditor() {
        if (spell != null) spell.clearHighlights();
        this.editorPane.requestFocusInWindow();
    }

    @Override
    public void showInfo(String title, String message) {
        dia.InfoDialog.show(this, title, message);
    }

    @Override
    public void addItemChangeListener(JMenuItem menuItem) {
        menuItem.addChangeListener(e -> {
            if (menuItem.isArmed() && menuItem != dernierMenuUtilise) {
                dernierMenuUtilise = menuItem;
            }
        });
    }
    
    @Override
    public SpellCheckLT getSpell() { return this.spell; }

    @Override
    public UndoManager getUndoManager() { return this.undoManager; }

    public Action getUndoAction() { return this.undoAction; }

    public Action getRedoAction() { return this.redoAction; }
    
    public Affiche getAffiche() { return this.affichage;}

    // === UTILITAIRE ===
    private void updateUndoRedoState() {
        this.undoAction.setEnabled(undoManager.canUndo());
        this.redoAction.setEnabled(undoManager.canRedo());
    }
    
	//=============================
	// === MARQUE PAGE ============
	 @Override
    public BookmarkManager getBookmarks() {
        if (this.bookmarks == null && editorPane != null) {
        	this.bookmarks = new BookmarkManager(editorPane);
        }
        return this.bookmarks;
    }
	 
	 @Override
	public void setBookMarkMannager(BookmarkManager bookmark) {
		this.bookmarks = bookmark;
	}

	public void createNewBookmarkManager() {
		if (this.bookmarks == null && editorPane != null) {
        	this.bookmarks = new BookmarkManager(editorPane);
        }
		if (this.bookmarks != null && editorPane != null){
        	this.bookmarks.clearAll();
        	this.bookmarks = new BookmarkManager(editorPane);
        }
	}

	public boolean withBm(java.util.function.Consumer<writer.bookmark.BookmarkManager> use) {
	     writer.bookmark.BookmarkManager m = bookmarks;
	     if (m == null) {
	         java.awt.Toolkit.getDefaultToolkit().beep();
	         System.out.println("Éditeur indisponible.");
	         return false;
	     }
	     use.accept(m);
	     return true;
	 }

	//=====================================================
	// === ANNONCE LA POSITION DANS LE TEXTE DU CURSEUR ===
	public final Action actAnnouncePosition = new AbstractAction("Position dans le texte") {
		 @Override
	        public void actionPerformed(ActionEvent e) {
			 final javax.swing.text.Document doc = editorPane.getDocument();
	 	        final int caretPara = safeParagraphIndexAt(doc, editorPane.getCaretPosition());

	 	        final HeadingFound above = findEnclosingHeading(); // titre au-dessus
	 	        final HeadingFound below = findNextHeadingBelow();  // titre en-dessous

	 	        StringBuilder msg = new StringBuilder(64);
	 	        msg.append("TITRES proches :\n");
	 	        msg.append(formatHeadingLine("• Au-dessus : ", above)).append(" ↓\n");
	 	        msg.append(formatHeadingLine("• En-dessous : ", below)).append(" ↓\n");
	 	        msg.append("• Curseur dans le § : ").append(caretPara);

	 	        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
	 	        dia.InfoDialog.show(owner, "Position dans le texte", msg.toString());
		 }
	};
	 
	@Override
	public Action actAnnouncePosition() {
		return this.actAnnouncePosition;
	}

	private static int safeParagraphIndexAt(javax.swing.text.Document doc, int pos) {
 	    try { return paragraphIndexAt(doc, pos); } catch (Exception ex) { return -1; }
 	}
	
 	private static int paragraphIndexAt(javax.swing.text.Document doc, int offset) {
 	    javax.swing.text.Element root = doc.getDefaultRootElement();
 	    int idx = root.getElementIndex(Math.max(0, Math.min(offset, doc.getLength())));
 	    return idx + 1;
 	}
 	
 	static final class HeadingFound {
 	    final String levelLabel;
 	    final String text;
 	    final int paraIndex;

 	    HeadingFound(String levelLabel, String text, int paraIndex) {
 	        this.levelLabel = levelLabel;
 	        this.text = text;
 	        this.paraIndex = paraIndex;
 	    }
 	}
 	
 	/** Petit conteneur pratique pour avoir au-dessus et en-dessous. */
 	public final class SurroundingHeadings {
 	    public final HeadingFound above; // peut être null
 	    public final HeadingFound below; // peut être null
 	    public SurroundingHeadings(HeadingFound above, HeadingFound below) {
 	        this.above = above; this.below = below;
 	    }
 	}

 	// Renvoie le premier titre AU-DESSUS du caret
 	private HeadingFound findEnclosingHeading() {
 	    try {
 	        javax.swing.text.Document doc = editorPane.getDocument();
 	        javax.swing.text.Element root = doc.getDefaultRootElement();

 	        int caret = Math.max(0, editorPane.getCaretPosition() - 1); // ignore ligne courante
 	        int lineIdx = Math.max(0, root.getElementIndex(caret)) - 1; // démarre à la ligne précédente

 	        for (int i = lineIdx; i >= 0; i--) {
 	            javax.swing.text.Element lineEl = root.getElement(i);
 	            int start = lineEl.getStartOffset();
 	            int end   = Math.min(lineEl.getEndOffset(), doc.getLength());
 	            String line = doc.getText(start, end - start).replaceAll("\\R$", "");

 	            java.util.regex.Matcher m = HEADING_PATTERN.matcher(line);
 	            if (m.matches()) {
 	                int lvl   = Integer.parseInt(m.group(1)); // 1..6
 	                String tx = m.group(2).trim();
 	                return new HeadingFound("Titre " + lvl, tx, i + 1); // 1-based
 	            }
 	        }
 	    } catch (Exception ignore) {}
 	    return null;
 	}
 	
 	/** Renvoie le premier titre EN-DESSOUS du caret (ligne courante incluse). */
 	private HeadingFound findNextHeadingBelow() {
 	    try {
 	        final javax.swing.text.Document doc = editorPane.getDocument();
 	        final javax.swing.text.Element root = doc.getDefaultRootElement();

 	        int caret = Math.max(0, editorPane.getCaretPosition());
 	        int startIdx = Math.max(0, root.getElementIndex(caret)); // démarre à la ligne courante

 	        for (int i = startIdx; i < root.getElementCount(); i++) {
 	            javax.swing.text.Element lineEl = root.getElement(i);
 	            int start = lineEl.getStartOffset();
 	            int end   = Math.min(lineEl.getEndOffset(), doc.getLength());
 	            String line = doc.getText(start, end - start).replaceAll("\\R$", "");

 	            java.util.regex.Matcher m = HEADING_PATTERN.matcher(line);
 	            if (m.matches()) {
 	                int lvl   = Integer.parseInt(m.group(1)); // 1..6
 	                String tx = m.group(2).trim();
 	                return new HeadingFound("Titre " + lvl, tx, i + 1); // 1-based
 	            }
 	        }
 	    } catch (Exception ignore) {}
 	    return null;
 	}
 	
 	private String formatHeadingLine(String prefix, HeadingFound h) {
 	    if (h == null) return prefix + "Aucun titre détecté.";
 	    // Exemple : "Au-dessus : Titre 2 Mon chapitre (§ 128)"
 	    return String.format("%s%s %s (§ %d)", prefix, h.levelLabel, h.text, h.paraIndex);
 	}

 	//===============================
 	//=== ACCEDE AU TITRE SUIVANT ===
 	private final javax.swing.Action actGotoNextHeading = new AbstractAction("Titre suivant") {
 	    @Override public void actionPerformed(ActionEvent e) {
 	        HeadingFound next = findNextHeadingStrictlyBelow();
 	        if (next != null) {
 	            moveCaretToHeadingStart(next);
 	            editorPane.requestFocusInWindow();
 	        } else {
 	            //java.awt.Toolkit.getDefaultToolkit().beep();
 	        }
 	    }
 	};
 	
	@Override
	public Action actGotoNextHeading() {
		return actGotoNextHeading;
	}
	
	/** Titre strictement en-dessous (ignore la ligne courante si c’est déjà un titre). */
 	private HeadingFound findNextHeadingStrictlyBelow() {
 	    try {
 	        final javax.swing.text.Document doc = editorPane.getDocument();
 	        final javax.swing.text.Element root = doc.getDefaultRootElement();

 	        int caret = Math.max(0, editorPane.getCaretPosition());
 	        int startIdx = Math.max(0, root.getElementIndex(caret)) + 1; // <-- +1 : on saute la ligne courante

 	        for (int i = startIdx; i < root.getElementCount(); i++) {
 	            javax.swing.text.Element lineEl = root.getElement(i);
 	            int start = lineEl.getStartOffset();
 	            int end   = Math.min(lineEl.getEndOffset(), doc.getLength());
 	            String line = doc.getText(start, end - start).replaceAll("\\R$", "");
 	            java.util.regex.Matcher m = HEADING_PATTERN.matcher(line);
 	            if (m.matches()) {
 	                int lvl   = Integer.parseInt(m.group(1));
 	                String tx = m.group(2).trim();
 	                return new HeadingFound("Titre " + lvl, tx, i + 1); // 1-based
 	            }
 	        }
 	    } catch (Exception ignore) {}
 	    return null;
 	}
	
 	/** Place le caret au début de la ligne du HeadingFound fourni. */
 	private void moveCaretToHeadingStart(HeadingFound h) {
 	    if (h == null) return;
 	    try {
 	        final javax.swing.text.Document doc = this.editorPane.getDocument();
 	        final javax.swing.text.Element root = doc.getDefaultRootElement();
 	        int lineIdx0 = Math.max(0, Math.min(h.paraIndex - 1, root.getElementCount() - 1));
 	        int pos = root.getElement(lineIdx0).getStartOffset();
 	       this.editorPane.setCaretPosition(pos);
 	        // Assure la visibilité à l’écran
 	        @SuppressWarnings("deprecation")
			java.awt.Rectangle r = this.editorPane.modelToView(pos);
 	        if (r != null) this.editorPane.scrollRectToVisible(r);
 	    } catch (Exception ignore) {}
 	}

 	//=================================
 	//=== ACCEDE AU TITRE PRECEDENT ===
	private final javax.swing.Action actGotoPrevHeading = new AbstractAction("Titre précédent") {
 	    @Override public void actionPerformed(ActionEvent e) {
 	        HeadingFound prev = findEnclosingHeading();
 	        if (prev != null) {
 	            moveCaretToHeadingStart(prev);
 	            editorPane.requestFocusInWindow();
 	        } else {
 	            //java.awt.Toolkit.getDefaultToolkit().beep();
 	        }
 	    }
 	};
	@Override
	public Action actGotoPrevHeading() {
		return actGotoPrevHeading;
	}
	
	//===========================================
	// ===== Mode Affichage de l'editorPane =====
	public Affiche getAffichage() {
        return affichage;
    }

    public void setAffichage(Affiche affichage) {
        this.affichage = affichage;
        updateWindowTitle();
    }

	@Override
	public void afficheDocumentation() {
		if(affichage == Affiche.DOCUMENTATION) {
			this.editorPane.setText(commandes.nodeDocumentation.retourneFirstEnfant("contentText").getContenuAvecTousLesContenusDesEnfants());
        	this.editorPane.setCaretPosition(0);
        	commandes.nameFile = commandes.nodeDocumentation.getAttributs().get("filename");
        	setModified(false);
    	}	
	}

	@Override
	public void sauvegardeTemporaire() {
		if(affichage == Affiche.TEXTE) {
    		positionCurseurSauv = this.editorPane.getCaretPosition();
    		commandes.sauvFile(this.editorPane);
    	}	
	}

	@Override
	public void AfficheTexte() {
		if(affichage == Affiche.TEXTE) {
    		commandes.nodeblindWriter = commandes.sauvFile;
        	if(commandes.nodeblindWriter.retourneFirstEnfant("contentText")!=null) {
        		this.editorPane.setText(commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenuAvecTousLesContenusDesEnfants());
        	}else {
        		this.editorPane.setText("");
        	}
         	commandes.nameFile = commandes.nodeblindWriter.getAttributs().get("filename");
         	this.editorPane.setCaretPosition(positionCurseurSauv);
        	this.editorPane.getAccessibleContext().setAccessibleName("Affichage de votre texte.");
        	setModified(false);
    	}
		
	}

	@Override
	public void AfficheManuel() {
		if(affichage == Affiche.MANUEL) {
			this.editorPane.setText(commandes.manuel.retourneFirstEnfant("contentText").getContenuAvecTousLesContenusDesEnfants());
        	commandes.nameFile = commandes.manuel.getAttributs().get("filename");
        	this.editorPane.setCaretPosition(0);
        	this.editorPane.getAccessibleContext().setAccessibleName("Affichage du manuel b.bbok.");
        	setModified(false);
    	}
	}


	//======================
	//=== ZOOM =============
	@Override
	public void zoomIn() {
	    if (this.editorFontSize < FONT_MAX) {
	    	this.editorFontSize = Math.min(FONT_MAX, this.editorFontSize + FONT_STEP);
	        applyEditorFont();
	    }
	}
	
	@Override
	public void zoomOut() {
	    if (this.editorFontSize > FONT_MIN) {
	    	this.editorFontSize = Math.max(FONT_MIN, this.editorFontSize - FONT_STEP);
	        applyEditorFont();
	    }
	}
	
	@Override
	public void zoomReset() {
		this.editorFontSize = 34f;
	    applyEditorFont();
	}

	// === ZOOM DU TEXTE ===
	private void applyEditorFont() {
	    if (this.editorPane == null) return;

	    // Ltaille dynamique (celle qui change avec le zoom)
	    this.editorPane.setFont(new Font(EDITOR_FONT_FAMILY, Font.PLAIN, Math.round(this.editorFontSize)));

	    // Marges internes pour le confort visuel
	    this.editorPane.setMargin(new java.awt.Insets(12, 24, 12, 24));

	    this.editorPane.revalidate();
	    this.editorPane.repaint();

	    // Le caret bien visible même après zoom
	    SwingUtilities.invokeLater(() -> ensureCaretHorizontalMargins(108, 108));
	}

    
	/** Force une marge horizontale minimale autour du caret dans le viewport. */
 	private void ensureCaretHorizontalMargins(int leftMarginPx, int rightMarginPx) {
 	    if (this.editorPane == null || this.scrollPane == null) return;
 	    try {
 	        int pos = Math.max(0, Math.min(this.editorPane.getCaretPosition(), this.editorPane.getDocument().getLength()));
 	        java.awt.geom.Rectangle2D r2 = this.editorPane.modelToView2D(pos);
 	        if (r2 == null) return; // <-- garde-fou
 	        java.awt.Rectangle r = r2.getBounds();

 	        javax.swing.JViewport vp = scrollPane.getViewport();
 	        java.awt.Rectangle view = vp.getViewRect();
 	        int newX = view.x;

 	        if (r.x - leftMarginPx < view.x) {
 	            newX = Math.max(0, r.x - leftMarginPx);
 	        } else if (r.x + r.width + rightMarginPx > view.x + view.width) {
 	            newX = r.x + r.width + rightMarginPx - view.width;
 	        }
 	        int maxX = Math.max(0, this.editorPane.getWidth() - view.width);
 	        newX = Math.max(0, Math.min(newX, maxX));
 	        if (newX != view.x) vp.setViewPosition(new java.awt.Point(newX, view.y));
 	    } catch (javax.swing.text.BadLocationException ignore) { }
 	}
 	
 	/** Fait défiler pour que le mot sous le caret soit visible avec une marge. */
    private void ensureWordVisibleWithMargin(int hMarginPx, int vMarginPx) {
        if (this.editorPane == null || this.scrollPane == null) return;

        try {
            int pos = editorPane.getCaretPosition();

            // Limites de mot (robuste même entre espaces/ponctuation)
            int ws = javax.swing.text.Utilities.getWordStart(editorPane, pos);
            int we = javax.swing.text.Utilities.getWordEnd(editorPane, pos);
            if (ws < 0 || we < ws) {
                // fallback sur le caractère
                Rectangle2D r2 = editorPane.modelToView2D(pos);
                if (r2 != null) {
                    Rectangle r = r2.getBounds();
                    expandAndScroll(r, hMarginPx, vMarginPx);
                }
                return;
            }

            // Rectangle du mot = union des bornes start/end
            Rectangle2D rStart2 = editorPane.modelToView2D(ws);
            Rectangle2D rEnd2   = editorPane.modelToView2D(Math.max(ws, Math.min(we, editorPane.getDocument().getLength())));
            if (rStart2 == null || rEnd2 == null) return;

            Rectangle rStart = rStart2.getBounds();
            Rectangle rEnd   = rEnd2.getBounds();

            Rectangle wordRect = new Rectangle(
                Math.min(rStart.x, rEnd.x),
                Math.min(rStart.y, rEnd.y),
                Math.abs((rEnd.x + rEnd.width) - rStart.x),
                Math.max(rStart.height, rEnd.height)
            );

            // Étend avec la marge et scroll
            expandAndScroll(wordRect, hMarginPx, vMarginPx);

        } catch (BadLocationException ignored) {}
    }
    
    /** Agrandit un rectangle avec des marges et appelle scrollRectToVisible. */
    private void expandAndScroll(Rectangle r, int hMarginPx, int vMarginPx) {
        if (r == null) return;

        // On crée un rect “coussin” pour éviter d’être collé au bord
        int x = Math.max(0, r.x - hMarginPx);
        int y = Math.max(0, r.y - vMarginPx);
        int w = r.width  + 2 * hMarginPx;
        int h = r.height + 2 * vMarginPx;

        Rectangle padded = new Rectangle(x, y, w, h);

        // Fait défiler la vue
        this.editorPane.scrollRectToVisible(padded);
    }
    
    //Permet de positionnner le scroll verticale en fonction de la position du curseur.
    public void placeCursorAtText(String searchText) {
       String text = this.editorPane.getText(); // Récupérer le texte de la JTextArea
        int index = text.indexOf(searchText); // Rechercher l'index de la chaîne "selectedT1"
        
        if (index != -1) {
            // Placer le curseur au début de "selectedT1"
        	this.editorPane.setCaretPosition(index);
        	
        	 // Faire défiler pour que le texte soit visible en haut de la zone visible
            try {
                // Récupérer les coordonnées du rectangle correspondant à la position du curseur avec modelToView2D
                Rectangle2D rect2D = this.editorPane.modelToView2D(this.editorPane.getCaretPosition());
                Rectangle rect = rect2D.getBounds();

                // Faire défiler pour que cette position soit visible en haut
                this.editorPane.scrollRectToVisible(new Rectangle(rect.x, rect.y, rect.width, this.scrollPane.getViewport().getHeight()));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        	
        } else {
        	System.out.println("Texte non trouvé : " + searchText );
        }
    }
    
    public static void enableVisibleTabs(JTextComponent editor) {
        // 1) Le TAB ne doit pas déplacer le focus
        editor.setFocusTraversalKeysEnabled(false);

        // 2) Quand l'utilisateur TAPE TAB -> insérer "[tab]"
        InputMap im = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = editor.getActionMap();

        im.put(KeyStroke.getKeyStroke("TAB"), "bw-insert-tab-tag");
        im.put(KeyStroke.getKeyStroke("shift TAB"), "bw-insert-tab-tag");
        am.put("bw-insert-tab-tag", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                editor.replaceSelection("[tab]");
            }
        });

        // 3) Intercepter COLLER (Ctrl+V, Shift+Insert)
        im.put(KeyStroke.getKeyStroke("ctrl V"), "bw-paste-visible-tabs");
        im.put(KeyStroke.getKeyStroke("shift INSERT"), "bw-paste-visible-tabs");
        am.put("bw-paste-visible-tabs", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                try {
                    Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                    if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        String s = (String) cb.getData(DataFlavor.stringFlavor);
                        s = mapTabs(s);             // \t -> [tab]
                        editor.replaceSelection(s);
                        return;
                    }
                } catch (Exception ignore) {}
                // Fallback : comportement standard
                editor.paste();
            }
        });

        // 4) TransferHandler : couvre menu contextuel / DnD / coller système
        final TransferHandler baseTH = editor.getTransferHandler(); // garde l'existant
        editor.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(JComponent comp, DataFlavor[] flavors) {
                // on accepte comme le handler existant
                return baseTH == null || baseTH.canImport(comp, flavors);
            }
            @Override
            public boolean importData(JComponent comp, Transferable t) {
                try {
                    if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String s = (String) t.getTransferData(DataFlavor.stringFlavor);
                        s = mapTabs(s);
                        // insère à la position du caret
                        editor.replaceSelection(s);
                        return true;
                    }
                } catch (Exception ignore) {}
                return baseTH != null && baseTH.importData(comp, t);
            }
        });

        // 5) DocumentFilter : couvre insertions programmées, replace, etc.
        Document doc = editor.getDocument();
        if (doc instanceof AbstractDocument ad) {
            ad.setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offs, String str, AttributeSet a)
                        throws BadLocationException {
                    super.insertString(fb, offs, mapTabs(str), a);
                }
                @Override
                public void replace(FilterBypass fb, int offs, int len, String str, AttributeSet a)
                        throws BadLocationException {
                    super.replace(fb, offs, len, mapTabs(str), a);
                }
            });
        }
    }

    // Utilitaire centralisé
    private static String mapTabs(String s) {
        return (s == null) ? null : s.replace("\t", "[tab] ");
    }


   
    

 	
}