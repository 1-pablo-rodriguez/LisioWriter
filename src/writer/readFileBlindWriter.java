package writer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.undo.UndoManager;

import writer.ui.EditorFrame;
import writer.ui.editor.BraillePrefixer;
import writer.ui.editor.FastHighlighter;
import xml.node;
import xml.transformeXLMtoNode;

/**
 * Chargement d'un fichier .bwr dans l'éditeur.
 * - insertion sur l'EDT
 * - rechargement des bookmarks
 * - vidage de l'UndoManager après chargement pour éviter des undo invalides
 */
public class readFileBlindWriter {

    public boolean erreur = false;

    public readFileBlindWriter(File selectedFile, EditorFrame parent) {
        try {
            // --- Lecture du fichier (texte brut XML) ---
            String content = Files.readString(selectedFile.toPath());

            // --- Transformation XML → nœuds internes ---
            new transformeXLMtoNode(content, false, null);
            node newNode = transformeXLMtoNode.getNodeRoot().retourneFirstEnfant("blindWriter");

            if (newNode == null) {
                erreur = true;
                return;
            }

            // --- Affectation globale ---
            commandes.nodeblindWriter = newNode;

            // Vérifications de structure minimale
            if (commandes.nodeblindWriter.getAttributs().get("filename") == null
                    || commandes.nodeblindWriter.retourneFirstEnfant("styles_paragraphes") == null
                    || commandes.nodeblindWriter.retourneFirstEnfant("meta") == null
                    || commandes.nodeblindWriter.retourneFirstEnfant("contentText") == null) {
                erreur = true;
                return;
            }

            // --- Sections internes ---
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

            // Texte principal
            commandes.texteDocument = commandes.nodeblindWriter
                    .retourneFirstEnfant("contentText")
                    .getContenuAvecTousLesContenusDesEnfants();
            
            //place les pieds de mouche ou insère après le pied de mouche un espace au début de chaque paragraphe
            commandes.texteDocument = BraillePrefixer.prefixParagraphsWithPiedDeMouche(commandes.texteDocument);
            
            commandes.hash = commandes.nodeblindWriter == null ? 0 :  commandes.nodeblindWriter.hashCode();

            // --- Chargement asynchrone dans l'éditeur (exécuté sur l'EDT) ---
            final String newText = commandes.texteDocument == null ? "" : commandes.texteDocument;
            SwingUtilities.invokeLater(() -> {
                try {
                	writer.ui.NormalizingTextPane editorComp = parent.getEditor();
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

                    // colorisation
                    FastHighlighter.rehighlightAll(editorComp); // une passe globale, optionnelle

                    // vide l'historique
                    parent.clearUndoHistory();
                    
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


                    // final : revalidate / repaint / focus
                    editorComp.revalidate();
                    editorComp.repaint();
                    editorComp.requestFocusInWindow();

                } catch (Exception e) {
                    e.printStackTrace();
                    erreur = true;
                }
            });

        } catch (IOException ex) {
            ex.printStackTrace();
            erreur = true;
        }
    }

    // getters / setters
    public boolean isErreur() { return erreur; }
    public void setErreur(boolean erreur) { this.erreur = erreur; }
}
