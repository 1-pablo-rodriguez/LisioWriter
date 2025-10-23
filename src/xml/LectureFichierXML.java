package xml;

import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;


/**
 * Chargement d'un fichier d'analyse
 * @author pabr6
 *
 */
public class LectureFichierXML extends JFileChooser {

	private String codeXML ="";

	private static final long serialVersionUID = 1L;

	/**
	 * Chargement d'un fichier d'analyse.<br>
	 * Le premier node doit être le node fichier.
	 */
	public LectureFichierXML() {
		setDialogTitle("Sélectionner un fichier d'analyse");
		FileNameExtensionFilter filter = new FileNameExtensionFilter("Fichier .XML", "xml");
		setFileFilter(filter);	
		setPreferredSize(new Dimension(550, 420));
		int response =  showOpenDialog(null);
		if(response == JFileChooser.APPROVE_OPTION) {
			File file = new File(getSelectedFile().getAbsolutePath());
			String ext = file.getName().substring(file.getName().lastIndexOf("."));
			if(ext.equals(".xml")) openFileXML(file);
		}
	}
	
//	@Override
//    protected JDialog createDialog( Component parent ) throws HeadlessException {
//        JDialog dialog = super.createDialog( parent );
//        Image img = new ImageIcon(getClass().getResource("/resources/evalwriter.png") ).getImage();
//        dialog.setIconImage(img);
//        return dialog;
//    }

	
	/**
	 * 
	 * @param file
	 */
	private String  openFileXML(File file) {
		BufferedReader br;
		try {
			br = new BufferedReader(
			        new InputStreamReader(
			            new FileInputStream(file.getAbsoluteFile()), StandardCharsets.UTF_8));
			String line;
			StringBuilder targetString = new StringBuilder();
			while ((line = br.readLine()) != null) {
				targetString.append(line);
			}
			this.codeXML = targetString.toString();
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (Exception e) {
		}
		return "";
	}

	public String getCodeXML() {
		return codeXML;
	}
	
	
	

}
