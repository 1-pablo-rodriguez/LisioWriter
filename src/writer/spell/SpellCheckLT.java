package writer.spell;

import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.LayeredHighlighter;

import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.UserConfig;
import org.languagetool.rules.RuleMatch;
import org.languagetool.rules.spelling.hunspell.HunspellRule;

import writer.commandes;

/**
 * Vérificateur orthographique utilisant LanguageTool + Hunspell.
 * Version : sans marqueurs "°°" — uniquement des highlights & navigation par match.
 */
public final class SpellCheckLT {
    private final writer.ui.NormalizingTextPane area;
    private final Timer debounce = new Timer(300, e -> checkNow());
    private final Highlighter highlighter;
    private final LayeredHighlighter.LayerPainter painter = new RedSquigglePainter();
    private final List<RuleMatch> matches = new ArrayList<>();
    // lazy-holder : createLT() ne sera invoqué qu'au premier accès à ToolHolder.TOOL
 // lazy-holder : createLT() n'est invoqué qu'au premier accès à ToolHolder.TOOL
    private static class ToolHolder {
        static final org.languagetool.JLanguageTool TOOL = createLT();
    }
    private static HunspellRule HUNSPELL_RULE;
    private static final java.util.Set<String> HUNSPELL_WHITELIST =
            new java.util.HashSet<>(java.util.Arrays.asList("a", "à", "y", "le", "la", "de", "l'", "d'"));

    private volatile boolean realtime = true;
    private int navStart = 0;
    private int navEnd   = Integer.MAX_VALUE;

    // ================= DICO UTILISATEUR =================
    private static final java.util.Set<String> USER_DICTIONARY = new java.util.HashSet<>();
    private static java.nio.file.Path userDictPath =
        java.nio.file.Paths.get(System.getProperty("user.home"), ".LisioWriter", "user-dictionary.txt");

    static { loadUserDictionaryFromDisk(); }

    public static void setUserDictionaryPath(java.nio.file.Path p) { if (p != null) userDictPath = p; loadUserDictionaryFromDisk(); }
    public static synchronized void addWordToUserDictionary(String word) { String w = norm(word); if (w.isEmpty()) return; if (USER_DICTIONARY.add(w)) { if (!commandes.listMotsDico.contains(w)) commandes.listMotsDico.add(w); saveUserDictionaryToDisk(); } }
    public static synchronized void removeWordFromUserDictionary(String word) { String w = norm(word); if (USER_DICTIONARY.remove(w)) { commandes.listMotsDico.remove(w); saveUserDictionaryToDisk(); } }
    public static synchronized void clearUserDictionary() { USER_DICTIONARY.clear(); commandes.listMotsDico.clear(); saveUserDictionaryToDisk(); }

    private static synchronized void loadUserDictionaryFromDisk() {
      try {
        USER_DICTIONARY.clear(); commandes.listMotsDico.clear();
        java.nio.file.Files.createDirectories(userDictPath.getParent());
        if (java.nio.file.Files.exists(userDictPath)) {
          for (String line : java.nio.file.Files.readAllLines(userDictPath)) {
            String w = norm(line);
            if (!w.isEmpty()) { USER_DICTIONARY.add(w); commandes.listMotsDico.add(w); }
          }
        }
      } catch (Exception ignore) {}
    }
    private static synchronized void saveUserDictionaryToDisk() {
      try {
        java.nio.file.Files.createDirectories(userDictPath.getParent());
        java.nio.file.Files.write(userDictPath, commandes.listMotsDico, java.nio.charset.StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
      } catch (Exception ignore) {}
    }
    private static String norm(String s) { if (s == null) return ""; return s.strip().toLowerCase(java.util.Locale.ROOT); }
    private static boolean shouldIgnoreToken(String token) {
      if (token == null) return true;
      String t = token.toLowerCase(java.util.Locale.ROOT);
      if (t.length() < 2) return true;
      if (HUNSPELL_WHITELIST.contains(t)) return true;
      if (USER_DICTIONARY.contains(t)) return true;
      return false;
    }

    // ========== CONSTRUCTEUR & ATTACH ==========
    @SuppressWarnings("serial")
	private SpellCheckLT(writer.ui.NormalizingTextPane area) {
        this.area = area;
        this.highlighter = area.getHighlighter();
        debounce.setRepeats(false);

        area.getDocument().addDocumentListener(new DocumentListener() {
        	@Override public void insertUpdate(DocumentEvent e){ if (realtime) debounce.restart(); }
        	@Override public void removeUpdate(DocumentEvent e){ if (realtime) debounce.restart(); }
        	@Override public void changedUpdate(DocumentEvent e){ if (realtime) debounce.restart(); }
        });

        area.getInputMap().put(KeyStroke.getKeyStroke("shift F10"), "spellPopup");
        area.getActionMap().put("spellPopup", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showPopupAtCaret(); }
        });

        area.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e){ maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e){ maybePopup(e); }
            @SuppressWarnings("deprecation")
			private void maybePopup(MouseEvent e){
                if (e.isPopupTrigger()) showPopup(e.getX(), e.getY(), area.viewToModel(e.getPoint()));
            }
        });

        // bind the key (garde ceci !)
        area.getInputMap().put(KeyStroke.getKeyStroke("F7"), "errNextMarker");
        area.getInputMap().put(KeyStroke.getKeyStroke("shift F7"), "errPrevMarker");

        // action: navigation immédiate + rafraîchissement asynchrone
        area.getActionMap().put("errNextMarker", new AbstractAction() {
          @Override public void actionPerformed(ActionEvent e) {
            // 1) navigation immédiate sur l'état courant
            boolean moved = performGotoNextMatch(); // voir ci-dessous : renvoyer boolean

            // 2) rafraîchissement asynchrone (ne bloque pas la navigation)
            checkDocumentNowAsync(null);

            // 3) si on n'a rien trouvé, faire un scan local rapide (paragraphe / sélection)
            if (!moved) {
              checkSelectionOrParagraphNowAsync(() -> SwingUtilities.invokeLater(() -> performGotoNextMatch()));
            }
          }
        });

        // même principe pour précédent (Shift+F7)
        area.getActionMap().put("errPrevMarker", new AbstractAction() {
          @Override public void actionPerformed(ActionEvent e) {
            boolean moved = performGotoPrevMatch();
            checkDocumentNowAsync(null);
            if (!moved) {
              checkSelectionOrParagraphNowAsync(() -> SwingUtilities.invokeLater(() -> performGotoPrevMatch()));
            }
          }
        });



        // suggestions clavier
        area.getInputMap().put(KeyStroke.getKeyStroke("alt ENTER"), "spellSuggest");
        area.getActionMap().put("spellSuggest", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showSuggestionsAtCaret(); }
        });

        for (int n = 1; n <= 9; n++) {
          final int num = n;
          area.getInputMap().put(KeyStroke.getKeyStroke("ctrl alt " + n), "spellApply" + n);
          area.getActionMap().put("spellApply" + n, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
              RuleMatch hit = findHunspellHitAt(area.getCaretPosition());
              if (hit == null) return;
              java.util.List<String> s = hit.getSuggestedReplacements();
              if (s != null && s.size() >= num) replace(hit, s.get(num - 1));
            }
          });
        }

        checkNow();
    }

    public static SpellCheckLT attach(writer.ui.NormalizingTextPane area) {
        if (!(area.getHighlighter() instanceof LayeredHighlighter)) {
            area.setHighlighter(new DefaultHighlighter());
        }
        return new SpellCheckLT(area);
    }

    // ========== CLEAR (sans marqueurs) ==========
    private void clear() {
        // 1) enlever highlights de notre painter
        for (Highlighter.Highlight h : highlighter.getHighlights()) {
            if (h.getPainter() == painter) {
                highlighter.removeHighlight(h);
            }
        }
        // 2) vider matches
        matches.clear();
        // 3) réinitialiser fenêtre de navigation
        navStart = 0;
        try { navEnd = area.getDocument().getLength(); } catch (Exception ex) { navEnd = Integer.MAX_VALUE; }
    }

    // ========== CHECK NOW (plein doc) ==========
    private void checkNow() {
      EventQueue.invokeLater(() -> {
        try {
          clear();
          Document doc = area.getDocument();
          String full = doc.getText(0, doc.getLength());
          if (full.isEmpty()) return;
          navStart = 0; navEnd = full.length();

          // enlever markup inline et construire mapping clean->orig
          InlineMarkupFilter.Result filtered = InlineMarkupFilter.strip(full);
          String clean = filtered.cleaned;

          List<RuleMatch> found = ToolHolder.TOOL.check(clean);

          // projeter les offsets vers le document original et ajouter highlights
          for (RuleMatch m : found) {
            if ("TIRET".equals(m.getRule().getId())) continue;
            if ("WRONG_ETRE_VPPA".equals(m.getRule().getId())) continue;

            int fC = Math.max(0, Math.min(m.getFromPos(), clean.length()));
            int tC = Math.max(fC, Math.min(m.getToPos(), clean.length()));
            if (tC <= fC) continue;

            int fOrig = filtered.mapStart(fC);
            int tOrig = filtered.mapEnd(tC);
            if (tOrig <= fOrig) continue;

            String token = full.substring(fOrig, tOrig);
            if (shouldIgnoreToken(token)) continue;

            RuleMatch nm = new RuleMatch(m.getRule(), m.getSentence(), fOrig, tOrig, m.getMessage(), m.getShortMessage());
            if (m.getSuggestedReplacements() != null) nm.setSuggestedReplacements(m.getSuggestedReplacements());
            if (m.getUrl() != null) nm.setUrl(m.getUrl());
            matches.add(nm);
            ((LayeredHighlighter) highlighter).addHighlight(fOrig, tOrig, painter);
          }

          matches.sort(java.util.Comparator.comparingInt(RuleMatch::getFromPos));
        } catch (Exception ignore) {}
      });
    }

    // ========== RESCAN WINDOW ========== (réanalyse d'une fenêtre sans marqueurs)
    private void rescanWindow(int start, int end) {
      try {
        Document doc = area.getDocument();
        int N = doc.getLength();
        start = Math.max(0, Math.min(start, N));
        end   = Math.max(start, Math.min(end,   N));
        final int winStart = start;
        final int winEnd = end;

        // 1) enlever highlights qui chevauchent la fenêtre
        for (Highlighter.Highlight h : highlighter.getHighlights()) {
          if (h.getPainter() == painter) {
            int hs = h.getStartOffset(), he = h.getEndOffset();
            if (hs < end && he > start) highlighter.removeHighlight(h);
          }
        }

        // 2) retirer des matches ceux qui chevauchent la fenêtre (on les recalculera)
        matches.removeIf(m -> m.getFromPos() < winEnd && m.getToPos() > winStart);

        // 3) préparer slice, nettoyer markup et checker
        String slice = doc.getText(start, end - start);
        InlineMarkupFilter.Result filtered = InlineMarkupFilter.strip(slice);
        String clean = filtered.cleaned;
        List<RuleMatch> found = ToolHolder.TOOL.check(clean);

        // 4) projeter et ajouter highlights + matches
        for (RuleMatch m : found) {
          int fC = Math.max(0, Math.min(m.getFromPos(), clean.length()));
          int tC = Math.max(fC, Math.min(m.getToPos(), clean.length()));
          if (tC <= fC) continue;

          int fOrig = start + filtered.mapStart(fC);
          int tOrig = start + filtered.mapEnd(tC);
          if (tOrig <= fOrig) continue;

          String token = doc.getText(fOrig, tOrig - fOrig);
          if (shouldIgnoreToken(token)) continue;

          RuleMatch nm = new RuleMatch(m.getRule(), m.getSentence(), fOrig, tOrig, m.getMessage(), m.getShortMessage());
          if (m.getSuggestedReplacements() != null) nm.setSuggestedReplacements(m.getSuggestedReplacements());
          if (m.getUrl() != null) nm.setUrl(m.getUrl());
          matches.add(nm);
          ((LayeredHighlighter) highlighter).addHighlight(fOrig, tOrig, painter);
        }
        matches.sort(java.util.Comparator.comparingInt(RuleMatch::getFromPos));
      } catch (Exception ignore) {}
    }

    private void focusMatch(RuleMatch m, boolean announce) {
        if (m == null) return;
        int from = Math.max(0, Math.min(m.getFromPos(), area.getDocument().getLength()));
        int to   = Math.max(from, Math.min(m.getToPos(),   area.getDocument().getLength()));
        area.requestFocusInWindow();
        area.select(from, to);
        area.getCaret().setDot(from);
        area.getCaret().moveDot(to);
        if (announce) {
            String msg = "Faute détectée. De " + from + " à " + to +
                         ". " + (m.getShortMessage() != null ? m.getShortMessage() : "Consultez les suggestions.");
            area.getAccessibleContext().setAccessibleDescription(msg);
        }
    }

    // ========== SUGGESTIONS / POPUP ==========
    private RuleMatch findHunspellHitAt(int pos) {
      for (RuleMatch m : matches) {
        if (pos >= m.getFromPos() && pos <= m.getToPos() && m.getRule() instanceof HunspellRule) return m;
      }
      return null;
    }
    private RuleMatch findAnyHitAt(int pos) {
      for (RuleMatch m : matches) if (pos >= m.getFromPos() && pos <= m.getToPos()) return m;
      return null;
    }

    @SuppressWarnings("deprecation")
	public void showPopupAtCaret() {
        try {
            Rectangle r = area.modelToView(area.getCaretPosition());
            if (r != null) showPopup(r.x, r.y + r.height, area.getCaretPosition());
        } catch (BadLocationException ignore) {}
    }

    private void showPopup(int x, int y, int pos) {
      // find match under caret (priority Hunspell)
      RuleMatch hit = findHunspellHitAt(pos);
      if (hit == null) hit = findAnyHitAt(pos);

      JPopupMenu menu = new JPopupMenu();
      try {
        if (hit != null && hit.getRule() instanceof org.languagetool.rules.spelling.hunspell.HunspellRule) {
          java.util.List<String> sugg = hit.getSuggestedReplacements();
          if (sugg == null || sugg.isEmpty()) {
            JMenuItem none = new JMenuItem("Aucune suggestion (Hunspell)"); none.setEnabled(false); menu.add(none);
          } else {
            for (String rep : sugg) {
              JMenuItem it = new JMenuItem(rep);
              RuleMatch hm = hit;
              it.addActionListener(evt -> replace(hm, rep));
              menu.add(it);
            }
          }
        } else if (hit != null) {
          String label = hit.getShortMessage() != null ? hit.getShortMessage() : hit.getRule().getDescription();
          JMenuItem header = new JMenuItem(label); header.setEnabled(false); menu.add(header); menu.addSeparator();
          java.util.List<String> sugg = hit.getSuggestedReplacements();
          if (sugg == null || sugg.isEmpty()) { JMenuItem none = new JMenuItem("Aucune suggestion"); none.setEnabled(false); menu.add(none); }
          else { for (String rep : sugg) { JMenuItem it = new JMenuItem(rep); RuleMatch hm = hit; it.addActionListener(evt -> replace(hm, rep)); menu.add(it); } }
        } else {
          // fallback : compute word bounds & hunspell suggestions
          final Document doc = area.getDocument();
          String full = doc.getText(0, doc.getLength());
          int[] wb = wordBounds(full, pos);
          final int startPos = wb[0], endPos = wb[1];
          String word = full.substring(startPos, Math.max(startPos, endPos));
          if (word.length() < 2) { JMenuItem none = new JMenuItem("Aucune suggestion (Hunspell)"); none.setEnabled(false); menu.add(none); }
          else {
            java.util.List<String> sugg = hunspellSuggest(ToolHolder.TOOL, word);
            if (sugg == null || sugg.isEmpty()) { JMenuItem none = new JMenuItem("Aucune suggestion (Hunspell)"); none.setEnabled(false); menu.add(none); }
            else {
              for (String rep : sugg) {
                JMenuItem it = new JMenuItem(rep);
                it.addActionListener(evt -> {
                  try {
                    doc.remove(startPos, endPos - startPos);
                    doc.insertString(startPos, rep, null);
                    int ps = javax.swing.text.Utilities.getRowStart(area, startPos);
                    int pe = javax.swing.text.Utilities.getRowEnd(area, startPos + rep.length());
                    rescanWindow(ps, pe);
                    navStart = 0; navEnd = doc.getLength();
                  } catch (BadLocationException ignore) {}
                });
                menu.add(it);
              }
            }
          }
        }
      } catch (Exception ignore) {
        JMenuItem none = new JMenuItem("Aucune suggestion"); none.setEnabled(false); menu.add(none);
      }

      // Dictionnaire utilisateur
      menu.addSeparator();
      final Document docRef = area.getDocument();
      final String fullText;
      try { fullText = docRef.getText(0, docRef.getLength()); } catch (BadLocationException ex) { menu.show(area, x, y); return; }
      int fromSel, toSel; String tokenUnderCaret;
      if (hit != null) { fromSel = Math.max(0, Math.min(hit.getFromPos(), fullText.length())); toSel = Math.max(fromSel, Math.min(hit.getToPos(), fullText.length())); tokenUnderCaret = fullText.substring(fromSel, toSel); }
      else { int[] wb = wordBounds(fullText, pos); fromSel = wb[0]; toSel = wb[1]; tokenUnderCaret = fullText.substring(fromSel, Math.max(fromSel, toSel)); }

      if (tokenUnderCaret != null && tokenUnderCaret.strip().length() >= 2) {
        JMenuItem addDic = new JMenuItem("Ajouter « " + tokenUnderCaret.strip() + " » au dictionnaire");
        addDic.addActionListener(evt -> {
          addWordToUserDictionary(tokenUnderCaret);
          try {
            int ps = javax.swing.text.Utilities.getRowStart(area, fromSel);
            int pe = javax.swing.text.Utilities.getRowEnd(area, toSel);
            rescanWindow(ps, pe);
            navStart = 0; navEnd = docRef.getLength();
          } catch (BadLocationException ignore) {}
        });
        menu.add(addDic);
      }
      JMenuItem manageDic = new JMenuItem("Gérer le dictionnaire…");
      manageDic.addActionListener(evt -> manageUserDictionaryDialog(area));
      menu.add(manageDic);

      menu.addSeparator();
      JMenuItem ign = new JMenuItem("Ignorer");
      ign.addActionListener(evt -> debounce.restart());
      menu.add(ign);

      menu.show(area, x, y);
    }

    private void replace(RuleMatch m, String replacement) {
    	  try {
    	    Document doc = area.getDocument();
    	    int from = m.getFromPos();
    	    int to   = m.getToPos();

    	    // calculer delta avant modification
    	    int oldLen = Math.max(0, to - from);
    	    int newLen = (replacement == null) ? 0 : replacement.length();
    	    int delta = newLen - oldLen;

    	    // effectuer la modification
    	    doc.remove(from, oldLen);
    	    doc.insertString(from, replacement, null);

    	    // 1) mettre à jour nos matches (décaler ceux après 'to')
    	    if (delta != 0) {
    	      shiftMatchesAfter(to, delta);
    	    } else {
    	      // même si delta==0, on doit supprimer les matches chevauchant la zone modifiée
    	      // rescanWindow fera cela ; on peut laisser tomber ici.
    	    }

    	    // 2) rescanner la ligne / paragraphe impacté pour rafraîchir les matches locaux
    	    int anchor = Math.min(from + Math.max(0, newLen - 1), doc.getLength());
    	    int ps = javax.swing.text.Utilities.getRowStart(area, from);
    	    int pe = javax.swing.text.Utilities.getRowEnd(area, anchor);
    	    rescanWindow(ps, pe);

    	    // 3) élargir la navigation F7 à *tout* le document
    	    navStart = 0;
    	    navEnd   = doc.getLength();

    	    // forcer une recomposition complète
    	    checkDocumentNowAsync(null);
    	    
    	  } catch (BadLocationException ignore) {}
    	}


    // ========== SUGGESTIONS D'API Hunspell ==========
    public static List<String> hunspellSuggest(JLanguageTool lt, String word) throws Exception {
      String text = " " + word + " ";
      List<String> out = new ArrayList<>();
      for (RuleMatch m : lt.check(text)) {
        if (m.getRule() instanceof HunspellRule && m.getFromPos() <= 1 && m.getToPos() >= 1 + word.length()) {
          out.addAll(m.getSuggestedReplacements());
        }
      }
      return out;
    }

    // ==== helpers pour mots ====
    private static boolean isWordChar(char c) { return Character.isLetter(c) || c == '\'' || c == '’' || c == '-'; }
    private static int[] wordBounds(String text, int pos) {
      int n = text.length();
      int s = Math.max(0, Math.min(pos, n));
      int e = s;
      while (s > 0 && isWordChar(text.charAt(s - 1))) s--;
      while (e < n && isWordChar(text.charAt(e)))     e++;
      if (s < e && (text.charAt(s) == '\'' || text.charAt(s) == '’')) {
        int s2 = s - 1;
        while (s2 > 0 && Character.isLetter(text.charAt(s2 - 1))) s2--;
        if (s2 < s) s = s2;
      }
      return new int[]{s, e};
    }

    // ========== find & show suggestions (dialog) ==========
    @SuppressWarnings("deprecation")
    private void showSuggestionsAtCaret() {
      int pos = area.getCaretPosition();
      RuleMatch hit = findHunspellHitAt(pos);
      if (hit == null) hit = findAnyHitAt(pos);
      if (hit == null) { area.getToolkit().beep(); return; }
      java.util.List<String> sugg = hit.getSuggestedReplacements();
      if (sugg == null || sugg.isEmpty()) { area.getToolkit().beep(); return; }
      final RuleMatch rm = hit;
      javax.swing.JDialog d = new javax.swing.JDialog(javax.swing.SwingUtilities.getWindowAncestor(area), "Suggestions", java.awt.Dialog.ModalityType.MODELESS);
      javax.swing.JList<String> list = new javax.swing.JList<>(sugg.toArray(new String[0]));
      list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
      list.setSelectedIndex(0);
      list.getAccessibleContext().setAccessibleName("Suggestions de remplacement");
      list.getAccessibleContext().setAccessibleDescription("Flèches pour choisir, Entrée pour appliquer, Échap pour fermer.");
      list.addKeyListener(new java.awt.event.KeyAdapter() {
        @Override public void keyPressed(java.awt.event.KeyEvent e) {
          if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
            String rep = list.getSelectedValue();
            if (rep != null) replace(rm, rep);
            d.dispose();
          } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) d.dispose();
        }
      });
      list.addMouseListener(new java.awt.event.MouseAdapter() {
        @Override public void mouseClicked(java.awt.event.MouseEvent e) {
          if (e.getClickCount() == 2) {
            String rep = list.getSelectedValue();
            if (rep != null) replace(rm, rep);
            d.dispose();
          }
        }
      });
      d.getContentPane().add(new javax.swing.JScrollPane(list));
      d.pack();
      try {
        java.awt.Rectangle r = area.modelToView(area.getCaretPosition());
        java.awt.Point p = new java.awt.Point(r.x, r.y + r.height);
        javax.swing.SwingUtilities.convertPointToScreen(p, area);
        d.setLocation(p);
      } catch (javax.swing.text.BadLocationException ignore) {}
      d.setVisible(true);
      list.requestFocusInWindow();
    }

    // ========== Dialog dictionnaire utilisateur (identique à ton impl.) ==========
    @SuppressWarnings("serial")
    private static void manageUserDictionaryDialog(java.awt.Component parent) {
      // (je n'ai pas modifié cette partie - tu peux réutiliser ta version)
      javax.swing.JDialog d = new javax.swing.JDialog(javax.swing.SwingUtilities.getWindowAncestor(parent), "Dictionnaire utilisateur", java.awt.Dialog.ModalityType.MODELESS);
      d.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();
      for (String w : commandes.listMotsDico) model.addElement(w);
      javax.swing.JList<String> list = new javax.swing.JList<>(model);
      list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
      list.getAccessibleContext().setAccessibleName("Liste des mots du dictionnaire");
      list.getAccessibleContext().setAccessibleDescription("Utilisez les flèches pour naviguer. Suppr pour supprimer l’élément sélectionné.");
      list.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), "delSelected");
      list.getActionMap().put("delSelected", new javax.swing.AbstractAction() {
        @Override public void actionPerformed(java.awt.event.ActionEvent e) {
          String sel = list.getSelectedValue();
          if (sel != null) { removeWordFromUserDictionary(sel); model.removeElement(sel); } else list.getToolkit().beep();
        }
      });
      javax.swing.JButton del = new javax.swing.JButton("Supprimer"); javax.swing.JButton clear = new javax.swing.JButton("Tout vider"); javax.swing.JButton close = new javax.swing.JButton("Fermer");
      del.setMnemonic('S'); clear.setMnemonic('V'); close.setMnemonic('F');
      del.addActionListener(e -> { String sel = list.getSelectedValue(); if (sel != null) { removeWordFromUserDictionary(sel); model.removeElement(sel); list.requestFocusInWindow(); } else d.getToolkit().beep();});
      clear.addActionListener(e -> { int ok = javax.swing.JOptionPane.showConfirmDialog(d, "Vider tout le dictionnaire ?", "Confirmation", javax.swing.JOptionPane.OK_CANCEL_OPTION); if (ok == javax.swing.JOptionPane.OK_OPTION) { clearUserDictionary(); model.clear(); list.requestFocusInWindow(); } });
      close.addActionListener(e -> d.dispose());
      java.awt.event.FocusListener setDefaultOnFocus = new java.awt.event.FocusAdapter() { @Override public void focusGained(java.awt.event.FocusEvent e) { if (e.getComponent() instanceof javax.swing.JButton b) d.getRootPane().setDefaultButton(b); } };
      del.addFocusListener(setDefaultOnFocus); clear.addFocusListener(setDefaultOnFocus); close.addFocusListener(setDefaultOnFocus);
      d.getRootPane().registerKeyboardAction(ev -> d.dispose(), javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
      javax.swing.JPanel buttons = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT)); buttons.add(del); buttons.add(clear); buttons.add(close);
      d.getContentPane().setLayout(new java.awt.BorderLayout()); d.getContentPane().add(new javax.swing.JScrollPane(list), java.awt.BorderLayout.CENTER); d.getContentPane().add(buttons, java.awt.BorderLayout.SOUTH);
      d.setSize(420, 360); d.setLocationRelativeTo(parent); d.setVisible(true); list.requestFocusInWindow();
    }

    // ========== InlineMarkupFilter (inchangé) ==========
    public static final class InlineMarkupFilter {
        private InlineMarkupFilter() {}
        private static final Set<String> TAGS = new HashSet<>(Arrays.asList(
            "**","__","^^",
            "*^","^*","_*","*_","_^","^_",
            "^\u00A8","\u00A8^","_\u00A8","\u00A8_"
        ));
        public static final class Result {
            public final String cleaned;
            public final int[] c2o;
            Result(String c, int[] map) { cleaned = c; c2o = map; }
            public int mapStart(int cleanStart) { return c2o[Math.max(0, Math.min(cleanStart, c2o.length-1))]; }
            public int mapEnd(int cleanEnd)     { return c2o[Math.max(0, Math.min(cleanEnd,   c2o.length-1))]; }
        }
        public static Result strip(String original) {
            StringBuilder out = new StringBuilder(original.length());
            int[] c2oTmp = new int[original.length() + 1];
            int cleanIdx = 0;
            for (int i = 0; i < original.length(); ) {
                if (i + 1 < original.length()) {
                    String two = original.substring(i, i + 2);
                    if (TAGS.contains(two)) { i += 2; continue; }
                }
                out.append(original.charAt(i));
                c2oTmp[cleanIdx] = i;
                cleanIdx++;
                i++;
            }
            c2oTmp[cleanIdx] = original.length();
            int[] c2o = java.util.Arrays.copyOf(c2oTmp, cleanIdx + 1);
            return new Result(out.toString(), c2o);
        }
    }

    // ========== getters / setters ==========
    public void setRealtime(boolean on) { realtime = on; if (!on) { debounce.stop(); clear(); } }
    public boolean isRealtime() { return realtime; }
    public void clearHighlights() { clear(); }
    public int getMatchesCount() { return matches.size(); }

    // ========== createLT() et create Hunspell ==========
    private static JLanguageTool createLT() {
        try {
            File dicRoot = new File(commandes.pathApp, "dic");
            if (!dicRoot.exists()) dicRoot.mkdirs();
            System.setProperty("languagetool.data.dir", dicRoot.getAbsolutePath());
            Language fr = org.languagetool.Languages.getLanguageForShortCode("fr");
            JLanguageTool lt = new JLanguageTool(fr);
            lt.disableRule("MULTITOKEN_SPELLER_RULE");
            lt.disableRule("MORFOLOGIK_RULE_FR_FR");
            for (org.languagetool.rules.Rule r : lt.getAllRules()) {
                if (r.isDefaultOff() && r.supportsLanguage(fr)) lt.enableRule(r.getId());
            }
            UserConfig uc = new UserConfig();
            HUNSPELL_RULE = new HunspellRule(JLanguageTool.getMessageBundle(), lt.getLanguage(), uc);
            lt.addRule(HUNSPELL_RULE);
            lt.disableRule("TIRET");
            lt.disableRule("WRONG_ETRE_VPPA");
            return lt;
        } catch (Throwable t) {
            t.printStackTrace();
            throw new IllegalStateException("Init LanguageTool FR a échoué", t);
        }
    }
    
    /** 
     * Analyse tout le document en background et met à jour matches/highlights.
     * onDone est appelé sur l'EDT (peut être null).
     */
    public void checkDocumentNowAsync(Runnable onDone) {
        try {
            final Document doc = area.getDocument();
            final String textRaw = doc.getText(0, doc.getLength());
            if (textRaw == null || textRaw.isEmpty()) {
                if (onDone != null) SwingUtilities.invokeLater(onDone);
                return;
            }

            // Préparer la version "clean" et la table de mapping *hors EDT*
            final InlineMarkupFilter.Result filtered = InlineMarkupFilter.strip(textRaw);
            final String clean = filtered.cleaned;

            new javax.swing.SwingWorker<java.util.List<RuleMatch>, Void>() {
                @Override
                protected java.util.List<RuleMatch> doInBackground() throws Exception {
                    // lourd → hors EDT
                    return ToolHolder.TOOL.check(clean);
                }

                @Override
                protected void done() {
                    try {
                        final java.util.List<RuleMatch> found = get();
                        EventQueue.invokeLater(() -> {
                            try {
                                // 1) vider l'état visuel
                                clear();

                                // 2) reprojeter chaque RuleMatch sur le document original et ajouter highlight
                                for (RuleMatch m : found) {
                                    int fC = Math.max(0, Math.min(m.getFromPos(), clean.length()));
                                    int tC = Math.max(fC, Math.min(m.getToPos(), clean.length()));
                                    if (tC <= fC) continue;

                                    int fOrig = filtered.mapStart(fC);
                                    int tOrig = filtered.mapEnd(tC);
                                    if (tOrig <= fOrig) continue;

                                    // filtrage lexical (dictionnaire utilisateur / whitelist)
                                    String token = textRaw.substring(Math.max(0, fOrig), Math.min(textRaw.length(), tOrig));
                                    if (shouldIgnoreToken(token)) continue;

                                    RuleMatch nm = new RuleMatch(m.getRule(), m.getSentence(), fOrig, tOrig, m.getMessage(), m.getShortMessage());
                                    if (m.getSuggestedReplacements() != null) nm.setSuggestedReplacements(m.getSuggestedReplacements());
                                    if (m.getUrl() != null) nm.setUrl(m.getUrl());

                                    matches.add(nm);
                                    try {
                                        ((LayeredHighlighter) highlighter).addHighlight(fOrig, tOrig, painter);
                                    } catch (Exception ignore) { /* si highlight échoue, on continue */ }
                                }

                                // 3) tri et réglages
                                matches.sort(java.util.Comparator.comparingInt(RuleMatch::getFromPos));
                                navStart = 0;
                                navEnd = doc.getLength();

                            } catch (Exception ex) {
                                ex.printStackTrace();
                            } finally {
                                if (onDone != null) onDone.run();
                            }
                        });
                    } catch (Exception ex) {
                        // erreur dans get() ou mapping
                        if (onDone != null) SwingUtilities.invokeLater(onDone);
                    }
                }
            }.execute();

        } catch (Exception ex) {
            if (onDone != null) SwingUtilities.invokeLater(onDone);
        }
    }
    
    /**
     * Décale tous les matches dont le fromPos est >= pos par delta (delta peut être négatif).
     * Reconstruit des RuleMatch propres (RuleMatch ne propose pas de setters publics pour offsets).
     */
    private void shiftMatchesAfter(int pos, int delta) {
        if (delta == 0) return;
        List<RuleMatch> updated = new ArrayList<>(matches.size());
        for (RuleMatch m : matches) {
            // si le match est strictement après la zone modifiée -> le décaler
            if (m.getFromPos() >= pos) {
                int newFrom = m.getFromPos() + delta;
                int newTo   = m.getToPos() + delta;
                // rebuild RuleMatch with new offsets
                RuleMatch nm = new RuleMatch(m.getRule(), m.getSentence(), Math.max(0, newFrom), Math.max(Math.max(0,newFrom), newTo), m.getMessage(), m.getShortMessage());
                if (m.getSuggestedReplacements() != null) nm.setSuggestedReplacements(m.getSuggestedReplacements());
                if (m.getUrl() != null) nm.setUrl(m.getUrl());
                updated.add(nm);
            } else {
                // matches qui sont avant la modification restent tels quels
                updated.add(m);
            }
        }
        matches.clear();
        matches.addAll(updated);
        matches.sort(java.util.Comparator.comparingInt(RuleMatch::getFromPos));
    }
    
    private boolean performGotoNextMatch() {
	  if (matches.isEmpty()) { area.getToolkit().beep(); return false; }
	  int caret = area.getCaretPosition();
	  for (RuleMatch m : matches) {
	    if (m.getFromPos() >= caret && m.getFromPos() < navEnd) { focusMatch(m, true); return true; }
	  }
	  // wrap
	  for (RuleMatch m : matches) {
	    if (m.getFromPos() >= navStart && m.getFromPos() < navEnd) { focusMatch(m, true); return true; }
	  }
	  area.getToolkit().beep();
	  return false;
	}

    private boolean performGotoPrevMatch() {
        if (matches.isEmpty()) { area.getToolkit().beep(); return false; }
        int caret = area.getCaretPosition();

        // Cherche le match le plus proche STRICTEMENT avant le caret :
        for (int i = matches.size() - 1; i >= 0; i--) {
            RuleMatch m = matches.get(i);
            // match entièrement avant le caret et dans la fenêtre de navigation
            if (m.getToPos() < caret && m.getFromPos() >= navStart && m.getFromPos() < navEnd) {
                focusMatch(m, true);
                return true;
            }
        }

        // wrap : aller au dernier match dans la plage de navigation
        for (int i = matches.size() - 1; i >= 0; i--) {
            RuleMatch m = matches.get(i);
            if (m.getFromPos() >= navStart && m.getFromPos() < navEnd) {
                focusMatch(m, true);
                return true;
            }
        }

        area.getToolkit().beep();
        return false;
    }



    /** 
     * Analyse la sélection (ou le paragraphe courant si pas de sélection) hors-EDT,
     * met à jour matches + highlights pour cette fenêtre, puis appelle onDone (sur EDT).
     */
    public void checkSelectionOrParagraphNowAsync(Runnable onDone) {
        try {
            final javax.swing.text.Document doc = area.getDocument();

            // 1) Déterminer la fenêtre : sélection ou paragraphe courant
            int start = area.getSelectionStart();
            int end   = area.getSelectionEnd();
            if (start == end) {
                start = javax.swing.text.Utilities.getRowStart(area, area.getCaretPosition());
                end   = javax.swing.text.Utilities.getRowEnd(area,   area.getCaretPosition());
            }
            final int s0 = Math.max(0, Math.min(start, doc.getLength()));
            final int e0 = Math.max(s0, Math.min(end, doc.getLength()));
            if (e0 <= s0) { if (onDone != null) SwingUtilities.invokeLater(onDone); return; }

            final String sliceRaw = doc.getText(s0, e0 - s0);
            final InlineMarkupFilter.Result filtered = InlineMarkupFilter.strip(sliceRaw);
            final String sliceClean = filtered.cleaned;

            new javax.swing.SwingWorker<java.util.List<RuleMatch>, Void>() {
                @Override
                protected java.util.List<RuleMatch> doInBackground() throws Exception {
                    // lourd → hors EDT
                    return ToolHolder.TOOL.check(sliceClean);
                }

                @Override
                protected void done() {
                    try {
                        final java.util.List<RuleMatch> found = get();
                        EventQueue.invokeLater(() -> {
                            try {
                                // 1) enlever les highlights qui chevauchent la fenêtre
                                for (Highlighter.Highlight h : highlighter.getHighlights()) {
                                    if (h.getPainter() == painter) {
                                        int hs = h.getStartOffset(), he = h.getEndOffset();
                                        if (hs < e0 && he > s0) highlighter.removeHighlight(h);
                                    }
                                }

                                // 2) retirer des matches ceux qui chevauchent la fenêtre (on les recalculera)
                                matches.removeIf(m -> m.getFromPos() < e0 && m.getToPos() > s0);

                                // 3) projeter les résultats LT (basés sur sliceClean) -> offsets absolus
                                for (RuleMatch m : found) {
                                    int fC = Math.max(0, Math.min(m.getFromPos(), sliceClean.length()));
                                    int tC = Math.max(fC, Math.min(m.getToPos(), sliceClean.length()));
                                    if (tC <= fC) continue;

                                    int fOrig = s0 + filtered.mapStart(fC);
                                    int tOrig = s0 + filtered.mapEnd(tC);
                                    if (tOrig <= fOrig) continue;

                                    // filtrage lexical (dictionnaire utilisateur / whitelist)
                                    String token = "";
                                    try { token = doc.getText(fOrig, tOrig - fOrig); } catch (Exception ignore) {}

                                    if (shouldIgnoreToken(token)) continue;

                                    RuleMatch nm = new RuleMatch(m.getRule(), m.getSentence(), fOrig, tOrig, m.getMessage(), m.getShortMessage());
                                    if (m.getSuggestedReplacements() != null) nm.setSuggestedReplacements(m.getSuggestedReplacements());
                                    if (m.getUrl() != null) nm.setUrl(m.getUrl());

                                    matches.add(nm);
                                    try { ((LayeredHighlighter) highlighter).addHighlight(fOrig, tOrig, painter); } catch (Exception ignore) {}
                                }

                                // 4) trier & limiter la fenêtre de navigation
                                matches.sort(java.util.Comparator.comparingInt(RuleMatch::getFromPos));
                                navStart = s0;
                                navEnd   = e0;

                            } catch (Exception ex) {
                                ex.printStackTrace();
                            } finally {
                                if (onDone != null) onDone.run();
                            }
                        });
                    } catch (Exception ex) {
                        if (onDone != null) SwingUtilities.invokeLater(onDone);
                    }
                }
            }.execute();

        } catch (Exception ex) {
            if (onDone != null) SwingUtilities.invokeLater(onDone);
        }
    }

    /** Force l'initialisation synchrone (utilisé si besoin). */
    public static org.languagetool.JLanguageTool getTool() {
        return ToolHolder.TOOL;
    }

    /** Force l'initialisation sans bloquer l'appelant (lance en background). */
    public static java.util.concurrent.CompletableFuture<Void> preloadInBackground() {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // hashCode() est juste pour forcer l'initialisation de la classe Holder
                ToolHolder.TOOL.hashCode();
            } catch (Throwable t) {
                // log, mais ne laisse pas l'exception remonter
                t.printStackTrace();
            }
        }, java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread th = new Thread(r, "spell-preload");
            th.setDaemon(true);
            return th;
        }));
    }


}
