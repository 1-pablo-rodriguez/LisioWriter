// writer/ui/menu/MenuBarFactory.java
package writer.ui.menu;


import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import dia.BoiteNewDocument;
import dia.BoiteNonVoyant;
import dia.BoiteRenameFile;
import dia.BoiteSaveAs;
import dia.HtmlBrowserDialog;
import dia.boiteMeta;
import dia.navigateurT1;
import dia.ouvrirTxt;
import exportODF.MarkdownOdfExporter;
import exportPDF.PdfExporter;
import exporterHTML.HtmlExporter;
import maj.AutoUpdater;
import maj.UpdateDialog;
import writer.commandes;
import writer.enregistre;
import writer.model.Affiche;
import writer.ui.EditorApi;
import writer.ui.EditorFrame;

public final class MenuBarFactory {
    private MenuBarFactory() {}

    public static JMenuBar create(EditorApi ctx) {
        JMenuBar bar = new JMenuBar();
        bar.add(menuFichier(ctx));
        bar.add(menuEdition(ctx));
        bar.add(menuNaviguer(ctx));
        bar.add(menuAppliquerStyle(ctx));
        bar.add(menuFormatageTexte(ctx));
        bar.add(menuInsertion(ctx));
        bar.add(menuImporte(ctx));
        bar.add(menuExporter(ctx));
        bar.add(menuSources(ctx));
        bar.add(menuDocumentation(ctx));
        bar.add(menuPreference(ctx));
        
        return bar;
    }

    private static JMenuItem createMenuItem(String text, int key, int mod, ActionListener action) {
        JMenuItem mi = new JMenuItem(text);
        mi.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        if (key != 0) mi.setAccelerator(KeyStroke.getKeyStroke(key, mod));
        mi.addActionListener(action);
        return mi;
    }
    
    private static JMenuItem createSimpleMenuItem(String text, ActionListener action) {
        JMenuItem mi = new JMenuItem(text);
        mi.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        mi.addActionListener(action);
        return mi;
    }

    // Menu Fichier
    private static JMenu menuFichier(EditorApi ctx) {
        JMenu m = new JMenu("Fichier");
        m.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        m.setMnemonic(KeyEvent.VK_F);
        m.getAccessibleContext().setAccessibleName("Fichier");
        m.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                SwingUtilities.invokeLater(() -> {
                    JMenu src = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) src.getParent();
                        msm.setSelectedPath(new MenuElement[] { bar, src, src.getPopupMenu() });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });

        JMenuItem createItem = createMenuItem("Nouveau", KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, e -> {
        	var win = ctx.getWindow();
        	if (win instanceof EditorFrame frame) {
                new BoiteNewDocument(frame);
                ctx.setModified(false);
                ctx.updateWindowTitle();
            } else {
                System.err.println("Impossible d’ouvrir la boîte : la fenêtre n’est pas un EditorFrame.");
                Toolkit.getDefaultToolkit().beep();
                return;
            }
        });

        JMenuItem openItem = createMenuItem("Ouvrir", KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK, e -> {
        	var win = ctx.getWindow();
			if (win instanceof EditorFrame frame) {
				 new dia.ouvrirBWR(frame);
			}
            ctx.setModified(false);
            ctx.updateWindowTitle();
        });

        JMenuItem saveItem = createMenuItem("Enregistrer", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK, e -> {
        	var win = ctx.getWindow();
			if (win instanceof EditorFrame frame) {
				 ctx.clearSpellHighlightsAndFocusEditor();
		            new enregistre(frame);
		            ctx.setModified(false);
		            ctx.updateWindowTitle();

		            StringBuilder msg = new StringBuilder(128);
		            msg.append("Fichier enregistré ↓")
		               .append("\n• Fichier : ").append(commandes.nameFile).append(".bwr ↓")
		               .append("\n• Dossier : ").append(commandes.nomDossierCourant).append(" ↓");
		            ctx.showInfo("Information", msg.toString());
			}
        });

        JMenuItem saveAsItem = createMenuItem("Enregistrer sous", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, e -> {
            ctx.clearSpellHighlightsAndFocusEditor();
            var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
            	 new BoiteSaveAs(frame);
                 ctx.setModified(false);
                 ctx.updateWindowTitle();

                 StringBuilder msg = new StringBuilder(128);
                 msg.append("Fichier enregistré ↓")
                    .append("\n• Fichier : ").append(commandes.nameFile).append(".bwr ↓")
                    .append("\n• Dossier : ").append(commandes.nomDossierCourant).append(" ↓");
                 ctx.showInfo("Information", msg.toString());
            }
        });

        JMenuItem renameItem = createMenuItem("Renommer le fichier", KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK, e -> {
            ctx.clearSpellHighlightsAndFocusEditor();
            var win = ctx.getWindow();
        	if (win instanceof EditorFrame frame) {
        		new BoiteRenameFile(frame);
                ctx.setModified(false);
                ctx.updateWindowTitle();
        	} 
        });

        JMenuItem metaItem = createMenuItem("Meta-données", KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK, e -> {
            ctx.clearSpellHighlightsAndFocusEditor();
            var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
            	new boiteMeta(frame);
            }
        });

        JMenuItem quitItem = createMenuItem("Quitter", KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK, e -> {
        	 var win = ctx.getWindow();
             if (win instanceof EditorFrame frame) {
                 new dia.BoiteQuitter(frame);
             }
        });

        // les listeners d’état (conserve le comportement existant)
        ctx.addItemChangeListener(createItem);
        ctx.addItemChangeListener(openItem);
        ctx.addItemChangeListener(saveItem);
        ctx.addItemChangeListener(saveAsItem);
        ctx.addItemChangeListener(renameItem);
        ctx.addItemChangeListener(metaItem);
        ctx.addItemChangeListener(quitItem);

        m.add(createItem);
        m.add(openItem);
        m.add(saveItem);
        m.add(saveAsItem);
        m.add(renameItem);
        m.add(metaItem);
        m.add(quitItem);
        return m;
    }

    //Menu Edition
    @SuppressWarnings("serial")
	private static JMenu menuEdition(EditorApi ctx) {
    	JMenu editionMenu = new JMenu("Édition");
    	editionMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	editionMenu.setMnemonic(KeyEvent.VK_E); // Utiliser ALT+e pour ouvrir le menu
    	editionMenu.getAccessibleContext().setAccessibleName("Édition");
    	editionMenu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
    	
    	JMenuItem undoItem = new JMenuItem(ctx.getUndoAction());
    	JMenuItem redoItem = new JMenuItem(ctx.getRedoAction());
    	
    	JMenuItem edition = new JMenuItem(new act.ToggleEditAction(ctx.getEditor()));
    	edition.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));
    	edition.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	editionMenu.add(edition);

    	Action actCheckDoc = new AbstractAction("Vérifier tout le document") {
     		  @Override public void actionPerformed(ActionEvent e) {
     			 new act.actCheckDoc(ctx);
     		  }
     		};
     		
 		Action actCheckWindow = new AbstractAction("Vérifier la sélection / le paragraphe") {
 	 		  @Override public void actionPerformed(ActionEvent e) {
 	 			 new act.actCheckWindow(ctx);
 	 		  }
 	 		};

     	 // Vérification de tout le document	
     	 JMenuItem checkAll = new JMenuItem(actCheckDoc);
     	 checkAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.CTRL_DOWN_MASK));
        
     	 // Vérification du paragraphe
    	JMenuItem checkSel = new JMenuItem(actCheckWindow);
    	checkSel.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7,
    	        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));

		// Nettoyer les soulignements, et prefix °°
		JMenuItem clear = createSimpleMenuItem("RAZ vérification",e -> { 
			writer.spell.SpellCheckLT spell = ctx.getSpell();
			if (spell != null) { 
				spell.clearHighlights(); ctx.getEditor().requestFocusInWindow(); 
				}});
		
		// Mode temps réel (ON/OFF)
		javax.swing.JCheckBoxMenuItem live = new javax.swing.JCheckBoxMenuItem("Vérification durant la frappe");
		live.setFont(new Font("Segoe UI", Font.PLAIN, 18));
		writer.spell.SpellCheckLT spell = ctx.getSpell();
		live.setSelected(spell != null && spell.isRealtime());
		live.addActionListener(e -> { 
			if (spell != null) spell.setRealtime(live.isSelected());
			commandes.verificationOrthoGr = live.isSelected();
		});
		
		JMenuItem suggestion = createSimpleMenuItem("Vérif. suggestion(s)", e -> {
		    spell.showPopupAtCaret();
		});

    	
    	// Ajouter des ChangeListeners pour les JMenuItem
		 ctx.addItemChangeListener(undoItem);
		 ctx.addItemChangeListener(edition);
		 ctx.addItemChangeListener(redoItem);
		 ctx.addItemChangeListener(checkAll);
		 ctx.addItemChangeListener(checkSel);
		 ctx.addItemChangeListener(clear);
		 ctx.addItemChangeListener(live);
		 ctx.addItemChangeListener(suggestion);

        editionMenu.add(undoItem);
        editionMenu.add(redoItem);
        editionMenu.addSeparator();
        editionMenu.add(edition);
    	editionMenu.addSeparator();
    	editionMenu.add(checkAll);
    	editionMenu.add(checkSel);
    	editionMenu.add(clear);
    	editionMenu.add(live);
    	editionMenu.add(suggestion);

    	return editionMenu;
    }
    
    //Menu Import
    private static JMenu menuImporte(EditorApi ctx) {
    	JMenu fileMenu = new JMenu("Importer");
        fileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        fileMenu.setMnemonic(KeyEvent.VK_M); // Utiliser ALT+M pour ouvrir le menu
        fileMenu.getAccessibleContext().setAccessibleName("Importer");
        // Listener déclenché à l’ouverture du menu
        fileMenu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        
        JMenuItem open2Item = createMenuItem("Importer fichier LibreOffice Writer", KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Ouverture fichier .odt");
            var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
            	new dia.ouvrirODT(frame);
                ctx.setModified(false);
            }
        });
        
        JMenuItem openItem = createSimpleMenuItem("Importer fichier texte",e -> {
            System.out.println("Ouverture fichier .txt");
            var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
               new ouvrirTxt(frame);
               ctx.setModified(false);
            }
        });
       
        JMenuItem open3Item = createMenuItem("Importer fichier Microsoft Word", KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Ouverture fichier .docx");
            var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
            	  new dia.ouvrirDOCX(frame);
            }
            ctx.setModified(false);
        });    
        
        JMenuItem importHtml = new JMenuItem("Importer HTML...");
        importHtml.addActionListener(e -> {
        	 var win = ctx.getWindow();
             if (win instanceof EditorFrame frame) {
            	 new dia.ouvrirHTML(frame);
             }
        	ctx.setModified(false);
        }); 
        
        JMenuItem importHtmlBrower = new JMenuItem("Importer Web...");
        importHtmlBrower.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> new HtmlBrowserDialog(ctx.getWindow(), ctx.getEditor()));
        });

       
        ctx.addItemChangeListener(open2Item);
        ctx.addItemChangeListener(openItem);
        ctx.addItemChangeListener(open3Item);
        ctx.addItemChangeListener(importHtml);
        ctx.addItemChangeListener(importHtmlBrower);
        
        fileMenu.add(open2Item);
        fileMenu.add(openItem);
        fileMenu.add(open3Item);
        fileMenu.add(importHtml);
        fileMenu.add(importHtmlBrower);
        
        return fileMenu;
    }

    // Menu aplliquer un style
    private static JMenu menuAppliquerStyle(EditorApi ctx) {
    	JMenu fileAppliqueStyle = new JMenu("Appliquer un style");
    	fileAppliqueStyle.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	fileAppliqueStyle.setMnemonic(KeyEvent.VK_U); 
    	fileAppliqueStyle.getAccessibleContext().setAccessibleName("Appliquer un style");
    	// Listener déclenché à l’ouverture du menu
    	fileAppliqueStyle.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        }); 
    	
    	JMenuItem ctItem = createMenuItem("Corps de texte", KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique Corps de texte"); // Debugger
            new styles.bodyTexte(ctx).appliquer();	
        });
    	ctItem.getAccessibleContext().setAccessibleName("Appliquer le style Corps de texte au paragraphe.");
    	
    	
    	JMenuItem t1Item = createMenuItem("Titre 1", KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre 1"); // Debugger
            new styles.titre1(ctx).appliquer();
        });
    	
    	JMenuItem t2Item = createMenuItem("Titre 2", KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre 2"); // Debugger
            new styles.titre2(ctx).appliquer();
        });
    	
    	JMenuItem t3Item = createMenuItem("Titre 3", KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre 3"); // Debugger
            new styles.titre3(ctx).appliquer();
        });
    	 
    	JMenuItem t4Item = createMenuItem("Titre 4", KeyEvent.VK_4, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre 4"); // Debugger
            new styles.titre4(ctx).appliquer();
        });
    	
    	JMenuItem t5Item = createMenuItem("Titre 5", KeyEvent.VK_5, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre 5"); // Debugger
            new styles.titre5(ctx).appliquer();
        });
    	 
    	JMenuItem tPrinItem = createMenuItem("Titre principale", KeyEvent.VK_8, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre principale"); // Debugger
            new styles.titrePrincipale(ctx).appliquer();
        });
    	 
    	JMenuItem sTitreItem = createMenuItem("Sous titre principale", KeyEvent.VK_9, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le Sous titre"); // Debugger
            new styles.sousTitre(ctx).appliquer();
        });
    		
    	
    	JMenuItem sPuceItem = createMenuItem("Puce", KeyEvent.VK_7, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Puce"); // Debugger
            new styles.listeNonNumero(ctx).appliquer();
        });
    	
    	// Ajouter des ChangeListeners pour les JMenuItem
    	ctx.addItemChangeListener(ctItem);
    	ctx.addItemChangeListener(tPrinItem);
    	ctx.addItemChangeListener(sTitreItem);
    	ctx.addItemChangeListener(t1Item);
    	ctx.addItemChangeListener(t2Item);
    	ctx.addItemChangeListener(t3Item);
    	ctx.addItemChangeListener(t4Item);
    	ctx.addItemChangeListener(t5Item);
    	ctx.addItemChangeListener(sPuceItem);
        
    	fileAppliqueStyle.add(ctItem);
    	fileAppliqueStyle.add(tPrinItem);
    	fileAppliqueStyle.add(sTitreItem);
    	fileAppliqueStyle.add(t1Item);
    	fileAppliqueStyle.add(t2Item);
    	fileAppliqueStyle.add(t3Item);
    	fileAppliqueStyle.add(t4Item);
    	fileAppliqueStyle.add(t5Item);
    	fileAppliqueStyle.add(sPuceItem); 
	
    	return fileAppliqueStyle;
    }

    // Menu formatage de texte
    private static JMenu menuFormatageTexte(EditorApi ctx) {
    	JMenu fileFormatage = new JMenu("Formatage local texte");
    	fileFormatage.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	fileFormatage.setMnemonic(KeyEvent.VK_O); // Utiliser ALT+F pour ouvrir le menu
    	fileFormatage.getAccessibleContext().setAccessibleName("Formatage local texte");
    	// Listener déclenché à l’ouverture du menu
    	fileFormatage.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                System.out.println("🔵 Menu formatage local ouvert !");
            }

            @Override
            public void menuDeselected(MenuEvent e) {}
            @Override
            public void menuCanceled(MenuEvent e) {}
        });
    	
	      JMenuItem souligneItem = createSimpleMenuItem("Souligner", e -> {
	            new formatage.Souligne(ctx).appliquer();
	        });
	      souligneItem.getAccessibleContext().setAccessibleName("Souligner");

	      JMenuItem soulignegrasItem = createSimpleMenuItem("Souligner Gras",e -> {
	            new formatage.SoulignerGras(ctx).appliquer();
	        });
	      soulignegrasItem.getAccessibleContext().setAccessibleName("Souligner gras");
	       
	      JMenuItem souligneitalicItem = createSimpleMenuItem("Souligner Italique",e -> {
	            new formatage.SouligneItalic(ctx).appliquer();
	        });
	      souligneitalicItem.getAccessibleContext().setAccessibleName("Souligner italique");
	       
	      
    	  JMenuItem grasItem = createSimpleMenuItem("Gras", e -> {
              new formatage.Gras(ctx).appliquer();
          });
    	  grasItem.getAccessibleContext().setAccessibleName("Gras");
    	  
    	  JMenuItem italicItem = createSimpleMenuItem("Italique", e -> {
              new formatage.Italique(ctx).appliquer();
          });
    	  italicItem.getAccessibleContext().setAccessibleName("Italique");
    	  
    	  JMenuItem grasitaliqueItem = createSimpleMenuItem("Gras Italique", e -> {
              new formatage.GrasItalique(ctx).appliquer();
          });
    	  grasitaliqueItem.getAccessibleContext().setAccessibleName("Gras Italique");
    	  
    	  JMenuItem exposantItem = createSimpleMenuItem("Exposant", e -> {
              new formatage.Exposant(ctx).appliquer();
          });
    	  exposantItem.getAccessibleContext().setAccessibleName("Exposant");
    	  
    	  JMenuItem indiceItem = createSimpleMenuItem("Indice", e -> {
              new formatage.Indice(ctx).appliquer();
          });
    	  indiceItem.getAccessibleContext().setAccessibleName("Indice");
    	  
    	  // Ajouter des ChangeListeners pour les JMenuItem
    	  ctx.addItemChangeListener(souligneItem);
    	  ctx.addItemChangeListener(soulignegrasItem);
    	  ctx.addItemChangeListener(souligneitalicItem);
    	  ctx.addItemChangeListener(grasItem);
    	  ctx.addItemChangeListener(italicItem);  
    	  ctx.addItemChangeListener(grasitaliqueItem);
    	  ctx.addItemChangeListener(exposantItem);
    	  ctx.addItemChangeListener(indiceItem);
    	  
          fileFormatage.add(souligneItem);
          fileFormatage.add(soulignegrasItem);
          fileFormatage.add(souligneitalicItem);
    	  fileFormatage.add(grasItem);
    	  fileFormatage.add(italicItem);
    	  fileFormatage.add(grasitaliqueItem);
    	  fileFormatage.addSeparator();
    	  fileFormatage.add(exposantItem);
    	  fileFormatage.add(indiceItem);
    	
        return fileFormatage;
    }

    // Menu Insertion
    private static JMenu menuInsertion(EditorApi ctx) {
    	JMenu Insertion = new JMenu("Insertion");
    	Insertion.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	Insertion.setMnemonic('i'); // Utiliser ALT+N pour ouvrir le menu
    	Insertion.getAccessibleContext().setAccessibleName("Insertion");
    	// Listener déclenché à l’ouverture du menu
    	Insertion.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
    	
    	
    	JMenuItem sautPageItem = createMenuItem("Insérer un saut de page manuel", KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Insérer un saut de page manuel");
            new page.sautPage(ctx).appliquer();
        });
         
        JMenuItem citationBasdePageItem = createMenuItem("Insérer une note de bas de page", KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Insertion note de bas de page");
            new writer.noteBasPage(ctx).appliquer();
        });
   	 	
	   	JMenuItem TOCItem = createMenuItem("Insérer une table des matières", KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK, e -> {
	         System.out.println("Insertion table des matière TOC");
	         new writer.tableMatieres(ctx).appliquer();
	    });
	   	
    
        // Ajouter des ChangeListeners pour les JMenuItem
	    ctx.addItemChangeListener(sautPageItem);
	    ctx.addItemChangeListener(citationBasdePageItem);
	    ctx.addItemChangeListener(TOCItem);

        Insertion.add(sautPageItem);
        Insertion.add(citationBasdePageItem);
        Insertion.add(TOCItem);

        return Insertion; 
    }
    
    // Menu Exporter
    private static JMenu menuExporter(EditorApi ctx) {
    	JMenu fileMenu = new JMenu("Exporter");
        fileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        fileMenu.setMnemonic(KeyEvent.VK_X); // Utiliser ALT+x pour ouvrir le menu
        fileMenu.getAccessibleContext().setAccessibleName("Exporter");
        // Listener déclenché à l’ouverture du menu
        fileMenu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        
        JMenuItem exportItem = createSimpleMenuItem("Exporter en .ODT (Writer)", e -> {
            System.out.println("Export au format ODF Writer"); // Debugger
            //ExportODFWriter();
            try {
            	writer.spell.SpellCheckLT spell = ctx.getSpell();
            	if (spell != null) { spell.clearHighlights(); ctx.getEditor().requestFocusInWindow(); }
            	java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(ctx.getEditor());
            	MarkdownOdfExporter.export(ctx.getEditor().getText(), new File(commandes.currentDirectory + "/" + commandes.nameFile+".odt"));
            	StringBuilder msg = new StringBuilder();
                msg.append("Info. Exportation terminé."
                		+ "\nFichier : " + commandes.nameFile+".odt"
                		+ "\nDossier : " + commandes.currentDirectory);
                dia.InfoDialog.show(owner, "Exportation", msg.toString());
            } catch (Exception e1) {
				e1.printStackTrace();
			}
        });
        
        JMenuItem exportPDFItem = createSimpleMenuItem("Exporter en .PDF", e -> {
            System.out.println("Export au format PDF"); // Debugger
			try {
				String html = PdfExporter.convertMarkupToHtml(ctx.getEditor().getText());
				
				// construire proprement le chemin de sortie
				Path outPath = Paths.get(commandes.currentDirectory.getPath(), commandes.nameFile + ".pdf").toAbsolutePath();
				String out = outPath.toString();
				System.out.println("EXPORT PATH (absolu) => " + out);
				try {
				    PdfExporter.htmlToPdf(html, out, null); // "C:\\Windows\\Fonts\\arial.ttf"
				    System.out.println("Après appel : exists=" + outPath.toFile().exists() + " size=" + (outPath.toFile().exists() ? outPath.toFile().length() : 0));
				} catch (Throwable t) {
				    System.err.println("Erreur lors de l'export HTML->PDF :");
				    t.printStackTrace();
				}
				
				java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(ctx.getEditor());
            	StringBuilder msg = new StringBuilder();
            	msg.append("Info. Exportation terminé."
                 		+ "\nFichier : " + commandes.nameFile+".pdf"
                 		+ "\nDossier : " + commandes.currentDirectory);
            	dia.InfoDialog.show(owner, "Exportation", msg.toString());
			} catch (Exception e1) {
				e1.printStackTrace();
			}
 
        });
        
        JMenuItem exportHTMLItem = createSimpleMenuItem("Exporter en .HTML", e -> {
            System.out.println("Export au format HTML"); // Debugger
            try {
                String html = PdfExporter.convertMarkupToHtml(ctx.getEditor().getText());

                // Construire le outPath proprement selon le type de commandes.currentDirectory
                Path outPath;
                if (commandes.currentDirectory instanceof java.io.File) {
                    File base = commandes.currentDirectory;
                    String safeName = HtmlExporter.sanitizeFileName(commandes.nameFile) + ".html";
                    outPath = base.toPath().resolve(safeName).toAbsolutePath();
                } else {
                    // si currentDirectory est une String
                    outPath = Paths.get(commandes.currentDirectory.toString(), HtmlExporter.sanitizeFileName(commandes.nameFile) + ".html").toAbsolutePath();
                }

                // Exporter et tenter d'ouvrir immédiatement
                Path written = HtmlExporter.exportHtml(html, outPath, false);

                // Message d'information pour l'utilisateur
                java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(ctx.getEditor());
                StringBuilder msg = new StringBuilder();
                msg.append("Info. Exportation terminée.")
                   .append("\nFichier : ").append(written.getFileName().toString())
                   .append("\nDossier : ").append(written.getParent() == null ? "<racine>" : written.getParent().toString());
                dia.InfoDialog.show(owner, "Exportation", msg.toString());

            } catch (Exception ex) {
                ex.printStackTrace();
                java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(ctx.getEditor());
                dia.InfoDialog.show(owner, "Erreur export HTML", "L'exportation a échoué : " + ex.getMessage());
            }
        });

       
        ctx.addItemChangeListener(exportItem);
        ctx.addItemChangeListener(exportPDFItem);
        ctx.addItemChangeListener(exportHTMLItem);
        
        fileMenu.add(exportItem);
        fileMenu.add(exportPDFItem);
        fileMenu.add(exportHTMLItem);
        
        return fileMenu;
    }

    //Menu Naviguer
    private static JMenu menuNaviguer(EditorApi ctx) {
    	JMenu naviguerMenu = new JMenu("Naviguer");
    	naviguerMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	naviguerMenu.setMnemonic(KeyEvent.VK_N); // Utiliser ALT+n pour ouvrir le menu
    	naviguerMenu.getAccessibleContext().setAccessibleName("Naviguer");
        // Listener déclenché à l’ouverture du menu
    	naviguerMenu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
    	
    	
    	JMenuItem navigateurItem = createMenuItem("Navigateur", KeyEvent.VK_F6, 0, e -> {
    		writer.spell.SpellCheckLT spell = ctx.getSpell();
    		if (spell != null) { spell.clearHighlights(); ctx.getEditor().requestFocusInWindow(); }
    		 var win = ctx.getWindow();
             if (win instanceof EditorFrame frame) {
                 new navigateurT1(frame);
             }
        });
    	
    	JMenuItem rechercher = createMenuItem("Rechercher texte", KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK, e -> {
    		writer.spell.SpellCheckLT spell = ctx.getSpell();
    		if (spell != null) { spell.clearHighlights(); ctx.getEditor().requestFocusInWindow(); }
            new writer.openSearchDialog(ctx.getEditor());
        });
	
    	//-------------- Marque page ----------------
	
    	JMenuItem bmToggle = createSimpleMenuItem("Marque-page (basculer)", e -> {
    	    var m = ctx.getBookmarks();
    	    if (m == null) {
    	        java.awt.Toolkit.getDefaultToolkit().beep();
    	        return;
    	    }
    	    boolean added = m.toggleHere();
    	    String message = (added ? "Marque-page ajouté." : "Marque-page supprimé.");
    	    java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(ctx.getEditor());
    	    dia.InfoDialog.show(owner, "Information", message);
    	    ctx.setModified(true);
    	});
		bmToggle.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, InputEvent.CTRL_DOWN_MASK));
		bmToggle.setFont(new Font("Segoe UI", Font.PLAIN, 18));
		

		JMenuItem bmNote = createSimpleMenuItem("Marque-page note", e -> { 
			var m = ctx.getBookmarks();                      
		    if (m == null) {                   
		        java.awt.Toolkit.getDefaultToolkit().beep();
		        return;
		    }
		    m.editNoteForNearest(javax.swing.SwingUtilities.getWindowAncestor(ctx.getEditor()));
			});
		bmNote.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2,InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
		bmNote.setFont(new Font("Segoe UI", Font.PLAIN, 18));
		
		
		JMenuItem bmNext = createSimpleMenuItem("Marque-page suivant", e -> { 
		var m = ctx.getBookmarks();                      
		if (m == null) {                   
		    java.awt.Toolkit.getDefaultToolkit().beep();
		    return;
		}
		m.goNext(); 
		});
		bmNext.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0));
		bmNext.setFont(new Font("Segoe UI", Font.PLAIN, 18));

		JMenuItem bmPrev = createSimpleMenuItem("Marque-page précédent", e -> { 
		var m = ctx.getBookmarks();                      
		if (m == null) {                   
		    java.awt.Toolkit.getDefaultToolkit().beep();
		    return;
		}
		m.goPrev(); 
		});
		bmPrev.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.SHIFT_DOWN_MASK));
		bmPrev.setFont(new Font("Segoe UI", Font.PLAIN, 18));
		
		// -------- Position dans texte (F2) --------
		JMenuItem posItem = new JMenuItem(ctx.actAnnouncePosition());
		posItem.setText("Titre avant & après");
		posItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
		posItem.setFont(new Font("Segoe UI", Font.PLAIN, 18));
		
		// -------- Titre suivant (F3) --------
		JMenuItem nextHeadingItem = new JMenuItem(ctx.actGotoNextHeading());
		nextHeadingItem.setText("Titre suivant");
		nextHeadingItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
		nextHeadingItem.setFont(new Font("Segoe UI", Font.PLAIN, 18));

		// -------- Titre précédent (Shift+F3) --------
		JMenuItem prevHeadingItem = new JMenuItem(ctx.actGotoPrevHeading());
		prevHeadingItem.setText("Titre précédent");
		prevHeadingItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK));
		prevHeadingItem.setFont(new Font("Segoe UI", Font.PLAIN, 18));

		 ctx.addItemChangeListener(navigateurItem);
		 ctx.addItemChangeListener(rechercher);
		 ctx.addItemChangeListener(bmToggle);
		 ctx.addItemChangeListener(bmNote);
		 ctx.addItemChangeListener(bmNext);
		 ctx.addItemChangeListener(bmPrev);
		 ctx.addItemChangeListener(posItem);
		 ctx.addItemChangeListener(nextHeadingItem);
		 ctx.addItemChangeListener(prevHeadingItem);
        
        naviguerMenu.add(navigateurItem);
        naviguerMenu.addSeparator();
        naviguerMenu.add(rechercher);
        naviguerMenu.addSeparator();
        naviguerMenu.add(bmToggle);
        naviguerMenu.add(bmNote);
        naviguerMenu.add(bmNext);
        naviguerMenu.add(bmPrev);
        naviguerMenu.addSeparator();
        naviguerMenu.add(posItem);
        naviguerMenu.add(nextHeadingItem);
        naviguerMenu.add(prevHeadingItem);

	
	return naviguerMenu;
    }

    // Menu Sources pour réaliser une bibilographie
    private static JMenu menuSources(EditorApi ctx) {
    	JMenu fileSources = new JMenu("Sources");
    	fileSources.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	fileSources.setMnemonic(KeyEvent.VK_S); 
    	fileSources.getAccessibleContext().setAccessibleName("Sources");
    	// Listener déclenché à l’ouverture du menu
    	fileSources.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        }); 
    	
    	return fileSources;
    }
    
    // Menu Documentation
    private static JMenu menuDocumentation(EditorApi ctx) {
    	JMenu fileDocumentation = new JMenu("Documentation");
        fileDocumentation.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        fileDocumentation.setMnemonic('D'); // Utiliser ALT+F pour ouvrir le menu
        // Listener déclenché à l’ouverture du menu
        fileDocumentation.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
			@Override public void menuCanceled(MenuEvent e) {}
        }); 
        
        JMenuItem afficheDocItem = createMenuItem("Doc. blindWriter", KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK, e -> {
	        ctx.sauvegardeTemporaire();             
	        if (ctx instanceof EditorFrame f) f.setAffichage(Affiche.DOCUMENTATION);
	        ctx.afficheDocumentation();
        });
        afficheDocItem.getAccessibleContext().setAccessibleName("Documentation blindWriter");
        
        JMenuItem afficheTextItem = createMenuItem("Votre texte", KeyEvent.VK_B, InputEvent.ALT_DOWN_MASK, e -> {
        	if (ctx instanceof EditorFrame f) f.setAffichage(Affiche.TEXTE);
            ctx.AfficheTexte();
        });
        afficheTextItem.getAccessibleContext().setAccessibleName("Basculer vers votre texte");
       
        JMenuItem afficheManuelItem = createMenuItem("Manuel b.book", KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK, e -> {
            ctx.sauvegardeTemporaire();
            if (ctx instanceof EditorFrame f) f.setAffichage(Affiche.MANUEL);
            ctx.AfficheManuel();
        });
        afficheManuelItem.getAccessibleContext().setAccessibleName("Manuel b.book");
        
        ctx.addItemChangeListener(afficheDocItem);
		ctx.addItemChangeListener(afficheTextItem);
		ctx.addItemChangeListener(afficheManuelItem);

        fileDocumentation.add(afficheDocItem);
        fileDocumentation.add(afficheTextItem);
        fileDocumentation.add(afficheManuelItem);

        
        return fileDocumentation;
    }
    
    //Menu Préférence
    private static JMenu menuPreference(EditorApi ctx) {
    	JMenu filepreference = new JMenu("Préférences");
        filepreference.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        filepreference.setMnemonic(KeyEvent.VK_T);
        //filepreference.setDisplayedMnemonicIndex(1); // 'r' est le 2e caractère de "Préférences"
        // Listener déclenché à l’ouverture du menu
        filepreference.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                // Laisse Swing ouvrir le menu, puis enlève l’item armé
                SwingUtilities.invokeLater(() -> {
                    JMenu m = (JMenu) e.getSource();
                    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
                    MenuElement[] path = msm.getSelectedPath();

                    // Cas typique: [JMenuBar, JMenu(Fichier), JPopupMenu, JMenuItem(premier)]
                    if (path.length >= 4 && path[path.length - 1] instanceof JMenuItem) {
                        JMenuBar bar = (JMenuBar) m.getParent(); // parent immédiat d’un JMenu = JMenuBar
                        msm.setSelectedPath(new MenuElement[] {
                            bar, m, m.getPopupMenu()   // <-- aucun item armé
                        });
                    }
                });
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        
        // Affiche la boite non-voyant
        JMenuItem voyant = createSimpleMenuItem("Non Voyant ?", e ->{
        	 var win = ctx.getWindow();
             if (win instanceof EditorFrame frame) {
            	 new BoiteNonVoyant(frame);
             }
        });
        
        // Affichage de la version actuel
        JMenuItem versionItem = createSimpleMenuItem("Version actuelle", e -> {
      	    // Récupérez la fenêtre parente (adapter editorPane si besoin)
      	    Window owner = SwingUtilities.getWindowAncestor(ctx.getEditor());
      	    String version = writer.util.AppInfo.getAppVersion();
      	    dia.BoiteVersionNV.show(owner, "blindWriter " + version);
      	});
        
        JMenuItem majItem = createSimpleMenuItem("Mise à Jour", e -> {
      	    Toolkit.getDefaultToolkit().beep(); // feedback immédiat : début de la vérif
      	    new SwingWorker<AutoUpdater.UpdateInfo, Void>() {
      	        private final AutoUpdater updater =
      	            new AutoUpdater("https://raw.githubusercontent.com/1-pablo-rodriguez/blindWriter/main/updates.json",
      	            		writer.util.AppInfo.getAppVersion());

      	        @Override protected AutoUpdater.UpdateInfo doInBackground() throws Exception {
      	            return updater.fetchMetadata();
      	        }

      	        @Override protected void done() {
      	            try {
      	                AutoUpdater.UpdateInfo info = get();
      	                if (info == null || info.version == null) {
      	                	Toolkit.getDefaultToolkit().beep();
      	                	Window owner = SwingUtilities.getWindowAncestor(ctx.getEditor());
      	                	dia.InfoDialog.show(owner, "Mise à jour", "Impossible de vérifier les mises à jour.");
      	                    return;
      	                }
      	                // (facultatif) log de diagnostic
      	                int cmp = AutoUpdater.versionCompare(info.version, updater.getCurrentVersion());
      	                 if (cmp > 0) {
      	                    UpdateDialog dlg = new UpdateDialog(ctx.getWindow(), updater, info);
      	                    dlg.setVisible(true);
      	                } else if (cmp == 0) {
      	                    Toolkit.getDefaultToolkit().beep();
      	                    Window owner = SwingUtilities.getWindowAncestor(ctx.getEditor());
      	                    dia.InfoDialog.show(owner,"Mise à jour", "Vous avez déjà la dernière version : " + updater.getCurrentVersion());
      	                } else {
      	                    Toolkit.getDefaultToolkit().beep();
      	                    Window owner = SwingUtilities.getWindowAncestor(ctx.getEditor());
      	                    dia.InfoDialog.show(owner,"Mise à jour", "Votre version (" + updater.getCurrentVersion() 
      	                    + ") est plus récente que celle du serveur (" + info.version + ")");
      	                }

      	            } catch (Exception ex) {
      	                Toolkit.getDefaultToolkit().beep();
      	                Window owner = SwingUtilities.getWindowAncestor(ctx.getEditor());
      	                dia.InfoDialog.show(owner,  "Mise à jour", "Erreur lors de la vérification : " + ex.getMessage());
      	                ex.printStackTrace();
      	            }
      	        }
      	    }.execute();
      	});


        JMenu m = new JMenu("Affichage");
        m.setFont(new Font("Segoe UI", Font.PLAIN, 18));

        JMenuItem zi = new JMenuItem("Zoom avant");
        zi.addActionListener(e -> ctx.zoomIn());
        zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
        m.add(zi);

        JMenuItem zo = new JMenuItem("Zoom arrière");
        zo.addActionListener(e -> ctx.zoomOut());
        zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
        m.add(zo);

        JMenuItem zr = new JMenuItem("Réinitialiser le zoom");
        zr.addActionListener(e -> ctx.zoomReset());
        zr.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
        m.add(zr);
        
        
        // Ajouter des ChangeListeners pour les JMenuItem
        ctx.addItemChangeListener(m);
        ctx.addItemChangeListener(voyant);
        ctx.addItemChangeListener(majItem);
        ctx.addItemChangeListener(versionItem);
        
        filepreference.add(m);
        filepreference.add(voyant);
        filepreference.add(majItem);
        filepreference.add(versionItem);
  
        return filepreference;
    }
    
}
