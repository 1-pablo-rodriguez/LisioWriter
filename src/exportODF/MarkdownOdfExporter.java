package exportODF;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.odftoolkit.odfdom.doc.OdfTextDocument;
import org.odftoolkit.odfdom.dom.element.text.TextAElement;
import org.odftoolkit.odfdom.dom.element.text.TextListElement;
import org.odftoolkit.odfdom.dom.element.text.TextListItemElement;
import org.odftoolkit.odfdom.dom.element.text.TextPElement;
import org.odftoolkit.odfdom.dom.element.text.TextTabElement;
import org.odftoolkit.odfdom.dom.style.OdfStyleFamily;
import org.odftoolkit.odfdom.incubator.doc.style.OdfStyle;
import org.odftoolkit.odfdom.pkg.OdfFileDom;

import writer.ui.text.PiedDeMoucheCleaner;

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

    private static final Pattern PAGE_BREAK = Pattern.compile("^\\s*@saut\\s+de\\s+page\\s*$");

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
    // 7) souligné italic  _^...^_
    private static final Pattern UNDERLINE_ITALIC = Pattern.compile("_\\^(.+?)\\^_");
    // 8) exposant ^¨...¨^
    private static final Pattern EXPOSANT = Pattern.compile("\\^¨(.+?)¨\\^");
    // 9) indice _¨...¨_
    private static final Pattern INDICE = Pattern.compile("_¨(.+?)¨_");
    // 10) lien hypertexte @[Texte: URL]
    private static final Pattern LINK =
            Pattern.compile("@\\[(.+?):\\s*([a-zA-Z][a-zA-Z0-9+\\-.]*:[^\\]]+?)\\]");

    // --- Styles de spans (noms auto) ---
    private static final String SPAN_BOLD       = "BW_Span_Bold";
    private static final String SPAN_ITALIC     = "BW_Span_Italic";
    private static final String SPAN_BOLDITALIC = "BW_Span_BoldItalic";
    private static final String SPAN_UNDER      = "BW_Span_Underline";
    private static final String SPAN_UNDERBOLD  = "BW_Span_UnderlineBold";
    private static final String SPAN_UNDERITALIC= "BW_Span_UnderlineItalic";
    private static final String SPAN_EXPOSANT   = "BW_Span_Exposant";
    private static final String SPAN_INDICE     = "BW_Span_Indice";

    // Suppression d’image
    // Variante qui englobe les espaces autour pour éviter des doubles espaces résiduels
    // supprime ![Logo], ![img](a/b/c.png), ![img][logo], Bonjour ![icone] monde
    private static final Pattern IMG_ANY_WITH_WS =
        Pattern.compile("(?m)[ \\t]*!\\[[^\\]]*\\](?:\\([^)]*\\)|\\[[^\\]]*\\])?[ \\t]*");

    private static final class ListKind {
        static final int NONE     = 0;
        static final int ORDERED  = 1;
        static final int UNORDERED= 2;
    }


    /** API principale : exporte la chaîne markdown-ish vers un .odt. */
    public static void export(String src, File outFile) throws Exception {
        OdfTextDocument odt = OdfTextDocument.newTextDocument();

        // Évite le 1er paragraphe vide généré par défaut
        dropInitialEmptyParagraph(odt);

        // Styles de spans (gras, italic, etc.)
        prepareSpanTextStyles(odt);

        // Style de paragraphe « corps de texte » basé sur bodyText
        String bodyParagraphStyleName = ensureBodyParagraphStyle(odt);

        OdfFileDom contentDom = odt.getContentDom();

        boolean pageBreakForNextParagraph = false;

        // État de liste en cours
        int listState = ListKind.NONE;
        TextListElement currentList = null;

        // Compteur MUTABLE pour les notes de bas de page
        IntBox footBox = new IntBox(1);
        int footnoteCounter = 1; // utilisé par les listes

        src = PiedDeMoucheCleaner.clean(src);

        // 1) Supprimer toutes les images en une seule passe
        src = IMG_ANY_WITH_WS.matcher(src).replaceAll(" ");

        // 2) Harmoniser un peu les espaces (sans toucher aux retours ligne)
        src = src.replaceAll(" {2,}", " ");       // compresser les doubles espaces
        src = src.replaceAll("(?m)[ \\t]+$", ""); // trim right ligne par ligne

        String[] lines = src.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // 0) Ligne vide : on *ne* crée pas de paragraphe vide
            if (line.trim().isEmpty()) {
                // on ferme juste une liste éventuelle ; on garde un éventuel saut de page
                if (listState != ListKind.NONE) {
                    currentList = null;
                    listState = ListKind.NONE;
                }
                continue;
            }

            // 1) Saut de page : s’applique au paragraphe SUIVANT
            if (PAGE_BREAK.matcher(line).matches()) {
                if (listState != ListKind.NONE) {
                    currentList = null;
                    listState = ListKind.NONE;
                }
                pageBreakForNextParagraph = true;
                continue;
            }

            // === TABLEAUX @t ... @/t ===
            if (writer.ui.editor.TableSyntax.isTableStart(line)) {
                // fermer liste éventuelle
                if (listState != ListKind.NONE) { currentList = null; listState = ListKind.NONE; }

                // récupérer les lignes du bloc
                java.util.List<String> rows = new java.util.ArrayList<>();
                int j = exportODF.OdfTableExporter.collectTableBlock(lines, i, rows);

                // éventuel saut de page pour le prochain bloc
                if (pageBreakForNextParagraph) {
                    TextPElement dummy = odt.newParagraph();
                    applyBreakBefore(odt, dummy); // utilisera BW_BodyText par défaut
                    pageBreakForNextParagraph = false;
                }

                // construire la table
                exportODF.OdfTableExporter.buildOdfTable(odt, contentDom, rows,
                    (p, cellText) -> appendInlineRuns(contentDom, p, cellText, odt, footBox)
                );

                i = j; // sauter jusqu’à @/t
                continue;
            }

            // --- Titres spéciaux #P. et #S. ---
            Matcher m;

            // #P. Titre principal  -> style "Title"
            m = H_P.matcher(line);
            if (m.matches()) {
                String txt = m.group(1).trim();

                if (listState != ListKind.NONE) {
                    currentList = null;
                    listState = ListKind.NONE;
                }

                if (!txt.isEmpty()) {
                    TextPElement p = odt.newParagraph();
                    // ⬇️ ici
                    p.setTextStyleNameAttribute(ensureHeadingParagraphStyle(odt));
                    if (pageBreakForNextParagraph) {
                        applyBreakBefore(odt, p);
                        pageBreakForNextParagraph = false;
                    }
                    appendInlineRuns(contentDom, p, txt, odt, footBox);
                }
                continue;
            }


            // #S. Sous-titre -> style "Subtitle" paramétré depuis commandes
            m = H_S.matcher(line);
            if (m.matches()) {
                String txt = m.group(1).trim();

                if (listState != ListKind.NONE) {
                    currentList = null;
                    listState = ListKind.NONE;
                }

                if (!txt.isEmpty()) {
                    TextPElement p = odt.newParagraph();
                    p.setTextStyleNameAttribute(ensureSubtitleParagraphStyle(odt));
                    if (pageBreakForNextParagraph) {
                        applyBreakBefore(odt, p);
                        pageBreakForNextParagraph = false;
                    }
                    appendInlineRuns(contentDom, p, txt, odt, footBox);
                }
                continue;
            }


            // 2) Détection des titres #1..#5
            int headingLevel = 0;
            String txt = null;

            if      ((m = H1.matcher(line)).matches()) { headingLevel = 1; txt = m.group(1).trim(); }
            else if ((m = H2.matcher(line)).matches()) { headingLevel = 2; txt = m.group(1).trim(); }
            else if ((m = H3.matcher(line)).matches()) { headingLevel = 3; txt = m.group(1).trim(); }
            else if ((m = H4.matcher(line)).matches()) { headingLevel = 4; txt = m.group(1).trim(); }
            else if ((m = H5.matcher(line)).matches()) { headingLevel = 5; txt = m.group(1).trim(); }

            if (headingLevel > 0) {
                if (listState != ListKind.NONE) {
                    currentList = null;
                    listState = ListKind.NONE;
                }
                if (txt != null && !txt.isEmpty()) {
                    TextPElement p = odt.newParagraph();
                    String style = switch (headingLevel) {
                    	case 1 -> ensureTitle1ParagraphStyle(odt);
                        case 2 -> ensureTitle2ParagraphStyle(odt);
                        case 3 -> ensureTitle3ParagraphStyle(odt);
                        case 4 -> ensureTitle4ParagraphStyle(odt);
                        case 5 -> ensureTitle5ParagraphStyle(odt);
                        default -> "Heading 1";
                    };
                    p.setTextStyleNameAttribute(style);
                    if (pageBreakForNextParagraph) {
                        applyBreakBefore(odt, p);
                        pageBreakForNextParagraph = false;
                    }
                    appendInlineRuns(contentDom, p, txt, odt, footBox);
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
                    String orderedStyleName = ensureListStyle(odt, true);
                    currentList = odt.getContentRoot().newTextListElement();
                    currentList.setTextStyleNameAttribute(orderedStyleName);
                    listState = ListKind.ORDERED;
                }

                TextListItemElement item = currentList.newTextListItemElement();
                TextPElement p = item.newTextPElement();
                
                // paragraphe au style « Corps de texte »
                p.setTextStyleNameAttribute(bodyParagraphStyleName);

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
                    if (listState != ListKind.NONE) {
                        currentList = null;
                        listState = ListKind.NONE;
                    }
                    // Style de liste non numérotée (•, indentation, etc.)
                    String unorderedStyleName = ensureListStyle(odt, false);
                    currentList = odt.getContentRoot().newTextListElement();
                    currentList.setTextStyleNameAttribute(unorderedStyleName);
                    listState = ListKind.UNORDERED;
                }

                TextListItemElement item = currentList.newTextListItemElement();
                TextPElement p = item.newTextPElement();

                // paragraphe au style « Corps de texte »
                p.setTextStyleNameAttribute(bodyParagraphStyleName);

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


            // 5) Paragraphe normal (corps de texte)
            if (listState != ListKind.NONE) {
                currentList = null;
                listState = ListKind.NONE;
            }

            TextPElement p = odt.newParagraph();
            // ← ICI on utilise ton style "corps de texte" basé sur bodyText
            p.setTextStyleNameAttribute(bodyParagraphStyleName);

            if (pageBreakForNextParagraph) {
                applyBreakBefore(odt, p);
                pageBreakForNextParagraph = false;
            }

            appendInlineRuns(contentDom, p, line.trim(), odt, footBox);
        }

        // Add méta-données
        applyMetadata(odt);

        // Fin : sauvegarde
        odt.save(outFile);
    }

    // ---------------------------------------------------------------------------------
    // Helpers styles
    // ---------------------------------------------------------------------------------

    /** Crée 4 styles de span (gras, italic, gras+italic, souligné, etc.). */
    private static void prepareSpanTextStyles(OdfTextDocument odt) throws Exception {
        OdfStyle sBold = ensureSpanStyle(odt, SPAN_BOLD);
        sBold.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontWeight,
                "bold");

        OdfStyle sIt = ensureSpanStyle(odt, SPAN_ITALIC);
        sIt.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontStyle,
                "italic");

        OdfStyle sBI = ensureSpanStyle(odt, SPAN_BOLDITALIC);
        sBI.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontWeight,
                "bold");
        sBI.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontStyle,
                "italic");

        OdfStyle sUnder = ensureSpanStyle(odt, SPAN_UNDER);
        sUnder.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineStyle,
                "solid");
        sUnder.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineType,
                "single");
        sUnder.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineColor,
                "font-color");

        OdfStyle sUnderBold = ensureSpanStyle(odt, SPAN_UNDERBOLD);
        sUnderBold.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontWeight,
                "bold");
        sUnderBold.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineStyle,
                "solid");
        sUnderBold.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineType,
                "single");
        sUnderBold.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineColor,
                "font-color");

        OdfStyle sUnderItalic = ensureSpanStyle(odt, SPAN_UNDERITALIC);
        sUnderItalic.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontStyle,
                "italic");
        sUnderItalic.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineStyle,
                "solid");
        sUnderItalic.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineType,
                "single");
        sUnderItalic.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextUnderlineColor,
                "font-color");

        OdfStyle sExpo = ensureSpanStyle(odt, SPAN_EXPOSANT);
        sExpo.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextPosition,
                "super 58%");

        OdfStyle sIndice = ensureSpanStyle(odt, SPAN_INDICE);
        sIndice.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.TextPosition,
                "sub 58%");
    }

    /** Crée ou récupère un style de liste (numérotée ou à puces). */
    private static String ensureListStyle(OdfTextDocument odt, boolean ordered) throws Exception {
        String styleName = ordered ? "BW_ListOrdered" : "BW_ListUnordered";

        var cdom = odt.getContentDom();
        var autoStyles = cdom.getAutomaticStyles();
        var existing = autoStyles.getListStyle(styleName);
        if (existing != null) return styleName;

        org.odftoolkit.odfdom.incubator.doc.text.OdfTextListStyle listStyle =
            autoStyles.newListStyle(styleName);

        if (ordered) {
            // === Liste numérotée niveau 1 ===
            org.odftoolkit.odfdom.dom.element.text.TextListLevelStyleNumberElement num =
                listStyle.newTextListLevelStyleNumberElement("1", 1);
            num.setStyleNumFormatAttribute("1");
            num.setStyleNumSuffixAttribute(".");

            org.odftoolkit.odfdom.dom.element.style.StyleListLevelPropertiesElement props =
                num.newStyleListLevelPropertiesElement();

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

    /**
     * Style de paragraphe « corps de texte » :
     * construit à partir de commandes.nodeblindWriter.bodyText.
     */
    private static String ensureBodyParagraphStyle(OdfTextDocument odt) throws Exception {
        final String STYLE_NAME = "Text body";

        // On travaille dans styles.xml
        var sdom = odt.getStylesDom();
        var officeStyles = sdom.getOfficeStyles();
        OdfStyle s = officeStyles.getStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
        if (s != null) return STYLE_NAME;

        // ⚠️ ici : styleName + famille
        s = officeStyles.newStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
        s.setStyleParentStyleNameAttribute("Text_20_body");

        var bodyText = writer.commandes.nodeblindWriter.retourneFirstEnfant("bodyText");

        String police       = null;
        String size         = null;
        String align        = null;
        String inter        = null;
        String marginTop    = null;
        String marginBottom = null;

        if (bodyText != null) {
            police = trimOrNull(bodyText.getAttributs("police"));
            size   = trimOrNull(bodyText.getAttributs("size"));
            align  = trimOrNull(bodyText.getAttributs("alignement"));
            inter  = trimOrNull(bodyText.getAttributs("interligne"));

            marginTop = firstNonBlank(
                    trimOrNull(bodyText.getAttributs("espacement_au_dessus")),
                    trimOrNull(bodyText.getAttributs("margin_top"))
            );
            marginBottom = firstNonBlank(
                    trimOrNull(bodyText.getAttributs("espacement_en_dessous")),
                    trimOrNull(bodyText.getAttributs("margin_bottom"))
            );
        }

        if (police == null)       police = "Arial";
        if (size == null)         size = "14pt";
        if (align == null)        align = "justify";
        if (inter == null)        inter = "115%";
        if (marginTop == null)    marginTop = "0.2cm";
        if (marginBottom == null) marginBottom = "0.2cm";

        if (align != null) {
            String a = align.toLowerCase();
            if (a.startsWith("just"))   align = "justify";
            else if (a.startsWith("centr"))  align = "center";
            else if (a.startsWith("gauch"))  align = "left";
            else if (a.startsWith("droite")) align = "right";
        }

        if (police != null) {
            s.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontName,
                police
            );
        }
        if (size != null) {
            s.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontSize,
                size
            );
        }
        if (align != null) {
            s.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.TextAlign,
                align
            );
        }
        if (inter != null) {
            s.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.LineHeight,
                inter
            );
        }
        if (marginTop != null) {
            s.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginTop,
                marginTop
            );
        }
        if (marginBottom != null) {
            s.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginBottom,
                marginBottom
            );
        }

        return STYLE_NAME;
    }


    /**
	 * Style de paragraphe « Titre 1 » :
	 * construit à partir de commandes.nodeblindWriter.Titre1.
	 */
	private static String ensureTitle1ParagraphStyle(OdfTextDocument odt) throws Exception {
	    final String STYLE_NAME = "Heading 1";
	
	    var sdom = odt.getStylesDom();
	    var officeStyles = sdom.getOfficeStyles();
	    OdfStyle s = officeStyles.getStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    if (s != null) return STYLE_NAME;
	
	    s = officeStyles.newStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    s.setStyleParentStyleNameAttribute("Heading 1");
	
	    var t1 = writer.commandes.nodeblindWriter.retourneFirstEnfant("Titre1");
	
	    String police       = null;
	    String size         = null;
	    String align        = null;
	    String inter        = null;
	    String marginTop    = null;
	    String marginBottom = null;
	    String keepWithNext = null;
	
	    if (t1 != null) {
	        police = trimOrNull(t1.getAttributs("police"));
	        size   = trimOrNull(t1.getAttributs("size"));
	        align  = trimOrNull(t1.getAttributs("alignement"));
	        inter  = trimOrNull(t1.getAttributs("interligne"));
	
	        marginTop = firstNonBlank(
	                trimOrNull(t1.getAttributs("espacement_au_dessus")),
	                trimOrNull(t1.getAttributs("margin_top"))
	        );
	        marginBottom = firstNonBlank(
	                trimOrNull(t1.getAttributs("espacement_en_dessous")),
	                trimOrNull(t1.getAttributs("margin_bottom"))
	        );
	
	        keepWithNext = trimOrNull(t1.getAttributs("keep-with-next"));
	    }
	
	    if (police == null)       police = "Arial";
	    if (size == null)         size = "18pt";
	    if (align == null)        align = "start";
	    if (inter == null)        inter = "150%";
	    if (marginTop == null)    marginTop = "0.3cm";
	    if (marginBottom == null) marginBottom = "0.5cm";
	
	    if (align != null) {
	        String a = align.toLowerCase();
	        if (a.startsWith("just"))        align = "justify";
	        else if (a.startsWith("centr"))  align = "center";
	        else if (a.startsWith("gauch"))  align = "left";
	        else if (a.startsWith("droite")) align = "right";
	        else if (a.startsWith("start"))  align = "start";
	    }
	
	    if (police != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontName,
	            police
	        );
	    }
	    if (size != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontSize,
	            size
	        );
	    }
	    if (align != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.TextAlign,
	            align
	        );
	    }
	    if (inter != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.LineHeight,
	            inter
	        );
	    }
	    if (marginTop != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginTop,
	            marginTop
	        );
	    }
	    if (marginBottom != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginBottom,
	            marginBottom
	        );
	    }
	
	    if ("always".equalsIgnoreCase(keepWithNext)) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.KeepWithNext,
	            "always"
	        );
	    }
	
	    return STYLE_NAME;
	}

	/**
	 * Style de paragraphe « Titre 2 » :
	 * construit à partir de commandes.nodeblindWriter.Titre2.
	 */
	private static String ensureTitle2ParagraphStyle(OdfTextDocument odt) throws Exception {
	    final String STYLE_NAME = "Heading 2";

	    var sdom = odt.getStylesDom();
	    var officeStyles = sdom.getOfficeStyles();
	    OdfStyle s = officeStyles.getStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    if (s != null) return STYLE_NAME;

	    s = officeStyles.newStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    s.setStyleParentStyleNameAttribute("Heading 2");

	    var t2 = writer.commandes.nodeblindWriter.retourneFirstEnfant("Titre2");

	    String police       = null;
	    String size         = null;
	    String align        = null;
	    String inter        = null;
	    String marginTop    = null;
	    String marginBottom = null;
	    String keepWithNext = null;

	    if (t2 != null) {
	        police = trimOrNull(t2.getAttributs("police"));
	        size   = trimOrNull(t2.getAttributs("size"));
	        align  = trimOrNull(t2.getAttributs("alignement"));
	        inter  = trimOrNull(t2.getAttributs("interligne"));

	        marginTop = firstNonBlank(
	                trimOrNull(t2.getAttributs("espacement_au_dessus")),
	                trimOrNull(t2.getAttributs("margin_top"))
	        );
	        marginBottom = firstNonBlank(
	                trimOrNull(t2.getAttributs("espacement_en_dessous")),
	                trimOrNull(t2.getAttributs("margin_bottom"))
	        );

	        keepWithNext = trimOrNull(t2.getAttributs("keep-with-next"));
	    }

	    if (police == null)       police = "Arial";
	    if (size == null)         size = "18pt";
	    if (align == null)        align = "start";
	    if (inter == null)        inter = "150%";
	    if (marginTop == null)    marginTop = "0.3cm";
	    if (marginBottom == null) marginBottom = "0.5cm";

	    if (align != null) {
	        String a = align.toLowerCase();
	        if (a.startsWith("just"))        align = "justify";
	        else if (a.startsWith("centr"))  align = "center";
	        else if (a.startsWith("gauch"))  align = "left";
	        else if (a.startsWith("droite")) align = "right";
	        else if (a.startsWith("start"))  align = "start";
	    }

	    if (police != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontName,
	            police
	        );
	    }
	    if (size != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontSize,
	            size
	        );
	    }
	    if (align != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.TextAlign,
	            align
	        );
	    }
	    if (inter != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.LineHeight,
	            inter
	        );
	    }
	    if (marginTop != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginTop,
	            marginTop
	        );
	    }
	    if (marginBottom != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginBottom,
	            marginBottom
	        );
	    }

	    if ("always".equalsIgnoreCase(keepWithNext)) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.KeepWithNext,
	            "always"
	        );
	    }

	    return STYLE_NAME;
	}

	
	/**
	 * Style de paragraphe « Titre 3 » :
	 * construit à partir de commandes.nodeblindWriter.Titre3.
	 */
	private static String ensureTitle3ParagraphStyle(OdfTextDocument odt) throws Exception {
	    final String STYLE_NAME = "Heading 3";

	    // On travaille dans styles.xml (styles de document)
	    var sdom = odt.getStylesDom();
	    var officeStyles = sdom.getOfficeStyles();
	    OdfStyle s = officeStyles.getStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    if (s != null) return STYLE_NAME;

	    // Création du style de paragraphe BW_Title3
	    s = officeStyles.newStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    // Parent = Heading 3 pour rester compatible LibreOffice
	    s.setStyleParentStyleNameAttribute("Heading 3");

	    // Lecture des attributs Titre3 dans commandes.nodeblindWriter
	    var t3 = writer.commandes.nodeblindWriter.retourneFirstEnfant("Titre3");

	    String police       = null;
	    String size         = null;
	    String align        = null;
	    String inter        = null;
	    String marginTop    = null;
	    String marginBottom = null;
	    String keepWithNext = null;

	    if (t3 != null) {
	        police = trimOrNull(t3.getAttributs("police"));
	        size   = trimOrNull(t3.getAttributs("size"));
	        align  = trimOrNull(t3.getAttributs("alignement"));
	        inter  = trimOrNull(t3.getAttributs("interligne"));

	        marginTop = firstNonBlank(
	                trimOrNull(t3.getAttributs("espacement_au_dessus")),
	                trimOrNull(t3.getAttributs("margin_top"))
	        );
	        marginBottom = firstNonBlank(
	                trimOrNull(t3.getAttributs("espacement_en_dessous")),
	                trimOrNull(t3.getAttributs("margin_bottom"))
	        );

	        keepWithNext = trimOrNull(t3.getAttributs("keep-with-next"));
	    }

	    // Fallbacks si jamais Titre3 n’est pas (bien) initialisé côté commandes
	    if (police == null)       police = "Arial";
	    if (size == null)         size = "18pt";
	    if (align == null)        align = "start";
	    if (inter == null)        inter = "150%";
	    if (marginTop == null)    marginTop = "0.3cm";
	    if (marginBottom == null) marginBottom = "0.5cm";

	    // Normalisation de l’alignement -> valeurs ODF
	    if (align != null) {
	        String a = align.toLowerCase();
	        if (a.startsWith("just"))        align = "justify";
	        else if (a.startsWith("centr"))  align = "center";
	        else if (a.startsWith("gauch"))  align = "left";
	        else if (a.startsWith("droite")) align = "right";
	        else if (a.startsWith("start"))  align = "start";
	    }

	    // --- Propriétés texte ---
	    if (police != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontName,
	            police
	        );
	    }
	    if (size != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontSize,
	            size
	        );
	    }

	    // --- Propriétés paragraphe ---
	    if (align != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.TextAlign,
	            align
	        );
	    }
	    if (inter != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.LineHeight,
	            inter
	        );
	    }
	    if (marginTop != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginTop,
	            marginTop
	        );
	    }
	    if (marginBottom != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginBottom,
	            marginBottom
	        );
	    }

	    // keep-with-next → éviter un Titre 3 orphelin en bas de page
	    if ("always".equalsIgnoreCase(keepWithNext)) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.KeepWithNext,
	            "always"
	        );
	    }

	    return STYLE_NAME;
	}

	
	/**
	 * Style de paragraphe « Titre 4 » :
	 * construit à partir de commandes.nodeblindWriter.Titre4.
	 */
	private static String ensureTitle4ParagraphStyle(OdfTextDocument odt) throws Exception {
	    final String STYLE_NAME = "Heading 4";

	    // On travaille dans styles.xml (styles de document)
	    var sdom = odt.getStylesDom();
	    var officeStyles = sdom.getOfficeStyles();
	    OdfStyle s = officeStyles.getStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    if (s != null) return STYLE_NAME;

	    // Création du style BW_Title4
	    s = officeStyles.newStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    // Parent = Heading 4 pour rester compatible LibreOffice
	    s.setStyleParentStyleNameAttribute("Heading 4");

	    // ---- Lecture des attributs dans commandes.nodeblindWriter.Titre4 ----
	    var t4 = writer.commandes.nodeblindWriter.retourneFirstEnfant("Titre4");

	    String police       = null;
	    String size         = null;
	    String align        = null;
	    String inter        = null;
	    String marginTop    = null;
	    String marginBottom = null;
	    String keepWithNext = null;

	    if (t4 != null) {
	        police = trimOrNull(t4.getAttributs("police"));
	        size   = trimOrNull(t4.getAttributs("size"));
	        align  = trimOrNull(t4.getAttributs("alignement"));
	        inter  = trimOrNull(t4.getAttributs("interligne"));

	        marginTop = firstNonBlank(
	                trimOrNull(t4.getAttributs("espacement_au_dessus")),
	                trimOrNull(t4.getAttributs("margin_top"))
	        );
	        marginBottom = firstNonBlank(
	                trimOrNull(t4.getAttributs("espacement_en_dessous")),
	                trimOrNull(t4.getAttributs("margin_bottom"))
	        );

	        keepWithNext = trimOrNull(t4.getAttributs("keep-with-next"));
	    }

	    // Fallbacks si Titre4 n’est pas (bien) initialisé
	    if (police == null)       police = "Arial";
	    if (size == null)         size = "18pt";
	    if (align == null)        align = "start";
	    if (inter == null)        inter = "150%";
	    if (marginTop == null)    marginTop = "0.3cm";
	    if (marginBottom == null) marginBottom = "0.5cm";

	    // Normalisation de l’alignement → valeurs ODF
	    if (align != null) {
	        String a = align.toLowerCase();
	        if (a.startsWith("just"))        align = "justify";
	        else if (a.startsWith("centr"))  align = "center";
	        else if (a.startsWith("gauch"))  align = "left";
	        else if (a.startsWith("droite")) align = "right";
	        else if (a.startsWith("start"))  align = "start";
	    }

	    // --- Propriétés texte ---
	    if (police != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontName,
	            police
	        );
	    }
	    if (size != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontSize,
	            size
	        );
	    }

	    // --- Propriétés paragraphe ---
	    if (align != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.TextAlign,
	            align
	        );
	    }
	    if (inter != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.LineHeight,
	            inter
	        );
	    }
	    if (marginTop != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginTop,
	            marginTop
	        );
	    }
	    if (marginBottom != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginBottom,
	            marginBottom
	        );
	    }

	    // keep-with-next → éviter un Titre 4 orphelin en bas de page
	    if ("always".equalsIgnoreCase(keepWithNext)) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.KeepWithNext,
	            "always"
	        );
	    }

	    return STYLE_NAME;
	}

	/**
	 * Style de paragraphe « Titre 5 » :
	 * construit à partir de commandes.nodeblindWriter.Titre5.
	 */
	private static String ensureTitle5ParagraphStyle(OdfTextDocument odt) throws Exception {
	    final String STYLE_NAME = "Heading 5";

	    // On travaille dans styles.xml (styles de document)
	    var sdom = odt.getStylesDom();
	    var officeStyles = sdom.getOfficeStyles();
	    OdfStyle s = officeStyles.getStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    if (s != null) return STYLE_NAME;

	    // Création du style BW_Title5
	    s = officeStyles.newStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    // Parent = Heading 5 pour rester compatible LibreOffice
	    s.setStyleParentStyleNameAttribute("Heading 5");

	    // ---- Lecture des attributs dans commandes.nodeblindWriter.Titre5 ----
	    var t5 = writer.commandes.nodeblindWriter.retourneFirstEnfant("Titre5");

	    String police       = null;
	    String size         = null;
	    String align        = null;
	    String inter        = null;
	    String marginTop    = null;
	    String marginBottom = null;
	    String keepWithNext = null;

	    if (t5 != null) {
	        police = trimOrNull(t5.getAttributs("police"));
	        size   = trimOrNull(t5.getAttributs("size"));
	        align  = trimOrNull(t5.getAttributs("alignement"));
	        inter  = trimOrNull(t5.getAttributs("interligne"));

	        marginTop = firstNonBlank(
	                trimOrNull(t5.getAttributs("espacement_au_dessus")),
	                trimOrNull(t5.getAttributs("margin_top"))
	        );
	        marginBottom = firstNonBlank(
	                trimOrNull(t5.getAttributs("espacement_en_dessous")),
	                trimOrNull(t5.getAttributs("margin_bottom"))
	        );

	        keepWithNext = trimOrNull(t5.getAttributs("keep-with-next"));
	    }

	    // Fallbacks si Titre5 n’est pas (bien) initialisé
	    if (police == null)       police = "Arial";
	    if (size == null)         size = "18pt";
	    if (align == null)        align = "start";
	    if (inter == null)        inter = "150%";
	    if (marginTop == null)    marginTop = "0.3cm";
	    if (marginBottom == null) marginBottom = "0.5cm";

	    // Normalisation de l’alignement → valeurs ODF
	    if (align != null) {
	        String a = align.toLowerCase();
	        if (a.startsWith("just"))        align = "justify";
	        else if (a.startsWith("centr"))  align = "center";
	        else if (a.startsWith("gauch"))  align = "left";
	        else if (a.startsWith("droite")) align = "right";
	        else if (a.startsWith("start"))  align = "start";
	    }

	    // --- Propriétés texte ---
	    if (police != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontName,
	            police
	        );
	    }
	    if (size != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontSize,
	            size
	        );
	    }

	    // --- Propriétés paragraphe ---
	    if (align != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.TextAlign,
	            align
	        );
	    }
	    if (inter != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.LineHeight,
	            inter
	        );
	    }
	    if (marginTop != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginTop,
	            marginTop
	        );
	    }
	    if (marginBottom != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginBottom,
	            marginBottom
	        );
	    }

	    // keep-with-next → éviter un Titre 5 orphelin en bas de page
	    if ("always".equalsIgnoreCase(keepWithNext)) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.KeepWithNext,
	            "always"
	        );
	    }

	    return STYLE_NAME;
	}

	/**
	 * Style de paragraphe « Titre principal » :
	 * met à jour le style intégré LibreOffice "Title"
	 * à partir de commandes.nodeblindWriter.Title.
	 */
	private static String ensureHeadingParagraphStyle(OdfTextDocument odt) throws Exception {
	   final String STYLE_NAME = "Title";
	
	    // On travaille dans styles.xml (styles de document)
	    var sdom = odt.getStylesDom();
	    var officeStyles = sdom.getOfficeStyles();
	
	    // On récupère le style "Title" existant si possible
	    OdfStyle s = officeStyles.getStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    if (s == null) {
	        // Cas très théorique : si le style n'existe pas, on le crée
	        s = officeStyles.newStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	        // On peut, au choix, mettre un parent générique
	        s.setStyleParentStyleNameAttribute("Standard"); // ou rien du tout
	    }
	
	    // ---- Lecture des attributs dans commandes.nodeblindWriter.Title ----
	    var tprin = writer.commandes.nodeblindWriter.retourneFirstEnfant("Title");
	
	    String police       = null;
	    String size         = null;
	    String align        = null;
	    String inter        = null;
	    String marginTop    = null;
	    String marginBottom = null;
	    String keepWithNext = null;
	
	    if (tprin != null) {
	        police = trimOrNull(tprin.getAttributs("police"));
	        size   = trimOrNull(tprin.getAttributs("size"));
	        align  = trimOrNull(tprin.getAttributs("alignement"));
	        inter  = trimOrNull(tprin.getAttributs("interligne"));
	
	        marginTop = firstNonBlank(
	                trimOrNull(tprin.getAttributs("espacement_au_dessus")),
	                trimOrNull(tprin.getAttributs("margin_top"))
	        );
	        marginBottom = firstNonBlank(
	                trimOrNull(tprin.getAttributs("espacement_en_dessous")),
	                trimOrNull(tprin.getAttributs("margin_bottom"))
	        );
	
	        keepWithNext = trimOrNull(tprin.getAttributs("keep-with-next"));
	    }
	
	    // Fallbacks si jamais Title n’est pas (bien) initialisé
	    if (police == null)       police = "Arial";
	    if (size == null)         size = "18pt";
	    if (align == null)        align = "center";
	    if (inter == null)        inter = "150%";
	    if (marginTop == null)    marginTop = "0.423cm";
	    if (marginBottom == null) marginBottom = "0.212cm";
	
	    // Normalisation de l’alignement → valeurs ODF
	    if (align != null) {
	        String a = align.toLowerCase();
	        if (a.startsWith("just"))        align = "justify";
	        else if (a.startsWith("centr"))  align = "center";
	        else if (a.startsWith("gauch"))  align = "left";
	        else if (a.startsWith("droite")) align = "right";
	        else if (a.startsWith("start"))  align = "start";
	        else if (a.startsWith("center")) align = "center";
	    }
	
	    // --- Propriétés texte ---
	    if (police != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontName,
	            police
	        );
	    }
	    if (size != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontSize,
	            size
	        );
	    }
	
	    // --- Propriétés paragraphe ---
	    if (align != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.TextAlign,
	            align
	        );
	    }
	    if (inter != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.LineHeight,
	            inter
	        );
	    }
	    if (marginTop != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginTop,
	            marginTop
	        );
	    }
	    if (marginBottom != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginBottom,
	            marginBottom
	        );
	    }
	
	    // keep-with-next → éviter que le titre soit seul en bas de page
	    if ("always".equalsIgnoreCase(keepWithNext)) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.KeepWithNext,
	            "always"
	        );
	    }
	
	    // On renvoie le nom du style LO : "Title"
	    return STYLE_NAME;
	}

	/**
	 * Style de paragraphe « Sous-titre » :
	 * met à jour le style intégré LibreOffice "Subtitle"
	 * à partir de commandes.nodeblindWriter.Subtitle.
	 */
	private static String ensureSubtitleParagraphStyle(OdfTextDocument odt) throws Exception {
	    // Nom interne du style LibreOffice pour « Sous-titre »
	    final String STYLE_NAME = "Subtitle";

	    // On travaille dans styles.xml (styles de document)
	    var sdom = odt.getStylesDom();
	    var officeStyles = sdom.getOfficeStyles();

	    // On récupère le style "Subtitle" existant si possible
	    OdfStyle s = officeStyles.getStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	    if (s == null) {
	        // Cas de secours : si le style n’existe pas, on le crée
	        s = officeStyles.newStyle(STYLE_NAME, OdfStyleFamily.Paragraph);
	        s.setStyleParentStyleNameAttribute("Standard"); // ou "Text_20_body" si tu préfères
	    }

	    // ---- Lecture des attributs dans commandes.nodeblindWriter.Subtitle ----
	    var st = writer.commandes.nodeblindWriter.retourneFirstEnfant("Subtitle");

	    String police       = null;
	    String size         = null;
	    String align        = null;
	    String inter        = null;
	    String marginTop    = null;
	    String marginBottom = null;
	    String keepWithNext = null;

	    if (st != null) {
	        police = trimOrNull(st.getAttributs("police"));
	        size   = trimOrNull(st.getAttributs("size"));
	        align  = trimOrNull(st.getAttributs("alignement"));
	        inter  = trimOrNull(st.getAttributs("interligne"));

	        marginTop = firstNonBlank(
	                trimOrNull(st.getAttributs("espacement_au_dessus")),
	                trimOrNull(st.getAttributs("margin_top"))
	        );
	        marginBottom = firstNonBlank(
	                trimOrNull(st.getAttributs("espacement_en_dessous")),
	                trimOrNull(st.getAttributs("margin_bottom"))
	        );

	        keepWithNext = trimOrNull(st.getAttributs("keep-with-next"));
	    }

	    // Fallbacks si jamais Subtitle n’est pas (bien) initialisé
	    if (police == null)       police = "Arial";
	    if (size == null)         size = "18pt";
	    if (align == null)        align = "start";
	    if (inter == null)        inter = "150%";
	    if (marginTop == null)    marginTop = "0.3cm";
	    if (marginBottom == null) marginBottom = "0.5cm";

	    // Normalisation de l’alignement → valeurs ODF
	    if (align != null) {
	        String a = align.toLowerCase();
	        if (a.startsWith("just"))        align = "justify";
	        else if (a.startsWith("centr"))  align = "center";
	        else if (a.startsWith("gauch"))  align = "left";
	        else if (a.startsWith("droite")) align = "right";
	        else if (a.startsWith("start"))  align = "start";
	    }

	    // --- Propriétés texte ---
	    if (police != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontName,
	            police
	        );
	    }
	    if (size != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleTextPropertiesElement.FontSize,
	            size
	        );
	    }

	    // --- Propriétés paragraphe ---
	    if (align != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.TextAlign,
	            align
	        );
	    }
	    if (inter != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.LineHeight,
	            inter
	        );
	    }
	    if (marginTop != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginTop,
	            marginTop
	        );
	    }
	    if (marginBottom != null) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.MarginBottom,
	            marginBottom
	        );
	    }

	    // keep-with-next → éviter un sous-titre orphelin en bas de page
	    if ("always".equalsIgnoreCase(keepWithNext)) {
	        s.setProperty(
	            org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.KeepWithNext,
	            "always"
	        );
	    }

	    // On renvoie le nom du style LO : "Subtitle"
	    return STYLE_NAME;
	}


    private static void applyBreakBefore(OdfTextDocument odt, TextPElement p) throws Exception {
        // 1) Style de base déjà présent (Title / Heading / BW_BodyText, etc.)
        String base = p.getTextStyleNameAttribute();
        if (base == null || base.isBlank()) {
            // si rien, on utilise le style de corps de texte
            base = ensureBodyParagraphStyle(odt);
        }

        // 2) Nom du style dérivé “base + saut de page avant”
        String derived = "BW_BreakBefore__" + base;

        org.odftoolkit.odfdom.dom.OdfContentDom cdom = odt.getContentDom();
        var auto = cdom.getAutomaticStyles();

        org.odftoolkit.odfdom.incubator.doc.style.OdfStyle s =
                auto.getStyle(derived, OdfStyleFamily.Paragraph);
        if (s == null) {
            s = auto.newStyle(OdfStyleFamily.Paragraph);
            s.setStyleNameAttribute(derived);
            s.setStyleParentStyleNameAttribute(base);
            s.setProperty(
                org.odftoolkit.odfdom.dom.element.style.StyleParagraphPropertiesElement.BreakBefore,
                "page"
            );
        }

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

    // ---------------------------------------------------------------------------------
    // Inline
    // ---------------------------------------------------------------------------------

    /** Parse le texte et insère dans le paragraphe les spans/notes. */
    private static void appendInlineRuns(
        org.odftoolkit.odfdom.pkg.OdfFileDom dom,
        org.odftoolkit.odfdom.dom.element.text.TextPElement paragraph,
        String text,
        org.odftoolkit.odfdom.doc.OdfTextDocument odt,
        IntBox footnoteCounter) throws Exception {

        org.w3c.dom.Document w3c = (org.w3c.dom.Document) dom;

        java.util.List<InlineToken> tokens = tokenizeInline(text);

        for (InlineToken tk : tokens) {
            switch (tk.kind) {

                case K.TEXT -> {
                    appendTextWithTabsToParagraph(dom, paragraph, tk.content);
                }

                case K.BOLDITALIC -> {
                    var span = paragraph.newTextSpanElement();
                    span.setTextStyleNameAttribute(SPAN_BOLDITALIC);
                    appendTextWithTabsToSpan(dom, span, tk.content);
                }

                case K.BOLD -> {
                    var span = paragraph.newTextSpanElement();
                    span.setTextStyleNameAttribute(SPAN_BOLD);
                    appendTextWithTabsToSpan(dom, span, tk.content);
                }

                case K.UNDERLINE -> {
                    var span = paragraph.newTextSpanElement();
                    span.setTextStyleNameAttribute(SPAN_UNDER);
                    appendTextWithTabsToSpan(dom, span, tk.content);
                }

                case K.UNDERBOLD -> {
                    var span = paragraph.newTextSpanElement();
                    span.setTextStyleNameAttribute(SPAN_UNDERBOLD);
                    appendTextWithTabsToSpan(dom, span, tk.content);
                }

                case K.UNDERITALIC -> {
                    var span = paragraph.newTextSpanElement();
                    span.setTextStyleNameAttribute(SPAN_UNDERITALIC);
                    appendTextWithTabsToSpan(dom, span, tk.content);
                }

                case K.ITALIC -> {
                    var span = paragraph.newTextSpanElement();
                    span.setTextStyleNameAttribute(SPAN_ITALIC);
                    appendTextWithTabsToSpan(dom, span, tk.content);
                }

                case K.EXPOSANT -> {
                    var span = paragraph.newTextSpanElement();
                    span.setTextStyleNameAttribute(SPAN_EXPOSANT);
                    appendTextWithTabsToSpan(dom, span, tk.content);
                }

                case K.INDICE -> {
                    var span = paragraph.newTextSpanElement();
                    span.setTextStyleNameAttribute(SPAN_INDICE);
                    appendTextWithTabsToSpan(dom, span, tk.content);
                }

                case K.FOOTNOTE -> {
                    var note = paragraph.newTextNoteElement("footnote");
                    var cit  = note.newTextNoteCitationElement();
                    cit.appendChild(w3c.createTextNode(String.valueOf(footnoteCounter.get())));

                    var body = note.newTextNoteBodyElement();
                    var bp   = body.newTextPElement();
                    bp.appendChild(w3c.createTextNode(tk.content));

                    footnoteCounter.inc();
                }

                case K.LINK -> {
                    String[] parts = tk.content.split("\\|\\|", 2);
                    String label = parts[0];
                    String url = (parts.length > 1 ? parts[1] : "").trim();

                    TextAElement link = paragraph.newTextAElement(url, null);
                    appendTextWithTabsToLink(dom, link, label);
                }
            }
        }
    }

    // Types de token inline
    private static final class K {
        static final int TEXT        = 0;
        static final int BOLD        = 1;
        static final int ITALIC      = 2;
        static final int UNDERLINE   = 3;
        static final int BOLDITALIC  = 4;
        static final int UNDERBOLD   = 5;
        static final int UNDERITALIC = 6;
        static final int EXPOSANT    = 7;
        static final int INDICE      = 8;
        static final int FOOTNOTE    = 9;
        static final int LINK        = 10;
    }


    private static final class InlineToken {
        final int kind;
        final String content;
        InlineToken(int k, String c) { kind = k; content = c; }
    }


    // Représente la prochaine occurrence parmi nos regex, la plus à gauche
    private static final class Match {
        final int kind; // 0=BI,1=UB,2=UI,3=B,4=U,5=I,6=FN,7=EXP,8=IND,9=LINK
        final int start, end;
        final String inner;
        Match(int k, int s, int e, String in) { kind = k; start = s; end = e; inner = in; }
    }

    /** Tokenizer : traite d’abord *^ ^* puis _* *_, _^ ^_, **, __, ^^, @(...) etc. */
    private static List<InlineToken> tokenizeInline(String src) {
        List<InlineToken> out = new ArrayList<>();
        int idx = 0;
        while (idx < src.length()) {
            Match best = findNextMatch(src, idx);
            if (best == null) {
                out.add(new InlineToken(K.TEXT, src.substring(idx)));
                break;
            }
            if (best.start > idx) {
                out.add(new InlineToken(K.TEXT, src.substring(idx, best.start)));
            }
            switch (best.kind) {
                case 0 -> out.add(new InlineToken(K.BOLDITALIC, best.inner)); // *^ ^*
                case 1 -> out.add(new InlineToken(K.UNDERBOLD,  best.inner)); // _* *_
                case 2 -> out.add(new InlineToken(K.UNDERITALIC,best.inner)); // _^ ^_
                case 3 -> out.add(new InlineToken(K.BOLD,       best.inner)); // ** **
                case 4 -> out.add(new InlineToken(K.UNDERLINE,  best.inner)); // __ __
                case 5 -> out.add(new InlineToken(K.ITALIC,     best.inner)); // ^^ ^^
                case 6 -> out.add(new InlineToken(K.FOOTNOTE,   best.inner)); // @(...)
                case 7 -> out.add(new InlineToken(K.EXPOSANT,   best.inner)); // ^¨ ¨^
                case 8 -> out.add(new InlineToken(K.INDICE,     best.inner)); // _¨ ¨_
                case 9 -> {
                    Matcher linkMatcher = LINK.matcher(src.substring(best.start, best.end));
                    if (linkMatcher.matches()) {
                        out.add(new InlineToken(
                                K.LINK,
                                linkMatcher.group(1).trim() + "||" + linkMatcher.group(2).trim()
                        ));
                    }
                }
            }
            idx = best.end;
        }
        return out;
    }

    private static Match findNextMatch(String s, int from) {
        Matcher[] ms = new Matcher[] {
            BOLD_ITALIC.matcher(s),      // 0
            UNDERLINE_BOLD.matcher(s),   // 1
            UNDERLINE_ITALIC.matcher(s), // 2
            BOLD.matcher(s),             // 3
            UNDERLINE.matcher(s),        // 4
            ITALIC.matcher(s),           // 5
            FOOTNOTE.matcher(s),         // 6
            EXPOSANT.matcher(s),         // 7
            INDICE.matcher(s),           // 8
            LINK.matcher(s)              // 9
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

    // --- main de test éventuel ---
    public static void main(String[] args) throws Exception {
    }

    // ---------------------------------------------------------------------------------
    // Gestion paragraphe vide initial
    // ---------------------------------------------------------------------------------

    /** Supprime le premier <text:p> effectivement vide (en ignorant sequence-decls, etc.). */
    private static void dropInitialEmptyParagraph(OdfTextDocument odt) throws Exception {
        var root = odt.getContentRoot();
        if (root == null) return;

        org.w3c.dom.Node n = root.getFirstChild();
        while (n != null && !(n instanceof org.odftoolkit.odfdom.dom.element.text.TextPElement)) {
            n = n.getNextSibling();
        }
        if (!(n instanceof org.odftoolkit.odfdom.dom.element.text.TextPElement p)) return;

        if (isEffectivelyEmptyParagraph(p)) {
            root.removeChild(p);
        }
    }

    /** Un paragraphe est “vide” s’il ne contient que des blancs, <text:s/> ou <text:tab/>. */
    private static boolean isEffectivelyEmptyParagraph(
            org.odftoolkit.odfdom.dom.element.text.TextPElement p) {

        final String TEXT_NS = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
        var kids = p.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            var c = kids.item(i);
            switch (c.getNodeType()) {
                case org.w3c.dom.Node.TEXT_NODE -> {
                    if (!c.getTextContent().trim().isEmpty()) return false;
                }
                case org.w3c.dom.Node.ELEMENT_NODE -> {
                    String local = c.getLocalName();
                    String ns = c.getNamespaceURI();
                    if (!(TEXT_NS.equals(ns) && ("s".equals(local) || "tab".equals(local)))) {
                        return false;
                    }
                }
            }
        }
        String t = p.getTextContent();
        return t == null || t.trim().isEmpty();
    }

    // ---------------------------------------------------------------------------------
    // Méta-données
    // ---------------------------------------------------------------------------------

    private static void applyMetadata(org.odftoolkit.odfdom.doc.OdfTextDocument odt) throws Exception {
        org.odftoolkit.odfdom.pkg.OdfFileDom metaDom = odt.getMetaDom();

        org.w3c.dom.Document w3c = (org.w3c.dom.Document) metaDom;
        org.w3c.dom.Element root = w3c.getDocumentElement();
        final String OFFICE_NS = org.odftoolkit.odfdom.dom.element.office.OfficeMetaElement.ELEMENT_NAME.getUri();
        final String OFFICE_META_LOCAL = org.odftoolkit.odfdom.dom.element.office.OfficeMetaElement.ELEMENT_NAME.getLocalName();

        org.w3c.dom.Element officeMetaEl = findChildByName(root, OFFICE_NS, OFFICE_META_LOCAL);
        if (officeMetaEl == null) {
            var officeMetaOdf = metaDom.newOdfElement(
                    org.odftoolkit.odfdom.dom.element.office.OfficeMetaElement.class);
            officeMetaEl = (org.w3c.dom.Element) officeMetaOdf;
            root.appendChild(officeMetaEl);
        }

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

        String titre       = metaAttr("titre", "LeTitre");
        String sujet       = metaAttr("sujet", "LeSujet");
        String auteur      = metaAttr("auteur", "nom");
        String coAuteurStr = firstNonBlank(
                metaAttr("coauteur", "noms"),
                metaAttr("coauteur", "nom"),
                metaAttr("coauteur", "liste"));
        String societe     = metaAttr("society", "nom");
        String description = metaAttr("description", "resume");

        putDescriptionAsTextP(metaDom, officeMetaEl, description);

        String motsCles = metaAttr("motsCles", "mots");
        if (motsCles != null && !motsCles.isBlank()) {
            for (String kw : motsCles.split("[,;]")) {
                String k = kw.trim();
                if (!k.isEmpty()) {
                    putTextElem(metaDom, officeMetaEl,
                            org.odftoolkit.odfdom.dom.element.meta.MetaKeywordElement.class, k);
                }
            }
        }

        String langue = metaAttr("langue", "lang");
        putTextElem(metaDom, officeMetaEl,
                org.odftoolkit.odfdom.dom.element.dc.DcLanguageElement.class,
                langue);
        if (langue != null && !langue.isBlank()) {
            putUserDefined(metaDom, officeMetaEl, "Langue", langue, w3c);
        }

        String creationIso = firstNonBlank(
                metaAttr("date_creation", "date"),
                java.time.OffsetDateTime.now().toString());

        putTextElem(metaDom, officeMetaEl,
                org.odftoolkit.odfdom.dom.element.dc.DcTitleElement.class, titre);
        putTextElem(metaDom, officeMetaEl,
                org.odftoolkit.odfdom.dom.element.dc.DcSubjectElement.class, sujet);

        putTextElem(metaDom, officeMetaEl,
                org.odftoolkit.odfdom.dom.element.dc.DcCreatorElement.class, auteur);
        putTextElem(metaDom, officeMetaEl,
                org.odftoolkit.odfdom.dom.element.meta.MetaInitialCreatorElement.class, auteur);

        if (coAuteurStr != null && !coAuteurStr.isBlank()) {
            for (String raw : coAuteurStr.split("[,;]")) {
                String name = raw.trim();
                if (!name.isEmpty() && !name.equalsIgnoreCase(auteur)) {
                    putTextElem(metaDom, officeMetaEl,
                            org.odftoolkit.odfdom.dom.element.dc.DcCreatorElement.class, name);
                }
            }
        }

        if (motsCles != null && !motsCles.isBlank()) {
            for (String kw : motsCles.split("[,;]")) {
                String k = kw.trim();
                if (!k.isEmpty()) {
                    putTextElem(metaDom, officeMetaEl,
                            org.odftoolkit.odfdom.dom.element.meta.MetaKeywordElement.class, k);
                }
            }
        }

        putTextElem(metaDom, officeMetaEl,
                org.odftoolkit.odfdom.dom.element.meta.MetaGeneratorElement.class,
                "LisioWriter/MarkdownOdfExporter");
        putTextElem(metaDom, officeMetaEl,
                org.odftoolkit.odfdom.dom.element.meta.MetaCreationDateElement.class,
                creationIso);

        if (societe != null && !societe.isBlank()) {
            putUserDefined(metaDom, officeMetaEl, "Société", societe, w3c);
        }

        odt.updateMetaData();
    }

    // --- helper pour commandes.meta ---
    private static String metaAttr(String childName, String attrName) {
        try {
            var node = writer.commandes.meta.retourneFirstEnfant(childName);
            if (node == null) return null;
            String v = node.getAttributs(attrName);
            return (v == null || v.isBlank()) ? null : v.trim();
        } catch (Exception ignore) {
            return null;
        }
    }

    // Écrit <dc:description><text:p>...</text:p></dc:description>
    private static void putDescriptionAsTextP(
            org.odftoolkit.odfdom.pkg.OdfFileDom metaDom,
            org.w3c.dom.Element officeMetaEl,
            String description) throws Exception {

        if (description == null || description.isBlank()) return;

        var desc = metaDom.newOdfElement(
                org.odftoolkit.odfdom.dom.element.dc.DcDescriptionElement.class);

        for (String line : description.split("\\R", -1)) {
            var p = metaDom.newOdfElement(
                    org.odftoolkit.odfdom.dom.element.text.TextPElement.class);
            p.appendChild(((org.w3c.dom.Document) metaDom).createTextNode(line));
            desc.appendChild(p);
        }

        officeMetaEl.appendChild((org.w3c.dom.Element) desc);
    }
    
    private static String trimOrNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }


    // retourne le premier string non vide
    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
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

    // Crée un élément ODFDOM, l’attache à parentEl, et y met un texte
    private static <T extends org.odftoolkit.odfdom.pkg.OdfElement> void putTextElem(
            org.odftoolkit.odfdom.pkg.OdfFileDom dom,
            org.w3c.dom.Element parentEl,
            Class<T> clazz,
            String text) throws Exception {

        if (text == null || text.isBlank()) return;

        T el = dom.newOdfElement(clazz);
        parentEl.appendChild((org.w3c.dom.Element) el);
        ((org.w3c.dom.Element) el).appendChild(
                ((org.w3c.dom.Document) dom).createTextNode(text));
    }

    private static void putUserDefined(
            org.odftoolkit.odfdom.pkg.OdfFileDom metaDom,
            org.w3c.dom.Element officeMetaEl,
            String metaName, String value,
            org.w3c.dom.Document w3c) throws Exception {

        if (value == null || value.isBlank()) return;
        var ud = metaDom.newOdfElement(
                org.odftoolkit.odfdom.dom.element.meta.MetaUserDefinedElement.class);
        ud.setMetaNameAttribute(metaName);
        ud.setMetaValueTypeAttribute("string");
        officeMetaEl.appendChild((org.w3c.dom.Node) ud);
        ((org.w3c.dom.Node) ud).appendChild(w3c.createTextNode(value.trim()));
    }

    // ---------------------------------------------------------------------------------
    // Ajout de texte avec [tab]
    // ---------------------------------------------------------------------------------

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
                p.newTextTabElement();
            }
        }
    }

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
                span.newTextTabElement();
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
                TextTabElement tab = dom.newOdfElement(TextTabElement.class);
                link.appendChild(tab);
            }
        }
    }
}
