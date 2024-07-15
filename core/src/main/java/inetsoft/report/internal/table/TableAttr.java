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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;

import java.io.Serializable;
import java.util.*;

/**
 * Table attr contains some table lens related attributes, which are used to
 * decorate a table lens.
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public abstract class TableAttr implements XMLSerializable, Serializable, Cloneable {
   /**
    * Get col index of a header in table.
    * @param header the specified header
    * @return col index of the specified header, null not found
    */
   public static int[] getColumns(TableLens table, String header) {
      int col = TableTool.getCol(table, header);

      if(col < 0) {
         return null;
      }

      TableDataDescriptor desc = table.getDescriptor();

      if(desc.getType() == TableDataDescriptor.CALC_TABLE) {
         RuntimeCalcTableLens rcalc = (RuntimeCalcTableLens)
            Util.getNestedTable(table, RuntimeCalcTableLens.class);

         // @by larryl, for calc table, the col from getCol() is the original
         // column index in CalcTableLens. Needs to find the real indices in
         // runtime calc table.
         if(rcalc != null) {
            Vector vec = new Vector();

            for(int i = 0; i < rcalc.getColCount(); i++) {
               if(rcalc.getCol(i) == col) {
                  vec.add(Integer.valueOf(i));
               }
            }

            int[] arr = new int[vec.size()];

            for(int i = 0; i < arr.length; i++) {
               arr[i] = ((Integer) vec.get(i)).intValue();
            }

            return arr;
         }
      }

      return new int[] { col };
   }

   /**
    * Find path by name.
    * @param path the specified table data path.
    * @param paths the available table data paths.
    */
   protected final TableDataPath findPathByName(TableDataPath path,
                                                Collection paths)
   {
      if(path == null) {
         return null;
      }

      Iterator<TableDataPath> iterator = paths.iterator();
      int type = path.getType();
      String[] parr = path.getPath();

      OUTER:
      while(iterator.hasNext()) {
         TableDataPath path2 = iterator.next();

         if(path2.getType() != type || path2.isCol() != path.isCol() ||
            path2.isRow() != path.isRow())
         {
            continue;
         }

         // we may consider comparing the last header only
         String[] parr2 = path2.getPath();

         if(parr.length != parr2.length) {
            continue;
         }

         for(int i = 0; i < parr.length; i++) {
            if(!Tool.equals(parr[i], parr2[i])) {
               continue OUTER;
            }
         }

         return path2;
      }

      return null;
   }

   /**
    * Create filter from a table lens. TableAttr will apply the attributes
    * to the created filter.
    * @param table the base table lens
    */
   public abstract TableLens createFilter(TableLens table);
}
