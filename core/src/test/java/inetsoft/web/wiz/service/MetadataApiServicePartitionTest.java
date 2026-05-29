/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.service;

import inetsoft.uql.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("core")
class MetadataApiServicePartitionTest {

   @Test
   void isPostgresDriver_returnsTrueForPostgresDriver() {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDriver()).thenReturn("org.postgresql.Driver");
      assertTrue(MetadataApiService.isPostgresDriver(ds));
   }

   @Test
   void isPostgresDriver_returnsFalseForMysqlDriver() {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDriver()).thenReturn("com.mysql.cj.jdbc.Driver");
      assertFalse(MetadataApiService.isPostgresDriver(ds));
   }

   @Test
   void isPostgresDriver_returnsFalseForNullDriver() {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDriver()).thenReturn(null);
      assertFalse(MetadataApiService.isPostgresDriver(ds));
   }

   @Test
   void findPostgresPartitionChildren_returnsLowercasedQualifiedNames() throws Exception {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDriver()).thenReturn("org.postgresql.Driver");

      java.sql.Connection conn = mock(java.sql.Connection.class);
      java.sql.PreparedStatement ps = mock(java.sql.PreparedStatement.class);
      java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
      when(conn.prepareStatement(anyString())).thenReturn(ps);
      when(ps.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(true, true, true, false);
      when(rs.getString(1))
         .thenReturn("public.payment_p2007_01", "public.payment_p2007_02", "ANALYTICS.AUDIT_2026_05");

      java.util.Set<String> result = MetadataApiService.findPostgresPartitionChildren(ds, conn);

      assertEquals(
         java.util.Set.of("public.payment_p2007_01", "public.payment_p2007_02", "analytics.audit_2026_05"),
         result
      );
   }

   @Test
   void findPostgresPartitionChildren_returnsEmptySetForNonPostgres() throws Exception {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDriver()).thenReturn("com.mysql.cj.jdbc.Driver");
      java.sql.Connection conn = mock(java.sql.Connection.class);

      java.util.Set<String> result = MetadataApiService.findPostgresPartitionChildren(ds, conn);

      assertTrue(result.isEmpty());
      // The mock connection must never be touched on the non-Postgres path.
      verifyNoInteractions(conn);
   }

   @Test
   void findPostgresPartitionChildren_returnsEmptySetForEmptyResultSet() throws Exception {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDriver()).thenReturn("org.postgresql.Driver");

      java.sql.Connection conn = mock(java.sql.Connection.class);
      java.sql.PreparedStatement ps = mock(java.sql.PreparedStatement.class);
      java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
      when(conn.prepareStatement(anyString())).thenReturn(ps);
      when(ps.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(false);

      java.util.Set<String> result = MetadataApiService.findPostgresPartitionChildren(ds, conn);

      assertTrue(result.isEmpty());
   }

   @Test
   void findPostgresPartitionChildren_closesResources() throws Exception {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDriver()).thenReturn("org.postgresql.Driver");

      java.sql.Connection conn = mock(java.sql.Connection.class);
      java.sql.PreparedStatement ps = mock(java.sql.PreparedStatement.class);
      java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
      when(conn.prepareStatement(anyString())).thenReturn(ps);
      when(ps.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(false);

      MetadataApiService.findPostgresPartitionChildren(ds, conn);

      // try-with-resources should close both
      verify(ps).close();
      verify(rs).close();
   }

   @Test
   void findPostgresPartitionChildren_closesPreparedStatementWhenExecuteQueryThrows() throws Exception {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDriver()).thenReturn("org.postgresql.Driver");

      java.sql.Connection conn = mock(java.sql.Connection.class);
      java.sql.PreparedStatement ps = mock(java.sql.PreparedStatement.class);
      when(conn.prepareStatement(anyString())).thenReturn(ps);
      when(ps.executeQuery()).thenThrow(new java.sql.SQLException("simulated catalog read failure"));

      // executeQuery throws, but try-with-resources must still close ps before
      // the exception propagates.
      assertThrows(java.sql.SQLException.class,
         () -> MetadataApiService.findPostgresPartitionChildren(ds, conn));

      verify(ps).close();
   }

   @Test
   void isPartitionChild_bySchemaAndTable_matchesLowercased() {
      java.util.Set<String> partitions = java.util.Set.of(
         "public.payment_p2007_01", "public.payment_p2007_02"
      );
      assertTrue(
         MetadataApiService.isPartitionChild("public", "payment_p2007_01", partitions));
      assertTrue(
         MetadataApiService.isPartitionChild("PUBLIC", "Payment_P2007_01", partitions));
      assertFalse(
         MetadataApiService.isPartitionChild("public", "payment", partitions));
   }

   @Test
   void isPartitionChild_bySchemaAndTable_nullSchemaBuildsBareKey() {
      java.util.Set<String> partitions = java.util.Set.of("payment_p2007_01");
      assertTrue(
         MetadataApiService.isPartitionChild(null, "payment_p2007_01", partitions));
   }

   @Test
   void isPartitionChild_bySchemaAndTable_emptyPartitionSetMatchesNothing() {
      java.util.Set<String> partitions = java.util.Set.of();
      assertFalse(
         MetadataApiService.isPartitionChild("public", "anything", partitions));
   }

   @Test
   void isPartitionChild_byAssetPath_parsesWizFormat() {
      java.util.Set<String> partitions = java.util.Set.of("public.payment_p2007_01");
      // Wiz asset paths look like "dsName/TABLE/schema/tableName"
      assertTrue(
         MetadataApiService.isPartitionChild("postgres/TABLE/public/payment_p2007_01", partitions));
      assertFalse(
         MetadataApiService.isPartitionChild("postgres/TABLE/public/payment", partitions));
      assertFalse(
         MetadataApiService.isPartitionChild("postgres/VIEW/public/payment_p2007_01", partitions));
   }

   @Test
   void isPartitionChild_byAssetPath_handlesShortOrEmptyPaths() {
      java.util.Set<String> partitions = java.util.Set.of("public.payment_p2007_01");
      assertFalse(
         MetadataApiService.isPartitionChild((String) null, partitions));
      assertFalse(
         MetadataApiService.isPartitionChild("", partitions));
      assertFalse(
         MetadataApiService.isPartitionChild("postgres", partitions));
      assertFalse(
         MetadataApiService.isPartitionChild("postgres/TABLE", partitions));
      assertFalse(
         MetadataApiService.isPartitionChild("postgres/TABLE/public", partitions));
   }
}
