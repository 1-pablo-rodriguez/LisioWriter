package dia;

import java.io.File;

import javax.swing.DefaultListModel;

class FilteredListModelDOCX extends DefaultListModel<String> {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	
	@Override
    public void addElement(String item) {
        if (item.matches("^[^\\.]+$") || item.matches(".*\\.docx$")) {
            super.addElement(item);  // Ajoute l'élément s'il est conforme aux critères
        }
    }

    public void addFile(File file) {
    	String item = file.getAbsolutePath();
    	 if (item.matches("^[^\\.]+$") || item.matches(".*\\.docx$")) {
    		 super.addElement(file.getAbsolutePath()); // Ajoute le chemin complet
    	 }
    }
    

    
}

