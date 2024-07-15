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
 * This class is used to specify the location of an event. It can be either 
 * row, column or cell.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class EventPoint extends Point {
   /**
    * Use factory method for creating point only to avoid confusion between
    * (x, y) and (row, col).
    */
   private EventPoint(int x, int y) {
      super(x, y);
   }
   
   /**
    * Create an event location from a point. The x in the point is used as
    * the column index, and y is used as the row index.
    */
   public static EventPoint at(Point pt) {
      return (pt == null) ? null : new EventPoint(pt.x, pt.y);
   }
   
   /**
    * Return a location for row.
    * @param r row index.
    */
   public static EventPoint row(int r) {
      return new EventPoint(-1, r);
   }
   
   /**
    * Return a location for column.
    * @param c column index.
    */
   public static EventPoint column(int c) {
      return new EventPoint(c, -1);
   }
   
   /**
    * Return a location for cell.
    * @param r row index.
    * @param c column index.
    */
   public static EventPoint cell(int r, int c) {
      return new EventPoint(c, r);
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
}

