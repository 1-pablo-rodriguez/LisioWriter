package writer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;

import writer.ui.NormalizingTextPane;

/**
 * Utilitaire pour installer un caret coloré et plus épais.
 * Fonctionne sur JTextArea / JTextPane (writer.ui.NormalizingTextPane).
 */
public final class CaretStyler {

    private CaretStyler() {}

    /**
     * Installe un caret « barre » coloré, d'épaisseur et blink-rate configurables.
     *
     * @param comp       le composant texte (JTextArea, JTextPane…)
     * @param color      couleur du caret (ex: new Color(255,120,120))
     * @param widthPx    épaisseur en pixels (ex: 2)
     * @param blinkMs    clignotement en ms (ex: 500). 0 = pas de clignotement.
     */
    public static void install(writer.ui.NormalizingTextPane comp, Color color, int widthPx, int blinkMs) {
        if (comp == null) return;
        comp.setCaretColor(color);

        DefaultCaret custom = new DefaultCaret() {
            private static final long serialVersionUID = 1L;

            // Force la zone à redessiner à la bonne largeur
            @Override
            protected synchronized void damage(Rectangle r) {
                if (r == null) return;
                x = r.x;
                y = r.y;
                width = Math.max(1, widthPx);
                height = r.height;
                repaint();
            }

            @SuppressWarnings("deprecation")
			@Override
            public void paint(Graphics g) {
                if (!isVisible()) return;
                try {
                	writer.ui.NormalizingTextPane c = (NormalizingTextPane) getComponent();

                    // Essaye d'abord Java 9+ (modelToView2D)
                    Rectangle r;
                    try {
                        java.awt.geom.Rectangle2D r2 = c.modelToView2D(getDot()).getBounds2D();
                        r = new Rectangle((int) r2.getX(), (int) r2.getY(), (int) r2.getWidth(), (int) r2.getHeight());
                    } catch (Throwable t) {
                        // Fallback Java 8 (modelToView)
                        r = c.modelToView(getDot());
                    }

                    if (r == null) return;
                    g.setColor(c.getCaretColor());
                    int w = Math.max(1, widthPx);
                    g.fillRect(r.x, r.y, w, r.height);
                } catch (BadLocationException e) {
                    // ignore
                }
            }
        };

        custom.setBlinkRate(Math.max(0, blinkMs));
        comp.setCaret(custom);
    }
}
