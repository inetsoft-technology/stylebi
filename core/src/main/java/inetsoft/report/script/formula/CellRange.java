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
package inetsoft.report.script.formula;

import inetsoft.uql.XTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collection;

/**
 * This interface parses cell range specification and provides methods for
 * retrieving the range as array.
 *
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public abstract class CellRange implements Serializable, Cloneable {
   /**
    * Get all cells in the range.
    * @param position true to return cell position (Point), false to return value.
    */
   public abstract Collection getCells(XTable table, boolean position)
         throws Exception;

   /**
    * Get cells values in the range.
    */
   public Collection getCells(XTable table) throws Exception {
      return getCells(table, false);
   }

   /**
    * Adjust the row and column index when row/col is moved.
    * @param row row position before the insert/remove.
    * @param col column position before the insert/remove.
    * @param rdiff amount row is moved. Negative is moving up.
    * @param cdiff amount column is moved. Negative is moving left.
    */
   public abstract void adjustIndex(int row, int col, int rdiff, int cdiff);

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Parse a cell range specification.
    */
   public static CellRange parse(String spec) throws Exception {
      spec = spec.trim();

      if(spec.startsWith("[") && spec.endsWith("]")) {
         return new PositionalCellRange(spec);
      }
      else {
         return new NamedCellRange(spec);
      }
   }

   /**
    * Get the default collection value. If there is a single value in the
    * collection, return the value. If the collection is empty, returns null.
    * Otherwise return the values as an array.
    */
   public Object getCollectionValue(Collection cells) {
      return getCollectionValue(cells, false);
   }

   /**
    * Get the default collection value. If there is a single value in the
    * collection, return the value. If the collection is empty, returns null.
    * Otherwise return the values as an array.
    */
   public Object getCollectionValue(Collection cells, boolean calc) {
      Object[] arr = cells.toArray();

      // fix bug1306222603153 return the values as an array when is a calc table
      if(arr.length == 1 && !calc) {
         return arr[0];
      }

      return (arr.length == 0) ? null : arr;
   }

   public void setProcessCalc(boolean calc) {
      this.processCalc = calc;
   }

   protected boolean processCalc = false;
   private static final Logger LOG = LoggerFactory.getLogger(CellRange.class);
}
