package xml;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class transformeXLMtoNode {
	
	public static node nodeRoot = new node("root");
	private static String codeXML = "";
	
	public transformeXLMtoNode(String code, boolean test, node nodePrecedent) {
		
		if(test) {
			codeXML = codeXMLPourTest();
		}else {
			codeXML=code;
		}
		
		codeXML = nettoyagePreliminaire(codeXML);
		
		nodeRoot = new node("root");
		
		Pattern p = Pattern.compile("(<[.[^ /<]]{1,}>{1}|<[.[^ /<]]{1,}/>|<[.[^ /<]]{1,}\\p{Space}[.[^<]]{1,}(>|/>){1}|</[[^< ].]{1,}>{1}|[.[^>\"<]]{1,})");

		
		Matcher m = p.matcher(codeXML);
		ArrayList<String> nom = new ArrayList<String>() ;
		
		while(m.find()) {
			 nom.add(codeXML.substring(m.start(), m.end()));
		}
		
		node lastNode = nodeRoot;
		for(int i = 0 ; i < nom.size(); i++) {

			//^<(?<nom>[^\s<>/]+?\b)(?<espace>\s+?)(?<attribut>.*?)(?<fin>>)
			p = Pattern.compile("^<(?<nom>[^\\s]+?\\b)(?<espace>\\s+?)(?<attribut>.*?)>");
			m = p.matcher(nom.get(i));
			if(m.find()) {
				String name = m.group("nom");
				lastNode.addNewEnfant(name);
				String lesAttributs = m.group("attribut");
				affecteAttribut(lastNode.retourneLastEnfant(),lesAttributs);
				String A = lesAttributs.substring(lesAttributs.length()-1, lesAttributs.length());
				if(A.equals("/")) {
					continue;
				}
				lastNode = lastNode.retourneLastEnfant();
				continue;
			}
			
			// ^<(?<nom>[^\s<>/]+?\b)>
			p = Pattern.compile("^<(?<nom>[^\\s<>/]+?\\b)>");
			m = p.matcher(nom.get(i));
			if(m.find()) {
				String name = m.group("nom");
				lastNode.addNewEnfant(name);
				lastNode = lastNode.retourneLastEnfant();
				continue;
			}
			
			//^[^<]+
			p = Pattern.compile("^[^<]+");
			m = p.matcher(nom.get(i));
			if(m.find()) {
				lastNode.addContenu(m.group());
				continue;
			}
			
			// ^</(?<nom>[^>]+?)>
			p = Pattern.compile("^</(?<nom>[^>]+?)>");
			m = p.matcher(nom.get(i));
			if(m.find()) {
				String name = m.group("nom");
				node ClotureNode = lastNode.retourneLastEnfant(name);
				if(ClotureNode!=null) ClotureNode.setNodeClose(true); else System.out.println("Erreur " + name);;
				lastNode = ClotureNode.getParent();
				continue;
			}
			
		}
		

		
	}
	
	
	private String nettoyagePreliminaire(String codeXML) {
		//codeXML = codeXML.replace("\t","").replace("\r", "").replace("\n", "");
		// Suppression du node <?xml >
		if(codeXML.contains("<?xml ")) codeXML = codeXML.replaceAll("<\\?xml\\s.*?>", "");
				
		// suppression node <!--Your comment-->
		if(codeXML.contains("<!--")) codeXML = codeXML.replaceAll("<!--.*?-->", "");

		return codeXML;
	}
	
	
 	private void affecteAttribut(node lastEnfant, String attributs) {
		// (?<nameAttribut>[^= ]+?)="(?<valueAttribut>.*?)"|(?<attribut>\b[^= ]+?\b)
		Pattern p = Pattern.compile("(?<nameAttribut>[^=\\s]+?)=\\\"(?<valueAttribut>.*?)\\\"|(?<attribut>\\b[^\\s]+?\\b)");
		Matcher m = p.matcher(attributs);
		while(m.find()) {
			if(m.group().contains("=")) {
				String nameAttribut = m.group("nameAttribut");
				String valueAttribut = m.group("valueAttribut");
				lastEnfant.addAttribut(nameAttribut, valueAttribut);
			}else {
				String attribut = m.group("attribut");
				lastEnfant.addAttribut(attribut, "");
			}
		}
	}
	
 	
 	private String codeXMLPourTest() {
 		return "<!--Commentaire du test-->"
 				+ "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
 				+ "<Node0 attribut1Node0=\"Je teste avec un texte plus long\" attributSansValeur2 attribut1Node0=\"=Je teste\" attributSansValeur>"
 				+ "<text:p bold=\"true\" upper=\"true\">Mon texte paragraphe 0"
 				+ "<text:tab>Le texte avec tabulation</text:tab> Le texte qui suivant la tabulation"
 				+ "</text:p>"
 				+ "<text:p>Mon texte paragraphe 1"
 				+ "<text:span bold=\"true\">le texte en GRAS</text:span>"
 				+ " Après le texte en GRAS</text:p>Deux de plus"
 				+ "<text:p bold=\"true\" upper=\"false\">Mon texte paragraphe 2</text:p>"
 				+ "</Node0>"
 				+ "<NodeTest attributVide/>"
 				+ "<Node0 attributVideDeNode0>Mon contenu"
 				+ "</Node0>";
 	}

 	/**
 	 * Retourne le node après transformationXMLtoNode
 	 * @return
 	 */
	public static node getNodeRoot() {
		return nodeRoot;
	}



	
	
}
