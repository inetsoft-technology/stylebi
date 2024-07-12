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
package inetsoft.util.css;

import inetsoft.report.StyleFont;
import inetsoft.uql.viewsheet.BorderColors;

import java.awt.*;
import java.util.Map;

/**
 * VSCSSStyle specify a style that can be applied to assemblies of viewsheet.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class CSSStyle {
   /**
    * The Constructor that creates a null style, meaning no style will be
    * applied until explicitly specified.
    */
   public CSSStyle() {
      background = null;
      foreground = null;
      font = null;
      borders = null;
      bcolors = null;
      trans = 100;
      borderRadius = 0;
   }

   /**
    * Sets the style of the background.
    * @param bg the background color to be used by this style.
    */
   public void setBackground(Color bg) {
      background = bg;
      bgDefined = true;
   }

   /**
    * Returns the background specified by this style.
    * @return the background this style holds, null if not specified.
    */
   public Color getBackground() {
      return background;
   }

   /**
    * Sets the style of the foreground.
    * @param fg the foreground color to be used by this style.
    */
   public void setForeground(Color fg) {
      foreground = fg;
      fgDefined = true;
   }

   /**
    * Returns the foreground specified by this style.
    * @return the forground this style holds, null if not specified.
    */
   public Color getForeground() {
      return foreground;
   }

   /**
    * Sets the style of the font.
    * @param f the font to be used by this style.
    */
   public void setFont(StyleFont font) {
      this.font = font;
      fontDefined = true;
   }

   /**
    * Returns the font specified by this style.
    * @return the font this style holds, null if not specified.
    */
   public StyleFont getFont() {
      return font;
   }

   /**
    * Get the borders.
    * @return the borders of this format.
    */
   public Insets getBorders() {
      return borders;
   }

   /**
    * Set the borders to this format.
    * @param borders the specified borders.
    */
   public void setBorders(Insets borders) {
      this.borders = borders;
      borderDefined = true;
   }

   /**
    * Get the border colors.
    * @return the border colors of this format.
    */
   public BorderColors getBorderColors() {
      return bcolors;
   }

   /**
    * Set the border colors to this format.
    * @param bcolors the specified border colors.
    */
   public void setBorderColors(BorderColors bcolors) {
      this.bcolors = bcolors;
      bcolorDefined = true;
   }

   public int getBorderRadius() {
      return borderRadius;
   }

   public void setBorderRadius(int radius) {
      this.borderRadius = radius;
      borderRadiusDefined = true;
   }

   /**
    * Check if should wrap text.
    * @return <tt>true</tt> if should wrap text, <tt>false</tt> otherwise.
    */
   public boolean isWrapping() {
      return wrap;
   }

   /**
    * Set whether should wrap text.
    * @param wrap <tt>true</tt> if should wrap text, <tt>false</tt> otherwise.
    */
   public void setWrapping(boolean wrap) {
      this.wrap = wrap;
      wrappingDefined = true;
   }

   /**
    * Get the alignment (horizontal and vertical).
    * @return the alignment of this format.
    */
   public int getAlignment() {
      return align;
   }

   /**
    * Set the alignment (horizontal and vertical) to this format.
    * @param align the specified alignment.
    */
   public void setAlignment(int align) {
      this.align = align;
      alignmentDefined = true;
   }

   /**
    * Get alpha.
    */
   public int getAlpha() {
      return trans;
   }

   /**
    * Set alpha.
    */
   public void setAlpha(int trans) {
      this.trans = trans;
      transDefined = true;
   }

   /**
    * Get custom properties map
    */
   public Map<String, String> getCustomProperties() {
      return customProperties;
   }

   /**
    * Set custom properties map
    */
   public void setCustomProperties(Map<String, String> customProperties) {
      this.customProperties = customProperties;
   }

   public Insets getPadding() {
      return padding;
   }

   public void setPadding(Insets padding) {
      this.padding = padding;
      paddingDefined = true;
   }

   public int getWidth() {
      return width;
   }

   public void setWidth(int width) {
      this.width = width;
      widthDefined = true;
   }

   public int getHeight() {
      return height;
   }

   public void setHeight(int height) {
      this.height = height;
      heightDefined = true;
   }

   public boolean isVisible() {
      return visible;
   }

   public void setVisible(boolean visible) {
      this.visible = visible;
      visibleDefined = true;
   }

   /**
    * Get the string representation.
    * @return the string representation of this object.
    */
   public String toString() {
      return "CSSStyle[" + foreground + ", " + background + ", " +
         StyleFont.toString(font) + ", " + align + ", " + borders + ", " +
         bcolors + ", " + wrap + ", " + borderRadius + "]";
   }

   /**
    * Check if alpha is defined in css.
    */
   public boolean isAlphaDefined() {
      return transDefined;
   }

   /**
    * Check if alignment is defined in css.
    */
   public boolean isAlignmentDefined() {
      return alignmentDefined;
   }

   /**
    * Check if wrapping is defined in css.
    */
   public boolean isWrappingDefined() {
      return wrappingDefined;
   }

   /**
    * Check if foreground is defiend.
    */
   public boolean isForegroundDefined() {
      return fgDefined;
   }

   /**
    * Check if background is defiend.
    */
   public boolean isBackgroundDefined() {
      return bgDefined;
   }

   /**
    * Check if font is defiend.
    */
   public boolean isFontDefined() {
      return fontDefined;
   }

   /**
    * Check if border is defiend.
    */
   public boolean isBorderDefined() {
      return borderDefined;
   }

   /**
    * Check if border color is defiend.
    */
   public boolean isBorderColorDefined() {
      return bcolorDefined;
   }

   public boolean isBorderRadiusDefined() {
      return borderRadiusDefined;
   }

   public boolean isPaddingDefined() {
      return paddingDefined;
   }

   public boolean isWidthDefined() {
      return widthDefined;
   }

   public boolean isHeightDefined() {
      return heightDefined;
   }

   public boolean isVisibleDefined() {
      return visibleDefined;
   }

   private Color background;
   private Color foreground;
   private StyleFont font;
   private int align;
   private Insets borders;
   private BorderColors bcolors;
   private int trans;
   private boolean wrap;
   private int borderRadius;
   private Insets padding;
   private int width;
   private int height;
   private boolean visible;
   private boolean wrappingDefined = false;
   private boolean alignmentDefined = false;
   private boolean fgDefined = false;
   private boolean bgDefined = false;
   private boolean fontDefined = false;
   private boolean borderDefined = false;
   private boolean bcolorDefined = false;
   private boolean transDefined = false;
   private boolean borderRadiusDefined = false;
   private boolean paddingDefined = false;
   private boolean widthDefined = false;
   private boolean heightDefined = false;
   private boolean visibleDefined = false;
   private Map<String, String> customProperties;
}
