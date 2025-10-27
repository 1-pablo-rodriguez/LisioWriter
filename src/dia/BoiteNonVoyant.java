package dia;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import writer.commandes;
import writer.ui.EditorFrame;

/** Boîte de choix pour activer/désactiver le mode non-voyant. */
@SuppressWarnings("serial")
public class BoiteNonVoyant extends JDialog {

    private final JButton btnOui = new JButton("OUI");
    private final JButton btnNon = new JButton("NON");
    private final JLabel question = new JLabel("Êtes-vous non voyant ?");
    EditorFrame parent ;


    public BoiteNonVoyant(EditorFrame parent) {
    	this.parent = parent;
    	
       
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Texte lisible par lecteurs d'écran/braille
        question.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        question.getAccessibleContext().setAccessibleName("Question : Êtes-vous non voyant ?");
        question.getAccessibleContext().setAccessibleDescription(
            "Choisissez OUI pour activer les annonces et aides non voyants. Choisissez NON pour les désactiver.");

        // Bouton OUI
        btnOui.setMnemonic(0); // Alt+O
        btnOui.getAccessibleContext().setAccessibleName("Bouton OUI, activer mode non voyant");
        btnOui.addActionListener((ActionEvent e) -> {
            commandes.nonvoyant = true;
            if (parent != null) {
                SwingUtilities.invokeLater(() -> {
                    parent.requestFocus();
                    parent.getEditor().requestFocusInWindow();
                });
            }
            dispose();
        });

        // Bouton NON
        btnNon.setMnemonic(0); // Alt+N
        btnNon.getAccessibleContext().setAccessibleName("Bouton NON, désactiver mode non voyant");
        btnNon.addActionListener((ActionEvent e) -> {
            commandes.nonvoyant = false;
            SwingUtilities.invokeLater(() -> {
                parent.requestFocus();
                parent.getEditor().requestFocusInWindow();
            });
            dispose();
        });

        // Disposition
        JPanel south = new JPanel();
        south.add(btnOui);
        south.add(btnNon);
        getContentPane().add(question, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);

        // Accessibilité clavier
        //getRootPane().setDefaultButton(btnOui); // Enter => OUI (choix sûr)
        // Échap ferme sans changer la valeur courante (par défaut true)
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { dispose(); }
        });
        
        
        btnNon.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                getRootPane().setDefaultButton(btnNon);
            }
        });
        btnOui.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                getRootPane().setDefaultButton(btnOui);
            }
        });

        pack();
        setLocationRelativeTo(parent);
        
        setVisible(true);
    }


}
