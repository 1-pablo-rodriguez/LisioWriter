package writer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Utilities;

import dia.BoiteNewDocument;
import dia.BoiteNonVoyant;
import dia.BoiteQuitter;
import dia.BoiteRenameFile;
import dia.BoiteSaveAs;
import dia.BoiteVersionNV;
import dia.HtmlBrowserDialog;
import dia.VerifDialog;
import dia.boiteMeta;
import dia.navigateurT1;
import dia.ouvrirTxt;
import exportODF.MarkdownOdfExporter;
import exportPDF.PdfExporter;
import exporterHTML.HtmlExporter;
import maj.AutoUpdater;
import maj.UpdateDialog;


public class blindWriter extends JFrame {

    private static final long serialVersionUID = 1L;
 // état "modifié" (non sauvegardé)
    private static boolean isModified = false;
    public static JTextArea editorPane;
    public static int positionCurseurSauv = 0;
    public static Affiche affichage = null;
    public static JScrollPane scrollPane = new JScrollPane();
    public static blindWriter instance ;
    public static boolean isDispose = true;
    private static JMenuItem dernierMenuUtilise = null;
    public static JMenu cachedMenuPages = null;
    // Historique d'annulation/refaçon
    private final static javax.swing.undo.UndoManager undoManager = new javax.swing.undo.UndoManager();
    @SuppressWarnings("serial")
	private final static Action undoAction = new AbstractAction("Annuler") {
        @Override public void actionPerformed(ActionEvent e) {
            try {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                  //announceCaretLine(true, true, "Annulé");
                }
            } finally { updateUndoRedoState(); }
        }
    };
    @SuppressWarnings("serial")
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
    
	// --- Announce SR/Braille (1px, focussable) ---
	private static final SRAnnouncerArea srAnnounce = new SRAnnouncerArea();
	private static javax.swing.Timer srTimer;
	private static java.awt.KeyEventDispatcher srDismissDispatcher;
	private static volatile boolean srActive = false;

	// --- Zoom éditeur ---
	private static String EDITOR_FONT_FAMILY = "Arial";
	private static int    EDITOR_FONT_SIZE   = 42;
	private static final int FONT_MIN = 16, FONT_MAX = 120, FONT_STEP = 2;
	
	// --- Taille de police des menus (globale) ---
	private static String MENU_FONT_FAMILY = "Segoe UI";
	private static int    MENU_FONT_SIZE   = 24;   // mets la taille que tu veux (ex : 22)

	// Optionnel : bornes si tu fais des raccourcis de zoom
	private static final int MENU_FONT_MIN = 18, MENU_FONT_MAX = 38, MENU_FONT_STEP = 2;
	
	// Instance globale (après création de editorPane)
	public static writer.bookmark.BookmarkManager bookmarks;

 	// Icone :
 	ImageIcon appIcon = loadIcon("/blindWriter.png");
 	
 	// correcteur
 	private static writer.spell.SpellCheckLT spell;

    public enum Affiche {
        TEXTE,
        DOCUMENTATION,
        MANUEL;
    }
    
    static {
  	  // Forcer les implémentations JDK pour JAXP
  	  System.setProperty("javax.xml.parsers.SAXParserFactory",
  	      "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");
  	  System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
  	      "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
  	  System.setProperty("javax.xml.transform.TransformerFactory",
  	      "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
  	}
    
    public blindWriter() {
    	init();
    	new bienvenueAffichage();	
    }

    @SuppressWarnings("serial")
	private void init() {
 
    	affichage=Affiche.TEXTE;
    	
        // Initialiser la fenêtre principale
    	setTitle("blindWriter");
        editorPane = new JTextArea();

        // Configurer la fermeture de la fenêtre
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1104, 623);

        // fenêtre qui n'est pas redimensionnable
        setResizable(true);
        
        // Icone de la fenêtre
		 setIconImage(appIcon.getImage());
        
        // Passer en mode plein écran
        setFullScreenMode();

        // Assurer que le JTextArea a le focus quand la fenêtre est visible
        SwingUtilities.invokeLater(editorPane::requestFocusInWindow);
        
        // Configurer le JTextArea
        setupEditorPane();
        
        try {   
            blindWriter.spell = writer.spell.SpellCheckLT.attach(editorPane);
            blindWriter.spell.setRealtime(commandes.verificationOrthoGr);   // ← démarre sans vérif pendant la frappe
        } catch (Throwable t) {
            t.printStackTrace(); // si un JAR manque, on ne casse pas l'appli
        }
        
        editorPane.addMouseWheelListener(e -> {
            if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                if (e.getWheelRotation() < 0) zoomIn(); else zoomOut();
                e.consume();
            }
        });
        
        // Ajouter une barre de défilement
        scrollPane = new JScrollPane(editorPane);
        //frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        
        // Configurer les raccourcis clavier
        configureKeyboardShortcuts();
        
        // Créer et ajouter la barre de menu
        setJMenuBar(createMenuBar());
        applyMenuFontTree(getJMenuBar());
        
        
        // Échap : si un menu est ouvert → on le ferme, sinon on laisse ton ESC habituel
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeMenusOrStop");

        getRootPane().getActionMap().put("closeMenusOrStop", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
                if (path != null && path.length > 0) {
                    // Ferme tous les menus ouverts et rend le focus à l’éditeur
                    MenuSelectionManager.defaultManager().clearSelectedPath();
                    editorPane.requestFocusInWindow();
                } else {
                    // Pas de menu ouvert : déclencher ton action ESC existante si tu en as une
                    Action a = editorPane.getActionMap().get("echappe"); // tu l'as déjà déclarée
                    if (a != null) a.actionPerformed(
                        new ActionEvent(editorPane, ActionEvent.ACTION_PERFORMED, "esc"));
                }
            }
        });
        
        // Définir la couleur du texte (blanc)
        editorPane.setForeground(Color.WHITE);
        
        // Définir la couleur de fond (noir)
        editorPane.setBackground(new Color(45, 45, 45));
        
        // surlignage du paragraphe actif
        ParagraphHighlighter.install(editorPane);
        
        // Caret rouge clair, épaisseur 2 px, clignote toutes les 500 ms
        CaretStyler.install(editorPane, new Color(255, 120, 120), 2, 500);
        
        // Shift + Flèche droite : sélectionner/étendre par mot
        WordSelectOnShiftRight.install(editorPane);

        // Announcer SR (1px, focussable)
        srAnnounce.setFocusable(true);
        srAnnounce.setOpaque(false);
        srAnnounce.setBorder(null);
        srAnnounce.setPreferredSize(new java.awt.Dimension(1,1));
        getContentPane().add(srAnnounce, BorderLayout.NORTH);
        
        
        // Afficher la fenêtre
        setVisible(true);
        
        // initialiser le titre (au démarrage)
        updateWindowTitle();
        
        // Garde une marge autour du caret à chaque mouvement
        editorPane.addCaretListener(ev ->
            SwingUtilities.invokeLater(() -> ensureCaretHorizontalMargins(108, 108)) // 48 px gauche/droite
        );

        
        // fermeture avec l'icon de la fenêtre
		addWindowListener(new java.awt.event.WindowAdapter() {
		    @Override
		    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
		    	 new BoiteQuitter();
		    }
		    @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                // Donner le focus à la JList dès que la fenêtre est visible
                editorPane.requestFocusInWindow();
            }
		});
		
		// Création des marques pages
		bookmarks = new writer.bookmark.BookmarkManager(editorPane);

		
    }

    private void setFullScreenMode() {
       // Maximiser la fenêtre pour qu'elle occupe tout l'écran sans cacher la barre des tâches
       setExtendedState(JFrame.MAXIMIZED_BOTH);
    }
    
    // Configuration d'editorPane
 	private void setupEditorPane() {
 		// Autorise ou n'autorise pas les retour à la ligne
 	    editorPane.setLineWrap(true);
 	    // Autorise les retour à la ligne mais ne coupe pas les mots
 	    editorPane.setWrapStyleWord(true);
 	    
 	    //editorPane.setFont(new Font("Arial", Font.PLAIN, 34));
 	    applyEditorFont();
 	    String Texte = commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenuAvecTousLesContenusDesEnfants();
 	    Texte = "";
 	    editorPane.setText(Texte);
 	    commandes.nameFile = commandes.nodeblindWriter.getAttributs().get("filename");
 	    editorPane.setCaretPosition(0);

 	    editorPane.getDocument().addUndoableEditListener(e -> {
 	        undoManager.addEdit(e.getEdit());
 	        updateUndoRedoState();
 	    });
 	    undoManager.setLimit(10000);
 	    // marquer le doc comme "modifié" à la moindre modification utilisateur
 	   editorPane.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
 	       @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { setModified(true); }
 	       @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { setModified(true); }
 	       @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { setModified(true); }
 	   });


 	    addUndoRedoShortcuts();

 	    editorPane.getAccessibleContext().setAccessibleName("Zone de texte du traitement de texte.");
 	    ((AbstractDocument) editorPane.getDocument()).setDocumentFilter(new AutoListContinuationFilter(editorPane));

 	    // 🔵 Garde le caret (le mot courant) visible avec une marge
 	    editorPane.addCaretListener(ev -> {
 	        // Petit defer pour laisser Swing finir le déplacement de caret
 	        SwingUtilities.invokeLater(() -> ensureWordVisibleWithMargin(96, 32)); // 48 px à gauche/droite, 8 px en vertical
 	    });
 	}
    
 	@SuppressWarnings("serial")
	final static Action actCheckDoc = new AbstractAction("Vérifier tout le document") {
 		  @Override public void actionPerformed(ActionEvent e) {
 		    if (spell == null) return;
 		    java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
 		    dia.VerifDialog dlg = VerifDialog.showNow(owner, "Vérif. doc. en cours…");
 		    
 		    //announceCaretLine(false, true, "Vérif. document en cours.");
 		    spell.checkDocumentNowAsync(() -> {
 		      //announceCaretLine(false, true,"Vérif. document terminée. " + spell.getMatchesCount() +" éléments détectés. F7 et Maj+F7 pour naviguer.");
 		    	dlg.close();
 		    	
 		    	int n = spell.getMatchesCount();
 		       String msg = (n == 0) ? "Aucune faute détectée."
 		                  : (n == 1) ? "1 élément détecté."
 		                             : n + " éléments détectés.";
 		       msg += "\n Les éléments sont signalés par le préfix °°.";
 		       msg += "\n Astuce : F7 et Maj+F7 pour naviguer entre les éléments.";
 		       msg += "\n Astuce : Maj+F10 contextuel pour : ";
 		       msg += "\n  (1) Obtenir une ou des suggestions ;";
 		       msg += "\n  (2) Ajouter au dictionnaire ou ignoré ;";
 		       msg += "\n  (3) Échappe pour sortir du menu contextuel.";
 		       
 		       // Boîte modale, lisible par la barre braille, fermeture avec Échap
 		       dia.InfoDialog.show(owner, "Vérification document terminée", msg);
 		       
 		    	editorPane.requestFocusInWindow();
 		    });
 		  }
 		};

 	@SuppressWarnings("serial")
	final static Action actCheckWindow = new AbstractAction("Vérifier la sélection / le paragraphe") {
 		  @Override public void actionPerformed(ActionEvent e) {
 		    if (spell == null) return;
 		   java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
 		    dia.VerifDialog dlg = VerifDialog.showNow(owner, "Vérif. para. en cours…");
 		    //announceCaretLine(false, true, "Vérif. paragraphe. Utilisez F7 et Maj+F7 pour naviguer.");
 		    spell.checkSelectionOrParagraphNowAsync(() -> {
 		      //announceCaretLine(false, true,"Vérif. paragraphe terminée. " + spell.getMatchesCount() +" éléments détectés. F7 et Maj+F7 pour naviguer.");
 		    	dlg.close();
 		    	
 		    	int n = spell.getMatchesCount();
 		    	String msg = (n == 0) ? "Dans le paragraphe aucune faute détectée."
		                  : (n == 1) ? "Dans le paragraphe 1 élément détecté."
		                             : "Dans le paragraphe " + n + " éléments détectés.";
		       msg += "\n Les éléments sont signalés par le préfix °°.";
		       msg += "\n Astuce : F7 et Maj+F7 pour naviguer entre les éléments.";
		       msg += "\n Astuce : Maj+F10 contextuel pour : ";
		       msg += "\n  (1) Obtenir une ou des suggestions ;";
		       msg += "\n  (2) Ajouter au dictionnaire ou ignoré ;";
		       msg += "\n  (3) Échappe pour sortir du menu contextuel.";

 		       // Boîte modale, lisible par la barre braille, fermeture avec Échap
 		       dia.InfoDialog.show(owner, "Vérification paragraphe terminée", msg);
 		       
 		    	editorPane.requestFocusInWindow();
 		    });
 		  }
 		};
    
	@SuppressWarnings("serial")
	private void configureKeyboardShortcuts() {
        // Conserver uniquement les raccourcis spécifiques à l'éditeur, sans conflit avec le menu
        addKeyBinding(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK, "paragraphe", new act.textBody());
        addKeyBinding(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK, "T1", new act.T1());
        addKeyBinding(KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK, "T2", new act.T2());
        addKeyBinding(KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK, "T3", new act.T3());
        addKeyBinding(KeyEvent.VK_4, InputEvent.CTRL_DOWN_MASK, "T4", new act.T4());
        addKeyBinding(KeyEvent.VK_5, InputEvent.CTRL_DOWN_MASK, "T5", new act.T5());
        addKeyBinding(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK, "gras", new act.Gras());
        addKeyBinding(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK, "italique", new act.Italique());
        addKeyBinding(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK, "versHaut", new act.VersHaut());
        addKeyBinding(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK, "versBas", new act.VersBas());
        addKeyBinding(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK, "versDroite", new act.VersDroite());
        addKeyBinding(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK, "versGauche", new act.VersGauche());
        addKeyBinding(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK, "sautPage", new act.SautPage());
        addKeyBinding(KeyEvent.VK_SPACE, InputEvent.ALT_DOWN_MASK, "sautPageSEP", new act.SautPageSEP());
        addKeyBinding(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, "citation", new act.citation());
        addKeyBinding(KeyEvent.VK_F6, 0, "navigateurT1", new act.ouvrirNavigateurT1());
        addKeyBinding(KeyEvent.VK_F1, 0, "Informations", new act.informations());
        addKeyBinding(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK, "rechercher", new act.openSearchDialog());
        
        Action puceAction = new InsertUnorderedBulletAction();
        // US : Ctrl+Shift+.
        addKeyBinding(KeyEvent.VK_PERIOD,InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,"puceTexte.us",puceAction);

        // FR (AZERTY) : Ctrl+Shift+.  => physiquement Ctrl+Shift+; (car '.' = Shift+;)
        addKeyBinding(KeyEvent.VK_SEMICOLON, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,"puceTexte.fr",puceAction);

        // Pavé numérique : Ctrl+Decimal
        addKeyBinding(KeyEvent.VK_DECIMAL,InputEvent.CTRL_DOWN_MASK,"puceTexte.numPad",puceAction);

        // Bonus robuste (alternative facile à dire) : Ctrl+Shift+L
        addKeyBinding(KeyEvent.VK_L,InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,"puceTexte.alt", puceAction);
    
        // activation ou désactivation de l'audio TTS
        addKeyBinding(KeyEvent.VK_A,InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,"toggleAudio",new ToggleAudioAction());   
       
        // Bloque ou active la modification editorpane
        addKeyBinding(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK, "toggleEdit", new ToggleEditAction());
        
        // ——— Raccourci F2 : annonce les titres autour du caret ———
        addKeyBinding(KeyEvent.VK_F2, 0, "announceHeadingsAround", actAnnouncePosition);
        // ——— Raccourci F3 : Pour aller sur le titre suivant ———
        addKeyBinding(KeyEvent.VK_F3, 0, "gotoNextHeading", actGotoNextHeading);
        // ——— Raccourci SHIFT+F3 : Pour aller sur le titre précédent ———
        addKeyBinding(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK, "gotoPrevHeading", actGotoPrevHeading);

        
        // Zoom clavier
        addKeyBinding(KeyEvent.VK_EQUALS,  InputEvent.CTRL_DOWN_MASK, "zoomInEq",
        	    new AbstractAction(){ public void actionPerformed(ActionEvent e){ zoomIn(); }});
    	addKeyBinding(KeyEvent.VK_ADD,     InputEvent.CTRL_DOWN_MASK, "zoomInNP",
    	    new AbstractAction(){ public void actionPerformed(ActionEvent e){ zoomIn(); }});
    	addKeyBinding(KeyEvent.VK_MINUS,   InputEvent.CTRL_DOWN_MASK, "zoomOut",
    	    new AbstractAction(){ public void actionPerformed(ActionEvent e){ zoomOut(); }});
    	addKeyBinding(KeyEvent.VK_SUBTRACT,InputEvent.CTRL_DOWN_MASK, "zoomOutNP",
    	    new AbstractAction(){ public void actionPerformed(ActionEvent e){ zoomOut(); }});
    	addKeyBinding(KeyEvent.VK_0,       InputEvent.CTRL_DOWN_MASK, "zoomReset",
    	    new AbstractAction(){ public void actionPerformed(ActionEvent e){ zoomReset(); }});
    	
    	// Actifs seulement quand le caret est dans l'éditeur
    	editorPane.getInputMap(JComponent.WHEN_FOCUSED).put(
    	    KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.CTRL_DOWN_MASK),
    	    "actCheckDoc");
    	editorPane.getActionMap().put("actCheckDoc", actCheckDoc);

    	editorPane.getInputMap(JComponent.WHEN_FOCUSED).put(
    	    KeyStroke.getKeyStroke(KeyEvent.VK_F7,
    	        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
    	    "actCheckWindow");
    	editorPane.getActionMap().put("actCheckWindow", actCheckWindow);
    	
    	// Ctrl+F2 : toggle marque-page sur la ligne
    	addKeyBinding(KeyEvent.VK_F2, InputEvent.CTRL_DOWN_MASK, "bmToggle", new AbstractAction() {
    	    @Override public void actionPerformed(ActionEvent e) {
    	    	var m = bm();
    	        if (m == null) { java.awt.Toolkit.getDefaultToolkit().beep(); return; }
    	        boolean added = bookmarks.toggleHere();
    	        if (added) {
    	        	// Boîte modale, lisible par la barre braille, fermeture avec Échap
    	 		    //dia.InfoDialog.show(javax.swing.SwingUtilities.getWindowAncestor(editorPane), "Marque-page", "Marque-page ajouté.");
    	        	 m.editNoteForNearest(javax.swing.SwingUtilities.getWindowAncestor(editorPane));
    	        }else {
    	        	dia.InfoDialog.show(javax.swing.SwingUtilities.getWindowAncestor(editorPane), "Marque-page", "Marque-page supprimé.");
    	        }
    	    }
    	});

    	// F4 : suivant ; Shift+F4 : précédent
    	addKeyBinding(KeyEvent.VK_F4, 0, "bmNext", new AbstractAction() {
    	    @Override public void actionPerformed(ActionEvent e) {
    	    	var m = bm();
    	        if (m == null) { java.awt.Toolkit.getDefaultToolkit().beep(); return; }
    	        bookmarks.goNext();
    	    }
    	});
    	addKeyBinding(KeyEvent.VK_F4, InputEvent.SHIFT_DOWN_MASK, "bmPrev", new AbstractAction() {
    	    @Override public void actionPerformed(ActionEvent e) {
    	    	var m = bm();
    	        if (m == null) { java.awt.Toolkit.getDefaultToolkit().beep(); return; }
    	        bookmarks.goPrev();
    	    }
    	});
    	
    	// Ajoute une note au marque-page
    	addKeyBinding(KeyEvent.VK_F2, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK,
    		    "bmEditNote", new AbstractAction() {
    		        @Override public void actionPerformed(ActionEvent e) {
    		            var m = bm();
    		            if (m == null) { java.awt.Toolkit.getDefaultToolkit().beep(); return; }
    		            java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
    		            m.editNoteForNearest(owner);
    		        }
    		    }
    		);
    	
    	// Lien
    	addKeyBinding(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK, "openUrlAtCaret", new AbstractAction() {
    	    @Override public void actionPerformed(ActionEvent e) {
    	        String url = findUrlAtCaret();
    	        if (url == null) {
    	            Toolkit.getDefaultToolkit().beep();
    	            writer.blindWriter.announceCaretLine(false, true, "Aucun lien détecté sous le curseur.");
    	            return;
    	        }
    	        // ouvre le navigateur intégré
    	        SwingUtilities.invokeLater(() -> new HtmlBrowserDialog(blindWriter.getInstance()).navigateTo(url));
    	    }
    	});



    }
    
	// Annulation ou rétablir
    private void addUndoRedoShortcuts() {
        // Ctrl+Z → Annuler
        KeyStroke ksUndo = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
        editorPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksUndo, "Undo");
        editorPane.getActionMap().put("Undo", undoAction);

        // Ctrl+Y → Rétablir
        KeyStroke ksRedo = KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
        editorPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksRedo, "Redo");
        editorPane.getActionMap().put("Redo", redoAction);

        // Ctrl+Shift+Z → Rétablir (variante macOS / IDE)
        KeyStroke ksRedoAlt = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        editorPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksRedoAlt, "Redo");
    }

    
    
    @SuppressWarnings("serial")
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



    private boolean isMenuOpen() {
        // Vérifier si le frame a un menu visible
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        return path.length > 0;
    }

    //Création de la barre des menus
    public static JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menuFichier());
        menuBar.add(menuEdition());
        menuBar.add(menuNaviguer());
        menuBar.add(menuInsertion());
        menuBar.add(menuFormatageTexte());
        menuBar.add(menuAppliquerStyle());
        menuBar.add(menuStyles());
        menuBar.add(menuPages());
        menuBar.add(menuImport());
        menuBar.add(menuExporter());
        menuBar.add(menuSources());
        menuBar.add(menuDocumentation());
        menuBar.add(menuPreference());
        
        addItemChangeListener(menuFichier());
        
        return menuBar;
    }

    //Menu Fichier
    private static JMenu menuFichier() {
    	
    	JMenu fileMenu = new JMenu("Fichier");
        fileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        fileMenu.setMnemonic(KeyEvent.VK_F); // Utiliser ALT+F pour ouvrir le menu
        fileMenu.getAccessibleContext().setAccessibleName("Fichier");
        // Listener déclenché à l’ouverture du menu
        fileMenu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });

        
        JMenuItem createItem = createMenuItem("Nouveau", KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Nouveau"); // Debugger
            new BoiteNewDocument();
            setModified(false);
            updateWindowTitle();

        });

        JMenuItem openItem = createMenuItem("Ouvrir", KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Ouvrir"); // Debugger
            new dia.boiteOuvrir2();
            setModified(false);
            updateWindowTitle();
        });
        
        
        JMenuItem saveItem = createMenuItem("Enregistrer", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Enregistrer"); // Debugger
            if (spell != null) { spell.clearHighlights(); editorPane.requestFocusInWindow(); }
            new enregistre();
            setModified(false);
            updateWindowTitle();
            StringBuilder message = new StringBuilder(128);
            message.append("Fichier enregistré").append(" ↓");
            message.append("\n• Fichier : ").append(commandes.nameFile).append(".bwr ↓");
            message.append("\n•Dossier : ").append(commandes.nomDossierCourant).append(" ↓");
            dia.InfoDialog.show(javax.swing.SwingUtilities.getWindowAncestor(editorPane),"Information", message.toString());
        });
        
        JMenuItem saveAsItem = createMenuItem("Enregistrer sous", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK, e -> {
            System.out.println("Enregistrement sous"); // Debugger
            if (spell != null) { spell.clearHighlights(); editorPane.requestFocusInWindow(); }
            new BoiteSaveAs();
            setModified(false);
            updateWindowTitle();
            StringBuilder message = new StringBuilder(128);
            message.append("Fichier enregistré").append(" ↓");
            message.append("\n• Fichier : ").append(commandes.nameFile).append(".bwr ↓");
            message.append("\n•Dossier : ").append(commandes.nomDossierCourant).append(" ↓");
            dia.InfoDialog.show(javax.swing.SwingUtilities.getWindowAncestor(editorPane),"Information", message.toString());
        });
        
        JMenuItem renameItem = createMenuItem("Renommer le fichier", KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Renommer"); // Debugger
            if (spell != null) { spell.clearHighlights(); editorPane.requestFocusInWindow(); }
            new BoiteRenameFile();
            setModified(false);
            updateWindowTitle();
        });
        
        JMenuItem metaItem = createMenuItem("Meta-données", KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Menu Méta-données"); // Debugger
            if (spell != null) { spell.clearHighlights(); editorPane.requestFocusInWindow(); }
            new boiteMeta();
        });
      
        JMenuItem quitItem = createMenuItem("Quitter", KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Quitter"); // Debugger
            new dia.BoiteQuitter();
        });

        // Ajouter des ChangeListeners pour les JMenuItem
        addItemChangeListener(createItem);
        addItemChangeListener(openItem);
        addItemChangeListener(saveItem);
        addItemChangeListener(saveAsItem);
        addItemChangeListener(renameItem);
        addItemChangeListener(metaItem);
        addItemChangeListener(quitItem);
        
        fileMenu.add(createItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.add(renameItem);
        fileMenu.add(metaItem);
        fileMenu.add(quitItem);
        
        return fileMenu;
    }
    
    
    private static JMenu menuImport() {
    	JMenu fileMenu = new JMenu("Importer");
        fileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        fileMenu.setMnemonic(KeyEvent.VK_M); // Utiliser ALT+M pour ouvrir le menu
        fileMenu.getAccessibleContext().setAccessibleName("Importer");
        // Listener déclenché à l’ouverture du menu
        fileMenu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        
        JMenuItem open2Item = createMenuItem("Importer fichier LibreOffice Writer", KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Ouverture fichier .odt"); // Debugger
            new dia.ouvrirODT();
            setModified(false);
            updateWindowTitle();
        });
        
        JMenuItem openItem = createSimpleMenuItem("Importer fichier texte",e -> {
            System.out.println("Ouverture fichier .txt"); // Debugger
            SwingUtilities.invokeLater(() -> new ouvrirTxt());
            setModified(false);
            updateWindowTitle();
        });
       
        JMenuItem open3Item = createMenuItem("Importer fichier Microsoft Word", KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Ouverture fichier .docx"); // Debugger
            new dia.ouvrirDOCX();
            setModified(false);
            updateWindowTitle();
        });    
        
        JMenuItem importHtml = new JMenuItem("Importer HTML...");
        importHtml.addActionListener(e -> {
        	new dia.ouvrirHTML();
        	setModified(false);
            updateWindowTitle();
        }); 
        
        JMenuItem importHtmlBrower = new JMenuItem("Importer Web...");
        importHtmlBrower.addActionListener(e -> {
            JFrame owner = blindWriter.getInstance(); // s'assure que la fenêtre existe
            SwingUtilities.invokeLater(() -> new HtmlBrowserDialog(owner));
        });

       
        addItemChangeListener(open2Item);
        addItemChangeListener(openItem);
        addItemChangeListener(open3Item);
        addItemChangeListener(importHtml);
        addItemChangeListener(importHtmlBrower);
        
        fileMenu.add(open2Item);
        fileMenu.add(openItem);
        fileMenu.add(open3Item);
        fileMenu.add(importHtml);
        fileMenu.add(importHtmlBrower);
        
        return fileMenu;
    }
    
    
    private static JMenu menuExporter() {
    	JMenu fileMenu = new JMenu("Exporter");
        fileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        fileMenu.setMnemonic(KeyEvent.VK_X); // Utiliser ALT+x pour ouvrir le menu
        fileMenu.getAccessibleContext().setAccessibleName("Exporter");
        // Listener déclenché à l’ouverture du menu
        fileMenu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        
        JMenuItem exportItem = createSimpleMenuItem("Exporter en .ODT (Writer)", e -> {
            System.out.println("Export au format ODF Writer"); // Debugger
            //ExportODFWriter();
            try {
            	if (spell != null) { spell.clearHighlights(); editorPane.requestFocusInWindow(); }
            	java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
            	MarkdownOdfExporter.export(editorPane.getText(), new File(commandes.currentDirectory + "/" + commandes.nameFile+".odt"));
            	StringBuilder msg = new StringBuilder();
                msg.append("Info. Exportation terminé."
                		+ "\nFichier : " + commandes.nameFile+".odt"
                		+ "\nDossier : " + commandes.currentDirectory);
                dia.InfoDialog.show(owner, "Exportation", msg.toString());
            } catch (Exception e1) {
				e1.printStackTrace();
			}
        });
        
        
        JMenuItem exportPDFItem = createSimpleMenuItem("Exporter en .PDF", e -> {
            System.out.println("Export au format PDF"); // Debugger
			try {
				String html = PdfExporter.convertMarkupToHtml(editorPane.getText());
				
				// construire proprement le chemin de sortie
				Path outPath = Paths.get(commandes.currentDirectory.getPath(), commandes.nameFile + ".pdf").toAbsolutePath();
				String out = outPath.toString();
				System.out.println("EXPORT PATH (absolu) => " + out);
				try {
				    PdfExporter.htmlToPdf(html, out, null); // "C:\\Windows\\Fonts\\arial.ttf"
				    System.out.println("Après appel : exists=" + outPath.toFile().exists() + " size=" + (outPath.toFile().exists() ? outPath.toFile().length() : 0));
				} catch (Throwable t) {
				    System.err.println("Erreur lors de l'export HTML->PDF :");
				    t.printStackTrace();
				}
				
				java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
            	StringBuilder msg = new StringBuilder();
            	msg.append("Info. Exportation terminé."
                 		+ "\nFichier : " + commandes.nameFile+".pdf"
                 		+ "\nDossier : " + commandes.currentDirectory);
            	dia.InfoDialog.show(owner, "Exportation", msg.toString());
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
 
        });
        
        JMenuItem exportHTMLItem = createSimpleMenuItem("Exporter en .HTML", e -> {
            System.out.println("Export au format HTML"); // Debugger
            try {
                String html = PdfExporter.convertMarkupToHtml(editorPane.getText());

                // Construire le outPath proprement selon le type de commandes.currentDirectory
                Path outPath;
                if (commandes.currentDirectory instanceof java.io.File) {
                    File base = commandes.currentDirectory;
                    String safeName = HtmlExporter.sanitizeFileName(commandes.nameFile) + ".html";
                    outPath = base.toPath().resolve(safeName).toAbsolutePath();
                } else {
                    // si currentDirectory est une String
                    outPath = Paths.get(commandes.currentDirectory.toString(), HtmlExporter.sanitizeFileName(commandes.nameFile) + ".html").toAbsolutePath();
                }

                // Exporter et tenter d'ouvrir immédiatement
                Path written = HtmlExporter.exportHtml(html, outPath, false);

                // Message d'information pour l'utilisateur
                java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
                StringBuilder msg = new StringBuilder();
                msg.append("Info. Exportation terminée.")
                   .append("\nFichier : ").append(written.getFileName().toString())
                   .append("\nDossier : ").append(written.getParent() == null ? "<racine>" : written.getParent().toString());
                dia.InfoDialog.show(owner, "Exportation", msg.toString());

            } catch (Exception ex) {
                ex.printStackTrace();
                java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
                dia.InfoDialog.show(owner, "Erreur export HTML", "L'exportation a échoué : " + ex.getMessage());
            }
        });

       
        addItemChangeListener(exportItem);
        addItemChangeListener(exportPDFItem);
        addItemChangeListener(exportHTMLItem);
        
        fileMenu.add(exportItem);
        fileMenu.add(exportPDFItem);
        fileMenu.add(exportHTMLItem);
        
        return fileMenu;
        
    }
    
    
    // Menu édition
    private static JMenu menuEdition() {
    	JMenu editionMenu = new JMenu("Édition");
    	editionMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	editionMenu.setMnemonic(KeyEvent.VK_E); // Utiliser ALT+e pour ouvrir le menu
    	editionMenu.getAccessibleContext().setAccessibleName("Édition");
        // Listener déclenché à l’ouverture du menu
    	editionMenu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
    	
    	
    	JMenuItem undoItem = new JMenuItem(undoAction);
    	undoItem.setText("Annuler");
    	undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
    	
    	JMenuItem redoItem = new JMenuItem(redoAction);
    	redoItem.setText("Rétablir");
    	redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
    	    	
        
    	JMenuItem edition = createMenuItem("Edition activée/désactivée", KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK, e -> {
    		new ToggleEditAction();
        });

    	// MENUS (accélérateurs)
    	JMenuItem checkAll = new JMenuItem(actCheckDoc);
    	checkAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.CTRL_DOWN_MASK));

    	JMenuItem checkSel = new JMenuItem(actCheckWindow);
    	checkSel.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7,
    	        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));

		// Nettoyer les soulignements, °° et formatage
		JMenuItem clear = createSimpleMenuItem("RAZ vérification",
		e -> { if (spell != null) { spell.clearHighlights(); editorPane.requestFocusInWindow(); }});
		
		// Mode temps réel (ON/OFF)
		javax.swing.JCheckBoxMenuItem live = new javax.swing.JCheckBoxMenuItem("Vérification durant la frappe");
		live.setFont(new Font("Segoe UI", Font.PLAIN, 18));
		live.setSelected(spell != null && spell.isRealtime());
		live.addActionListener(e -> { 
			if (spell != null) spell.setRealtime(live.isSelected());
			commandes.verificationOrthoGr = live.isSelected();
		});
		
		JMenuItem suggestion = createSimpleMenuItem("Vérif. suggestion(s)", e -> {
		    spell.showPopupAtCaret();
		});

    	
    	// Ajouter des ChangeListeners pour les JMenuItem
        addItemChangeListener(undoItem);
        addItemChangeListener(redoItem);
        addItemChangeListener(edition);
        addItemChangeListener(checkAll);
        addItemChangeListener(checkSel);
        addItemChangeListener(clear);
        addItemChangeListener(live);
        addItemChangeListener(suggestion);

        editionMenu.add(undoItem);
        editionMenu.add(redoItem);
        editionMenu.addSeparator();
    	editionMenu.add(edition);
    	editionMenu.addSeparator();
    	editionMenu.add(checkAll);
    	editionMenu.add(checkSel);
    	editionMenu.add(clear);
    	editionMenu.add(live);
    	editionMenu.add(suggestion);

       	
    	return editionMenu;
    }
    
    
    private static JMenu menuNaviguer() {
    	JMenu naviguerMenu = new JMenu("Naviguer");
    	naviguerMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	naviguerMenu.setMnemonic(KeyEvent.VK_N); // Utiliser ALT+n pour ouvrir le menu
    	naviguerMenu.getAccessibleContext().setAccessibleName("Naviguer");
        // Listener déclenché à l’ouverture du menu
    	naviguerMenu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
    	
    	
    	JMenuItem navigateurItem = createMenuItem("Navigateur", KeyEvent.VK_F6, 0, e -> {
    		if (spell != null) { spell.clearHighlights(); editorPane.requestFocusInWindow(); }
            new navigateurT1();
        });
    	
    	JMenuItem rechercher = createMenuItem("Rechercher texte", KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK, e -> {
    		if (spell != null) { spell.clearHighlights(); editorPane.requestFocusInWindow(); }
            new writer.openSearchDialog();
        });
    	
    	//-------------- Marque page ----------------
		
    			JMenuItem bmToggle = createSimpleMenuItem("Marque-page (basculer)", e -> {
    			    var m = bm();                      // récupère/initialise le manager
    			    if (m == null) {                   // pas d’editorPane dispo
    			        java.awt.Toolkit.getDefaultToolkit().beep();
    			        return;
    			    }
    			    boolean added = m.toggleHere();
    			    announceCaretLine(false, true, added ? "Marque-page ajouté." : "Marque-page supprimé.");
    			});
    			bmToggle.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, InputEvent.CTRL_DOWN_MASK));
    			bmToggle.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    			

    			JMenuItem bmNote = createSimpleMenuItem("Marque-page note", e -> { 
    				var m = bm();                      
    			    if (m == null) {                   
    			        java.awt.Toolkit.getDefaultToolkit().beep();
    			        return;
    			    }
    			    m.editNoteForNearest(javax.swing.SwingUtilities.getWindowAncestor(editorPane));
    				});
    			bmNote.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2,InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
    			bmNote.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    			
    			
    			JMenuItem bmNext = createSimpleMenuItem("Marque-page suivant", e -> { 
    				var m = bm();                      
    			    if (m == null) {                   
    			        java.awt.Toolkit.getDefaultToolkit().beep();
    			        return;
    			    }
    				bookmarks.goNext(); 
    				});
    			bmNext.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0));
    			bmNext.setFont(new Font("Segoe UI", Font.PLAIN, 18));

    			JMenuItem bmPrev = createSimpleMenuItem("Marque-page précédent", e -> { 
    				var m = bm();                      
    			    if (m == null) {                   
    			        java.awt.Toolkit.getDefaultToolkit().beep();
    			        return;
    			    }
    				bookmarks.goPrev(); 
    				});
    			bmPrev.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.SHIFT_DOWN_MASK));
    			bmPrev.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    			
    			// -------- Position dans texte (F2) --------
    			JMenuItem posItem = new JMenuItem(actAnnouncePosition);
    			posItem.setText("Titre avant & après");
    			posItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
    			posItem.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    			
    			// -------- Titre suivant (F3) --------
    			JMenuItem nextHeadingItem = new JMenuItem(actGotoNextHeading);
    			nextHeadingItem.setText("Titre suivant");
    			nextHeadingItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
    			nextHeadingItem.setFont(new Font("Segoe UI", Font.PLAIN, 18));

    			// -------- Titre précédent (Shift+F3) --------
    			JMenuItem prevHeadingItem = new JMenuItem(actGotoPrevHeading);
    			prevHeadingItem.setText("Titre précédent");
    			prevHeadingItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK));
    			prevHeadingItem.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	
    			addItemChangeListener(navigateurItem);
    			addItemChangeListener(rechercher);
    			addItemChangeListener(bmToggle);
    			addItemChangeListener(bmNote);
		        addItemChangeListener(bmNext);
		        addItemChangeListener(bmPrev);
		        addItemChangeListener(posItem);
		        addItemChangeListener(nextHeadingItem);
		        addItemChangeListener(prevHeadingItem);
		        
		        naviguerMenu.add(navigateurItem);
		        naviguerMenu.addSeparator();
		        naviguerMenu.add(rechercher);
		        naviguerMenu.addSeparator();
		        naviguerMenu.add(bmToggle);
		        naviguerMenu.add(bmNote);
		        naviguerMenu.add(bmNext);
		        naviguerMenu.add(bmPrev);
		        naviguerMenu.addSeparator();
		        naviguerMenu.add(posItem);
		        naviguerMenu.add(nextHeadingItem);
		        naviguerMenu.add(prevHeadingItem);

    	
    	return naviguerMenu;
 
    }
    
    //Menu Insertion
    private static JMenu menuInsertion() {
    	JMenu Insertion = new JMenu("Insertion");
    	Insertion.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	Insertion.setMnemonic('i'); // Utiliser ALT+N pour ouvrir le menu
    	Insertion.getAccessibleContext().setAccessibleName("Insertion");
    	// Listener déclenché à l’ouverture du menu
    	Insertion.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
    	
    	
    	JMenuItem sautPageItem = createMenuItem("Insérer un saut de page manuel", KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Insérer un saut de page manuel"); // Debugger
            new page.sautPage();
        });
         
        JMenuItem sautPage2Item = createMenuItem("Insérer un saut de page sE&P", KeyEvent.VK_SPACE, InputEvent.ALT_DOWN_MASK, e -> {
            System.out.println("Insérer un saut de page sans Entête et Pied de page"); // Debugger
            new page.sautPageSEP();
        });
        
        JMenuItem citationBasdePageItem = createMenuItem("Insérer une note de bas de page", KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Insertion note de bas de page"); // Debugger
            new writer.noteBasPage();
        });
   	 	
	   	JMenuItem TOCItem = createMenuItem("Insérer une table des matières", KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK, e -> {
	         System.out.println("Insertion table des matière TOC"); // Debugger
	         new writer.tableMatieres();
	    });
	   	
        JMenuItem insertionTitreItem = createMenuItem("Insérer le champ titre.", 0, 0, e -> {
            System.out.println("Insérer le champ titre"); // Debugger
            new writer.champTitre();
        });
        
        JMenuItem insertionSujetItem = createMenuItem("Insérer le champ sujet.", 0, 0, e -> {
            System.out.println("Insérer le champ sujet"); // Debugger
            new writer.champSujet();
        });
        
        JMenuItem insertionAuteurItem = createMenuItem("Insérer le champ auteur.", 0, 0, e -> {
            System.out.println("Insérer le champ auteur"); // Debugger
            new writer.champAuteur();
        });
        
        JMenuItem insertionCoAuteurItem = createMenuItem("Insérer le champ coauteur.", 0, 0, e -> {
            System.out.println("Insérer le champ coauteur"); // Debugger
            new writer.champCoAuteur();
        });
        
        JMenuItem insertionSocietyItem = createMenuItem("Insérer le champ société.", 0, 0, e -> {
            System.out.println("Insérer le champ société"); // Debugger
            new writer.champSociety();
        });
        
        JMenuItem insertionDateModificationItem = createMenuItem("Insérer le champ date.", 0, 0, e -> {
            System.out.println("Insérer le champ date"); // Debugger
            new writer.champDateModification();
        });
                
        // Ajouter des ChangeListeners pour les JMenuItem
        addItemChangeListener(sautPageItem);
        addItemChangeListener(sautPage2Item);
        addItemChangeListener(citationBasdePageItem);
        addItemChangeListener(TOCItem);
        addItemChangeListener(insertionTitreItem);
        addItemChangeListener(insertionSujetItem);
        addItemChangeListener(insertionAuteurItem);
        addItemChangeListener(insertionCoAuteurItem);
        addItemChangeListener(insertionSocietyItem);
        addItemChangeListener(insertionDateModificationItem);
        
        Insertion.add(sautPageItem);
        Insertion.add(sautPage2Item);
        Insertion.add(citationBasdePageItem);
        Insertion.add(TOCItem);
        Insertion.add(insertionTitreItem);
        Insertion.add(insertionSujetItem);
        Insertion.add(insertionAuteurItem);
        Insertion.add(insertionCoAuteurItem);
        Insertion.add(insertionSocietyItem);
        Insertion.add(insertionDateModificationItem);
        
        return Insertion; 
    }
    
    //Menu Style
    private static JMenu menuStyles() {
    	JMenu fileStyles = new JMenu("Modifier les styles");
        fileStyles.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        fileStyles.setMnemonic(KeyEvent.VK_L); // Utiliser ALT+L pour ouvrir le menu
     // Listener déclenché à l’ouverture du menu
        fileStyles.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        }); 
        
        
        JMenuItem bodyTextItem = createSimpleMenuItem("Modif. Corps de texte", e -> {
        	 System.out.println("Corpps de texte"); // Debugger 
        });        
        
        JMenuItem tritre1Item = createSimpleMenuItem("Modif. Titre 1", e -> {
            System.out.println("Titre 1"); // Debugger
           
        });
        
        JMenuItem tritre2Item = createSimpleMenuItem("Modif. Titre 2", e -> {
            System.out.println("Titre 2"); // Debugger
           
        });
        
        JMenuItem tritre3Item = createSimpleMenuItem("Modif. Titre 3", e -> {
            System.out.println("Titre 3"); // Debugger
           
        });
        
        JMenuItem tritre4Item = createSimpleMenuItem("Modif. Titre 4", e -> {
            System.out.println("Titre 4"); // Debugger
           
        });
        
        JMenuItem tritre5Item = createSimpleMenuItem("Modif. Titre 5", e -> {
            System.out.println("Titre 5"); // Debugger
           
        });
        
        JMenuItem tritrePrincipaleItem = createSimpleMenuItem("Modif. Titre principale", e -> {
            System.out.println("Titre principale"); // Debugger
           
        });
         
        JMenuItem sousTitreItem = createSimpleMenuItem("Modif. Sous titre principale", e -> {
            System.out.println("Sous titre principale"); // Debugger
           
        });
         
        // Ajouter des ChangeListeners pour les JMenuItem
        addItemChangeListener(bodyTextItem);
        addItemChangeListener(tritrePrincipaleItem);
        addItemChangeListener(sousTitreItem);
        addItemChangeListener(tritre1Item);
        addItemChangeListener(tritre2Item);
        addItemChangeListener(tritre3Item);
        addItemChangeListener(tritre4Item);
        addItemChangeListener(tritre5Item);
        
        fileStyles.add(bodyTextItem);
        fileStyles.add(tritrePrincipaleItem);
        fileStyles.add(sousTitreItem);
        fileStyles.add(tritre1Item);
        fileStyles.add(tritre2Item);
        fileStyles.add(tritre3Item);
        fileStyles.add(tritre4Item);
        fileStyles.add(tritre5Item);
        
        return fileStyles;
    }
   
   
    //Menu Pages
    public static JMenu menuPages() {
    	
    	if (cachedMenuPages != null) return cachedMenuPages;
    	
    	JMenu filePage    = new JMenu("Pages");
    	  	
    	filePage.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	filePage.setMnemonic(KeyEvent.VK_P); // Utiliser ALT+P pour ouvrir le menu
    	filePage.getAccessibleContext().setAccessibleName("Pages"); 
    	// Listener déclenché à l’ouverture du menu
    	filePage.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        }); 
    	
    	JCheckBoxMenuItem checkBoxActivePageTitre= new JCheckBoxMenuItem("Première page couverture");
    	checkBoxActivePageTitre.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	 if(Boolean.valueOf( commandes.pageTitre.getAttributs().get("couverture"))) {
        	checkBoxActivePageTitre.setSelected(true);
        	checkBoxActivePageTitre.getAccessibleContext().setAccessibleName("Case cochée : page de couverture.");
        }else {
        	checkBoxActivePageTitre.setSelected(false);
        	checkBoxActivePageTitre.getAccessibleContext().setAccessibleDescription("Case décochée : pas de page de couverture.");
        }
     // Ajouter un écouteur d'événement pour détecter les changements
        checkBoxActivePageTitre.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (checkBoxActivePageTitre.isSelected()) {
                	commandes.pageTitre.getAttributs().put("couverture", "true");
                	checkBoxActivePageTitre.getAccessibleContext().setAccessibleDescription("Case cochée : page de couverture.");
                	
                } else {
                	commandes.pageTitre.getAttributs().put("couverture", "false");
                	checkBoxActivePageTitre.getAccessibleContext().setAccessibleDescription("Case décochée : pas de page de couverture.");
                		
                }
                readFileBlindWriter.MAJMenuPage();
                blindWriter.addItemChangeListener(checkBoxActivePageTitre);
            }
        });
    	
    	
     // Créer un JCheckBoxMenuItem
        JCheckBoxMenuItem checkBoxActiveEntete= new JCheckBoxMenuItem("Afficher dans l'entête le titre");
        checkBoxActiveEntete.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        if(Boolean.valueOf(commandes.pageDefaut.getAttributs().get("entete"))) {
        	checkBoxActiveEntete.setSelected(true);
        	 checkBoxActiveEntete.getAccessibleContext().setAccessibleName("Case cochée : Afficher dans l'entête le titre");
        }else {
        	checkBoxActiveEntete.setSelected(false);
        	 checkBoxActiveEntete.getAccessibleContext().setAccessibleName("Case décochée : n'Afficher pas le titre dans l'entête");  
        }
        // Ajouter un écouteur d'événement pour détecter les changements
        checkBoxActiveEntete.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (checkBoxActiveEntete.isSelected()) {
              	  System.out.println("Active l'entête"); // Debugger
              	  commandes.pageDefaut.getAttributs().put("entete", "true");
                  checkBoxActiveEntete.getAccessibleContext().setAccessibleDescription("Afficher le titre dans l'entête");
                  
                } else {
              	  System.out.println("Désactive l'enête"); // Debugger
              	  commandes.pageDefaut.getAttributs().put("entete", "false");
              	  checkBoxActiveEntete.getAccessibleContext().setAccessibleDescription("Pas d'entête");
              	
                }
                readFileBlindWriter.MAJMenuPage();
                blindWriter.addItemChangeListener(checkBoxActiveEntete);
            }
        });
        
        // Créer un JCheckBoxMenuItem
        JCheckBoxMenuItem checkBoxActivePiedPage= new JCheckBoxMenuItem("Afficher le numéro de la page dans le pied de page");
        checkBoxActivePiedPage.setFont(new Font("Segoe UI", Font.PLAIN, 18));
         if(Boolean.valueOf(commandes.pageDefaut.getAttributs().get("piedpage"))) {
        	checkBoxActivePiedPage.setSelected(true);
            checkBoxActivePiedPage.getAccessibleContext().setAccessibleName("Case cochée : Afficher le numéro de la page dans le pied de page"); 
         }else {
        	checkBoxActivePiedPage.setSelected(false);
            checkBoxActivePiedPage.getAccessibleContext().setAccessibleName("Case décochée : n'Afficher pas le numéro de la page dans le pied de page");
         }
        // Ajouter un écouteur d'événement pour détecter les changements
        checkBoxActivePiedPage.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (checkBoxActivePiedPage.isSelected()) {
              	  System.out.println("Active le pied de page"); // Debugger
              	  commandes.pageDefaut.getAttributs().put("piedpage", "true");
              	  checkBoxActivePiedPage.getAccessibleContext().setAccessibleDescription("Affiche le numéro de page dans le pied de page.");
	              	        	  
              	 } else {
              	  System.out.println("Désactive le pied de page"); // Debugger
              	  commandes.pageDefaut.getAttributs().put("piedpage", "false");
              	  checkBoxActivePiedPage.getAccessibleContext().setAccessibleDescription("Pas de pied de page");
	              		 
                }
                readFileBlindWriter.MAJMenuPage();
                blindWriter.addItemChangeListener(checkBoxActivePiedPage);
            }
        });
        // Ajouter des ChangeListeners pour les JMenuItem
        addItemChangeListener(checkBoxActiveEntete);
        addItemChangeListener(checkBoxActivePiedPage);
        addItemChangeListener(checkBoxActivePageTitre);
        
        filePage.add(checkBoxActivePageTitre);
        filePage.add(checkBoxActiveEntete);
        filePage.add(checkBoxActivePiedPage);
       
        cachedMenuPages = filePage; // ✅ stocker pour la prochaine fois
        
        return cachedMenuPages;
    }
    
    //Menu Formatage Texte
    private static JMenu menuFormatageTexte() {
    	JMenu fileFormatage = new JMenu("Formatage local texte");
    	fileFormatage.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	fileFormatage.setMnemonic('m'); // Utiliser ALT+F pour ouvrir le menu
    	fileFormatage.getAccessibleContext().setAccessibleName("Formatage local texte");
    	fileFormatage.getAccessibleContext().setAccessibleDescription("Sélectionner votre texte, puis sélection un sous menu : Gras, Italic, Souligné");      
    	// Listener déclenché à l’ouverture du menu
    	fileFormatage.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                System.out.println("🔵 Menu formatage local ouvert !");
            }

            @Override
            public void menuDeselected(MenuEvent e) {}
            @Override
            public void menuCanceled(MenuEvent e) {}
        });
    	
	      JMenuItem souligneItem = createSimpleMenuItem("Souligner", e -> {
	            new formatage.Souligne();
	        });
	      souligneItem.getAccessibleContext().setAccessibleName("Souligner");
	      souligneItem.getAccessibleContext().setAccessibleDescription("Menu pour appliquer le style souligner au texte sélectionné.");

	      JMenuItem soulignegrasItem = createSimpleMenuItem("Souligner Gras",e -> {
	            new formatage.SoulignerGras();
	        });
	      soulignegrasItem.getAccessibleContext().setAccessibleName("Souligner gras");
	      soulignegrasItem.getAccessibleContext().setAccessibleDescription("Menu pour appliquer le style souligner gras au texte sélectionné.");
	      
	      JMenuItem souligneitalicItem = createSimpleMenuItem("Souligner Italique",e -> {
	            new formatage.SouligneItalic();
	        });
	      souligneitalicItem.getAccessibleContext().setAccessibleName("Souligner italique");
	      souligneitalicItem.getAccessibleContext().setAccessibleDescription("Menu pour appliquer le style souligner italique au texte sélectionné.");
	      
	      
    	  JMenuItem grasItem = createSimpleMenuItem("Gras", e -> {
              new formatage.Gras();
          });
    	  grasItem.getAccessibleContext().setAccessibleName("Menu pour appliquer le style Gras au texte sélectionné.");
    	  
    	  JMenuItem italicItem = createSimpleMenuItem("Italique", e -> {
              new formatage.Italique();
          });
    	  italicItem.getAccessibleContext().setAccessibleName("Italique");
    	  italicItem.getAccessibleContext().setAccessibleDescription("Menu pour appliquer le style italique au texte sélectionné.");
    	  
    	  JMenuItem grasitaliqueItem = createSimpleMenuItem("Gras Italique", e -> {
              new formatage.GrasItalique();
          });
    	  grasitaliqueItem.getAccessibleContext().setAccessibleName("Gras Italique");
    	  grasitaliqueItem.getAccessibleContext().setAccessibleDescription("Menu pour appliquer le style gras italique au texte sélectionné."); 
    	  
    	  JMenuItem exposantItem = createSimpleMenuItem("Exposant", e -> {
              new formatage.Exposant();
          });
    	  exposantItem.getAccessibleContext().setAccessibleName("Exposant");
    	  exposantItem.getAccessibleContext().setAccessibleDescription("Menu pour appliquer le style exposant au texte sélectionné."); 
    	  
    	  JMenuItem indiceItem = createSimpleMenuItem("Indice", e -> {
              new formatage.Indice();
          });
    	  indiceItem.getAccessibleContext().setAccessibleName("Indice");
    	  indiceItem.getAccessibleContext().setAccessibleDescription("Menu pour appliquer le style indice au texte sélectionné."); 
    	  
    	  
    	  // Ajouter des ChangeListeners pour les JMenuItem
    	  addItemChangeListener(souligneItem);
    	  addItemChangeListener(soulignegrasItem);
    	  addItemChangeListener(souligneitalicItem);
          addItemChangeListener(grasItem);
          addItemChangeListener(italicItem);  
          addItemChangeListener(grasitaliqueItem);
          addItemChangeListener(exposantItem);
          addItemChangeListener(indiceItem);
    	  
          fileFormatage.add(souligneItem);
          fileFormatage.add(soulignegrasItem);
          fileFormatage.add(souligneitalicItem);
    	  fileFormatage.add(grasItem);
    	  fileFormatage.add(italicItem);
    	  fileFormatage.add(grasitaliqueItem);
    	  fileFormatage.addSeparator();
    	  fileFormatage.add(exposantItem);
    	  fileFormatage.add(indiceItem);
    	
        return fileFormatage;
    }
    
    // Menu Appliquer le style
    private static JMenu menuAppliquerStyle() {
   	
    	JMenu fileAppliqueStyle = new JMenu("Appliquer un style");
    	fileAppliqueStyle.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	fileAppliqueStyle.setMnemonic(KeyEvent.VK_U); 
    	fileAppliqueStyle.getAccessibleContext().setAccessibleName("Menu principale : Appliquer un style");
    	// Listener déclenché à l’ouverture du menu
    	fileAppliqueStyle.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        }); 
    	
    	JMenuItem ctItem = createMenuItem("Corps de texte", KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique Corps de texte"); // Debugger
            new styles.bodyTexte();	
        });
    	ctItem.getAccessibleContext().setAccessibleName("Appliquer le style Corps de texte au paragraphe.");
    	
    	
    	JMenuItem t1Item = createMenuItem("Titre 1", KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre 1"); // Debugger
            new styles.titre1();
        });
    	
    	JMenuItem t2Item = createMenuItem("Titre 2", KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre 2"); // Debugger
            new styles.titre2();
        });
    	
    	JMenuItem t3Item = createMenuItem("Titre 3", KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre 3"); // Debugger
            new styles.titre3();
        });
    	 
    	JMenuItem t4Item = createMenuItem("Titre 4", KeyEvent.VK_4, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre 4"); // Debugger
            new styles.titre4();
        });
    	
    	JMenuItem t5Item = createMenuItem("Titre 5", KeyEvent.VK_5, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre 5"); // Debugger
            new styles.titre5();
        });
    	 
    	JMenuItem tPrinItem = createMenuItem("Titre principale", KeyEvent.VK_8, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre principale"); // Debugger
            new styles.titrePrincipale();
        });
    	 
    	JMenuItem sTitreItem = createMenuItem("Sous titre principale", KeyEvent.VK_9, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le Sous titre"); // Debugger
            new styles.sousTitre();
        });
    		
    	
    	JMenuItem sPuceItem = createMenuItem("Puce", KeyEvent.VK_7, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Puce"); // Debugger
            new styles.listeNonNumero();
        });
    	
    	// Ajouter des ChangeListeners pour les JMenuItem
        addItemChangeListener(ctItem);
        addItemChangeListener(tPrinItem);
        addItemChangeListener(sTitreItem);
        addItemChangeListener(t1Item);
        addItemChangeListener(t2Item);
        addItemChangeListener(t3Item);
        addItemChangeListener(t4Item);
        addItemChangeListener(t5Item);
        addItemChangeListener(sPuceItem);
        
    	fileAppliqueStyle.add(ctItem);
    	fileAppliqueStyle.add(tPrinItem);
    	fileAppliqueStyle.add(sTitreItem);
    	fileAppliqueStyle.add(t1Item);
    	fileAppliqueStyle.add(t2Item);
    	fileAppliqueStyle.add(t3Item);
    	fileAppliqueStyle.add(t4Item);
    	fileAppliqueStyle.add(t5Item);
    	fileAppliqueStyle.add(sPuceItem); 
	
    	return fileAppliqueStyle;
    }
    
    //Menu Sources
    private static JMenu menuSources() {
    	JMenu fileSources = new JMenu("Sources");
    	fileSources.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	fileSources.setMnemonic(KeyEvent.VK_S); 
    	fileSources.getAccessibleContext().setAccessibleName("Sources");
    	// Listener déclenché à l’ouverture du menu
    	fileSources.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        }); 
    	
    	return fileSources;
    }
    
    //Menu Documentation
    private static JMenu menuDocumentation() {
    	JMenu fileDocumentation = new JMenu("Documentation");
        fileDocumentation.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        fileDocumentation.setMnemonic('D'); // Utiliser ALT+F pour ouvrir le menu
     // Listener déclenché à l’ouverture du menu
        fileDocumentation.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
			@Override public void menuCanceled(MenuEvent e) {}
        }); 
        
        JMenuItem afficheDocItem = createMenuItem("Documentation blindWriter", KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK, e -> {
            sauvegardeTemporaire();
            affichage = Affiche.DOCUMENTATION;
            afficheDocumentation();
        });
        afficheDocItem.getAccessibleContext().setAccessibleName("Documentation blindWriter");
        
        JMenuItem afficheTextItem = createMenuItem("Votre texte", KeyEvent.VK_B, InputEvent.ALT_DOWN_MASK, e -> {
            affichage = Affiche.TEXTE;
            AfficheTexte();
        });
        afficheTextItem.getAccessibleContext().setAccessibleName("Basculer vers votre texte");
       
        JMenuItem afficheManuelItem = createMenuItem("Manuel b.book", KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK, e -> {
            sauvegardeTemporaire();
            affichage = Affiche.MANUEL;
            AfficheManuel();
        });
        afficheManuelItem.getAccessibleContext().setAccessibleName("Manuel b.book");
       
        // Ajouter des ChangeListeners pour les JMenuItem
        addItemChangeListener(afficheDocItem);
        addItemChangeListener(afficheTextItem);
        addItemChangeListener(afficheManuelItem);
        
        
        fileDocumentation.add(afficheDocItem);
        fileDocumentation.add(afficheTextItem);
        fileDocumentation.add(afficheManuelItem);
        
        return fileDocumentation;
    }
    
    
   
    //Menu préférence
    private static JMenu menuPreference() {
    	  JMenu filepreference = new JMenu("Préférences");
          filepreference.setFont(new Font("Segoe UI", Font.PLAIN, 18));
          filepreference.setMnemonic(KeyEvent.VK_R);
          //filepreference.setDisplayedMnemonicIndex(1); // 'r' est le 2e caractère de "Préférences"
          // Listener déclenché à l’ouverture du menu
          filepreference.addMenuListener(new MenuListener() {
              @Override public void menuSelected(MenuEvent e) {
                  // Laisse Swing ouvrir le menu, puis enlève l’item armé
                  SwingUtilities.invokeLater(() -> {
                      JMenu m = (JMenu) e.getSource();
                      MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                      MenuElement[] path = msm.getSelectedPath();

                      // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                      if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                          JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                          msm.setSelectedPath(new MenuElement[] {
                              bar, m, m.getPopupMenu()   // <-- aucun item armé
                          });
                      }
                  });
              }
              @Override public void menuDeselected(MenuEvent e) {}
              @Override public void menuCanceled(MenuEvent e) {}
          });
          
          JMenuItem voyant = createSimpleMenuItem("Non Voyant ?", e ->{
        	  BoiteNonVoyant.show(instance);
          });
          
          JMenuItem versionItem = createSimpleMenuItem("Version actuelle", e -> {
        	    // Récupérez la fenêtre parente (adapter editorPane si besoin)
        	    Window win = SwingUtilities.getWindowAncestor(editorPane);
        	    String version = getAppVersion();
        	    dia.BoiteVersionNV.show(win, "blindWriter " + version);
        	});
          
          JMenuItem majItem = createSimpleMenuItem("Mise à Jour", e -> {
        	  new SwingWorker<AutoUpdater.UpdateInfo, Void>() {
        	    private final AutoUpdater updater = new AutoUpdater("https://raw.githubusercontent.com/1-pablo-rodriguez/blindWriter/main/updates.json", getAppVersion());
        	    @Override protected AutoUpdater.UpdateInfo doInBackground() throws Exception {
        	        return updater.fetchMetadata();
        	    }
        	    @Override protected void done() {
        	        try {
        	            AutoUpdater.UpdateInfo info = get();
        	            if (info == null) {
        	            	 System.out.println("Impossible de vérifier les mises à jour.");
        	                return;
        	            }
        	            int cmp = AutoUpdater.versionCompare(info.version, updater.getCurrentVersion());
        	            if (cmp > 0) {
        	                UpdateDialog dlg = new UpdateDialog(getInstance(), updater, info);
        	                dlg.setVisible(true);
        	            } else if (cmp == 0) {
        	            	 System.out.println("Vous avez la dernière version installée : " + updater.getCurrentVersion());
        	            } else {
        	            	 System.out.println("Votre version (" + updater.getCurrentVersion() + ") est plus récente que celle du serveur (" + info.version + ").");
        	            }
        	        } catch (Exception ex) {
        	            ex.printStackTrace();
        	            System.out.println("Erreur lors de la vérification : " + ex.getMessage());
        	        }
        	    }
        	  }.execute();
        	});

          JMenu m = new JMenu("Affichage");
          m.setFont(new Font("Segoe UI", Font.PLAIN, 18));

          JMenuItem zi = new JMenuItem("Zoom avant");
          zi.addActionListener(e -> zoomIn());
          zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
          m.add(zi);

          JMenuItem zo = new JMenuItem("Zoom arrière");
          zo.addActionListener(e -> zoomOut());
          zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
          m.add(zo);

          JMenuItem zr = new JMenuItem("Réinitialiser le zoom");
          zr.addActionListener(e -> zoomReset());
          zr.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
          m.add(zr);
          
          JMenuItem zmIn = new JMenuItem("Taille des menus +");
          zmIn.addActionListener(e -> menuZoomIn());
          zmIn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
          m.add(zmIn);

          JMenuItem zmOut = new JMenuItem("Taille des menus -");
          zmOut.addActionListener(e -> menuZoomOut());
          zmOut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,  InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
          m.add(zmOut);

          JMenuItem zmReset = new JMenuItem("Réinitialiser taille menus");
          zmReset.addActionListener(e -> menuZoomReset());
          zmReset.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,     InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
          m.add(zmReset);
          
          // Ajouter des ChangeListeners pour les JMenuItem
          addItemChangeListener(m);
          addItemChangeListener(voyant);
          addItemChangeListener(majItem);
          addItemChangeListener(versionItem);
          
          filepreference.add(m);
          filepreference.add(voyant);
          filepreference.add(majItem);
          filepreference.add(versionItem);
    
          return filepreference;
    }
    
    
    // Création générique des menuItem
    private static JMenuItem createMenuItem(String text, int keyEvent, int modifier, ActionListener action) {
        JMenuItem menuItem = new JMenuItem(text);
        menuItem.setFont(new Font("Segoe UI", Font.PLAIN, 18));

        // Associer un raccourci clavier
       if(keyEvent != 0) menuItem.setAccelerator(KeyStroke.getKeyStroke(keyEvent, modifier));

        // Associer l'ActionListener
        menuItem.addActionListener(action);
        return menuItem;
    }
    
    // Surcharge simple (à ajouter une fois)
    private static JMenuItem createSimpleMenuItem(String text, ActionListener action) {
        JMenuItem mi = new JMenuItem(text);
        mi.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        mi.addActionListener(action);
        return mi;
    }
    
    // Obtient une instance de blindWriter
	public static blindWriter getInstance() {
        if (isDispose) {
            instance = new blindWriter();
            if(commandes.affichageDocumentationOuverture) afficheDocumentation();
        }
        instance.setVisible(true);
        instance.toFront();
        instance.requestFocus();
		editorPane.requestFocus();
        isDispose=false;
        return instance;
    }
 
    // Démarrage du logiciel
	public static void main(String[] args) {
	    // 1) Calculer le chemin passé par l’OS au double-clic
	    final String startupPath = (args != null && args.length > 0) ? args[0] : null;

	    try {
	        System.setProperty("file.encoding", "UTF-8");
	        commandes.init();

	        // 2) Lancer l'UI sur l'EDT
	        javax.swing.SwingUtilities.invokeLater(() -> {
	            @SuppressWarnings("unused")
				blindWriter app = blindWriter.getInstance(); // crée/affiche l’UI

	            // 3) Ouvrir le fichier si fourni
	            if (startupPath != null && !startupPath.isBlank()) {
	                openFileOnStartup(startupPath); // méthode static (voir ci-dessous)
	                // Création des marques pages
	            }
	        });
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}

   
    public static void openFileOnStartup(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.isFile()) {
                System.err.println("Fichier introuvable: " + path);
                // Optionnel: annoncer via TTS / barre braille
                return;
            }
            // Cette classe charge le .bwr, peuple commandes.*, et met le texte dans editorPane
            new readFileBlindWriter(f);
            // s'assurer qu'on ne marque pas comme modifié à l'ouverture
            setModified(false);
            // rafraîchir le titre pour afficher le nom du fichier ouvert
            updateWindowTitle();

            // Optionnel: mettre le focus dans l’éditeur + annoncer
            if (editorPane != null) {
                editorPane.requestFocusInWindow();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Affiche la documnetation dans blindWriter
    private static void afficheDocumentation() {
    	if(affichage == Affiche.DOCUMENTATION) {
        	editorPane.setText(commandes.nodeDocumentation.retourneFirstEnfant("contentText").getContenuAvecTousLesContenusDesEnfants());
        	editorPane.setCaretPosition(0);
        	commandes.nameFile = commandes.nodeDocumentation.getAttributs().get("filename");
        	announceCaretLine(false, true, "Affichage de la documentation.");
        	setModified(false);
        	updateWindowTitle();
    	}
    }
    
    //Affiche le texte
    private static void AfficheTexte() {
    	if(affichage == Affiche.TEXTE) {
    		commandes.nodeblindWriter = commandes.sauvFile;
        	if(commandes.nodeblindWriter.retourneFirstEnfant("contentText")!=null) {
        		editorPane.setText(commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenuAvecTousLesContenusDesEnfants());
        	}else {
        		editorPane.setText("");
        	}
         	commandes.nameFile = commandes.nodeblindWriter.getAttributs().get("filename");
        	editorPane.setCaretPosition(positionCurseurSauv);
        	editorPane.getAccessibleContext().setAccessibleName("Affichage de votre texte.");
        	setModified(false);
        	updateWindowTitle();
    	}
    }
 
    // Affichage du manuel du bbook
    private static void AfficheManuel() {
    	if(affichage == Affiche.MANUEL) {
        	editorPane.setText(commandes.manuel.retourneFirstEnfant("contentText").getContenuAvecTousLesContenusDesEnfants());
        	commandes.nameFile = commandes.manuel.getAttributs().get("filename");
        	editorPane.setCaretPosition(0);
        	editorPane.getAccessibleContext().setAccessibleName("Affichage du manuel b.bbok.");
        	setModified(false);
        	updateWindowTitle();
    	}
    }
 
    // Sauvegarde temporaire du texte
    private static void sauvegardeTemporaire() {
    	if(affichage == Affiche.TEXTE) {
    		positionCurseurSauv = editorPane.getCaretPosition();
    		commandes.sauvFile();
    	}
    }
    
    //Permet de positionnner le scroll verticale en fonction de la position du curseur.
    public static void placeCursorAtText(String searchText) {
       String text = editorPane.getText(); // Récupérer le texte de la JTextArea
        int index = text.indexOf(searchText); // Rechercher l'index de la chaîne "selectedT1"
        
        if (index != -1) {
            // Placer le curseur au début de "selectedT1"
        	editorPane.setCaretPosition(index);
        	
        	 // Faire défiler pour que le texte soit visible en haut de la zone visible
            try {
                // Récupérer les coordonnées du rectangle correspondant à la position du curseur avec modelToView2D
                Rectangle2D rect2D = editorPane.modelToView2D(editorPane.getCaretPosition());
                Rectangle rect = rect2D.getBounds();

                // Faire défiler pour que cette position soit visible en haut
                editorPane.scrollRectToVisible(new Rectangle(rect.x, rect.y, rect.width, scrollPane.getViewport().getHeight()));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        	
        } else {
        	System.out.println("Texte non trouvé : " + searchText );
        }
    }
    
    /** Fait défiler pour que le mot sous le caret soit visible avec une marge. */
    private static void ensureWordVisibleWithMargin(int hMarginPx, int vMarginPx) {
        if (editorPane == null || scrollPane == null) return;

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
    private static void expandAndScroll(Rectangle r, int hMarginPx, int vMarginPx) {
        if (r == null) return;

        // On crée un rect “coussin” pour éviter d’être collé au bord
        int x = Math.max(0, r.x - hMarginPx);
        int y = Math.max(0, r.y - vMarginPx);
        int w = r.width  + 2 * hMarginPx;
        int h = r.height + 2 * vMarginPx;

        Rectangle padded = new Rectangle(x, y, w, h);

        // Fait défiler la vue
        editorPane.scrollRectToVisible(padded);

        // Optionnel : si la marge est plus “intelligente” (ex. placer ~1/3 de la vue),
        // on peut ajuster avec le viewport :
        // JViewport vp = scrollPane.getViewport();
        // Rectangle view = vp.getViewRect();
        // ... (ajustements fins si besoin)
    }



    // Permet de lire les accesibleName des JMenuItem
    public static void addItemChangeListener(JMenuItem menuItem) {
        menuItem.addChangeListener(e -> {
            // Vérifier si le menu est actuellement armé (surligné)
            if (menuItem.isArmed()) {
                // Ne s'exécute que si le menu actuel est différent du dernier utilisé
                if (menuItem != dernierMenuUtilise) {
                	
                    String accessibleName = new TraitementSonPourTTS(menuItem.getAccessibleContext().getAccessibleName()).returnTexte;
                    if(menuItem.getAccessibleContext().getAccessibleDescription()!=null) {
                    	accessibleName = new TraitementSonPourTTS(accessibleName + " " + menuItem.getAccessibleContext().getAccessibleDescription()).returnTexte;
                    }
                    
                    
                    if (accessibleName != null) {
                        System.out.println(accessibleName);  // Affiche dans la console
                        

                        // Mettre à jour le dernier menu utilisé
                        dernierMenuUtilise = menuItem;
                    }
                }
            }
        });
    }
    
    
 // Auto-continuer les listes : "-. " inchangé, "n. " incrémenté
 // + renumérotation des paragraphes suivants quand on insère au milieu
 // + renumérotation des paragraphes suivants quand on SUPPRIME au milieu.
 // Compatibles Java 8+ (OK JRE 19).
 private static final class AutoListContinuationFilter extends DocumentFilter {
     private final JTextArea area;
     // Garde-fou pour éviter les ré-entrées pendant les remplacements
     private boolean renumbering = false;

     AutoListContinuationFilter(JTextArea area) {
         this.area = area;
     }

     @Override
     public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
             throws BadLocationException {
         String augmented = maybeAugment(text, offset, fb);
         super.insertString(fb, offset, augmented, attrs);

         // Si on vient d'insérer un "\n" + préfixe "n. ", tenter la renumérotation
         if (!renumbering && augmented != null && augmented.indexOf('\n') >= 0) {
             tryRenumberTailAfterInsertion(fb);
         }
     }

     @Override
     public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
             throws BadLocationException {
         String augmented = maybeAugment(text, offset, fb);
         super.replace(fb, offset, length, augmented, attrs);

         if (!renumbering && augmented != null && augmented.indexOf('\n') >= 0) {
             tryRenumberTailAfterInsertion(fb);
         }
     }

     @Override
     public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
         // Supprimer d'abord
         super.remove(fb, offset, length);

         // Puis renuméroter si on a supprimé dans une liste numérotée
         if (!renumbering) {
             tryRenumberTailAfterDeletion(fb, offset);
         }
     }

     private String maybeAugment(String text, int offset, FilterBypass fb) throws BadLocationException {
         if (text == null) return null;
         int nl = text.indexOf('\n');
         if (nl < 0) return text; // on ne s’active qu’au retour chariot

         String prefix = getContinuationPrefix(fb.getDocument(), offset);
         if (prefix == null) return text;

         // Insère le préfixe juste après le premier '\n'
         StringBuilder sb = new StringBuilder(text.length() + prefix.length());
         sb.append(text, 0, nl + 1).append(prefix);
         if (nl + 1 < text.length()) sb.append(text, nl + 1, text.length());
         return sb.toString();
     }

     /**
      * Après insertion du "\n" + préfixe, si la ligne suivante est déjà numérotée
      * avec la même indentation, renuméroter tout le bloc en aval (+1, +1, ...).
      */
     private void tryRenumberTailAfterInsertion(FilterBypass fb) {
         try {
             Document doc = fb.getDocument();

             // Ligne courante = la nouvelle ligne insérée (où est le caret)
             int caret = area.getCaretPosition();
             int curStart = Utilities.getRowStart(area, caret);
             int curEnd   = Utilities.getRowEnd(area, caret);
             if (curStart < 0 || curEnd < curStart) return;

             String curLine = doc.getText(curStart, curEnd - curStart);

             // Indentation + tête de ligne
             int i = 0;
             while (i < curLine.length() && Character.isWhitespace(curLine.charAt(i))) i++;
             String indent = curLine.substring(0, i);
             String head   = curLine.substring(i);

             // On ne renumérote que si la nouvelle ligne est bien "n. " (pas "-. ")
             int j = 0;
             while (j < head.length() && Character.isDigit(head.charAt(j))) j++;
             if (j == 0 || j + 2 > head.length()
                     || head.charAt(j) != '.'
                     || head.charAt(j + 1) != ' ') {
                 return;
             }

             int currentNumber = Integer.parseInt(head.substring(0, j));
             int nextWanted = currentNumber + 1;

             // Position de la ligne suivante
             int nextStart = curEnd + 1;
             if (nextStart > doc.getLength()) return;

             renumbering = true;

             int p = nextStart;
             while (p <= doc.getLength()) {
                 int s = Utilities.getRowStart(area, p);
                 if (s < 0) break;
                 int e = Utilities.getRowEnd(area, s);
                 if (e < s) break;

                 String line = doc.getText(s, e - s);

                 // Même indentation ?
                 int a = 0;
                 while (a < line.length() && Character.isWhitespace(line.charAt(a))) a++;
                 String indent2 = line.substring(0, a);
                 if (!indent2.equals(indent)) break; // autre bloc

                 // Motif "^\s*\d+\. "
                 int b = a;
                 while (b < line.length() && Character.isDigit(line.charAt(b))) b++;
                 if (b == a || b + 2 > line.length()
                         || line.charAt(b) != '.'
                         || line.charAt(b + 1) != ' ') {
                     break; // fin de la séquence numérotée
                 }

                 String tail = line.substring(b + 2); // après "n. "
                 String newHead = indent + nextWanted + ". ";
                 String newLine = newHead + tail;

                 // Remplacer la ligne par sa version renumérotée
                 fb.replace(s, e - s, newLine, null);

                 nextWanted++; // +1 pour la suivante

                 // Avancer à la prochaine ligne (recalcule car longueur modifiée)
                 int newEnd = Utilities.getRowEnd(area, s);
                 if (newEnd < 0) break;
                 p = newEnd + 1;
             }
         } catch (BadLocationException ex) {
             // On ignore silencieusement : pas de crash en saisie
         } finally {
             renumbering = false;
         }
     }

     /**
      * Après suppression (ligne entière, retour chariot, ou bloc), si la ligne
      * désormais suivante est une "n. " avec la même indentation que l’item
      * précédent, on renumérote tout le bloc (décrément implicite : ancre+1, ancre+2…).
      * Si aucun item précédent n’existe au même niveau, on redémarre à 1.
      */
     private void tryRenumberTailAfterDeletion(FilterBypass fb, int offset) {
         try {
             Document doc = fb.getDocument();

             // Première ligne potentiellement à renuméroter = celle qui commence à 'offset'
             int nextStart = Utilities.getRowStart(area, Math.min(offset, doc.getLength()));
             if (nextStart < 0) return;

             int nextEnd = Utilities.getRowEnd(area, nextStart);
             if (nextEnd < nextStart) return;

             String nextLine = doc.getText(nextStart, nextEnd - nextStart);

             // Indentation + motif "n. " sur la ligne suivante ?
             int i = 0;
             while (i < nextLine.length() && Character.isWhitespace(nextLine.charAt(i))) i++;
             String indent = nextLine.substring(0, i);
             String head   = nextLine.substring(i);

             int j = 0;
             while (j < head.length() && Character.isDigit(head.charAt(j))) j++;
             if (j == 0 || j + 2 > head.length()
                     || head.charAt(j) != '.'
                     || head.charAt(j + 1) != ' ') {
                 return; // pas une ligne numérotée : rien à faire
             }

             // Chercher l'item précédent pour connaître le "prochain attendu"
             int wanted = 1; // par défaut, on redémarre à 1
             if (nextStart > 0) {
                 int ancStart = Utilities.getRowStart(area, nextStart - 1);
                 int ancEnd   = Utilities.getRowEnd(area, ancStart);
                 if (ancStart >= 0 && ancEnd >= ancStart) {
                     String ancLine = doc.getText(ancStart, ancEnd - ancStart);
                     int a = 0;
                     while (a < ancLine.length() && Character.isWhitespace(ancLine.charAt(a))) a++;
                     String ancIndent = ancLine.substring(0, a);
                     String ancHead   = ancLine.substring(a);

                     int b = 0;
                     while (b < ancHead.length() && Character.isDigit(ancHead.charAt(b))) b++;
                     if (b > 0 && b + 2 <= ancHead.length()
                             && ancHead.charAt(b) == '.'
                             && ancHead.charAt(b + 1) == ' '
                             && ancIndent.equals(indent)) {
                         try {
                             int ancNum = Integer.parseInt(ancHead.substring(0, b));
                             wanted = ancNum + 1;
                         } catch (NumberFormatException ignored) {}
                     }
                 }
             }

             // Renuméroter à partir de nextStart : wanted, wanted+1, ...
             renumbering = true;

             int p = nextStart;
             int nextWanted = wanted;
             while (p <= doc.getLength()) {
                 int s = Utilities.getRowStart(area, p);
                 if (s < 0) break;
                 int e = Utilities.getRowEnd(area, s);
                 if (e < s) break;

                 String line = doc.getText(s, e - s);

                 int a = 0;
                 while (a < line.length() && Character.isWhitespace(line.charAt(a))) a++;
                 String indent2 = line.substring(0, a);
                 if (!indent2.equals(indent)) break; // on sort du bloc

                 int b = a;
                 while (b < line.length() && Character.isDigit(line.charAt(b))) b++;
                 if (b == a || b + 2 > line.length()
                         || line.charAt(b) != '.'
                         || line.charAt(b + 1) != ' ') {
                     break; // plus une ligne numérotée
                 }

                 String tail = line.substring(b + 2);
                 String newLine = indent + nextWanted + ". " + tail;
                 fb.replace(s, e - s, newLine, null);

                 nextWanted++;

                 int newEnd = Utilities.getRowEnd(area, s);
                 if (newEnd < 0) break;
                 p = newEnd + 1;
             }
         } catch (BadLocationException ex) {
             // pas de crash
         } finally {
             renumbering = false;
         }
     }

     /**
      * Détermine le préfixe à insérer au début de la ligne suivante,
      * en fonction du début de la ligne courante.
      * - "   -. "  -> "   -. "
      * - "   1. "  -> "   2. " (garde l’indentation)
      */
     private String getContinuationPrefix(Document doc, int offset) throws BadLocationException {
         int pos = Math.max(0, Math.min(offset, doc.getLength()));
         int lineStart = Utilities.getRowStart(area, pos);
         int lineEnd   = Utilities.getRowEnd(area, pos);
         if (lineStart < 0) lineStart = 0;
         if (lineEnd < lineStart) lineEnd = lineStart;

         String line = doc.getText(lineStart, lineEnd - lineStart);

         // Indentation facultative
         int i = 0;
         while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
         String indent = line.substring(0, i);
         String head   = line.substring(i);

         // Cas "-. "
         if (head.startsWith("-. ")) {
             return indent + "-. ";
         }

         // Cas "n. " (point + espace obligatoires)
         int j = 0;
         while (j < head.length() && Character.isDigit(head.charAt(j))) j++;
         if (j > 0 && j + 2 <= head.length()
                 && head.charAt(j) == '.'
                 && head.charAt(j + 1) == ' ') {
             try {
                 int n = Integer.parseInt(head.substring(0, j));
                 return indent + (n + 1) + ". ";
             } catch (NumberFormatException ignored) {
                 // nombre invalide → pas de continuation
             }
         }
         return null; // pas de continuation automatique
     }
 
 }

	//Insérer " -. " au début du paragraphe courant (après l'indentation)
	 private static final class InsertUnorderedBulletAction extends AbstractAction {
		private static final long serialVersionUID = 1L;
	
		@Override
		public void actionPerformed(ActionEvent e) {
	      try {
	          Document doc = editorPane.getDocument();
	          int caret = editorPane.getCaretPosition();
	
	          int lineStart = Utilities.getRowStart(editorPane, caret);
	          int lineEnd   = Utilities.getRowEnd(editorPane, caret);
	          if (lineStart < 0 || lineEnd < lineStart) return;
	
	          String line = doc.getText(lineStart, lineEnd - lineStart);
	
	          // Indentation (espaces / tabulations) conservée
	          int i = 0;
	          while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
	          int insertPos = lineStart + i;
	
	          // Déjà en liste non numérotée ? => toggle = retirer
	          if (line.startsWith("-. ", i)) {
	              doc.remove(insertPos, 3);
	              if (caret >= insertPos) editorPane.setCaretPosition(Math.max(lineStart, caret - 3));
	              return;
	          }
	
	
	          // Si ligne numérotée "n. ", on remplace le préfixe par "-. "
	          int j = i;
	          while (j < line.length() && Character.isDigit(line.charAt(j))) j++;
	          if (j > i && j + 2 <= line.length()
	                  && line.charAt(j) == '.'
	                  && line.charAt(j + 1) == ' ') {
	              int removeLen = (j + 2) - i; // longueur de "n. "
	              doc.remove(insertPos, removeLen);
	              doc.insertString(insertPos, "-. ", null);
	
	              // Ajuster le caret si besoin
	              if (caret >= insertPos) {
	                  int delta = "-. ".length() - removeLen;
	                  editorPane.setCaretPosition(Math.max(lineStart, caret + delta));
	              }
	              return;
	          }
	
	          // Sinon, on insère simplement "-. " après l'indentation
	          doc.insertString(insertPos, "-. ", null);
	          if (caret >= insertPos) editorPane.setCaretPosition(caret + 3);
	      } catch (BadLocationException ex) {
	          // silencieux en cas de micro course
	      }
	  }
	}

 	@SuppressWarnings("serial")
 	private static final class ToggleAudioAction extends AbstractAction {
	    @Override public void actionPerformed(ActionEvent e) {
	        try {
	            if (commandes.audio) {
	                // Dire avant de couper, sinon on n’entendrait rien
	                commandes.audio = false;
	            } else {
	                commandes.audio = true;
	                // Maintenant que c'est actif, on peut annoncer
	            }
	            // Garder la préférence en phase si tu t’en sers à l’ouverture des menus
	            commandes.audioActif = commandes.audio;
	        } catch (Exception ignore) {
	            // on ne casse pas la saisie si TTS plante
	        }
	    }
	}
  
 	@SuppressWarnings("serial")
 	public static final class SRAnnouncerArea extends javax.swing.JTextArea {
 	    public SRAnnouncerArea() {
 	        super();
 	        setEditable(false);
 	        setFocusable(true);
 	        setRequestFocusEnabled(true);
 	        setLineWrap(true);
 	        setWrapStyleWord(true);
 	        setOpaque(false);
 	        setBorder(null);
 	        setHighlighter(null);
 	        putClientProperty("FocusTraversalKeysEnabled", Boolean.FALSE);
 	        setCaretColor(getBackground());
 	    }

 	    public void announce(String msg) {
 	        // 1) texte visible pour la braille + navigation
 	        setText(msg != null ? msg : "");
 	        // 2) place un caret réel
 	        setCaretPosition(0);

 	        // ⚠️ Ne PAS forcer ACCESSIBLE_NAME_PROPERTY ici, ça double l'annonce.
 	        // Si tu tiens à garder un nom, fais-le sans firePropertyChange :
 	        javax.accessibility.AccessibleContext ac = getAccessibleContext();
 	        if (ac != null) {
 	            ac.setAccessibleName("Annonce"); // optionnel, générique
 	        }
 	    }

 	    public void clear() {
 	        setText("");
 	        setCaretPosition(0);
 	        javax.accessibility.AccessibleContext ac = getAccessibleContext();
 	        if (ac != null) {
 	            ac.setAccessibleName("Annonce"); // optionnel
 	        }
 	    }
 	}

 	
 	public static void announceCaretLine() { announceCaretLine(false, false,null); }

 	public static void announceCaretLine(boolean withTimer, boolean withMessage, String msg) {
 	    try {
 	    	
 	    	if(!commandes.nonvoyant && !withTimer) return;
 	    		
 	        if (editorPane == null) return;

 	        int caret = editorPane.getCaretPosition();
 	        int start = javax.swing.text.Utilities.getRowStart(editorPane, caret);
 	        int end   = javax.swing.text.Utilities.getRowEnd(editorPane, caret);
 	        if (start < 0 || end < start) return;

 	        String line = editorPane.getDocument().getText(start, end - start);
 	        String baseText = withMessage ? msg : line;

 	        // Ne PAS réassigner toShow; crée une variable finale capturable
 	        final String announceText = withTimer ? baseText : "INFO échap. : " + baseText;

 	        srAnnounce.announce(announceText);

 	        // Calcule le délai hors lambda pour rester "effectively final"
 	        final int delayMs = withTimer ? computeDisplayMillisByWords(announceText) : 0;

 	        javax.swing.SwingUtilities.invokeLater(() -> {
 	            srAnnounce.requestFocusInWindow();
 	            srActive = true;

 	            if (delayMs > 0) {
 	                srTimer = new javax.swing.Timer(delayMs, ev -> {
 	                    cancelSrAnnouncement();
 	                    srAnnounce.clear();
 	                    editorPane.requestFocusInWindow();
 	                });
 	                srTimer.setRepeats(false);
 	                srTimer.start();
 	            }else {
 	 	            srDismissDispatcher = new java.awt.KeyEventDispatcher() {
 	 	                @Override public boolean dispatchKeyEvent(java.awt.event.KeyEvent e) {
 	 	                    if (!srActive) return false;
 	 	                    if (e.getID() != java.awt.event.KeyEvent.KEY_PRESSED) return false;

 	 	                    int kc = e.getKeyCode();
 	 	                    if (kc == java.awt.event.KeyEvent.VK_ESCAPE) {
 	 	                        e.consume();
 	 	                        cancelSrAnnouncement();
 	 	                        srAnnounce.clear();
 	 	                        editorPane.requestFocusInWindow();

 	 	                        java.awt.EventQueue.invokeLater(() -> {
 	 	                            editorPane.dispatchEvent(new java.awt.event.KeyEvent(
 	 	                                editorPane,
 	 	                                java.awt.event.KeyEvent.KEY_PRESSED,
 	 	                                System.currentTimeMillis(),
 	 	                                e.getModifiersEx(),
 	 	                                kc,
 	 	                                e.getKeyChar(),
 	 	                                e.getKeyLocation()
 	 	                            ));
 	 	                        });
 	 	                        return true;
 	 	                    }
 	 	                    return false;
 	 	                }
 	 	            };
 	 	            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
 	 	                    .addKeyEventDispatcher(srDismissDispatcher);
 	            }
 	        });
 	    } catch (Exception ignore) {}
 	}


 	/** Coupe le timer et enlève le KeyEventDispatcher si présents. */
 	private static void cancelSrAnnouncement() {
 	    if (srTimer != null) {
 	        srTimer.stop();
 	        srTimer = null;
 	    }
 	    if (srDismissDispatcher != null) {
 	        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
 	                .removeKeyEventDispatcher(srDismissDispatcher);
 	        srDismissDispatcher = null;
 	    }
 	    srActive = false;
 	}


 	// déterminer le delay pour la lecture d'une annonce withTimer.
 	private static int computeDisplayMillisByWords(String msg) {
 	    if (msg == null) return 100;
 	    int words = Math.max(1, msg.trim().split("\\s+").length);

 	    int base  = 900;   // ms
 	    int perW  = 350;   // ms par mot
 	    int minMs = 1000;
 	    int maxMs = 8000;

 	    long raw = (long) base + (long) perW * words;
 	    return (int) Math.max(minMs, Math.min(maxMs, raw));
 	}
 	
 	
 	// --- Motif unique : "#<niveau>. <texte>" strictement en début de ligne ---
 	private static final Pattern HEADING_PATTERN = Pattern.compile("^#([1-6])\\.\\s+(.+?)\\s*$");

 	// Conteneur résultat
 	// Petit DTO local pour ne PAS modifier blindWriter.HeadingInfo
 	private static final class HeadingFound {
		final String levelLabel;
		final String text;
		final int paraIndex; // 1-based

 	    HeadingFound(String levelLabel, String text, int paraIndex) {
 	        this.levelLabel = levelLabel;
 	        this.text = text;
 	        this.paraIndex = paraIndex;
 	    }
 	}


 // Renvoie le premier titre AU-DESSUS du caret (inchangé)
 	private static HeadingFound findEnclosingHeading() {
 	    try {
 	        final javax.swing.text.Document doc = editorPane.getDocument();
 	        final javax.swing.text.Element root = doc.getDefaultRootElement();

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
 	private static HeadingFound findNextHeadingBelow() {
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

 	/** Petit conteneur pratique pour avoir au-dessus et en-dessous. */
 	public static final class SurroundingHeadings {
 	    public final HeadingFound above; // peut être null
 	    public final HeadingFound below; // peut être null
 	    public SurroundingHeadings(HeadingFound above, HeadingFound below) {
 	        this.above = above; this.below = below;
 	    }
 	}
 	
 	private static String formatHeadingLine(String prefix, HeadingFound h) {
 	    if (h == null) return prefix + "Aucun titre détecté.";
 	    // Exemple : "Au-dessus : Titre 2 Mon chapitre (§ 128)"
 	    return String.format("%s%s %s (§ %d)", prefix, h.levelLabel, h.text, h.paraIndex);
 	}

 	private static int safeParagraphIndexAt(javax.swing.text.Document doc, int pos) {
 	    try { return paragraphIndexAt(doc, pos); } catch (Exception ex) { return -1; }
 	}


 	@SuppressWarnings("serial")
 	private static final class ToggleEditAction extends AbstractAction {
 	    @Override
 	    public void actionPerformed(ActionEvent e) {
 	       boolean editable = editorPane.isEditable();
 	       editorPane.setEditable(!editable);
 	       String msg = editable ? "Édition bloquée." : "Édition activée.";
 	       java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
           dia.InfoDialog.show(owner, "Mode édition", msg);
 	    }
 	}


 	/** Force une marge horizontale minimale autour du caret dans le viewport. */
 	private static void ensureCaretHorizontalMargins(int leftMarginPx, int rightMarginPx) {
 	    if (editorPane == null || scrollPane == null) return;
 	    try {
 	        int pos = Math.max(0, Math.min(editorPane.getCaretPosition(),
 	                                       editorPane.getDocument().getLength()));
 	        java.awt.geom.Rectangle2D r2 = editorPane.modelToView2D(pos);
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
 	        int maxX = Math.max(0, editorPane.getWidth() - view.width);
 	        newX = Math.max(0, Math.min(newX, maxX));
 	        if (newX != view.x) vp.setViewPosition(new java.awt.Point(newX, view.y));
 	    } catch (javax.swing.text.BadLocationException ignore) { }
 	}


 	private static void applyEditorFont() {
 	    if (editorPane == null) return;
 	    editorPane.setFont(new Font(EDITOR_FONT_FAMILY, Font.PLAIN, EDITOR_FONT_SIZE));
 	    // optionnel : marges internes pour le confort visuel
 	    editorPane.setMargin(new java.awt.Insets(12, 24, 12, 24));
 	    editorPane.revalidate();
 	    editorPane.repaint();
 	    // garde le caret bien visible même après zoom
 	    SwingUtilities.invokeLater(() -> ensureCaretHorizontalMargins(100, 100));
 	}

 	public static void zoomIn() {
 	    if (EDITOR_FONT_SIZE < FONT_MAX) {
 	        EDITOR_FONT_SIZE = Math.min(FONT_MAX, EDITOR_FONT_SIZE + FONT_STEP);
 	        applyEditorFont();
 	        announceCaretLine(false, true, "Zoom " + EDITOR_FONT_SIZE + " points.");
 	    }
 	}

 	public static void zoomOut() {
 	    if (EDITOR_FONT_SIZE > FONT_MIN) {
 	        EDITOR_FONT_SIZE = Math.max(FONT_MIN, EDITOR_FONT_SIZE - FONT_STEP);
 	        applyEditorFont();
 	        announceCaretLine(false, true, "Zoom " + EDITOR_FONT_SIZE + " points.");
 	    }
 	}

 	public static void zoomReset() {
 	    EDITOR_FONT_SIZE = 34;
 	    applyEditorFont();
 	    announceCaretLine(false, true, "Zoom réinitialisé.");
 	}
 	
 	/** Applique la police à la barre de menus et à tout son contenu (récursif). */
 	private static void applyMenuFontTree(JMenuBar bar) {
 	    if (bar == null) return;
 	    Font f = getMenuFont();
 	    setFontRecursively(bar, f);
 	    // force le rafraîchissement visuel
 	    bar.revalidate();
 	    bar.repaint();
 	}
 	
 	// Renvoie la police courante pour les menus
 	private static Font getMenuFont() {
 	    return new Font(MENU_FONT_FAMILY, Font.PLAIN, MENU_FONT_SIZE);
 	}
 	
 	/** (Facultatif) commandes pour zoomer les menus au clavier */
 	private static void menuZoomIn()  { if (MENU_FONT_SIZE < MENU_FONT_MAX) { MENU_FONT_SIZE += MENU_FONT_STEP; applyMenuFontTree(blindWriter.getInstance().getJMenuBar()); announceCaretLine(false, true, "Menus " + MENU_FONT_SIZE + " points."); } }
 	private static void menuZoomOut() { if (MENU_FONT_SIZE > MENU_FONT_MIN) { MENU_FONT_SIZE -= MENU_FONT_STEP; applyMenuFontTree(blindWriter.getInstance().getJMenuBar()); announceCaretLine(false, true, "Menus " + MENU_FONT_SIZE + " points."); } }
 	private static void menuZoomReset(){ MENU_FONT_SIZE = 22; applyMenuFontTree(blindWriter.getInstance().getJMenuBar()); announceCaretLine(false, true, "Taille des menus réinitialisée."); }

 	/** Parcourt récursivement menus, items et sous-composants pour poser la police. */
 	private static void setFontRecursively(java.awt.Component c, Font f) {
 	    if (c == null) return;
 	    c.setFont(f);

 	    // Si c'est un JMenu, traiter aussi son popup et ses items
 	    if (c instanceof javax.swing.JMenu) {
 	        javax.swing.JMenu jm = (javax.swing.JMenu) c;
 	        java.awt.Component[] arr = jm.getMenuComponents();
 	        for (java.awt.Component child : arr) setFontRecursively(child, f);
 	        if (jm.getPopupMenu() != null) setFontRecursively(jm.getPopupMenu(), f);
 	    }

 	    if (c instanceof java.awt.Container) {
 	        for (java.awt.Component child : ((java.awt.Container) c).getComponents()) {
 	            setFontRecursively(child, f);
 	        }
 	    }
 	}
 	
 	// index du paragraphe
 	private static int paragraphIndexAt(javax.swing.text.Document doc, int offset) {
 	    javax.swing.text.Element root = doc.getDefaultRootElement();
 	    int idx = root.getElementIndex(Math.max(0, Math.min(offset, doc.getLength())));
 	    return idx + 1; // 1-based
 	}

 	
 	// dans writer.blindWriter.init(...)
 	private static ImageIcon loadIcon(String absoluteClasspathPath) {
 	    var url = blindWriter.class.getResource(absoluteClasspathPath);
 	    if (url == null) {
 	        throw new IllegalStateException("Ressource introuvable : " + absoluteClasspathPath);
 	    }
 	    return new ImageIcon(url);
 	}

 	// Version de l'application
 	public static String getAppVersion() {
 	    String v = null;
 	    Package p = BoiteVersionNV.class.getPackage();
 	    if (p != null) v = p.getImplementationVersion();
 	    if (v != null && !v.isBlank()) return v;
 	    try (var in = BoiteVersionNV.class.getResourceAsStream("/version.properties")) {
 	        if (in != null) {
 	            var props = new java.util.Properties();
 	            props.load(in);
 	            v = props.getProperty("app.version");
 	            if (v != null && !v.isBlank()) return v;
 	        }
 	    } catch (Exception ignore) {}
 	    return "dev";
 	}

 	
 // Action F2 : "Position dans le texte"
 	@SuppressWarnings("serial")
	private static final javax.swing.Action actAnnouncePosition = new AbstractAction("Position dans le texte") {
 	    @Override public void actionPerformed(ActionEvent e) {
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
 	        // ou audio uniquement :
 	        // announceCaretLine(false, true, msg.toString());
 	    }
 	};

 	/** Titre strictement en-dessous (ignore la ligne courante si c’est déjà un titre). */
 	private static HeadingFound findNextHeadingStrictlyBelow() {
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
 	private static void moveCaretToHeadingStart(HeadingFound h) {
 	    if (h == null) return;
 	    try {
 	        final javax.swing.text.Document doc = editorPane.getDocument();
 	        final javax.swing.text.Element root = doc.getDefaultRootElement();
 	        int lineIdx0 = Math.max(0, Math.min(h.paraIndex - 1, root.getElementCount() - 1));
 	        int pos = root.getElement(lineIdx0).getStartOffset();
 	        editorPane.setCaretPosition(pos);
 	        // Assure la visibilité à l’écran
 	        @SuppressWarnings("deprecation")
			java.awt.Rectangle r = editorPane.modelToView(pos);
 	        if (r != null) editorPane.scrollRectToVisible(r);
 	    } catch (Exception ignore) {}
 	}

 	// Action F3 : aller au début du prochain titre
 	@SuppressWarnings("serial")
	private static final javax.swing.Action actGotoNextHeading = new AbstractAction("Titre suivant") {
 	    @Override public void actionPerformed(ActionEvent e) {
 	        HeadingFound next = findNextHeadingStrictlyBelow(); // ou findNextHeadingBelow()
 	        if (next != null) {
 	            moveCaretToHeadingStart(next);
 	            // annonce courte (optionnelle)
 	            // announceCaretLine(false, true, next.levelLabel + " " + next.text + " (§ " + next.paraIndex + ")");
 	            editorPane.requestFocusInWindow();
 	        } else {
 	            java.awt.Toolkit.getDefaultToolkit().beep();
 	            // announceCaretLine(false, true, "Aucun titre suivant.");
 	        }
 	    }
 	};
 	
 	// Action SHIFT+F3 : aller au début du titre précédent
	@SuppressWarnings("serial")
	private static final javax.swing.Action actGotoPrevHeading = new AbstractAction("Titre précédent") {
 	    @Override public void actionPerformed(ActionEvent e) {
 	        HeadingFound prev = findEnclosingHeading(); // plus proche titre AU-DESSUS (ligne courante ignorée)
 	        if (prev != null) {
 	            moveCaretToHeadingStart(prev);
 	            // announceCaretLine(false, true, prev.levelLabel + " " + prev.text + " (§ " + prev.paraIndex + ")");
 	            editorPane.requestFocusInWindow();
 	        } else {
 	            java.awt.Toolkit.getDefaultToolkit().beep();
 	            // announceCaretLine(false, true, "Aucun titre précédent.");
 	        }
 	    }
 	};


	 // Accesseur paresseux : crée le manager si besoin, quand editorPane existe
	 public static writer.bookmark.BookmarkManager bm() {
	     if (bookmarks == null && editorPane != null) {
	         bookmarks = new writer.bookmark.BookmarkManager(editorPane);
	     }
	     return bookmarks;
	 }
	
	 // Variante pratique : renvoie false et beep si indisponible
	 public static boolean withBm(java.util.function.Consumer<writer.bookmark.BookmarkManager> use) {
	     writer.bookmark.BookmarkManager m = bm();
	     if (m == null) {
	         java.awt.Toolkit.getDefaultToolkit().beep();
	         // announceCaretLine(false, true, "Éditeur indisponible.");
	         return false;
	     }
	     use.accept(m);
	     return true;
	 }
	 
	 /** Met à jour le titre de la fenêtre selon commandes.nameFile / currentDirectory / état modifié. */
	 public static void updateWindowTitle() {
	     if (instance == null) return;
	     String base = "blindWriter";
	     String name = (commandes.nameFile != null && !commandes.nameFile.isBlank())
	         ? commandes.nameFile
	         : "Nouveau";
	     // si tu veux afficher l'extension .bwr systématiquement, décommente la ligne suivante :
	     // if (!name.endsWith(".bwr")) name = name + ".bwr";

	     StringBuilder title = new StringBuilder(base).append(" — ").append(name);

	     if (commandes.currentDirectory != null) {
	         // n'affiche que le nom du dossier parent pour ne pas surcharger
	         java.io.File dir = new java.io.File(commandes.currentDirectory.toString());
	         String folder = dir.getName().isBlank() ? dir.getPath() : dir.getName();
	         title.append(" (").append(folder).append(")");
	     }
	     if (isModified) title.insert(0, "*"); // préfixe * quand non sauvegardé
	     instance.setTitle(title.toString());
	 }

	 /** Définit l'état modifié et met à jour le titre. */
	 public static void setModified(boolean modified) {
	     isModified = modified;
	     updateWindowTitle();
	 }
	// Ajoute cette méthode dans blindWriter (ou une utilité)
	 public static String findUrlAtCaret() {
	     try {
	         int pos = editorPane.getCaretPosition();
	         String text = editorPane.getText();
	         // cherche un "http" le plus proche autour du caret
	         int s = Math.max(0, text.lastIndexOf("http", pos - 1));
	         if (s < 0) return null;
	         int e = s;
	         while (e < text.length() && !Character.isWhitespace(text.charAt(e)) && text.charAt(e) != ')' && text.charAt(e) != '"') e++;
	         return text.substring(s, e);
	     } catch (Exception ex) { return null; }
	 }



    
}
