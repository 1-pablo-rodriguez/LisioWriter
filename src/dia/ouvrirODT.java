package dia;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;
import javax.swing.text.Position;

import Import.OdtReader;
import writer.commandes;
import writer.model.Affiche;
import writer.ui.EditorFrame;
import writer.ui.editor.FastHighlighter;
import writer.util.RecentFilesManager;


/** Boîte "Ouvrir" accessible (clavier/lecteur d’écran). */
@SuppressWarnings("serial")
public final class ouvrirODT extends JDialog {
    private static final long serialVersionUID = 1L;

    // ——— Modèle et widgets ———
    private final DefaultListModel<File> model = new DefaultListModel<>();

	private final JList<File> fileList = new JList<>(model) {
        @Override
        public int getNextMatch(String prefix, int startIndex, Position.Bias bias) {
            return -1; // désactive la recherche intégrée de Swing
        }
    };
    private final JLabel status = new JLabel(" ");

    // ——— État ———
    private final File savedDirectory;       // répertoire au lancement (pour ESC)
    private String typed = "";               // saisie incrémentale
    private long lastTypeTs = 0L;            // timestamp dernière frappe
    private static final int TYPE_DELAY_MS = 1000;

    // ——— Réglages "grands caractères" ———
    private static final String UI_FONT_FAMILY = "Segoe UI"; // ou "Arial"
    private static final int UI_FONT_SIZE_LIST = 28;         // taille de base de la liste
    private static final int UI_FONT_SIZE_STATUS = 24;       // barre d’état
    private static final int UI_ROW_HEIGHT = 44;        
    
    // ---- Braille paging ----
    private static final int BRAILLE_WINDOW = 32;  // taille visible de la barre braille
    private static final int BRAILLE_OVERLAP = 4;  // petit chevauchement pour le confort
    private int brailleOffset = 0;                 // offset courant dans le nom sélectionné

    // local annonce
    private static java.awt.KeyEventDispatcher srDismissDispatcher;
    private static javax.swing.Timer srTimer;
	private static volatile boolean srActive = false;
	
	// ——— Noms lisibles de lecteurs (Windows, macOS, Linux) ———
	private static final FileSystemView FSV = FileSystemView.getFileSystemView();
	
	// Icônes standard (fallbacks UI)
	private static final Icon ICON_PARENT = UIManager.getIcon("FileChooser.upFolderIcon");
	private static final Icon ICON_FOLDER = UIManager.getIcon("FileView.directoryIcon");
	private static final Icon ICON_FILE   = UIManager.getIcon("FileView.fileIcon");
	
	private EditorFrame parent;
	
	public ouvrirODT(EditorFrame parent, boolean OuvreSansFenetre){
		this.parent = parent;
		this.savedDirectory = null;
	}
    
	public ouvrirODT(EditorFrame parent){
        super(parent);
		this.parent = parent;
		
        setTitle("Ouvrir un fichier");
        setModal(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // Point de départ : dossier courant connu de l’app
        File start;
        if (commandes.currentDirectory != null) {
            start = commandes.currentDirectory.getAbsoluteFile();
        } else if (commandes.nomDossierCourant != null) {
            start = new File(commandes.nomDossierCourant);
            if (!start.isDirectory()) start = start.getParentFile(); // au cas où c’est un fichier
        } else {
            start = new File(System.getProperty("user.home"));
        }
        this.savedDirectory = start;

        // Liste
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setVisibleRowCount(18);
        fileList.setCellRenderer(new FileRenderer());
//        fileList.getAccessibleContext().setAccessibleName("Liste des dossiers et fichiers.");
//        fileList.getAccessibleContext().setAccessibleDescription("Utilisez flèches, Entrée, Espace et Retour arrière pour naviguer.");
        add(new JScrollPane(fileList), BorderLayout.CENTER);      
        
        // Barre d’état
        status.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(status, BorderLayout.SOUTH);

        // ——— Raccourcis ———
        var im = fileList.getInputMap(JComponent.WHEN_FOCUSED);
        var am = fileList.getActionMap();

        // Entrer = ouvrir (dossier/fichier)
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        am.put("open", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { try {
				openSelected();
			} catch (Exception e1) {
				e1.printStackTrace();
			} }
        });

        // Retour arrière = remonter
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "up");
        am.put("up", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { goParent(); }
        });

        // Barre d'espace = remonter
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "spaceUp");
        am.put("spaceUp", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                goParentWithAnnounce();
            }
        });


        // Échap = fermer sans rien ouvrir (restaure le dossier courant)
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        am.put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { closeDialog(true); }
        });

        // F1 = infos
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "info");
        am.put("info", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showPathOnBraille(); }
        });

        // Saisie incrémentale : on écoute les caractères réellement tapés
     // --- Saisie incrémentale : concatène les caractères tapés ---
        fileList.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyTyped(java.awt.event.KeyEvent e) {
                char ch = e.getKeyChar();
                if (Character.isISOControl(ch)) return; // ignorer Entrée, Échap, etc.

                long now = System.currentTimeMillis();
                if (now - lastTypeTs > TYPE_DELAY_MS) {
                    typed = ""; // délai écoulé -> on redémarre une nouvelle recherche
                }
                typed += ch;
                lastTypeTs = now;

                selectFirstStartingWith(typed); // ta méthode corrigée
            }
        });

        
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, KeyEvent.CTRL_DOWN_MASK), "clearTyped");
        am.put("clearTyped", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { typed = ""; }
        });


        // Annonce à chaque sélection (facultatif)
        fileList.addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) {
                File f = fileList.getSelectedValue();
                if (f == null) return;
                String label = isParentEntry(f) ? "Dossier parent"
                        : f.isDirectory() ? "Dossier" : "Fichier";
                announceHere(label + " " + renderName(f), true, false);
           
                resetAndAnnounce();
            }
        });
        
        // Ajouter un “zoom” clavier
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, KeyEvent.CTRL_DOWN_MASK), "zoomIn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD,  KeyEvent.CTRL_DOWN_MASK), "zoomIn");
        am.put("zoomIn", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int newSize = fileList.getFont().getSize() + 2;
                fileList.setFont(fileList.getFont().deriveFont((float)newSize));
                fileList.setFixedCellHeight((int)Math.round(newSize * 1.5));
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK), "zoomOut");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, KeyEvent.CTRL_DOWN_MASK), "zoomOut");
        am.put("zoomOut", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int newSize = Math.max(14, fileList.getFont().getSize() - 2);
                fileList.setFont(fileList.getFont().deriveFont((float)newSize));
                fileList.setFixedCellHeight((int)Math.round(newSize * 1.5));
            }
        });

     // → : tranche suivante
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "brailleNext");
        am.put("brailleNext", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                File f = fileList.getSelectedValue();
                if (f == null) return;
                String raw = nameForBraille(f);
                // avance de (fenêtre - chevauchement)
                int step = Math.max(1, BRAILLE_WINDOW - BRAILLE_OVERLAP);
                brailleOffset = Math.min(raw.length(), brailleOffset + step);
                announceCurrentChunk();
            }
        });

        // ← : tranche précédente
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "braillePrev");
        am.put("braillePrev", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int step = Math.max(1, BRAILLE_WINDOW - BRAILLE_OVERLAP);
                brailleOffset = Math.max(0, brailleOffset - step);
                announceCurrentChunk();
            }
        });

        // Home = début, End = fin
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "brailleHome");
        am.put("brailleHome", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                brailleOffset = 0;
                announceCurrentChunk();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), "brailleEnd");
        am.put("brailleEnd", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                File f = fileList.getSelectedValue();
                if (f == null) return;
                String raw = nameForBraille(f);
                brailleOffset = Math.max(0, raw.length() - BRAILLE_WINDOW);
                announceCurrentChunk();
            }
        });

        // F2 = informations fichier (.bwr)
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "fileInfo");
        am.put("fileInfo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showSelectedFileInfo(); }
        });

        // Charge le répertoire de départ
        loadDirectory(savedDirectory);

        // Fenêtre
        // Fenêtre (plus grande, redimensionnable)
        setResizable(true);
//        setSize(1200, 800);                 // largeur x hauteur
        maximizeToUsableScreen();
        setMinimumSize(new java.awt.Dimension(1000, 700));
        setLocationRelativeTo(null);

        // Appliquer la charte "grands caractères"
        applyLargePrint();


        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                closeDialog(true);
            }
        });

//        SwingUtilities.invokeLater(() -> {
//            fileList.requestFocusInWindow();
//            File dir = commandes.currentDirectory;
//            String msg = (dir != null ? "Ouvrir — Dossier " + dir.getName() : "Ouvrir — Racine système")
//                    + ". " + status.getText();
//            announceHere(msg,true,true);
//        });

        setVisible(true);
    }

    /** Charge le contenu d’un dossier. */
    private void loadDirectory(File dir) {
        model.clear();
        typed = ""; // reset recherche incrémentale

        if (dir == null) {
            // Pas de parent → afficher les lecteurs / racines
            File[] roots = File.listRoots();
            if (roots != null) for (File r : roots) model.addElement(r);
            commandes.currentDirectory = null;
            updateStatusRoots();
            if (!model.isEmpty()) fileList.setSelectedIndex(0);
            return;
        }

        // Met à jour le "courant" app
        commandes.currentDirectory = dir.getAbsoluteFile();

        // Entrée “.. (Dossier parent)”
        if (dir.getParentFile() != null) model.addElement(parentEntry());

        File[] list = dir.listFiles();
        if (list != null) {
            // Pas de tri pour respecter l’ordre natif ; on filtre juste :
            for (File f : list) {
                if (f.isDirectory() || f.getName().toLowerCase().endsWith(".odt")) {
                    model.addElement(f);
                }
            }
        }

        // Sélection initiale : 1er élément “utile”
        int sel = model.isEmpty() ? -1 : (isParentEntry(model.firstElement()) ? (model.size() > 1 ? 1 : 0) : 0);
        if (sel >= 0) fileList.setSelectedIndex(sel);

        updateStatusDir(dir);
    }

    /** Monte d’un cran : parent si possible, sinon affiche les racines. */
    private void goParent() {
        typed = "";
        File cur = commandes.currentDirectory;
        if (cur == null) { // déjà aux racines
            loadDirectory(null);
            return;
        }
        File parent = cur.getParentFile();
        if (parent != null) loadDirectory(parent);
        else loadDirectory(null); // racines
    }

    /** Ouvre l’élément sélectionné : dossier → naviguer, fichier .odt → charger. 
     * @throws Exception */
    private void openSelected() throws Exception {
        File sel = fileList.getSelectedValue();
        if (sel == null) return;

        if (isParentEntry(sel)) {
            goParent();
            return;
        }

        if (sel.isDirectory()) {
            loadDirectory(sel);
            return;
        }

        // Fichier .odt
        if (sel.isFile() && sel.getName().toLowerCase().endsWith(".odt")) {
            	readFile(sel);
                closeDialog(false);
        }
    }

    /** Ferme la boîte. restoreDir=true → remet le dossier courant initial. */
    private void closeDialog(boolean restoreDir) {
        if (restoreDir) commandes.currentDirectory = savedDirectory;
        SwingUtilities.invokeLater(() -> {
            parent.requestFocus();
            parent.getEditor().requestFocusInWindow();
        });
        dispose();
    }

    // ——— Recherche incrémentale avec priorité .odt > fichiers > dossiers ———

    /** Sélectionne le PREMIER élément de la liste qui commence par prefix (ordre d'affichage). */
    private void selectFirstStartingWith(String prefix) {
        if (prefix == null || prefix.isEmpty()) return;

        String p = prefix.toLowerCase();

        for (int i = 0; i < model.size(); i++) {
            File f = model.get(i);
            if (isParentEntry(f)) continue; // on ne matche pas ".. (Dossier parent)"

            String name = nameForMatch(f).toLowerCase();
            if (name.startsWith(p)) {
                fileList.setSelectedIndex(i);
                fileList.ensureIndexIsVisible(i);
                return;
            }
        }
    }


    /** Nom utilisé pour la comparaison : pour une racine (C:\, D:\ …) on prend le chemin absolu. */
    private static String nameForMatch(File f) {
        if (isParentEntry(f)) return "";
        if (f.getParentFile() == null) return driveDisplayName(f); // lecteur : matcher sur le nom lisible
        return f.getName();
    }



    // —————————— AFFICHAGE & ACCESSIBILITÉ ——————————

    private void updateStatusDir(File dir) {
        int folders = 0, files = 0;
        for (int i = 0; i < model.size(); i++) {
            File f = model.get(i);
            if (isParentEntry(f)) continue;
            if (f.isDirectory()) folders++; else files++;
        }
        String txt = String.format("%s — %d éléments : %d dossier(s), %d fichier(s) .txt",
                dir.getAbsolutePath(), folders + files, folders, files);
        status.setText(txt);
//        status.getAccessibleContext().setAccessibleDescription(txt);
    }

    private void updateStatusRoots() {
        List<File> roots = new ArrayList<>();
        File[] rs = File.listRoots();
        if (rs != null) for (File r : rs) roots.add(r);
        String txt = String.format("Racines système — %d lecteur(s) détecté(s).", roots.size());
        status.setText(txt);
//        status.getAccessibleContext().setAccessibleDescription(txt);
    }

    private static boolean isParentEntry(File f) {
        return f != null && "..".equals(f.getName()) && !f.isAbsolute();
    }

    private static File parentEntry() {
        // Sentinelle : un File relatif nommé ".." (jamais absolu)
        return new File("..");
    }

    private static String renderName(File f) {
        if (isParentEntry(f)) return ".. (Dossier parent)";
        if (f.isDirectory()) {
            if (f.getParentFile() == null) {
                // Racine (lecteur) : nom système lisible
                return "[Lecteur] " + driveDisplayName(f);
            }
            return "[Dossier] " + f.getName();
        }
        return "[Fichier] " + f.getName();
    }



    /** Renderer simple, lisible et accessible. */
    private static final class FileRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setFont(list.getFont());

            if (value instanceof File f) {
                // Texte
                setText(renderName(f));

                // Icône : 
                if (isParentEntry(f)) {
                    setIcon(ICON_PARENT);
                } else if (f.getParentFile() == null) {
                    // Lecteur / racine → icône système (disque, clé USB, etc.)
                    setIcon(FSV.getSystemIcon(f));
                } else if (f.isDirectory()) {
                    // Dossier
                    Icon sys = FSV.getSystemIcon(f);
                    setIcon(sys != null ? sys : ICON_FOLDER);
                } else {
                    // Fichier (.odt)
                    Icon sys = FSV.getSystemIcon(f);
                    setIcon(sys != null ? sys : ICON_FILE);
                }

                var ac = getAccessibleContext();
                if (ac != null) {
//                    ac.setAccessibleName(getText());
//                    ac.setAccessibleDescription(isSelected ? "sélectionné" : "non sélectionné");
                }
            }
            return this;
        }
    }

 
    /** Applique la charte "grands caractères" aux composants. */
    private void applyLargePrint() {
        var listFont = new java.awt.Font(UI_FONT_FAMILY, java.awt.Font.PLAIN, UI_FONT_SIZE_LIST);
        var statusFont = new java.awt.Font(UI_FONT_FAMILY, java.awt.Font.PLAIN, UI_FONT_SIZE_STATUS);

        // La JList + sa hauteur de ligne
        fileList.setFont(listFont);
        fileList.setFixedCellHeight(UI_ROW_HEIGHT);
        fileList.setVisibleRowCount(16);

        // Contrastes (facultatif mais utile en basse vision)
        fileList.setSelectionBackground(new java.awt.Color(30, 144, 255)); // bleu "dodger"
        fileList.setSelectionForeground(java.awt.Color.WHITE);

        // Barre d’état
        status.setFont(statusFont);

        // Indiquer au renderer d’utiliser la même police (voir plus bas)
        // (On ne fait rien ici: c’est géré dans FileRenderer via setFont(list.getFont()))
    }
    
    
    private void maximizeToUsableScreen() {
        java.awt.GraphicsConfiguration gc = getGraphicsConfiguration();
        java.awt.Rectangle b = gc.getBounds();
        java.awt.Insets insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc);

        int x = b.x + insets.left;
        int y = b.y + insets.top;
        int w = b.width  - insets.left - insets.right;
        int h = b.height - insets.top  - insets.bottom;

        setBounds(x, y, w, h);
    }

    /** Texte affiché/logique pour le nom courant (sans [Dossier] etc.). */
    private static String nameForBraille(File f) {
        if (isParentEntry(f)) return ".. (Dossier parent)";
        if (f.getParentFile() == null) return driveDisplayName(f); // lecteur
        return f.getName();
    }

    /** Calcule la tranche à annoncer à partir d'un offset. */
    private static String sliceForBraille(String s, int offset, int win, int overlap) {
        if (s == null) s = "";
        if (offset < 0) offset = 0;
        if (offset >= s.length()) offset = Math.max(0, s.length() - win);
        int end = Math.min(s.length(), offset + win);
        String chunk = s.substring(offset, end);

        // Indicateurs ⟨⟩ si tronqué (optionnels; utiles sur braille)
        boolean left = offset > 0;
        boolean right = end < s.length();
        if (left)  chunk = "… " + chunk;
        if (right) chunk = chunk + " …";
        return chunk;
    }

    /** (Ré)annonce la tranche courante pour l'élément sélectionné. */
    private void announceCurrentChunk() {
        File f = fileList.getSelectedValue();
        if (f == null) return;
        String raw = nameForBraille(f);
        String chunk = sliceForBraille(raw, brailleOffset, BRAILLE_WINDOW, BRAILLE_OVERLAP);
        String prefix = f.isDirectory() && !isParentEntry(f) ? "[Dossier] " : (!f.isDirectory() && !isParentEntry(f) ? "[Fichier] " : "");
        announceHere(prefix + chunk, false,false);
    }

    /** Réinitialise l'offset et annonce le début. */
	private void resetAndAnnounce() {
        brailleOffset = 0;
        announceCurrentChunk();
    }

	private void announceHere(String msg, boolean autoHide, boolean takeFocus) {

	    if (!takeFocus) return; // ✅ on n'installe PAS le dispatcher, on ne touche pas au focus

	    javax.swing.SwingUtilities.invokeLater(() -> {
	        srActive = autoHide;

	        srDismissDispatcher = new java.awt.KeyEventDispatcher() {
	            @Override public boolean dispatchKeyEvent(java.awt.event.KeyEvent e) {
	                if (!srActive) return false;
	                if (e.getID() != java.awt.event.KeyEvent.KEY_PRESSED) return false;

	                int kc = e.getKeyCode();
	                if (kc != java.awt.event.KeyEvent.VK_LEFT && kc != java.awt.event.KeyEvent.VK_RIGHT) {
	                	if (kc == KeyEvent.VK_SPACE) {
	                	    e.consume();
	                	    cancelSrLocal();
	                	    // NE PAS redispatcher Espace vers fileList ici
	                	    goParentWithAnnounce();
	                	    return true;
	                	}
	                    e.consume();
	                    cancelSrLocal();
	                    fileList.requestFocusInWindow();

	                    java.awt.EventQueue.invokeLater(() -> {
	                        fileList.dispatchEvent(new java.awt.event.KeyEvent(
	                            fileList,
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
	                return false; // ← et → laissent l'annonce active

	            }
	        };
	        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
	            .addKeyEventDispatcher(srDismissDispatcher);
	    });
	}

	/** Coupe le timer et enlève le KeyEventDispatcher si présents. */
	private void cancelSrLocal() {
	    if (srTimer != null) { srTimer.stop(); srTimer = null; }
	    if (srDismissDispatcher != null) {
	        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
	            .removeKeyEventDispatcher(srDismissDispatcher);
	        srDismissDispatcher = null;
	    }
	    srActive = false;
	}

	private void showPathOnBraille() {
	    File target = pathTargetForSelection();
	    java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(fileList);

	    // Cas racines système
	    if (target == null) {
	        File[] roots = File.listRoots();
	        
	        String msg = String.format("Racines système — %d lecteur(s) détecté(s).", roots == null ? 0 : roots.length);
	        dia.InfoDialog.show(owner, "Information boîte Ouvrir", msg, parent.getAffichage());
	        return;
	    }

	    // Si l'utilisateur a sélectionné un fichier, on affiche les infos du dossier parent
	    File dir = target.isDirectory() ? target : target.getParentFile();
	    
	    if (dir == null) {
	        // Pas de parent -> racines système
	        File[] roots = File.listRoots();
	        String msg = String.format("Racines système — %d lecteur(s) détecté(s).", roots == null ? 0 : roots.length);
	        dia.InfoDialog.show(owner, "Information boîte Ouvrir", msg, parent.getAffichage());
	        return;
	    }

	    
	    dir = target.getParentFile();
	    
	    // Nom lisible (utilise driveDisplayName pour les racines)
	    String name = (dir.getParentFile() == null) ? driveDisplayName(dir) : dir.getName();
	    String parent2 = (dir.getParentFile()== null) ? driveDisplayName(dir) : dir.getParentFile().getName();

	    // Comptages
	    int folders = 0, files = 0, bweCount = 0;
	    File[] list = dir.listFiles();
	    if (list != null) {
	        for (File f : list) {
	            if (f.isDirectory()) {
	                folders++;
	            } else {
	                files++;
	                if (f.getName().toLowerCase().endsWith(".odt")) bweCount++;
	            }
	        }
	    }

	    String msg = String.format(
	        "Dossier : %s%nDossier parent : %s%nFichiers .odt : %d%nTotal : %d éléments (%d dossier(s), %d fichier(s))",
	        name, parent2, bweCount, folders + files, folders, files
	    );

	    dia.InfoDialog.show(owner, "Information boîte Ouvrir", msg, parent.getAffichage());
	}



	private File pathTargetForSelection() {
	    File sel = fileList.getSelectedValue();
	    if (sel == null) return commandes.currentDirectory;        // sécurité

	    if (isParentEntry(sel)) {
	        // ".." -> on affiche le chemin du dossier parent du dossier courant
	        File cur = commandes.currentDirectory;
	        return (cur != null) ? cur.getParentFile() : null;     // null => racines
	    }
	    return sel; // dossier/fichier/lecteur sélectionné
	}

	/** Monte d’un cran ET annonce "Ouvrir — Dossier …" sur la braille. */
	private void goParentWithAnnounce() {
	    goParent(); // met à jour commandes.currentDirectory + UI + status
	}



	/** Nom "humain" du lecteur/racine (ex. "Windows (C:)", "Macintosh HD"). */
	private static String driveDisplayName(File root) {
	    if (root == null) return "";
	    // Un "lecteur" => pas de parent
	    if (root.getParentFile() != null) return root.getName();

	    String n = FSV.getSystemDisplayName(root);
	    if (n != null) n = n.trim();
	    if (n != null && !n.isEmpty()) return n;

	    // Fallbacks si l’OS ne renvoie rien d’exploitable
	    String path = root.getAbsolutePath();
	    if (path.matches("^[A-Z]:\\\\$")) {               // Windows "C:\"
	        return "Lecteur " + path.substring(0, 1) + " (" + path.substring(0, 1) + ":)";
	    }
	    if ("/".equals(path)) return "Disque système";    // macOS/Linux racine
	    return path;                                      // dernier recours
	}

	public void readFile(File selectedFile) throws Exception {
    	System.out.println("lecture d'un fichier ODT");
    	String contenu = OdtReader.extractStructuredTextFromODT(selectedFile.getAbsolutePath());
    	commandes.init();
    	
    	 commandes.texteDocument = contenu;
         
    	 if(parent.getAffichage()==Affiche.TEXTE1) commandes.hash1 = commandes.texteDocument.hashCode();
         if(parent.getAffichage()==Affiche.TEXTE2) commandes.hash2 = commandes.texteDocument.hashCode();
         
         parent.getEditor().setText(commandes.texteDocument);
         
         // colorisation
         FastHighlighter.rehighlightAll(parent.getEditor());

         // vide l'historique
         parent.clearUndoHistory();
         
         parent.getEditor().setCaretPosition(0);
         
         commandes.nameFile = selectedFile.getName().replaceFirst("\\.odt$", "");
         commandes.nomDossierCourant = selectedFile.getParentFile().getAbsolutePath();
         
         // Ajoute dans la liste des fichiers récents
         RecentFilesManager.addOpenedFile(selectedFile);
    }
	
	private void showSelectedFileInfo() {
	    File f = fileList.getSelectedValue();
	    if (f == null) {
	        return;
	    }

	    // Parent / dossier spécial
	    if (isParentEntry(f) || f.isDirectory()) {
	        return;
	    }

	    // Ne traiter que les fichiers .odt
	    if (!f.isFile() || !f.getName().toLowerCase().endsWith(".odt")) {
	        return;
	    }

	    // Récupère les infos
	    String name = f.getName();
	    String fullPath = f.getAbsolutePath();
	    String parent2 = (f.getParentFile() != null) ? f.getParentFile().getAbsolutePath() : "";
	    long size = f.length();
	    long modifiedMillis = f.lastModified();

	    String modified = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
	            .withZone(java.time.ZoneId.systemDefault())
	            .format(java.time.Instant.ofEpochMilli(modifiedMillis));

	    String humanSize = humanReadableByteCount(size);

	    // Message pour synthèse vocale / braille (court)
	    String spoken = String.format("%s. Taille %s. Modifié le %s.", name, humanSize, modified);
	    announceHere(spoken, true, true);

	    // Texte détaillé pour la boîte d'info (multiligne lisible)
	    String info = String.format("Nom : %s%nChemin : %s%nDossier : %s%nTaille : %s (%d octets)%nDate modification : %s",
	            name, fullPath, parent2, humanSize, size, modified);

	    java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(fileList);
	    dia.InfoDialog.show(owner, "Information fichier", info, parent.getAffichage());
	}
	
	private static String humanReadableByteCount(long bytes) {
	    if (bytes < 1024) return bytes + " octets";
	    final String[] units = {"ko","Mo","Go","To","Po"};
	    double b = bytes;
	    int u = -1;
	    while (b >= 1024 && u < units.length - 1) {
	        b /= 1024.0;
	        u++;
	    }
	    if (u < 0) return String.format("%.0f octets", b);
	    return String.format("%.1f %s", b, units[u]);
	}
    
}

