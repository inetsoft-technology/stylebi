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
package inetsoft.uql.asset.internal;

import inetsoft.uql.XTable;
import inetsoft.util.swap.XSwappableObjectList;

/**
 * Objects 2D array.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
class Object2DArray {
   Object2DArray(XSwappableObjectList data) {
      this.data = data;
   }

   Object2DArray(XTable table) {
      this.table = table;
   }

   Object[] getRow(int r) {
      if(data != null) {
         return (Object[]) data.get(r);
      }

      if(table != null) {
         if(table.moreRows(r)) {
            Object[] row = new Object[table.getColCount()];

            for(int i = 0; i < row.length; i++) {
               row[i] = table.getObject(r, i);
            }

            return row;
         }
      }

      return null;
   }

   int getRowCount() {
      if(data != null) {
         return data.size();
      }

      if(table != null) {
         table.moreRows(table.EOT);
         return table.getRowCount();
      }

      return -1;
   }

   private XSwappableObjectList data;
   private XTable table;
}