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
package inetsoft.report.event;

import java.awt.*;

/**
 * This class is used to specify the range of cells in an event. 
 *
 * @version 7.0, 2/28/2005
 * @author InetSoft Technology Corp
 */
public class EventRange extends Rectangle {
   /**
    * Create a cell range.
    */
   public EventRange(Rectangle rect) {
      super(rect);
   }
   
   /**
    * Get the row index of the event location.
    */
   public int getRow() {
      return y;
   }

   /**
    * Set the row index of the event location.
    */
   public void setRow(int r) {
      y = r;
   }
   
   /**
    * Get the column index of the event location.
    */
   public int getColumn() {
      return x;
   }
   
   /**
    * Set the column index of the event location.
    */
   public void setColumn(int c) {
      x = c;
   }

   /**
    * Get the number of rows in the cell range.
    */
   public int getRowCount() {
      return height;
   }
   
   /**
    * Set the number of rows in the cell range.
    */
   public void setRowCount(int nrows) {
      height = nrows;
   }

   /**
    * Get the number of column in the cell range.
    */
   public int getColCount() {
      return width;
   }
   
   /**
    * Set the number of columns in the cell range.
    */
   public void setColCount(int ncols) {
      width = ncols;
   }
}
