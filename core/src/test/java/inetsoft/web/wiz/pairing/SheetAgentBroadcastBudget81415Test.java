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
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.assembly.WSAssemblyModel;
import inetsoft.web.composer.ws.command.RefreshWorksheetCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.binding.service.VSBindingTreeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.MessageHeaders;

import java.security.Principal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81415: the worksheet refresh broadcast ran every table's model build, i.e. the table
 * itself, on the HTTP request thread of the edit with no limit. A table downstream of a script
 * expression column (mirror, join, ...) held the request for the script's whole duration.
 *
 * <p>The model build is replaced by a fake that blocks on a latch for the slow table, so each test
 * shows the broadcast returns within its budget with every table still in the command. Every wait
 * is bounded, so without the fix a test fails instead of hanging.</p>
 */
@Tag("core")
@WizAgentTestSupport
class SheetAgentBroadcastBudget81415Test {
   private final Worksheet ws = new Worksheet();
   private final RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
   private final CommandDispatcherService dispatcher = TestDispatchers.live();
   private final SheetAgentBroadcastService svc = new SheetAgentBroadcastService(
      dispatcher, mock(VSObjectModelFactoryService.class), mock(VSBindingTreeService.class),
      mock(CoreLifecycleService.class));
   private final Principal owner = TestPrincipals.user("alice", "host-org");
   private final CountDownLatch release = new CountDownLatch(1);
   private final Map<String, AtomicInteger> liveBuilds = new ConcurrentHashMap<>();
   private final Map<String, AtomicInteger> degradedBuilds = new ConcurrentHashMap<>();
   private final Map<String, WSAssemblyModel> live = new ConcurrentHashMap<>();
   private final Map<String, WSAssemblyModel> degraded = new ConcurrentHashMap<>();
   private final long savedTable = SheetAgentBroadcastService.TABLE_BUDGET_MS;
   private final long savedBroadcast = SheetAgentBroadcastService.BROADCAST_BUDGET_MS;

   SheetAgentBroadcastBudget81415Test() {
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getSocketSessionId()).thenReturn("stomp-1");
      when(rws.getSocketUserName()).thenReturn("alice~;~host-org");
      SheetAgentBroadcastService.TABLE_BUDGET_MS = 300;
      SheetAgentBroadcastService.BROADCAST_BUDGET_MS = 5_000;

      // "Slow*" tables block while building live; every other table builds at once. A model built
      // without executing the table (liveMetadata false) never blocks.
      svc.setModelFactory((assembly, runtime, principal, liveMetadata) -> {
         String name = assembly.getName();
         WSAssemblyModel model = mock(WSAssemblyModel.class);
         when(model.getName()).thenReturn(name);

         if(!liveMetadata) {
            degradedBuilds.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
            degraded.put(name, model);
            return model;
         }

         liveBuilds.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();

         if(name.startsWith("Slow")) {
            release.await(20, TimeUnit.SECONDS);
         }

         live.put(name, model);
         return model;
      });
   }

   @AfterEach
   void tearDown() {
      release.countDown();
      SheetAgentBroadcastService.TABLE_BUDGET_MS = savedTable;
      SheetAgentBroadcastService.BROADCAST_BUDGET_MS = savedBroadcast;
   }

   private TableAssembly table(String name) {
      TableAssembly t = TestWorksheets.withGroupSumAndSort(
         TestWorksheets.nonEmbeddedTableWithColumns(ws, name, "cust", "amount"), "cust", "amount");
      ws.addAssembly(t);
      return t;
   }

   private MirrorTableAssembly mirrorOf(String name, TableAssembly base) {
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, name, base);
      ws.addAssembly(mirror);
      return mirror;
   }

   /** Broadcasts once; returns the models of the RefreshWorksheetCommand it sent. */
   private List<WSAssemblyModel> broadcast() {
      clearInvocations(dispatcher);
      svc.broadcastRefresh(rws, SheetType.WORKSHEET, "Worksheet/foo-7", owner);
      ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
      verify(dispatcher, atLeastOnce()).convertAndSendToUser(
         eq("alice~;~host-org"), eq(CommandDispatcher.COMMANDS_TOPIC), command.capture(),
         any(MessageHeaders.class));

      for(Object sent : command.getAllValues()) {
         if(sent instanceof RefreshWorksheetCommand refresh) {
            return new ArrayList<>(refresh.assemblies());
         }
      }

      return fail("no RefreshWorksheetCommand was sent");
   }

   private static List<String> names(List<WSAssemblyModel> models) {
      return models.stream().map(WSAssemblyModel::getName).sorted().toList();
   }

   private int liveCount(String name) {
      AtomicInteger n = liveBuilds.get(name);
      return n == null ? 0 : n.get();
   }

   private int degradedCount(String name) {
      AtomicInteger n = degradedBuilds.get(name);
      return n == null ? 0 : n.get();
   }

   @Test
   void slowMirrorDoesNotHoldTheBroadcastAndEveryTableIsStillSent() {
      TableAssembly slow = table("Slow1");
      mirrorOf("Slow1Mirror", slow);
      table("Fast");

      List<WSAssemblyModel> models = assertTimeoutPreemptively(Duration.ofSeconds(10), this::broadcast);

      assertEquals(List.of("Fast", "Slow1", "Slow1Mirror"), names(models),
                   "a table that cannot build live must still be sent");
      assertSame(live.get("Fast"), models.stream()
         .filter(m -> m.getName().equals("Fast")).findFirst().orElseThrow(),
                 "a healthy table keeps its live model");
      assertEquals(1, degradedCount("Slow1"), "the slow table is sent without live metadata");
   }

   @Test
   void aJoinOverASlowTableIsBoundedToo() {
      TableAssembly slow = table("Slow1");
      TableAssembly other = table("Other");
      RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
         ws, "SlowJoin", new TableAssembly[] { slow, other }, new TableAssemblyOperator[0]);
      ws.addAssembly(join);

      List<WSAssemblyModel> models = assertTimeoutPreemptively(Duration.ofSeconds(10), this::broadcast);

      assertEquals(List.of("Other", "Slow1", "SlowJoin"), names(models));
      assertEquals(1, degradedCount("SlowJoin"));
   }

   @Test
   void aSecondEditWhileTheFirstBuildRunsStartsNoSecondBuildAndIsAlsoBounded() {
      table("Slow1");

      assertTimeoutPreemptively(Duration.ofSeconds(10), this::broadcast);
      long start = System.currentTimeMillis();
      List<WSAssemblyModel> second = assertTimeoutPreemptively(Duration.ofSeconds(10), this::broadcast);

      assertEquals(List.of("Slow1"), names(second));
      assertEquals(1, liveCount("Slow1"), "the abandoned build must not be started again");
      assertEquals(2, degradedCount("Slow1"));
      assertTrue(System.currentTimeMillis() - start < 250,
                 "the second edit must not wait for a build it knows is still running");
   }

   @Test
   void aCorrectedTableIsBuiltLiveAgainWhileTheStaleBuildStillRuns() {
      TableAssembly slow = table("Slow1");
      assertTimeoutPreemptively(Duration.ofSeconds(10), this::broadcast);

      // The user fixed the table: its definition changed, so the stale build is not waited for.
      slow.setSortInfo(new SortInfo());
      release.countDown();
      List<WSAssemblyModel> models = assertTimeoutPreemptively(Duration.ofSeconds(10), this::broadcast);

      assertEquals(2, liveCount("Slow1"));
      assertEquals(List.of("Slow1"), names(models));
   }

   @Test
   void aSlowTableDoesNotStarveTheHealthyTablesAfterIt() {
      table("Slow1");
      table("Slow2");

      for(int i = 0; i < 5; i++) {
         table("Fast" + i);
      }

      List<WSAssemblyModel> models = assertTimeoutPreemptively(Duration.ofSeconds(10), this::broadcast);

      assertEquals(7, models.size());

      for(int i = 0; i < 5; i++) {
         assertEquals(1, liveCount("Fast" + i), "Fast" + i + " must be built live");
         assertEquals(0, degradedCount("Fast" + i), "Fast" + i + " must keep its row count");
      }
   }

   @Test
   void aSpentBroadcastBudgetStillSendsTheRemainingTables() {
      SheetAgentBroadcastService.TABLE_BUDGET_MS = 300;
      SheetAgentBroadcastService.BROADCAST_BUDGET_MS = 400;
      table("Slow1");
      table("Slow2");
      table("Slow3");

      List<WSAssemblyModel> models = assertTimeoutPreemptively(Duration.ofSeconds(10), this::broadcast);

      assertEquals(List.of("Slow1", "Slow2", "Slow3"), names(models));
      assertEquals(3, degradedCount("Slow1") + degradedCount("Slow2") + degradedCount("Slow3"));
      assertTrue(liveCount("Slow1") + liveCount("Slow2") + liveCount("Slow3") <= 2,
                 "once the shared budget is spent no further live build is started");
   }

   @Test
   void fastTablesAreAllBuiltLiveAndNothingIsDegraded() {
      table("FastA");
      mirrorOf("FastB", table("FastBase"));

      List<WSAssemblyModel> models = assertTimeoutPreemptively(Duration.ofSeconds(10), this::broadcast);

      assertEquals(List.of("FastA", "FastB", "FastBase"), names(models));
      assertEquals(0, degradedCount("FastA") + degradedCount("FastB") + degradedCount("FastBase"));
   }
}
