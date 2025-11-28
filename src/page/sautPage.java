package page;

import java.util.regex.Pattern;

import javax.swing.text.BadLocationException;

import writer.ui.EditorApi;
import writer.ui.text.Lines;

public class sautPage {
	private final EditorApi ctx;
	private static final char PARAGRAPH_MARK = '\u00B6';

	private static final Pattern LINE_BLANK =
	        Pattern.compile("^" + PARAGRAPH_MARK + "\\s*$");

	public sautPage(EditorApi ctx) {
	    this.ctx = ctx;
	}

	public void appliquer() {
		try {
			 writer.ui.NormalizingTextPane editor = ctx.getEditor();
			 int caretPosition = editor.getCaretPosition();
			 int line = Lines.getLineOfOffset(ctx.getEditor(), caretPosition);
			 int lineFinale = Lines.getLineCount(ctx.getEditor())-1;
			 int lineStart = Lines.getLineStartOffset(ctx.getEditor(), line);
			 int lineEnd =  Lines.getLineEndOffset(ctx.getEditor(), line); 
			 String lineText = editor.getText(lineStart, lineEnd - lineStart);
	            
           lineText = lineText.replace("\r", "").replace("\n", "");
           
           // il faut tenir compte du caractère pied de mouche ¶ au début de paragraphe.
           if (LINE_BLANK.matcher(lineText).matches()) {
        	   if(line!=lineFinale) {
               	   Lines.replaceRange(editor, " @saut de page\n", lineStart+1, lineEnd);
        	   }else {
               	   Lines.replaceRange(editor, " @saut de page", lineStart+1, lineEnd);
        	   }
           }
           
       } catch (BadLocationException e1) {
           e1.printStackTrace();
       }
	}
	
}
