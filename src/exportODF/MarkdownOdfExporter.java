package exportODF;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.odftoolkit.odfdom.doc.OdfTextDocument;
import org.odftoolkit.odfdom.dom.element.text.TextListElement;
import org.odftoolkit.odfdom.dom.element.text.TextListItemElement;
import org.odftoolkit.odfdom.dom.element.text.TextPElement;
import org.odftoolkit.odfdom.dom.style.OdfStyleFamily;
import org.odftoolkit.odfdom.incubator.doc.style.OdfStyle;
import org.odftoolkit.odfdom.pkg.OdfFileDom;
import org.odftoolkit.odfdom.dom.element.text.TextAElement;
import org.odftoolkit.odfdom.dom.element.text.TextTabElement;

import writer.ui.editor.TableSyntax;

import org.odftoolkit.odfdom.dom.element.table.TableTableElement;
import org.odftoolkit.odfdom.dom.element.table.TableTableColumnElement;
import org.odftoolkit.odfdom.dom.element.table.TableTableRowElement;
import org.odftoolkit.odfdom.dom.element.table.TableTableCellElement;
import org.odftoolkit.odfdom.dom.element.table.TableTableHeaderRowsElement;


import writer.commandes;

public final class MarkdownOdfExporter {

    private MarkdownOdfExporter() {}

    // --- Regex des blocs (début de paragraphe) ---
    private static final Pattern H_P  = Pattern.compile("^#P\\.(.*)$");
    private static final Pattern H_S  = Pattern.compile("^#S\\.(.*)$");
    private static final Pattern H1   = Pattern.compile("^#1\\.(.*)$");
    private static final Pattern H2   = Pattern.compile("^#2\\.(.*)$");
    private static final Pattern H3   = Pattern.compile("^#3\\.(.*)$");
    private static final Pattern H4   = Pattern.compile("^#4\\.(.*)$");
    private static final Pattern H5   = Pattern.compile("^#5\\.(.*)$");

    private static final Pattern OL   = Pattern.compile("^\\s*([0-9]+)\\.\\s+(.*)$"); // liste numérotée
    private static final Pattern UL   = Pattern.compile("^\\s*-\\.(.*)$");            // liste à puces

    private static final Pattern PAGE_BREAK = Pattern.compile("^\\s*@saut\\s+de\\s+page\\s+manuel\\b.*$");

    // --- Regex inline (on traite dans cet ordre pour éviter les conflits) ---
    // 1) gras+italic *^...^*
    private static final Pattern BOLD_ITALIC = Pattern.compile("\\*\\^(.+?)\\^\\*");
    // 2) gras **...**
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    // 3) souligné __...__
    private static final Pattern UNDERLINE = Pattern.compile("__(.+?)__");
    // 4) italic ^^...^^
    private static final Pattern ITALIC = Pattern.compile("\\^\\^(.+?)\\^\\^");
    // 5) note de bas de page @(...)
    private static final Pattern FOOTNOTE = Pattern.compile("@\\((.+?)\\)");
    // 6) souligné gras  _*...*_
    private static final Pattern UNDERLINE_BOLD = Pattern.compile("_\\*(.+?)\\*_");
    // 7) souligné gras  _*...*_
    private static final Pattern UNDERLINE_ITALIC = Pattern.compile("_\\^(.+?)\\^_");
    // 8) souligné gras  _*...*_
    private static final Pattern EXPOSANT = Pattern.compile("\\^¨(.+?)¨\\^");
    // 9) Pattern : _¨...¨_
    private static final Pattern INDICE = Pattern.compile("_¨(.+?)¨_");
    // 10) lien hypertexte @[Texte: URL]
    private static final Pattern LINK =
    	    Pattern.compile("@\\[(.+?):\\s*([a-zA-Z][a-zA-Z0-9+\\-.]*:[^\\]]+?)\\]");

    // --- Styles de spans (noms auto) ---
    private static final String SPAN_BOLD       = "BW_Span_Bold";
    private static final String SPAN_ITALIC     = "BW_Span_Italic";
    private static final String SPAN_BOLDITALIC = "BW_Span_BoldItalic";
    private static final String SPAN_UNDER      = "BW_Span_Underline";
    private static final String SPAN_UNDERBOLD      = "BW_Span_UnderlineBold";
    private static final String SPAN_UNDERITALIC     = "BW_Span_UnderlineItalic";
    private static final String SPAN_EXPOSANT     = "BW_Span_Exposant";
    private static final String SPAN_INDICE = "BW_Span_Indice";
    
    
    

    private enum ListKind { NONE, ORDERED, UNORDERED }
    

    /** API principale : exporte la chaîne markdown-ish vers un .odt. */
    public static void export(String src, File outFile) throws Exception {
        OdfTextDocument odt = OdfTextDocument.newTextDocument();

        // Prépare quelques styles utiles
        prepareSpanTextStyles(odt);

        OdfFileDom contentDom = odt.getContentDom();

        boolean pageBreakForNextParagraph = false;

        // État de liste en cours
        ListKind listState = ListKind.NONE;
        TextListElement currentList = null;
        
     // ⬇️ Compteur MUTABLE pour les notes de bas de page
        IntBox footBox = new IntBox(1);

        // Compteur pour notes
        int footnoteCounter = 1;

        String[] lines = src.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // 0) Ligne vide : on ferme une liste éventuelle et on ajoute un paragraphe vide
            if (line.trim().isEmpty()) {
                // flush liste si besoin
                if (listState != ListKind.NONE) {
                    currentList = null;
                    listState = ListKind.NONE;
                } else {
                    TextPElement p = odt.newParagraph();
                    if (pageBreakForNextParagraph) {
                        applyBreakBefore(odt, p);
                        pageBreakForNextParagraph = false;
                    }
                    // paragraphe vide
                }
                continue;
            }

            // 1) Saut de page manuel : s’applique au paragraphe SUIVANT
            if (PAGE_BREAK.matcher(line).matches()) {
                // flush de liste si on était dedans
                if (listState != ListKind.NONE) {
                    currentList = null;
                    listState = ListKind.NONE;
                }
                pageBreakForNextParagraph = true;
                continue;
            }
            
            // === TABLEAUX @t ... @/t ===
            if (TableSyntax.isTableStart(line)) {
                // 1) Fermer une liste éventuelle
                if (listState != ListKind.NONE) {
                    currentList = null;
                    listState = ListKind.NONE;
                }

                // 2) Récupérer toutes les lignes jusqu'à @/t (non inclus)
                List<String> rows = new ArrayList<>();
                int j = i + 1;
                for (; j < lines.length; j++) {
                    String l = lines[j];
                    if (TableSyntax.isTableEnd(l)) break;
                    // on ignore les lignes vides au milieu du bloc table
                    if (l != null && !l.strip().isEmpty()) rows.add(l);
                }

                // 3) Insérer un saut de page si demandé pour "le prochain paragraphe"
                if (pageBreakForNextParagraph) {
                    TextPElement dummy = odt.newParagraph();
                    applyBreakBefore(odt, dummy);
                    pageBreakForNextParagraph = false;
                }

                // 4) Construire la table dans l’ODT
                buildOdfTable(odt, contentDom, rows, footBox);

                // 5) Sauter jusqu'à la fin du bloc
                i = (j < lines.length ? j : lines.length - 1);
                continue;
            }

            
            // --- Titres spéciaux #P. et #S. ---
            Matcher m;

            // #P. Titre principal  -> style "Title"
            m = H_P.matcher(line);
            if (m.matches()) {
                String txt = m.group(1).trim();

                // fermer une éventuelle liste en cours
                if (listState != ListKind.NONE) {
                    currentList = null;
                    listState = ListKind.NONE;
                }

                if (!txt.isEmpty()) {
                    TextPElement p = odt.newParagraph();
                    p.setTextStyleNameAttribute("Title"); // style standard LibreOffice
                    if (pageBreakForNextParagraph) {
                        applyBreakBefore(odt, p);
                        pageBreakForNextParagraph = false;
                    }
                    appendInlineRuns(contentDom, p, txt, odt, footBox);
                }
                continue; // on a traité la ligne
            }

            // #S. Sous-titre -> style "Subtitle"
            m = H_S.matcher(line);
            if (m.matches()) {
                String txt = m.group(1).trim();

                if (listState != ListKind.NONE) {
                    currentList = null;
                    listState = ListKind.NONE;
                }

                if (!txt.isEmpty()) {
                    TextPElement p = odt.newParagraph();
                    p.setTextStyleNameAttribute("Subtitle"); // style standard LibreOffice
                    if (pageBreakForNextParagraph) {
                        applyBreakBefore(odt, p);
                        pageBreakForNextParagraph = false;
                    }
                    appendInlineRuns(contentDom, p, txt, odt, footBox);
                }
                continue;
            }


            // 2) Détection des titres
            int headingLevel = 0;
            String txt = null;

            if ((m = H1.matcher(line)).matches()) { headingLevel = 1; txt = m.group(1).trim(); }
            else if ((m = H2.matcher(line)).matches()) { headingLevel = 2; txt = m.group(1).trim(); }
            else if ((m = H3.matcher(line)).matches()) { headingLevel = 3; txt = m.group(1).trim(); }
            else if ((m = H4.matcher(line)).matches()) { headingLevel = 4; txt = m.group(1).trim(); }
            else if ((m = H5.matcher(line)).matches()) { headingLevel = 5; txt = m.group(1).trim(); }

            if (headingLevel > 0) {
                // ⬇️ au lieu de flushListIfAny(..., () -> { ... }, ...)
                if (listState != ListKind.NONE) {
                    currentList = null;
                    listState = ListKind.NONE;
                }
                if (txt != null && !txt.isEmpty()) {
                    TextPElement p = odt.newParagraph();
                    String style = switch (headingLevel) {
                        case 1 -> "Heading 1";
                        case 2 -> "Heading 2";
                        case 3 -> "Heading 3";
                        case 4 -> "Heading 4";
                        case 5 -> "Heading 5";
                        default -> "Heading 1";
                    };
                    p.setTextStyleNameAttribute(style);
                    if (pageBreakForNextParagraph) {
                        applyBreakBefore(odt, p);
                        pageBreakForNextParagraph = false;
                    }
                    appendInlineRuns(contentDom, p, txt, odt, footBox); // voir NOTE ci-dessous
                }
                continue;
            }



            // 3) Listes numérotées
	        m = OL.matcher(line);
	        if (m.matches()) {
	            String itemTxt = m.group(2).trim();
	
	            if (listState != ListKind.ORDERED) {
	                if (listState != ListKind.NONE) {
	                    currentList = null;
	                    listState = ListKind.NONE;
	                }
	                // ✅ Crée un style de liste numérotée s’il n’existe pas encore
	                String orderedStyleName = ensureListStyle(odt, true);
	
	                currentList = odt.getContentRoot().newTextListElement();
	                currentList.setTextStyleNameAttribute(orderedStyleName); // ✅ associe le style
	                listState = ListKind.ORDERED;
	            }
	
	            TextListItemElement item = currentList.newTextListItemElement();
	            TextPElement p = item.newTextPElement();
	
	            if (pageBreakForNextParagraph) {
	                applyBreakBefore(odt, p);
	                pageBreakForNextParagraph = false;
	            }
	            if (!itemTxt.isEmpty()) {
	                appendInlineRuns(contentDom, p, itemTxt, odt, footnoteCounterRef(footnoteCounter));
	                footnoteCounter = footnoteCounterRef(footnoteCounter).get();
	            }
	            continue;
	        }



         // 4) Listes à puces
            m = UL.matcher(line);
            if (m.matches()) {
                String itemTxt = m.group(1).trim();

                if (listState != ListKind.UNORDERED) {
                	if (listState != ListKind.NONE) { currentList = null; listState = ListKind.NONE; }
                    // ⬇️ idem : crée via le parent
                    currentList = odt.getContentRoot().newTextListElement();
                    listState = ListKind.UNORDERED;
                }

                TextListItemElement item = currentList.newTextListItemElement();
                TextPElement p = item.newTextPElement();

                if (pageBreakForNextParagraph) {
                    applyBreakBefore(odt, p);
                    pageBreakForNextParagraph = false;
                }
                if (!itemTxt.isEmpty()) {
                    appendInlineRuns(contentDom, p, itemTxt, odt, footnoteCounterRef(footnoteCounter));
                    footnoteCounter = footnoteCounterRef(footnoteCounter).get();
                }
                continue;
            }

            // 5) Paragraphe normal ("Text body")
            if (listState != ListKind.NONE) {      // <-- flush sans lambda
                currentList = null;
                listState = ListKind.NONE;
            }

            TextPElement p = odt.newParagraph();
            if (pageBreakForNextParagraph) {
                applyBreakBefore(odt, p);
                pageBreakForNextParagraph = false;
            }
            p.setTextStyleNameAttribute("Text_20_body"); // alias "Text body"

            // ⚠️ utilise toujours footBox (le compteur mutable déclaré en début de méthode)
            appendInlineRuns(contentDom, p, line.trim(), odt, footBox);

        
        }
        // Add les méta-données
        applyMetadata(odt);
        
        // Fin : si on est encore en liste, rien à faire (déjà dans le DOM)
        odt.save(outFile);
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    /** Crée 4 styles de span (gras, italic, gras+italic, souligné). */
    private static void prepareSpanTextStyles(OdfTextDocument odt) throws Exception {
        OdfStyle sBold = ensureSpanStyle(odt, SPAN_BOLD);
        sBold.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontWeight, "bold");

        OdfStyle sIt = ensureSpanStyle(odt, SPAN_ITALIC);
        sIt.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontStyle, "italic");

        OdfStyle sBI = ensureSpanStyle(odt, SPAN_BOLDITALIC);
        sBI.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontWeight, "bold");
        sBI.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontStyle, "italic");

        OdfStyle sUnder = ensureSpanStyle(odt, SPAN_UNDER);
        sUnder.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineStyle, "solid");
        sUnder.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineType, "single");
        sUnder.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineColor, "font-color");
   
        OdfStyle sUnderBold = ensureSpanStyle(odt, SPAN_UNDERBOLD);
        sUnderBold.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontWeight, "bold");
        sUnderBold.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineStyle, "solid");
        sUnderBold.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineType, "single");
        sUnderBold.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineColor, "font-color");   
    
        OdfStyle sUnderItalic = ensureSpanStyle(odt, SPAN_UNDERITALIC);
        sUnderItalic.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontStyle, "italic");
        sUnderItalic.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineStyle, "solid");
        sUnderItalic.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineType, "single");
        sUnderItalic.setProperty(org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineColor, "font-color");    
    
        OdfStyle sExpo = ensureSpanStyle(odt, SPAN_EXPOSANT);
        sExpo.setProperty(
        		org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextPosition,
        		"super 58%"
        		);
        
        OdfStyle sIndice = ensureSpanStyle(odt, SPAN_INDICE);
        sIndice.setProperty(
	         org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextPosition,
	         "sub 58%"
	         );
    }

    /** Crée ou récupère un style de liste (numérotée ou à puces). */
    private static String ensureListStyle(OdfTextDocument odt, boolean ordered) throws Exception {
        String styleName = ordered ? "BW_ListOrdered" : "BW_ListUnordered";

        var cdom = odt.getContentDom(); // on garde le dom sous la main
        var autoStyles = cdom.getAutomaticStyles();
        var existing = autoStyles.getListStyle(styleName);
        if (existing != null) return styleName;

        // Style de liste
        org.odftoolkit.odfdom.incubator.doc.text.OdfTextListStyle listStyle =
            autoStyles.newListStyle(styleName);

        if (ordered) {
            // === Liste numérotée niveau 1 ===
            org.odftoolkit.odfdom.dom.element.text.TextListLevelStyleNumberElement num =
                listStyle.newTextListLevelStyleNumberElement("1", 1);
            num.setStyleNumFormatAttribute("1");
            num.setStyleNumSuffixAttribute(".");

            // Propriétés du niveau
            org.odftoolkit.odfdom.dom.element.style.StyleListLevelPropertiesElement props =
                num.newStyleListLevelPropertiesElement();

            // Mode requis pour activer la tabulation
            props.setAttributeNS(
                "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
                "text:list-level-position-and-space-mode",
                "label-alignment"
            );

            // ✅ Crée explicitement <style:list-level-label-alignment> via le DOM
            org.odftoolkit.odfdom.dom.element.style.StyleListLevelLabelAlignmentElement align =
                cdom.newOdfElement(
                    org.odftoolkit.odfdom.dom.element.style.StyleListLevelLabelAlignmentElement.class
                );
            props.appendChild(align);

            // Attributs sur le nœud d’alignement
            align.setAttributeNS(
                "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
                "text:label-followed-by", "listtab"
            );
            align.setAttributeNS(
                "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
                "text:list-tab-stop-position", "1cm"
            );
            align.setAttributeNS(
                "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0",
                "fo:text-indent", "-0.5cm"
            );
            align.setAttributeNS(
                "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0",
                "fo:margin-left", "1cm"
            );

        } else {
            // === Liste à puces niveau 1 ===
            org.odftoolkit.odfdom.dom.element.text.TextListLevelStyleBulletElement bullet =
                listStyle.newTextListLevelStyleBulletElement("•", 1);
            bullet.setStyleNumSuffixAttribute(" ");

            org.odftoolkit.odfdom.dom.element.style.StyleListLevelPropertiesElement props =
                bullet.newStyleListLevelPropertiesElement();

            props.setAttributeNS(
                "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
                "text:list-level-position-and-space-mode",
                "label-alignment"
            );

            org.odftoolkit.odfdom.dom.element.style.StyleListLevelLabelAlignmentElement align =
                cdom.newOdfElement(
                    org.odftoolkit.odfdom.dom.element.style.StyleListLevelLabelAlignmentElement.class
                );
            props.appendChild(align);

            align.setAttributeNS(
                "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
                "text:label-followed-by", "listtab"
            );
            align.setAttributeNS(
                "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
                "text:list-tab-stop-position", "1cm"
            );
            align.setAttributeNS(
                "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0",
                "fo:text-indent", "-0.5cm"
            );
            align.setAttributeNS(
                "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0",
                "fo:margin-left", "1cm"
            );
        }

        return styleName;
    }



    private static void applyBreakBefore(OdfTextDocument odt, TextPElement p) throws Exception {
        // 1) Style de base déjà présent (Title / Heading / Text_20_body, etc.)
        String base = p.getTextStyleNameAttribute();
        if (base == null || base.isBlank()) base = "Text_20_body";

        // 2) Nom du style dérivé “base + saut de page avant”
        String derived = "BW_BreakBefore__" + base;

        // 3) IMPORTANT : utiliser OdfContentDom (pas OdfFileDom)
        org.odftoolkit.odfdom.dom.OdfContentDom cdom = odt.getContentDom();
        var auto = cdom.getAutomaticStyles();

        org.odftoolkit.odfdom.incubator.doc.style.OdfStyle s =
                auto.getStyle(derived, OdfStyleFamily.Paragraph);
        if (s == null) {
            s = auto.newStyle(OdfStyleFamily.Paragraph);
            s.setStyleNameAttribute(derived);
            s.setStyleParentStyleNameAttribute(base); // hérite de la mise en forme du style de base
            s.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.BreakBefore,
                "page"
            );
        }

        // 4) Appliquer le style dérivé au paragraphe
        p.setTextStyleNameAttribute(derived);
    }


    private static OdfStyle ensureSpanStyle(OdfTextDocument odt, String name) throws Exception {
        OdfStyle s = odt.getContentDom().getAutomaticStyles().getStyle(name, OdfStyleFamily.Text);
        if (s == null) {
            s = odt.getContentDom().getAutomaticStyles().newStyle(OdfStyleFamily.Text);
            s.setStyleNameAttribute(name);
        }
        return s;
    }

    // Petit conteneur mutable pour le compteur de notes (évite champs statiques)
    private static class IntBox {
        private int v;
        IntBox(int v) { this.v = v; }
        int get() { return v; }
        void inc() { v++; }
    }
    private static IntBox footnoteCounterRef(int start) { return new IntBox(start); }

    /** Parse le texte et insère dans le paragraphe les spans/notes. */
   private static void appendInlineRuns(
        org.odftoolkit.odfdom.pkg.OdfFileDom dom,
        org.odftoolkit.odfdom.dom.element.text.TextPElement paragraph,
        String text,
        org.odftoolkit.odfdom.doc.OdfTextDocument odt,
        IntBox footnoteCounter) throws Exception {

    // Utilise l’interface W3C pour fabriquer des nœuds texte
    org.w3c.dom.Document w3c = (org.w3c.dom.Document) dom;

    java.util.List<InlineToken> tokens = tokenizeInline(text);

    for (InlineToken tk : tokens) {
        switch (tk.kind) {

            case TEXT: {
            	appendTextWithTabsToParagraph(dom, paragraph, tk.content);
                break;
            }

            case BOLDITALIC: {
                var span = paragraph.newTextSpanElement();
                span.setTextStyleNameAttribute(SPAN_BOLDITALIC);
                appendTextWithTabsToSpan(dom, span, tk.content);
                break;
            }

            case BOLD: {
                var span = paragraph.newTextSpanElement();
                span.setTextStyleNameAttribute(SPAN_BOLD);
                appendTextWithTabsToSpan(dom, span, tk.content);
                break;
            }

            case UNDERLINE: {
                var span = paragraph.newTextSpanElement();
                span.setTextStyleNameAttribute(SPAN_UNDER);
                appendTextWithTabsToSpan(dom, span, tk.content);
                break;
            }
            
            case UNDERBOLD: {
                var span = paragraph.newTextSpanElement();
                span.setTextStyleNameAttribute(SPAN_UNDERBOLD);
                appendTextWithTabsToSpan(dom, span, tk.content);
                break;
            }
            
            case UNDERITALIC: {
                var span = paragraph.newTextSpanElement();
                span.setTextStyleNameAttribute(SPAN_UNDERITALIC);
                appendTextWithTabsToSpan(dom, span, tk.content);
                break;
            }

            case ITALIC: {
                var span = paragraph.newTextSpanElement();
                span.setTextStyleNameAttribute(SPAN_ITALIC);
                appendTextWithTabsToSpan(dom, span, tk.content);
                break;
            }
            
            case EXPOSANT: {
                var span = paragraph.newTextSpanElement();
                span.setTextStyleNameAttribute(SPAN_EXPOSANT);
                appendTextWithTabsToSpan(dom, span, tk.content);
                break;
            }
            
            case INDICE: {
                var span = paragraph.newTextSpanElement();
                span.setTextStyleNameAttribute(SPAN_INDICE);
                appendTextWithTabsToSpan(dom, span, tk.content);
                break;
            }

            case FOOTNOTE: {
                // Crée la note via le parent (et donc déjà attachée)
                var note = paragraph.newTextNoteElement("footnote");
                var cit  = note.newTextNoteCitationElement();
                cit.appendChild(w3c.createTextNode(String.valueOf(footnoteCounter.get())));

                var body = note.newTextNoteBodyElement();
                var bp   = body.newTextPElement();
                bp.appendChild(w3c.createTextNode(tk.content));

                footnoteCounter.inc();
                break;
            }
            
            case LINK: {
                String[] parts = tk.content.split("\\|\\|", 2);
                String label = parts[0];
                String url = (parts.length > 1 ? parts[1] : "").trim();

                // Crée <text:a xlink:href="url">
                TextAElement link = paragraph.newTextAElement(url, null);

                // Ajoute le texte (avec gestion de [tab]) *dans* le lien
                appendTextWithTabsToLink(dom, link, label);
                break;
            }

        }
    }
}

    // Types de token inline
    private enum K { TEXT, BOLD, ITALIC, UNDERLINE, BOLDITALIC, UNDERBOLD, UNDERITALIC, EXPOSANT, INDICE, FOOTNOTE, LINK }
    private static final class InlineToken {
        final K kind;
        final String content;
        InlineToken(K k, String c) { kind = k; content = c; }
    }

    
    // Représente la prochaine occurrence parmi nos 5 regex, la plus à gauche
    private static final class Match {
        final int kind; // 0=BI,1=B,2=U,3=I,4=FN
        final int start, end;
        final String inner;
        Match(int k, int s, int e, String in) { kind = k; start = s; end = e; inner = in; }
    }
    
    /** Tokenizer très simple : traite d’abord *^ ^* puis **, __, ^^ puis les @(...) */
    private static List<InlineToken> tokenizeInline(String src) {
        // On va itérer en remplaçant au fur et à mesure ; pour conserver l’ordre,
        // on segmente par recherche la plus à gauche.
        List<InlineToken> out = new ArrayList<>();
        int idx = 0;
        while (idx < src.length()) {
            Match best = findNextMatch(src, idx);
            if (best == null) {
                out.add(new InlineToken(K.TEXT, src.substring(idx)));
                break;
            }
            // texte brut avant
            if (best.start > idx) {
                out.add(new InlineToken(K.TEXT, src.substring(idx, best.start)));
            }
            @SuppressWarnings("unused")
			String inner = best.inner;
            switch (best.kind) {
	            case 0: out.add(new InlineToken(K.BOLDITALIC, best.inner)); break; // *^ ^*
	            case 1: out.add(new InlineToken(K.UNDERBOLD,  best.inner)); break; // _* *_
	            case 2: out.add(new InlineToken(K.UNDERITALIC,  best.inner)); break; // _^ ^_
	            case 3: out.add(new InlineToken(K.BOLD,       best.inner)); break; // ** **
	            case 4: out.add(new InlineToken(K.UNDERLINE,  best.inner)); break; // __ __
	            case 5: out.add(new InlineToken(K.ITALIC,     best.inner)); break; // ^^ ^^
	            case 6: out.add(new InlineToken(K.FOOTNOTE,   best.inner)); break; // @(...)
	            case 7: out.add(new InlineToken(K.EXPOSANT,   best.inner)); break; // ^¨ ¨^ 
	            case 8: out.add(new InlineToken(K.INDICE,     best.inner)); break; // _¨ ¨_
	            case 9: {
	                Matcher linkMatcher = LINK.matcher(src.substring(best.start, best.end));
	                if (linkMatcher.matches()) {
	                    out.add(new InlineToken(K.LINK,
	                            linkMatcher.group(1).trim() + "||" + linkMatcher.group(2).trim()));
	                }
	                break;
	            }
            }
            idx = best.end;
        }
        return out;
    }

   

    private static Match findNextMatch(String s, int from) {
        Matcher[] ms = new Matcher[] {
            BOLD_ITALIC.matcher(s),   // 0
            UNDERLINE_BOLD.matcher(s),// 1
            UNDERLINE_ITALIC.matcher(s),// 2
            BOLD.matcher(s),          // 3
            UNDERLINE.matcher(s),     // 4
            ITALIC.matcher(s),        // 5
            FOOTNOTE.matcher(s),      // 6
            EXPOSANT.matcher(s),        // 7
            INDICE.matcher(s),          // 8
            LINK.matcher(s)           // 9
        };
        Match best = null;
        for (int k = 0; k < ms.length; k++) {
            Matcher m = ms[k];
            if (m.find(from)) {
                Match cur = new Match(k, m.start(), m.end(), m.group(1));
                if (best == null || cur.start < best.start) best = cur;
            }
        }
        return best;
    }

    // --- Démo rapide (à enlever si tu intègres dans ton app) ---
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
            "Du texte normal avec **gras**, ^^italique^^, __souligné__, _*souligné gras*_ et _^souligné italique^_.",
            "Du texte \"Chimie : H_¨2¨_O et Mathématique x^¨2¨^ = y_¨3¨_ + 1.\""
        );
        export(demo, new File("export_demo.odt"));
        System.out.println("ODT écrit : export_demo.odt");
    }
    
	private static void applyMetadata(org.odftoolkit.odfdom.doc.OdfTextDocument odt) throws Exception {
        // DOM de meta.xml
        org.odftoolkit.odfdom.pkg.OdfFileDom metaDom = odt.getMetaDom();

        // Utilise l’interface W3C pour naviguer
        org.w3c.dom.Document w3c = (org.w3c.dom.Document) metaDom;
        org.w3c.dom.Element root = w3c.getDocumentElement(); // <office:document-meta>

        // Trouver ou créer <office:meta>
        final String OFFICE_NS = org.odftoolkit.odfdom.dom.element.office.OfficeMetaElement.ELEMENT_NAME.getUri();
        final String OFFICE_META_LOCAL = org.odftoolkit.odfdom.dom.element.office.OfficeMetaElement.ELEMENT_NAME.getLocalName();

        org.w3c.dom.Element officeMetaEl = findChildByName(root, OFFICE_NS, OFFICE_META_LOCAL);
        if (officeMetaEl == null) {
            var officeMetaOdf =
                metaDom.newOdfElement(org.odftoolkit.odfdom.dom.element.office.OfficeMetaElement.class);
            officeMetaEl = (org.w3c.dom.Element) officeMetaOdf;   // ✅ cast explicite
            root.appendChild(officeMetaEl);
        }


        // Nettoyer les précédents champs pour éviter les doublons
        removeChildrenByName(officeMetaEl, org.odftoolkit.odfdom.dom.element.dc.DcTitleElement.ELEMENT_NAME);
        removeChildrenByName(officeMetaEl, org.odftoolkit.odfdom.dom.element.dc.DcSubjectElement.ELEMENT_NAME);
        removeChildrenByName(officeMetaEl, org.odftoolkit.odfdom.dom.element.dc.DcCreatorElement.ELEMENT_NAME);
        removeChildrenByName(officeMetaEl, org.odftoolkit.odfdom.dom.element.meta.MetaInitialCreatorElement.ELEMENT_NAME);
        removeChildrenByName(officeMetaEl, org.odftoolkit.odfdom.dom.element.meta.MetaKeywordElement.ELEMENT_NAME);
        removeChildrenByName(officeMetaEl, org.odftoolkit.odfdom.dom.element.dc.DcDescriptionElement.ELEMENT_NAME);
        removeChildrenByName(officeMetaEl, org.odftoolkit.odfdom.dom.element.dc.DcLanguageElement.ELEMENT_NAME);
        removeChildrenByName(officeMetaEl, org.odftoolkit.odfdom.dom.element.meta.MetaGeneratorElement.ELEMENT_NAME);
        removeChildrenByName(officeMetaEl, org.odftoolkit.odfdom.dom.element.meta.MetaCreationDateElement.ELEMENT_NAME);
        removeChildrenByName(officeMetaEl, org.odftoolkit.odfdom.dom.element.meta.MetaUserDefinedElement.ELEMENT_NAME);

        // Écrire les champs (création via ODFDOM, texte via W3C)
        putTextElem(metaDom, officeMetaEl, org.odftoolkit.odfdom.dom.element.dc.DcTitleElement.class,
                commandes.meta.retourneFirstEnfant("titre").getAttributs("LeTitre"));

        putTextElem(metaDom, officeMetaEl, org.odftoolkit.odfdom.dom.element.dc.DcSubjectElement.class,
        		commandes.meta.retourneFirstEnfant("sujet").getAttributs("LeSujet"));

        putTextElem(metaDom, officeMetaEl, org.odftoolkit.odfdom.dom.element.dc.DcCreatorElement.class,
        		commandes.meta.retourneFirstEnfant("auteur").getAttributs("nom"));

        if (commandes.meta.retourneFirstEnfant("motsCles") != null) {
            for (String kw : commandes.meta.retourneFirstEnfant("motsCles").getAttributs("mots").split(",")) {
                String k = kw.trim();
                if (!k.isEmpty()) {
                    putTextElem(metaDom, officeMetaEl,
                            org.odftoolkit.odfdom.dom.element.meta.MetaKeywordElement.class, k);
                }
            }
        }

        putTextElem(metaDom, officeMetaEl, org.odftoolkit.odfdom.dom.element.dc.DcDescriptionElement.class,
        		commandes.meta.retourneFirstEnfant("description").getAttributs("resume"));

        putTextElem(metaDom, officeMetaEl, org.odftoolkit.odfdom.dom.element.dc.DcLanguageElement.class,
        		commandes.meta.retourneFirstEnfant("langue").getAttributs("lang"));

        putTextElem(metaDom, officeMetaEl, org.odftoolkit.odfdom.dom.element.meta.MetaGeneratorElement.class,
                "blindWriter/MarkdownOdfExporter");

        String creationIso = (commandes.meta.retourneFirstEnfant("date_creation").getAttributs("date") != null 
        		&& !commandes.meta.retourneFirstEnfant("date_creation").getAttributs("date").isBlank())
                ? commandes.meta.retourneFirstEnfant("date_creation").getAttributs("date").trim()
                : java.time.OffsetDateTime.now().toString();
        putTextElem(metaDom, officeMetaEl, org.odftoolkit.odfdom.dom.element.meta.MetaCreationDateElement.class,
                creationIso);
        

        // -- Société (méta user-defined) --
        var societyNode = commandes.meta.retourneFirstEnfant("society");
        String soc = (societyNode != null) ? societyNode.getAttributs("nom") : null;
        if (soc != null && !soc.isBlank()) {
            // Crée l’élément via ODFDOM           
            putUserDefined(metaDom, officeMetaEl, "Société", soc, w3c);
        }



        odt.updateMetaData();
    }

   
 // Trouve le premier enfant par (namespace, localName) sous parentEl
    private static org.w3c.dom.Element findChildByName(org.w3c.dom.Element parentEl,
                                                       String ns, String local) {
        for (org.w3c.dom.Node n = parentEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                org.w3c.dom.Element e = (org.w3c.dom.Element) n;
                if (local.equals(e.getLocalName()) && ns.equals(e.getNamespaceURI())) {
                    return e;
                }
            }
        }
        return null;
    }

    // Supprime tous les enfants d’un certain nom (namespace + localName)
    private static void removeChildrenByName(org.w3c.dom.Element parentEl,
                                             org.odftoolkit.odfdom.pkg.OdfName name) {
        java.util.List<org.w3c.dom.Node> toRemove = new java.util.ArrayList<>();
        for (org.w3c.dom.Node n = parentEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                org.w3c.dom.Element e = (org.w3c.dom.Element) n;
                if (name.getLocalName().equals(e.getLocalName())
                        && name.getUri().equals(e.getNamespaceURI())) {
                    toRemove.add(n);
                }
            }
        }
        for (org.w3c.dom.Node n : toRemove) parentEl.removeChild(n);
    }

    // Crée un élément de type ODFDOM, l’attache à parentEl, et y met un texte
    private static <T extends org.odftoolkit.odfdom.pkg.OdfElement> void putTextElem(
            org.odftoolkit.odfdom.pkg.OdfFileDom dom,
            org.w3c.dom.Element parentEl,
            Class<T> clazz,
            String text) throws Exception {

        if (text == null || text.isBlank()) return;

        // Crée l’élément via la fabrique ODFDOM
        T el = dom.newOdfElement(clazz);

        // Attache-le via W3C (OdfElement implémente Element)
        parentEl.appendChild((org.w3c.dom.Element) el);

        // Ajoute le texte
        ((org.w3c.dom.Element) el).appendChild(((org.w3c.dom.Document) dom).createTextNode(text));
    }


    private static void putUserDefined(org.odftoolkit.odfdom.pkg.OdfFileDom metaDom,
            org.w3c.dom.Element officeMetaEl,
            String metaName, String value,
            org.w3c.dom.Document w3c) throws Exception {
		if (value == null || value.isBlank()) return;
		var ud = metaDom.newOdfElement(org.odftoolkit.odfdom.dom.element.meta.MetaUserDefinedElement.class);
		ud.setMetaNameAttribute(metaName);
		ud.setMetaValueTypeAttribute("string");
		officeMetaEl.appendChild((org.w3c.dom.Node) ud);
		((org.w3c.dom.Node) ud).appendChild(w3c.createTextNode(value.trim()));
	}

    
 // Insère du texte qui peut contenir [tab] dans un PARAGRAPHE <text:p>
    private static void appendTextWithTabsToParagraph(
            org.odftoolkit.odfdom.pkg.OdfFileDom dom,
            org.odftoolkit.odfdom.dom.element.text.TextPElement p,
            String text) {

        if (text == null || text.isEmpty()) return;
        org.w3c.dom.Document w3c = (org.w3c.dom.Document) dom;

        String[] parts = text.split("\\[tab\\]", -1);
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                p.appendChild(w3c.createTextNode(parts[i]));
            }
            if (i < parts.length - 1) {
                p.newTextTabElement(); // <text:tab/>
            }
        }
    }

    // Insère du texte qui peut contenir [tab] dans un SPAN <text:span>
    private static void appendTextWithTabsToSpan(
            org.odftoolkit.odfdom.pkg.OdfFileDom dom,
            org.odftoolkit.odfdom.dom.element.text.TextSpanElement span,
            String text) {

        if (text == null || text.isEmpty()) return;
        org.w3c.dom.Document w3c = (org.w3c.dom.Document) dom;

        String[] parts = text.split("\\[tab\\]", -1);
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                span.appendChild(w3c.createTextNode(parts[i]));
            }
            if (i < parts.length - 1) {
                span.newTextTabElement(); // <text:tab/>
            }
        }
    }
    
    private static void appendTextWithTabsToLink(
            org.odftoolkit.odfdom.pkg.OdfFileDom dom,
            TextAElement link,
            String text) {

        if (text == null || text.isEmpty()) return;
        org.w3c.dom.Document w3c = (org.w3c.dom.Document) dom;

        String[] parts = text.split("\\[tab\\]", -1);
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                link.appendChild(w3c.createTextNode(parts[i]));
            }
            if (i < parts.length - 1) {
                // insère <text:tab/> dans le lien
                TextTabElement tab = dom.newOdfElement(TextTabElement.class);
                link.appendChild(tab);
            }
        }
    }

    /** Construit un <table:table> avec une éventuelle ligne d’en-tête "|!".
     *  - Respecte les échappements \| et \\ via TableSyntax.splitCells
     *  - Rend les cellules avec appendInlineRuns(...) pour garder le inline
     */
    private static void buildOdfTable(
            OdfTextDocument odt,
            OdfFileDom contentDom,
            List<String> rawRows,
            IntBox footnoteCounter) throws Exception {

        if (rawRows == null || rawRows.isEmpty()) return;

        // 1) Convertir chaque ligne en liste de cellules
        List<List<String>> parsed = new ArrayList<>();
        boolean hasHeader = false;

        int maxCols = 0;
        for (int r = 0; r < rawRows.size(); r++) {
            String row = rawRows.get(r);
            if (!TableSyntax.isTableRow(row)) continue; // sécurité

            List<String> cells = TableSyntax.splitCells(row);
            // Si la ligne est en-tête, splitCells a déjà ignoré le préfixe "|!"
            if (TableSyntax.isHeaderRow(row)) hasHeader = true;

            parsed.add(cells);
            if (cells.size() > maxCols) maxCols = cells.size();
        }
        if (parsed.isEmpty() || maxCols == 0) return;

        // 2) Créer <table:table> et colonnes
        TableTableElement table = contentDom.newOdfElement(TableTableElement.class);
        odt.getContentRoot().appendChild(table);

        // colonnes (on peut aussi utiliser number-columns-repeated)
        for (int c = 0; c < maxCols; c++) {
            TableTableColumnElement col = contentDom.newOdfElement(TableTableColumnElement.class);
            table.appendChild(col);
        }

        // 3) Si en-tête, prendre la/les premières lignes "|!" en header-rows
        int rowIndex = 0;
        if (hasHeader) {
            TableTableHeaderRowsElement thead = contentDom.newOdfElement(TableTableHeaderRowsElement.class);
            table.appendChild(thead);

            // On met TOUTES les lignes initiales commençant par "|!" dans l’en-tête
            while (rowIndex < parsed.size() && TableSyntax.isHeaderRow(rawRows.get(rowIndex))) {
                List<String> cells = parsed.get(rowIndex);
                TableTableRowElement tr = contentDom.newOdfElement(TableTableRowElement.class);
                thead.appendChild(tr);

                // cells -> table:table-cell + <text:p> + inline
                for (int c = 0; c < maxCols; c++) {
                    TableTableCellElement tc = contentDom.newOdfElement(TableTableCellElement.class);
                    tr.appendChild(tc);

                    TextPElement p = contentDom.newOdfElement(TextPElement.class);
                    tc.appendChild(p);

                    String cellText = (c < cells.size()) ? cells.get(c) : "";
                    appendInlineRuns(contentDom, p, cellText, odt, footnoteCounter);
                }
                rowIndex++;
            }
        }

        // 4) Corps du tableau
        for (; rowIndex < parsed.size(); rowIndex++) {
            List<String> cells = parsed.get(rowIndex);
            TableTableRowElement tr = contentDom.newOdfElement(TableTableRowElement.class);
            table.appendChild(tr);

            for (int c = 0; c < maxCols; c++) {
                TableTableCellElement tc = contentDom.newOdfElement(TableTableCellElement.class);
                tr.appendChild(tc);

                TextPElement p = contentDom.newOdfElement(TextPElement.class);
                tc.appendChild(p);

                String cellText = (c < cells.size()) ? cells.get(c) : "";
                appendInlineRuns(contentDom, p, cellText, odt, footnoteCounter);
            }
        }
    }




}
