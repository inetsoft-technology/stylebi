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
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.protocol.internal.ProtocolConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The equivalence test charter §6's C1 amendment requires as the price of letting
 * {@link CassandraTable}'s {@code DataType -> Class<?>} switch be extracted into
 * {@code CassandraTable.getType(DataType)} instead of staying frozen verbatim inside
 * {@code getType(int)}. C1 used to protect this mapping by forbidding any edit to the file; now
 * that an edit is allowed (in exactly the one shape docs/teams/2026-09-01-tabular-catalog-cassandra
 * permits — extract-and-delegate, no changed branch), this test is the replacement protection: it
 * pins the pre-extraction behavior for every protocol code the switch names.
 *
 * <p>Deliberately asserts through the public instance method {@link CassandraTable#getType(int)}
 * — the one {@code CassandraRuntime.runQuery} actually calls — rather than against the package-
 * private static {@code CassandraTable.getType(DataType)} directly. A test that only exercised the
 * static function would still pass even if {@code getType(int)} were wired to delegate to the
 * wrong thing (or not delegate at all), because it would never call the delegating method.
 *
 * <p>Every fall-through group the switch declares gets two separate cases here on purpose
 * (BIGINT/COUNTER, UUID/TIMEUUID, ASCII/VARCHAR) — that's exactly where a careless extraction
 * silently drops one of the two labels. {@code UDT} is not named in the switch at all; it stands
 * in for the {@code default} branch.
 */
class CassandraTableTypeEquivalenceTest {

   @ParameterizedTest(name = "protocol code {0} -> {1}")
   @MethodSource("everyProtocolCodeNamedInTheSwitch")
   void getType_matchesThePreExtractionMapping(int protocolCode, Class<?> expected)
      throws Exception
   {
      CassandraTable table = singleColumnTable(protocolCode);

      assertEquals(expected, table.getType(0));
   }

   static Stream<Arguments> everyProtocolCodeNamedInTheSwitch() {
      return Stream.of(
         Arguments.of(ProtocolConstants.DataType.BIGINT, Long.class),
         Arguments.of(ProtocolConstants.DataType.COUNTER, Long.class),
         Arguments.of(ProtocolConstants.DataType.INT, Integer.class),
         Arguments.of(ProtocolConstants.DataType.VARINT, BigInteger.class),
         Arguments.of(ProtocolConstants.DataType.DECIMAL, BigDecimal.class),
         Arguments.of(ProtocolConstants.DataType.DOUBLE, Double.class),
         Arguments.of(ProtocolConstants.DataType.FLOAT, Float.class),
         Arguments.of(ProtocolConstants.DataType.BOOLEAN, Boolean.class),
         Arguments.of(ProtocolConstants.DataType.TIMESTAMP, java.util.Date.class),
         Arguments.of(ProtocolConstants.DataType.LIST, java.util.List.class),
         Arguments.of(ProtocolConstants.DataType.MAP, java.util.Map.class),
         Arguments.of(ProtocolConstants.DataType.SET, java.util.Set.class),
         Arguments.of(ProtocolConstants.DataType.UUID, UUID.class),
         Arguments.of(ProtocolConstants.DataType.TIMEUUID, UUID.class),
         Arguments.of(ProtocolConstants.DataType.BLOB, Image.class),
         Arguments.of(ProtocolConstants.DataType.INET, InetAddress.class),
         Arguments.of(ProtocolConstants.DataType.TUPLE, TupleValue.class),
         Arguments.of(ProtocolConstants.DataType.ASCII, String.class),
         Arguments.of(ProtocolConstants.DataType.VARCHAR, String.class),
         // Not named in the switch — exercises the default branch.
         Arguments.of(ProtocolConstants.DataType.UDT, Object.class)
      );
   }

   /**
    * Builds a real {@link CassandraTable} — not a mock of it — around a single mocked column, so
    * the assertion in the test method above goes through the real {@code getType(int)} body,
    * including its cached {@code types[]} array built by the constructor from
    * {@code ResultSet.getColumnDefinitions()}.
    */
   private static CassandraTable singleColumnTable(int protocolCode) throws Exception {
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

      CqlSession session = mock(CqlSession.class);

      return new CassandraTable(result, session, 0);
   }
}
