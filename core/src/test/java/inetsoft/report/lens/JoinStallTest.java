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
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static inetsoft.util.stall.StallTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The join tables' reader waits in {@code XSwappableTable} are bounded by the lock-stall
 * watchdog (bug #76967): a stall throws and is never the end of the table, a join worker's
 * stall is rethrown to the reader, and rows buffered by a slow worker count as progress.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
public class JoinStallTest {
   @BeforeEach
   public void setUp() {
      resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir));
      pool = readerPool();
   }

   @AfterEach
   public void tearDown() {
      if(gated != null) {
         gated.open();
      }

      pool.shutdownNow();
      StallPolicy.setOverride(null);
   }

   @Test
   public void stalledHashJoinThrowsInsteadOfEndOfTable() throws Exception {
      gated = new GatedTable(30);
      Future<List<List<Object>>> reader =
         pool.submit(() -> drain(hash(gated, new DefaultTableLens(data(30)))));

      assertEquals("JoinTable.moreRows", stallIn(failureOf(reader, 15)).getSite());
   }

   @Test
   public void stalledMergeJoinThrowsInsteadOfEndOfTable() throws Exception {
      gated = new GatedTable(30);
      Future<List<List<Object>>> reader =
         pool.submit(() -> drain(merge(gated, new DefaultTableLens(data(30)))));

      assertEquals("JoinTable.moreRows", stallIn(failureOf(reader, 15)).getSite());
   }

   @Test
   public void hashJoinWorkerStallIsNotTheEndOfTheTable() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      TableLens left = new FailingTable(30, 5, original);
      Future<List<List<Object>>> reader =
         pool.submit(() -> drain(hash(left, new DefaultTableLens(data(30)))));

      assertSame(original, stallIn(failureOf(reader, 15)).getCause());
   }

   @Test
   public void hashJoinWrappedWorkerStallIsNotTheEndOfTheTable() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      TableLens left = new WrappingFailingTable(30, 5, original);
      Future<List<List<Object>>> reader =
         pool.submit(() -> drain(hash(left, new DefaultTableLens(data(30)))));

      assertSame(original, stallIn(failureOf(reader, 15)).getCause());
   }

   @Test
   public void mergeJoinBaseStallIsNotTheEndOfTheTable() throws Exception {
      // the merge join sorts its bases with SortFilter on its worker, the stall must escape it
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      TableLens left = new FailingTable(30, 5, original);
      Future<List<List<Object>>> reader =
         pool.submit(() -> drain(merge(left, new DefaultTableLens(data(30)))));

      assertSame(original, stallIn(failureOf(reader, 15)).getCause());
   }

   @Test
   public void mergeJoinWrappedBaseStallIsNotTheEndOfTheTable() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      TableLens left = new WrappingFailingTable(30, 5, original);
      Future<List<List<Object>>> reader =
         pool.submit(() -> drain(merge(left, new DefaultTableLens(data(30)))));

      assertSame(original, stallIn(failureOf(reader, 15)).getCause());
   }

   @Test
   public void rowCountOfAStalledJoinThrows() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      // built on a reader, the base fails only on the join's worker threads
      TableLens join = pool.submit(
         () -> hash(new FailingTable(30, 5, original), new DefaultTableLens(data(30))))
         .get(15, TimeUnit.SECONDS);
      failureOf(pool.submit(() -> drain(join)), 15);

      LockStallException ex = assertThrows(LockStallException.class, join::getRowCount);
      assertSame(original, ex.getCause());
   }

   @Test
   public void pastEndReadOfAStalledJoinThrows() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      // built on a reader, the base fails only on the join's worker threads
      HashJoinTable join = pool.submit(
         () -> (HashJoinTable) hash(new FailingTable(30, 5, original), new DefaultTableLens(data(30))))
         .get(15, TimeUnit.SECONDS);
      failureOf(pool.submit(() -> drain(join)), 15);

      LockStallException ex = assertThrows(LockStallException.class, () -> join.getObject(30, 0));
      assertSame(original, ex.getCause());
      // the rows added before the stall stay readable
      assertEquals(0, join.getObject(0, 0));
   }

   @Test
   public void pastEndReadOfACompletedJoinIsNotAStall() throws Exception {
      HashJoinTable join = pool.submit(
         () -> (HashJoinTable) hash(new DefaultTableLens(data(8)), new DefaultTableLens(data(8))))
         .get(15, TimeUnit.SECONDS);
      drain(join);

      try {
         join.getObject(30, 0);
      }
      catch(LockStallException ex) {
         fail("a completed join is not stalled: " + ex);
      }
      catch(RuntimeException ex) {
         // past the end of the table, as on main
      }
   }

   /**
    * The left rows are slow and join nothing until the last one: the rows the worker scans
    * are progress although it adds no row for longer than the 1 s limit.
    */
   @Test
   public void slowHashJoinWithoutMatchesCompletes() throws Exception {
      Object[][] right = { { "group", "value" }, { "k2", 8 } };
      List<List<Object>> expected = pool.submit(
         () -> drain(hash(new DefaultTableLens(data(8)), new DefaultTableLens(right))))
         .get(15, TimeUnit.SECONDS);
      Future<List<List<Object>>> reader =
         pool.submit(() -> drain(hash(new SlowTable(8, 300), new DefaultTableLens(right))));

      assertEquals(expected, reader.get(15, TimeUnit.SECONDS));
      assertEquals(2, expected.size(), "the header and the one joined row");
   }

   @Test
   public void slowHashJoinWithBufferedRowsCompletes() throws Exception {
      List<List<Object>> expected = sorted(pool.submit(
         () -> drain(hash(new DefaultTableLens(data(8)), new DefaultTableLens(data(8)))))
         .get(15, TimeUnit.SECONDS));
      Future<List<List<Object>>> reader =
         pool.submit(() -> drain(hash(new SlowTable(8, 300), new DefaultTableLens(data(8)))));

      assertEquals(expected, sorted(reader.get(15, TimeUnit.SECONDS)));
   }

   private static TableLens hash(TableLens left, TableLens right) {
      return new HashJoinTable(left, right, new int[] { 1 }, new int[] { 1 },
                               JoinTableLens.INNER_JOIN, true, Integer.MAX_VALUE);
   }

   private static TableLens merge(TableLens left, TableLens right) {
      return new MergeJoinTable(left, right, new int[] { 1 }, new int[] { 1 },
                                JoinTableLens.INNER_JOIN, true, Integer.MAX_VALUE);
   }

   private static List<List<Object>> sorted(List<List<Object>> rows) {
      rows.sort(Comparator.comparing(Object::toString));
      return rows;
   }

   /**
    * A table whose rows from {@code failAt} on fail on non-reader threads with the stall
    * wrapped in another exception, as a base table may wrap a nested stall.
    */
   private static final class WrappingFailingTable extends DefaultTableLens {
      WrappingFailingTable(int rows, int failAt, LockStallException failure) {
         super(data(rows));
         this.failAt = failAt;
         this.failure = failure;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= failAt && !isReader()) {
            throw new RuntimeException("wrapped", failure);
         }

         return super.moreRows(row);
      }

      private final int failAt;
      private final LockStallException failure;
   }

   @TempDir
   File dumpDir;
   private ExecutorService pool;
   private GatedTable gated;
}
