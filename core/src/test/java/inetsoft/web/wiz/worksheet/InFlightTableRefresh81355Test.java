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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.QueryManager;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.service.RenderNotReadyException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static inetsoft.web.wiz.worksheet.InFlightTableRefresh.Policy.JOIN_SAME_DEFINITION;
import static inetsoft.web.wiz.worksheet.InFlightTableRefresh.Policy.SUPERSEDE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81355: a refresh that {@code RenderWaitSupport} timed out on keeps running; every later
 * edit used to start one more identical query. The registry keeps one per (runtime, table
 * definition).
 */
@Tag("core")
@WizAgentTestSupport
class InFlightTableRefresh81355Test {
   private final Worksheet ws = new Worksheet();
   private final RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
   private final AssetQuerySandbox box = mock(AssetQuerySandbox.class);
   private final QueryManager qmgr = mock(QueryManager.class);
   private final CountDownLatch release = new CountDownLatch(1);
   private final AtomicInteger started = new AtomicInteger();

   InFlightTableRefresh81355Test() {
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getQueryManager()).thenReturn(qmgr);
   }

   private TableAssembly slowTable(String name) {
      TableAssembly t = TestWorksheets.withGroupSumAndSort(
         TestWorksheets.nonEmbeddedTableWithColumns(ws, name, "cust", "amount"), "cust", "amount");
      ws.addAssembly(t);
      return t;
   }

   private Object blockingWork() throws Exception {
      started.incrementAndGet();
      release.await(20, TimeUnit.SECONDS);
      return "ran";
   }

   private Object call(String table, InFlightTableRefresh.Policy policy, long timeoutMs,
                       Runnable onNotRun)
      throws Exception
   {
      return InFlightTableRefresh.awaitOrRetry(
         rws, table, policy, this::blockingWork, timeoutMs, 1,
         RenderNotReadyException.Subject.TABLE, onNotRun);
   }

   @Test
   void sameDefinitionStillInFlightIsJoinedNotRestarted() throws Exception {
      slowTable("Slow");

      assertThrows(RenderNotReadyException.class, () -> call("Slow", JOIN_SAME_DEFINITION, 100, null));
      assertThrows(RenderNotReadyException.class, () -> call("Slow", JOIN_SAME_DEFINITION, 100, null));
      assertThrows(RenderNotReadyException.class, () -> call("Slow", JOIN_SAME_DEFINITION, 100, null));

      assertEquals(1, started.get(), "three edits within the query's lifetime must start one query");
      verify(qmgr, never()).cancelForChange();
      release.countDown();
   }

   @Test
   void joinerReturnsOnceTheInFlightRefreshFinishes() throws Exception {
      slowTable("Slow");
      assertThrows(RenderNotReadyException.class, () -> call("Slow", JOIN_SAME_DEFINITION, 100, null));

      new Thread(() -> {
         try {
            Thread.sleep(150);
         }
         catch(InterruptedException ignore) {
         }

         release.countDown();
      }).start();

      assertNull(call("Slow", JOIN_SAME_DEFINITION, 5_000, null));
      assertEquals(1, started.get());
   }

   @Test
   void aFinishedRefreshDoesNotBlockTheNextOne() throws Exception {
      slowTable("Slow");
      release.countDown();

      assertEquals("ran", call("Slow", JOIN_SAME_DEFINITION, 5_000, null));
      assertEquals("ran", call("Slow", JOIN_SAME_DEFINITION, 5_000, null));
      assertEquals(2, started.get());
   }

   @Test
   void changedDefinitionCancelsTheRunningRefreshAndStartsAFreshOne() throws Exception {
      TableAssembly slow = slowTable("Slow");
      assertThrows(RenderNotReadyException.class, () -> call("Slow", JOIN_SAME_DEFINITION, 100, null));

      slow.setSortInfo(new SortInfo());
      assertThrows(RenderNotReadyException.class, () -> call("Slow", JOIN_SAME_DEFINITION, 100, null));

      assertEquals(2, started.get());
      verify(qmgr, times(1)).cancelForChange();
      release.countDown();
   }

   @Test
   void supersedeAlwaysCancelsAndRestarts() throws Exception {
      slowTable("Slow");
      assertThrows(RenderNotReadyException.class, () -> call("Slow", SUPERSEDE, 100, null));
      assertThrows(RenderNotReadyException.class, () -> call("Slow", SUPERSEDE, 100, null));

      assertEquals(2, started.get());
      verify(qmgr, times(1)).cancelForChange();
      release.countDown();
   }

   @Test
   void aDifferentTableIsNotJoined() throws Exception {
      slowTable("A");
      slowTable("B");
      assertThrows(RenderNotReadyException.class, () -> call("A", JOIN_SAME_DEFINITION, 100, null));
      assertThrows(RenderNotReadyException.class, () -> call("B", JOIN_SAME_DEFINITION, 100, null));

      assertEquals(2, started.get());
      release.countDown();
   }

   @Test
   void aDependentWaitsForTheInFlightTableInsteadOfRunningItsSqlAgain() throws Exception {
      TableAssembly slow = slowTable("Slow");
      MirrorTableAssembly dependent = new MirrorTableAssembly(ws, "Dep", slow);
      ws.addAssembly(dependent);

      assertThrows(RenderNotReadyException.class, () -> call("Slow", JOIN_SAME_DEFINITION, 100, null));
      assertThrows(RenderNotReadyException.class, () -> call("Dep", JOIN_SAME_DEFINITION, 100, null));

      assertEquals(1, started.get(), "the dependent must not start a second statement for Slow");
      release.countDown();
   }

   @Test
   void onNotRunFiresWhenJoinedAndNotWhenRun() throws Exception {
      slowTable("Slow");
      AtomicInteger notRun = new AtomicInteger();

      assertThrows(RenderNotReadyException.class,
                   () -> call("Slow", JOIN_SAME_DEFINITION, 100, notRun::incrementAndGet));
      assertEquals(0, notRun.get());

      assertThrows(RenderNotReadyException.class,
                   () -> call("Slow", JOIN_SAME_DEFINITION, 100, notRun::incrementAndGet));
      assertEquals(1, notRun.get());
      release.countDown();
   }

   @Test
   void unrelatedEditKeepsTheDefinitionKeyAndAnOwnEditChangesIt() {
      TableAssembly slow = slowTable("Slow");
      TableAssembly other = slowTable("Table1");

      String before = InFlightTableRefresh.fingerprint(rws, "Slow");
      assertNotNull(before);
      assertEquals(before, InFlightTableRefresh.fingerprint(rws, "Slow"));

      other.setSortInfo(new SortInfo());
      ws.getAssembly("Table1").getInfo().setName("Table1");
      assertEquals(before, InFlightTableRefresh.fingerprint(rws, "Slow"),
                   "an edit to another table must not change this table's key");

      slow.setSortInfo(new SortInfo());
      assertNotEquals(before, InFlightTableRefresh.fingerprint(rws, "Slow"));
   }

   @Test
   void aTableWhoseDefinitionCannotBeKeyedFallsBackToTheUnguardedWait() throws Exception {
      release.countDown();

      assertEquals("ran", call("NoSuchTable", JOIN_SAME_DEFINITION, 5_000, null));
      assertEquals("ran", call("NoSuchTable", JOIN_SAME_DEFINITION, 5_000, null));
      assertEquals(2, started.get());
   }
}
