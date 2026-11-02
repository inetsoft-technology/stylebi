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
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CountDownLatch;
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
      FailingUnion union = failingUnion();
      DistinctTableLens lens = new DistinctTableLens(union, new int[] { 0 }, false);
      union.arm();

      assertSetFailure(assertThrows(MessageException.class, () -> lens.moreRows(XTable.EOT)));
   }

   @Test
   public void sortDistinctMoreRowsThrows() {
      FailingUnion union = failingUnion();
      DistinctTableLens lens = new DistinctTableLens(union, new int[] { 0, 1 }, false);
      union.arm();

      assertSetFailure(assertThrows(MessageException.class, () -> lens.moreRows(XTable.EOT)));
   }

   @Test
   public void rowCountFirstThrows() {
      for(int[] cols : new int[][] { { 0 }, { 0, 1 } }) {
         FailingUnion union = failingUnion();
         DistinctTableLens lens = new DistinctTableLens(union, cols, false);
         union.arm();

         assertSetFailure(assertThrows(MessageException.class, () -> rowCountUntilDone(lens)));
      }
   }

   /**
    * Every later read fails too, with a new instance that is a copy of the kept failure
    * (bug #78084), never as a complete table without rows or with a count of -1.
    */
   @Test
   public void laterReadsThrowCopies() {
      FailingUnion union = failingUnion();
      DistinctTableLens lens = new DistinctTableLens(union, new int[] { 0 }, false);
      union.arm();
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
    * Serializing a lens that failed reads its rows, so it fails with the failure of the base
    * too (as for a lost swap file), it is never a serialization error of the kept failure nor
    * a silently empty table.
    */
   @Test
   public void failedLensSerializationFailsWithTheFailure() {
      DistinctTableLens lens = new DistinctTableLens(new MessageBase(data(10)),
                                                     new int[] { 0 }, false);
      assertThrows(MessageException.class, () -> lens.moreRows(XTable.EOT));
      assertThrows(MessageException.class,
                   () -> TestSerializeUtils.serializeAndDeserialize(lens));
   }

   /**
    * A message exception wrapped by another exception of the base is found in the cause chain,
    * as SortFilter does (bug #77875).
    */
   @Test
   public void wrappedMessageExceptionThrows() {
      DistinctTableLens lens = new DistinctTableLens(new MessageBase(data(10)),
                                                     new int[] { 0 }, false);

      assertThrows(MessageException.class, () -> lens.moreRows(XTable.EOT));
      assertThrows(MessageException.class, () -> lens.moreRows(XTable.EOT));
   }

   /**
    * A script failure of the base is not one, it keeps ending the distinct table as before
    * (bug #77875).
    */
   @Test
   public void scriptExceptionOfBaseUnchanged() {
      DefaultTableLens base = new DefaultTableLens(data(10)) {
         @Override
         public boolean moreRows(int row) {
            if(row >= 6 || row == XTable.EOT) {
               throw new ScriptException("script failed", new MessageException("wrapped"));
            }

            return super.moreRows(row);
         }
      };
      DistinctTableLens lens = new DistinctTableLens(base, new int[] { 0 }, false);

      assertDoesNotThrow(() -> lens.moreRows(XTable.EOT));
      assertDoesNotThrow(lens::getRowCount);
   }

   /**
    * A reader holding a script lock merges and reads on its own thread (bug #76938), it fails
    * too, and so do its later reads.
    */
   @Test
   public void inlineReadThrows() {
      FailingUnion union = failingUnion();
      DistinctTableLens lens = new DistinctTableLens(union, new int[] { 0 }, false);
      union.arm();
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
      FailingUnion union = failingUnion();
      union.arm();

      assertThrows(SetTableLens.SetOperationException.class, () ->
         new inetsoft.report.filter.SortFilter(union, new int[] { 0 },
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

   /**
    * A real union whose merge worker fails with a plain exception after three rows, once
    * {@link FailingUnion#arm()} is called, which the union reports to its readers as a set
    * operation failure. The worker waits at the fourth row until it is armed, so that a lens
    * built over it never sees the failure before the test reads it.
    */
   private static FailingUnion failingUnion() {
      return new FailingUnion();
   }

   private static final class FailingUnion extends UnionTableLens {
      FailingUnion() {
         super(new DefaultTableLens(data(10)), new DefaultTableLens(data(0)));
      }

      void arm() {
         armed.countDown();
      }

      @Override
      protected MergedTable.Visitor getVisitor(Pass pass) {
         MergedTable.Visitor visitor = super.getVisitor(pass);
         AtomicInteger visited = new AtomicInteger();

         return row -> {
            if(visited.incrementAndGet() > 3) {
               try {
                  assertTrue(armed.await(30, TimeUnit.SECONDS), "never armed");
               }
               catch(InterruptedException ex) {
                  Thread.currentThread().interrupt();
               }

               throw new IllegalStateException("visitor failed");
            }

            if(visitor != null) {
               visitor.visit(row);
            }
         };
      }

      private final CountDownLatch armed = new CountDownLatch(1);
   }

   /**
    * A base whose rows from the sixth throw a plain exception wrapping a message exception.
    */
   private static final class MessageBase extends DefaultTableLens {
      MessageBase(Object[][] data) {
         super(data);
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= 6 || row == XTable.EOT) {
            throw new RuntimeException("base failed", new MessageException("wrapped"));
         }

         return super.moreRows(row);
      }
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
