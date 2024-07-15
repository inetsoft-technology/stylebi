/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.report.pdf;

import inetsoft.report.internal.FontMetrics2;

import java.awt.*;
import java.io.Serializable;
import java.util.*;

/**
 * FontInfo contains font information. It is used when embedding font
 * in a PDF file. The font information is extracted from font files.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
abstract class FontInfo implements Serializable {
   /**
    * Get the font name.
    */
   public String getFontName() {
      return fontName;
   }

   /**
    * Get the font full name.
    */
   public String getFullName() {
      return fullName;
   }

   /**
    * Get the font family name.
    */
   public String getFamilyName() {
      return familyName;
   }

   /**
    * Get the font weight.
    */
   public String getWeight() {
      return weight;
   }

   /**
    * Check if the font is fixed font.
    */
   public boolean isFixedPitch() {
      return fixedPitch;
   }

   /**
    * Get the font ascent.
    */
   public int getAscent() {
      return ascender;
   }

   /**
    * Get the font descent.
    */
   public int getDescent() {
      return descender;
   }

   /**
    * Get the maximum advance of the characters in the font.
    */
   public int getMaxAdvance() {
      return advance;
   }

   /**
    * Get the line gap.
    */
   public int getLeading() {
      return lineGap;
   }

   /**
    * Get the character width for the first 255 characters.
    */
   public short[] getWidths() {
      return widths;
   }

   /**
    * Get the kerning information. The hashtable key is the two character
    * pair, and the value is the corresponding kerning value.
    */
   public Map getKern() {
      return pairKern;
   }

   /**
    * Get the font italic angle.
    */
   public double getItalicAngle() {
      return italicAngle;
   }

   /**
    * Get the font bounding box.
    */
   public Rectangle getFontBBox() {
      return bbox;
   }

   /**
    * Get the font encoding scheme.
    */
   public String getEncoding() {
      return encoding;
   }

   /**
    * Get the capital letter height.
    */
   public int getCapHeight() {
      return capHeight;
   }

   /**
    * Returns the total advance width for showing the specified String
    * in this Font.
    * The advance width is the amount by which the current point is
    * moved from one character to the next in a line of text.
    * @param str the String to be measured
    * @return    the advance width of the specified string
    *                  in the font described by this font metric.
    */
   public float stringWidth(String str, int size) {
      int w = 0;
      int length = str.length();
      int end = length - 1;
      boolean hasPair = pairKern.size() > 0;

      // accumulate width
      for(int i = 0; i < length; i++) {
         int c = str.charAt(i);
         w += getWidth(c);

         if(hasPair && i < end && pairLeft.get(c)) {
            Number kern = (Number) pairKern.get(str.substring(i, i + 2));

            if(kern != null) {
               w += kern.intValue();
            }
         }
      }

      return w * size / 1000.0f;
   }

   /**
    * Get a FontMetrics object.
    * @param font font, must match the font in this font info.
    */
   public FontMetrics getFontMetrics(Font font) {
      return new FontMetrics(font);
   }

   /**
    * Get the character width.
    */
   public int getWidth(int idx) {
      return (idx >= 0 &&
              idx < widths.length && widths[idx] > 0) ?
         widths[idx] :
         ((idx >= 0x300 && idx <= 0x320) ? 0 : widths[0]);
   }

   /**
    * Map font info to font metrics.
    */
   class FontMetrics extends java.awt.FontMetrics implements FontMetrics2 {
      public FontMetrics(Font font) {
         super(font);
         this.size = font.getSize();
      }

      @Override
      public int getAscent() {
         // descender is negative
         return (int) Math.ceil(getHeight() * ascender /
            (ascender - descender));
      }

      @Override
      public int getDescent() {
         return (int) Math.ceil(getHeight() * -descender /
            (ascender - descender));
      }

      @Override
      public int getMaxAdvance() {
         return (int) Math.ceil(FontInfo.this.getMaxAdvance() * size / 1000.0);
      }

      @Override
      public int getHeight() {
         return (int) Math.ceil(Math.max((bbox.height) * size / 1000.0,
            (ascender - descender) * size / 1000.0));
      }

      @Override
      public int charWidth(char ch) {
         return (int) Math.ceil(FontInfo.this.getWidth(ch) * size / 1000.0);
      }

      @Override
      public int stringWidth(String str) {
         return (int) Math.ceil(FontInfo.this.stringWidth(str, size));
      }

      @Override
      public float stringWidth2(String str) {
         return FontInfo.this.stringWidth(str, size);
      }

      @Override
      public int[] getWidths() {
         int[] nwidths = new int[widths.length];

         for(int ch = 0; ch < 256; ch++) {
            nwidths[ch] = widths[ch];
         }

         return nwidths;
      }

      int size;
   }

   protected String fontName, fullName, familyName, weight;
   protected String encoding;
   protected boolean fixedPitch;
   protected double italicAngle;
   protected int ascender, descender;
   protected int lineGap;
   protected short[] widths;
   protected int advance;			// max advance
   protected int capHeight;
   protected Rectangle bbox;
   protected Map pairKern = new TreeMap();
   protected BitSet pairLeft = new BitSet();
}

