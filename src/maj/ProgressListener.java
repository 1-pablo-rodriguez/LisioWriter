package maj;

/**
 * Listener simple pour recevoir la progression du téléchargement
 * et des messages d'état (utiles pour la TTS).
 */
public interface ProgressListener {
    /**
     * Appelé régulièrement pendant le téléchargement.
     * @param bytesRead octets téléchargés
     * @param totalBytes taille totale ou -1 si inconnue
     */
    void onProgress(long bytesRead, long totalBytes);

    /**
     * Message d'état ou d'erreur (à annoncer via TTS).
     * @param status message lisible
     */
    void onStatus(String status);
}
