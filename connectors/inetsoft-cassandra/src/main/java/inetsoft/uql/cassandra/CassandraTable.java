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
package inetsoft.uql.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.ListType;
import com.datastax.oss.driver.api.core.type.MapType;
import com.datastax.oss.driver.api.core.type.SetType;
import com.datastax.oss.protocol.internal.ProtocolConstants;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTableNode;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A class to map a Cassandra resultset to a table.
 */
public class CassandraTable extends XTableNode {
   public CassandraTable(ResultSet result, CqlSession session, int maxrows) throws Exception {
      this.result = result;
      this.session = session;
      this.maxrows = maxrows;

      ColumnDefinitions cols = result.getColumnDefinitions();
      names = new String[cols.size()];
      types = new DataType[names.length];

      for(int i = 0; i < names.length; i++) {
         names[i] = cols.get(i).getName().asInternal();
         types[i] = cols.get(i).getType();
      }
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    * @return true if there are more rows.
    */
   @Override
   public synchronized boolean next() {
      curr = (maxrows > 0 && rowcnt >= maxrows) ? null : result.one();

      if(curr == null) {
         session.close();
      }
      else {
         rowcnt++;
      }

      return curr != null;
   }

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    * @return true if the rewinding is successful.
    */
   @Override
   public synchronized boolean rewind() {
      return false;
   }

   /**
    * Check if the cursor can be rewinded.
    * @return true if the cursor can be rewinded.
    */
   @Override
   public boolean isRewindable() {
      return false;
   }

   /**
    * Get the number of columns in the table.
    */
   @Override
   public int getColCount() {
      return names.length;
   }

   /**
    * Get the column name.
    * @param col column index.
    * @return column name or alias if defined.
    */
   @Override
   public String getName(int col) {
      return names[col];
   }

   /**
    * Get the column type.
    * @param col column index.
    * @return column data type.
    */
   @Override
   public Class<?> getType(int col) {
      return getType(types[col]);
   }

   /**
    * Maps a Cassandra {@link DataType} to the Java class {@link #getObject(int)} returns values
    * as. Extracted verbatim from the former body of {@link #getType(int)} — case labels, return
    * values, and the {@code default} are unchanged — so that a schema-only caller (one with a
    * {@code DataType} from {@code ColumnMetadata.getType()} but no live {@link ResultSet} to build
    * a {@code CassandraTable} from) can reuse the identical mapping instead of duplicating it.
    * {@link #getType(int)} now delegates here rather than switching directly, so this one switch
    * is the sole copy in the module; see
    * {@code CassandraTableTypeEquivalenceTest} for the regression test that stands in place of the
    * "this file does not change" guarantee this extraction gives up.
    */
   static Class<?> getType(DataType type) {
      switch(type.getProtocolCode()) {
      case ProtocolConstants.DataType.BIGINT:
      case ProtocolConstants.DataType.COUNTER:
         return Long.class;
      case ProtocolConstants.DataType.INT:
         return Integer.class;
      case ProtocolConstants.DataType.SMALLINT:
         return Short.class;
      case ProtocolConstants.DataType.TINYINT:
         return Byte.class;
      case ProtocolConstants.DataType.VARINT:
         return BigInteger.class;
      case ProtocolConstants.DataType.DECIMAL:
         return BigDecimal.class;
      case ProtocolConstants.DataType.DOUBLE:
         return Double.class;
      case ProtocolConstants.DataType.FLOAT:
         return Float.class;
      case ProtocolConstants.DataType.BOOLEAN:
         return Boolean.class;
      case ProtocolConstants.DataType.TIMESTAMP:
         return java.util.Date.class;
      case ProtocolConstants.DataType.DATE:
         return java.sql.Date.class;
      case ProtocolConstants.DataType.TIME:
         return java.sql.Time.class;
      case ProtocolConstants.DataType.DURATION:
         return String.class;
      case ProtocolConstants.DataType.LIST:
         return java.util.List.class;
      case ProtocolConstants.DataType.MAP:
         return java.util.Map.class;
      case ProtocolConstants.DataType.SET:
         return java.util.Set.class;
      case ProtocolConstants.DataType.UUID:
      case ProtocolConstants.DataType.TIMEUUID:
         return UUID.class;
      case ProtocolConstants.DataType.BLOB:
         return Image.class;
      case ProtocolConstants.DataType.INET:
         return InetAddress.class;
      case ProtocolConstants.DataType.TUPLE:
         return TupleValue.class;
      case ProtocolConstants.DataType.ASCII:
      case ProtocolConstants.DataType.VARCHAR:
         return String.class;
      default:
         return Object.class;
      }
   }

   /**
    * Get the value in the current row at the specified column.
    * @param col column index.
    * @return column value.
    */
   @Override
   public synchronized Object getObject(int col) {
      // The driver's primitive getters (getLong, getInt, getDouble, getFloat, getBoolean) map a
      // CQL null to 0/false, which corrupts aggregates, so null must be detected up front.
      if(curr.isNull(col)) {
         return null;
      }

      switch(types[col].getProtocolCode()) {
      case ProtocolConstants.DataType.BIGINT:
      case ProtocolConstants.DataType.COUNTER:
         return curr.getLong(col);
      case ProtocolConstants.DataType.INT:
         return curr.getInt(col);
      case ProtocolConstants.DataType.VARINT:
         return curr.getBigInteger(col);
      case ProtocolConstants.DataType.DECIMAL:
         BigDecimal decimal = curr.getBigDecimal(col);
         return decimal == null ? null : decimal.doubleValue();
      case ProtocolConstants.DataType.DOUBLE:
         return curr.getDouble(col);
      case ProtocolConstants.DataType.FLOAT:
         return curr.getFloat(col);
      case ProtocolConstants.DataType.BOOLEAN:
         return curr.getBoolean(col);
      case ProtocolConstants.DataType.TIMESTAMP:
         Instant instant = curr.getInstant(col);
         return instant == null ? null : new Date(instant.toEpochMilli());
      case ProtocolConstants.DataType.SMALLINT:
      case ProtocolConstants.DataType.TINYINT:
      case ProtocolConstants.DataType.DATE:
      case ProtocolConstants.DataType.TIME:
      case ProtocolConstants.DataType.DURATION:
      case ProtocolConstants.DataType.LIST:
      case ProtocolConstants.DataType.MAP:
      case ProtocolConstants.DataType.SET:
         return convert(curr.getObject(col), types[col]);
      case ProtocolConstants.DataType.UUID:
      case ProtocolConstants.DataType.TIMEUUID:
         return curr.getUuid(col);
      case ProtocolConstants.DataType.BLOB:
         ByteBuffer buf = curr.getByteBuffer(col);

         try {
            return buf == null ?
               null : Tool.getImage(new ByteArrayInputStream(buf.array()), false);
         }
         catch(Exception ex) {
            return buf;
         }
      case ProtocolConstants.DataType.INET:
         return curr.getInetAddress(col);
      case ProtocolConstants.DataType.TUPLE:
         return curr.getTupleValue(col);
      case ProtocolConstants.DataType.ASCII:
      case ProtocolConstants.DataType.VARCHAR:
         return curr.getString(col);
      default:
         try {
            return curr.getUdtValue(col);
         }
         catch(Throwable ex) {
            LOG.info("Failed to get value: (" + col + ")", ex);
            return null;
         }
      }
   }

   /**
    * Converts a value read with the driver's own codec for {@code type} to the class
    * {@link #getType(DataType)} reports for it. Collections are converted element by element with
    * the element's own type, so nested and frozen collections follow the same mapping as columns.
    * Tuples and UDTs are returned as the driver values, as they are for a top-level column.
    */
   private static Object convert(Object value, DataType type) {
      if(value == null) {
         return null;
      }

      switch(type.getProtocolCode()) {
      case ProtocolConstants.DataType.DATE:
         return java.sql.Date.valueOf((LocalDate) value);
      case ProtocolConstants.DataType.TIME:
         // java.sql.Time has no sub-second precision
         return java.sql.Time.valueOf((LocalTime) value);
      case ProtocolConstants.DataType.TIMESTAMP:
         return new Date(((Instant) value).toEpochMilli());
      case ProtocolConstants.DataType.DURATION:
         return value.toString();
      case ProtocolConstants.DataType.LIST:
         DataType listElement = ((ListType) type).getElementType();
         List<Object> list = new ArrayList<>();

         for(Object element : (List<?>) value) {
            list.add(convert(element, listElement));
         }

         return list;
      case ProtocolConstants.DataType.SET:
         DataType setElement = ((SetType) type).getElementType();
         Set<Object> set = new LinkedHashSet<>();

         for(Object element : (Set<?>) value) {
            set.add(convert(element, setElement));
         }

         return set;
      case ProtocolConstants.DataType.MAP:
         MapType mapType = (MapType) type;
         Map<Object, Object> map = new LinkedHashMap<>();

         for(Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            map.put(convert(entry.getKey(), mapType.getKeyType()),
                    convert(entry.getValue(), mapType.getValueType()));
         }

         return map;
      default:
         return value;
      }
   }

   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return null;
   }

   private final String[] names;
   private final DataType[] types;
   private final ResultSet result;
   private final CqlSession session;
   private Row curr;
   private int rowcnt = 0;
   private int maxrows = 0;

   private static final Logger LOG = LoggerFactory.getLogger(CassandraTable.class.getName());
}
