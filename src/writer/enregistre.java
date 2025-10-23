package writer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class enregistre {
	
    public enregistre() {
   	 // Sauvegarder le texte du JEditorPane dans un fichier
    	
    	// Mise à jour des bookmarks
    	commandes.nodeblindWriter.removeEnfant("bookmarks");
    	commandes.nodeblindWriter.addEnfant(blindWriter.bookmarks.saveToXml());
    	
       String text = blindWriter.editorPane.getText(); // récupération du texte.
       commandes.contentText.getContenu().clear(); // RAZ du node contentText
       commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenu().clear(); // RAZ du node contentText
       
       commandes.nodeblindWriter.getAttributs().put("filename", commandes.nameFile); // Mise à jour de nom du fichier
       commandes.nodeblindWriter.retourneFirstEnfant("contentText").addContenu(text); // ajout du contenu textuel
       //commandes.defaultStyles();       
      
       // mise à jour de la date de modification
       if(commandes.nodeblindWriter.containChildByName("date_modification")) {
    	   commandes.nodeblindWriter.retourneFirstEnfant("date_modification").getAttributs().put("date", commandes.dateNow());
       };
       
       // Convertion du node blindWriter en texte.
       text = commandes.nodeblindWriter.ecritureXML().toString();
       
       File fichier = new File(commandes.currentDirectory, commandes.nameFile + ".bwr");
       
       try (FileWriter writer = new FileWriter(fichier)) {
    	   
           // Écrit du contenu dans le fichier
           writer.write(text);
           
           commandes.hash = text.hashCode();
           commandes.texteDocument=text;
       } catch (IOException e1) {
           e1.printStackTrace();
       }
   }
    
    public enregistre(String nameFile) {
      	 // Sauvegarder le texte du JEditorPane dans un fichier
          String text = blindWriter.editorPane.getText();
          commandes.nodeblindWriter.removeEnfant("bookmarks");
          commandes.nodeblindWriter.addEnfant(blindWriter.bookmarks.saveToXml());
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
              
              commandes.hash = text.hashCode();
              commandes.texteDocument=text;
              
          } catch (IOException e1) {
              e1.printStackTrace();
          }
      }
    
}
