package Import;

//HtmlImporter.java
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

/**
* Convertit un document HTML en markup "blindWriter".
* Règles simples :
*  - h1 -> "#1. ", h2 -> "#2. " ...
*  - p -> double saut de ligne (nouveau paragraphe)
*  - ol/ul -> listes (ol numérotée, ul avec "- ")
*  - strong/b/ em/i / u -> marqueurs inline
*  - a -> "texte (URL)"
*  - img -> "Image: alt (src)"
*/
public final class HtmlImporter {

 // Options (tu peux en faire configurable)
 private static final String BOLD_OPEN = "*";
 private static final String BOLD_CLOSE = "*";
 private static final String ITALIC_OPEN = "_";
 private static final String ITALIC_CLOSE = "_";
 private static final String UNDERLINE_OPEN = "__";
 private static final String UNDERLINE_CLOSE = "__";

 public static String importFileToBlindWriter(File htmlFile, String baseUri) throws IOException {
     Document doc = Jsoup.parse(htmlFile, StandardCharsets.UTF_8.name(), baseUri == null ? "" : baseUri);
     Element body = doc.body();
     StringBuilder out = new StringBuilder();
     traverseChildren(body, out, 0, null);
     return tidyOutput(out.toString());
 }

 private static void traverseChildren(Node node, StringBuilder out, int listDepth, AtomicInteger olCounter) {
     List<Node> children = node.childNodes();
     for (Node child : children) {
         if (child instanceof TextNode) {
             String t = ((TextNode) child).text();
             // nettoie espaces multiples mais conserve retours raisonnables
             out.append(normalizeInlineText(t));
         } else if (child instanceof Element) {
             Element e = (Element) child;
             String tag = e.tagName().toLowerCase();

             switch (tag) {
                 case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
                     int level = Integer.parseInt(tag.substring(1));
                     out.append("\n\n#").append(level).append(". ");
                     // contenu inline du titre
                     traverseChildren(e, out, 0, null);
                     out.append("\n\n");
                     break;

                 case "p":
                     out.append("\n");
                     traverseChildren(e, out, 0, null);
                     out.append("\n\n");
                     break;

                 case "br":
                     out.append("\n");
                     break;

                 case "strong": case "b":
                     out.append(BOLD_OPEN);
                     traverseChildren(e, out, listDepth, olCounter);
                     out.append(BOLD_CLOSE);
                     break;

                 case "em": case "i":
                     out.append(ITALIC_OPEN);
                     traverseChildren(e, out, listDepth, olCounter);
                     out.append(ITALIC_CLOSE);
                     break;

                 case "u":
                     out.append(UNDERLINE_OPEN);
                     traverseChildren(e, out, listDepth, olCounter);
                     out.append(UNDERLINE_CLOSE);
                     break;

                 case "a": {
                     String text = e.text();
                     String href = e.absUrl("href");
                     if (href == null || href.isEmpty()) href = e.attr("href");
                     // format lisible pour lecteur d'écran : "texte (URL)"
                     out.append(text);
                     if (href != null && !href.isEmpty()) {
                         out.append(" (").append(href).append(")");
                     }
                     break;
                 }

                 case "img": {
                     String alt = e.attr("alt");
                     String src = e.absUrl("src");
                     if (src == null || src.isEmpty()) src = e.attr("src");
                     out.append("\n[Image");
                     if (alt != null && !alt.isEmpty()) out.append(": ").append(alt);
                     if (src != null && !src.isEmpty()) out.append(" (").append(src).append(")");
                     out.append("]\n");
                     break;
                 }

                 case "ul":
                     traverseList(e, out, false, listDepth + 1);
                     out.append("\n");
                     break;

                 case "ol":
                     traverseList(e, out, true, listDepth + 1);
                     out.append("\n");
                     break;

                 case "pre":
                     out.append("\n\n"); // bloc préformaté
                     out.append(e.text());
                     out.append("\n\n");
                     break;

                 case "table":
                     // simplification : extraire texte ligne par ligne
                     Elements rows = e.select("tr");
                     for (Element r : rows) {
                         Elements cols = r.select("th,td");
                         boolean firstCol = true;
                         for (Element c : cols) {
                             if (!firstCol) out.append(" | ");
                             out.append(c.text());
                             firstCol = false;
                         }
                         out.append("\n");
                     }
                     out.append("\n");
                     break;

                 default:
                     // Par défaut on descend dans l'arbre
                     traverseChildren(e, out, listDepth, olCounter);
             }
         }
     }
 }

 private static void traverseList(Element listElement, StringBuilder out, boolean ordered, int depth) {
     int index = 1;
     for (Element li : listElement.children()) {
         if (!li.tagName().equalsIgnoreCase("li")) continue;
         // indentation selon profondeur
         String indent = "    ".repeat(Math.max(0, depth - 1));
         if (ordered) {
             out.append(indent).append(index).append(". ");
         } else {
             out.append(indent).append("- ");
         }
         // contenu du li (peut contenir des sous-listes)
         traverseChildren(li, out, depth, null);
         out.append("\n");
         // gérer sous-listes déjà traitées par traverseChildren
         index++;
     }
 }

	 private static String normalizeInlineText(String s) {
	     // remplace plusieurs espaces par un seul, mais conserve les retours de ligne
	     return s.replaceAll("\\s+", " ");
	 }
	
	 private static String tidyOutput(String s) {
	     // nettoyage final: collapse >2 nouvelles lignes en 2
	     return s.replaceAll("[\\t ]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim() + "\n\n";
	 }
	 
	//Dans HtmlImporter.java (déjà importé org.jsoup.*)
	public static String importFromUrl(String url) throws IOException {
	  // User-Agent pour éviter certains blocages
	  Document doc = Jsoup.connect(url)
	                      .userAgent("blindWriter/1.0 (+https://example.org)")
	                      .timeout(15000) // 15s
	                      .followRedirects(true)
	                      .get();
	  Element body = doc.body();
	  StringBuilder out = new StringBuilder();
	  // Réutilise la traversée existante (traverseChildren)
	  traverseChildren(body, out, 0, null);
	  // Ajoute le titre/metadatas en tête si présent
	  String title = doc.title();
	  if (title != null && !title.isBlank()) {
	      out.insert(0, "#1. " + title + "\n\n");
	  }
	  // Ajoute la source en pied pour rappel
	  out.append("\n\nSource: ").append(url).append("\n");
	  return tidyOutput(out.toString());
	}


}

