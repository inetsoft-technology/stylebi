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
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapLostTestSupport.LostTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static inetsoft.util.swap.SwapLostTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A lost swap file of a join's base fails the read of the join instead of leaving the join
 * with the rows joined before the failure (bug #77651), whether the join runs on its worker
 * threads or inline on a thread that holds the script lock (bug #77215).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class JoinSwapLostTest {
   @Test
   public void mergeJoinWorkerFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      TableLens join = merge(new LostTable(values(40), 21, lost), new DefaultTableLens(values(40)));

      assertSame(lost, swapIn(failureOf(15, () -> drain(join))));
   }

   @Test
   public void mergeJoinWorkerWithTheLostRightBaseFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      TableLens join = merge(new DefaultTableLens(values(40)), new LostTable(values(40), 21, lost));

      assertSame(lost, swapIn(failureOf(15, () -> drain(join))));
   }

   @Test
   public void hashJoinWorkerFailsTheRead() throws Exception {
      // the two scans run on two workers in either order
      for(int round = 0; round < 3; round++) {
         SwapFileReadException lost = swapLost();
         TableLens join =
            hash(new LostTable(values(40), 21, lost), new DefaultTableLens(values(40)));

         assertSame(lost, swapIn(failureOf(15, () -> drain(join))), "round " + round);
      }
   }

   @Test
   public void hashJoinWorkerWithTheLostRightBaseFailsTheRead() throws Exception {
      for(int round = 0; round < 3; round++) {
         SwapFileReadException lost = swapLost();
         TableLens join =
            hash(new DefaultTableLens(values(40)), new LostTable(values(40), 21, lost));

         assertSame(lost, swapIn(failureOf(15, () -> drain(join))), "round " + round);
      }
   }

   @Test
   public void inlineMergeJoinFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      Supplier<TableLens> join =
         () -> merge(new LostTable(values(40), 21, lost), new DefaultTableLens(values(40)));

      assertSame(lost, swapIn(failureOf(15, () -> drain(inline(join)))));
   }

   @Test
   public void inlineHashJoinFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      Supplier<TableLens> join =
         () -> hash(new LostTable(values(40), 21, lost), new DefaultTableLens(values(40)));

      assertSame(lost, swapIn(failureOf(15, () -> drain(inline(join)))));
   }

   @Test
   public void inlineHashJoinWithTheLostRightBaseFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      Supplier<TableLens> join =
         () -> hash(new DefaultTableLens(values(40)), new LostTable(values(40), 21, lost));

      assertSame(lost, swapIn(failureOf(15, () -> drain(inline(join)))));
   }

   private static TableLens hash(TableLens left, TableLens right) {
      return new HashJoinTable(left, right, new int[] { 1 }, new int[] { 1 },
                               JoinTableLens.INNER_JOIN, true, Integer.MAX_VALUE);
   }

   private static TableLens merge(TableLens left, TableLens right) {
      return new MergeJoinTable(left, right, new int[] { 1 }, new int[] { 1 },
                                JoinTableLens.INNER_JOIN, true, Integer.MAX_VALUE);
   }

   /**
    * Build the join on a thread that holds the script lock, so it joins inline.
    */
   private static TableLens inline(Supplier<TableLens> join) {
      JavaScriptEngine.pushHeldScriptLock(new ReentrantLock());

      try {
         return join.get();
      }
      finally {
         JavaScriptEngine.popHeldScriptLock();
      }
   }

   /**
    * {@code rows} rows of {@code key, value}, value {@code i} for row {@code i}.
    */
   private static Object[][] values(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { "k" + r, r };
      }

      return data;
   }
}
