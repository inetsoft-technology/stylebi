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
package inetsoft.report.filter;

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static inetsoft.util.stall.StallTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The waits of {@link SummaryFilter} on its worker are bounded by the lock-stall watchdog
 * (bug #76967): a stalled worker fails the reader with a {@link LockStallException}, a stall
 * of the worker itself is rethrown to the reader instead of ending the table, and a slow but
 * progressing worker is left alone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
public class SummaryFilterStallTest {
   @BeforeEach
   public void setUp() {
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
   public void stalledWorkerFailsMoreRows() throws Exception {
      gated = new GatedTable(30);
      Future<Boolean> reader = pool.submit(() -> summary(gated).moreRows(1));

      LockStallException stall = stallIn(failureOf(reader, 15));
      assertEquals("SummaryFilter.waitForRow", stall.getSite());
      assertTrue(stall.getStalledMillis() >= 1000);
      // the server's dumper writes at most one dump a minute, so an earlier stall of this JVM
      // (another test) may leave this one without its own dump path
      StallDumper.LastDump last = WaitRegistry.global().getDumper().getLastDump();
      assertNotNull(last, "the stall wrote a thread dump");

      if(stall.getDumpPath() == null) {
         assertTrue(System.nanoTime() - last.startNanos() < TimeUnit.SECONDS.toNanos(60),
                    "only the dumper's back-off window leaves the dump path off");
      }
   }

   @Test
   public void stalledWorkerFailsGetObject() throws Exception {
      gated = new GatedTable(30);
      Future<Object> reader = pool.submit(() -> summary(gated).getObject(1, 1));

      assertEquals("SummaryFilter.getObject", stallIn(failureOf(reader, 15)).getSite());
   }

   @Test
   public void workerStallIsNotTheEndOfTheTable() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      TableLens base = new FailingTable(30, 5, original);
      Future<List<List<Object>>> reader = pool.submit(() -> drain(summary(base)));

      LockStallException stall = stallIn(failureOf(reader, 15));
      assertSame(original, stall.getCause(), "the reader rethrows the worker's stall");
   }

   @Test
   public void stallRevokesTheLoanBeforeThrowing() throws Exception {
      LendableReentrantLock lock = new LendableReentrantLock();
      gated = new GatedTable(30);
      SummaryFilter summary = summary(gated);
      // start the worker on a thread holding no lock (query build time)
      pool.submit(() -> summary.getRowCount()).get(5, TimeUnit.SECONDS);

      Future<String> holder = pool.submit(() -> {
         lock.lock();
         JavaScriptEngine.pushHeldScriptLock(lock);

         try {
            summary.moreRows(1);
            return "no stall";
         }
         catch(LockStallException ex) {
            return "stall lent=" + lock.isLent() + " holds=" + lock.getHoldCount();
         }
         finally {
            JavaScriptEngine.popHeldScriptLock();
            lock.unlock();
         }
      });

      assertEquals("stall lent=false holds=1", holder.get(15, TimeUnit.SECONDS));
      assertFalse(lock.isLocked());
   }

   @Test
   public void slowButProgressingWorkerCompletes() throws Exception {
      List<List<Object>> expected =
         pool.submit(() -> drain(summary(new DefaultTableLens(data(8))))).get(15, TimeUnit.SECONDS);
      Future<List<List<Object>>> reader = pool.submit(() -> drain(summary(new SlowTable(8, 300))));

      assertEquals(expected, reader.get(15, TimeUnit.SECONDS));
   }

   @Test
   public void completedSummaryRegistersNothing() throws Exception {
      SummaryFilter summary = summary(new DefaultTableLens(data(8)));
      List<List<Object>> rows = pool.submit(() -> drain(summary)).get(15, TimeUnit.SECONDS);
      long before = WaitRegistry.global().getBeginCount();

      assertEquals(rows, drain(summary));
      assertEquals(before, WaitRegistry.global().getBeginCount());
   }

   @Test
   public void alertModeKeepsWaiting() throws Exception {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.ALERT, 1000, 200, dumpDir));
      gated = new GatedTable(30);
      Future<Boolean> reader = pool.submit(() -> summary(gated).moreRows(1));

      Thread.sleep(2500);
      assertFalse(reader.isDone(), "alert mode must not fail the wait");
      gated.open();
      assertTrue(reader.get(15, TimeUnit.SECONDS));
   }

   private static SummaryFilter summary(TableLens base) {
      return new SummaryFilter(base, new int[] { 0 }, new int[] { 1 }, new SumFormula(), null);
   }

   @TempDir
   File dumpDir;
   private ExecutorService pool;
   private GatedTable gated;
}
