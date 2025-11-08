package writer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

@SuppressWarnings("serial")
public class openSearchDialog extends JDialog {

    // --- État persistant entre ouvertures ---
    public static String searchText = "";
    private static Integer totalCount = 0;
    private boolean userValidated = false;

    // --- UI ---
    private JTextField field;
    private JTextComponent editor;
    private JLabel lblCount;

    // --- Liste de résultats ---
    private javax.swing.DefaultListModel<String> resultModel;
    private javax.swing.JList<String> resultList;
    
    // --- Position initiale du curseur ---
    private int originalCaretPos = -1;
    
    // --- Lecteure sur la barre braille ---
    private JTextArea brailleArea;   
    
    public openSearchDialog(JTextComponent editor) {
        super(SwingUtilities.getWindowAncestor(editor), "Recherche");
        this.editor = editor;

        // --- Mémorise la position initiale du curseur pour Échap
        try { originalCaretPos = editor.getCaretPosition(); } catch (Exception ignored) {}

        setModalityType(ModalityType.MODELESS);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // --- 🔠 Police très lisible pour malvoyants
        Font font = new Font("Segoe UI", Font.PLAIN, 20);
        Font listFont = font.deriveFont(Font.PLAIN, 22);

        // --- Champ texte de recherche
        field = new JTextField(40);
        field.setFont(font);
        field.setBackground(new Color(250, 250, 250));
        field.setCaretColor(Color.BLACK);
        field.setBorder(javax.swing.BorderFactory.createLineBorder(new Color(120, 120, 120)));
        field.getAccessibleContext().setAccessibleName("Zone de recherche");
        field.getAccessibleContext().setAccessibleDescription(
        	    "Appuyez sur Entrée pour lancer la recherche, ou sur espace depuis la liste pour modifier la requête."
        	);
        
        JLabel lab = new JLabel("Rechercher :");
        lab.setFont(font);
        lab.setLabelFor(field);

        JPanel north = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 12));
        north.add(lab);
        north.add(field);

        // --- Liste de résultats
        resultModel = new javax.swing.DefaultListModel<>();
        resultList = new javax.swing.JList<>(resultModel);
        resultList.setFont(listFont);
        resultList.setBackground(new Color(250, 250, 255));
        resultList.setSelectionBackground(new Color(255, 255, 150));
        resultList.setSelectionForeground(Color.BLACK);
        resultList.setVisibleRowCount(10);
        resultList.setFixedCellHeight(36);
        resultList.setBorder(javax.swing.BorderFactory.createTitledBorder("Résultats"));
        resultList.getAccessibleContext().setAccessibleName("Liste des résultats");
        resultList.getAccessibleContext().setAccessibleDescription(
            "Liste des occurrences trouvées. Utilisez flèche haut ou bas pour parcourir. Appuyez sur Entrée pour aller à la position dans le document. Appuyez sur flèche droite pour lire le contenu du résultat.");

        // --- Zone de lecture braille (textuelle, focusable)
        brailleArea = new javax.swing.JTextArea(3, 80);
        brailleArea.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        brailleArea.setLineWrap(true);
        brailleArea.setWrapStyleWord(true);
        brailleArea.setEditable(false);
        brailleArea.setFocusable(true);
        brailleArea.setBackground(new Color(240, 240, 255));
        brailleArea.setForeground(Color.BLACK);
        brailleArea.setBorder(javax.swing.BorderFactory.createTitledBorder("Lecture du texte"));
        brailleArea.getAccessibleContext().setAccessibleName("Zone de lecture du texte du résultat sélectionné");
        brailleArea.getAccessibleContext().setAccessibleDescription(
            "Utilisez flèche gauche et droite pour lire le texte. Flèche haut et bas pour changer de résultat.");

        // --- Navigation clavier entre liste et zone braille
        InputMap rim = resultList.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap ram = resultList.getActionMap();

        // → Passe à la zone braille
        rim.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "toBraille");
        ram.put("toBraille", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int idx = resultList.getSelectedIndex();
                if (idx < 0) return;
                String text = resultList.getModel().getElementAt(idx);
                brailleArea.setText(text);
                brailleArea.setCaretPosition(0);
                brailleArea.requestFocusInWindow();
            }
        });

        // Entrée = aller à l’occurrence dans le document
        rim.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "validateResult");
        ram.put("validateResult", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int idx = resultList.getSelectedIndex();
                if (idx >= 0) {
                    int start = highlightOccurrence(idx);
                    userValidated = true;
                    if (start >= 0) {
                        // Place le curseur devant le mot trouvé
                        editor.setCaretPosition(start);
                        scrollToVisible(editor, start, start + 1);
                    }

                    // Supprime le surlignage avant fermeture
                    SwingUtilities.invokeLater(() ->close());
                }
            }
        });
        
        // Barre d’espace = retour à la zone de saisie pour modifier la recherche
        rim.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "focusSearchField");
        ram.put("focusSearchField", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                field.requestFocusInWindow();
                field.selectAll(); // ✅ permet de retaper directement une nouvelle recherche
            }
        });


        // --- Flèches haut/bas dans la zone braille : reviennent à la liste
        InputMap bim = brailleArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap bam = brailleArea.getActionMap();

        bim.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "backToListUp");
        bim.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "backToListDown");

        bam.put("backToListUp", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int idx = resultList.getSelectedIndex();
                if (idx > 0) {
                    resultList.setSelectedIndex(idx - 1);
                    resultList.requestFocusInWindow();
                }
            }
        });
        bam.put("backToListDown", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int idx = resultList.getSelectedIndex();
                if (idx < resultModel.size() - 1) {
                    resultList.setSelectedIndex(idx + 1);
                    resultList.requestFocusInWindow();
                }
            }
        });

        // --- Quand la sélection change, affiche le texte dans la zone braille
        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String text = resultList.getSelectedValue();
                if (text != null) {
                    brailleArea.setText(text);
                    brailleArea.setCaretPosition(0);
                }
            }
        });

        // --- Compteur
        lblCount = new JLabel("0 occurrence.");
        lblCount.setFont(font.deriveFont(Font.BOLD));
        lblCount.setForeground(new Color(50, 50, 120));
        lblCount.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 12, 6, 12));

        // --- Assemblage de la fenêtre
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(lblCount, BorderLayout.NORTH);
        centerPanel.add(new javax.swing.JScrollPane(resultList), BorderLayout.CENTER);

        getContentPane().setLayout(new BorderLayout(10, 10));
        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(centerPanel, BorderLayout.CENTER);
        getContentPane().add(new javax.swing.JScrollPane(brailleArea), BorderLayout.SOUTH);

        installKeyBindings(this);
        field.addActionListener(e -> launchSearch());

        // --- Taille et affichage
        setSize(1100, 650);
        setLocationRelativeTo(getOwner());
        setAlwaysOnTop(true);
        setResizable(true);
        setVisible(true);
    }

    // --- Raccourcis et navigation clavier cohérents pour NVDA/JAWS ---
    private void installKeyBindings(JDialog d) {
	    JComponent root = (JComponent) d.getContentPane();
	    InputMap im = root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
	    ActionMap am = root.getActionMap();
	
	    // Échap = abandon
	    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
	    am.put("close", new AbstractAction() {
	        @Override public void actionPerformed(ActionEvent e) { close(); }
	    });
	
	    // Entrée dans la liste = aller à l’occurrence
	    resultList.getInputMap(JComponent.WHEN_FOCUSED)
	        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "validateResult");
	    resultList.getActionMap().put("validateResult", new AbstractAction() {
	        @Override public void actionPerformed(ActionEvent e) {
	            int idx = resultList.getSelectedIndex();
	            if (idx >= 0) {
	                int start = highlightOccurrence(idx);  // retourne la position du début
	                userValidated = true;

	                // place le curseur devant le mot trouvé
	                if (start >= 0) {
	                    editor.setCaretPosition(start);
	                    scrollToVisible(editor, start, start + 1);
	                }

	                SwingUtilities.invokeLater(() -> close());
	            }
	            if (resultList.getSelectedIndex() >= 0) {
	                String text = resultList.getModel().getElementAt(resultList.getSelectedIndex());
	                showBrailleSegment(text);
	            }
	        }
	    });
	}


    private void resetState() {
        // Nettoyer les surlignages
        try {
        	editor.getHighlighter().removeAllHighlights();
        } catch (Exception ignore) {}
    }

    private void countOccurrences() {
        totalCount = 0;
        resultModel.clear();
        if (searchText == null || searchText.isBlank()) return;

        String all = getAllText();
        java.util.regex.Pattern p = buildPattern(searchText);
        if (p == null) return;

        java.util.regex.Matcher m = p.matcher(all);

        // 1) collecte des positions
        java.util.List<int[]> hits = new java.util.ArrayList<>();
        while (m.find()) {
            hits.add(new int[] { m.start(), m.end() - m.start() });
        }

        // 2) total connu
        totalCount = hits.size();
        if (totalCount == 0) return;

        // 3) remplir la liste d'affichage avec index 1-based et total fixe
        for (int i = 0; i < hits.size(); i++) {
            int[] h = hits.get(i);
            int start = h[0];
            int len = h[1];
            int displayIndex = i + 1; // 1..N pour affichage (1/113, 2/113, ...)
            addResultToList(all, start, len, displayIndex, totalCount);
        }
    }

    private String getAllText() {
        try {
            Document doc = editor.getDocument();
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return editor.getText();
        }
    }

    @SuppressWarnings("deprecation")
	private void scrollToVisible(JTextComponent area, int start, int end) {
        try {
            java.awt.Rectangle rStart = area.modelToView(start);
            java.awt.Rectangle rEnd   = area.modelToView(Math.max(start, end-1));
            if (rStart != null && rEnd != null) {
                java.awt.Rectangle r = rStart.union(rEnd);
                area.scrollRectToVisible(r);
            }
        } catch (BadLocationException ignore) {}
    }

    private void close() {
        try {
            if (!userValidated && originalCaretPos >= 0) {
                // L’utilisateur a annulé → on restaure
                editor.setCaretPosition(Math.min(originalCaretPos, editor.getDocument().getLength()));
            }

            // Supprimer tous les surlignages résiduels
            try {
                editor.getHighlighter().removeAllHighlights();
            } catch (Exception ignored) {}

            // ✅ Forcer un repaint pour que le fond jaune disparaisse
            editor.repaint();

            // 🧭 Redonner le focus proprement
            editor.requestFocusInWindow();
        } catch (Exception ignored) {}

        // Ferme la boîte de recherche
        dispose();
    }

    

    private String makeContextSnippet(String text, int start, int len) {
        if (text == null || text.isEmpty() || start < 0 || start >= text.length()) return "";

        // bornes du paragraphe
        int beforeNLn = text.lastIndexOf('\n', Math.max(0, start - 1));
        int beforeNLr = text.lastIndexOf('\r', Math.max(0, start - 1));
        int paraStart = Math.max(beforeNLn, beforeNLr) + 1; // -1 -> 0

        int afterNLn = text.indexOf('\n', start + len);
        int afterNLr = text.indexOf('\r', start + len);
        int paraEnd = text.length();
        if (afterNLn != -1) paraEnd = Math.min(paraEnd, afterNLn);
        if (afterNLr != -1) paraEnd = Math.min(paraEnd, afterNLr);

        // fenêtre compacte autour du match (sans trimming, on garde la ponctuation/espaces d’origine)
        final int LEFT = 60, RIGHT = 80;
        int left  = Math.max(paraStart, start - LEFT);
        int right = Math.min(paraEnd,   start + len + RIGHT);

        boolean ellLeft  = left  > paraStart;
        boolean ellRight = right < paraEnd;

        String window = text.substring(left, right);        
        int relStart = start - left;
        int relEnd   = relStart + len;

        StringBuilder sb = new StringBuilder();
        if (ellLeft) sb.append("…");
        // insérer les crochets SANS ajouter d'espaces
        sb.append(window, 0, relStart)
          .append('[')
          .append(window, relStart, relEnd)
          .append(']')
          .append(window.substring(relEnd));
        if (ellRight) sb.append("…");

        return sb.toString();
    }

 // Conversion de la requête utilisateur vers Pattern regex.
 // Règle : si la requête commence par "==", on active le mode sensible à la casse.
 // Si la requête contient '?', on force le motif à correspondre à un "mot" isolé
 // (on n'acceptera pas de correspondance à l'intérieur d'un mot plus long).
    private java.util.regex.Pattern buildPattern(String q) {
        if (q == null || q.isBlank()) return null;

        // garder l'original pour détecter leading/trailing '*'
        String orig = q;

        boolean exact = false;
        if (q.startsWith("==")) {
            exact = true;
            q = q.substring(2);
            orig = orig.substring(2); // garder cohérence pour detection '*' si user a mis ==*...
            if (q.isEmpty()) return null; // "==" seul -> rien
        }

        // === Modes "contient" ===
        String trimmed = q.stripLeading();

        // 1️⃣ Recherche dans tout le texte (paragraphes + titres)
        if (trimmed.startsWith("&&")) {
            String inner = java.util.regex.Pattern.quote(trimmed.substring(2).trim());
            int flags = java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE;
            String rx = "(?m)^(?:\\s*\\u283F\\s*)?.*" + inner + ".*$";
            return java.util.regex.Pattern.compile(rx, flags);
        }

        // 2️⃣ Recherche uniquement dans les titres (lignes commençant par #)
		//      Compatible avec un éventuel préfixe braille "⠿" + espaces.
		  if (trimmed.startsWith("##")) {
		      String inner = java.util.regex.Pattern.quote(trimmed.substring(2).trim());
		      int flags = java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE;
		
		      // ⟶ (?m)  multilignes
		      // ⟶ ^(?:\s*\u283F\s*)?   préfixe braille optionnel (avec espaces)
		      // ⟶ \#{1,} \s*           un ou plusieurs dièses (Markdown), puis espaces
		      // ⟶ .*inner.*$           on veut "inner" quelque part dans le titre
		      String rx = "(?m)^(?:\\s*\\u283F\\s*)?#{1,}\\s*.*" + inner + ".*$";
		      return java.util.regex.Pattern.compile(rx, flags);
		  }

       
        // Classe de "caractères de mot" utilisée pour jokers et pour les lookarounds :
        final String wordCharClass = "[\\p{L}\\p{N}'’_-]";

        // détection d'étoile en début/fin (dans l'original après retrait éventuel de "==")
        boolean origStartsWithStar = orig.startsWith("*");
        boolean origEndsWithStar = orig.endsWith("*");

        // si la requête contient '?', on force l'ancrage des deux côtés (mot de longueur définie)
        boolean containsQuestion = q.indexOf('?') >= 0;

        StringBuilder rx = new StringBuilder(q.length() * 3);
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            
            // 🔢 Détection du joker %d (nombre de 1 à 6 chiffres)
            if (c == '%' && i + 1 < q.length() && q.charAt(i + 1) == 'd') {
                rx.append("[0-9]{1}");
                i++; // saute le 'd'
                continue;
            }

            switch (c) {
                case '*':
                    // zéro ou plusieurs caractères de mot
                    rx.append(wordCharClass).append("*");
                    break;
                case '?':
                    // exactement un caractère de mot
                    rx.append(wordCharClass);
                    break;
                default:
                    // échappe les caractères spéciaux regex
                    if ("\\.^$|()[]{}+*?".indexOf(c) >= 0) rx.append('\\');
                    rx.append(c);
            }
        }


        String core = rx.toString();

        // Déterminer l'ancrage à gauche/droite selon la présence de '*' en bord
        boolean anchorLeft  = containsQuestion || !origStartsWithStar || exact;
        boolean anchorRight = containsQuestion || !origEndsWithStar   || exact;

        // Construire la regex finale avec lookarounds si nécessaire
        StringBuilder finalRx = new StringBuilder();
        if (anchorLeft)  finalRx.append("(?<!").append(wordCharClass).append(")");
        finalRx.append("(?:").append(core).append(")");
        if (anchorRight) finalRx.append("(?!").append(wordCharClass).append(")");

        int flags = exact ? 0 : (java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
        try {
            return java.util.regex.Pattern.compile(finalRx.toString(), flags);
        } catch (java.util.regex.PatternSyntaxException e) {
            return null;
        }
    }




    /**
     * Retourne le "mot" complet entourant la zone [start,end) en étendant
     * à gauche/droite jusqu'aux caractères de mot définis (lettres, chiffres,
     * apostrophes, underscore et tiret). Si le segment récupéré contient
     * plusieurs mots (ex. "pré **mémoire**."), on renvoie la première séquence
     * de caractères de mot trouvée.
     */
    private String extractMatchedWord(String text, int start, int end) {
        if (text == null || text.isEmpty() || start < 0 || end <= start) return "";

        int n = text.length();
        // étendre à gauche
        int l = start;
        while (l > 0 && isWordChar(text.charAt(l - 1))) l--;
        // étendre à droite
        int r = end;
        while (r < n && isWordChar(text.charAt(r))) r++;

        if (l >= r) return "";

        String candidate = text.substring(l, r).trim();
        if (candidate.isEmpty()) return "";

        // si candidate contient des choses bizarres (balises, ponctuations),
        // retrouver la première "séquence mot" à l'intérieur
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\p{L}\\p{N}'’_-]+").matcher(candidate);
        if (m.find()) return m.group();
        return candidate; // fallback
    }

    /** Détermine si un caractère fait partie d'un mot (lettre/chiffre/'/_/-) */
    private boolean isWordChar(char c) {
        if (Character.isLetterOrDigit(c)) return true;
        if (c == '\'' || c == '’' || c == '_' || c == '-') return true;
        return false;
    }

    private void addResultToList(String all, int start, int len, int idx, int total) {
        String snippet = makeContextSnippet(all, start, len);
        String matchedWord = extractMatchedWord(all, start, start + len);
        String label = (matchedWord == null || matchedWord.isBlank())
            ? (idx + " / " + total + "  " + snippet)
            : (matchedWord + " (" + idx + "/" + total + ")  " + snippet);
        resultModel.addElement(label);
    }

    private int highlightOccurrence(int index) {
        if (index < 0) return -1;

        String all = getAllText();
        String query = field.getText();
        java.util.regex.Pattern p = buildPattern(query);
        if (p == null) return -1;

        boolean lineMode = isLineModeQuery(query);
        java.util.regex.Matcher m = p.matcher(all);
        int i = 0;
        while (m.find()) {
            if (i == index) {
                int start = m.start();
                int end   = m.end();
                int caretStart = lineMode ? adjustCaretToLineContent(all, start, end) : start;
                try {
                    editor.requestFocusInWindow();
                    editor.getHighlighter().removeAllHighlights();
                    // Surbrillance : on garde la ligne entière (start..end)
                    editor.getHighlighter().addHighlight(start, end,
                        new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW));
                    // Sélection visible (optionnel) : idem
                    editor.select(start, end);
                    scrollToVisible(editor, start, end);
                    return caretStart; // ← on renvoie le début "visuel"
                } catch (Exception ignored) {}
                break;
            }
            i++;
        }
        return -1;
    }

    private void launchSearch() {
        resultModel.clear();
        searchText = field.getText();
        resetState();
        countOccurrences();

        if (totalCount == 0) {
            brailleArea.setText("Aucune occurrence trouvée.");
            brailleArea.requestFocusInWindow();
            brailleArea.getCaret().setVisible(true);
            lblCount.setText("0 occurrence.");
        } else {
            lblCount.setText(totalCount + " occurrence" + (totalCount > 1 ? "s" : "") + ".");
            resultList.setSelectedIndex(0);
            resultList.requestFocusInWindow(); // place le focus sur la liste
            brailleArea.getCaret().setVisible(true);
            highlightOccurrence(0); // surligne la première
        }
        if (resultList.getSelectedIndex() >= 0) {
            String text = resultList.getModel().getElementAt(resultList.getSelectedIndex());
            showBrailleSegment(text);
        }
        // Réactive le focus et les raccourcis clavier de la liste après une nouvelle recherche
        SwingUtilities.invokeLater(() -> {
            resultList.requestFocusInWindow();

            // Redonne la touche Entrée à la liste
            InputMap rim = resultList.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap ram = resultList.getActionMap();

            rim.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "validateResult");
            ram.put("validateResult", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    int idx = resultList.getSelectedIndex();
                    if (idx >= 0) {
                        int start = highlightOccurrence(idx);
                        userValidated = true;
                        if (start >= 0) {
                            editor.setCaretPosition(start);
                            scrollToVisible(editor, start, start + 1);
                        }
                        SwingUtilities.invokeLater(() -> close());
                    }
                }
            });
        });
    }


    /** Affiche la portion de texte lisible sur la barre braille */
    /** Affiche le texte sélectionné dans la zone de lecture braille */
    private void showBrailleSegment(String text) {
        if (text == null) text = "";
        brailleArea.setText(text);
        brailleArea.setCaretPosition(0); // le curseur au début pour NVDA/braille
        brailleArea.requestFocusInWindow(); // donne le focus pour la lecture
        brailleArea.getCaret().setVisible(true);
    }

    private static boolean isLineModeQuery(String q) {
        if (q == null) return false;
        String t = q.stripLeading();
        return t.startsWith("##") || t.startsWith("&&");
    }

    private static int adjustCaretToLineContent(String all, int start, int end) {
        int i = Math.max(0, start);
        int n = Math.min(all.length(), end);

        // Sauter espaces/tabs en tête de ligne
        while (i < n) {
            char c = all.charAt(i);
            if (c == ' ' || c == '\t') { i++; continue; }
            break;
        }
        // Sauter préfixe braille ⠿ + espaces
        if (i < n && all.charAt(i) == '\u283F') {
            i++;
            while (i < n && (all.charAt(i) == ' ' || all.charAt(i) == '\t')) i++;
        }
        // Si titre : sauter les dièses successifs + espaces
        int j = i;
        while (j < n && all.charAt(j) == '#') j++;
        if (j > i) {
            i = j;
            while (i < n && (all.charAt(i) == ' ' || all.charAt(i) == '\t')) i++;
        }
        return i;
    }


    
}
