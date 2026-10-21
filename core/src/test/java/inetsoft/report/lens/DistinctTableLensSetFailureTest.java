/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.report.internal.table.MergedTable;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.util.MessageException;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A distinct table lens over a set table (here a real union) whose merge fails with a
 * message exception fails its reads as a sort filter over it does, instead of ending as an
 * empty or truncated complete table (bug #78365). A plain exception of a base keeps ending the
 * distinct table as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
public class DistinctTableLensSetFailureTest {
   @Test
   public void hashDistinctMoreRowsThrows() {
      DistinctTableLens lens = new DistinctTableLens(failingUnion(), new int[] { 0 }, false);

      assertSetFailure(assertThrows(MessageException.class, () -> lens.moreRows(XTable.EOT)));
   }

   @Test
   public void sortDistinctMoreRowsThrows() {
      DistinctTableLens lens = new DistinctTableLens(failingUnion(), new int[] { 0, 1 }, false);

      assertSetFailure(assertThrows(MessageException.class, () -> lens.moreRows(XTable.EOT)));
   }

   @Test
   public void rowCountFirstThrows() {
      assertSetFailure(assertThrows(MessageException.class, () ->
         rowCountUntilDone(new DistinctTableLens(failingUnion(), new int[] { 0 }, false))));
      assertSetFailure(assertThrows(MessageException.class, () ->
         rowCountUntilDone(new DistinctTableLens(failingUnion(), new int[] { 0, 1 }, false))));
   }

   /**
    * Every later read fails too, with a new instance that is a copy of the kept failure
    * (bug #78084), never as a complete table without rows or with a count of -1.
    */
   @Test
   public void laterReadsThrowCopies() {
      DistinctTableLens lens = new DistinctTableLens(failingUnion(), new int[] { 0 }, false);
      MessageException first = assertThrows(
         MessageException.class, () -> lens.moreRows(XTable.EOT));
      MessageException second = assertThrows(
         MessageException.class, () -> lens.moreRows(XTable.EOT));
      MessageException third = assertThrows(MessageException.class, lens::getRowCount);

      assertNotSame(second, third);
      assertEquals(first.getMessage(), second.getMessage());
      assertEquals(first.getMessage(), third.getMessage());
      assertNotNull(second.getCause());
      assertNotSame(first, second);
   }

   /**
    * The kept failure is transient, a failed lens still serializes.
    */
   @Test
   public void failedLensSerializes() throws Exception {
      DistinctTableLens lens = new DistinctTableLens(new DefaultTableLens(data(3)),
                                                     new int[] { 0 }, false);
      assertFalse(lens.moreRows(XTable.EOT));
      assertEquals(DistinctTableLens.class,
                   TestSerializeUtils.serializeAndDeserialize(lens).getClass());
   }

   /**
    * A reader holding a script lock merges and reads on its own thread (bug #76938), it fails
    * too, and so do its later reads.
    */
   @Test
   public void inlineReadThrows() {
      DistinctTableLens lens = new DistinctTableLens(failingUnion(), new int[] { 0 }, false);
      LendableReentrantLock lock = new LendableReentrantLock();
      lock.lock();
      JavaScriptEngine.pushHeldScriptLock(lock);

      try {
         assertSetFailure(assertThrows(MessageException.class,
                                       () -> lens.moreRows(XTable.EOT)));
         assertThrows(MessageException.class, lens::getRowCount);
         assertThrows(MessageException.class, () -> lens.moreRows(XTable.EOT));
      }
      finally {
         JavaScriptEngine.popHeldScriptLock();
         lock.unlock();
      }
   }

   @Test
   public void sortFilterFailsTheSame() {
      assertThrows(SetTableLens.SetOperationException.class, () ->
         new inetsoft.report.filter.SortFilter(failingUnion(), new int[] { 0 },
                                               new boolean[] { true }).moreRows(XTable.EOT));
   }

   /**
    * A plain exception of a base, not wrapped in a message exception, is unchanged: the
    * distinct table ends with the rows found so far, which is a policy matter apart from
    * this bug.
    */
   @Test
   public void plainExceptionOfBaseUnchanged() {
      DefaultTableLens base = new DefaultTableLens(data(10)) {
         @Override
         public boolean moreRows(int row) {
            if(row >= 6 && row != XTable.EOT || row == XTable.EOT) {
               throw new IllegalStateException("base failed");
            }

            return super.moreRows(row);
         }
      };
      DistinctTableLens lens = new DistinctTableLens(base, new int[] { 0 }, false);

      assertDoesNotThrow(() -> lens.moreRows(XTable.EOT));
      assertDoesNotThrow(lens::getRowCount);
   }

   /**
    * A real union whose merge worker fails after three rows with a plain exception, which the
    * union reports to its readers as a set operation failure.
    */
   /**
    * The row count of a table still loading is negative, it is the number of rows once done.
    */
   private static int rowCountUntilDone(DistinctTableLens lens) {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
      int count = lens.getRowCount();

      while(count < 0 && System.nanoTime() < end) {
         Thread.yield();
         count = lens.getRowCount();
      }

      return count;
   }

   private static void assertSetFailure(MessageException ex) {
      assertEquals(inetsoft.util.Catalog.getCatalog().getString("common.table.getDataFailed"),
                   ex.getMessage());
      assertNotNull(ex.getCause());
   }

   private static TableLens failingUnion() {
      AtomicInteger visited = new AtomicInteger();
      UnionTableLens union = new UnionTableLens(new DefaultTableLens(data(10)),
                                                new DefaultTableLens(data(0)))
      {
         @Override
         protected MergedTable.Visitor getVisitor(Pass pass) {
            MergedTable.Visitor visitor = super.getVisitor(pass);

            return row -> {
               if(visited.incrementAndGet() > 3) {
                  throw new IllegalStateException("visitor failed");
               }

               if(visitor != null) {
                  visitor.visit(row);
               }
            };
         }
      };

      return union;
   }

   private static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { "k" + r, r };
      }

      return data;
   }
}
