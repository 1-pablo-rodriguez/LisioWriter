package xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;

public class Zip {

	/**
	 * Ajoute un fichier à partir d'un StringBuilder dans une archive à la volé.<br>
	 * 
	 * @param feedback Un StringBuilder cotenant tout le code au format HTML (et du javascript).
	 * @param filenameStudent Un nom du dossier de l'étudiant.
	 * @param Size La taille maximale d'une archive en octect.
	 * @param nameZip Le nom de la'archive.
	 * @return 
	 * @throws net.lingala.zip4j.exception.ZipException Excpetions
	 */
	public static String  AddBaseToZip(StringBuilder base) throws net.lingala.zip4j.exception.ZipException{

		
		InputStream is = null;
		String PathBaseEvaluations = Paths.get("").toAbsolutePath().toString();
		LocalDateTime date = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
		String formattedDate = date.format(formatter) + "-base_evaluations_analyseWriter.xml";
		
		try {
			// Initiate ZipFile object with the path/name of the zip file.
			// Zip file may not necessarily exist. If zip file exists, then 
			// all these files are added to the zip file. If zip file does not
			// exist, then a new zip file is created with the files mentioned
			ZipFile zipFile = new ZipFile(PathBaseEvaluations + "/archive des bases .zip");

			
			
			// Initiate Zip Parameters which define various properties such
			// as compression method, etc. More parameters are explained in other
			// examples
			ZipParameters parameters = new ZipParameters();
			parameters.setCompressionMethod(Zip4jConstants.ENC_METHOD_STANDARD);
			
			// below two parameters have to be set for adding content to a zip file 
			// directly from a stream
			
			// this would be the name of the file for this entry in the zip file
			parameters.setFileNameInZip(formattedDate);
			
			// we set this flag to true. If this flag is true, Zip4j identifies that
			// the data will not be from a file but directly from a stream
			parameters.setSourceExternalStream(true);
			
			// For this example I use a FileInputStream but in practise this can be 
			// any inputstream
			//is = new FileInputStream(filename);
			

			
			is = new ByteArrayInputStream(base.toString().getBytes(StandardCharsets.UTF_8));
			
			
			// Creates a new entry in the zip file and adds the content to the zip file
			zipFile.addStream(is, parameters);
			

			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		// reourne le nom du fichier dans l'archive
		return formattedDate;
	}
	
}
