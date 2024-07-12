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
package inetsoft.web.composer.tablestyle;

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.style.XTableStyle;
import inetsoft.web.adhoc.model.AlignmentInfo;
import inetsoft.web.adhoc.model.FontInfo;

import java.awt.*;

public class BodyRegionFormat implements TableStyleRegionFormat {
   public BodyRegionFormat() {
   }

   public BodyRegionFormat(XTableStyle tableStyle, String region) {
      super();

      this.region = region;
      Object rowBorder = tableStyle.get(getAttributeKey(getRowBorderKey()));
      Object rowBorderColor = tableStyle.get(getAttributeKey(getRowBorderColorKey()));
      Object colBorder = tableStyle.get(getAttributeKey(getColBorderKey()));
      Object colBorderColor = tableStyle.get(getAttributeKey(getColBorderColorKey()));
      Object background = tableStyle.get(getAttributeKey("background"));
      Object foreground = tableStyle.get(getAttributeKey("foreground"));
      Object font = tableStyle.get(getAttributeKey("font"));
      Object alignment = tableStyle.get(getAttributeKey("alignment"));
      setRowBorder(rowBorder instanceof Integer ? (Integer) rowBorder : -1);
      setColBorder(colBorder instanceof Integer ? (Integer) colBorder : -1);
      setRowBorderColor(getColorString(rowBorderColor));
      setColBorderColor(getColorString(colBorderColor));
      setBackground(getColorString(background));
      setForeground(getColorString(foreground));
      setFont(font instanceof Font ? new FontInfo((Font) font) : new FontInfo(defFont));
      setAlignment(alignment instanceof Integer ? new AlignmentInfo((Integer) alignment) :
         new AlignmentInfo(StyleConstants.FILL));
   }

   @Override
   public void updateTableStyle(XTableStyle tableStyle) {
      if(tableStyle != null) {
         Object background = getColor(this.background);
         Object foreground = getColor(this.foreground);
         Object rowBorderColor = getColor(this.rowBorderColor);
         Object colBorderColor = getColor(this.colBorderColor);
         tableStyle.put(getAttributeKey("background"), background);
         tableStyle.put(getAttributeKey("foreground"), foreground);
         tableStyle.put(getAttributeKey(getColBorderKey()), this.colBorder >= 0 ? this.colBorder : null);
         tableStyle.put(getAttributeKey(getRowBorderKey()), this.rowBorder >= 0 ? this.rowBorder : null);
         tableStyle.put(getAttributeKey(getRowBorderColorKey()), rowBorderColor);
         tableStyle.put(getAttributeKey(getColBorderColorKey()), colBorderColor);
         tableStyle.put(getAttributeKey("alignment"), this.alignment.toAlign() == 0 ?
            null : this.alignment.toAlign());
         tableStyle.put(getAttributeKey("font"), this.font == null || defFont.equals(this.font.toFont()) ?
            null : this.font.toFont());
      }
   }

   protected String getRowBorderKey() {
      return "row-border";
   }

   protected String getRowBorderColorKey() {
      return "rcolor";
   }

   protected String getColBorderKey() {
      return "col-border";
   }

   protected String getColBorderColorKey() {
      return "ccolor";
   }

   public String getRegion() {
      return region;
   }

   public void setRegion(String region) {
      this.region = region;
   }

   public String getForeground() {
      return foreground;
   }

   public void setForeground(String foreground) {
      this.foreground = foreground;
   }

   public String getBackground() {
      return background;
   }

   public void setBackground(String background) {
      this.background = background;
   }

   public FontInfo getFont() {
      return font;
   }

   public void setFont(FontInfo font) {
      this.font = font;
   }

   public AlignmentInfo getAlignment() {
      return alignment;
   }

   public void setAlignment(AlignmentInfo alignment) {
      this.alignment = alignment;
   }

   public int getRowBorder() {
      return rowBorder;
   }

   public void setRowBorder(int rowBorder) {
      this.rowBorder = rowBorder;
   }

   public String getRowBorderColor() {
      return rowBorderColor;
   }

   public void setRowBorderColor(String rowBorderColor) {
      this.rowBorderColor = rowBorderColor;
   }

   public int getColBorder() {
      return colBorder;
   }

   public void setColBorder(int colBorder) {
      this.colBorder = colBorder;
   }

   public String getColBorderColor() {
      return colBorderColor;
   }

   public void setColBorderColor(String colBorderColor) {
      this.colBorderColor = colBorderColor;
   }

   private String region;
   private String foreground;
   private String background;
   private FontInfo font;
   private AlignmentInfo alignment;
   private int rowBorder;
   private String rowBorderColor;
   private int colBorder;
   private String colBorderColor;

   public static StyleFont defFont = new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 11);
}
