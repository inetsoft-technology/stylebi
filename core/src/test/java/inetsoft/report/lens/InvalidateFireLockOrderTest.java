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

import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.filter.*;
import inetsoft.report.internal.table.FormatTableLens2;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.util.stall.StallPolicy;
import inetsoft.util.stall.StallTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A lens whose invalidate() fired its change event while holding its own monitor deadlocked
 * with a reader of a downstream lens: the invalidating thread holds the upstream monitor and
 * waits for the downstream lens's monitor in its invalidate(), while the reader holds the
 * downstream lens's monitor (or computation lock) and waits for the upstream monitor to read
 * the next row (bug #77432). The two-level chain checks that every lens on the event path
 * fires after releasing its monitor, not only the one invalidated.
 *
 * <p>No gate: one thread loops {@code C.invalidate()}, another loops
 * {@code L.moreRows(EOT)}. Both are daemon threads with a bounded join, and a deadlock is
 * reported by the thread MX bean, so a regression fails instead of hanging the build.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
public class InvalidateFireLockOrderTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      // the production policy: a wait fails only once a cycle is confirmed
      StallPolicy.setOverride(
         new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir, 20, false));
   }

   @AfterEach
   public void tearDown() {
      StallTestSupport.clearOverride();
   }

   @Test
   public void conditionFilterOverRanking() throws Exception {
      ConditionFilter filter = acceptAll(base());
      RankingTableLens lens = ranking(filter);

      stress("CF->Ranking", filter::invalidate, lens);
      assertEquals(TOP + 1, lens.getRowCount());
   }

   @Test
   public void conditionFilterOverSortFilter() throws Exception {
      ConditionFilter filter = acceptAll(base());
      SortFilter lens = new SortFilter(filter, new int[] { 1 });

      stress("CF->SortFilter", filter::invalidate, lens);
      assertEquals(ROWS + 1, lens.getRowCount());
   }

   @Test
   public void conditionFilterOverUnion() throws Exception {
      ConditionFilter filter = acceptAll(base());
      UnionTableLens lens = new UnionTableLens(filter, base());

      stress("CF->Union", filter::invalidate, lens);
      assertEquals(ROWS + 1, lens.getRowCount());
   }

   @Test
   public void conditionFilterOverConditionFilterOverRanking() throws Exception {
      ConditionFilter top = acceptAll(base());
      ConditionFilter middle = acceptAll(top);
      RankingTableLens lens = ranking(middle);

      stress("CF->CF->Ranking", top::invalidate, lens);
      assertEquals(TOP + 1, lens.getRowCount());
   }

   @Test
   public void formatTableLensOverRanking() throws Exception {
      FormatTableLens2 format = new FormatTableLens2(base());
      RankingTableLens lens = ranking(format);

      stress("Format->Ranking", format::invalidate, lens);
      assertEquals(TOP + 1, lens.getRowCount());
   }

   @Test
   public void conditionFilterOverFormatTableLensOverRanking() throws Exception {
      ConditionFilter top = acceptAll(base());
      FormatTableLens2 format = new FormatTableLens2(top);
      RankingTableLens lens = ranking(format);

      stress("CF->Format->Ranking", top::invalidate, lens);
      assertEquals(TOP + 1, lens.getRowCount());
   }

   @Test
   public void summaryFilterOverRanking() throws Exception {
      SummaryFilter summary = summary(base());
      RankingTableLens lens = ranking(summary);

      stress("Summary->Ranking", summary::invalidate, lens);
   }

   @Test
   public void conditionFilterOverSummaryFilterOverRanking() throws Exception {
      ConditionFilter top = acceptAll(base());
      RankingTableLens lens = ranking(summary(top));

      stress("CF->Summary->Ranking", top::invalidate, lens);
   }

   @Test
   public void crossJoinOverRanking() throws Exception {
      CrossJoinTableLens join = new CrossJoinTableLens(base(60), base(50));
      RankingTableLens lens = ranking(join);

      stress("CrossJoin->Ranking", join::invalidate, lens);
      assertEquals(TOP + 1, lens.getRowCount());
   }

   @Test
   public void conditionFilterOverDistinctOverRanking() throws Exception {
      ConditionFilter top = acceptAll(base());
      RankingTableLens lens = ranking(new DistinctTableLens(top));

      stress("CF->Distinct->Ranking", top::invalidate, lens);
      assertEquals(TOP + 1, lens.getRowCount());
   }

   /**
    * The reader evaluates highlights, which holds the highlight lens's monitor while it
    * reads the ranking lens.
    */
   @Test
   public void rankingOverHighlight() throws Exception {
      RankingTableLens ranking = ranking(base());
      TableLens lens = highlight(ranking);

      stress("Ranking->Highlight", ranking::invalidate, lens, () -> {
         for(int r = 1; r < 40 && lens.moreRows(r); r++) {
            lens.getBackground(r, 1);
         }
      });
   }

   /**
    * Loop {@code invalidate} on one thread and {@code lens.moreRows(EOT)} on another for
    * {@link #DURATION_MS}, and fail if they deadlock or do not stop.
    */
   private void stress(String name, Runnable invalidate, TableLens lens) throws Exception {
      stress(name, invalidate, lens, null);
   }

   /**
    * Same as {@link #stress(String, Runnable, TableLens)}, and the reader also runs
    * {@code read} after each {@code moreRows(EOT)} if it is not null.
    */
   private void stress(String name, Runnable invalidate, TableLens lens, Runnable read)
      throws Exception
   {
      AtomicBoolean done = new AtomicBoolean();
      AtomicLong invalidations = new AtomicLong();
      AtomicLong reads = new AtomicLong();
      AtomicReference<Throwable> error = new AtomicReference<>();

      Thread invalidator = daemon(() -> {
         while(!done.get()) {
            invalidate.run();
            invalidations.incrementAndGet();
         }
      }, error, "InvalidateFireLockOrderTest-" + name + "-invalidator");
      Thread reader = daemon(() -> {
         while(!done.get()) {
            lens.moreRows(TableLens.EOT);
            lens.getRowCount();

            if(read != null) {
               read.run();
            }

            reads.incrementAndGet();
         }
      }, error, "InvalidateFireLockOrderTest-" + name + "-reader");
      Set<Long> ids = Set.of(invalidator.getId(), reader.getId());

      invalidator.start();
      reader.start();

      long end = System.currentTimeMillis() + DURATION_MS;

      try {
         while(System.currentTimeMillis() < end) {
            assertNoDeadlock(name, ids);
            Thread.sleep(50);
         }
      }
      finally {
         done.set(true);
      }

      invalidator.join(JOIN_MS);
      reader.join(JOIN_MS);
      assertNoDeadlock(name, ids);

      String summary = name + " invalidations=" + invalidations.get() + " reads=" + reads.get();
      assertFalse(invalidator.isAlive(), "invalidator did not stop: " + summary +
         stack(invalidator));
      assertFalse(reader.isAlive(), "reader did not stop: " + summary + stack(reader));
      assertNull(error.get(), summary + " error: " + error.get());
      assertTrue(invalidations.get() > 0, summary);
      // the lens settles on the unchanged base once the invalidations stop
      assertFalse(lens.moreRows(TableLens.EOT), summary);
   }

   private static void assertNoDeadlock(String name, Set<Long> ids) {
      long[] found = ManagementFactory.getThreadMXBean().findDeadlockedThreads();

      if(found == null || Arrays.stream(found).noneMatch(ids::contains)) {
         return;
      }

      StringBuilder dump = new StringBuilder(name + " deadlocked threads:");
      // only this case's threads, an earlier failed case leaves its own deadlocked threads
      long[] own = Arrays.stream(found).filter(ids::contains).toArray();

      for(ThreadInfo info : ManagementFactory.getThreadMXBean().getThreadInfo(own, true, true)) {
         dump.append('\n').append(info);
      }

      // the deadlocked daemon threads are abandoned, they only hold this test's lenses
      fail(dump.toString());
   }

   private static Thread daemon(Runnable run, AtomicReference<Throwable> error, String name) {
      Thread thread = new Thread(() -> {
         try {
            run.run();
         }
         catch(Throwable ex) {
            error.compareAndSet(null, ex);
         }
      }, name);
      thread.setDaemon(true);
      return thread;
   }

   private static String stack(Thread thread) {
      StringBuilder text = new StringBuilder();

      for(StackTraceElement element : thread.getStackTrace()) {
         text.append("\n\tat ").append(element);
      }

      return text.toString();
   }

   private static ConditionFilter acceptAll(TableLens table) {
      return new ConditionFilter(table, new ConditionGroup()) {
         @Override
         protected boolean checkCondition(int r) {
            return true;
         }
      };
   }

   /**
    * The top {@link #TOP} rows of {@code table} on the value column.
    */
   private static RankingTableLens ranking(TableLens table) {
      RankingTableLens lens = new RankingTableLens(table);
      lens.setRankingColumn(1);
      lens.setEqualityKept(false);
      lens.setRankingN(TOP);
      lens.setTopRanking(true);
      return lens;
   }

   /**
    * Sum the id column grouped by the value column.
    */
   private static SummaryFilter summary(TableLens table) {
      return new SummaryFilter(table, new int[] { 1 }, new int[] { 0 }, new SumFormula(), null);
   }

   /**
    * Highlight the value cells equal to 5.
    */
   private static TableLens highlight(TableLens table) {
      Condition condition = new Condition();
      condition.addValue(5);
      condition.setOperation(XCondition.EQUAL_TO);
      ConditionList conditions = new ConditionList();
      conditions.append(new ConditionItem(new ColumnRef(new AttributeRef("value")), condition, 0));

      ColumnHighlight highlight = new ColumnHighlight();
      highlight.setConditionGroup(conditions);
      highlight.setBackground(Color.BLUE);
      HighlightGroup group = new HighlightGroup();
      group.addHighlight("h1", highlight);

      TableDataPath path = new TableDataPath("value");
      path.setType(TableDataPath.DETAIL);
      path.setColIndex(1);
      TableHighlightAttr attr = new TableHighlightAttr();
      attr.setHighlight(path, group);

      TableLens lens = attr.createFilter(table);
      assertInstanceOf(TableHighlightAttr.HighlightTableLens.class, lens);
      return lens;
   }

   /**
    * {@link #ROWS} distinct rows of {@code id, value}.
    */
   private static DefaultTableLens base() {
      return base(ROWS);
   }

   /**
    * {@code rows} distinct rows of {@code id, value}.
    */
   private static DefaultTableLens base(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { r, (r * 7919) % 3001 };
      }

      return new DefaultTableLens(data);
   }

   @TempDir
   File dumpDir;

   private static final int ROWS = 3000;
   private static final int TOP = 100;
   private static final long DURATION_MS = 1500;
   private static final long JOIN_MS = 10000;
}
