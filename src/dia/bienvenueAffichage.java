package dia;

import writer.commandes;
import writer.ui.EditorFrame;
import writer.util.AppInfo;

public class bienvenueAffichage {

	// Constructeur
	public bienvenueAffichage(EditorFrame frame) {
        StringBuilder message = new StringBuilder(128);

        writer.ui.NormalizingTextPane editorPane = frame.getEditor();
        char c = '\u283F';
        try {

                String fileName = (commandes.nameFile != null && !commandes.nameFile.isBlank())
                                  ? commandes.nameFile + ".bwr" : "Sans nom.bwr";
                String folder   = (commandes.currentDirectory != null)
                                  ? commandes.currentDirectory.getName() : "Dossier inconnu";

                boolean editable = editorPane != null && editorPane.isEditable();

                message.append("BIENVENUE sur LisioWriter ↓");
                message.append("\n").append(c).append("Version : ").append(AppInfo.getAppVersion()).append(" ↓");
                message.append("\n").append(c).append("Fichier ouvert : ").append(fileName).append(" ↓");
                message.append("\n").append(c).append("Dossier de travail : ").append(folder).append(" ↓");
                message.append(editable ? "\n" + c +"Mode éditable." : "\n"+c+"Mode en lecture seule.");
        } catch (Exception ignore) {
            // on évite toute exception dans ce chemin d’annonce
            message = new StringBuilder(c+"Informations indisponibles pour le moment.");
        }
        
        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(editorPane);
        dia.InfoDialog.show(owner, c+"Bienvenue", message.toString());
    }
}
