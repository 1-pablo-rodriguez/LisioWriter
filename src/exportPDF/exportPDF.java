package exportPDF;

import java.io.IOException;

import writer.blindWriter;
import writer.commandes;

public class exportPDF {
	public exportPDF() {
		 try {
	            TxtToPdfConverter.convertStringToPdf(blindWriter.editorPane.getText(),commandes.nomDossierCourant+"/"+commandes.nameFile+".pdf");
	            System.out.println("✅ PDF généré !");
	            //"Le fichier a été exporté au format pé Dé éfeu. Dans le dossier " 
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	}
}
