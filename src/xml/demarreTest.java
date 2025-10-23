 package xml;

public class demarreTest {

	public static void main(String[] args) {


        
		String codeXML = new LectureFichierXML().getCodeXML();
		
		 // Mesure du temps avant l'exécution de la méthode
        long startTime = System.nanoTime();
		
		new transformeXLMtoNode(codeXML,false,null);
		node nod = transformeXLMtoNode.getNodeRoot();
		
		// Mesure du temps après l'exécution de la méthode
        long endTime = System.nanoTime();
        
        // Calcul du temps écoulé
        long duration = endTime - startTime;
        
        // Affichage du temps écoulé en nanosecondes
        System.out.println("Temps de traitement: " + duration + " nanosecondes");
        
        // Affichage du temps écoulé en millisecondes
        System.out.println("Temps de traitement: " + (duration / 1000000) + " millisecondes");
		
		
		
//		node cloneRoot = null;
//		try {
//			cloneRoot = nod.clone();
//		} catch (CloneNotSupportedException e) {
//			e.printStackTrace();
//		}
//		cloneRoot.setNameNode("clone");
//		cloneRoot.addAttribut("test", "true");
//		cloneRoot.retourneFirstEnfant("NodeTest").addAttribut("testAttribut", "Oui");
//		if(cloneRoot.retourneFirstEnfant("NodeTest").moveUp()) System.out.println("OK Up");
//		
//		node A = cloneRoot.retourneFirstEnfant("text:span");
//		A.addAttribut("AA","BB");
//		if(A.moveLeft()) System.out.println("OK Left");
//		if(A.moveUp()) System.out.println("OK Up");
//		A.modifieContenu(0, "Mon nouveau texte");
		
//		node tout = new node("tout");
//		tout.addEnfant(nod);
//		tout.addEnfant(cloneRoot);
//		if(nod.moveDown()) System.out.println("Move Down");
		
        startTime = System.nanoTime();
		ecritureFileXML.write(nod, "test.xml");
		
		endTime = System.nanoTime();
	        
        // Calcul du temps écoulé
        duration = endTime - startTime;
	        
        // Affichage du temps écoulé en nanosecondes
        System.out.println("Temps de traitement: " + duration + " nanosecondes");
	        
        // Affichage du temps écoulé en millisecondes
        System.out.println("Temps de traitement: " + (duration / 1_000_000) + " millisecondes");
			
//		nod.isVide();
		
	}

}
