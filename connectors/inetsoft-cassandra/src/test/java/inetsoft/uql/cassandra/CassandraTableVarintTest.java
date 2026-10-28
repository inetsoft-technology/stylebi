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

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79586: a varint beyond 64 bits must not reach the core table lens as a BigInteger, which
 * it narrows to a long. The value is returned as its exact decimal text instead.
 */
class CassandraTableVarintTest {
   @Test
   void varintBeyondSixtyFourBitsIsReturnedExactly() throws Exception {
      BigInteger big = new BigInteger("123456789012345678901234567890");

      CassandraTable table = varintTable(big);

      assertEquals(String.class, table.getType(0));
      table.next();
      assertEquals("123456789012345678901234567890", table.getObject(0));
   }

   @Test
   void smallAndNegativeVarintsKeepTheirDecimalText() throws Exception {
      CassandraTable table = varintTable(BigInteger.valueOf(-42));
      table.next();
      assertEquals("-42", table.getObject(0));
   }

   @Test
   void nullVarintIsNull() throws Exception {
      CassandraTable table = varintTable(null);
      table.next();
      assertNull(table.getObject(0));
   }

   private static CassandraTable varintTable(BigInteger value) throws Exception {
      DataType type = mock(DataType.class);
      when(type.getProtocolCode()).thenReturn(ProtocolConstants.DataType.VARINT);

      ColumnDefinition column = mock(ColumnDefinition.class);
      when(column.getName()).thenReturn(CqlIdentifier.fromInternal("col"));
      when(column.getType()).thenReturn(type);

      ColumnDefinitions columns = mock(ColumnDefinitions.class);
      when(columns.size()).thenReturn(1);
      when(columns.get(0)).thenReturn(column);

      Row row = mock(Row.class);
      when(row.getBigInteger(0)).thenReturn(value);

      ResultSet result = mock(ResultSet.class);
      when(result.getColumnDefinitions()).thenReturn(columns);
      when(result.one()).thenReturn(row);

      return new CassandraTable(result, mock(CqlSession.class), 0);
   }
}
