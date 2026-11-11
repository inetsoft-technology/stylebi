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
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The row order of a cross join is the left table varying fastest, whichever base table
 * finishes loading first (bug #81087).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CrossJoinTableLensRowOrderTest {
   /**
    * The left table is still loading when the right table completes: the rows are still left
    * fastest.
    */
   @Test
   public void rightCompletesFirstKeepsLeftFastest() throws Exception {
      GatedTable left = new GatedTable("L", 3);
      GatedTable right = new GatedTable("R", 5);
      right.gate.countDown();
      CrossJoinTableLens lens = new CrossJoinTableLens(left, right);

      try {
         lens.moreRows(0);
         assertTrue(right.reported.await(CAP_SECONDS, TimeUnit.SECONDS));
         // the right loader updates its count right after reading it
         Thread.sleep(200);
         left.gate.countDown();

         assertFalse(lens.moreRows(TableLens.EOT));
         assertEquals(3 * 5 + 1, lens.getRowCount());
         assertOrder(lens, 3, 5);
      }
      finally {
         left.gate.countDown();
         right.gate.countDown();
      }
   }

   /**
    * Both tables complete: the same order every time.
    */
   @Test
   public void bothCompleteLeftFastestEveryTime() {
      for(int i = 0; i < ITERATIONS; i++) {
         CrossJoinTableLens lens = new CrossJoinTableLens(
            new DefaultTableLens(data("L", 3)), new DefaultTableLens(data("R", 5)));
         assertFalse(lens.moreRows(TableLens.EOT));
         assertEquals(16, lens.getRowCount());
         assertOrder(lens, 3, 5);
      }
   }

   /**
    * The left table completed and the right table is still loading: no data row before the
    * right table reports its first rows, then the count grows without moving the rows read.
    */
   @Test
   public void leftCompleteRightLoadingIsProgressiveAndStable() throws Exception {
      GatedTable left = new GatedTable("L", 3);
      GatedTable right = new GatedTable("R", 5);
      left.gate.countDown();
      CrossJoinTableLens lens = new CrossJoinTableLens(left, right);

      try {
         lens.moreRows(0);
         assertTrue(left.reported.await(CAP_SECONDS, TimeUnit.SECONDS));

         // the first chunk of the right table is one row, so the rows so far are 3 * 1
         long deadline = System.currentTimeMillis() + CAP_SECONDS * 1000;

         while(lens.getRowCount() != -(3 * 1 + 1) - 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
         }

         assertEquals(-(3 * 1 + 1) - 1, lens.getRowCount());
         assertEquals("L1", lens.getObject(1, 0));
         assertEquals("L2", lens.getObject(2, 0));
         assertEquals("L3", lens.getObject(3, 0));
         assertEquals("R1", lens.getObject(3, 1));

         right.gate.countDown();
         assertFalse(lens.moreRows(TableLens.EOT));
         assertEquals(3 * 5 + 1, lens.getRowCount());
         assertEquals("L3", lens.getObject(3, 0));
         assertEquals("R1", lens.getObject(3, 1));
         assertOrder(lens, 3, 5);
      }
      finally {
         left.gate.countDown();
         right.gate.countDown();
      }
   }

   /**
    * join.table.maxrows keeps the first rows of the left fastest order.
    */
   @Test
   public void maxRowsKeepsFirstRowsOfLeftFastestOrder() {
      String old = SreeEnv.getProperty("join.table.maxrows");
      SreeEnv.setProperty("join.table.maxrows", "7");

      try {
         for(int i = 0; i < ITERATIONS; i++) {
            CrossJoinTableLens lens = new CrossJoinTableLens(
               new DefaultTableLens(data("L", 3)), new DefaultTableLens(data("R", 5)));
            assertFalse(lens.moreRows(TableLens.EOT));
            assertEquals(7 + 1, lens.getRowCount());

            for(int r = 0; r < 7; r++) {
               assertEquals("L" + (r % 3 + 1), lens.getObject(r + 1, 0));
               assertEquals("R" + (r / 3 + 1), lens.getObject(r + 1, 1));
            }
         }
      }
      finally {
         if(old == null) {
            SreeEnv.remove("join.table.maxrows");
         }
         else {
            SreeEnv.setProperty("join.table.maxrows", old);
         }
      }
   }

   private static void assertOrder(CrossJoinTableLens lens, int lrows, int rrows) {
      for(int r = 0; r < lrows * rrows; r++) {
         assertEquals("L" + (r % lrows + 1), lens.getObject(r + 1, 0), "left of row " + (r + 1));
         assertEquals("R" + (r / lrows + 1), lens.getObject(r + 1, 1), "right of row " + (r + 1));
      }
   }

   private static Object[][] data(String prefix, int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { prefix };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { prefix + r };
      }

      return data;
   }

   /**
    * A table whose row count, when read by a cross join loader, waits for {@link #gate}
    * and is reported by {@link #reported} first.
    */
   private static class GatedTable extends DefaultTableLens {
      GatedTable(String prefix, int rows) {
         super(data(prefix, rows));
      }

      @Override
      public int getRowCount() {
         if(Thread.currentThread().getClass().getName().contains("CrossJoinTableLens$")) {
            reported.countDown();

            try {
               gate.await(CAP_SECONDS, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.getRowCount();
      }

      private final CountDownLatch gate = new CountDownLatch(1);
      private final CountDownLatch reported = new CountDownLatch(1);
   }

   private static final int ITERATIONS = 200;
   private static final long CAP_SECONDS = 30;
}
