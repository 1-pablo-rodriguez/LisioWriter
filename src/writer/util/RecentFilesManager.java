package writer.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gère la liste des fichiers récemment ouverts (max 10) et la persiste
 * dans un petit fichier texte.
 */
public final class RecentFilesManager {

    private static final int MAX = 40;
    private static final List<File> RECENTS = new ArrayList<>();

    // ---- Où stocker la liste ? ----
    // Option simple : dans le répertoire utilisateur.
    // Ex : C:\Users\toi\.lisiowriter_recent.txt ou /home/toi/.lisiowriter_recent.txt
    private static final Path STORE_FILE = Path.of(
            System.getProperty("user.home"),
            ".lisiowriter_recent.txt"
    );

    // Si tu préfères le dossier d’exécution de l’appli :
    // private static final Path STORE_FILE = Path.of(
    //        System.getProperty("user.dir"),
    //        "recent_files.txt"
    // );

    static {
        loadFromDisk();
    }

    private RecentFilesManager() {}

    /** Ajoute un fichier en tête de liste (élimine les doublons, coupe à 30) et sauvegarde. */
    public static synchronized void addOpenedFile(File f) {
        if (f == null) return;

        // Supprimer toute entrée équivalente
        RECENTS.removeIf(existing -> samePath(existing, f));

        // Ajouter en tête
        RECENTS.add(0, f);

        // Limiter à MAX
        while (RECENTS.size() > MAX) {
            RECENTS.remove(RECENTS.size() - 1);
        }

        saveToDisk();
    }

    /** Renvoie une copie de la liste (pour ne pas exposer la liste interne). */
    public static synchronized List<File> getRecentFiles() {
        return new ArrayList<>(RECENTS);
    }

    /** Supprime un fichier de la liste (ex: introuvable sur disque) et sauvegarde. */
    public static synchronized void remove(File f) {
        if (f == null) return;
        RECENTS.removeIf(existing -> samePath(existing, f));
        saveToDisk();
    }

    // ---------- Persistance ----------

    private static void loadFromDisk() {
        RECENTS.clear();
        if (!Files.exists(STORE_FILE)) {
            return; // rien à charger
        }
        try {
            List<String> lines = Files.readAllLines(STORE_FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                File f = new File(trimmed);
                // option : ne garder que les fichiers qui existent encore
                if (f.exists()) {
                    RECENTS.add(f);
                }
                if (RECENTS.size() >= MAX) break;
            }
        } catch (IOException e) {
            // on loggue, mais on ne casse pas l'appli
            System.err.println("Impossible de charger la liste des fichiers récents : " + e.getMessage());
        }
    }

    private static void saveToDisk() {
        try {
            // s'assurer que le parent existe
            if (STORE_FILE.getParent() != null) {
                Files.createDirectories(STORE_FILE.getParent());
            }

            try (BufferedWriter w = Files.newBufferedWriter(STORE_FILE, StandardCharsets.UTF_8)) {
                for (File f : RECENTS) {
                    try {
                        // on écrit le chemin canonique pour éviter les doublons bizarres
                        w.write(f.getCanonicalPath());
                    } catch (IOException e) {
                        // sinon, on se rabat sur getAbsolutePath
                        w.write(f.getAbsolutePath());
                    }
                    w.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Impossible de sauvegarder la liste des fichiers récents : " + e.getMessage());
        }
    }

    private static boolean samePath(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (Exception e) {
            return a.equals(b);
        }
    }
}