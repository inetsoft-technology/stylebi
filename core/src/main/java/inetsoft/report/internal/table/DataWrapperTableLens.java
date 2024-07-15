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

import inetsoft.report.TableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.SubTableLens;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * A table lens that ensures that there are no duplicate header names
 */
public class DataWrapperTableLens extends AttributeTableLens {
   public DataWrapperTableLens(TableLens lens) {
      super(getOneHeaderRowTable(lens));
      Map<String, Integer> headerCountMap = new HashMap<>();

      for(int c = 0; c < lens.getColCount(); c++) {
         String header = Util.getHeader(lens, c).toString();
         // replace special characters to make the header valid
         header = header.replaceAll("[()/]", "_");
         Integer count = headerCountMap.get(header);
         count = count == null ? 0 : count + 1;
         headerCountMap.put(header, count);
         header = Util.getDupHeader(header, count).toString();
         setColHeader(c, header);

         if(count > 0) {
            setObject(0, c, header);
         }
      }

      int headerCnt = lens.getHeaderRowCount();
      final int diff = (headerCnt < 1) ? 0 : headerCnt - 1;

      // copy data from span cell to all covered cells.
      for(int r = headerCnt; lens.moreRows(r); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            Dimension span = lens.getSpan(r, c);

            if(span != null) {
               Object v = lens.getObject(r, c);

               if(v != null) {
                  for(int r2 = 0; r2 < span.height; r2++) {
                     for(int c2 = 0; c2 < span.width; c2++) {
                        // dup headers copied in to previous loop
                        if(r != 0 && (r2 != 0 || c2 != 0)) {
                           setObject(r + r2 - diff, c + c2, v);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   // get a table lens with only one header row, and ignore the other headers
   private static TableLens getOneHeaderRowTable(TableLens tbl) {
      int headerCnt = tbl.getHeaderRowCount();

      if(headerCnt < 2 && tbl.getTrailerRowCount() == 0) {
         return tbl;
      }

      final int diff = headerCnt - 1;
      return new SubTableLens(tbl, null, null) {
         @Override
         protected int getR(int r) {
            if(r < 1) {
               return r;
            }

            return r + diff;
         }

         @Override
         public int getRowCount() {
            return super.getRowCount() - diff;
         }

         // calc table may have more than one header row, but when used as data source,
         // we should only use the first row as header
         @Override
         public int getHeaderRowCount() {
            return Math.min(1, super.getHeaderRowCount());
         }

         @Override
         public int getTrailerRowCount() {
            return 0;
         }
      };
   }

   @Override
   public Dimension getSpan(int r, int c) {
      return null;
   }

   @Override
   public Class getColType(int col) {
      Class type = columnTypes.get(col);

      if(type == null) {
         type = Util.getColType(getTable(), col, String.class, 1000, true);
         columnTypes.put(col, type);
      }

      return type;
   }
}
