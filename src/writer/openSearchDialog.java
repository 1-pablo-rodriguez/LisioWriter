package writer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
    private static Integer lastFoundStart = null;
    @SuppressWarnings("unused")
	private static Integer lastFoundEnd   = null;
    private static Boolean lastDirectionUp = null;
    private static Integer totalCount = 0;
    private static Integer currentIndex = 0; // position logique 1..N

    // --- UI ---
    private JTextField field;
    private JLabel status;
    private JButton btnNext, btnPrev;
    private JTextComponent editor;

    public openSearchDialog(JTextComponent editor) {
        super(SwingUtilities.getWindowAncestor(editor), "Recherche");
        this.editor = editor;
        
        setModalityType(ModalityType.MODELESS);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Champ
        field = new JTextField(30);
        field.getAccessibleContext().setAccessibleName("Zone de recherche");
        field.getAccessibleContext().setAccessibleDescription("Tapez le texte à rechercher");
        if (searchText != null && !searchText.isBlank()) {
            field.setText(searchText);
            field.selectAll();
        } else {
            String sel = editor.getSelectedText();
            if (sel != null && !sel.isBlank()) {
                field.setText(sel);
                field.selectAll();
                searchText = sel;
            }
        }

        // Boutons
        btnNext = new JButton("Suivant");
        btnNext.setMnemonic('S'); // Alt+S
        btnNext.getAccessibleContext().setAccessibleDescription("Chercher l’occurrence suivante");

        btnPrev = new JButton("Précédent");
        btnPrev.setMnemonic('H'); // Alt+H (comme Haut)
        btnPrev.getAccessibleContext().setAccessibleDescription("Chercher l’occurrence précédente");

        // Libellé + association pour lecteurs d’écran
        JLabel lab = new JLabel("Rechercher :");
        lab.setLabelFor(field);

        // Zone de statut lisible par lecteurs d’écran
        status = new JLabel("Prêt.");
        status.setForeground(new Color(0, 64, 160));
        status.getAccessibleContext().setAccessibleName("Statut de la recherche");
        status.getAccessibleContext().setAccessibleDescription("Afficher l’état de la recherche, par exemple nombre d’occurrences et la position courante");

        // Layout
        JPanel north = new JPanel();
        north.add(lab);
        north.add(field);
        north.add(btnNext);
        north.add(btnPrev);
        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(status, BorderLayout.SOUTH);

        // Raccourcis clavier accessibles
        installKeyBindings(this);

        // Actions
        btnNext.addActionListener(e -> findNext(false));
        btnPrev.addActionListener(e -> findNext(true));

        // Recompte en live à chaque changement
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e){ onQueryChanged(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e){ onQueryChanged(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e){ onQueryChanged(); }
        });

        // Focus initial
        EventQueue.invokeLater(() -> {
            field.requestFocusInWindow();
            field.selectAll();
            // Premier comptage sur contenu actuel
            onQueryChanged();
        });

        pack();
        setLocationRelativeTo(getOwner());
        setAlwaysOnTop(true);
        setVisible(true);
    }

    // --- Raccourcis et navigation clavier cohérents pour NVDA/JAWS ---
    private void installKeyBindings(JDialog d) {
        JComponent root = (JComponent) d.getContentPane();
        InputMap im = root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        am.put("close", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ close(); }});

        // Entrée = suivant, Shift+Entrée = précédent
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "next");
        am.put("next", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ findNext(false); }});
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "prev");
        am.put("prev", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ findNext(true); }});

        // F3 et Shift+F3 (standard)
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "f3next");
        am.put("f3next", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ findNext(false); }});
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, KeyEvent.SHIFT_DOWN_MASK), "f3prev");
        am.put("f3prev", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ findNext(true); }});

        // ↑/↓ (optionnels, comme tu les avais)
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "arrowPrev");
        am.put("arrowPrev", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ findNext(true); }});
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "arrowNext");
        am.put("arrowNext", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ findNext(false); }});

        // Définir le bouton par défaut (Enter activera “Suivant” depuis la boîte)
        getRootPane().setDefaultButton(btnNext);
    }

    private void onQueryChanged() {
        searchText = field.getText();
        resetState();
        countOccurrences();
        if (totalCount == 0) {
            setStatus("0 occurrence.");
        } else {
            boolean hasWildcard = searchText.indexOf('*') >= 0 || searchText.indexOf('?') >= 0;
            boolean exact = searchText != null && searchText.startsWith("==");
            String note = "";
            if (hasWildcard) note = " (joker actif)";
            if (exact) {
                note = note.isEmpty() ? " (sensible à la casse)" : note + ", sensible à la casse";
            }
            setStatus(totalCount + " occurrence" + (totalCount>1?"s":"") + note + ". Appuyez sur Entrée pour la première.");
        }
    }

    private void resetState() {
        lastFoundStart = null;
        lastFoundEnd   = null;
        lastDirectionUp = null;
        currentIndex = 0;
        // Nettoyer les surlignages
        try {
        	editor.getHighlighter().removeAllHighlights();
        } catch (Exception ignore) {}
    }

    private void countOccurrences() {
        totalCount = 0;
        if (searchText == null || searchText.isBlank()) return;

        String all = getAllText();
        java.util.regex.Pattern p = buildPattern(searchText);
        if (p == null) return;

        java.util.regex.Matcher m = p.matcher(all);
        while (m.find()) totalCount++;
    }


    private String getAllText() {
        try {
            Document doc = editor.getDocument();
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return editor.getText();
        }
    }

    private void findNext(boolean up) {
        String q = field.getText();

        final javax.swing.text.JTextComponent area = editor;
        final String all = getAllText();
        final java.util.regex.Pattern p = buildPattern(q);


        final int caret = area.getCaretPosition();
        int startIdx;
        boolean directionChanged = (lastDirectionUp == null || lastDirectionUp != up);

        if (lastFoundStart == null || directionChanged) {
            startIdx = Math.max(0, Math.min(caret, all.length()));
        } else {
            startIdx = up ? Math.max(0, lastFoundStart - 1)
                          : Math.min(all.length(), lastFoundStart + 1);
        }

        int foundStart = -1, foundEnd = -1;
        java.util.regex.Matcher m = p.matcher(all);

        if (!up) {
            // Vers le bas
            if (m.find(startIdx)) {
                foundStart = m.start(); foundEnd = m.end();
            } else if (m.find(0)) {
                foundStart = m.start(); foundEnd = m.end();
            }
        } else {
            // Vers le haut : on garde le dernier match <= startIdx
            int limit = Math.max(0, Math.min(startIdx, all.length()));
            while (m.find()) {
                if (m.start() <= limit) { foundStart = m.start(); foundEnd = m.end(); }
                else break;
            }
            if (foundStart < 0) {
                // wrap à la fin
                while (m.find()) { foundStart = m.start(); foundEnd = m.end(); }
            }
        }


        // --- mémorisation & index logique k/N ---
        lastDirectionUp = up;
        lastFoundStart = foundStart;
        lastFoundEnd   = foundEnd;

        // calcule k (1..N) en itérant jusqu'à foundStart
        int k = 0;
        m.reset();
        while (m.find()) {
            k++;
            if (m.start() == foundStart && m.end() == foundEnd) break;
        }
        currentIndex = Math.max(1, k);

        // --- sélection + scroll + surlignage ---
        area.requestFocusInWindow();
        area.select(foundStart, foundEnd);
        scrollToVisible(area, foundStart, foundEnd);
        try {
            javax.swing.text.Highlighter hl = area.getHighlighter();
            hl.removeAllHighlights();
            hl.addHighlight(foundStart, foundEnd,
                new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(java.awt.Color.YELLOW));
        } catch (javax.swing.text.BadLocationException ignore) {}

        String msg = currentIndex + " sur " + totalCount + ".";
        setStatus(msg);

        // Extrait le mot complet autour du match pour l'annonce
        String matchedWord = extractMatchedWord(all, foundStart, foundEnd);

        // Si l'extraction a échoué, essaye la sélection dans l'éditeur (fallback)
        if (matchedWord == null || matchedWord.isBlank()) {
            String sel = area.getSelectedText();
            if (sel != null && !sel.isBlank()) {
                // nettoie les balises/astérisques courantes et ponctuation autour
                String clean = sel.replaceAll("[*_\\[\\]()`\"«»]", "");
                // supprime bordures non-mots
                clean = clean.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
                java.util.regex.Matcher wm = java.util.regex.Pattern.compile("[\\p{L}\\p{N}'’_-]+").matcher(clean);
                if (wm.find()) matchedWord = wm.group();
            }
        }

        String spoken;
        if (matchedWord == null || matchedWord.isBlank()) {
            spoken = "Occurrence " + currentIndex + " sur " + totalCount + ".";
        } else {
            spoken = matchedWord + " - " + currentIndex + "/" + totalCount;
        }
        System.out.println(spoken);


        // --- InfoDialog : contexte avec crochets ---
        showSearchContextDialog(all, foundStart, foundEnd - foundStart, currentIndex, totalCount);
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

    private void setStatus(String s) {
        status.setText(s);
        status.getAccessibleContext().setAccessibleDescription(s);
    }



    private void close() {
        // Rendre le focus à l’éditeur
        try {
        	editor.requestFocusInWindow();
        } catch (Throwable ignore) {}
        dispose();
    }
    
    // 1) À placer dans la classe (par ex. sous setStatus/say)
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

        String window = text.substring(left, right);          // contexte exact
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


    private void showSearchContextDialog(String all, int start, int len, int idx, int total) {
        String snippet = makeContextSnippet(all, start, len);

        // Extrait un "mot" lisible autour du match (lettres/chiffres/'/_/-)
        String matchedWord = extractMatchedWord(all, start, start + len);
        String firstLine;
        if (matchedWord == null || matchedWord.isBlank()) {
            firstLine = idx + " / " + total;
        } else {
            firstLine = matchedWord + " - " + idx + " / " + total;
        }

        String msg = firstLine + "\n" + snippet;
        java.awt.Window owner = getOwner() != null ? getOwner() : SwingUtilities.getWindowAncestor(editor);
        try {
            dia.InfoDialog.show(owner, "Recherche", msg);
        } catch (Throwable t) {
            // en secours, on annonce seulement (synthèse)
        	System.out.println(snippet);
        }
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
            switch (c) {
                case '*':
                    // zéro ou plusieurs caractères de mot (ne traverse pas espaces/ponct.)
                    rx.append(wordCharClass).append("*");
                    break;
                case '?':
                    // exactement un caractère de mot
                    rx.append(wordCharClass);
                    break;
                default:
                    // échapper les méta-characters regex (sauf '*' et '?' gérés)
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





    
}
