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
	                    // --- Supprimer les éléments non pertinents ---
	                	content.select(
	                		    ".mw-editsection, " +
	                		    ".reflist, " +
	                		    ".navbox, " +
	                		    ".metadata, " +
	                		    "sup.reference, " +
	                		    "div[class^=infobox], " +           // anciens <div> infobox
	                		    "table[class~=\\binfobox\\b]"       // toute <table> ayant la classe 'infobox' parmi d’autres
	                		).remove();
	                	
	                	content.select(
	                		    "table.infobox, " +                 // classe 'infobox'
	                		    "table.infobox_v2, " +              // classe 'infobox_v2' (issue de 'infobox&#95;v2')
	                		    "table.infobox--frwiki"             // variante frwiki
	                		).remove();
	                    
	                    content.select(
	                    	    "[class~=\\bbandeau-container\\b]" +
	                    	    "[class~=\\bbandeau-section\\b]" +
	                    	    "[class~=\\bmetadata\\b]" +
	                    	    "[class~=\\bbandeau-niveau-information\\b]"
	                    	).remove();
	                    
	                    content.select("li[id^=cite_note]").remove();
	                    content.select("[id^=cite_note], [id^=cite_ref]").remove();  // enlève tout élément dont l'id commence par cite_note ou cite_ref
	                    content.select("ol.references > li[id^=cite_note]").remove();
	                    
	                    // --- Convertir les tableaux en @t ... @/t ---
		                convertAllTables(content);

	                    // --- Convertir les liens Wikipédia et externes en format LisioWriter ---
	                    Elements links = content.select("a[href]");
	                    for (Element link : links) {
	                        String href = link.attr("href").trim();
	                        @SuppressWarnings("unused")
							String text = link.text().trim();
	                        if (href.isEmpty()) continue;
	
	                        if (href.startsWith("/wiki/")) {
	                            String fullUrl = "https://fr.wikipedia.org" + href;
	                            link.after(" @[lien : " + fullUrl + "]");
	                            link.unwrap();
	                        } else if (href.startsWith("http")) {
	                            link.after(" @[lien : " + href + "]");
	                            link.unwrap();
	                        }
	                    }
	
	                    // --- Convertir les images en descriptions accessibles ---
	                    Elements images = content.select("img");
	                    for (Element img : images) {
	                        String alt = img.attr("alt").trim();
	
	                        // 1️⃣ Essayer d'abord avec alt
	                        // 2️⃣ Puis chercher une légende figcaption ou thumbcaption
	                        if (alt.isEmpty()) {
	                            Element fig = img.closest("figure");
	                            Element caption = null;
	
	                            if (fig != null)
	                                caption = fig.selectFirst("figcaption");
	
	                            if (caption == null) {
	                                Element thumb = img.closest("div.thumb");
	                                if (thumb != null)
	                                    caption = thumb.selectFirst(".thumbcaption");
	                            }
	
	                            if (caption != null && !caption.text().isBlank()) {
	                                alt = caption.text().trim();
	                            } else {
	                                // ⚠️ Pas de description utile → on supprime complètement l'image
	                                img.remove();
	                                continue;
	                            }
	                        }
	
	                        // ✅ On ne garde que les images avec vraie description
	                        img.after("![Image : " + alt + "]");
	                        img.remove();
	                    }
	
	                 // --- Conversion finale du HTML vers le format LisioWriter ---
	                    String html = content.html();
	                    converted = HtmlImporter.importFromHtml(html);
	                    if (converted != null) {
	                        converted = converted
	                            .replace('\u00A0', ' ')      // espace insécable → espace normal
	                            .replace('\u2028', '\n')     // séparateur de ligne → vrai saut de ligne
	                            .replace('\u2029', '\n')     // séparateur de paragraphe → saut de ligne
	                            .replaceAll("[\\r\\n]{3,}", "\n\n") // pas plus de 2 sauts consécutifs

	                            // 1) Enlever les espaces AVANT le marqueur de liste en début de ligne
	                            .replaceAll("(?m)^[ \\t]+(?=(?:-\\.|\\*|\\d+\\.)\\s)", "")

	                            // 2) Choisir ce que tu veux APRÈS le marqueur :
	                            //    a) AUCUN espace après le marqueur:
	                            .replaceAll("(?m)^(?:\\s*)(-\\.|\\*|\\d+\\.)\\s+", "$1")
	                            //    b) (Alternative) EXACTEMENT 1 espace après le marqueur:
	                            // .replaceAll("(?m)^(?:\\s*)(-\\.|\\*|\\d+\\.)\\s+", "$1 ")

	                            .trim();
	                    }
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
	
	                // ✅ Récupérer le titre Wikipédia depuis <h1 id="firstHeading">
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
	                    if (sel.title != null && !sel.title.isBlank())
	                        articleTitle = sel.title;
	                }
	
	                // ✅ Formater le contenu final (titre + texte importé)
	                String formatted = "#1. " + articleTitle + "\n" + (converted == null ? "" : converted);
	                
	                System.out.println("✅ Article inséré : " + articleTitle);
	                	               	                
	                doc.insertString(pos, formatted, null);
	                
	                editorPane.setCaretPosition(pos);
	                
	                // ✅ Fermer la fenêtre et redonner le focus à l’éditeur
	                dispose();
	                commandes.init();
	                commandes.nameFile = articleTitle;
	                
	                SwingUtilities.invokeLater(() -> editorPane.requestFocusInWindow());
	
	            } catch (Exception ex) {
	                ex.printStackTrace();
	            }
	        }
	    };
	
	    wk.execute();
	}

	/** Échappe | et \ dans les cellules pour la syntaxe LisioWriter. */
	private static String escapeCell(String s) {
	    if (s == null) return "";
	    // D’abord \ puis | (pour éviter de ré-échappper)
	    return s.replace("\\", "\\\\").replace("|", "\\|").trim();
	}

	/** Convertit les liens <a> en texte + @[lien : URL] à l’intérieur d’un élément donné. */
	private static void convertLinksInline(org.jsoup.nodes.Element root) {
	    for (org.jsoup.nodes.Element a : root.select("a[href]")) {
	        String href = a.attr("href").trim();
	        if (href.isEmpty()) continue;
	        if (href.startsWith("/wiki/")) href = "https://fr.wikipedia.org" + href;
	        a.after(" @[lien : " + href + "]"); // on ajoute le marqueur après le texte du lien
	        a.unwrap(); // on garde le texte cliquable
	    }
	}

	/** Transforme une table HTML en texte LisioWriter (@t ... @/t). */
	private static String htmlTableToLisio(org.jsoup.nodes.Element table) {
	    StringBuilder out = new StringBuilder();
	    out.append("@t\n");

	    // Chaque ligne <tr>
	    for (org.jsoup.nodes.Element tr : table.select("> tbody > tr, > thead > tr, > tr")) {
	    	// Ligne d’entête = que des <th> (et pas de <td>) OU ligne dans <thead>
	    	boolean header = (tr.parent() != null && "thead".equalsIgnoreCase(tr.parent().tagName()))
	    	                 || (!tr.select("> th").isEmpty() && tr.select("> td").isEmpty());


	        // Récupérer les cellules dans l’ordre (th puis td)
	        List<org.jsoup.nodes.Element> cells = new ArrayList<>();
	        cells.addAll(tr.select("> th"));
	        cells.addAll(tr.select("> td"));

	        // S’il n’y a aucune cellule, ignorer la ligne
	        if (cells.isEmpty()) continue;

	        // Préfixe |! pour en-tête, | sinon
	        out.append(header ? "|! " : "| ");

	        boolean first = true;
	        for (org.jsoup.nodes.Element cell : cells) {
	            if (!first) out.append(" | ");
	            first = false;

	            org.jsoup.nodes.Element copy = cell.clone();

		         // 1) images d’abord (elles peuvent être dans un <a>)
		         convertImagesInline(copy);
	
		         // 2) puis les liens (pour obtenir "... ![Image : ...] @[lien : ...]")
		         convertLinksInline(copy);
	
		         // Récupérer le texte rendu
		         String text = copy.text();
		         out.append(escapeCell(text));

	        }
	        out.append('\n');
	    }

	    out.append("@/t\n");
	    return out.toString();
	}

	// Fabrication des tableaux
	private static org.jsoup.nodes.Element tableAsBlockElement(String lisio) {
	    org.jsoup.nodes.Element div = new org.jsoup.nodes.Element("div").addClass("lw-table");
	    org.jsoup.nodes.Element p = div.appendElement("p");

	    String[] lines = lisio.split("\\R"); // pas de -1 => on ne garde pas la dernière ligne vide potentielle
	    for (int i = 0; i < lines.length; i++) {
	        String line = lines[i];
	        if (line.isEmpty()) continue;      // ignore lignes vides
	        p.appendText(line);
	        if (i < lines.length - 1) {
	            p.appendElement("br");         // simple saut de ligne (pas de paragraphe)
	        }
	    }
	    return div;
	}

	
	private static void convertAllTables(org.jsoup.nodes.Element content) {
	    for (org.jsoup.nodes.Element table : new ArrayList<>(content.select("table"))) {
	        String lisio = htmlTableToLisio(table);
	        org.jsoup.nodes.Element block = tableAsBlockElement(lisio);
	        table.replaceWith(block);
	    }
	}

	/** Convertit les <img> en texte ![Image : ...] à l’intérieur d’un sous-arbre. */
	/** Convertit les <img> en texte ![Image : ...] dans le sous-arbre donné. */
	private static void convertImagesInline(org.jsoup.nodes.Element root) {
	    for (org.jsoup.nodes.Element img : root.select("img")) {
	        String alt = img.attr("alt").trim();
	        if (alt.isEmpty()) alt = img.attr("title").trim();

	        // Ancêtre lien (quel qu'il soit : mw-file-description, image, etc.)
	        org.jsoup.nodes.Element a = img.closest("a");
	        if ((alt == null || alt.isEmpty()) && a != null) {
	            String t = a.attr("title").trim();
	            if (!t.isEmpty()) alt = t;
	            else {
	                String ar = a.attr("aria-label").trim();
	                if (!ar.isEmpty()) alt = ar;
	            }
	        }

	        // Dernier recours : déduire un libellé lisible depuis l'URL du fichier
	        if (alt == null || alt.isEmpty()) {
	            String src = img.attr("src");
	            if (src != null && !src.isBlank()) {
	                // Examples:
	                // //upload.wikimedia.org/.../thumb/e/ef/GERARD_LARCHER_TROMBI_PDC.jpg/250px-...jpg
	                // //upload.wikimedia.org/.../e/ef/GERARD_LARCHER_TROMBI_PDC.jpg
	                String file = src;
	                @SuppressWarnings("unused")
					int idx;
	                // Si c'est une miniature "thumb", le nom de fichier est juste avant le segment taille
	                // On isole la dernière occurrence de ".jpg", ".png", ".jpeg", ".gif", ".webp"
	                String lower = src.toLowerCase();
	                int end = Math.max(
	                    Math.max(lower.lastIndexOf(".jpg"), lower.lastIndexOf(".jpeg")),
	                    Math.max(lower.lastIndexOf(".png"), Math.max(lower.lastIndexOf(".gif"), lower.lastIndexOf(".webp")))
	                );
	                if (end > 0) {
	                    // remonter au dernier '/' avant l'extension
	                    int start = src.lastIndexOf('/', end);
	                    if (start >= 0) file = src.substring(start + 1, end);
	                } else {
	                    // fallback : dernier segment après '/'
	                    int last = src.lastIndexOf('/');
	                    if (last >= 0) file = src.substring(last + 1);
	                }
	                // Nettoyage : underscores -> espaces
	                file = file.replace('_', ' ').trim();
	                if (!file.isEmpty()) alt = file;
	            }
	        }

	        if (alt == null || alt.isEmpty()) {
	            // Ne jamais perdre l'info : texte générique
	            alt = "Image";
	        }

	        // Insérer le marqueur dans la cellule, juste après l'image, puis supprimer <img>
	        img.after("![Image : " + alt + "]");
	        img.remove();
	    }
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
