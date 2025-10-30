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
			 var editor = ctx.getEditor();
			 int caretPosition = editor.getCaretPosition();
			 int line = Lines.getLineOfOffset(ctx.getEditor(), caretPosition);
			 int lineStart = Lines.getLineStartOffset(ctx.getEditor(), line);
			 int lineEnd =  Lines.getLineEndOffset(ctx.getEditor(), lineStart); 
			 String lineText = editor.getText(lineStart, lineEnd - lineStart);
	            
           lineText = lineText.replace("\r", "").replace("\n", "");
           if(lineText.length()==0) {
        	   Lines.replaceRange(editor, "@saut de page manuel\n", lineStart, lineEnd);
           }
           
       } catch (BadLocationException e1) {
           e1.printStackTrace();
       }
	}
	
}
