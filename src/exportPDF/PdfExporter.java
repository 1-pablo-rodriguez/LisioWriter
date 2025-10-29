package exportPDF;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;


public class PdfExporter {

	private static PrintWriter LOG = null;
	private static final Object LOG_LOCK = new Object();
 
    

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String applyInlineMarkers(String escaped) {
        if (escaped == null || escaped.isEmpty()) return escaped;

        // combinaisons underline+bold / underline+italic
        escaped = escaped.replaceAll("(?s)_\\*(.*?)\\*_", "<u><strong>$1</strong></u>");
        escaped = escaped.replaceAll("(?s)_\\^(.*?)\\^_", "<u><em>$1</em></u>");
        
        // subscript / superscript : _¨...¨_ et ^¨...¨^   (¨ = U+00A8)
        escaped = escaped.replaceAll("(?s)_\u00A8(.*?)\u00A8_", "<sub>$1</sub>");
        escaped = escaped.replaceAll("(?s)\\^\u00A8(.*?)\u00A8\\^", "<sup>$1</sup>");

        // bold, italic, underline
        escaped = escaped.replaceAll("(?s)\\*\\*(.*?)\\*\\*", "<strong>$1</strong>");
        escaped = escaped.replaceAll("(?s)\\^\\^(.*?)\\^\\^", "<em>$1</em>");
        escaped = escaped.replaceAll("(?s)__(.*?)__", "<u>$1</u>");

        // nettoyage pour anciennes notations si présentes
        escaped = escaped.replaceAll("(?s)\\*\\^(.*?)\\^\\*", "<strong><em>$1</em></strong>");
        escaped = escaped.replaceAll("(?s)\\^\\*(.*?)\\*\\^", "<em><strong>$1</strong></em>");
        return escaped;
    }

    private static String slugify(String text) {
        if (text == null) return "id";
        String x = text.toLowerCase().replaceAll("[^a-z0-9\\- ]", "").trim().replaceAll("\\s+", "-");
        if (x.isEmpty()) x = "id";
        return x;
    }

    // ---------------- conversion markup -> HTML propre ----------------

    public static String convertMarkupToHtml(String text) {
        if (text == null) return "";

        StringBuilder body = new StringBuilder();
        String[] lines = text.split("\\r?\\n", -1);
        StringBuilder paragraph = new StringBuilder();
        boolean paragraphOpen = false;

        // === Déclarations pour les listes (en dehors de la boucle !) ===
        final Pattern RE_OL = Pattern.compile("^(\\d+)\\.\\s+(.*)$");      // 1. Item
        final Pattern RE_UL = Pattern.compile("^(?:-\\.|[-\\*])\\s+(.*)$"); // -. Item  ou  - / *
        boolean inOl = false, inUl = false;
        
        
        for (int i = 0; i < lines.length; i++) {
        	String raw = lines[i];
            String trimmed = raw.trim();
        	
            // ==== Directives (fermer listes/paragraphe avant) ====
            if (trimmed.equalsIgnoreCase("@sautDePage") 
                || trimmed.equalsIgnoreCase("@saut de page") 
                || trimmed.startsWith("@saut")) {
                if (inOl) { body.append("</ol>\n"); inOl = false; }
                if (inUl) { body.append("</ul>\n"); inUl = false; }
                if (paragraphOpen) {
                    body.append("<p>").append(paragraph).append("</p>\n");
                    paragraph.setLength(0); paragraphOpen = false;
                }
                body.append("<div style=\"page-break-after: always;\"></div>\n");
                continue;
            }
           
            // ==== TOC (fermer listes/paragraphe avant) ====
            if (trimmed.startsWith("@TOC")) {
                if (inOl) { body.append("</ol>\n"); inOl = false; }
                if (inUl) { body.append("</ul>\n"); inUl = false; }
                if (paragraphOpen) {
                    body.append("<p>").append(paragraph).append("</p>\n");
                    paragraph.setLength(0); paragraphOpen = false;
                }
                // On garde la directive telle quelle dans un commentaire, l'analyse se fera ensuite
                body.append("<!-- TOC_PLACEHOLDER ").append(escapeHtml(trimmed)).append(" -->\n");
                continue;
            }
            
        	Matcher hMatcher = Pattern.compile("^#(\\d+)\\.\\s*(.*)$").matcher(raw);
        	Matcher pMatcher = Pattern.compile("^#P\\.\\s*(.*)$").matcher(raw);
        	Matcher sMatcher = Pattern.compile("^#S\\.\\s*(.*)$").matcher(raw);

        	boolean isH = hMatcher.find();
        	boolean isP = pMatcher.find();
        	boolean isS = sMatcher.find();

        	
            // ==== Titres : fermer d'abord les listes ouvertes ====
        	if (isH || isP || isS) {
        	    if (inOl) { body.append("</ol>\n"); inOl = false; }
        	    if (inUl) { body.append("</ul>\n"); inUl = false; }
        	    if (paragraphOpen) {
        	        body.append("<p>").append(paragraph).append("</p>\n");
        	        paragraph.setLength(0); paragraphOpen = false;
        	    }
        	    if (isH) {
        	        int lvl = Math.max(1, Math.min(6, Integer.parseInt(hMatcher.group(1))));
        	        String inside = applyInlineMarkers(escapeHtml(hMatcher.group(2)));
        	        body.append("<h").append(lvl).append(">").append(inside).append("</h").append(lvl).append(">\n");
        	    } else if (isP) {
        	        String inside = applyInlineMarkers(escapeHtml(pMatcher.group(1)));
        	        body.append("<h1>").append(inside).append("</h1>\n");
        	    } else { // isS
        	        String inside = applyInlineMarkers(escapeHtml(sMatcher.group(1)));
        	        body.append("<h2>").append(inside).append("</h2>\n");
        	    }
        	    continue;
        	}

        	// ==== Listes ====
        	Matcher mOl = RE_OL.matcher(raw);
        	Matcher mUl = RE_UL.matcher(raw);

        	if (mOl.matches()) {
        	    if (paragraphOpen) {
        	        body.append("<p>").append(paragraph).append("</p>\n");
        	        paragraph.setLength(0); paragraphOpen = false;
        	    }
        	    if (inUl) { body.append("</ul>\n"); inUl = false; }

        	    int num = 1;
        	    try { num = Integer.parseInt(mOl.group(1)); } catch (Exception ignore) {}
        	    String li = applyInlineMarkers(escapeHtml(mOl.group(2)));

        	    if (!inOl) {
        	        body.append(num != 1 ? "<ol start=\"" + num + "\">\n" : "<ol>\n");
        	        inOl = true;
        	    }
        	    body.append("<li>").append(li).append("</li>\n");
        	    continue;
        	}

        	if (mUl.matches()) {
        	    if (paragraphOpen) {
        	        body.append("<p>").append(paragraph).append("</p>\n");
        	        paragraph.setLength(0); paragraphOpen = false;
        	    }
        	    if (inOl) { body.append("</ol>\n"); inOl = false; }

        	    if (!inUl) { body.append("<ul>\n"); inUl = true; }
        	    String li = applyInlineMarkers(escapeHtml(mUl.group(1)));
        	    body.append("<li>").append(li).append("</li>\n");
        	    continue;
        	}

        	// ==== Ligne vide -> fermer les listes puis le paragraphe ====
        	if (trimmed.isEmpty()) {
        	    if (inOl) { body.append("</ol>\n"); inOl = false; }
        	    if (inUl) { body.append("</ul>\n"); inUl = false; }
        	    if (paragraphOpen) {
        	        body.append("<p>").append(paragraph).append("</p>\n");
        	        paragraph.setLength(0); paragraphOpen = false;
        	    }
        	    continue;
        	}

        	// ==== Texte normal ====
        	if (inOl) { body.append("</ol>\n"); inOl = false; }
        	if (inUl) { body.append("</ul>\n"); inUl = false; }

        	String escaped = applyInlineMarkers(escapeHtml(raw));
        	if (!paragraphOpen) {
        	    paragraphOpen = true;
        	    paragraph.append(escaped);
        	} else {
        	    paragraph.append("<br/>").append(escaped);
        	}
        }

        // Fin de fichier : fermer ce qui reste
        if (inOl) body.append("</ol>\n");
        if (inUl) body.append("</ul>\n");
        if (paragraphOpen) {
            body.append("<p>").append(paragraph).append("</p>\n");
        }


        String css =
        		  "body { font-family: 'DejaVu Sans', sans-serif; }"
        		+ "h1 { font-size: 20pt; margin-top: 1em; }"
        		+ "h2 { font-size: 18pt; margin-top: 0.9em; }"
        		+ "h3 { font-size: 16pt; }"
        		+ "p { margin: 0.4em 0; }"
        		+ "u { text-decoration: underline; }"
        		+ "ol, ul { margin: 0.5em 0 0.5em 1.5em; }"
        		+ "ol { list-style: decimal; margin: 0.5em 0 0.5em 1.5em; }"
        		+ "sub { vertical-align: sub; font-size: 0.8em; }"
        		+ "sup { vertical-align: super; font-size: 0.8em; }"
        		+ "@media print {"
        		+ "  ul { list-style: none; margin: 0.5em 0 0.5em 1.6em; }"
        		+ "  ul li { position: relative; padding-left: 0.90em; }"
        		+ "  ul li::before {"
        		+ "    content: ''; position: absolute; left: 0; top: 0.60em;"
        		+ "    width: 0.34em; height: 0.34em; border-radius: 50%;"
        		+ "    background-color: #000;"
        		+ "  }"
        		+ "}"
        		+ "body.pdf ul { list-style: none; }"
        		+ "body.pdf ul li { position: relative; padding-left: 0.90em; }"
        		+ "body.pdf ul li::before {"
        		+ "  content: '';\r\n"
        		+ "  position: absolute; left: 0; top: 0.60em;"
        		+ "  width: 0.34em; height: 0.34em; border-radius: 50%;"
        		+ "  background-color: #000;"
        		+ "}";


        String html = "<!doctype html>\n<html>\n<head>\n<meta charset='utf-8'/>\n"
                    + "<style>" + css + "</style>\n"
                    + "</head>\n<body>\n"
                    + body.toString()
                    + "\n</body>\n</html>";
        return html;
    }

    // ---------------- TOC: insertion simple ----------------
    // Attends qu'un <!-- TOC_PLACEHOLDER @TOC;... --> existe ; lit paramètres simples si fournis
    private static String insertTocIfRequested(String html) {
        if (html == null || !html.contains("<!-- TOC_PLACEHOLDER")) return html;

        // parse placeholder params (très simple: chercher 'niveau_max=N' et 'Titre=...') 
        int maxLevel = 5;
        String title = "Table des matières";
        Matcher ph = Pattern.compile("<!-- TOC_PLACEHOLDER\\s*(.*?)-->").matcher(html);
        if (ph.find()) {
            String inside = ph.group(1);
            Matcher mlev = Pattern.compile("niveau_max=(\\d+)").matcher(inside);
            if (mlev.find()) {
                try { maxLevel = Integer.parseInt(mlev.group(1)); } catch (Exception e) { maxLevel = 5; }
            }
            Matcher mt = Pattern.compile("Titre=([^;]+)").matcher(inside);
            if (mt.find()) title = mt.group(1).trim();
        }

        // collect headings (h1..h6) up to maxLevel and add ids
        Pattern head = Pattern.compile("(?i)<h([1-6])>(.*?)</h\\1>");
        Matcher m = head.matcher(html);
        List<Heading> heads = new ArrayList<>();
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int lvl = Integer.parseInt(m.group(1));
            String content = m.group(2);
            String id = slugify(content);
            // ensure unique id by appending index if necessary
            String uniqueId = id + "-" + heads.size();
            heads.add(new Heading(lvl, content, uniqueId));
            String replacement = "<h" + lvl + " id=\"" + uniqueId + "\">" + content + "</h" + lvl + ">";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        String htmlWithIds = sb.toString();

        // build TOC html
        StringBuilder toc = new StringBuilder();
        toc.append("<nav class=\"toc\"><h3>").append(escapeHtml(title)).append("</h3>\n<ul>\n");
        for (Heading h : heads) {
            if (h.level <= maxLevel) {
                toc.append("<li class=\"toc-lvl").append(h.level).append("\">")
                   .append("<a href=\"#").append(h.id).append("\">").append(escapeHtml(h.text)).append("</a>")
                   .append("</li>\n");
            }
        }
        toc.append("</ul></nav>\n");

        // replace first placeholder occurrence
        htmlWithIds = htmlWithIds.replaceFirst("<!-- TOC_PLACEHOLDER.*?-->", Matcher.quoteReplacement(toc.toString()));
        return htmlWithIds;
    }

    private static class Heading {
        int level;
        String text;
        String id;
        Heading(int l, String t, String i) { level = l; text = t; id = i; }
    }

    // ---------------- rendu HTML -> PDF ----------------

    public static void htmlToPdf(String html, String outFilePath, String optionalTtfFontPath) throws Exception {
        // Pré-traitements HTML
        html = insertTocIfRequested(html);
        html = normalizeHtmlRobust(html);
        html = tidyWithJsoup(html);

        Path outPath = Paths.get(outFilePath).toAbsolutePath();
        System.out.println("htmlToPdf START -> outFile=" + outPath + " optionalFont=" + optionalTtfFontPath);


        try {
        // Créer le dossier parent si nécessaire
        if (outPath.getParent() != null) {
            try {
                Files.createDirectories(outPath.getParent());
            } catch (Exception e) {
                System.err.println("Impossible de créer le répertoire parent: " + e.getMessage());
                throw e;
            }
        }

        // Préparer CSS d'embed depuis le fichier système si fourni
        String embedFontCssFromFile = null;
        if (optionalTtfFontPath != null && !optionalTtfFontPath.trim().isEmpty()) {
            try {
                File sysF = new File(optionalTtfFontPath);
                if (sysF.exists() && sysF.isFile() && sysF.canRead()) {
                    embedFontCssFromFile = makeFontFaceCssFromFile(sysF, "EmbeddedSysFont");
                    System.out.println("Embed CSS prepared from system font: " + sysF.getAbsolutePath());
                } else {
                    System.err.println("Police système introuvable ou illisible: " + optionalTtfFontPath);
                    logWarn("Police système introuvable ou illisible: " + optionalTtfFontPath);
                }
            } catch (Throwable t) {
                System.err.println("Warning preparing embed CSS from system font: " + t.getMessage());
                logWarn("Warning preparing embed CSS from system font: "+ t.getMessage());
                logError("Warning preparing embed CSS from system font: ", t);
            }
        }

        html = html.replaceFirst("(?i)<body(\\b[^>]*)>", "<body$1 class='pdf'>");

        
        // --- Tentative 1 : useFont avec optionalTtfFontPath (si fourni) ---
        if (optionalTtfFontPath != null && !optionalTtfFontPath.trim().isEmpty()) {
            File f = new File(optionalTtfFontPath);
            if (f.exists() && f.isFile() && f.canRead()) {
                try (FileOutputStream os = new FileOutputStream(outPath.toFile())) {
                    System.out.println("Attempt 1: useFont(system) -> " + f.getAbsolutePath());
                    PdfRendererBuilder builder = new PdfRendererBuilder();
                    builder.useFastMode();
                    builder.withHtmlContent(html, null);
                    // family name explicite
                    builder.useFont(f, "SystemProvidedFont");
                    builder.toStream(os);
                    builder.run();
                    System.out.println("PDF généré avec la police système: " + f.getAbsolutePath());
                    System.out.println("htmlToPdf END -> exists=" + outPath.toFile().exists() + " size=" + outPath.toFile().length());
                    return;
                } catch (Throwable t) {
                    System.err.println("Erreur export avec useFont(system) : " + t.getMessage());
                    logWarn("Erreur export avec useFont(system) : " + t.getMessage());
                    logError("Stacktrace pour useFont(system) failure", t);
                    t.printStackTrace();
                    // on continue vers fallback
                }
            }
        }

        // --- Fallback : embed base64 (depuis fichier système si disponible, sinon depuis resource, sinon registerBundledFonts) ---
        // Construire htmlWithEmbed
        String htmlWithEmbed = html;
        boolean embedUsed = false;

        if (embedFontCssFromFile != null) {
            htmlWithEmbed = html.replaceFirst("(?i)</head>", "<style>" + embedFontCssFromFile + "body{font-family:'EmbeddedSysFont', sans-serif;}</style></head>");
            embedUsed = true;
            System.out.println("Fallback: using embed CSS from system font");
        } else {
            // essayer ressource embarquée DejaVu
            try {
            	String cssFromRes = makeFontFaceCssFromResource("/fonts/DejaVuSans.ttf", "DejaVu Sans");
            	htmlWithEmbed = html.replaceFirst("(?i)</head>",
            	    "<style>" + cssFromRes + "body{font-family:'DejaVu Sans',sans-serif;}</style></head>");
            	embedUsed = true;
                System.out.println("Fallback: using embed CSS from embedded resource DejaVuSans.ttf");
            } catch (Throwable t) {
                System.err.println("Impossible d'utiliser embed depuis resource embarquée: " + t.getMessage());
                logWarn("Impossible d'utiliser embed depuis resource embarquée: " + t.getMessage());
                logError("Impossible d'utiliser embed depuis resource embarquée: ", t);
            }
        }

        // Si on a un embed (base64), on tente avec celui-ci
        if (embedUsed) {
            try (FileOutputStream os = new FileOutputStream(outPath.toFile())) {
                System.out.println("Attempt 2: generate PDF via embed base64");
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(htmlWithEmbed, null);
                builder.toStream(os);
                builder.run();
                System.out.println("PDF généré via embed base64 (fallback).");
                System.out.println("htmlToPdf END -> exists=" + outPath.toFile().exists() + " size=" + outPath.toFile().length());
                return;
            } catch (Throwable t) {
                System.err.println("Erreur export fallback embed: " + t.getMessage());
                logWarn("Erreur export fallback embed: " + t.getMessage());
                logError("Erreur export fallback embed: ", t);
                t.printStackTrace();
                // continue to final fallback
            }
        }

        // Dernier fallback : essayer registerBundledFonts (useFont sur ressources embarquées)
        try (FileOutputStream os = new FileOutputStream(outPath.toFile())) {
            System.out.println("Attempt 3: registerBundledFonts + useFont on bundled fonts");
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            try {
                registerBundledFonts(builder);
                builder.toStream(os);
                builder.run();
                System.out.println("PDF généré via registerBundledFonts.");
                System.out.println("htmlToPdf END -> exists=" + outPath.toFile().exists() + " size=" + outPath.toFile().length());
                return;
            } catch (Throwable t) {
                System.err.println("registerBundledFonts attempt failed: " + t.getMessage());
                logWarn("registerBundledFonts attempt failed: "  + t.getMessage());
                logError("registerBundledFonts attempt failed: ", t);
                t.printStackTrace();
                // will throw below
            }
        } catch (Throwable t) {
            System.err.println("Impossible d'ouvrir OutputStream pour dernier fallback: " + t.getMessage());
            logWarn("Impossible d'ouvrir OutputStream pour dernier fallback: "  + t.getMessage());
            logError("Impossible d'ouvrir OutputStream pour dernier fallback: ", t);
            t.printStackTrace();
        }

        // Si nous sommes arrivés ici, tout a échoué
        throw new Exception("Echec génération PDF : toutes les stratégies (useFont system, embed base64, registerBundledFonts) ont échoué. Voir les logs pour détails.");
    
        } catch (Throwable t) {
            logError("Erreur fatale htmlToPdf", t);
            throw t instanceof Exception ? (Exception) t : new Exception(t);
        } finally {
            logInfo("htmlToPdf FIN (closing log).");
            closeLog();
        }
        
    }

    
    private static String makeFontFaceCssFromResource(String resourcePath, String familyName) throws IOException {
        InputStream is = openResourceFlex(
            resourcePath,
            resourcePath.toLowerCase(),
            resourcePath.toUpperCase(),
            // variantes usuelles pour DejaVu
            "/fonts/DejaVuSans.ttf",
            "/fonts/dejavusans.ttf",
            "/fonts/DEJAVUSANS.TTF"
        );
        if (is == null) throw new IOException("Resource not found (any casing): " + resourcePath);
        byte[] all = is.readAllBytes();
        String b64 = Base64.getEncoder().encodeToString(all);
        String mime = "font/ttf";
        return "@font-face{font-family:'" + familyName + "';src:url('data:" + mime + ";base64," + b64 + "') format('truetype');font-weight:normal;font-style:normal;}\n";
    }


    
        /**
     * Copie une ressource du classpath vers un fichier temporaire (binaire),
     * vérifie l'en-tête et (optionnel) tente de parser la font via FontBox.
     *
     * resourcePath : ex "fonts/DejaVuSans.ttf" ou "/fonts/DejaVuSans.ttf"
     */
    private static File copyResourceToTempFile(String resourcePath, String prefix) throws IOException {
    	String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
    	InputStream is = openResourceFlex(
    	    normalized,
    	    normalized.toLowerCase(),
    	    normalized.toUpperCase()
    	);
    	if (is == null) {
    	    throw new IOException("Ressource police introuvable (any casing): " + normalized);
    	}
        File tmp = File.createTempFile(prefix, ".ttf");
        //tmp.deleteOnExit();
        logInfo("Created temp font file: " + tmp.getAbsolutePath() + " size=" + tmp.length());
        try (InputStream in = is; OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            out.flush();
        }
        logInfo("copyResourceToTempFile: wrote temp file " + tmp.getAbsolutePath() + " len=" + tmp.length());

        // LOG header
        try (RandomAccessFile raf = new RandomAccessFile(tmp, "r")) {
            byte[] head = new byte[4];
            raf.seek(0);
            int read = raf.read(head);
            if (read == 4) {
                System.out.printf("FONT HEAD HEX: %02X %02X %02X %02X (size=%d)%n",
                        head[0] & 0xFF, head[1] & 0xFF, head[2] & 0xFF, head[3] & 0xFF, tmp.length());
            }
        } catch (IOException e) {
            // just log
            System.err.println("Could not read font header: " + e.getMessage());
        }

        // Tentative de parse liée à FontBox : NE PAS supprimer le tmp si parse échoue.
        try {
            new org.apache.fontbox.ttf.TTFParser().parse(tmp);
            System.out.println("Validation FontBox OK");
        } catch (Throwable t) {
            System.err.println("FontBox parse failed (non-fatal) : " + t.getMessage());
            // On ne jette pas d'exception : on laisse caller décider (log pour debug)
        }

        return tmp;
    }


    /**
     * Enregistre des polices embarquées (normal / bold / italic) pour OpenHTMLToPDF.
     * Appelle cette méthode avant builder.run().
     */
    private static void registerBundledFonts(PdfRendererBuilder builder) throws IOException {
        // chemins dans src/main/resources
        File fNormal = copyResourceToTempFile("/fonts/DEJAVUSANS.ttf", "dejavu-normal");
        
        System.out.println(">>> LANCEMENT VALIDATION POLICE EMBARQUEE <<<");
        try {
            validateTtf(fNormal);
            hexDumpFirstBytes(fNormal, 128);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        
        File fBold   = copyResourceToTempFile("/fonts/DEJAVUSANS-BOLD.ttf", "dejavu-bold");
        File fItalic = copyResourceToTempFile("/fonts/DEJAVUSANS-OBLIQUE.ttf", "dejavu-italic");
        
        try {
			debugFontResource("/fonts/DEJAVUSANS.ttf");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
         System.out.println("DEBUG font file: " + fNormal.getAbsolutePath() + " size=" + fNormal.length());

        try {
            // tentative de parsing via FontBox (si la dépendance est présente)
            org.apache.fontbox.ttf.TTFParser parser = new org.apache.fontbox.ttf.TTFParser();
            parser.parse(fNormal);
            System.out.println("Police OK (TTF parsée)");
        } catch (Throwable t) {
            System.err.println("Police invalide ou corrompue : " + t.getMessage());
            // ne pas enregistrer la police si elle est corrompue
        }
       
        // Enregistrer sous le même family name ; le renderer gère les variantes.
        builder.useFont(fNormal, "DejaVu-Sans");
        builder.useFont(fBold,   "DejaVu-Sans");
        builder.useFont(fItalic, "DejaVu-Sans");
    }

    
    
    
    private static String normalizeHtmlRobust(String html) {
        if (html == null) return "<!doctype html><html><body></body></html>";
        // 1) supprimer BOM
        if (html.startsWith("\uFEFF")) html = html.substring(1);

        // 2) supprimer tous les caractères de contrôle (sauf \n,\r,\t) avant le premier '<'
        int firstLt = -1;
        for (int i = 0; i < html.length(); i++) {
            if (html.charAt(i) == '<') { firstLt = i; break; }
        }
        if (firstLt > 0) {
            // keep only from first '<'
            html = html.substring(firstLt);
        } else if (firstLt == -1) {
            // pas de balise : on enveloppe proprement le contenu
            html = "<!doctype html>\n<html><head><meta charset='utf-8'/></head><body>"
                   + escapeHtml(html) + "</body></html>";
            return html;
        }

        html = html.trim();

        // 3) si ne commence ni par <!doctype ni par <html, on wrappe
        String low = html.length() > 20 ? html.substring(0, 20).toLowerCase() : html.toLowerCase();
        if (!low.startsWith("<!doctype") && !low.startsWith("<html") && !low.startsWith("<!doctype")) {
            html = "<!doctype html>\n<html><head><meta charset='utf-8'/></head><body>\n"
                   + html + "\n</body></html>";
        }
        return html;
    }



    private static String tidyWithJsoup(String html) {
        if (html == null) return html;
        // parser le HTML et ressortir en XHTML bien formé
        Document doc = Jsoup.parse(html);
        OutputSettings settings = new OutputSettings();
        settings.prettyPrint(true);
        settings.syntax(OutputSettings.Syntax.xml); // produire XHTML
        settings.charset(java.nio.charset.StandardCharsets.UTF_8);
        doc.outputSettings(settings);
        // tu peux aussi nettoyer via Whitelist si tu veux restreindre les tags
        return doc.html();
    }

    
    
    // ---------------- exemple d'utilisation (CLI) ----------------

    /**
     * Usage:
     * java Import.PdfExporter input.bwr output.pdf [optional-font.ttf]
     */
    public static void main(String[] args) throws Exception {

           }
    
    public static void debugFontResource(String resourcePath) throws Exception {
        File tmp = copyResourceToTempFile(resourcePath, "debugfont");
        System.out.println("Temp font path: " + tmp.getAbsolutePath() + " size=" + tmp.length());
        byte[] data = Files.readAllBytes(tmp.toPath());
        byte[] sha = MessageDigest.getInstance("SHA-256").digest(data);
        System.out.println("SHA-256: " + HexFormat.of().formatHex(sha));
        // Ne pas supprimer tmp tout de suite pour pouvoir l'ouvrir manuellement si besoin
    }
    
 // Affiche les en-têtes de table d'une font TrueType/OTF et signale les tables hors fichier.
    private static void validateTtf(File f) throws IOException {
        System.out.println("validateTtf: " + f.getAbsolutePath() + " size=" + f.length());
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            // lire sfnt header (big-endian)
            byte[] sfnt = new byte[4];
            raf.seek(0);
            raf.readFully(sfnt);
            String sfntTag = new String(sfnt, java.nio.charset.StandardCharsets.ISO_8859_1);
            int numTables = raf.readUnsignedShort();
            int searchRange = raf.readUnsignedShort();
            int entrySelector = raf.readUnsignedShort();
            int rangeShift = raf.readUnsignedShort();
            System.out.printf("SFNT tag='%s' numTables=%d searchRange=%d entrySelector=%d rangeShift=%d%n",
                    sfntTag, numTables, searchRange, entrySelector, rangeShift);

            long fileLen = f.length();
            System.out.printf("%-6s %-12s %-12s %-12s %-10s%n", "idx", "tag", "checksum", "offset", "length");
            for (int i = 0; i < numTables; i++) {
                byte[] tagb = new byte[4];
                raf.readFully(tagb);
                String tag = new String(tagb, java.nio.charset.StandardCharsets.ISO_8859_1);
                long checksum = Integer.toUnsignedLong(raf.readInt());
                long offset = Integer.toUnsignedLong(raf.readInt());
                long length = Integer.toUnsignedLong(raf.readInt());
                String ok = (offset + length <= fileLen) ? "OK" : "OUT-OF-RANGE";
                System.out.printf("%-6d %-12s 0x%08X %-12d %-12d %s%n", i, tag, checksum, offset, length, ok);
                if (!ok.equals("OK")) {
                    System.err.printf("  -> table '%s' offset+length = %d > file size %d%n", tag, offset + length, fileLen);
                }
            }
        }
    }

    // Dump hex des premiers octets pour inspection rapide
    private static void hexDumpFirstBytes(File f, int n) throws IOException {
        byte[] buf = new byte[n];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            int read = raf.read(buf);
            if (read <= 0) {
                System.out.println("hexDump: fichier vide ou lecture impossible");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < read; i++) {
                if (i % 16 == 0) {
                    sb.append(String.format("%04X: ", i));
                }
                sb.append(String.format("%02X ", buf[i]));
                if ((i + 1) % 16 == 0) sb.append(System.lineSeparator());
            }
            System.out.println(sb.toString());
            // also print as ASCII for first bytes
            String ascii = new String(buf, 0, Math.min(read, n), java.nio.charset.StandardCharsets.ISO_8859_1);
            System.out.println("ASCII begin: " + ascii);
        }
    }
    
    private static String makeFontFaceCssFromFile(File ttfFile, String familyName) throws IOException {
        byte[] all = Files.readAllBytes(ttfFile.toPath());
        String b64 = Base64.getEncoder().encodeToString(all);
        String mime = "font/ttf";
        return "@font-face{font-family: '" + familyName + "'; src: url('data:" + mime + ";base64," + b64 + "') format('truetype'); font-weight: normal; font-style: normal;}\n";
    }

    private static void closeLog() {
        synchronized (LOG_LOCK) {
            if (LOG != null) {
                try {
                    LOG.println("=== PdfExporter log closed: " +
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now(ZoneId.systemDefault())));
                    LOG.flush();
                    LOG.close();
                } catch (Throwable ignored) {}
                LOG = null;
            }
        }
    }

    private static void logInfo(String s) {
        synchronized (LOG_LOCK) {
            if (LOG != null) LOG.println("[I] " + DateTimeFormatter.ISO_LOCAL_TIME.format(ZonedDateTime.now()) + " " + s);
            System.out.println(s);
        }
    }

    private static void logWarn(String s) {
        synchronized (LOG_LOCK) {
            if (LOG != null) LOG.println("[W] " + DateTimeFormatter.ISO_LOCAL_TIME.format(ZonedDateTime.now()) + " " + s);
            System.err.println(s);
        }
    }

    private static void logError(String s, Throwable t) {
        synchronized (LOG_LOCK) {
            if (LOG != null) {
                LOG.println("[E] " + DateTimeFormatter.ISO_LOCAL_TIME.format(ZonedDateTime.now()) + " " + s);
                t.printStackTrace(LOG);
            }
            System.err.println(s);
            t.printStackTrace();
        }
    }
    
 // Essaie plusieurs chemins/majuscules/minuscules sur le classpath
    private static InputStream openResourceFlex(String... candidates) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String p : candidates) {
            String abs = p.startsWith("/") ? p : "/" + p;
            InputStream is = PdfExporter.class.getResourceAsStream(abs);
            if (is != null) return is;
            // version sans "/" pour le context classloader
            String rel = abs.startsWith("/") ? abs.substring(1) : abs;
            is = (cl != null) ? cl.getResourceAsStream(rel) : null;
            if (is != null) return is;
        }
        return null;
    }


}
