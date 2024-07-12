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
package inetsoft.report.io.rtf;

import inetsoft.report.StyleFont;

import javax.swing.text.html.HTML;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * RichTextFont is an extended font class. It supported some additional
 * features such as: h1, h2, h3, h4, h5, h6, indent, decrease indent,
 * ordered list, unordered list.
 *
 * @version 10.2, 7/21/2009
 * @author InetSoft Technology Corp
 */
public class RichTextFont extends StyleFont {
   /**
    * Create a RichTextFont object.
    * @param font a font
    */
   public RichTextFont(Font font) {
      super(font);
   }

   /**
    * Create a RichTextFont object.
    * @param fontName font name
    * @param style font style
    * @param size font size
    */
   public RichTextFont(String fontName, int style, int size) {
      super(fontName, style, size);
   }

   /**
    * Indicates whether or not this Font object's style is PLAIN.
    */
   @Override
   public boolean isPlain() {
      return super.isPlain();
   }

   /**
    * Indicates whether or not this Font object's style is BOLD.
    */
   @Override
   public boolean isBold() {
      return super.isBold();
   }

   /**
    * Indicates whether or not this Font object's style is ITALIC.
    */
   @Override
   public boolean isItalic() {
      return super.isItalic();
   }

   /**
    * Checks whether the underline attribute is set.
    */
   public boolean isUnderline() {
      return isUnderline;
   }

   /**
    * Set underline.
    */
   public void setUnderline(boolean isUnderline) {
      this.isUnderline = isUnderline;
   }

   /**
    * Checks whether the strikethrough attribute is set.
    */
   public boolean isStrikethrough() {
      return isStrikethrough;
   }

   /**
    * Set strikethrough.
    */
   public void setStrikethrough(boolean isStrikethrough) {
      this.isStrikethrough = isStrikethrough;
   }

   /**
    * Checks whether the superscript attribute is set.
    */
   public boolean isSuperscript() {
      return isSuperscript;
   }

   /**
    * Set superscript.
    */
   public void setSuperscript(boolean isSuperscript) {
      this.isSuperscript = isSuperscript;
   }

   /**
    * Checks whether the subscript attribute is set.
    */
   public boolean isSubscript() {
      return isSubscript;
   }

   /**
    * Set subscript.
    */
   public void setSubscript(boolean isSubscript) {
      this.isSubscript = isSubscript;
   }

   /**
    * Returns the logical name of this Font.
    */
   @Override
   public String getName() {
      return super.getName();
   }

   /**
    * Returns the point size of this Font, rounded to an integer.
    */
   @Override
   public int getSize() {
      return super.getSize();
   }

   /**
    * Set the text's header, such as h1, h2...
    * @param header HTML.Tag
    */
   public void setHeader(HTML.Tag header) {
      this.header = header;
   }

   /**
    * Returns the HTML.Tag.
    */
   public HTML.Tag getHeader() {
      return header;
   }

   /**
    * Set the text's order type, such as circle, square...
    * @param liType order type
    */
   public void setLIType(String liType) {
      this.liType = liType;
   }

   /**
    * Returns the order type.
    */
   public String getLIType() {
      return liType;
   }

   /**
    * Derives a new font with the new size.
    */
   @Override
   public RichTextFont deriveFont(float size) {
      RichTextFont font = new RichTextFont(getName(), getStyle(), (int) size);
      font.isUnderline = isUnderline;
      font.isStrikethrough = isStrikethrough;
      font.isSuperscript = isSuperscript;
      font.isSubscript = isSubscript;
      font.header = header;
      font.liType = liType;
      return font;
   }

   private boolean isUnderline;
   private boolean isStrikethrough;
   private boolean isSuperscript;
   private boolean isSubscript;
   private HTML.Tag header;
   private String liType;
   public static final String DECIMAL = "decimal";
   public static final String DISC = "disc";
   public static final String CIRCLE = "circle";
   public static final String SQUARE = "square";
   public static final String LOWER_ROMAN = "lower-roman";
   public static final String UPPER_ROMAN = "upper-roman";
   public static final String LOWER_ALPHA = "lower-alpha";
   public static final String UPPER_ALPHA = "upper-alpha";
   public static final Map<HTML.Tag, Integer> headers = new HashMap<>();

   static {
      headers.put(HTML.Tag.H1, 24);
      headers.put(HTML.Tag.H2, 18);
      headers.put(HTML.Tag.H3, 14);
      headers.put(HTML.Tag.H4, 12);
      headers.put(HTML.Tag.H5, 10);
      headers.put(HTML.Tag.H6, 8);
   }
}
