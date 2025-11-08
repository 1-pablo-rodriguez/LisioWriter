package exporterHTML;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Utility for exporting a HTML string to a .html file.
 */
public final class HtmlExporter {

    private HtmlExporter() {}

    /**
     * Exporte la chaîne HTML vers outPath (chemin complet du fichier .html).
     * Si openInBrowser == true et que Desktop est supporté, ouvre le fichier après écriture.
     *
     * @param html          contenu HTML complet (<!doctype html>...).
     * @param outPath       chemin complet du fichier de sortie (.html).
     * @param openInBrowser si true tente d'ouvrir le fichier dans le navigateur par défaut.
     * @return Path vers le fichier écrit.
     * @throws IOException si écriture ou création du répertoire échoue.
     */
    public static Path exportHtml(String html, Path outPath, boolean openInBrowser) throws IOException {
        if (html == null) html = "";
        
        // Normaliser nouvelle ligne
        html = html.replace("\r\n", "\n").replace("\r", "\n");

        // S'assurer que l'extension est .html
        String fileName = outPath.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".html")) {
            fileName = fileName + ".html";
            outPath = outPath.getParent() != null ? outPath.getParent().resolve(fileName) : outPath.resolveSibling(fileName);
        }

        // Créer dossier parent si besoin
        Path parent = outPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // Écrire en UTF-8 atomiquement (écriture vers fichier temporaire puis move)
        Path tmp = Files.createTempFile("export-html-", ".tmp");
        Files.writeString(tmp, html, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

        // Déplacer atomiquement si possible (remplace le fichier s'il existe)
        try {
            Files.move(tmp, outPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // fallback non-atomique
            Files.move(tmp, outPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Ouvrir dans le navigateur si demandé
        if (openInBrowser && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(outPath.toUri());
            } catch (Throwable t) {
                // ne pas casser l'export si l'ouverture échoue ; juste log
                System.err.println("HtmlExporter: impossible d'ouvrir le navigateur : " + t.getMessage());
            }
        }

        return outPath;
    }

    /**
     * Sanitize filename (enlever caractères interdits Windows/Unix).
     */
    public static String sanitizeFileName(String name) {
        if (name == null) return "document";
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.isEmpty()) clean = "document";
        if (clean.length() > 200) clean = clean.substring(0, 200);
        return clean;
    }
}