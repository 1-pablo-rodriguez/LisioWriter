package writer.util;

import javax.swing.ImageIcon;

/**
 * Utilitaire pour charger des icônes depuis le classpath.
 */
public final class IconLoader {

    private IconLoader() {}

    public static ImageIcon load(String absoluteClasspathPath) {
        var url = IconLoader.class.getResource(absoluteClasspathPath);
        if (url == null) {
            throw new IllegalStateException("Ressource introuvable : " + absoluteClasspathPath);
        }
        return new ImageIcon(url);
    }
}