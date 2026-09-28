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
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature #77123 (context-pool brief §5 P2): an expression column that reads its own
 * accumulator before writing it warns exactly once, however many rows and lenses evaluate it,
 * with the script context pool off and on.
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

   @Test
   void accumulatorWarnsOncePoolOff() {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      String formula = unique("var acc = (acc || 0) + field['x']; acc");
      long scripts = ScriptStateLint.nodeStateHazardScripts();

      FormulaTableLens lens = new FormulaTableLens(table(ROWS), new String[] { "RunningX" },
                                                   new String[] { formula }, report);
      lens.setTableName("Query1");
      readAll(lens);
      assertOneWarning(formula, "RunningX", "Query1");
      assertEquals(scripts + 1, ScriptStateLint.nodeStateHazardScripts());

      // a second lens with the same formula does not warn again
      FormulaTableLens lens2 = new FormulaTableLens(table(ROWS), new String[] { "RunningX" },
                                                    new String[] { formula }, report);
      readAll(lens2);
      assertEquals(1, warnings(formula).size());
      assertEquals(scripts + 1, ScriptStateLint.nodeStateHazardScripts());
   }

   @Test
   void accumulatorWarnsOncePoolOn() {
      ScriptEnv env = PoolTestSupport.env();
      String formula = unique("var acc = (acc || 0) + field['x']; acc");

      FormulaTableLens lens = new FormulaTableLens(table(ROWS), new String[] { "RunningX" },
                                                   new String[] { formula }, env, null);
      lens.setTableName("Query2");
      readAll(lens);
      assertOneWarning(formula, "RunningX", "Query2");

      readAll(new FormulaTableLens(table(ROWS), new String[] { "RunningX" },
                                   new String[] { formula }, env, null));
      assertEquals(1, warnings(formula).size());
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
    * One accumulator formula over 10,000 rows, re-executed 10 times on each of 5 lenses (50
    * executions), pool off and on: one WARN line per formula text, and the text is lexed once.
    */
   @Test
   void accumulatorDoesNotFloodAcrossRowsReExecutionsAndLenses() {
      TabularSheet report = new TabularSheet(Mockito.mock(LibManagerProvider.class),
                                             Mockito.mock(Cluster.class));
      ScriptEnv pooled = PoolTestSupport.env();

      for(boolean pool : new boolean[] { false, true }) {
         String formula = unique("var acc = (acc || 0) + field['x']; acc");
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

   private void assertOneWarning(String formula, String col, String table) {
      List<ILoggingEvent> warns = warnings(formula);
      assertEquals(1, warns.size(), () -> "warnings: " + appender.list);
      String msg = warns.get(0).getFormattedMessage();
      assertTrue(msg.contains("expression column \"" + col + "\" of table \"" + table + "\""), msg);
      assertTrue(msg.contains("reads variable \"acc\""), msg);
      assertTrue(msg.contains("field[-1]['" + col + "']"), msg);
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
