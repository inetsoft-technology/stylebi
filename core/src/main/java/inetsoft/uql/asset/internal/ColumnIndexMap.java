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

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.Util;
import inetsoft.uql.XTable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;

import java.util.*;

/**
 *  This class is used to store the column index information into a hashmap. When you
 *  need to get the column index in a nested loop, please use this class to collect the
 *  column indexes before using them in the loop to avoid performance issues caused by
 *  nested loops (see Bug #49611, the ws table has 4000+ columns).
 *
 *  And here we support fuzzy and regular way to collect column indexes information.
 *  Fuzzy should be used when try to find column index by Util.findColumn function.
 *  Regular should be used when try to find column index by AssetUtil.findColumn function.
 *
 *  Ideally we should use only one way to search for a column index, but the fuzzy and regular
 *  ways have already been accummulated over the years, discarding the existing logic is a bit
 *  risky, so just keep the logic and centralizing in this class to help us refactoring/cleaning
 *  it up in the future.
 */
public class ColumnIndexMap {
   public ColumnIndexMap() {
      super();
   }

   public ColumnIndexMap(XTable table) {
      this(table, false);
   }

   public ColumnIndexMap(XTable table, boolean fuzzy) {
      super();

      this.table = table;
      init(fuzzy);
   }

   public static ColumnIndexMap createColumnIndexMap(VSTableLens lens) {
      TableLens dataTable = lens.getTable() instanceof TableFilter ?
         Util.getDataTable((TableFilter) lens.getTable()) : lens.getTable();
      return new ColumnIndexMap(dataTable, true);
   }

   protected void init(boolean fuzzy) {
      if(table == null || table.getColCount() == 0) {
         return;
      }

      if(fuzzy) {
         initFuzzyColumnIndexMap();
      }
      else {
         initColumnIndexMap();
      }
   }

   /**
    *  Store the following information into column index map.
    *  IDENTIFIER -> map
    *                   column identifier -> col index
    *  IDENTIFIER_LOWSER_CASE -> map
    *                   column lowercase_identifier -> col index
    *  "header" -> map
    *                   column header -> col index
    *  "lowercase_header" -> map
    *                   column lowercase_header -> col index
    *
    *   This function was used to replace AssetUtil.findColumn function.
    */
   public void initColumnIndexMap() {
      int colCount = table.getColCount();

      for(int i = 0; i < colCount; i++) {
         String identifier = table.getColumnIdentifier(i);

         if(identifier != null) {
            if(map.get(IDENTIFIER) == null) {
               map.put(IDENTIFIER, new HashMap(colCount));
            }

            if(!map.get(IDENTIFIER).containsKey(identifier)) {
               map.get(IDENTIFIER).put(identifier, i);
            }

            if(map.get(IDENTIFIER_LOWSER_CASE) == null) {
               map.put(IDENTIFIER_LOWSER_CASE, new HashMap(colCount));
            }

            identifier = identifier.toLowerCase();

            if(!map.get(IDENTIFIER_LOWSER_CASE).containsKey(identifier)) {
               map.get(IDENTIFIER_LOWSER_CASE).put(identifier, i);
            }
         }

         String header = AssetUtil.format(XUtil.getHeader(table, i));

         if(header != null) {
            if(map.get(FORMAT_HEADER) == null) {
               map.put(FORMAT_HEADER, new HashMap(colCount));
            }

            if(!map.get(FORMAT_HEADER).containsKey(header)) {
               map.get(FORMAT_HEADER).put(header, i);
            }

            if(map.get(FORMAT_HEADER_LOWSER_CASE) == null) {
               map.put(FORMAT_HEADER_LOWSER_CASE, new HashMap(colCount));
            }

            header = header.toLowerCase();

            if(!map.get(FORMAT_HEADER_LOWSER_CASE).containsKey(header)) {
               map.get(FORMAT_HEADER_LOWSER_CASE).put(header, i);
            }
         }
      }
   }

   /**
    * Store the following information into column index map.
    *  IDENTIFIER -> map
    *                   column identifier -> col index
    *  IDENTIFIER_LOWSER_CASE -> map
    *                   column lowercase_identifier -> col index
    *  "header" -> map
    *                   column header -> col index
    *  "lowercase_header" -> map
    *                   column lowercase_header -> col index
    *
    *   This function should be called before using Util.findColumn function.
    */
   private void initFuzzyColumnIndexMap() {
      int colCount = table.getColCount();

      for(int i = 0; i < colCount; i++) {
         String identifier = table.getColumnIdentifier(i);

         if(identifier != null) {
            if(map.get(IDENTIFIER) == null) {
               map.put(IDENTIFIER, new HashMap(colCount));
            }

            if(!map.get(IDENTIFIER).containsKey(identifier)) {
               map.get(IDENTIFIER).put(identifier, i);
            }
         }
      }

      XTable innerMostFilter = getInnerMostTableFilter(table);

      if(innerMostFilter == null) {
         return;
      }

      for(int i = 0; i < innerMostFilter.getColCount(); i++) {
         String identifier0 = innerMostFilter.getColumnIdentifier(i);

         if(identifier0 != null) {
            if(map.get(IDENTIFIER2) == null) {
               map.put(IDENTIFIER2, new HashMap(colCount));
            }

            if(!map.get(IDENTIFIER2).containsKey(identifier0)) {
               map.get(IDENTIFIER2).put(identifier0, i);
            }
         }

         Object val = innerMostFilter.getObject(0, i);
         val = (val == null) ? "" : val;

         if(map.get(HEADER) == null) {
            map.put(HEADER, new HashMap(colCount));
         }

         if(!map.get(HEADER).containsKey(val)) {
            map.get(HEADER).put(val, i);
         }

         if(map.get(STRING_HEADER) == null) {
            map.put(STRING_HEADER, new HashMap(colCount));
         }

         String header = val.toString();

         if(!map.get(STRING_HEADER).containsKey(header)) {
            map.get(STRING_HEADER).put(header, i);
         }

         if(map.get(STRING_HEADER_LOWSER_CASE) == null) {
            map.put(STRING_HEADER_LOWSER_CASE, new HashMap(colCount));
         }

         header = header.toLowerCase();

         if(!map.get(STRING_HEADER_LOWSER_CASE).containsKey(header)) {
            map.get(STRING_HEADER_LOWSER_CASE).put(header, i);
         }

         if(map.get(FORMAT_HEADER) == null) {
            map.put(FORMAT_HEADER, new HashMap(colCount));
         }

         header = Tool.toString(val);

         if(!map.get(FORMAT_HEADER).containsKey(header)) {
            map.get(FORMAT_HEADER).put(header, i);
         }

         if(map.get(FORMAT_HEADER_LOWSER_CASE) == null) {
            map.put(FORMAT_HEADER_LOWSER_CASE, new HashMap(colCount));
         }

         header = header.toLowerCase();

         if(!map.get(FORMAT_HEADER_LOWSER_CASE).containsKey(header)) {
            map.get(FORMAT_HEADER_LOWSER_CASE).put(header, i);
         }
      }
   }

   private XTable getInnerMostTableFilter(XTable table) {
      XTable innerMostFilter = table;

      // @by larryl, check case where header row is hidden, we should check
      // if the column order is changed in filter, but currently the only
      // know use for hidden header row is in section binding, which does
      // not use column mapping. To handle column mapping requires a reverse
      // column index mapping to be built here and used on result
      while(innerMostFilter != null && innerMostFilter.getHeaderRowCount() == 0) {
         if(innerMostFilter instanceof TableFilter) {
            innerMostFilter = ((TableFilter) innerMostFilter).getTable();
         }
         else {
            innerMostFilter = null;
         }
      }

      return innerMostFilter;
   }

   public int getColIndexByIdentifier(Object identifier) {
      return getColIndexByIdentifier(identifier, false);
   }

   public int getColIndexByIdentifier(Object identifier, boolean lowerCase) {
      identifier = lowerCase && identifier != null ?
         identifier.toString().toLowerCase() : identifier;
      return getColumnIndex(lowerCase ? IDENTIFIER_LOWSER_CASE : IDENTIFIER, identifier);
   }

   public int getColIndexByIdentifier2(Object identifier2) {
      return getColumnIndex(IDENTIFIER2, identifier2);
   }

   public int getColIndexByHeader(Object header) {
      return getColumnIndex(HEADER, header);
   }

   public int getColIndexByStrHeader(Object header) {
      return getColIndexByStrHeader(header, false);
   }

   public int getColIndexByHeader2(Object header) {
      return getColumnIndex(HEADER2, header);
   }

   public int getColIndexByStrHeader(Object header, boolean lowerCase) {
      header = lowerCase && header != null ? header.toString().toLowerCase() : header;
      return getColumnIndex(lowerCase ? STRING_HEADER_LOWSER_CASE : STRING_HEADER, header);
   }

   public int getColIndexByFormatedHeader(Object header) {
      return getColIndexByFormatedHeader(header, false);
   }

   public int getColIndexByFormatedHeader(Object header, boolean lowerCase) {
      header = lowerCase && header != null ? header.toString().toLowerCase() : header;
      return getColumnIndex(lowerCase ? FORMAT_HEADER_LOWSER_CASE : FORMAT_HEADER, header);
   }

   public int getColumnIndex(String mapKey, Object valMapKey) {
      Map<Object, Integer> valMap = map.get(mapKey);

      if(valMap == null || valMapKey == null) {
         return -1;
      }

      Integer result = valMap.get(valMapKey.toString());

      return result == null ? -1 : result.intValue();
   }

   public Set getIdentifierKeySet() {
      return map.containsKey(IDENTIFIER) ? map.get(IDENTIFIER).keySet() : null;
   }

   public Set<Map.Entry<Object, Integer>> getIdentifierEntrySet() {
      return map.containsKey(IDENTIFIER) ? map.get(IDENTIFIER).entrySet() : null;
   }

   public Set<Map.Entry<Object, Integer>> getIdentifier2EntrySet() {
      return map.containsKey(IDENTIFIER2) ? map.get(IDENTIFIER2).entrySet() : null;
   }

   public Set getStrHeaderKeySet() {
      return map.containsKey(STRING_HEADER) ? map.get(STRING_HEADER).keySet() : null;
   }

   public Set<Map.Entry<Object, Integer>> getStrHeaderEntrySet() {
      return map.containsKey(STRING_HEADER) ? map.get(STRING_HEADER).entrySet() : null;
   }

   public XTable getTable() {
      return table;
   }

   protected XTable table;
   protected Map<String, Map<Object, Integer>> map = new HashMap<>();

   private static final String IDENTIFIER = "identifier";
   private static final String IDENTIFIER_LOWSER_CASE = "identifier_lower_case";  // lowser case of IDENTIFIER
   private static final String IDENTIFIER2 = "identifier2";  // column identifier in innermost table filter.
   protected static final String HEADER = "header"; // the first row value of the column.
   protected static final String HEADER2 = "header2"; // the header more complicated than the intuitive header.
   private static final String STRING_HEADER = "string_header"; // the first row value's toString result.
   private static final String STRING_HEADER_LOWSER_CASE = "string_header_lowser_case"; // lowser case of STRING_HEADER
   private static final String FORMAT_HEADER = "formated_header"; // Tool.toString(the first row value)
   private static final String FORMAT_HEADER_LOWSER_CASE = "formated_header_lowser_case"; // lowser case of STRING_HEADER2
}
