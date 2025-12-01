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
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import writer.commandes;

/**
 * Boîte de dialogue pour modifier les paramètres du style "Corps de texte"
 * (node "bodyText" dans commandes.nodeblindWriter).
 *
 * Adaptée non-voyants :
 * - Navigation Tab / Shift+Tab
 * - Entrée = Enregistrer (bouton par défaut)
 * - Échap = Annuler
 */
public final class BodyTextStyleDialog {

    private BodyTextStyleDialog() {}

    @SuppressWarnings("serial")
    public static void open(Window parent) {

        final JDialog dlg = new JDialog(parent, "Style : Corps de texte",
                JDialog.ModalityType.APPLICATION_MODAL);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Police d’interface confortable
        Font labelFont = new Font("Segoe UI", Font.PLAIN, 18);
        Font fieldFont = new Font("Segoe UI", Font.PLAIN, 18);

        // ===========================
        //   Lecture valeurs actuelles
        // ===========================

        var bodyText = commandes.nodeblindWriter.retourneFirstEnfant("bodyText");

        String police       = "Arial";
        String size         = "14pt";
        String alignement   = "justify";
        String interligne   = "115%";
        String espHaut      = "0.2cm";
        String espBas       = "0.2cm";

        if (bodyText != null) {

            String v;

            v = bodyText.getAttributs("police");
            if (v != null && !v.isBlank()) police = v.trim();

            v = bodyText.getAttributs("size");
            if (v != null && !v.isBlank()) size = v.trim();

            v = bodyText.getAttributs("alignement");
            if (v != null && !v.isBlank()) alignement = v.trim();

            v = bodyText.getAttributs("interligne");
            if (v != null && !v.isBlank()) interligne = v.trim();

            v = bodyText.getAttributs("espacement_au_dessus");
            if (v != null && !v.isBlank()) espHaut = v.trim();

            v = bodyText.getAttributs("espacement_en_dessous");
            if (v != null && !v.isBlank()) espBas = v.trim();
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
        if (!foundFont) {
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
        if (!foundAlign) {
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

        // =======================
        //   Labels
        // =======================

        JLabel lblPolice  = new JLabel("Police :");
        JLabel lblSize    = new JLabel("Taille (ex : 14pt) :");
        JLabel lblAlign   = new JLabel("Alignement :");
        JLabel lblInter   = new JLabel("Interligne (ex : 115%) :");
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

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblPolice, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(comboPolice, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblSize, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtSize, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblAlign, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(comboAlign, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblInter, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtInter, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblEspHaut, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtEspHaut, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        center.add(lblEspBas, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        center.add(txtEspBas, gbc); row++;

        // =======================
        //   Boutons
        // =======================

        JButton btnSave = new JButton("Enregistrer");
        JButton btnCancel = new JButton("Annuler");

        btnSave.setFont(labelFont);
        btnCancel.setFont(labelFont);

        // Sauvegarde
        btnSave.addActionListener(e -> {
            var node = commandes.nodeblindWriter.retourneFirstEnfant("bodyText");
            if (node != null) {

                String p  = ((String) comboPolice.getSelectedItem()).trim();
                String al = ((String) comboAlign.getSelectedItem()).trim();
                String sz = txtSize.getText().trim();
                String in = txtInter.getText().trim();
                String eh = txtEspHaut.getText().trim();
                String eb = txtEspBas.getText().trim();

                node.getAttributs().put("police", p);
                node.getAttributs().put("alignement", al);

                if (!sz.isEmpty()) node.getAttributs().put("size", sz);
                if (!in.isEmpty()) node.getAttributs().put("interligne", in);
                if (!eh.isEmpty()) node.getAttributs().put("espacement_au_dessus", eh);
                if (!eb.isEmpty()) node.getAttributs().put("espacement_en_dessous", eb);
            }
            dlg.dispose();
        });

        btnCancel.addActionListener(e -> dlg.dispose());

        JPanel south = new JPanel();
        south.add(btnSave);
        south.add(btnCancel);

        // =======================
        //   Échap = Annuler
        // =======================

        dlg.getRootPane().registerKeyboardAction(
            new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    dlg.dispose();
                }
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Entrée sur Enregistrer
        dlg.getRootPane().setDefaultButton(btnSave);

        // =======================
        //   Assemblage final
        // =======================

        dlg.getContentPane().setLayout(new BorderLayout(10, 10));
        dlg.getContentPane().add(center, BorderLayout.CENTER);
        dlg.getContentPane().add(south, BorderLayout.SOUTH);

        dlg.pack();
        dlg.setLocationRelativeTo(parent);

        SwingUtilities.invokeLater(() -> comboPolice.requestFocusInWindow());

        dlg.setVisible(true);
    }
}