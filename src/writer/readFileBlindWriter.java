package writer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.text.BadLocationException;

import writer.ui.EditorFrame;
import xml.node;
import xml.transformeXLMtoNode;

public class readFileBlindWriter {

	public boolean erreur = false;
	
	public readFileBlindWriter(File selectedFile, EditorFrame parent) {
		try {
            // Lecture du fichier et insertion dans le JTextArea
            String content = new String(Files.readAllBytes(selectedFile.toPath()));
  		 
            new transformeXLMtoNode(content,false,null);
            node newNode = transformeXLMtoNode.getNodeRoot().retourneFirstEnfant("blindWriter");
            
            if(newNode==null) {
            	erreur=true;
            }else {
            	commandes.nodeblindWriter = newNode;
            	
            	if(commandes.nodeblindWriter.getAttributs().get("filename")==null 
            			| commandes.nodeblindWriter.retourneFirstEnfant("styles_paragraphes")==null
            			| commandes.nodeblindWriter.retourneFirstEnfant("meta")==null
            			| commandes.nodeblindWriter.retourneFirstEnfant("contentText")==null) {
            		erreur = true;
            		return ;
            	}
            	
            	commandes.nameFile = commandes.nodeblindWriter.getAttributs().get("filename");
            	
                commandes.styles_paragraphe = commandes.nodeblindWriter.retourneFirstEnfant("styles_paragraphes");
                
                commandes.meta = commandes.nodeblindWriter.retourneFirstEnfant("meta");
                commandes.maj_meta();

                commandes.pageDefaut = commandes.nodeblindWriter.retourneFirstEnfant("pageDefaut");

                if( commandes.nodeblindWriter.retourneFirstEnfant("pageTitre")!=null) {
                    commandes.pageTitre = commandes.nodeblindWriter.retourneFirstEnfant("pageTitre");
                }else {
                	commandes.pageTitre = new node();
                	commandes.pageTitre.setNameNode("pageTitre");
                	commandes.pageTitre.getAttributs().put("couverture", "false");
                	commandes.nodeblindWriter.retourneFirstEnfant("styles_pages").getEnfants().add(commandes.pageTitre);
                }
                
                
                commandes.texteDocument = commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenuAvecTousLesContenusDesEnfants();
                
                commandes.hash = commandes.texteDocument.hashCode();
                
                parent.getEditor().setText(commandes.texteDocument);

                parent.getEditor().setCaretPosition(0);
                
                parent.createNewBookmarkManager(); // repart sur le nouveau doc
                // puis, si tu as un fichier ouvert, recharge les marque-pages
                //blindWriter.bookmarks.loadSidecar(commandes.currentFilePath);
                if(commandes.nodeblindWriter.retourneFirstEnfant("bookmarks") != null) {
                	commandes.bookmarks = commandes.nodeblindWriter.retourneFirstEnfant("bookmarks");
                	parent.getBookmarks().loadFromXml(commandes.nodeblindWriter.retourneFirstEnfant("bookmarks"));
                }

	            parent.setModified(false);
                
            }
        } catch (IOException ex) {
        	erreur=true;
        } catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	


    
    
	public boolean isErreur() {
		return erreur;
	}

	public void setErreur(boolean erreur) {
		this.erreur = erreur;
	}
	
	
	
	
	
	
	
}
