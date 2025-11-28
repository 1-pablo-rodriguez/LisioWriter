package exportPDF;

import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public class TxtToPdfConverter {

    public static void convertStringToPdf(String content, String pdfPath) throws IOException {
        
    	File f = new File(pdfPath);
    	if (f.exists() && !f.delete()) {
    	    System.err.println("⚠️ Impossible d'écraser le fichier. Fermez-le dans Acrobat ou un navigateur.");
    	    return;
    	}
    	
    	
    	try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            float leading = 14.5f;

            contentStream.setFont(PDType1Font.HELVETICA, 12);
            contentStream.beginText();
            contentStream.newLineAtOffset(margin, y);

            for (String line : content.split("\n")) {
            	
            	if (line.trim().equals("@saut de page")) {
            	    contentStream.endText();
            	    contentStream.close();

            	    page = new PDPage(PDRectangle.A4);
            	    document.addPage(page);
            	    contentStream = new PDPageContentStream(document, page);

            	    y = page.getMediaBox().getHeight() - margin;

            	    contentStream.setFont(PDType1Font.HELVETICA, 12);
            	    contentStream.beginText();
            	    contentStream.newLineAtOffset(margin, y);
            	    continue; // ignorer cette ligne
            	}
            	

                if (y <= margin) {
                    contentStream.endText();
                    contentStream.close();

                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - margin;

                    contentStream.setFont(PDType1Font.HELVETICA, 12);
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, y);
                }

                if (line.startsWith("#P. ")) {
                    contentStream.endText();
                    contentStream.setFont(PDType1Font.TIMES_BOLD, 22);
                    contentStream.beginText();
                    float centerX = (page.getMediaBox().getWidth() - PDType1Font.TIMES_BOLD.getStringWidth(line.substring(4)) / 1000 * 22) / 2;
                    contentStream.newLineAtOffset(centerX, y);
                    contentStream.showText(line.substring(4));
                    contentStream.endText();
                    contentStream.setFont(PDType1Font.HELVETICA, 12);
                    y -= leading * 2;
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, y);
                    continue;
                }

                if (line.startsWith("#S. ")) {
                    contentStream.endText();
                    contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 16);
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, y);
                    contentStream.showText(line.substring(4));
                    contentStream.endText();
                    contentStream.setFont(PDType1Font.HELVETICA, 12);
                    y -= leading * 1.5;
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, y);
                    continue;
                }

                if (line.startsWith("-. ")) {
                	contentStream.showText("    - " + line.substring(3)); // 4 espaces au lieu de tab + puce
                    contentStream.newLineAtOffset(0, -leading);
                    y -= leading;
                    continue;
                }

                int titleLevel = getTitleLevel(line);
                if (titleLevel > 0) {
                    contentStream.endText();

                    float fontSize = switch (titleLevel) {
                        case 1 -> 18;
                        case 2 -> 16;
                        case 3 -> 14;
                        default -> 12;
                    };

                    String cleanedLine = line.replaceFirst("^#\\d\\.\\s*", "");
                    contentStream.setFont(PDType1Font.TIMES_BOLD, fontSize);
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, y);
                    contentStream.showText(cleanedLine);
                    contentStream.endText();

                    y -= leading * 2;
                    contentStream.setFont(PDType1Font.HELVETICA, 12);
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, y);
                } else {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -leading);
                    y -= leading;
                }
            }

            contentStream.endText();
            contentStream.close();
            document.save(pdfPath);
        }
    }

    private static int getTitleLevel(String line) {
        for (int i = 1; i <= 6; i++) {
            if (line.startsWith("#" + i + ". ")) {
                return i;
            }
        }
        return 0;
    }
}
