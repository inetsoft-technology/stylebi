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
import com.datastax.oss.driver.api.core.type.*;
import com.datastax.oss.protocol.internal.ProtocolConstants;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Blob and inet elements inside collections render like the same types at top level.
 */
class CassandraTableCollectionElementTest {
   private static final String HELLO_HEX = "0x68656c6c6f";
   private static final String IP = "192.168.1.10";

   @Test
   void listOfBlob() throws Exception {
      ListType type = list(simple(ProtocolConstants.DataType.BLOB));
      assertEquals(List.of(HELLO_HEX), value(type, List.of(blob())));
   }

   @Test
   void listOfInet() throws Exception {
      ListType type = list(simple(ProtocolConstants.DataType.INET));
      assertEquals(List.of(IP), value(type, List.of(inet())));
   }

   @Test
   void setOfInet() throws Exception {
      DataType element = simple(ProtocolConstants.DataType.INET);
      SetType type = mock(SetType.class);
      when(type.getProtocolCode()).thenReturn(ProtocolConstants.DataType.SET);
      when(type.getElementType()).thenReturn(element);
      assertEquals(Set.of(IP), value(type, Set.of(inet())));
   }

   @Test
   void mapOfInetToBlob() throws Exception {
      DataType key = simple(ProtocolConstants.DataType.INET);
      DataType val = simple(ProtocolConstants.DataType.BLOB);
      MapType type = mock(MapType.class);
      when(type.getProtocolCode()).thenReturn(ProtocolConstants.DataType.MAP);
      when(type.getKeyType()).thenReturn(key);
      when(type.getValueType()).thenReturn(val);
      assertEquals(Map.of(IP, HELLO_HEX), value(type, Map.of(inet(), blob())));
   }

   @Test
   void nestedListOfSetOfInet() throws Exception {
      DataType element = simple(ProtocolConstants.DataType.INET);
      SetType inner = mock(SetType.class);
      when(inner.getProtocolCode()).thenReturn(ProtocolConstants.DataType.SET);
      when(inner.getElementType()).thenReturn(element);
      ListType outer = list(inner);
      assertEquals(List.of(Set.of(IP)), value(outer, List.of(Set.of(inet()))));
   }

   @Test
   void slicedBlobElementUsesOnlyRemainingBytes() throws Exception {
      byte[] backing = "xxhelloyy".getBytes(StandardCharsets.UTF_8);
      ByteBuffer slice = ByteBuffer.wrap(backing, 2, 5).slice();
      ListType type = list(simple(ProtocolConstants.DataType.BLOB));
      assertEquals(List.of(HELLO_HEX), value(type, List.of(slice)));
   }

   private static ByteBuffer blob() {
      return ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8));
   }

   private static InetAddress inet() throws Exception {
      return InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, 1, 10 });
   }

   private static DataType simple(int code) {
      DataType type = mock(DataType.class);
      when(type.getProtocolCode()).thenReturn(code);
      return type;
   }

   private static ListType list(DataType element) {
      ListType type = mock(ListType.class);
      when(type.getProtocolCode()).thenReturn(ProtocolConstants.DataType.LIST);
      when(type.getElementType()).thenReturn(element);
      return type;
   }

   private static Object value(DataType type, Object raw) throws Exception {
      Row row = mock(Row.class);
      when(row.getObject(0)).thenReturn(raw);

      ColumnDefinition column = mock(ColumnDefinition.class);
      when(column.getName()).thenReturn(CqlIdentifier.fromInternal("col"));
      when(column.getType()).thenReturn(type);

      ColumnDefinitions columns = mock(ColumnDefinitions.class);
      when(columns.size()).thenReturn(1);
      when(columns.get(0)).thenReturn(column);

      ResultSet result = mock(ResultSet.class);
      when(result.getColumnDefinitions()).thenReturn(columns);
      when(result.one()).thenReturn(row, (Row) null);

      CassandraTable table = new CassandraTable(result, mock(CqlSession.class), 0);
      assertTrue(table.next());
      return table.getObject(0);
   }
}
