package Import;

//HtmlImporter.java
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

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

	//=== Variables de classe ===
	private static int compteurGras = 0;
	private static int compteurItalique = 0;
	private static int compteurSouligne = 0;
	
	

 public static String importFileToBlindWriter(File htmlFile, String baseUri) throws IOException {
     Document doc = Jsoup.parse(htmlFile, StandardCharsets.UTF_8.name(), baseUri == null ? "" : baseUri);
     Element body = doc.body();
     StringBuilder out = new StringBuilder();
     traverseChildren(body, out, 0, null);
     return tidyOutput(out.toString());
 }

 
 private static void traverseChildren(Node node, StringBuilder out, int listDepth, AtomicInteger olCounter) {
	    for (Node child : node.childNodes()) {

	        // ——— Texte brut
	        if (child instanceof TextNode) {
	            out.append(normalizeInlineText(((TextNode) child).text()));
	            continue;
	        }

	        // ——— Autre élément HTML
	        if (!(child instanceof Element)) continue;
	        Element e = (Element) child;
	        String tag = e.tagName().toLowerCase();

	        // Cas spéciaux (titres, paragraphes, etc.)
	        switch (tag) {
	            case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
	                int level = Integer.parseInt(tag.substring(1));
	                out.append("\n\n#").append(level).append(". ");
	                traverseChildren(e, out, 0, null);
	                out.append("\n\n");
	                continue;

	            case "p":
	                out.append("\n");
	                traverseChildren(e, out, 0, null);
	                out.append("\n\n");
	                continue;

	            case "br":
	                out.append("\n");
	                continue;

	            case "ul":
	                traverseList(e, out, false, listDepth + 1);
	                out.append("\n");
	                continue;

	            case "ol":
	                traverseList(e, out, true, listDepth + 1);
	                out.append("\n");
	                continue;

	            case "a": {
	                String text = e.text();
	                String href = e.absUrl("href");
	                if (href == null || href.isEmpty()) href = e.attr("href");
	                out.append(text);
	                if (href != null && !href.isEmpty()) {
	                    out.append(" (").append(href).append(")");
	                }
	                continue;
	            }

	            case "img": {
	                String alt = e.attr("alt");
	                String src = e.absUrl("src");
	                if (src == null || src.isEmpty()) src = e.attr("src");
	                out.append("\n[Image");
	                if (alt != null && !alt.isEmpty()) out.append(": ").append(alt);
	                if (src != null && !src.isEmpty()) out.append(" (").append(src).append(")");
	                out.append("]\n");
	                continue;
	            }
	        }

	        // ——— Pour les styles inline : b, i, u (ou imbriqués)
	        int profondeur = countNestingDepth(e.outerHtml());

	        if (profondeur >= 1) {
	            // Choisir le format LisioWriter selon les compteurs détectés
	            String open = "", close = "";

	            if (compteurGras > 0 && compteurItalique > 0 && compteurSouligne > 0) {
	                open = "_*^"; close = "^*_";
	            } else if (compteurGras > 0 && compteurItalique > 0) {
	                open = "*^"; close = "^*";
	            } else if (compteurGras > 0 && compteurSouligne > 0) {
	                open = "_*"; close = "*_";
	            } else if (compteurItalique > 0 && compteurSouligne > 0) {
	                open = "_^"; close = "^_";
	            } else if (compteurGras > 0) {
	                open = "**"; close = "**";
	            } else if (compteurItalique > 0) {
	                open = "^^"; close = "^^";
	            } else if (compteurSouligne > 0) {
	                open = "__"; close = "__";
	            }

	            out.append(open);
	            traverseChildren(e, out, listDepth, olCounter);
	            out.append(close);
	        } else {
	            traverseChildren(e, out, listDepth, olCounter);
	        }
	    }
	}

 


	/**
	* Analyse un fragment HTML pour déterminer la profondeur d'imbrication
	* et compter les balises de style rencontrées (b, i, u, etc.)
	*
	* @return 0 = aucune balise, 1 = simple, 2 = double, 3 = triple
	*/
	 public static int countNestingDepth(String html) {
		    compteurGras = compteurItalique = compteurSouligne = 0;
		    if (html == null || html.isBlank()) return 0;
	
		    final java.util.regex.Pattern TAG = java.util.regex.Pattern.compile("(?is)</?([a-z0-9]+)\\b[^>]*>");
		    java.util.regex.Matcher m = TAG.matcher(html);
	
		    // ── Vérifie la toute première balise ───────────────────────────
		    if (!m.find()) return 0;                     // pas de balise du tout
		    String firstFull = m.group();                // ex: "<i>" ou "</b>"
		    String firstTag  = m.group(1).toLowerCase(); // ex: "i" ou "b"
	
		    boolean isOpening = !firstFull.startsWith("</");
		    boolean isStyleFirst = firstTag.equals("b") || firstTag.equals("strong")
		                        || firstTag.equals("i") || firstTag.equals("em")
		                        || firstTag.equals("u");
	
		    if (!isOpening || !isStyleFirst) return 0;   // 1ʳᵉ balise non-style -> rejet
	
		    // ── Recommence le parcours depuis le début ─────────────────────
		    m = TAG.matcher(html);
	
		    int depth = 0, maxDepth = 0;
		    while (m.find()) {
		        String full = m.group();                  // balise complète
		        String tag  = m.group(1).toLowerCase();   // nom de balise
	
		        // On ne compte que les balises de style
		        boolean isStyle = false;
		        switch (tag) {
		            case "b":
		            case "strong":
		                compteurGras++;
		                isStyle = true;
		                break;
		            case "i":
		            case "em":
		                compteurItalique++;
		                isStyle = true;
		                break;
		            case "u":
		                compteurSouligne++;
		                isStyle = true;
		                break;
		        }
	
		        if (!isStyle) continue;                   // ignore autres balises
	
		        if (!full.startsWith("</")) depth++;      // ouverture style
		        else depth--;                             // fermeture style
	
		        if (depth > maxDepth) maxDepth = depth;
		    }
	
		    // limite à 3 (gras + italique + souligné)
		    return Math.min(maxDepth, 3);
		}

 
	private static void traverseList(Element listElement, StringBuilder out, boolean ordered, int depth) {
	    int index = 1;
	    for (Element li : listElement.children()) {
	        if (!li.tagName().equalsIgnoreCase("li")) continue;
	        // 🔹 Format LisioWriter : "-." pour toutes les listes non ordonnées
	        if (ordered) {
	            out.append(index).append(". ");
	        } else {
	            out.append("-. ");
	        }

	        traverseChildren(li, out, depth, null);
	        out.append("\n");
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
	                      .userAgent("LisioWriter/1.0 (+https://example.org)")
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
	
	/**
	 * Convertit directement une chaîne HTML (déjà téléchargée ou filtrée)
	 * vers le format blindWriter.
	 */
	public static String importFromHtml(String html) throws IOException {
	    if (html == null || html.isBlank()) return "";

	    // Parse le HTML brut fourni
	    Document doc = Jsoup.parse(html);
	    Element body = doc.body();
	    StringBuilder out = new StringBuilder();

	    // Conversion comme pour importFromUrl()
	    traverseChildren(body, out, 0, null);

	    // Nettoyage final du texte
	    return tidyOutput(out.toString());
	}



}

