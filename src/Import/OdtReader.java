package Import;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import Import.odt.style.*;
import writer.ui.editor.BraillePrefixer;


/**
 * OdtReader amélioré : corrige extraction styles, héritage, spans inline et spans imbriqués,
 * et améliore la détection des styles de titre (p.ex. "Heading 1", "Heading_20_1", "Titre 1", ou texte avec text:outline-level).
 */
public class OdtReader {
	
	private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
	private static final String DRAW_NS = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0";
	private static final String SVG_NS  = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0";


	/** Empêche le libellé de casser la syntaxe @[Texte: URL] */
	private static String escapeLabelForAtLink(String s) {
	    if (s == null) return "";
	    // ']' termine le lien, on le remplace par ')'
	    s = s.replace(']', ')');
	    // ':' sépare libellé et URL, on le remplace par le deux-points math. U+2236
	    s = s.replace(':', '∶'); // visuellement proche, n'entre pas en collision avec le séparateur
	    return s;
	}


    public static String extractStructuredTextFromODT(String odtFilePath) throws Exception {
        ZipFile zipFile = new ZipFile(odtFilePath);

        Document contentDoc = parseXmlFromZip(zipFile, "content.xml");
        Document stylesDoc = parseXmlFromZip(zipFile, "styles.xml");

        // map style -> parent-style (pour héritage)
        Map<String, String> styleToParent = buildStyleParentMap(stylesDoc, contentDoc);
        Map<String, Integer> headingStyleLevels = buildHeadingLevelMap();

        // map style -> propriétés textuelles directes (sans héritage)
        Map<String, TextStyle> directTextStyleMap = buildTextStylePropertiesMap(stylesDoc, contentDoc);

        StringBuilder result = new StringBuilder();

        NodeList textNodes = contentDoc.getElementsByTagNameNS("*", "text");
        if (textNodes.getLength() > 0) {
            Element textRoot = (Element) textNodes.item(0);
            parseContentInOrder(textRoot, styleToParent, directTextStyleMap, headingStyleLevels, result, false, stylesDoc, contentDoc);
        }

        zipFile.close();
        
        String text = BraillePrefixer.addBrailleAtParagraphStarts(result.toString());
        return text;
    }

    private static Document parseXmlFromZip(ZipFile zipFile, String entryName) throws Exception {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) throw new FileNotFoundException(entryName + " non trouvé dans l'archive ODT.");

        InputStream stream = zipFile.getInputStream(entry);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(stream);
    }

    private static Map<String, String> buildStyleParentMap(Document stylesDoc, Document contentDoc) {
        Map<String, String> styleToParent = new HashMap<>();

        // Extraire styles depuis styles.xml
        extractStylesFromXml(stylesDoc, styleToParent);

        // Extraire styles automatiques depuis content.xml
        extractStylesFromXml(contentDoc, styleToParent);

        return styleToParent;
    }

    private static void extractStylesFromXml(Document doc, Map<String, String> styleToParent) {
        if (doc == null) return;
        NodeList styles = doc.getElementsByTagNameNS("*", "style");
        for (int i = 0; i < styles.getLength(); i++) {
            Element style = (Element) styles.item(i);
            String name = style.getAttribute("style:name");
            String parent = style.getAttribute("style:parent-style-name");

            if (name != null && !name.isEmpty() && parent != null && !parent.isEmpty()) {
                styleToParent.put(name, parent);
            }
        }
        // Correction : itérer styles2 et utiliser styles2.item(i)
        NodeList styles2 = doc.getElementsByTagNameNS("*", "list-style");
        for (int i = 0; i < styles2.getLength(); i++) {
            Element style = (Element) styles2.item(i);
            String name = style.getAttribute("style:name");
            Element child = (Element) style.getFirstChild();
            if (child != null) {
                String parent = child.getAttribute("style:num-format");
                if (parent != null) parent = "Numbering_20_Symbols";
                if (name != null && !name.isEmpty() && parent != null && !parent.isEmpty()) {
                    styleToParent.put(name, parent);
                }
            }
        }
    }

    private static Map<String, Integer> buildHeadingLevelMap() {
        Map<String, Integer> headingMap = new HashMap<>();
        // clés courantes
        headingMap.put("Heading_20_1", 1);
        headingMap.put("Heading_20_2", 2);
        headingMap.put("Heading_20_3", 3);
        headingMap.put("Heading_20_4", 4);
        headingMap.put("Heading_20_5", 5);
        headingMap.put("Heading_20_6", 6);
        headingMap.put("Heading_20_7", 7);
        headingMap.put("Heading_20_8", 8);
        headingMap.put("Heading_20_9", 9);
        headingMap.put("Heading_20_10", 10);
        // variantes possibles (on ajoute quelques variantes utiles)
        headingMap.put("Heading_1", 1);
        headingMap.put("Heading_2", 2);
        headingMap.put("Heading_3", 3);
        headingMap.put("Heading1", 1);
        headingMap.put("Heading2", 2);
        headingMap.put("Title", 11);
        headingMap.put("Subtitle", 12);
        headingMap.put("Numbering_20_Symbols", 13);
        // français courants
        headingMap.put("Titre_1", 1);
        headingMap.put("Titre_2", 2);
        headingMap.put("Titre_3", 3);
        return headingMap;
    }

    /**
     * Essaie de résoudre le niveau de titre à partir d'un style donné.
     * - remonte la chaîne d'héritage styleToParent
     * - normalise le nom (remplace espaces / _20_ / underscore)
     * - tente une détection regex (Heading N / Titre N)
     */
    private static int resolveHeadingLevelFromStyle(String style, Map<String, String> styleToParent, Map<String, Integer> headingLevels) {
        if (style == null || style.isEmpty()) return -1;

        // 1) remonter chaîne d'héritage et tester clés directes
        String current = style;
        Set<String> visited = new HashSet<>();
        while (current != null && !visited.contains(current)) {
            visited.add(current);
            if (headingLevels.containsKey(current)) {
                return headingLevels.get(current);
            }
            // essayer aussi quelques normalisations directes
            String n1 = normalizeStyleKey(current);
            if (!n1.equals(current) && headingLevels.containsKey(n1)) return headingLevels.get(n1);
            String n2 = n1.replaceAll("_20_", "_");
            if (!n2.equals(n1) && headingLevels.containsKey(n2)) return headingLevels.get(n2);

            current = styleToParent.get(current);
        }

        // 2) si pas trouvé, essayer d'extraire un numéro avec regex sur le nom initial
        // Exemples acceptés : "Heading_20_3", "Heading 3", "Heading3", "Titre 2"
        Pattern pEng = Pattern.compile("(?i).*heading[_\\s_20]*(\\d{1,2}).*");
        Pattern pFr  = Pattern.compile("(?i).*titre[_\\s]*(\\d{1,2}).*");

        Matcher mEng = pEng.matcher(style);
        if (mEng.matches()) {
            try { return Integer.parseInt(mEng.group(1)); } catch (Exception ignored) {}
        }
        Matcher mFr = pFr.matcher(style);
        if (mFr.matches()) {
            try { return Integer.parseInt(mFr.group(1)); } catch (Exception ignored) {}
        }

        // 3) fallback : -1 (pas un titre reconnu)
        return -1;
    }

    private static String normalizeStyleKey(String s) {
        if (s == null) return null;
        // quelques normalisations courantes
        String t = s.trim();
        t = t.replaceAll(" ", "_20_"); // version with _20_
        return t;
    }

    private static boolean isInsideListItem(Node node) {
        Node parent = node.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE &&
                ((Element) parent).getLocalName().equals("list-item")) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    /**
     * Construit une map styleName -> TextStyle (gras/italique/souligné) DIRECT (sans héritage).
     */
    private static Map<String, TextStyle> buildTextStylePropertiesMap(Document stylesDoc, Document contentDoc) {
        Map<String, TextStyle> map = new HashMap<>();
        processDocForTextStyles(stylesDoc, map);
        processDocForTextStyles(contentDoc, map);
        return map;
    }

    private static void processDocForTextStyles(Document doc, Map<String, TextStyle> map) {
        if (doc == null) return;

        NodeList styleNodes = doc.getElementsByTagNameNS("*", "style");
        for (int i = 0; i < styleNodes.getLength(); i++) {
            Element style = (Element) styleNodes.item(i);
            String styleName = style.getAttribute("style:name");
            if (styleName == null || styleName.isEmpty()) continue;

            boolean b = false, it = false, u = false;
            boolean sub = false, sup = false;

            NodeList textPropsList = style.getElementsByTagNameNS("*", "text-properties");
            for (int j = 0; j < textPropsList.getLength(); j++) {
                Element props = (Element) textPropsList.item(j);

                // --- Bold / Italic / Underline ---
                String fw = props.getAttribute("fo:font-weight");
                String fs = props.getAttribute("fo:font-style");
                String underline = props.getAttribute("style:text-underline-style");
                String u2 = props.getAttribute("style:text-underline-type");
                String u3 = props.getAttribute("text-underline-style"); // fallback

                if (fw != null && (fw.equalsIgnoreCase("bold") || fw.matches(".*[6-9]00.*"))) b = true;
                if (fs != null && fs.equalsIgnoreCase("italic")) it = true;
                if ((underline != null && !underline.isEmpty() && !"none".equalsIgnoreCase(underline))
                 || (u2 != null && !u2.isEmpty() && !"none".equalsIgnoreCase(u2))
                 || (u3 != null && !u3.isEmpty() && !"none".equalsIgnoreCase(u3))) {
                    u = true;
                }

                // fallback générique pour font-weight sans préfixe
                if (!b) {
                    String fwGeneric = props.getAttribute("font-weight");
                    if (fwGeneric != null && (fwGeneric.contains("bold") || fwGeneric.matches(".*[6-9]00.*"))) b = true;
                }

                // --- Subscript / Superscript (style:text-position / text-position) ---
                String tp1 = props.getAttribute("style:text-position");
                String tp2 = props.getAttribute("text-position");
                TextStyle tmp = new TextStyle(); // vide, juste pour utiliser applyTextPosition
                applyTextPosition(tp1, tmp);
                applyTextPosition(tp2, tmp);
                sub |= tmp.subscript;
                sup |= tmp.superscript;
            }

            // Enregistre une seule fois si au moins une propriété est vraie
            if (b || it || u || sub || sup) {
                TextStyle ts = new TextStyle(b, it, u);
                ts.subscript = sub;
                ts.superscript = sup;
                map.put(styleName, ts);
            }
        }
    }


    /**
     * Résout l'ensemble des propriétés (bold/italic/underline) pour un style donné
     * en remontant la chaîne d'héritage styleToParent. Les propriétés sont OR-ées.
     */
    private static TextStyle resolveTextStyleFromStyleName(
            String styleName,
            Map<String, TextStyle> directMap,
            Map<String, String> styleToParent) {

        boolean b = false, it = false, u = false;
        boolean sub = false, sup = false;

        Set<String> seen = new HashSet<>();
        String cur = styleName;

        while (cur != null && !cur.isEmpty() && !seen.contains(cur)) {
            seen.add(cur);
            TextStyle d = directMap.get(cur);
            if (d != null) {
                b   |= d.bold;
                it  |= d.italic;
                u   |= d.underline;
                sub |= d.subscript;
                sup |= d.superscript;
            }
            cur = styleToParent.get(cur);
        }

        TextStyle ts = new TextStyle(b, it, u);
        ts.subscript = sub;
        ts.superscript = sup;
        return ts;
    }


    /**
     * Lit attributs inline (ex. sur <text:span>) et retourne un TextStyle
     */
    private static TextStyle getInlineTextStyle(Element e) {
        if (e == null) return null;

        boolean b = false, it = false, u = false;

        // --- Gras / Italique / Souligné
        String fw = e.getAttribute("fo:font-weight");
        if (fw == null || fw.isEmpty()) fw = e.getAttribute("font-weight");
        if (fw != null && (fw.equalsIgnoreCase("bold") || fw.matches(".*[6-9]00.*"))) b = true;

        String fs = e.getAttribute("fo:font-style");
        if (fs == null || fs.isEmpty()) fs = e.getAttribute("font-style");
        if (fs != null && fs.equalsIgnoreCase("italic")) it = true;

        String underline = e.getAttribute("style:text-underline-style");
        if (underline == null || underline.isEmpty()) underline = e.getAttribute("text-underline-style");
        if (underline == null || underline.isEmpty()) underline = e.getAttribute("style:text-underline-type");
        if (underline != null && !underline.isEmpty() && !"none".equalsIgnoreCase(underline)) u = true;

        // --- Indice / Exposant
        String tp1 = e.getAttribute("style:text-position");
        String tp2 = e.getAttribute("text-position");

        TextStyle t = new TextStyle(b, it, u);
        applyTextPosition(tp1, t);
        applyTextPosition(tp2, t);

        // 🔴 Ne PAS se fier à isEmpty() si elle ignore sub/sup
        boolean any = b || it || u || t.subscript || t.superscript;
        return any ? t : null;
    }



    // Ancienne API -> appelle la nouvelle avec un style vide
    private static void parseContentInOrder(Node node,
            Map<String, String> styleToParent,
            Map<String, TextStyle> directTextStyleMap,
            Map<String, Integer> headingLevels,
            StringBuilder result, boolean listenumerote,
            Document stylesDoc, Document contentDoc) {
        parseContentInOrder(node, styleToParent, directTextStyleMap, headingLevels,
                result, listenumerote, stylesDoc, contentDoc, new TextStyle());
    }

    // Nouvelle API avec style hérité
    private static void parseContentInOrder(Node node,
            Map<String, String> styleToParent,
            Map<String, TextStyle> directTextStyleMap,
            Map<String, Integer> headingLevels,
            StringBuilder result, boolean listenumerote,
            Document stylesDoc, Document contentDoc,
            TextStyle inheritedStyle) {
    	if (node.getNodeType() == Node.TEXT_NODE) {
    	    String txt = node.getNodeValue();
    	    if (txt != null && !txt.isEmpty()) {
    	        if (txt.trim().isEmpty()) {
    	            result.append(txt);
    	        } else {
    	            result.append(wrapWithMarkers(txt, inheritedStyle));
    	        }
    	    }
    	    return;
    	}
        if (node.getNodeType() != Node.ELEMENT_NODE) return;

        boolean listenumerote2 = listenumerote;
        Element element = (Element) node;
        String tag = element.getLocalName();

        if (!isInsideListItem(element)) listenumerote2 = false;

        switch (tag) {
            case "p": {
                String styleName = element.getAttribute("text:style-name");

                // Détecte page break (inchangé)
                if (hasPageBreakStyle(styleName, stylesDoc, contentDoc)) {
                    result.append("@saut de page manuel\n");
                }

                // Détecte heading (inchangé)
                String levelAttr = element.getAttribute("text:outline-level");
                int level = -1;
                if (levelAttr != null && !levelAttr.isEmpty()) {
                    try { level = Integer.parseInt(levelAttr); } catch (Exception ignored) { level = -1; }
                } else {
                    level = resolveHeadingLevelFromStyle(styleName, styleToParent, headingLevels);
                }
                if (level > 0 && level < 11)       result.append("#").append(level).append(". ");
                else if (level == 11)              result.append("#P. ");
                else if (level == 12)              result.append("#S. ");

                // ⬅️ Style du paragraphe + hérité
                TextStyle pStyle = (styleName == null || styleName.isEmpty())
                        ? new TextStyle()
                        : resolveTextStyleFromStyleName(styleName, directTextStyleMap, styleToParent);
                TextStyle nextInherited = TextStyle.merge(inheritedStyle, pStyle);

                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    parseContentInOrder(children.item(i), styleToParent, directTextStyleMap, headingLevels,
                            result, listenumerote2, stylesDoc, contentDoc, nextInherited);
                }
                if (!isInsideListItem(element)) result.append("\n");
                break;
            }

            case "span": {
                String styleName = element.getAttribute("text:style-name");
                if (styleName == null || styleName.isEmpty())
                    styleName = element.getAttribute("style:style-name");

                TextStyle spanNamed = (styleName == null || styleName.isEmpty())
                        ? new TextStyle()
                        : resolveTextStyleFromStyleName(styleName, directTextStyleMap, styleToParent);
                TextStyle spanInline = getInlineTextStyle(element);
                TextStyle spanStyle = TextStyle.merge(spanNamed, spanInline);

                // ⬅️ On ne wrappe PAS ici ; on propage le style cumulé
                TextStyle nextInherited = TextStyle.merge(inheritedStyle, spanStyle);

                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    parseContentInOrder(children.item(i), styleToParent, directTextStyleMap, headingLevels,
                            result, listenumerote2, stylesDoc, contentDoc, nextInherited);
                }
                break;
            }

            case "a": {
                String href = element.getAttributeNS(XLINK_NS, "href");
                if (href == null || href.isEmpty()) href = element.getAttribute("xlink:href");

                StringBuilder labelSb = new StringBuilder();
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    // ⬅️ propage le style hérité dans le libellé
                    parseContentInOrder(children.item(i), styleToParent, directTextStyleMap, headingLevels,
                            labelSb, listenumerote2, stylesDoc, contentDoc, inheritedStyle);
                }
                String label = escapeLabelForAtLink(labelSb.toString().trim());

                if (href == null || href.isBlank()) {
                    result.append(label);
                } else {
                    result.append("@[").append(label).append(": ").append(href.trim()).append("]");
                }
                break;
            }

            case "note": {
                String noteText = extractNoteBodyText(element).trim();
                if (!noteText.isEmpty()) result.append("@(").append(noteText).append(")");
                break;
            }

            case "tab": {
                result.append("[tab]");
                break;
            }

            case "h": {
                String levelStr = element.getAttribute("text:outline-level");
                int level = levelStr.isEmpty() ? -1 : Integer.parseInt(levelStr);
                result.append("#").append(level).append(". ")
                      .append(element.getTextContent().trim()).append("\n");
                break;
            }

            case "list": {
                String styleName = element.getAttribute("text:style-name");
                int level = resolveHeadingLevelFromStyle(styleName, styleToParent, headingLevels);
                listenumerote2 = (level == 13);
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    parseContentInOrder(children.item(i), styleToParent, directTextStyleMap, headingLevels,
                            result, listenumerote2, stylesDoc, contentDoc, inheritedStyle);
                }
                break;
            }

            case "list-item": {
                if (listenumerote2) result.append("1. "); else result.append("-. ");
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    parseContentInOrder(children.item(i), styleToParent, directTextStyleMap, headingLevels,
                            result, listenumerote2, stylesDoc, contentDoc, inheritedStyle);
                }
                result.append("\n");
                break;
            }

            case "frame": { emitImageMarkup(element, result); break; }
            case "image": { emitImageMarkup(element, result); break; }

            case "table": {
                // (inchangé) — si tu appelles parseContentInOrder(...) dedans,
                // pense à propager 'inheritedStyle' mais ici on génère les lignes nous-mêmes.
                result.append("@t\n");
                NodeList headers = element.getElementsByTagNameNS("*", "table-header-rows");
                if (headers.getLength() > 0) {
                    Element thead = (Element) headers.item(0);
                    NodeList hRows = thead.getChildNodes();
                    for (int r = 0; r < hRows.getLength(); r++) {
                        Node rn = hRows.item(r);
                        if (rn.getNodeType() != Node.ELEMENT_NODE) continue;
                        Element row = (Element) rn;
                        if (!"table-row".equals(row.getLocalName())) continue;
                        java.util.List<String> cells = renderRowCells(row, styleToParent, directTextStyleMap, headingLevels, stylesDoc, contentDoc);
                        int repeat = getRowRepeat(row);
                        for (int k = 0; k < repeat; k++) appendTableLine(result, cells, true);
                    }
                }
                NodeList rows = element.getChildNodes();
                for (int r = 0; r < rows.getLength(); r++) {
                    Node rn = rows.item(r);
                    if (rn.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element e2 = (Element) rn;
                    String l2 = e2.getLocalName();
                    if ("table-header-rows".equals(l2)) continue;
                    if (!"table-row".equals(l2)) continue;
                    java.util.List<String> cells = renderRowCells(e2, styleToParent, directTextStyleMap, headingLevels, stylesDoc, contentDoc);
                    int repeat = getRowRepeat(e2);
                    for (int k = 0; k < repeat; k++) appendTableLine(result, cells, false);
                }
                result.append("@/t\n");
                break;
            }

            default: {
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    parseContentInOrder(children.item(i), styleToParent, directTextStyleMap, headingLevels,
                            result, listenumerote2, stylesDoc, contentDoc, inheritedStyle);
                }
                break;
            }
        }
    }

    
    private static String applyBIU(String s, boolean b, boolean it, boolean u) {
	    String t = s;
	    if (u && b && it)        t = "_*^" + t + "^*_";
	    else if (u && b)         t = "_*"  + t + "*_";
	    else if (u && it)        t = "_^"  + t + "^_";
	    else if (b && it)        t = "*^"  + t + "^*";
	    else if (b)              t = "**"  + t + "**";
	    else if (it)             t = "^^"  + t + "^^";
	    else if (u)              t = "__"  + t + "__";
	    return t;
    }

	private static String wrapWithMarkers(String text, TextStyle ts) {
	    if (ts == null) return text;
	
	    // on calcule d’abord B/I/U
	    String core = applyBIU(text, ts.bold, ts.italic, ts.underline);
	
	    // puis on pose super/sub par-dessus (prioritaires)
	    if (ts.superscript) return "^¨" + core + "¨^";
	    if (ts.subscript)   return "_¨" + core + "¨_";
	    return core;
	}
	
	    private static boolean hasPageBreakStyle(String styleName, Document stylesDoc, Document contentDoc) {
	        return hasBreakBeforeInDoc(styleName, stylesDoc) || hasBreakBeforeInDoc(styleName, contentDoc);
	    }
	
	    private static boolean hasBreakBeforeInDoc(String styleName, Document doc) {
	        if (doc == null || styleName == null || styleName.isEmpty()) return false;
	        NodeList styleNodes = doc.getElementsByTagNameNS("*", "style");
	        for (int i = 0; i < styleNodes.getLength(); i++) {
	            Element style = (Element) styleNodes.item(i);
	            if (styleName.equals(style.getAttribute("style:name"))) {
	                NodeList children = style.getElementsByTagNameNS("*", "paragraph-properties");
	                for (int j = 0; j < children.getLength(); j++) {
	                    Element props = (Element) children.item(j);
	                    String breakBefore = props.getAttribute("fo:break-before");
	                    if ("page".equalsIgnoreCase(breakBefore)) {
	                        return true;
	                    }
	                }
	            }
	        }
	        return false;
    }
    
    /**
     * Retourne le texte "brut" du <text:note-body> d’un <text:note>,
     * sans le numéro de note. On se contente du textContent du note-body,
     * qui exclut naturellement <text:note-citation>.
     */
    private static String extractNoteBodyText(Element noteElem) {
        NodeList bodies = noteElem.getElementsByTagNameNS("*", "note-body");
        if (bodies.getLength() == 0) return "";
        Element body = (Element) bodies.item(0);

        // Récupère le texte. Si tu veux conserver des sauts de ligne entre <text:p>,
        // on peut nettoyer un peu (remplacer plusieurs blancs par un espace).
        String raw = body.getTextContent();
        if (raw == null) return "";
        // Optionnel: compacter l’espace
        return raw.replaceAll("\\s+", " ").trim();
    }
    
    /** Renvoie le texte du premier enfant <svg:title> sous e (ou vide). */
    private static String getSvgTitle(Element e) {
        NodeList titles = e.getElementsByTagNameNS(SVG_NS, "title");
        if (titles.getLength() > 0) {
            String t = titles.item(0).getTextContent();
            if (t != null) return t.trim();
        }
        return "";
    }

    /** Renvoie le texte du premier enfant <svg:desc> sous e (ou vide). */
    private static String getSvgDesc(Element e) {
        NodeList descs = e.getElementsByTagNameNS(SVG_NS, "desc");
        if (descs.getLength() > 0) {
            String t = descs.item(0).getTextContent();
            if (t != null) return t.trim();
        }
        return "";
    }

    /** Écrit dans result la ligne LisioWriter pour une image trouvée dans un frame ou un image. */
    private static void emitImageMarkup(Element frameOrImage, StringBuilder result) {
	    String alt  = getSvgTitle(frameOrImage);
	    String desc = getSvgDesc(frameOrImage);
	    String cap  = "";
	
	    if (frameOrImage.getLocalName().equals("frame")) {
	        NodeList imgs = frameOrImage.getElementsByTagNameNS(DRAW_NS, "image");
	        if (imgs.getLength() > 0) {
	            Element img = (Element) imgs.item(0);
	            if (alt.isBlank())  alt  = getSvgTitle(img);
	            if (desc.isBlank()) desc = getSvgDesc(img);
	        }
	        // ⬅️ ici : récupère la légende (caption, text-box, ou <text:p> suivant)
	        cap = getFrameCaption(frameOrImage);

	        if (alt.isBlank()) {
	            String name = frameOrImage.getAttribute("draw:name");
	            if (name != null && !name.isBlank()) alt = name.trim();
	        }
	    }

	    // Nettoyage espaces
	    alt  = alt.replaceAll("\\s+", " ").trim();
	    desc = desc.replaceAll("\\s+", " ").trim();
	    cap  = cap.replaceAll("\\s+", " ").trim();
	
	    String label = buildImageLabel(alt, desc, cap);
	    result.append("![Image: ").append(label).append("]");
    }

    /** Echappe le contenu d'une cellule vers la syntaxe LisioWriter (table). */
    private static String escapeForTableCell(String s) {
        if (s == null) return "";
        // Ne pas toucher aux marqueurs [tab] produits par parseContentInOrder
        // On supprime CR, remplace NL par espace pour "aplatir" les <text:p> des cellules
        String t = s.replace("\r", "").replace("\n", " ").trim();
        // Echapper \ puis |
        t = t.replace("\\", "\\\\").replace("|", "\\|");
        return t;
    }

    /** Convertit le contenu d'une cellule ODT en texte LisioWriter (inline conservé). */
    private static String collectCellText(Element tableCell,
                                          Map<String, String> styleToParent,
                                          Map<String, TextStyle> directTextStyleMap,
                                          Map<String, Integer> headingLevels,
                                          Document stylesDoc, Document contentDoc) {
        StringBuilder sb = new StringBuilder();
        NodeList kids = tableCell.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            // On réutilise le parseur inline/paragraphes,
            // mais on "aplatit" ensuite (les \n seront remplacés par des espaces).
            parseContentInOrder(kids.item(i), styleToParent, directTextStyleMap, headingLevels,
                    sb, /*listenumerote*/ false, stylesDoc, contentDoc);
        }
        return escapeForTableCell(sb.toString());
    }

    /** Parse une ligne <table:table-row> et renvoie la ligne formatée (|…|…|). */
    private static java.util.List<String> renderRowCells(Element row,
                                                         Map<String, String> styleToParent,
                                                         Map<String, TextStyle> directTextStyleMap,
                                                         Map<String, Integer> headingLevels,
                                                         Document stylesDoc, Document contentDoc) {
        java.util.List<String> cellsOut = new java.util.ArrayList<>();
        NodeList cells = row.getChildNodes();
        for (int c = 0; c < cells.getLength(); c++) {
            Node n = cells.item(c);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element ce = (Element) n;
            String local = ce.getLocalName();
            // covered-table-cell = cellule couverte par un spanning → vide
            if ("covered-table-cell".equals(local)) {
                // respectons number-columns-repeated si présent
                int rep = 1;
                try { rep = Integer.parseInt(ce.getAttribute("table:number-columns-repeated")); } catch (Exception ignored) {}
                for (int k = 0; k < Math.max(rep, 1); k++) cellsOut.add("");
                continue;
            }
            if (!"table-cell".equals(local)) continue;

            int rep = 1;
            try { rep = Integer.parseInt(ce.getAttribute("table:number-columns-repeated")); } catch (Exception ignored) {}

            String cellText = collectCellText(ce, styleToParent, directTextStyleMap, headingLevels, stylesDoc, contentDoc);
            for (int k = 0; k < Math.max(rep, 1); k++) cellsOut.add(cellText);
        }
        return cellsOut;
    }

    /** Ecrit une ligne de tableau dans result, avec préfixe | ou |! selon header. */
    private static void appendTableLine(StringBuilder result, java.util.List<String> cells, boolean header) {
        result.append(header ? "|! " : "| ");
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) result.append(" | ");
            result.append(cells.get(i));
        }
        result.append("\n");
    }

    /** Déroule un éventuel table:number-rows-repeated sur une <table:table-row>. */
    private static int getRowRepeat(Element row) {
        try {
            String v = row.getAttribute("table:number-rows-repeated");
            if (v != null && !v.isBlank()) return Math.max(1, Integer.parseInt(v.trim()));
        } catch (Exception ignored) {}
        return 1;
    }

    /** Construit "description. Légende : …" en priorisant desc > alt > "Image".
     *  Évite les doublons quand la légende commence déjà par la description. */
    private static String buildImageLabel(String alt, String desc, String caption) {
        alt     = (alt     == null) ? "" : alt.trim();
        desc    = (desc    == null) ? "" : desc.trim();
        caption = (caption == null) ? "" : caption.trim();

        // Phrase de base : description > alt > "Image"
        String base = !desc.isEmpty() ? desc : (!alt.isEmpty() ? alt : "Image");

        if (caption.isEmpty()) return base;

        // --- Déduplication : si la légende commence par la base, on l'enlève (avec ponctuations/espaces)
        String capClean = caption.replaceAll("\\s+", " ").trim();
        String baseNorm = base.replaceAll("\\s+", " ").trim();

        // Enlève "base" au début de la légende, tolérant ponctuation et espaces après
        String prefixRegex = "^" + Pattern.quote(baseNorm) + "\\s*[\\p{Punct}\\s]*";
        capClean = capClean.replaceFirst(prefixRegex, "").trim();

        // Si après nettoyage il ne reste rien, on ne rajoute pas "Légende : ..."
        if (capClean.isEmpty() || capClean.equalsIgnoreCase(baseNorm)) {
            return base;
        }

        // Ajoute un point si nécessaire à la fin de base
        char last = base.isEmpty() ? '\0' : base.charAt(base.length() - 1);
        if (last != '.' && last != '!' && last != '?' && last != ':' && last != ';') {
            base += ".";
        }
        return base + " Légende : " + capClean;
    }


    private static boolean looksLikeCaptionStyle(String styleName) {
        if (styleName == null) return false;
        String s = styleName.toLowerCase();
        return s.contains("caption") || s.contains("illustration") || s.contains("légende") || s.contains("legende");
    }

    /** Renvoie la légende associée au frame :
     *  - <draw:caption> interne
     *  - texte d'un <draw:text-box> interne
     *  - paragraphe <text:p> immédiatement suivant avec un style de type "Caption/Illustration/Légende"
     */
    private static String getFrameCaption(Element frame) {
        // 1) <draw:caption>
        NodeList caps = frame.getElementsByTagNameNS(DRAW_NS, "caption");
        if (caps.getLength() > 0) {
            String t = caps.item(0).getTextContent();
            if (t != null && !t.trim().isEmpty()) return t.trim();
        }

        // 2) <draw:text-box> → concat texte
        NodeList tboxes = frame.getElementsByTagNameNS(DRAW_NS, "text-box");
        if (tboxes.getLength() > 0) {
            String t = tboxes.item(0).getTextContent();
            if (t != null && !t.trim().isEmpty()) return t.replaceAll("\\s+", " ").trim();
        }

        // 3) Paragraphe juste après le frame, de type "Caption/Illustration/Légende"
        Node sib = frame.getNextSibling();
        while (sib != null && sib.getNodeType() == Node.TEXT_NODE && sib.getNodeValue().trim().isEmpty()) {
            sib = sib.getNextSibling();
        }
        if (sib != null && sib.getNodeType() == Node.ELEMENT_NODE) {
            Element e = (Element) sib;
            if ("p".equals(e.getLocalName())) {
                String st = e.getAttribute("text:style-name");
                if (looksLikeCaptionStyle(st)) {
                    String t = e.getTextContent();
                    if (t != null && !t.trim().isEmpty()) return t.replaceAll("\\s+", " ").trim();
                }
            }
        }
        return "";
    }

    private static void applyTextPosition(String value, TextStyle ts) {
        if (value == null || value.isBlank() || ts == null) return;
        String v = value.toLowerCase().trim();
        // cas simples
        if (v.startsWith("super") || v.contains("sup")) {
            ts.superscript = true;
            ts.subscript = false;
            return;
        }
        if (v.startsWith("sub")) {
            ts.subscript = true;
            ts.superscript = false;
            return;
        }
        // cas numériques: "x% y%" -> y% positif = super, négatif = sub (convention LO)
        // Exemple: "0% 58%"  / "0% -33%"
        String[] parts = v.replace(',', '.').split("\\s+");
        for (String p : parts) {
            if (p.endsWith("%")) {
                try {
                    double n = Double.parseDouble(p.substring(0, p.length()-1));
                    if (n > 0) { ts.superscript = true; ts.subscript = false; return; }
                    if (n < 0) { ts.subscript = true; ts.superscript = false; return; }
                } catch (Exception ignored) {}
            }
        }
    }



}
