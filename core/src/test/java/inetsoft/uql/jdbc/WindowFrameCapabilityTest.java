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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SQLHelper#supportsWindowFrame(String, boolean, boolean)} and
 * {@link SQLHelper#formatWindowFrameInterval(int, String)}, and the PostgreSQL override.
 *
 * NOTE: as with {@link WindowFunctionCapabilityTest}, a bare helper has no UniformSQL wired to
 * a JDBCDataSource, so uniformSql.getDataSource() is null and any version gate falls back to
 * its permissive default.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WindowFrameCapabilityTest {

   // ── Base SQLHelper: default-deny ────────────────────────────────────────

   @Test
   void base_defaultDeny() {
      SQLHelper h = new SQLHelper();   // generic base; WINDOW_FUNCTION defaults true
      // ROWS + RANGE-peer allowed; RANGE value/date + GROUPS denied
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));   // peer
      assertFalse(h.supportsWindowFrame("RANGE", true, false));   // numeric value offset
      assertFalse(h.supportsWindowFrame("RANGE", true, true));    // date INTERVAL
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void base_interval_ansi() {
      assertEquals("INTERVAL '7 day'", new SQLHelper().formatWindowFrameInterval(7, "day"));
   }

   // ── quarter/week normalization: neither is a valid ANSI interval field on every dialect
   // (quarter fails on base/PG, Oracle, Databricks/Spark, Vertica; week fails on Oracle), so
   // both are normalized to an exact equivalent (quarter -> 3*offset month, week -> 7*offset
   // day) BEFORE rendering, universally across dialects. day/month/etc. offsets are byte-
   // identical to pre-normalization behavior (pass through unchanged).

   @Test
   void base_interval_quarterAndWeek_normalizedToMonthAndDay() {
      SQLHelper h = new SQLHelper();
      assertEquals("INTERVAL '21 month'", h.formatWindowFrameInterval(7, "quarter"));
      assertEquals("INTERVAL '14 day'", h.formatWindowFrameInterval(2, "week"));
      assertEquals("INTERVAL '7 day'", h.formatWindowFrameInterval(7, "day"));   // unchanged
   }

   // ── PostgreSQL: opts into everything (byte-parity anchor) ───────────────

   @Test
   void postgres_full() {
      PostgreSQLHelper h = new PostgreSQLHelper();
      assertTrue(h.supportsWindowFrame("RANGE", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", true, true));
      assertTrue(h.supportsWindowFrame("GROUPS", false, false));   // PG >= 11 (default permissive when version unknown)
      assertEquals("INTERVAL '7 day'", h.formatWindowFrameInterval(7, "day"));   // inherits base literal
      // PG rejects INTERVAL '<n> quarter'; normalized to month (inherits base normalization).
      assertEquals("INTERVAL '21 month'", h.formatWindowFrameInterval(7, "quarter"));
      assertEquals("INTERVAL '14 day'", h.formatWindowFrameInterval(2, "week"));
   }

   // ── Strong tier: opt-in RANGE value/date pushdown, GROUPS still denied ──

   @Test
   void oracle_rangeValueAndDate_noGroups() {
      OracleSQLHelper h = new OracleSQLHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));   // peer
      assertTrue(h.supportsWindowFrame("RANGE", true, false));    // numeric value
      assertTrue(h.supportsWindowFrame("RANGE", true, true));     // date INTERVAL
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
      assertEquals("INTERVAL '7' DAY", h.formatWindowFrameInterval(7, "day"));
      // Oracle's INTERVAL literal only accepts YEAR/MONTH/DAY/HOUR/MINUTE/SECOND; quarter and
      // week are normalized to month/day before rendering.
      assertEquals("INTERVAL '21' MONTH", h.formatWindowFrameInterval(7, "quarter"));
      assertEquals("INTERVAL '14' DAY", h.formatWindowFrameInterval(2, "week"));
   }

   @Test
   void mysql_rangeValueAndDate_noGroups() {
      MySQLHelper h = new MySQLHelper();
      // no UniformSQL wired -> getDataSource() is null -> WINDOW_FUNCTION version gate is
      // permissive (see MySQLHelper#supportsOperation), same as WindowFunctionCapabilityTest.
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertTrue(h.supportsWindowFrame("RANGE", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
      assertEquals("INTERVAL 7 DAY", h.formatWindowFrameInterval(7, "day"));
      // MySQL happens to support "quarter" natively, but normalizing to month is still correct
      // (exact equivalent) and keeps the rendering path uniform across dialects.
      assertEquals("INTERVAL 21 MONTH", h.formatWindowFrameInterval(7, "quarter"));
      assertEquals("INTERVAL 14 DAY", h.formatWindowFrameInterval(2, "week"));
   }

   @Test
   void db2_rangeNumericOnly_dateDenied_noGroups() {
      DB2SQLHelper h = new DB2SQLHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));    // peer
      assertTrue(h.supportsWindowFrame("RANGE", true, false));     // numeric value offset (ANSI, well-established)
      assertFalse(h.supportsWindowFrame("RANGE", true, true));     // date offset: unverified syntax, deny (fail-safe)
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void databricks_rangeValueAndDate_noGroups() {
      DatabricksHelper h = new DatabricksHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertTrue(h.supportsWindowFrame("RANGE", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
      assertEquals("INTERVAL 7 DAYS", h.formatWindowFrameInterval(7, "day"));
      // Spark's INTERVAL literal doesn't accept "quarter"; normalized to month before rendering.
      assertEquals("INTERVAL 21 MONTHS", h.formatWindowFrameInterval(7, "quarter"));
   }

   @Test
   void vertica_rangeValueAndDate_noGroups() {
      VerticaHelper h = new VerticaHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertTrue(h.supportsWindowFrame("RANGE", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
      assertEquals("INTERVAL '7 days'", h.formatWindowFrameInterval(7, "day"));
      // Vertica's INTERVAL literal doesn't accept "quarter"; normalized to month before rendering.
      assertEquals("INTERVAL '21 months'", h.formatWindowFrameInterval(7, "quarter"));
   }

   @Test
   void bigquery_numericRangeOnly_noInterval() {
      GoogleBigQueryHelper h = new GoogleBigQueryHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertTrue(h.supportsWindowFrame("RANGE", true, false));    // numeric
      assertFalse(h.supportsWindowFrame("RANGE", true, true));    // no date INTERVAL frame
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   // ── Conservative tier: ROWS + RANGE-peer only (base default, unmodified) ────
   // These dialects have WINDOW_FUNCTION == true (no supportsOperation override found for any
   // of them) and no supportsWindowFrame override, so they land on the base matrix row exactly:
   // ROWS true, RANGE-peer true, RANGE-value/date false, GROUPS false. No code change; these
   // tests confirm + lock the contract.

   @Test
   void sqlServer_rowsAndRangePeer_denyValueDateAndGroups() {
      SQLServerHelper h = new SQLServerHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));    // peer
      assertFalse(h.supportsWindowFrame("RANGE", true, false));    // no value-offset RANGE
      assertFalse(h.supportsWindowFrame("RANGE", true, true));     // no date INTERVAL frame
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));  // no GROUPS
   }

   @Test
   void snowflake_rowsAndRangePeer_denyValueDateAndGroups() {
      SnowflakeHelper h = new SnowflakeHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));    // peer
      assertFalse(h.supportsWindowFrame("RANGE", true, false));    // Snowflake RANGE is peer-only
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void clickhouse_rowsAndRangePeer_denyValueDateAndGroups() {
      ClickhouseHelper h = new ClickhouseHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void hive_rowsAndRangePeer_denyValueDateAndGroups() {
      HiveHelper h = new HiveHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void impala_rowsAndRangePeer_denyValueDateAndGroups() {
      ImpalaHelper h = new ImpalaHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void exasol_rowsAndRangePeer_denyValueDateAndGroups() {
      ExasolHelper h = new ExasolHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   // ── No window functions at all: base denies everything ──────────────────────

   @Test
   void access_noWindowFunctions_denyEverything() {
      AccessSQLHelper h = new AccessSQLHelper();
      // Access has no window functions; AccessSQLHelper.supportsOperation(WINDOW_FUNCTION)
      // already returns false, so the base capability gate denies ROWS/RANGE-peer too.
      assertFalse(h.supportsWindowFrame("ROWS", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void mongo_noWindowFunctions_denyEverything() {
      MongoHelper h = new MongoHelper();
      // MongoHelper already gates WINDOW_FUNCTION off (supportsOperation(op, info) override).
      assertFalse(h.supportsWindowFrame("ROWS", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void derby_noWindowFunctions_denyEverything() {
      // Note: unlike the rest of the "long tail" below, DerbyHelper unconditionally denies
      // WINDOW_FUNCTION (not version-gated), so it lands in the no-window-function tier
      // alongside Access/Mongo -- base denies ROWS/RANGE-peer too, not just RANGE-value/GROUPS.
      DerbyHelper h = new DerbyHelper();
      assertFalse(h.supportsWindowFrame("ROWS", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   // ── Long tail: inherit base (ROWS/RANGE-peer true, RANGE-value/date/GROUPS false) ───────
   // Verified via source: none of these override supportsOperation(WINDOW_FUNCTION) or
   // supportsWindowFrame, so WINDOW_FUNCTION defaults true and the base matrix row applies in
   // full (including ROWS/RANGE-peer, safe to assert here since it was independently verified,
   // not merely assumed).

   @Test
   void h2_inheritsBase_rowsAndRangePeerTrue_denyValueDateAndGroups() {
      H2Helper h = new H2Helper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void sybase_inheritsBase_rowsAndRangePeerTrue_denyValueDateAndGroups() {
      SybaseHelper h = new SybaseHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void denodo_inheritsBase_rowsAndRangePeerTrue_denyValueDateAndGroups() {
      DenodoSQLHelper h = new DenodoSQLHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void dremio_inheritsBase_rowsAndRangePeerTrue_denyValueDateAndGroups() {
      DremioHelper h = new DremioHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void ingres_inheritsBase_rowsAndRangePeerTrue_denyValueDateAndGroups() {
      IngresHelper h = new IngresHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   @Test
   void luciddb_inheritsBase_rowsAndRangePeerTrue_denyValueDateAndGroups() {
      LucidDbHelper h = new LucidDbHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, false));
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   // ── Informix: genuine divergence found + fixed ───────────────────────────────
   // InformixSQLHelper extends DB2SQLHelper for unrelated SQL-generation quirks (MAXROWS
   // suppression, alias-length limiting, keyword list) -- NOT because Informix and DB2 (LUW)
   // share verified window-frame syntax. Without its own override, Informix would silently
   // inherit DB2SQLHelper's Task-4 RANGE-value opt-in (a claim never researched for Informix
   // specifically). InformixSQLHelper now overrides supportsWindowFrame to reproduce the
   // conservative base row directly instead of falling through to DB2SQLHelper's override.

   @Test
   void informix_doesNotInheritDb2RangeValueOptIn_deniesValueDateAndGroups() {
      InformixSQLHelper h = new InformixSQLHelper();
      assertTrue(h.supportsWindowFrame("ROWS", true, false));
      assertTrue(h.supportsWindowFrame("RANGE", false, false));     // peer
      assertFalse(h.supportsWindowFrame("RANGE", true, false));     // NOT DB2's numeric opt-in
      assertFalse(h.supportsWindowFrame("RANGE", true, true));
      assertFalse(h.supportsWindowFrame("GROUPS", false, false));
   }

   // ── PR #4237 review finding A: capability<->render coupling ─────────────────────────────
   // supportsWindowFrame("RANGE", true, true) (date-offset RANGE pushdown) and
   // formatWindowFrameInterval(...) (the dialect's INTERVAL literal renderer) are two separate
   // methods maintained by hand; nothing in the compiler ties them together. If a future dialect
   // opts into date-RANGE pushdown but forgets to override formatWindowFrameInterval, it would
   // silently fall through to the base INTERVAL '<n> <unit>' literal -- which happens to be
   // Postgres/ANSI syntax -- and emit a DB syntax error at query time instead of failing a build.
   // PostgreSQLHelper is the one dialect that legitimately opts in AND legitimately inherits the
   // base literal (its own syntax IS the base syntax), so it is the sole, explicit allow-list
   // exemption. Every other date-RANGE opt-in dialect must render something other than the base
   // literal.
   @Test
   void dateRangeOptIn_requiresNonBaseIntervalRenderer_exceptPostgres() {
      String baseLiteral = new SQLHelper().formatWindowFrameInterval(7, "day");
      assertEquals("INTERVAL '7 day'", baseLiteral);

      SQLHelper[] helpers = new SQLHelper[] {
         new SQLHelper(),
         new PostgreSQLHelper(),
         new OracleSQLHelper(),
         new MySQLHelper(),
         new DB2SQLHelper(),
         new DatabricksHelper(),
         new VerticaHelper(),
         new GoogleBigQueryHelper(),
         new InformixSQLHelper(),
         new SQLServerHelper(),
         new SnowflakeHelper(),
      };

      for(SQLHelper h : helpers) {
         if(!h.supportsWindowFrame("RANGE", true, true)) {
            // this dialect doesn't opt into date-offset RANGE pushdown at all; the coupling
            // doesn't apply (nothing will ever be pushed down for it to mis-render).
            continue;
         }

         if(h instanceof PostgreSQLHelper) {
            // the one intentional base-inheritor: skip the mismatch assertion below, but
            // confirm (outside the loop) that it really does opt in and really does inherit.
            continue;
         }

         assertNotEquals(baseLiteral, h.formatWindowFrameInterval(7, "day"),
            h.getClass().getSimpleName() + " opts into date-offset RANGE pushdown " +
            "(supportsWindowFrame(\"RANGE\", true, true) == true) but formatWindowFrameInterval " +
            "renders the base Postgres/ANSI literal (" + baseLiteral + ") -- it is missing its " +
            "own formatWindowFrameInterval override and would emit invalid syntax for its " +
            "dialect at query time.");
      }

      // Document that PostgreSQL is the deliberate exception, not a silently-skipped dialect:
      // it DOES opt into date-RANGE pushdown, and it DOES render the base literal on purpose.
      PostgreSQLHelper pg = new PostgreSQLHelper();
      assertTrue(pg.supportsWindowFrame("RANGE", true, true),
         "PostgreSQL is expected to opt into date-offset RANGE pushdown");
      assertEquals(baseLiteral, pg.formatWindowFrameInterval(7, "day"),
         "PostgreSQL is the intentional base-literal inheritor (its native syntax IS the base " +
         "ANSI syntax); if this ever fails, PostgreSQLHelper picked up its own " +
         "formatWindowFrameInterval override and the allow-list carve-out above should be " +
         "revisited.");
   }
}
