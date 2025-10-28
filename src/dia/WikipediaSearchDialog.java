package dia;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import writer.ui.EditorFrame;

public class WikipediaSearchDialog {

    public static void open(EditorFrame parent, java.util.function.Consumer<String> onSearch) {
        JDialog dlg = new JDialog(parent, "Recherche Wikipédia", true);
        dlg.setLayout(new BorderLayout(10, 10));

        JLabel lbl = new JLabel("Rechercher un article sur Wikipédia :");
        JTextField field = new JTextField(40);
        JButton searchBtn = new JButton("Rechercher (Entrée)");
        JButton cancelBtn = new JButton("Annuler (Échap)");

        JPanel center = new JPanel(new BorderLayout());
        center.add(lbl, BorderLayout.NORTH);
        center.add(field, BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.add(searchBtn);
        south.add(cancelBtn);

        dlg.add(center, BorderLayout.CENTER);
        dlg.add(south, BorderLayout.SOUTH);

        // Raccourcis clavier
        dlg.getRootPane().setDefaultButton(searchBtn);
        dlg.getRootPane().registerKeyboardAction(e -> dlg.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Action recherche
        searchBtn.addActionListener(e -> {
            String query = field.getText().trim();
            if (!query.isEmpty()) {
                try {
                    String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                    String url = "https://fr.wikipedia.org/w/index.php?search=" + encoded
                            + "&title=Sp%C3%A9cial%3ARecherche&profile=advanced&fulltext=1&ns0=1";

                    dlg.dispose();

                    // ✅ Crée et ouvre directement le navigateur avec la recherche
                    new HtmlBrowserDialog(parent, parent.getEditor(), url);

                } catch (Exception ex) {
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(dlg, "Erreur encodage : " + ex.getMessage());
                }
            }
        });

        cancelBtn.addActionListener(e -> dlg.dispose());

        dlg.pack();
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }
}