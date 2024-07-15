/*
 * inetsoft-tabular-util - StyleBI is a business intelligence web application.
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
package inetsoft.uql.util;

import inetsoft.uql.XMetaInfo;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import javax.json.stream.JsonParsingException;
import java.io.StringReader;
import java.util.*;

/**
 *
 * Handles JSON objects where both headers and rows are represented as arrays.
 *
 * Each item in rows is an array that contains comma-delimited data corresponding to
 * a single row of data. The order of the comma-delimited data fields should match the order of
 * the columns listed in the headers array.
 *
 * {
 *   "headers": [
 *     "header1", "header2", ...
 *   ],
 *   "rows": [
 *     [
 *       {row1-value}, {row1-value}, ...
 *     ],
 *     [
 *       {row2-value}, {row2-value}, ...
 *     ],
 *     ... more rows ...
 *   ]
 * }
 */
public class HeadersAndRowsJsonTable extends BaseJsonTable {
   public HeadersAndRowsJsonTable(String headersPath, String rowsPath) {
      this.headersPath = headersPath;
      this.rowsPath = rowsPath;
   }

   @Override
   public void beginStreamedLoading() {
      data = new XSwappableObjectList<>(null);
   }

   @SuppressWarnings("unchecked")
   @Override
   public void loadStreamed(Object json) {
      if(getMaxRows() > 0 && data.size() >= getMaxRows()) {
         return;
      }

      Object jsonObject = prepareData(json);

      if(jsonObject instanceof Map) {
         Map<?, ?> map = (Map<?, ?>) jsonObject;
         map = getJsonMap(headersPath, map);
         String headersProp = getFinalJsonPath(headersPath);
         Object headersObj = map.get(headersProp);

         if(headersObj instanceof List) {
            List<Object> headerList = (List<Object>) headersObj;

            for(Object headerObj : headerList) {
               String name = getHeaderName(headerObj);
               Integer idx = nameIdx.get(name);

               if(idx == null) {
                  idx = names.size();
                  nameIdx.put(name, idx);
                  names.add(name);
                  types.add(null);
               }
            }
         }

         map = getJsonMap(rowsPath, (Map<?, ?>) jsonObject);
         String rowsProp = getFinalJsonPath(rowsPath);
         Object rowsObj = map.get(rowsProp);

         if(rowsObj instanceof List) {
            List<Object> rowsList = (List<Object>) rowsObj;

            for(Object rowObj : rowsList) {
               if(rowObj instanceof List) {
                  List<Object> row = (List<Object>) rowObj;
                  Object[] newRow = new Object[row.size()];

                  for(int i = 0; i < row.size(); i++) {
                     Object val = parseValue(names.get(i), row.get(i));
                     newRow[i] = val;

                     Class type = types.get(i);

                     // find column type
                     if(type != String.class && val != null) {
                        Class<?> cls = getTypeClass(val);

                        if(cls == String.class) {
                           cls = getJavaType(names.get(i));
                        }

                        // handle mixed type
                        if(type != null && type != cls) {
                           types.set(i, String.class);
                        }
                        else {
                           types.set(i, cls);
                        }
                     }
                  }

                  data.add(newRow);

                  if(getMaxRows() > 0 && data.size() >= getMaxRows()) {
                     break;
                  }
               }
            }
         }
      }
   }

   private Map<?, ?> getJsonMap(String path, Map<?, ?> map) {
      int separator = path.indexOf(".");

      if(separator == -1) {
         return map;
      }

      String prop = path.substring(0, separator);

      if(prop.endsWith("[0]")) {
         prop = prop.substring(0, separator - 3);
         List list = (List) map.get(prop);
         map = (Map<?, ?>)list.get(0);
         return getJsonMap(path.substring(separator + 1), map);
      }
      else {
         map = (Map<?, ?>) map.get(prop);
         return getJsonMap(path.substring(separator + 1), map);
      }
   }

   private String getFinalJsonPath(String path) {
      if(path.lastIndexOf(".") != -1) {
         return path.substring(path.lastIndexOf(".") + 1);
      }
      else {
         return path;
      }
   }

   private String getHeaderName(Object headerObj) {
      if(headerObj instanceof Map) {
         Map<?, ?> map = (Map<?, ?>) headerObj;
         return getHeaderName(map.entrySet().iterator().next().getValue());
      }
      else if(headerObj instanceof List<?>) {
         return getHeaderName(((List<?>) headerObj).get(0));
      }
      else {
         return headerObj + "";
      }
   }

   @Override
   public void finishStreamedLoading() {
      data.complete();
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
         catch(JsonParsingException e) {
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

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    *
    * @return true if there are more rows.
    */
   @Override
   public synchronized boolean next() {
      return ++curr < data.size();
   }

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    *
    * @return true if the rewinding is successful.
    */
   @Override
   public synchronized boolean rewind() {
      curr = -1;
      return true;
   }

   /**
    * Check if the cursor can be rewound.
    *
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
    *
    * @param col column index.
    *
    * @return column name or alias if defined.
    */
   @Override
   public String getName(int col) {
      return names.get(col);
   }

   /**
    * Get the column type.
    *
    * @param col column index.
    *
    * @return column data type.
    */
   @Override
   public Class<?> getType(int col) {
      return types.get(col);
   }

   /**
    * Get the value in the current row at the specified column.
    *
    * @param col column index.
    *
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

   private String headersPath;
   private String rowsPath;
   private XSwappableObjectList<Object[]> data;
   private int curr = -1;
   private final Map<String, Integer> nameIdx = new HashMap<>();
   private final List<String> names = new ArrayList<>();
   private final List<Class<?>> types = new ArrayList<>();

   private static final Logger LOG = LoggerFactory.getLogger(HeadersAndRowsJsonTable.class.getName());
}
