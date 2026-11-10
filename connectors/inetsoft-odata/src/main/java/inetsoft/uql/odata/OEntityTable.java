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
package inetsoft.uql.odata;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.util.Tool;
import inetsoft.util.swap.*;
import inetsoft.uql.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.TabularColumn;
import inetsoft.uql.tabular.TabularDatasetSchema;

import java.io.*;
import java.text.Format;
import java.util.*;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetIteratorRequest;
import org.apache.olingo.client.api.communication.request.retrieve.RetrieveRequestFactory;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.*;
import org.apache.olingo.commons.api.format.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class to map a OEntity resultset to a table.
 */
public class OEntityTable extends XTableNode {
   public OEntityTable(ClientEntitySetIterator<ClientEntitySet, ClientEntity> entities,
                       RetrieveRequestFactory requestFactory, boolean isV4, ODataQuery query)
   {
      this(entities, requestFactory, isV4, query, null);
   }

   /**
    * @param typedSchema the {@code $metadata} schema of the query's entity set when the query is
    *                    {@link ODataQuery#isTypedByMetadata() typed by metadata}, else
    *                    {@code null} (columns are then typed from the returned values only).
    */
   public OEntityTable(ClientEntitySetIterator<ClientEntitySet, ClientEntity> entities,
                       RetrieveRequestFactory requestFactory, boolean isV4, ODataQuery query,
                       TabularDatasetSchema typedSchema)
   {
      this.query = query;
      expandArrays = query.isExpanded();
      initTypedSchema(typedSchema);
      // Check for paged results and execute a new request if it exists
      do {
         while(entities.hasNext()) {
            allRows.add(new ExpandedRows(entities.next()));
         }

         if(entities.getNext() != null) {
            ODataEntitySetIteratorRequest<ClientEntitySet,ClientEntity> req;
            req = requestFactory.getEntitySetIteratorRequest(entities.getNext());

            if(isV4) {
               req.setAccept("*/*");
            }
            else {
               req.setAccept(ContentType.APPLICATION_ATOM_XML.toString());
            }

            ODataRetrieveResponse<ClientEntitySetIterator<ClientEntitySet, ClientEntity>> res;
            res = req.execute();
            entities = res.getBody();
         }
      }
      while(entities.hasNext());

      allRows.complete();
      completeTyping();
   }

   public OEntityTable(ClientEntitySet entitySet, ODataQuery query) {
      this(entitySet, query, null);
   }

   OEntityTable(ClientEntitySet entitySet, ODataQuery query, TabularDatasetSchema typedSchema) {
      this.query = query;
      expandArrays = query.isExpanded();
      initTypedSchema(typedSchema);
      List<ClientEntity> entities = entitySet.getEntities();

      for(ClientEntity entity : entities) {
         allRows.add(new ExpandedRows(entity));
      }

      allRows.complete();
      completeTyping();
   }

   public OEntityTable(ClientEntity entity, ODataQuery query) {
      expandArrays = query.isExpanded();
      allRows.add(new ExpandedRows(entity));
      allRows.complete();
      this.query = query;
   }

   public OEntityTable(ClientProperty property, ODataQuery query) {
      expandArrays = query.isExpanded();
      allRows.add(new ExpandedRows(property));
      allRows.complete();
      this.query = query;
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    * @return true if there are more rows.
    */
   @Override
   public synchronized boolean next() {
      if(index >= allRows.size()) {
         return false;
      }

      subIdx++;

      ExpandedRows curr = allRows.get(index);

      if(subIdx >= curr.getRowCount()) {
         if(index < allRows.size() - 1) {
            curr = allRows.get(++index);
            subIdx = 0;
         }
      }

      return subIdx < curr.getRowCount();
   }

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    * @return true if the rewinding is successful.
    */
   @Override
   public synchronized boolean rewind() {
      index = 0;
      subIdx = -1;
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
   public Class getType(int col) {
      return types.get(col);
   }

   /**
    * Get the value in the current row at the specified column.
    * @param col column index.
    * @return column value.
    */
   @Override
   public synchronized Object getObject(int col) {
      if(index < allRows.size()) {
         ExpandedRows curr = allRows.get(index);
         return (subIdx < curr.getRowCount()) ? curr.getValue(subIdx, col) : null;
      }

      return null;
   }

   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return null;
   }

   private class ExpandedRows implements Serializable {
      public ExpandedRows(ClientEntity row) {
         if(row != null) {
            Map<String, Object> map = new HashMap<>();
            rows.add(map);
            walkTree(row, map, "", 0);
         }
      }

      public ExpandedRows(ClientProperty prop) {
         if(prop != null) {
            Map<String, Object> map = new HashMap<>();
            rows.add(map);

            if(prop.hasPrimitiveValue() || prop.hasNullValue() ||
               (prop.hasCollectionValue() && !expandArrays)) {
               String name = prop.getName();

               if(!prefixes.contains(name) && !typeMap.containsKey(name)) {
                  Class<?> type = prop.hasNullValue() || prop.hasCollectionValue() ?
                     String.class : prop.getPrimitiveValue().getType().getDefaultType();
                  names.add(name);
                  types.add(type);
                  typeMap.put(name, type);
               }

               if(prop.hasNullValue()) {
                  map.put(name, String.class.equals(typeMap.get(name)) ? "" : null);
               }
               else if(prop.hasCollectionValue()) {
                  map.put(name, parseCollection(prop.getCollectionValue()));
               }
               else {
                  map.put(name, prop.getPrimitiveValue().toValue());
               }
            }
            else if(prop.hasCollectionValue()) {
               List<ClientCollectionValue<ClientValue>> list = new ArrayList<>();
               list.add(prop.getCollectionValue());
               List<String> listName = new ArrayList<>();
               listName.add(prop.getName());

               addCollectionValues(list, listName, map, "", 0);
            }
            else if(prop.hasComplexValue()) {
               addComplexValue(prop.getComplexValue(), map, prop.getName() + ".", 0);
            }
         }
      }

      private int walkTree(ClientEntity row, Map<String, Object> map,
                            String prefix, int idx)
      {
         List<ClientProperty> props = row.getProperties();
         int cnt = 1;
         List<ClientCollectionValue<ClientValue>> lists = new ArrayList<>();
         List<String> listNames = new ArrayList<>();

         for(ClientProperty prop : props) {
            if(prop.hasPrimitiveValue() || prop.hasNullValue() ||
               (prop.hasCollectionValue() && !expandArrays))
            {
               String name = prefix + prop.getName();

               if(!prefixes.contains(name) && !typeMap.containsKey(name)) {
                  Class<?> type = columnType(name, prop);
                  names.add(name);
                  types.add(type);
                  typeMap.put(name, type);
               }

               if(prop.hasNullValue()) {
                  map.put(name, String.class.equals(typeMap.get(name)) ? "" : null);
               }
               else if(prop.hasCollectionValue()) {
                  map.put(name, parseCollection(prop.getCollectionValue()));
               }
               else {
                  map.put(name, prop.getPrimitiveValue().toValue());
               }
            }
            else if(prop.hasCollectionValue()) {
               lists.add(prop.getCollectionValue());
               listNames.add(prop.getName());
            }
            else if(prop.hasComplexValue()) {
               addComplexValue(prop.getComplexValue(), map, prefix + prop.getName() + ".", idx);
            }
         }

         Map<String, Object> map0 = new HashMap<>(map);

         for(ClientLink link : row.getNavigationLinks()) {
            String linkName = link.getName();
            ClientInlineEntitySet inlineSet = link.asInlineEntitySet();

            if(inlineSet == null) {
               ClientInlineEntity inlineEntity = link.asInlineEntity();

               if(inlineEntity == null) {
                  continue;
               }

               if(rows.size() <= idx) {
                  map = new HashMap<>(map0);
                  rows.add(map);
               }
               else {
                  map = rows.get(idx);
               }

               walkTree(inlineEntity.getEntity(), map, prefix + linkName + ".", idx);
               continue;
            }

            ClientEntitySet eset = inlineSet.getEntitySet();
            List<ClientEntity> entities = eset.getEntities();
            int tempCnt = 0;

            for(int i = 0; i < entities.size(); i++) {
               if(rows.size() <= idx + tempCnt) {
                  map = new HashMap<>(map0);
                  rows.add(map);
               }
               else {
                  map = rows.get(idx + tempCnt);
               }

               tempCnt += walkTree(entities.get(i), map, prefix + linkName + ".", idx + tempCnt);
            }

            cnt = Math.max(cnt, tempCnt);
         }

         cnt = Math.max(cnt, addCollectionValues(lists, listNames, map0, prefix,idx));

         return cnt;
      }

      private String parseCollection(ClientCollectionValue<ClientValue> collectionValue) {
         StringBuilder builder = new StringBuilder("[");
         Iterator<ClientValue> iterator = collectionValue.iterator();

         while(iterator.hasNext()) {
            ClientValue value = iterator.next();

            if(value.isPrimitive()) {
               boolean isString = value.asPrimitive().toValue() instanceof String;

               if(isString) {
                  builder.append("\"" + value.asPrimitive().toValue() + "\"");
               }
               else {
                  builder.append("\"" + value.asPrimitive().toValue() + "\"");
               }
            }
            else if(value.isComplex()) {
               builder.append(parseComplexValue(value.asComplex()));
            }
            else if(value.isCollection()) {
               builder.append(parseCollection(value.asCollection()));
            }

            if(iterator.hasNext()) {
               builder.append(",");
            }
         }

         builder.append("]");
         return builder.toString();
      }

      private String parseComplexValue(ClientComplexValue complexValue) {
         StringBuilder builder = new StringBuilder("{");
         Iterator<ClientProperty> iterator = complexValue.iterator();

         while(iterator.hasNext()) {
            ClientProperty prop = iterator.next();
            ClientValue value = prop.getValue();

            if(prop.hasPrimitiveValue()) {
               boolean isString = value.asPrimitive().toValue() instanceof String;

               builder.append("\"" + prop.getName() + "\":");

               if(isString) {
                  builder.append("\"" + value.asPrimitive().toValue() + "\"");
               }
               else {
                  builder.append("\"" + value.asPrimitive().toValue() + "\"");
               }
            }
            else if(prop.hasComplexValue()) {
               builder.append(parseComplexValue(value.asComplex()));
            }
            else if(prop.hasCollectionValue()) {
               builder.append(parseCollection(value.asCollection()));
            }

            if(iterator.hasNext()) {
               builder.append(",");
            }
         }

         builder.append("}");
         return builder.toString();
      }

      private int addComplexValue(ClientComplexValue complexValue, Map<String, Object> map,
                                  String prefix, int idx)
      {
         List<ClientCollectionValue<ClientValue>> lists = new ArrayList<>();
         List<String> listNames = new ArrayList<>();
         int cnt = 0;
         int tempCnt = 0;
         String objectName = prefix.substring(0, prefix.length() - 1);
         prefixes.add(objectName);

         if(typeMap.containsKey(objectName)) {
            int nameIndex = names.indexOf(objectName);
            names.remove(nameIndex);
            types.remove(nameIndex);
            typeMap.remove(objectName);
         }

         for(ClientProperty prop : complexValue) {
            if(prop.hasPrimitiveValue() || prop.hasNullValue() ||
               (prop.hasCollectionValue() && !expandArrays))
            {
               String name = prefix + prop.getName();

               if(!prefixes.contains(name) && !typeMap.containsKey(name)) {
                  Class<?> type = columnType(name, prop);
                  names.add(name);
                  types.add(type);
                  typeMap.put(name, type);
               }

               if(prop.hasNullValue()) {
                  map.put(name, String.class.equals(typeMap.get(name)) ? "" : null);
               }
               else if(prop.hasCollectionValue()) {
                  map.put(name, parseCollection(prop.getCollectionValue()));
               }
               else {
                  map.put(name, prop.getPrimitiveValue().toValue());
               }

               tempCnt = 1;
            }
            else if(prop.hasComplexValue()){
               tempCnt = addComplexValue(prop.getComplexValue(), map, prefix + prop.getName() + ".", idx);

            }
            else if(prop.hasCollectionValue()) {
               lists.add(prop.getCollectionValue());
               listNames.add(prop.getName());
            }

            cnt = Math.max(cnt, tempCnt);
         }

         tempCnt = addCollectionValues(lists, listNames, map, prefix, idx);
         cnt = Math.max(cnt, tempCnt);

         return cnt;
      }

      private int addCollectionValues(List<ClientCollectionValue<ClientValue>> lists, List<String> listNames,
                                      Map<String, Object> map0, String prefix, int idx)
      {
         Map<String, Object> map;
         int cnt = 0;

         for(int i = 0; i < lists.size(); i ++) {
            ClientCollectionValue<ClientValue> list = lists.get(i);
            String listName = listNames.get(i);
            int tempCnt = 0;

            for(ClientValue value : list) {
               if(rows.size() <= idx + tempCnt) {
                  map = new HashMap<>(map0);
                  rows.add(map);
               }
               else {
                  map = rows.get(idx + tempCnt);
               }

               if(value == null) {
                  continue;
               }
               else if(value.isPrimitive()) {
                  String name = prefix + listName;

                  if(!prefixes.contains(name) && !typeMap.containsKey(name)) {
                     Class<?> type = value.asPrimitive().getType().getDefaultType();
                     names.add(name);
                     types.add(type);
                     typeMap.put(name, type);
                  }

                  map.put(name, value.asPrimitive().toValue());
                  tempCnt++;
               }
               else if(value.isComplex()) {
                  tempCnt += addComplexValue(value.asComplex(), map,
                                             prefix + listName + ".", idx + tempCnt);
               }
               else if(value.isCollection()) {
                  map.put(prefix + listName, parseCollection(value.asCollection()));
                  tempCnt++;
               }
            }

            cnt = Math.max(cnt, tempCnt);
         }

         return cnt;
      }

      public int getRowCount() {
         return rows.size();
      }

      public Object getValue(int row, int col) {
         // column may be missing in this row
         if(col < names.size()) {
            String name = names.get(col);
            Object value = rows.get(row).get(name);

            if(!temporalTypes.isEmpty()) {
               value = temporalValue(name, value);
            }

            return transform(value, col);
         }

         return null;
      }

      private Object transform(Object oldValue, int col) {
         final String header = names.get(col);
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

      /**
       * @return true if every non-null value of the column converts to the temporal type.
       */
      boolean isConvertible(String name, Class<?> type) {
         for(Map<String, Object> row : rows) {
            Object value = row.get(name);

            if(value != null && convertTemporal(value, type) == null) {
               return false;
            }
         }

         return true;
      }

      private List<Map<String, Object>> rows = new ArrayList<>();
   }

   private void initTypedSchema(TabularDatasetSchema schema) {
      typedSchema = schema;

      if(schema == null) {
         return;
      }

      for(TabularColumn column : schema.columns()) {
         Class<?> type = temporalClass(column.type());

         if(type != null) {
            temporalTypes.put(column.name(), type);
         }
      }
   }

   /**
    * The type of a column as it is first met: the schema's date/time class for a column the schema
    * types as date/time (null cells included, so a null stays null instead of becoming ""),
    * otherwise the type of the returned value.
    */
   private Class<?> columnType(String name, ClientProperty prop) {
      Class<?> temporal = temporalTypes.get(name);

      if(temporal != null && !prop.hasCollectionValue()) {
         return temporal;
      }

      return prop.hasNullValue() || prop.hasCollectionValue() ? String.class :
         prop.getPrimitiveValue().getType().getDefaultType();
   }

   /**
    * Applies the schema once every row is read: a date/time column whose values do not all parse
    * goes back to the untyped (text) column, and the columns the schema describes but the
    * returned rows did not produce are added.
    */
   private void completeTyping() {
      if(typedSchema == null) {
         return;
      }

      for(Map.Entry<String, Class<?>> entry : temporalTypes.entrySet()) {
         String name = entry.getKey();

         if(!entry.getValue().equals(typeMap.get(name))) {
            continue;
         }

         boolean convertible = true;

         for(int i = 0; i < allRows.size() && convertible; i++) {
            convertible = allRows.get(i).isConvertible(name, entry.getValue());
         }

         if(!convertible) {
            LOG.warn("OData column '{}' has values that are not valid {}, keeping it as text",
                     name, entry.getValue().getSimpleName());
            types.set(names.indexOf(name), String.class);
            typeMap.put(name, String.class);
            revertedTemporal.add(name);
         }
      }

      padDescribedColumns();
   }

   /**
    * Makes the built columns include every column the schema describes. A column is missing when
    * no returned row carries it (a derived entity type with no instance in the result, a filter
    * or paging that excludes it, a complex property that is null in every row), and then the
    * column is added with null cells; for a complex property that is null in every row the
    * single text column the walk made for it is replaced by its dotted leaf columns. The
    * described columns say nothing about a query that narrows the columns itself ($select) or
    * reshapes the rows (expanded arrays), so those are left as returned.
    */
   private void padDescribedColumns() {
      if(expandArrays || (query.getSelect() != null && !query.getSelect().trim().isEmpty())) {
         return;
      }

      for(TabularColumn column : typedSchema.columns()) {
         String name = column.name();

         for(int dot = name.indexOf('.'); dot >= 0; dot = name.indexOf('.', dot + 1)) {
            String parent = name.substring(0, dot);

            if(typeMap.containsKey(parent)) {
               int index = names.indexOf(parent);
               names.remove(index);
               types.remove(index);
               typeMap.remove(parent);
               prefixes.add(parent);
            }
         }
      }

      for(TabularColumn column : typedSchema.columns()) {
         String name = column.name();

         if(!typeMap.containsKey(name) && !prefixes.contains(name)) {
            Class<?> type = describedClass(column.type());
            names.add(name);
            types.add(type);
            typeMap.put(name, type);
         }
      }
   }

   private Object temporalValue(String name, Object value) {
      Class<?> type = temporalTypes.get(name);

      if(type == null) {
         return value;
      }

      if(revertedTemporal.contains(name)) {
         return value == null ? "" : value;
      }

      return value == null ? null : convertTemporal(value, type);
   }

   private static Class<?> temporalClass(String xType) {
      if(XSchema.TIME_INSTANT.equals(xType)) {
         return java.sql.Timestamp.class;
      }

      if(XSchema.DATE.equals(xType)) {
         return java.sql.Date.class;
      }

      if(XSchema.TIME.equals(xType)) {
         return java.sql.Time.class;
      }

      return null;
   }

   private static Class<?> describedClass(String xType) {
      Class<?> temporal = temporalClass(xType);

      if(temporal != null) {
         return temporal;
      }

      switch(xType) {
         case XSchema.LONG:
            return Long.class;
         case XSchema.DOUBLE:
            return Double.class;
         case XSchema.BOOLEAN:
            return Boolean.class;
         default:
            return String.class;
      }
   }

   /**
    * @return the value as an instance of the date/time type, or null if it is not one. The server
    *         sends a date/time as ISO text unless the payload carries type annotations.
    */
   static Object convertTemporal(Object value, Class<?> type) {
      if(type.isInstance(value)) {
         return value;
      }

      Date date = null;

      if(value instanceof Calendar) {
         date = ((Calendar) value).getTime();
      }
      else if(value instanceof Date) {
         date = (Date) value;
      }

      if(date != null) {
         if(java.sql.Timestamp.class.equals(type)) {
            return new java.sql.Timestamp(date.getTime());
         }

         if(java.sql.Date.class.equals(type)) {
            return new java.sql.Date(date.getTime());
         }

         return new java.sql.Time(date.getTime());
      }

      if(!(value instanceof String)) {
         return null;
      }

      String text = ((String) value).trim();

      try {
         if(java.sql.Timestamp.class.equals(type)) {
            try {
               return java.sql.Timestamp.from(java.time.OffsetDateTime.parse(text).toInstant());
            }
            catch(java.time.format.DateTimeParseException ex) {
               return java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse(text));
            }
         }

         if(java.sql.Date.class.equals(type)) {
            return java.sql.Date.valueOf(java.time.LocalDate.parse(text));
         }

         return java.sql.Time.valueOf(java.time.LocalTime.parse(text));
      }
      catch(java.time.format.DateTimeParseException ex) {
         return null;
      }
   }

   private final ODataQuery query;
   private final List<String> names = new ArrayList<>();
   private final List<Class<?>> types = new ArrayList<>();
   private final Map<String, Class<?>> typeMap = new HashMap<>();
   private final Set<String> prefixes = new HashSet<>();
   private final XSwappableObjectList<ExpandedRows> allRows = new XSwappableObjectList<>(null);
   private final boolean expandArrays;
   // date/time columns the $metadata schema types, keyed by built column name; empty unless the
   // query is typed by metadata
   private final Map<String, Class<?>> temporalTypes = new HashMap<>();
   // date/time columns that fell back to text because a value did not parse
   private final Set<String> revertedTemporal = new HashSet<>();
   private TabularDatasetSchema typedSchema;
   private int index = 0; // index in allRows
   private int subIdx = -1; // index in ExpandedRows at (index)
   private static final Logger LOG = LoggerFactory.getLogger(OEntityTable.class.getName());
}
