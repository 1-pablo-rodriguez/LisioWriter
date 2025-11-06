package writer.util;

import org.languagetool.JLanguageTool;
import org.languagetool.language.French;

public final class LanguageToolProvider {
    private LanguageToolProvider() {}

    private static class Holder {
        static final JLanguageTool INSTANCE = create();
        @SuppressWarnings("deprecation")
		private static JLanguageTool create() {
            try {
                JLanguageTool lt = new JLanguageTool(new French());
                // configuration éventuelle, ex: lt.activateDefaultPatternRules();
                return lt;
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    /**
     * Retourne l'instance (initialise la première fois).
     * Attention : si l'initialisation échoue, une ExceptionInInitializerError est levée.
     */
    public static JLanguageTool get() {
        return Holder.INSTANCE;
    }
    
    
}
