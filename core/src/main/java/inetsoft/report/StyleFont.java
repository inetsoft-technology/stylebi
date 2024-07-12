/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;

/**
 * StyleFont is an extended font class. It supported some additional
 * features such as: underline, strikethrough, small caps, all caps.
 * <p>
 * A font can be created using the styles defined in java.awt.Font and
 * in this class:<p>
 * <pre>
 *    StyleFont font = new StyleFont("Serif", Font.BOLD | StyleFont.UNDERLINE,
 *                                   10, StyleConstants.THIN_LINE);
 * </pre>
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class StyleFont extends Font implements StyleConstants, Cloneable {
   /**
    * Bit mask to get the AWT font style.
    */
   public static final int AWT_FONT_MASK = 0x03;
   /**
    * Bit mask to get the StyleFont extended style.
    */
   public static final int STYLE_FONT_MASK = 0xFFF0;
   /**
    * Underline font style. An optional line style can be specified.
    */
   public static final int UNDERLINE = 0x10;
   /**
    * Strikethrough font style. An optional line style can be specified.
    */
   public static final int STRIKETHROUGH = 0x20;
   /**
    * Superscript font style. Draw string at upper corner in small font.
    */
   public static final int SUPERSCRIPT = 0x40;
   /**
    * Subscript font style. Draw string at lower corner in small font.
    */
   public static final int SUBSCRIPT = 0x80;
   /**
    * Shadow font style.
    */
   public static final int SHADOW = 0x100;
   /**
    * Small caps font style draw lower case characters in a smaller
    * capital letter.
    */
   public static final int SMALLCAPS = 0x200;
   /**
    * All caps font style draw all characters in upper case.
    */
   public static final int ALLCAPS = 0x400;
   /**
    * The default font font family.
    */
   public static final String DEFAULT_FONT_FAMILY = "Default";
   /* not implemented now
    public static final int OUTLINE = 0x200;
    public static final int EMBOSE = 0x400;
    public static final int ENGRAVE = 0x800;
    */

   /**
    * Creates a new font with the specified name, style and point size.
    * @param name the font name
    * @param style the constant style used
    * @param size the point size of the font
    */
   public StyleFont(String name, int style, int size) {
      super(DEFAULT_FONT_FAMILY.equals(name) ? getDefaultFontFamily() : name,
            style & AWT_FONT_MASK, size);
      this.extstyle = style;
      this.defaultFont = DEFAULT_FONT_FAMILY.equals(name);
   }

   /**
    * Creates a new font with the specified name, style and point size.
    * The line style specifies the line used in UNDERLINE and STRIKETHROUGH
    * styles. It defaults to THIN_LINE.
    * @param name the font name
    * @param style the constant style used
    * @param size the point size of the font
    * @param linestyle line styles defined in StyleConstants.
    */
   public StyleFont(String name, int style, int size, int linestyle) {
      this(name, style, size, linestyle, linestyle);
   }

   /**
    * Creates a new font with the specified name, style and point size.
    * The line style specifies the line used in UNDERLINE and STRIKETHROUGH
    * styles. It defaults to THIN_LINE.
    * @param name the font name
    * @param style the constant style used
    * @param size the point size of the font
    * @param underline line styles defined in StyleConstants.
    * @param strikeline line styles defined in StyleConstants.
    */
   public StyleFont(String name, int style, int size, int underline, int strikeline) {
      this(DEFAULT_FONT_FAMILY.equals(name) ? getDefaultFontFamily() : name, style, size);
      this.underline = underline;
      this.strikeline = strikeline;
      this.defaultFont = DEFAULT_FONT_FAMILY.equals(name);
   }

   /**
    * Create a StyleFont from a font.
    */
   public StyleFont(Font font) {
      this(font.getName(), font.getStyle(), font.getSize(),
           (font instanceof StyleFont) ?
           ((StyleFont) font).getUnderlineStyle() : 0,
           (font instanceof StyleFont) ?
           ((StyleFont) font).getStrikelineStyle() : 0);
      this.defaultFont = isDefaultFont(font);
   }

   /**
    * Create a StyleFont from a font and fontName. Used for user fonts only
    */
   public StyleFont(Font font, String fontName) {
      super(font);
      this.name = fontName;
      this.defaultFont = isDefaultFont(font);
      this.userFontName = fontName;
   }

   /**
    * Get the style of the font.
    */
   @Override
   public int getStyle() {
      return extstyle;
   }

   /**
    * Get the line style used for underline.
    * @deprecated Use getUnderlineStyle or getStrikelineStyle instead.
    */
   @Deprecated
   public int getLineStyle() {
      return getUnderlineStyle();
   }

   /**
    * Get the line style used for underline.
    */
   public int getUnderlineStyle() {
      return underline;
   }

   /**
    * Get the line style used for strikethrough.
    */
   public int getStrikelineStyle() {
      return strikeline;
   }

   /**
    * Gets the specified font using the name passed in.
    * @param val the font name
    * @return font.
    */
   public static Font decode(String val) {
      if(val == null || val.length() == 0) {
         return null;
      }

      try {
         StyleFont font = fontcache.get(val);

         if(font != null) {
            return font;
         }

         String[] arr = Tool.split(val, '-');
         String name = null;
         int size = 12;
         int style = Font.PLAIN;
         int underline = 0, strikeline = 0;
         int idx = 0, styleIdx = val.length();
         String ustr = arr.length >= 2 ?
            arr[0] + '-' + arr[1].toUpperCase() : val;

         if((idx = ustr.indexOf("PLAIN")) >= 0) {
            styleIdx = Math.min(styleIdx, idx);
            style |= StyleFont.PLAIN;
         }

         if((idx = ustr.indexOf("BOLD")) >= 0) {
            styleIdx = Math.min(styleIdx, idx);
            style |= StyleFont.BOLD;
         }

         if((idx = ustr.indexOf("ITALIC")) >= 0) {
            styleIdx = Math.min(styleIdx, idx);
            style |= StyleFont.ITALIC;
         }

         if((idx = ustr.indexOf("UNDERLINE")) >= 0) {
            styleIdx = Math.min(styleIdx, idx);
            style |= StyleFont.UNDERLINE;
         }

         if((idx = ustr.indexOf("STRIKETHROUGH")) >= 0) {
            styleIdx = Math.min(styleIdx, idx);
            style |= StyleFont.STRIKETHROUGH;
         }

         if((idx = ustr.indexOf("SUPERSCRIPT")) >= 0) {
            styleIdx = Math.min(styleIdx, idx);
            style |= StyleFont.SUPERSCRIPT;
         }

         if((idx = ustr.indexOf("SUBSCRIPT")) >= 0) {
            styleIdx = Math.min(styleIdx, idx);
            style |= StyleFont.SUBSCRIPT;
         }

         if((idx = ustr.indexOf("SHADOW")) >= 0) {
            styleIdx = Math.min(styleIdx, idx);
            style |= StyleFont.SHADOW;
         }

         int small = -1;

         if((small = ustr.indexOf("SMALLCAPS")) >= 0) {
            styleIdx = Math.min(styleIdx, small);
            style |= StyleFont.SMALLCAPS;
         }

         int all = -1;

         // ALLCAPS is a substring of SMALLCAPS
         if((all = ustr.indexOf("ALLCAPS")) >= 0 && all != small + 2) {
            styleIdx = Math.min(styleIdx, all);
            style |= StyleFont.ALLCAPS;
         }

         if((style & StyleFont.UNDERLINE) != 0 ||
            (style & StyleFont.STRIKETHROUGH) != 0) {
            // for background compatibility, line styles may be written
            // as a single number or two separate number
            if(arr.length > 4) {
               try {
                  underline = decodeLineStyle(arr[arr.length - 2]);
                  strikeline = decodeLineStyle(arr[arr.length - 1]);
               }
               catch(Throwable ex) {
               }
            }

            if(underline == 0 && strikeline == 0) {
               underline = decodeLineStyle(arr[arr.length - 1]);
               strikeline = underline;
            }
         }

         if((style & StyleFont.STYLE_FONT_MASK) != 0) {
            // find size
            int sizeIdx = val.indexOf('-', styleIdx + 1);

            if(sizeIdx < 0) {
               LOG.warn("Invalid font, expected size: " + val);
               return null;
            }

            sizeIdx++;
            int size2Idx = val.indexOf('-', sizeIdx);

            size = Integer.parseInt((size2Idx < 0) ?
               val.substring(sizeIdx) :
               val.substring(sizeIdx, size2Idx));

            int nameIdx = val.lastIndexOf('-', styleIdx);

            if(nameIdx < 0) {
               LOG.warn("Invalid font, expected name: " + val);
               return null;
            }

            name = val.substring(0, nameIdx);
            name = Tool.replace(name, "/", "-");
            font = new StyleFont(name, style, size, underline, strikeline);
         }
         else {
            size = Integer.parseInt(arr[arr.length - 1]);
            name = arr[0];

            for(int i = 1; i < arr.length - 2; i++) {
               name += "-" + arr[i];
            }

            name = Tool.replace(name, "/", "-");
            font = new StyleFont(name, style, size);
         }

         fontcache.put(val, font);

         return font;
         // return new com.ms.awt.FontX(name, style, size); // commerceone
      }
      catch(Exception e) {
         LOG.error("Failed to decode font: " + val, e);
         return null;
      }
   }

   public String toString() {
      return toString(this);
   }

   /**
    * Convert a font object to its string representation.
    * @param font font object.
    * @return string representation, which can be converted back to a font
    * by using the decode() method.
    */
   public static String toString(Font font) {
      if(font == null) {
         return "null";
      }

      String name = StyleFont.isDefaultFont(font) ? StyleFont.DEFAULT_FONT_FAMILY : font.getName();
      name = Tool.replace(name, "-", "/");
      StringBuilder fstr = new StringBuilder(name);
      int style = font.getStyle();

      fstr.append("-");

      if(style == StyleFont.PLAIN) {
         fstr.append("PLAIN");
      }

      if((style & StyleFont.BOLD) != 0) {
         fstr.append("BOLD");
      }

      if((style & StyleFont.ITALIC) != 0) {
         fstr.append("ITALIC");
      }

      // @by larryl, optimization
      boolean isStyleFont = (style & StyleFont.STYLE_FONT_MASK) != 0;

      if(isStyleFont) {
         if((style & StyleFont.UNDERLINE) != 0) {
            fstr.append("UNDERLINE");
         }

         if((style & StyleFont.STRIKETHROUGH) != 0) {
            fstr.append("STRIKETHROUGH");
         }

         if((style & StyleFont.SUPERSCRIPT) != 0) {
            fstr.append("SUPERSCRIPT");
         }

         if((style & StyleFont.SUBSCRIPT) != 0) {
            fstr.append("SUBSCRIPT");
         }

         if((style & StyleFont.SHADOW) != 0) {
            fstr.append("SHADOW");
         }

         if((style & StyleFont.SMALLCAPS) != 0) {
            fstr.append("SMALLCAPS");
         }

         if((style & StyleFont.ALLCAPS) != 0) {
            fstr.append("ALLCAPS");
         }
      }

      fstr.append("-");
      fstr.append(font.getSize());

      if(isStyleFont && font instanceof StyleFont) {
         fstr.append("-");
         fstr.append(((StyleFont) font).getUnderlineStyle());
         fstr.append("-");
         fstr.append(((StyleFont) font).getStrikelineStyle());
      }

      return fstr.toString();
   }

   /**
    * Return the line style constant for the line style name.
    */
   public static int decodeLineStyle(String val) {
      if(val.equals("NO_BORDER")) {
         return StyleConstants.NO_BORDER;
      }
      else if(val.equals("ULTRA_THIN_LINE")) {
         return StyleConstants.ULTRA_THIN_LINE;
      }
      else if(val.equals("THIN_THIN_LINE")) {
         return StyleConstants.THIN_THIN_LINE;
      }
      else if(val.equals("THIN_LINE")) {
         return StyleConstants.THIN_LINE;
      }
      else if(val.equals("MEDIUM_LINE")) {
         return StyleConstants.MEDIUM_LINE;
      }
      else if(val.equals("THICK_LINE")) {
         return StyleConstants.THICK_LINE;
      }
      else if(val.equals("DOUBLE_LINE")) {
         return StyleConstants.DOUBLE_LINE;
      }
      else if(val.equals("RAISED_3D")) {
         return StyleConstants.RAISED_3D;
      }
      else if(val.equals("LOWERED_3D")) {
         return StyleConstants.LOWERED_3D;
      }
      else if(val.equals("DOUBLE_3D_RAISED")) {
         return StyleConstants.DOUBLE_3D_RAISED;
      }
      else if(val.equals("DOUBLE_3D_LOWERE")) {
         return StyleConstants.DOUBLE_3D_LOWERED;
      }
      else if(val.equals("DOT_LINE")) {
         return StyleConstants.DOT_LINE;
      }
      else if(val.equals("DASH_LINE")) {
         return StyleConstants.DASH_LINE;
      }
      else if(val.equals("MEDIUM_DASH")) {
         return StyleConstants.MEDIUM_DASH;
      }
      else if(val.equals("LARGE_DASH")) {
         return StyleConstants.LARGE_DASH;
      }

      return Integer.parseInt(val);
   }

   /**
    * Compare if the font object describes the same font.
    * @return true if the two fonts are equivalent.
    */
   public boolean equals(Object obj) {
      if(obj == this) {
         return true;
      }

      if(!super.equals(obj)) {
         return false;
      }

      if(obj instanceof StyleFont) {
         StyleFont font = (StyleFont) obj;
         return (extstyle == font.extstyle) && (underline == font.underline) &&
            (strikeline == font.strikeline);
      }

      return (extstyle & STYLE_FONT_MASK) == 0;
   }

   /**
    * Make a copy of this font.
    */
   @Override
   public Object clone() {
      return new StyleFont(this);
   }

   /**
    * Get display text.
    * @return the text of the label display.
    */
   public static String getDisplayText(Font font) {
      String name = font.getName();
      name = Tool.replace(name, "-", "/");
      StringBuilder fstr = new StringBuilder(name);
      int style = font.getStyle();

      fstr.append("-");

      if(style == StyleFont.PLAIN) {
         fstr.append(Catalog.getCatalog().getString("PLAIN"));
      }

      if((style & StyleFont.BOLD) != 0) {
         fstr.append(Catalog.getCatalog().getString("BOLD"));
      }

      if((style & StyleFont.ITALIC) != 0) {
         fstr.append(Catalog.getCatalog().getString("ITALIC"));
      }

      boolean isStyleFont = (style & StyleFont.STYLE_FONT_MASK) != 0;

      if(isStyleFont) {
         if((style & StyleFont.UNDERLINE) != 0) {
            fstr.append(Catalog.getCatalog().getString("UNDERLINE"));
         }

         if((style & StyleFont.STRIKETHROUGH) != 0) {
            fstr.append(Catalog.getCatalog().getString("STRIKETHROUGH"));
         }

         if((style & StyleFont.SUPERSCRIPT) != 0) {
            fstr.append(Catalog.getCatalog().getString("SUPERSCRIPT"));
         }

         if((style & StyleFont.SUBSCRIPT) != 0) {
            fstr.append(Catalog.getCatalog().getString("SUBSCRIPT"));
         }

         if((style & StyleFont.SHADOW) != 0) {
            fstr.append(Catalog.getCatalog().getString("SHADOW"));
         }

         if((style & StyleFont.SMALLCAPS) != 0) {
            fstr.append(Catalog.getCatalog().getString("SMALLCAPS"));
         }

         if((style & StyleFont.ALLCAPS) != 0) {
            fstr.append(Catalog.getCatalog().getString("ALLCAPS"));
         }
      }

      fstr.append("-");
      fstr.append(font.getSize());

      if(isStyleFont && font instanceof StyleFont) {
         fstr.append("-");
         fstr.append(((StyleFont) font).getUnderlineStyle());
         fstr.append("-");
         fstr.append(((StyleFont) font).getStrikelineStyle());
      }

      return fstr.toString();
   }

   @Override
   public Font deriveFont(int style) {
      StyleFont nfont = new StyleFont(getName(), style, getSize(), underline, strikeline);
      nfont.underline = underline;
      nfont.strikeline = strikeline;
      nfont.defaultFont = defaultFont;

      if((style & UNDERLINE) != 0 && underline == 0) {
         nfont.underline = StyleConstants.THIN_LINE;
      }
      else if((style & STRIKETHROUGH) != 0 && strikeline == 0) {
         nfont.strikeline = StyleConstants.THIN_LINE;
      }

      return nfont;
   }

   /**
    * Override deriveFont method. For super.deriveFont() will return a Font type
    * but we should return a StyleFont.
    * @return a StyleFont.
    */
   @Override
   public Font deriveFont(float size) {
      StyleFont nfont = new StyleFont(getName(), getStyle(), (int) (size + 0.5),
         underline, strikeline);
      nfont.underline = underline;
      nfont.strikeline = strikeline;
      nfont.defaultFont = defaultFont;
      nfont.pointSize = size;

      return nfont;
   }

   @Override
   public String getFamily() {
      if(userFontName != null) {
         return userFontName;
      }

      return super.getFamily();
   }

   @Override
   public String getFontName() {
      if(userFontName != null) {
         return userFontName;
      }

      return super.getFontName();
   }

   @Override
   public String getFamily(Locale l) {
      if(userFontName != null) {
         return userFontName;
      }

      return super.getFamily(l);
   }

   @Override
   public String getFontName(Locale l) {
      if(userFontName != null) {
         return userFontName;
      }

      return super.getFontName(l);
   }

   // Default font implementation -
   // A default font is a font where the font family is controlled by a property. A default
   // font is created by using 'Default' font as the font family in the constructor. The
   // font family will be replaced with the real font name, so the Font.getName() returns
   // the real font family name. While the string representation of the font, StyleFont.toString()
   // returns 'Default' as the font name. To check if a font is a default font, use the
   // StyleFont.isDefaultFont().

   /**
    * Check if the font is a default font, where the font family is controlled dynamically
    * by the 'default.font.family' property.
    */
   public static boolean isDefaultFont(Font font) {
      return font instanceof StyleFont && ((StyleFont) font).defaultFont;
   }

   public static String getDefaultFontFamily() {
      return SreeEnv.getProperty("default.font.family", "Roboto");
   }

   private int extstyle;
   private int underline = 0;
   private int strikeline = 0;
   private boolean defaultFont = false;
   private String userFontName;
   private static final Map<String, StyleFont> fontcache = new Hashtable<>();
   private static final Logger LOG = LoggerFactory.getLogger(StyleFont.class);

   // jdk1.2 bug, must call this to initialize the fonts, otherwise some
   // fonts are not found even they are on the system (barcode, etc..)
   static {
      Tool.getToolkit().getFontList();
   }
}
