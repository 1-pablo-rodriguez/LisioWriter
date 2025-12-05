package writer;

import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.undo.UndoManager;

import writer.model.Affiche;
import writer.ui.EditorFrame;
import writer.ui.NormalizingTextPane;

public class ChargeNouveauFichier {

	public ChargeNouveauFichier(EditorFrame parent, String nameFile) {
		final String newText  = "¶" ;
        SwingUtilities.invokeLater(() -> {
        	
        	commandes.nameFile = nameFile;
            if(parent.getAffichage()==Affiche.TEXTE1) commandes.hash1=0;
            if(parent.getAffichage()==Affiche.TEXTE2) commandes.hash2=0;
            
            commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenu().clear();
           	commandes.nodeblindWriter.getAttributs().put("filename","nouveaux document");
            
            commandes.defaultStyles();
        	
        	
            try {
            	writer.ui.NormalizingTextPane editorComp = (NormalizingTextPane) parent.getEditor();
                Document doc = editorComp.getDocument();

                // Insertion du texte : si AbstractDocument, on utilise remove/insertString (sur EDT)
                if (doc instanceof AbstractDocument ad) {
                    try {
                        // vider le document proprement
                        int len = Math.max(0, ad.getLength());
                        if (len > 0) ad.remove(0, len);

                        // insertion du texte (null attributes)
                        if (!newText.isEmpty()) ad.insertString(0, newText, null);
                    } catch (BadLocationException ble) {
                        // si problème de position, fallback sur setText
                        ble.printStackTrace();
                        editorComp.setText(newText);
                    }
                } else {
                    // fallback : setText
                    editorComp.setText(newText);
                }

                // positionner le caret au début
                try { editorComp.setCaretPosition(0); } catch (Exception ignore) {}

                // rechargement des signets si présents
                parent.createNewBookmarkManager();
                if (commandes.nodeblindWriter.retourneFirstEnfant("bookmarks") != null) {
                    commandes.bookmarks = commandes.nodeblindWriter.retourneFirstEnfant("bookmarks");
                    parent.getBookmarks()
                          .loadFromXml(commandes.nodeblindWriter.retourneFirstEnfant("bookmarks"));
                }

                // Document propre : marquer non-modifié
                parent.setModified(false);

                // Vider l'historique d'undo tout de suite pour éviter que l'ouverture soit annulable.
                try {
                    UndoManager um = parent.getUndoManager();
                    if (um != null) um.discardAllEdits();
                } catch (Throwable ignore) {}

                // Mettre à jour l'état des actions Undo/Redo (UI)
                try { parent.getUndoAction().setEnabled(false); } catch (Throwable ignore) {}
                try { parent.getRedoAction().setEnabled(false); } catch (Throwable ignore) {}

                
                if(parent.getAffichage()==Affiche.TEXTE1)commandes.hash1 = commandes.nodeblindWriter.hashCode();
                if(parent.getAffichage()==Affiche.TEXTE2)commandes.hash2 = commandes.nodeblindWriter.hashCode();
                
                // final : revalidate / repaint / focus
                editorComp.revalidate();
                editorComp.repaint();
                editorComp.requestFocusInWindow();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

	}
}
	
	
	
	
