package Import;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;

import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.w3c.dom.Node;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;



/**
 * DocxReader : extrait texte structuré depuis un .docx en conservant
 * titres (#N.), listes et styles gras/italique/souligné (combinaisons).
 */
public class DocxReader {
	
	// --- state pour générer des numéros incrémentés lors de l'import ---
	private static final java.util.Map<java.math.BigInteger, java.util.Map<Integer, Integer>> LIST_COUNTERS = new java.util.HashMap<>();


    private static class TextStyle {
        boolean bold;
        boolean italic;
        boolean underline;
        TextStyle() {}
        @SuppressWarnings("unused")
		TextStyle(boolean b, boolean i, boolean u) { bold=b; italic=i; underline=u; }
        boolean isEmpty(){ return !bold && !italic && !underline; }
    }

    public static String extractStructuredTextFromDocx(String docxPath) throws Exception {
    	LIST_COUNTERS.clear();
    	StringBuilder sb = new StringBuilder();
        try (InputStream in = new FileInputStream(docxPath);
             XWPFDocument doc = new XWPFDocument(in)) {
        	for (IBodyElement be : doc.getBodyElements()) {
        	    if (be.getElementType() == BodyElementType.PARAGRAPH) {
        	        XWPFParagraph p = (XWPFParagraph) be;
        	        handleParagraph(p, doc, sb);
        	    } else if (be.getElementType() == BodyElementType.TABLE) {
        	        XWPFTable t = (XWPFTable) be;
        	        emitLisioTable(t, doc, sb);   // ⬅️ nouveau
        	    }
        	}
        }
        return sb.toString();
    }

    @SuppressWarnings("unused")
	private static void handleParagraph(XWPFParagraph p, XWPFDocument doc, StringBuilder sb) {
        // 1) détecter niveau de titre : d'abord outline-level dans pPr, sinon via style id/name
        int headingLevel = -1;

        // a) outline-level dans la structure XML (si présent)
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr ppr = p.getCTP().getPPr();
        
        // Paragraphe forcé à commencer sur une nouvelle page
        if (ppr != null && ppr.isSetPageBreakBefore()) {
        	appendManualPageBreakLineNoDup(sb);
        }
        
        if (ppr != null && ppr.getOutlineLvl() != null) {
            try {
                java.math.BigInteger val = ppr.getOutlineLvl().getVal();
                if (val != null) {
                    // IMPORTANT : outline-level est 0-based dans le XML Word -> +1
                    headingLevel = val.intValue() + 1;
                }
            } catch (Exception ignored) {}
        }

        // b) sinon via style (styleId peut être "Heading1", "Heading 1", "Titre1"...)
        if (headingLevel == -1) {
            String styleId = p.getStyle();
            if (styleId != null && !styleId.isEmpty()) {
                headingLevel = headingLevelFromStyleName(styleId);
            } else {
                // tenter via getStyleID fallback / style name
                org.apache.poi.xwpf.usermodel.XWPFStyle style = null;
                try {
                    org.apache.poi.xwpf.usermodel.XWPFStyles styles = p.getDocument().getStyles();
                    if (styles != null && p.getStyleID() != null) {
                        style = styles.getStyle(p.getStyleID());
                    }
                } catch (Exception ignored) {}
                if (style != null) {
                    String name = style.getName();
                    headingLevel = headingLevelFromStyleName(name);
                }
            }
        }

        // 2) détecter si paragraphe numéroté (liste)
        boolean isNumbered = p.getNumID() != null;

        // 4) ajouter préfixe (titre ou liste)
        if (headingLevel > 0 && headingLevel < 11) {
            sb.append("#").append(headingLevel).append(". ");
        } else if (headingLevel == 11) {
            sb.append("#P. ");
        } else if (headingLevel == 12) {
            sb.append("#S. ");
        } else if (isNumbered) {
            // identifier le format (bullet vs decimal) en essayant plusieurs sources
            java.math.BigInteger numId = null;
            java.math.BigInteger ilvl = null;
            String numFmt = null;
            String numLevelText = null;
            try { numId = p.getNumID(); } catch (Exception ignored) {}
            try { ilvl = p.getNumIlvl(); } catch (Exception ignored) {}
            try { numFmt = p.getNumFmt(); } catch (Exception ignored) {}
            try { numLevelText = p.getNumLevelText(); } catch (Exception ignored) {}

            // fallback : interroger la définition dans XWPFNumbering / XWPFNum / XWPFAbstractNum
            if ((numFmt == null || numFmt.isEmpty()) && numId != null) {
                try {
                    org.apache.poi.xwpf.usermodel.XWPFNumbering numbering = p.getDocument().getNumbering();
                    if (numbering != null) {
                        org.apache.poi.xwpf.usermodel.XWPFNum xnum = numbering.getNum(numId);
                        if (xnum != null) {
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNum ctNum = xnum.getCTNum();
                            if (ctNum != null && ctNum.getAbstractNumId() != null) {
                                java.math.BigInteger absId = ctNum.getAbstractNumId().getVal();
                                if (absId != null) {
                                    org.apache.poi.xwpf.usermodel.XWPFAbstractNum wab = numbering.getAbstractNum(absId);
                                    if (wab != null) {
                                        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum ctAbs = wab.getCTAbstractNum();
                                        if (ctAbs != null) {
                                            int levelIndex = 0;
                                            if (ilvl != null) {
                                                levelIndex = Math.max(0, Math.min(ctAbs.sizeOfLvlArray() - 1, ilvl.intValue()));
                                            }
                                            try {
                                                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl lvl = ctAbs.getLvlArray(levelIndex);
                                                if (lvl != null) {
                                                    if (lvl.isSetNumFmt() && lvl.getNumFmt() != null && lvl.getNumFmt().getVal() != null) {
                                                        numFmt = lvl.getNumFmt().getVal().toString();
                                                    }
                                                    if (lvl.isSetLvlText() && lvl.getLvlText() != null && lvl.getLvlText().getVal() != null) {
                                                        numLevelText = lvl.getLvlText().getVal();
                                                    }
                                                }
                                            } catch (Exception ignored) {
                                                // ignore
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // safe: ne pas planter l'import pour un fichier exotique
                }
            }

            // Déterminer s'il s'agit d'une puce (bullet)
            boolean isBullet = false;
            if (numFmt != null) {
                if ("bullet".equalsIgnoreCase(numFmt) || "char".equalsIgnoreCase(numFmt)) isBullet = true;
            }
            if (!isBullet && numLevelText != null) {
                String nt = numLevelText.trim();
                if (nt.contains("\u2022") || nt.contains("\u00B7") || nt.startsWith("-") || nt.startsWith("•") || nt.startsWith("·")) {
                    isBullet = true;
                }
            }

            // Respecter indentation (ilvl) en ajoutant des tabulations (remplace par espaces si tu préfères)
            int level = 0;
            if (ilvl != null) {
                try { level = ilvl.intValue(); } catch (Exception ignored) {}
            }
            for (int i = 0; i < level; ++i) sb.append("[tab]");

            if (isBullet) {
                sb.append("-. ");
            } else {
                // ici on récupère le prochain numéro pour cette liste+niveau
                int num = nextListNumber(numId, level);
                sb.append(num).append(". ");
            }
        }

        boolean paraHasModernPic = paragraphHasModernPicture(p);
        
        // 5) parcourir les runs
        java.util.List<org.apache.poi.xwpf.usermodel.XWPFRun> runs = null;
        try { runs = p.getRuns(); } catch (Exception ignored) { runs = null; }

        boolean wrotePageBreakInThisPara = false;
        boolean hadVisibleText = false; // <-- pour savoir si on a réussi à écrire quelque chose

        if (runs != null && !runs.isEmpty()) {
            int i = 0;
            while (i < runs.size()) {
                XWPFRun run = runs.get(i);

                // === 🟦 Cas spécial : lien hypertexte ===
                if (run instanceof XWPFHyperlinkRun hrun) {
                    String url = null;
                    try {
                        if (hrun.getHyperlink(doc) != null)
                            url = hrun.getHyperlink(doc).getURL();
                    } catch (Exception ignored) {}
                    if ((url == null || url.isBlank()) && hrun.getHyperlinkId() != null) {
                        try {
                            var link = doc.getHyperlinkByID(hrun.getHyperlinkId());
                            if (link != null) url = link.getURL();
                        } catch (Exception ignored) {}
                    }

                    // Fusionner tous les runs du même lien
                    StringBuilder linkText = new StringBuilder();
                    while (i < runs.size()) {
                        XWPFRun r = runs.get(i);
                        if (r instanceof XWPFHyperlinkRun hr2) {
                            String u2 = null;
                            try {
                                if (hr2.getHyperlink(doc) != null)
                                    u2 = hr2.getHyperlink(doc).getURL();
                                if ((u2 == null || u2.isBlank()) && hr2.getHyperlinkId() != null) {
                                    var link = doc.getHyperlinkByID(hr2.getHyperlinkId());
                                    if (link != null) u2 = link.getURL();
                                }
                            } catch (Exception ignored) {}
                            if (url != null && url.equals(u2)) {
                                if (hr2.text() != null) linkText.append(hr2.text());
                                i++;
                                continue;
                            }
                        }
                        break;
                    }

                    String text = normalizeSpaces(linkText.toString());
                    if (!text.isBlank() && url != null && !url.isBlank()) {
                        sb.append("@[").append(text).append(": ").append(url).append("]");
                        hadVisibleText = true;
                    }
                    continue; // saute les runs déjà fusionnés
                }

                // === 🖼️ Images (inline/anchor/VML/embedded) ===
                try {
                	if (emitPicturesFromRun(run, doc, sb, paraHasModernPic)) {
                	    hadVisibleText = true;
                	}
                } catch (Exception ignored) {}

                
                // === ⬛ Sauts de page dans le run: <w:br w:type="page"/> ===
                try {
                    var ctr = run.getCTR();
                    if (ctr != null) {
                        // Parcourt tous les <w:br/>
                        java.util.List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr> brs = ctr.getBrList();
                        if (brs != null) {
                            for (var br : brs) {
                                org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType.Enum tp =
                                        (br != null && br.isSetType()) ? br.getType() : null;
                                if (tp == org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType.PAGE) {
                                    appendManualPageBreakLineNoDup(sb);
                                    wrotePageBreakInThisPara = true; // si tu veux t’en servir plus tard
                                }
                            }
                        }
                        // ⚠ on ignore volontairement <w:lastRenderedPageBreak/> (pas un saut manuel)
                    }
                } catch (Exception ignored) {}
                
                // === 🟩 Cas général : run normal ===
                String text = getRunVisibleText(run);
                TextStyle ts = new TextStyle();
                try { ts.bold = run.isBold(); } catch (Exception ignored) {}
                try { ts.italic = run.isItalic(); } catch (Exception ignored) {}
                try {
                    var u = run.getUnderline();
                    ts.underline = (u != null && u != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE);
                } catch (Exception ignored) {}

                if (text != null && !text.isEmpty()) {
                    String styled = wrapWithMarkers(text, ts);
                    styled = wrapSuperSub(styled, run);
                    sb.append(styled);
                    hadVisibleText = true;
                }

                // === 🟨 Notes de bas de page ===
                try {
                    var ctr = run.getCTR();
                    var refs = ctr.getFootnoteReferenceList();
                    if (refs != null && !refs.isEmpty()) {
                        for (var r : refs) {
                            var id = r.getId();
                            String note = extractFootnoteInlineText(doc, id);
                            if (!note.isBlank()) {
                                sb.append("@(").append(note).append(")");
                                hadVisibleText = true;
                            }
                        }
                    }
                } catch (Exception ignored) {}

                i++;
            }
        }



        // 🔁 Fallback : si on n'a rien écrit depuis les runs, relire le paragraphe en entier
        // (compte aussi les <w:tab/> qui ne sont pas dans <w:t>)
        if (!hadVisibleText) {
            String vis = paragraphToVisible(p);   // <-- ton helper existant
            if (!vis.isEmpty()) {
                sb.append(vis);
                hadVisibleText = true;
            }
        }

         
        boolean sectionForcesNextPage = false;
        try {
            if (ppr != null && ppr.getSectPr() != null && ppr.getSectPr().isSetType()) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr sect = ppr.getSectPr();
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark.Enum st = sect.getType().getVal();
                // Ces types provoquent un saut de page après le paragraphe courant
                sectionForcesNextPage =
                    st == org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark.NEXT_PAGE ||
                    st == org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark.EVEN_PAGE ||
                    st == org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark.ODD_PAGE;
            }
        } catch (Exception ignored) {}

        if (sectionForcesNextPage) {
            // finir la ligne du paragraphe courant
            if (sb.length() == 0 || sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
            // insérer la ligne de saut de page
            appendManualPageBreakLineNoDup(sb);
        }

        // 6) fin de paragraphe
        sb.append("\n");
    }



    private static int headingLevelFromStyleName(String style) {
        if (style == null) return -1;
        String s = style.trim();
        String low = s.toLowerCase();

        // Cas explicites : Title / Titre (sans numéro) -> niveau spécial 11 (Titre principal)
        if (low.equals("title") || low.equals("titre") || low.contains("titre principal")
            || low.equals("titreprincipal") || low.equals("main title") || low.equals("main_title")) {
            return 11; // #P.
        }
        // Subtitle / Sous-titre -> 12
        if (low.equals("subtitle") || low.contains("sous-titre") || low.contains("soustitre") || low.equals("sous_titre")) {
            return 12; // #S.
        }

        // Chercher tous les entiers dans la chaîne et prendre le DERNIER (ex: "Heading_20_1" -> 1)
        Matcher mAll = Pattern.compile("(\\d{1,2})").matcher(s);
        int lastFound = -1;
        while (mAll.find()) {
            try {
                lastFound = Integer.parseInt(mAll.group(1));
            } catch (Exception ignored) {}
        }
        if (lastFound != -1) {
            // normaliser / clamp
            if (lastFound < 1) return -1;
            if (lastFound > 10) lastFound = 10;
            return lastFound;
        }

        // Cas spécifiques supplémentaires (tentatives sur formes exotiques)
        Matcher mH = Pattern.compile("(?i).*heading[_\\s]*20[_\\s]*(\\d{1,2}).*").matcher(s);
        if (mH.find()) try { return Integer.parseInt(mH.group(1)); } catch (Exception ignored) {}
        Matcher mH2 = Pattern.compile("(?i).*heading[_\\s]*(\\d{1,2}).*").matcher(s);
        if (mH2.find()) try { return Integer.parseInt(mH2.group(1)); } catch (Exception ignored) {}
        Matcher mT = Pattern.compile("(?i).*titre[_\\s]*(\\d{1,2}).*").matcher(s);
        if (mT.find()) try { return Integer.parseInt(mT.group(1)); } catch (Exception ignored) {}

        // Si contient "titre" sans chiffre, considérer comme titre principal
        if (low.contains("titre") && !low.matches(".*\\d+.*")) {
            return 11;
        }

        // fallback
        return -1;
    }

    private static int nextListNumber(java.math.BigInteger numId, int ilvl) {
        if (numId == null) return 1;
        java.util.Map<Integer, Integer> levelMap = LIST_COUNTERS.get(numId);
        if (levelMap == null) {
            levelMap = new java.util.HashMap<>();
            LIST_COUNTERS.put(numId, levelMap);
        }
        Integer cur = levelMap.get(ilvl);
        if (cur == null) cur = 0;
        cur = cur + 1;
        levelMap.put(ilvl, cur);

        // Quand on avance à un niveau L, on remet à zéro les compteurs des niveaux plus profonds (> L)
        java.util.List<Integer> toReset = new java.util.ArrayList<>();
        for (Integer k : new java.util.ArrayList<>(levelMap.keySet())) {
            if (k > ilvl) toReset.add(k);
        }
        for (Integer k : toReset) {
            levelMap.put(k, 0);
        }

        return cur;
    }

	private static String extractFootnoteInlineText(XWPFDocument doc, java.math.BigInteger id) {
        if (doc == null || id == null) return "";
        try {
            org.apache.poi.xwpf.usermodel.XWPFFootnote fn = doc.getFootnoteByID(id.intValue());
            if (fn == null) return "";
            StringBuilder out = new StringBuilder();
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph pp : fn.getParagraphs()) {
                // Option simple : texte brut du paragraphe
                String t = pp.getText();
                if (t != null && !t.isEmpty()) {
                    if (out.length() > 0) out.append(" ");
                    out.append(t);
                }
            }
            return normalizeSpaces(out.toString());
        } catch (Exception e) {
            return "";
        }
    }

    private static String normalizeSpaces(String s) {
        if (s == null) return "";
        return s.replace('\r',' ')
                .replace('\n',' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    @SuppressWarnings("deprecation")
    private static String getRunVisibleText(org.apache.poi.xwpf.usermodel.XWPFRun run) {
        if (run == null) return "";
        StringBuilder out = new StringBuilder();

        var ctr = run.getCTR();
        var cur = ctr.newCursor();

        if (cur.toFirstChild()) {
            do {
                if (!cur.isStart()) continue;               // on ne traite que les START
                QName qn = cur.getName();
                String local = (qn != null) ? qn.getLocalPart() : null;

                if ("t".equals(local)) {
                    // Lire le texte de <w:t> sans getTextContent (non supporté par XMLBeans)
                    Node tNode = cur.getDomNode(); 
                    StringBuilder txt = new StringBuilder();
                    for (Node c = tNode.getFirstChild(); c != null; c = c.getNextSibling()) {
                        short type = c.getNodeType();
                        if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
                            String v = c.getNodeValue();
                            if (v != null && !v.isEmpty()) txt.append(v);
                        }
                    }
                    if (txt.length() > 0) {
                        out.append(txt.toString().replace("\t", "[tab]"));
                    }
                }
                else if ("tab".equals(local)) {
                    out.append("[tab]");
                }
                else if ("br".equals(local) || "footnoteReference".equals(local)) {
                    // ignoré ici (saut de page/ligne et ref de note gérés ailleurs si besoin)

                }

            } while (cur.toNextSibling());
        }
        cur.dispose();

        return out.toString();
    }


    private static String wrapWithMarkers(String text, TextStyle ts) {
        if (ts == null || ts.isEmpty()) return text;
        boolean b = ts.bold, it = ts.italic, u = ts.underline;
        if (u && b && it) return "_*^" + text + "^*_";
        if (u && b) return "_*" + text + "*_";
        if (u && it) return "_^" + text + "^_";
        if (b && it) return "*^" + text + "^*";
        if (b) return "**" + text + "**";
        if (it) return "^^" + text + "^^";
        if (u) return "__" + text + "__";
        return text;
    }
    
    private static String paragraphToVisible(XWPFParagraph p){
        StringBuilder sb = new StringBuilder();
        for (IRunElement re : p.getIRuns()){
            if (re instanceof XWPFRun r){
                // texte du run (remplacer les \t)
                String t = r.text();
                if (t != null && !t.isEmpty()){
                    sb.append(t.replace("\t", "[tab]"));
                }
                // tabs explicites <w:tab/> contenus dans le CTR
                var ctr = r.getCTR();
                if (ctr != null){
                    int tabs = ctr.sizeOfTabArray();
                    for (int i=0;i<tabs;i++) sb.append("[tab]");
                }
            }
        }
        return sb.toString();
    }
    
    @SuppressWarnings("deprecation")
	private static String wrapSuperSub(String text, XWPFRun run) {
        if (text == null || text.isEmpty() || run == null) return text;

        // 1) Essai API haut-niveau (si dispo sur ta version)
        try {
            // Certaines versions ont getSubscript(), d'autres getVerticalAlignment()
            try {
                org.apache.poi.xwpf.usermodel.VerticalAlign va =
                    (org.apache.poi.xwpf.usermodel.VerticalAlign)
                    XWPFRun.class.getMethod("getSubscript").invoke(run);
                if (va == org.apache.poi.xwpf.usermodel.VerticalAlign.SUPERSCRIPT) {
                    return "^¨" + text + "¨^";
                } else if (va == org.apache.poi.xwpf.usermodel.VerticalAlign.SUBSCRIPT) {
                    return "_¨" + text + "¨_";
                }
            } catch (NoSuchMethodException ignore) {
                try {
                    org.apache.poi.xwpf.usermodel.VerticalAlign va =
                        (org.apache.poi.xwpf.usermodel.VerticalAlign)
                        XWPFRun.class.getMethod("getVerticalAlignment").invoke(run);
                    if (va == org.apache.poi.xwpf.usermodel.VerticalAlign.SUPERSCRIPT) {
                        return "^¨" + text + "¨^";
                    } else if (va == org.apache.poi.xwpf.usermodel.VerticalAlign.SUBSCRIPT) {
                        return "_¨" + text + "¨_";
                    }
                } catch (NoSuchMethodException ignoreToo) {
                    // pass -> on descend au fallback XML
                }
            }
        } catch (Throwable ignoreAll) {
            // on tombera sur le fallback XML
        }

        // 2) Fallback bas-niveau: lire <w:vertAlign w:val="...">
     // 2) Fallback bas-niveau: lire <w:vertAlign w:val="..."> via XmlCursor
        try {
            var ctr = run.getCTR();
            if (ctr != null && ctr.isSetRPr()) {
                var rpr = ctr.getRPr();
                org.apache.xmlbeans.XmlCursor xc = rpr.newCursor();
                if (xc.toFirstChild()) {
                    do {
                        if (!xc.isStart()) continue;
                        javax.xml.namespace.QName qn = xc.getName();
                        if (qn != null && "vertAlign".equals(qn.getLocalPart())) {
                            String val = xc.getAttributeText(
                                new javax.xml.namespace.QName(
                                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val"
                                )
                            );
                            if ("superscript".equalsIgnoreCase(val)) {
                                xc.dispose();
                                return "^¨" + text + "¨^";
                            } else if ("subscript".equalsIgnoreCase(val)) {
                                xc.dispose();
                                return "_¨" + text + "¨_";
                            }
                            break;
                        }
                    } while (xc.toNextSibling());
                }
                xc.dispose();
            }
        } catch (Throwable ignore) { /* ne casse jamais l’import */ }


        return text; // normal
    }
    
    /** Écrit un tableau Word en syntaxe LisioWriter @t … @/t */
    private static void emitLisioTable(XWPFTable table, XWPFDocument doc, StringBuilder sb) {
        if (table == null) return;

        // Commencer le bloc
        endLineIfNeeded(sb);
        sb.append("@t\n");

        // Heuristique "ligne d’en-tête" :
        // - si la 1ère ligne a tblHeader=true, on la traite en |!
        // - sinon, si *toutes* les cellules de la 1ère ligne sont en gras, on la traite en |!
        boolean firstIsHeader = false;
        if (!table.getRows().isEmpty()) {
            var r0 = table.getRow(0);
            firstIsHeader = isWordHeaderRow(r0);
            if (!firstIsHeader) firstIsHeader = rowLooksBold(r0); // heuristique de repli
        }

        for (int r = 0; r < table.getNumberOfRows(); r++) {
            var row = table.getRow(r);
            boolean header = (r == 0 && firstIsHeader);

            sb.append(header ? "|! " : "| ");

            int n = row.getTableCells().size();
            for (int c = 0; c < n; c++) {
                var cell = row.getCell(c);
                String cellText = extractCellInline(cell, doc);   // contenu riche (gras/italique/liens/notes)
                sb.append(escapePipes(cellText));
                if (c < n - 1) sb.append(" | ");
            }
            sb.append('\n');
        }

        sb.append("@/t\n");
        endLineIfNeeded(sb);
        // ligne vide après le tableau (comme pour un paragraphe)
        // (optionnel) sb.append('\n');
    }

    /** Vrai si *tous* les runs de la 1ère ligne paraissent gras (heuristique simple). */
    private static boolean rowLooksBold(org.apache.poi.xwpf.usermodel.XWPFTableRow row) {
        if (row == null) return false;
        for (var cell : row.getTableCells()) {
            boolean anyText = false, allBold = true;
            for (var p : cell.getParagraphs()) {
                for (var r : p.getRuns()) {
                    String t = r.text();
                    if (t != null && !t.isBlank()) {
                        anyText = true;
                        try { if (!r.isBold()) allBold = false; } catch (Exception ignored) { allBold = false; }
                    }
                }
            }
            if (anyText && !allBold) return false;
        }
        return true;
    }

    /** Concatène le contenu d’une cellule avec la même logique inline que les paragraphes (sans préfixes de titre/liste). */
    private static String extractCellInline(org.apache.poi.xwpf.usermodel.XWPFTableCell cell, XWPFDocument doc) {
        if (cell == null) return "";
        StringBuilder out = new StringBuilder();
        var paras = cell.getParagraphs();
        if (paras == null || paras.isEmpty()) {
            // fallback brut
            String t = normalizeSpaces(cell.getText());
            return t == null ? "" : t;
        }
        for (int i = 0; i < paras.size(); i++) {
            if (i > 0) out.append(" "); // séparateur doux entre paragraphes de la même cellule
            out.append(inlineFromParagraph(paras.get(i), doc));
        }
        return normalizeSpaces(out.toString());
    }

    /** Rend *uniquement* le contenu inline d’un paragraphe (gras/italique/souligné, liens, images, notes, tabs), sans newline ni préfixe. */
    private static String inlineFromParagraph(XWPFParagraph p, XWPFDocument doc) {
	    StringBuilder sb = new StringBuilder();
	
	    // ✅ Calculer une fois pour ce paragraphe
	    boolean paraHasModernPic = paragraphHasModernPicture(p);
	
	    var runs = p.getRuns();
	    if (runs != null) {
	        int i = 0;
	        while (i < runs.size()) {
	            XWPFRun run = runs.get(i);
	
	            // --- Liens fusionnés (inchangé) ---
	            if (run instanceof XWPFHyperlinkRun hrun) {
	                String url = null;
	                try { if (hrun.getHyperlink(doc) != null) url = hrun.getHyperlink(doc).getURL(); } catch (Exception ignored) {}
	                if ((url == null || url.isBlank()) && hrun.getHyperlinkId() != null) {
	                    try { var link = doc.getHyperlinkByID(hrun.getHyperlinkId()); if (link != null) url = link.getURL(); } catch (Exception ignored) {}
	                }
	                StringBuilder label = new StringBuilder();
	                while (i < runs.size()) {
	                    XWPFRun r = runs.get(i);
	                    if (r instanceof XWPFHyperlinkRun hr2) {
	                        String u2 = null;
	                        try {
	                            if (hr2.getHyperlink(doc) != null) u2 = hr2.getHyperlink(doc).getURL();
	                            if ((u2 == null || u2.isBlank()) && hr2.getHyperlinkId() != null) {
	                                var link = doc.getHyperlinkByID(hr2.getHyperlinkId()); if (link != null) u2 = link.getURL();
	                            }
	                        } catch (Exception ignored) {}
	                        if ((url != null && url.equals(u2)) || (url == null && u2 == null)) {
	                            if (hr2.text() != null) label.append(hr2.text());
	                            i++; continue;
	                        }
	                    }
	                    break;
	                }
	                String lbl = normalizeSpaces(label.toString());
	                if (!lbl.isBlank() && url != null && !url.isBlank()) sb.append("@[").append(lbl).append(": ").append(url).append("]");
	                else sb.append(lbl);
	                continue;
	            }
	
	            // --- Images : passer le flag pour ignorer VML si déjà un drawing/embedded dans le paragraphe ---
	            try {
	                if (emitPicturesFromRun(run, doc, sb, paraHasModernPic)) {
	                    // rien d’autre à faire ici
	                }
	            } catch (Exception ignored) {}
	
	            // --- Texte stylé (inchangé) ---
	            String t = getRunVisibleText(run);
	            TextStyle ts = new TextStyle();
	            try { ts.bold = run.isBold(); } catch (Exception ignored) {}
	            try { ts.italic = run.isItalic(); } catch (Exception ignored) {}
	            try { var u = run.getUnderline(); ts.underline = (u != null && u != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE); } catch (Exception ignored) {}
	            if (t != null && !t.isEmpty()) sb.append(wrapSuperSub(wrapWithMarkers(t, ts), run));
	
	            // --- Notes (inchangé) ---
	            try {
	                var ctr = run.getCTR();
	                var refs = ctr.getFootnoteReferenceList();
	                if (refs != null) for (var r : refs) {
	                    var id = r.getId();
	                    String note = extractFootnoteInlineText(doc, id);
	                    if (!note.isBlank()) sb.append("@(").append(note).append(")");
	                }
	            } catch (Exception ignored) {}
	
	            i++;
	        }
	    }
	
	    if (sb.length() == 0) sb.append(normalizeSpaces(paragraphToVisible(p)));
	    return sb.toString();
    }

    /** Échappe les barres verticales et antislash pour la syntaxe de tableau LW. */
    private static String escapePipes(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("|", "\\|").trim();
    }

    /** Ajoute un saut de ligne si le buffer ne se termine pas déjà par \n. */
    private static void endLineIfNeeded(StringBuilder sb) {
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) != '\n') sb.append('\n');
    }

 // Détecte si la ligne Word est marquée "header" (w:tblHeader) quelle que soit la version des schémas.
    @SuppressWarnings("deprecation")
	private static boolean isWordHeaderRow(org.apache.poi.xwpf.usermodel.XWPFTableRow row) {
        if (row == null || row.getCtRow() == null || row.getCtRow().getTrPr() == null) return false;
        var trPr = row.getCtRow().getTrPr();

        // 1) Tentative API directe (certaines versions l’ont)
        try {
            // CTTrPr#isSetTblHeader / getTblHeader
            java.lang.reflect.Method isSet = trPr.getClass().getMethod("isSetTblHeader");
            java.lang.reflect.Method getVal = trPr.getClass().getMethod("getTblHeader");
            Boolean present = (Boolean) isSet.invoke(trPr);
            if (present != null && present) {
                Object onOff = getVal.invoke(trPr); // CTOnOff
                // CTOnOff#getVal() -> Boolean (peut être null => considéré "on")
                java.lang.reflect.Method getVal2 = onOff.getClass().getMethod("getVal");
                Object v = getVal2.invoke(onOff);
                return (v == null) || Boolean.TRUE.equals(v);
            }
        } catch (Throwable ignore) { /* passe au plan B */ }

        // 2) Liste (autres versions exposent getTblHeaderList())
        try {
            java.lang.reflect.Method getList = trPr.getClass().getMethod("getTblHeaderList");
            java.util.List<?> list = (java.util.List<?>) getList.invoke(trPr);
            if (list != null && !list.isEmpty()) {
                Object onOff = list.get(0);
                java.lang.reflect.Method isSetVal = onOff.getClass().getMethod("isSetVal");
                java.lang.reflect.Method getVal2 = onOff.getClass().getMethod("getVal");
                boolean hasVal = (Boolean) isSetVal.invoke(onOff);
                if (!hasVal) return true;                 // <w:tblHeader/> => vrai
                Object v = getVal2.invoke(onOff);         // Boolean
                return Boolean.TRUE.equals(v);
            }
        } catch (Throwable ignore) { /* passe au plan C */ }

        // 3) Fallback XmlCursor : chercher explicitement <w:tblHeader ...>
        try {
            org.apache.xmlbeans.XmlCursor xc = trPr.newCursor();
            if (xc.toFirstChild()) {
                do {
                    if (xc.isStart() && "tblHeader".equals(xc.getName().getLocalPart())) {
                        String val = xc.getAttributeText(
                            new javax.xml.namespace.QName("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val"));
                        xc.dispose();
                        // si @w:val absent -> true ; sinon true/1
                        return (val == null || val.isBlank() || "true".equalsIgnoreCase(val) || "1".equals(val));
                    }
                } while (xc.toNextSibling());
            }
            xc.dispose();
        } catch (Throwable ignore) {}

        return false;
    }

    private static void appendManualPageBreakLineNoDup(StringBuilder sb) {
        // Regarde les ~80 derniers caractères pour voir si on vient déjà d’émettre la même ligne
        int len = sb.length();
        int from = Math.max(0, len - 80);
        String tail = sb.substring(from, len);

        // On considère doublon si la dernière occurrence est déjà la dernière "vraie" ligne
        if (tail.contains("@saut de page manuel")) return;

        // Sinon, on l'écrit proprement
        if (len > 0 && sb.charAt(len - 1) != '\n') sb.append('\n');
        sb.append("@saut de page manuel\n");
    }
    
 // === Helpers images DOCX ===============================================

    /** Ajoute le marqueur d'image avec un fallback sûr. */
    private static void appendImageMarker(String alt, StringBuilder sb) {
        String label = (alt == null || alt.trim().isEmpty()) ? "Image" : alt.trim();
        sb.append("![Image : ").append(label).append("]");
    }
    
    /** Ajoute le marqueur d’image avec légende si trouvée */
    private static void appendImageMarkerWithCaption(String alt, String caption, StringBuilder sb) {
        String label = (alt == null || alt.trim().isEmpty()) ? "Image" : alt.trim();
        sb.append("![Image : ").append(label);
        if (caption != null && !caption.isBlank()) {
            sb.append(". Légende : ").append(caption.trim());
        }
        sb.append("]");
    }

    /**
     * Cherche une légende liée à une image :
     *  - dans w:drawing (txbxContent ou wps:txbx)
     *  - dans w:pict / v:textbox (VML, anciens .docx)
     *  - sinon dans le paragraphe suivant.
     */
    private static String extractCaptionFromDrawingOrNearby(XWPFRun run, XWPFDocument doc) {
        try {
            var ctr = run.getCTR();

            // === 1️⃣ Cas des dessins modernes ===
            if (ctr != null) {
                var drawings = ctr.getDrawingList();
                if (drawings != null) {
                    for (var drawing : drawings) {
                        String xml = drawing.xmlText();
                        Matcher m = Pattern.compile(
                            "<(?:wps:)?txbx[^>]*>.*?<w:txbxContent[^>]*>(.*?)</w:txbxContent>",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                        ).matcher(xml);
                        if (m.find()) {
                            String raw = m.group(1)
                                .replaceAll("<[^>]+>", " ")
                                .replaceAll("\\s+", " ")
                                .trim();
                            if (raw.toLowerCase().contains("figure") || raw.toLowerCase().contains("fig.")) {
                                return normalizeSpaces(raw);
                            }
                        }
                    }
                }

                // === 2️⃣ Cas des images VML : w:pict + v:textbox ===
                var picts = ctr.getPictList();
                if (picts != null && !picts.isEmpty()) {
                    for (var pict : picts) {
                        String xml = pict.xmlText();
                        // Recherche contenu de <v:textbox> ... <w:txbxContent>
                        Matcher m = Pattern.compile(
                            "<v:textbox[^>]*>.*?<w:txbxContent[^>]*>(.*?)</w:txbxContent>.*?</v:textbox>",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                        ).matcher(xml);
                        if (m.find()) {
                            String raw = m.group(1)
                                .replaceAll("<[^>]+>", " ")
                                .replaceAll("\\s+", " ")
                                .trim();
                            if (raw.toLowerCase().contains("figure") || raw.toLowerCase().contains("fig.")) {
                                return normalizeSpaces(raw);
                            }
                        }
                    }
                }
            }

            // === 3️⃣ Fallback : paragraphe suivant ===
            var parent = run.getParent();
            if (parent instanceof XWPFParagraph para) {
                var body = para.getBody();
                var paras = body.getParagraphs();
                int idx = paras.indexOf(para);
                if (idx >= 0 && idx < paras.size() - 1) {
                    var next = paras.get(idx + 1);
                    String txt = next.getText();
                    if (txt != null && (txt.toLowerCase().contains("figure") || txt.toLowerCase().contains("fig."))) {
                        return normalizeSpaces(txt);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Essaie de repérer une légende (type "Figure...") dans le même paragraphe ou juste après. */
    private static String extractCaptionTextNear(XWPFRun run) {
        try {
            var parent = run.getParent();
            if (!(parent instanceof XWPFParagraph para)) return null;

            // 1️⃣ Cherche dans les runs suivants du même paragraphe
            var runs = para.getRuns();
            if (runs != null) {
                for (int i = runs.indexOf(run) + 1; i < runs.size(); i++) {
                    String txt = runs.get(i).text();
                    if (txt != null && txt.toLowerCase().contains("figure")) {
                        return normalizeSpaces(txt);
                    }
                }
            }

            // 2️⃣ Sinon, cherche dans le paragraphe suivant
            var body = para.getBody();
            var paras = body.getParagraphs();
            int idx = paras.indexOf(para);
            if (idx >= 0 && idx < paras.size() - 1) {
                var next = paras.get(idx + 1);
                String txt = next.getText();
                if (txt != null && txt.toLowerCase().contains("figure")) {
                    return normalizeSpaces(txt);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Déduit un libellé à partir d'un rId (nom de fichier) si possible. */
    private static String altFromRelationId(XWPFDocument doc, String rId) {
        if (doc == null || rId == null || rId.isEmpty()) return null;
        try {
            org.apache.poi.ooxml.POIXMLDocumentPart part = doc.getRelationById(rId);
            if (part instanceof org.apache.poi.xwpf.usermodel.XWPFPictureData pd) {
                String name = pd.getFileName(); // ex: "photo_001.jpg"
                if (name != null && !name.isBlank()) {
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) name = name.substring(0, dot);
                    return name.replace('_', ' ').trim();
                }
            } else if (part != null && part.getPackagePart() != null) {
                String name = part.getPackagePart().getPartName().getName(); // /word/media/image1.png
                if (name != null) {
                    int slash = name.lastIndexOf('/');
                    if (slash >= 0) name = name.substring(slash + 1);
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) name = name.substring(0, dot);
                    return name.replace('_', ' ').trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Extrait ALT depuis CTInline/CTAnchor (docPr) puis fallback via rId du blip. */
    private static String altFromInlineOrAnchor(Object inlineOrAnchor, XWPFDocument doc) {
        try {
            // CTInline ou CTAnchor ont getDocPr()
            java.lang.reflect.Method mDocPr = inlineOrAnchor.getClass().getMethod("getDocPr");
            Object docPr = mDocPr.invoke(inlineOrAnchor); // CTNonVisualDrawingProps
            String alt = null;
            try {
                java.lang.reflect.Method mDescr = docPr.getClass().getMethod("getDescr");
                Object descr = mDescr.invoke(docPr);
                if (descr != null) alt = descr.toString();
            } catch (Exception ignored) {}
            if (alt == null || alt.isBlank()) {
                try {
                    java.lang.reflect.Method mTitle = docPr.getClass().getMethod("getTitle");
                    Object title = mTitle.invoke(docPr);
                    if (title != null) alt = title.toString();
                } catch (Exception ignored) {}
            }
            // Chercher rId du blip (embed/link) pour nom de fichier
            if (alt == null || alt.isBlank()) {
                // inlineOrAnchor.getGraphic().getGraphicData().getPic().getBlipFill().getBlip()
                java.lang.reflect.Method mGraphic = inlineOrAnchor.getClass().getMethod("getGraphic");
                Object graphic = mGraphic.invoke(inlineOrAnchor);
                if (graphic != null) {
                    Object gdata = graphic.getClass().getMethod("getGraphicData").invoke(graphic);
                    if (gdata != null) {
                        Object pic = gdata.getClass().getMethod("getPic").invoke(gdata);
                        if (pic != null) {
                            Object blipFill = pic.getClass().getMethod("getBlipFill").invoke(pic);
                            if (blipFill != null) {
                                Object blip = blipFill.getClass().getMethod("getBlip").invoke(blipFill);
                                if (blip != null) {
                                    String rId = null;
                                    try { rId = (String) blip.getClass().getMethod("getEmbed").invoke(blip); } catch (Exception ignored) {}
                                    if ((rId == null || rId.isBlank())) {
                                        try { rId = (String) blip.getClass().getMethod("getLink").invoke(blip); } catch (Exception ignored) {}
                                    }
                                    String byName = altFromRelationId(doc, rId);
                                    if (byName != null && !byName.isBlank()) alt = byName;
                                }
                            }
                        }
                    }
                }
            }
            return (alt == null || alt.isBlank()) ? null : alt.trim();
        } catch (Exception e) {
            return null;
        }
    }

	   /**
	 * Émet tous les marqueurs d’images présents dans un run (inline, anchor, embedded, VML),
	 * en cherchant aussi les légendes VML dans le run précédent.
	 */
	private static boolean emitPicturesFromRun(
	        XWPFRun run,
	        XWPFDocument doc,
	        StringBuilder sb,
	        boolean suppressVMLInThisParagraph) {
	
	    boolean wrote = false;
	    java.util.Set<String> seen = new java.util.HashSet<>();
	
	    try {
	        var ctr = run.getCTR();
	        if (ctr == null) return false;
	
	        // --- 🖼️ Images modernes (w:drawing -> inline/anchor) ---
	        var drawings = ctr.getDrawingList();
	        if (drawings != null && !drawings.isEmpty()) {
	            for (var drawing : drawings) {
	
	                // inline
	                var inlines = drawing.getInlineList();
	                if (inlines != null) {
	                    for (var inl : inlines) {
	                        String rId = blipRelationIdFromInlineOrAnchor(inl);
	                        String alt = altFromInlineOrAnchor(inl, doc);
	
	                        // 🔍 Récupère la légende (priorité au run précédent)
	                        String caption = findCaptionForRun(run, doc);
	                        if (caption == null) caption = extractCaptionFromDrawingOrNearby(run, doc);
	
	                        if (registerOnce(seen, rId, alt)) {
	                            appendImageMarkerWithCaption(alt, caption, sb);
	                            wrote = true;
	                        }
	                    }
	                }
	
	                // anchor (autres cas)
	                try {
	                    var anchors = drawing.getAnchorList();
	                    if (anchors != null) {
	                        for (var anc : anchors) {
	                            String rId = blipRelationIdFromInlineOrAnchor(anc);
	                            String alt = altFromInlineOrAnchor(anc, doc);
	
	                            // 🔍 Légende VML ou fallback
	                            String caption = findCaptionForRun(run, doc);
	                            if (caption == null) caption = extractCaptionFromDrawingOrNearby(run, doc);
	
	                            if (registerOnce(seen, rId, alt)) {
	                                appendImageMarkerWithCaption(alt, caption, sb);
	                                wrote = true;
	                            }
	                        }
	                    }
	                } catch (Throwable ignored) {}
	            }
	        }
	
	        // --- 🧩 VML <w:pict> (anciennes images ou légendes seules) ---
	        try {
	            if (!suppressVMLInThisParagraph && seen.isEmpty()) {
	                var picts = ctr.getPictList();
	                if (picts != null && !picts.isEmpty()) {
	                    for (var pict : picts) {
	                        String xml = pict.xmlText();
	
	                        // 1️⃣ Légende dans le textbox
	                        String caption = extractCaptionFromPictXml(xml);
	
	                        // 2️⃣ Alt via r:id (fichier)
	                        String alt = altFromVmlImagedata(xml, doc);
	
	                        if (registerOnce(seen, null, alt)) {
	                            appendImageMarkerWithCaption(alt, caption, sb);
	                            wrote = true;
	                        }
	                    }
	                }
	            }
	        } catch (Throwable ignored) {}
	
	        // --- 🧱 API haut-niveau embeddedPictures ---
	        if (seen.isEmpty()) {
	            var pics = run.getEmbeddedPictures();
	            if (pics != null) {
	                for (var p : pics) {
	                    String alt = null;
	                    try { alt = p.getDescription(); } catch (Exception ignored) {}
	
	                    String rId = null;
	                    try {
	                        rId = p.getCTPicture().getBlipFill().getBlip().getEmbed();
	                        if (rId == null || rId.isBlank()) {
	                            rId = p.getCTPicture().getBlipFill().getBlip().getLink();
	                        }
	                    } catch (Exception ignored) {}
	
	                    if (alt == null || alt.isBlank()) {
	                        try {
	                            String name = p.getPictureData().getFileName();
	                            if (name != null) {
	                                int dot = name.lastIndexOf('.');
	                                if (dot > 0) name = name.substring(0, dot);
	                                alt = name.replace('_', ' ').trim();
	                            }
	                        } catch (Exception ignored) {}
	                    }
	
	                    // 🔍 même logique de légende que pour drawings
	                    String caption = findCaptionForRun(run, doc);
	                    if (caption == null) caption = extractCaptionFromDrawingOrNearby(run, doc);
	
	                    if (registerOnce(seen, rId, alt)) {
	                        appendImageMarkerWithCaption(alt, caption, sb);
	                        wrote = true;
	                    }
	                }
	            }
	        }
	
	    } catch (Exception ignored) {}
	
	    return wrote;
	}

	/**
	 * Cherche une légende éventuelle dans le run précédent (VML <w:pict> avec <v:textbox>).
	 */
	@SuppressWarnings("deprecation")
	private static String findCaptionForRun(XWPFRun run, XWPFDocument doc) {
	    try {
	        var para = run.getParagraph();
	        if (para == null) return null;

	        var runs = para.getRuns();
	        if (runs == null) return null;

	        int idx = runs.indexOf(run);
	        if (idx <= 0) return null;

	        // Vérifie le run précédent
	        var prevRun = runs.get(idx - 1);
	        var prevCtr = prevRun.getCTR();
	        if (prevCtr == null) return null;

	        if (prevCtr.sizeOfPictArray() > 0) {
	            for (var pict : prevCtr.getPictList()) {
	                String xmlPrev = pict.xmlText();
	                String caption = extractCaptionFromPictXml(xmlPrev);
	                if (caption != null && !caption.isBlank()) {
	                    return caption;
	                }
	            }
	        }
	    } catch (Exception ignored) {}
	    return null;
	}

	
    // Renvoie l'ID de relation du blip (embed/link) si dispo, sinon null.
    private static String blipRelationIdFromInlineOrAnchor(Object inlineOrAnchor) {
        try {
            Object graphic = inlineOrAnchor.getClass().getMethod("getGraphic").invoke(inlineOrAnchor);
            if (graphic == null) return null;
            Object gdata = graphic.getClass().getMethod("getGraphicData").invoke(graphic);
            if (gdata == null) return null;
            Object pic = gdata.getClass().getMethod("getPic").invoke(gdata);
            if (pic == null) return null;
            Object blipFill = pic.getClass().getMethod("getBlipFill").invoke(pic);
            if (blipFill == null) return null;
            Object blip = blipFill.getClass().getMethod("getBlip").invoke(blipFill);
            if (blip == null) return null;
            String rId = null;
            try { rId = (String) blip.getClass().getMethod("getEmbed").invoke(blip); } catch (Exception ignored) {}
            if (rId == null || rId.isBlank()) {
                try { rId = (String) blip.getClass().getMethod("getLink").invoke(blip); } catch (Exception ignored) {}
            }
            return (rId != null && !rId.isBlank()) ? rId : null;
        } catch (Exception e) { return null; }
    }

    // Enregistre une image une seule fois (clé = rId sinon ALT).
    private static boolean registerOnce(java.util.Set<String> seen, String rId, String alt) {
        String key = (rId != null && !rId.isBlank())
                ? "RID:" + rId
                : "ALT:" + (alt == null ? "" : alt.trim());
        if (seen.contains(key)) return false;
        seen.add(key);
        return true;
    }

    private static boolean paragraphHasModernPicture(XWPFParagraph p) {
        var runs = p.getRuns();
        if (runs == null) return false;
        for (XWPFRun r : runs) {
            try {
                var ctr = r.getCTR();
                if (ctr == null) continue;
                var drawings = ctr.getDrawingList();
                if (drawings != null && !drawings.isEmpty()) return true;
                // ou images haut niveau
                var pics = r.getEmbeddedPictures();
                if (pics != null && !pics.isEmpty()) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /** Extrait la légende complète d’un bloc VML <v:textbox><w:txbxContent>…</w:txbxContent>. */
    private static String extractCaptionFromPictXml(String xml) {
        if (xml == null) return null;

        Matcher m = Pattern.compile(
            "<v:textbox[^>]*>.*?<w:txbxContent[^>]*>(.*?)</w:txbxContent>.*?</v:textbox>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        ).matcher(xml);

        if (m.find()) {
            String inside = m.group(1);

            // ✅ Concatène tous les <w:t>...</w:t> (y compris ceux imbriqués dans w:r / w:fldSimple)
            StringBuilder txt = new StringBuilder();
            Matcher t = Pattern.compile("<w:t[^>]*>(.*?)</w:t>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(inside);
            while (t.find()) {
                txt.append(t.group(1)).append(" ");
            }

            // Nettoyage du texte final
            String raw = txt.toString()
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();

            if (!raw.isBlank() && (raw.toLowerCase().contains("figure") || raw.toLowerCase().contains("fig."))) {
                return normalizeSpaces(raw);
            }
        }
        return null;
    }


    /** Récupère un ALT via le nom de fichier de <v:imagedata r:id="..."> si possible. */
    private static String altFromVmlImagedata(String xml, XWPFDocument doc) {
        if (xml == null || doc == null) return null;
        try {
            Matcher m = Pattern.compile("r:id\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(xml);
            if (m.find()) {
                String rid = m.group(1);
                // Tenter de retrouver le nom de fichier via la relation
                try {
                    var part = doc.getRelationById(rid);
                    if (part instanceof org.apache.poi.xwpf.usermodel.XWPFPictureData pd) {
                        String name = pd.getFileName();
                        if (name != null) {
                            int dot = name.lastIndexOf('.');
                            if (dot > 0) name = name.substring(0, dot);
                            return name.replace('_', ' ').trim();
                        }
                    } else if (part != null && part.getPackagePart() != null) {
                        String name = part.getPackagePart().getPartName().getName(); // /word/media/image1.png
                        int slash = name.lastIndexOf('/');
                        if (slash >= 0) name = name.substring(slash + 1);
                        int dot = name.lastIndexOf('.');
                        if (dot > 0) name = name.substring(0, dot);
                        return name.replace('_', ' ').trim();
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null; // laisser appendImageMarkerWithCaption gérer le fallback "Image"
    }



}
