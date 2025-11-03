package dia;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import writer.commandes;
import writer.ui.EditorFrame;

public class boiteMeta {
    private final EditorFrame parent;
    private JDialog dialog;

    // Champs existants
    private JTextField titreField, sujetField, auteurField, coauteurField, societyField;
    // Nouveaux champs
    private JTextArea descriptionField;
    private JTextField motsClesField;
    private JComboBox<String> langueField;
    
    // En haut de la classe
    private static final int LV_FONT_SIZE = 22; // adapte si besoin (20–24 conseillé)


    public boiteMeta(EditorFrame parent) {
        this.parent = parent;
        buildUI();
    }

    private void buildUI() {
        dialog = new JDialog((Frame) null, "Modifier les méta-données", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(15, 15));

        Font font = new Font("Segoe UI", Font.PLAIN, 18);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // --- Crée les champs
        titreField = createTextField(font);
        sujetField = createTextField(font);
        auteurField = createTextField(font);
        coauteurField = createTextField(font);
        societyField = createTextField(font);

        descriptionField = createTextArea(font);
        enableTabTraversal(descriptionField);
        
        motsClesField = createTextField(font);

        langueField = new JComboBox<>(new String[] {
        	    "allemand",
        	    "anglais",
        	    "danois",
        	    "espagnol",
        	    "finnois",
        	    "français",
        	    "grec",
        	    "italien",
        	    "néerlandais",
        	    "norvégien",
        	    "polonais",
        	    "portugais",
        	    "suédois"
        	});
        langueField.setEditable(true);
        langueField.setFont(font);
        langueField.getAccessibleContext().setAccessibleName("Langue : liste déroulante, éditable");
        langueField.getAccessibleContext().setAccessibleDescription(
            "Choisissez ou saisissez la langue du document, par exemple français ou anglais."
        );

        // valeur par défaut avant override par les métas (si présentes)
        langueField.setSelectedItem("français");
        
        langueField.setEditable(true);
        langueField.setFont(font);
        langueField.getAccessibleContext().setAccessibleName("Langue : liste, éditable");
        
        // --- Valeurs initiales depuis commandes.meta
        setTextSafe(titreField,      "titre",     "LeTitre");
        setTextSafe(sujetField,      "sujet",     "LeSujet");
        setTextSafe(auteurField,     "auteur",    "nom");
        setTextSafe(coauteurField,   "coauteur",  "nom");
        setTextSafe(societyField,    "society",   "nom");
        setTextAreaSafe(descriptionField, "description", "description");
        setTextSafe(motsClesField,   "motsCles",  "motsCles");

        String langueInit = getMetaAttr("langue", "langue");
        if (langueInit != null && !langueInit.isBlank()) {
            langueField.setSelectedItem(langueInit);
        }

        int row = 0;
        addRow(formPanel, gbc, row++, "Titre :", titreField);
        addRow(formPanel, gbc, row++, "Sujet :", sujetField);
        addRow(formPanel, gbc, row++, "Auteur :", auteurField);
        addRow(formPanel, gbc, row++, "Coauteur :", coauteurField);
        addRow(formPanel, gbc, row++, "Société :", societyField);
        addRowTextArea(formPanel, gbc, row++, "Description :", descriptionField, 4);
        addRowCombo(formPanel, gbc, row++, "Mots-clés :", motsClesField); // champ simple mais placé ici pour la lisibilité
        addRowCombo(formPanel, gbc, row++, "Langue :", langueField);

        // --- Boutons
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Annuler");
        okBtn.setFont(font);
        cancelBtn.setFont(font);
        okBtn.addActionListener(e -> valide());
        cancelBtn.addActionListener(e -> fermeture());

        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(Color.WHITE);
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        // --- Raccourcis clavier (Entrée valide, Échap annule)
        KeyAdapter keys = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !(e.getSource() instanceof JTextArea)) {
                    // Enter dans les champs mono-ligne => Valider
                    valide();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    fermeture();
                }
            }
        };
        for (JTextField f : new JTextField[]{titreField, sujetField, auteurField, coauteurField, societyField, motsClesField}) {
            f.addKeyListener(keys);
        }
        // Pour la zone Description : Enter = nouvelle ligne ; Ctrl+Enter = Valider
        descriptionField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) valide();
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) fermeture();
            }
        });

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(okBtn);

        dialog.pack();
        dialog.setSize(640, 560);
        dialog.setLocationRelativeTo(null);
        SwingUtilities.invokeLater(titreField::requestFocusInWindow);
        dialog.setVisible(true);
    }

    // ===== Helpers UI accessibles =====
    private JTextField createTextField(Font font) {
        JTextField f = new JTextField(25);
        f.setFont(font);
        return f;
    }

    private JTextArea createTextArea(Font ignored) {
        JTextArea ta = new JTextArea();
        ta.setFont(new Font("Segoe UI", Font.PLAIN, LV_FONT_SIZE));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setMargin(new Insets(6, 8, 6, 8)); // lisibilité
        ta.setCaretColor(Color.BLACK);
        ta.setSelectedTextColor(Color.BLACK);
        ta.setSelectionColor(new Color(255, 230, 150)); // contraste doux
        return ta;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int y, String labelText, JTextField field) {
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Segoe UI", Font.BOLD, 18));
        label.setLabelFor(field);

        field.getAccessibleContext().setAccessibleName(labelText + " Champ d’édition de texte");
        field.getAccessibleContext().setAccessibleDescription("Saisissez " + labelText.replace(" :", "").toLowerCase());

        gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0.0;
        panel.add(label, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(field, gbc);
    }

    private void addRowTextArea(JPanel panel, GridBagConstraints gbc, int y,
            String labelText, JTextArea area, int rows) {
		JLabel label = new JLabel(labelText);
		label.setFont(new Font("Segoe UI", Font.BOLD, 18));
		label.setLabelFor(area);
		
		// Accessibilité
		area.getAccessibleContext().setAccessibleName(labelText + " Zone de texte multiligne");
		area.getAccessibleContext().setAccessibleDescription(
		"Saisissez " + labelText.replace(" :", "").toLowerCase()
		+ ". Tab pour changer de champ. Ctrl+Entrée pour valider.");
		
		// ≥ N lignes visibles
		int visibleRows = Math.max(rows, 5);     // ← mets 5 (ou 6) pour bien voir le texte
		area.setRows(visibleRows);
		area.setColumns(40);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setMargin(new Insets(6, 8, 6, 8));
		
		// Calcule une hauteur préférée fiable à partir de la police
		FontMetrics fm = area.getFontMetrics(area.getFont());
		int lineHeight = fm.getHeight();
		int height = lineHeight * visibleRows + 20;  // marge incluse
		
		JScrollPane sp = new JScrollPane(area);
		sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		sp.setPreferredSize(new Dimension(600, height));
		sp.setMinimumSize(new Dimension(300, height));
		
		// Colonne label
		gbc.gridx = 0; gbc.gridy = y;
		gbc.weightx = 0.0; gbc.weighty = 0.0;        // le label ne grandit pas
		gbc.fill = GridBagConstraints.NONE;
		panel.add(label, gbc);
		
		// Colonne zone de texte : elle peut grandir en hauteur
		gbc.gridx = 1;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;                           // ← donne de la hauteur à cette ligne
		gbc.fill = GridBagConstraints.BOTH;          // ← s’étire en largeur ET hauteur
		panel.add(sp, gbc);
		
		// IMPORTANT : réinitialiser pour les lignes suivantes
		gbc.weighty = 0.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
	}


    private void addRowCombo(JPanel panel, GridBagConstraints gbc, int y, String labelText, JComponent comp) {
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Segoe UI", Font.BOLD, 18));
        label.setLabelFor(comp);

        comp.getAccessibleContext().setAccessibleName(labelText + " Champ d’édition de texte");
        comp.getAccessibleContext().setAccessibleDescription("Saisissez " + labelText.replace(" :", "").toLowerCase());

        gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0.0;
        panel.add(label, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(comp, gbc);
    }

    // ===== Accès sûrs aux méta =====
    private static void setTextSafe(JTextField field, String node, String attr) {
        String v = getMetaAttr(node, attr);
        field.setText(v != null ? v : "");
    }
    private static void setTextAreaSafe(JTextArea area, String node, String attr) {
        String v = getMetaAttr(node, attr);
        area.setText(v != null ? v : "");
    }
    private static String getMetaAttr(String node, String attr) {
        try {
            var n = commandes.meta.retourneFirstEnfant(node);
            if (n == null) return null;
            return n.getAttributs().get(attr);
        } catch (Exception e) {
            return null;
        }
    }

    // ===== Fermer / Valider =====
    private void fermeture() {
        if (parent != null) {
            SwingUtilities.invokeLater(() -> {
                parent.requestFocus();
                parent.getEditor().requestFocusInWindow();
            });
        }
        dialog.dispose();
    }

    private void valide() {
        // existants
        commandes.meta.retourneFirstEnfant("titre").getAttributs().put("LeTitre",  titreField.getText().trim());
        commandes.meta.retourneFirstEnfant("sujet").getAttributs().put("LeSujet",  sujetField.getText().trim());
        commandes.meta.retourneFirstEnfant("auteur").getAttributs().put("nom",     auteurField.getText().trim());
        commandes.meta.retourneFirstEnfant("coauteur").getAttributs().put("nom",   coauteurField.getText().trim());
        commandes.meta.retourneFirstEnfant("society").getAttributs().put("nom",    societyField.getText().trim());

        // nouveaux
        commandes.meta.retourneFirstEnfant("description").getAttributs()
                .put("description", descriptionField.getText().trim());
        commandes.meta.retourneFirstEnfant("motsCles").getAttributs()
                .put("motsCles", motsClesField.getText().trim());
        Object langValue = langueField.getEditor().getItem();
        commandes.meta.retourneFirstEnfant("langue").getAttributs()
                .put("langue", langValue != null ? langValue.toString().trim() : "");

        fermeture();
    }
    
 // === À mettre dans ta classe (méthode utilitaire) ===
    @SuppressWarnings("serial")
	private static void enableTabTraversal(JTextArea area) {
        // Tab -> focus suivant
        area.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "focusNext");
        area.getActionMap().put("focusNext", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                area.transferFocus();
            }
        });

        // Shift+Tab -> focus précédent
        area.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK), "focusPrev");
        area.getActionMap().put("focusPrev", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                area.transferFocusBackward();
            }
        });
    }
    



}
