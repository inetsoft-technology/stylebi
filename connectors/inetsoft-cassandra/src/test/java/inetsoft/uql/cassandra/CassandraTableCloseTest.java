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
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for Bug #79588: a {@link CassandraTable} owns the {@link CqlSession} it was
 * created with, but released it only when {@link CassandraTable#next()} ran off the end, so a table
 * that was closed or cancelled without being read to the end leaked its session.
 */
class CassandraTableCloseTest {
   @Test
   void closeWithoutReadingReleasesTheSessionOnce() throws Exception {
      CqlSession session = mock(CqlSession.class);
      CassandraTable table = newTable(session, 0);

      table.close();

      verify(session, times(1)).close();
   }

   @Test
   void closeIsIdempotent() throws Exception {
      CqlSession session = mock(CqlSession.class);
      CassandraTable table = newTable(session, 0);

      table.close();
      table.close();

      verify(session, times(1)).close();
   }

   @Test
   void closeAfterExhaustionDoesNotCloseTheSessionTwice() throws Exception {
      CqlSession session = mock(CqlSession.class);
      CassandraTable table = newTable(session, 0);

      assertTrue(table.next());
      assertFalse(table.next());
      table.close();

      verify(session, times(1)).close();
   }

   @Test
   void closeAfterReachingMaxRowsDoesNotCloseTheSessionTwice() throws Exception {
      CqlSession session = mock(CqlSession.class);
      CassandraTable table = newTable(session, 1);

      assertTrue(table.next());
      assertFalse(table.next());
      table.close();

      verify(session, times(1)).close();
   }

   @Test
   void sessionStaysOpenWhileRowsRemain() throws Exception {
      CqlSession session = mock(CqlSession.class);
      newTable(session, 0);

      verify(session, never()).close();
   }

   @Test
   void cancelReleasesTheSessionOnce() throws Exception {
      CqlSession session = mock(CqlSession.class);
      CassandraTable table = newTable(session, 0);

      table.cancel();
      table.close();

      verify(session, times(1)).close();
   }

   private static CassandraTable newTable(CqlSession session, int maxrows) throws Exception {
      ColumnDefinitions columns = mock(ColumnDefinitions.class);
      when(columns.size()).thenReturn(0);

      Row row = mock(Row.class);
      ResultSet result = mock(ResultSet.class);
      when(result.getColumnDefinitions()).thenReturn(columns);
      // one row, then exhausted
      when(result.one()).thenReturn(row, (Row) null);

      return new CassandraTable(result, session, maxrows);
   }
}
