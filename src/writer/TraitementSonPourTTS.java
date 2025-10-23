package writer;
import java.util.LinkedHashMap;
import java.util.Map;

public class TraitementSonPourTTS {
    public String returnTexte = "";

    public TraitementSonPourTTS(String texte) {
        texte = texte.toLowerCase().trim();
        texte = texte.replaceAll("\\b([0-9]{1,})-([0-9]{1,})\\b", " $1 - $2 ");
        	
        // Map pour stocker les remplacements
        Map<String, String> replacements = new LinkedHashMap<>();
        
        // Remplacements spécifiques avec expressions régulières (regex)
        replacements.put("#p.", "Titre principal du document. ");
        replacements.put("^#s\\.", "sous-titre . ");
        replacements.put("^#1\\.", "Titre 1. ");
        replacements.put("^#2\\.", "Titre 2. ");
        replacements.put("^#3\\.", "Titre 3. ");
        replacements.put("^#4\\.", "Titre 4. ");
        replacements.put("^#5\\.", "Titre 5. ");
        replacements.put("^([0-9]{1,})\\.", "Liste numérotée $1. ");
        replacements.put("^-\\.[^\\.]", "Liste niveau 1. ");
        replacements.put("^-\\.\\.[^\\.]", "Liste niveau 2. ");
        replacements.put("^-\\.\\.\\.[^\\.]", "Liste niveau 3. ");
        replacements.put("^-\\.\\.\\.\\.[^\\.]", "Liste niveau 4. ");
        replacements.put("@\\(", " note de bas de page. ");
        
        // Remplacements simples
        replacements.put("blindwriter", "blaïdevraïteur");
        replacements.put("ctrl", " controle ");
        replacements.put("shift", " chiffeteu ");
        replacements.put("&", " et ");
        replacements.put("\\(", " parenthèse ouverte ");
        replacements.put("\\)", " parenthèse fermée ");
        replacements.put("_", " tiret du bas ");
        replacements.put("#", " dièse ");
        replacements.put("\"", " guillemet ");
        replacements.put(" ' ", " apostrophe ");
        replacements.put(" - ", " tiret ");
        replacements.put("@", " arobasse ");
        replacements.put("\\[", " crochet ouvert ");
        replacements.put("\\]", " crochet fermé ");
        replacements.put("\\{", " accolade ouverte ");
        replacements.put("\\}", " accolade fermée ");
        replacements.put("=", " égale ");
        replacements.put(" odf", " Hoo-Dai-éfeu ");
        replacements.put(" pdf", " Pai-Dai-éfeu ");
        replacements.put("\\.pdf", " Pai-Dai-éfeu ");
        replacements.put(".odt", " Hoo-Dai-tai ");
        replacements.put("\\.xml", " isce-émeu-éleu");
        replacements.put(" writer ", " vraïteur ");
        replacements.put(" open ", " aupènne ");
        replacements.put("^@saut de page manuel", " saut de page manuel");
        replacements.put("^@saut de page sans EP", " saut de page sans Entête et Pied de page");
        replacements.put("\\.\\.\\.", " et cetera ");
        replacements.put(" etc\\.", " et cétera ");
        replacements.put("\"", " guillemet ");
        replacements.put("l'entête", " lans téte ");

        // Appliquer les remplacements
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
//        	System.out.println(entry.getKey());
            texte = texte.replaceAll(entry.getKey(), entry.getValue());
        }

        returnTexte = texte;
    }
}
