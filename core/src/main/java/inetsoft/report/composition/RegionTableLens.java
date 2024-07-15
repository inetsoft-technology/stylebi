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
package inetsoft.report.composition;

import inetsoft.report.TableLens;

import java.awt.*;

/**
 * Region table lens, the sub region of a table lens.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class RegionTableLens extends VSTableLens {
   /**
    * Constructor.
    */
   public RegionTableLens(TableLens table, int rcount, int ccount) {
      super(table);

      if(rcount < 0 || ccount < 0) {
         throw new RuntimeException("Invalid rcount or ccount found: " +
                                    rcount + ", " + ccount + "!");
      }

      this.rcount = rcount;
      this.ccount = ccount;

      // validate row count and col count
      this.rcount = Math.min(table.getRowCount(), this.rcount);
      this.ccount = Math.min(table.getColCount(), this.ccount);

      // set column widths for numLines checking
      if(table instanceof VSTableLens) {
         int[] widths = ((VSTableLens) table).getColumnWidths();
         int[] heights = ((VSTableLens) table).getRowHeights();
         this.setColumnWidths(widths);
         this.setRowHeights(heights);
         // export excel need link selections.
         this.setLinkSelections(((VSTableLens) table).getLinkSelections());
      }
   }

   /**
    * Set the base table lens.
    * @param table the specified base table.
    */
   @Override
   public void setTable(TableLens table) {
      super.setTable(table);
      table.moreRows(TableLens.EOT);

      if(rcount < 0 || ccount < 0) {
         return;
      }

      // validate row count and col count
      rcount = Math.min(table.getRowCount(), rcount);
      ccount = Math.min(table.getColCount(), ccount);
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return rcount;
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return ccount;
   }

   /**
    * Check if there are more rows. The row index is the row that will be
    * accessed. This method must block until the row is available, or
    * return false if the row does not exist in the table. This method is
    * used to iterate through the table, and allow partial table to be
    * accessed in report processing.
    * @param row row number.
    * @return true if the row exists, or false if no more rows.
    */
   @Override
   public boolean moreRows(int row) {
      return row < rcount;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return Math.min(rcount, getTable().getHeaderRowCount());
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return Math.min(ccount, getTable().getHeaderColCount());
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      int tindex = getTable().getRowCount() - getTable().getTrailerRowCount();
      int count = rcount - tindex;

      count = Math.min(count, getTable().getTrailerRowCount());
      return Math.max(0, count);
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      int tindex = getTable().getColCount() - getTable().getTrailerColCount();
      int count = ccount - tindex;

      count = Math.min(count, getTable().getTrailerColCount());
      return Math.max(0, count);
   }

   /**
    * Return the spanning setting for the cell. If the specified cell
    * is not a spanning cell, it returns null. Otherwise it returns
    * a Dimension object with Dimension.width equals to the number
    * of columns and Dimension.height equals to the number of rows
    * of the spanning cell.
    * @param r row number.
    * @param c column number.
    * @return span cell dimension.
    */
   @Override
   public Dimension getSpan(int r, int c) {
      Dimension span = getTable().getSpan(r, c);

      if(span == null) {
         return span;
      }

      int width = Math.min(ccount - c, span.width);
      int height = Math.min(rcount - r, span.height);
      Dimension span2 = new Dimension(width, height);

      return width == 1 && height == 1 ? null : span2;
   }

   private int rcount = -1;
   private int ccount = -1;
}
