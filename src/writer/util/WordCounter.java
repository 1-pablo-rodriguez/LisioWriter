package writer.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WordCounter {

    // même motif que dans AnnouncePositionAction
    private static final Pattern WORD_PATTERN =
            Pattern.compile("\\p{L}+(?:['’\\-]\\p{L}+)*", Pattern.UNICODE_CHARACTER_CLASS);

    private WordCounter() {}

    public static int countWords(String s) {
        if (s == null || s.isBlank()) return 0;
        Matcher m = WORD_PATTERN.matcher(s);
        int c = 0;
        while (m.find()) c++;
        return c;
    }
}
