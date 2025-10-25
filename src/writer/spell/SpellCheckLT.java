package writer.spell;

import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;

import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.UserConfig;
import org.languagetool.rules.RuleMatch;
import org.languagetool.rules.spelling.hunspell.HunspellRule;

import writer.commandes;

public final class SpellCheckLT {
    private final JTextComponent area;                 // JTextArea ou JTextPane
    //JLanguageTool tool = new JLanguageTool(Languages.getLanguageForLocale(Locale.FRENCH));
    private final Timer debounce = new Timer(300, e -> checkNow());
    private final Highlighter highlighter;
    private final LayeredHighlighter.LayerPainter painter = new RedSquigglePainter();
    private final List<RuleMatch> matches = new ArrayList<>();
    private final static JLanguageTool tool = createLT();
    private static HunspellRule HUNSPELL_RULE;
    private static final java.util.Set<String> HUNSPELL_WHITELIST =
            new java.util.HashSet<>(java.util.Arrays.asList("a", "à", "y", "le", "la", "de", "l'", "d'")); // ajoute ce que tu veux
    // --- dans SpellCheckLT ---
    private volatile boolean realtime = true;   // par défaut : ON (tu peux mettre false si tu veux)
    // Fenêtre courante pour la navigation F7 (par défaut : document entier)
    private int navStart = 0;
    private int navEnd   = Integer.MAX_VALUE;

 // ======== MARQUEURS DE FAUTE POUR NON-VOYANTS ========
    private static final String ERR_MARK = "°°";        // le marqueur demandé
    private static final String ERR_OPEN = ERR_MARK;    // avant le mot en faute
    // Si tu veux un marqueur de fin distinct : private static final String ERR_CLOSE = "°°";
    
 // Regex pour repérer tous les marqueurs dans le texte
    private static final java.util.regex.Pattern ERR_ANY = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(ERR_MARK));
    private boolean inMarkerRebuild = false;

    
    // Retire tous les marqueurs (avant d’appeler LanguageTool ou avant export)
    private static String stripErrorMarkers(String s) {
        if (s == null || s.isEmpty()) return s;
        return ERR_ANY.matcher(s).replaceAll("");
    }

    // Insère le marqueur devant un offset (on insère à partir de la fin → pas de décalage)
    private static void insertMarkerAt(javax.swing.text.Document doc, int pos) throws javax.swing.text.BadLocationException {
        pos = Math.max(0, Math.min(pos, doc.getLength()));
        doc.insertString(pos, ERR_OPEN, null);
    }

    // Supprime tous les marqueurs du document
    private static void removeAllMarkersFromDoc(javax.swing.text.Document doc) {
        try {
            String t = doc.getText(0, doc.getLength());
            java.util.regex.Matcher m = ERR_ANY.matcher(t);
            // supprimer depuis la fin
            java.util.List<int[]> spans = new java.util.ArrayList<>();
            while (m.find()) spans.add(new int[]{m.start(), m.end()});
            for (int i = spans.size()-1; i >= 0; i--) {
                int[] sp = spans.get(i);
                doc.remove(sp[0], sp[1]-sp[0]);
            }
        } catch (Exception ignore) {}
    }
    
    // Ne retire que les highlights + l'état interne, mais CONSERVE les "°°"
    private void clearVisualsKeepMarkers() {
      for (Highlighter.Highlight h : highlighter.getHighlights()) {
        if (h.getPainter() == painter) highlighter.removeHighlight(h);
      }
      matches.clear();
    }

    
 // ================= DICO UTILISATEUR (IGNORER DES MOTS) =================
    private static final java.util.Set<String> USER_DICTIONARY = new java.util.HashSet<>();
    private static java.nio.file.Path userDictPath =
        java.nio.file.Paths.get(System.getProperty("user.home"), ".blindWriter", "user-dictionary.txt");

    // normalisation simple : minuscules + trim
    private static String norm(String s) {
      if (s == null) return "";
      return s.strip().toLowerCase(java.util.Locale.ROOT);
    }

    // charge le fichier au chargement de la classe
    static {
      loadUserDictionaryFromDisk();
    }

    public static void setUserDictionaryPath(java.nio.file.Path p) {
      if (p != null) userDictPath = p;
      loadUserDictionaryFromDisk();
    }

    public static synchronized void addWordToUserDictionary(String word) {
      String w = norm(word);
      if (w.isEmpty()) return;
      if (USER_DICTIONARY.add(w)) {
        if (!commandes.listMotsDico.contains(w)) commandes.listMotsDico.add(w);
        saveUserDictionaryToDisk();
      }
    }

    public static synchronized void removeWordFromUserDictionary(String word) {
      String w = norm(word);
      if (USER_DICTIONARY.remove(w)) {
        commandes.listMotsDico.remove(w);
        saveUserDictionaryToDisk();
      }
    }

    public static synchronized void clearUserDictionary() {
      USER_DICTIONARY.clear();
      commandes.listMotsDico.clear();
      saveUserDictionaryToDisk();
    }

    private static synchronized void loadUserDictionaryFromDisk() {
      try {
        USER_DICTIONARY.clear();
        commandes.listMotsDico.clear();
        java.nio.file.Files.createDirectories(userDictPath.getParent());
        if (java.nio.file.Files.exists(userDictPath)) {
          for (String line : java.nio.file.Files.readAllLines(userDictPath)) {
            String w = norm(line);
            if (!w.isEmpty()) {
              USER_DICTIONARY.add(w);
              commandes.listMotsDico.add(w);
            }
          }
        }
      } catch (Exception ignore) {}
    }

    private static synchronized void saveUserDictionaryToDisk() {
      try {
        java.nio.file.Files.createDirectories(userDictPath.getParent());
        java.nio.file.Files.write(
            userDictPath,
            commandes.listMotsDico,
            java.nio.charset.StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
      } catch (Exception ignore) {}
    }

    // règle unique pour savoir si on doit ignorer un token
    private static boolean shouldIgnoreToken(String token) {
      if (token == null) return true;
      String t = token.toLowerCase(java.util.Locale.ROOT);
      if (t.length() < 2) return true;
      if (HUNSPELL_WHITELIST.contains(t)) return true;
      if (USER_DICTIONARY.contains(t)) return true;
      return false;
    }
    
    /** Supprime tous les marqueurs, relance LT sur le texte nettoyé, puis insère ERR_MARK devant chaque faute. */
    @SuppressWarnings("unused")
	private void rebuildErrorMarkersWholeDoc() {
    	  EventQueue.invokeLater(() -> {
    	    if (inMarkerRebuild) return;
    	    inMarkerRebuild = true;
    	    try {
    	      final javax.swing.text.Document doc = area.getDocument();
    	      removeAllMarkersFromDoc(doc);                  // 1)
    	      String text  = doc.getText(0, doc.getLength());
    	      String clean = stripErrorMarkers(text);        // 2)
    	      java.util.List<RuleMatch> found = tool.check(clean);

    	      java.util.List<int[]> offs = new java.util.ArrayList<>();
    	      for (RuleMatch m : found) {
    	        int from = Math.max(0, Math.min(m.getFromPos(), clean.length()));
    	        int to   = Math.max(from, Math.min(m.getToPos(),  clean.length()));
    	        if (to <= from) continue;
    	        String token = clean.substring(from, to);
    	        if (shouldIgnoreToken(token)) continue;
    	        offs.add(new int[]{from, to});
    	      }
    	      offs.sort(java.util.Comparator.comparingInt(a -> a[0]));
    	      for (int i = offs.size()-1; i >= 0; i--) {
    	        int fromDoc = mapCleanOffsetToDocWithMarkers(text, offs.get(i)[0]);
    	        insertMarkerAt(doc, fromDoc);
    	      }

    	      area.getAccessibleContext().setAccessibleDescription(offs.size() + " faute(s) marquée(s).");
    	    } catch (Exception ex) {
    	      area.getToolkit().beep();
    	    } finally {
    	      inMarkerRebuild = false;
    	    }
    	  });
    	}

    
    /** Convertit un offset du texte "clean" vers l'offset dans le "doc" qui contient déjà des marqueurs. */
    private int mapCleanOffsetToDocWithMarkers(String docTextWithMarkers, int cleanOffset) {
      // On parcourt docTextWithMarkers en ignorant les marqueurs et on compte
      int cleanPos = 0;
      for (int i = 0; i < docTextWithMarkers.length(); ) {
        // Si on tombe sur un marqueur, on saute sa longueur (2 caractères ici)
        if (i + ERR_OPEN.length() <= docTextWithMarkers.length()
            && docTextWithMarkers.regionMatches(i, ERR_OPEN, 0, ERR_OPEN.length())) {
          i += ERR_OPEN.length();
          continue;
        }
        if (cleanPos == cleanOffset) return i;
        // avancer d'un caractère "réel"
        i++; cleanPos++;
      }
      return docTextWithMarkers.length();
    }

    private void gotoMarker(boolean forward) {
    	  try {
    	    final javax.swing.text.Document doc = area.getDocument();
    	    final String t = doc.getText(0, doc.getLength());
    	    final int caret = area.getCaretPosition();

    	    // Cherche le prochain/précédent "°°"
    	    int target = -1;
    	    if (forward) {
    	      target = t.indexOf(ERR_OPEN, Math.max(0, caret + (t.startsWith(ERR_OPEN, Math.max(0, caret-ERR_OPEN.length())) ? 1 : 0)));
    	      if (target < 0) target = t.indexOf(ERR_OPEN, 0); // wrap
    	    } else {
    	    	  // Chercher le marqueur strictement avant le caret.
    	    	  // Si le caret est juste après OU dans "°°", saute d’abord ce marqueur.
    	    	  int fromIndex = Math.max(0, caret - 1);

    	    	  // Détecter si on est juste après ou dans le marqueur courant
    	    	  int maybeStart = Math.max(0, caret - ERR_OPEN.length());
    	    	  if (maybeStart + ERR_OPEN.length() <= t.length()
    	    	      && t.regionMatches(maybeStart, ERR_OPEN, 0, ERR_OPEN.length())) {
    	    	    // on était sur le marqueur courant → reculer d’un cran pour l’ignorer
    	    	    fromIndex = Math.max(0, maybeStart - 1);
    	    	  }

    	    	  target = t.lastIndexOf(ERR_OPEN, fromIndex);
    	    	  if (target < 0) target = t.lastIndexOf(ERR_OPEN, t.length()); // wrap
    	    	}


    	    // Sélectionner le mot qui suit le marqueur
    	    int start = target + ERR_OPEN.length();
    	    int[] wb = wordBounds(t, start);
    	    int from = Math.max(start, wb[0]);
    	    int to   = Math.max(from,  wb[1]);

    	    area.requestFocusInWindow();
    	    area.select(from, to);
    	    area.getCaret().setDot(from);
    	    area.getCaret().moveDot(to);

    	    // Annonce pour SR/braille
    	    String token = t.substring(from, to);
    	    area.getAccessibleContext().setAccessibleDescription("Faute : " + token + ". Entrée pour suggestions.");
    	  } catch (Exception ex) {
    	    area.getToolkit().beep();
    	  }
    	}
    
    
    // =======================================================================

    
    @SuppressWarnings("serial")
	private SpellCheckLT(JTextComponent area) {

        this.area = area;
        this.highlighter = area.getHighlighter(); // DefaultHighlighter est LayeredHighlighter
        debounce.setRepeats(false);

        area.getDocument().addDocumentListener(new DocumentListener() {
        	@Override public void insertUpdate(DocumentEvent e){ if (realtime) debounce.restart(); }
        	@Override public void removeUpdate(DocumentEvent e){ if (realtime) debounce.restart(); }
        	@Override public void changedUpdate(DocumentEvent e){ if (realtime) debounce.restart(); }
        });

        // (Optionnel) menu suggestions au clavier: Shift+F10
        area.getInputMap().put(KeyStroke.getKeyStroke("shift F10"), "spellPopup");
        area.getActionMap().put("spellPopup", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showPopupAtCaret(); }
        });

        // (Optionnel) clic droit
        area.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e){ maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e){ maybePopup(e); }
            @SuppressWarnings("deprecation")
			private void maybePopup(MouseEvent e){
                if (e.isPopupTrigger()) showPopup(e.getX(), e.getY(), area.viewToModel(e.getPoint()));
            }
        });
        
        // F7 : suivant
        area.getInputMap().put(KeyStroke.getKeyStroke("F7"), "errNextMarker");
        area.getActionMap().put("errNextMarker", new AbstractAction() {
          @Override public void actionPerformed(ActionEvent e) {
            gotoMarker(true);
          }
        });

        // Shift+F7 : précédent
        area.getInputMap().put(KeyStroke.getKeyStroke("shift F7"), "errPrevMarker");
        area.getActionMap().put("errPrevMarker", new AbstractAction() {
          @Override public void actionPerformed(ActionEvent e) {
            gotoMarker(false);
          }
        });


     // Alt+Entrée : ouvrir la boîte de suggestions accessible au clavier
     area.getInputMap().put(KeyStroke.getKeyStroke("alt ENTER"), "spellSuggest");
     area.getActionMap().put("spellSuggest", new AbstractAction() {
       @Override public void actionPerformed(ActionEvent e) { showSuggestionsAtCaret(); }
     });

     // Ctrl+Alt+1..9 : appliquer directement une suggestion
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


        checkNow(); // premier passage
    }

    public static SpellCheckLT attach(JTextComponent area) {
        // s’assure d’avoir un LayeredHighlighter (JTextArea en a un par défaut)
        if (!(area.getHighlighter() instanceof LayeredHighlighter)) {
            area.setHighlighter(new DefaultHighlighter());
        }
        return new SpellCheckLT(area);
    }

    // Retire surlignages + réinitialise l’état + supprime tous les marqueurs "°°"
    private void clear() {
        // 1) enlever les surlignages de notre painter
        for (Highlighter.Highlight h : highlighter.getHighlights()) {
            if (h.getPainter() == painter) {
                highlighter.removeHighlight(h);
            }
        }

        // 2) vider les matches
        matches.clear();

        // 3) retirer tous les marqueurs "°°" du document (depuis la fin pour éviter les décalages)
        try {
            javax.swing.text.Document doc = area.getDocument();
            String t = doc.getText(0, doc.getLength());

            // Si tu as déjà défini ERR_ANY/ERR_OPEN, garde-les.
            // Sinon, on utilise le littéral "°°" ici :
            java.util.regex.Pattern MARK = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote("°°"));
            java.util.regex.Matcher m = MARK.matcher(t);

            java.util.List<int[]> spans = new java.util.ArrayList<>();
            while (m.find()) spans.add(new int[]{m.start(), m.end()});

            for (int i = spans.size() - 1; i >= 0; i--) {
                int[] sp = spans.get(i);
                doc.remove(sp[0], sp[1] - sp[0]);
            }
        } catch (Exception ignore) {
            // no-op
        }

        // (optionnel) réinitialiser la fenêtre de navigation F7
        navStart = 0;
        navEnd   = area.getDocument().getLength();
    }

    
    private void checkNow() {
    	  EventQueue.invokeLater(() -> {
    	    try {
    	      if (inMarkerRebuild) return;               
    	      clearVisualsKeepMarkers();    
    	      String text = area.getDocument().getText(0, area.getDocument().getLength());
    	      if (text.isEmpty()) return;
    	      navStart = 0; navEnd = text.length();

    	      String clean = stripErrorMarkers(text);
    	      List<RuleMatch> found = tool.check(clean);

    	      matches.addAll(found);
    	      matches.sort(java.util.Comparator.comparingInt(RuleMatch::getFromPos));

    	      for (RuleMatch m : found) {
    	        if ("TIRET".equals(m.getRule().getId())) continue;
    	        if ("WRONG_ETRE_VPPA".equals(m.getRule().getId())) continue;
    	        //if ("AUX_ETRE_VCONJ".equals(m.getRule().getId())) continue;

    	        int from = Math.max(0, Math.min(m.getFromPos(), text.length()));
    	        int to   = Math.max(from, Math.min(m.getToPos(),  text.length()));
    	        if (to <= from) continue;

    	        String token = text.substring(from, to);
    	        if (shouldIgnoreToken(token)) continue;

    	        ((LayeredHighlighter) highlighter).addHighlight(from, to, painter);
    	      }
    	    } catch (Exception ignore) {}
    	  });
    	}


    // ---------- Suggestions ----------
    private RuleMatch findHunspellHitAt(int pos) {
    	  for (RuleMatch m : matches) {
    	    if (pos >= m.getFromPos() && pos <= m.getToPos()
    	        && m.getRule() instanceof HunspellRule) {
    	      return m;
    	    }
    	  }
    	  return null;
    	}


    @SuppressWarnings("deprecation")
	public void showPopupAtCaret() {
        try {
            Rectangle r = area.modelToView(area.getCaretPosition());
            if (r != null) showPopup(r.x, r.y + r.height, area.getCaretPosition());
        } catch (BadLocationException ignore) {
        	
        }
    }

    // Menu contextuel des suggestion
    private void showPopup(int x, int y, int pos) {
    	
    	// si le caret est posé sur "°°", saute le marqueur pour atteindre le mot
    	  try {
    	    String t = area.getDocument().getText(0, area.getDocument().getLength());
    	    if (pos >= 0 && pos + ERR_OPEN.length() <= t.length()
    	        && t.regionMatches(pos, ERR_OPEN, 0, ERR_OPEN.length())) {
    	      pos += ERR_OPEN.length();
    	    }
    	  } catch (BadLocationException ignore) {}
    	
    	
	    	// 1) Hunspell sous le caret
	    	RuleMatch hit = findHunspellHitAt(pos);
	
	    	// 2) Sinon, autre règle LT
	    	if (hit == null) {
	    	  for (RuleMatch m : matches) {
	    	    if (pos >= m.getFromPos() && pos <= m.getToPos()) { hit = m; break; }
	    	  }
	    	}
	
	    	// 2bis) Si c'est TIRET, on l'ignore et on cherche une autre règle
	    	if (hit != null) {
	    		  String id = hit.getRule().getId();
	    		  if ("TIRET".equals(id) || "WRONG_ETRE_VPPA".equals(id) /* || "AUX_ETRE_VCONJ".equals(id) */) {
	    		    hit = null;  // on ignore cette règle → fallback Hunspell/mot
	    		  }
	    		}
		
		  JPopupMenu menu = new JPopupMenu();
		
		  try {
		    // ----- CAS A : match Hunspell sous le caret → suggestions d’orthographe -----
		    if (hit != null && hit.getRule() instanceof org.languagetool.rules.spelling.hunspell.HunspellRule) {
		      java.util.List<String> sugg = hit.getSuggestedReplacements();
		
		      if (sugg == null || sugg.isEmpty()) {
		        JMenuItem none = new JMenuItem("Aucune suggestion (Hunspell)");
		        none.setEnabled(false);
		        menu.add(none);
		      } else {
		        for (String rep : sugg) {
		          JMenuItem it = new JMenuItem(rep);
		          final RuleMatch hm = hit; // effectively final pour la lambda
		          it.addActionListener(evt -> replace(hm, rep));
		          menu.add(it);
		        }
		      }
		
		    // ----- CAS B : match LT non-Hunspell → suggestions de la règle LT (grammaire/style) -----
		    } else if (hit != null) {
		      String label = hit.getShortMessage() != null ? hit.getShortMessage()
		                                                   : hit.getRule().getDescription();
		      JMenuItem header = new JMenuItem(label);
		      header.setEnabled(false);
		      menu.add(header);
		      menu.addSeparator();
		
		      java.util.List<String> sugg = hit.getSuggestedReplacements();
		      if (sugg == null || sugg.isEmpty()) {
		        JMenuItem none = new JMenuItem("Aucune suggestion");
		        none.setEnabled(false);
		        menu.add(none);
		      } else {
		        for (String rep : sugg) {
		          JMenuItem it = new JMenuItem(rep);
		          final RuleMatch hm = hit;
		          it.addActionListener(evt -> replace(hm, rep));
		          menu.add(it);
		        }
		      }
		
		    // ----- CAS C : aucun match sous le caret → fallback : calculer suggestions Hunspell sur le “mot” -----
		    } else {
		      final javax.swing.text.Document doc = area.getDocument();
		      String text = doc.getText(0, doc.getLength());
		
		      int[] b = wordBounds(text, pos);        // utilise ta fonction utilitaire
		      final int startPos = b[0];
		      final int endPos   = b[1];
		
		      String word = text.substring(startPos, Math.max(startPos, endPos));
		
		      if (word.length() < 2) {
		        JMenuItem none = new JMenuItem("Aucune suggestion (Hunspell)");
		        none.setEnabled(false);
		        menu.add(none);
		      } else {
		        java.util.List<String> sugg = hunspellSuggest(tool, word); // ta fonction utilitaire filtrée Hunspell
		        if (sugg == null || sugg.isEmpty()) {
		          JMenuItem none = new JMenuItem("Aucune suggestion (Hunspell)");
		          none.setEnabled(false);
		          menu.add(none);
		        } else {
		          for (String rep : sugg) {
		            JMenuItem it = new JMenuItem(rep);
		            it.addActionListener(evt -> {
		            	  try {
		            	    doc.remove(startPos, endPos - startPos);
		            	    doc.insertString(startPos, rep, null);
	
		            	    int ps = javax.swing.text.Utilities.getRowStart(area, startPos);
		            	    int pe = javax.swing.text.Utilities.getRowEnd(area, startPos + rep.length());
		            	    rescanWindow(ps, pe);
		            	    navStart = 0;                           // ← F7 redevient “plein document”
		            	    navEnd   = doc.getLength();
	
		            	  } catch (BadLocationException ignore) {}
		            	});
		            menu.add(it);
		          }
		        }
		      }
		    }
		
		  } catch (Exception ignore) {
		    JMenuItem none = new JMenuItem("Aucune suggestion");
		    none.setEnabled(false);
		    menu.add(none);
	  }
	

	  
	// === AJOUTS DICTIONNAIRE UTILISATEUR ===
	  menu.addSeparator();

	  // 3.a — récupérer le mot sous le caret (ou le token du match)
	  final Document docRef = area.getDocument();
	  final String fullText;
	  try {
	    fullText = docRef.getText(0, docRef.getLength());
	  } catch (BadLocationException ex) {
	    // en cas d’erreur, on n’ajoute pas les items dico
	    menu.show(area, x, y);
	    return;
	  }

	  int fromSel, toSel;
	  String tokenUnderCaret;
	  if (hit != null) {
	    fromSel = Math.max(0, Math.min(hit.getFromPos(), fullText.length()));
	    toSel   = Math.max(fromSel, Math.min(hit.getToPos(), fullText.length()));
	    tokenUnderCaret = fullText.substring(fromSel, toSel);
	  } else {
	    int[] wb = wordBounds(fullText, pos);
	    fromSel = wb[0];
	    toSel   = wb[1];
	    tokenUnderCaret = fullText.substring(fromSel, Math.max(fromSel, toSel));
	  }

	  // 3.b — Ajouter au dictionnaire
	  if (tokenUnderCaret != null && tokenUnderCaret.strip().length() >= 2) {
	    JMenuItem addDic = new JMenuItem("Ajouter « " + tokenUnderCaret.strip() + " » au dictionnaire");
	    addDic.addActionListener(evt -> {
	      addWordToUserDictionary(tokenUnderCaret);
	      // rescanner localement pour enlever le soulignement tout de suite
	      try {
	        int ps = javax.swing.text.Utilities.getRowStart(area, fromSel);
	        int pe = javax.swing.text.Utilities.getRowEnd(area, toSel);
	        rescanWindow(ps, pe);
	        navStart = 0; navEnd = docRef.getLength(); // F7 = tout le document
	      } catch (BadLocationException ignore) {}
	    });
	    menu.add(addDic);
	  }

	  // 3.c — Gérer le dictionnaire…
	  JMenuItem manageDic = new JMenuItem("Gérer le dictionnaire…");
	  manageDic.addActionListener(evt -> manageUserDictionaryDialog(area));
	  menu.add(manageDic);
	  // === FIN AJOUTS DICTIONNAIRE UTILISATEUR ===

	  // Actions communes
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
    	    doc.remove(from, to - from);
    	    doc.insertString(from, replacement, null);

    	    // 1) rescanner uniquement la ligne/ le paragraphe impacté
    	    int anchor = Math.min(from + Math.max(0, replacement.length() - 1), doc.getLength());
    	    int ps = javax.swing.text.Utilities.getRowStart(area, from);
    	    int pe = javax.swing.text.Utilities.getRowEnd(area,   anchor);
    	    rescanWindow(ps, pe);   // <= remet les soulignements à jour dans ce bloc

    	    // 2) élargir la navigation F7 à *tout* le document
    	    navStart = 0;
    	    navEnd   = doc.getLength();
    	  } catch (BadLocationException ignore) {}
    	}

    private static JLanguageTool createLT() {
	  try {
	    Language fr = org.languagetool.Languages.getLanguageForShortCode("fr");
	    JLanguageTool lt = new JLanguageTool(fr);
	    System.out.println("Langue du correcteur : " + lt.getLanguage().getName());
	
	    // 1) ce que tu ne veux jamais
	    lt.disableRule("MULTITOKEN_SPELLER_RULE");
	    lt.disableRule("MORFOLOGIK_RULE_FR_FR");
	
	    // 2) (optionnel) activer les règles "par défaut off"
	    for (org.languagetool.rules.Rule r : lt.getAllRules()) {
	      if (r.isDefaultOff() && r.supportsLanguage(fr)) {
	        lt.enableRule(r.getId());
	      }
	    }
	
	    // 3) Hunspell (dicos LibreOffice sur le classpath)
	    UserConfig uc = new UserConfig();
	    HUNSPELL_RULE = new HunspellRule(JLanguageTool.getMessageBundle(), lt.getLanguage(), uc);
	    lt.addRule(HUNSPELL_RULE);
	
	    // 4) Couper explicitement les règles gênantes (après les activations)
	    lt.disableRule("TIRET");
	    lt.disableRule("WRONG_ETRE_VPPA");   // ⇦ remplace “a/avait” par “est/était”
	    return lt;
	  } catch (Throwable t) {
	    throw new IllegalStateException("Init LanguageTool FR a échoué", t);
	  }
	}


    // renvoie le premier index de match qui commence >= fromPos
    @SuppressWarnings("unused")
	private int indexOfNextFrom(int pos) {
    	  // 1) d'abord dans la fenêtre courante
    	  for (int i = 0; i < matches.size(); i++) {
    	    int f = matches.get(i).getFromPos();
    	    if (f >= pos && f < navEnd) return i;
    	  }
    	  // 2) ensuite après la fenêtre (reste du document)
    	  for (int i = 0; i < matches.size(); i++) {
    	    int f = matches.get(i).getFromPos();
    	    if (f >= Math.max(pos, navEnd)) return i;
    	  }
    	  // 3) wrap au début (jusqu'au début de la fenêtre)
    	  for (int i = 0; i < matches.size(); i++) {
    	    int f = matches.get(i).getFromPos();
    	    if (f < navStart) return i;
    	  }
    	  return -1;
    	}

    // renvoie le dernier index de match qui se termine <= fromPos
    @SuppressWarnings("unused")
	private int indexOfPrevFrom(int pos) {
    	  // 1) d'abord dans la fenêtre courante
    	  for (int i = matches.size() - 1; i >= 0; i--) {
    	    int t = matches.get(i).getToPos();
    	    if (t <= pos && t > navStart) return i;
    	  }
    	  // 2) ensuite avant la fenêtre (reste du document)
    	  for (int i = matches.size() - 1; i >= 0; i--) {
    	    int t = matches.get(i).getToPos();
    	    if (t <= Math.min(pos, navStart)) return i;
    	  }
    	  // 3) wrap à la fin (depuis la fin du doc jusqu’à la fin de la fenêtre)
    	  for (int i = matches.size() - 1; i >= 0; i--) {
    	    int t = matches.get(i).getToPos();
    	    if (t >= navEnd) return i;
    	  }
    	  return -1;
    	}

    // utilitaires pour borner à la fenêtre
    @SuppressWarnings("unused")
	private int firstIndexInWindow() {
      for (int i = 0; i < matches.size(); i++) {
        if (matches.get(i).getFromPos() >= navStart && matches.get(i).getFromPos() < navEnd) return i;
      }
      return -1;
    }
    @SuppressWarnings("unused")
	private int lastIndexInWindow() {
      for (int i = matches.size()-1; i >= 0; i--) {
        if (matches.get(i).getFromPos() >= navStart && matches.get(i).getFromPos() < navEnd) return i;
      }
      return -1;
    }


    // Déplacer le caret sur un RuleMatch et le rendre visible
    @SuppressWarnings("unused")
	private void focusMatch(int idx, boolean announce) {
        if (idx < 0 || idx >= matches.size()) return;
        RuleMatch m = matches.get(idx);
        int from = Math.max(0, Math.min(m.getFromPos(), area.getDocument().getLength()));
        int to   = Math.max(from, Math.min(m.getToPos(),   area.getDocument().getLength()));
        area.requestFocusInWindow();
        area.select(from, to);  // sélectionne le mot en faute (parfait pour lecteurs d'écran)
        area.getCaret().setDot(from);
        area.getCaret().moveDot(to);

        if (announce) {
            // Annonce simple via l’AccessibleDescription : beaucoup de lecteurs d’écran la lisent
            String msg = "Faute détectée. De " + from + " à " + to +
                         ". " + (m.getShortMessage() != null ? m.getShortMessage() : "Consultez les suggestions.");
            area.getAccessibleContext().setAccessibleDescription(msg);
            // Optionnel : si tu as déjà un JLabel srAnnounce dans la fenêtre :
            // srAnnounce.setText(msg);
        }
    }

    @SuppressWarnings("deprecation")
	private void showSuggestionsAtCaret() {
    	int pos = area.getCaretPosition();
	  // si le caret est posé sur "°°", saute le marqueur pour atteindre le mot
	  try {
	    String t = area.getDocument().getText(0, area.getDocument().getLength());
	    if (pos >= 0 && pos + ERR_OPEN.length() <= t.length()
	        && t.regionMatches(pos, ERR_OPEN, 0, ERR_OPEN.length())) {
	      pos += ERR_OPEN.length();
	    }
	  } catch (BadLocationException ignore) {}
    	  
	  // 1) priorité à Hunspell
	  RuleMatch hit = findHunspellHitAt(pos);
	  // 2) sinon toute autre règle LT (grammaire/style…)
	  if (hit == null) hit = findAnyHitAt(pos);
	  // 3) rien sous le caret
	  if (hit == null) { area.getToolkit().beep(); return; }
	
	  java.util.List<String> sugg = hit.getSuggestedReplacements();
	  if (sugg == null || sugg.isEmpty()) { area.getToolkit().beep(); return; }
	
	  // copie "effectively final" pour les lambdas
	  final RuleMatch rm = hit;
	
	  // modeless (non modal)
	  javax.swing.JDialog d = new javax.swing.JDialog(
	      javax.swing.SwingUtilities.getWindowAncestor(area),
	      "Suggestions",
	      java.awt.Dialog.ModalityType.MODELESS
	  );
	
	  javax.swing.JList<String> list = new javax.swing.JList<>(sugg.toArray(new String[0]));
	  list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
	  list.setSelectedIndex(0);
	  list.getAccessibleContext().setAccessibleName("Suggestions de remplacement");
	  list.getAccessibleContext().setAccessibleDescription(
	      "Flèches pour choisir, Entrée pour appliquer, Échap pour fermer.");
	
	  list.addKeyListener(new java.awt.event.KeyAdapter() {
	    @Override public void keyPressed(java.awt.event.KeyEvent e) {
	      if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
	        String rep = list.getSelectedValue();
	        if (rep != null) replace(rm, rep);   // ← utiliser rm
	        d.dispose();
	      } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
	        d.dispose();
	      }
	    }
	  });
	  list.addMouseListener(new java.awt.event.MouseAdapter() {
	    @Override public void mouseClicked(java.awt.event.MouseEvent e) {
	      if (e.getClickCount() == 2) {
	        String rep = list.getSelectedValue();
	        if (rep != null) replace(rm, rep);   // ← utiliser rm
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

    public static List<String> hunspellSuggest(JLanguageTool lt, String word) throws Exception {
    	  String text = " " + word + " ";
    	  List<String> out = new ArrayList<>();
    	  for (RuleMatch m : lt.check(text)) {
    	    if (m.getRule() instanceof HunspellRule
    	        && m.getFromPos() <= 1
    	        && m.getToPos() >= 1 + word.length()) {
    	      out.addAll(m.getSuggestedReplacements());
    	    }
    	  }
    	  return out;
    	}

    
    private static boolean isWordChar(char c) {
    	  return Character.isLetter(c) || c == '\'' || c == '’' || c == '-';
    	}

    	private static int[] wordBounds(String text, int pos) {
    	  int n = text.length();
    	  int s = Math.max(0, Math.min(pos, n));
    	  int e = s;

    	  while (s > 0 && isWordChar(text.charAt(s - 1))) s--;
    	  while (e < n && isWordChar(text.charAt(e)))     e++;

    	  // si ça commence par une apostrophe, étend à gauche (ex: "m’" dans "m’a")
    	  if (s < e && (text.charAt(s) == '\'' || text.charAt(s) == '’')) {
    	    int s2 = s - 1;
    	    while (s2 > 0 && Character.isLetter(text.charAt(s2 - 1))) s2--;
    	    if (s2 < s) s = s2;
    	  }
    	  return new int[]{s, e};
    	}

    	private RuleMatch findAnyHitAt(int pos) {
    		  for (RuleMatch m : matches)
    		    if (pos >= m.getFromPos() && pos <= m.getToPos()) return m;
    		  return null;
		}

    	
    	
    	public void setRealtime(boolean on) {
    		  realtime = on;
    		  if (!on) { debounce.stop(); clear(); }
    		}

    		public boolean isRealtime() { return realtime; }

    		// garder visible depuis l’extérieur
    		public void clearHighlights() { clear(); }


    		/** Re-scan only a window [start,end) after an edit, rebuild matches & highlights there. */
    		private void rescanWindow(int start, int end) {
    			  try {
    			    Document doc = area.getDocument();
    			    int N = doc.getLength();
    			    start = Math.max(0, Math.min(start, N));
    			    end   = Math.max(start, Math.min(end,   N));
    			    final int winStart = start;
    			    final int winEnd   = end;

    			    // 1) retirer les highlights qui recouvrent la fenêtre
    			    for (Highlighter.Highlight h : highlighter.getHighlights()) {
    			      if (h.getPainter() == painter) {
    			        int hs = h.getStartOffset(), he = h.getEndOffset();
    			        if (hs < end && he > start) highlighter.removeHighlight(h);
    			      }
    			    }

    			    // 2) retirer des matches ceux qui recouvrent la fenêtre
    			    matches.removeIf(m -> m.getFromPos() < winEnd && m.getToPos() > winStart);

    			    // 3) re-check uniquement la tranche AVEC nettoyage des balises
    			    String sliceWithMarkers = doc.getText(start, end - start);

    			    // 3.a retirer les °° (pour que LT ne les voie pas)
    			    String sliceNoErr = stripErrorMarkers(sliceWithMarkers);

    			    // 3.b retirer les balises inline + obtenir mapping clean->sliceNoErr
    			    InlineMarkupFilter.Result filtered = InlineMarkupFilter.strip(sliceNoErr);
    			    String clean = filtered.cleaned;

    			    // 3.c LT sur le “clean”
    			    List<RuleMatch> found = tool.check(clean);

    			    // 4) réinjecter avec offsets absolus MAPPÉS vers le DOC (qui contient encore les °°)
    			    for (RuleMatch m : found) {
    			      int fC = Math.max(0, Math.min(m.getFromPos(), clean.length()));
    			      int tC = Math.max(fC, Math.min(m.getToPos(),  clean.length()));
    			      if (tC <= fC) continue;

    			      // 1ère étape : clean -> sliceNoErr
    			      int fNoErr = filtered.mapStart(fC);
    			      int tNoErr = filtered.mapEnd(tC);

    			      // 2ème étape : sliceNoErr -> sliceWithMarkers
    			      int fRel = mapCleanOffsetToDocWithMarkers(sliceWithMarkers, fNoErr);
    			      int tRel = mapCleanOffsetToDocWithMarkers(sliceWithMarkers, tNoErr);

    			      int from = start + fRel;
    			      int to   = start + tRel;
    			      if (to <= from) continue;

    			      String tokenAbs = doc.getText(from, to - from);
    			      if (shouldIgnoreToken(tokenAbs)) continue;

    			      RuleMatch nm = new RuleMatch(
    			          m.getRule(), m.getSentence(), from, to, m.getMessage(), m.getShortMessage()
    			      );
    			      if (m.getSuggestedReplacements() != null)
    			        nm.setSuggestedReplacements(m.getSuggestedReplacements());
    			      if (m.getUrl() != null)
    			        nm.setUrl(m.getUrl());

    			      matches.add(nm);
    			      ((LayeredHighlighter) highlighter).addHighlight(from, to, painter);
    			    }

    			    // 5) garder l’ordre
    			    matches.sort(java.util.Comparator.comparingInt(RuleMatch::getFromPos));

    			  } catch (Exception ignore) {}
    			}
    		
    		// Optionnel : pour afficher un compteur à la fin
    		public int getMatchesCount() { return matches.size(); }

    		// private boolean inMarkerRebuild = false;
			public void checkDocumentNowAsync(Runnable onDone) {
			  try {
			    final Document doc = area.getDocument();
			    final String textRaw = doc.getText(0, doc.getLength());
			    if (textRaw.isEmpty()) { if (onDone != null) SwingUtilities.invokeLater(onDone); return; }
			
			    new javax.swing.SwingWorker<java.util.List<RuleMatch>, Void>() {
			      // 1) Préparer le texte nettoyé + mapping
			      final InlineMarkupFilter.Result filtered = InlineMarkupFilter.strip(textRaw);
			      final String clean = filtered.cleaned;
			
			      @Override protected java.util.List<RuleMatch> doInBackground() throws Exception {
			        // lourd → HORS EDT
			        return tool.check(clean);
			      }
			
			      @Override protected void done() {
			        try {
			          final java.util.List<RuleMatch> found = get();
			          EventQueue.invokeLater(() -> {
			            if (inMarkerRebuild) { if (onDone != null) onDone.run(); return; }
			            inMarkerRebuild = true;
			            try {
			              // a) tout remettre à zéro (highlights + matches + °°)
			              clear();
			
			              // b) collecter les offsets en ABSOLU (mappés sur le doc ORIGINAL)
			              java.util.List<int[]> offs = new java.util.ArrayList<>();
			              for (RuleMatch m : found) {
			                int fC = Math.max(0, Math.min(m.getFromPos(), clean.length()));
			                int tC = Math.max(fC, Math.min(m.getToPos(),  clean.length()));
			                if (tC <= fC) continue;
			
			                int f = filtered.mapStart(fC);  // ← map clean -> original
			                int t = filtered.mapEnd(tC);
			                if (t <= f) continue;
			
			                // (optionnel) filtrage lexical sur le token original
			                String token = textRaw.substring(f, t);
			                if (shouldIgnoreToken(token)) continue;
			
			                offs.add(new int[]{f, t});
			              }
			
			              // c) insérer les °° depuis la fin
			              offs.sort(java.util.Comparator.comparingInt(a -> a[0]));
			              for (int i = offs.size() - 1; i >= 0; i--) {
			                insertMarkerAt(doc, offs.get(i)[0]);
			              }
			
			              // d) refaire les highlights sur tout le doc
			              rescanWindow(0, doc.getLength());
			
			              // e) navigation = tout le doc
			              navStart = 0;
			              navEnd   = doc.getLength();
			
			            } catch (Exception ex) {
			              area.getToolkit().beep();
			            } finally {
			              inMarkerRebuild = false;
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
			
			
			/** Vérifie la sélection ou le paragraphe courant en arrière-plan. */
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
			        start = Math.max(0, Math.min(start, doc.getLength()));
			        end   = Math.max(start, Math.min(end,   doc.getLength()));
			        if (end <= start) { if (onDone != null) SwingUtilities.invokeLater(onDone); return; }

			        // 2) Captures pour le worker
			        final int s0 = start, e0 = end;
			        final String sliceRaw = doc.getText(s0, e0 - s0);

			        // ★ Nettoyage des marqueurs inline + table de mapping clean→orig
			        final writer.spell.SpellCheckLT.InlineMarkupFilter.Result filtered = InlineMarkupFilter.strip(sliceRaw);
			        final String sliceClean = filtered.cleaned;

			        // 3) Lancer l’analyse hors-EDT
			        new javax.swing.SwingWorker<java.util.List<org.languagetool.rules.RuleMatch>, Void>() {
			            @Override protected java.util.List<org.languagetool.rules.RuleMatch> doInBackground() throws Exception {
			                // ⚠️ lourd → HORS EDT
			                return tool.check(sliceClean);   // ★ on analyse la version nettoyée
			            }

			            @Override protected void done() {
			                try {
			                    // 4) Récupérer les résultats
			                    final java.util.List<org.languagetool.rules.RuleMatch> found = get();

			                    // 5) Retour UI : poser marqueurs + highlights dans [s0,e0)
			                    EventQueue.invokeLater(() -> {
			                        if (inMarkerRebuild) { if (onDone != null) onDone.run(); return; }
			                        inMarkerRebuild = true;
			                        try {
			                            // 5.1 Limiter F7 à la fenêtre
			                            navStart = s0;
			                            navEnd   = e0;

			                            // 5.2 Conserver les °° globaux, mais nettoyer le visuel dans la fenêtre
			                            clearVisualsKeepMarkers();

			                            // 5.3 Enlever les marqueurs °° dans [s0,e0) pour repartir propre
			                            removeMarkersInRange(doc, s0, e0);

			                            // 5.4 Construire la liste d’offsets ABSOLUS dans le document ORIGINAL
			                            //     en reprojetant les offsets LT (basés sur sliceClean)
			                            java.util.List<int[]> absRanges = new java.util.ArrayList<>();
			                            for (org.languagetool.rules.RuleMatch m : found) {
			                                int fClean = Math.max(0, Math.min(m.getFromPos(), sliceClean.length()));
			                                int tClean = Math.max(fClean, Math.min(m.getToPos(),  sliceClean.length()));
			                                if (tClean <= fClean) continue;

			                                // (optionnel) filtrage lexical sur le token "nettoyé"
			                                String tokenClean = sliceClean.substring(fClean, tClean);
			                                if (shouldIgnoreToken(tokenClean)) continue;

			                                // ★ Mapping vers les offsets dans sliceRaw, puis vers le doc
			                                int fOrig = s0 + filtered.mapStart(fClean);
			                                int tOrig = s0 + filtered.mapEnd(tClean);
			                                if (tOrig > fOrig) absRanges.add(new int[]{fOrig, tOrig});
			                            }

			                            // 5.5 (si tu utilises des marqueurs "°°") les insérer depuis la fin
			                            absRanges.sort(java.util.Comparator.comparingInt(a -> a[0]));
			                            for (int i = absRanges.size() - 1; i >= 0; i--) {
			                                int absFrom = absRanges.get(i)[0];   // ★ déjà mappé
			                                insertMarkerAt(doc, absFrom);
			                            }

			                            // 5.6 Refaire les highlights/matches sur la fenêtre
			                            //     (si ta logique a besoin des plages complètes, utilise absRanges)
			                            rescanWindow(s0, e0);

			                            // 5.7 Annonce (optionnel)
			                            area.getAccessibleContext().setAccessibleDescription(
			                                absRanges.size() + " faute(s) marquée(s) dans la sélection."
			                            );

			                        } catch (Exception ignore) {
			                        } finally {
			                            inMarkerRebuild = false;
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

    		
    		/** Supprime tous les marqueurs "°°" uniquement dans [start,end). */
    		private static void removeMarkersInRange(javax.swing.text.Document doc, int start, int end) {
    		    try {
    		        start = Math.max(0, Math.min(start, doc.getLength()));
    		        end   = Math.max(start, Math.min(end,   doc.getLength()));
    		        if (end <= start) return;

    		        String slice = doc.getText(start, end - start);
    		        java.util.regex.Pattern MARK = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote("°°"));
    		        java.util.regex.Matcher m = MARK.matcher(slice);

    		        java.util.List<int[]> spans = new java.util.ArrayList<>();
    		        while (m.find()) spans.add(new int[]{m.start(), m.end()});
    		        for (int i = spans.size() - 1; i >= 0; i--) {
    		            int[] sp = spans.get(i);
    		            doc.remove(start + sp[0], sp[1] - sp[0]);
    		        }
    		    } catch (Exception ignore) {}
    		}

    		/** Reconstruit les marqueurs "°°" sur la fenêtre [start,end) uniquement. */
    		@SuppressWarnings("unused")
			private void rebuildErrorMarkersInWindow(int start, int end) {
    			 if (inMarkerRebuild) return;
    			 inMarkerRebuild = true;
    		    try {
    		        final javax.swing.text.Document doc = area.getDocument();
    		        start = Math.max(0, Math.min(start, doc.getLength()));
    		        end   = Math.max(start, Math.min(end,   doc.getLength()));
    		        if (end <= start) return;

    		        // 1) retirer les marqueurs dans la fenêtre
    		        removeMarkersInRange(doc, start, end);

    		        // 2) checker sur le texte nettoyé (strip des "°°" si l’utilisateur en a tapé à la main)
    		        String sliceRaw = doc.getText(start, end - start);
    		        final InlineMarkupFilter.Result filtered = InlineMarkupFilter.strip(sliceRaw);  // ← nettoie et crée la table de mapping
    		        final String sliceClean = filtered.cleaned;                  // ← texte à passer à LT


    		        java.util.List<org.languagetool.rules.RuleMatch> found = tool.check(sliceClean);

    		        // 3) collecter les offsets (dans le "clean")
    		        java.util.List<int[]> offs = new java.util.ArrayList<>();
    		        for (org.languagetool.rules.RuleMatch m : found) {
    		            int from = Math.max(0, Math.min(m.getFromPos(), sliceClean.length()));
    		            int to   = Math.max(from, Math.min(m.getToPos(),  sliceClean.length()));
    		            if (to <= from) continue;

    		            String token = sliceClean.substring(from, to);
    		            if (shouldIgnoreToken(token)) continue;

    		            offs.add(new int[]{from, to});
    		        }

    		        // 4) insérer les marqueurs dans le document (depuis la fin → pas de décalage)
    		        offs.sort(java.util.Comparator.comparingInt(a -> a[0]));
    		        for (int i = offs.size() - 1; i >= 0; i--) {
    		            int fromClean = offs.get(i)[0];
    		            int absFrom   = start + fromClean; // pas besoin de mapping : on vient d’enlever les marqueurs dans la fenêtre
    		            insertMarkerAt(doc, absFrom);      // insère "°°"
    		        }

    		        // 5) annonce (optionnel)
    		        area.getAccessibleContext().setAccessibleDescription(
    		            offs.size() + " faute(s) marquée(s) dans la sélection."
    		        );

    		    } catch (Exception ex) {
    		        area.getToolkit().beep();
    		    }finally {
    		        inMarkerRebuild = false;
    		    }
    		}


    		@SuppressWarnings("serial")
			private static void manageUserDictionaryDialog(java.awt.Component parent) {
    			  javax.swing.JDialog d = new javax.swing.JDialog(
    			      javax.swing.SwingUtilities.getWindowAncestor(parent),
    			      "Dictionnaire utilisateur",
    			      java.awt.Dialog.ModalityType.MODELESS
    			  );
    			  d.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    			  // --- Modèle + liste (focus de départ sur la liste) ---
    			  javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();
    			  for (String w : commandes.listMotsDico) model.addElement(w);

    			  javax.swing.JList<String> list = new javax.swing.JList<>(model);
    			  list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    			  list.getAccessibleContext().setAccessibleName("Liste des mots du dictionnaire");
    			  list.getAccessibleContext().setAccessibleDescription(
    			      "Utilisez les flèches pour naviguer. Suppr pour supprimer l’élément sélectionné."
    			  );

    			  // Suppr par clavier quand la liste a le focus
    			  list.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
    			      .put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), "delSelected");
    			  list.getActionMap().put("delSelected", new javax.swing.AbstractAction() {
    			    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
    			      String sel = list.getSelectedValue();
    			      if (sel != null) {
    			        removeWordFromUserDictionary(sel);
    			        model.removeElement(sel);
    			      } else {
    			        list.getToolkit().beep();
    			      }
    			    }
    			  });

    			  // --- Boutons ---
    			  javax.swing.JButton del   = new javax.swing.JButton("Supprimer");
    			  javax.swing.JButton clear = new javax.swing.JButton("Tout vider");
    			  javax.swing.JButton close = new javax.swing.JButton("Fermer");

    			  // Mnemonics (Alt+S / Alt+V / Alt+F)
    			  del.setMnemonic('S');
    			  clear.setMnemonic('V');
    			  close.setMnemonic('F');

    			  // Actions boutons
    			  del.addActionListener(e -> {
    			    String sel = list.getSelectedValue();
    			    if (sel != null) {
    			      removeWordFromUserDictionary(sel);
    			      model.removeElement(sel);
    			      // Rester clavier-friendly : remet le focus sur la liste
    			      list.requestFocusInWindow();
    			    } else {
    			      d.getToolkit().beep();
    			    }
    			  });

    			  clear.addActionListener(e -> {
    			    int ok = javax.swing.JOptionPane.showConfirmDialog(
    			        d, "Vider tout le dictionnaire ?", "Confirmation",
    			        javax.swing.JOptionPane.OK_CANCEL_OPTION
    			    );
    			    if (ok == javax.swing.JOptionPane.OK_OPTION) {
    			      clearUserDictionary();
    			      model.clear();
    			      list.requestFocusInWindow();
    			    }
    			  });

    			  close.addActionListener(e -> d.dispose());

    			  // Entrée = “cliquer” quand un bouton a le focus (press/release)
    			  javax.swing.KeyStroke ENTER = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0);
    			  javax.swing.KeyStroke ENTER_RELEASED = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0, true);

    			  java.util.function.Consumer<javax.swing.JButton> makeEnterClick = (btn) -> {
    			    javax.swing.InputMap im = btn.getInputMap(javax.swing.JComponent.WHEN_FOCUSED);
    			    im.put(ENTER, "press");
    			    im.put(ENTER_RELEASED, "release");
    			    // Les actions "press"/"release" existent déjà dans l'ActionMap du JButton → doClick()
    			  };
    			  makeEnterClick.accept(del);
    			  makeEnterClick.accept(clear);
    			  makeEnterClick.accept(close);


    			  // Option : quand un bouton prend le focus, il devient "default button"
    			  java.awt.event.FocusListener setDefaultOnFocus = new java.awt.event.FocusAdapter() {
    			    @Override public void focusGained(java.awt.event.FocusEvent e) {
    			      if (e.getComponent() instanceof javax.swing.JButton b) {
    			        d.getRootPane().setDefaultButton(b);
    			      }
    			    }
    			  };
    			  del.addFocusListener(setDefaultOnFocus);
    			  clear.addFocusListener(setDefaultOnFocus);
    			  close.addFocusListener(setDefaultOnFocus);

    			  // Échap pour fermer, partout dans la fenêtre
    			  d.getRootPane().registerKeyboardAction(
    			      ev -> d.dispose(),
    			      javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
    			      javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
    			  );

    			  // Layout propre
    			  javax.swing.JPanel buttons = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
    			  buttons.add(del);
    			  buttons.add(clear);
    			  buttons.add(close);

    			  d.getContentPane().setLayout(new java.awt.BorderLayout());
    			  d.getContentPane().add(new javax.swing.JScrollPane(list), java.awt.BorderLayout.CENTER);
    			  d.getContentPane().add(buttons, java.awt.BorderLayout.SOUTH);

    			  d.setSize(420, 360);
    			  d.setLocationRelativeTo(parent);
    			  d.setVisible(true);

    			  // Focus clavier initial sur la liste
    			  list.requestFocusInWindow();
    			}


    		public static final class InlineMarkupFilter {
    		    private InlineMarkupFilter() {}

    		    // Tous les délimiteurs à ignorer (2 caractères chacun)
    		    private static final Set<String> TAGS = new HashSet<>(Arrays.asList(
    		        "**","__","^^",
    		        "*^","^*","_*","*_","_^","^_",
    		        "^\u00A8","\u00A8^","_\u00A8","\u00A8_"
    		    ));

    		    /** Résultat du filtrage : texte nettoyé + table clean→orig */
    		    public static final class Result {
    		        public final String cleaned;
    		        /** Pour tout index i dans cleaned (et i==cleaned.length), c2o[i] = index dans l’original */
    		        public final int[] c2o;
    		        Result(String c, int[] map) { cleaned = c; c2o = map; }

    		        public int mapStart(int cleanStart) { return c2o[Math.max(0, Math.min(cleanStart, c2o.length-1))]; }
    		        public int mapEnd(int cleanEnd)     { return c2o[Math.max(0, Math.min(cleanEnd,   c2o.length-1))]; }
    		    }

    		    /** Retire les marqueurs inline (sans toucher au texte) et prépare la table d’index. */
    		    public static Result strip(String original) {
    		        StringBuilder out = new StringBuilder(original.length());
    		        // c2o a une case de plus pour mapper la fin (length)
    		        int[] c2oTmp = new int[original.length() + 1];
    		        int cleanIdx = 0;

    		        for (int i = 0; i < original.length(); ) {
    		            if (i + 1 < original.length()) {
    		                String two = original.substring(i, i + 2);
    		                if (TAGS.contains(two)) {
    		                    // on saute ces 2 caractères (marqueurs)
    		                    i += 2;
    		                    continue;
    		                }
    		            }
    		            // garder ce caractère
    		            out.append(original.charAt(i));
    		            c2oTmp[cleanIdx] = i;
    		            cleanIdx++;
    		            i++;
    		        }
    		        // map la fin
    		        c2oTmp[cleanIdx] = original.length();
    		        // redimensionner c2o à (cleanIdx+1)
    		        int[] c2o = java.util.Arrays.copyOf(c2oTmp, cleanIdx + 1);
    		        return new Result(out.toString(), c2o);
    		    }
    		}


    		

}
