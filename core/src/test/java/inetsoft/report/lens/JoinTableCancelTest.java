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
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.TableFilter2;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.util.XTableTableNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78135: JoinTable.cancel() took the cancelled flag from its last base. A cancel of a
 * running join completes it with the rows so far, and a fully loaded base reports not
 * cancelled, so the partial join reported not cancelled and AssetDataCache kept handing it out
 * as the whole join. The join must report cancelled if the cancel stopped it while it was
 * running, and a cancel of a complete join must leave it whole and cacheable.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JoinTableCancelTest {
   // 100 x 100 rows joined on i % 2
   private static final int ROWS = 100;
   private static final int FULL = ROWS * ROWS / 2;

   @AfterEach
   void resetForceHash() {
      SreeEnv.remove("join.table.forceHash");
   }

   @Test
   void hashJoinCancelledWhileRunning() throws Exception {
      SlowBase left = new SlowBase();
      JoinTable join = new HashJoinTable(left, loadedBase(), new int[] { 1 }, new int[] { 1 },
                                         JoinTableLens.INNER_JOIN, true, Integer.MAX_VALUE);
      assertCancelledWhileRunning(join, left);
   }

   @Test
   void mergeJoinCancelledWhileRunning() throws Exception {
      SlowBase left = new SlowBase();
      JoinTable join = new MergeJoinTable(left, loadedBase(), new int[] { 1 }, new int[] { 1 },
                                          JoinTableLens.INNER_JOIN, true, Integer.MAX_VALUE);
      assertCancelledWhileRunning(join, left);
   }

   @Test
   void cancelledJoinLensIsNotCacheable() throws Exception {
      SreeEnv.setProperty("join.table.forceHash", "true");
      SlowBase left = new SlowBase();
      JoinTableLens lens = new JoinTableLens(left, loadedBase(), new int[] { 1 },
                                             new int[] { 1 }, JoinTableLens.INNER_JOIN, true);
      TableFilter2 cached = new TableFilter2(lens);
      assertTrue(left.started.await(30, TimeUnit.SECONDS), "the join never read its base");

      lens.cancel();

      assertTrue(lens.isCancelled());
      assertTrue(AssetDataCache.isCancelled(cached));
   }

   @Test
   void hashJoinCancelledAfterCompletionStaysWhole() throws Exception {
      JoinTable join = new HashJoinTable(fastBase(), loadedBase(), new int[] { 1 },
                                         new int[] { 1 }, JoinTableLens.INNER_JOIN, true,
                                         Integer.MAX_VALUE);
      assertCancelAfterCompletion(join);
   }

   @Test
   void mergeJoinCancelledAfterCompletionStaysWhole() throws Exception {
      JoinTable join = new MergeJoinTable(fastBase(), loadedBase(), new int[] { 1 },
                                          new int[] { 1 }, JoinTableLens.INNER_JOIN, true,
                                          Integer.MAX_VALUE);
      assertCancelAfterCompletion(join);
   }

   @Test
   void completeJoinLensStaysCacheable() throws Exception {
      SreeEnv.setProperty("join.table.forceHash", "true");
      JoinTableLens lens = new JoinTableLens(fastBase(), loadedBase(), new int[] { 1 },
                                             new int[] { 1 }, JoinTableLens.INNER_JOIN, true);
      lens.moreRows(XTable.EOT);

      lens.cancel();

      assertFalse(lens.isCancelled());
      assertFalse(AssetDataCache.isCancelled(new TableFilter2(lens)));
      assertEquals(FULL, lens.getRowCount() - lens.getHeaderRowCount());
   }

   private static void assertCancelledWhileRunning(JoinTable join, SlowBase left)
      throws Exception
   {
      assertTrue(left.started.await(30, TimeUnit.SECONDS), "the join never read its base");
      assertTrue(join.getRowCount() < 0, "the join must still be running when it is cancelled");

      join.cancel();
      join.moreRows(XTable.EOT);
      int rows = join.getRowCount() - join.getHeaderRowCount();

      assertTrue(rows < FULL, "the cancel landed after the join completed: " + rows);
      assertTrue(join.isCancelled(), "a join stopped with " + rows + " of " + FULL +
         " rows reports not cancelled");
   }

   private static void assertCancelAfterCompletion(JoinTable join) {
      join.moreRows(XTable.EOT);
      assertEquals(FULL, join.getRowCount() - join.getHeaderRowCount());

      join.cancel();

      assertFalse(join.isCancelled(), "a cancel of a complete join must leave it whole");
      assertEquals(FULL, join.getRowCount() - join.getHeaderRowCount());
   }

   // a real query result, already loaded: a cancel leaves it not cancelled
   private static TableLens loadedBase() {
      XNodeTableLens base = new XNodeTableLens(new XTableTableNode(fastBase()));
      base.moreRows(XTable.EOT);
      assertEquals(ROWS + 1, base.getRowCount());
      return base;
   }

   private static DefaultTableLens fastBase() {
      DefaultTableLens base = new DefaultTableLens(ROWS + 1, 2);
      base.setHeaderRowCount(1);
      base.setObject(0, 0, "id");
      base.setObject(0, 1, "k");

      for(int i = 1; i <= ROWS; i++) {
         base.setObject(i, 0, i);
         base.setObject(i, 1, i % 2);
      }

      return base;
   }

   /**
    * A base whose data is all there, but which the join scans slowly: each read on another
    * thread than the one that built it (the join worker) takes 20 ms.
    */
   private static final class SlowBase extends DefaultTableLens {
      SlowBase() {
         super(fastBase());
         owner = Thread.currentThread();
      }

      @Override
      public Object getObject(int r, int c) {
         if(Thread.currentThread() != owner && r > 0) {
            started.countDown();

            try {
               Thread.sleep(20);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.getObject(r, c);
      }

      private final Thread owner;
      final CountDownLatch started = new CountDownLatch(1);
   }
}
