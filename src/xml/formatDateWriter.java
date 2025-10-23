package xml;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;

import writer.commandes;


public class formatDateWriter {

	/**
	 * Retourne true sie le format est YYYY-MM-JJTHH:MM:SS.<br>
	 * C'est le format utilisé par LibreOffice.<br>
	 * @param date une String.
	 * @return retrourne true ou false.
	 */
	public static boolean isCorrect(String date) {
		Pattern p = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}$");
		Matcher m = p.matcher(date);
		if(m.find()) return true;
		if(!date.isBlank()) {
			JOptionPane.showMessageDialog(null, "Le format de la date "+ date +" n'est pas correct.");
		}
		return false;
	}
	
	
	/**
	 * Retourne une date à partir d'une String composée de YYYY-MM-JJTHH:MM:SS.<br>
	 * @param libreoffice_date
	 * @return une date.
	 */
	public static Date DateLibreOffice(String libreoffice_date){
		if(libreoffice_date.isBlank()) return null;
		boolean contientHeure = false;
		if(libreoffice_date.contains("T")) {
			libreoffice_date=libreoffice_date.replace("T", " ");
			contientHeure=true;
		}
		Date d = null;
		SimpleDateFormat simpledateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		if(!contientHeure) simpledateformat = new SimpleDateFormat("yyyy-MM-dd");
		
		try {
			d = simpledateformat.parse(libreoffice_date);
		}catch(ParseException e) {
			e.printStackTrace();
		}
		
		return  d;
	}
	
	/**
	 * Retourne une String au format dd/MM/yyy HH:mm:ss à partir d'une date.</br>
	 * @param date
	 * @return
	 */
	public static String DateEnClairFR(Date date) {
		SimpleDateFormat simpledateformat = new SimpleDateFormat("EEEE dd MMM yyy' à 'hh:mm:ss");
		String d1 = simpledateformat.format(date);
		
		return d1;
	}
	
	/**
	 * Retourne une String au format YYYY-MM-JJTHH:MM:SS à partir d'une date.</br>
	 * @param d
	 * @return
	 * @throws ParseException
	 */
	public static String DateLibreOffice(Date date) throws ParseException {
		SimpleDateFormat simpledateformat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		String d1 = simpledateformat.format(date);
		
		return d1;
	}
	
	/**
	 * Retourne sous la forme d'un string au format LibreOffice la date d'aujourd'hui.<br>
	 * @return
	 * @throws ParseException
	 */
	public static String dateTodayLibreOffice() throws ParseException {
		Date aujourdhui = new Date();
		return DateLibreOffice(aujourdhui);
	}
	
	/**
	 * Retourne la date du fichier du node commandes.sujet<br>
	 * Si ne trouve pas la date alors retourne un null.
	 * @return
	 */
	public static Date dateNodeSujet() {
		if(commandes.dateModif.getAttributs().get("date")!=null) {
			String dateString = commandes.dateModif.getAttributs().get("date");
			return DateLibreOffice(dateString);
		}
		return null;
	}
	
}
