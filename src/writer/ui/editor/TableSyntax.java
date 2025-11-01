package writer.ui.editor;

import java.util.ArrayList;
import java.util.List;

public final class TableSyntax {
    private TableSyntax() {}

    // Délimiteurs de bloc table
    public static boolean isTableStart(String line) { return line != null && line.strip().equals("@t"); }
    public static boolean isTableEnd(String line)   { return line != null && line.strip().equals("@/t"); }

    // Lignes
    public static boolean isTableRow(String line)  { return line != null && line.strip().startsWith("|"); }
    public static boolean isHeaderRow(String line) {
        String s = (line == null) ? "" : line.strip();
        return s.startsWith("|!");
    }

    /** Split des cellules en respectant \| et \\ ; trim ; conserve cellules vides. */
    public static List<String> splitCells(String row) {
        String s = row.strip();
        int start = s.startsWith("|!") ? 2 : 1; // saute le préfixe | ou |!
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { cur.append(c); esc = false; }
            else if (c == '\\') { esc = true; }
            else if (c == '|') { out.add(cur.toString().trim()); cur.setLength(0); }
            else { cur.append(c); }
        }
        out.add(cur.toString().trim());
        return out;
    }
}