package writer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import writer.ui.EditorFrame;


public class enregistre {
	
	
    public enregistre(EditorFrame parent) {
   	 // Sauvegarder le texte du JEditorPane dans un fichier
    	
    	// Suppression des bookmarks
    	commandes.nodeblindWriter.removeAllEnfantWithThisName("bookmarks");
    	// Mise en jour des bookmarks
    	commandes.nodeblindWriter.addEnfant(parent.getBookmarks().saveToXml());
    	
       String text = parent.getEditor().getText(); // récupération du texte.
       commandes.contentText.getContenu().clear(); // RAZ du node contentText
       commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenu().clear(); // RAZ du node contentText
       
       commandes.nodeblindWriter.getAttributs().put("filename", commandes.nameFile); // Mise à jour de nom du fichier
       commandes.nodeblindWriter.retourneFirstEnfant("contentText").addContenu(text); // ajout du contenu textuel
       //commandes.defaultStyles();       
      
       // mise à jour de la date de modification
       if(commandes.nodeblindWriter.containChildByName("date_modification")) {
    	   commandes.nodeblindWriter.retourneFirstEnfant("date_modification").getAttributs().put("date", commandes.dateNow());
       };
       
       // Convertion du node LisioWriter en texte.
       text = commandes.nodeblindWriter.ecritureXML().toString();
       
       File fichier = new File(commandes.currentDirectory, commandes.nameFile + ".bwr");
       
       try (FileWriter writer = new FileWriter(fichier)) {
    	   
           // Écrit du contenu dans le fichier
           writer.write(text);
           commandes.texteDocument=text;
           
           // Hash du node
           commandes.hash = commandes.nodeblindWriter.hashCode();
           
       } catch (IOException e1) {
           e1.printStackTrace();
       }
   }
    
    public enregistre(String nameFile, EditorFrame parent) {
      	 // Sauvegarder le texte du JEditorPane dans un fichier
          String text = parent.getEditor().getText();
          commandes.nodeblindWriter.removeAllEnfantWithThisName("bookmarks");
          commandes.nodeblindWriter.addEnfant(parent.getBookmarks().saveToXml());
          commandes.contentText.getContenu().clear();
          commandes.contentText.addContenu(text);
          commandes.nodeblindWriter.getAttributs().put("filename", commandes.nameFile);
          commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenu().clear();
          commandes.nodeblindWriter.retourneFirstEnfant("contentText").addContenu(text);
          
          if(commandes.nodeblindWriter.containChildByName("date_modification")) {
       	   commandes.nodeblindWriter.retourneFirstEnfant("date_modification").getAttributs().put("date", commandes.dateNow());
          };
          
          text = commandes.nodeblindWriter.ecritureXML().toString();
          
          File fichier = new File(commandes.currentDirectory, nameFile + ".bwr");
          try (FileWriter writer = new FileWriter(fichier)) {
              // Écrit du contenu dans le fichier
              writer.write(text);
              
              commandes.texteDocument=text;
              
              // Hash du node
              commandes.hash = commandes.nodeblindWriter.hashCode();
              
          } catch (IOException e1) {
              e1.printStackTrace();
          }
      }
    
}
