package writer;

public class bienvenueAffichage {
	public bienvenueAffichage() {
        StringBuilder message = new StringBuilder(128);

        try {

                String fileName = (commandes.nameFile != null && !commandes.nameFile.isBlank())
                                  ? commandes.nameFile + ".bwr" : "Sans nom.bwr";
                String folder   = (commandes.currentDirectory != null)
                                  ? commandes.currentDirectory.getName() : "Dossier inconnu";

                boolean editable = blindWriter.editorPane != null && blindWriter.editorPane.isEditable();
                boolean liveSpell = commandes.verificationOrthoGr; // ta variable existante

                message.append("BIENVENUE sur blindWriter (BWR) ↓");
                message.append("\nVersion : ").append(blindWriter.getAppVersion()).append(" ↓");
                message.append("\nFichier ouvert : ").append(fileName).append(" ↓");
                message.append("\nDossier de travail : ").append(folder).append(" ↓");
                message.append(editable ? "\nMode éditable. ↓" : "Mode en lecture seule. ↓");
                message.append(liveSpell ? "\nVérif. frappe activée. ↓"
                                         : "\nVérif. frappe désactivée. ↓");
        } catch (Exception ignore) {
            // on évite toute exception dans ce chemin d’annonce
            message = new StringBuilder("Informations indisponibles pour le moment.");
        }
        
        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(blindWriter.editorPane);
        dia.InfoDialog.show(owner, "Bienvenue", message.toString());

        //blindWriter.announceCaretLine(false, true, message.toString());
    }
}
