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
package inetsoft.web.binding.model.table;

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.model.*;

import java.awt.*;

public class TableFormatInfo extends FormatInfoModel {
   /**
    * Constructor.
    */
   public TableFormatInfo() {
   }

   /**
    * Constructor.
    */
   public TableFormatInfo(TableFormat tformat) {
      super(tformat);

      if(tformat == null) {
         return;
      }

      setSuppressIfZero(tformat.suppressIfZero);
      setSuppressIfDuplicate(tformat.suppressIfDuplicate);
      setLineWrap(tformat.linewrap);
   }

   /**
    * Get suppress if zero.
    * @return true if  suppressIfZero.
    */
   public boolean isSuppressIfZero() {
      return suppressIfZero;
   }

   /**
    * Set suppress if zero.
    * @param suppressIfZero the suppress if zero.
    */
   public void setSuppressIfZero(boolean suppressIfZero) {
      this.suppressIfZero = suppressIfZero;
   }

   /**
    * Get suppress if duplicate.
    * @return suppressIfZero.
    */
   public boolean isSuppressIfDuplicate() {
      return suppressIfDuplicate;
   }

   /**
    * Set suppress if duplicate.
    * @param suppressIfDuplicate the suppress if duplicate.
    */
   public void setSuppressIfDuplicate(boolean suppressIfDuplicate) {
      this.suppressIfDuplicate = suppressIfDuplicate;
   }

   /**
    * Get line wrap.
    * @return line wrap.
    */
   public boolean isLineWrap() {
      return linewrap;
   }

   /**
    * Set line wrap.
    * @param linewrap the line wrap.
    */
   public void setLineWrap(boolean linewrap) {
      this.linewrap = linewrap;
   }

   /**
    * Set formats to table Format.
    * @param tformat the table format.
    */
   public void toTableFormat(TableFormat tformat, TableFormat tableStyleFormat) {
      if(tformat.foreground != null || !"#000000".equals(getColor())) {
         Color newForeground = stringToColor(getColor());

         if(tformat.foreground != null || tableStyleFormat == null ||
            !Tool.equals(tableStyleFormat.foreground, newForeground))
         {
            tformat.foreground = newForeground;
         }
      }

      Color newBackground = stringToColor(getBackgroundColor());

      if(tformat.background != null || tableStyleFormat == null ||
         !Tool.equals(tableStyleFormat.background, newBackground))
      {
         tformat.background = newBackground;
      }

      if(getFont() != null) {
         FontInfo font = getFont();
         String fontType = font.getFontFamily() == null ? StyleFont.DEFAULT_FONT_FAMILY :
            font.getFontFamily();
         int size = font.getFontSize() == null ? 11 :
            Integer.parseInt(font.getFontSize());
         int style = "bold".equals(font.getFontWeight()) ? Font.BOLD : 0;
         style |= "italic".equals(font.getFontStyle()) ? Font.ITALIC : 0;
         style |= "underline".equals(font.getFontUnderline()) ? StyleFont.UNDERLINE : 0;
         style |= "strikethrough".equals(font.getFontStrikethrough()) ? StyleFont.STRIKETHROUGH : 0;
         Font newFont = new inetsoft.report.StyleFont(fontType, style, size);

         if(tformat.font != null || tableStyleFormat == null ||
            !Tool.equals(tableStyleFormat.font, newFont))
         {
            tformat.font = newFont;
         }
      }

      if(getFormat() != null) {
         tformat.format = FormatInfoModel.getDurationFormat(getFormat(), isDurationPadZeros());

         if(TableFormat.DATE_FORMAT.equals(tformat.format) &&
            !"Custom".equals(getDateSpec()))
         {
            tformat.format_spec = getDateSpec();
         }
         else if(getFormatSpec() != null) {
            tformat.format_spec = getFormatSpec();
         }
      }
      else {
         tformat.setFormat(null);
      }

      int align_h = 0;
      int align_v = 0;

      if(getAlign() != null) {
         AlignmentInfo align = getAlign();
         String halign = align.getHalign();

         if("Left".equals(halign)) {
            align_h = StyleConstants.H_LEFT;
         }
         else if("Center".equals(halign)) {
            align_h = StyleConstants.H_CENTER;
         }
         else if("Right".equals(halign)) {
            align_h = StyleConstants.H_RIGHT;
         }

         String valign = align.getValign();

         if("Top".equals(valign)) {
            align_v = StyleConstants.V_TOP;
         }
         else if("Middle".equals(valign)) {
            align_v = StyleConstants.V_CENTER;
         }
         else if("Bottom".equals(valign)) {
            align_v = StyleConstants.V_BOTTOM;
         }

         int newAlignment = align_h + align_v;

         if(tformat.alignment != null || tableStyleFormat == null ||
            !Tool.equals(tableStyleFormat.alignment, newAlignment))
         {
            tformat.alignment = valign == null && halign == null ? null : align_h + align_v;
         }
      }
      else {
         tformat.alignment = null;
      }

      tformat.suppressIfZero = isSuppressIfZero();
      tformat.suppressIfDuplicate = isSuppressIfDuplicate();
      tformat.linewrap = Boolean.valueOf(isLineWrap());
   }

   private Color stringToColor(String color) {
      if(color == null || color.length() == 0) {
         return null;
      }

      String colorReb = color.substring(1);
      int rgb= Integer.parseInt(colorReb, 16);
      return new Color(rgb);
   }

   private boolean suppressIfZero = false;
   private boolean suppressIfDuplicate = false;
   private boolean linewrap = true; // should match the default on table lens
}
