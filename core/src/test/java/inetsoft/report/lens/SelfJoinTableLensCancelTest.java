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
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78341: a non-equality in-memory join is a SelfJoinTableLens over a CrossJoinTableLens.
 * Its scan never read the cancelled flag, so Stop query changed nothing and the join ran to
 * the end with all its rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SelfJoinTableLensCancelTest {
   private static final int N = 3000;

   @Test
   public void cancelEndsALessJoin() throws Exception {
      assertCancelEnds(SelfJoinTableLens.LESS_JOIN, (long) N * (N - 1) / 2);
   }

   @Test
   public void cancelEndsAGreaterEqualJoin() throws Exception {
      assertCancelEnds(SelfJoinTableLens.GREATER_EQUAL_JOIN, (long) N * (N + 1) / 2);
   }

   @Test
   public void cancelEndsANotEqualJoin() throws Exception {
      assertCancelEnds(SelfJoinTableLens.NOT_EQUAL_JOIN, (long) N * (N - 1));
   }

   /** A cancel after the join completed leaves it whole. */
   @Test
   public void cancelOfACompleteJoinKeepsItWhole() throws Exception {
      SelfJoinTableLens join = join(SelfJoinTableLens.LESS_JOIN);
      join.moreRows(XTable.EOT);
      join.cancel();

      assertFalse(join.isCancelled());
      assertEquals((long) N * (N - 1) / 2, dataRows(join));
   }

   /** A cancelled join that is invalidated and read again is not stuck at its partial rows. */
   @Test
   public void invalidateAfterCancelRunsAFullPass() throws Exception {
      long full = (long) N * (N - 1) / 2;
      SelfJoinTableLens join = join(SelfJoinTableLens.LESS_JOIN);
      long rows = readCancelled(join);
      assertTrue(rows < full, "the cancel did not stop the join: " + rows);

      join.invalidate();
      join.moreRows(XTable.EOT);

      assertEquals(full, dataRows(join));
   }

   private static void assertCancelEnds(int op, long full) throws Exception {
      SelfJoinTableLens join = join(op);
      long rows = readCancelled(join);

      assertTrue(rows < full, "the cancel did not stop the join: " + rows + " of " + full);
      assertTrue(join.isCancelled());
      // a second cancel (QueryManager and Stop query both reach the lens) keeps it cancelled
      join.cancel();
      assertTrue(join.isCancelled());
   }

   // reads the join in another thread, cancels it 300 ms into the scan and waits for the read
   private static long readCancelled(SelfJoinTableLens join) throws Exception {
      AtomicInteger failed = new AtomicInteger();
      Thread reader = new Thread(() -> {
         try {
            join.moreRows(XTable.EOT);
         }
         catch(Throwable ex) {
            failed.incrementAndGet();
         }
      });
      reader.start();
      Thread.sleep(300);
      join.cancel();
      reader.join(30000);

      assertFalse(reader.isAlive(), "the read did not end after the cancel");
      assertEquals(0, failed.get());
      return dataRows(join);
   }

   private static long dataRows(TableLens join) {
      return join.getRowCount() - join.getHeaderRowCount();
   }

   // column 0 of the left table (i) OP column 1 (the right table's i), both bases complete
   private static SelfJoinTableLens join(int op) {
      CrossJoinTableLens cross = new CrossJoinTableLens(table(), table());
      cross.moreRows(XTable.EOT);
      SelfJoinTableLens join = new SelfJoinTableLens(cross);
      join.addJoin(0, op, 1);
      return join;
   }

   private static TableLens table() {
      Object[][] data = new Object[N + 1][1];
      data[0][0] = "id";

      for(int i = 1; i <= N; i++) {
         data[i][0] = i;
      }

      return new DefaultTableLens(data);
   }
}
