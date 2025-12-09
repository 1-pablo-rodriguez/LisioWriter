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
import java.util.List;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import dia.BoiteNewDocument;
import dia.BoiteNonVoyant;
import dia.BoiteRenameFile;
import dia.BoiteSaveAs;
import dia.boiteMeta;
import dia.navigateurT1;
import dia.ouvrirDOCX;
import dia.ouvrirHTML;
import dia.ouvrirODT;
import dia.ouvrirTxt;
import exportODF.MarkdownOdfExporter;
import exportOOXML.MarkdownOOXMLExporter;
import exportPDF.PdfExporter;
import exporterHTML.HtmlExporter;
import writer.commandes;
import writer.enregistre;
import writer.readFileBlindWriter;
import writer.readFileTXT;
import writer.internets.WikipediaSearchDialog;
import writer.internets.WiktionarySearchDialog;
import writer.model.Affiche;
import writer.ui.EditorApi;
import writer.ui.EditorFrame;
import writer.ui.editor.RemoveEmptyParagraphsAction;
import writer.update.UpdateChecker;
import writer.util.RecentFilesManager;


public final class MenuBarFactory {
	
	private static int tailleFont = 22;
	
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
        bar.add(menuInternet(ctx));
        bar.add(menuFenetres(ctx));
        bar.add(menuPreference(ctx));
        
        return bar;
    }
    

    private static JMenuItem createMenuItem(String text, int key, int mod, ActionListener action) {
        JMenuItem mi = new JMenuItem(text);
        if (key != 0) mi.setAccelerator(KeyStroke.getKeyStroke(key, mod));
        mi.addActionListener(action);
        mi.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
        return mi;
    }
    
    private static JMenuItem createSimpleMenuItem(String text, ActionListener action) {
        JMenuItem mi = new JMenuItem(text);
        mi.addActionListener(action);
        mi.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
        return mi;
    }

    
    
    // Menu Fichier
	private static JMenu menuFichier(EditorApi ctx) {
	    JMenu m = new JMenu("Fichier");
	    m.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
	    m.setMnemonic(KeyEvent.VK_F);
	    m.getAccessibleContext().setAccessibleName("Fichier");
	
	    // --- Sous-menu Fichiers récents ---
	    JMenu recentMenu = new JMenu("Fichiers récents");
	    recentMenu.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
	    recentMenu.getAccessibleContext().setAccessibleName("Fichiers récents");
	
	    // Quand on ouvre le menu Fichier : on reconstruit le sous-menu
	    m.addMenuListener(new MenuListener() {
	        @Override public void menuSelected(MenuEvent e) {
	            // Reconstruire les entrées de Fichiers récents
	            rebuildRecentFilesMenu(recentMenu, ctx);
	
	            // Garde ton comportement actuel d'armage de menu
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
	            new enregistre(frame);
	            ctx.setModified(false);
	            ctx.updateWindowTitle(); 
	        }
	    });
	
	    JMenuItem saveAsItem = createMenuItem("Enregistrer sous", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, e -> {
	        var win = ctx.getWindow();
	        if (win instanceof EditorFrame frame) {
	            new BoiteSaveAs(frame);
	        }
	    });
	
	    JMenuItem renameItem = createMenuItem("Renommer le fichier", KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK, e -> {
	        ctx.clearSpellHighlightsAndFocusEditor();
	        var win = ctx.getWindow();
	        if (win instanceof EditorFrame frame) {
	            new BoiteRenameFile(frame);
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
	    ctx.addItemChangeListener(recentMenu);
	    ctx.addItemChangeListener(createItem);
	    ctx.addItemChangeListener(openItem);
	    ctx.addItemChangeListener(saveItem);
	    ctx.addItemChangeListener(saveAsItem);
	    ctx.addItemChangeListener(renameItem);
	    ctx.addItemChangeListener(metaItem);
	    ctx.addItemChangeListener(quitItem);

	    m.add(createItem);
	    m.add(openItem);
	    m.add(recentMenu);
	    m.addSeparator();
	    m.add(renameItem);
	    m.addSeparator();
	    m.add(saveItem);
	    m.add(saveAsItem);
	    m.addSeparator();
	    m.add(metaItem);
	    m.addSeparator();
	    m.add(quitItem);
	    
	    return m;
	}

    //Menu Edition
    private static JMenu menuEdition(EditorApi ctx) {
    	JMenu editionMenu = new JMenu("Edition");
    	editionMenu.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
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
    	
    	// Annuler
    	JMenuItem undoItem = createMenuItem("Annuler", KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK, e -> {
    	    Action a = ctx.getUndoAction();
    	    if (a != null) a.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, "menu-undo"));
    	});

    	// Rétablir
    	JMenuItem redoItem = createMenuItem("Retablir", KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK, e -> {
    	    Action a = ctx.getRedoAction();
    	    if (a != null) a.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, "menu-redo"));
    	});   	   	
    	
    	// Active/ désactive l'édition
   	 	JMenuItem edition = createMenuItem("Active/désactive édition", KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Active désactive édition");
            var win = ctx.getWindow();
            if(win instanceof EditorFrame frame) {
            	new act.ToggleEditAction(frame).actionPerformed(null);
            }
        }); 
   	 	
	   	// Supprimer les paragraphes vides
	   	 JMenuItem suppParagraphesVides = createSimpleMenuItem("Supprimer les paragraphes vides", e -> {
	   	     var win = ctx.getWindow();
	   	     if (win instanceof EditorFrame frame) {
	   	         // On crée l'action et on l'exécute
	   	         new RemoveEmptyParagraphsAction(frame, ctx.getEditor()).actionPerformed(e);
	   	     }
	   	 });
  	 	
   		// Active/ désactive l'édition
	 	JMenuItem removeLink = createMenuItem("Supprime les liens", KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK, e -> {
            var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
            	   new act.removeLinks(ctx.getEditor(), frame).actionPerformed(null);
            	   System.out.println("Supprime les liens");
            }
	    });

	 	// Convertie les liens
 	 	JMenuItem convertLink = createMenuItem("Simplifier les liens", KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK, e -> {
 	 		var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
            	new act.convertAtLink(ctx.getEditor(),frame).actionPerformed(null);
            	System.out.println("Simplifier les liens");
            }
 	    });

    	// Vérification de tout le document
    	 JMenuItem checkAll = createMenuItem("Vérifier tout le document", KeyEvent.VK_F7, InputEvent.CTRL_DOWN_MASK, e -> {
             System.out.println("Vérification document");
             new act.actCheckDoc(ctx);
         }); 
    	 
    	// Vérification du paragraphe
    	 JMenuItem checkSel = createMenuItem("Vérifier la sélection / le paragraphe", KeyEvent.VK_F7, InputEvent.SHIFT_DOWN_MASK, e -> {
             System.out.println("Vérification paragraphe");
             new act.actCheckWindow(ctx);
         }); 

		// Nettoyer les soulignements, et prefix °°
		JMenuItem clear = createSimpleMenuItem("RAZ vérification",e -> { 
			writer.spell.SpellCheckLT spell = ctx.getSpell();
			if (spell != null) { 
				spell.clearHighlights(); ctx.getEditor().requestFocusInWindow(); 
				}});
		
		// Mode temps réel (ON/OFF)
		javax.swing.JCheckBoxMenuItem live = new javax.swing.JCheckBoxMenuItem("Vérification durant la frappe");
		live.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
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
		 ctx.addItemChangeListener(redoItem);
		 ctx.addItemChangeListener(edition);
		 ctx.addItemChangeListener(suppParagraphesVides);
		 ctx.addItemChangeListener(convertLink);
		 ctx.addItemChangeListener(removeLink);
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
        editionMenu.add(suppParagraphesVides);
        editionMenu.add(removeLink);
        editionMenu.add(convertLink);
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
        fileMenu.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
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
        
        JMenuItem importWriter = createMenuItem("Importer fichier Writer", KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Ouverture fichier .odt");
            var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
            	new dia.ouvrirODT(frame);
                ctx.setModified(false);
            }
        });
        
        JMenuItem importTxt = createSimpleMenuItem("Importer fichier texte", e -> {
            System.out.println("Ouverture fichier .txt");
            var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
               new ouvrirTxt(frame);
               ctx.setModified(false);
            }
        });
       
        JMenuItem importWord = createMenuItem("Importer fichier Word", KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Ouverture fichier .docx");
            var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
            	  new dia.ouvrirDOCX(frame);
            	  ctx.setModified(false);
            }
        });    
        
        JMenuItem importHtml = createSimpleMenuItem("Importer fichier HTML", e -> {
        	 var win = ctx.getWindow();
             if (win instanceof EditorFrame frame) {
            	 new dia.ouvrirHTML(frame);
             }
        	ctx.setModified(false);
        }); 
        
       
        
        ctx.addItemChangeListener(importWriter);
        ctx.addItemChangeListener(importWord);
        ctx.addItemChangeListener(importTxt);
        ctx.addItemChangeListener(importHtml);
        
 
        fileMenu.add(importWriter);
        fileMenu.add(importWord);
        fileMenu.add(importTxt);
        fileMenu.add(importHtml);
        fileMenu.addSeparator();

        
        return fileMenu;
    }

    // Menu aplliquer un style
    private static JMenu menuAppliquerStyle(EditorApi ctx) {
    	JMenu fileAppliqueStyle = new JMenu("Appliquer un style");
    	fileAppliqueStyle.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
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
    	
    	JMenuItem ctItem = createMenuItem("Corps de texte", KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK, e -> {
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
    	 
    	JMenuItem tPrinItem = createMenuItem("Titre principal", KeyEvent.VK_8, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le titre principale"); // Debugger
            new styles.titrePrincipale(ctx).appliquer();
        });
    	 
    	JMenuItem sTitreItem = createMenuItem("Sous-titre principal", KeyEvent.VK_9, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Applique le Sous titre"); // Debugger
            new styles.sousTitre(ctx).appliquer();
        });
    		
    	
    	JMenuItem sPuceItem = createMenuItem("Puce", KeyEvent.VK_7, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Puce"); // Debugger
            new styles.listeNonNumero(ctx).appliquer();
        });
    	
    	JMenuItem ecItem = new JMenuItem("Éditer corps de texte");
    	ecItem.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
    	ecItem.addActionListener(e -> {
    	    styles.BodyTextStyleDialog.open(ctx.getWindow());
    	});
    	
    	JMenuItem et1Item = new JMenuItem("Éditer Titre 1");
    	et1Item.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
    	et1Item.addActionListener(e -> {
    	    styles.Titre1TextStyleDialog.open(ctx.getWindow());
    	});
    	
    	JMenuItem et2Item = new JMenuItem("Éditer Titre 2");
    	et2Item.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
    	et2Item.addActionListener(e -> {
    	    styles.Titre2TextStyleDialog.open(ctx.getWindow());
    	});
    	
    	JMenuItem et3Item = new JMenuItem("Éditer Titre 3");
    	et3Item.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
    	et3Item.addActionListener(e -> {
    	    styles.Titre3TextStyleDialog.open(ctx.getWindow());
    	});
    	
    	JMenuItem et4Item = new JMenuItem("Éditer Titre 4");
    	et4Item.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
    	et4Item.addActionListener(e -> {
    	    styles.Titre4TextStyleDialog.open(ctx.getWindow());
    	});
    	
    	JMenuItem et5Item = new JMenuItem("Éditer Titre 5");
    	et5Item.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
    	et5Item.addActionListener(e -> {
    	    styles.Titre5TextStyleDialog.open(ctx.getWindow());
    	});
    	
    	JMenuItem etPItem = new JMenuItem("Éditer Titre principal");
    	etPItem.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
    	etPItem.addActionListener(e -> {
    	    styles.TitrePTextStyleDialog.open(ctx.getWindow());
    	});
    	
    	JMenuItem etSItem = new JMenuItem("Éditer Sous-titre principal");
    	etSItem.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
    	etSItem.addActionListener(e -> {
    	    styles.TitreSTextStyleDialog.open(ctx.getWindow());
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
    	ctx.addItemChangeListener(ecItem);
    	ctx.addItemChangeListener(etPItem);
    	ctx.addItemChangeListener(etSItem);
    	ctx.addItemChangeListener(et1Item);
    	ctx.addItemChangeListener(et2Item);
    	ctx.addItemChangeListener(et3Item);
    	ctx.addItemChangeListener(et4Item);
    	ctx.addItemChangeListener(et5Item);
    	
    	fileAppliqueStyle.add(ctItem);
    	fileAppliqueStyle.add(tPrinItem);
    	fileAppliqueStyle.add(sTitreItem);
    	fileAppliqueStyle.add(t1Item);
    	fileAppliqueStyle.add(t2Item);
    	fileAppliqueStyle.add(t3Item);
    	fileAppliqueStyle.add(t4Item);
    	fileAppliqueStyle.add(t5Item);
    	fileAppliqueStyle.add(sPuceItem);
    	fileAppliqueStyle.addSeparator();
    	fileAppliqueStyle.add(ecItem);
    	fileAppliqueStyle.add(etPItem);
    	fileAppliqueStyle.add(etSItem);
    	fileAppliqueStyle.add(et1Item);
    	fileAppliqueStyle.add(et2Item);
    	fileAppliqueStyle.add(et3Item);
    	fileAppliqueStyle.add(et4Item);
    	fileAppliqueStyle.add(et5Item);
	
    	return fileAppliqueStyle;
    }

    // Menu formatage de texte
    private static JMenu menuFormatageTexte(EditorApi ctx) {
    	JMenu fileFormatage = new JMenu("Formatage local texte");
    	fileFormatage.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
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
    	Insertion.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
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
    	
    	
    	JMenuItem sautPageItem = createMenuItem("Insérer un saut de page", KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK, e -> {
            System.out.println("Insérer un saut de page");
            new page.sautPage(ctx).appliquer();
        });
         
        JMenuItem citationBasdePageItem = createMenuItem("Insérer une note de bas de page", KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK, e -> {
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
        fileMenu.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
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
//            	writer.spell.SpellCheckLT spell = ctx.getSpell();
//            	if (spell != null) { spell.clearHighlights(); ctx.getEditor().requestFocusInWindow(); }
            	MarkdownOdfExporter.export(ctx.getEditor().getText(), new File(commandes.currentDirectory + "/" + commandes.nameFile+".odt"));
            	StringBuilder msg = new StringBuilder();
                msg.append("Exportation terminé. ↓")
                		.append("\n• Fichier : ").append(commandes.nameFile).append(".docx").append(" ↓")
                		.append("\n• Dossier : ").append(commandes.nomDossierCourant).append(" ↓")
                		.append("\n• Chemin : ").append(commandes.currentDirectory.toString());
                ctx.showInfo("Exportation", msg.toString());
            } catch (Exception e1) {
				e1.printStackTrace();
			}
        });
        
        JMenuItem exportDocItem = createSimpleMenuItem("Exporter en .DOCX (Word)", e -> {
            System.out.println("Export au format DOCX Word"); // Debugger
            //ExportODFWriter();
            try {
            	var win = ctx.getWindow();
            	if (win instanceof EditorFrame frame) {
            		boolean exportReussit = MarkdownOOXMLExporter.export(frame, new File(commandes.currentDirectory + "/" + commandes.nameFile+".docx"));
               	 	if(exportReussit) {
	               	 	File dossierParent = commandes.currentDirectory.getParentFile();
	                    String nomDossier = (dossierParent != null) ? dossierParent.getName() : null;
	                    commandes.nomDossierCourant = nomDossier;
	               	
	                    StringBuilder msg = new StringBuilder();
	                    msg.append("Exportation terminé. ↓")
	                   		.append("\n• Fichier : ").append(commandes.nameFile).append(".docx").append(" ↓")
	                   		.append("\n• Dossier : ").append(commandes.nomDossierCourant).append(" ↓")
	                   		.append("\n• Chemin : ").append(commandes.currentDirectory.toString());
	                    ctx.showInfo("Exportation", msg.toString());
               	 	}
            	}
            } catch (Exception e1) {
				e1.printStackTrace();
			}
        });
        
        JMenuItem exportPDFItem = createSimpleMenuItem("Exporter en .PDF", e -> {
            System.out.println("Export au format PDF"); // Debugger
			try {
//				writer.spell.SpellCheckLT spell = ctx.getSpell();
//				if (spell != null) {  ctx.getEditor().requestFocusInWindow(); }
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
				
				StringBuilder msg = new StringBuilder();
                msg.append("Exportation terminé. ↓")
                		.append("\n• Fichier : ").append(commandes.nameFile).append(".docx").append(" ↓")
                		.append("\n• Dossier : ").append(commandes.nomDossierCourant).append(" ↓")
                		.append("\n• Chemin : ").append(commandes.currentDirectory.toString());
                ctx.showInfo("Exportation", msg.toString());
			} catch (Exception e1) {
				e1.printStackTrace();
			}
 
        });
        
        JMenuItem exportHTMLItem = createSimpleMenuItem("Exporter en .HTML", e -> {
            System.out.println("Export au format HTML"); // Debugger
            try {
            	writer.spell.SpellCheckLT spell = ctx.getSpell();
            	if (spell != null) { spell.clearHighlights(); ctx.getEditor().requestFocusInWindow(); }
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
                StringBuilder msg = new StringBuilder();
                msg.append("Info. Exportation terminée.")
                   .append("\nFichier : ").append(written.getFileName().toString())
                   .append("\nDossier : ").append(written.getParent() == null ? "<racine>" : written.getParent().toString())
                	.append("\n• Chemin : ").append(commandes.currentDirectory.toString());
                ctx.showInfo("Exportation", msg.toString());
            } catch (Exception ex) {
                ex.printStackTrace();
                ctx.showInfo("Erreur export HTML", "L'exportation a échoué : " + ex.getMessage());
            }
        });

       
        ctx.addItemChangeListener(exportItem);
        ctx.addItemChangeListener(exportDocItem);
        ctx.addItemChangeListener(exportPDFItem);
        ctx.addItemChangeListener(exportHTMLItem);
        
        fileMenu.add(exportItem);
        fileMenu.add(exportDocItem);
        fileMenu.add(exportPDFItem);
        fileMenu.add(exportHTMLItem);
        
        return fileMenu;
    }

    // Menu Naviguer
	private static JMenu menuNaviguer(EditorApi ctx) {
        JMenu naviguerMenu = new JMenu("Naviguer");
        naviguerMenu.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
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

        JMenuItem navigateurItem = createMenuItem("Navigateur de titre", KeyEvent.VK_F6, 0, e -> {
            var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
                new navigateurT1(frame);
            }
        });

        JMenuItem rechercher = createMenuItem("Rechercher texte",
                KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK, e -> {
                new writer.openSearchDialog(ctx.getEditor());
        });

        JMenuItem remplacer = createMenuItem("Rechercher et remplacer texte",
                KeyEvent.VK_H, KeyEvent.CTRL_DOWN_MASK, e -> {
            var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
                dia.openReplaceDialog.open(frame, ctx.getEditor());
            } else {
                dia.openReplaceDialog.open(null, ctx.getEditor());
            }
        });

        // On neutralise le raccourci Swing par défaut Ctrl+H dans l'éditeur
        KeyStroke ksCtrlH = KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.CTRL_DOWN_MASK);
        ctx.getEditor().getInputMap(JComponent.WHEN_FOCUSED)
                .put(ksCtrlH, "none");


        //-------------- Marque page ----------------

        JMenuItem bmToggle = createMenuItem("Marque-page (basculer)",
                KeyEvent.VK_F2, InputEvent.CTRL_DOWN_MASK, e -> {
            var m = ctx.getBookmarks();
            if (m == null) {
                java.awt.Toolkit.getDefaultToolkit().beep();
                return;
            }
            boolean added = m.toggleHere();
            String message = (added ? "Marque-page ajouté." : "Marque-page supprimé.");
            ctx.showInfo("Information", message);
            ctx.setModified(true);
        });

        JMenuItem bmNote = createMenuItem("Marque-page note",
                KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK, e -> {
            var m = ctx.getBookmarks();
            if (m == null) {
                java.awt.Toolkit.getDefaultToolkit().beep();
                return;
            }
            m.editNoteForNearest(javax.swing.SwingUtilities.getWindowAncestor(ctx.getEditor()));
        });

        JMenuItem bmNext = createMenuItem("Marque-page suivant",
                KeyEvent.VK_F5, 0, e -> {
            var m = ctx.getBookmarks();
            if (m == null) {
                java.awt.Toolkit.getDefaultToolkit().beep();
                return;
            }
            m.goNext();
        });

        JMenuItem bmPrev = createMenuItem("Marque-page précédent",
                KeyEvent.VK_F5, InputEvent.SHIFT_DOWN_MASK, e -> {
            var m = ctx.getBookmarks();
            if (m == null) {
                java.awt.Toolkit.getDefaultToolkit().beep();
                return;
            }
            m.goPrev();
        });

        // -------- Position dans texte (F2) --------
        JMenuItem posItem = new JMenuItem(ctx.actAnnouncePosition());
        posItem.setText("Se repèrer dans le texte");
        posItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        posItem.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));

        // -------- Titre suivant (F3) --------
        JMenuItem nextHeadingItem = new JMenuItem(ctx.actGotoNextHeading());
        nextHeadingItem.setText("Titre suivant");
        nextHeadingItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0));
        nextHeadingItem.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));

        // -------- Titre précédent (Shift+F3 ?) --------
        JMenuItem prevHeadingItem = new JMenuItem(ctx.actGotoPrevHeading());
        prevHeadingItem.setText("Titre précédent");
        // (Tu as mis F4 ici, à vérifier par rapport à ta doc clavier)
        prevHeadingItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
        prevHeadingItem.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));

        // --- listeners d’activation / désactivation suivant le contexte ---
        ctx.addItemChangeListener(navigateurItem);
        ctx.addItemChangeListener(rechercher);
        ctx.addItemChangeListener(remplacer);   // <<< on ajoute le nouveau
        ctx.addItemChangeListener(bmToggle);
        ctx.addItemChangeListener(bmNote);
        ctx.addItemChangeListener(bmNext);
        ctx.addItemChangeListener(bmPrev);
        ctx.addItemChangeListener(posItem);
        ctx.addItemChangeListener(nextHeadingItem);
        ctx.addItemChangeListener(prevHeadingItem);

        // --- construction du menu ---
        naviguerMenu.add(navigateurItem);
        naviguerMenu.addSeparator();
        naviguerMenu.add(rechercher);
        naviguerMenu.add(remplacer);
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
    private static JMenu menuInternet(EditorApi ctx) {
    	JMenu fileInternet = new JMenu("Internets");
    	fileInternet.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
    	fileInternet.setMnemonic(KeyEvent.VK_S); 
    	fileInternet.getAccessibleContext().setAccessibleName("Internet");
    		
    	// Listener déclenché à l’ouverture du menu
    	fileInternet.addMenuListener(new MenuListener() {
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
    	
    	 JMenuItem importWikipedia = createMenuItem("Recherche Wikipedia", KeyEvent.VK_F8, 0,e -> {
        	 var win = ctx.getWindow();
             if (win instanceof EditorFrame frame) {
            	 WikipediaSearchDialog.open(frame, url -> {
            		    new writer.internets.HtmlBrowserDialog_WIKIPEDIA(frame, frame.getEditor(), url);
            		});
             }
        });
        
        JMenuItem importWiktionaire = createMenuItem("Recherche Wiktionaire", 0, 0,e -> {
       	 var win = ctx.getWindow();
            if (win instanceof EditorFrame frame) {
           		WiktionarySearchDialog.open(frame, url -> {
           		    // callback si tu veux logguer, adapter le comportement, etc.
           		    System.out.println("URL Wiktionnaire sélectionnée : " + url);
           		});
            }
       });

        
        ctx.addItemChangeListener(importWikipedia);
        ctx.addItemChangeListener(importWiktionaire);
    	
        fileInternet.add(importWikipedia);
        fileInternet.add(importWiktionaire);
        
    	return fileInternet;
    }
    
    // Menu Documentation
    private static JMenu menuFenetres(EditorApi ctx) {
    	JMenu fileFenetre = new JMenu("Fenêtres");
    	fileFenetre.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
    	fileFenetre.setMnemonic('D'); // Utiliser ALT+F pour ouvrir le menu
        // Listener déclenché à l’ouverture du menu
    	fileFenetre.addMenuListener(new MenuListener() {
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
        
        JMenuItem afficheDocItem = createMenuItem("Fenêtre Documentation", KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK, e -> {        
	        if (ctx instanceof EditorFrame f) {
	        	if(f.getAffichage() == Affiche.TEXTE1) ctx.sauvegardeTemporaireTexte1(); 
	        	if(f.getAffichage() == Affiche.TEXTE2) ctx.sauvegardeTemporaireTexte2();
	        	if(f.getAffichage() != Affiche.DOCUMENTATION){
		        	f.setAffichage(Affiche.DOCUMENTATION);
		        	ctx.afficheDocumentation();
	        	}
	        }
        });
          
        JMenuItem afficheTextItem = createMenuItem("Fenêtre 1 - Texte", KeyEvent.VK_B, InputEvent.ALT_DOWN_MASK, e -> {
        	if (ctx instanceof EditorFrame f) {
	        	if(f.getAffichage() == Affiche.TEXTE2) ctx.sauvegardeTemporaireTexte2();
        		if(f.getAffichage()!=Affiche.TEXTE1){
            		f.setAffichage(Affiche.TEXTE1);
            		ctx.AfficheTexte();
        		}
        	}
        });
        
        JMenuItem afficheManuelItem = createMenuItem("Fenêtre 2 - Texte", KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK, e -> {
            if (ctx instanceof EditorFrame f) {
            	if(f.getAffichage() == Affiche.TEXTE1) ctx.sauvegardeTemporaireTexte1(); 
            	if(f.getAffichage() != Affiche.TEXTE2){
                	f.setAffichage(Affiche.TEXTE2);
                	ctx.AfficheTexte();
            	}
            }  
        });
         
        ctx.addItemChangeListener(afficheDocItem);
		ctx.addItemChangeListener(afficheTextItem);
		ctx.addItemChangeListener(afficheManuelItem);

		fileFenetre.add(afficheDocItem);
		fileFenetre.add(afficheTextItem);
		fileFenetre.add(afficheManuelItem);

        
        return fileFenetre;
    }
    
    //Menu Préférence
    private static JMenu menuPreference(EditorApi ctx) {
    	JMenu filepreference = new JMenu("Paramètres");
        filepreference.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
        filepreference.setMnemonic(KeyEvent.VK_T);
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
      	    dia.BoiteVersionNV.show(owner, "LisioWriter " + version);
      	});
        
        JMenuItem majItem = createMenuItem("Mise à jour…", KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK,e -> {
            // feedback immédiat facultatif
            java.awt.Toolkit.getDefaultToolkit().beep();
            UpdateChecker.checkNow(ctx);
        });

        JMenu m = new JMenu("Affichage");
        m.setFont(new Font("Segoe UI", Font.PLAIN, 18));

        JMenuItem zi = new JMenuItem("Zoom avant");
        zi.addActionListener(e -> ctx.zoomIn());
        zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK));
        m.add(zi);

        JMenuItem zo = new JMenuItem("Zoom arrière");
        zo.addActionListener(e -> ctx.zoomOut());
        zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
        m.add(zo);

        JMenuItem zr = new JMenuItem("Réinitialiser le zoom");
        zr.addActionListener(e -> ctx.zoomReset());
        zr.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
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
    
    
    /** Reconstruit le menu "Fichiers récents" à partir de RecentFilesManager. */
    private static void rebuildRecentFilesMenu(JMenu recentMenu, EditorApi ctx) {
        recentMenu.removeAll();

        List<File> recents = RecentFilesManager.getRecentFiles();
        if (recents.isEmpty()) {
            JMenuItem empty = new JMenuItem("(Aucun fichier récent)");
            empty.setEnabled(false);
            empty.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
            recentMenu.add(empty);
            return;
        }

        int index = 1;
        for (File f : recents) {
            final File file = f;  // pour la lambda

            // ① Label numéroté : "1- nomDuFichier.ext"
            String label = index + "- " + file.getName();

            JMenuItem item = new JMenuItem(label);
            item.setFont(new Font("Segoe UI", Font.PLAIN, tailleFont));
            item.setToolTipText(file.getAbsolutePath());

            item.addActionListener(ev -> {
                // ouverture du fichier récent
                if (!file.exists()) {
                    Toolkit.getDefaultToolkit().beep();
                    ctx.showInfo("Fichier introuvable",
                            "Ce fichier n'existe plus :\n" + file.getAbsolutePath());
                    RecentFilesManager.remove(file);
                    rebuildRecentFilesMenu(recentMenu, ctx);
                    return;
                }

                var win = ctx.getWindow();
                if (win instanceof EditorFrame frame) {
                    try {
                        String name = file.getName().toLowerCase(java.util.Locale.ROOT);

                        if (name.endsWith(".bwr")) {
                            new readFileBlindWriter(file, frame);
                        } else if (name.endsWith(".txt")) {
                            new readFileTXT(file, frame);
                        } else if (name.endsWith(".odt")) {
                            new ouvrirODT(frame, true).readFile(file);
                        } else if (name.endsWith(".docx")) {
                            new ouvrirDOCX(frame, true).readFile(file);
                        } else if (name.endsWith(".html") || name.endsWith(".htm")) {
                            new ouvrirHTML(frame, true).readFile(file);
                        } else {
                            // Extension inconnue
                            Toolkit.getDefaultToolkit().beep();
                            ctx.showInfo("Extension non prise en charge",
                                    "Impossible d’ouvrir ce fichier récent :\n"
                                  + file.getAbsolutePath()
                                  + "\n\nExtension non reconnue.");
                            return;
                        }

                        // Si on arrive ici, l’ouverture s’est bien passée
                        ctx.setModified(false);
                        ctx.updateWindowTitle();

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        Toolkit.getDefaultToolkit().beep();
                        ctx.showInfo("Erreur d’ouverture",
                                "Une erreur est survenue en ouvrant le fichier :\n"
                              + file.getAbsolutePath()
                              + "\n\n" + ex.getMessage());
                    }
                }
            });

            recentMenu.add(item);
            index++;
            if (index > 40) break;
        }
    }


    
}
