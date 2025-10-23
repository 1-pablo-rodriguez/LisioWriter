package writer;

import java.awt.Font;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.text.BadLocationException;

import xml.node;
import xml.transformeXLMtoNode;

public class readFileBlindWriter {

	public boolean erreur = false;
	
	public readFileBlindWriter(File selectedFile) {
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
                
                blindWriter.editorPane.setText(commandes.texteDocument);

                blindWriter.editorPane.setCaretPosition(0);
                
                blindWriter.bookmarks = new writer.bookmark.BookmarkManager(blindWriter.editorPane); // repart sur le nouveau doc
                // puis, si tu as un fichier ouvert, recharge les marque-pages
                //blindWriter.bookmarks.loadSidecar(commandes.currentFilePath);
                if(commandes.nodeblindWriter.retourneFirstEnfant("bookmarks") != null) {
                	commandes.bookmarks = commandes.nodeblindWriter.retourneFirstEnfant("bookmarks");
                	blindWriter.bookmarks.loadFromXml(commandes.nodeblindWriter.retourneFirstEnfant("bookmarks"));
                }
                
                System.out.println("Menu visible: " + blindWriter.menuPages().isShowing());
                System.out.println("Parent: " + blindWriter.menuPages().getParent());
                
                
                MAJMenuPage();

                
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
	
	
	
	public static void MAJMenuPage() {
		JMenu menu = blindWriter.menuPages();
        menu.removeAll();
        
        blindWriter.cachedMenuPages = null;
       menuP(menu);
        
        blindWriter.menuPages().revalidate();
        blindWriter.menuPages().repaint();
	}
	
	
	private static void menuP(JMenu filePage) {
    	
    	JCheckBoxMenuItem checkBoxActivePageTitre= new JCheckBoxMenuItem("Première page couverture");
    	checkBoxActivePageTitre.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	 if(Boolean.valueOf( commandes.pageTitre.getAttributs().get("couverture"))) {
        	checkBoxActivePageTitre.setSelected(true);
        	checkBoxActivePageTitre.getAccessibleContext().setAccessibleName("Case cochée : page de couverture.");
        }else {
        	checkBoxActivePageTitre.setSelected(false);
        	checkBoxActivePageTitre.getAccessibleContext().setAccessibleDescription("Case décochée : pas de page de couverture.");
        }
     // Ajouter un écouteur d'événement pour détecter les changements
        checkBoxActivePageTitre.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (checkBoxActivePageTitre.isSelected()) {
                	commandes.pageTitre.getAttributs().put("couverture", "true");
                	checkBoxActivePageTitre.getAccessibleContext().setAccessibleDescription("Case cochée : page de couverture.");

                } else {
                	commandes.pageTitre.getAttributs().put("couverture", "false");
                	checkBoxActivePageTitre.getAccessibleContext().setAccessibleDescription("Case décochée : pas de page de couverture.");

                }
                readFileBlindWriter.MAJMenuPage();
                blindWriter.addItemChangeListener(checkBoxActivePageTitre);
            }
        });
    	
    	
    	
        // Créer un JCheckBoxMenuItem
        JCheckBoxMenuItem checkBoxActiveEntete= new JCheckBoxMenuItem("Afficher dans l'entête le titre");
        checkBoxActiveEntete.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        if(Boolean.valueOf(commandes.pageDefaut.getAttributs().get("entete"))) {
        	checkBoxActiveEntete.setSelected(true);
        	 checkBoxActiveEntete.getAccessibleContext().setAccessibleName("Case cochée : Afficher dans l'entête le titre");
        }else {
        	checkBoxActiveEntete.setSelected(false);
        	 checkBoxActiveEntete.getAccessibleContext().setAccessibleName("Case décochée : n'Afficher pas le titre dans l'entête");  
        }
        // Ajouter un écouteur d'événement pour détecter les changements
        checkBoxActiveEntete.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (checkBoxActiveEntete.isSelected()) {
              	  System.out.println("Active l'entête"); // Debugger
              	  commandes.pageDefaut.getAttributs().put("entete", "true");
                  checkBoxActiveEntete.getAccessibleContext().setAccessibleDescription("Afficher le titre dans l'entête");

                } else {
              	  System.out.println("Désactive l'enête"); // Debugger
              	  commandes.pageDefaut.getAttributs().put("entete", "false");
              	  checkBoxActiveEntete.getAccessibleContext().setAccessibleDescription("Pas d'entête");

                }
                readFileBlindWriter.MAJMenuPage();
                blindWriter.addItemChangeListener(checkBoxActiveEntete);
            }
        });
        
        // Créer un JCheckBoxMenuItem
        JCheckBoxMenuItem checkBoxActivePiedPage= new JCheckBoxMenuItem("Afficher le numéro de la page dans le pied de page");
        checkBoxActivePiedPage.setFont(new Font("Segoe UI", Font.PLAIN, 18));
         if(Boolean.valueOf(commandes.pageDefaut.getAttributs().get("piedpage"))) {
        	checkBoxActivePiedPage.setSelected(true);
            checkBoxActivePiedPage.getAccessibleContext().setAccessibleName("Case cochée : Afficher le numéro de la page dans le pied de page"); 
         }else {
        	checkBoxActivePiedPage.setSelected(false);
            checkBoxActivePiedPage.getAccessibleContext().setAccessibleName("Case décochée : n'Afficher pas le numéro de la page dans le pied de page");
         }
        // Ajouter un écouteur d'événement pour détecter les changements
        checkBoxActivePiedPage.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (checkBoxActivePiedPage.isSelected()) {
              	  System.out.println("Active le pied de page"); // Debugger
              	  commandes.pageDefaut.getAttributs().put("piedpage", "true");
              	  checkBoxActivePiedPage.getAccessibleContext().setAccessibleDescription("Affiche le numéro de page dans le pied de page.");
	       	  
              	 } else {
              	  System.out.println("Désactive le pied de page"); // Debugger
              	  commandes.pageDefaut.getAttributs().put("piedpage", "false");
              	  checkBoxActivePiedPage.getAccessibleContext().setAccessibleDescription("Pas de pied de page");
 
                }
                readFileBlindWriter.MAJMenuPage();
                blindWriter.addItemChangeListener(checkBoxActivePiedPage);
            }
        });
       
        // Ajouter des ChangeListeners pour les JMenuItem
        blindWriter.addItemChangeListener(checkBoxActiveEntete);
        blindWriter.addItemChangeListener(checkBoxActivePiedPage);
        blindWriter.addItemChangeListener(checkBoxActivePageTitre);
        
        filePage.add(checkBoxActivePageTitre);
        filePage.add(checkBoxActiveEntete);
        filePage.add(checkBoxActivePiedPage);
        
        
        blindWriter.cachedMenuPages = filePage;
                
	}
	
	
	
}
