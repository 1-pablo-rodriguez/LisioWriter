package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import dia.VerifDialog;
import writer.ui.EditorApi;

@SuppressWarnings("serial")
public class actCheckDoc extends AbstractAction{
	
	private final EditorApi ctx;

	// Constructeur
	public actCheckDoc(EditorApi ctx) {
	    super("actCheckDoc");
	    this.ctx = ctx;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		writer.spell.SpellCheckLT spell = ctx.getSpell();
		    if (spell == null) return;
		    java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(ctx.getEditor());
		    VerifDialog dlg = VerifDialog.showNow(owner, "Vérif. doc. en cours…");
		    
		    //announceCaretLine(false, true, "Vérif. document en cours.");
		    spell.checkDocumentNowAsync(() -> {
		      //announceCaretLine(false, true,"Vérif. document terminée. " + spell.getMatchesCount() +" éléments détectés. F7 et Maj+F7 pour naviguer.");
		    	dlg.close();
		    	
		    	int n = spell.getMatchesCount();
		       String msg = (n == 0) ? "Aucune faute détectée."
		                  : (n == 1) ? "1 élément détecté."
		                             : n + " éléments détectés.";
		       msg += "\n Astuce : F7 et Maj+F7 pour naviguer entre les éléments.";
		       msg += "\n Astuce : Maj+F10 contextuel pour : ";
		       msg += "\n  (1) Obtenir une ou des suggestions ;";
		       msg += "\n  (2) Ajouter au dictionnaire ou ignoré ;";
		       msg += "\n  (3) Échappe pour sortir du menu contextuel.";
		       
		       // Boîte modale, lisible par la barre braille, fermeture avec Échap
//		       dia.InfoDialog.show(owner, "Vérification document terminée", msg, ctx.getEditor());
		       
		       ctx.showInfo("Vérification document terminée", msg);
		       ctx.getEditor().requestFocusInWindow();
		
		    });
	}
	
}
