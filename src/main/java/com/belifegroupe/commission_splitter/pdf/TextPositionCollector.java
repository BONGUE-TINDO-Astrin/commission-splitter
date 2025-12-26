package com.belifegroupe.commission_splitter.pdf;

import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Collecte les "glyphes" (caractères) avec leurs coordonnées (x,y).
 * Cela permet de reconstruire les lignes et les tokens sans dépendre des espaces.
 */
public class TextPositionCollector extends PDFTextStripper {

    public static class Glyph {
        public final String ch;
        public final float x;
        public final float y;
        public final float width;

        public Glyph(String ch, float x, float y, float width) {
            this.ch = ch;
            this.x = x;
            this.y = y;
            this.width = width;
        }
    }

    private final List<Glyph> glyphs = new ArrayList<>();

    public TextPositionCollector() throws IOException {
        setSortByPosition(true);
    }

//    public List<Glyph> getGlyphs() {
//        return glyphs;
//    }
//
//    public void reset() {
//        glyphs.clear();
//    }

    @Override
    protected void processTextPosition(TextPosition text) {
        String unicode = text.getUnicode();
        if (unicode == null || unicode.isEmpty()) return;

        glyphs.add(new Glyph(
                unicode,
                text.getXDirAdj(),
                text.getYDirAdj(),
                text.getWidthDirAdj()
        ));
    }

}
