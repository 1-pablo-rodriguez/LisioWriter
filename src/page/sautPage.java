package page;

import javax.swing.text.BadLocationException;

import writer.ui.EditorApi;
import writer.ui.text.Lines;

public class sautPage {
	private final EditorApi ctx;

	public sautPage(EditorApi ctx) {
	    this.ctx = ctx;
	}

	public void appliquer() {
		try {
			 writer.ui.NormalizingTextPane editor = ctx.getEditor();
			 int caretPosition = editor.getCaretPosition();
			 int line = Lines.getLineOfOffset(ctx.getEditor(), caretPosition);
			 int lineStart = Lines.getLineStartOffset(ctx.getEditor(), line);
			 int lineEnd =  Lines.getLineEndOffset(ctx.getEditor(), line); 
			 String lineText = editor.getText(lineStart, lineEnd - lineStart);
	            
           lineText = lineText.replace("\r", "").replace("\n", "");
           
           // il faut tenir compte du caractère braille ¶ au début de paragraphe.
           if(lineText.length()==1) {
        	   Lines.replaceRange(editor, "@saut de page\n", lineStart+1, lineEnd);
           }
           
       } catch (BadLocationException e1) {
           e1.printStackTrace();
       }
	}
	
}
