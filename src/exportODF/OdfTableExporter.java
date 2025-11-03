package exportODF;

import java.util.ArrayList;
import java.util.List;

import org.odftoolkit.odfdom.doc.OdfTextDocument;
import org.odftoolkit.odfdom.pkg.OdfFileDom;
import org.odftoolkit.odfdom.dom.element.table.TableTableElement;
import org.odftoolkit.odfdom.dom.element.table.TableTableColumnElement;
import org.odftoolkit.odfdom.dom.element.table.TableTableHeaderRowsElement;
import org.odftoolkit.odfdom.dom.element.table.TableTableRowElement;
import org.odftoolkit.odfdom.dom.element.table.TableTableCellElement;
import org.odftoolkit.odfdom.dom.element.text.TextPElement;

import writer.ui.editor.TableSyntax;

/** Utilitaires d’export ODT pour les tableaux @t ... @/t */
public final class OdfTableExporter {

    private OdfTableExporter() {}

    /** Permet à l’appelant de “rendre” le contenu d’une cellule (gras/italique, liens, notes, etc.). */
    @FunctionalInterface
    public interface CellRenderer {
        void render(TextPElement p, String cellText) throws Exception;
    }

    /**
     * Parcourt lines à partir de start (inclus) et collecte les lignes d’un bloc tableau
     * entre @t et @/t (exclus).
     * @return l’index de la ligne @/t (ou la dernière ligne traitée si @/t manquant), pour
     *         permettre à l’appelant d’avancer i = returnedIndex.
     */
    public static int collectTableBlock(String[] lines, int start, List<String> outRows) {
        int j = start + 1;
        for (; j < lines.length; j++) {
            String l = lines[j];
            if (TableSyntax.isTableEnd(l)) break;
            if (l != null && !l.strip().isEmpty()) outRows.add(l);
        }
        // si @/t trouvé, on renvoie son index ; sinon on renvoie la dernière ligne
        return (j < lines.length ? j : lines.length - 1);
    }

    /**
     * Construit un <table:table> dans le document, avec gestion des lignes d’en-tête "|!".
     * Le rendu du contenu des cellules est délégué à cellRenderer (pour garder votre
     * logique d’inline : gras/italique/liens/notes).
     */
    public static void buildOdfTable(
            OdfTextDocument odt,
            OdfFileDom contentDom,
            List<String> rawRows,
            CellRenderer cellRenderer) throws Exception {

        if (rawRows == null || rawRows.isEmpty()) return;

        // 1) Parse lignes -> listes de cellules
        List<List<String>> parsed = new ArrayList<>();
        boolean hasHeader = false;
        int maxCols = 0;

        for (String row : rawRows) {
            if (!TableSyntax.isTableRow(row)) continue;
            List<String> cells = TableSyntax.splitCells(row);
            if (TableSyntax.isHeaderRow(row)) hasHeader = true;
            parsed.add(cells);
            if (cells.size() > maxCols) maxCols = cells.size();
        }
        if (parsed.isEmpty() || maxCols == 0) return;

        // 2) Création table et colonnes
        TableTableElement table = contentDom.newOdfElement(TableTableElement.class);
        odt.getContentRoot().appendChild(table);
        for (int c = 0; c < maxCols; c++) {
            TableTableColumnElement col = contentDom.newOdfElement(TableTableColumnElement.class);
            table.appendChild(col);
        }

        int rowIndex = 0;

        // 3) En-têtes
        if (hasHeader) {
            TableTableHeaderRowsElement thead = contentDom.newOdfElement(TableTableHeaderRowsElement.class);
            table.appendChild(thead);

            while (rowIndex < parsed.size() && TableSyntax.isHeaderRow(rawRows.get(rowIndex))) {
                List<String> cells = parsed.get(rowIndex);
                TableTableRowElement tr = contentDom.newOdfElement(TableTableRowElement.class);
                thead.appendChild(tr);

                for (int c = 0; c < maxCols; c++) {
                    TableTableCellElement tc = contentDom.newOdfElement(TableTableCellElement.class);
                    tr.appendChild(tc);
                    TextPElement p = contentDom.newOdfElement(TextPElement.class);
                    tc.appendChild(p);

                    String cellText = (c < cells.size()) ? cells.get(c) : "";
                    cellRenderer.render(p, cellText);
                }
                rowIndex++;
            }
        }

        // 4) Corps
        for (; rowIndex < parsed.size(); rowIndex++) {
            List<String> cells = parsed.get(rowIndex);
            TableTableRowElement tr = contentDom.newOdfElement(TableTableRowElement.class);
            table.appendChild(tr);

            for (int c = 0; c < maxCols; c++) {
                TableTableCellElement tc = contentDom.newOdfElement(TableTableCellElement.class);
                tr.appendChild(tc);
                TextPElement p = contentDom.newOdfElement(TextPElement.class);
                tc.appendChild(p);

                String cellText = (c < cells.size()) ? cells.get(c) : "";
                cellRenderer.render(p, cellText);
            }
        }
    }
}
