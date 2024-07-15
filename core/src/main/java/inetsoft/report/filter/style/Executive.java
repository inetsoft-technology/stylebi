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
import inetsoft.report.painter.PresenterPainter;
import inetsoft.report.painter.ShadowPresenter;

import java.awt.*;

/**
 * Executive style displays group and summary headers in 3D shadows.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Executive extends GroupStyle {
   /**
    * Create an empty style. The setTable() method must be called before
    * it can be used.
    */
   public Executive() {
   }

   /**
    * Create a style to decorate the specified table.
    * @param table table lens.
    */
   public Executive(GroupedTable table) {
      super(table);
   }

   /**
    * Set the shading color. The shading is the area where the text is
    * drawn.
    * @param shading shading color.
    */
   public void setShading(Color shading) {
      this.shading = shading;
   }

   /**
    * Get the shading color.
    * @return shading color.
    */
   public Color getShading() {
      return shading;
   }

   /**
    * Set the text color.
    * @param textcolor color of the text.
    */
   public void setTextColor(Color textcolor) {
      textC = textcolor;
   }

   /**
    * Get the text color.
    * @return text color.
    */
   public Color getTextColor() {
      return textC;
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
            ((r == 0 && isFormatFirstRow()) ? DASH_LINE : NO_BORDER);
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

         if(r == 0 && isFormatFirstRow()) {
            return createFont(font, Font.BOLD);
         }

         return font;
      }

      /**
       * Return the value at the specified cell.
       * @param r row number.
       * @param c column number.
       * @return the value at the location.
       */
      @Override
      public Object getObject(int r, int c) {
         Object obj = table.getObject(r, c);

         if(obj != null &&
            (gtable.isSummaryRow(r) || gtable.isGroupHeaderRow(r) ||
             gtable.hasGrandSummary() && !gtable.moreRows(r + 1) &&
             c > gtable.getGroupColCount())) {
            ShadowPresenter shadow = new ShadowPresenter();
            Font font = table.getFont(r, c);

            shadow.setFont(createFont(font, Font.BOLD));
            shadow.setShading(shading);
            shadow.setTextColor(textC);
            return new PresenterPainter(obj, shadow);
         }

         return obj;
      }
   }

   Color shading = Color.white;
   Color textC = Color.black;
}

