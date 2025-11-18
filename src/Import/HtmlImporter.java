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

import writer.ui.editor.BraillePrefixer;

/**
* Convertit un document HTML en markup "lisioWriter".
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
     String text = BraillePrefixer.addBrailleAtParagraphStarts(out.toString());
     return tidyOutput(text);
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
	                out.append("\n#").append(level).append(". ");
	                traverseChildren(e, out, 0, null);
	                out.append("\n");
	                continue;

	            case "p":
	                traverseChildren(e, out, 0, null);
	                out.append("\n");
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
	            	
	            case "table":
	            	convertTable(e, out);
	            	out.append("\n"); // petit espace après le tableau
	            	continue;
	            	
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
	 * vers le format LisioWriter.
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

	// --- Conversion TABLE → syntaxe LisioWriter -------------------------------

	/** Convertit <table> HTML en:
	 *  @t
	 *  |! h1 | h2
	 *  | c1  | c2
	 *  @/t
	 */
	private static void convertTable(Element table, StringBuilder out) {
	    // Ligne blanche avant le tableau si besoin
	    if (out.length() > 0 && !out.toString().endsWith("\n\n")) out.append("\n");

	    out.append("@t").append("\n");

	    // 1) thead (en-têtes)
	    Element thead = table.selectFirst("thead");
	    if (thead != null) {
	        for (Element tr : thead.select("> tr")) {
	            appendTableRow(tr, out, true);
	        }
	    }

	    // 2) tr directement sous table et dans tbody (pour couvrir les deux cas)
	    for (Element tr : table.select("> tr, > tbody > tr")) {
	        // Si on a déjà émis des en-têtes via <thead>, on traite ces <tr> comme des lignes normales
	        appendTableRow(tr, out, hasTh(tr) && thead == null /* header seulement si pas de thead */);
	    }

	    // 3) tfoot (souvent des totaux) → lignes normales
	    Element tfoot = table.selectFirst("tfoot");
	    if (tfoot != null) {
	        for (Element tr : tfoot.select("> tr")) {
	            appendTableRow(tr, out, false);
	        }
	    }

	    out.append("@/t").append("\n");
	}

	/** Ajoute une ligne de tableau. */
	private static void appendTableRow(Element tr, StringBuilder out, boolean header) {
	    // Choix du préfixe: |! pour en-tête, | sinon
	    StringBuilder line = new StringBuilder(header ? "|!" : "|");

	    // On parcourt les cellules dans l’ordre d’apparition
	    for (Element cell : tr.children()) {
	        String tag = cell.tagName().toLowerCase();
	        if (!tag.equals("td") && !tag.equals("th")) continue;

	        String cellText = renderCell(cell);

	        // Échappe les caractères spéciaux de la grammaire LisioWriter (| et \)
	        cellText = escapeTableCell(cellText);

	        line.append(" ").append(cellText).append(" ").append("|");

	        // Gestion (très) simple des colspans: on ajoute des cellules vides en plus
	        int colspan = parsePositiveInt(cell.attr("colspan"), 1);
	        for (int i = 1; i < colspan; i++) {
	            line.append(" ").append("").append(" ").append("|");
	        }

	        // NB: rowspan n’a pas d’équivalent dans la grammaire actuelle → ignoré
	    }

	    // Supprime un éventuel pipe final doublon proprement (si souhaité)
	    // (optionnel) on peut trim mais on garde le pipe de fin pour rester cohérent
	    out.append(line).append("\n");
	}

	/** Rendu de la cellule: on réutilise la logique inline existante (gras/italique/liens...). */
	private static String renderCell(Element cell) {
	    StringBuilder sb = new StringBuilder();
	    // On réutilise la traversée existante pour convertir le contenu HTML interne
	    traverseChildren(cell, sb, 0, null);

	    // Nettoyage léger: une cellule n’a pas besoin d’avoir des sauts multiples
	    String s = sb.toString().trim();
	    // Evite que le tidy global doublement saute des lignes dans une cellule
	    s = s.replace('\n', ' ').replaceAll("\\s+", " ").trim();
	    return s;
	}

	/** Échappe les caractères réservés par la syntaxe des tableaux LisioWriter. */
	private static String escapeTableCell(String s) {
	    if (s == null || s.isEmpty()) return "";
	    // Attention à l’ordre: d’abord \ puis |
	    s = s.replace("\\", "\\\\"); // \\ littéral
	    s = s.replace("|", "\\|");   // barre verticale littérale
	    return s.trim();
	}

	/** Lit un entier positif, ou renvoie fallback. */
	private static int parsePositiveInt(String s, int fallback) {
	    try {
	        int v = Integer.parseInt(s.trim());
	        return (v > 0) ? v : fallback;
	    } catch (Exception ignore) {
	        return fallback;
	    }
	}

	/** Détecte s’il y a au moins un <th> dans la ligne. */
	private static boolean hasTh(Element tr) {
	    return !tr.select("> th").isEmpty();
	}



}

