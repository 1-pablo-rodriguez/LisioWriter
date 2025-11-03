package writer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.SwingUtilities;

import writer.ui.EditorFrame;
import xml.node;
import xml.transformeXLMtoNode;

public class readFileBlindWriter {

	public boolean erreur = false;
	
	// Lecture d'un fichier BWR
	public readFileBlindWriter(File selectedFile, EditorFrame parent) {
	    try {
	        // --- Lecture du fichier (texte brut XML)
	        String content = Files.readString(selectedFile.toPath());

	        // --- Transformation XML → nœuds internes
	        new transformeXLMtoNode(content, false, null);
	        node newNode = transformeXLMtoNode.getNodeRoot().retourneFirstEnfant("blindWriter");

	        if (newNode == null) {
	            erreur = true;
	            return;
	        }

	        commandes.nodeblindWriter = newNode;

	        // Vérifications de structure
	        if (commandes.nodeblindWriter.getAttributs().get("filename") == null
	                || commandes.nodeblindWriter.retourneFirstEnfant("styles_paragraphes") == null
	                || commandes.nodeblindWriter.retourneFirstEnfant("meta") == null
	                || commandes.nodeblindWriter.retourneFirstEnfant("contentText") == null) {
	            erreur = true;
	            return;
	        }

	        // --- Affectation des sections internes ---
	        commandes.nameFile = commandes.nodeblindWriter.getAttributs().get("filename");
	        commandes.styles_paragraphe = commandes.nodeblindWriter.retourneFirstEnfant("styles_paragraphes");
	        commandes.meta = commandes.nodeblindWriter.retourneFirstEnfant("meta");
	        commandes.maj_meta();
	        commandes.pageDefaut = commandes.nodeblindWriter.retourneFirstEnfant("pageDefaut");

	        // Page de titre (crée si absente)
	        if (commandes.nodeblindWriter.retourneFirstEnfant("pageTitre") != null) {
	            commandes.pageTitre = commandes.nodeblindWriter.retourneFirstEnfant("pageTitre");
	        } else {
	            commandes.pageTitre = new node();
	            commandes.pageTitre.setNameNode("pageTitre");
	            commandes.pageTitre.getAttributs().put("couverture", "false");
	            commandes.nodeblindWriter
	                    .retourneFirstEnfant("styles_pages")
	                    .getEnfants()
	                    .add(commandes.pageTitre);
	        }

	        // --- Texte principal du document ---
	        commandes.texteDocument = commandes.nodeblindWriter
	                .retourneFirstEnfant("contentText")
	                .getContenuAvecTousLesContenusDesEnfants();

	        commandes.hash = commandes.texteDocument.hashCode();

	        // --- Chargement asynchrone dans l’éditeur ---
	        new Thread(() -> {
	            final String newText = commandes.texteDocument;
	            SwingUtilities.invokeLater(() -> {
	                try {
	                    var editorPane = parent.getEditor();
	                    javax.swing.text.Document doc = editorPane.getDocument();

	                    // Effacement rapide
	                    editorPane.setText("");

	                    // Insertion directe dans le modèle (plus rapide que setText)
	                    doc.insertString(0, newText, null);
	                    editorPane.setCaretPosition(0);

	                    // Rechargement des signets
	                    parent.createNewBookmarkManager();
	                    if (commandes.nodeblindWriter.retourneFirstEnfant("bookmarks") != null) {
	                        commandes.bookmarks = commandes.nodeblindWriter.retourneFirstEnfant("bookmarks");
	                        parent.getBookmarks()
	                              .loadFromXml(commandes.nodeblindWriter.retourneFirstEnfant("bookmarks"));
	                    }

	                    // Document propre (pas de modification en attente)
	                    parent.setModified(false);

	                } catch (Exception e) {
	                    e.printStackTrace();
	                }
	            });
	        }).start();

	    } catch (IOException ex) {
	        erreur = true;
	    }
	}

	   
	public boolean isErreur() {
		return erreur;
	}

	public void setErreur(boolean erreur) {
		this.erreur = erreur;
	}
	
	
	
	
	
	
	
}
