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

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.request.FkIntegrityRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers {@link FkIntegrityService}, which answers one question for wiz-services: how many rows
 * would an INNER join from a fact table to its FK target silently drop?
 *
 * <p>Two properties are load-bearing and are what this test class exists to pin down:</p>
 * <ul>
 *   <li><b>Identifier validation is the security boundary.</b> The endpoint deliberately does not
 *       accept SQL — it accepts identifiers and builds the statement server-side. Every identifier
 *       segment must match {@code ^[A-Za-z_][A-Za-z0-9_]*$}. Anything else is rejected outright,
 *       never quoted or escaped into "safety", and never executed.</li>
 *   <li><b>A wrong zero is the worst possible failure.</b> {@code 0} means "no rows would be
 *       dropped", which is what opens the gate for the join injection. So no failure path may
 *       degrade into a count: exceptions propagate, and an empty or NULL result row is an error,
 *       not a zero.</li>
 * </ul>
 */
@Tag("core")
class FkIntegrityServiceTest {
   // ------------------------------------------------------------------
   // Identifier validation — the security boundary
   // ------------------------------------------------------------------

   @ParameterizedTest
   @ValueSource(strings = { "partner_id", "sale_order", "_private", "Order2", "A1_b2" })
   void validateColumnIdentifier_acceptsPlainIdentifiers(String identifier) {
      assertEquals(identifier, FkIntegrityService.validateColumnIdentifier(identifier, "fkColumn"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "sale_order", "public.sale_order", "_s._t" })
   void validateTableIdentifier_acceptsPlainAndSchemaQualifiedTables(String identifier) {
      assertEquals(identifier, FkIntegrityService.validateTableIdentifier(identifier, "sourceTable"));
   }

   /**
    * The injection attempts the endpoint exists to refuse. Each of these must be rejected as a
    * 400-class {@link IllegalArgumentException} — not sanitized, not quoted, not executed.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      "partner_id; DROP TABLE x",
      "partner_id--",
      "partner_id'",
      "\"partner_id\"",
      "partner id",
      "partner_id)",
      "1partner",
      "partner-id",
      "partner_id/*x*/",
      " partner_id",
      "partner_id ",
      "パートナー"
   })
   void validateColumnIdentifier_rejectsAnythingOutsideTheAllowedCharacterSet(String identifier) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> FkIntegrityService.validateColumnIdentifier(identifier, "fkColumn"));

      assertTrue(ex.getMessage().contains("fkColumn"),
         "the error must name the offending field so the caller can fix its request");
   }

   @ParameterizedTest
   @NullAndEmptySource
   void validateColumnIdentifier_rejectsNullAndEmpty(String identifier) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> FkIntegrityService.validateColumnIdentifier(identifier, "fkColumn"));

      assertTrue(ex.getMessage().contains("fkColumn"));
   }

   @ParameterizedTest
   @NullAndEmptySource
   void validateTableIdentifier_rejectsNullAndEmpty(String identifier) {
      assertThrows(IllegalArgumentException.class,
         () -> FkIntegrityService.validateTableIdentifier(identifier, "sourceTable"));
   }

   /** A column is exactly one segment: a dot in a column name is a rejection, not a qualification. */
   @Test
   void validateColumnIdentifier_rejectsAQualifiedName() {
      assertThrows(IllegalArgumentException.class,
         () -> FkIntegrityService.validateColumnIdentifier("src.partner_id", "fkColumn"));
   }

   /** A table is at most two segments (schema.table); catalog.schema.table is out of contract. */
   @Test
   void validateTableIdentifier_rejectsMoreThanTwoSegments() {
      assertThrows(IllegalArgumentException.class,
         () -> FkIntegrityService.validateTableIdentifier("odoo.public.sale_order", "sourceTable"));
   }

   /**
    * Splitting must retain empty segments. If it didn't, "sale_order." would split to a single
    * valid segment and slip through with a dangling dot in the generated SQL.
    */
   @ParameterizedTest
   @ValueSource(strings = { "sale_order.", ".sale_order", "public..sale_order", "." })
   void validateTableIdentifier_rejectsEmptySegments(String identifier) {
      assertThrows(IllegalArgumentException.class,
         () -> FkIntegrityService.validateTableIdentifier(identifier, "sourceTable"));
   }

   // ------------------------------------------------------------------
   // Generated SQL
   // ------------------------------------------------------------------

   /**
    * Both halves of "rows an INNER join would drop" must be counted: NULL FKs, and FKs with no
    * matching target row. Counting only one half would under-report and open the gate wrongly.
    */
   @Test
   void buildDroppedRowCountSql_countsBothNullFksAndOrphanedFks() {
      String sql = FkIntegrityService.buildDroppedRowCountSql(
         "public.sale_order", "partner_id", "public.res_partner", "id");

      assertTrue(sql.startsWith("SELECT COUNT(*) FROM public.sale_order"), sql);
      assertTrue(sql.contains("src.partner_id IS NULL"), sql);
      assertTrue(sql.contains("NOT EXISTS"), sql);
      assertTrue(sql.contains("FROM public.res_partner"), sql);
      assertTrue(sql.contains("tgt.id = src.partner_id"), sql);
      // the orphan half is a correlated subquery, not a concatenated literal
      assertFalse(sql.contains("'"), "the statement must contain no string literals: " + sql);
   }

   // ------------------------------------------------------------------
   // countDroppedRows — never degrade into a zero
   // ------------------------------------------------------------------

   @Test
   void countDroppedRows_returnsTheCountFromTheFirstColumn() throws Exception {
      Harness harness = new Harness();
      when(harness.resultSet.next()).thenReturn(true);
      when(harness.resultSet.getLong(1)).thenReturn(17L);

      assertEquals(17L, harness.service.countDroppedRows(request(), harness.principal));

      verify(harness.connection).prepareStatement(FkIntegrityService.buildDroppedRowCountSql(
         "public.sale_order", "partner_id", "public.res_partner", "id"));
      verify(harness.connection).close();
   }

   @Test
   void countDroppedRows_returnsZeroOnlyWhenTheDatabaseActuallySaysZero() throws Exception {
      Harness harness = new Harness();
      when(harness.resultSet.next()).thenReturn(true);
      when(harness.resultSet.getLong(1)).thenReturn(0L);

      assertEquals(0L, harness.service.countDroppedRows(request(), harness.principal));
   }

   /** A SQLException must surface as a failure, never be swallowed into a "safe to join" zero. */
   @Test
   void countDroppedRows_propagatesSqlExceptionsInsteadOfReturningZero() throws Exception {
      Harness harness = new Harness();
      when(harness.statement.executeQuery()).thenThrow(new SQLException("relation does not exist"));

      SQLException ex = assertThrows(SQLException.class,
         () -> harness.service.countDroppedRows(request(), harness.principal));

      assertTrue(ex.getMessage().contains("relation does not exist"));
      verify(harness.connection).close();
   }

   /** An empty result set is a broken query, not "zero dropped rows". */
   @Test
   void countDroppedRows_throwsWhenTheResultSetHasNoRow() throws Exception {
      Harness harness = new Harness();
      when(harness.resultSet.next()).thenReturn(false);

      assertThrows(SQLException.class,
         () -> harness.service.countDroppedRows(request(), harness.principal));
   }

   /** getLong() returns 0 for a SQL NULL — which would read as "safe to join". Refuse it. */
   @Test
   void countDroppedRows_throwsWhenTheCountIsSqlNull() throws Exception {
      Harness harness = new Harness();
      when(harness.resultSet.next()).thenReturn(true);
      when(harness.resultSet.getLong(1)).thenReturn(0L);
      when(harness.resultSet.wasNull()).thenReturn(true);

      assertThrows(SQLException.class,
         () -> harness.service.countDroppedRows(request(), harness.principal));
   }

   /** A rejected identifier must never reach the database — validation precedes connection. */
   @Test
   void countDroppedRows_rejectsBadIdentifiersWithoutOpeningAConnection() throws Exception {
      Harness harness = new Harness();
      FkIntegrityRequest request = request();
      request.setFkColumn("partner_id; DROP TABLE x");

      assertThrows(IllegalArgumentException.class,
         () -> harness.service.countDroppedRows(request, harness.principal));

      assertEquals(0, harness.connectionsOpened);
      verify(harness.connection, never()).prepareStatement(anyString());
   }

   @Test
   void countDroppedRows_requiresADatasourcePath() throws Exception {
      Harness harness = new Harness();
      FkIntegrityRequest request = request();
      request.setDatasourcePath(null);

      assertThrows(IllegalArgumentException.class,
         () -> harness.service.countDroppedRows(request, harness.principal));

      assertEquals(0, harness.connectionsOpened);
   }

   /** No READ permission on the datasource means no query at all. */
   @Test
   void countDroppedRows_deniesAccessWithoutReadPermission() throws Exception {
      Harness harness = new Harness();
      when(harness.dataSourceService.checkPermission(
         eq("odoo"), eq(ResourceAction.READ), any())).thenReturn(false);

      assertThrows(SecurityException.class,
         () -> harness.service.countDroppedRows(request(), harness.principal));

      assertEquals(0, harness.connectionsOpened);
   }

   // ------------------------------------------------------------------
   // helpers
   // ------------------------------------------------------------------

   private static FkIntegrityRequest request() {
      FkIntegrityRequest request = new FkIntegrityRequest();
      request.setDatasourcePath("odoo");
      request.setSourceTable("public.sale_order");
      request.setFkColumn("partner_id");
      request.setTargetTable("public.res_partner");
      request.setTargetKeyColumn("id");
      return request;
   }

   /**
    * Wires a {@link FkIntegrityService} over a mocked JDBC stack by overriding the single
    * connection-acquisition seam, so the whole method — validation, permission, execution and
    * result handling — is exercised without a live database.
    */
   private static final class Harness {
      Harness() throws Exception {
         when(metadataService.getJDBCDatasource("odoo")).thenReturn(dataSource);
         when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class), any()))
            .thenReturn(true);
         when(connection.prepareStatement(anyString())).thenReturn(statement);
         when(statement.executeQuery()).thenReturn(resultSet);

         service = new FkIntegrityService(metadataService, dataSourceService) {
            @Override
            Connection openConnection(JDBCDataSource ds, Principal principal) {
               connectionsOpened++;
               return connection;
            }
         };
      }

      final MetadataApiService metadataService = mock(MetadataApiService.class);
      final DataSourceService dataSourceService = mock(DataSourceService.class);
      final JDBCDataSource dataSource = mock(JDBCDataSource.class);
      final Connection connection = mock(Connection.class);
      final PreparedStatement statement = mock(PreparedStatement.class);
      final ResultSet resultSet = mock(ResultSet.class);
      final Principal principal = mock(Principal.class);
      final FkIntegrityService service;
      int connectionsOpened;
   }
}
