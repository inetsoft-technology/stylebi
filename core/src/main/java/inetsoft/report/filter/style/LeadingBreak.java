/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.filter.style;

import inetsoft.report.TableLens;
import inetsoft.report.filter.GroupedTable;

import java.awt.*;

/**
 * Leading break style.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class LeadingBreak extends GroupStyle {
   /**
    * Create an empty style. The setTable() method must be called before
    * it can be used.
    */
   public LeadingBreak() {
   }

   /**
    * Create a style to decorate the specified table.
    * @param table table lens.
    */
   public LeadingBreak(GroupedTable table) {
      super(table);
   }

   /**
    * Create a style to decorate the table.
    * @return a style lens.
    */
   @Override
   protected TableLens createStyle(TableLens tbl) {
      return new Style();
   }

   /**
    * Style lens.
    */
   class Style extends Transparent {
      /**
       * Return the color for drawing the row border lines.
       * @param r row number.
       * @param c column number.
       * @return ruling color.
       */
      @Override
      public Color getRowBorderColor(int r, int c) {
         return Color.black;
      }

      /**
       * Return the color for drawing the column border lines.
       * @param r row number.
       * @param c column number.
       * @return ruling color.
       */
      @Override
      public Color getColBorderColor(int r, int c) {
         return Color.black;
      }

      /**
       * Return the style for bottom border of the specified cell. The flag
       * must be one of the style options defined in the StyleConstants
       * class. If the row number is -1, it's checking the outside ruling
       * on the top.
       * @param r row number.
       * @param c column number.
       * @return ruling flag.
       */
      @Override
      public int getRowBorder(int r, int c) {
         return (r == -1 || r == lastRow()) ?
            MEDIUM_LINE :
            ((gtable.isSummaryRow(r) && gtable.getObject(r, 1) == null &&
            gtable.isSummaryCol(c)) ?
            THIN_LINE :
            ((r == 0 && isFormatFirstRow() ||
            gtable.isGroupHeaderRow(r) &&
            (gtable.getObject(r, 0) != null ||
             gtable.getObject(r, c) != null) ||
            gtable.isSummaryRow(r + 1)) ?
            DASH_LINE :
            NO_BORDER));
      }

      /**
       * Return the style for right border of the specified row. The flag
       * must be one of the style options defined in the StyleConstants
       * class. If the column number is -1, it's checking the outside ruling
       * on the left.
       * @param r row number.
       * @param c column number.
       * @return ruling flag.
       */
      @Override
      public int getColBorder(int r, int c) {
         return NO_BORDER;
      }

      /**
       * Return the per cell alignment.
       * @param r row number.
       * @param c column number.
       * @return cell alignment.
       */
      @Override
      public int getAlignment(int r, int c) {
         return (H_LEFT | V_CENTER);
      }

      /**
       * Return the per cell font. Return null to use default font.
       * @param r row number.
       * @param c column number.
       * @return font for the specified cell.
       */
      @Override
      public Font getFont(int r, int c) {
         Font font = table.getFont(r, c);

         if(r == 0 && isFormatFirstRow() || gtable.isSummaryRow(r)) {
            return createFont(font, Font.BOLD);
         }

         return font;
      }
   }
}

