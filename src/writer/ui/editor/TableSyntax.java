package writer.ui.editor;

import java.util.ArrayList;
import java.util.List;

public final class TableSyntax {
    private TableSyntax() {}

    public static boolean isTableStart(String line) { return line != null && line.strip().equals("@t"); }
    public static boolean isTableEnd(String line)   { return line != null && line.strip().equals("@/t"); }

    public static boolean isTableRow(String line)  { return line != null && line.strip().startsWith("|"); }
    public static boolean isHeaderRow(String line) {
        String s = (line == null) ? "" : line.strip();
        return s.startsWith("|!");
    }

    /**
     * Split des cellules en respectant \| et \\ ; trim ; conserve cellules vides.
     * Corrigée pour gérer les doubles antislashs.
     */
    public static List<String> splitCells(String row) {
        String s = row.strip();
        int start = s.startsWith("|!") ? 2 : 1;
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);

            // --- 1) Détection des séquences d’échappement ---
            if (c == '\\') {
                if (i + 1 < s.length()) {
                    char next = s.charAt(i + 1);
                    // \| → insère une barre verticale littérale
                    if (next == '|') {
                        cur.append('|');
                        i++; // sauter le caractère suivant
                        continue;
                    }
                    // \\ → insère un antislash littéral
                    else if (next == '\\') {
                        cur.append('\\');
                        i++;
                        continue;
                    }
                }
                // cas isolé : fin de ligne ou antislash seul → garde-le
                cur.append('\\');
            }

            // --- 2) Détection des séparateurs de cellule ---
            else if (c == '|') {
                out.add(cur.toString().trim());
                cur.setLength(0);
            }

            // --- 3) Caractère normal ---
            else {
                cur.append(c);
            }
        }

        // Dernière cellule
        out.add(cur.toString().trim());
        return out;
    }


    /**
     * Variante "propre" sans cellule vide de fin, utilisée pour PDF/HTML.
     */
    public static List<String> splitCellsTrimmed(String row) {
        List<String> cells = splitCells(row);
        if (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
            cells.remove(cells.size() - 1);
        }
        return cells;
    }
}
