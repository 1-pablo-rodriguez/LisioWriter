package dia;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;

import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import act.select_CANCEL;
import act.select_OK;
import writer.commandes;
import writer.ui.EditorFrame;

public class BoiteNewDocument {

    private final JDialog dialog;
    private final EditorFrame parent;
    private final JTextField textField;
    private final JButton okButton;
    private final JButton cancelButton;

    // Caractères autorisés pour le nom de fichier
    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9 _-]+$");

    @SuppressWarnings("unused")
	public BoiteNewDocument(EditorFrame parent) {
        this.parent = parent;

        // --- Création de la boîte de dialogue ---
        Frame owner = (parent != null) ? parent : null;
        dialog = new JDialog(owner, "Créer un nouveau document", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setResizable(false);

        // --- Police agrandie pour le confort visuel ---
        Font baseFont = new Font("Segoe UI", Font.PLAIN, 20);
        Font titleFont = baseFont.deriveFont(Font.BOLD, 22f);

        // ========== Zone principale (message + champ) ==========
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // Libellé du champ
        JLabel fieldLabel = new JLabel("Nom du fichier :");
        fieldLabel.setFont(baseFont);
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Champ de saisie
        textField = new JTextField(30);
        textField.setFont(baseFont);
        textField.setAlignmentX(Component.LEFT_ALIGNMENT);


        // Associer le label au champ (utile pour certains lecteurs d’écran)
        fieldLabel.setLabelFor(textField);

        // Entrée dans le champ = validation
        textField.addActionListener(e -> valider());

        // Focus sur le champ (utile si tu veux jouer un son ici plus tard)
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                // Exemple : lecture audio à l’arrivée dans le champ
                // if (commandes.audioActif) new playSound(...);
            }
        });

        // Ajout des composants dans le panel principal
        mainPanel.add(fieldLabel);
        mainPanel.add(Box.createVerticalStrut(5));
        mainPanel.add(textField);

        dialog.add(mainPanel, BorderLayout.CENTER);

        // ========== Zone des boutons ==========
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 15, 20));
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

        okButton = new JButton("OK");
        okButton.setFont(baseFont);
        okButton.setMnemonic(KeyEvent.VK_O); // Alt+O
        okButton.getAccessibleContext().setAccessibleName("Valider");

        cancelButton = new JButton("Annuler");
        cancelButton.setFont(baseFont);
        cancelButton.setMnemonic(KeyEvent.VK_A); // Alt+A
        cancelButton.getAccessibleContext().setAccessibleName("Annuler");

        // Action OK
        okButton.addActionListener(e -> valider());

        // Action Annuler
        cancelButton.addActionListener(e -> annuler());

        // Focus gained → déclenche les actions audio existantes
        okButton.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                AbstractAction action = new select_OK();
                action.actionPerformed(null);
            }
        });

        cancelButton.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                AbstractAction action = new select_CANCEL();
                action.actionPerformed(null);
            }
        });

        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(okButton);
        buttonPanel.add(Box.createHorizontalStrut(15));
        buttonPanel.add(cancelButton);

        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // ========== Raccourcis clavier globaux ==========
        // Entrée = bouton OK par défaut
        dialog.getRootPane().setDefaultButton(okButton);

        // Échap = Annuler partout dans la boîte de dialogue
        dialog.getRootPane().registerKeyboardAction(
            (ActionEvent e) -> annuler(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // ========== Affichage ==========
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setAlwaysOnTop(true);

        // Donne le focus au champ de texte dès l’ouverture
        SwingUtilities.invokeLater(() -> textField.requestFocusInWindow());

        dialog.setVisible(true);
    }

    /**
     * Validation du nom de fichier et création du document.
     */
    private void valider() {
        String name = textField.getText().trim();

        if (name.isEmpty()) {
            InfoDialog.show(parent, "Nom de fichier manquant",
                    "Tu dois saisir un nom pour le nouveau fichier.", parent.getAffichage());
            textField.requestFocusInWindow();
            return;
        }

        // Vérifie les caractères autorisés
        if (!FILENAME_PATTERN.matcher(name).matches()) {
            InfoDialog.show(parent, "Nom de fichier invalide",
                    "Le nom de fichier ne peut contenir que des lettres, des chiffres,\n"
                    + "des espaces, des tirets et des tirets bas.", parent.getAffichage());
            textField.requestFocusInWindow();
            return;
        }

        // Ici, tu peux choisir :
        // - soit la logique actuelle (document vierge),
        // - soit appeler une classe qui crée/charge un fichier physique.
        //
        // Version "document vierge" (comme ton code de bouton OK) :
        System.out.println("Création d'un document vierge : " + name);
        commandes.nameFile = name;
        commandes.hash = 0;

        if (parent != null && parent.getEditor() != null) {
            parent.getEditor().setText("¶ ");
            parent.getEditor().setCaretPosition(2);
        }

        fermer();
    }

    /**
     * Annulation simple.
     */
    private void annuler() {
        fermer();
    }

    /**
     * Fermeture de la boîte de dialogue + retour du focus à l'éditeur.
     */
    private void fermer() {
        if (parent != null) {
            SwingUtilities.invokeLater(() -> {
                parent.requestFocus();
                if (parent.getEditor() != null) {
                    parent.getEditor().requestFocusInWindow();
                }
            });
        }
        dialog.dispose();
    }
}
