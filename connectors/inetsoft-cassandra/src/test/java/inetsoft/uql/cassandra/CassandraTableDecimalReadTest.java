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

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.internal.core.cql.DefaultRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for Bug #79583: {@link CassandraTable#getObject(int)} read DECIMAL columns with
 * {@code Row.getDouble}, which has no DECIMAL codec in the driver and throws
 * {@code CodecNotFoundException}. The tests use a real driver {@link DefaultRow} so the real codec
 * lookup runs; a mocked {@code Row} would not reproduce the failure.
 */
class CassandraTableDecimalReadTest {

   @Test
   void decimalColumnIsReadAsDouble() throws Exception {
      CassandraTable table = tableWithRow(DataTypes.DECIMAL,
         TypeCodecs.DECIMAL.encode(new BigDecimal("12.50"), ProtocolVersion.DEFAULT));

      assertEquals(12.5, (Double) table.getObject(0), 0.0);
   }

   @Test
   void nullDecimalColumnIsReadAsNull() throws Exception {
      CassandraTable table = tableWithRow(DataTypes.DECIMAL, null);

      assertNull(table.getObject(0));
   }

   @Test
   void doubleColumnIsStillReadAsDouble() throws Exception {
      CassandraTable table = tableWithRow(DataTypes.DOUBLE,
         TypeCodecs.DOUBLE.encodePrimitive(3.5, ProtocolVersion.DEFAULT));

      assertEquals(3.5, (Double) table.getObject(0), 0.0);
   }

   private static CassandraTable tableWithRow(DataType type, ByteBuffer value) throws Exception {
      ColumnDefinition column = mock(ColumnDefinition.class);
      when(column.getName()).thenReturn(CqlIdentifier.fromInternal("col"));
      when(column.getType()).thenReturn(type);

      ColumnDefinitions columns = mock(ColumnDefinitions.class);
      when(columns.size()).thenReturn(1);
      when(columns.get(0)).thenReturn(column);

      DefaultRow row = new DefaultRow(columns, Collections.singletonList(value));

      ResultSet result = mock(ResultSet.class);
      when(result.getColumnDefinitions()).thenReturn(columns);
      when(result.one()).thenReturn(row);

      CassandraTable table = new CassandraTable(result, mock(CqlSession.class), 0);
      table.next();
      return table;
   }
}
