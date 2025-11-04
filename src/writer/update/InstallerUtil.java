package writer.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lance l'installateur en mode silencieux (Windows, Inno Setup). */
public final class InstallerUtil {
    private InstallerUtil() {}

    /**
     * Exécute l'EXE en silencieux et logue dans %LOCALAPPDATA%\LisioWriter\install.log
     */
    public static Process runInstallerSilent(Path installer) throws IOException {
    	System.out.println("Début de l'installation silencieuse.");
        String exe = installer.toAbsolutePath().toString();

        Path logDir = Path.of(System.getProperty("user.home"), "AppData", "Local", "LisioWriter");
        Files.createDirectories(logDir);

        return new ProcessBuilder(
                "cmd.exe", "/c", "start", "",        // lance dans une nouvelle fenêtre
                "\"" + exe + "\"",
                "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART",
                "/LOG=" + logDir.resolve("install.log")
        )
        .directory(installer.getParent().toFile())
        .inheritIO()
        .start();
    }
}