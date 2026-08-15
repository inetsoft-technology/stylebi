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
import inetsoft.web.wiz.model.FkIntegrityResponse;
import inetsoft.web.wiz.request.FkIntegrityRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.Principal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers {@link FkIntegrityService}, which measures whether injecting an INNER join from a fact
 * table to its FK target would change the dashboard's aggregates.
 *
 * <p>Three properties are load-bearing and are what this test class exists to pin down:</p>
 * <ul>
 *   <li><b>Identifier validation is the security boundary.</b> The endpoint deliberately does not
 *       accept SQL — it accepts identifiers and builds the statements server-side. Every
 *       identifier segment must match {@code ^[A-Za-z_][A-Za-z0-9_]*$}. Anything else is rejected
 *       outright, never quoted or escaped into "safety", and never executed.</li>
 *   <li><b>Every interpolated field is wired to that boundary.</b> Pinning the validation rule is
 *       not the same as pinning that a given field goes through it: all four fields funnel into
 *       one shared helper, so mutating that helper proves only that the rule is enforced
 *       somewhere — it cannot detect a field wired straight from the request into the SQL
 *       builder. {@link #checkIntegrity_rejectsBadIdentifiersInEveryFieldWithoutOpeningAConnection}
 *       exists specifically to pin the four call sites individually.</li>
 *   <li><b>A wrong zero is the worst possible failure.</b> Zeros mean "safe to join", which is
 *       what opens the gate. So no failure path may degrade into a count, for either field:
 *       exceptions propagate, and an empty or NULL result row is an error, not a zero.</li>
 * </ul>
 */
@Tag("core")
class FkIntegrityServiceTest {
   /** The exact statements the service must emit for the canonical request. */
   private static final String DROP_SQL =
      "SELECT COUNT(*) FROM public.sale_order src WHERE src.partner_id IS NULL " +
      "OR NOT EXISTS (SELECT 1 FROM public.res_partner tgt WHERE tgt.id = src.partner_id)";
   private static final String DUPLICATE_SQL =
      "SELECT COUNT(*) FROM (SELECT id FROM public.res_partner GROUP BY id HAVING COUNT(*) > 1) d";

   /**
    * Injection attempts the endpoint exists to refuse. Every one of these is invalid as a table
    * name AND as a column name, so the one list drives both validation paths and the per-field
    * wiring test.
    */
   private static final List<String> HOSTILE_IDENTIFIERS = List.of(
      "partner_id; DROP TABLE x",
      "partner_id--",
      "partner_id'",
      "\"partner_id\"",
      "partner id",
      "sale_order) UNION SELECT 1 --",
      "partner_id/*x*/",
      "1partner",
      "");

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

   @ParameterizedTest
   @MethodSource("hostileIdentifiers")
   void validateColumnIdentifier_rejectsAnythingOutsideTheAllowedCharacterSet(String identifier) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> FkIntegrityService.validateColumnIdentifier(identifier, "fkColumn"));

      assertTrue(ex.getMessage().contains("fkColumn"),
         "the error must name the offending field so the caller can fix its request");
   }

   /**
    * The table path interpolates just as directly as the column path, so it gets the same hostile
    * payloads. Its wider contract (two segments are legal) must not widen the character set.
    */
   @ParameterizedTest
   @MethodSource("hostileIdentifiers")
   void validateTableIdentifier_rejectsAnythingOutsideTheAllowedCharacterSet(String identifier) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> FkIntegrityService.validateTableIdentifier(identifier, "sourceTable"));

      assertTrue(ex.getMessage().contains("sourceTable"));
   }

   /** Qualifying a hostile payload must not launder it through the other segment. */
   @ParameterizedTest
   @MethodSource("hostileIdentifiers")
   void validateTableIdentifier_rejectsHostilePayloadsInEitherSegment(String identifier) {
      assertThrows(IllegalArgumentException.class,
         () -> FkIntegrityService.validateTableIdentifier("public." + identifier, "targetTable"));
      assertThrows(IllegalArgumentException.class,
         () -> FkIntegrityService.validateTableIdentifier(identifier + ".sale_order", "targetTable"));
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

      assertEquals(DROP_SQL, sql);
      assertTrue(sql.contains("src.partner_id IS NULL"), sql);
      assertTrue(sql.contains("NOT EXISTS"), sql);
      assertTrue(sql.contains("tgt.id = src.partner_id"), sql);
      // the orphan half is a correlated subquery, not a concatenated literal
      assertFalse(sql.contains("'"), "the statement must contain no string literals: " + sql);
   }

   /**
    * The uniqueness half of the question. A target key appearing more than once fans source rows
    * out, inflating every aggregate — while dropping exactly zero rows, so the drop count cannot
    * detect it.
    */
   @Test
   void buildDuplicateTargetKeyCountSql_countsTargetKeyValuesOccurringMoreThanOnce() {
      String sql = FkIntegrityService.buildDuplicateTargetKeyCountSql("public.res_partner", "id");

      assertEquals(DUPLICATE_SQL, sql);
      assertTrue(sql.contains("GROUP BY id"), sql);
      assertTrue(sql.contains("HAVING COUNT(*) > 1"), sql);
      assertFalse(sql.contains("'"), "the statement must contain no string literals: " + sql);
   }

   // ------------------------------------------------------------------
   // THE CAST FIX — a text FK pointing at a numeric target key
   // ------------------------------------------------------------------
   //
   // Reproduced live on orangehrm: hs_hr_employee.sal_grd_code is a VARCHAR, genuinely FK to
   // ohrm_pay_grade.id (an integer PK). The bare `tgt.id = src.sal_grd_code` comparison this
   // class always used to emit fails outright on Postgres ("operator does not exist: integer =
   // character varying"), which propagates as a SQLException and is reported upstream as
   // UNESTABLISHED -- so the FK-label feature silently never offered a pay-grade filter, with no
   // error anywhere a caller could see. These tests pin resolveCastSide's classification and the
   // SQL it produces; the end-to-end checkIntegrity test pins that the cast actually reaches the
   // statement that gets executed.

   @Test
   void resolveCastSide_castsTheNumericTargetWhenFkIsText() throws Exception {
      Connection conn = columnTypedConnection("sal_grd_code", Types.VARCHAR, "id", Types.INTEGER);

      assertEquals(FkIntegrityService.CastSide.TARGET_KEY,
         FkIntegrityService.resolveCastSide(conn, "public.hs_hr_employee", "sal_grd_code",
            "public.ohrm_pay_grade", "id"));
   }

   @Test
   void resolveCastSide_castsTheNumericFkWhenTargetIsText() throws Exception {
      Connection conn = columnTypedConnection("partner_id", Types.INTEGER, "code", Types.VARCHAR);

      assertEquals(FkIntegrityService.CastSide.FK,
         FkIntegrityService.resolveCastSide(conn, "public.sale_order", "partner_id",
            "public.res_partner", "code"));
   }

   @Test
   void resolveCastSide_isNoneWhenBothSidesAgree() throws Exception {
      Connection conn = columnTypedConnection("partner_id", Types.INTEGER, "id", Types.INTEGER);

      assertEquals(FkIntegrityService.CastSide.NONE,
         FkIntegrityService.resolveCastSide(conn, "public.sale_order", "partner_id",
            "public.res_partner", "id"));
   }

   @Test
   void resolveCastSide_isNoneForDifferentNumericFamiliesInTheSameCategory() throws Exception {
      // INTEGER vs BIGINT: different java.sql.Types codes, but every dialect this feature runs
      // against already compares them directly -- casting would be pure churn.
      Connection conn = columnTypedConnection("partner_id", Types.INTEGER, "id", Types.BIGINT);

      assertEquals(FkIntegrityService.CastSide.NONE,
         FkIntegrityService.resolveCastSide(conn, "public.sale_order", "partner_id",
            "public.res_partner", "id"));
   }

   @Test
   void resolveCastSide_isNoneWhenColumnMetadataCannotBeEstablished() throws Exception {
      // getMetaData() answers null -- an un-mocked/minimal connection, exactly what every OTHER
      // test in this file already uses. Must degrade to NONE, never throw and never guess.
      Connection conn = mock(Connection.class);

      assertEquals(FkIntegrityService.CastSide.NONE,
         FkIntegrityService.resolveCastSide(conn, "public.sale_order", "partner_id",
            "public.res_partner", "id"));
   }

   @Test
   void resolveCastSide_isNoneWhenGetColumnsThrows() throws Exception {
      Connection conn = mock(Connection.class);
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getColumns(any(), any(), any(), any()))
         .thenThrow(new SQLException("driver does not support getColumns"));

      assertEquals(FkIntegrityService.CastSide.NONE,
         FkIntegrityService.resolveCastSide(conn, "public.sale_order", "partner_id",
            "public.res_partner", "id"));
   }

   @Test
   void buildDroppedRowCountSql_castsTheTargetKeyToTextWhenDirected() {
      String sql = FkIntegrityService.buildDroppedRowCountSql(
         "public.hs_hr_employee", "sal_grd_code", "public.ohrm_pay_grade", "id",
         FkIntegrityService.CastSide.TARGET_KEY, FkIntegrityService.Dialect.DEFAULT);

      assertTrue(sql.contains("CAST(tgt.id AS VARCHAR) = src.sal_grd_code"), sql);
      // The NULL check must stay on the RAW column -- NULL-ness does not depend on type agreement.
      assertTrue(sql.contains("src.sal_grd_code IS NULL"), sql);
   }

   @Test
   void buildDroppedRowCountSql_castsTheFkToTextWhenDirected() {
      String sql = FkIntegrityService.buildDroppedRowCountSql(
         "public.sale_order", "partner_id", "public.res_partner", "code",
         FkIntegrityService.CastSide.FK, FkIntegrityService.Dialect.DEFAULT);

      assertTrue(sql.contains("tgt.code = CAST(src.partner_id AS VARCHAR)"), sql);
   }

   @Test
   void buildDroppedRowCountSql_usesCharNotVarcharOnMySql() {
      // MySQL's CAST rejects VARCHAR as a target type outright; it wants CHAR.
      String sql = FkIntegrityService.buildDroppedRowCountSql(
         "public.hs_hr_employee", "sal_grd_code", "public.ohrm_pay_grade", "id",
         FkIntegrityService.CastSide.TARGET_KEY, FkIntegrityService.Dialect.MYSQL);

      assertTrue(sql.contains("CAST(tgt.id AS CHAR)"), sql);
      assertFalse(sql.contains("VARCHAR"), sql);
   }

   /**
    * Oracle's CAST requires an explicit length for a character target (VARCHAR2(n)) -- a bare
    * "CAST(... AS VARCHAR)" is a SYNTAX ERROR on Oracle, not merely the wrong keyword. TO_CHAR
    * needs no length and is Oracle's own numeric-to-text conversion, which is always what this
    * cast means: resolveCastSide only ever directs the NUMERIC side to be cast.
    */
   @Test
   void buildDroppedRowCountSql_usesToCharOnOracle() {
      String sql = FkIntegrityService.buildDroppedRowCountSql(
         "public.hs_hr_employee", "sal_grd_code", "public.ohrm_pay_grade", "id",
         FkIntegrityService.CastSide.TARGET_KEY, FkIntegrityService.Dialect.ORACLE);

      assertTrue(sql.contains("TO_CHAR(tgt.id) = src.sal_grd_code"), sql);
      assertFalse(sql.contains("CAST"), sql);
      assertFalse(sql.contains("VARCHAR"), sql);
   }

   @Test
   void buildDroppedRowCountSql_fourArgOverloadAppliesNoCast() {
      // The pre-existing 4-arg overload must stay byte-identical to before this fix -- every
      // caller that never resolves a cast side (or can't) gets exactly the old SQL.
      assertEquals(DROP_SQL, FkIntegrityService.buildDroppedRowCountSql(
         "public.sale_order", "partner_id", "public.res_partner", "id"));
   }

   /** End-to-end: the cast actually reaches the statement checkIntegrity executes. */
   @Test
   void checkIntegrity_castsTheTargetKeyWhenTheFkIsTextAndTheTargetIsNumeric() throws Exception {
      Harness harness = new Harness();
      harness.request.setSourceTable("public.hs_hr_employee");
      harness.request.setFkColumn("sal_grd_code");
      harness.request.setTargetTable("public.ohrm_pay_grade");
      harness.request.setTargetKeyColumn("id");
      harness.stubColumnTypes(Types.VARCHAR, Types.INTEGER);

      String expectedDrop =
         "SELECT COUNT(*) FROM public.hs_hr_employee src WHERE src.sal_grd_code IS NULL " +
         "OR NOT EXISTS (SELECT 1 FROM public.ohrm_pay_grade tgt " +
         "WHERE CAST(tgt.id AS VARCHAR) = src.sal_grd_code)";
      String expectedDuplicate =
         "SELECT COUNT(*) FROM (SELECT id FROM public.ohrm_pay_grade GROUP BY id " +
         "HAVING COUNT(*) > 1) d";
      doReturn(harness.dropStatement).when(harness.connection).prepareStatement(expectedDrop);
      doReturn(harness.duplicateStatement).when(harness.connection)
         .prepareStatement(expectedDuplicate);

      FkIntegrityResponse response =
         harness.service.checkIntegrity(harness.request, harness.principal);

      assertEquals(0L, response.droppedRowCount());
      verify(harness.connection).prepareStatement(expectedDrop);
   }

   /** Same shape, but on a MySQL connection -- CHAR, not VARCHAR. */
   @Test
   void checkIntegrity_usesCharOnMySqlWhenCasting() throws Exception {
      Harness harness = new Harness();
      harness.request.setSourceTable("public.hs_hr_employee");
      harness.request.setFkColumn("sal_grd_code");
      harness.request.setTargetTable("public.ohrm_pay_grade");
      harness.request.setTargetKeyColumn("id");
      harness.stubColumnTypes(Types.VARCHAR, Types.INTEGER);
      when(harness.databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");

      String expectedDrop =
         "SELECT COUNT(*) FROM public.hs_hr_employee src WHERE src.sal_grd_code IS NULL " +
         "OR NOT EXISTS (SELECT 1 FROM public.ohrm_pay_grade tgt " +
         "WHERE CAST(tgt.id AS CHAR) = src.sal_grd_code)";
      String expectedDuplicate =
         "SELECT COUNT(*) FROM (SELECT id FROM public.ohrm_pay_grade GROUP BY id " +
         "HAVING COUNT(*) > 1) d";
      doReturn(harness.dropStatement).when(harness.connection).prepareStatement(expectedDrop);
      doReturn(harness.duplicateStatement).when(harness.connection)
         .prepareStatement(expectedDuplicate);

      harness.service.checkIntegrity(harness.request, harness.principal);

      verify(harness.connection).prepareStatement(expectedDrop);
   }

   /** Same shape, but on an Oracle connection -- TO_CHAR, not CAST ... VARCHAR (a syntax error there). */
   @Test
   void checkIntegrity_usesToCharOnOracleWhenCasting() throws Exception {
      Harness harness = new Harness();
      harness.request.setSourceTable("public.hs_hr_employee");
      harness.request.setFkColumn("sal_grd_code");
      harness.request.setTargetTable("public.ohrm_pay_grade");
      harness.request.setTargetKeyColumn("id");
      harness.stubColumnTypes(Types.VARCHAR, Types.INTEGER);
      when(harness.databaseMetaData.getDatabaseProductName()).thenReturn("Oracle");

      String expectedDrop =
         "SELECT COUNT(*) FROM public.hs_hr_employee src WHERE src.sal_grd_code IS NULL " +
         "OR NOT EXISTS (SELECT 1 FROM public.ohrm_pay_grade tgt " +
         "WHERE TO_CHAR(tgt.id) = src.sal_grd_code)";
      String expectedDuplicate =
         "SELECT COUNT(*) FROM (SELECT id FROM public.ohrm_pay_grade GROUP BY id " +
         "HAVING COUNT(*) > 1) d";
      doReturn(harness.dropStatement).when(harness.connection).prepareStatement(expectedDrop);
      doReturn(harness.duplicateStatement).when(harness.connection)
         .prepareStatement(expectedDuplicate);

      harness.service.checkIntegrity(harness.request, harness.principal);

      verify(harness.connection).prepareStatement(expectedDrop);
   }

   /**
    * Wires a mocked {@code DatabaseMetaData.getColumns(...)} call so {@code resolveCastSide} can
    * classify both columns without a live database -- routed EXPLICITLY by each column's own
    * name (never by position or by exclusion), so a test naming two columns that happen to share
    * a name with another test's fixture can never cross-contaminate.
    */
   private static Connection columnTypedConnection(
      String fkColumnName, int fkJdbcType, String targetColumnName, int targetJdbcType)
      throws SQLException
   {
      Connection conn = mock(Connection.class);
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      when(conn.getMetaData()).thenReturn(meta);

      ResultSet fkRs = mock(ResultSet.class);
      when(fkRs.next()).thenReturn(true, false);
      when(fkRs.getInt("DATA_TYPE")).thenReturn(fkJdbcType);
      when(meta.getColumns(any(), any(), any(), eq(fkColumnName))).thenReturn(fkRs);

      ResultSet targetRs = mock(ResultSet.class);
      when(targetRs.next()).thenReturn(true, false);
      when(targetRs.getInt("DATA_TYPE")).thenReturn(targetJdbcType);
      when(meta.getColumns(any(), any(), any(), eq(targetColumnName))).thenReturn(targetRs);

      return conn;
   }

   // ------------------------------------------------------------------
   // checkIntegrity — both measurements, never a wrong zero
   // ------------------------------------------------------------------

   @Test
   void checkIntegrity_returnsBothCountsFromTheFirstColumnOfEachQuery() throws Exception {
      Harness harness = new Harness();
      harness.dropCount(17L);
      harness.duplicateCount(3L);

      FkIntegrityResponse response = harness.service.checkIntegrity(request(), harness.principal);

      assertEquals(17L, response.droppedRowCount());
      assertEquals(3L, response.duplicateTargetKeyCount());
   }

   /** Both statements must run, on the one connection, which must then be closed. */
   @Test
   void checkIntegrity_runsBothMeasurementsOnASingleConnection() throws Exception {
      Harness harness = new Harness();

      harness.service.checkIntegrity(request(), harness.principal);

      assertEquals(1, harness.connectionsOpened);
      verify(harness.connection).prepareStatement(DROP_SQL);
      verify(harness.connection).prepareStatement(DUPLICATE_SQL);
      verify(harness.connection).close();
   }

   @Test
   void checkIntegrity_returnsZeroesOnlyWhenTheDatabaseActuallySaysZero() throws Exception {
      Harness harness = new Harness();
      harness.dropCount(0L);
      harness.duplicateCount(0L);

      FkIntegrityResponse response = harness.service.checkIntegrity(request(), harness.principal);

      assertEquals(0L, response.droppedRowCount());
      assertEquals(0L, response.duplicateTargetKeyCount());
   }

   /**
    * The case a drop count alone cannot see: nothing would be dropped, but the target key is not
    * unique, so the join would fan rows out and inflate every aggregate. The response must carry
    * that, otherwise a caller gating on "droppedRowCount == 0" would wrongly proceed.
    */
   @Test
   void checkIntegrity_reportsDuplicateKeysEvenWhenNothingWouldBeDropped() throws Exception {
      Harness harness = new Harness();
      harness.dropCount(0L);
      harness.duplicateCount(5L);

      FkIntegrityResponse response = harness.service.checkIntegrity(request(), harness.principal);

      assertEquals(0L, response.droppedRowCount());
      assertEquals(5L, response.duplicateTargetKeyCount(),
         "a non-unique target key must be reported even though no row would be dropped");
   }

   /** A SQLException must surface as a failure, never be swallowed into a "safe to join" zero. */
   @Test
   void checkIntegrity_propagatesSqlExceptionsFromTheDropQueryInsteadOfReturningZero()
      throws Exception
   {
      Harness harness = new Harness();
      when(harness.dropStatement.executeQuery())
         .thenThrow(new SQLException("relation does not exist"));

      SQLException ex = assertThrows(SQLException.class,
         () -> harness.service.checkIntegrity(request(), harness.principal));

      assertTrue(ex.getMessage().contains("relation does not exist"));
      verify(harness.connection).close();
   }

   /**
    * A failure of the second measurement must fail the whole request. Reporting the drop count
    * alongside a defaulted duplicate count of 0 would be exactly the wrong-zero failure.
    */
   @Test
   void checkIntegrity_propagatesSqlExceptionsFromTheDuplicateQueryInsteadOfReturningZero()
      throws Exception
   {
      Harness harness = new Harness();
      harness.dropCount(0L);
      when(harness.duplicateStatement.executeQuery())
         .thenThrow(new SQLException("permission denied for table res_partner"));

      SQLException ex = assertThrows(SQLException.class,
         () -> harness.service.checkIntegrity(request(), harness.principal));

      assertTrue(ex.getMessage().contains("permission denied"));
      verify(harness.connection).close();
   }

   /** An empty result set is a broken query, not "zero dropped rows". */
   @Test
   void checkIntegrity_throwsWhenTheDropResultSetHasNoRow() throws Exception {
      Harness harness = new Harness();
      when(harness.dropResultSet.next()).thenReturn(false);

      assertThrows(SQLException.class,
         () -> harness.service.checkIntegrity(request(), harness.principal));
   }

   @Test
   void checkIntegrity_throwsWhenTheDuplicateResultSetHasNoRow() throws Exception {
      Harness harness = new Harness();
      when(harness.duplicateResultSet.next()).thenReturn(false);

      assertThrows(SQLException.class,
         () -> harness.service.checkIntegrity(request(), harness.principal));
   }

   /** getLong() returns 0 for a SQL NULL — which would read as "safe to join". Refuse it. */
   @Test
   void checkIntegrity_throwsWhenTheDropCountIsSqlNull() throws Exception {
      Harness harness = new Harness();
      when(harness.dropResultSet.wasNull()).thenReturn(true);

      assertThrows(SQLException.class,
         () -> harness.service.checkIntegrity(request(), harness.principal));
   }

   @Test
   void checkIntegrity_throwsWhenTheDuplicateCountIsSqlNull() throws Exception {
      Harness harness = new Harness();
      when(harness.duplicateResultSet.wasNull()).thenReturn(true);

      assertThrows(SQLException.class,
         () -> harness.service.checkIntegrity(request(), harness.principal));
   }

   // ------------------------------------------------------------------
   // Wiring — each interpolated field is individually pinned to the validator
   // ------------------------------------------------------------------

   /**
    * Every identifier that reaches the generated SQL must be validated, and that must be pinned
    * per field rather than per rule. All four fields funnel into one shared helper, so mutating
    * the helper proves only that the rule is enforced somewhere — it cannot detect a field wired
    * straight from the request into the SQL builder. This test covers each call site
    * individually: deleting the validation call for any single field turns it red.
    */
   @ParameterizedTest(name = "{0} = \"{1}\"")
   @MethodSource("hostileFieldAssignments")
   void checkIntegrity_rejectsBadIdentifiersInEveryFieldWithoutOpeningAConnection(
      String field, String hostileValue, BiConsumer<FkIntegrityRequest, String> setter)
      throws Exception
   {
      Harness harness = new Harness();
      FkIntegrityRequest request = request();
      setter.accept(request, hostileValue);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> harness.service.checkIntegrity(request, harness.principal),
         field + " must be validated before the SQL is built");

      assertTrue(ex.getMessage().contains(field),
         "the rejection must name " + field + ", not some other field: " + ex.getMessage());
      assertEquals(0, harness.connectionsOpened,
         "a rejected " + field + " must never reach a connection");
      verify(harness.connection, never()).prepareStatement(anyString());
   }

   /** The same wiring guarantee for a null in any of the four fields. */
   @ParameterizedTest(name = "{0} = null")
   @MethodSource("nullFieldAssignments")
   void checkIntegrity_rejectsNullIdentifiersInEveryFieldWithoutOpeningAConnection(
      String field, BiConsumer<FkIntegrityRequest, String> setter) throws Exception
   {
      Harness harness = new Harness();
      FkIntegrityRequest request = request();
      setter.accept(request, null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> harness.service.checkIntegrity(request, harness.principal));

      assertTrue(ex.getMessage().contains(field), ex.getMessage());
      assertEquals(0, harness.connectionsOpened);
   }

   @Test
   void checkIntegrity_requiresADatasourcePath() throws Exception {
      Harness harness = new Harness();
      FkIntegrityRequest request = request();
      request.setDatasourcePath(null);

      assertThrows(IllegalArgumentException.class,
         () -> harness.service.checkIntegrity(request, harness.principal));

      assertEquals(0, harness.connectionsOpened);
   }

   /** No READ permission on the datasource means no query at all. */
   @Test
   void checkIntegrity_deniesAccessWithoutReadPermission() throws Exception {
      Harness harness = new Harness();
      when(harness.dataSourceService.checkPermission(
         eq("odoo"), eq(ResourceAction.READ), any())).thenReturn(false);

      assertThrows(SecurityException.class,
         () -> harness.service.checkIntegrity(request(), harness.principal));

      assertEquals(0, harness.connectionsOpened);
   }

   // ------------------------------------------------------------------
   // helpers
   // ------------------------------------------------------------------

   private static Stream<String> hostileIdentifiers() {
      return HOSTILE_IDENTIFIERS.stream();
   }

   /** The four SQL-interpolated fields, crossed with the hostile payloads. */
   private static Stream<Arguments> hostileFieldAssignments() {
      return identifierSetters().entrySet().stream()
         .flatMap(setter -> HOSTILE_IDENTIFIERS.stream()
            .map(payload -> Arguments.of(setter.getKey(), payload, setter.getValue())));
   }

   private static Stream<Arguments> nullFieldAssignments() {
      return identifierSetters().entrySet().stream()
         .map(setter -> Arguments.of(setter.getKey(), setter.getValue()));
   }

   /**
    * Every request field that ends up interpolated into a generated statement. If a field is ever
    * added to the SQL, it belongs here too.
    */
   private static Map<String, BiConsumer<FkIntegrityRequest, String>> identifierSetters() {
      Map<String, BiConsumer<FkIntegrityRequest, String>> setters = new LinkedHashMap<>();
      setters.put("sourceTable", FkIntegrityRequest::setSourceTable);
      setters.put("fkColumn", FkIntegrityRequest::setFkColumn);
      setters.put("targetTable", FkIntegrityRequest::setTargetTable);
      setters.put("targetKeyColumn", FkIntegrityRequest::setTargetKeyColumn);
      return setters;
   }

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
    * connection-acquisition seam, so the whole method — validation, permission, both queries and
    * result handling — is exercised without a live database. Statements are routed by SQL text so
    * the two measurements can be failed independently.
    */
   private static final class Harness {
      Harness() throws Exception {
         when(metadataService.getJDBCDatasource("odoo")).thenReturn(dataSource);
         when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class), any()))
            .thenReturn(true);
         // Anything other than the two expected statements is a leak — most likely an identifier
         // that reached the SQL without being validated. Fail loudly and quote the statement,
         // rather than letting an unstubbed mock return null and surface as a bare NPE.
         // do*/when form throughout: when(mock.call()) would invoke the catch-all answer while
         // recording the two specific stubs below, and blow up during setup.
         doAnswer(invocation -> {
            throw new AssertionError(
               "unexpected SQL reached the database: " + invocation.getArgument(0));
         }).when(connection).prepareStatement(anyString());
         doReturn(dropStatement).when(connection).prepareStatement(DROP_SQL);
         doReturn(duplicateStatement).when(connection).prepareStatement(DUPLICATE_SQL);
         when(dropStatement.executeQuery()).thenReturn(dropResultSet);
         when(duplicateStatement.executeQuery()).thenReturn(duplicateResultSet);
         // Both measurements succeed with 0 by default; each test overrides only what it means to
         // exercise, so an unrelated failure can't masquerade as the behaviour under test.
         dropCount(0L);
         duplicateCount(0L);

         service = new FkIntegrityService(metadataService, dataSourceService) {
            @Override
            Connection openConnection(JDBCDataSource ds, Principal principal) {
               connectionsOpened++;
               return connection;
            }
         };
      }

      void dropCount(long count) throws SQLException {
         when(dropResultSet.next()).thenReturn(true);
         when(dropResultSet.getLong(1)).thenReturn(count);
      }

      void duplicateCount(long count) throws SQLException {
         when(duplicateResultSet.next()).thenReturn(true);
         when(duplicateResultSet.getLong(1)).thenReturn(count);
      }

      /**
       * Wires {@code connection.getMetaData()} so {@code resolveCastSide} can classify
       * {@code request}'s fkColumn/targetKeyColumn without a live database. Call AFTER setting
       * {@code request}'s column names -- the routing is by column name, matching how
       * {@code columnJdbcType} actually calls {@code getColumns}.
       */
      void stubColumnTypes(int fkJdbcType, int targetJdbcType) throws SQLException {
         when(connection.getMetaData()).thenReturn(databaseMetaData);
         // Default product name so resolveDialect degrades to DEFAULT unless a test overrides it.
         when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");

         ResultSet fkRs = mock(ResultSet.class);
         when(fkRs.next()).thenReturn(true, false);
         when(fkRs.getInt("DATA_TYPE")).thenReturn(fkJdbcType);
         ResultSet targetRs = mock(ResultSet.class);
         when(targetRs.next()).thenReturn(true, false);
         when(targetRs.getInt("DATA_TYPE")).thenReturn(targetJdbcType);

         when(databaseMetaData.getColumns(any(), any(), any(), eq(request.getFkColumn())))
            .thenReturn(fkRs);
         when(databaseMetaData.getColumns(any(), any(), any(), eq(request.getTargetKeyColumn())))
            .thenReturn(targetRs);
      }

      final MetadataApiService metadataService = mock(MetadataApiService.class);
      final DataSourceService dataSourceService = mock(DataSourceService.class);
      final JDBCDataSource dataSource = mock(JDBCDataSource.class);
      final Connection connection = mock(Connection.class);
      final DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
      final PreparedStatement dropStatement = mock(PreparedStatement.class);
      final PreparedStatement duplicateStatement = mock(PreparedStatement.class);
      final ResultSet dropResultSet = mock(ResultSet.class);
      final ResultSet duplicateResultSet = mock(ResultSet.class);
      final Principal principal = mock(Principal.class);
      final FkIntegrityRequest request = request();
      final FkIntegrityService service;
      int connectionsOpened;
   }
}
