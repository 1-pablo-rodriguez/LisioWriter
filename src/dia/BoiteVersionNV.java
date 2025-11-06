package dia;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
/**
 * Boite de dialogue version
 * @author pabr6
 *
 */
@SuppressWarnings("serial")
public final class BoiteVersionNV extends JDialog {

    private final JLabel status = new JLabel(" ");          // barre braille / SR
    private final JTextField versionField = new JTextField(32);
    private final Component focusAfterClose;
    
    private BoiteVersionNV(Window owner, String versionText, Component focusAfterClose) {
        super(owner, "À propos / Version", ModalityType.APPLICATION_MODAL);
        this.focusAfterClose = focusAfterClose;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // ——— Lisibilité (polices + contraste)
        Font big = getFont().deriveFont(Font.PLAIN, 18f);
        UIManager.put("Button.font", big);
        UIManager.put("Label.font", big);
        UIManager.put("TextField.font", big);

        JPanel root = new JPanel(new BorderLayout(12,12));
        root.setBorder(new EmptyBorder(16,16,16,16));
        setContentPane(root);

        // En-tête
        JLabel title = new JLabel("LisioWriter — Version");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        root.add(title, BorderLayout.NORTH);

        // Centre : champ non éditable pour permettre la sélection/copier
        versionField.setText(versionText);
        versionField.setEditable(false);
        versionField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180,180,180)),
                new EmptyBorder(8,8,8,8)));
        versionField.setCaretPosition(0);
        versionField.setFocusable(true);
        root.add(versionField, BorderLayout.CENTER);

        // Barre braille / statut (grosse police, texte court, sans fioritures)
        status.setBorder(new EmptyBorder(10,0,0,0));
        status.setFont(big);
        root.add(status, BorderLayout.SOUTH);

        // Boutons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        root.add(buttons, BorderLayout.PAGE_END);
        // Actions
        JButton copy = new JButton("Copier");
        JButton ok   = new JButton("OK");
        buttons.add(copy);
        buttons.add(ok);
        
        ok.addActionListener(e -> closeAndRefocus());
        copy.addActionListener(e -> copyToClipboard(versionText));

        // Annonce immédiate
        announce("Version : " + versionText, /*takeFocus*/ true);

        // Raccourcis clavier — Échap ferme + refocus
        JRootPane rp = getRootPane();
        rp.registerKeyboardAction(e -> closeAndRefocus(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        rp.setDefaultButton(ok);

        // Ctrl+C
        rp.registerKeyboardAction(e -> copyToClipboard(versionText),
                KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Taille/placement
        pack();
        setMinimumSize(new Dimension(Math.max(420, getWidth()), getHeight()+20));
        setLocationRelativeTo(owner);

        
        // En plus : si la fenêtre est fermée par la croix/ALT+F4, refocus aussi
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { requestFocusBack(); }
        });
        
        
        // Focus initial sur le champ (lisible + copiable)
        SwingUtilities.invokeLater(() -> {
            versionField.requestFocusInWindow();
            versionField.selectAll();
        });
    }

    private void announce(String text, boolean takeFocus) {
        // court et sans préfixe pour maximiser les cellules braille utiles
        status.setText(text);
        status.getAccessibleContext().setAccessibleName(text);
        if (takeFocus) {
            // Donner brièvement le focus à la barre pour forcer la lecture
            status.requestFocusInWindow();
            // Revenir au champ version après une courte impulsion
            Timer t = new Timer(120, e -> versionField.requestFocusInWindow());
            t.setRepeats(false);
            t.start();
        }
        Toolkit.getDefaultToolkit().beep(); // feedback discret
    }

    private static void copyToClipboard(String s) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(s), null);
        } catch (Exception ignored) {}
    }


    
    // 👉 ferme et recentre proprement
    private void closeAndRefocus() {
        dispose();            // ferme la JDialog
        requestFocusBack();   // puis rend le focus
    }

    private void requestFocusBack() {
        if (focusAfterClose != null) {
            SwingUtilities.invokeLater(() -> {
                // si le composant n’est plus displayable, remonte vers sa fenêtre
                if (focusAfterClose.isDisplayable()) {
                    focusAfterClose.requestFocusInWindow();
                } else {
                    Window ow = getOwner();
                    if (ow != null) ow.requestFocus();
                }
            });
        }
    }

    /** Surcharges utilitaires */
    public static void show(Window owner, String versionText, Component focusAfterClose) {
        new BoiteVersionNV(owner, versionText, focusAfterClose).setVisible(true);
    }

    // rétro-compat : si tu n’envoies pas de cible, pas de refocus spécifique
    public static void show(Window owner, String versionText) {
        show(owner, versionText, null);
    }
    
}
    

