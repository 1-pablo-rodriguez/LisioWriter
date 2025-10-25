package Import;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

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
            for (int i = 0; i < level; ++i) sb.append("\t");

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
        
        if (runs != null) {
            for (org.apache.poi.xwpf.usermodel.XWPFRun run : runs) {
                // 5.a) texte du run + styles
            	String text = getRunVisibleText(run);  // n’inclut pas [footnoteRef:1]
                TextStyle ts = new TextStyle();
                try { ts.bold = run.isBold(); } catch (Exception ignored) {}
                try { ts.italic = run.isItalic(); } catch (Exception ignored) {}
                try {
                    org.apache.poi.xwpf.usermodel.UnderlinePatterns u = run.getUnderline();
                    ts.underline = (u != null && u != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE);
                } catch (Exception ignored) {}
                if (text != null && !text.isEmpty()) {
                    sb.append(wrapWithMarkers(text, ts));
                }
                
                // Détection des sauts de page manuels dans CE run (<w:br w:type="page"/>)
                try {
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr = run.getCTR();
                    int n = ctr.sizeOfBrArray();
                    for (int i = 0; i < n; i++) {
                        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr br = ctr.getBrArray(i);
                        if (br != null && br.isSetType()) {
                            // t vaut LINE, PAGE, COLUMN, etc.
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType.Enum t = br.getType();
                            if (t == org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType.PAGE) {
                                appendPageBreakLine(sb); // -> écrit "@saut de page\n" proprement
                                wrotePageBreakInThisPara = true;
                            }
                        }
                        // Si tu voulais traiter d'autres cas :
                        // else if (t == STBrType.COLUMN) { /* colonne: ignorer ou gérer */ }
                        // else { /* simple saut de ligne: sb.append('\n'); */ }
                    }
                } catch (Exception ignored) {}

                // 5.b) références de notes de bas de page dans CE run
                // (les runs qui portent une note n'ont souvent pas de texte, d'où l'étape 5.a indépendante)
                try {
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr = run.getCTR();
                    java.util.List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFtnEdnRef> refs =
                            ctr.getFootnoteReferenceList();
                    if (refs != null && !refs.isEmpty()) {
                        for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFtnEdnRef r : refs) {
                            java.math.BigInteger id = r.getId();
                            String note = extractFootnoteInlineText(doc, id);
                            if (!note.isBlank()) {
                                sb.append("@(").append(note).append(")");
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // ne jamais casser l'import sur cas exotique
                }
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

    private static String getRunVisibleText(org.apache.poi.xwpf.usermodel.XWPFRun run) {
        if (run == null) return "";
        StringBuilder sb = new StringBuilder();
        // Ne lit QUE les <w:t> (texte visible), pas les footnoteReference.
        int tCount = run.getCTR().sizeOfTArray();
        for (int i = 0; i < tCount; i++) {
            String part = run.getText(i);
            if (part != null && !part.isEmpty()) sb.append(part);
        }
        // (facultatif) gérer les tabulations et retours ligne du run :
        // for (int i = 0; i < run.getCTR().sizeOfTabArray(); i++) sb.append("\t");
        // for (int i = 0; i < run.getCTR().sizeOfBrArray();  i++) sb.append("\n");
        return sb.toString();
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
}
