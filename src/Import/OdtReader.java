package Import;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;




/**
 * OdtReader amélioré : corrige extraction styles, héritage, spans inline et spans imbriqués,
 * et améliore la détection des styles de titre (p.ex. "Heading 1", "Heading_20_1", "Titre 1", ou texte avec text:outline-level).
 */
public class OdtReader {
	
	private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

	/** Empêche le libellé de casser la syntaxe @[Texte: URL] */
	private static String escapeLabelForAtLink(String s) {
	    if (s == null) return "";
	    // ']' termine le lien, on le remplace par ')'
	    s = s.replace(']', ')');
	    // ':' sépare libellé et URL, on le remplace par le deux-points math. U+2236
	    s = s.replace(':', '∶'); // visuellement proche, n'entre pas en collision avec le séparateur
	    return s;
	}


    private static class TextStyle {
        boolean bold;
        boolean italic;
        boolean underline;

        TextStyle() {}
        TextStyle(boolean b, boolean i, boolean u) { bold = b; italic = i; underline = u; }
        void or(TextStyle other) {
            if (other == null) return;
            bold = bold || other.bold;
            italic = italic || other.italic;
            underline = underline || other.underline;
        }
        boolean isEmpty() { return !bold && !italic && !underline; }
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
        return result.toString();
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

            NodeList textPropsList = style.getElementsByTagNameNS("*", "text-properties");
            boolean b=false, it=false, u=false;
            for (int j = 0; j < textPropsList.getLength(); j++) {
                Element props = (Element) textPropsList.item(j);

                // lire plusieurs formes d'attribut (parfois prefix diffèrent)
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

                // fallback generique
                if (!b) {
                    String fwGeneric = props.getAttribute("font-weight");
                    if (fwGeneric != null && (fwGeneric.contains("bold") || fwGeneric.matches(".*[6-9]00.*"))) b = true;
                }
            }
            if (b || it || u) {
                map.put(styleName, new TextStyle(b, it, u));
            }
        }
    }

    /**
     * Résout l'ensemble des propriétés (bold/italic/underline) pour un style donné
     * en remontant la chaîne d'héritage styleToParent. Les propriétés sont OR-ées.
     */
    private static TextStyle resolveTextStyleFromStyleName(String styleName, Map<String, TextStyle> directMap, Map<String, String> styleToParent) {
        boolean b=false, it=false, u=false;
        Set<String> seen = new HashSet<>();
        String cur = styleName;
        while (cur != null && !cur.isEmpty() && !seen.contains(cur)) {
            seen.add(cur);
            TextStyle d = directMap.get(cur);
            if (d != null) {
                b = b || d.bold;
                it = it || d.italic;
                u = u || d.underline;
            }
            cur = styleToParent.get(cur);
        }
        return new TextStyle(b, it, u);
    }

    /**
     * Lit attributs inline (ex. sur <text:span>) et retourne un TextStyle
     */
    private static TextStyle getInlineTextStyle(Element e) {
        if (e == null) return null;
        boolean b=false, it=false, u=false;

        // vérifier attributs communs
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

        if (b || it || u) return new TextStyle(b, it, u);
        return null;
    }

    /**
     * Concatène le texte structuré en descendant l'arbre.
     * - gère spans imbriqués correctement (on construit d'abord le contenu interne, puis on wrap)
     * IMPORTANT: ici on détecte d'abord text:outline-level sur les <p>
     */
    private static void parseContentInOrder(Node node, Map<String, String> styleToParent, Map<String, TextStyle> directTextStyleMap, Map<String, Integer> headingLevels, StringBuilder result, boolean listenumerote, Document stylesDoc, Document contentDoc) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            String txt = node.getNodeValue();
            if (txt != null && !txt.isEmpty()) result.append(txt);
            return;
        }
        if (node.getNodeType() != Node.ELEMENT_NODE) return;

        boolean listenumerote2 = listenumerote;

        Element element = (Element) node;
        String tag = element.getLocalName();

        if (!isInsideListItem(element)) listenumerote2 = false;

        switch (tag) {
            case "h": {
                // <text:h> explicit: outline-level attribute typically present
                String levelStr = element.getAttribute("text:outline-level");
                int level = levelStr.isEmpty() ? -1 : Integer.parseInt(levelStr);
                result.append("#").append(level).append(". ")
                      .append(element.getTextContent().trim()).append("\n");
                break;
            }
            case "p": {
                String styleName = element.getAttribute("text:style-name");

                // si le paragraphe a directement un outline-level (certains ODT exportent ainsi), le prendre PRIORITAIREMENT
                String levelAttr = element.getAttribute("text:outline-level");
                int level = -1;
                if (levelAttr != null && !levelAttr.isEmpty()) {
                    try { level = Integer.parseInt(levelAttr); } catch (Exception ignored) { level = -1; }
                } else {
                    // sinon, tenter de résoudre via le style (avec normalisations / regex fallback)
                    level = resolveHeadingLevelFromStyle(styleName, styleToParent, headingLevels);
                }

                if (hasPageBreakStyle(styleName, stylesDoc, contentDoc)) {
                    result.append("@sautDePage\n");
                }

                if (level > 0 && level < 11) {
                    result.append("#").append(level).append(". ");
                } else if (level == 11) {
                    result.append("#P. ");
                } else if (level == 12) {
                    result.append("#S. ");
                }

                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    parseContentInOrder(children.item(i), styleToParent, directTextStyleMap, headingLevels, result, listenumerote2, stylesDoc, contentDoc);
                }
                result.append("\n");
                break;
            }
            case "list": {
                String styleName = element.getAttribute("text:style-name");
                int level = resolveHeadingLevelFromStyle(styleName, styleToParent, headingLevels);
                listenumerote2 = (level == 13);
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    parseContentInOrder(children.item(i), styleToParent, directTextStyleMap, headingLevels, result, listenumerote2, stylesDoc, contentDoc);
                }
                break;
            }
            case "list-item": {
                if (listenumerote2) result.append("1. "); else result.append("-. ");
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    parseContentInOrder(children.item(i), styleToParent, directTextStyleMap, headingLevels, result, listenumerote2, stylesDoc, contentDoc);
                }
                result.append("\n");
                break;
            }
            case "span": {
                // Au lieu de getTextContent() on construit le contenu interne pour préserver styles imbriqués
                String styleName = element.getAttribute("text:style-name");
                if (styleName == null || styleName.isEmpty()) styleName = element.getAttribute("style:style-name");

                // construire contenu interne dans une StringBuilder temporaire
                StringBuilder inner = new StringBuilder();
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    parseContentInOrder(children.item(i), styleToParent, directTextStyleMap, headingLevels, inner, listenumerote2, stylesDoc, contentDoc);
                }

                // déterminer style effectif : héritage des styles nommés + attributs inline
                TextStyle computed = null;
                if (styleName != null && !styleName.isEmpty()) {
                    computed = resolveTextStyleFromStyleName(styleName, directTextStyleMap, styleToParent);
                }
                TextStyle inline = getInlineTextStyle(element);
                if (computed == null) computed = (inline == null ? new TextStyle() : inline);
                else if (inline != null) computed.or(inline);

                // appliquer wrappers si nécessaire
                String wrapped = wrapWithMarkers(inner.toString(), computed);
                result.append(wrapped);
                break;
            }
            case "note": { // text:note (footnote/endnote)
                // Ignore le numéro (text:note-citation) et ne prend que le corps (text:note-body)
                String noteText = extractNoteBodyText(element).trim();
                if (!noteText.isEmpty()) {
                    result.append("@(").append(noteText).append(")");
                }
                break;
            }
            case "note-citation": {
                // On n’affiche jamais le numéro de note
                break;
            }
            case "note-body": {
                // Normalement non atteint (géré via "note"), on ne fait rien ici.
                break;
            }
            case "a": { // <text:a xlink:href="..."> ... </text:a>
                // Récupère l'URL
                String href = element.getAttributeNS(XLINK_NS, "href");
                if (href == null || href.isEmpty()) {
                    // fallback si l'ODT a un attribut non namespacé (rare)
                    href = element.getAttribute("xlink:href");
                }

                // Construit le libellé en préservant les styles imbriqués
                StringBuilder labelSb = new StringBuilder();
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    parseContentInOrder(children.item(i),
                            styleToParent, directTextStyleMap, headingLevels,
                            labelSb, listenumerote2, stylesDoc, contentDoc);
                }
                String label = escapeLabelForAtLink(labelSb.toString().trim());

                if (href == null || href.isBlank()) {
                    // pas d'URL → on garde juste le libellé
                    result.append(label);
                } else {
                    // produit la syntaxe LisioWriter
                    result.append("@[").append(label).append(": ").append(href.trim()).append("]");
                }
                break;
            }

            case "tab": { // <text:tab/>
                result.append("[tab]");
                break;
            }


            default: {
                // descente par défaut
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    parseContentInOrder(children.item(i), styleToParent, directTextStyleMap, headingLevels, result, listenumerote2, stylesDoc, contentDoc);
                }
                break;
            }
        }
    }

    private static String wrapWithMarkers(String text, TextStyle ts) {
        if (ts == null || ts.isEmpty()) return text;
        boolean b = ts.bold;
        boolean it = ts.italic;
        boolean u = ts.underline;

        if (u && b && it) {
            return "_*^" + text + "^*_";
        } else if (u && b) {
            return "_*" + text + "*_";
        } else if (u && it) {
            return "_^" + text + "^_";
        } else if (b && it) {
            return "*^" + text + "^*";
        } else if (b) {
            return "**" + text + "**";
        } else if (it) {
            return "^^" + text + "^^";
        } else if (u) {
            return "__" + text + "__";
        } else {
            return text;
        }
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

}
