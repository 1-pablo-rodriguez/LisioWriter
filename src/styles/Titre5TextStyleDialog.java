package styles;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import writer.commandes;

/**
 * Boîte de dialogue pour modifier les paramètres du style "Titre 5"
 * (node "Titre5" dans commandes.nodeblindWriter).
 *
 * Pensée pour un non-voyant :
 * - navigation au clavier (Tab / Shift+Tab),
 * - bouton "Enregistrer" activable avec Entrée,
 * - Échap pour annuler,
 * - case à cocher activable avec Espace.
 */
public final class Titre5TextStyleDialog {

    private Titre5TextStyleDialog() {
        // utilitaire static
    }

    /**
     * Ouvre la fenêtre de modification du style "Titre 5".
     * @param parent fenêtre parente (peut être null)
     */
    @SuppressWarnings("serial")
    public static void open(Window parent) {
        // Création du JDialog modal
        final JDialog dlg = new JDialog(parent, "Style : Titre 5", JDialog.ModalityType.APPLICATION_MODAL);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Police un peu plus grande pour le confort
        Font labelFont = new Font("Segoe UI", Font.PLAIN, 18);
        Font fieldFont = new Font("Segoe UI", Font.PLAIN, 18);

        // ========== Récupération des valeurs actuelles ==========

        var t5 = commandes.nodeblindWriter.retourneFirstEnfant("Titre5");

        String police       = "Arial";
        String size         = "18pt";
        String alignement   = "start";
        String interligne   = "150%";
        String espHaut      = "0.3cm";
        String espBas       = "0.5cm";
        boolean keepNext    = true; // garder avec le paragraphe suivant par défaut

        if (t5 != null) {
            String v;

            v = t5.getAttributs("police");
            if (v != null && !v.isBlank()) police = v.trim();

            v = t5.getAttributs("size");
            if (v != null && !v.isBlank()) size = v.trim();

            v = t5.getAttributs("alignement");
            if (v != null && !v.isBlank()) alignement = v.trim();

            v = t5.getAttributs("interligne");
            if (v != null && !v.isBlank()) interligne = v.trim();

            v = t5.getAttributs("espacement_au_dessus");
            if (v != null && !v.isBlank()) espHaut = v.trim();

            v = t5.getAttributs("espacement_en_dessous");
            if (v != null && !v.isBlank()) espBas = v.trim();

            v = t5.getAttributs("keep-with-next");
            if (v != null && !v.isBlank()) {
                keepNext = v.trim().equalsIgnoreCase("always");
            }
        }

        // ========== Composants de saisie ==========

        // --- Combo des polices système ---
        String[] fontNames = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();

        JComboBox<String> comboPolice = new JComboBox<>(fontNames);
        comboPolice.setFont(fieldFont);
        comboPolice.setEditable(false);

        boolean foundPolice = false;
        for (int i = 0; i < fontNames.length; i++) {
            if (fontNames[i].equalsIgnoreCase(police)) {
                comboPolice.setSelectedIndex(i);
                foundPolice = true;
                break;
            }
        }
        if (!foundPolice && police != null && !police.isBlank()) {
            comboPolice.insertItemAt(police, 0);
            comboPolice.setSelectedIndex(0);
        }

        // --- Combo alignement ---
        String[] alignChoices = { "start", "left", "center", "right", "justify" };
        JComboBox<String> comboAlign = new JComboBox<>(alignChoices);
        comboAlign.setFont(fieldFont);
        comboAlign.setEditable(false);

        boolean foundAlign = false;
        for (int i = 0; i < alignChoices.length; i++) {
            if (alignChoices[i].equalsIgnoreCase(alignement)) {
                comboAlign.setSelectedIndex(i);
                foundAlign = true;
                break;
            }
        }
        if (!foundAlign) {
            comboAlign.insertItemAt(alignement, 0);
            comboAlign.setSelectedIndex(0);
        }

        JTextField txtSize     = new JTextField(size, 20);
        JTextField txtInter    = new JTextField(interligne, 20);
        JTextField txtEspHaut  = new JTextField(espHaut, 20);
        JTextField txtEspBas   = new JTextField(espBas, 20);
        JCheckBox  chkKeepNext = new JCheckBox("Garder avec le paragraphe suivant");
        chkKeepNext.setSelected(keepNext);

        txtSize.setFont(fieldFont);
        txtInter.setFont(fieldFont);
        txtEspHaut.setFont(fieldFont);
        txtEspBas.setFont(fieldFont);
        chkKeepNext.setFont(labelFont);

        JLabel lblPolice  = new JLabel("Police :");
        JLabel lblSize    = new JLabel("Taille (ex : 18pt) :");
        JLabel lblAlign   = new JLabel("Alignement :");
        JLabel lblInter   = new JLabel("Interligne (ex : 150%) :");
        JLabel lblEspHaut = new JLabel("Espacement au-dessus (ex : 0.3cm) :");
        JLabel lblEspBas  = new JLabel("Espacement en dessous (ex : 0.5cm) :");

        lblPolice.setFont(labelFont);
        lblSize.setFont(labelFont);
        lblAlign.setFont(labelFont);
        lblInter.setFont(labelFont);
        lblEspHaut.setFont(labelFont);
        lblEspBas.setFont(labelFont);

        lblPolice.setLabelFor(comboPolice);
        lblSize.setLabelFor(txtSize);
        lblAlign.setLabelFor(comboAlign);
        lblInter.setLabelFor(txtInter);
        lblEspHaut.setLabelFor(txtEspHaut);
        lblEspBas.setLabelFor(txtEspBas);

        // ========== Layout central ==========

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        // Ligne 1 : police
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblPolice, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(comboPolice, gbc);
        row++;

        // Ligne 2 : taille
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblSize, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtSize, gbc);
        row++;

        // Ligne 3 : alignement
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblAlign, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(comboAlign, gbc);
        row++;

        // Ligne 4 : interligne
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblInter, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtInter, gbc);
        row++;

        // Ligne 5 : espacement au-dessus
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblEspHaut, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtEspHaut, gbc);
        row++;

        // Ligne 6 : espacement en dessous
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblEspBas, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtEspBas, gbc);
        row++;

        // Ligne 7 : garder avec le paragraphe suivant
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weightx = 1.0;
        center.add(chkKeepNext, gbc);
        gbc.gridwidth = 1;
        row++;

        // ========== Boutons ==========

        JButton btnSave = new JButton("Enregistrer");
        JButton btnCancel = new JButton("Annuler");

        btnSave.setFont(labelFont);
        btnCancel.setFont(labelFont);

        // Action Enregistrer
        btnSave.addActionListener(e -> {
            var node = commandes.nodeblindWriter.retourneFirstEnfant("Titre5");
            if (node != null) {
                String p  = (String) comboPolice.getSelectedItem();
                String sz = txtSize.getText().trim();
                String al = (String) comboAlign.getSelectedItem();
                String in = txtInter.getText().trim();
                String eh = txtEspHaut.getText().trim();
                String eb = txtEspBas.getText().trim();

                if (p != null && !p.isEmpty())  node.getAttributs().put("police", p.trim());
                if (!sz.isEmpty()) node.getAttributs().put("size", sz);
                if (al != null && !al.isEmpty()) node.getAttributs().put("alignement", al.trim());
                if (!in.isEmpty()) node.getAttributs().put("interligne", in);
                if (!eh.isEmpty()) node.getAttributs().put("espacement_au_dessus", eh);
                if (!eb.isEmpty()) node.getAttributs().put("espacement_en_dessous", eb);

                node.getAttributs().put(
                        "keep-with-next",
                        chkKeepNext.isSelected() ? "always" : ""
                );
            }
            dlg.dispose();
        });

        // Action Annuler
        btnCancel.addActionListener(e -> dlg.dispose());

        JPanel south = new JPanel();
        south.add(btnSave);
        south.add(btnCancel);

        // ========== Raccourcis clavier (Échap) ==========

        dlg.getRootPane().registerKeyboardAction(
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dlg.dispose();
                }
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Entrée = Enregistrer
        dlg.getRootPane().setDefaultButton(btnSave);

        // ========== Assemblage et affichage ==========

        dlg.getContentPane().setLayout(new BorderLayout(10, 10));
        dlg.getContentPane().add(center, BorderLayout.CENTER);
        dlg.getContentPane().add(south, BorderLayout.SOUTH);

        dlg.pack();
        dlg.setLocationRelativeTo(parent);

        // Focus initial sur la combo des polices
        SwingUtilities.invokeLater(() -> comboPolice.requestFocusInWindow());

        dlg.setVisible(true);
    }
}