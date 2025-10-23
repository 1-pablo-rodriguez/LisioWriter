package page;

import javax.swing.text.BadLocationException;

import writer.blindWriter;

public class sautPage {

	public sautPage() {
		try {
			 // Obtenez la position du curseur
           int caretPosition = blindWriter.editorPane.getCaretPosition();
			 // Trouvez la ligne actuelle
           int line = blindWriter.editorPane.getLineOfOffset(caretPosition);

           // Obtenez les offsets de début et de fin de la ligne
           int lineStart = blindWriter.editorPane.getLineStartOffset(line);
           int lineEnd = blindWriter.editorPane.getLineEndOffset(line);
           
           // Extraire le texte de la ligne
           String lineText = blindWriter.editorPane.getText(lineStart, lineEnd - lineStart);
           lineText = lineText.replace("\r", "").replace("\n", "");
           if(lineText.length()==0) {
           	blindWriter.editorPane.replaceRange("@saut de page manuel\n", lineStart, lineEnd);
           	//"Le saut de page est inséré."
           }
           
       } catch (BadLocationException e1) {
           e1.printStackTrace();
       }
	}
	
}
