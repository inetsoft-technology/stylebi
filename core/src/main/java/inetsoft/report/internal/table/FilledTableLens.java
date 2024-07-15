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
package inetsoft.report.internal.table;

import inetsoft.report.TableLens;
import inetsoft.report.internal.*;
import inetsoft.report.internal.binding.*;
import inetsoft.report.lens.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;

import java.util.*;

/**
 * Create a table with the specified columns. Fill in the missing
 * columns with null.
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class FilledTableLens extends SubTableLens {
   /**
    * @param headers the column headers (including empty columns).
    * @param included the name of columns to include in this table lens.
    */
   public FilledTableLens(TableLens tbl, String[] headers, Set included, SubColumns subcols) {
      super(tbl, null, null);
      this.headers = headers;
      this.included = included;
      this.subcols = subcols;
      this.base = tbl;
      init();
   }

   /**
    * Get the columns that are included in this table.
    */
   public SubColumns getSubColumns() {
      return subcols;
   }

   /**
    * Initialize mapping.
    */
   private void init() {
      TableLens tbl = getTable();
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(tbl, true);
      mapping = new int[headers != null ? headers.length : getColCount()];
      cols = new int[mapping.length];

      for(int i = 0; i < mapping.length; i++) {
         if(headers != null) {
            cols[i] = Util.findColumn(columnIndexMap, headers[i]);

            if(cols[i] < 0 && headers[i].startsWith("Column [")) {
               cols[i] = -1;
               Set<Map.Entry<Object, Integer>> entrySet = columnIndexMap.getIdentifier2EntrySet();

               if(entrySet != null) {
                  for(Map.Entry<Object, Integer> entry : entrySet) {
                     String identifier = entry.getKey() == null ? "" : entry.getKey().toString();

                     if(identifier != null  &&
                        (headers[i].equals(identifier) || identifier.endsWith("." + headers[i])))
                     {
                        cols[i] = entry.getValue();
                        break;
                     }
                  }
               }
            }

            mapping[i] = included == null || included.contains(headers[i]) ? cols[i] : -1;
         }
         else {
            mapping[i] = included == null || included.contains(tbl.getObject(0, i)) ? i : -1;
            cols[i] = i;
         }
      }
   }

   @Override
   public String getColumnIdentifier(int col) {
      if(cols[col] < 0) {
         return headers[col];
      }

      return super.getColumnIdentifier(col);
   }

   @Override
   public Class getColType(int col) {
      if(cols[col] < 0) {
         return String.class;
      }

      return super.getColType(col);
   }

   @Override
   public Object getObject(int row, int col) {
      if(row == 0 && headers != null) {
         return headers[col];
      }

      // @by: Chris Spagnoli bug1423535634052 2015-3-25
      // bounds checking to prevent exception
      if(col >= mapping.length) {
         return null;
      }

      if(row > 0 && mapping[col] < 0) {
         return null;
      }

      return super.getObject(row, col);
   }

   /**
    * Add a table to be joined with this table as part of applyFilters.
    * This is used to support different grouping for discrete measures
    */
   public void addJoin(TableLens tbl, int[] joinCols, BindingAttr runtime) {
      joins.add(new JoinInfo(tbl, joinCols, runtime));
   }

   private static class JoinInfo {
      public JoinInfo(TableLens table, int[] cols, BindingAttr runtime) {
         this.table = table;
         this.joinColumns = cols;
         this.runtime = runtime;
      }

      TableLens table;
      int[] joinColumns;
      BindingAttr runtime;
   }

   private Set included;
   private int[] mapping;
   private String[] headers;
   private TableLens base;
   private transient SubColumns subcols;
   private List<JoinInfo> joins = new ArrayList();
}
