package dia;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.*;

import writer.blindWriter;
import writer.commandes;

/** Boîte de choix pour activer/désactiver le mode non-voyant. */
@SuppressWarnings("serial")
public final class BoiteNonVoyant extends JDialog {

    private final JButton btnOui = new JButton("OUI");
    private final JButton btnNon = new JButton("NON");
    private final JLabel question = new JLabel("Êtes-vous non voyant ?");

    /** Affiche la boîte (modale) et met à jour commandes.nonvoyant. */
    public static void show(Frame parent) {
        BoiteNonVoyant d = new BoiteNonVoyant(parent);
        d.setVisible(true);
        // À la fermeture sans choix, on NE change rien => reste true par défaut
    }

    private BoiteNonVoyant(Frame parent) {
        super(parent, "Accessibilité", Dialog.ModalityType.APPLICATION_MODAL);
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
            dispose();
            blindWriter.getInstance();
        });

        // Bouton NON
        btnNon.setMnemonic(0); // Alt+N
        btnNon.getAccessibleContext().setAccessibleName("Bouton NON, désactiver mode non voyant");
        btnNon.addActionListener((ActionEvent e) -> {
            commandes.nonvoyant = false;
            dispose();
            blindWriter.getInstance();
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
    }


}
