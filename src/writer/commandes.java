package writer;

import writer.model.Affiche;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;

import writer.ui.EditorFrame;
import writer.ui.NormalizingTextPane;
import xml.node;
import xml.transformeXLMtoNode;

public class commandes {
	// Initialisation du répertoire courant (Dossier Documents)
    public static String userHome = System.getProperty("user.home");
    public static File currentDirectory = new File(userHome + "/Documents");
    public static String nomDossierCourant = currentDirectory.getName();
    public static String pathApp = Paths.get("").toAbsolutePath().toString();
	public static String getPathApp = pathApp ;
	public static FileSystemView filesys = FileSystemView.getFileSystemView();
	// public static File dossierDocuments = filesys.getDefaultDirectory();
	public static String iconPath = commandes.getPathApp + "\\LisioWriter.ico";
	// dictionnaires
	public static String dicFr = commandes.getPathApp + "\\dic\\fr_FR.dic";
	public static String affFr = commandes.getPathApp + "\\dic\\fr_FR.aff";
	
    public static String nameFile = "Nouveau document";
    public static int hash1 = 0;
    public static int hash2 = 0;
    public static String texteDocument = "";
    
    public static boolean audio = false;
    public static boolean nonvoyant = true;
    public static boolean verificationOrthoGr = false;

    public static node contentText = new node();
    public static node nodeblindWriter = new node();
    public static node styles_paragraphe = new node();
    public static node styles_page = new node();
    public static node Tprin = new node();
    public static node Tstitre = new node();
    public static node bodyText = new node();
    public static node T1 = new node();
    public static node T2 = new node();
    public static node T3 = new node();
    public static node T4 = new node();
    public static node T5 = new node();
    public static node pageDefaut = new node();
    public static node pageTitre = new node();
    public static node meta = new node();
    public static node bookmarks = new node();
    public static node dateModif = new node();
    public static node dateCreate = new node();
    
    public static String version = "1.0";
	public static node nodeDocumentation = new node();
    public static node sauvText1File = new node();
    public static node sauvText2File = new node();
    
    public static boolean audioActif = false;
    public static boolean affichageDocumentationOuverture=true;
     
 // Dans commandes (ou où tu déclares la liste)
    public static final java.util.List<String> listMotsDico = new java.util.ArrayList<>();

    public static void init() {
    	initLesNodesBlindWriter();
        loadDocumentation();
//        loadManuel();
    }
    
    // Création du node LisioWriter
    public static void initLesNodesBlindWriter() {

        nodeblindWriter.setNameNode("blindWriter");
        nodeblindWriter.getAttributs().put("filename", "Nouveau document");
        styles_paragraphe = new node();
        styles_paragraphe.setNameNode("styles_paragraphes");
        
        Tprin = new node();
        Tprin.setNameNode("Title");
        Tprin.getAttributs().put("name", "Title");
        
        Tstitre = new node();
        Tstitre.setNameNode("Subtitle");
        Tstitre.getAttributs().put("name", "Subtitle");
        
        T1 = new node();
    	T1.setNameNode("Titre1");
    	T1.getAttributs().put("name", "Titre1");
    	
    	T2 = new node();
    	T2.setNameNode("Titre2");
    	T2.getAttributs().put("name", "Titre2");
    	
    	T3 = new node();
    	T3.setNameNode("Titre3");
    	T3.getAttributs().put("name", "Titre3");
    	
    	T4 = new node();
    	T4.setNameNode("Titre4");
    	T4.getAttributs().put("name", "Titre4");
    	
    	T5 = new node();
    	T5.setNameNode("Titre5");
    	T5.getAttributs().put("name", "Titre5");

    	bodyText = new node();
    	bodyText.setNameNode("bodyText");
    	bodyText.getAttributs().put("name", "Corps de texte");
    	
    	styles_page = new node();
    	styles_page.setNameNode("styles_pages");
    	
    	styles_paragraphe.addEnfant(Tprin);
    	styles_paragraphe.addEnfant(Tstitre);
    	styles_paragraphe.addEnfant(T1);
    	styles_paragraphe.addEnfant(T2);
    	styles_paragraphe.addEnfant(T3);
    	styles_paragraphe.addEnfant(T4);
    	styles_paragraphe.addEnfant(T5);
    	styles_paragraphe.addEnfant(bodyText);
    	
    	
    	pageDefaut = new node();
    	pageDefaut.setNameNode("pageDefaut");
    	
    	pageTitre = new node();
    	pageTitre.setNameNode("pageTitre");
    	
    	styles_page.addEnfant(pageDefaut);
    	styles_page.addEnfant(pageTitre);
    	
    	meta = new node();
    	meta.setNameNode("meta");   	
    	init_meta();
    	
    	bookmarks = new node();
    	bookmarks.setNameNode("bookmarks");
    	bookmarks.getAttributs().put("version", "1");
    	
    	contentText = new node();
    	contentText.setNameNode("contentText"); 
    	
    	nodeblindWriter.addEnfant( commandes.meta);
    	nodeblindWriter.addEnfant( commandes.styles_paragraphe);
    	nodeblindWriter.addEnfant( commandes.styles_page);
    	nodeblindWriter.addEnfant( commandes.bookmarks);
        nodeblindWriter.addEnfant( commandes.contentText);
        
        
        defaultStyles();
        sauvText1File = nodeblindWriter;
        
        
        try {
			sauvText2File = nodeblindWriter.clone();
			sauvText2File.getAttributs().put("filename", "Texte 2");
			sauvText2File.retourneFirstEnfant("contentText").addContenu("¶ ");
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}

        listMotsDico.add("blindWriter");
        listMotsDico.add("LisioWriter");
        listMotsDico.add("@saut de page");
        listMotsDico.add("@(");
    }
    
    // Pour la création d'un nouveau fichier
    public static void initNodeBlindWriter() {
    	nodeblindWriter = new node();
    	
        nodeblindWriter.setNameNode("blindWriter");
        nodeblindWriter.getAttributs().put("filename", "Nouveau document");
        styles_paragraphe = new node();
        styles_paragraphe.setNameNode("styles_paragraphes");
        
        Tprin = new node();
        Tprin.setNameNode("Title");
        Tprin.getAttributs().put("name", "Title");
        
        Tstitre = new node();
        Tstitre.setNameNode("Subtitle");
        Tstitre.getAttributs().put("name", "Subtitle");
        
        T1 = new node();
    	T1.setNameNode("Titre1");
    	T1.getAttributs().put("name", "Titre1");
    	
    	T2 = new node();
    	T2.setNameNode("Titre2");
    	T2.getAttributs().put("name", "Titre2");
    	
    	T3 = new node();
    	T3.setNameNode("Titre3");
    	T3.getAttributs().put("name", "Titre3");
    	
    	T4 = new node();
    	T4.setNameNode("Titre4");
    	T4.getAttributs().put("name", "Titre4");
    	
    	T5 = new node();
    	T5.setNameNode("Titre5");
    	T5.getAttributs().put("name", "Titre5");

    	bodyText = new node();
    	bodyText.setNameNode("bodyText");
    	bodyText.getAttributs().put("name", "Corps de texte");
    	
    	styles_page = new node();
    	styles_page.setNameNode("styles_pages");
    	
    	styles_paragraphe.addEnfant(Tprin);
    	styles_paragraphe.addEnfant(Tstitre);
    	styles_paragraphe.addEnfant(T1);
    	styles_paragraphe.addEnfant(T2);
    	styles_paragraphe.addEnfant(T3);
    	styles_paragraphe.addEnfant(T4);
    	styles_paragraphe.addEnfant(T5);
    	styles_paragraphe.addEnfant(bodyText);
    	
    	pageDefaut = new node();
    	pageDefaut.setNameNode("pageDefaut");
    	
    	pageTitre = new node();
    	pageTitre.setNameNode("pageTitre");
    	
    	styles_page.addEnfant(pageDefaut);
    	styles_page.addEnfant(pageTitre);
    	
    	meta = new node();
    	meta.setNameNode("meta");   	
    	init_meta();
    	
    	bookmarks = new node();
    	bookmarks.setNameNode("bookmarks");
    	bookmarks.getAttributs().put("version", "1");
    	
    	contentText = new node();
    	contentText.setNameNode("contentText"); 
    	contentText.addContenu("¶ ");
    	
    	nodeblindWriter.addEnfant( meta);
    	nodeblindWriter.addEnfant( styles_paragraphe);
    	nodeblindWriter.addEnfant( styles_page);
    	nodeblindWriter.addEnfant( bookmarks);
        nodeblindWriter.addEnfant( contentText);
        
        // Mise à jour des styles et des meta
        defaultStyles();
        init_meta();
    }
    
    
    // Retourne la date au format yyyy-MM-dd'T'HH:mm:ss
    public static String dateNow() {
    	// Obtenir la date et l'heure actuelles
        LocalDateTime now = LocalDateTime.now();
        // Définir le format de date souhaité
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        // Formater la date
        String formattedDate = now.format(formatter);

        // Afficher la date formatée
        System.out.println("Date actuelle : " + formattedDate);
        
        return formattedDate;
    }
    
    // Chargement de la documentation
    private static void  loadDocumentation() {
    	try {
            // Lecture du fichier et insertion dans le JTextArea
    		File selectedFile = new File(pathApp+"/documentation.bwr");
            String content = new String(Files.readAllBytes(selectedFile.toPath()));
            new transformeXLMtoNode(content,false,null);
            commandes.nodeDocumentation = transformeXLMtoNode.getNodeRoot().retourneFirstEnfant("blindWriter");    
         } catch (IOException ex) {
        }
    }
    
    
//    // Chargement du manuel b.book
//    private static void  loadManuel() {
//    	try {
//            // Lecture du fichier et insertion dans le JTextArea
//    		File selectedFile = new File(pathApp+"/manuel.bwr");
//            String content = new String(Files.readAllBytes(selectedFile.toPath()));
//            new transformeXLMtoNode(content,false,null);
//            commandes.manuel = transformeXLMtoNode.getNodeRoot().retourneFirstEnfant("blindWriter");    
//         } catch (IOException ex) {
//        }
//    }
    
    // mise à jour du nodeBlindWriter pour enregister ou pour quitter et vérifier le hash
    // mise à jour sans hash
    public static void maj_nodeBlindWriter(EditorFrame editorFrame) {
    	// Suppression des bookmarks
    	nodeblindWriter.removeAllEnfantWithThisName("bookmarks");
    	// Mise en jour des bookmarks
    	nodeblindWriter.addEnfant(editorFrame.getBookmarks().saveToXml());
    	
       String text = editorFrame.getEditor().getText(); // récupération du texte.
       contentText.getContenu().clear(); // RAZ du node contentText
       nodeblindWriter.retourneFirstEnfant("contentText").getContenu().clear(); // RAZ du node contentText
       
       nodeblindWriter.getAttributs().put("filename", nameFile); // Mise à jour de nom du fichier
       nodeblindWriter.retourneFirstEnfant("contentText").addContenu(text); // ajout du contenu textuel
       //commandes.defaultStyles();       
      
       // mise à jour de la date de modification
       if(nodeblindWriter.containChildByName("date_modification")) {
    	   nodeblindWriter.retourneFirstEnfant("date_modification").getAttributs().put("date", commandes.dateNow());
       };
       
    }
    
    // Retourne le HashCode du nodeBlindWriter
    public static int calcul_HashCodeNodeBlindWriter(EditorFrame editorFrame) {
    	Affiche f = editorFrame.getAffichage();
    	if(f == Affiche.TEXTE1
    			|| f == Affiche.TEXTE2) return nodeblindWriter.hashCode();
    	return 0;
    }
    
    
    // Sauvegarde temporaire du texte 1
    public static void sauvTexte1File(NormalizingTextPane editor, Affiche f) {
    	if(f!= Affiche.TEXTE1) return;
    	 sauvText1File = new node();
    	 sauvText1File.setNameNode("blindWriter");
    	 sauvText1File.getAttributs().put("filename", commandes.nameFile);
    	 sauvText1File.addEnfant(styles_paragraphe);
    	 sauvText1File.addEnfant(styles_page);
    	 sauvText1File.addEnfant(meta);
    	 node contentText = new node();
    	 contentText.setNameNode("contentText");
    	 contentText.addContenu(editor.getText());
    	 sauvText1File.addEnfant(contentText);
     	// Mise en jour des bookmarks
     	Window win = SwingUtilities.getWindowAncestor(editor);
     	if (win instanceof EditorFrame frame) {
     		sauvText1File.addEnfant(frame.getBookmarks().saveToXml());
     	}
    }
    
    // Sauvegarde temporaire du texte 2
    public static void sauvTexte2File(NormalizingTextPane editor, Affiche f) {
    	if(f!= Affiche.TEXTE2) return;
    	 sauvText2File = new node();
    	 sauvText2File.setNameNode("blindWriter");
    	 sauvText2File.getAttributs().put("filename", commandes.nameFile);
    	 sauvText2File.addEnfant(styles_paragraphe);
    	 sauvText2File.addEnfant(styles_page);
    	 sauvText2File.addEnfant(meta);
    	 node contentText = new node();
    	 contentText.setNameNode("contentText");
    	 contentText.addContenu(editor.getText());
    	 sauvText2File.addEnfant(contentText);
     	// Mise en jour des bookmarks
     	Window win = SwingUtilities.getWindowAncestor(editor);
     	if (win instanceof EditorFrame frame) {
     		sauvText2File.addEnfant(frame.getBookmarks().saveToXml());
     	}
    }
    
    // Chargement des styles par défaut
    public static void defaultStyles() {

    	bodyText = commandes.nodeblindWriter.retourneFirstEnfant("bodyText");
        if(!bodyText.isHasAttributs("police")) bodyText.getAttributs().put("police", "Arial");
        if(!bodyText.isHasAttributs("size")) bodyText.getAttributs().put("size", "12pt");
        if(!bodyText.isHasAttributs("alignement")) bodyText.getAttributs().put("alignement", "justify");
        if(!bodyText.isHasAttributs("interligne")) bodyText.getAttributs().put("interligne", "115%");
        if(!bodyText.isHasAttributs("margin_top")) bodyText.getAttributs().put("espacement_au_dessus", "0.2cm");
        if(!bodyText.isHasAttributs("margin_bottom")) bodyText.getAttributs().put("espacement_en_dessous", "0.2cm");
        
        Tprin = commandes.nodeblindWriter.retourneFirstEnfant("Title");
        if(!Tprin.isHasAttributs("police")) Tprin.getAttributs().put("police", "Arial");
        if(!Tprin.isHasAttributs("size")) Tprin.getAttributs().put("size", "18pt");
        if(!Tprin.isHasAttributs("alignement")) Tprin.getAttributs().put("alignement", "center");
        if(!Tprin.isHasAttributs("interligne")) Tprin.getAttributs().put("interligne", "150%");
        if(!Tprin.isHasAttributs("margin_top")) Tprin.getAttributs().put("espacement_au_dessus", "0.423cm");
        if(!Tprin.isHasAttributs("margin_bottom")) Tprin.getAttributs().put("espacement_en_dessous", "0.212cm");
        if(!Tprin.isHasAttributs("keep-with-next")) Tprin.getAttributs().put("keep-with-next", "always");
        
        Tstitre = commandes.nodeblindWriter.retourneFirstEnfant("Subtitle");
        if(!Tstitre.isHasAttributs("police")) Tstitre.getAttributs().put("police", "Arial");
        if(!Tstitre.isHasAttributs("size")) Tstitre.getAttributs().put("size", "18pt");
        if(!Tstitre.isHasAttributs("alignement")) Tstitre.getAttributs().put("alignement", "start");
        if(!Tstitre.isHasAttributs("interligne")) Tstitre.getAttributs().put("interligne", "150%");
        if(!Tstitre.isHasAttributs("margin_top")) Tstitre.getAttributs().put("espacement_au_dessus", "0.3cm");
        if(!Tstitre.isHasAttributs("margin_bottom")) Tstitre.getAttributs().put("espacement_en_dessous", "0.5cm");
        if(!Tstitre.isHasAttributs("keep-with-next")) Tstitre.getAttributs().put("keep-with-next", "always");     
        
        T1 = commandes.nodeblindWriter.retourneFirstEnfant("Titre1");
        if(!T1.isHasAttributs("police")) T1.getAttributs().put("police", "Arial");
        if(!T1.isHasAttributs("size")) T1.getAttributs().put("size", "18pt");
        if(!T1.isHasAttributs("alignement")) T1.getAttributs().put("alignement", "start");
        if(!T1.isHasAttributs("interligne")) T1.getAttributs().put("interligne", "150%");
        if(!T1.isHasAttributs("margin_top")) T1.getAttributs().put("espacement_au_dessus", "0.3cm");
        if(!T1.isHasAttributs("margin_bottom")) T1.getAttributs().put("espacement_en_dessous", "0.5cm");
        if(!T1.isHasAttributs("keep-with-next")) T1.getAttributs().put("keep-with-next", "always");
        
        T2 = commandes.nodeblindWriter.retourneFirstEnfant("Titre2");
        if(!T2.isHasAttributs("police")) T2.getAttributs().put("police", "Arial");
        if(!T2.isHasAttributs("size")) T2.getAttributs().put("size", "18pt");
        if(!T2.isHasAttributs("alignement")) T2.getAttributs().put("alignement", "start");
        if(!T2.isHasAttributs("interligne")) T2.getAttributs().put("interligne", "150%");
        if(!T2.isHasAttributs("margin_top")) T2.getAttributs().put("espacement_au_dessus", "0.3cm");
        if(!T2.isHasAttributs("margin_bottom")) T2.getAttributs().put("espacement_en_dessous", "0.5cm");
        if(!T2.isHasAttributs("keep-with-next")) T2.getAttributs().put("keep-with-next", "always");
        
        T3 = commandes.nodeblindWriter.retourneFirstEnfant("Titre3");
        if(!T3.isHasAttributs("police")) T3.getAttributs().put("police", "Arial");
        if(!T3.isHasAttributs("size")) T3.getAttributs().put("size", "18pt");
        if(!T3.isHasAttributs("alignement")) T3.getAttributs().put("alignement", "start");
        if(!T3.isHasAttributs("interligne")) T3.getAttributs().put("interligne", "150%");
        if(!T3.isHasAttributs("margin_top")) T3.getAttributs().put("espacement_au_dessus", "0.3cm");
        if(!T3.isHasAttributs("margin_bottom")) T3.getAttributs().put("espacement_en_dessous", "0.5cm");
        if(!T3.isHasAttributs("keep-with-next")) T3.getAttributs().put("keep-with-next", "always");
        
        T4 = commandes.nodeblindWriter.retourneFirstEnfant("Titre4");
        if(!T4.isHasAttributs("police")) T4.getAttributs().put("police", "Arial");
        if(!T4.isHasAttributs("size")) T4.getAttributs().put("size", "18pt");
        if(!T4.isHasAttributs("alignement")) T4.getAttributs().put("alignement", "start");
        if(!T4.isHasAttributs("interligne")) T4.getAttributs().put("interligne", "150%");
        if(!T4.isHasAttributs("margin_top")) T4.getAttributs().put("espacement_au_dessus", "0.3cm");
        if(!T4.isHasAttributs("margin_bottom")) T4.getAttributs().put("espacement_en_dessous", "0.5cm");
        if(!T4.isHasAttributs("keep-with-next")) T4.getAttributs().put("keep-with-next", "always");
        
        T5 = commandes.nodeblindWriter.retourneFirstEnfant("Titre5");
        if(!T5.isHasAttributs("police")) T5.getAttributs().put("police", "Arial");
        if(!T5.isHasAttributs("size")) T5.getAttributs().put("size", "18pt");
        if(!T5.isHasAttributs("alignement")) T5.getAttributs().put("alignement", "start");
        if(!T5.isHasAttributs("interligne")) T5.getAttributs().put("interligne", "150%");
        if(!T5.isHasAttributs("margin_top")) T5.getAttributs().put("espacement_au_dessus", "0.3cm");
        if(!T5.isHasAttributs("margin_bottom")) T5.getAttributs().put("espacement_en_dessous", "0.5cm");
        if(!T5.isHasAttributs("keep-with-next")) T5.getAttributs().put("keep-with-next", "always");
        
        pageDefaut = commandes.nodeblindWriter.retourneFirstEnfant("pageDefaut");
        if(!pageDefaut.isHasAttributs("entete")) pageDefaut.getAttributs().put("entete", "false");
        if(!pageDefaut.isHasAttributs("piedpage")) pageDefaut.getAttributs().put("piedpage", "false");
        
        
        pageTitre = commandes.nodeblindWriter.retourneFirstEnfant("pageTitre");
        if(!pageTitre.isHasAttributs("couverture")) pageTitre.getAttributs().put("couverture", "false");
        
    }
    
    
    
    public static void init_meta() {
    	meta.removeAllAttributs();
    	meta.removeAllEnfants();
    	meta.addEnfant(auteur());
    	meta.addEnfant(titre());
    	meta.addEnfant(sujet());
    	meta.addEnfant(coauteur());
    	meta.addEnfant(societe());
    	meta.addEnfant(description());
    	meta.addEnfant(motsCles());
    	meta.addEnfant(langue());
    	meta.addEnfant(dateCreation());
    	meta.addEnfant(dateModification());	
    }
    
    
    public static void maj_meta() {
    	majAuteur();
    	majTitre();
    	majSujet();
    	majCoauteur();
    	majSociete();
    	majDescription();
    	majMotsCles();
    	majLangue();
    	majDateModification();
    	majDateCreation();
    }
    
    
    private static void majAuteur() {
    	if(meta.retourneFirstEnfant("auteur")==null) {
    		meta.addEnfant(auteur());
    	}else if(meta.retourneFirstEnfant("auteur").getAttributs("nom")==null) {
    		meta.removeEnfant("auteur");
    		meta.addEnfant(auteur());
    	}
    }
    
    private static void majTitre() {
    	if(meta.retourneFirstEnfant("titre")==null) {
    		meta.addEnfant(titre());
    	}else if(meta.retourneFirstEnfant("titre").getAttributs("LeTitre")==null) {
    		meta.removeEnfant("titre");
    		meta.addEnfant(titre());
    	}
    }
    
    private static void majSujet() {
    	if(meta.retourneFirstEnfant("sujet")==null) {
    		meta.addEnfant(sujet());
    	}else if(meta.retourneFirstEnfant("sujet").getAttributs("LeSujet")==null) {
    		meta.removeEnfant("sujet");
    		meta.addEnfant(sujet());
    	}
    }
    
    private static void majCoauteur() {
    	if(meta.retourneFirstEnfant("coauteur")==null) {
    		meta.addEnfant(coauteur());
    	}else if(meta.retourneFirstEnfant("coauteur").getAttributs("nom")==null) {
    		meta.removeEnfant("coauteur");
    		meta.addEnfant(coauteur());
    	}
    }
    
    private static void majSociete() {
    	if(meta.retourneFirstEnfant("society")==null) {
    		meta.addEnfant(societe());
    	}else if(meta.retourneFirstEnfant("society").getAttributs("nom")==null) {
    		meta.removeEnfant("society");
    		meta.addEnfant(societe());
    	}
    }

    private static void majDescription() {
    	if(meta.retourneFirstEnfant("description")==null) {
    		meta.addEnfant(description());
    	}else if(meta.retourneFirstEnfant("description").getAttributs("resume")==null) {
    		meta.removeEnfant("description");
    		meta.addEnfant(description());
    	}
    }
    
    private static void majMotsCles() {
    	if(meta.retourneFirstEnfant("motsCles")==null) {
    		meta.addEnfant(motsCles());
    	}else if(meta.retourneFirstEnfant("motsCles").getAttributs("mots")==null) {
    		meta.removeEnfant("motsCles");
    		meta.addEnfant(motsCles());
    	}
    }
    
    private static void majLangue() {
    	if(meta.retourneFirstEnfant("langue")==null) {
    		meta.addEnfant(langue());
    	}else if(meta.retourneFirstEnfant("langue").getAttributs("lang")==null) {
    		meta.removeEnfant("langue");
    		meta.addEnfant(langue());
    	}
    }
    
    private static void majDateModification() {
    	if(meta.retourneFirstEnfant("date_modification")==null) {
    		meta.addEnfant(dateModification());
    	}else if(meta.retourneFirstEnfant("date_modification").getAttributs("date")==null) {
    		meta.removeEnfant("date_modification");
    		meta.addEnfant(dateModification());
    	}
    }
    
    private static void majDateCreation() {
    	if(meta.retourneFirstEnfant("date_creation")==null) {
    		meta.addEnfant(dateCreation());
    	}else if(meta.retourneFirstEnfant("date_creation").getAttributs("date")==null) {
    		meta.removeEnfant("date_creation");
    		meta.addEnfant(dateCreation());
    	}
    }
    
    
    
    private static node auteur() {
    	node n = new node();
    	n.setNameNode("auteur");
    	n.getAttributs().put("nom", "");
    	return n;
    }
    
    private static node titre() {
    	node n = new node();
    	n.setNameNode("titre");
    	n.getAttributs().put("LeTitre", "");
    	return n;
    }
    
    private static node sujet() {
    	node n = new node();
    	n.setNameNode("sujet");
    	n.getAttributs().put("LeSujet", "");
    	return n;
    }
    
    private static node coauteur() {
    	node n = new node();
    	n.setNameNode("coauteur");
    	n.getAttributs().put("nom", "LisioWriter; ...");
    	return n;
    }
    
    private static node societe() {
    	node n = new node();
    	n.setNameNode("society");
    	n.getAttributs().put("nom", "");
    	return n;
    }
    
    private static node description() {
    	node n = new node();
    	n.setNameNode("description");
    	n.getAttributs().put("resume", "");
    	return n;
    }
    
    private static node motsCles() {
    	node n = new node();
    	n.setNameNode("motsCles");
    	n.getAttributs().put("mots", "");
    	return n;
    }
    
    private static node langue() {
    	node n = new node();
    	n.setNameNode("langue");
    	n.getAttributs().put("lang", "");
    	return n;
    }
    
    private static node dateModification() {
    	node n = new node();
    	n.setNameNode("date_modification");
    	n.getAttributs().put("date", dateNow());
    	return n;
    }
    
    private static node dateCreation() {
    	node n = new node();
    	n.setNameNode("date_creation");
    	n.getAttributs().put("date", dateNow());
    	return n;
    }
    

    
    
}
