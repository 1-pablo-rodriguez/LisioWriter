package writer.ui.editor;

import javax.swing.text.*;

@SuppressWarnings("serial")
public final class WrapEditorKit extends StyledEditorKit {
    private final ViewFactory defaultFactory = new WrapColumnFactory();
    @Override public ViewFactory getViewFactory() { return defaultFactory; }

    static final class WrapColumnFactory implements ViewFactory {
        @Override public View create(Element elem) {
            String kind = elem.getName();
            if (kind != null) {
                if (kind.equals(AbstractDocument.ContentElementName)) return new WrapLabelView(elem);
                if (kind.equals(AbstractDocument.ParagraphElementName)) return new ParagraphView(elem);
                if (kind.equals(AbstractDocument.SectionElementName)) {
                    // Insets par défaut = 0, pas besoin de setInsets(...)
                    return new BoxView(elem, View.Y_AXIS);
                }
                if (kind.equals(StyleConstants.ComponentElementName)) return new ComponentView(elem);
                if (kind.equals(StyleConstants.IconElementName))       return new IconView(elem);
            }
            return new LabelView(elem);
        }
    }

    /** Autorise le retour à la ligne : largeur min = 0 sur l’axe X. */
    static final class WrapLabelView extends LabelView {
        WrapLabelView(Element elem) { super(elem); }
        @Override public float getMinimumSpan(int axis) {
            return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
        }
    }
}
