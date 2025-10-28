package writer;

import java.io.File;

import javax.swing.JMenu;
import javax.swing.SwingUtilities;

import writer.ui.EditorFrame;


public class LisioWriter extends writer.ui.EditorFrame {

    private static final long serialVersionUID = 1L;
    public static int positionCurseurSauv = 0;
    public static boolean isDispose = true;
    public static JMenu cachedMenuPages = null;

    
    static {
  	  // Forcer les implémentations JDK pour JAXP
  	  System.setProperty("javax.xml.parsers.SAXParserFactory",
  	      "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");
  	  System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
  	      "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
  	  System.setProperty("javax.xml.transform.TransformerFactory",
  	      "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
  	}
   

	    // Démarrage du logiciel
	    public static void main(String[] args) {
	        // 1️⃣ — Chemin du fichier fourni au démarrage (si double-clic)
	        final String startupPath = (args != null && args.length > 0) ? args[0] : null;

	        try {
	            System.setProperty("file.encoding", "UTF-8");
	            commandes.init(); // initialisation de tes préférences et chemins

	            // 2️⃣ — Lancer l’UI sur le thread Swing (EDT)
	            SwingUtilities.invokeLater(() -> {
	                EditorFrame frame = new EditorFrame();
	                frame.setVisible(true);

	                // 3) Ouvrir le fichier si fourni
		            if (startupPath != null && !startupPath.isBlank()) {
		                openFileOnStartup(startupPath, frame); // méthode static (voir ci-dessous)
		            }
		            
		            new bienvenueAffichage(frame);
	            });

	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	    /**
	     * Ouvre le fichier donné au démarrage.
	     */
	    private static void openFileOnStartup(String path,  EditorFrame frame) {
        	 try {
                 File f = new File(path);
                 if (!f.exists() || !f.isFile()) {
                     System.err.println("Fichier introuvable: " + path);
                     // Optionnel: annoncer via TTS / barre braille
                     return;
                 }
	            // tu peux ici appeler ta classe d’import :
	            new readFileBlindWriter(f, frame);

	        } catch (Exception ex) {
	            ex.printStackTrace();
	        }
	    }
}

   
   





    

