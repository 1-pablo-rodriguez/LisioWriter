package dia;

import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

import Import.OdtReader;
import writer.blindWriter;
import writer.commandes;
import writer.ui.EditorFrame;

public class boiteOuvrir3 extends JDialog{

	private static final long serialVersionUID = 1L;

	private File sauvCurrentDirectory;
    private String typedText = "";  // Pour gérer la recherche par texte
    private long lastKeyPressTime = 0; // Pour gérer les délais de recherche par texte
    private final int KEY_TYPING_DELAY = 1000; // 1 seconde pour la recherche par lettre
    private EditorFrame parent;

    public boiteOuvrir3() {
        // Sauvegarde du répertoire courant
        sauvCurrentDirectory = new File(commandes.currentDirectory.getAbsolutePath());
        
        // Configuration de la fenêtre
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setTitle("Ouvrir un fichier");

        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BorderLayout());

        JList<String> fileList = new JList<>();
        fileList.getAccessibleContext().setAccessibleName("Liste des dossiers et fichiers.");
        fileList.getAccessibleContext().setAccessibleDescription("Affiche la liste des dossiers et fichiers.");
        
        // Obtenir la liste des fichiers/dossiers du répertoire courant
        File[] files = commandes.currentDirectory.listFiles();

        FilteredListModelODT listModelFiltre = new FilteredListModelODT();

        // Ajout des fichiers/dossiers au modèle avec leur chemin complet
        if (files != null) {
            for (File file : files) {
                listModelFiltre.addFile(file);
            }
        }

        fileList.setModel(listModelFiltre);
        fileList.setSelectedIndex(0);

        // Retour audio initial pour indiquer que la fenêtre est ouverte
        blindWriter.announceCaretLine(false, true, "La boite Ouvrir un fichier est affichée. Vous êtes dans le dossier " + commandes.currentDirectory.getName());

        fileList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int selectedIndex = fileList.getSelectedIndex();

                
                // Navigation avec les flèches
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                	 SwingUtilities.invokeLater(new Runnable() {
             	        public void run() {
             	        	int selectedIndex = fileList.getSelectedIndex();
		                    if (selectedIndex < fileList.getModel().getSize() - 1) {
		                        fileList.setSelectedIndex(selectedIndex);
		                        announceSelectedFile(fileList);	                        
		                    }
             	        }
                	 });
                	 
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                	 SwingUtilities.invokeLater(new Runnable() {
              	        public void run() {
              	        	int selectedIndex = fileList.getSelectedIndex();
		                    if (selectedIndex > 0) {
		                        fileList.setSelectedIndex(selectedIndex);
		                        announceSelectedFile(fileList);
		                    }
              	        }
                	 });
                }

                
                // Sélection d'un fichier ou ouverture d'un dossier avec la touche Entrée
                if (e.getKeyCode() == KeyEvent.VK_ENTER && selectedIndex != -1) {
                    String selectedFilePath = fileList.getSelectedValue();
                    File selectedFile = new File(selectedFilePath);
                    if (selectedFile.isDirectory()) {
                        navigateToDirectory(selectedFile, listModelFiltre, fileList);
                    } else if (selectedFile.isFile() && selectedFile.getName().endsWith(".odt")) {
                        try {
							readFile(selectedFile);
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						} catch (ParserConfigurationException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						} catch (SAXException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						} catch (Exception e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
                    }
                }

                // Remonter au dossier parent avec la barre d'espace
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    File parentDirectory = commandes.currentDirectory.getParentFile();
                    if (parentDirectory != null) {
                        navigateToDirectory(parentDirectory, listModelFiltre, fileList);
                    }
                }

                // Retour au répertoire précédent avec la touche Échap
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    commandes.currentDirectory = sauvCurrentDirectory;
                    ferme();
                }
                // Retour au répertoire précédent avec la touche Échap
                if (e.getKeyCode() == KeyEvent.VK_F1) {
                	
                }

                // Recherche rapide par saisie des lettres
                if (Character.isLetterOrDigit(e.getKeyChar())) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastKeyPressTime > KEY_TYPING_DELAY) {
                        typedText = ""; // Réinitialiser la saisie après un délai
                    }
                    typedText += e.getKeyChar();
                    lastKeyPressTime = currentTime;
                    selectFileStartingWith(typedText, fileList);
                }
            }
        });

        FileListRenderer renderer = new FileListRenderer();
        fileList.setCellRenderer(renderer);

        JScrollPane scrollPane = new JScrollPane(fileList);
        contentPane.add(scrollPane, BorderLayout.CENTER);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                ferme();
            }
        });

        setContentPane(contentPane);
        setSize(500, 400);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Annonce audio du fichier/dossier sélectionné
    private void announceSelectedFile(JList<String> fileList) {
    	File file = new File(fileList.getSelectedValue());
    	System.out.println(file.getName().replaceFirst("\\.odt$", ""));
        if (commandes.audio) {
        	String message = "";
        	if(file.getName().contains(".odt")) {
        		message= "Fichier : " + file.getName().replaceFirst("\\.odt$", "");
        	}else {
        		message= "Dossier : " + file.getName();
        	}
            fileList.getAccessibleContext().setAccessibleName(message);
        }
    }

    // Fonction pour naviguer dans les répertoires
    private void navigateToDirectory(File directory, FilteredListModelODT listModelFiltre, JList<String> fileList) {
        File[] newFiles = directory.listFiles();
        listModelFiltre.clear(); // Efface l'ancien contenu
        if (newFiles != null) {
            for (File file : newFiles) {
                listModelFiltre.addFile(file); // Ajoute les nouveaux fichiers/dossiers
            }
        }
        commandes.currentDirectory = directory; // Mise à jour du répertoire courant
        fileList.setSelectedIndex(0); // Sélectionne le premier élément du nouveau dossier
    }

    // Fonction pour rechercher et sélectionner un fichier/dossier commençant par le texte tapé
    private void selectFileStartingWith(String typedText, JList<String> fileList) {
        ListModel<String> model = fileList.getModel();
        for (int i = 0; i < model.getSize(); i++) {
        	File file = new File(model.getElementAt(i));
            if (file.getName().toLowerCase().startsWith(typedText.toLowerCase())) {
                fileList.setSelectedIndex(i);
                announceSelectedFile(fileList);
                break;
            }
        }
    }

    // Fermeture de la boite
    private void ferme() {
    	// Le place le dossier de travail
    	blindWriter.getInstance();
 		dispose();
    }
	
    // Charge le fichier
    private void readFile(File selectedFile) throws Exception {
    	System.out.println("lecture d'un fichier ODT");
    	String contenu = OdtReader.extractStructuredTextFromODT(selectedFile.getAbsolutePath());
    	System.out.println(contenu);
    	commandes.init();
//    	commandes.contentText.addContenu(contenu);
    	
    	 commandes.texteDocument = contenu;
         
         commandes.hash = commandes.texteDocument.hashCode();
         
         blindWriter.editorPane.setText(commandes.texteDocument);

         blindWriter.editorPane.setCaretPosition(0);
         
         commandes.nameFile = selectedFile.getName().replaceFirst("\\.odt$", "");
         commandes.nomDossierCourant = selectedFile.getParentFile().getAbsolutePath();
         
         ferme();
    }
    
   
   
	
}
