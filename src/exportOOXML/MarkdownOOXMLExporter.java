package exportOOXML;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.VerticalAlign;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

import writer.commandes;

public final class MarkdownOOXMLExporter {

    private MarkdownOOXMLExporter() {}

    // --- Regex blocs ---
    private static final Pattern H_P  = Pattern.compile("^#P\\.(.*)$");
    private static final Pattern H_S  = Pattern.compile("^#S\\.(.*)$");
    private static final Pattern H1   = Pattern.compile("^#1\\.(.*)$");
    private static final Pattern H2   = Pattern.compile("^#2\\.(.*)$");
    private static final Pattern H3   = Pattern.compile("^#3\\.(.*)$");
    private static final Pattern H4   = Pattern.compile("^#4\\.(.*)$");
    private static final Pattern H5   = Pattern.compile("^#5\\.(.*)$");

    private static final Pattern OL   = Pattern.compile("^\\s*([0-9]+)\\.\\s+(.*)$");
    private static final Pattern UL   = Pattern.compile("^\\s*-\\.(.*)$");

    private static final Pattern PAGE_BREAK = Pattern.compile("^\\s*@saut\\s+de\\s+page\\s+manuel\\b.*$");

    // --- Regex inline (même ordre de traitement que pour ODT) ---
    private static final Pattern BOLD_ITALIC      = Pattern.compile("\\*\\^(.+?)\\^\\*");
    private static final Pattern UNDERLINE_BOLD   = Pattern.compile("_\\*(.+?)\\*_");
    private static final Pattern UNDERLINE_ITALIC = Pattern.compile("_\\^(.+?)\\^_");
    private static final Pattern BOLD             = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern UNDERLINE        = Pattern.compile("__(.+?)__");
    private static final Pattern ITALIC           = Pattern.compile("\\^\\^(.+?)\\^\\^");
    private static final Pattern FOOTNOTE         = Pattern.compile("@\\((.+?)\\)");
    private static final Pattern EXPOSANT         = Pattern.compile("\\^¨(.+?)¨\\^");
    private static final Pattern INDICE           = Pattern.compile("_¨(.+?)¨_");
    
    private static final Pattern LINK = Pattern.compile("@\\[([^:]+?):\\s*(https?://[^\\]]+)\\]");

    private enum ListKind { NONE, ORDERED, UNORDERED }

    // ---------- API ----------
    /** Exporte la chaîne « BlindWriter-Markdown » vers un .docx. */
    public static void export(String src, File outFile) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
        	ensureWordBuiltinStyles(doc);
            applyMetadata(doc);

            // Crée deux schémas de numérotation : 1) ordonnée  2) puces
            BigInteger numIdOrdered   = ensureNumbering(doc, true);
            BigInteger numIdUnordered = ensureNumbering(doc, false);

            boolean pageBreakForNextParagraph = false;
            ListKind listState = ListKind.NONE;
            IntBox footBox = new IntBox(1);

            String[] lines = src.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

            for (String rawLine : lines) {
                String line = rawLine;

                // Ligne vide -> on sort du mode "liste" et on ajoute un paragraphe vide (sans numérotation)
                if (line.trim().isEmpty()) {
                    listState = ListKind.NONE;
                    doc.createParagraph();
                    continue;
                }

                // Saut de page manuel -> s'applique au paragraphe SUIVANT
                if (PAGE_BREAK.matcher(line).matches()) {
                    listState = ListKind.NONE;
                    pageBreakForNextParagraph = true;
                    continue;
                }

                Matcher m;

                // #P. -> Title (jamais de setNumID ici)
                m = H_P.matcher(line);
                if (m.matches()) {
                    listState = ListKind.NONE;
                    String txt = m.group(1).trim();
                    if (!txt.isEmpty()) {
                        XWPFParagraph p = doc.createParagraph();
                        p.setStyle("Title");
                        if (pageBreakForNextParagraph) { p.setPageBreak(true); pageBreakForNextParagraph = false; }
                        appendInlineRuns(doc, p, txt, footBox);
                    }
                    continue;
                }

                // #S. -> Subtitle (pas de setNumID)
                m = H_S.matcher(line);
                if (m.matches()) {
                    listState = ListKind.NONE;
                    String txt = m.group(1).trim();
                    if (!txt.isEmpty()) {
                        XWPFParagraph p = doc.createParagraph();
                        p.setStyle("Subtitle");
                        if (pageBreakForNextParagraph) { p.setPageBreak(true); pageBreakForNextParagraph = false; }
                        appendInlineRuns(doc, p, txt, footBox);
                    }
                    continue;
                }

                // Titres #1. .. #5. (pas de setNumID)
                int headingLevel = 0;
                String txt = null;
                if ((m = H1.matcher(line)).matches()) { headingLevel = 1; txt = m.group(1).trim(); }
                else if ((m = H2.matcher(line)).matches()) { headingLevel = 2; txt = m.group(1).trim(); }
                else if ((m = H3.matcher(line)).matches()) { headingLevel = 3; txt = m.group(1).trim(); }
                else if ((m = H4.matcher(line)).matches()) { headingLevel = 4; txt = m.group(1).trim(); }
                else if ((m = H5.matcher(line)).matches()) { headingLevel = 5; txt = m.group(1).trim(); }

                if (headingLevel > 0) {
                    listState = ListKind.NONE;
                    if (txt != null && !txt.isEmpty()) {
                        XWPFParagraph p = doc.createParagraph();
                        p.setStyle("Heading" + headingLevel); // "Heading1"..."Heading5"
                        if (pageBreakForNextParagraph) { p.setPageBreak(true); pageBreakForNextParagraph = false; }
                        appendInlineRuns(doc, p, txt, footBox);
                    }
                    continue;
                }

                // Liste numérotée -> ICI on pose numIdOrdered
                m = OL.matcher(line);
                if (m.matches()) {
                    String itemTxt = m.group(2).trim();
                    if (listState != ListKind.ORDERED) listState = ListKind.ORDERED;

                    XWPFParagraph p = doc.createParagraph();
                    p.setNumID(numIdOrdered);
                    if (pageBreakForNextParagraph) { p.setPageBreak(true); pageBreakForNextParagraph = false; }
                    if (!itemTxt.isEmpty()) appendInlineRuns(doc, p, itemTxt, footBox);
                    continue;
                }

                // Liste à puces -> ICI on pose numIdUnordered
                m = UL.matcher(line);
                if (m.matches()) {
                    String itemTxt = m.group(1).trim();
                    if (listState != ListKind.UNORDERED) listState = ListKind.UNORDERED;

                    XWPFParagraph p = doc.createParagraph();
                    p.setNumID(numIdUnordered);
                    if (pageBreakForNextParagraph) { p.setPageBreak(true); pageBreakForNextParagraph = false; }
                    if (!itemTxt.isEmpty()) appendInlineRuns(doc, p, itemTxt, footBox);
                    continue;
                }

                // Paragraphe normal (PAS de setNumID)
                listState = ListKind.NONE;
                XWPFParagraph p = doc.createParagraph();
                if (pageBreakForNextParagraph) { p.setPageBreak(true); pageBreakForNextParagraph = false; }
                appendInlineRuns(doc, p, line.trim(), footBox);
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                doc.write(fos);
            }
        }
    }

    // ---------- Helpers ----------

    // Crée (si besoin) 2 schémas de numérotation : décimal/suffixe "." et puces "•"
    private static BigInteger ensureNumbering(XWPFDocument doc, boolean ordered) throws Exception {
        XWPFNumbering numbering = doc.createNumbering();

        CTAbstractNum ctAbs = CTAbstractNum.Factory.newInstance();
        ctAbs.setAbstractNumId(BigInteger.valueOf(ordered ? 1 : 2));

        CTLvl lvl = ctAbs.addNewLvl();
        lvl.setIlvl(BigInteger.ZERO);

        if (ordered) {
            lvl.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
            lvl.addNewLvlText().setVal("%1.");   // 1.
            // (pas de lvl.addNewSuff())
        } else {
            lvl.addNewNumFmt().setVal(STNumberFormat.BULLET);
            lvl.addNewLvlText().setVal("•");     // puce
            // (pas de lvl.addNewSuff())
        }

        XWPFAbstractNum abs = new XWPFAbstractNum(ctAbs, numbering);
        BigInteger abstractNumId = numbering.addAbstractNum(abs);
        return numbering.addNum(abstractNumId);
    }


    // Petit conteneur mutable pour le compteur de notes
    private static final class IntBox {
        private int v;
        IntBox(int v) { this.v = v; }
        int get() { return v; }
        void inc() { v++; }
    }

    // Styles inline -> runs
    private enum K { TEXT, BOLD, ITALIC, UNDERLINE, BOLDITALIC, UNDERBOLD, UNDERITALIC, EXPOSANT, INDICE, FOOTNOTE, LINK }
    private static final class InlineToken {
        final K kind; final String content;
        InlineToken(K k, String c) { kind = k; content = c; }
    }

	private static void appendInlineRuns(XWPFDocument doc, XWPFParagraph p, String text, IntBox footCounter) {
		List<InlineToken> tokens = tokenizeInline(text);
		if (tokens.isEmpty()) return;
        

        for (InlineToken tk : tokens) {
        	if (tk.content == null || tk.content.isBlank()) continue;
            switch (tk.kind) {
                case TEXT -> appendStyledTextWithTabs(p, tk.content, false, false, null, null);
                case BOLDITALIC -> appendStyledTextWithTabs(p, tk.content, true, true, null, null);
                case BOLD -> appendStyledTextWithTabs(p, tk.content, true, false, null, null);
                case UNDERLINE -> appendStyledTextWithTabs(p, tk.content, false, false, UnderlinePatterns.SINGLE, null);
                case UNDERBOLD -> appendStyledTextWithTabs(p, tk.content, true, false, UnderlinePatterns.SINGLE, null);
                case UNDERITALIC -> appendStyledTextWithTabs(p, tk.content, false, true, UnderlinePatterns.SINGLE, null);
                case ITALIC -> appendStyledTextWithTabs(p, tk.content, false, true, null, null);
                case EXPOSANT -> appendStyledTextWithTabs(p, tk.content, false, false, null, VerticalAlign.SUPERSCRIPT);
                case INDICE -> appendStyledTextWithTabs(p, tk.content, false, false, null, VerticalAlign.SUBSCRIPT);
                case FOOTNOTE -> {
                    XWPFRun refRun = p.createRun();
                    refRun.getCTR().addNewFootnoteReference().setId(BigInteger.valueOf(footCounter.get()));
                    XWPFFootnote fn = doc.createFootnote();
                    fn.getCTFtnEdn().setId(BigInteger.valueOf(footCounter.get()));
                    XWPFParagraph fp = fn.createParagraph();
                    appendStyledTextWithTabs(fp, tk.content, false, false, null, null);
                    footCounter.inc();
                }
                case LINK -> {
                    String[] parts = tk.content.split(":", 2);
                    if (parts.length == 2) {
                        String label = parts[0].trim();
                        String url = parts[1].trim();
                        try {
                            XWPFHyperlinkRun linkRun = createHyperlinkRun(p, url);
                            linkRun.setText(label);
                            linkRun.setColor("0000FF");
                            linkRun.setUnderline(UnderlinePatterns.SINGLE);
                        } catch (Exception e) {
                            XWPFRun r = p.createRun();
                            r.setText(label + " (" + url + ")");
                        }
                    } else {
                        appendStyledTextWithTabs(p, tk.content, false, false, null, null);
                    }
                }



                
            }
        }
    }


    // Tokenizer inline (calqué sur ton ODT)
    private static List<InlineToken> tokenizeInline(String src) {
        List<InlineToken> out = new ArrayList<>();
        int idx = 0;
        while (idx < src.length()) {
            Match best = findNextMatch(src, idx);
            if (best == null) {
                out.add(new InlineToken(K.TEXT, src.substring(idx)));
                break;
            }
            if (best.start > idx) {
                out.add(new InlineToken(K.TEXT, src.substring(idx, best.start)));
            }
            switch (best.kind) {
                case 0: out.add(new InlineToken(K.BOLDITALIC, best.inner)); break;   // *^ ^*
                case 1: out.add(new InlineToken(K.UNDERBOLD, best.inner)); break;    // _* *_
                case 2: out.add(new InlineToken(K.UNDERITALIC, best.inner)); break;  // _^ ^_
                case 3: out.add(new InlineToken(K.BOLD, best.inner)); break;         // ** **
                case 4: out.add(new InlineToken(K.UNDERLINE, best.inner)); break;    // __ __
                case 5: out.add(new InlineToken(K.ITALIC, best.inner)); break;       // ^^ ^^
                case 6: out.add(new InlineToken(K.FOOTNOTE, best.inner)); break;     // @(...)
                case 7: out.add(new InlineToken(K.EXPOSANT, best.inner)); break;     // ^¨ ¨^
                case 8: out.add(new InlineToken(K.INDICE, best.inner)); break;       // _¨ ¨_
                case 9: out.add(new InlineToken(K.LINK, best.inner)); break;
            }
            idx = Math.max(idx + 1, best.end);
        }
        return out;
    }

    private static final class Match {
        final int kind; final int start, end; final String inner;
        Match(int k, int s, int e, String in) { kind = k; start = s; end = e; inner = in; }
    }

    private static Match findNextMatch(String s, int from) {
        Matcher[] ms = new Matcher[] {
            BOLD_ITALIC.matcher(s),      // 0
            UNDERLINE_BOLD.matcher(s),   // 1
            UNDERLINE_ITALIC.matcher(s), // 2
            BOLD.matcher(s),             // 3
            UNDERLINE.matcher(s),         // 4
            ITALIC.matcher(s),           // 5
            FOOTNOTE.matcher(s),         // 6
            EXPOSANT.matcher(s),         // 7
            INDICE.matcher(s),            // 8
            LINK.matcher(s)              // 9
        };
        Match best = null;
        for (int k = 0; k < ms.length; k++) {
            Matcher m = ms[k];
            if (m.find(from)) {
            	String inner;
            	if (k == 9 && m.groupCount() >= 2) {
            	    // Pour les liens, on garde "titre: url"
            	    inner = m.group(1).trim() + ": " + m.group(2).trim();
            	} else {
            	    inner = m.group(1);
            	}
            	Match cur = new Match(k, m.start(), m.end(), inner);

                if (best == null || cur.start < best.start) best = cur;
            }
        }
        return best;
    }

    // Métadonnées (équivalentes au code ODF)
    private static void applyMetadata(XWPFDocument doc) {
	    var props = doc.getProperties();               // type inféré: POIXMLProperties
	    var core  = props.getCoreProperties();         // type inféré: POIXMLProperties.CoreProperties
	
	    try {
	        String titre = commandes.meta.retourneFirstEnfant("titre").getAttributs("LeTitre");
	        if (titre != null && !titre.isBlank()) core.setTitle(titre);
	
	        String sujet = commandes.meta.retourneFirstEnfant("sujet").getAttributs("LeSujet");
	        if (sujet != null && !sujet.isBlank()) core.setSubjectProperty(sujet);
	
	        String auteur = commandes.meta.retourneFirstEnfant("auteur").getAttributs("nom");
	        if (auteur != null && !auteur.isBlank()) core.setCreator(auteur);
	
	        var mots = commandes.meta.retourneFirstEnfant("motsCles");
	        if (mots != null) {
	            String kw = mots.getAttributs("mots");
	            if (kw != null && !kw.isBlank()) core.setKeywords(kw);
	        }
	
	        String desc = commandes.meta.retourneFirstEnfant("description").getAttributs("resume");
	        if (desc != null && !desc.isBlank()) core.setDescription(desc);
	
	        String creationIso =
	            (commandes.meta.retourneFirstEnfant("date_creation").getAttributs("date") != null
	             && !commandes.meta.retourneFirstEnfant("date_creation").getAttributs("date").isBlank())
	            ? commandes.meta.retourneFirstEnfant("date_creation").getAttributs("date").trim()
	            : java.time.OffsetDateTime.now().toString();
	
	        var createdDate = java.util.Date.from(java.time.OffsetDateTime.parse(creationIso).toInstant());
	        core.setCreated(java.util.Optional.of(createdDate));
	
	        var societyNode = commandes.meta.retourneFirstEnfant("society");
	        String soc = (societyNode != null) ? societyNode.getAttributs("nom") : null;
	        if (soc != null && !soc.isBlank()) {
	            props.getCustomProperties().addProperty("Société", soc);
	        }
	    } catch (Exception ignore) { }
    }

    // --- Démo rapide (peut être supprimée) ---
    public static void main(String[] args) throws Exception {
        String demo = String.join("\n",
            "#P. Titre principale",
            "#S. Sous-titre",
            "#1. Section 1 **important** et ^^italic^^ et __souligné__ et *^fort et penché^*.",
            "",
            "1. liste numéroté 1",
            "2. liste numéroté 2 @(note pour la 2)",
            "3. liste numéroté 3",
            "",
            "-. liste non numéroté (puce)",
            "-. deuxième puce",
            "",
            "@saut de page manuel",
            "Paragraphe après saut de page, avec une @(note de bas de page).",
            "Chimie : H_¨2¨_O ; Math : x^¨2¨^ + y_¨3¨_."
        );
        File f = new File("export_demo.docx");
        export(demo, f);
        System.out.println("DOCX écrit : " + f.getAbsolutePath());
    }
    
    // Ajoute si besoin un style de paragraphe basique
 // Ajoute si besoin un style de paragraphe (sans getCTStyles)
    private static void ensureParaStyle(XWPFDocument doc, String styleId, String uiName, Integer outlineLvl) {
        XWPFStyles styles = doc.createStyles();               // crée la table si absente
        if (styles.styleExist(styleId)) return;

        // Crée le CTStyle bas-niveau
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle ct =
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle.Factory.newInstance();
        ct.setStyleId(styleId);

        // <w:name w:val="..."/>
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString nm =
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString.Factory.newInstance();
        nm.setVal(uiName);
        ct.setName(nm);

        // <w:type w:val="paragraph"/>
        ct.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType.PARAGRAPH);

        // <w:qFormat/> pour l’afficher dans la galerie
        ct.addNewQFormat();

        // Propriétés de paragraphe (styles)
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral ppr =
                ct.isSetPPr() ? ct.getPPr() : ct.addNewPPr();

        if (outlineLvl != null) {
            // 0 = Heading 1, 1 = Heading 2, ...
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber ol =
                    ppr.isSetOutlineLvl() ? ppr.getOutlineLvl() : ppr.addNewOutlineLvl();
            ol.setVal(java.math.BigInteger.valueOf(outlineLvl));

            // garder avec le suivant (comportement typique des titres)
            if (!ppr.isSetKeepNext()) ppr.addNewKeepNext();
        }

        // Propriétés de run (laisse vide pour ne pas sur-styliser)
        if (!ct.isSetRPr()) ct.addNewRPr();

        // Enveloppe XWPF + ajout dans la table
        org.apache.poi.xwpf.usermodel.XWPFStyle style = new org.apache.poi.xwpf.usermodel.XWPFStyle(ct);
        styles.addStyle(style);
    }

    /** Injecte Title, Subtitle, Heading1..5 si absents */
    private static void ensureWordBuiltinStyles(XWPFDocument doc) {
        ensureParaStyle(doc, "Title",    "Title",    null);
        ensureParaStyle(doc, "Subtitle", "Subtitle", null);
        ensureParaStyle(doc, "Heading1", "heading 1", 0);
        ensureParaStyle(doc, "Heading2", "heading 2", 1);
        ensureParaStyle(doc, "Heading3", "heading 3", 2);
        ensureParaStyle(doc, "Heading4", "heading 4", 3);
        ensureParaStyle(doc, "Heading5", "heading 5", 4);
    }

    /** Version “stylée” : applique bold/italic/underline/verticalAlign à chaque fragment + aux tabs. */
    private static void appendStyledTextWithTabs(XWPFParagraph p, String text, boolean bold, boolean italic, 
    		UnderlinePatterns underline, VerticalAlign vAlign ) {
        String[] parts = text.split("\\[tab\\]", -1);
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                XWPFRun r = p.createRun();
                if (bold) r.setBold(true);
                if (italic) r.setItalic(true);
                if (underline != null) r.setUnderline(underline);
                if (vAlign != null) r.setSubscript(vAlign);
                r.setText(parts[i]);
            }
            if (i < parts.length - 1) {
                XWPFRun rt = p.createRun();
                if (bold) rt.setBold(true);
                if (italic) rt.setItalic(true);
                if (underline != null) rt.setUnderline(underline);
                if (vAlign != null) rt.setSubscript(vAlign);
                rt.addTab(); // vrai tab
            }
        }
    }
    


    private static XWPFHyperlinkRun createHyperlinkRun(XWPFParagraph paragraph, String uri) {
        // 1️⃣ Ajoute une relation externe "hyperlink" dans le document principal
        String rId = paragraph.getDocument().getPackagePart()
                .addExternalRelationship(uri, XWPFRelation.HYPERLINK.getRelation())
                .getId();

        // 2️⃣ Crée le nœud <w:hyperlink r:id="...">
        CTHyperlink ctHyperlink = paragraph.getCTP().addNewHyperlink();
        ctHyperlink.setId(rId);
        ctHyperlink.addNewR(); // crée le premier run à l’intérieur

        // 3️⃣ Retourne un vrai XWPFHyperlinkRun
        return new XWPFHyperlinkRun(ctHyperlink, ctHyperlink.getRArray(0), paragraph);
    }

   
    

}
