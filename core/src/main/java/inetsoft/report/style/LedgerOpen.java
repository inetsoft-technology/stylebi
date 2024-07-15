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

/**
 * The LedgerOpen class is a table style class. It can be used with other
 * table lens classes to provide a visual style for table formatting and
 * printing. The default style for LedgerOpen is:<p>
 * <img src="images/LedgerOpen.gif"><p>
 * Additional formatting for the last row and column may be available.
 * Please check the style guide or use StyleViewer to experiment with
 * the different settings of the style.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class LedgerOpen extends TableStyle {
   /**
    * Create an empty style. The setTable() method must be called before
    * it can be used.
    */
   public LedgerOpen() {
   }

   /**
    * Create a style to decorate the specified table.
    * @param table table lens.
    */
   public LedgerOpen(TableLens table) {
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
         return (isHeaderRowFormat(r) || isTrailerRowFormat(r + 1)) ?
            MEDIUM_LINE :
            ((r == -1 || r == lastRow()) ? NO_BORDER : THIN_LINE);
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
         return (c >= 0 && c < lastCol()) ? THIN_LINE : NO_BORDER;
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
   }
}

