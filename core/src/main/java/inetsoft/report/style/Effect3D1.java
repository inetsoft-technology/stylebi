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
package inetsoft.report.style;

import inetsoft.report.TableLens;

import java.awt.*;

/**
 * The Effect3D1 class is a table style class. It can be used with other
 * table lens classes to provide a visual style for table formatting and
 * printing. The default style for Effect3D1 is:<p>
 * <img src="images/Effect3D1.gif"><p>
 * Additional formatting for the last row and column may be available.
 * Please check the style guide or use StyleViewer to experiment with
 * the different settings of the style.
 *
 * @version 5.1, 9/20/2003
 * @auther InetSoft Technology Corp
 */
public class Effect3D1 extends TableStyle {
   /**
    * Create an empty style. The setTable() method must be called before
    * it can be used.
    */
   public Effect3D1() {
   }

   /**
    * Create a style to decorate the specified table.
    * @param table table lens.
    */
   public Effect3D1(TableLens table) {
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
         return r == getHeaderInnerRow() ? Color.gray : Color.lightGray;
      }

      /**
       * Return the color for drawing the column border lines.
       * @param r row number.
       * @param c column number.
       * @return ruling color.
       */
      @Override
      public Color getColBorderColor(int r, int c) {
         return c == getHeaderInnerCol() ? Color.gray : Color.lightGray;
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
         if((isFormatFirstRow() && r == getHeaderInnerRow() &&
            !isHeaderColFormat(c) && !isTrailerColFormat(c)) ||
            (isFormatLastRow() && r == getTrailerInnerRow() &&
            !isHeaderColFormat(c) && !isTrailerColFormat(c))) {
            return THIN_LINE;
         }

         return NO_BORDER;
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
         if((isFormatFirstCol() && c == getHeaderInnerCol() &&
            !isHeaderRowFormat(r) && !isTrailerRowFormat(r)) ||
            (isFormatLastCol() && c == getTrailerInnerCol() &&
            !isHeaderRowFormat(r) && !isTrailerRowFormat(r))) {
            return THIN_LINE;
         }

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

         return (isHeaderRowFormat(r) || isHeaderColFormat(c)) ?
            createFont(font, Font.BOLD) :
            font;
      }

      /**
       * Return the per cell foreground color. Return null to use default
       * color.
       * @param r row number.
       * @param c column number.
       * @return foreground color for the specified cell.
       */
      @Override
      public Color getForeground(int r, int c) {
         return isHeaderRowFormat(r) ?
            new Color(128, 0, 128) :
            ((isHeaderColFormat(c) && isTrailerRowFormat(r)) ?
            new Color(0, 0, 200) :
            table.getForeground(r, c));
      }
   }
}

