package Import.odt.style;

public class TextStyle {
    public boolean bold;
    public boolean italic;
    public boolean underline;
    public boolean subscript;   // indice
    public boolean superscript; // exposant

    public TextStyle() {}

    public TextStyle(boolean b, boolean i, boolean u) {
        this.bold = b; this.italic = i; this.underline = u;
    }

    public void or(TextStyle other) {
        if (other == null) return;
        bold       |= other.bold;
        italic     |= other.italic;
        underline  |= other.underline;
        subscript  |= other.subscript;
        superscript|= other.superscript;
    }

    public static TextStyle copy(TextStyle t) {
        if (t == null) return new TextStyle();
        TextStyle r = new TextStyle(t.bold, t.italic, t.underline);
        r.subscript = t.subscript;
        r.superscript = t.superscript;
        return r;
    }

    public static TextStyle or(TextStyle a, TextStyle b) {
        TextStyle r = copy(a);
        if (b != null) r.or(b);
        return r;
    }

    public boolean isEmpty() {
        return !bold && !italic && !underline && !subscript && !superscript;
    }
    
    // Petit helper local pour dupliquer un TextStyle (tous les attributs)
    private static TextStyle copyTS(TextStyle s) {
        if (s == null) return new TextStyle();
        TextStyle r = new TextStyle(s.bold, s.italic, s.underline);
        r.subscript   = s.subscript;
        r.superscript = s.superscript;
        return r;
    }

    public static TextStyle merge(TextStyle a, TextStyle b) {
        if (a == null) return copyTS(b);
        if (b == null) return copyTS(a);
        TextStyle r = new TextStyle(a.bold || b.bold, a.italic || b.italic, a.underline || b.underline);
        r.subscript   = a.subscript   || b.subscript;
        r.superscript = a.superscript || b.superscript;
        return r;
    }


}
