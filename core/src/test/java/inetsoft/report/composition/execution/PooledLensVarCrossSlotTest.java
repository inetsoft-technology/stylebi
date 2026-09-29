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
package inetsoft.report.composition.execution;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.TableRowScope;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A formula table's var across pooled batches that run on different contexts (Testing
 * #77123): a primitive var is kept, whichever context a batch runs on; a script object
 * created on another context cannot be used there, so it reads as undefined with one WARN,
 * no worse than the per-batch reset the pool had before, and never as a live reference to
 * the other context (which failed with a TypeError or a multi-threaded access error).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledLensVarCrossSlotTest {
   @BeforeEach
   void setUp() throws Exception {
      box = PoolTestSupport.poolBox(true);
      env = (WorksheetScriptEnv) box.getScriptEnv();
      logger = (Logger) LoggerFactory.getLogger(TableRowScope.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      env.retire();
   }

   @Test
   void aPrimitiveVarContinuesOnAnotherContext() throws Exception {
      TableLens t = make("var acc = (acc || 0) + field['value']; acc");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);
      // the batches that computed rows 1..200 ran here; the next starts on another context
      PoolTestSupport.whileHeldElsewhere(env, () -> read(t, v, 201, 600));
      // and back on the first one
      read(t, v, 601, ROWS);

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(r, v[r], "row " + r);
      }

      assertTrue(warnings().isEmpty(), "no warning for a primitive: " + warnings());
   }

   @Test
   void anObjectVarOfAnotherContextReadsAsUndefinedWithOneWarning() throws Exception {
      TableLens t = make("var a = a || []; a.push(1); a.length");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);

      // the whole batch on another context: the array of the first context is not used
      // there, the formula starts a new one, which is then kept within that batch
      PoolTestSupport.whileHeldElsewhere(env, () -> read(t, v, 201, 600));
      // the batches that computed rows 1..first ran on this thread's context, a sequential
      // read's batches doubling from the pool-off look-ahead, so first is past 200
      int first = 200;

      while(v[first + 1] != 1.0) {
         first++;
      }

      assertTrue(first < 600, "no batch on the other context");

      for(int r = 1; r <= first; r++) {
         assertEquals(r, v[r], "first batch row " + r);
      }

      for(int r = first + 1; r <= 600; r++) {
         assertEquals(r - first, v[r], "second batch row " + r);
      }

      List<ILoggingEvent> warns = warnings();
      assertEquals(1, warns.size(), "one warning: " + warns);
      assertTrue(warns.get(0).getFormattedMessage().contains("\"a\" holds an array"),
                 warns.get(0).getFormattedMessage());
   }

   @Test
   void anObjectVarIsKeptAcrossBatchesOnTheSameContext() throws Exception {
      TableLens t = make("var o = o || {n: 0}; o.n++; o.n");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, ROWS);

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(r, v[r], "row " + r);
      }

      assertTrue(warnings().isEmpty(), "no warning on one context: " + warnings());
   }

   // --- helpers ---

   private TableLens make(String expr) {
      Object[][] d = new Object[ROWS + 1][];
      d[0] = new Object[] { "value", "id" };

      for(int i = 1; i <= ROWS; i++) {
         d[i] = new Object[] { 1, i };
      }

      return PostProcessor.formula(new DefaultTableLens(d), new String[] { "out" },
                                   new String[] { expr }, env, box.getScope(), null, "T", null,
                                   List.of(Double.class), new boolean[] { false });
   }

   private static void read(TableLens t, double[] v, int from, int to) {
      for(int r = from; r <= to; r++) {
         assertTrue(t.moreRows(r));
         Object o = t.getObject(r, 2);
         v[r] = o instanceof Number ? ((Number) o).doubleValue() : Double.NaN;
      }
   }

   private List<ILoggingEvent> warnings() {
      return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
   }

   private static final int ROWS = 1200;
   private AssetQuerySandbox box;
   private WorksheetScriptEnv env;
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}
