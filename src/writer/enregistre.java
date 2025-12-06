package writer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import writer.model.Affiche;
import writer.ui.EditorFrame;
import writer.util.RecentFilesManager;


public class enregistre {
	
	
    public enregistre(EditorFrame parent) {
    	
    	
    	
       File fichier = new File(commandes.currentDirectory, commandes.nameFile + ".bwr");
       
       try (FileWriter writer = new FileWriter(fichier)) {
    	   
    	   // mise à jour du node blindWriter
    	   commandes.maj_nodeBlindWriter(parent);
    	   
           // Écrit du contenu dans le fichier
           writer.write(commandes.nodeblindWriter.ecritureXML().toString());
           commandes.texteDocument=commandes.nodeblindWriter.ecritureXML().toString();
        
           Affiche f = parent.getAffichage();
           int hashNodeBlindWriter = commandes.nodeblindWriter.hashCode();
           if(f == Affiche.TEXTE1 ) commandes.hash1 = hashNodeBlindWriter;
           if(f == Affiche.TEXTE2 ) commandes.hash2 = hashNodeBlindWriter;
           
           // Document propre : marquer non-modifié
           parent.setModified(false);
           
           // Ajoute dans la liste des fichiers récents
           RecentFilesManager.addOpenedFile(fichier);
           
           File dossierParent = fichier.getParentFile();
           String nomDossier = (dossierParent != null) ? dossierParent.getName() : null;
           commandes.nomDossierCourant = nomDossier;
           
           StringBuilder msg = new StringBuilder(128);
           msg.append("Fichier enregistré ↓")
              .append("\n• Fichier : ").append(commandes.nameFile).append(".bwr ↓")
              .append("\n• Dossier : ").append(commandes.nomDossierCourant).append(" ↓")
              .append("\n• Chemin : " + commandes.currentDirectory.toString());
           parent.showInfo("Information", msg.toString());
           
       } catch (IOException e1) {
           e1.printStackTrace();
       }
   }
    
    public enregistre(String nameFile, File file, EditorFrame parent) {
    	
    	// mise à jour du node blindWriter
    	commandes.maj_nodeBlindWriter(parent);
          
          String text = commandes.nodeblindWriter.ecritureXML().toString();
          
          File fichier = new File(commandes.currentDirectory, nameFile + ".bwr");
          try (FileWriter writer = new FileWriter(fichier)) {
              // Écrit du contenu dans le fichier
              writer.write(text);
              commandes.texteDocument=text;
              
              // Hash du node
              if(parent.getAffichage()==Affiche.TEXTE1)commandes.hash1 = commandes.nodeblindWriter.hashCode();
              if(parent.getAffichage()==Affiche.TEXTE2)commandes.hash2 = commandes.nodeblindWriter.hashCode();
              
              // Document propre : marquer non-modifié
              parent.setModified(false);
              
              // Ajoute dans la liste des fichiers récents
              RecentFilesManager.addOpenedFile(file);
              
          } catch (IOException e1) {
              e1.printStackTrace();
          }
      }
    
}
