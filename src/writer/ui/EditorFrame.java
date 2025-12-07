package writer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
//import java.awt.Desktop;
import java.awt.Font;
//import java.awt.Rectangle;
//import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
//import java.awt.geom.Rectangle2D;
//import java.net.URI;
//import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
//import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
//import javax.swing.text.StyledDocument;
import javax.swing.undo.UndoManager;

import writer.CaretStyler;
import writer.ParagraphHighlighter;
import writer.WordSelectOnShiftRight;
import writer.commandes;
import writer.bookmark.BookmarkManager;
import writer.model.Affiche;
import writer.spell.SpellCheckLT;
import writer.ui.editor.EnterPiedDeMoucheInsertAction;
import writer.ui.editor.FastHighlighter;
//import writer.ui.editor.WrapEditorKit;
import writer.ui.editor.enableCopyPasteVisibleTabs;
import writer.util.IconLoader;
import xml.node;


@SuppressWarnings("serial")
public class EditorFrame extends JFrame implements EditorApi {

    // === CHAMPS PRINCIPAUX ===
    private final NormalizingTextPane editorPane = new NormalizingTextPane(); // Migration vers private JTextPane editor;
    private final UndoManager undoManager = new UndoManager();
    private final Action undoAction;
    private final Action redoAction;
    private JMenuItem dernierMenuUtilise = null;
    private boolean isModified = false;
    private SpellCheckLT spell;
    private BookmarkManager bookmarks;
    
    // --- Motif unique : "#<niveau>. <texte>" strictement en début de ligne ---
  	private static final Pattern HEADING_PATTERN = Pattern.compile("^(?:¶\\s*)?#([1-6])\\.\\s*(.+?)\\s*$", Pattern.MULTILINE);

  	
  	// Détecte une image au format ![Image : description]
  	@SuppressWarnings("unused")
	private static final Pattern IMAGE_PATTERN = Pattern.compile(
  	    "!\\[\\s*Image\\s*:\\s*([^\\]]+)\\]"
  	);

  	private Affiche affichage = Affiche.TEXTE1;
  	public static int positionTexte1CurseurSauv = 0;
  	public static int positionTexte2CurseurSauv = 0;
  	
  	// --- Zoom éditeur ---
 	private static String EDITOR_FONT_FAMILY = "Arial";
 	@SuppressWarnings("unused")
	private static int    EDITOR_FONT_SIZE   = 42;
 	private static final int FONT_MIN = 14, FONT_MAX = 130, FONT_STEP = 2;
  	private float editorFontSize = 34f;
  	private final JScrollPane scrollPane;
  	
  	// === ANNONCE LA POSITION DANS LE TEXTE DU CURSEUR ===
  	private final Action actAnnouncePosition = new writer.ui.editor.AnnouncePositionAction(this);

  	// === Drapeau pour suspendre l'enregistrement de l'historique ===
   	private volatile boolean undoSuspended = false;
   	
   	// Mémo de l’état précédent pour éviter les setEnabled inutiles
   	private boolean prevCanUndo = false;
   	private boolean prevCanRedo = false;
   	
   	// Cache pour éviter re-setFont/revalidate/repaint inutiles
   	private float lastAppliedFontSize = -1f;
   	private boolean marginsAppliedOnce = false;

   	// Marges réutilisées (pas de nouvelle instance à chaque fois)
   	private static final java.awt.Insets EDITOR_MARGINS = new java.awt.Insets(12, 24, 12, 24);

	 // — KeyStrokes centralisés —
	 // Navigation / actions fréquentes
	 private static final KeyStroke KS_CTRL_ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK);
	 private static final KeyStroke KS_TAB        = KeyStroke.getKeyStroke("TAB");
	 private static final KeyStroke KS_SHIFT_TAB  = KeyStroke.getKeyStroke("shift TAB");
	 private static final KeyStroke KS_HOME       = KeyStroke.getKeyStroke("HOME");
	
	 // Éditions
	 private static final KeyStroke KS_CTRL_DEL        = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.CTRL_DOWN_MASK);
	 private static final KeyStroke KS_CTRL_BSP        = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.CTRL_DOWN_MASK);
	 private static final KeyStroke KS_CTRL_SHIFT_DEL  = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK);
	 private static final KeyStroke KS_CTRL_SHIFT_BSP  = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK);
	 private static final KeyStroke KS_BSP             = KeyStroke.getKeyStroke("BACK_SPACE");
	 private static final KeyStroke KS_DEL             = KeyStroke.getKeyStroke("DELETE");

	 // cache pour la mise à jour du titre de la fenêtre
	 private String lastWindowTitle = null;
	 
	// même couleur que l’éditeur
     Color EDITOR_BG = new Color(45, 45, 45);
	 	 
    // === CONSTRUCTEUR ===
    public EditorFrame() {
        super("LisioWriter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- CONFIGURATION DE L'ÉDITEUR ---
        scrollPane = new JScrollPane(editorPane);
        
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        scrollPane.getViewport().setBackground(EDITOR_BG);
        scrollPane.setBackground(EDITOR_BG);
        scrollPane.getViewport().setOpaque(true);   // par sécurité
        getContentPane().setBackground(EDITOR_BG);  // si jamais on voit le fond de la frame


        // Attache le gestionnaire Undo/Redo de la méthode defaultUndoableEditListener
        editorPane.addStickyUndoableEditListener(defaultUndoableEditListener);
        
        // --- ICON APP ----
        setIconImage(IconLoader.load(Icons.APP).getImage());

        // --- CRÉATION DES ACTIONS ANNULER / RÉTABLIR ---
        undoAction = new AbstractAction("Annuler") {
            @Override public void actionPerformed(ActionEvent e) {
            	System.out.println("UNDO pressed, canUndo="+undoManager.canUndo());
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
        
        this.undoManager.setLimit(Integer.getInteger("lisio.undo.limit", 2000));
        
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
        
        // --- RACCOURCIS CLAVIER DIRECTS ---
        InputMap im = this.editorPane.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = this.editorPane.getActionMap();
        
        // --- Ouvrir lien wikipédia sous le curseur (Ctrl + Entrée) ---
        im.put(KS_CTRL_ENTER, "bw-open-link");
        am.put("bw-open-link", new writer.ui.editor.OpenLinkAtCaretAction(this));
               
        // Key binding: TAB et Shift+TAB pour saisir à la place [Tab]
        im.put(KS_TAB, "bw-insert-tab-tag");
        im.put(KS_SHIFT_TAB, "bw-insert-tab-tag");
        am.put("bw-insert-tab-tag", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
            	editorPane.replaceSelection("[tab] ");
            }
        });
        
        // Ctrl+Suppr -> supprimer mot en avant
        im.put(KS_CTRL_DEL, "bw-delete-next-word");
        am.put("bw-delete-next-word", new writer.ui.editor.DeleteNextWordAction(this.editorPane));

        // Ctrl+Backspace -> supprimer le mot en arrière
        im.put(KS_CTRL_BSP, "bw-delete-prev-word");
        am.put("bw-delete-prev-word", new writer.ui.editor.DeletePrevWordAction(this.editorPane));
        
        // Ctrl+Maj+Suppr -> supprimer le paragraphe courant et aller au suivant
        im.put(KS_CTRL_SHIFT_DEL, "bw-delete-paragraph-forward");
        am.put("bw-delete-paragraph-forward",
              new writer.ui.editor.DeleteParagraphForwardAction(this.editorPane));
        
        // Ctrl+Maj+Backspace -> supprime le paragraphe courant et va à la fin du précédent
        im.put(KS_CTRL_SHIFT_BSP, "bw-delete-paragraph-backward");
        am.put("bw-delete-paragraph-backward",
              new writer.ui.editor.DeleteParagraphBackwardAction(this.editorPane));
        
        // Récupère l’action Backspace par défaut (supprime le caractère précédent)
        Action defaultBackspace = this.editorPane.getActionMap().get(DefaultEditorKit.deletePrevCharAction);
        
        // Filtre automatique pour que toute tabulation soit une comme [Tab] dans l'éditeur.
        enableCopyPasteVisibleTabs.enableVisibleTabs(this.editorPane);

        // --- BARRE DE MENUS ---
        setJMenuBar(writer.ui.menu.MenuBarFactory.create(this));
        
        // --- FOCUS INITIAL ---
        SwingUtilities.invokeLater(this.editorPane::requestFocusInWindow);

        // Le TAB ne doit pas changer le focus lorsque le focus est dans l'éditeur
        this.editorPane.setFocusTraversalKeysEnabled(false);
        
        // Navigation paragraphe par paragraphe sur ↑ / ↓
        writer.ui.editor.ParagraphNavigator.install(this.editorPane);
        
    	// --- Setup de la frame ---
    	setupEditorPane();
    	
    	editorPane.setNavigationFilter(
    		    new writer.ui.editor.NoGapAcrossBrailleNavigationFilter(
    		        editorPane,
    		        editorPane.getNavigationFilter() 
    		    )
    		);
    	
    	// --- Remapper Home pour aller au début logique (1 au lieu de 0)
    	editorPane.getInputMap().put(KS_HOME, "bw-home-safe");
		editorPane.getActionMap().put("bw-home-safe", new javax.swing.AbstractAction() {
		    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
		        int len = editorPane.getDocument().getLength();
		        int pos = (len == 0) ? 0 : 1;
		        editorPane.setCaretPosition(pos);
		    }
		});
    		
		// --- Ajoute les raccourcis clavier ---
    	new writer.ui.editor.KeyboardShortcutManager(this, this.editorPane).installShortcuts();
    	
    	// --- Mise à jour du titre de la fenêtre si modifier ---
    	updateWindowTitle();
    	
    	// --- Adaptation de la taille de la frame à son contenu ---
    	pack();
         
         // --- Positionnement de la frame ---
         setLocationRelativeTo(null);
         
     	// --- Maximise la fenêtre ---
     	setExtendedState(JFrame.MAXIMIZED_BOTH);
     	
     	this.addWindowListener(new WindowAdapter() {
     	    @Override
     	    public void windowOpened(WindowEvent e) {
     	        writer.spell.SpellCheckLT.preloadInBackground(); // safe et idempotent
     	    }
     	});

     	// récupérer action par défaut comme fallback (optionnel)
     	EnterPiedDeMoucheInsertAction piedDeMoucheEnter = EnterPiedDeMoucheInsertAction.createWithDefaultFallback(editorPane, true);
  	    editorPane.getActionMap().put(javax.swing.text.DefaultEditorKit.insertBreakAction, piedDeMoucheEnter);

  	    // Remappe BACK_SPACE vers notre action intelligente
  	    this.editorPane.getInputMap().put(KS_BSP, "bw-smart-backspace");
        this.editorPane.getActionMap().put("bw-smart-backspace",
            new writer.ui.editor.SmartBackspaceAction(editorPane, defaultBackspace));
        
        // Remappe Delete vers notre action intelligente
        this.editorPane.getInputMap().put(KS_DEL, "bw-smart-delete");
        this.editorPane.getActionMap().put("bw-smart-delete",
            new writer.ui.editor.SmartDeleteAction(editorPane, defaultBackspace));      
    
        // Colorisation - installer le highlighter sans invokeLater inutile
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            FastHighlighter.install(this.editorPane);
        } else {
            javax.swing.SwingUtilities.invokeLater(() -> FastHighlighter.install(this.editorPane));
        }
        
    }
    
    // Tous les filtres
    private final UndoableEditListener defaultUndoableEditListener = e -> {
        if (undoSuspended) return;

        Object edit = e.getEdit();
        if (edit instanceof AbstractDocument.DefaultDocumentEvent dev) {
            if (dev.getType() == DocumentEvent.EventType.CHANGE) {
                // On ignore TOUT CHANGE (coloration, attributs, etc.)
                return;
            }
        }
        undoManager.addEdit(e.getEdit());
        updateUndoRedoState();
    };


    // --- CONFIGURATION EDITORPANE ---
  	public void setupEditorPane() { 	    
  	    // --- Apparence & confort de saisie (on GARDE) ---
  		this.editorPane.setForeground(Color.WHITE);
  		this.editorPane.setBackground(EDITOR_BG);
  		this.editorPane.setOpaque(true);


        // Surlignage de paragraphe, style du caret, sélection par mot
        ParagraphHighlighter.install(this.editorPane);
        CaretStyler.install(this.editorPane, new Color(255, 80, 80), 3, 500);
        WordSelectOnShiftRight.install(this.editorPane);

  	    //editorPane.setFont(new Font("Arial", Font.PLAIN, 34));
  	    applyEditorFont();

	  	// --- AJOUTE LES CLASS QUI PERMETTENT LES LISTES PUCES OU NUMEROTEES ---
	  	// --- empêche d’insérer quelque chose en position zéro
	  	javax.swing.text.Document doc = editorPane.getDocument();
	  	if (doc instanceof javax.swing.text.AbstractDocument ad) {
	  	    javax.swing.text.DocumentFilter guard = new writer.ui.editor.NoInsertAtZeroFilter();
	  	    javax.swing.text.DocumentFilter auto  = new writer.editor.AutoListContinuationFilter(this.editorPane); // ← ici le bon package
	
	  	    javax.swing.text.DocumentFilter existing = ad.getDocumentFilter();
	
	  	    // Compose proprement : (existing) -> guard -> auto
	  	    javax.swing.text.DocumentFilter composed =
	  	        (existing == null)
	  	            ? new writer.ui.editor.CompositeDocumentFilter(guard, auto)
	  	            : new writer.ui.editor.CompositeDocumentFilter(
	  	                  existing,
	  	                  new writer.ui.editor.CompositeDocumentFilter(guard, auto)
	  	              );
	
	  	    ad.setDocumentFilter(composed);
	  	}


//  	    this.editorPane.getAccessibleContext().setAccessibleName("Zone de texte.");
  	
  	}
    
  	// --- Vérifier si le frame a un menu visible ----
    @SuppressWarnings("unused")
	private boolean isMenuOpen() {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        return path.length > 0;
    }
 
    // ===  GESTION ET ACCÈS AUX SERVICES ===
    @Override
    public JFrame getWindow() { return this; }

    @Override
    public writer.ui.NormalizingTextPane getEditor() { return this.editorPane; }

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
	    final String base = "LisioWriter";
	    final String name = (commandes.nameFile != null && !commandes.nameFile.isBlank())
	            ? commandes.nameFile
	            : "Nouveau";

	    String folder = null;
	    if (commandes.currentDirectory != null) {
	        java.io.File dir = new java.io.File(commandes.currentDirectory.toString());
	        folder = dir.getName().isBlank() ? dir.getPath() : dir.getName();
	    }

	    StringBuilder title = new StringBuilder();
	    if (isModified) title.append('*');
	    title.append(base).append(" — ").append(name);
	    if (folder != null) title.append(" (").append(folder).append(')');

	    String newTitle = title.toString();
	    if (!newTitle.equals(lastWindowTitle)) {
	        setTitle(newTitle);
	        lastWindowTitle = newTitle;
	    }
	}


    @Override
    public void clearSpellHighlightsAndFocusEditor() {
        if (spell != null) spell.clearHighlights();
        this.editorPane.requestFocusInWindow();
    }

    @Override
    public void showInfo(String title, String message) {
        dia.InfoDialog.show(this, title, message, affichage);
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
        boolean canUndo = undoManager.canUndo();
        boolean canRedo = undoManager.canRedo();

        if (canUndo != prevCanUndo) {
            this.undoAction.setEnabled(canUndo);
            prevCanUndo = canUndo;
        }
        if (canRedo != prevCanRedo) {
            this.redoAction.setEnabled(canRedo);
            prevCanRedo = canRedo;
        }
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

	@Override
  	public Action actAnnouncePosition() { return this.actAnnouncePosition; }

 	
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
 	            String line = chompLine(doc.getText(start, end - start));

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


 	//===============================
 	//=== ACCEDE AU TITRE SUIVANT ===
 	private final javax.swing.Action actGotoNextHeading = new AbstractAction("Titre suivant") {
 	    @Override public void actionPerformed(ActionEvent e) {
 	        HeadingFound next = findNextHeadingStrictlyBelow();
 	        if (next != null) {
 	            moveCaretToHeadingStart(next);
 	            editorPane.requestFocusInWindow();
 	        } else {
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
 	            String line = chompLine(doc.getText(start, end - start));
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
 	        // Assure la visibilité à l’écran (API non dépréciée)
 	        java.awt.geom.Rectangle2D r2d = this.editorPane.modelToView2D(pos);
 	        if (r2d != null) this.editorPane.scrollRectToVisible(r2d.getBounds());
 	    } catch (Exception ignore) {}
 	}

 	//=================================
 	//=== ACCEDE AU TITRE PRECEDENT ===
 	private final javax.swing.Action actGotoPrevHeading = new AbstractAction("Titre précédent") {
 	    @Override public void actionPerformed(ActionEvent e) {
 	        HeadingFound prev = findEnclosingHeading();
 	        if (prev != null) {
 	            // On garde ta méthode commune
 	            moveCaretToHeadingStart(prev);
 	            // Puis on corrige la position pour entrer vraiment dans la ligne du titre
 	            try {
 	                final javax.swing.text.Document doc = editorPane.getDocument();
 	                final javax.swing.text.Element root = doc.getDefaultRootElement();
 	                int lineIdx0 = Math.max(0, Math.min(prev.paraIndex - 1, root.getElementCount() - 1));
 	                int start = root.getElement(lineIdx0).getStartOffset();
 	                int pos = logicalStartOfLine(doc, start);
 	                editorPane.setCaretPosition(pos);
 	            } catch (Exception ignore) {}
 	            editorPane.requestFocusInWindow();
 	        }
 	    }
 	};
	@Override
	public Action actGotoPrevHeading() {
		return actGotoPrevHeading;
	}
	
	/** Avance depuis le début de la ligne jusqu’au début logique (# ou texte),
	 * en sautant CR/LF résiduels, braille ¶ et espaces. */
	private int logicalStartOfLine(javax.swing.text.Document doc, int lineStart) throws Exception {
	    final int len = doc.getLength();
	    final int blockSize = Math.min(256, len - lineStart); // lecture en bloc, suffisant pour un début de ligne
	    if (blockSize <= 0) return lineStart;

	    javax.swing.text.Segment seg = new javax.swing.text.Segment();
	    doc.getText(lineStart, blockSize, seg);

	    int offset = 0;

	    // 1) Corrige les fins de ligne précédentes (\r, \n, \r\n)
	    if (lineStart > 0) {
	        char prev = doc.getText(lineStart - 1, 1).charAt(0);
	        if (prev == '\r' && lineStart < len) {
	            char next = doc.getText(lineStart, 1).charAt(0);
	            if (next == '\n') offset++; // saute le \n après un \r
	        }
	    }

	    // 2) Parcourt le buffer localement (très rapide)
	    while (offset < seg.count) {
	        char c = seg.array[seg.offset + offset];
	        if (c == '\u00B6' || Character.isWhitespace(c)) {
	            offset++;
	        } else break;
	    }

	    return lineStart + offset;
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
        	// colorisation
            FastHighlighter.rehighlightAll(this.editorPane); // une passe globale, optionnelle
        	setModified(false);
    	}	
	}

	@Override
	public void sauvegardeTemporaireTexte1() {
		if(affichage == Affiche.TEXTE1) {
    		positionTexte1CurseurSauv = this.editorPane.getCaretPosition();
    		commandes.sauvTexte1File(editorPane,affichage);
    	}	
	}
	
	@Override
	public void sauvegardeTemporaireTexte2() {
		if(affichage == Affiche.TEXTE2) {
    		positionTexte2CurseurSauv = this.editorPane.getCaretPosition();
    		commandes.sauvTexte2File(editorPane,affichage);
    	}	
	}

	@Override
	public void AfficheTexte() {
		if(affichage == Affiche.TEXTE1 || affichage == Affiche.TEXTE2) commandes.nodeblindWriter = new node(); else return;
		if(affichage == Affiche.TEXTE1) commandes.nodeblindWriter = commandes.sauvText1File;
		if(affichage == Affiche.TEXTE2) commandes.nodeblindWriter = commandes.sauvText2File;
		
		if(commandes.nodeblindWriter.retourneFirstEnfant("contentText")!=null) {
    		this.editorPane.setText(commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenuAvecTousLesContenusDesEnfants());
    	}else {
    		this.editorPane.setText("¶ ");
    	}
		
		if(commandes.nodeblindWriter.retourneFirstEnfant("styles_paragraphes")!=null) {
			commandes.styles_paragraphe = commandes.nodeblindWriter.retourneFirstEnfant("styles_paragraphes");
		}else {
			commandes.defaultStyles();
		}
		
		if(commandes.nodeblindWriter.retourneFirstEnfant("meta")!=null) {
			commandes.maj_meta() ;
		}else {
			commandes.init_meta();
		}
		
    	if (commandes.nodeblindWriter.retourneFirstEnfant("bookmarks") != null) {
            commandes.bookmarks = commandes.nodeblindWriter.retourneFirstEnfant("bookmarks");
            try {
				getBookmarks().loadFromXml(commandes.nodeblindWriter.retourneFirstEnfant("bookmarks"));
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
    	}
    	
    	
    	// colorisation
        FastHighlighter.rehighlightAll(this.editorPane); // une passe globale, optionnelle
     	commandes.nameFile = commandes.nodeblindWriter.getAttributs().get("filename");
     	
     	if(affichage == Affiche.TEXTE1) {
     		this.editorPane.setCaretPosition(positionTexte1CurseurSauv);
//     		this.editorPane.getAccessibleContext().setAccessibleName("Fenêtre 1");
     	}else if(affichage == Affiche.TEXTE2){
     		this.editorPane.setCaretPosition(positionTexte2CurseurSauv);
//     		this.editorPane.getAccessibleContext().setAccessibleName("Fenêtre 2");
     	}
        	
        	setModified(false);

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

	    // 1) Appliquer les marges une seule fois
	    if (!marginsAppliedOnce) {
	        this.editorPane.setMargin(EDITOR_MARGINS);
	        marginsAppliedOnce = true;
	    }

	    // 2) Si la taille n’a pas changé, ne rien faire (évite Font + revalidate + repaint)
	    float size = this.editorFontSize;
	    if (size == lastAppliedFontSize) return;

	    // 3) Taille différente → nouveau Font uniquement maintenant
	    this.editorPane.setFont(new Font(EDITOR_FONT_FAMILY, Font.PLAIN, Math.round(size)));
	    lastAppliedFontSize = size;

	    // 4) Et seulement maintenant, le rafraîchissement
	    this.editorPane.revalidate();
	    this.editorPane.repaint();
	}

	/** Exécute une édition du document sans créer d’entrée d’historique. */
	public void runWithoutUndo(Runnable edit) {
	    Runnable task = () -> {
	        undoSuspended = true;               // ← coupe l’Undo (texte + styles)
	        try {
	            edit.run();                     // (setText, setCharacterAttributes, etc.)
	        } finally {
	            undoSuspended = false;          // ← réactive
	            updateUndoRedoState();
	        }
	    };

	    if (javax.swing.SwingUtilities.isEventDispatchThread()) {
	        task.run();
	    } else {
	        javax.swing.SwingUtilities.invokeLater(task);
	    }
	}
	
	// Supprime un unique saut de ligne final (\n, \r ou \r\n) sans regex.
	private static String chompLine(String s) {
	    int len = s.length();
	    if (len == 0) return s;
	    char last = s.charAt(len - 1);
	    if (last == '\n') {
	        // \r\n ?
	        if (len >= 2 && s.charAt(len - 2) == '\r') return s.substring(0, len - 2);
	        return s.substring(0, len - 1);
	    } else if (last == '\r') {
	        return s.substring(0, len - 1);
	    }
	    return s;
	}

	/** Vide proprement l’historique Undo/Redo pour libérer la mémoire. */
	public void clearUndoHistory() {
	    undoManager.discardAllEdits();
	    prevCanUndo = false;
	    prevCanRedo = false;
	    updateUndoRedoState();
	}

	
}