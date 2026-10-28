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
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.protocol.internal.ProtocolConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for Bug #79584. The driver's primitive getters (getLong, getInt, getDouble,
 * getFloat, getBoolean) return 0/false for a CQL null, so {@link CassandraTable#getObject(int)}
 * used to hand 0/false to the engine for null cells of those column types, and aggregates such
 * as Average counted the nulls as zeros. The mocked row mimics the driver: the primitive getters
 * return 0/false and {@code isNull(col)} reports true.
 */
class CassandraTableNullCellTest {
   @ParameterizedTest(name = "null cell of protocol code {0} -> null")
   @ValueSource(ints = {
      ProtocolConstants.DataType.BIGINT,
      ProtocolConstants.DataType.COUNTER,
      ProtocolConstants.DataType.INT,
      ProtocolConstants.DataType.DOUBLE,
      ProtocolConstants.DataType.DECIMAL,
      ProtocolConstants.DataType.FLOAT,
      ProtocolConstants.DataType.BOOLEAN
   })
   void getObject_returnsNullForNullPrimitiveBackedCell(int protocolCode) throws Exception {
      Row row = mock(Row.class);
      when(row.isNull(0)).thenReturn(true);
      when(row.getLong(0)).thenReturn(0L);
      when(row.getInt(0)).thenReturn(0);
      when(row.getDouble(0)).thenReturn(0.0D);
      when(row.getFloat(0)).thenReturn(0.0F);
      when(row.getBoolean(0)).thenReturn(false);

      CassandraTable table = tableWithRow(protocolCode, row);

      assertNull(table.getObject(0));
   }

   @Test
   void getObject_stillReturnsValueForNonNullCells() throws Exception {
      Row row = mock(Row.class);
      when(row.isNull(0)).thenReturn(false);
      when(row.getLong(0)).thenReturn(42L);
      when(row.getInt(0)).thenReturn(7);
      when(row.getDouble(0)).thenReturn(2.5D);
      when(row.getFloat(0)).thenReturn(1.5F);
      when(row.getBoolean(0)).thenReturn(true);

      assertEquals(42L, tableWithRow(ProtocolConstants.DataType.BIGINT, row).getObject(0));
      assertEquals(42L, tableWithRow(ProtocolConstants.DataType.COUNTER, row).getObject(0));
      assertEquals(7, tableWithRow(ProtocolConstants.DataType.INT, row).getObject(0));
      assertEquals(2.5D, tableWithRow(ProtocolConstants.DataType.DOUBLE, row).getObject(0));
      assertEquals(2.5D, tableWithRow(ProtocolConstants.DataType.DECIMAL, row).getObject(0));
      assertEquals(1.5F, tableWithRow(ProtocolConstants.DataType.FLOAT, row).getObject(0));
      assertEquals(true, tableWithRow(ProtocolConstants.DataType.BOOLEAN, row).getObject(0));
   }

   /**
    * Builds a real {@link CassandraTable} around a single mocked column and positions its cursor
    * on the given row, so the assertion goes through the real {@code getObject(int)} body.
    */
   private static CassandraTable tableWithRow(int protocolCode, Row row) throws Exception {
      DataType type = mock(DataType.class);
      when(type.getProtocolCode()).thenReturn(protocolCode);

      ColumnDefinition column = mock(ColumnDefinition.class);
      when(column.getName()).thenReturn(CqlIdentifier.fromInternal("col"));
      when(column.getType()).thenReturn(type);

      ColumnDefinitions columns = mock(ColumnDefinitions.class);
      when(columns.size()).thenReturn(1);
      when(columns.get(0)).thenReturn(column);

      ResultSet result = mock(ResultSet.class);
      when(result.getColumnDefinitions()).thenReturn(columns);
      when(result.one()).thenReturn(row);

      CassandraTable table = new CassandraTable(result, mock(CqlSession.class), 0);
      assertTrue(table.next());

      return table;
   }
}
