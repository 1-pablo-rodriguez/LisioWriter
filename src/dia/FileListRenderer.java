package dia;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

class FileListRenderer extends DefaultListCellRenderer {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        String filePath = (String) value;
        File file = new File(filePath); // Crée un objet File avec le chemin complet

        if (file.isDirectory()) {
            label.setText(file.getName()); //label.setText("Dossier - " + file.getName());
            label.setIcon(UIManager.getIcon("FileView.directoryIcon"));
        } else {
            label.setText(file.getName()); //label.setText("Fichier - " + file.getName());
            label.setIcon(UIManager.getIcon("FileView.fileIcon"));
        }

        label.setBorder(new EmptyBorder(5, 5, 5, 5));
        return label;
    }
}
