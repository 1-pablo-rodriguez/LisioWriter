package writer.ui.editor;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Classe utilitaire pour coloriser le texte dans l’éditeur.
 */
public final class TextHighlighter {

    private TextHighlighter() {}

    @SuppressWarnings("unused")
	public static void apply(JTextPane editor) {
    try {
        final String BRAILLE = "\u283F"; // ⠿

        StyledDocument doc = editor.getStyledDocument();
        String text = doc.getText(0, doc.getLength());

        // === 1. Réinitialiser le style global ===
        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setForeground(normal, Color.WHITE);
        StyleConstants.setUnderline(normal, false);
        doc.setCharacterAttributes(0, text.length(), normal, false);

        // === 2. Style des titres, listes, [Tab], codes spéciaux ===
        SimpleAttributeSet codeStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(codeStyle, new Color(255, 180, 80)); // orange doux
        StyleConstants.setBold(codeStyle, true);

        Pattern titlePattern    = Pattern.compile("^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?#([1-5PSps])\\.", Pattern.MULTILINE);
        Pattern listPattern     = Pattern.compile("^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?-\\.", Pattern.MULTILINE);
        Pattern listNumPattern  = Pattern.compile("^\\s*(?:" + Pattern.quote(BRAILLE) + "\\s*)?[1-9]\\d*\\.", Pattern.MULTILINE);
        Pattern tabPattern      = Pattern.compile("\\[tab\\]", Pattern.CASE_INSENSITIVE);
        Pattern specialCodes    = Pattern.compile("(__|_\\*|_\\^|\\*\\^|\\*\\*|\\^\\^|\\^¨|_¨|¨_|\\^\\*|\\^_|¨\\^|\\*_)");

        Matcher m1 = titlePattern.matcher(text);
        while (m1.find()) doc.setCharacterAttributes(m1.start(), m1.end() - m1.start(), codeStyle, false);
        Matcher m2 = listPattern.matcher(text);
        while (m2.find()) doc.setCharacterAttributes(m2.start(), m2.end() - m2.start(), codeStyle, false);
        Matcher m3 = listNumPattern.matcher(text);
        while (m3.find()) doc.setCharacterAttributes(m3.start(), m3.end() - m3.start(), codeStyle, false);
        Matcher m4 = tabPattern.matcher(text);
        while (m4.find()) doc.setCharacterAttributes(m4.start(), m4.end() - m4.start(), codeStyle, false);
        Matcher m5 = specialCodes.matcher(text);
        while (m5.find()) doc.setCharacterAttributes(m5.start(), m5.end() - m5.start(), codeStyle, false);

        // === 3. Notes de bas de page @(texte) ===
        SimpleAttributeSet noteStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(noteStyle, new Color(200, 150, 255)); // violet clair
        StyleConstants.setItalic(noteStyle, true);

        Pattern notePattern = Pattern.compile("@\\(([^)]+)\\)");
        Matcher noteMatcher = notePattern.matcher(text);
        while (noteMatcher.find()) {
            doc.setCharacterAttributes(noteMatcher.start(), noteMatcher.end() - noteMatcher.start(), noteStyle, false);
        }

        // === 4. Sauts de page ===
        Pattern sautPattern = Pattern.compile("@saut\\s+de\\s+page(\\s+manuel)?", Pattern.CASE_INSENSITIVE);
        Matcher sautMatcher = sautPattern.matcher(text);
        while (sautMatcher.find()) {
            doc.setCharacterAttributes(sautMatcher.start(), sautMatcher.end() - sautMatcher.start(), noteStyle, false);
        }

        // === 5. Liens @[Titre: URL] ===
        Pattern URL_PATTERN = Pattern.compile("@\\[([^\\]]+?):\\s*(https?://[^\\s\\]]+)\\]");
        Matcher linkMatcher = URL_PATTERN.matcher(text);

        SimpleAttributeSet prefixStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(prefixStyle, new Color(180, 180, 180));

        SimpleAttributeSet linkStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(linkStyle, new Color(80, 170, 255));
        StyleConstants.setUnderline(linkStyle, true);

        while (linkMatcher.find()) {
            int fullStart = linkMatcher.start();
            int fullEnd   = linkMatcher.end();
            int urlStart  = linkMatcher.start(2);
            int urlEnd    = linkMatcher.end(2);

            if (urlStart > fullStart)
                doc.setCharacterAttributes(fullStart, urlStart - fullStart, prefixStyle, false);
            doc.setCharacterAttributes(urlStart, urlEnd - urlStart, linkStyle, false);
            if (urlEnd < fullEnd)
                doc.setCharacterAttributes(urlEnd, fullEnd - urlEnd, normal, false);
        }

        // === 6. Images ![Image : description] ===
        Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*?):\\s*([^\\]]+)\\]");
        Matcher imgMatcher = IMAGE_PATTERN.matcher(text);

        SimpleAttributeSet imageStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(imageStyle, new Color(0, 220, 100));
        StyleConstants.setBold(imageStyle, true);

        while (imgMatcher.find()) {
            doc.setCharacterAttributes(imgMatcher.start(), imgMatcher.end() - imgMatcher.start(), imageStyle, false);
        }

        // === 7. Tables: coloriser @t, @/t, et tous les '|' non échappés dans les lignes ===
        String[] linesArr = text.split("\n", -1);
        int runningOffset = 0;
        boolean inTable = false;

        // --- créer un style spécifique pour le marqueur braille (jaune) ---
        SimpleAttributeSet brailleStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(brailleStyle, Color.YELLOW);
        StyleConstants.setBold(brailleStyle, true);

        for (int li = 0; li < linesArr.length; li++) {
            String lineTxt = linesArr[li];
            String trimmed = lineTxt.strip();

            // Si la ligne commence par le marqueur braille, calculer un trimmed2 sans le préfixe
            boolean hasBraillePrefix = false;
            String trimmedNoBraille = trimmed;
            if (trimmed.startsWith(BRAILLE)) {
                hasBraillePrefix = true;
                trimmedNoBraille = trimmed.substring(1).strip();
            }

            // Balises d'ouverture / fermeture de tableau (vérifier après suppression du braille)
            if (trimmedNoBraille.equals("@t")) {
                // colorise toute la ligne @t (inclut le braille si présent)
                doc.setCharacterAttributes(runningOffset, lineTxt.length(), codeStyle, false);
                inTable = true;
            } else if (trimmedNoBraille.equals("@/t")) {
                doc.setCharacterAttributes(runningOffset, lineTxt.length(), codeStyle, false);
                inTable = false;
            } else if (inTable) {
                // Lignes de tableau: on doit détecter '|' en tenant compte d'un éventuel préfixe braille
                int contentStartInLine = 0;
                if (trimmed.startsWith(BRAILLE)) {
                    int idx = lineTxt.indexOf(BRAILLE);
                    if (idx >= 0) {
                        contentStartInLine = idx + 1;
                        while (contentStartInLine < lineTxt.length() && Character.isWhitespace(lineTxt.charAt(contentStartInLine)))
                            contentStartInLine++;
                        // coloriser le marqueur braille LÀ avec brailleStyle (JAUNE), pas avec codeStyle
                        doc.setCharacterAttributes(runningOffset + idx, 1, brailleStyle, false);
                    }
                } else {
                    contentStartInLine = 0;
                }

                String s = lineTxt.substring(contentStartInLine);
                int start = 0;

                if (s.startsWith("|!")) {
                    doc.setCharacterAttributes(runningOffset + contentStartInLine, 2, codeStyle, false);
                    start = 2;
                } else if (s.startsWith("|")) {
                    doc.setCharacterAttributes(runningOffset + contentStartInLine, 1, codeStyle, false);
                    start = 1;
                }

                boolean esc = false;
                for (int i = start; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (esc) { esc = false; continue; }
                    if (c == '\\') { esc = true; continue; }
                    if (c == '|') {
                        doc.setCharacterAttributes(runningOffset + contentStartInLine + i, 1, codeStyle, false);
                    }
                }
            }

            runningOffset += lineTxt.length() + 1;
        }

        // === 8. Appliquer la coloration spécifique du caractère braille EN DÉBUT DE PARAGRAPHE
        //     (on applique après les autres styles pour bien écraser la couleur du seul caractère)
        Pattern braillePrefixPattern = Pattern.compile("(?m)^\\s*(" + Pattern.quote(BRAILLE) + ")");
        Matcher brailleMatcher = braillePrefixPattern.matcher(text);
        while (brailleMatcher.find()) {
            int start = brailleMatcher.start(1);
            doc.setCharacterAttributes(start, 1, brailleStyle, false);
        }

        // === 9. Rafraîchir l’affichage ===
        editor.repaint();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

}
