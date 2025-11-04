package writer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import javax.swing.undo.UndoManager;

import writer.CaretStyler;
import writer.ParagraphHighlighter;
import writer.WordSelectOnShiftRight;
import writer.commandes;
import writer.bookmark.BookmarkManager;
import writer.editor.AutoListContinuationFilter;
import writer.model.Affiche;
import writer.spell.SpellCheckLT;
import writer.ui.editor.TextHighlighter;
import writer.ui.editor.WrapEditorKit;
import writer.util.IconLoader;

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

  	// Détecte un lien au format @[Titre du lien: https://exemple.com]
  	private static final Pattern URL_PATTERN = Pattern.compile(
  	    "@\\[([^\\]]+?):\\s*(https?://[^\\s\\]]+)\\]"
  	);
  	
  	// Détecte une image au format ![Image : description]
  	@SuppressWarnings("unused")
	private static final Pattern IMAGE_PATTERN = Pattern.compile(
  	    "!\\[\\s*Image\\s*:\\s*([^\\]]+)\\]"
  	);

  	private Affiche affichage = Affiche.TEXTE;
  	public static int positionCurseurSauv = 0;
  	// --- Zoom éditeur ---
 	private static String EDITOR_FONT_FAMILY = "Arial";
 	@SuppressWarnings("unused")
	private static int    EDITOR_FONT_SIZE   = 42;
 	private static final int FONT_MIN = 14, FONT_MAX = 130, FONT_STEP = 2;
  	private float editorFontSize = 34f;
  	private final JScrollPane scrollPane;
  	
  	// === ANNONCE LA POSITION DANS LE TEXTE DU CURSEUR ===
  	private final Action actAnnouncePosition = new writer.ui.editor.AnnouncePositionAction(this.editorPane);



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
        
        // --- Ouvrir lien sous le curseur (Ctrl + Entrée) ---
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "bw-open-link");
        am.put("bw-open-link", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                try {
                    int pos = editorPane.getCaretPosition();
                    StyledDocument doc = editorPane.getStyledDocument();
                    String text = doc.getText(0, doc.getLength());
                    Matcher m = URL_PATTERN.matcher(text);
                    while (m.find()) {
                        int start = m.start(2);
                        int end = m.end(2);
                        if (pos >= start && pos <= end) {
                            String title = m.group(1).trim();
                            String url = m.group(2).trim();
                            System.out.println("Ouverture du lien : " + title + " → " + url);
                            Desktop.getDesktop().browse(new URI(url));
                            return;
                        }
                    }
                    Toolkit.getDefaultToolkit().beep();
                } catch (Exception ex) {
                    Toolkit.getDefaultToolkit().beep();
                }
            }
        });

        
        // Key binding: TAB et Shift+TAB pour saisir à la place [Tab]
        im.put(KeyStroke.getKeyStroke("TAB"), "bw-insert-tab-tag");
        im.put(KeyStroke.getKeyStroke("shift TAB"), "bw-insert-tab-tag");
        am.put("bw-insert-tab-tag", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
            	editorPane.replaceSelection("[tab] ");
            }
        });
        
        // Ctrl+Suppr -> supprimer mot en avant
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, java.awt.event.KeyEvent.CTRL_DOWN_MASK),
              "bw-delete-next-word");
        am.put("bw-delete-next-word", new writer.ui.editor.DeleteNextWordAction(this.editorPane));

        // Ctrl+Backspace -> supprimer le mot en arrière
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_BACK_SPACE, java.awt.event.KeyEvent.CTRL_DOWN_MASK),
              "bw-delete-prev-word");
        am.put("bw-delete-prev-word", new writer.ui.editor.DeletePrevWordAction(this.editorPane));
        
        // Ctrl+Maj+Suppr -> supprimer le paragraphe courant et aller au suivant
        im.put(KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_DELETE,
                java.awt.event.KeyEvent.CTRL_DOWN_MASK | java.awt.event.KeyEvent.SHIFT_DOWN_MASK),
              "bw-delete-paragraph-forward");
        am.put("bw-delete-paragraph-forward",
              new writer.ui.editor.DeleteParagraphForwardAction(this.editorPane));
        
        // Ctrl+Maj+Backspace -> supprime le paragraphe courant et va à la fin du précédent
        im.put(KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_BACK_SPACE,
                java.awt.event.KeyEvent.CTRL_DOWN_MASK | java.awt.event.KeyEvent.SHIFT_DOWN_MASK),
              "bw-delete-paragraph-backward");
        am.put("bw-delete-paragraph-backward",
              new writer.ui.editor.DeleteParagraphBackwardAction(this.editorPane));
        
        // Récupère l’action Backspace par défaut (supprime le caractère précédent)
        Action defaultBackspace = this.editorPane.getActionMap().get(DefaultEditorKit.deletePrevCharAction);

        // Remappe BACK_SPACE vers notre action intelligente
        this.editorPane.getInputMap().put(KeyStroke.getKeyStroke("BACK_SPACE"), "bw-smart-backspace");
        this.editorPane.getActionMap().put("bw-smart-backspace",
            new writer.ui.editor.SmartBackspaceAction(editorPane, defaultBackspace));
        
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
        
        // Navigation paragraphe par paragraphe sur ↑ / ↓
        writer.ui.editor.ParagraphNavigator.install(this.editorPane);
        
    	// --- Setup de la frame ---
    	setupEditorPane();
    	
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
  	    this.editorPane.setText(Texte);
  	    commandes.nameFile = commandes.nodeblindWriter.getAttributs().get("filename");
  	    this.editorPane.setCaretPosition(0);

  	    // marquer le doc comme "modifié" à la moindre modification utilisateur
	  	  this.editorPane.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
	  	    @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
	  	        setModified(true);
	  	        SwingUtilities.invokeLater(() -> TextHighlighter.apply(editorPane));
	  	    }
	  	    @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
	  	        setModified(true);
	  	        SwingUtilities.invokeLater(() -> TextHighlighter.apply(editorPane));
	  	    }
	  	    @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {
	  	        setModified(true);
	  	    }
	  	});




  	   // --- AJOUTE LES CLASS QUI PERMETTENT LES LISTES PUCES OU NULEROTES ---
  	    this.editorPane.getAccessibleContext().setAccessibleName("Zone de texte.");
  	    ((AbstractDocument) editorPane.getDocument()).setDocumentFilter(new AutoListContinuationFilter(this.editorPane));
  	
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