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
package inetsoft.report.lens;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.TabularSheet;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptStateLint;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature #77123 (context-pool brief §5 P2): an expression column that reads state before
 * writing it warns exactly once, however many rows and lenses evaluate it, with the script
 * context pool off and on. A top-level var the table owns (Testing #77123, P1) is a supported
 * accumulator and warns only when it holds a script object with the pool on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FormulaTableLensStateLintTest {
   @BeforeEach
   void setUp() {
      logger = (Logger) LoggerFactory.getLogger(ScriptStateLint.LOGGER_NAME);
      level = logger.getLevel();
      logger.setLevel(Level.WARN);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      logger.setLevel(level);
   }

   /**
    * Testing #77123 (review r2 m-1): a top-level var accumulator is owned by its table and
    * keeps its value for the whole table in both pool modes, so the lint does not warn about
    * it, and the column computes 1..N.
    */
   @Test
   void ownedPrimitiveAccumulatorDoesNotWarnPoolOffOrOn() {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      ScriptEnv pooled = PoolTestSupport.env();

      for(boolean pool : new boolean[] { false, true }) {
         String formula = unique("var acc = (acc || 0) + 1; acc");
         String compact = unique("var acc2=(acc2||0)+field['x']; acc2");
         long scripts = ScriptStateLint.nodeStateHazardScripts();
         FormulaTableLens lens = new FormulaTableLens(
            table(ROWS), new String[] { "RunningX", "SumX" }, new String[] { formula, compact },
            pool ? pooled : report.getScriptEnv(), new PoolTestSupport.MapScope());
         lens.setTableName("Query1");
         readAll(lens);

         assertEquals(0, warnings(formula).size(), () -> "pool " + pool + ": " + appender.list);
         assertEquals(0, warnings(compact).size(), () -> "pool " + pool + ": " + appender.list);
         assertEquals(scripts, ScriptStateLint.nodeStateHazardScripts(), "pool " + pool);
         assertEquals(ROWS, ((Number) lens.getObject(ROWS, 1)).intValue(), "pool " + pool);
         assertEquals(ROWS * (ROWS + 1) / 2, ((Number) lens.getObject(ROWS, 2)).intValue(),
                      "pool " + pool);
      }
   }

   /**
    * With the pool on, an owned var that holds a script object is kept only within one batch
    * (the P3 leftover), so its read-before-write still warns, once, naming the object; with
    * the pool off it is kept for the table and does not warn.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      "var list = list || []; list.push(field['x']); list.length",
      "var seen = seen || {}; seen[field['x']] = 1; Object.keys(seen).length"
   })
   void ownedObjectAccumulatorWarnsOnlyWithThePoolOn(String text) {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      String formula = unique(text);
      long scripts = ScriptStateLint.nodeStateHazardScripts();

      readAll(new FormulaTableLens(table(ROWS), new String[] { "Obj" },
                                   new String[] { formula }, report.getScriptEnv(),
                                   new PoolTestSupport.MapScope()));
      assertEquals(0, warnings(formula).size(), () -> "pool off: " + appender.list);
      assertEquals(scripts, ScriptStateLint.nodeStateHazardScripts());

      ScriptEnv env = PoolTestSupport.env();

      for(int l = 0; l < 2; l++) {
         FormulaTableLens lens = new FormulaTableLens(table(ROWS), new String[] { "Obj" },
                                                      new String[] { formula }, env,
                                                      new PoolTestSupport.MapScope());
         lens.setTableName("Query2");
         readAll(lens);
      }

      List<ILoggingEvent> warns = warnings(formula);
      assertEquals(1, warns.size(), () -> "pool on: " + appender.list);
      String msg = warns.get(0).getFormattedMessage();
      assertTrue(msg.contains("expression column \"Obj\" of table \"Query2\""), msg);
      assertTrue(msg.contains("rule R1"), msg);
      assertTrue(msg.contains("assigns it an array, object or function"), msg);
      assertTrue(msg.contains("kept only within one batch"), msg);
      assertFalse(msg.contains("not reset between tables"), msg);
      assertEquals(scripts + 1, ScriptStateLint.nodeStateHazardScripts());
   }

   /**
    * A Date in an owned var is kept across pooled batches (Testing #77123, B1 residual), so
    * its read-before-write does not warn, pool off or on.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      "var first; if(!first) { first = new Date(); } first.getTime() > 0 ? 1 : 0",
      "var d = d || new Date(0); d.setTime(d.getTime() + 1000); d.getTime()"
   })
   void aDateAccumulatorDoesNotWarnWithThePoolOn(String text) {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      String formula = unique(text);
      long scripts = ScriptStateLint.nodeStateHazardScripts();

      readAll(new FormulaTableLens(table(ROWS), new String[] { "D" },
                                   new String[] { formula }, report.getScriptEnv(),
                                   new PoolTestSupport.MapScope()));
      FormulaTableLens lens = new FormulaTableLens(table(ROWS), new String[] { "D" },
                                                   new String[] { formula },
                                                   PoolTestSupport.env(),
                                                   new PoolTestSupport.MapScope());
      lens.setTableName("Query2");
      readAll(lens);

      assertEquals(0, warnings(formula).size(), () -> "warnings: " + appender.list);
      assertEquals(scripts, ScriptStateLint.nodeStateHazardScripts());
   }

   /** R2 is unchanged: an undeclared global accumulator warns once, pool off and on. */
   @Test
   void undeclaredGlobalAccumulatorStillWarns() {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      ScriptEnv pooled = PoolTestSupport.env();

      for(boolean pool : new boolean[] { false, true }) {
         String formula = unique(
            "runSum = (typeof runSum == 'undefined' ? 0 : runSum) + field['x']; runSum");
         FormulaTableLens lens = new FormulaTableLens(
            table(ROWS), new String[] { "Total" }, new String[] { formula },
            pool ? pooled : report.getScriptEnv(), new PoolTestSupport.MapScope());
         lens.setTableName("Query3");
         readAll(lens);

         List<ILoggingEvent> warns = warnings(formula);
         assertEquals(1, warns.size(), () -> "pool " + pool + ": " + appender.list);
         String msg = warns.get(0).getFormattedMessage();
         assertTrue(msg.contains("reads global \"runSum\""), msg);
         assertTrue(msg.contains("rule R2"), msg);
         assertTrue(msg.contains("not reset between tables"), msg);
      }
   }

   /**
    * A var that another formula of the table declares with let/const is not owned by the
    * table (it keeps main's behaviour), so its read-before-write still warns, both modes.
    */
   @Test
   void varNotOwnedBecauseOfALetStillWarns() {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      ScriptEnv pooled = PoolTestSupport.env();

      for(boolean pool : new boolean[] { false, true }) {
         String let = unique("let k = field['x']; k");
         String var = unique("var k = (k || 0) + 1; k");
         FormulaTableLens lens = new FormulaTableLens(
            table(ROWS), new String[] { "L", "V" }, new String[] { let, var },
            pool ? pooled : report.getScriptEnv(), new PoolTestSupport.MapScope());
         readAll(lens);

         assertEquals(0, warnings(let).size(), () -> "pool " + pool + ": " + appender.list);
         List<ILoggingEvent> warns = warnings(var);
         assertEquals(1, warns.size(), () -> "pool " + pool + ": " + appender.list);
         String msg = warns.get(0).getFormattedMessage();
         assertTrue(msg.contains("reads variable \"k\""), msg);
         assertTrue(msg.contains("with let or const"), msg);
      }
   }

   /**
    * A table built without a scope (the report constructor) does not own its vars: the var
    * accumulator keeps main's behaviour there, so it still warns, pool off and on.
    */
   @Test
   void accumulatorOfATableWithoutScopeStillWarns() {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      ScriptEnv pooled = PoolTestSupport.env();

      for(boolean pool : new boolean[] { false, true }) {
         String formula = unique("var acc = (acc || 0) + field['x']; acc");
         FormulaTableLens lens = pool
            ? new FormulaTableLens(table(ROWS), new String[] { "RunningX" },
                                   new String[] { formula }, pooled, null)
            : new FormulaTableLens(table(ROWS), new String[] { "RunningX" },
                                   new String[] { formula }, report);
         lens.setTableName("Query4");
         readAll(lens);

         List<ILoggingEvent> warns = warnings(formula);
         assertEquals(1, warns.size(), () -> "pool " + pool + ": " + appender.list);
         String msg = warns.get(0).getFormattedMessage();
         assertTrue(msg.contains("expression column \"RunningX\" of table \"Query4\""), msg);
         assertTrue(msg.contains("reads variable \"acc\""), msg);
         assertTrue(msg.contains("field[-1]['RunningX']"), msg);
         assertFalse(msg.contains("with let or const"), msg);
      }
   }

   @Test
   void safeFormulasDoNotWarn() {
      ScriptEnv env = PoolTestSupport.env();
      String prev = unique("row <= 1 ? field['x'] : field[-1]['Total'] + field['x']");
      String local = unique("var y = field['x'] * 2; y");
      String inPlace = unique("var X = X * 2; X");

      FormulaTableLens lens = new FormulaTableLens(table(ROWS),
         new String[] { "Total", "Double", "InPlace" }, new String[] { prev, local, inPlace },
         env, null);
      readAll(lens);
      assertEquals(0, warnings(prev).size());
      assertEquals(0, warnings(local).size());
      assertEquals(0, warnings(inPlace).size());
   }

   /**
    * One (undeclared global) accumulator formula over 10,000 rows, re-executed 10 times on each of 5 lenses (50
    * executions), pool off and on: one WARN line per formula text, and the text is lexed once.
    */
   @Test
   void accumulatorDoesNotFloodAcrossRowsReExecutionsAndLenses() {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      ScriptEnv pooled = PoolTestSupport.env();

      for(boolean pool : new boolean[] { false, true }) {
         String formula = unique(
            "acc = (typeof acc == 'undefined' ? 0 : acc) + field['x']; acc");
         long scripts = ScriptStateLint.nodeStateHazardScripts();
         long checks = ScriptStateLint.nodeStateLintChecks();
         int before = appender.list.size();

         for(int l = 0; l < 5; l++) {
            FormulaTableLens lens = pool
               ? new FormulaTableLens(table(10_000), new String[] { "RunningX" },
                                      new String[] { formula }, pooled, null)
               : new FormulaTableLens(table(10_000), new String[] { "RunningX" },
                                      new String[] { formula }, report);
            lens.setTableName("Flood" + l);

            for(int e = 0; e < 10; e++) {
               if(e > 0) {
                  lens.invalidate(); // recompiles, so the hook runs again
               }

               readAll(lens);
            }
         }

         assertEquals(1, warnings(formula).size(), "pool " + pool);
         assertEquals(1, appender.list.size() - before, () -> "pool " + pool + ": " + appender.list);
         assertEquals(scripts + 1, ScriptStateLint.nodeStateHazardScripts());
         assertEquals(checks + 1, ScriptStateLint.nodeStateLintChecks());
      }
   }

   /**
    * A failure inside the check (here the column resolution it uses throws) is swallowed: the
    * column still computes every row, and nothing is logged at WARN.
    */
   @Test
   void failureInsideCheckDoesNotAffectTheColumn() {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      String formula = unique("var u = u; field['x'] * 2");
      long errors = ScriptStateLint.nodeStateLintErrors();
      boolean[] thrown = { false };
      DefaultTableLens base = new DefaultTableLens(table(ROWS)) {
         @Override
         public String getColumnIdentifier(int col) {
            for(StackTraceElement e : Thread.currentThread().getStackTrace()) {
               if(e.getClassName().equals(ScriptStateLint.class.getName())) {
                  thrown[0] = true;
                  throw new IllegalStateException("injected");
               }
            }

            return super.getColumnIdentifier(col);
         }
      };

      FormulaTableLens lens = new FormulaTableLens(base, new String[] { "Double" },
                                                   new String[] { formula }, report);
      readAll(lens);

      assertTrue(thrown[0], "the check did not reach the column resolution");
      assertEquals(errors + 1, ScriptStateLint.nodeStateLintErrors());
      assertEquals(0, warnings(formula).size());

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(r * 2, ((Number) lens.getObject(r, 1)).intValue(), "row " + r);
      }
   }

   /**
    * A bare name the row resolves to a column (table prefix, other case) is not script state:
    * no WARN, and the in-place per-row idioms really do compute per row from the column.
    */
   @Test
   void qualifiedAndCaseInsensitiveColumnNamesDoNotWarn() {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      String upper = unique("var company = company.toUpperCase(); company");
      String clamp = unique("if(Sales < 0) Sales = 0; Sales");
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "Customers.Company", "SALES" },
         { "acme", -5 },
         { "globex", 7 },
      });

      // a host scope, as the worksheet queries pass: bare column names resolve on it
      FormulaTableLens lens = new FormulaTableLens(base, new String[] { "Upper", "Clamped" },
                                                   new String[] { upper, clamp },
                                                   report.getScriptEnv(),
                                                   new PoolTestSupport.MapScope());
      readAll(lens);

      assertEquals(0, warnings(upper).size(), () -> "warnings: " + appender.list);
      assertEquals(0, warnings(clamp).size(), () -> "warnings: " + appender.list);
      assertEquals("ACME", lens.getObject(1, 2));
      assertEquals("GLOBEX", lens.getObject(2, 2));
      assertEquals(0, ((Number) lens.getObject(1, 3)).intValue());
      assertEquals(7, ((Number) lens.getObject(2, 3)).intValue());
   }

   private static void readAll(FormulaTableLens lens) {
      for(int r = 0; lens.moreRows(r); r++) {
         lens.getObject(r, lens.getColCount() - 1);
      }
   }

   private List<ILoggingEvent> warnings(String formula) {
      // the message numbers the formula lines, so match on its unique marker
      String marker = formula.substring(formula.indexOf("/*"));
      return appender.list.stream()
         .filter(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(marker))
         .toList();
   }

   // a distinct text per test: the check is once per text per node
   private static String unique(String formula) {
      return formula + " /*" + UUID.randomUUID() + "*/";
   }

   private static DefaultTableLens table(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "x" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { i };
      }

      return new DefaultTableLens(data);
   }

   private static final int ROWS = 600;
   private Logger logger;
   private Level level;
   private ListAppender<ILoggingEvent> appender;
}
