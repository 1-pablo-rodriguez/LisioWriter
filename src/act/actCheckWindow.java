package act;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import dia.VerifDialog;
import writer.ui.EditorApi;

@SuppressWarnings("serial")
public class actCheckWindow extends AbstractAction{

	private final EditorApi ctx;

	// Constructeur
	public actCheckWindow(EditorApi ctx) {
	    super("actCheckWindow");
	    this.ctx = ctx;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		writer.spell.SpellCheckLT spell = ctx.getSpell();
		    if (spell == null) return;
		   java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(ctx.getEditor());
		    dia.VerifDialog dlg = VerifDialog.showNow(owner, "Vérif. para. en cours…");
		    //announceCaretLine(false, true, "Vérif. paragraphe. Utilisez F7 et Maj+F7 pour naviguer.");
		    spell.checkSelectionOrParagraphNowAsync(() -> {
		      //announceCaretLine(false, true,"Vérif. paragraphe terminée. " + spell.getMatchesCount() +" éléments détectés. F7 et Maj+F7 pour naviguer.");
		    	dlg.close();
		    	
		    	int n = spell.getMatchesCount();
		    	String msg = (n == 0) ? "Dans le paragraphe aucune faute détectée."
		                  : (n == 1) ? "Dans le paragraphe 1 élément détecté."
		                             : "Dans le paragraphe " + n + " éléments détectés.";
		       msg += "\n Les éléments sont signalés par le préfix °°.";
		       msg += "\n Astuce : F7 et Maj+F7 pour naviguer entre les éléments.";
		       msg += "\n Astuce : Maj+F10 contextuel pour : ";
		       msg += "\n  (1) Obtenir une ou des suggestions ;";
		       msg += "\n  (2) Ajouter au dictionnaire ou ignoré ;";
		       msg += "\n  (3) Échappe pour sortir du menu contextuel.";

		       // Boîte modale, lisible par la barre braille, fermeture avec Échap
		       dia.InfoDialog.show(owner, "Vérification paragraphe terminée", msg);
		       
		    	ctx.getEditor().requestFocusInWindow();
		    });
		
	}

}
