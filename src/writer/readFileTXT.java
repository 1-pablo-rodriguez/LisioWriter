package writer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import writer.ui.EditorFrame;
import writer.ui.editor.BraillePrefixer;

public final class readFileTXT {

	EditorFrame parent;
	
    @SuppressWarnings("unused")
	public readFileTXT(File f, EditorFrame parent) {
        if (f == null || !f.isFile()) {
        	System.out.println("Fichier introuvable.");
            return;
        }

        final String texte;
        this.parent = parent;
        
        try {
            texte = readTextSmart(f);
        } catch (IOException ex) {
        	System.out.println("Erreur de lecture : " + ex.getMessage());
            return;
        }

        // Mettre à jour l’UI et l’état de l’app sur l’EDT
        SwingUtilities.invokeLater(() -> {
        	
        	commandes.init();
        	
            // Nom de fichier sans extension
            String baseName = stripExt(f.getName());
            commandes.nameFile = baseName;
            commandes.hash = 0;

            // (optionnel) mémoriser le chemin réel
            if (commandes.nodeblindWriter != null) {
                commandes.nodeblindWriter.getAttributs()
                        .put("filename", f.getAbsolutePath());
                // Si tu veux aussi stocker le contenu dans ton modèle interne :
                try {
                    var contentNode = commandes.nodeblindWriter.retourneFirstEnfant("contentText");
                    if (contentNode != null && contentNode.getContenu() != null) {
                        contentNode.getContenu().clear();
                        contentNode.getContenu().add(texte); // selon ton API (StringBuilder / autre)
                    }
                } catch (Throwable ignore) { /* si non disponible, on ignore */ }
            }

            // Afficher dans l’éditeur
            parent.getEditor().setText(texte != null ? BraillePrefixer.addBrailleAtParagraphStarts(texte) : BraillePrefixer.addBrailleAtParagraphStarts(""));
            
            // initialisation des bookmarks
            parent.createNewBookmarkManager();
            
            // Recupération du nom du fichier
            File nameFolder = f.getAbsoluteFile().getParentFile(); // dossier contenant f

         // 1) Nom "pur" du dossier (ex: "Documents")
         String nomDossier = (parent != null && nameFolder.getName() != null && !nameFolder.getName().isBlank())
                 ? nameFolder.getName()
                 : (nameFolder != null ? nameFolder.getAbsolutePath() : ""); // fallback (racine: C:\ ou "/")

         // 2) Chemin absolu du dossier (ex: "C:\Users\Moi\Documents")
         String cheminDossier = (nameFolder != null) ? nameFolder.getAbsolutePath() : null;

         // Mettre à jour tes variables d’appli
         commandes.nameFile = stripExt(f.getName());        // nom du fichier (avec extension)
         commandes.nomDossierCourant = cheminDossier; // chemin du dossier
         commandes.currentDirectory = nameFolder;     // si tu l’utilises ailleurs

        });
    }

    /** Lecture "intelligente" : détecte BOM, tente UTF-8, sinon fallback CP1252/ISO-8859-1. */
    private static String readTextSmart(File file) throws IOException {
        // 1) BOM aware (UTF-8/UTF-16)
        try (InputStream fin = new FileInputStream(file);
             PushbackInputStream in = new PushbackInputStream(fin, 3)) {

            byte[] bom = new byte[3];
            int n = in.read(bom, 0, bom.length);
            int unread = n;

            Charset cs = StandardCharsets.UTF_8; // défaut

            if (n >= 3 && (bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF)) {
                // UTF-8 BOM -> ignorer 3 octets
                unread = n - 3;
            } else if (n >= 2 && (bom[0] == (byte)0xFE && bom[1] == (byte)0xFF)) {
                cs = StandardCharsets.UTF_16BE; unread = n - 2;
            } else if (n >= 2 && (bom[0] == (byte)0xFF && bom[1] == (byte)0xFE)) {
                cs = StandardCharsets.UTF_16LE; unread = n - 2;
            }

            if (unread > 0) in.unread(bom, n - unread, unread);

            byte[] data = in.readAllBytes();
            String s = new String(data, cs);

            // 2) Si UTF-8 sans BOM mais caractères � (U+FFFD), on tente des fallbacks Windows
            if (containsReplacementChar(s)) {
                String s1252 = Files.readString(Path.of(file.getPath()), Charset.forName("windows-1252"));
                if (!containsReplacementChar(s1252)) return s1252;

                String sIso = Files.readString(Path.of(file.getPath()), StandardCharsets.ISO_8859_1);
                if (!containsReplacementChar(sIso)) return sIso;
            }
            return s;
        }
    }

    private static boolean containsReplacementChar(String s) {
        return s != null && s.indexOf('\uFFFD') >= 0;
    }



    
    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return (i > 0) ? name.substring(0, i) : name;
    }
}

