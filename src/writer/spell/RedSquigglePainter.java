package writer.spell;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import javax.swing.text.*;

final class RedSquigglePainter extends LayeredHighlighter.LayerPainter {
    private static final Color RED = new Color(255, 80, 80);
    private static final int AMP  = 3;   // amplitude de la vague
    private static final int STEP = 4;   // largeur d’une demi-onde

    @Override
    public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
        // inutilisé (seulement la version "layered" nous intéresse)
    }

    @Override
    public Shape paintLayer(Graphics g, int offs0, int offs1, Shape bounds,
                            JTextComponent c, View view) {
        try {
            // JDK 9+ : modelToView2D évite la méthode dépréciée
            Rectangle2D r0_2d = c.modelToView2D(offs0);
            Rectangle2D r1_2d = c.modelToView2D(offs1);
            if (r0_2d == null || r1_2d == null) return bounds;

            Rectangle r0 = r0_2d.getBounds();
            Rectangle r1 = r1_2d.getBounds();

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(RED);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int y    = r0.y + r0.height - 2;   // proche de la ligne de base
            int x    = r0.x;
            int xEnd = r1.x;

            int dir = 1;
            while (x < xEnd) {
                int nx = Math.min(x + STEP, xEnd);
                int ny = y + dir * AMP;
                g2.drawLine(x, y, nx, ny);
                x = nx;
                dir = -dir;
            }
            g2.dispose();

            // Renvoie la zone peinte (union de r0 et r1) pour que Swing sache quoi repainter
            Rectangle union = r0.union(r1);
            return union;

        } catch (BadLocationException ex) {
            // fallback minimal : rien dessiner mais renvoyer la zone d’origine
            return bounds;
        }
    }
}
