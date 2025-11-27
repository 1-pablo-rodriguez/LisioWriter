package dia;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;

import writer.commandes;
import writer.ui.EditorFrame;

public class BoiteSaveAs extends JFrame {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final DefaultListModel<File> model = new DefaultListModel<>();
    private final JList<File> fileList = new JList<>(model);
    private static boolean isParentEntry(File f) { return f != null && "..".equals(f.getName()) && !f.isAbsolute(); }
    private static File parentEntry() { return new File(".."); }
    private java.awt.KeyEventDispatcher srDismissDispatcher;
    private javax.swing.Timer srTimer;
    private volatile boolean srActive = false;
    private final JLabel status = new JLabel(" ");
    private String typed = "";
    private long lastTypeTs = 0L;
    private static final int TYPE_DELAY_MS = 600; // 1 s entre deux frappes
    
	// Variable pour suivre le répertoire courant
	public static boolean isOKtoSaveAs = false;
	
	// Champ de saisie du nom de fichier (focusable pour la braille)
	private final javax.swing.JTextField nameField = new javax.swing.JTextField(32);

	// Réglages "grands caractères" de la boîte
	// ——— Thème basse vision (taille à ajuster si besoin)
	private static final java.awt.Font  LV_FONT_LIST   = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 28);
	private static final java.awt.Font  LV_FONT_STATUS = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 24);
	private static final java.awt.Font  LV_FONT_BTN    = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 26);
	private static final java.awt.Dimension LV_BTN_SIZE   = new java.awt.Dimension(220, 60);
	private static final java.awt.Dimension LV_FRAME_SIZE = new java.awt.Dimension(1100, 800);

	// 1 caractère = 1 cellule braille
	private static final String BRAILLE_ICON = "\u28FF"; // ⣿
	//private static final String FALLBACK_ICON = "•";     // si police ne supporte pas le braille
	
	 // ——— Lecteurs : nom/icone système ———
    private static final FileSystemView FSV = FileSystemView.getFileSystemView();
    
    private EditorFrame parent;
	
	@SuppressWarnings("serial")
	public BoiteSaveAs(EditorFrame parent) {
		this.parent = parent;
		
	    setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
	    setTitle("Enregistrer sous");
	
	    // --- Layout principal
	    JPanel contentPane = new JPanel(new BorderLayout());
	    setContentPane(contentPane);                  // ✅ manquait
	
	 // --- En haut : SR braille + ligne "Nom du fichier"
	    JPanel north = new JPanel(new BorderLayout());

	    JPanel namePanel = new JPanel(new BorderLayout(8, 8));
	    JLabel nameLabel = new JLabel("Nom du fichier :");
	    nameLabel.setFont(LV_FONT_STATUS);
	    nameField.setFont(LV_FONT_LIST);
	    nameLabel.setLabelFor(nameField);
	    nameField.getAccessibleContext().setAccessibleName("Nom du fichier");
//	    nameField.getAccessibleContext().setAccessibleDescription("Saisir un nom : ");

	    namePanel.add(nameLabel, BorderLayout.WEST);
	    namePanel.add(nameField, BorderLayout.CENTER);
	    north.add(namePanel, BorderLayout.SOUTH);
	    contentPane.add(north, BorderLayout.NORTH);

	
	    // --- Liste + renderer
	    fileList.getAccessibleContext().setAccessibleName("Liste des dossiers et fichiers.");
	    fileList.getAccessibleContext().setAccessibleDescription("Affiche la liste des dossiers et fichiers.");
	    fileList.setCellRenderer(new FileRenderer());
	    contentPane.add(new JScrollPane(fileList), BorderLayout.CENTER);   // ✅ une seule fois
	
	    fileList.addKeyListener(new java.awt.event.KeyAdapter() {
	    	  @Override public void keyTyped(java.awt.event.KeyEvent e) {
	    	    if (srActive) return;                  // si bandeau actif, on laisse ton dispatcher gérer
	    	    char ch = e.getKeyChar();
	    	    if (Character.isISOControl(ch)) return;

	    	    long now = System.currentTimeMillis();
	    	    if (now - lastTypeTs > TYPE_DELAY_MS) typed = ""; // nouveau mot
	    	    typed += ch;
	    	    lastTypeTs = now;

	    	    selectFirstStartingWith(typed);
	    	  }
	    	});

	    
	    // --- Barre d’état + boutons en bas (SOUTH)
	    JLabel status = this.status; // déjà créé en champ
	    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
	    JButton saveButton = new JButton("Enregistrer");
	    JButton cancelButton = new JButton("Annuler");
	    buttonPanel.add(saveButton);
	    buttonPanel.add(cancelButton);
	
	    JPanel south = new JPanel(new BorderLayout(8, 8)); // ✅ conteneur commun
	    south.add(status, BorderLayout.CENTER);
	    south.add(buttonPanel, BorderLayout.EAST);
	    contentPane.add(south, BorderLayout.SOUTH);
	
	    // --- Actions boutons
	    saveButton.addActionListener(e -> {
	        // Nom depuis la zone de texte
	        String base = nameField.getText().trim();
	        if (base.isEmpty()) {
	            announceHere("Nom de fichier vide. Saisissez un nom.", true, true);
	            nameField.requestFocusInWindow();
	            nameField.selectAll();
	            return;
	        }
	        // Interdire quelques caractères Windows / généraux
	        if (base.matches(".*[\\\\/:*?\"<>|].*")) {
	            announceHere("Nom invalide. Caractères interdits : \\ / : * ? \" < > |", true, true);
	            nameField.requestFocusInWindow();
	            nameField.selectAll();
	            return;
	        }

	        // Extension .bwr automatique si absente
	        String fileName = base.toLowerCase().endsWith(".bwr") ? base : base + ".bwr";

	        // Cible dans le dossier courant
	        File target = new File(commandes.currentDirectory, fileName);

	        // Confirmer si le fichier existe
	        if (target.exists()) {
	            String body = "Le fichier " + target.getName() + " existe déjà. Écraser ?";
	            boolean ok = confirmWithBrailleDialog(body, "Confirmer l’écrasement");
	            if (!ok) {
	                refocusListAndAnnounce("Enregistrement annulé.");
	                return;
	            }
	        }

	        typed = "";
	        saveTo(target);
	    });

	    nameField.getActionMap().put("enter", new AbstractAction() {
	        @Override public void actionPerformed(ActionEvent e) { saveButton.doClick(); }
	    });

	    cancelButton.addActionListener(e -> fermeture());
	    
	    for (JButton b : new JButton[]{ saveButton, cancelButton }) {
	        b.getInputMap(JComponent.WHEN_FOCUSED)
	         .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter");
	        b.getActionMap().put("enter", new AbstractAction() {
	            @Override public void actionPerformed(ActionEvent e) { b.doClick(); }
	        });
	        b.setFont(LV_FONT_BTN);
	        b.setPreferredSize(LV_BTN_SIZE);
	    }
	    
	    // Un peu d’air autour de la liste (facultatif)
	    ((JScrollPane)fileList.getParent().getParent())
	        .setBorder(javax.swing.BorderFactory.createEmptyBorder(8,8,8,8));
	    
	    saveButton.addFocusListener(new java.awt.event.FocusAdapter() {
	        @Override public void focusGained(FocusEvent e) { cancelSrLocal(); }
	    });
	    cancelButton.addFocusListener(new java.awt.event.FocusAdapter() {
	        @Override public void focusGained(FocusEvent e) { cancelSrLocal(); }
	    });

	
	    // — ESC dans le champ "Nom du fichier" => fermer la fenêtre
	    nameField.getInputMap(JComponent.WHEN_FOCUSED)
	             .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
	    nameField.getActionMap().put("cancel", new AbstractAction() {
	        @Override public void actionPerformed(ActionEvent e) {
	            fermeture();
	        }
	    });

	    
	    // --- Raccourcis sur la liste
	    var im = fileList.getInputMap(JComponent.WHEN_FOCUSED);
	    var am = fileList.getActionMap();
	    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openOrSave");
	    am.put("openOrSave", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { openOrSave(); }});
	    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "goParent");
	    am.put("goParent", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { goParentWithAnnounce(); }});
	    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "showPath");
	    am.put("showPath", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { showPathOnBraille(); }});
	    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
	    am.put("cancel", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { fermeture(); }});
	
	    // --- Taille/position
	    setSize(700, 500);
	    setLocationRelativeTo(null);
	
	    // --- Charger le dossier de départ AVANT d’afficher
	    File start = (commandes.currentDirectory != null)
	        ? commandes.currentDirectory
	        : new File(System.getProperty("user.home"));
	    loadDirectory(start);
	
	    // --- Focus + annonce initiale, puis afficher
	    SwingUtilities.invokeLater(() -> {
	        fileList.requestFocusInWindow();
	        File dir = commandes.currentDirectory;
	        String msg = (dir != null)
	            ? "Enregistrer sous — Dossier " + dir.getName()
	            : "Enregistrer sous — Racine système";
	        announceHere(msg, true, true);
	    });
	    

	    // ----- CHAMP 'Nom du fichier' (basse vision + accessibilité)
	    nameLabel.setFont(LV_FONT_STATUS);
	    nameField.setFont(LV_FONT_LIST);

	    // Valeur par défaut
	    String defName = (commandes.nameFile != null && !commandes.nameFile.isBlank())
	            ? commandes.nameFile
	            : "document";
	    nameField.setText(defName);

	    // Accessibilité
	    nameLabel.setLabelFor(nameField);
	    nameField.getAccessibleContext().setAccessibleName("Nom du fichier");
//	    nameField.getAccessibleContext().setAccessibleDescription("Saisir un nom : ");

	    // Entrée dans le champ => clic sur Enregistrer
	    nameField.getInputMap(JComponent.WHEN_FOCUSED)
	             .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter");
	    nameField.getActionMap().put("enter", new AbstractAction() {
	        @Override public void actionPerformed(ActionEvent e) { /* défini après saveButton */ }
	    });

	    // Binding global F1 (peu importe quel composant a le focus)
	    JRootPane root = getRootPane();
	    InputMap gim = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
	    ActionMap gam = root.getActionMap();
	    gim.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "showPathGlobal");
	    gam.put("showPathGlobal", new AbstractAction() {
	        @Override public void actionPerformed(ActionEvent e) { showPathOnBraille(); }
	    });

	    
	    // Liste : grosse police + lignes plus hautes
        fileList.setFont(LV_FONT_LIST);
        // une hauteur confortable (≈ 1.4 × taille police)
        fileList.setFixedCellHeight((int) Math.round(LV_FONT_LIST.getSize() * 1.4));

        // Barre d’état et boutons
        status.setFont(LV_FONT_STATUS);

        // Fenêtre plus grande
        setMinimumSize(LV_FRAME_SIZE);
        setSize(LV_FRAME_SIZE);
        setLocationRelativeTo(null);
	
	    setVisible(true);
	}

	private void fermeture() {
	    cancelSrLocal();            
	    SwingUtilities.invokeLater(() -> {
            parent.requestFocus();
            parent.getEditor().requestFocusInWindow();
        });
	    dispose();
	}

    
	private void loadDirectory(File dir) {
	    model.clear();
	    typed = "";

	    if (dir == null) {
	        // ——— RACINES ———
	        File[] roots = File.listRoots();
	        if (roots != null) for (File r : roots) model.addElement(r);
	        commandes.currentDirectory = null;
	        updateStatusRoots();
	        if (!model.isEmpty()) fileList.setSelectedIndex(0);
	        return;
	    }

	    // ——— DOSSIER NORMAL ———
	    commandes.currentDirectory = dir.getAbsoluteFile();

	    if (dir.getParentFile() != null) model.addElement(parentEntry());

	    File[] list = dir.listFiles();
	    if (list != null) {
	        for (File f : list) {
	            if (f.isDirectory() || f.getName().toLowerCase().endsWith(".bwr")) {
	                model.addElement(f);
	            }
	        }
	    }
	    if (!model.isEmpty()) {
	        int sel = isParentEntry(model.firstElement()) && model.size() > 1 ? 1 : 0;
	        fileList.setSelectedIndex(sel);
	    }
	    updateStatusDir(dir);
	}

	private void updateStatusDir(File dir) {
	    int folders = 0, files = 0;
	    for (int i = 0; i < model.size(); i++) {
	        File f = model.get(i);
	        if (isParentEntry(f)) continue;
	        if (f.isDirectory()) folders++; else files++;
	    }
	    String txt = String.format("%s — %d éléments : %d dossier(s), %d fichier(s) .bwr",
	            dir.getAbsolutePath(), folders + files, folders, files);
	    status.setText(txt);
//	    status.getAccessibleContext().setAccessibleDescription(txt);
	}

	private void updateStatusRoots() {
	    File[] rs = File.listRoots();
	    int count = (rs == null) ? 0 : rs.length;
	    StringBuilder details = new StringBuilder();
	    if (rs != null) {
	        for (int i = 0; i < rs.length; i++) {
	            if (i > 0) details.append(" · ");
	            details.append(driveDisplayName(rs[i]));
	        }
	    }
	    String txt = (count > 0)
	        ? String.format("Racines système — %d lecteur(s) : %s", count, details)
	        : "Racines système — aucun lecteur détecté.";
	    status.setText(txt);
//	    status.getAccessibleContext().setAccessibleDescription(txt);
	}


    @SuppressWarnings("serial")
    private static final class FileRenderer extends javax.swing.DefaultListCellRenderer {
      @Override public java.awt.Component getListCellRendererComponent(
          JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setFont(list.getFont());

        if (value instanceof File f) {
          String txt;
          if (isParentEntry(f)) {
              txt = ".. (Dossier parent)";
          } else if (f.isDirectory()) {
              if (f.getParentFile() == null) {               // racine/lecteur
                  txt = "[Lecteur] " + driveDisplayName(f);
              } else {
                  txt = "[Dossier] " + f.getName();
              }
          } else {
              txt = "[Fichier] " + f.getName();
          }
          setText(txt);

          // icône système (montre un disque pour les lecteurs)
          setIcon(FSV.getSystemIcon(f));

          var ac = getAccessibleContext();
          if (ac != null) {
            ac.setAccessibleName(txt);
            ac.setAccessibleDescription(isSelected ? "sélectionné" : "non sélectionné");
          }
        }
        return this;
      }
    }

    
    private void announceHere(String msg, boolean autoHide, boolean takeFocus) {


        if (!takeFocus) return;

        SwingUtilities.invokeLater(() -> {
            srActive = autoHide;

            srDismissDispatcher = new java.awt.KeyEventDispatcher() {
                @Override public boolean dispatchKeyEvent(java.awt.event.KeyEvent e) {
                    if (!srActive || e.getID()!=java.awt.event.KeyEvent.KEY_PRESSED) return false;
                    int kc = e.getKeyCode();

                    // Fermer l’annonce pour TOUT sauf ← → (pagination braille possible)
                    boolean isModifier = kc==KeyEvent.VK_SHIFT || kc==KeyEvent.VK_CONTROL
                                      || kc==KeyEvent.VK_ALT   || kc==KeyEvent.VK_ALT_GRAPH
                                      || kc==KeyEvent.VK_META;
                    if (!isModifier && kc!=KeyEvent.VK_LEFT && kc!=KeyEvent.VK_RIGHT) {
                        e.consume();
                        cancelSrLocal();
                        fileList.requestFocusInWindow();

                        // Espace : on veut monter d’un cran ET annoncer
                        if (kc == KeyEvent.VK_SPACE) { goParentWithAnnounce(); return true; }

                        // Redispatcher la touche à la liste (↑/↓ etc.)
                        java.awt.EventQueue.invokeLater(() -> fileList.dispatchEvent(new java.awt.event.KeyEvent(
                                fileList, java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                                e.getModifiersEx(), kc, e.getKeyChar(), e.getKeyLocation()
                        )));
                        return true;
                    }
                    return false;
                }
            };
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(srDismissDispatcher);
        });
    }

    private void cancelSrLocal() {
        if (srTimer != null) { srTimer.stop(); srTimer = null; }
        if (srDismissDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(srDismissDispatcher);
            srDismissDispatcher = null;
        }
        srActive = false;
    }



    private void openOrSave() {
        File sel = fileList.getSelectedValue();
        if (sel == null) return;

        if (isParentEntry(sel)) { goParentWithAnnounce(); return; }

        if (sel.isDirectory()) {
            loadDirectory(sel);
            announceHere("Dossier : " + sel.getName(), true, false); // navigation : ne pas voler le focus
            return;
        }

        // Fichier .bwr → enregistrer (confirmer si existant)
        if (sel.isFile() && sel.getName().toLowerCase().endsWith(".bwr")) {
        	if (sel.exists()) {
        	    String body = "Le fichier " + sel.getName() + " existe déjà. Écraser ?";
        	    boolean ok = confirmWithBrailleDialog(body, "Confirmer l’écrasement");
        	    if (!ok) {
        	    	refocusListAndAnnounce("Enregistrement annulé.");
        	        SwingUtilities.invokeLater(() -> announceHere("Écrasement annulé. Retour sur " + sel.getName(), true, true));
        	        return;
        	    }
        	}
            typed = "";
            // Mettre à jour la zone de texte avec le nom choisi (sans le chemin)
            nameField.setText(stripExt(sel.getName()));

            saveTo(sel);
            return;
        }
    }

    private void goParentWithAnnounce() {
        File cur = commandes.currentDirectory;
        File parent = (cur!=null) ? cur.getParentFile() : null;
        if (parent == null) { // racines
            loadDirectory(null);
            announceHere("Ouvrir — Racine système", true, true);
        } else {
            loadDirectory(parent);
            announceHere("Ouvrir — Dossier " + parent.getName(), true, true);
        }
    }

    private void showPathOnBraille() {
        if (commandes.currentDirectory == null) {
            announceHere(icon() + "Racines système", true, true);
            return;
        }
        String path = commandes.currentDirectory.getAbsolutePath();
        announceHere(icon() + path, true, true);
    }


    private void saveTo(File targetFile) {
        commandes.currentDirectory = targetFile.getParentFile().getAbsoluteFile();
        commandes.nameFile = stripExt(targetFile.getName());
        new writer.enregistre(commandes.nameFile, parent);
        
        fermeture(); // rend le focus à l'éditeur

        System.out.println("Fichier enregistré : " + targetFile.getName());
        
    }


    private void selectFirstStartingWith(String prefix) {
        if (prefix == null || prefix.isEmpty()) return;
        String p = prefix.toLowerCase();

        for (int i = 0; i < model.size(); i++) {
            File f = model.get(i);
            if (isParentEntry(f)) continue;

            String name;
            if (f.getParentFile() == null) {           // lecteur
                name = driveDisplayName(f).toLowerCase();
            } else {
                name = f.getName().toLowerCase();
            }

            if (name.startsWith(p)) {
                fileList.setSelectedIndex(i);
                fileList.ensureIndexIsVisible(i);
                return;
            }
        }
    }


    /** Boîte de confirmation accessible : affiche le message dans une SRAnnouncerArea
     *  focusable (braille) et retourne true si l'utilisateur choisit OUI.
     */
    @SuppressWarnings("serial")
    private boolean confirmWithBrailleDialog(String body, String title) {
    	
    	cancelSrLocal(); // pas d’interception de touches pendant la boîte

        JDialog dlg = new JDialog(this, title, true);
        dlg.setModalityType(java.awt.Dialog.ModalityType.DOCUMENT_MODAL);
        dlg.setLayout(new BorderLayout(16, 16));
        dlg.getRootPane().setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 20, 16, 20));
        dlg.setResizable(true);


        // --- 2) Texte VISUEL grand pour la basse vision (pas de doublon à l’écran)
        String html = "<html><body style='width:100%;'>" +
                      (body == null ? "" : body.replace("&","&amp;")
                                            .replace("<","&lt;")
                                            .replace(">","&gt;")
                                            .replace("\n","<br/>")) +
                      "</body></html>";
        JLabel bigText = new JLabel(html);
        bigText.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 28));
        JScrollPane centerScroll = new JScrollPane(bigText,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        centerScroll.setBorder(null);
        dlg.add(centerScroll, BorderLayout.CENTER);

        // --- 3) Boutons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 8));
        JButton yes = new JButton("Oui");
        JButton no  = new JButton("Non");
        java.awt.Font btnFont = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 26);
        java.awt.Dimension BTN_SIZE = new java.awt.Dimension(180, 56);
        for (JButton b : new JButton[]{ yes, no }) {
            b.setFont(btnFont);
            b.setPreferredSize(BTN_SIZE);
        }
        buttons.add(yes);
        buttons.add(no);
        dlg.add(buttons, BorderLayout.SOUTH);

        final boolean[] result = { false };
        yes.addActionListener(ev -> { result[0] = true;  dlg.dispose(); });
        no.addActionListener(ev  -> { result[0] = false; dlg.dispose(); });

        // Entrée = doClick() sur le bouton focus
        for (JButton b : new JButton[]{ yes, no }) {
            b.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter");
            b.getActionMap().put("enter", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { b.doClick(); }
            });
            b.setFont(LV_FONT_BTN);
            b.setPreferredSize(LV_BTN_SIZE);
        }
        // Échap = Non
        dlg.getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        dlg.getRootPane().getActionMap().put("esc", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { no.doClick(); }
        });
    
        // Taille confortable
        dlg.setMinimumSize(new java.awt.Dimension(760, 320));
        dlg.setPreferredSize(new java.awt.Dimension(900, 360));
        dlg.pack();
        java.awt.Dimension s = dlg.getSize();
        dlg.setSize(Math.max(s.width, 900), Math.max(s.height, 360));
        dlg.setLocationRelativeTo(this);

        
        dlg.setVisible(true);

        cancelSrLocal(); // nettoyage
        return result[0];
    }


    private void refocusListAndAnnounce(String msg) {
        SwingUtilities.invokeLater(() -> {
            fileList.requestFocusInWindow();     // la liste reprend le focus
            announceHere(msg, /*autoHide*/ true, /*takeFocus*/ false); // puis on annonce (srLocal prendra le focus brièvement)
        });
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return (i > 0) ? name.substring(0, i) : name;
    }

    private String icon() {
        // Optionnel : tester si la police courante supporte ⣿, sinon fallback
        return BRAILLE_ICON; // ou FALLBACK_ICON
    }
    

    /** Nom lisible du lecteur/racine (ex. "Windows (C:)", "Macintosh HD"). */
    private static String driveDisplayName(File root) {
        if (root == null) return "";
        if (root.getParentFile() != null) return root.getName(); // pas une racine, juste un dossier
        String n = FSV.getSystemDisplayName(root);
        if (n != null) n = n.trim();
        if (n != null && !n.isEmpty()) return n;

        // Fallbacks
        String path = root.getAbsolutePath();
        if (path.matches("^[A-Z]:\\\\$")) return "Lecteur " + path.substring(0,1) + " (" + path.substring(0,1) + ":)";
        if ("/".equals(path)) return "Disque système";
        return path;
    }

    

}