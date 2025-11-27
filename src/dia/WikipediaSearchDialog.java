package dia;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.swing.*;
import writer.ui.EditorFrame;

public class WikipediaSearchDialog {

    public static void open(EditorFrame parent, java.util.function.Consumer<String> onSearch) {
        // --- Création de la boîte de dialogue ---
        JDialog dlg = new JDialog(parent, "Recherche Wikipédia", true);
        dlg.setLayout(new BorderLayout(15, 15));
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setResizable(false);

        // === Apparence adaptée aux malvoyants ===
        Font labelFont = new Font("Segoe UI Semibold", Font.PLAIN, 22);
        Font fieldFont = new Font("Segoe UI", Font.PLAIN, 26); // 💡 Taille plus grande ici
        Font buttonFont = new Font("Segoe UI Semibold", Font.PLAIN, 20);

        Color bg = new Color(245, 245, 245);
        Color fg = Color.BLACK;

        dlg.getContentPane().setBackground(bg);

        // --- Label principal ---
        JLabel lbl = new JLabel("Rechercher un article sur Wikipédia :");
        lbl.setFont(labelFont);
        lbl.setForeground(fg);
        lbl.setBorder(BorderFactory.createEmptyBorder(15, 20, 5, 20));

        // --- Champ de recherche agrandi ---
        JTextField field = new JTextField(40);
        field.setFont(fieldFont);
        field.setForeground(fg);
        field.setBackground(Color.WHITE);
        field.setCaretColor(Color.BLACK);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY, 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        field.setPreferredSize(new Dimension(650, 60)); // agrandit la hauteur
        field.getAccessibleContext().setAccessibleName("Zone de saisie du mot-clé Wikipédia");
//        field.getAccessibleContext().setAccessibleDescription("Tapez le mot à rechercher puis appuyez sur Entrée pour lancer la recherche.");

        // --- Boutons bas de fenêtre ---
        JButton searchBtn = new JButton("🔍 Rechercher (Entrée)");
        JButton cancelBtn = new JButton("❌ Annuler (Échap)");
        searchBtn.setFont(buttonFont);
        cancelBtn.setFont(buttonFont);

        Dimension buttonSize = new Dimension(300, 55);
        searchBtn.setPreferredSize(buttonSize);
        cancelBtn.setPreferredSize(buttonSize);

        searchBtn.setBackground(new Color(210, 230, 255));
        cancelBtn.setBackground(new Color(255, 225, 225));

        searchBtn.setFocusPainted(true);
        cancelBtn.setFocusPainted(true);

        // --- Disposition ---
        JPanel center = new JPanel(new BorderLayout(15, 15));
        center.setBackground(bg);
        center.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        center.add(lbl, BorderLayout.NORTH);
        center.add(field, BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.setBackground(bg);
        south.add(searchBtn);
        south.add(cancelBtn);

        dlg.add(center, BorderLayout.CENTER);
        dlg.add(south, BorderLayout.SOUTH);

        // --- Accessibilité clavier ---
        dlg.getRootPane().setDefaultButton(searchBtn);
        dlg.getRootPane().registerKeyboardAction(e -> {
            dlg.dispose();
            SwingUtilities.invokeLater(() -> parent.getEditor().requestFocusInWindow());
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // --- Action recherche ---
        searchBtn.addActionListener(e -> {
            String query = field.getText().trim();
            if (!query.isEmpty()) {
                try {
                    Toolkit.getDefaultToolkit().beep();
                    System.out.println("Recherche Wikipédia pour : " + query);

                    String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                    String url = "https://fr.wikipedia.org/w/index.php?search=" + encoded
                            + "&title=Sp%C3%A9cial%3ARecherche&profile=advanced&fulltext=1&ns0=1";

                    dlg.dispose();
                    new HtmlBrowserDialog(parent, parent.getEditor(), url);

                } catch (Exception ex) {
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(dlg, "Erreur d'encodage : " + ex.getMessage(),
                            "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                Toolkit.getDefaultToolkit().beep();
                JOptionPane.showMessageDialog(dlg,
                        "Veuillez saisir un mot à rechercher.",
                        "Saisie manquante", JOptionPane.WARNING_MESSAGE);
            }
        });

        cancelBtn.addActionListener(e -> {
            dlg.dispose();
            SwingUtilities.invokeLater(() -> parent.getEditor().requestFocusInWindow());
        });

        // --- Accessibilité focus automatique ---
        EventQueue.invokeLater(() -> {
            field.requestFocusInWindow();
            Toolkit.getDefaultToolkit().beep();
            System.out.println("Fenêtre de recherche Wikipédia ouverte.");
        });

        dlg.pack();
        dlg.setMinimumSize(new Dimension(750, 280));
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }
}
