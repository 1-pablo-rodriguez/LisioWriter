package dia;

import writer.commandes;
import writer.ui.EditorFrame;
import writer.update.UpdateChecker;
import writer.util.AppInfo;

public class bienvenueAffichage {

	// Constructeur
	public bienvenueAffichage(EditorFrame frame) {
        StringBuilder message = new StringBuilder(128);

        writer.ui.NormalizingTextPane editorPane = frame.getEditor();
        String c = "INFO. : ";
        try {
        	 	String fileName = (commandes.nameFile != null && !commandes.nameFile.isBlank())
                     ? commandes.nameFile + ".bwr" : "Sans nom.bwr";
        	 	String folder   = (commandes.currentDirectory != null)
                     ? commandes.currentDirectory.getName() : "Dossier inconnu";

        	 	boolean editable = editorPane != null && editorPane.isEditable();
        	
	        	boolean flag = UpdateChecker.hasNewVersion();
	
	            if (flag) {
	            	UpdateChecker.UpdateAvailability avail = UpdateChecker.checkAvailability();
	            	String url = avail.info.downloadUrl;
	            	String version = avail.info.version;
	            	String notes = avail.info.notes;
	                System.out.println("Nouvelle version dispo : " + version);
	                System.out.println("Nouvelle version url : " + url);
	                System.out.println("Nouvelle version note : " + notes);
	                message.append("NOUVELLE VERSION DISPONIBLE : v").append(version).append(" ↓");
	                if(!notes.isBlank()) {
	                	message.append("\n").append(c).append(notes).append(" ↓");
	                }
	                message.append("\n").append(c).append("Pour installer : Ctrl+U ou Paramètres > Mise à jour. ↓");
	                message.append("\n").append(c).append("Après le téléchargement, LisioWriter se fermera puis installera automatiquement la nouvelle version. "
	                		+ "Il suffit d’attendre environ 2 minutes. ↓");
	                message.append("\n").append(c).append("Votre version : ").append(AppInfo.getAppVersion()).append(" ↓");
	            } else {
	                System.out.println("Tu es déjà à jour.");
	                message.append("BIENVENUE sur LisioWriter - v").append(AppInfo.getAppVersion()).append(" ↓");
	                message.append("\n").append(c).append("Version à jour : ").append(AppInfo.getAppVersion()).append(" ↓");
	            }
            
	            message.append("\n").append(c).append("Fichier ouvert : ").append(fileName).append(" ↓");
                message.append("\n").append(c).append("Dossier de travail : ").append(folder).append(" ↓");
                message.append(editable ? "\n" + c +"Mode éditable. ↓" : "\n"+c+"Mode en lecture seule. ↓");
        
                
                
        } catch (Exception ignore) {
            // on évite toute exception dans ce chemin d’annonce
            message = new StringBuilder(c+"Informations indisponibles pour le moment.");
        }
        
        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
        dia.InfoDialog.show(owner, c+"Bienvenue", message.toString());
    }
}
