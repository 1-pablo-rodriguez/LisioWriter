package writer.bookmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.UUID;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Position;

import xml.node;

public final class BookmarkManager {
 public static final class Bookmark {
     public final String id;         // identifiant stable
     public final Position pos;      // suit le texte
     public String note;			// note
     public String label;            // texte
     public final long createdAt;

     public Bookmark(String id, Position pos, String label, long createdAt) {
         this.id = id; this.pos = pos; this.label = label; this.createdAt = createdAt;
     } 
     public Bookmark(String id, Position pos, String note, String label, long createdAt) {
         this.id = id; this.pos = pos; this.note=note; this.label = label; this.createdAt = createdAt;
     } 
 }

 private final writer.ui.NormalizingTextPane area;
 private final LinkedHashMap<String, Bookmark> map = new LinkedHashMap<>();
 private final ArrayList<String> order = new ArrayList<>(); // ordre d’ajout pour Next/Prev
 private int cursor = -1;

 public BookmarkManager(writer.ui.NormalizingTextPane area) { this.area = area; }

 // Ajoute un marque-page au caret (ou label custom)
 public Bookmark addHere(String label) {
     try {
         Document doc = area.getDocument();
         int off = Math.max(0, Math.min(area.getCaretPosition(), doc.getLength()));
         Position p = doc.createPosition(off);
         String id = UUID.randomUUID().toString();
         @SuppressWarnings("unused")
		String defaultNote = defaultNoteForCurrentLine();
         Bookmark b = new Bookmark(id, p, label, System.currentTimeMillis());
         map.put(id, b);
         order.add(id);
         rebuildOrderByOffset();
         if (cursor < 0) cursor = 0;  // premier ajout
         return b;
     } catch (BadLocationException ex) { return null; }
 }
 
 // Supprime tous les marque-pages
 public void clearAll() {
	    map.clear();
	    order.clear();
	    cursor = -1;
	}

 // Supprime le marque-page le plus proche du caret (tolérance : même ligne)
 public Bookmark removeNearestOnSameLine() {
     Bookmark b = nearestOnSameLine();
     if (b == null) return null;
     map.remove(b.id);
     int idx = order.indexOf(b.id);
     if (idx >= 0) order.remove(idx);
     if (order.isEmpty()) cursor = -1;
     else cursor = Math.min(cursor, order.size() - 1);
     compact();
     rebuildOrderByOffset();
     return b;
 }

 // Bascule : si un marque-page existe sur la ligne → supprime, sinon → ajoute
 public boolean toggleHere() {
     Bookmark b = nearestOnSameLine();
     if (b != null) { removeNearestOnSameLine(); return false; }
     addHere(labelForCurrentLine());
     return true;
 }

 public Bookmark goNext() {
	    if (order.isEmpty()) return null;
	    compact();

	    int caret = area.getCaretPosition();
	    int bestIdx = -1, bestOff = Integer.MAX_VALUE;
	    int minIdx  = -1, minOff  = Integer.MAX_VALUE;

	    for (int i = 0; i < order.size(); i++) {
	        Bookmark b = map.get(order.get(i));
	        if (b == null) continue;
	        int off = b.pos.getOffset();

	        // candidat “après caret”
	        if (off > caret && off < bestOff) { bestOff = off; bestIdx = i; }

	        // mémorise le plus petit offset (pour wrap)
	        if (off < minOff) { minOff = off; minIdx = i; }
	    }

	    int idx = (bestIdx != -1 ? bestIdx : minIdx);
	    if (idx == -1) return null; // aucun bookmark valide
	    cursor = idx;
	    return moveCaretTo(order.get(cursor));
	}

	public Bookmark goPrev() {
	    if (order.isEmpty()) return null;
	    compact();

	    int caret = area.getCaretPosition();
	    int bestIdx = -1, bestOff = -1;
	    int maxIdx  = -1, maxOff  = -1;

	    for (int i = 0; i < order.size(); i++) {
	        Bookmark b = map.get(order.get(i));
	        if (b == null) continue;
	        int off = b.pos.getOffset();

	        // candidat “avant caret”
	        if (off < caret && off > bestOff) { bestOff = off; bestIdx = i; }

	        // mémorise le plus grand offset (pour wrap)
	        if (off > maxOff) { maxOff = off; maxIdx = i; }
	    }

	    int idx = (bestIdx != -1 ? bestIdx : maxIdx);
	    if (idx == -1) return null;
	    cursor = idx;
	    return moveCaretTo(order.get(cursor));
	}


 public java.util.List<Bookmark> listAll() {
     ArrayList<Bookmark> L = new ArrayList<>();
     for (String id : order) L.add(map.get(id));
     return Collections.unmodifiableList(L);
 }

	 // --- Persistance : .bwr.marks (TSV simple) ---
	 public void saveSidecar(Path mainFile) throws IOException {
		    if (mainFile == null) return;
		    Path marks = sidecarPath(mainFile);
		    StringBuilder sb = new StringBuilder();
		    for (String id : order) {
		        Bookmark b = map.get(id);
		        sb.append(id).append('\t')
		          .append(b.pos.getOffset()).append('\t')
		          .append(b.createdAt).append('\t')
		          .append(escape(b.note == null ? "" : b.note)).append('\t')
		          .append(escape(b.label == null ? "" : b.label)).append('\n');
		    }
		    Files.writeString(marks, sb.toString(), StandardCharsets.UTF_8,
		            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}


	 public void loadSidecar(Path mainFile) throws IOException, BadLocationException {
		    map.clear(); order.clear(); cursor = -1;
		    if (mainFile == null || area == null) return;
		    Path marks = sidecarPath(mainFile);
		    if (!Files.exists(marks)) return;

		    final Document doc = area.getDocument();
		    if (doc == null) return;

		    final java.nio.charset.Charset CS = StandardCharsets.UTF_8;
		    final java.util.HashSet<String> seen = new java.util.HashSet<>();

		    for (String raw : Files.readAllLines(marks, CS)) {
		        if (raw == null) continue;
		        String line = raw;
		        if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
		        if (line.isBlank()) continue;

		        String[] t = line.split("\t", -1);
		        // attendu: id, off, createdAt, note, label  (5 colonnes)
		        if (t.length < 4) continue;

		        final String id = (t[0] == null || t[0].isBlank()) ? UUID.randomUUID().toString() : t[0];
		        if (seen.contains(id)) continue; seen.add(id);

		        final int off = clamp(parseInt(t[1], 0), 0, doc.getLength());
		        final long ts  = parseLong(t[2], System.currentTimeMillis());

		        String note  = null;
		        String label = "Marque-page";

		        if (t.length >= 5) {
		            note  = unescape(t[3]);
		            label = unescape(t[4]);
		        } else { 
		            // compat ancien format (4 colonnes: pas de note, t[3] = label)
		            label = unescape(t[3]);
		        }

		        final Position p = doc.createPosition(off);
		        final Bookmark b = new Bookmark(id, p, note, label, ts);
		        map.put(id, b);
		        order.add(id);
		    }

		    compact();
		    if (!order.isEmpty()) cursor = 0; else cursor = -1;
		}

	 private static Path sidecarPath(Path main) {
	     String fn = main.getFileName().toString();
	     return main.resolveSibling(fn + ".marks"); // ex: monfichier.bwr.marks
	 }

	 @SuppressWarnings("deprecation")
	private Bookmark moveCaretTo(String id) {
	     Bookmark b = map.get(id);
	     if (b == null) return null;
	     int off = b.pos.getOffset();
	     area.setCaretPosition(off);
	     try {
	         java.awt.Rectangle r = area.modelToView(off);
	         if (r != null) area.scrollRectToVisible(r);
	     } catch (BadLocationException ignore) {}
	     return b;
	 }


 private Bookmark nearestOnSameLine() {
     try {
         Document doc = area.getDocument();
         int caret = area.getCaretPosition();
         Element root = doc.getDefaultRootElement();
         int line = root.getElementIndex(caret);
         Element lineEl = root.getElement(line);
         int L = lineEl.getStartOffset(), R = lineEl.getEndOffset();
         Bookmark best = null;
         for (String id : order) {
             Bookmark b = map.get(id);
             int off = b.pos.getOffset();
             if (off >= L && off < R) { best = b; break; }
         }
         return best;
     } catch (Exception ex) { return null; }
 }

 private String labelForCurrentLine() {
     try {
         Document doc = area.getDocument();
         Element root = doc.getDefaultRootElement();
         int line = root.getElementIndex(area.getCaretPosition());
         Element el = root.getElement(line);
         String s = doc.getText(el.getStartOffset(), Math.max(0, el.getEndOffset() - el.getStartOffset()));
         s = s.replaceAll("\\R", " ").trim();
         if (s.length() > 80) s = s.substring(0, 80) + "…";
         return s.isBlank() ? "Marque-page" : s;
     } catch (Exception e) {
         return "Marque-page";
     }
 }

 
 /** Extrait un contexte plus long (paragraphe) pour pré-remplir la note. */
 private String defaultNoteForCurrentLine() {
     try {
         Document doc = area.getDocument();
         int caret = Math.max(0, Math.min(area.getCaretPosition(), doc.getLength()));
         Element root = doc.getDefaultRootElement();
         int line = root.getElementIndex(caret);
         // étendre un peu : prendre la ligne courante + suivante si courte
         Element lineEl = root.getElement(line);
         int start = Math.max(0, lineEl.getStartOffset());
         int end   = Math.min(doc.getLength(), lineEl.getEndOffset());

         // essayer d'englober un peu plus (jusqu'à la fin du paragraphe)
         // on cherche l'élément suivant tant que ce n'est pas une séparation logique
         int maxLen = 20; // longueur max de note par défaut
         String s = doc.getText(start, Math.max(0, end - start)).replaceAll("\\R", " ").trim();

         // si trop court, essayer d'ajouter la ligne suivante
         if (s.length() < 80 && line + 1 < root.getElementCount()) {
             Element next = root.getElement(line + 1);
             int extraLen = Math.min(maxLen - s.length(), Math.max(0, next.getEndOffset() - next.getStartOffset()));
             if (extraLen > 0) {
                 String extra = doc.getText(next.getStartOffset(), extraLen).replaceAll("\\R", " ").trim();
                 if (!extra.isBlank()) s = (s + " " + extra).trim();
             }
         }

         if (s.length() > maxLen) s = s.substring(0, maxLen) + "…";
         if (s.isBlank()) return "Note du marque-page";
         return "NOTE - "+ s;
     } catch (Exception e) {
         return "Note du marque-page";
     }
 }

 
 private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(v, hi)); }
 private static int parseInt(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
 private static long parseLong(String s, long def){ try { return Long.parseLong(s);} catch (Exception e){ return def; } }
 private static String escape(String s){ return s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n"); }
 private static String unescape(String s){ return s.replace("\\n","\n").replace("\\t","\t").replace("\\\\","\\"); }
 
 
	//--- Dans BookmarkManager ---
	 public node saveToXml() {
		rebuildOrderByOffset();
		compact();
	    node bookmarks = new node();
	    bookmarks.setNameNode("bookmarks");
    	bookmarks.getAttributs().put("version", "1");
	     for (String id : order) {
	    	 if(id!=null) {
	         Bookmark b = map.get(id);
	         node bm = new node();
	         bm.setNameNode("bm");
	         bm.getAttributs().put("id", id);
	         bm.getAttributs().put("off", Integer.toString(b.pos.getOffset()));
	         if (b.note != null && !b.note.isBlank()) {
	        	 bm.getAttributs().put("note",b.note);
	         }
	         if (b.label != null && !b.label.isBlank()) bm.getAttributs().put("label", b.label);
	         bm.getAttributs().put("created", java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
	                 .format(java.time.LocalDateTime.ofEpochSecond(b.createdAt / 1000, 0, java.time.ZoneOffset.UTC)));
	         
	         bookmarks.addEnfant(bm);
	    	 }
	     }
	    return bookmarks;
	 }

	 public void loadFromXml(node bookmarksEl) throws BadLocationException {
		    map.clear(); order.clear(); cursor = -1;
		    if (bookmarksEl == null) return;

		    javax.swing.text.Document doc = this.area.getDocument();
		    ArrayList<node> nodes = bookmarksEl.getEnfants();
		    for (int i = 0; i < nodes.size(); i++) {
		        node e = nodes.get(i);
		        String id = optAttr(e, "id", UUID.randomUUID().toString());
		        int off = clamp(parseInt(optAttr(e, "off","0"), 0), 0, doc.getLength());
		        String label = optAttr(e, "label", "Marque-page");
		        long ts = System.currentTimeMillis();
		        try {
		            String created = e.getAttributs("created");
		            if (created != null && !created.isBlank()) {
		                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(created);
		                ts = ldt.toEpochSecond(java.time.ZoneOffset.UTC) * 1000L;
		            }
		        } catch (Exception ignore) {}
		        String note = optAttr(e, "note", null);

		        javax.swing.text.Position p = doc.createPosition(off);
		        Bookmark b = new Bookmark(id, p, note, label, ts);
		        map.put(id, b); order.add(id);
		    }
		    if (!order.isEmpty()) cursor = Math.min(cursor < 0 ? 0 : cursor, order.size() - 1);
		    compact();
		    rebuildOrderByOffset();
		}

	
	 private static String optAttr(node e, String name, String def) {
	     String v = e.getAttributs(name);
	     return (v == null || v.isEmpty()) ? def : v;
	 }
	 
	 /** Supprime de 'order' les ids inexistants dans 'map'. Ajuste 'cursor'. */
	 private void compact() {
	     boolean changed = false;
	     for (int i = order.size() - 1; i >= 0; i--) {
	         String id = order.get(i);
	         if (id == null || !map.containsKey(id) || map.get(id) == null) {
	             order.remove(i);
	             changed = true;
	         }
	     }
	     if (changed) {
	         if (order.isEmpty()) cursor = -1;
	         else cursor = Math.min(Math.max(cursor, 0), order.size() - 1);
	     }
	 }
 
	 private void rebuildOrderByOffset() {
		    order.removeIf(id -> id == null || !map.containsKey(id) || map.get(id) == null);
		    order.sort(java.util.Comparator.comparingInt(id -> map.get(id).pos.getOffset()));
		    if (order.isEmpty()) cursor = -1;
		    else cursor = Math.min(Math.max(cursor, 0), order.size() - 1);
		}
	 
	// Définit/édite le note du marque-page le plus proche sur la même ligne
	 public Bookmark setNoteNearestOnSameLine(String note) {
	     Bookmark b = nearestOnSameLine();
	     if (b != null) b.note = (note == null || note.isBlank()) ? null : note;
	     return b;
	 }

	 // Définit/édite la note par id
	 public boolean setNote(String id, String note) {
	     Bookmark b = map.get(id);
	     if (b == null) return false;
	     b.note = (note == null || note.isBlank()) ? null : note;
	     return true;
	 }

	 // Récupère la note (utile pour pré-remplir le dialogue)
	 public String getNoteNearestOnSameLine() {
	     Bookmark b = nearestOnSameLine();
	     return b != null ? (b.note == null ? "" : b.note) : "";
	 }

	 @SuppressWarnings("serial")
	 public void editNoteForNearest(java.awt.Window owner) {
	     Bookmark b = nearestOnSameLine();
	     if (b == null) {
	         java.awt.Toolkit.getDefaultToolkit().beep();
	         return;
	     }

	     // --- Détection haut contraste Windows (si dispo)
	     boolean highContrast = false;
	     try {
	         Object hc = java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on");
	         highContrast = Boolean.TRUE.equals(hc);
	     } catch (Throwable ignore) { /* non Windows ou propriété indisponible */ }

	     // --- Palette accessible (clair vs haut contraste sombre)
	     java.awt.Color FG = highContrast ? java.awt.Color.WHITE : java.awt.Color.BLACK;
	     java.awt.Color BG = highContrast ? java.awt.Color.BLACK : java.awt.Color.WHITE;
	     java.awt.Color SEL_BG = highContrast ? java.awt.Color.YELLOW : new java.awt.Color(0xCCE5FF);
	     java.awt.Color SEL_FG = highContrast ? java.awt.Color.BLACK : java.awt.Color.BLACK;
	     java.awt.Color BORDER = highContrast ? new java.awt.Color(0xAAAAAA) : new java.awt.Color(0x444444);

	     // --- Taille de base (zoomable via Ctrl +/−/0)
	     final int[] fontPt = { 18 }; // point size par défaut, confortable pour DV

	     // --- Zone de saisie multi-ligne
	     javax.swing.JTextArea areaNote = new javax.swing.JTextArea(8, 40);
	     areaNote.setLineWrap(true);
	     areaNote.setWrapStyleWord(true);
	     areaNote.setText(b.note == null ? defaultNoteForCurrentLine() : b.note);
	     areaNote.setCaretPosition(0);
	     areaNote.setMargin(new java.awt.Insets(10, 12, 10, 12));
	     areaNote.setForeground(FG);
	     areaNote.setBackground(BG);
	     areaNote.setCaretColor(FG);
	     areaNote.setSelectionColor(SEL_BG);
	     areaNote.setSelectedTextColor(SEL_FG);
	     areaNote.setBorder(javax.swing.BorderFactory.createCompoundBorder(
	         javax.swing.BorderFactory.createLineBorder(BORDER, 2),
	         javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6)
	     ));
	     areaNote.getAccessibleContext().setAccessibleName("Note du marque-page");
	     areaNote.getAccessibleContext().setAccessibleDescription(
	         "Saisissez ou modifiez la note du marque-page. Entrée ou Contrôle+Entrée valide. Échap annule."
	     );

	     // --- Libellé
	     javax.swing.JLabel lbl = new javax.swing.JLabel("Note du marque-page :");
	     lbl.setDisplayedMnemonic('T'); // Alt+T -> focus zone
	     lbl.setLabelFor(areaNote);
	     lbl.setForeground(FG);
	     lbl.setBackground(BG);
	     lbl.setOpaque(true);
	     lbl.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 2, 6, 2));

	     // --- Scroll + panel
	     javax.swing.JScrollPane sp = new javax.swing.JScrollPane(
	         areaNote,
	         javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
	         javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
	     );
	     sp.getViewport().setBackground(BG);
	     sp.setBorder(javax.swing.BorderFactory.createLineBorder(BORDER, 1));
	     sp.getAccessibleContext().setAccessibleName("Zone d'édition de la note");
	     sp.getAccessibleContext().setAccessibleDescription("Zone défilante contenant la note du marque-page.");

	     javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(12, 12));
	     panel.setBackground(BG);
	     panel.add(lbl, java.awt.BorderLayout.NORTH);
	     panel.add(sp,  java.awt.BorderLayout.CENTER);
	     panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));

	     // --- Boutons clavier-accessibles (grands et contrastés)
	     final javax.swing.JButton okBtn = new javax.swing.JButton("OK");
	     okBtn.setMnemonic('O');
	     okBtn.getAccessibleContext().setAccessibleName("Valider");
	     okBtn.getAccessibleContext().setAccessibleDescription("Valider et enregistrer la note du marque-page.");
	     okBtn.setMargin(new java.awt.Insets(10, 18, 10, 18));
	     okBtn.setFocusPainted(true);

	     final javax.swing.JButton cancelBtn = new javax.swing.JButton("Annuler");
	     cancelBtn.setMnemonic('A');
	     cancelBtn.getAccessibleContext().setAccessibleName("Annuler");
	     cancelBtn.getAccessibleContext().setAccessibleDescription("Fermer sans enregistrer.");
	     cancelBtn.setMargin(new java.awt.Insets(10, 18, 10, 18));
	     cancelBtn.setFocusPainted(true);

	     // Harmoniser les couleurs boutons si haut contraste
	     if (highContrast) {
	         okBtn.setBackground(new java.awt.Color(0x202020));
	         okBtn.setForeground(FG);
	         cancelBtn.setBackground(new java.awt.Color(0x202020));
	         cancelBtn.setForeground(FG);
	     }

	     Object[] options = { okBtn, cancelBtn };

	     // --- OptionPane -> Dialog
	     javax.swing.JOptionPane op = new javax.swing.JOptionPane(
	         panel,
	         javax.swing.JOptionPane.PLAIN_MESSAGE,
	         javax.swing.JOptionPane.OK_CANCEL_OPTION,
	         null,
	         options,
	         okBtn
	     );

	     final javax.swing.JDialog dialog = op.createDialog(owner, "Note du marque-page");
	     dialog.setModal(true);
	     dialog.setResizable(true);
	     dialog.getAccessibleContext().setAccessibleName("Dialogue d'édition de la note de marque-page");
	     dialog.getAccessibleContext().setAccessibleDescription(
	         "Saisissez la note du marque-page. Tabulation pour aller aux boutons. Entrée valide. Échap annule. Ctrl plus ou Ctrl moins pour ajuster la taille du texte."
	     );

	     // --- Appliquer le zoom (police)
	     final Runnable applyZoom = () -> {
	         java.awt.Font f = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, Math.max(12, fontPt[0]));
	         java.awt.Font fBold = f.deriveFont(java.awt.Font.BOLD);

	         areaNote.setFont(f);
	         lbl.setFont(fBold);
	         okBtn.setFont(fBold);
	         cancelBtn.setFont(fBold);

	         // Optionnel : agrandir la taille mini du dialogue
	         java.awt.Dimension min = new java.awt.Dimension(
	             Math.max(420, 16 * fontPt[0]),
	             Math.max(260, 12 * fontPt[0])
	         );
	         dialog.setMinimumSize(min);
	         dialog.pack();
	     };
	     applyZoom.run();

	     // --- Actions OK/Annuler
	     okBtn.addActionListener(e -> { op.setValue(okBtn); dialog.dispose(); });
	     cancelBtn.addActionListener(e -> { op.setValue(cancelBtn); dialog.dispose(); });

	     // --- Entrée & Ctrl+Entrée => OK dans la zone
	     javax.swing.Action validateAction = new javax.swing.AbstractAction() {
	         @Override public void actionPerformed(java.awt.event.ActionEvent e) {
	             op.setValue(okBtn);
	             dialog.dispose();
	         }
	     };
	     areaNote.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
         .put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER,0), "doOKCtrl");
	     areaNote.getActionMap().put("doOKCtrl", validateAction);
 
	     areaNote.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
	             .put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK), "doOKCtrl");
	     areaNote.getActionMap().put("doOKCtrl", validateAction);

	     // --- Échap => Annuler (depuis n'importe où)
	     javax.swing.Action cancelAction = new javax.swing.AbstractAction() {
	         @Override public void actionPerformed(java.awt.event.ActionEvent e) {
	             op.setValue(cancelBtn);
	             dialog.dispose();
	         }
	     };
	     dialog.getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
	           .put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "doCancel");
	     dialog.getRootPane().getActionMap().put("doCancel", cancelAction);

	     // --- TAB/Shift+TAB sortent de la JTextArea vers les boutons
	     areaNote.setFocusTraversalKeysEnabled(false);
	     areaNote.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
	             .put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0), "focusNext");
	     areaNote.getActionMap().put("focusNext", new javax.swing.AbstractAction() {
	         @Override public void actionPerformed(java.awt.event.ActionEvent e) { areaNote.transferFocus(); }
	     });
	     areaNote.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
	             .put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, java.awt.event.InputEvent.SHIFT_DOWN_MASK), "focusPrev");
	     areaNote.getActionMap().put("focusPrev", new javax.swing.AbstractAction() {
	         @Override public void actionPerformed(java.awt.event.ActionEvent e) { areaNote.transferFocusBackward(); }
	     });

	     // --- Bouton par défaut = OK (Entrée le déclenche partout)
	     dialog.getRootPane().setDefaultButton(okBtn);

	     // --- Raccourcis ZOOM : Ctrl + / Ctrl - / Ctrl 0
	     javax.swing.InputMap rim = dialog.getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
	     javax.swing.ActionMap ram = dialog.getRootPane().getActionMap();
	     rim.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_EQUALS, java.awt.event.InputEvent.CTRL_DOWN_MASK), "zoomIn");   // Ctrl + (=/+)
	     rim.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ADD, java.awt.event.InputEvent.CTRL_DOWN_MASK), "zoomIn");
	     rim.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PLUS, java.awt.event.InputEvent.CTRL_DOWN_MASK), "zoomIn");
	     rim.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, java.awt.event.InputEvent.CTRL_DOWN_MASK), "zoomOut");
	     rim.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SUBTRACT, java.awt.event.InputEvent.CTRL_DOWN_MASK), "zoomOut");
	     rim.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, java.awt.event.InputEvent.CTRL_DOWN_MASK), "zoomReset");

	     ram.put("zoomIn", new javax.swing.AbstractAction() {
	         @Override public void actionPerformed(java.awt.event.ActionEvent e) { fontPt[0] = Math.min(36, fontPt[0] + 2); applyZoom.run(); }
	     });
	     ram.put("zoomOut", new javax.swing.AbstractAction() {
	         @Override public void actionPerformed(java.awt.event.ActionEvent e) { fontPt[0] = Math.max(12, fontPt[0] - 2); applyZoom.run(); }
	     });
	     ram.put("zoomReset", new javax.swing.AbstractAction() {
	         @Override public void actionPerformed(java.awt.event.ActionEvent e) { fontPt[0] = 18; applyZoom.run(); }
	     });

	     // --- Focus initial dans la zone + sélection
	     dialog.addWindowListener(new java.awt.event.WindowAdapter() {
	    	    @SuppressWarnings("deprecation")
				@Override public void windowOpened(java.awt.event.WindowEvent e) {
	    	        areaNote.requestFocusInWindow();
	    	        // Ne pas sélectionner tout : place juste le caret au début de la note
	    	        javax.swing.SwingUtilities.invokeLater(() -> {
	    	            areaNote.setSelectionStart(0);
	    	            areaNote.setSelectionEnd(0);
	    	            areaNote.setCaretPosition(0);
	    	            try {
	    	                java.awt.Rectangle r = areaNote.modelToView(0);
	    	                if (r != null) areaNote.scrollRectToVisible(r);
	    	            } catch (javax.swing.text.BadLocationException ignore) {}
	    	        });
	    	    }
	    	});

	     dialog.pack();
	     dialog.setLocationRelativeTo(owner);
	     dialog.setVisible(true);

	     // --- Résultat
	     Object val = op.getValue();
	     if (val == okBtn) {
	         String txt = areaNote.getText();
	         b.note = (txt == null || txt.isBlank()) ? null : txt;
	     }
	 }



 
}
