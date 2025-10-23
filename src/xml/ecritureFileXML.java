package xml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;



public class ecritureFileXML {
	
	
	public enum LocationFile {
	    DansDossier,
	    UniquementFichier,
	}
	
	public enum typeFichier {
		fichier_analyse,
		verification_etudiant,
		fichier_etudiant,
	}
	
	
	
	public static boolean write(node nodeWrite, String nameFileWithExt) {
		String directoryName = Paths.get("").toAbsolutePath().toString();
		File file = new File(directoryName + "\\" + nameFileWithExt) ;

		try {
			Path outputFilePath = file.toPath();
			BufferedWriter  fichier = Files.newBufferedWriter(outputFilePath, StandardCharsets.UTF_8);
			fichier.write(nodeWrite.ecritureXML().toString());
			fichier.close();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	
	/**
	 * Ce node permet la configuration personnalisé de l'application.<br>
	 * Ajoute le node setting avec les différentes valeurs par défaut.<br>
	 *
	 * @param sujet Le node du sujet.
	 * @return Le node du sujet avec le node setting ajouté.
	 */
	public static node addSetting(node sujet) {
		//nodeAC setting
		node setting = new node();
		setting.setNameNode("settings");
		setting.getAttributs().put("culture","FR");
		
		
		//nodeAC csv
		node csv = new node();
		csv.setNameNode("csv");
		csv.getAttributs().put("encoding", "UTF-8");
		csv.getAttributs().put("separator", ";");
		csv.addContenu("choose the encoding from this list : UTF-8 US-ASCII ISO-8859-1 UTF-16BE UTF-16LE UTF-16");
		csv.setNodeClose(true);
		
		//nodeAC export du csv
		node export = new node();
		export.setNameNode("import_moodle");
		export.getAttributs().put("email", "adresse");
		export.getAttributs().put("id", "identification");
		export.getAttributs().put("firstname", "prenom");
		export.getAttributs().put("name", "nom");
		export.setNodeClose(true);
		
		//nodeAC taille zip
		node zip = new node();
		zip.setNameNode("zip");
		zip.getAttributs().put("size", "48000000");
		zip.getAttributs().put("nameZip", "feedbackMoodle");
		zip.setNodeClose(true);
		
		//nodeAC verif
		node plagiarism  = new node();
		plagiarism.setNameNode("plagiarism");
		plagiarism.getAttributs().put("number_match", "2");
		plagiarism.getAttributs().put("mini_number_modification", "-1");
		plagiarism.getAttributs().put("nombres_modifications_simultané_maxi", "100");
		plagiarism.setNodeClose(true);
		
		//construction nodeAC similitude
		node similarity = new node();
		similarity.setNameNode("text:similarity");
		similarity.getAttributs().put("tolerance_characters", "5");
		similarity.getAttributs().put("tolerance_text", "0.79");
		similarity.setNodeClose(true);
		
		//nodeAC color
		node color = new node();
		color.setNameNode("color");
		color.getAttributs().put("tolerance_rouge", "30");
		color.getAttributs().put("tolerance_vert", "30");
		color.getAttributs().put("tolerance_bleu", "30");
		color.setNodeClose(true);
		
		//construction du nodeAC setting
		csv.getEnfants().add(export);
		setting.getEnfants().add(csv);
		setting.getEnfants().add(zip);
		setting.getEnfants().add(plagiarism);
		setting.getEnfants().add(similarity);
		setting.getEnfants().add(color);
		
		//ajoute la nodeAC translation
		setting.getEnfants().add(translation());
		
		
		// ajoute le nodeAC setting au nodeAC sujet
		sujet.getEnfants().add(setting);
		
		//fermeture du nodeAC
		setting.setNodeClose(true);
		
		return sujet;
	}
	
	
	/**
	 * Retourne le node translation.<br>
	 * @return Le node translation.
	 */
	public static node translation() {
		node translation = new node();
		translation.setNameNode("translation");
		translation.getAttributs().put("class", "tooltip1");
		translation.getAttributs().put("classtext", "tooltiptext1");
		
		//les champs
		translation.getEnfants().add(nodeTranslation("text:initial-creator..name","La valeur du champ premier auteur","", "#111166","",false));
		translation.getEnfants().add(nodeTranslation("dc:subject..texte","Valeur de la méta donnée -!b!-Sujet-!/b!-","Menu Fichier/Propriétés-!br!-Onglet Description","#111166","",false));
		translation.getEnfants().add(nodeTranslation("dc:title..texte","Valeur de la méta donnée -!b!-Titre-!/b!-","Menu Fichier/Propriétés-!br!-Onglet Description", "#111166","",false));
		translation.getEnfants().add(nodeTranslation("text:title..name","Valeur de la méta donnée -!b!-Titre-!/b!-","Menu Fichier/Propriétés-!br!-Onglet Description", "#111166","",false));	
		translation.getEnfants().add(nodeTranslation("text:editing-cycles..name","Valeur du champ révision","", "#111166","",false));	
		translation.getEnfants().add(nodeTranslation("text:creator..name","Auteur des modifications","", "#111166","",false));	
		translation.getEnfants().add(nodeTranslation("meta:initial-creator..texte","Premier auteur du fichier","", "#111166","",false));
		translation.getEnfants().add(nodeTranslation("meta:user-defined..Auteur2","La méta donnée -!b!-Auteur2-!/b!-","Menu Fichier/Propriétés-!br!-Onglet Propriétés personnalisées-!br!-Cliquez sur le bouton &quot;Ajouter une propriété&quot; pour ajouter une méta données.-!br!--!br!--!b!--!u!-ATTENTION-!/u!--!/b!-: Vous devez tapez comme nom de la méta donnée -!b!-Auteur2-!b!--!br!-Exactement ces caractères, ne tapez pas d'espace après le dernier caractère.-!br!-Sinon vous aurez une valeur -!b!-null-!/b!-.",  "#111166","",true));
		translation.getEnfants().add(nodeTranslation("meta:user-defined..Date..du..contrôle","La méta donnée -!b!-Date du contrôle-!/b!-","Menu Fichier/Propriétés-!br!-Onglet Propriétés personnalisées-!br!-Clique sur le bouton &quot;Ajouter une propriété&quot; pour ajouter une méta données.-!br!--!br!--!b!--!u!-ATTENTION-!/u!--!/b!-: Vous devez tapez comme nom de la méta donnée -!b!-Date du contrôle-!b!--!br!-Exactement ces caractères, ne tapez pas d'espace après le dernier caractère.-!br!-Sinon vous aurez une valeur -!b!-null-!/b!-.",  "#111166","",true));
		translation.getEnfants().add(nodeTranslation("meta:user-defined..Département","La méta donnée -!b!-Département-!/b!-","Menu Fichier/Propriétés-!br!-Onglet Propriétés personnalisées-!br!-Cliquez sur le bouton &quot;Ajouter une propriété&quot; pour ajouter une méta données.-!br!--!br!--!b!--!u!-ATTENTION-!/u!--!/b!-: Vous devez tapez comme nom de la méta donnée -!b!-Département-!b!--!br!-Exactement ces caractères, ne tapez pas d'espace après le dernier caractère.-!br!-Sinon vous aurez une valeur -!b!-null-!/b!-.",  "#111166","",true));
		translation.getEnfants().add(nodeTranslation("meta:user-defined..Groupe","La méta donnée -!b!-Groupe-!/b!-","Menu Fichier/Propriétés-!br!-Onglet Propriétés personnalisées-!br!-Clique sur le bouton &quot;Ajouter une propriété&quot; pour ajouter une méta données.-!br!--!br!--!b!--!u!-ATTENTION-!/u!--!/b!-: Vous devez tapez comme nom de la méta donnée -!b!-Groupe-!b!--!br!-Exactement ces caractères, ne tapez pas d'espace après le dernier caractère.-!br!-Sinon vous aurez une valeur -!b!-null-!/b!-.",  "#111166","",true));
		translation.getEnfants().add(nodeTranslation("text:user-defined..text:name","La méta donnée personnalisée","Menu Fichier/Propriétés-!br!-Onglet Propriétés personnalisées-!br!-Clique sur le bouton &quot;Ajouter une propriété&quot; pour ajouter une méta données.", "#111166","",true));
		translation.getEnfants().add(nodeTranslation("text:initial-creator..name","La valeur du champ premier auteur","", "#111166","",false));
		translation.getEnfants().add(nodeTranslation("text:creation-date..Nom..du..nodeAC","Champ date de création","", "#111166","",false));	
		translation.getEnfants().add(nodeTranslation("text:creator..Nom..du..nodeAC","Champ auteur","", "#111166","",false));
		translation.getEnfants().add(nodeTranslation("text:date..Nom..du..nodeAC","Champ date de modification","", "#111166","",false));	
		translation.getEnfants().add(nodeTranslation("dc:subject..Contenu..textuel","Champ sujet","", "#111166","",false));	
		translation.getEnfants().add(nodeTranslation("dc:title..Contenu..textuel","Champ titre","", "#111166","",false));	
		translation.getEnfants().add(nodeTranslation("meta:user-defined..meta:name","Propriété personnalisée","", "#111166","",false));	
		
		
		
		
		//style de paragraphe
		translation.getEnfants().add(nodeTranslation("style:style..style:master-page-name","Enchaînement insère saut de page","Le style de paragraphe doit insérer un saut de page.", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:style..style:next-style-name","Style du paragraphe suivant","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:border-bottom","Bordure basse du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:border-top","Bordure haute du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:border-left","Bordure gauche du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:border-right","Bordure droite du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:keep-with-next","Conserver avec le paragraphe suivant","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:keep-together","Ne pas scinder le paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:margin-top","Espacement au dessus du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:margin-bottom","Espacement en dessous du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:orphans","Nombre de ligne d'orpheline","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:widows","Nombre de ligne de veuve","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..style:text-underline-color","Couleur de soulignement du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..style:text-underline-style","Style du soulignement du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..style:text-underline-width","Epaisseur du trait de soulignement du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..style:font-name","Police de caractère du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..fo:font-size","Taille de la police de caractère du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..style:font-style-name","Style de la police de caractère du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..fo:text-shadow","Effet de caractère ombré du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:text-align","Alignement du paragraphe","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..fo:font-variant","Effet de caractère petite majuscule","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:line-height","Interligne","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:text-indent","Retrait de première ligne","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:style..style:parent-style-name","Hérite du style","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:text-indent","Retrait de première ligne","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:padding-top","Remplissage (espacement) en haut","Les padding sont des marges intérieures.", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:padding-left","Remplissage (espacement) à gauche","Les padding sont des marges intérieures.", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:padding-right","Remplissage (espacement) à droite","Les padding sont des marges intérieures.", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:padding-bottom","Remplissage (espacement) en bas","Les padding sont des marges intérieures.", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..style:text-underline-type","Style du trait de soulignage","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..style:tab-stop-distance","Distance du stop de la tabulation","", "#118811","https://moodle.univ-artois.fr/cours/",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..fo:font-weight","Style texte GRAS","", "#000000","",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..fo:font-style","Style texte italic","", "#000000","",false));
		
		//style de page
		translation.getEnfants().add(nodeTranslation("style:master-page..style:name","Nom du style de page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:style..style:name","Nom du style de page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout..style:page-usage","Mise en page de la page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout-properties..fo:page-width","Largeur de la page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout-properties..fo:page-height","Hauteur de la page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout-properties..fo:margin-right","Marge à droite de la page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout-properties..fo:border","Les 4 bordures de la page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout-properties..fo:margin-left","Marge à gauche de la page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout-properties..fo:margin-bottom","Marge en bas de la page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout-properties..fo:margin-top","Marge en haut de la page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout-properties..fo:margin-right","Marge à droite de la page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout-properties..fo:padding","Remplissage (marges intéreures)","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("text:chapter..text:display","Champ chapitre","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("text:chapter..Contenu..textuel","Valeur du champ chapitre","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:border-bottom","-!b!-Entête ou Pied de page-!/b!- : Bordure inférieure","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:border-right","-!b!-Entête ou Pied de page-!/b!- : Bordure droite","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:border-left","-!b!-Entête ou Pied de page-!/b!- : Bordure gauche","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:border-top","-!b!-Entête ou Pied de page-!/b!- : Bordure haute","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:margin-bottom","-!b!-Entête ou Pied de page-!/b!- : Marge en dessous","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:margin-top","-!b!-Entête ou Pied de page-!/b!- : Marge au dessus","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:margin-top","-!b!-Entête ou Pied de page-!/b!- : Marge au dessus","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:margin-right","-!b!-Entête ou Pied de page-!/b!- : Marge à droite","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:margin-left","-!b!-Entête ou Pied de page-!/b!- : Marge à gauche","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:master-page..style:next-style-name","Style de la page suivante","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("text:bookmark-ref..text:ref-name","Nom du repère de texte","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("text:bookmark-ref..text:reference-format","Réfèrence du repère de texte","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("text:bookmark-ref..Contenu..textuel","Contenu textuel du repère de texte","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:columns..fo:column-count","Nombre de colonne","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:columns..fo:column-gap","Espacement entre les colonnes-!br!--!i!-Gouttière-!/i!-","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:border","Les 4 bordures du pied de page-!br!--!i!-droite gauche bas haut-!/i!-","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:border","Les 4 bordures de l'entête-!br!--!i!-droite gauche bas haut-!/i!-","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("text:tab..name","Tabulation","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("text:tab..Contenu..textuel","Contenu textuel après tabulation","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("text:initial-creator..Contenu..textuel","Champ auteur (premier auteur)","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("text:creator..Contenu..textuel","Champ auteur (modifié)","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout-properties..style:num-format","Format de la numérotation","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("text:page-number..text:select-page","Champ numérotation de la page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:page-layout-properties..style:print-orientation","Orientation de la page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("style:header-footer-properties..fo:min-height","Hauteur minimal du pied de page","", "#995511","",false));
		translation.getEnfants().add(nodeTranslation("page..style:page_number","Champ - numéro de la page","", "#000000","",false));
		
			
		//style structure
		translation.getEnfants().add(nodeTranslation("page..style:master-page-name","Nom du style de page","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("page..numeroabsolue","Position (numéro) absolue de la page-!br!-par rapport à l'ensemble des pages","C'est l'ordre d'apparition de la page lorsque le mode &quot;-!b!-Livre-!/b!-&quot; est utilisé.-!br!-Dans le mode d'affichage &quot;-!b!-Livre-!/b!-&quot; toutes les pages s'affichent, y compris les pages vides.", "#903BA9","",true));
		translation.getEnfants().add(nodeTranslation("text:title..Contenu..textuel","Valeur de la méta donnée &quot;-!b!-Titre-!/b!-&quot;","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:subject..Contenu..textuel","Valeur de la méta donnée &quot;-!b!-Sujet-!/b!-&quot;","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("draw:frame..draw:name","Nom de l'objet indiqué dans-!br!-l'onglet &quot;-!b!-Options-!/b!-&quot;-!br!-de la boite &quot;-!b!--!u!-Propriétés-!/u!--!/b!-&quot; de l'objet.","Si l'objet ne se nomme pas -!b!--!u!-EXACTEMENT-!/u!--!/b!- comme indiqué dans la consigne.-!br!--!br!-L'algorithme d'analyse ne pourra pas trouver l'objet.Vous aurez que des valeurs -!b!--!u!-NULL-!/u!--!/b!--!br!--!br!-Faites attention à la case (majuscule et minuscule). Ne tapez pas d'espace après le dernier caractère. Ne tapez pas de guillemet, etc.", "#903BA9","",true));
		translation.getEnfants().add(nodeTranslation("text:description..Contenu..textuel","Champ -!b!-Commentaires-!/b!-","Pour insérer le champ &quot;-!b!-Commentaires-!/b!-&quot;.-!br!-Sélectionner le menu Insertion/Champ/Autres champs...-!br!--!br!-Dans la boite de dialogue &quot;Champ&quot;-!br!-Onglet &quot;Info document&quot;", "#903BA9","",true));
		translation.getEnfants().add(nodeTranslation("page..style:page-usage","Mise en page de la page","Dans les -!b!-Propriétés-!/b!- du style de page-!br!-Onglet -!b!-Page-!/b!--!br!--!b!-Mise en page-!/b!-", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:illustration-index-source..text:caption-sequence-name","Catégorie de la légende","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:index-title-template..Contenu..textuel","Titre de l'index","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:table-of-content..text:protected","Protection de l'index","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:table-of-content-source..text:outline-level","Niveau de plan de l'index-!br!-Type &quot;Table des matières&quot;", "", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:conditional-text..text:condition","La condition du texte conditionnel","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:conditional-text..text:string-value-if-true","Si la condition est -!b!-Vrai-!/b!- affiche le texte","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:conditional-text..text:string-value-if-false","Si la condition est -!b!-Fausse-!/b!- affiche le texte","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:date..text:fixed","La date est fixe","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:date..style:data-style-name","Style de la date","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:span..Contenu..textuel","Contenu textuel formatage local","-!b!--!u!-Attention formatage local:-!/u!--!/b!--!br!-Si le texte n'a pas été trouvé.-!br!-Vous devez savoir que l'application recherche le texte par son contenu.-!br!-Mais aussi par le type d'élément, ici un formatage local.-!br!-Il est probable que tous les autres attributs (propriétés) de ce formatage local soient &quot;null&quot;.-!br!-Vérifier le contenu textuel, ou vérifier que vous avez formaté localement ce text.-!br!-N'oubliez pas d'effacer le format avant de reformater le texte.", "#000000","",true));
		translation.getEnfants().add(nodeTranslation("text:p..Contenu..textuel","Contenu textuel (paragraphe)","-!b!--!u!-Attention paragraphe de texte:-!/u!--!/b!--!br!-Si le texte de ce paragraphe n'a pas été trouvé.-!br!-Vous devez savoir que l'application recherche le paragraphe par son contenu.-!br!-Mais aussi par le type de l'élément, ici un paragraphe.-!br!-Il est probable que tous les autres attributs (propriétés) de ce paragraphe soient &quot;null&quot;.-!br!-Vérifier le contenu textuel de votre paragraphe, ou vérifier que c'est bien un paragraphe.-!br!-", "#000000","",true));
		translation.getEnfants().add(nodeTranslation("text:date..text:date-value","La date","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:section..text:name","Nom de la section","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:section..text:condition","Condition de la section","-!b!--!u!-Attention :-!/u!--!/b!--!br!-Ne tapez pas d'espace après le dernier guillemet du texte.-!br!-Ne tapez pas d'espace après le dernier caractère de votre condition.", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:section..Contenu..textuel","Contenu textuel de la section","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:section..text:display","Masqué la section","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:break-before","Type de saut placé avant","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:padding","Remplissage (padding) du paragraphe","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("style:text-properties..fo:color","Couleur de la police","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:h..text:style-name","Nom du style du paragraphe Titre","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("text:change-start..Contenu..textuel","Insertion du texte","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:margin-right","Retrait après le paragraphe","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:margin-left","Retrait avant le paragraphe","", "#903BA9","",false));
		translation.getEnfants().add(nodeTranslation("style:paragraph-properties..fo:border","Style des quatre bordures","", "#903BA9","",false));
		
		
		//frame
		translation.getEnfants().add(nodeTranslation("draw:frame..text:anchor-type","Ancrage de l'objet","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("draw:frame..text:anchor-page-number","Ancrage dans la page numéro","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("draw:frame..svg:y","Position (distance) verticale-!br!-de l'objet","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("draw:frame..svg:x","Position (distance) horizontale-!br!-de l'objet","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("draw:frame..svg:height","Hauteur de l'objet","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("draw:frame..svg:width","Largeur de l'objet","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..fo:padding","Remplissage (marge)-!br!-avec les bords du cadre de l'objet","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..style:vertical-pos","Position verticale de l'objet par rapport à","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..style:horizontal-pos","Position horizontale de l'objet par rapport à","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..fo:border","Les 4 bordures de l'objet","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..fo:margin-bottom","Espacement en dessous de l'objet","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..fo:margin-top","Espacement au dessus de l'objet","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..fo:margin-right","Espacement à droite de l'objet","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..fo:margin-left","Espacement à gauche de l'objet","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..style:horizontal-rel","Position horizontale par rapport à","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..style:vertical-rel","Position verticale par rapport à","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..style:wrap","Adaptation du texte","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..style:number-wrapped-paragraphs","Adaptation du texte-!br!-nombre de paragraphe adapté","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..style:wrap-contour","Adaptation du texte &quot;-!b!-Contour-!/b!-&quot;","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("text:sequence..text:name","Nom de la variable de -!b!-Séquence-!/b!- pour légender","Menu Insertion/Champ/Autres champs...-!br!-Onglet &quot;-!b!-Variables-!/b!-&quot;", "#FF5B00","",true));
		translation.getEnfants().add(nodeTranslation("text:sequence..Contenu..textuel","La légende avec la variable de -!b!-Séquence-!/b!-","Pour légender une image, il faut un clic droite sur l'image et sélectionner -!b!-Insérer une légende...-!/b!--!br!-Cependant, il faut retirer la protection du contenu.", "#FF5B00","",true));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..style:protect","Protection de l'objet","Dans la boite de dialogue -!b!-Propriétés-!/b!--!br!-Onglet Option-!br!-Vou devez cocher les protections (case à cocher)", "#FF5B00","",true));
		translation.getEnfants().add(nodeTranslation("style:graphic-properties..style:wrap-contour-mode","Mode contour du texte","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("draw:text-box..fo:min-height","Hauteur du frame-!br!-(cadre de texte)","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("draw:frame..Contenu..textuel","Paragraphe d'ancrage","", "#FF5B00","",false));
		
		//numérotation hiérarchisée
		translation.getEnfants().add(nodeTranslation("text:outline-level-style..style:num-suffix","Suffix (après la numérotation)","", "#C1BA00","",false));
		translation.getEnfants().add(nodeTranslation("text:outline-level-style..style:num-prefix","Prefix (devant la numérotation)","-!b!--!u!-Attention :-!/u!--!/b!--!br!-Il peut y avoir devant la numération un espace.-!br!--!br!-Par exemple : -!b!-§[espace]-!/b!-", "#C1BA00","",true));
		translation.getEnfants().add(nodeTranslation("text:outline-level-style..style:num-format","Format de la numérotation","", "#C1BA00","",false));
		translation.getEnfants().add(nodeTranslation("text:outline-level-style..text:level","Niveau de la numérotation","", "#C1BA00","",false));
		translation.getEnfants().add(nodeTranslation("style:list-level-properties..text:list-level-position-and-space-mode","Position, Espacement de la numérotation","", "#C1BA00","",false));
		translation.getEnfants().add(nodeTranslation("style:list-level-label-alignment..text:label-followed-by","Numérotation suivi d'un(e)","Dans la boite de dialogue &quot;Numérotation des chapitres&quot;-!br!-Onglet Position-!br!-Numerotation suivi par.", "#C1BA00","",true));
		translation.getEnfants().add(nodeTranslation("text:outline-level-style..text:display-levels","Nombre de niveau affiché par la numérotation","Dans la boite de dialogue &quot;Numérotation des chapitre&quot;-!br!-Afficher les sous-niveaux.", "#C1BA00","",true));
		
		//Table, index, bibliographie
		translation.getEnfants().add(nodeTranslation("text:a..Contenu..textuel","Le texte du lien","Le texte du lien qui permet d'atteindre le paragraphe.", "#0000FF","",true));
		translation.getEnfants().add(nodeTranslation("text:bibliography..Contenu..textuel","Tout le contenu textuel de la biliographie","", "#0000FF","",false));
		translation.getEnfants().add(nodeTranslation("text:index-body..Contenu..textuel","Tout le contenu textuel de l'index","", "#0000FF","",false));
		translation.getEnfants().add(nodeTranslation("text:table-of-content..Contenu..textuel","Tout le contenu textuel de la table","", "#0000FF","",false));
		translation.getEnfants().add(nodeTranslation("text:illustration-index..Contenu..textuel","Tout le contenu textuel de la table des figures","", "#0000FF","",false));
		
		//Les tableaux
		translation.getEnfants().add(nodeTranslation("table:table-row..Contenu..textuel","Tout le contenu textuel d'un ligne de la table","", "#0000FF","",false));
		translation.getEnfants().add(nodeTranslation("table:table-cell..Contenu..textuel","Tout le contenu textuel d'une cellule de la table","", "#0000FF","",false));
		
		//database
		translation.getEnfants().add(nodeTranslation("text:database-display..name","Base de données","", "#0000FF","",false));
		translation.getEnfants().add(nodeTranslation("text:database-display..text:table-type","Type de la source de données","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("text:database-display..text:column-name","Nom du champ de données","Ne tapez pas d'espace à la fin du nom de la colonne, Sinon Null.", "#FF5B00","",true));
		translation.getEnfants().add(nodeTranslation("text:database-display..text:database-name","Nom de la base de données","", "#FF5B00","",false));
		translation.getEnfants().add(nodeTranslation("text:database-display..text:table-name","Nom de la table de données","", "#FF5B00","",false));
		
		
		//variable de séquence
		translation.getEnfants().add(nodeTranslation("text:sequence-decl..text:name","Variable de séquence","La variable de séquence permet de légender et de créer des index.-!br!-Pour ajouter une variable de séquence, vous devez sélectionner le menu-!br!-Insertion/Champ/Autres champs...-!br!-Onglet &quot;-!b!-Variables-!/b!-&quot;.", "#C1BA00","",true));
		
		//Retour à la ligne
		translation.getEnfants().add(nodeTranslation("text:line-break..name","Retour à la ligne" ,"Retour à la ligne-!br!-Un retour à la ligne est différent d'un paragraphe.-!br!-Pour réaliser un retour à la ligne vous devez taper-!br!--!b!-SHIFT (majuscule) + ENTRÉE-!/b!-","#000000","",true));
		translation.getEnfants().add(nodeTranslation("text:line-break..Contenu..textuel","Contenu textuel dans un retour à la ligne," ,"-!b!--!u!-Attention retour à la ligne:-!/u!--!/b!--!br!-Si le texte n'a pas été trouvé.-!br!-Vous devez savoir que l'application recherche le texte par son contenu.-!br!-Mais aussi par le type de l'élément, ici un retour à la ligne.-!br!-Il est probable que tous les autres attributs (propriétés) de ce paragraphe soient &quot;null&quot;.-!br!-Vérifier le contenu textuel de votre paragraphe, ou vérifier que c'est bien un retour à la ligne.-!br!-", "#000000","",true));
		translation.getEnfants().add(nodeTranslation("text:line-break..Nom..du..nodeAC","Retour à la ligne" ,"Retour à la ligne-!br!-Un retour à la ligne est différent d'un paragraphe.-!br!-Pour réaliser un retour à la ligne vous devez taper-!br!--!b!-SHIFT (majuscule) + ENTRÉE-!/b!-","#000000","",true));
		
		// tabulation
		translation.getEnfants().add(nodeTranslation("text:tab..Nom..du..nodeAC","Tabulation" ,"Insertion d'un caractère tabulation","#000000","",true));
		
		// numérotation des pages
		translation.getEnfants().add(nodeTranslation("text:page-number..Nom..du..nodeAC","Numéro de page" ,"Insertion du champ numéro de page","#000000","",true));
		translation.getEnfants().add(nodeTranslation("text:page-count..Nom..du..nodeAC","Nombre de page" ,"Insertion du champ nombre de page","#000000","",true));
		
		//Paragraphe de texte
		translation.getEnfants().add(nodeTranslation("text:p..name","Paragraphe de texte" ,"Paragraphe de texte-!br!-Un paragraphe de texte est créé avec la touche-!br!--!b!-ENTRÉE-!/b!-","#000000","",true));
		translation.getEnfants().add(nodeTranslation("txt:p..Contenu..textuel","Contenu textuel dans un paragraphe de texte" ,"Paragraphe de texte-!br!-Un paragraphe de texte est créé avec la touche-!br!--!b!-ENTRÉE-!/b!-","#000000","",true));
		translation.getEnfants().add(nodeTranslation("text:span..name","Texte formatage direct" ,"Le formatage direct des cacartères-!br!-Lorsque l'on modifie localement les attributs d'un texte (Gras, italic, taille, soulignage, etc.)","#000000","",true));
		translation.getEnfants().add(nodeTranslation("text:span..Nom..du..nodeAC","Texte formatage direct des caractères" ,"Le formatage direct-!br!-Lorsque l'on modifie localement les attributs d'un texte (Gras, italic, taille, soulignage, etc.)","#000000","",true));
		
		//Espace
		translation.getEnfants().add(nodeTranslation("text:s","Espace LO Writer","Un espace est inséré lorsque vous souhaitez un séparateur de mots à un endroit où un saut de ligne est acceptable.", "#000000","",true));
		translation.getEnfants().add(nodeTranslation("text:s..name","Espace LO Writer","Un espace est inséré lorsque vous souhaitez un séparateur de mots à un endroit où un saut de ligne est acceptable.", "#000000","",true));
		translation.getEnfants().add(nodeTranslation("text:s..Contenu..textuel","Espace suivi du texte","Un espace est inséré lorsque vous souhaitez un séparateur de mots à un endroit où un saut de ligne est acceptable.", "#000000","",true));
		
		
		return translation;
	}
	
	
	/**
	 * Permet de réaliser les nodes de traduction.<br>
	 * 
	 * @param nameNode Le nom du nodeAC.
	 * @param traduction La traduction qui s'affiche dans le feedback.
	 * @param commentaire Le commentaire qui apparaît.
	 * @return Le nodeAC traduction.
	 */
	private static node nodeTranslation(String nameNode, String traduction, String commentaire, String color, String link, Boolean T) {
		node A = new node();
		A.setNameNode(nameNode);
		A.getAttributs().put("translate", traduction);
		A.addContenu(commentaire);
		A.getAttributs().put("color", color);
		if(T.equals(true)) A.getAttributs().put("image", "true");
		if(!link.isBlank()) A.getAttributs().put("link", link);
		A.setNodeClose(true);
		return A;
	}
	
	
	
}
