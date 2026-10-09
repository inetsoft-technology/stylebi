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

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static inetsoft.util.swap.SwapLostTestSupport.swapLost;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A worksheet formula table whose formulas own a var, and whose row read data that was not
 * available inside its script (a lock stall, a lost swap file) once: a later read of the same
 * lens gets every value of the control, the var applied once per row, not the stalled row's
 * change twice (bug #78133). Pool off and on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormulaTableLensStallVarTest {
   @AfterEach
   void tearDown() {
      if(env instanceof WorksheetScriptEnv pooled) {
         pooled.retire();
      }
   }

   /**
    * The accumulator is the column whose field read fails.
    */
   @ParameterizedTest(name = "pool={0}, fault={1}")
   @CsvSource({ "false, stall", "false, swap", "true, stall", "true, swap" })
   void ownedVarIsAppliedOnceAfterAFailedRead(boolean pool, String fault) throws Exception {
      assertAppliedOnce(pool, fault, new String[] { "acc" },
                        new String[] { "var k = (k || 0) + 1; var v = field['value']; k" });
   }

   /**
    * The accumulator is another column of the row, which ran before the failed one: the
    * whole row is computed again.
    */
   @ParameterizedTest(name = "pool={0}, fault={1}")
   @CsvSource({ "false, stall", "false, swap", "true, stall", "true, swap" })
   void ownedVarOfAnotherColumnIsAppliedOnceAfterAFailedRead(boolean pool, String fault)
      throws Exception
   {
      assertAppliedOnce(pool, fault, new String[] { "acc", "v" },
                        new String[] { "var k = (k || 0) + 1; k", "field['value']" });
   }

   private void assertAppliedOnce(boolean pool, String fault, String[] headers,
                                  String[] formulas) throws Exception
   {
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      env = box.getScriptEnv();
      RuntimeException failure = "stall".equals(fault)
         ? new LockStallException("test.site", "worker", 1234, null) : swapLost();
      FailOnceTable base = new FailOnceTable(() -> failure);
      TableLens lens = PostProcessor.formula(base, headers, formulas, env, box.getScope(), null,
                                             "T", null, types(formulas.length),
                                             new boolean[formulas.length]);

      Throwable thrown = assertThrows(RuntimeException.class, () -> read(lens));
      Class<?> type = "stall".equals(fault) ? LockStallException.class
         : SwapFileReadException.class;
      assertNotNull("stall".equals(fault) ? LockStallException.find(thrown)
                       : SwapFileReadException.find(thrown),
                    "the reader gets the " + type.getSimpleName() + ", not " + thrown);
      assertTrue(base.failed, "sanity: the field read failed");

      assertEquals(expected(), read(lens), "the read after the failure");
      assertEquals(expected(), read(lens), "a later read");
   }

   private static List<Class<?>> types(int n) {
      List<Class<?>> types = new ArrayList<>();

      for(int i = 0; i < n; i++) {
         types.add(Double.class);
      }

      return types;
   }

   private static List<Integer> expected() {
      List<Integer> values = new ArrayList<>();

      for(int r = 1; r <= ROWS; r++) {
         values.add(r);
      }

      return values;
   }

   // the accumulator column, the first formula column
   private static List<Integer> read(TableLens lens) {
      List<Integer> values = new ArrayList<>();

      for(int r = 1; lens.moreRows(r); r++) {
         Object value = lens.getObject(r, 2);
         values.add(value instanceof Number ? ((Number) value).intValue() : null);
      }

      return values;
   }

   private static Object[][] data() {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] { "k" + i, i };
      }

      return data;
   }

   /**
    * A base whose first read of the value of row {@link #FAILED_ROW} fails, as a stalled
    * table or a transiently lost swap file does; the later reads succeed.
    */
   private static final class FailOnceTable extends DefaultTableLens {
      FailOnceTable(Supplier<RuntimeException> failure) {
         super(data());
         this.failure = failure;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == FAILED_ROW && c == 1 && !failed) {
            failed = true;
            throw failure.get();
         }

         return super.getObject(r, c);
      }

      private final Supplier<RuntimeException> failure;
      private volatile boolean failed;
   }

   private static final int ROWS = 8;
   private static final int FAILED_ROW = 3;
   private ScriptEnv env;
}
