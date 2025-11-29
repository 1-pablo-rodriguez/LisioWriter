package dia;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import writer.ui.EditorFrame;

@SuppressWarnings("serial")
public class openReplaceDialog extends JDialog {

    // Valeurs mémorisées entre deux ouvertures
    public static String lastSearch  = "";
    public static String lastReplace = "";

    private final JTextComponent editor;
    private final JTextField fieldSearch;
    private final JTextField fieldReplace;
    private final JLabel lblInfo;

    // Position du dernier match dans le texte
    private int lastIndex = -1;

    // --- Méthode statique d'ouverture ---
    public static void open(EditorFrame parent, JTextComponent editor) {
        openReplaceDialog dlg = new openReplaceDialog(parent, editor);
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }

    private openReplaceDialog(EditorFrame parent, JTextComponent editor) {
        // faux modal pour garder le focus clavier possible dans l’éditeur
        super(parent, "Rechercher et remplacer", false);
        this.editor = editor;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        Font labelFont = new Font("Segoe UI", Font.PLAIN, 20);
        Font fieldFont = new Font("Segoe UI", Font.PLAIN, 20);
        Font infoFont  = new Font("Segoe UI", Font.PLAIN, 16);

        // === Centre : champs ===
        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 10, 5, 10);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        // Ligne 1 : texte à rechercher
        JLabel lblSearch = new JLabel("Texte à rechercher :");
        lblSearch.setFont(labelFont);

        fieldSearch = new JTextField(30);
        fieldSearch.setFont(fieldFont);
        fieldSearch.setText(lastSearch != null ? lastSearch : "");
        lblSearch.setLabelFor(fieldSearch); // pas de descriptionAccessible

        c.gridx = 0; c.gridy = 0; c.weightx = 0.0;
        center.add(lblSearch, c);
        c.gridx = 1; c.gridy = 0; c.weightx = 1.0;
        center.add(fieldSearch, c);

        // Ligne 2 : texte de remplacement
        JLabel lblReplace = new JLabel("Texte de remplacement :");
        lblReplace.setFont(labelFont);

        fieldReplace = new JTextField(30);
        fieldReplace.setFont(fieldFont);
        fieldReplace.setText(lastReplace != null ? lastReplace : "");
        lblReplace.setLabelFor(fieldReplace); // toujours pas de descriptionAccessible

        c.gridx = 0; c.gridy = 1; c.weightx = 0.0;
        center.add(lblReplace, c);
        c.gridx = 1; c.gridy = 1; c.weightx = 1.0;
        center.add(fieldReplace, c);

        add(center, BorderLayout.CENTER);

        // === Bas : boutons + infos ===
        JPanel south = new JPanel(new BorderLayout(5, 5));

        JPanel buttons = new JPanel();
        JButton btnFindNext   = new JButton("Suivant");
        JButton btnReplace    = new JButton("Remplacer");
        JButton btnReplaceAll = new JButton("Remplacer tout");
        JButton btnClose      = new JButton("Fermer");

        btnFindNext.setFont(labelFont);
        btnReplace.setFont(labelFont);
        btnReplaceAll.setFont(labelFont);
        btnClose.setFont(labelFont);

        buttons.add(btnFindNext);
        buttons.add(btnReplace);
        buttons.add(btnReplaceAll);
        buttons.add(btnClose);

        south.add(buttons, BorderLayout.CENTER);

        lblInfo = new JLabel("Prêt.");
        lblInfo.setFont(infoFont);
        south.add(lblInfo, BorderLayout.SOUTH);

        add(south, BorderLayout.SOUTH);

        // Taille raisonnable
        pack();
        if (getWidth() < 600) {
            setSize(new Dimension(600, getHeight()));
        }

        // === Actions de base ===
        btnFindNext.addActionListener(e -> findNext());
        btnReplace.addActionListener(e -> replaceOnce());
        btnReplaceAll.addActionListener(e -> replaceAll());
        btnClose.addActionListener(e -> dispose());

        // --- Gestion de la touche Entrée sur les boutons ---
        KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);

        btnFindNext.registerKeyboardAction(
            e -> btnFindNext.doClick(),
            enter,
            JComponent.WHEN_FOCUSED
        );
        btnReplace.registerKeyboardAction(
            e -> btnReplace.doClick(),
            enter,
            JComponent.WHEN_FOCUSED
        );
        btnReplaceAll.registerKeyboardAction(
            e -> btnReplaceAll.doClick(),
            enter,
            JComponent.WHEN_FOCUSED
        );
        btnClose.registerKeyboardAction(
            e -> btnClose.doClick(),
            enter,
            JComponent.WHEN_FOCUSED
        );

        // Entrée = Remplacer quand le focus est dans un champ texte
        getRootPane().setDefaultButton(btnReplace);

        // ESC = fermer la boîte
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Raccourcis Alt (via mnemonics)
        btnFindNext.setMnemonic('S');  // Alt+S
        btnReplace.setMnemonic('R');   // Alt+R
        btnReplaceAll.setMnemonic('T'); // Alt+T
        btnClose.setMnemonic('F');     // Alt+F

        // Au moment de l'ouverture : partir de la position du caret
        SwingUtilities.invokeLater(() -> {
            fieldSearch.requestFocusInWindow();
            fieldSearch.selectAll();
            if (editor != null) {
                lastIndex = editor.getCaretPosition();
            } else {
                lastIndex = 0;
            }
            if (!fieldSearch.getText().isEmpty()) {
                findNext();
            }
        });
    }

    // ==========================
    //   Logique Rechercher / Remplacer
    // ==========================

    private String getEditorText() {
        return editor != null ? editor.getText() : "";
    }

    private void findNext() {
        String search = fieldSearch.getText();
        if (search == null || search.isEmpty()) {
            lblInfo.setText("Tape d'abord le texte à rechercher.");
            fieldSearch.requestFocusInWindow();
            return;
        }

        lastSearch = search;

        String all = getEditorText();
        if (all.isEmpty()) {
            lblInfo.setText("Texte vide.");
            return;
        }

        int startPos = 0;
        if (editor != null) {
            startPos = Math.max(0, editor.getCaretPosition());
        }
        if (lastIndex >= 0) {
            startPos = lastIndex + search.length();
        }

        int idx = all.indexOf(search, startPos);

        // Si plus rien après, on boucle depuis le début
        if (idx < 0) {
            idx = all.indexOf(search);
        }

        if (idx < 0) {
            lblInfo.setText("Aucune occurrence trouvée.");
            lastIndex = -1;
            return;
        }

        lastIndex = idx;

        // sélectionne dans l'éditeur
        if (editor != null) {
            editor.requestFocusInWindow();
            editor.select(idx, idx + search.length());
        }

        // calcule nombre total d'occurrences (pour info)
        int total = 0;
        int pos = 0;
        while (true) {
            int p = all.indexOf(search, pos);
            if (p < 0) break;
            total++;
            pos = p + search.length();
        }

        lblInfo.setText("Occurrence trouvée. Position " + (idx + 1) + "/" + all.length()
                        + " – " + total + " occurrence(s) au total.");
    }

    private void replaceOnce() {
        String search  = fieldSearch.getText();
        String replace = fieldReplace.getText();

        if (search == null || search.isEmpty()) {
            lblInfo.setText("Texte à rechercher vide.");
            fieldSearch.requestFocusInWindow();
            return;
        }

        lastSearch  = search;
        lastReplace = replace;

        if (editor == null) {
            lblInfo.setText("Aucun éditeur.");
            return;
        }

        int selStart = editor.getSelectionStart();
        int selEnd   = editor.getSelectionEnd();

        try {
            // Si la sélection actuelle ne correspond pas au texte recherché,
            // on cherche la prochaine occurrence.
            if (selStart < 0 || selEnd <= selStart ||
                !editor.getText().regionMatches(selStart, search, 0, search.length())) {

                findNext();
                selStart = editor.getSelectionStart();
                selEnd   = editor.getSelectionEnd();

                if (selStart < 0 || selEnd <= selStart) {
                    lblInfo.setText("Rien à remplacer.");
                    return;
                }
            }

            Document doc = editor.getDocument();
            doc.remove(selStart, selEnd - selStart);
            doc.insertString(selStart, replace, null);

            // caret après le remplacement
            int newPos = selStart + replace.length();
            editor.setCaretPosition(newPos);
            lastIndex = newPos - replace.length(); // pour repartir juste après

            lblInfo.setText("Une occurrence remplacée.");
            // On passe directement à la suivante
            findNext();

        } catch (BadLocationException ex) {
            ex.printStackTrace();
            lblInfo.setText("Erreur pendant le remplacement.");
        }
    }

    private void replaceAll() {
        String search  = fieldSearch.getText();
        String replace = fieldReplace.getText();

        if (search == null || search.isEmpty()) {
            lblInfo.setText("Texte à rechercher vide.");
            fieldSearch.requestFocusInWindow();
            return;
        }

        lastSearch  = search;
        lastReplace = replace;

        String all = getEditorText();
        if (all.isEmpty()) {
            lblInfo.setText("Texte vide.");
            return;
        }

        int count = 0;
        StringBuilder sb = new StringBuilder();
        int from = 0;
        int idx;

        while ((idx = all.indexOf(search, from)) >= 0) {
            sb.append(all, from, idx);
            sb.append(replace);
            from = idx + search.length();
            count++;
        }
        sb.append(all.substring(from));

        try {
            if (editor != null) {
                Document doc = editor.getDocument();
                doc.remove(0, doc.getLength());
                doc.insertString(0, sb.toString(), null);

                // On remet le caret au début
                editor.setCaretPosition(0);
            }

            lastIndex = -1;

            if (count == 0) {
                lblInfo.setText("Aucune occurrence trouvée.");
            } else {
                lblInfo.setText(count + " occurrence(s) remplacée(s).");
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
            lblInfo.setText("Erreur pendant le remplacement global.");
        }
    }
}