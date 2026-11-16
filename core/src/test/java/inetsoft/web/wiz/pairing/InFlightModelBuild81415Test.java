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
package inetsoft.web.wiz.pairing;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.SortInfo;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.web.wiz.service.RuntimeHeartbeatKeepAlive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #81415: the live build of a table's model in the worksheet broadcast is bounded, a build
 * that outlives its bound is not started again by the next edit, and the runtime heartbeat kept
 * for it ends.
 *
 * <p>Every wait is bounded, so on code without the guard a test fails instead of hanging.</p>
 */
@Tag("core")
@WizAgentTestSupport
class InFlightModelBuild81415Test {
   private final Worksheet ws = new Worksheet();
   private final RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
   private final CountDownLatch release = new CountDownLatch(1);
   private final AtomicInteger started = new AtomicInteger();
   private final long savedInterval = RuntimeHeartbeatKeepAlive.INTERVAL_MS;
   private final long savedCap = InFlightModelBuild.heartbeatCapMs;

   InFlightModelBuild81415Test() {
      when(rws.getWorksheet()).thenReturn(ws);
   }

   @AfterEach
   void tearDown() {
      release.countDown();
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = savedInterval;
      InFlightModelBuild.heartbeatCapMs = savedCap;
   }

   private TableAssembly table(String name) {
      TableAssembly t = TestWorksheets.withGroupSumAndSort(
         TestWorksheets.nonEmbeddedTableWithColumns(ws, name, "cust", "amount"), "cust", "amount");
      ws.addAssembly(t);
      return t;
   }

   private Optional<String> run(String table, long timeoutMs) throws Exception {
      return InFlightModelBuild.run(rws, table, () -> {
         started.incrementAndGet();
         release.await(20, TimeUnit.SECONDS);
         return "built";
      }, timeoutMs);
   }

   @Test
   void aBuildPastItsLimitReleasesTheCaller() {
      table("Slow");

      assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
         assertTrue(run("Slow", 100).isEmpty(), "the caller must fall back, not wait"));
   }

   @Test
   void aFinishedBuildReturnsItsModelAndDoesNotBlockTheNext() {
      table("Fast");
      release.countDown();

      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
         assertEquals(Optional.of("built"), run("Fast", 5_000));
         assertEquals(Optional.of("built"), run("Fast", 5_000));
      });
      assertEquals(2, started.get());
   }

   @Test
   void theSameDefinitionStillBuildingIsNotStartedAgain() {
      table("Slow");

      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
         assertTrue(run("Slow", 100).isEmpty());
         assertTrue(run("Slow", 100).isEmpty());
         assertTrue(run("Slow", 100).isEmpty());
      });
      assertEquals(1, started.get(), "three edits within the build's lifetime start one build");
   }

   @Test
   void aChangedDefinitionStartsAFreshBuildAndGetsItsModel() {
      TableAssembly slow = table("Slow");

      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
         assertTrue(run("Slow", 100).isEmpty());

         // The user fixed the table while the old build still runs.
         slow.setSortInfo(new SortInfo());
         release.countDown();
         assertEquals(Optional.of("built"), run("Slow", 5_000),
                      "a corrected table must not stay degraded behind the stale build");
      });
      assertEquals(2, started.get());
   }

   @Test
   void aDifferentTableIsNotSkipped() {
      table("A");
      table("B");

      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
         assertTrue(run("A", 100).isEmpty());
         assertTrue(run("B", 100).isEmpty());
      });
      assertEquals(2, started.get());
   }

   @Test
   void aFailedBuildIsThrownAndDoesNotBlockTheNext() {
      table("Bad");

      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
         assertThrows(IllegalStateException.class, () -> InFlightModelBuild.run(
            rws, "Bad", () -> {
               throw new IllegalStateException("boom");
            }, 5_000));
         release.countDown();
         assertEquals(Optional.of("built"), run("Bad", 5_000));
      });
   }

   @Test
   void theHeartbeatStopsWhenTheBuildFinishes() throws Exception {
      table("Fast");
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 20;
      release.countDown();

      assertEquals(Optional.of("built"), run("Fast", 5_000));
      assertStable(rws, 300);
   }

   @Test
   void theHeartbeatOfABuildThatNeverEndsStopsAtItsCap() throws Exception {
      table("Slow");
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 20;
      InFlightModelBuild.heartbeatCapMs = 200;

      assertTrue(run("Slow", 100).isEmpty());

      // The build is still blocked, yet the heartbeat that would pin the runtime ends.
      assertEquals(1, started.get());
      assertStable(rws, 600);
   }

   @Test
   void aSkippedBuildStartsNoHeartbeat() throws Exception {
      table("Slow");
      InFlightModelBuild.heartbeatCapMs = 100;
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 20;
      assertTrue(run("Slow", 100).isEmpty());
      assertStable(rws, 500);

      clearInvocations(rws);
      assertTrue(run("Slow", 100).isEmpty());
      Thread.sleep(200);
      verify(rws, never()).access(anyBoolean());
   }

   // The heartbeat refreshes through rws.access(false); it has stopped once the count stops moving.
   private static void assertStable(RuntimeWorksheet rws, long settleMs) throws Exception {
      Thread.sleep(settleMs);
      int before = mockingDetails(rws).getInvocations().size();
      Thread.sleep(200);
      assertEquals(before, mockingDetails(rws).getInvocations().size(),
                   "the heartbeat is still refreshing the runtime");
   }
}
