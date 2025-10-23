package xml;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.model.FileHeader;





public class LecturesDossiers {

	
	private static EnsembleFichiers EC = new EnsembleFichiers();
	
	
	public LecturesDossiers( ecritureFileXML.LocationFile location, String patch) throws ParserConfigurationException ,  IOException, SAXException {
		String NomDossier = null;
		String leNomDuRepertoire = patch;
		String ContentT = null;
		String ContentObjectGraphicTableur = null;
		
		String fichierAnalyseods = null;

		
		File rep = new File(leNomDuRepertoire);
		
		if(location==ecritureFileXML.LocationFile.DansDossier) {
			System.out.println();
			
			if(rep.isDirectory()) {
				File[] fichiers = rep.listFiles();
				
				for(int i=0; i<fichiers.length; i++ ) {
					ContentT = "";
					ContentObjectGraphicTableur = "";
					fichierAnalyseods = null;
					NomDossier=null;
					
					if(fichiers[i].isDirectory()) {
						
						String filename = fichiers[i].getName();
						
						//String fichierAnalyseods = null ;
						if(fichiers[i].getName().contains("_")) {
							NomDossier = fichiers[i].getName().substring(0, fichiers[i].getName().indexOf("_"));
						}else {
							NomDossier = fichiers[i].getName();
						}
							
							File[] fichiers2 = fichiers[i].listFiles();
							for(int j=0; j<fichiers2.length; j++ ) {
								if(fichiers2[j].getName().contains(".")) {
									String ext = fichiers2[j].getName().substring( fichiers2[j].getName().lastIndexOf("."));
									if(ext.equals(".ods")) {
										ContentT = ContenuContent(fichiers2[j]);
										fichierAnalyseods = fichiers2[j].getName();
										ContentObjectGraphicTableur	= ContenuContentObject(fichiers2[j]);
										fichierAnalyseods = fichiers2[j].getName(); }
								}
							}
							if(NomDossier==null) {NomDossier="inconnu"; filename="inconnu";}
							if(fichierAnalyseods==null) fichierAnalyseods="pas de fichier calc déposé.";

						
							if(fichierAnalyseods!=null) {
								new transformeXLMtoNode(fichierAnalyseods,false,null);
								node nod = transformeXLMtoNode.getNodeRoot();
								fichierAnalyseods = nod.ecritureXML().toString();
								
								new transformeXLMtoNode(ContentObjectGraphicTableur,false,null);
								nod = transformeXLMtoNode.getNodeRoot();
								ContentObjectGraphicTableur = nod.ecritureXML().toString();
							}
							
							EC.AjouteEnsembleAnalyse(NomDossier, ContentT, fichierAnalyseods,ContentObjectGraphicTableur,filename );
							System.out.print(".");	
					}
				}

			}
		}
	
		
		if(location == ecritureFileXML.LocationFile.UniquementFichier) {
			System.out.println();
			
			File[] fichiers = rep.listFiles();
			
			ContentT = "";
			fichierAnalyseods = null;
			NomDossier=null;

			for(int j=0; j<fichiers.length; j++ ) {
				if(fichiers[j].getName().contains(".")) {
					String ext = fichiers[j].getName().substring( fichiers[j].getName().lastIndexOf("."));
					if(fichiers[j].isFile()) {
						if(ext.equals(".ods")) { 
							ContentT = ContenuContent(fichiers[j]);
							fichierAnalyseods = fichiers[j].getName();
							NomDossier=fichierAnalyseods;
							if(NomDossier.contains("_")) NomDossier = NomDossier.substring(0, NomDossier.indexOf("_"));
							ContentObjectGraphicTableur	= ContenuContentObject(fichiers[j]); fichierAnalyseods = fichiers[j].getName(); 								
							
							if(fichierAnalyseods!=null) {
								new transformeXLMtoNode(fichierAnalyseods,false,null);
								node nod = transformeXLMtoNode.getNodeRoot();
								
								fichierAnalyseods = nod.ecritureXML().toString();
								
								if(ContentObjectGraphicTableur!=null) {
									new transformeXLMtoNode(ContentObjectGraphicTableur,false,null);
									nod = transformeXLMtoNode.getNodeRoot();
									ContentObjectGraphicTableur = nod.ecritureXML().toString();
								}
								
							}
							
							
							EC.AjouteEnsembleAnalyse(NomDossier, ContentT,fichierAnalyseods, ContentObjectGraphicTableur, "");
							ContentT="";
							ContentObjectGraphicTableur="";
							NomDossier="";
						}
					}
				}
				
			}
		}
	}
	
	
	
	/**
	 * 
	 * @param Version
	 * @param patch
	 * @param fichierStudentInCurrentFolder
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	public LecturesDossiers(String pathFichier) throws ParserConfigurationException, SAXException, IOException {
		String NomDossier = null;
		
		String ContentT = null;
		String ContentObjectGraphicTableur = null;
		String fichierAnalyseods = null;

		File fichiers = new File(pathFichier);
	
		ContentT = "";
		ContentObjectGraphicTableur = "";
			
		String filename = fichiers.getName();
		if(fichiers.getName().contains(".")) {
			String ext = fichiers.getName().substring( fichiers.getName().lastIndexOf("."));
			filename = fichiers.getName();
			NomDossier = pathFichier;
			
			if(ext.equals(".ods")) {
				ContentT = ContenuContent(fichiers);
				fichierAnalyseods = fichiers.getName();
				ContentObjectGraphicTableur	= ContenuContentObject(fichiers);
				fichierAnalyseods = fichiers.getName();
			}
		}
		
		if(fichierAnalyseods==null) fichierAnalyseods="pas de fichier calc déposé.";
		
		System.out.println("ContentT :" + ContentT);
		System.out.println("ContentObjectGraphicTableur :" + ContentObjectGraphicTableur);
		System.out.println("NomDossier :" + NomDossier);
		System.out.println("filename :" + filename);
		
		
		EC.AjouteEnsembleAnalyse(NomDossier, ContentT, fichierAnalyseods,ContentObjectGraphicTableur,filename );
	}
	
	/**
	 * 
	 * @param zipf
	 * @return
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	private static String ContenuContent(File zipf) throws ParserConfigurationException, SAXException, IOException {
		String content = null;
		String style= null;
		String meta =null;

		try {
            ZipFile zipFile = new ZipFile(zipf.getAbsolutePath());
                      
            @SuppressWarnings("unchecked")
			List<FileHeader> fileHeaderList = (List<FileHeader>) zipFile.getFileHeaders();
            
            for (int i = 0; i < fileHeaderList.size(); i++) {
            	FileHeader fileHeader = (FileHeader) fileHeaderList.get(i);
            	net.lingala.zip4j.io.ZipInputStream is = zipFile.getInputStream(fileHeader);
                
            	if(fileHeader.getFileName().equals("content.xml")) {
            		 int uncompressedSize = (int) fileHeader.getUncompressedSize();
                     
                     OutputStream os = new ByteArrayOutputStream(uncompressedSize);
                     
                     int bytesRead;
                     
                     byte[] buffer = new byte[4096];
                     while ((bytesRead = is.read(buffer)) != -1) {
                         os.write(buffer, 0, bytesRead);  // os le contenu du fichier
                         }
                     content = os.toString();
                     
            	}
            	if(fileHeader.getFileName().equals("styles.xml")) {
            		 int uncompressedSize = (int) fileHeader.getUncompressedSize();
                     
                     OutputStream os = new ByteArrayOutputStream(uncompressedSize);
            		
                     int bytesRead;

                     byte[] buffer = new byte[4096];
                    
                     while ((bytesRead = is.read(buffer)) != -1) {
                         os.write(buffer, 0, bytesRead);  // os le contenu du fichier
                         }
                     style = os.toString();
            	}
            	if(fileHeader.getFileName().equals("meta.xml")) {
            		 int uncompressedSize = (int) fileHeader.getUncompressedSize();
                     
                     OutputStream os = new ByteArrayOutputStream(uncompressedSize);
                     
                     int bytesRead;

                     byte[] buffer = new byte[4096];
                    
                     while ((bytesRead = is.read(buffer)) != -1) {
                         os.write(buffer, 0, bytesRead);  // os le contenu du fichier
                         }
                     meta = os.toString();
            	}
            	
            	
            	
            	
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        } catch (net.lingala.zip4j.exception.ZipException e) {
			e.printStackTrace();
		}
		
		String tout = content + style + meta ;
		
		
		 byte[] defaultBytes = tout.getBytes();
         //Charset def = Charset.defaultCharset();
         Charset utf8 = Charset.forName("utf-8");
         ByteBuffer bb = ByteBuffer.wrap(defaultBytes);
         CharBuffer cb = utf8.decode(bb);
         tout = cb.toString();
         

         
		return tout;
	}
	
	/**
	 * 
	 * @param zipf
	 * @return
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	private static String ContenuContentObject(File zipf) throws ParserConfigurationException, SAXException, IOException {
	String object= null;

	try {
        ZipFile zipFile = new ZipFile(zipf.getAbsolutePath());
                  
        @SuppressWarnings("unchecked")
		List<FileHeader> fileHeaderList = (List<FileHeader>) zipFile.getFileHeaders();
        
//        int CompteurObject = 1;
        
        for (int i = 0; i < fileHeaderList.size(); i++) {
        	FileHeader fileHeader = (FileHeader) fileHeaderList.get(i);
        	net.lingala.zip4j.io.ZipInputStream is = zipFile.getInputStream(fileHeader);
            
        	if(fileHeader.getFileName().contains("Object ") && fileHeader.getFileName().contains("/content.xml") ) {
        		String num = fileHeader.getFileName();
        		Pattern p = Pattern.compile("[a-zA-Z]");
        		Matcher m = p.matcher(num);
        		num = m.replaceAll("");
        		
        		p = Pattern.compile("/");
        		m = p.matcher(num);
        		num = m.replaceAll("");
        		
        		p = Pattern.compile(" ");
        		m = p.matcher(num);
        		num = m.replaceAll("");
        		
        		p = Pattern.compile("\\.");
        		m = p.matcher(num);
        		num = m.replaceAll("");
        		
        		int uncompressedSize = (int) fileHeader.getUncompressedSize();
                
                OutputStream os = new ByteArrayOutputStream(uncompressedSize);
       		
                int bytesRead;

                byte[] buffer = new byte[4096];
               
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);  // os le contenu du fichier
                    }
                object = "<Object num=\""+num+"\">" + os.toString() + "</Object>" + object;
        		
        	}
        	
 
        }
        
    } catch (IOException ex) {
        ex.printStackTrace(System.err);
    } catch (net.lingala.zip4j.exception.ZipException e) {
		e.printStackTrace();
	}
	
	
	String tout = object ;
	if(tout!=null) {
		 byte[] defaultBytes = tout.getBytes();
         //Charset def = Charset.defaultCharset();
         Charset utf8 = Charset.forName("utf-8");
         ByteBuffer bb = ByteBuffer.wrap(defaultBytes);
         CharBuffer cb = utf8.decode(bb);
         tout = cb.toString();
	}
	
     
	return tout;
}

	/**
	 * 
	 * @return
	 */
	public static EnsembleFichiers getEC() {
		return EC;
	}

	/**
	 * 
	 * @param eC
	 */
	public void setEC(EnsembleFichiers eC) {
		EC = eC;
	}

	

	
	
}
