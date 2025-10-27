package dia;


import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.accessibility.AccessibleContext;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import writer.TraitementSonPourTTS;
import writer.ui.EditorFrame;


public class navigateurT1 extends JFrame{

	private static final long serialVersionUID = 1L;
	private  JList<String> list;
    private DefaultListModel<String> listModel;
    private  StringBuilder allTitles = new StringBuilder();
    private  Map<String, String> structure = new LinkedHashMap<String, String>();
    private java.util.List<String> titresOrdre = new java.util.ArrayList<>();
    // garde les positions (offsets) des titres dans le texte source
    private java.util.List<Integer> titresOffsets = new java.util.ArrayList<>();
    // mapping entre l’index affiché dans la JList et l’index global dans titresOrdre
    private java.util.List<Integer> viewToGlobal = new java.util.ArrayList<>();
    // index global du titre actuellement sélectionné (dans titresOrdre/titresOffsets)
    private int selectedGlobalIndex = -1;
    // champ
    private final java.util.List<Integer> titresNiveaux = new java.util.ArrayList<>();
    private final java.util.Set<Integer> expanded = new java.util.HashSet<>();
    private final java.util.List<Integer> parents  = new java.util.ArrayList<>();
    
	//private final  writer.blindWriter.SRAnnouncerArea sr1px = new writer.blindWriter.SRAnnouncerArea();;
	// si non-null, on n’affiche que les titres dont l’ancêtre racine == focusedRoot
	private Integer focusedRoot = null;
	// Montre uniquement la branche du nœud ciblé (ancêtres + lui + ses descendants)
	private Integer focusedBranch = null;

    int selectedIndex = 0;

    private EditorFrame parent;
    private JTextArea editor;
    
	@SuppressWarnings("serial")
	public navigateurT1(EditorFrame parent) {
		this.parent = parent;
		this.editor = parent.getEditor();
		
		this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		
		setTitle("Navigateur de titre");
		setResizable(false);
		
		structure = new LinkedHashMap<>();
		listModel = new DefaultListModel<>();
		list = new JList<>(listModel);
		
		list.setCellRenderer(new DefaultListCellRenderer() {
		    @Override
		    public Component getListCellRendererComponent(
		            JList<?> lst, Object value, int viewIndex,
		            boolean isSelected, boolean cellHasFocus) {

		        JLabel lbl = (JLabel) super.getListCellRendererComponent(
		                lst, value, viewIndex, isSelected, cellHasFocus);

		        int gi = (viewIndex >= 0 && viewIndex < viewToGlobal.size())
		                ? viewToGlobal.get(viewIndex) : -1;

		        // Nom accessible = indicateur [+]/[-] + contenu “propre”
		        String accessible = (gi >= 0) ? indicatorFor(gi) + buildNavTextBase(gi) : "";
		        lbl.getAccessibleContext().setAccessibleName(accessible);

		        String shown = (value == null) ? "" : value.toString();
			     // ... éventuelles modifs de shown ...
			     lbl.setText(shown); // seulement si tu as modifié 'shown'

		        return lbl;
		    }
		});
		
		// Police plus grande pour la liste des titres
		Font big = list.getFont().deriveFont(24f); // 22 points
		list.setFont(big);

		// (optionnel) un peu de marge verticale par ligne pour l’accessibilité
		int lineH = list.getFontMetrics(big).getHeight();
		list.setFixedCellHeight(lineH + 6); // +6 px de padding
		
		JScrollPane scrollPane = new JScrollPane(list);
      
		getContentPane().add(scrollPane, BorderLayout.CENTER);

		silentFocus.setFocusable(true);
		silentFocus.setOpaque(false);
		silentFocus.setPreferredSize(new java.awt.Dimension(1,1));
		silentFocus.setMinimumSize(new java.awt.Dimension(1,1));
		silentFocus.setMaximumSize(new java.awt.Dimension(1,1));
		getContentPane().add(silentFocus, BorderLayout.SOUTH); // 1px en bas, invisible

		list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

		allTitle(editor.getText());
		// au lieu de ajouteTousLesTitreUnDansList();
		rebuildVisibleModel();
		//ajouteTousLesTitreUnDansList();

		// Sélectionner le premier élément de la liste après avoir ajouté les titres
        if (!listModel.isEmpty()) {
            list.setSelectedIndex(0);  // Sélectionner le premier élément
            updateSelectionContextAndSpeak(); // pour initialiser selectedGlobalIndex dès l’ouverture
        }
        
        
        list.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                // <<< AVANT : tout un bloc pour lire l’élément >>>
                // <<< MAINTENANT : une seule ligne >>>
                updateSelectionContextAndSpeak();
            }
            @Override public void focusLost(FocusEvent e) {}
        });
      
        
        // Ajouter un écouteur de touches pour détecter la touche Entrée
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
            	
            	if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            	    updateSelectionContextAndSpeak();

            	    if (selectedGlobalIndex >= 0 && selectedGlobalIndex < titresOffsets.size()) {
            	        int caret = titresOffsets.get(selectedGlobalIndex);

            	        // Sécurité : avance jusqu’au premier '#'
            	        String doc = editor.getText();
            	        while (caret < doc.length() && Character.isWhitespace(doc.charAt(caret))) {
            	            if (caret + 1 < doc.length() && doc.charAt(caret + 1) == '#') { break; }
            	            caret++;
            	        }
            	        // ou plus direct : si ce n’est pas déjà un '#', cherche le prochain '#'
            	        if (caret < doc.length() && doc.charAt(caret) != '#') {
            	            int h = doc.indexOf('#', caret);
            	            if (h >= 0 && h - caret < 8) caret = h; // marge raisonnable
            	        }

            	        caret = Math.max(0, Math.min(caret, editor.getDocument().getLength()));
            	        editor.setCaretPosition(caret);

            	        try {
            	            java.awt.geom.Rectangle2D r2d = editor.modelToView2D(caret);
            	            if (r2d != null) {
            	                java.awt.Rectangle r = r2d.getBounds();
            	                int h = parent.getScrollPane().getViewport().getHeight();
            	                parent.getScrollPane().scrollRectToVisible(new java.awt.Rectangle(r.x, r.y, r.width, h));
            	            }
            	        } catch (Exception ex) {
            	            ex.printStackTrace();
            	        }

            	        fermeture();
            	    }
            	}

            	
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                	fermeture();
                }
                
                    

                if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP) {
                    SwingUtilities.invokeLater(() -> updateSelectionContextAndSpeak());
                }

                
                if (e.isAltDown() && e.getKeyCode() == KeyEvent.VK_UP) {
                    // 1) s'assurer que selectedGlobalIndex correspond à l’élément surligné
                    updateSelectionContextAndSpeak();
                    final int keepGlobal = selectedGlobalIndex;

                    SwingUtilities.invokeLater(() -> {
                        if (deplacerBlocVersHaut(keepGlobal)) {
                        	 //Titre déplacé vers le haut."
                        } else {
                        	//"Impossible de déplacer ce titre."
                        }
                    });
                }

                
                if (e.isAltDown() && e.getKeyCode() == KeyEvent.VK_DOWN) {
                    // s'assurer que selectedGlobalIndex correspond bien à la sélection visible
                    updateSelectionContextAndSpeak();
                    final int keepGlobal = selectedGlobalIndex;

                    SwingUtilities.invokeLater(() -> {
                        if (deplacerBlocVersBas(keepGlobal)) {
                        	//"Titre déplacé vers le bas."
                        } else {
                        	//"Impossible de déplacer ce titre vers le bas."
                        }
                    });
                }
                
                
                if(e.getKeyCode() == KeyEvent.VK_F1) {
                	informations();
                }
            }
        });
        
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                // Donner le focus à la JList dès que la fenêtre est visible
            	list.requestFocusInWindow(); 
            }
            @Override
 		    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
            	fermeture();
 		    }
        });        
    
        // Assure que le JTextField a le focus quand la boîte apparaît
        SwingUtilities.invokeLater(list::requestFocusInWindow);

        
        
        //setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setVisible(true);
        
        setupKeyBindings();
	}
	
	// Découpage de la structure par les titres du documents
	private void allTitle(String text) {

		allTitles = new StringBuilder();
	    structure = new LinkedHashMap<>();
	    titresOrdre.clear();
	    titresOffsets.clear();
	    titresNiveaux.clear();   // <-- AJOUT IMPORTANT

	    Pattern pattern = Pattern.compile("(?m)^\\s*#[1-5]\\..*$");
	    Matcher matcher = pattern.matcher(text);

	    while (matcher.find()) {
	        String line = matcher.group();
	        int startLine = matcher.start();
	        int firstHashRel = Math.max(0, line.indexOf('#'));
	        int caretPos = startLine + firstHashRel;

	        titresOrdre.add(line);
	        titresNiveaux.add(niveauDuTitre(line));
	        titresOffsets.add(caretPos);
	        allTitles.append(line).append(System.lineSeparator());
	        structure.putIfAbsent(keyOf(line), "");
	    }

	    // Compléments TTS pour #1
	    int count = 0;
	    String courantH1 = null;

	    for (String k : titresOrdre) {
	        if (k.matches("^\\s*#1\\..*")) {
	            if (courantH1 != null) {
	                structure.put(
	                    keyOf(courantH1),
	                    (count > 0)
	                      ? ". Dans cette partie, il y a " + count + " titres de niveau inférieurs."
	                      : ". Dans cette partie, il n'y a pas de titre de niveau inférieur."
	                );
	            }
	            courantH1 = k;
	            count = 0;
	        } else if (courantH1 != null) {
	            count++;
	        }
	    }
	    if (courantH1 != null) {
	        structure.put(
	            keyOf(courantH1),
	            (count > 0)
	              ? ". Dans cette partie, il y a " + count + " titres de niveau inférieurs."
	              : ". Dans cette partie, il n'y a pas de titre de niveau inférieur."
	        );
	    }
	    
	    parents.clear();
	    java.util.Deque<Integer> stack = new java.util.ArrayDeque<>();
	    for (int i = 0; i < titresOrdre.size(); i++) {
	        int lvl = titresNiveaux.get(i);
	        while (!stack.isEmpty() && titresNiveaux.get(stack.peek()) >= lvl) {
	            stack.pop();
	        }
	        int par = stack.isEmpty() ? -1 : stack.peek();
	        parents.add(par);
	        stack.push(i);
	    }

	    // quand on relit le document, on repart avec tout refermé
	    expanded.clear();

	}


	//Utilitaire : mets à jour le contexte de sélection
	private void updateSelectionContextAndSpeak() {
	    int viewIndex = list.getSelectedIndex();
	    if (viewIndex < 0 || viewIndex >= viewToGlobal.size()) {
	        selectedGlobalIndex = -1;
	        return;
	    }
	    selectedGlobalIndex = viewToGlobal.get(viewIndex);

//	    // ✅ dans la liste : on PRÉFIXE l’indicateur
//	    String toSpeak = indicatorFor(selectedGlobalIndex) + buildNavTextBase(selectedGlobalIndex);
//	    if (commandes.audio) commandes.tts.speak(toSpeak, 2.0f, true, false);
	}



	
	//Déplacer/Supprimer : utiliser les indices globaux et offsets
	private int indiceSuivantMemeOuInferieur(int idx, int niveauTitre) {
	    for (int i = idx + 1; i < titresOrdre.size(); i++) {
	        int lvl = niveauDuTitre(titresOrdre.get(i));
	        if (lvl == niveauTitre || lvl < niveauTitre) return i;
	    }
	    return -1; // pas de suivant
	}

	private int indicePrecedentMemeOuInferieur(int idx, int niveauTitre) {
	    int res = -1;
	    for (int i = 0; i < idx; i++) {
	        int lvl = niveauDuTitre(titresOrdre.get(i));
	        if (lvl == niveauTitre || lvl < niveauTitre) res = i;
	    }
	    return res; // -1 si rien
	}

	
	// Fermeture du navigateur
	private void fermeture() {
		list.removeAll();
	  	structure.clear();
		dispose();
		SwingUtilities.invokeLater(() -> {
            parent.requestFocus();
            parent.getEditor().requestFocusInWindow();
        });
	}

	
   	// Méthode pour sélectionner un élément dans une JList en fonction de son texte
	private void selectItemByText(String titre) {
	    try {
	        String cible = (titre == null) ? "" : titre.replaceFirst("^\\s*\\+\\s*", "");
	        for (int i = 0; i < list.getModel().getSize(); i++) {
	            String cand = list.getModel().getElementAt(i);
	            String candBase = (cand == null) ? "" : cand.replaceFirst("^\\s*\\+\\s*", "");
	            if (candBase.equals(cible)) {
	                list.setSelectedIndex(i);
	                list.ensureIndexIsVisible(i);
	                break;
	            }
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}

	    
    //Place le curseur dans le titre 1 au-dessus
	@SuppressWarnings("unused")
	private void titre1AuDessus(String Titre) {
	    if (Titre == null) return;
	    Titre = Titre.replaceFirst("^\\s*\\+\\s*", "");
	    Titre = keyOf(Titre); // <-- normalise pour matcher les clés

	    String majTitre = "";
	    for (String key : structure.keySet()) {
	        if (key.matches("^#1\\..*")) majTitre = key;
	        if (key.equals(Titre)) break;
	    }
	    selectItemByText(majTitre);
	}


	        
	// Information sur l'utilisation de la boite
	private void informations() {

	    int totalTitres = titresOrdre.size();
	    int visibles = (viewToGlobal != null) ? viewToGlobal.size() : 0;

	    // Combien de nœuds ont (au moins) un enfant ?
	    int expandables = 0;
	    for (int i = 0; i < titresOrdre.size(); i++) {
	        if (hasChildrenAt(i)) expandables++;
	    }

	    StringBuilder msg = new StringBuilder();
	    msg.append("Info. navigateur\nActuellement, le document contient au total ")
	       .append(totalTitres).append(" titres.");
	       

	    if (expanded.isEmpty()) {
	        // Personne n'est ouvert -> seulement les H1 visibles
	        msg.append("\nSeuls les titres à la racine du document (souvent de niveau #1) sont visibles. ");
	    } else if (expanded.size() >= expandables && expandables > 0) {
	        // Tous les nœuds dépliables sont ouverts -> tout est visible
	        msg.append("\nTous les titres sont développés. ");
	    } else {
	        // Quelques titres sont ouverts : on les énumère (ceux qui sont réellement visibles)
	        java.util.List<String> ouvertsVisibles = new java.util.ArrayList<>();
	        for (int i = 0; i < titresOrdre.size(); i++) {
	            if (expanded.contains(i) && isVisible(i)) {
	                String base = titresOrdre.get(i);
	                // Passage par ton traitement TTS habituel pour un rendu propre
	                ouvertsVisibles.add(new TraitementSonPourTTS(base).returnTexte);
	            }
	        }

	        if (ouvertsVisibles.isEmpty()) {
	            // Cas rare : des nœuds "ouverts" mais masqués parce qu’un parent est fermé
	            msg.append("\nPlusieurs titres sont marqués comme développés, mais ils ne sont pas visibles tant que leurs parents ne le sont pas.");
	        } else {
	            msg.append("\n");
	            int max = Math.min(5, ouvertsVisibles.size());
	            for (int i = 0; i < max; i++) {
	                if (i > 0) msg.append(i == max - 1 ? " et " : ", ");
	                msg.append(ouvertsVisibles.get(i));
	            }
	            if (ouvertsVisibles.size() > max) {
	                msg.append(", et ").append(ouvertsVisibles.size() - max).append(" autres");
	            }
	            msg.append(".").append("\nLe navigateur en affiche ").append(visibles).append(".");
	        }
	    }

	    // État du titre actuellement sélectionné (s’il a des enfants)
	    if (selectedGlobalIndex >= 0 && selectedGlobalIndex < titresOrdre.size() && hasChildrenAt(selectedGlobalIndex)) {
	        boolean selOuvert = expanded.contains(selectedGlobalIndex);
	        msg.append("\nLe titre sélectionné est ")
	           .append(selOuvert ? "développé." : "réduit.");
	    }

	    
	    java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(list);
		// Boîte modale, lisible par la barre braille, fermeture avec Échap
		dia.InfoDialog.show(owner, "Navigateur", msg.toString());
	    
	    
//	    announceAndRefocus(msg.toString(),15000);
//	    commandes.tts.speak(msg.toString(), 2.0f, true, false);
	}

	 	  
	  // Supprime un titre
	@SuppressWarnings("unused")
	private boolean supprimeTitre() {
	    if (!demanderConfirmationSuppression()) return false;
	    if (selectedGlobalIndex < 0 || selectedGlobalIndex >= titresOrdre.size()) return false;

	    int idx = selectedGlobalIndex;
	    int niveau = titresNiveaux.get(idx);
	    if (niveau == 0) return false;

	    // bornes du bloc (titre + descendants)
	    String contenu = editor.getText();
	    int start = titresOffsets.get(idx);
	    int idxSuiv = indiceSuivantMemeOuInferieur(idx, niveau);
	    int end = (idxSuiv >= 0) ? titresOffsets.get(idxSuiv) : contenu.length();

	    // snapshot expansions
	    java.util.Set<String> expSnapshot = snapshotExpandedKeys();

	    // garder un voisin pour reselection après suppression
	    String prevKey = null;
	    for (int p = idx - 1; p >= 0; p--) { prevKey = keyOf(titresOrdre.get(p)); break; }
	    String nextKey = (idxSuiv >= 0) ? keyOf(titresOrdre.get(idxSuiv)) : null;

	    // supprimer le bloc
	    String nouveau = contenu.substring(0, start) + contenu.substring(end);
	    editor.setText(nouveau);

	    // séquence complète
	    allTitle(nouveau);
	    restoreExpandedFromKeys(expSnapshot);
	    rebuildVisibleModel();

	    // choisir la cible de reselection
	    int target = -1;
	    if (prevKey != null) target = findIndexByKey(prevKey);
	    if (target < 0 && nextKey != null) target = findIndexByKey(nextKey);
	    if (target < 0 && !titresOrdre.isEmpty()) target = Math.min(idx, titresOrdre.size() - 1);

	    reselectByGlobalIndexLater(target, () -> {
	    	//"Titre et sous-parties supprimés."
	    });
	    return true;
	}


	  
		// Méthode pour afficher la boîte de dialogue de confirmation
	@SuppressWarnings("serial")
	public static boolean demanderConfirmationSuppression() {
    final String titre   = "Confirmation de suppression";
    final String message = "Voulez-vous vraiment supprimer ce titre et tous les sous-titres et textes associés ?";

    final Object[] options = { "Oui", "Non" };

    final JLabel msg = new JLabel(message);
    msg.setFocusable(true);
    
    final JOptionPane optionPane = new JOptionPane(
	        msg,
	        JOptionPane.QUESTION_MESSAGE,
	        JOptionPane.DEFAULT_OPTION,   // pas YES_NO ici
	        null,
	        options,
	        null                          // pas de valeur par défaut
	    );

    final JDialog dialog = optionPane.createDialog((Component) null, titre);
    dialog.setModal(true);
    
    // ESC => Non
    dialog.getRootPane().registerKeyboardAction(
        e -> optionPane.setValue(JOptionPane.NO_OPTION),
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW
    );
    
    // >>> Binding : Entrée clique le bouton qui a le focus
    InputMap im = dialog.getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    ActionMap am = dialog.getRootPane().getActionMap();
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pressFocusedButton");
    am.put("pressFocusedButton", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) {
            Component fo = java.awt.KeyboardFocusManager
                    .getCurrentKeyboardFocusManager().getFocusOwner();
            if (fo instanceof JButton) {
                ((JButton) fo).doClick();
            }
        }
    });
    // <<<
    
    // À l’ouverture : pas de bouton par défaut, focus sur le message (lecture NVDA)
    dialog.addWindowListener(new java.awt.event.WindowAdapter() {
        @Override public void windowOpened(java.awt.event.WindowEvent e) {
        	dialog.getRootPane().setDefaultButton(null); // Entrée n’active aucun "default"
            msg.requestFocusInWindow();
        }
    });    

    dialog.setVisible(true);

    Object val = optionPane.getValue();
    dialog.dispose();

    return "Oui".equals(val); // true=Oui, false=Non ou fermeture
}

	
	/** Confirmation accessible pour changement de niveau.
	 * @param increase true = augmenter, false = diminuer
	 * @param applyToBlock true = appliquer au bloc (titre + descendants), false = seulement la ligne de titre
	 * @param from niveau actuel
	 * @param to   niveau cible
	 * @param titre Lisible (par ex. "#2. Mon titre" ou seulement le texte)
	 */
	@SuppressWarnings("serial")
	public static boolean demanderConfirmationChangementNiveau(
	        boolean increase, boolean applyToBlock, int from, int to, String titre) {

	    final String titreDlg = "Confirmation de modification de niveau";
	    final String verbe    = increase ? "augmenter" : "réduire";
	    final String cible    = applyToBlock ? "le titre et ses sous-parties" : "le titre";
	    final String libTitre = (titre == null) ? "" : titre.trim();

	    final String message  = String.format(
	        "Voulez-vous vraiment %s le niveau de %s de %d à %d ?%s",
	        verbe, cible, from, to, libTitre.isEmpty() ? "" : ("\n\nTitre : " + libTitre)
	    );

	    final JLabel msg = new JLabel(message);
	    msg.setFocusable(true);

	    final Object[] options = { "Oui", "Non" };

	    final JOptionPane pane = new JOptionPane(
	        msg,
	        JOptionPane.QUESTION_MESSAGE,
	        JOptionPane.DEFAULT_OPTION,   // pas YES_NO ici
	        null,
	        options,
	        null                          // pas de valeur par défaut
	    );

	    final JDialog dlg = pane.createDialog((Component) null, titreDlg);
	    dlg.setModal(true);

	    // ESC => "Non"
	    dlg.getRootPane().registerKeyboardAction(
	        e -> pane.setValue("Non"),
	        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
	        JComponent.WHEN_IN_FOCUSED_WINDOW
	    );

	    // >>> Binding : Entrée clique le bouton qui a le focus
	    InputMap im = dlg.getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
	    ActionMap am = dlg.getRootPane().getActionMap();
	    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pressFocusedButton");
	    am.put("pressFocusedButton", new AbstractAction() {
	        @Override public void actionPerformed(ActionEvent e) {
	            Component fo = java.awt.KeyboardFocusManager
	                    .getCurrentKeyboardFocusManager().getFocusOwner();
	            if (fo instanceof JButton) {
	                ((JButton) fo).doClick();
	            }
	        }
	    });
	    // <<<

	    // À l’ouverture : pas de bouton par défaut, focus sur le message (lecture NVDA)
	    dlg.addWindowListener(new java.awt.event.WindowAdapter() {
	        @Override public void windowOpened(java.awt.event.WindowEvent e) {
	            dlg.getRootPane().setDefaultButton(null); // Entrée n’active aucun "default"
	            msg.requestFocusInWindow();
	        }
	    });

	    dlg.setVisible(true);
	    Object val = pane.getValue();
	    dlg.dispose();

	    return "Oui".equals(val); // true=Oui, false=Non ou fermeture
	}


 


  
	
    // Retourne le niveau du titre
    private int niveauDuTitre(String titre) {
        if (titre == null) return 0;
        Matcher m = Pattern.compile("^\\s*#([1-5])\\.", Pattern.DOTALL).matcher(titre);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
	    
	  
	  	  
  private boolean hasChildrenAt(int i) {
	    if (i < 0 || i >= titresNiveaux.size() - 1) return false;
	    int cur = titresNiveaux.get(i);
	    int nxt = titresNiveaux.get(i + 1);
	    return cur > 0 && nxt > cur;
	}


		
		private void reselectByGlobalIndexLater(int keepGlobal, Runnable after) {
		    SwingUtilities.invokeLater(() -> {
		        int viewIdx = -1;
		        if (keepGlobal >= 0) {
		            viewIdx = viewToGlobal.indexOf(keepGlobal);
		            if (viewIdx < 0) {
		                for (int g = keepGlobal; g >= 0; g--) {
		                    int vi = viewToGlobal.indexOf(g);
		                    if (vi >= 0) { viewIdx = vi; break; }
		                }
		            }
		        }
		        if (viewIdx >= 0) {
		            list.setSelectedIndex(viewIdx);
		            list.ensureIndexIsVisible(viewIdx);
		        } else if (listModel.getSize() > 0) {
		            list.setSelectedIndex(0);
		        }
		        updateSelectionContextAndSpeak(); // met à jour selectedGlobalIndex + TTS du titre

		        if (after != null) after.run();   // ➜ parler ici
		    });
		}

		// Déplca vers le haut un titre
		private boolean deplacerBlocVersHaut(int idx) {
		    if (idx < 0 || idx >= titresOrdre.size()) return false;

		    int niveau = titresNiveaux.get(idx);
		    if (niveau == 0) return false;

		    int prevIdx = indicePrecedentMemeOuInferieur(idx, niveau);
		    if (prevIdx < 0) return false; // déjà tout en haut pour ce niveau

		    String contenu = editor.getText();

		    // bornes du bloc à déplacer : [start, end)
		    int start = titresOffsets.get(idx);
		    int nextIdx = indiceSuivantMemeOuInferieur(idx, niveau);
		    int end = (nextIdx >= 0) ? titresOffsets.get(nextIdx) : contenu.length();

		    // 1) snapshot de l'état d’expansion pour ne pas le perdre
		    java.util.Set<String> expSnapshot = snapshotExpandedKeys();

		    // 2) extraire et supprimer le bloc
		    String bloc = contenu.substring(start, end);
		    StringBuilder sb = new StringBuilder(contenu);
		    sb.delete(start, end);
		    String sansBloc = sb.toString();

		    // 3) recalculer les tables sur le texte SANS le bloc (offsets changent)
		    allTitle(sansBloc);

		    // 4) insérer AVANT le précédent bloc de même ou inférieur niveau
		    int insertPos = titresOffsets.get(prevIdx);
		    String nouveau = new StringBuilder(sansBloc).insert(insertPos, bloc).toString();

		    // 5) appliquer le texte
		    editor.setText(nouveau);

		    // 6) reconstruire les tables sur le texte final, restaurer expansions, re-bâtir la vue
		    allTitle(nouveau);
		    restoreExpandedFromKeys(expSnapshot);
		    rebuildVisibleModel();

		    // 7) reselectionner le bloc déplacé : il est maintenant à l’index prevIdx
		    reselectByGlobalIndexLater(prevIdx, null);

		    return true;
		}
		
		// Déplace un titre vers le bas
		private boolean deplacerBlocVersBas(int idx) {
		    if (idx < 0 || idx >= titresOrdre.size()) return false;

		    int niveau = titresNiveaux.get(idx);
		    if (niveau == 0) return false;

		    // début/fin du bloc courant
		    String contenu = editor.getText();
		    int start = titresOffsets.get(idx);
		    int idxBreak = indiceSuivantMemeOuInferieur(idx, niveau); // 1er titre après le bloc courant (même ou plus haut niveau)
		    int end = (idxBreak >= 0) ? titresOffsets.get(idxBreak) : contenu.length();

		    // Le "frère" suivant est exactement idxBreak s'il est du même niveau
		    if (idxBreak < 0 || niveauDuTitre(titresOrdre.get(idxBreak)) != niveau) return false;

		    // Clés pour retrouver les blocs après recalcul
		    String movedKey   = keyOf(titresOrdre.get(idx));
		    String siblingKey = keyOf(titresOrdre.get(idxBreak));

		    // Sauver l'état d'expansion
		    java.util.Set<String> expSnapshot = snapshotExpandedKeys();

		    // Extraire/supprimer le bloc courant
		    String bloc = contenu.substring(start, end);
		    StringBuilder sb = new StringBuilder(contenu);
		    sb.delete(start, end);
		    String sansBloc = sb.toString();

		    // Recalculer sur le texte sans le bloc
		    allTitle(sansBloc);

		    // Retrouver le frère dans "sansBloc"
		    int sibIdx = findIndexByKey(siblingKey);
		    if (sibIdx < 0) return false; // ne devrait pas arriver

		    // Position d'insertion = fin du bloc du frère
		    int sibEndIdx = indiceSuivantMemeOuInferieur(sibIdx, niveau);
		    int insertPos = (sibEndIdx >= 0) ? titresOffsets.get(sibEndIdx) : sansBloc.length();

		    // Insérer le bloc après le frère
		    String nouveau = new StringBuilder(sansBloc).insert(insertPos, bloc).toString();
		    editor.setText(nouveau);

		    // Recalculer sur le texte final + restaurer expansions + rebâtir la vue
		    allTitle(nouveau);
		    restoreExpandedFromKeys(expSnapshot);
		    rebuildVisibleModel();

		    // Reselectionner le bloc déplacé
		    int newIdx = findIndexByKey(movedKey);
		    reselectByGlobalIndexLater(newIdx, null);

		    return true;
		}

		
		// Sauvegarde l'état 'expanded' via les clés normalisées des titres
		private java.util.Set<String> snapshotExpandedKeys() {
		    java.util.Set<String> keys = new java.util.HashSet<>();
		    for (Integer i : expanded) {
		        if (i != null && i >= 0 && i < titresOrdre.size()) {
		            keys.add(keyOf(titresOrdre.get(i)));
		        }
		    }
		    return keys;
		}

		// Restaure 'expanded' après recalcul des titres
		private void restoreExpandedFromKeys(java.util.Set<String> keys) {
		    expanded.clear();
		    if (keys == null || keys.isEmpty()) return;
		    for (int i = 0; i < titresOrdre.size(); i++) {
		        if (keys.contains(keyOf(titresOrdre.get(i))) && hasChildrenAt(i)) {
		            expanded.add(i);
		        }
		    }
		}

		private int findIndexByKey(String key) {
		    for (int i = 0; i < titresOrdre.size(); i++) {
		        if (keyOf(titresOrdre.get(i)).equals(key)) return i;
		    }
		    return -1;
		}

		

		// Surcharge de confort si tu veux l’appeler sans callback
		private void reselectByGlobalIndexLater(int keepGlobal) {
		    reselectByGlobalIndexLater(keepGlobal, null);
		}
	  
		// utilitaire : clé normalisée = sans espaces en début de ligne
		private static String keyOf(String s) {
		    return (s == null) ? "" : s.replaceFirst("^\\s+", "");
		}
		
		// visible si H1 ou si tous ses ancêtres sont dans expanded
		private boolean isVisible(int i) {
		    int p = parents.get(i);
		    while (p != -1) {
		        if (!expanded.contains(p)) return false;
		        p = parents.get(p);
		    }
		    return true;
		}

		private String indentFor(int i) {
		    int lvl = Math.max(1, titresNiveaux.get(i));
		    StringBuilder sb = new StringBuilder();
		    for (int k = 1; k < lvl; k++) sb.append("    "); // 4 espaces par niveau
		    return sb.toString();
		}

		private void rebuildVisibleModel() {
		    DefaultListModel<String> newModel = new DefaultListModel<>();
		    java.util.List<Integer> newViewToGlobal = new java.util.ArrayList<>();

		    

		    
		    for (int i = 0; i < titresOrdre.size(); i++) {
		        // NEW: si un root est “focus”, ignorer les autres racines
		        if (focusedRoot != null && topAncestorOf(i) != focusedRoot) continue;

		        if (!isVisible(i)) continue;
		        
		        if (focusedBranch != null) {
			        boolean keep = (i == focusedBranch) || isAncestorOf(i, focusedBranch) || isAncestorOf(focusedBranch, i);
			        if (!keep) continue;
			    }
		        
		        String raw = titresOrdre.get(i);
		        String indicator = hasChildrenAt(i)
		                ? (expanded.contains(i) ? "[-] " : "[+] ")
		                : "    ";
		        String label = indentFor(i) + indicator + raw;

		        newModel.addElement(label);
		        newViewToGlobal.add(i);
		    }
		    listModel = newModel;
		    viewToGlobal = newViewToGlobal;
		    list.setModel(listModel);
		}


		// Ancêtre racine (H1 en général)
		private int topAncestorOf(int i) {
		    if (i < 0 || i >= parents.size()) return -1;
		    int p = i;
		    while (parents.get(p) != -1) p = parents.get(p);
		    return p;
		}
		
		// Ctrl + - : tout réduire, puis reselectionner l’ancêtre racine du titre courant
		private void collapseAllKeepRootSelected() {
		    int keepGlobal = (selectedGlobalIndex >= 0) ? selectedGlobalIndex : 0;
		    int root = topAncestorOf(keepGlobal);
		    if (root < 0) root = 0;

		    expanded.clear();           // aucun nœud ouvert ⇒ seuls les racines sont visibles
		    
		    focusedBranch = null; // reset global

		    rebuildVisibleModel();
		    reselectByGlobalIndexLater(root, () -> {
		    	//"Tout réduit : titres de niveau 1."
		    });
		}

		// Ctrl + + : tout développer, puis garder la sélection
		private void expandAllKeepSelection() {
		    int keepGlobal = selectedGlobalIndex;

		    expanded.clear();
		    for (int i = 0; i < titresOrdre.size(); i++) {
		        if (hasChildrenAt(i)) expanded.add(i);
		    }
		    // NEW: montrer toutes les racines
		    focusedRoot = null;
		    
		    focusedBranch = null; // reset global

		    rebuildVisibleModel();
		    reselectByGlobalIndexLater(keepGlobal, () -> {
		        //if (commandes.audio) commandes.tts.speak("Tous les titres développés.", 2.0f, true, false);
		    });
		}

		
		private void expandSelected() {
		    int gi = selectedGlobalIndex;
		    if (gi >= 0 && hasChildrenAt(gi)) {
		        expanded.add(gi);        // le nœud lui-même
		        refocusOn(gi, "Titre développé. Branche courante seule.", 2000);
		    } else {
//		        announceAndRefocus("Aucun sous-titre à développer.", 2000);
		    }
		}


		private void collapseSelected() {
		    int gi = selectedGlobalIndex;
		    if (gi >= 0) {
		        expanded.remove(gi);
		        showSiblingsOnCollapse(gi);
		        focusedBranch = null;    // on ré-affiche les frères
		        rebuildVisibleModel();
		        reselectByGlobalIndexLater(gi); //announceAndRefocus("Titre réduit. Frères affichés.", 1800));
		    }
		}


		
		@SuppressWarnings("serial")
		private void setupKeyBindings() {
		    InputMap im = list.getInputMap(JComponent.WHEN_FOCUSED);
		    ActionMap am = list.getActionMap();

		    // "+" → développer le titre sélectionné
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ADD, 0), "expandSelected");                        // pavé num
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_EQUALS, java.awt.event.KeyEvent.SHIFT_DOWN_MASK), "expandSelected"); // rangée du haut (= + Shift → ‘+’ sur beaucoup de layouts)
		    im.put(KeyStroke.getKeyStroke('+'), "expandSelected"); // touche tapée (fallback)
		    am.put("expandSelected", new AbstractAction() {
		        @Override public void actionPerformed(ActionEvent e) {
		            expandSelected();
		            forceSRRefocus();
		        }
		    });

		    // "-" → réduire le titre sélectionné
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SUBTRACT, 0), "collapseSelected"); // pavé num
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, 0), "collapseSelected");    // rangée du haut
		    im.put(KeyStroke.getKeyStroke('-'), "collapseSelected"); // touche tapée (fallback)
		    am.put("collapseSelected", new AbstractAction() {
		        @Override public void actionPerformed(ActionEvent e) {
		        	collapseSelected();
		            forceSRRefocus();
		        }
		    });


		    // Ctrl + "+" → tout développer
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ADD, java.awt.event.KeyEvent.CTRL_DOWN_MASK), "expandAll");
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_EQUALS,
		            java.awt.event.KeyEvent.CTRL_DOWN_MASK | java.awt.event.KeyEvent.SHIFT_DOWN_MASK), "expandAll");
		    am.put("expandAll", new AbstractAction() {
		        @Override public void actionPerformed(ActionEvent e) {
//		            announceAndRefocus("Développement de tous les titres" , 2500);
		            expandAllKeepSelection();
		        }
		    });


		    // Ctrl + "-" → tout réduire (n’afficher que les H1)
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SUBTRACT, java.awt.event.KeyEvent.CTRL_DOWN_MASK), "collapseAll");
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, java.awt.event.KeyEvent.CTRL_DOWN_MASK), "collapseAll");
		    am.put("collapseAll", new AbstractAction() {
		        @Override public void actionPerformed(ActionEvent e) {
//		        	 announceAndRefocus("Réduction de tous les titres",2500);
		            collapseAllKeepRootSelected();
		        }
		    });
		    
		    // Supprimer le bloc sélectionné (DELETE)
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), "deleteBlock");
		    am.put("deleteBlock", new AbstractAction() {
		    	private static final long serialVersionUID = 1L;
		        @Override public void actionPerformed(ActionEvent e) {
		            // s'assurer que selectedGlobalIndex correspond à la sélection visible
		            updateSelectionContextAndSpeak();
		            if (!supprimeTitre()) {
//		                announceAndRefocus("Suppression annulée ou impossible." , 3000);
		            }
		        }
		    });
		    
		    // Bloc : F7 ↑  / F6 ↓
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F7, 0), "increaseLevel");
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F6, 0), "decreaseLevel");

		    // Titre seul : Maj+F7 ↑  / Maj+F6 ↓
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F7, java.awt.event.KeyEvent.SHIFT_DOWN_MASK), "increaseLevelTitleOnly");
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F6, java.awt.event.KeyEvent.SHIFT_DOWN_MASK), "decreaseLevelTitleOnly");
    
		    
		    // Bloc : F7 ↑  
		    am.put("increaseLevel", new AbstractAction() {
		        private static final long serialVersionUID = 1L;
		        @Override public void actionPerformed(ActionEvent e) {
		            increaseLevelOfSelectedBlock();
		        }
		    });
		    
		    // Bloc : F6 ↓
		    am.put("decreaseLevel", new AbstractAction() {
		        private static final long serialVersionUID = 1L;
		        @Override public void actionPerformed(ActionEvent e) {
		            decreaseLevelOfSelectedBlock();
		        }
		    });
		    
		    //Maj+F7 ↑
		    am.put("increaseLevelTitleOnly", new AbstractAction() {
		        private static final long serialVersionUID = 1L;
		        @Override public void actionPerformed(ActionEvent e) {
		            increaseLevelOfSelectedTitleOnly();
		        }
		    });
		    
		    //Maj+F6 ↓
		    am.put("decreaseLevelTitleOnly", new AbstractAction() {
		        private static final long serialVersionUID = 1L;
		        @Override public void actionPerformed(ActionEvent e) {
		            decreaseLevelOfSelectedTitleOnly();
		        }
		    });
		    
		    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "brailleRead");
		    am.put("brailleRead", new AbstractAction() {
		        @Override public void actionPerformed(ActionEvent e) { startBrailleReading(); }
		    });
		    
		    // F2 : annoncer l'état (réduit/développé) et lire le titre
		    im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0), "speakTitleState");
		    am.put("speakTitleState", new AbstractAction() {
		        private static final long serialVersionUID = 1L;
		        @Override public void actionPerformed(ActionEvent e) {
		            speakSelectedTitleState();
		        }
		    });
		    
		 // Ctrl + C : copier le bloc (titre + sous-parties) dans le presse-papiers
		    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_DOWN_MASK), "copyBlock");
		    am.put("copyBlock", new AbstractAction() {
		        private static final long serialVersionUID = 1L;
		        @Override public void actionPerformed(ActionEvent e) {
		            copySelectedBlockToClipboard();
		        }
		    });

		    
		    
   
		}

		
		// Augmente le niveau du titre sélectionné ET de tous ses descendants (bloc),
		// en le limitant à 5. Séquence de mise à jour respectée :
		// allTitle(...) → restoreExpandedFromKeys(...) → rebuildVisibleModel() → reselectByGlobalIndexLater(...)
		private void increaseLevelOfSelectedBlock() {
		    // Assure que selectedGlobalIndex correspond bien à la sélection
		    updateSelectionContextAndSpeak();

		    int idx = selectedGlobalIndex;
		    if (idx < 0 || idx >= titresOrdre.size()) {
//		        announceAndRefocus("Aucun titre sélectionné.", 1500);
		        return;
		    }

		    int lvl = titresNiveaux.get(idx);
		    if (lvl == 0) {
//		        announceAndRefocus("Élément non reconnu comme titre.", 2000);
		        return;
		    }
		    if (lvl >= 5) {
//		        announceAndRefocus("Ce titre est déjà au niveau maximum cinq.", 2000);
		        return;
		    }	    
		    
		    String titreBrut = titresOrdre.get(idx); // ex: "#2. Mon titre"
		    int newLevel = lvl + 1;
		    if (!demanderConfirmationChangementNiveau(true,true, lvl, newLevel, titreBrut)) {
//		        announceAndRefocus("Augmentation du niveau annulée.", 2000);
		        return;
		    }
		    
		    // Bornes du bloc (titre + descendants)
		    String contenu = editor.getText();
		    int start = titresOffsets.get(idx);
		    int idxBreak = indiceSuivantMemeOuInferieur(idx, lvl);
		    int end = (idxBreak >= 0) ? titresOffsets.get(idxBreak) : contenu.length();

		    String bloc = contenu.substring(start, end);

		    // Vérifier si on peut encore augmenter (aucun titre niveau 5 dans le bloc)
		    Pattern p = Pattern.compile("(?m)^\\s*#([1-5])\\..*$");
		    Matcher m = p.matcher(bloc);
		    int maxInBloc = 0;
		    while (m.find()) {
		        int k = Integer.parseInt(m.group(1));
		        if (k > maxInBloc) maxInBloc = k;
		    }
		    if (maxInBloc >= 5) {
		    	//"Impossible d'augmenter : un sous-titre est déjà au niveau cinq."
		        return;
		    }

		    // Snapshot des expansions pour les restaurer après
		    java.util.Set<String> expSnapshot = snapshotExpandedKeys();
		    boolean wasExpanded = expanded.contains(idx);

		    // Construire une version du bloc avec tous les niveaux +1
		    Pattern pLine = Pattern.compile("(?m)^(\\s*)#([1-5])(\\.)(.*)$");
		    Matcher ml = pLine.matcher(bloc);
		    StringBuffer sbBloc = new StringBuffer();
		    while (ml.find()) {
		        int k = Integer.parseInt(ml.group(2));
		        int newK = Math.min(5, k + 1); // sécurité ; on a déjà vérifié max < 5
		        String rep = ml.group(1) + "#" + newK + ml.group(3) + ml.group(4);
		        ml.appendReplacement(sbBloc, Matcher.quoteReplacement(rep));
		    }
		    ml.appendTail(sbBloc);
		    String blocShifted = sbBloc.toString();

		    // Construire le nouveau document
		    String nouveau = contenu.substring(0, start) + blocShifted + contenu.substring(end);
		    editor.setText(nouveau);

		    // Recalculs + resto expansions + rebuild
		    allTitle(nouveau);
		    restoreExpandedFromKeys(expSnapshot);

		    // Retrouver l’index du titre modifié (sa ligne a changé de #n. en #(n+1).)
		    String oldLine = titresOrdre.get(Math.min(idx, titresOrdre.size()-1)); // sécurité si taille changée
		    // Recalcule oldLine à partir du bloc modifié : plus robuste
		    // On refait la ligne attendue à partir de l’ancienne (avant changement) :
		    String originalLineBefore = oldLine; // (capturée avant allTitle si tu préfères)
		    // Si tu préfères la version simple, prends la première ligne de blocShifted :
		    // mais ici on fabrique la "nouvelle" ligne depuis l’ancienne valeur connue 'lvl'
		    String newTitleLine = originalLineBefore.replaceFirst("^\\s*#([1-5])\\.", "#" + (lvl + 1) + ".");

		    rebuildVisibleModel();

		    int newIdx = findIndexByExactLine(newTitleLine);
		    if (newIdx < 0) newIdx = idx; // fallback

		    if (wasExpanded && newIdx >= 0) {
		        expanded.add(newIdx); // si le titre était ouvert, on le rouvre
		        rebuildVisibleModel(); // refléter l’ouverture
		    }

		    @SuppressWarnings("unused")
			final int announceLevel = Math.min(5, lvl + 1);
		 // Re-focaliser la vue sur la branche du titre modifié
		    openAncestors(newIdx);
		    collapseSiblingsOf(newIdx);
		    focusedRoot = topAncestorOf(newIdx);
		    focusedBranch = newIdx;

		    rebuildVisibleModel();
		    refocusOn(newIdx, "Affichage recentré sur la branche courante.", 1800);

		}

		
		// Diminue le niveau du titre sélectionné ET de tous ses descendants (bloc), min = 1.
		// Séquence : allTitle(...) → restoreExpandedFromKeys(...) → rebuildVisibleModel() → reselectByGlobalIndexLater(...)
		private void decreaseLevelOfSelectedBlock() {
		    // Assure que selectedGlobalIndex correspond bien à la sélection
		    updateSelectionContextAndSpeak();

		    int idx = selectedGlobalIndex;
		    if (idx < 0 || idx >= titresOrdre.size()) {
		    	//"Aucun titre sélectionné."
		        return;
		    }

		    int lvl = titresNiveaux.get(idx);
		    if (lvl == 0) {
		    	//"Élément non reconnu comme titre."
		        return;
		    }
		    if (lvl <= 1) {
		    	//"Ce titre est déjà au niveau minimum un."
		        return;
		    }
		    
		    String titreBrut = titresOrdre.get(idx);
		    int newLevel = lvl - 1;
		    if (!demanderConfirmationChangementNiveau(false, /*applyToBlock*/true, lvl, newLevel, titreBrut)) {
		        //"Réduction du niveau annulée."
		        return;
		    }


		    // Bornes du bloc (titre + descendants)
		    String contenu = editor.getText();
		    int start = titresOffsets.get(idx);
		    int idxBreak = indiceSuivantMemeOuInferieur(idx, lvl);
		    int end = (idxBreak >= 0) ? titresOffsets.get(idxBreak) : contenu.length();

		    String bloc = contenu.substring(start, end);

		    // Vérifier si l’un des titres du bloc est déjà niveau 1 → on refuse
		    Matcher m = Pattern.compile("(?m)^\\s*#([1-5])\\..*$").matcher(bloc);
		    int minInBloc = Integer.MAX_VALUE;
		    while (m.find()) {
		        int k = Integer.parseInt(m.group(1));
		        if (k < minInBloc) minInBloc = k;
		    }
		    if (minInBloc <= 1) {
		    	//"Impossible de réduire : un sous-titre est déjà au niveau un."
		        return;
		    }

		    // Snapshot expansions pour les restaurer ensuite
		    java.util.Set<String> expSnapshot = snapshotExpandedKeys();
		    boolean wasExpanded = expanded.contains(idx);

		    // Construire une version du bloc avec tous les niveaux -1 (sans passer sous 1)
		    Pattern pLine = Pattern.compile("(?m)^(\\s*)#([1-5])(\\.)(.*)$");
		    Matcher ml = pLine.matcher(bloc);
		    StringBuffer sbBloc = new StringBuffer();
		    while (ml.find()) {
		        int k = Integer.parseInt(ml.group(2));
		        int newK = Math.max(1, k - 1);
		        String rep = ml.group(1) + "#" + newK + ml.group(3) + ml.group(4);
		        ml.appendReplacement(sbBloc, Matcher.quoteReplacement(rep));
		    }
		    ml.appendTail(sbBloc);
		    String blocShifted = sbBloc.toString();

		    // Construire le nouveau document
		    String nouveau = contenu.substring(0, start) + blocShifted + contenu.substring(end);
		    editor.setText(nouveau);

		    // Recalcul structures + restauration expansions + rebuild de la vue
		    allTitle(nouveau);
		    restoreExpandedFromKeys(expSnapshot);
		    rebuildVisibleModel();

		    // Retrouver l’index du titre modifié (sa ligne passe de #n. à #(n-1).)
		    String oldLine = titresOrdre.get(Math.min(idx, titresOrdre.size() - 1)); // sécurité
		    // Reconstituer la nouvelle ligne attendue depuis le niveau connu
		    String newTitleLine = oldLine.replaceFirst("^\\s*#([1-5])\\.", "#" + (lvl - 1) + ".");

		    int newIdx = findIndexByExactLine(newTitleLine);
		    if (newIdx < 0) newIdx = idx; // fallback

		    // Si le titre racine était développé, on le rouvre
		    if (wasExpanded && newIdx >= 0) {
		        expanded.add(newIdx);
		        rebuildVisibleModel();
		    }

		    @SuppressWarnings("unused")
			final int announceLevel = Math.max(1, lvl - 1);
		    // Re-focaliser la vue sur la branche du titre modifié
		    openAncestors(newIdx);
		    collapseSiblingsOf(newIdx);
		    focusedRoot = topAncestorOf(newIdx);
		    focusedBranch = newIdx;

		    rebuildVisibleModel();
		    refocusOn(newIdx, "Affichage recentré sur la branche courante.", 1800);


		}

		
		// Augmente uniquement le niveau de la ligne de titre sélectionnée (pas les descendants), max = 5.
		// Séquence : allTitle(...) → restoreExpandedFromKeys(...) → rebuildVisibleModel() → reselectByGlobalIndexLater(...)
		private void increaseLevelOfSelectedTitleOnly() {
		    updateSelectionContextAndSpeak();

		    int idx = selectedGlobalIndex;

		    int lvl = titresNiveaux.get(idx);
		    if (lvl <= 0) {
//		        announceAndRefocus("Ce titre est déjà au niveau minimum un.", 2100);
		        return;
		    }
		    if (lvl >= 5) {
//		        announceAndRefocus("Ce titre est déjà au niveau maximum cinq.", 2500);
		        return;
		    }
		    
		    String titreBrut = titresOrdre.get(idx);
		    int newLevel = lvl + 1;
		    if (!demanderConfirmationChangementNiveau(true, /*applyToBlock*/false, lvl, newLevel, titreBrut)) {
//		        announceAndRefocus("Augmentation du niveau annulée.", 2000);
		        return;
		    }


		    // Contenu courant
		    String contenu = editor.getText();

		    // Offsets de la LIGNE de titre
		    int start = titresOffsets.get(idx);
		    String oldLine = titresOrdre.get(idx); // la ligne telle que trouvée par allTitle()
		    int lineLen = oldLine.length();

		    // Nouvelle ligne : #n. → #(n+1).
		    String newLine = oldLine.replaceFirst("^\\s*#([1-5])\\.", "#" + (lvl + 1) + ".");

		    // Snapshot des expansions pour restauration
		    java.util.Set<String> expSnapshot = snapshotExpandedKeys();

		    // Remplacement dans le document (uniquement la ligne de titre)
		    String nouveau = contenu.substring(0, start) + newLine + contenu.substring(start + lineLen);
		    editor.setText(nouveau);

		    // Recalcul & restauration UI
		    allTitle(nouveau);
		    restoreExpandedFromKeys(expSnapshot);
		    rebuildVisibleModel();

		    // Retrouver la nouvelle position du même titre
		    int newIdx = findIndexByExactLine(newLine);
		    if (newIdx < 0) newIdx = idx; // fallback si égalités de lignes

		    @SuppressWarnings("unused")
			final int announceLevel = Math.min(5, lvl + 1);
		    
		    // Re-focaliser la vue sur la branche du titre modifié
		    openAncestors(newIdx);
		    collapseSiblingsOf(newIdx);
		    focusedRoot = topAncestorOf(newIdx);
		    focusedBranch = newIdx;

		    rebuildVisibleModel();
		    refocusOn(newIdx, "Affichage recentré sur la branche courante.", 1800);


		}

		
		// Réduit uniquement le niveau de la ligne de titre sélectionnée (pas les descendants), min = 1.
		// Séquence respectée : allTitle(...) → restoreExpandedFromKeys(...) → rebuildVisibleModel() → reselectByGlobalIndexLater(...)
		private void decreaseLevelOfSelectedTitleOnly() {
		    updateSelectionContextAndSpeak();

		    int idx = selectedGlobalIndex;
		    int lvl = titresNiveaux.get(idx);

		    if (lvl <= 1) {
//		    	 announceAndRefocus("Ce titre est déjà au niveau minimum un.", 2000);
		        return;
		    }
		    
		    String titreBrut = titresOrdre.get(idx);
		    int newLevel = lvl - 1;
		    if (!demanderConfirmationChangementNiveau(false, false, lvl, newLevel, titreBrut)) {
//		    	announceAndRefocus("Réduction du niveau annulée.", 2000);
		    	return;
		    }

		    String contenu = editor.getText();
		    int start = titresOffsets.get(idx);
		    String oldLine = titresOrdre.get(idx);
		    int lineLen = oldLine.length();

		    // Conserver l’éventuel espace en tête, ne changer que "#n."
		    Matcher mm = Pattern.compile("^(\\s*)#([1-5])\\.(.*)$").matcher(oldLine);
		    if (!mm.find()) {
		    	//"Impossible de réduire ce titre."
		        return;
		    }
		    String leading = mm.group(1);
		    String tail    = mm.group(3);
		    newLevel   = Math.max(1, lvl - 1);
		    String newLine = leading + "#" + newLevel + "." + tail;

		    // Sauvegarder l'état d'expansion et si ce titre était ouvert
		    java.util.Set<String> expSnapshot = snapshotExpandedKeys();
		    boolean wasExpanded = expanded.contains(idx);

		    // Remplacer uniquement la ligne de titre dans le document
		    String nouveau = contenu.substring(0, start) + newLine + contenu.substring(start + lineLen);
		    editor.setText(nouveau);

		    // Recalcul + restauration + rebuild
		    allTitle(nouveau);
		    restoreExpandedFromKeys(expSnapshot);
		    rebuildVisibleModel();

		    // Retrouver le nouvel index de ce titre
		    int newIdx = findIndexByExactLine(newLine);
		    if (newIdx < 0) newIdx = idx; // fallback

		    // S’il était développé, on le rouvre
		    if (wasExpanded && newIdx >= 0) {
		        expanded.add(newIdx);
		        rebuildVisibleModel();
		    }

			@SuppressWarnings("unused")
			final int announceLevel = newLevel;
		    
		    // Re-focaliser la vue sur la branche du titre modifié
		    openAncestors(newIdx);
		    collapseSiblingsOf(newIdx);
		    focusedRoot = topAncestorOf(newIdx);
		    focusedBranch = newIdx;

		    rebuildVisibleModel();
		    refocusOn(newIdx, "Affichage recentré sur la branche courante.", 1800);


		}

		// Annonce si le titre est réduit ou développé, lit le titre et indique des stats sur tout son bloc (sous-titres compris).
		private void speakSelectedTitleState() {
		    int viewIndex = list.getSelectedIndex();
		    java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(list);
		
		    if (viewIndex < 0 || viewIndex >= viewToGlobal.size()) {
		        dia.InfoDialog.show(owner, "Information titre", "Aucun titre sélectionné.");
		        return;
		    }
		
		    selectedGlobalIndex = viewToGlobal.get(viewIndex);
		    int idx = selectedGlobalIndex;
		    if (idx < 0 || idx >= titresOrdre.size()) {
		        dia.InfoDialog.show(owner, "Information titre", "Aucun titre sélectionné.");
		        return;
		    }
		
		    // Titre lisible (sans indicateur [+]/[-])
		    String titreBrut = titresOrdre.get(idx);
		    String titreLisible = new TraitementSonPourTTS(
		            titreBrut.replaceFirst("^\\s*(\\[\\+\\]|\\[\\-\\])\\s*", "").trim()
		    ).returnTexte;
		
		    int niveau = Math.max(1, titresNiveaux.get(idx));
		    boolean aDesEnfants = hasChildrenAt(idx);
		    boolean estOuvert   = expanded.contains(idx);
		
		    int nbSousTitres = countSubtitlesInBlock(idx);
		    String etat = !aDesEnfants ? "sans sous-titre" : (estOuvert ? "développé" : "réduit");
		    String libSousTitres = (nbSousTitres <= 1) ? "sous-titre" : "sous-titres";
		
		    // === Délimitation du bloc (titre + descendants) ===
		    String contenuDoc = editor.getText();
		    int lvl   = Math.max(1, titresNiveaux.get(idx));
		    int start = titresOffsets.get(idx);
		    int idxBreak = indiceSuivantMemeOuInferieur(idx, lvl); // 1er titre de même ou plus haut niveau après 'idx'
		    int end = (idxBreak >= 0) ? titresOffsets.get(idxBreak) : contenuDoc.length();
		
		    start = Math.max(0, Math.min(start, contenuDoc.length()));
		    end   = Math.max(start, Math.min(end,   contenuDoc.length()));
		
		    String bloc = contenuDoc.substring(start, end);
		
		    // === Statistiques ===
		    String[] lignes = bloc.split("\\R", -1);
		    int nbLignesPhysiques = 0; // lignes physiques non vides
		    int nbTitres          = 0; // lignes "#n."
		    int nbSautsDePage     = 0; // lignes "@..."
		
		    for (String li : lignes) {
		        String l = li.trim();
		        if (l.isEmpty()) continue;
		        nbLignesPhysiques++;
		        if (l.matches("^#[1-5]\\..*")) nbTitres++;
		        else if (l.matches("^@.*"))    nbSautsDePage++;
		    }
		    // Paragraphes (selon l'ancienne logique) = lignes de texte (ni titre, ni @)
		    int nbParagraphesCalc = Math.max(0, nbLignesPhysiques - nbTitres - nbSautsDePage);
		
		    // --- Ici on inverse l'affichage :
		    // "lignes" affichées = nbParagraphesCalc (donc les groupes / paragraphes)
		    // "paragraphes" affichés = nbLignesPhysiques (lignes physiques)
		    int affichLignes = nbParagraphesCalc;
		    int affichParagraphes = nbLignesPhysiques;
		
		    // Mots / caractères (hors espaces) : sans marqueurs de titre ni lignes @
		    String blocPourMots = bloc
		            .replaceAll("(?m)^\\s*#[1-5]\\.", " ") // enlève le préfixe de titre
		            .replaceAll("(?m)^\\s*@.*$", " ")      // enlève les lignes @… (sauts de page)
		            .replaceAll("[^\\p{L}\\p{N}'’_-]+", " ")
		            .trim();
		    int nbMots = blocPourMots.isEmpty() ? 0 : blocPourMots.split("\\s+").length;
		    int nbCaracteresHorsEspaces = blocPourMots.replaceAll("\\s+", "").length();
		
		    // Pluriels basés sur les valeurs affichées
		    String libLignes       = (affichLignes <= 1)      ? "paragraphe"       : "paragraphes";
		    String libTitres       = (nbTitres <= 1)          ? "titre"       : "titres";
		    String libParagraphes  = (affichParagraphes <= 1) ? "paragraphe"  : "paragraphes";
		    String libSauts        = (nbSautsDePage <= 1)     ? "saut de page": "sauts de page";
		    String libMots         = (nbMots <= 1)            ? "mot"         : "mots";
		    String libCars         = (nbCaracteresHorsEspaces <= 1) ? "caractère" : "caractères";
		
		    String message = String.format(
		    	    "#%d. %s%n" +
		    	    "État : %s.%n" +
		    	    "Sous-titres : %d %s.%n" +
		    	    "Bloc (titre + sous-parties) :%n" +
		    	    "- %d %s (non vides, hors titre et saut) : %d %s, %d %s, %d %s.%n" +
		    	    "- %d %s ; %d %s (hors espaces).",
		    	    niveau, titreLisible,
		    	    etat,
		    	    nbSousTitres, libSousTitres,
		    	    // ligne récap : "lignes" (affichLignes), nbTitres, "paragraphes" (affichParagraphes), sauts
		    	    affichLignes, libLignes, nbTitres, libTitres, affichParagraphes, libParagraphes, nbSautsDePage, libSauts,
		    	    // stats mots / caractères
		    	    nbMots, libMots, nbCaracteresHorsEspaces, libCars
		    	);

		
		    dia.InfoDialog.show(owner, "Information titre", message);
		}

		// Compte tous les sous-titres du bloc (descendants, tous niveaux > au niveau du titre courant)
		private int countSubtitlesInBlock(int idx) {
		    if (idx < 0 || idx >= titresOrdre.size()) return 0;
		    int lvl = titresNiveaux.get(idx);
		    int count = 0;
		    for (int i = idx + 1; i < titresOrdre.size(); i++) {
		        int l = titresNiveaux.get(i);
		        if (l <= lvl) break;   // on sort du bloc
		        count++;
		    }
		    return count;
		}
		
		// Trouve l'index de la ligne
		private int findIndexByExactLine(String line) {
		    if (line == null) return -1;
		    for (int i = 0; i < titresOrdre.size(); i++) {
		        if (line.equals(titresOrdre.get(i))) return i;
		    }
		    return -1;
		}
		
		// champs de la classe
		@SuppressWarnings("serial")
		private final JComponent silentFocus = new JComponent() {
		    @Override public AccessibleContext getAccessibleContext() {
		        return null; // pas d’accessibilité => NVDA reste silencieux
		    }
		};

		// Place le focus sur un bouton virtuel puis retour du Focus sur la liste du navigateur
		private void forceSRRefocus() {
		    if (!isShowing()) return;
		    // étape 1 : focus sur le composant muet (rien n’est annoncé)
		    @SuppressWarnings("unused")
			boolean ok = silentFocus.requestFocusInWindow();
		    // étape 2 : on rend tout de suite le focus à la liste
		    javax.swing.Timer t = new javax.swing.Timer(80, e -> {
		        list.requestFocusInWindow();
		    });
		    t.setRepeats(false);
		    t.start();
		}
		
		

		/** Replie tous les frères du nœud gi (même parent), sauf gi lui-même. */
		private void collapseSiblingsOf(int gi) {
		    if (gi < 0 || gi >= parents.size()) return;
		    int p = parents.get(gi); // parent commun (-1 si racine)
		    for (int i = 0; i < parents.size(); i++) {
		        if (i != gi && parents.get(i) == p) {
		            expanded.remove(i); // retirer des “ouverts” suffit à masquer toute leur branche
		        }
		    }
		}
		
		/** Après une réduction, montre les frères du même niveau. */
		private void showSiblingsOnCollapse(int gi) {
		    if (gi < 0 || gi >= parents.size()) return;
		    int par = parents.get(gi);
		    if (par == -1) {
		        // On vient de réduire un H1 → réafficher toutes les racines (tous les H1)
		        focusedRoot = null;
		    } else {
		        // Assure que le parent est ouvert pour voir tous les frères de même niveau
		        expanded.add(par);
		        // On garde focusedRoot tel quel (filtre sur la branche de H1 inchangé)
		    }
		}

		private void openAncestors(int i) {
		    int p = parents.get(i);
		    while (p != -1) { expanded.add(p); p = parents.get(p); }
		}
		private boolean isAncestorOf(int anc, int node) {
		    int p = parents.get(node);
		    while (p != -1) { if (p == anc) return true; p = parents.get(p); }
		    return false;
		}

		private void refocusOn(int gi, String announce, int ms) {
		    // Ouvrir ancêtres et replier les frères
		    openAncestors(gi);
		    collapseSiblingsOf(gi);

		    // Focus de branche + racine
		    focusedRoot = topAncestorOf(gi);
		    focusedBranch = gi;

		    // IMPORTANT : si le nœud a des enfants, il doit être "expanded"
		    if (hasChildrenAt(gi)) expanded.add(gi);

		    rebuildVisibleModel();
		    reselectByGlobalIndexLater(gi, () -> {
		        if (announce != null && !announce.isEmpty()) {
//		            announceAndRefocus(announce, ms);
		        }
		    });
		}
		
		/** Lance la lecture braille du titre sélectionné : focus dans sr1px. */
		private void startBrailleReading() {
		    int viewIndex = list.getSelectedIndex();
		    if (viewIndex < 0 || viewIndex >= viewToGlobal.size()) return;

		    int gi = viewToGlobal.get(viewIndex);
		    String msg = buildNavText(gi);

		    final String prefix = "INFO echap. ";
		    if (!msg.startsWith(prefix)) msg = prefix + msg;

		}

		
		/** Phrase lue/affichée pour un titre (même contenu que →, sans préfixe). */
		private String buildNavText(int globalIndex) {
		    if (globalIndex < 0 || globalIndex >= titresOrdre.size()) return "";
		    String raw = titresOrdre.get(globalIndex);              // ex: "[+] #1. Titre"
		    String base = raw.replaceFirst("^\\s*(\\[\\+\\]|\\[\\-\\])\\s*", "").trim();
		    String supplement = structure.getOrDefault(keyOf(base), "");
		    return new TraitementSonPourTTS(base).returnTexte + supplement;
		}

		/** Texte “contenu” sans indicateur, pour TTS/braille. */
		private String buildNavTextBase(int gi) {
		    if (gi < 0 || gi >= titresOrdre.size()) return "";
		    String raw  = titresOrdre.get(gi);
		    String base = raw.replaceFirst("^\\s*(\\[\\+\\]|\\[\\-\\])\\s*", "").trim();
		    String supplement = structure.getOrDefault(keyOf(base), "");
		    return new TraitementSonPourTTS(base).returnTexte + supplement;
		}

		/** Indicateur à préfixer dans la liste (vide si pas d’enfants). */
		private String indicatorFor(int gi) {
		    if (gi < 0 || gi >= titresOrdre.size()) return "";
		    return hasChildrenAt(gi) ? (expanded.contains(gi) ? "[-] " : "[+] ") : "";
		}

		/** Copie dans le presse-papiers le titre sélectionné et tout son bloc
		 * (jusqu’au prochain titre de même ou plus haut niveau). */
		private void copySelectedBlockToClipboard() {
		    // S’assurer que selectedGlobalIndex correspond bien à l’élément visible
		    updateSelectionContextAndSpeak();

		    int idx = selectedGlobalIndex;
		    java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(list);

		    if (idx < 0 || idx >= titresOrdre.size()) {
		        dia.InfoDialog.show(owner, "Copie", "Aucun titre sélectionné.");
		        return;
		    }

		    int niveau = titresNiveaux.get(idx);
		    if (niveau <= 0) {
		        dia.InfoDialog.show(owner, "Copie", "L'élément sélectionné n'est pas un titre.");
		        return;
		    }

		    // Délimiter le bloc : [start, end)
		    String contenu = editor.getText();
		    int start = titresOffsets.get(idx);
		    int idxSuiv = indiceSuivantMemeOuInferieur(idx, niveau); // 1er titre de même ou plus haut niveau
		    int end = (idxSuiv >= 0) ? titresOffsets.get(idxSuiv) : contenu.length();

		    // Sécurité bornes
		    start = Math.max(0, Math.min(start, contenu.length()));
		    end   = Math.max(start, Math.min(end,   contenu.length()));

		    // Extraire le bloc
		    String bloc = contenu.substring(start, end);

		    // Mettre dans le presse-papiers (texte brut)
		    try {
		        java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(bloc);
		        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);

		        // (Optionnel Linux/X11) : sélection primaire pour clic-molette
		        try {
		            java.awt.datatransfer.Clipboard xsel = java.awt.Toolkit.getDefaultToolkit().getSystemSelection();
		            if (xsel != null) xsel.setContents(sel, null);
		        } catch (Throwable ignore) { /* non dispo sur Windows/macOS => ignorer */ }

		        dia.InfoDialog.show(owner, "Copie", "Titre et contenu copiés dans le presse-papiers.");
		    } catch (Exception ex) {
		        ex.printStackTrace();
		        dia.InfoDialog.show(owner, "Copie", "Échec de la copie dans le presse-papiers.");
		    }
		}


}
