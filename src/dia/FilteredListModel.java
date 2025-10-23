package dia;

import java.io.File;

import javax.swing.DefaultListModel;

class FilteredListModel extends DefaultListModel<String> {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	
	@Override
    public void addElement(String item) {
        if (item.matches("^[^\\.]+$") || item.matches(".*\\.xml$")) {
            super.addElement(item);  // Ajoute l'élément s'il est conforme aux critères
        }
    }

    public void addFile(File file) {
    	String item = file.getAbsolutePath();
    	 if (item.matches("^[^\\.]+$") || item.matches(".*\\.xml$")) {
    		 super.addElement(file.getAbsolutePath()); // Ajoute le chemin complet
    	 }
    }
    

    
}

