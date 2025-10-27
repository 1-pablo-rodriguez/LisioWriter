// HtmlBrowserDialog.java
package dia;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import Import.HtmlImporter;

@SuppressWarnings("serial")
public class HtmlBrowserDialog extends JDialog {

    private final JTextField addressField = new JTextField();
    private final JButton backBtn = new JButton("← (Retour)");
    private final JButton forwardBtn = new JButton("→ (Suivant)");
    private final JButton reloadBtn = new JButton("⟳ (Recharger)");
    private final JButton linksBtn = new JButton("Liens");
    private final JButton insertBtn = new JButton("Insérer (Ctrl+I)");
    private final JButton closeBtn = new JButton("Fermer (Esc)");
    private final JTextArea previewArea = new JTextArea();
    private final JLabel statusLabel = new JLabel(" ");

    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;

    // liens extraits (ordonnés) pour la vue "Liens"
    private List<String> extractedLinks = new ArrayList<>();

    public HtmlBrowserDialog(JFrame owner, JTextArea editorPane) {
        super(owner, "Navigateur accessible — blindWriter", true); // modal pour braille/TTS clair
        setLayout(new BorderLayout(6,6));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Top panel : controls + adresse
        JPanel top = new JPanel(new BorderLayout(6,6));
        JPanel controls = new JPanel();

        // boutons avec accessible names/descriptions
        backBtn.setMnemonic(KeyEvent.VK_B);
        backBtn.getAccessibleContext().setAccessibleName("Bouton Retour");
        backBtn.getAccessibleContext().setAccessibleDescription("Retourne à la page précédente de l'historique. Alt+Flèche gauche ou Alt+←.");
        controls.add(backBtn);

        forwardBtn.setMnemonic(KeyEvent.VK_F);
        forwardBtn.getAccessibleContext().setAccessibleName("Bouton Suivant");
        forwardBtn.getAccessibleContext().setAccessibleDescription("Avance à la page suivante de l'historique. Alt+Flèche droite ou Alt+→.");
        controls.add(forwardBtn);

        reloadBtn.setMnemonic(KeyEvent.VK_R);
        reloadBtn.getAccessibleContext().setAccessibleName("Bouton Recharger");
        reloadBtn.getAccessibleContext().setAccessibleDescription("Recharge la page courante. Ctrl+R.");
        controls.add(reloadBtn);

        linksBtn.setMnemonic(KeyEvent.VK_L);
        linksBtn.getAccessibleContext().setAccessibleName("Bouton Liens");
        linksBtn.getAccessibleContext().setAccessibleDescription("Ouvre la liste des liens présents sur la page pour sélection clavier.");
        controls.add(linksBtn);

        top.add(controls, BorderLayout.WEST);

        addressField.getAccessibleContext().setAccessibleName("Champ adresse");
        addressField.getAccessibleContext().setAccessibleDescription("Saisissez l'URL ou collez-la puis appuyez sur Entrée pour charger. Ctrl+L pour y revenir.");
        top.add(addressField, BorderLayout.CENTER);

        JPanel rightControls = new JPanel(new BorderLayout(4,4));
        insertBtn.getAccessibleContext().setAccessibleName("Insérer");
        insertBtn.getAccessibleContext().setAccessibleDescription("Insère le texte converti dans le document. Raccourci Ctrl+I.");
        rightControls.add(insertBtn, BorderLayout.NORTH);

        closeBtn.getAccessibleContext().setAccessibleName("Fermer");
        closeBtn.getAccessibleContext().setAccessibleDescription("Ferme la fenêtre du navigateur. Esc ou Ctrl+W.");
        rightControls.add(closeBtn, BorderLayout.SOUTH);

        top.add(rightControls, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);

        // Preview area — texte converti (non riche)
        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setFont(editorPane.getFont()); // même police que l'éditeur principal
        previewArea.getAccessibleContext().setAccessibleName("Aperçu de la page");
        previewArea.getAccessibleContext().setAccessibleDescription("Zone contenant le texte converti de la page web. Utilisez Tab pour atteindre les commandes.");

        JScrollPane sc = new JScrollPane(previewArea);
        sc.setPreferredSize(new Dimension(900, 600));
        add(sc, BorderLayout.CENTER);

        statusLabel.getAccessibleContext().setAccessibleName("Statut navigation");
        statusLabel.getAccessibleContext().setAccessibleDescription("Affiche des informations de chargement ou d'erreur.");
        add(statusLabel, BorderLayout.SOUTH);

        // Actions wiring
        addressField.addActionListener(e -> navigateTo(addressField.getText().trim()));
        backBtn.addActionListener(e -> goBack());
        forwardBtn.addActionListener(e -> goForward());
        reloadBtn.addActionListener(e -> reload());
        insertBtn.addActionListener(e -> insertIntoEditor(editorPane));
        closeBtn.addActionListener(e -> closeDialog());
        linksBtn.addActionListener(e -> openLinksDialog());

        // Key bindings globales (au JRootPane)
        var root = getRootPane();

        // Ctrl+L => focus champ adresse
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control L"), "focusAddress");
        root.getActionMap().put("focusAddress", new AbstractAction(){ public void actionPerformed(ActionEvent e){ addressField.requestFocusInWindow(); }});

        // Ctrl+I => Insérer
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control I"), "insertDoc");
        root.getActionMap().put("insertDoc", new AbstractAction(){ public void actionPerformed(ActionEvent e){ insertIntoEditor(editorPane); }});

        // Ctrl+R => Reload
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control R"), "reload");
        root.getActionMap().put("reload", new AbstractAction(){ public void actionPerformed(ActionEvent e){ reload(); }});

        // Ctrl+W or Esc => close
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl W"), "close");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        root.getActionMap().put("close", new AbstractAction(){ public void actionPerformed(ActionEvent e){ closeDialog(); }});

        // Alt+Left / Alt+Right for back/forward
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.ALT_DOWN_MASK), "back");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.ALT_DOWN_MASK), "forward");
        root.getActionMap().put("back", new AbstractAction(){ public void actionPerformed(ActionEvent e){ goBack(); }});
        root.getActionMap().put("forward", new AbstractAction(){ public void actionPerformed(ActionEvent e){ goForward(); }});

        // Etats init
        updateControls();

        // Focus initial sur le champ adresse pour saisie rapide
        pack();
        setLocationRelativeTo(owner);

        // annoncer l'ouverture
        EventQueue.invokeLater(() -> {
            addressField.requestFocusInWindow();
            System.out.println("Navigateur ouvert. Entrez une adresse et appuyez sur Entrée. Ctrl+L pour revenir au champ adresse.");
        });

        setVisible(true);
    }

    /** Navigue vers l'URL fournie et extrait les liens. */
    public void navigateTo(String url) {
        if (url == null || url.isBlank()) {
            Toolkit.getDefaultToolkit().beep();
            System.out.println("Aucune adresse fournie.");
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file:")) {
            url = "https://" + url;
        }
        final String finalUrl = url;
        statusLabel.setText("Chargement : " + finalUrl);
        previewArea.setText("");
        extractedLinks.clear();
        updateControls();
        System.out.println("Chargement de la page : " + finalUrl);

        // Utiliser SwingWorker pour le téléchargement et la conversion (non bloquant UI)
        SwingWorker<Void, Void> wk = new SwingWorker<>() {
            String converted = null;
            String title = null;
            String error = null;
            List<String> linksLocal = new ArrayList<>();

            @Override protected Void doInBackground() {
                try {
                    // 1) Utilise HtmlImporter pour la conversion (il récupère le HTML)
                    converted = HtmlImporter.importFromUrl(finalUrl);

                    // 2) Pour extraire titre & liens, on fait une requête Jsoup séparée
                    try {
                        Document doc = Jsoup.connect(finalUrl)
                                .userAgent("blindWriter/accessible-browser")
                                .timeout(15000)
                                .followRedirects(true)
                                .get();
                        title = doc.title();
                        Elements a = doc.select("a[href]");
                        for (Element e : a) {
                            String href = e.absUrl("href");
                            if (href == null || href.isBlank()) href = e.attr("href");
                            if (href != null && !href.isBlank()) linksLocal.add(href);
                        }
                    } catch (Exception ex) {
                        // si l'extraction fail, on continue malgré tout (conversion peut avoir marché)
                    }
                } catch (IOException io) {
                    error = io.getMessage();
                } catch (Throwable t) {
                    error = t.getMessage();
                }
                return null;
            }

            @Override protected void done() {
                if (error != null) {
                    previewArea.setText("Erreur de chargement : " + error);
                    statusLabel.setText("Erreur");
                    System.out.println("Erreur chargement : " + (error == null ? "inconnue" : error));
                  
                } else {
                    previewArea.setText((title != null && !title.isBlank() ? ("#1. " + title + "\n\n") : "") + (converted == null ? "" : converted));
                    previewArea.setCaretPosition(0);
                    statusLabel.setText("Chargé : " + finalUrl);
                    // remplir l'historique et les liens
                    pushHistory(finalUrl);
                    addressField.setText(finalUrl);
                    extractedLinks = linksLocal;
                    String announceMsg = "Page chargée.";
                    if (title != null && !title.isBlank()) announceMsg += " Titre : " + title + ".";
                    announceMsg += " " + extractedLinks.size() + " liens détectés.";
                    System.out.println(announceMsg);
                }
                updateControls();
            }
        };
        wk.execute();
    }

    private void pushHistory(String url) {
        while (history.size() > historyIndex + 1) history.remove(history.size()-1);
        history.add(url);
        historyIndex = history.size() - 1;
    }
    private void goBack() {
        if (historyIndex > 0) {
            historyIndex--;
            String u = history.get(historyIndex);
            addressField.setText(u);
            navigateTo(u);
        } else {
            Toolkit.getDefaultToolkit().beep();
            System.out.println("Aucune page précédente.");
        }
    }
    private void goForward() {
        if (historyIndex < history.size() - 1) {
            historyIndex++;
            String u = history.get(historyIndex);
            addressField.setText(u);
            navigateTo(u);
        } else {
            Toolkit.getDefaultToolkit().beep();
            System.out.println("Aucune page suivante.");
        }
    }
    private void reload() {
        String u = addressField.getText().trim();
        if (!u.isBlank()) navigateTo(u);
    }

    /** Insère le texte converti affiché dans l'éditeur principal (à la fin). */
    private void insertIntoEditor(JTextArea editorPane) {
        String t = previewArea.getText();
        if (t == null || t.isBlank()) {
            Toolkit.getDefaultToolkit().beep();
            System.out.println("Rien à insérer.");
            return;
        }
        try {
            javax.swing.text.Document doc = editorPane.getDocument();
            int pos = doc.getLength();
            doc.insertString(pos, "\n\n" + t, null);
            editorPane.setCaretPosition(pos);
            //writer.blindWriter.setModified(true);
            System.out.println("Page insérée dans le document.");
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("Erreur lors de l'insertion : " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Erreur insertion : " + ex.getMessage());
        }
    }

    private void openLinksDialog() {
        if (extractedLinks == null || extractedLinks.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            System.out.println("Aucun lien détecté sur cette page.");
            return;
        }

        // Dialog listant les liens (numérotés). Permet d'ouvrir le lien sélectionné.
        JDialog dlg = new JDialog(this, "Liens détectés", true);
        DefaultListModel<String> model = new DefaultListModel<>();
        for (int i = 0; i < extractedLinks.size(); i++) {
            model.addElement((i+1) + ". " + extractedLinks.get(i));
        }
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName("Liste des liens");
        list.getAccessibleContext().setAccessibleDescription("Sélectionnez un lien et appuyez sur Entrée pour l'ouvrir dans le navigateur intégré.");

        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(800, 400));
        dlg.getContentPane().add(sp, BorderLayout.CENTER);

        // Bouton ouvrant le lien sélectionné
        JButton openBtn = new JButton("Ouvrir (Entrée)");
        openBtn.addActionListener(e -> {
            int sel = list.getSelectedIndex();
            if (sel >= 0) {
                String url = extractedLinks.get(sel);
                dlg.dispose();
                // ouvrir la page choisie
                navigateTo(url);
            }
        });
        // close button
        JButton cancel = new JButton("Annuler (Esc)");
        cancel.addActionListener(e -> dlg.dispose());

        JPanel p = new JPanel();
        p.add(openBtn);
        p.add(cancel);
        dlg.getContentPane().add(p, BorderLayout.SOUTH);

        // Key binding : Enter ouvre, Esc ferme
        var rp = dlg.getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        rp.getActionMap().put("open", new AbstractAction(){ public void actionPerformed(ActionEvent e){ openBtn.doClick(); }});
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        rp.getActionMap().put("close", new AbstractAction(){ public void actionPerformed(ActionEvent e){ cancel.doClick(); }});

        dlg.pack();
        dlg.setLocationRelativeTo(this);

        // annonce avant affichage
        System.out.println(extractedLinks.size() + " liens listés. Utilisez les flèches et appuyez sur Entrée pour ouvrir.");
        dlg.setVisible(true);
    }

    private void updateControls() {
        backBtn.setEnabled(historyIndex > 0);
        forwardBtn.setEnabled(historyIndex < history.size() - 1);
        reloadBtn.setEnabled(historyIndex >= 0);
        linksBtn.setEnabled(extractedLinks != null && !extractedLinks.isEmpty());
    }

    private void closeDialog() {
    	System.out.println("Navigateur fermé.");
        dispose();
    }
}
