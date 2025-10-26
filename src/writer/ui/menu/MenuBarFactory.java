// writer/ui/menu/MenuBarFactory.java
package writer.ui.menu;

import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

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
import dia.BoiteRenameFile;
import dia.BoiteSaveAs;
import dia.boiteMeta;
import writer.commandes;
import writer.enregistre;
import writer.ui.EditorApi;

public final class MenuBarFactory {
    private MenuBarFactory() {}

    public static JMenuBar create(EditorApi ctx) {
        JMenuBar bar = new JMenuBar();
        bar.add(menuFichier(ctx));
        bar.add(menuEdition(ctx));
        return bar;
    }

    private static JMenuItem createMenuItem(String text, int key, int mod, ActionListener action) {
        JMenuItem mi = new JMenuItem(text);
        mi.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        if (key != 0) mi.setAccelerator(KeyStroke.getKeyStroke(key, mod));
        mi.addActionListener(action);
        return mi;
    }

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
            new BoiteNewDocument();
            ctx.setModified(false);
            ctx.updateWindowTitle();
        });

        JMenuItem openItem = createMenuItem("Ouvrir", KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK, e -> {
            new dia.boiteOuvrir2();
            ctx.setModified(false);
            ctx.updateWindowTitle();
        });

        JMenuItem saveItem = createMenuItem("Enregistrer", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK, e -> {
            ctx.clearSpellHighlightsAndFocusEditor();
            new enregistre();
            ctx.setModified(false);
            ctx.updateWindowTitle();

            StringBuilder msg = new StringBuilder(128);
            msg.append("Fichier enregistré ↓")
               .append("\n• Fichier : ").append(commandes.nameFile).append(".bwr ↓")
               .append("\n• Dossier : ").append(commandes.nomDossierCourant).append(" ↓");
            ctx.showInfo("Information", msg.toString());
        });

        JMenuItem saveAsItem = createMenuItem("Enregistrer sous",
                KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, e -> {
            ctx.clearSpellHighlightsAndFocusEditor();
            new BoiteSaveAs();
            ctx.setModified(false);
            ctx.updateWindowTitle();

            StringBuilder msg = new StringBuilder(128);
            msg.append("Fichier enregistré ↓")
               .append("\n• Fichier : ").append(commandes.nameFile).append(".bwr ↓")
               .append("\n• Dossier : ").append(commandes.nomDossierCourant).append(" ↓");
            ctx.showInfo("Information", msg.toString());
        });

        JMenuItem renameItem = createMenuItem("Renommer le fichier", KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK, e -> {
            ctx.clearSpellHighlightsAndFocusEditor();
            new BoiteRenameFile();
            ctx.setModified(false);
            ctx.updateWindowTitle();
        });

        JMenuItem metaItem = createMenuItem("Meta-données", KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK, e -> {
            ctx.clearSpellHighlightsAndFocusEditor();
            new boiteMeta();
        });

        JMenuItem quitItem = createMenuItem("Quitter", KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK, e -> {
            new dia.BoiteQuitter();
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

    private static JMenu menuEdition(EditorApi ctx) {
    	JMenu editionMenu = new JMenu("Édition");
    	editionMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
    	editionMenu.setMnemonic(KeyEvent.VK_E); // Utiliser ALT+e pour ouvrir le menu
    	editionMenu.getAccessibleContext().setAccessibleName("Édition");
        // Listener déclenché à l’ouverture du menu
    	editionMenu.addMenuListener(new MenuListener() {
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
    	
    	
    	

       	
    	return editionMenu;
    }

}
