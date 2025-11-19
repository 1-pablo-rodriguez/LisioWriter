package writer;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.undo.UndoManager;

import writer.ui.EditorFrame;
import writer.ui.NormalizingTextPane;

public class ChargeNouveauFichier {

	public ChargeNouveauFichier(EditorFrame parent, String nameFile) {
		final String newText  = "¶" ;
        SwingUtilities.invokeLater(() -> {
        	
        	commandes.nameFile = nameFile;
            commandes.hash=0;
            
            commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenu().clear();
           	commandes.nodeblindWriter.getAttributs().put("filename","nouveaux fichier LisioWriter");
            
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

                
//                // -> insère le caractère braille début de paragraphe 
//                if (parent instanceof EditorFrame) {
//                    ((EditorFrame) parent).ensureLeadingBrailleMarkOnAllParagraphs();
//                }
                
//                // Appliquer la colorisation/surlignage si possible (TextHighlighter.apply attend un JTextPane)
//                try {
//                    if (editorComp instanceof JTextPane tp) {
//                        writer.ui.editor.TextHighlighter.apply(tp);
//                        // si la colorisation a généré des edits, on vide l'historique à nouveau
//                        try {
//                            UndoManager um2 = parent.getUndoManager();
//                            if (um2 != null) um2.discardAllEdits();
//                        } catch (Throwable ignore) {}
//                    }
//                } catch (Throwable t) {
//                    // ne bloque pas le chargement si le highlighter échoue
//                    t.printStackTrace();
//                }

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
	
	
	
	
