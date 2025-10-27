package page;

import javax.swing.text.BadLocationException;

import writer.ui.EditorApi;

public class sautPage {
	private final EditorApi ctx;

	public sautPage(EditorApi ctx) {
	    this.ctx = ctx;
	}

	public void appliquer() {
		try {
			 var editor = ctx.getEditor();
			 int caretPosition = editor.getCaretPosition();
			 int line = editor.getLineOfOffset(caretPosition);
			 int lineStart = editor.getLineStartOffset(line);
			 int lineEnd = editor.getLineEndOffset(line);
			 String lineText = editor.getText(lineStart, lineEnd - lineStart);
	            
           lineText = lineText.replace("\r", "").replace("\n", "");
           if(lineText.length()==0) {
        	   editor.replaceRange("@saut de page manuel\n", lineStart, lineEnd);
           	//"Le saut de page est inséré."
           }
           
       } catch (BadLocationException e1) {
           e1.printStackTrace();
       }
	}
	
}
