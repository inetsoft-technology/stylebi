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
package inetsoft.uql.datagov;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTableNode;
import inetsoft.uql.util.filereader.TextUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import java.text.*;
import java.util.*;

/**
 * A class to map the data from a Data.gov query to a table. The logic for this
 * structure is very similar to JsonTable, however, there is no need to
 * support multi-Array expansion as the original data structure is a
 * multi-dimensional array.
 */
class DatagovTable extends XTableNode {
   /**
    * Creates a new instance of <tt>DatagovTable</tt>.
    *
    * @param json the JSON data.
    * @param queryRows the maximum number of rows to process for the query.
    */
   DatagovTable(JsonObject json, int queryRows, DatagovQuery query) {
      maxRows = queryRows;
      parseMetaData(json);
      parseData(json, query);
   }

   /**
    * Parses the meta-data portion of the JSON data.
    *
    * @param json the JSON data.
    */
   private void parseMetaData(JsonObject json) {
      JsonArray columns = json.getJsonObject("meta").getJsonObject("view")
         .getJsonArray("columns");
      types = new Class[columns.size()];
      dataGovTypes = new String[columns.size()];

      for(int i = 0; i < columns.size(); i++) {
         String name =
            columns.getJsonObject(i).getJsonString("name").getString();
         String jsonClass =
            columns.getJsonObject(i).getJsonString("dataTypeName").getString();
         names.add(name);
         types[i] = getClassName(jsonClass);
         dataGovTypes[i] = jsonClass;
      }
   }

   /**
    * Parses the data values from the JSON data.
    *
    * @param json the JSON data.
    */
   private void parseData(JsonObject json, DatagovQuery query) {
      JsonArray dataArray = json.getJsonArray("data");
      int dataArraySize = maxRows <= 0 ? dataArray.size() : maxRows;
      dataArraySize = dataArraySize > dataArray.size() ? dataArray.size() : dataArraySize;
      data = new Object[dataArraySize][];

      for(int i = 0; i < dataArraySize; i++) {
         data[i] = new Object[names.size()];
         JsonArray colArray = dataArray.getJsonArray(i);

         for(int j = 0; j < names.size(); j++) {
            data[i][j] = parseValueType(colArray.get(j), types[j],
                                        dataGovTypes[j]);

            if(query.getColumnType((names.get(j))) != null)
            {
               data[i][j] = transform(query, names.get(j), data[i][j]);
            }
         }
      }
   }

   /**
    * Gets the Java class that corresponds to the specified JSON type.
    *
    * @param jsonType the JSON type name.
    *
    * @return the Java class.
    */
   private Class getClassName(String jsonType) {
      if("number".equals(jsonType) || "percent".equals(jsonType) ||
         "money".equals(jsonType))
      {
         return Double.class;
      }
      else if("calendar_date".equals(jsonType)) {
         return Date.class;
      }
      else if("checkbox".equals(jsonType)) {
         return Boolean.class;
      }

      return String.class;
   }

   /**
    * Parses a value from a JSON representation.
    *
    * @param val the JSON value to parse.
    * @param javaType  the Java type of the value.
    * @param dataGovType  the data-gov meta-data type.
    *
    * @return the parsed value.
    */
   private Object parseValueType(JsonValue val, Class javaType,
                                 String dataGovType)
   {
      JsonValue.ValueType valtype = val.getValueType();
      String valStr = TextUtil.stripOffQuote(val.toString());
      Object jsonValue = valStr;

      switch(valtype) {
      case ARRAY:
         if("location".equals(dataGovType)) {
            JsonArray addressArray = (JsonArray) val;
            JsonValue jLong = addressArray.get(1);
            JsonValue jLat = addressArray.get(2);
            jsonValue = TextUtil.stripOffQuote("(" + jLong.toString() +
                                                  ", " + jLat.toString() + ")");
         }
         else if("phone".equals(dataGovType)) {
            JsonArray phoneArray = (JsonArray) val;
            jsonValue = TextUtil.stripOffQuote(phoneArray.get(0).toString());
         }
         else {
            jsonValue = valStr;
         }

         break;
      case NUMBER:
         jsonValue = ((JsonNumber) val).bigDecimalValue();
         break;
      case TRUE:
         jsonValue = Boolean.TRUE;
         break;
      case FALSE:
         jsonValue = Boolean.FALSE;
         break;
      default:
         if(Date.class.isAssignableFrom(javaType)) {
            valStr = valStr.replace("T", " ");

            try {
               synchronized(formatter) { // SimpleDateFormat is not thread-safe
                  jsonValue = formatter.parse(valStr);
               }
            }
            catch(ParseException pex) {
               //ignore exception and just return the String version of the date.
               jsonValue = valStr;
            }
         }
         else if(Double.class.isAssignableFrom(javaType) &&
            ("money".equals(dataGovType) || "percent".equals(dataGovType) ||
             "number".equals(dataGovType)))
         {
            if("null".equals(valStr)) {
               jsonValue = null;
            }
            else {
               try {
                  jsonValue = Double.parseDouble(valStr);
               }
               catch(NumberFormatException nfe) {
                  LOG.error("Invalid Number value: " + valStr +
                     " For Data.Gov type: " + dataGovType, nfe);
                  jsonValue = null;
               }
            }
         }
      }

      return jsonValue;
   }

   private Object transform(DatagovQuery query, String header, Object oldValue) {
      final String columnType = query.getColumnType(header);

      if(columnType == null) {
         return oldValue;
      }

      final String formatType = query.getColumnFormat(header);
      final String formatExtent = query.getColumnFormatExtent(header);

      Format fmt = null;

      if(formatType != null) {
         fmt = TableFormat.getFormat(formatType, formatExtent);
      }

      try {
         return Tool.transform(oldValue, columnType, fmt, false);
      }
      catch(Exception e) {
         LOG.debug("Failed to parse number: {}", oldValue, e);
         return null;
      }
   }

   @Override
   public synchronized boolean next() {
      return ++curr < data.length;
   }

   @Override
   public synchronized boolean rewind() {
      curr = -1;
      return true;
   }

   @Override
   public boolean isRewindable() {
      return true;
   }

   @Override
   public int getColCount() {
      return names.size();
   }

   @Override
   public String getName(int col) {
      return names.get(col);
   }

   @Override
   public Class getType(int col) {
      if(data.length > 0 && data[0][col] != null) {
         return data[0][col].getClass();
      }
      else {
         return types[col];
      }
   }

   @Override
   public synchronized Object getObject(int col) {
      return data[curr][col];
   }

   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return null;
   }

   private int maxRows;
   private final SimpleDateFormat formatter =
      new SimpleDateFormat("yyyy-MM-dd HH:mm:SS");
   private List<String> names = new ArrayList<>();
   private Object[][] data;
   private Class[] types;
   private int curr = -1;
   private String[] dataGovTypes;
   private static final Logger LOG = LoggerFactory.getLogger(DatagovTable.class.getName());
}
