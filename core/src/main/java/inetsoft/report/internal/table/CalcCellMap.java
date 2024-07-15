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

import inetsoft.report.lens.CalcTableLens;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * This class holds the information to look up cells in a calc table using
 * cells names.
 */
public class CalcCellMap {
   /**
    * Create a cell name to location mapping.
    */
   public CalcCellMap(RuntimeCalcTableLens calc) {
      this.calc = calc;
   }
   
   /**
    * Build the mapping.
    */
   private void buildNameMap() {
      CalcTableLens base = calc.getCalcTableLens();

      namemap = new HashMap<>();

      if(cache == null) {
         cache = new Hashtable(); 
      
         for(int i = 0; i < base.getRowCount(); i++) {
            for(int j = 0; j < base.getColCount(); j++) {
               String name = base.getCellName(i, j);

               if(name != null) {
                  cache.put(new Point(i, j), name);
               }
            }
         }
      }
      
      int ncols = calc.getColCount(); // optimization
      Point loc = new Point();
      
      for(int i = 0; i < calc.getRowCount(); i++) {
         int row = calc.getRow(i);

         for(int j = 0; j < ncols; j++) {
            int col = calc.getCol(j);
            loc.x = row;
            loc.y = col;
            String name = (String) cache.get(loc);

            if(name != null) {
               addCell(name, i, j);

               if(base.getExpansion(row, col) == CalcTableLens.EXPAND_NONE) {
                  CalcCellContext context = calc.getCellContext(i, j);

                  if(context != null) {
                     addCell(name + "::" + context.getIdentifier(), i, j);
                  }
               }
            }
         }
      }
   }

   /**
    * Add a cell location mapping.
    */
   private void addCell(String name, int row, int col) {
      List<Point> locs = namemap.get(name);
      
      if(locs == null) {
         locs = new ArrayList<>();
         namemap.put(name, locs);
      }
      
      locs.add(new Point(col, row));
   }

   /**
    * Clear and rebuild mapping.
    */
   public synchronized void reset() {
      namemap = null;
   }

   /**
    * Get all cell names.
    */
   public synchronized Collection<String> getCellNames() {
      if(namemap == null) {
         buildNameMap();
      }

      return namemap.keySet();
   }
   
   /**
    * Get all cell locations with the name.
    */
   public synchronized Point[] getLocations(String name) {
      if(namemap == null) {
         buildNameMap();
      }
      
      List<Point> vec = namemap.get(name);
      
      if(vec == null) {
         return new Point[0];
      }
      
      return (Point[]) vec.toArray(new Point[vec.size()]);
   }

   /**
    * Get all cell locations with the name and share the same context.
    */
   public Point[] getLocations(String name, CalcCellContext context) {
      return getLocations(name + "::" + context.getIdentifier());
   }

   void clearCache() {
      cache = null;
   }

   private Map<String,List<Point>> namemap = null; // name -> Vector of CellLoc
   private RuntimeCalcTableLens calc;
   private transient Map cache = null;
}
