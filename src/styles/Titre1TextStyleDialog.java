package styles;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
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
 * Boîte de dialogue pour modifier les paramètres du style "Titre 1"
 * (node "Titre1" dans commandes.nodeblindWriter).
 *
 * Adaptée non-voyants :
 * - Navigation clavier (Tab / Shift+Tab)
 * - Entrée = Enregistrer (bouton par défaut)
 * - Échap = Annuler
 */
public final class Titre1TextStyleDialog {

    private Titre1TextStyleDialog() {
        // utilitaire static
    }

    @SuppressWarnings("serial")
    public static void open(Window parent) {

        final JDialog dlg = new JDialog(parent, "Style : Titre 1",
                JDialog.ModalityType.APPLICATION_MODAL);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        Font labelFont = new Font("Segoe UI", Font.PLAIN, 18);
        Font fieldFont = new Font("Segoe UI", Font.PLAIN, 18);

        // ===========================
        //   Lecture valeurs actuelles
        // ===========================

        var t1 = commandes.nodeblindWriter.retourneFirstEnfant("Titre1");

        String police       = "Arial";
        String size         = "18pt";
        String alignement   = "start";
        String interligne   = "150%";
        String espHaut      = "0.3cm";
        String espBas       = "0.5cm";
        boolean keepWithNext = true; // par défaut "always"

        if (t1 != null) {
            String v;

            v = t1.getAttributs("police");
            if (v != null && !v.isBlank()) police = v.trim();

            v = t1.getAttributs("size");
            if (v != null && !v.isBlank()) size = v.trim();

            v = t1.getAttributs("alignement");
            if (v != null && !v.isBlank()) alignement = v.trim();

            v = t1.getAttributs("interligne");
            if (v != null && !v.isBlank()) interligne = v.trim();

            v = t1.getAttributs("espacement_au_dessus");
            if (v != null && !v.isBlank()) espHaut = v.trim();

            v = t1.getAttributs("espacement_en_dessous");
            if (v != null && !v.isBlank()) espBas = v.trim();

            v = t1.getAttributs("keep-with-next");
            if (v != null && !v.isBlank()) {
                keepWithNext = "always".equalsIgnoreCase(v.trim());
            }
        }

        // =======================
        //   Combo polices système
        // =======================

        String[] fontNames = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();

        JComboBox<String> comboPolice = new JComboBox<>(fontNames);
        comboPolice.setFont(fieldFont);

        boolean foundFont = false;
        for (int i = 0; i < fontNames.length; i++) {
            if (fontNames[i].equalsIgnoreCase(police)) {
                comboPolice.setSelectedIndex(i);
                foundFont = true;
                break;
            }
        }
        if (!foundFont && police != null && !police.isBlank()) {
            comboPolice.insertItemAt(police, 0);
            comboPolice.setSelectedIndex(0);
        }

        // =======================
        //   Combo alignement
        // =======================

        String[] alignments = { "start", "center", "justify", "right" };

        JComboBox<String> comboAlign = new JComboBox<>(alignments);
        comboAlign.setFont(fieldFont);

        boolean foundAlign = false;
        for (int i = 0; i < alignments.length; i++) {
            if (alignments[i].equalsIgnoreCase(alignement)) {
                comboAlign.setSelectedIndex(i);
                foundAlign = true;
                break;
            }
        }
        if (!foundAlign && alignement != null && !alignement.isBlank()) {
            comboAlign.insertItemAt(alignement, 0);
            comboAlign.setSelectedIndex(0);
        }

        // =======================
        //   Champs restants
        // =======================

        JTextField txtSize    = new JTextField(size, 20);
        JTextField txtInter   = new JTextField(interligne, 20);
        JTextField txtEspHaut = new JTextField(espHaut, 20);
        JTextField txtEspBas  = new JTextField(espBas, 20);

        txtSize.setFont(fieldFont);
        txtInter.setFont(fieldFont);
        txtEspHaut.setFont(fieldFont);
        txtEspBas.setFont(fieldFont);

        JCheckBox chkKeepNext = new JCheckBox("Garder avec le paragraphe suivant");
        chkKeepNext.setFont(labelFont);
        chkKeepNext.setSelected(keepWithNext);

        // =======================
        //   Labels
        // =======================

        JLabel lblPolice  = new JLabel("Police :");
        JLabel lblSize    = new JLabel("Taille (ex : 18pt) :");
        JLabel lblAlign   = new JLabel("Alignement :");
        JLabel lblInter   = new JLabel("Interligne (ex : 150%) :");
        JLabel lblEspHaut = new JLabel("Espacement au-dessus :");
        JLabel lblEspBas  = new JLabel("Espacement en dessous :");

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

        // =======================
        //   Layout principal
        // =======================

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 12, 6, 12);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        // ligne 1 : police
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblPolice, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(comboPolice, gbc);
        row++;

        // ligne 2 : taille
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblSize, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtSize, gbc);
        row++;

        // ligne 3 : alignement
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblAlign, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(comboAlign, gbc);
        row++;

        // ligne 4 : interligne
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblInter, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtInter, gbc);
        row++;

        // ligne 5 : espacement au-dessus
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblEspHaut, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtEspHaut, gbc);
        row++;

        // ligne 6 : espacement en dessous
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblEspBas, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtEspBas, gbc);
        row++;

        // ligne 7 : keep-with-next (case à cocher)
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        center.add(chkKeepNext, gbc);
        row++;
        gbc.gridwidth = 1;

        // =======================
        //   Boutons
        // =======================

        JButton btnSave = new JButton("Enregistrer");
        JButton btnCancel = new JButton("Annuler");

        btnSave.setFont(labelFont);
        btnCancel.setFont(labelFont);

        // Action Enregistrer
        btnSave.addActionListener(e -> {
            var node = commandes.nodeblindWriter.retourneFirstEnfant("Titre1");
            if (node != null) {
                String p  = (String) comboPolice.getSelectedItem();
                String al = (String) comboAlign.getSelectedItem();
                String sz = txtSize.getText().trim();
                String in = txtInter.getText().trim();
                String eh = txtEspHaut.getText().trim();
                String eb = txtEspBas.getText().trim();

                if (p != null && !p.isEmpty()) {
                    node.getAttributs().put("police", p.trim());
                }
                if (al != null && !al.isEmpty()) {
                    node.getAttributs().put("alignement", al.trim());
                }
                if (!sz.isEmpty()) {
                    node.getAttributs().put("size", sz);
                }
                if (!in.isEmpty()) {
                    node.getAttributs().put("interligne", in);
                }
                if (!eh.isEmpty()) {
                    node.getAttributs().put("espacement_au_dessus", eh);
                }
                if (!eb.isEmpty()) {
                    node.getAttributs().put("espacement_en_dessous", eb);
                }

                if (chkKeepNext.isSelected()) {
                    node.getAttributs().put("keep-with-next", "always");
                } else {
                    // on supprime l’attribut si présent
                    node.getAttributs().remove("keep-with-next");
                }
            }
            dlg.dispose();
        });

        // Action Annuler
        btnCancel.addActionListener(e -> dlg.dispose());

        JPanel south = new JPanel();
        south.add(btnSave);
        south.add(btnCancel);

        // =======================
        //   Échap = Annuler
        // =======================

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

        // =======================
        //   Assemblage final
        // =======================

        dlg.getContentPane().setLayout(new BorderLayout(10, 10));
        dlg.getContentPane().add(center, BorderLayout.CENTER);
        dlg.getContentPane().add(south, BorderLayout.SOUTH);

        dlg.pack();
        dlg.setLocationRelativeTo(parent);

        // Focus initial sur la combo police
        SwingUtilities.invokeLater(() -> comboPolice.requestFocusInWindow());

        dlg.setVisible(true);
    }
}