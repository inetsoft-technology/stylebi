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
import inetsoft.util.ObjectWrapper;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import java.io.StringReader;
import java.util.*;

/**
 * A class to map a Json array to a table.
 */
public class JsonTable extends BaseJsonTable {
   public JsonTable() {
   }

   public JsonTable(Object json, int maximumRows) {
      setMaxRows(maximumRows);
      load(json);
   }

   @Override
   public void beginStreamedLoading() {
      data = new XSwappableObjectList<>(null);

      if(getJsonMetadata() != null) {
         metadata = new JsonTable(getJsonMetadata(), getMaxRows());
      }
   }

   @SuppressWarnings("unchecked")
   @Override
   public void loadStreamed(Object json) {
      Object jsonObject = prepareData(json);
      List<Object> dataList;

      if(jsonObject instanceof List) {
         dataList = (List<Object>) jsonObject;
      }
      else {
         dataList = Collections.singletonList(jsonObject);
      }

      int total = size();
      int limit;

      if(getMaxRows() > 0 && total + dataList.size() > getMaxRows()) {
         limit = getMaxRows() - total;
      }
      else {
         limit = dataList.size();
      }

      for(int i = 0; i < limit; i++) {
         List<Object> row = new ArrayList<>();
         walkRecord(dataList.get(i), row, null);
         data.add(transformRow(row));
      }
   }

   private Object[] transformRow(List<Object> row) {
      if(metadata == null) {
         return row.toArray(new Object[0]);
      }

      // put metadata columns first, and new columns last
      Set<String> nameSet = new LinkedHashSet<>(metadata.names);
      nameSet.addAll(names);

      Object[] newRow = new Object[nameSet.size()];
      int count = 0;

      // rearrange the row entries
      for(String name : nameSet) {
         if(count < metadata.names.size()) {

            if(nameIdx.containsKey(name)) {
               int idx = nameIdx.get(name);

               if(idx < row.size()) {
                  newRow[metadata.nameIdx.get(name)] = row.get(idx);
               }
            }
            else {
               newRow[metadata.nameIdx.get(name)] = null;
            }
         }
         else {
            newRow[count] = row.get(nameIdx.get(name));
         }

         count++;
      }

      return newRow;
   }

   @Override
   public void finishStreamedLoading() {
      data.complete();

      if(metadata != null) {
         transformTableWithMetadata();
      }

      // Default to string when all column's values are null.
      for(int i = 0; i < types.size(); i++) {
         if(types.get(i) == null) {
            types.set(i, String.class);
         }
      }

      for(int i = 0; i < names.size(); i++) {
         final String name = names.get(i);
         final String columnType = getColumnType(name);

         if(columnType == null) {
            setColumnType(name, Tool.getDataType(types.get(i)));
         }
      }
   }

   /**
    * Transform the nameIdx, names, and types data structures to include the metadata columns
    */
   private void transformTableWithMetadata() {
      // put metadata columns first
      Set<String> nameSet = new LinkedHashSet<>(metadata.names);
      nameSet.addAll(names);

      Map<String, Integer> newNameIdx = new HashMap<>();
      List<String> newNames = new ArrayList<>();
      List<Class<?>> newTypes = new ArrayList<>();

      int count = 0;

      for(String name : nameSet) {
         newNames.add(name);
         newNameIdx.put(name, count);

         if(count < metadata.names.size()) {
            // if type is null then use the type from metadata
            // otherwise use type derived from real data
            Class<?> currentType = nameIdx.containsKey(name) ? types.get(nameIdx.get(name)) : null;

            if(currentType == null) {
               newTypes.add(metadata.types.get(metadata.nameIdx.get(name)));
            }
            else {
               newTypes.add(currentType);
            }
         }
         else {
            newTypes.add(types.get(nameIdx.get(name)));
         }

         count++;
      }

      nameIdx = newNameIdx;
      names = newNames;
      types = newTypes;
   }

   @Override
   public int size() {
      return data == null ? 0 : data.size();
   }

   private Object prepareData(Object json) {
      if(json instanceof String) {
         try {
            return parseJson((String) json);
         }
         catch(JsonException e) {
            LOG.debug("Failed to parse JSON", e);
         }
      }

      return json;
   }

   /**
    * Parse json string into a JsonStructure.
    */
   protected static JsonStructure parseJson(String json) {
      try(JsonReader parser = Json.createReader(new StringReader(json))) {
         return parser.read();
      }
   }

   private void walkRecord(Object record, List<Object> list, String prefix) {
      if(record instanceof Map) {
         Map<?, ?> map = (Map<?, ?>) record;

         for(Map.Entry<?, ?> entry : map.entrySet()) {
            final String key = (String) entry.getKey();
            final Object val = entry.getValue();

            String name = prefix == null ? key : prefix + "." + key;
            walkRecord(val, list, name);
         }

         return;
      }

      // if not a map, it's a literal value
      Object val = record;
      String name = prefix == null ? "Column" : prefix;
      Integer idx = nameIdx.get(name);

      if(idx == null) {
         idx = names.size();
         nameIdx.put(name, idx);
         names.add(name);
         types.add(null);
      }

      while(names.size() <= idx) {
         names.add(null);
         types.add(null);
      }

      while(list.size() <= idx) {
         list.add(null);
      }

      val = parseValue(names.get(idx), val);
      list.set(idx, val);

      Class<?> type = types.get(idx);

      // find column type
      if(type != String.class && val != null) {
         Class<?> cls = getTypeClass(val);

         if(cls == String.class) {
            cls = getJavaType(names.get(idx));
         }

         // handle mixed type
         if(type != null && type != cls) {
            types.set(idx, String.class);
         }
         else {
            types.set(idx, cls);
         }
      }
   }

   /**
    * Hook for special logic to retrieve value from map.
    */
   protected Object getValue(Map<?, ?> record, Object key) {
      return record.get(key);
   }

   /**
    * Convert from JSON class to Java class.
    */
   static Class<?> getTypeClass(Object val) {
      Class<?> cls = val.getClass();

      if(JsonNumber.class.isAssignableFrom(cls)) {
         return Double.class;
      }
      else if(JsonString.class.isAssignableFrom(cls)) {
         return String.class;
      }
      else if(val instanceof JsonValue) {
         switch(((JsonValue) val).getValueType()) {
         case NUMBER:
            return Double.class;
         case TRUE:
         case FALSE:
            return Boolean.class;
         default:
            // fall-through
         }
      }

      return cls;
   }

   protected static Object getJavaValue(Object val) {
      if(val == null) {
         return null;
      }

      Class<?> cls = val.getClass();

      if(JsonNumber.class.isAssignableFrom(cls)) {
         return ((JsonNumber) val).doubleValue();
      }
      else if(JsonString.class.isAssignableFrom(cls)) {
         return ((JsonString) val).getString();
      }
      else if(val instanceof JsonValue) {
         switch(((JsonValue) val).getValueType()) {
         case NULL:
            return null;
         case NUMBER:
            return Double.parseDouble(val.toString());
         case TRUE:
            return true;
         case FALSE:
            return false;
         default:
            // fall-through
         }
      }
      else if(val instanceof Number || val instanceof Date || val instanceof Boolean) {
         return val;
      }
      else if(val instanceof ObjectWrapper) {
         return ((ObjectWrapper) val).unwrap();
      }

      return val.toString();
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    * @return true if there are more rows.
    */
   @Override
   public synchronized boolean next() {
      return ++curr < data.size();
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
    * Check if the cursor can be rewound.
    * @return true if the cursor can be rewound.
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
      return names.size();
   }

   /**
    * Get the column name.
    * @param col column index.
    * @return column name or alias if defined.
    */
   @Override
   public String getName(int col) {
      return names.get(col);
   }

   /**
    * Get the column type.
    * @param col column index.
    * @return column data type.
    */
   @Override
   public Class<?> getType(int col) {
      return types.get(col);
   }

   /**
    * Get the value in the current row at the specified column.
    * @param col column index.
    * @return column value.
    */
   @Override
   public synchronized Object getObject(int col) {
      Object[] row = data.get(curr);
      return row != null && col < row.length ? row[col] : null;
   }

   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return null;
   }

   private XSwappableObjectList<Object[]> data;
   private int curr = -1;
   private Map<String, Integer> nameIdx = new HashMap<>();
   private List<String> names = new ArrayList<>();
   private List<Class<?>> types = new ArrayList<>();
   private JsonTable metadata;

   private static final Logger LOG = LoggerFactory.getLogger(JsonTable.class.getName());
}
