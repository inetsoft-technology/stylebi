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
package inetsoft.uql.util;

import inetsoft.uql.XMetaInfo;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwappableObjectList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import javax.json.stream.JsonParsingException;
import java.util.*;

/**
 * A class to map a Json structure to a table. Arrays inside data structures
 * are expanded into multiple rows. The expansion is in parallel so two arrays
 * inside an object will generate the number of rows equals to the maximum
 * length of the arrays.
 */
public class ExpandedJsonTable extends BaseJsonTable {
   public ExpandedJsonTable() {
      this(null);
   }

   public ExpandedJsonTable(String expandedPath) {
      this.expandedPath = expandedPath;
   }

   public ExpandedJsonTable(Object json, String expandedPath) {
      this.expandedPath = expandedPath;
      load(json);
   }

   public ExpandedJsonTable(Object json, int expandLevels) {
      this.expandLevels = expandLevels;
      load(json);
   }

   /**
    * Get the maximum number of levels to expand.
    */
   public int getExpandLevels() {
      return expandLevels;
   }

   /**
    * Set the maximum number of levels to expand.
    */
   public void setExpandLevels(int expandLevels) {
      this.expandLevels = expandLevels;
   }

   @Override
   public void beginStreamedLoading() {
      // no-op
   }

   @Override
   public void loadStreamed(Object json) {
      if(getMaxRows() > 0 && size() >= getMaxRows()) {
         return;
      }

      Object jsonObject = prepareData(json);
      processJson(size(), new Object2ObjectLinkedOpenHashMap<>(), null, jsonObject, expandLevels, 0);
   }

   @Override
   public void finishStreamedLoading() {
      rows.complete();

      // get headers
      Set<String> names = new HashSet<>();
      int rcnt = Math.min(rows.size(), 10000);

      for(int r = 0; r < rcnt; r++) {
         Map<String, Object> row = rows.get(r);

         if(row != null) {
            for(String header : row.keySet()) {
               if(!names.contains(header)) {
                  headers.add(header);
                  names.add(header);
               }
            }
         }
      }

      // initialize types
      types = new Class[headers.size()];

      for(int i = 0; i < types.length; i++) {
         for(int r = 0; r < rcnt; r++) {
            Map<String, Object> row = rows.get(r);
            Object val = row == null ? null : row.get(headers.get(i));

            if(types[i] != String.class && val != null) {
               Class<?> cls = JsonTable.getTypeClass(val);

               if(cls == String.class) {
                  cls = getJavaType(headers.get(i));
               }

               if(types[i] != null && types[i] != cls) {
                  types[i] = String.class;
               }
               else {
                  types[i] = cls;
               }

               if(types[i] == String.class) {
                  break;
               }
            }
         }

         // Default to string when all column's values are null.
         if(types[i] == null) {
            types[i] = String.class;
         }
      }

      for(int i = 0; i < headers.size(); i++) {
         final String name = headers.get(i);
         final String columnType = getColumnType(name);

         if(columnType == null) {
            setColumnType(name, Tool.getDataType(types[i]));
         }
      }
   }

   @Override
   public int size() {
      return rows.size();
   }

   private Object prepareData(Object data) {
      if(data instanceof String) {
         try {
            return JsonTable.parseJson((String) data);
         }
         catch(JsonParsingException e) {
            return Collections.singletonList(data);
         }
      }

      return data;
   }

   /**
    * Process a json object.
    * @param ridx the ridxent row index.
    * @param row the ridxent row object.
    * @param prefix the prefix for column name.
    * @param jobj the json object.
    * @return the number of rows added
    */
   private int processJson(int ridx, Map<String, Object> row, String prefix, Object jobj,
                           int expandLevels, int level)
   {
      int cnt = 1;

      addRow(ridx, row);

      if(jobj instanceof LookupData) {
         expandLevels = ((LookupData) jobj).getExpandLevels();
         level = 0;
      }

      if(jobj instanceof Map) {
         cnt = expandMap(ridx, cnt, row, prefix, (Map<?, ?>) jobj, expandLevels, level);
      }
      else if(jobj instanceof List && (prefix == null || prefix.isEmpty() ||
         expandedPath == null || expandedPath.isEmpty() || expandedPath.equals(prefix) ||
         expandedPath.startsWith(prefix + ".")) && level < expandLevels)
      {
         cnt = expandList(ridx, row, prefix, (List<?>) jobj, expandLevels, level + 1);
      }
      else {
         if(prefix == null) {
            // for loading array of literal values
            row.put("Column", parseValue("Column", jobj));
         }
         else {
            row.put(prefix, parseValue(prefix, jobj));
         }

         addRow(ridx, row);
      }

      return cnt;
   }

   /**
    * Add a row to the table. If the row already exists, merge the values
    * into the existing row.
    */
   private void addRow(int idx, Map<String, Object> row) {
      while(idx >= rows.size()) {
         rows.add(new Object2ObjectLinkedOpenHashMap<>());
      }

      Map<String, Object> map = rows.get(idx);

      if(map != null) {
         map.putAll(row); // merge into row
      }
   }

   /**
    * Expand the map into columns.
    * @return the number of rows added
    */
   private int expandMap(int ridx, int parentCnt, Map<String, Object> row, String prefix,
                         Map<?, ?> jobj, int expandLevels, int level)
   {
      List<String> mapnames = new ArrayList<>();
      List<Map<?, ?>> maps = new ArrayList<>();
      List<String> listnames = new ArrayList<>();
      List<List<?>> lists = new ArrayList<>();
      int cnt = 1;

      for(Object key : jobj.keySet()) {
         Object val = jobj.get(key);
         String name = prefix == null ? key + "" : prefix + "." + key;

         if(val instanceof Map) {
            mapnames.add(name);
            maps.add((Map<?, ?>) val);
         }
         else if((val instanceof List) && (expandedPath == null || expandedPath.isEmpty() ||
            expandedPath.equals(name) || expandedPath.startsWith(name + ".")))
         {
            listnames.add(name);
            lists.add((List<?>) val);
         }
         else {
            row.put(name, parseValue(name, val));
         }
      }

      for(int i = 0; i < maps.size(); i++) {
         final Map<?, ?> map = maps.get(i);
         int lvl = level;
         int expLvls = expandLevels;

         if(map instanceof LookupData) {
            expLvls = ((LookupData) map).getExpandLevels();
            lvl = 0;
         }

         cnt = Math.max(cnt, expandMap(ridx, cnt, row, mapnames.get(i), map, expLvls, lvl + 1));
      }

      // Don't add an empty row as a result of expanding empty lists.
      final boolean emptyListRow = !allowEmptyLists && row.isEmpty() &&
                                   lists.stream().noneMatch(l -> l.size() > 0);

      for(int i = 0; i < lists.size(); i++) {
         final List<?> list = lists.get(i);
         int lvl = level;
         int expLvls = expandLevels;

         if(list instanceof LookupData) {
            expLvls = ((LookupData) list).getExpandLevels();
            lvl = 0;
         }

         if(!emptyListRow && (expLvls <= 0 || lvl < expLvls)) {
            cnt = Math.max(cnt, expandList(ridx, row, listnames.get(i), list, expLvls,  lvl + 1));
         }
         else {
            row.put(listnames.get(i), parseValue(listnames.get(i), list));
         }
      }

      for(int i = 0; i < parentCnt; i++) {
         addRow(ridx + i, row);
      }

      return cnt;
   }

   /**
    * Expand the list into rows.
    * @return the number of rows added
    */
   private int expandList(int ridx, Map<String, Object> row, String prefix, List<?> list,
                          int expandLevels, int level)
   {
      int cnt = 0;

      for(Object obj : list) {
         int rc = processJson(ridx, new Object2ObjectLinkedOpenHashMap<>(row), prefix, obj, expandLevels, level + 1);
         ridx += rc;
         cnt += rc;

         // short-circuit if max rows is reached in top-level array
         if(getMaxRows() > 0 && ridx >= getMaxRows() && level <= 1) {
            return cnt;
         }
      }

      return cnt;
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    * @return true if there are more rows.
    */
   @Override
   public synchronized boolean next() {
      return ++curr < rows.size();
   }

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    * @return true if the rewinding is successful.
    */
   @Override
   public synchronized boolean rewind() {
      curr = -1;
      return true;
   }

   /**
    * Check if the cursor can be rewinded.
    * @return true if the cursor can be rewinded.
    */
   @Override
   public boolean isRewindable() {
      return true;
   }

   /**
    * Get the number of columns in the table.
    */
   @Override
   public int getColCount() {
      return headers.size();
   }

   /**
    * Get the column name.
    * @param col column index.
    * @return column name or alias if defined.
    */
   @Override
   public String getName(int col) {
      return headers.get(col);
   }

   /**
    * Get the column type.
    * @param col column index.
    * @return column data type.
    */
   @Override
   public Class<?> getType(int col) {
      return types[col];
   }

   /**
    * Get the value in the current row at the specified column.
    * @param col column index.
    * @return column value.
    */
   @Override
   public synchronized Object getObject(int col) {
      Map<String, Object> row = rows.get(curr);
      return row == null ? null : row.get(headers.get(col));
   }

   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return null;
   }

   public void setAllowEmptyLists(boolean allowEmptyLists) {
      this.allowEmptyLists = allowEmptyLists;
   }

   private final XSwappableObjectList<Map<String, Object>> rows = new XSwappableObjectList<>(null);
   private final List<String> headers = new ArrayList<>();
   private Class<?>[] types;
   private int curr = -1;
   private String expandedPath;
   private int expandLevels = Integer.MAX_VALUE;
   private boolean allowEmptyLists = false;
}
