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

import inetsoft.report.TableLens;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.internal.table.MergedTable;
import inetsoft.test.*;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The reporter's shape of bug #77875 over each set operation: a summary of the third column
 * of a set table whose merged table can't be created fails every read with the "get data
 * failed" message exception, directly and through a sort, and returns the correct summary
 * once the set table recovers (the minus case after its real, unshortened retry delay).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
public class SummaryFilterSetLensFailureTest {
   enum Op {
      MINUS(t -> new MinusTableLens(t, new DefaultTableLens(data(0))) {
         @Override
         protected MergedTable createMergedTable() throws Exception {
            return create(super::createMergedTable);
         }

         @Override
         long getFailureRetryDelay() {
            return retryDelay(super.getFailureRetryDelay());
         }
      }),
      UNION(t -> new UnionTableLens(t, new DefaultTableLens(data(0))) {
         @Override
         protected MergedTable createMergedTable() throws Exception {
            return create(super::createMergedTable);
         }

         @Override
         long getFailureRetryDelay() {
            return retryDelay(super.getFailureRetryDelay());
         }
      }),
      INTERSECT(t -> new IntersectTableLens(t, new DefaultTableLens(data(5))) {
         @Override
         protected MergedTable createMergedTable() throws Exception {
            return create(super::createMergedTable);
         }

         @Override
         long getFailureRetryDelay() {
            return retryDelay(super.getFailureRetryDelay());
         }
      });

      Op(Function<TableLens, SetTableLens> factory) {
         this.factory = factory;
      }

      final Function<TableLens, SetTableLens> factory;
   }

   @ParameterizedTest
   @EnumSource(Op.class)
   public void summaryOfFailingSetTableFailsThenRecovers(Op op) throws Exception {
      check(op, false);
   }

   @ParameterizedTest
   @EnumSource(Op.class)
   public void summaryOfSortOfFailingSetTableFailsThenRecovers(Op op) throws Exception {
      check(op, true);
   }

   private void check(Op op, boolean sorted) throws Exception {
      FAIL.set(true);
      // the reporter's case waits for the real retry delay, the others don't wait
      REAL_DELAY.set(op == Op.MINUS && !sorted);

      try {
         SetTableLens set = op.factory.apply(new DefaultTableLens(data(5)));
         TableLens base = sorted ? new SortFilter(set, new int[] { 0 }, true) : set;
         SummaryFilter summary =
            new SummaryFilter(base, new int[] { 0 }, new int[] { 2 }, new SumFormula(), null);

         assertDataFailed(() -> summary.moreRows(TableLens.EOT));
         assertDataFailed(summary::getRowCount);
         assertDataFailed(() -> summary.getObject(1, 0));

         FAIL.set(false);

         if(REAL_DELAY.get()) {
            // the set table retries after its real retry delay
            Thread.sleep(set.getFailureRetryDelay() + 200);
         }

         assertFalse(summary.moreRows(TableLens.EOT));
         assertEquals(6, summary.getRowCount());
         assertEquals("id", summary.getObject(0, 0));

         for(int r = 1; r <= 5; r++) {
            assertEquals("k" + r, summary.getObject(r, 0));
            assertEquals(r * 10.0, ((Number) summary.getObject(r, 1)).doubleValue());
         }
      }
      finally {
         FAIL.set(false);
      }
   }

   /**
    * A read fails with the set table's message exception, never with rows. A row count is
    * "loading" (negative) until the failed pass completes.
    */
   private static void assertDataFailed(ThrowingRead read) throws InterruptedException {
      long end = System.currentTimeMillis() + 20000;

      while(true) {
         Object result;

         try {
            result = read.read();
         }
         catch(Throwable ex) {
            assertInstanceOf(SetTableLens.SetOperationException.class, ex);
            assertSame(ex, MessageException.find(ex));
            assertInstanceOf(IOException.class, ex.getCause());
            return;
         }

         assertTrue(result instanceof Integer && (Integer) result < 0,
                    "a failed table returned a result: " + result);
         assertTrue(System.currentTimeMillis() < end, "the read never failed");
         Thread.sleep(10);
      }
   }

   private static long retryDelay(long delay) {
      return REAL_DELAY.get() ? delay : 0;
   }

   private static MergedTable create(Factory factory) throws Exception {
      if(FAIL.get()) {
         throw new IOException("Cannot create the cache temp file of a merged table");
      }

      return factory.create();
   }

   private static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "name", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { "k" + r, "n" + r, r * 10 };
      }

      return data;
   }

   private interface Factory {
      MergedTable create() throws Exception;
   }

   private interface ThrowingRead {
      Object read() throws Exception;
   }

   private static final AtomicBoolean FAIL = new AtomicBoolean();
   private static final AtomicBoolean REAL_DELAY = new AtomicBoolean();
}
