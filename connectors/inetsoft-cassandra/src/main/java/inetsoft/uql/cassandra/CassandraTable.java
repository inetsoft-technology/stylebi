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
import java.util.Date;
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
      switch(types[col].getProtocolCode()) {
      case ProtocolConstants.DataType.BIGINT:
      case ProtocolConstants.DataType.COUNTER:
         return Long.class;
      case ProtocolConstants.DataType.INT:
         return Integer.class;
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
      switch(types[col].getProtocolCode()) {
      case ProtocolConstants.DataType.BIGINT:
      case ProtocolConstants.DataType.COUNTER:
         return curr.getLong(col);
      case ProtocolConstants.DataType.INT:
         return curr.getInt(col);
      case ProtocolConstants.DataType.VARINT:
         return curr.getBigInteger(col);
      case ProtocolConstants.DataType.DECIMAL:
      case ProtocolConstants.DataType.DOUBLE:
         return curr.getDouble(col);
      case ProtocolConstants.DataType.FLOAT:
         return curr.getFloat(col);
      case ProtocolConstants.DataType.BOOLEAN:
         return curr.getBoolean(col);
      case ProtocolConstants.DataType.TIMESTAMP:
         Instant instant = curr.getInstant(col);
         return instant == null ? null : new Date(instant.toEpochMilli());
      case ProtocolConstants.DataType.LIST:
         try {
            return curr.getList(col, String.class);
         }
         catch(Throwable ex) {
            LOG.info("Failed to get value: (" + col + ")", ex);
            return null;
         }
      case ProtocolConstants.DataType.MAP:
         try {
            return curr.getMap(col, String.class, String.class);
         }
         catch(Throwable ex) {
            LOG.info("Failed to get value: (" + col + ")", ex);
            return null;
         }
      case ProtocolConstants.DataType.SET:
         try {
            return curr.getSet(col, String.class);
         }
         catch(Throwable ex) {
            LOG.info("Failed to get value: (" + col + ")", ex);
            return null;
         }
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
