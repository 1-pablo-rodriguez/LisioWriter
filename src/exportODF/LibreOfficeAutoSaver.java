package exportODF;

import java.io.File;
import java.io.IOException;

public class LibreOfficeAutoSaver {

    /**
     * Réenregistre un fichier .odt avec LibreOffice en mode headless pour forcer le recalcul des sauts de page.
     *
     * @param sourceFilePath Chemin du fichier source (.odt)
     * @param outputDirectory Dossier où sauvegarder
     */
    public static void regenerateOdtWithLibreOffice(String sourceFilePath, String outputDirectory) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
            "C:/Program Files/LibreOffice/program/soffice.exe",
            "--headless",
            "--convert-to", "odt",
            "--outdir", outputDirectory,
            sourceFilePath
        );

        processBuilder.redirectErrorStream(true);
        processBuilder.directory(new File(".")); // Facultatif : répertoire de travail

        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println("✅ Document régénéré avec succès !");
        } else {
            System.err.println("❌ Erreur lors de la régénération, code de sortie : " + exitCode);
        }
    }
}

