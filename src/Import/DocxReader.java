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
        StringBuilder sb = new StringBuilder();
        try (InputStream in = new FileInputStream(docxPath);
             XWPFDocument doc = new XWPFDocument(in)) {

        	for (IBodyElement be : doc.getBodyElements()) {
        	    if (be.getElementType() == BodyElementType.PARAGRAPH) {
        	        XWPFParagraph p = (XWPFParagraph) be;
        	        // ⬇️ on passe 'doc' à handleParagraph pour gérer les notes
        	        handleParagraph(p, doc, sb);
        	    } else if (be.getElementType() == BodyElementType.TABLE) {
        	        @SuppressWarnings("unused")
					XWPFTable t = (XWPFTable) be;
        	        sb.append("@table\n");
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
            appendPageBreakLine(sb);
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

             // === 🖼️ Cas spécial : images ===
                try {
                    var ctr = run.getCTR();
                    var drawingList = ctr.getDrawingList();
                    if (drawingList != null && !drawingList.isEmpty()) {
                        for (var drawing : drawingList) {
                            var inlineList = drawing.getInlineList();
                            if (inlineList != null) {
                                for (var inline : inlineList) {
                                    String altText = null;
                                    try {
                                        altText = inline.getDocPr().getDescr(); // le texte de remplacement
                                    } catch (Exception ignored) {}

                                    if (altText == null || altText.isBlank()) {
                                        // fallback : titre éventuel
                                        try { altText = inline.getDocPr().getTitle(); } catch (Exception ignored) {}
                                    }

                                    if (altText != null && !altText.isBlank()) {
                                        sb.append("![Image : ").append(altText.trim()).append("]");
                                    } else {
                                        sb.append("![Image]");
                                    }

                                    hadVisibleText = true;
                                }
                            }
                        }
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
            appendPageBreakLine(sb);
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


    private static void appendPageBreakLine(StringBuilder sb) {
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) != '\n') sb.append('\n');
        sb.append("@saut de page\n");
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


}
