package dia;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.JTextComponent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import Import.HtmlImporter;
import writer.commandes;

@SuppressWarnings("serial")
public class HtmlBrowserDialog extends JDialog {

    private final JButton insertBtn = new JButton("Insérer (Entrée)");
    private final JButton closeBtn = new JButton("Fermer (Échap)");
    private final DefaultListModel<WikiResult> resultModel = new DefaultListModel<>();
    private final JList<WikiResult> resultList = new JList<>(resultModel);


    public HtmlBrowserDialog(JFrame owner, JTextComponent editorPane, String searchUrl) {
        super(owner, "Résultats Wikipédia", true);
        setLayout(new BorderLayout(6, 6));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // === Liste des résultats ===
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFont(editorPane.getFont());
        resultList.getAccessibleContext().setAccessibleName("Résultats de recherche Wikipédia");
        resultList.getAccessibleContext().setAccessibleDescription("Liste des résultats de recherche Wikipédia. Appuyez sur Entrée pour insérer.");

        JScrollPane sc = new JScrollPane(resultList);
        sc.setPreferredSize(new Dimension(900, 600));
        add(sc, BorderLayout.CENTER);

        // === Boutons bas de fenêtre ===
        JPanel bottomPanel = new JPanel();
        insertBtn.getAccessibleContext().setAccessibleName("Insérer");
        insertBtn.getAccessibleContext().setAccessibleDescription("Insère l'article sélectionné dans le document.");
        bottomPanel.add(insertBtn);

        closeBtn.getAccessibleContext().setAccessibleName("Fermer");
        closeBtn.getAccessibleContext().setAccessibleDescription("Ferme la fenêtre des résultats Wikipédia.");
        bottomPanel.add(closeBtn);

        add(bottomPanel, BorderLayout.SOUTH);

        // === Navigation clavier simple ===
        resultList.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    insertBtn.doClick();
                }
            }
        });
        
        // === Affichage du titre et de l'url dans la resultList ===
        resultList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            javax.swing.JLabel label = new javax.swing.JLabel();
            label.setText("<html><b>" + value.title + "</b><br><small>" + value.url + "</small></html>");
            label.setOpaque(true);
            label.setFont(list.getFont());
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            return label;
        });


        // === Actions ===
        insertBtn.addActionListener(e -> insertIntoEditor(editorPane));
        closeBtn.addActionListener(e -> {
            dispose();
            // remettre le focus dans l’éditeur principal
            SwingUtilities.invokeLater(() -> editorPane.requestFocusInWindow());
        });

        // === Key Binding globale pour Échap ===
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeDialog");
        getRootPane().getActionMap().put("closeDialog", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                dispose();
                SwingUtilities.invokeLater(() -> editorPane.requestFocusInWindow());
            }
        });

        // === Charger les résultats ===
        loadWikipediaResults(searchUrl);

        pack();
        setLocationRelativeTo(owner);
        setVisible(true);

        EventQueue.invokeLater(() -> {
            resultList.requestFocusInWindow();
            if (resultModel.size() > 0) resultList.setSelectedIndex(0);
        });
        
    }

    /** Charge les résultats Wikipédia à partir de l’URL donnée. */
    private void loadWikipediaResults(String url) {
        if (url == null || url.isBlank()) return;
        System.out.println("Chargement des résultats depuis : " + url);

        SwingWorker<List<String[]>, Void> wk = new SwingWorker<>() {
            String error = null;

            @Override
            protected List<String[]> doInBackground() {
                List<String[]> resultsList = new ArrayList<>();
                try {
                    Document fullDoc = Jsoup.connect(url)
                            .userAgent("blindWriter/accessible-browser")
                            .timeout(15000)
                            .followRedirects(true)
                            .get();

                    Elements results = fullDoc.select(".mw-search-result-heading a");

                    if (results.isEmpty()) {
                        resultsList.add(new String[]{"Aucun résultat trouvé.", ""});
                    } else {
                        for (Element e : results) {
                            String title = e.text();
                            String href = e.absUrl("href");
                            resultsList.add(new String[]{title, href});
                        }
                    }

                } catch (Exception ex) {
                    error = ex.getMessage();
                }
                return resultsList;
            }

            @Override
            protected void done() {
                try {
                    if (error != null) {
                        resultModel.clear();
                        resultModel.addElement(new WikiResult("Erreur : " + error, null));
                        Toolkit.getDefaultToolkit().beep();
                        System.out.println("Erreur : " + error);
                        return;
                    }

                    List<String[]> results = get();
                    resultModel.clear();

                    for (String[] pair : results) {
                        String title = pair[0];
                        String href = pair[1];
                        resultModel.addElement(new WikiResult(title, href));
                    }

                    System.out.println(results.size() + " résultats chargés.");
                    if (!resultModel.isEmpty()) {
                        resultList.setSelectedIndex(0);
                        resultList.ensureIndexIsVisible(0);
                    }
                    resultList.repaint();

                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(HtmlBrowserDialog.this,
                            "Erreur : " + ex.getMessage(),
                            "Erreur de chargement",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        wk.execute();
    }

    /** Insère le texte de l’article sélectionné dans le document. */
    private void insertIntoEditor(JTextComponent editorPane) {
        WikiResult sel = resultList.getSelectedValue();
        if (sel == null || sel.url == null || sel.url.isBlank()) {
            Toolkit.getDefaultToolkit().beep();
            System.out.println("Aucun résultat sélectionné.");
            return;
        }

        String url = sel.url;
        System.out.println("Téléchargement de l’article : " + url);

        SwingWorker<Void, Void> wk = new SwingWorker<>() {
            String converted = null;
            String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    Element content = Jsoup.connect(url)
                            .userAgent("LisioWriter/accessible-browser")
                            .timeout(15000)
                            .followRedirects(true)
                            .get()
                            .selectFirst("#mw-content-text");

                    if (content != null) {
                        content.select(".mw-editsection, .reflist, .navbox, .infobox, .metadata, table, sup.reference").remove();
                        String html = content.html();
                        converted = HtmlImporter.importFromHtml(html);
                    }
                } catch (Exception ex) {
                    error = ex.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(HtmlBrowserDialog.this, "Erreur lors du chargement : " + error);
                    return;
                }
                try {
                    javax.swing.text.Document doc = editorPane.getDocument();
                    int pos = doc.getLength();

                    // ✅ Récupérer le vrai titre Wikipédia depuis la balise <h1 id="firstHeading">
                    String articleTitle = "Article Wikipédia";
                    try {
                        Document titleDoc = Jsoup.connect(url)
                                .userAgent("LisioWriter/accessible-browser")
                                .timeout(10000)
                                .get();
                        Element h1 = titleDoc.selectFirst("#firstHeading");
                        if (h1 != null && !h1.text().isBlank()) {
                            articleTitle = h1.text();
                        }
                    } catch (Exception ignore) {
                        // en cas d’erreur, on garde le titre du lien
                        if (sel.title != null && !sel.title.isBlank())
                            articleTitle = sel.title;
                    }

                    // ✅ Formater le contenu en texte blindWriter (titre + contenu)
                    String formatted = "#1. " + articleTitle + "\n" + (converted == null ? "" : converted);
                    
                    doc.insertString(pos, formatted, null);
                    editorPane.setCaretPosition(pos);
                    System.out.println("✅ Article inséré : " + articleTitle);

                    // ✅ Fermer la fenêtre et redonner le focus
                    dispose();
                    commandes.nameFile = articleTitle;
                    SwingUtilities.invokeLater(() -> editorPane.requestFocusInWindow());

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };

        wk.execute();
    }
    
    /** Classe interne représentant un résultat Wikipédia (titre + URL). */
    private static class WikiResult {
        final String title;
        final String url;

        WikiResult(String title, String url) {
            this.title = title;
            this.url = url;
        }

        @Override
        public String toString() {
            // ce qui sera affiché dans la JList
            return title;
        }
    }

    
}
