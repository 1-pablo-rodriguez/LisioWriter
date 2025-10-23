package xml;

import java.util.ArrayList;

public class EnsembleFichiers {
	private static ArrayList<String> ListeContentTableur = new ArrayList<String>();
	private static ArrayList<String> ListeNomDossier = new ArrayList<String>();
	private static ArrayList<String> ListeNomFichierFeedBack = new ArrayList<String>();
	private static ArrayList<String> ListeFichierods = new ArrayList<String>();
	private static ArrayList<String> ListeObjetGraphicTableur = new ArrayList<String>();
	public static int size=0;
	
	public EnsembleFichiers() {
		
	}
	
	/**
	 * 
	 */
	public void Initialise() {
		ListeNomDossier.clear();
		ListeNomFichierFeedBack.clear();
		ListeFichierods.clear();
		ListeObjetGraphicTableur.clear();
	}
	
	/**
	 * 
	 * @param NomDossier
	 * @param ContentWriter
	 * @param contentT
	 * @param contentB
	 * @param fichierodt
	 * @param fichierods
	 * @param fichierodb
	 * @param fichierObjectgraphicTableur
	 * @param fichierFeedBack
	 */
	public void AjouteEnsembleAnalyse(String NomDossier, String contentT,String fichierods, 
		String fichierObjectgraphicTableur, String fichierFeedBack) {
		ListeContentTableur.add(contentT);
		ListeNomDossier.add(NomDossier);
		ListeObjetGraphicTableur.add(fichierObjectgraphicTableur);
		ListeFichierods.add(fichierods);
		ListeNomFichierFeedBack.add(fichierFeedBack);
		size++;
	}


	public ArrayList<String> getListeNomDossier() {
		return ListeNomDossier;
	}

	public ArrayList<String> getListeFichierods() {
		return ListeFichierods;
	}


	public int getSize() {
		return EnsembleFichiers.size;
	}


	public void setListeNomDossier(ArrayList<String> listeNomDossier) {
		ListeNomDossier = listeNomDossier;
	}

	public void setListeFichierods(ArrayList<String> listeFichierods) {
		ListeFichierods = listeFichierods;
	}


	public void setSize(int size) {
		EnsembleFichiers.size = size;
	}

	public ArrayList<String> getListeObjetGraphicTableur() {
		return ListeObjetGraphicTableur;
	}

	public void setListeObjetGraphicTableur(ArrayList<String> listeObjetGraphicTableur) {
		ListeObjetGraphicTableur = listeObjetGraphicTableur;
	}

	public ArrayList<String> getListeNomFichierFeedBack() {
		return ListeNomFichierFeedBack;
	}

	public void setListeNomFichierFeedBack(ArrayList<String> listeNomFichierFeedBack) {
		ListeNomFichierFeedBack = listeNomFichierFeedBack;
	}

	public ArrayList<String> getListeContentTableur() {
		return ListeContentTableur;
	}


}
