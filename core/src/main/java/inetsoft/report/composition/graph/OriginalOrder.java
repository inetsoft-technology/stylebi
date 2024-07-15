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
package inetsoft.report.composition.graph;

import inetsoft.graph.data.DataSet;
import inetsoft.report.Comparer;
import inetsoft.report.TableLens;

import java.util.HashMap;
import java.util.Map;

/**
 * Order the rows into the original order before sorting.
 *
 * @author InetSoft Technology Corp.
 * @since  10.0
 */
public class OriginalOrder implements Comparer {
   /**
    * Create an ordering of the column based on the base table.
    */
   public OriginalOrder(TableLens table, int col) {
      for(int i = 1; table.moreRows(i); i++) {
         Object v = table.getObject(i, col);

         if(!map.containsKey(v)) {
            map.put(v, map.size());
         }
      }
   }

   public OriginalOrder(DataSet table, int col) {
      for(int i = 0; i < table.getRowCount(); i++) {
         Object v = table.getData(col, i);

         if(!map.containsKey(v)) {
            map.put(v, map.size());
         }
      }
   }

   /**
    * Compare the values and return the order from the original table.
    */
   @Override
   public int compare(Object v1, Object v2) {
      Integer n1 = map.get(v1);
      Integer n2 = map.get(v2);

      if(n1 == null || n2 == null) {
         return 0;
      }
      
      return n1.intValue() - n2.intValue();
   }

   @Override
   public int compare(double v1, double v2) {
      return 0;
   }

   @Override
   public int compare(float v1, float v2) {
      return 0;
   }

   @Override
   public int compare(long v1, long v2) {
      return 0;
   }

   @Override
   public int compare(int v1, int v2) {
      return 0;
   }

   @Override
   public int compare(short v1, short v2) {
      return 0;
   }

   private Map<Object, Integer> map = new HashMap<>();
}
