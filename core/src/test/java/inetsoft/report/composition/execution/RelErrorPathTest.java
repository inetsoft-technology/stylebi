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

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O2 (Testing #77123): a formula column whose script throws, read row by row by a reader that
 * swallows each failure. The cells are the same pool off and on. A batch computes all its rows
 * and throws its first script error at the end, so a column failing on every row costs one
 * context clean per batch, not one per failing row. A timeout still ends the batch at once.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RelErrorPathTest {
   @AfterEach
   public void tearDown() {
      SreeEnv.remove(PoolConfig.ENABLED);
   }

   @Test
   public void failingCellsMatchPoolOff() throws Exception {
      String pct = "if(field['value'] % 50 == 0) { throw new Error('x'); } field['value'] * 2";
      Read off = read(pct, 600, false);
      Read on = read(pct, 600, true);
      assertEquals(off.cells, on.cells);
      assertTrue(off.errors > 0);
      assertTrue(on.errors > 0);
      // the failing rows are null cells in both modes
      assertNull(on.cells.get(49));
      assertEquals(4.0, ((Number) on.cells.get(1)).doubleValue());

      Read offAll = read("unknownName + field['value']", 300, false);
      Read onAll = read("unknownName + field['value']", 300, true);
      assertEquals(offAll.cells, onAll.cells);
      // one failure per batch, which differs by mode, never per failing row
      assertTrue(offAll.errors > 0 && offAll.errors < 300, "errors off " + offAll.errors);
      assertTrue(onAll.errors > 0 && onAll.errors < 300, "errors on " + onAll.errors);
   }

   /**
    * The O2 fix: a formula column failing on every row cleans its context once per batch
    * (before, 1000 failing rows cost 1000 cleans, ~0.7 ms each).
    */
   @Test
   public void failingRowsShareABatchClean() throws Exception {
      Read on = read("unknownName + field['value']", 1000, true);
      assertTrue(on.errors > 0, "the reader still sees the failure");
      assertTrue(on.cleans <= 50, "cleans per 1000 failing rows: " + on.cleans);
   }

   /**
    * A row that times out is not followed by the rest of its batch: each row would wait out
    * the timeout again, under the lens lock.
    */
   @Test
   public void timeoutEndsTheBatch() throws Exception {
      String previous = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "1");
      refreshTimeout();

      try {
         for(boolean pool : new boolean[] { false, true }) {
            SreeEnv.setProperty(PoolConfig.ENABLED, String.valueOf(pool));
            AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet());

            try {
               Object[][] data = new Object[21][];
               data[0] = new Object[] {"value"};

               for(int i = 1; i <= 20; i++) {
                  data[i] = new Object[] {i};
               }

               FormulaTableLens lens = new FormulaTableLens(new DefaultTableLens(data),
                  new String[] {"f"}, new String[] {"while(true) {} field['value']"},
                  box.getScriptEnv(), box.getScope());
               long start = System.nanoTime();
               assertThrows(Exception.class, () -> lens.moreRows(1), "pool " + pool);
               long millis = (System.nanoTime() - start) / 1_000_000;
               // one timeout, not one per row of the batch (20 s)
               assertTrue(millis < 8000, "pool " + pool + ": first read took " + millis + " ms");
            }
            finally {
               box.dispose();
            }
         }
      }
      finally {
         if(previous == null) {
            SreeEnv.remove("script.execution.timeout");
         }
         else {
            SreeEnv.setProperty("script.execution.timeout", previous);
         }

         refreshTimeout();
      }
   }

   // the engine caches the property for 10 s
   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   private static Read read(String script, int rows, boolean pool) throws Exception {
      SreeEnv.setProperty(PoolConfig.ENABLED, String.valueOf(pool));
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet());
      assertEquals(pool, box.getScriptEnv() instanceof WorksheetScriptEnv);

      try {
         Object[][] data = new Object[rows + 1][];
         data[0] = new Object[] {"value"};

         for(int i = 1; i <= rows; i++) {
            data[i] = new Object[] {i};
         }

         FormulaTableLens lens = new FormulaTableLens(new DefaultTableLens(data),
            new String[] {"f"}, new String[] {script}, box.getScriptEnv(), box.getScope());
         long cleans0 = pool ? ((WorksheetScriptEnv) box.getScriptEnv()).getMetrics().getCleans() : 0;
         Read read = new Read();

         // the swallowing reader: a failure is counted and the next row read
         for(int r = 1; ; r++) {
            try {
               if(!lens.moreRows(r)) {
                  break;
               }
            }
            catch(Exception ex) {
               read.errors++;
            }
         }

         read.cleans = pool
            ? ((WorksheetScriptEnv) box.getScriptEnv()).getMetrics().getCleans() - cleans0 : 0;

         // every row is computed now: read the cells without computing again
         for(int r = 1; r <= rows; r++) {
            read.cells.add(lens.getObject(r, 1));
         }

         return read;
      }
      finally {
         box.dispose();
      }
   }

   private static final class Read {
      final List<Object> cells = new ArrayList<>();
      int errors;
      long cleans;
   }
}
