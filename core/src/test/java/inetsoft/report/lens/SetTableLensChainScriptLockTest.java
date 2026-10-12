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
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.test.*;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77874: a set table lens over bases whose reads take a script engine lock takes that
 * lock before its own monitor for the read that merges the bases (a header row too), and a
 * row count probe merges nothing while the lock is not held.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SetTableLensChainScriptLockTest {
   /**
    * The probe reports loading without reading the bases or taking the lock; the first read,
    * of the header row, merges the bases holding the lock; the lock is released afterwards.
    */
   @Test
   public void minusTakesTheChainLockFirstAndProbesStartNothing() {
      LendableReentrantLock lock = new LendableReentrantLock();
      LockingFilter left = new LockingFilter(base(5), lock);
      LockingFilter right = new LockingFilter(base(2), lock);
      MinusTableLens minus = new MinusTableLens(left, right);

      assertEquals(-1, minus.getRowCount(), "a probe merged the bases");
      assertNull(left.heldOnDataRead, "a probe read the bases");
      assertFalse(lock.isLocked(), "a probe took the lock");

      assertTrue(minus.moreRows(0));
      assertEquals(Boolean.TRUE, left.heldOnDataRead, "the bases were merged without the lock");
      assertEquals(Boolean.TRUE, right.heldOnDataRead, "the bases were merged without the lock");
      assertFalse(lock.isLocked(), "the lock is held after the read");

      assertTrue(minus.moreRows(3));
      assertFalse(minus.moreRows(4));
      assertEquals(4, minus.getRowCount(), "the header and rows 3 to 5");
   }

   /**
    * A thread already holding the chain's lock (recorded, as a condition filter or a script
    * holds it) reads as before: a probe of that thread merges the bases on the thread and
    * computes the count.
    */
   @Test
   public void heldChainLockReadsAsBefore() {
      LendableReentrantLock lock = new LendableReentrantLock();
      UnionTableLens union = new UnionTableLens(new LockingFilter(base(3), lock),
                                                new LockingFilter(base(3), lock));
      lock.lock();
      JavaScriptEngine.pushHeldScriptLock(lock);

      try {
         assertEquals(4, union.getRowCount(), "the header and 3 distinct rows");
      }
      finally {
         JavaScriptEngine.popHeldScriptLock();
         lock.unlock();
      }

      assertFalse(lock.isLocked());
   }

   /**
    * A non-distinct union's cell read past the end of its bases answers like the distinct
    * path, the row does not exist, instead of throwing ArrayIndexOutOfBoundsException.
    */
   @Test
   public void nonDistinctUnionCellPastTheEndDoesNotExist() {
      UnionTableLens union = new UnionTableLens(base(2), base(2));
      union.setDistinct(false);

      assertTrue(union.moreRows(4));
      assertFalse(union.moreRows(5));
      assertEquals(5, union.getRowCount());
      assertTrue(union.isNull(10, 0));
      assertEquals(0, union.getDouble(10, 1));
      assertNull(union.getObject(10, 0));
   }

   private static DefaultTableLens base(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { "k" + i, i };
      }

      return new DefaultTableLens(data);
   }

   /**
    * Passes its base through and takes an engine lock when its rows are read, as a condition
    * filter over a formula lens does with the pool off. Records whether the lock was held at
    * the first data row read.
    */
   private static final class LockingFilter extends DefaultTableFilter
      implements ChainScriptLock.Source
   {
      LockingFilter(TableLens table, LendableReentrantLock lock) {
         super(table);
         this.lock = lock;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= 1 && heldOnDataRead == null) {
            heldOnDataRead = lock.isHeldByCurrentThread();
         }

         return super.moreRows(row);
      }

      @Override
      public Lock getScriptLock() {
         return lock;
      }

      private final LendableReentrantLock lock;
      volatile Boolean heldOnDataRead;
   }
}
