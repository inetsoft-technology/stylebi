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
package inetsoft.report.composition;

import inetsoft.sree.internal.cluster.AffinityCallable;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81431: the lock of a worksheet/viewsheet only saw the runtime sheets of its own node. The
 * lookup asks the other nodes, and must never make an open or a save fail or wait longer than its
 * time box: any trouble with the other nodes means "use the local answer", which is what the lock
 * did before.
 */
@Tag("core")
class SheetLockLookupTest {
   private static final String CACHE = "test.cache";

   private Cluster cluster;
   private SheetLockLookup lookup;
   private AssetEntry entry;

   @BeforeEach
   void setUp() {
      cluster = mock(Cluster.class);
      when(cluster.getLocalMember()).thenReturn("node1");
      when(cluster.getServerClusterNodes()).thenReturn(Set.of("node1", "node2", "node3"));
      lookup = new SheetLockLookup(cluster, CACHE, 1000L);
      entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws", null);
   }

   @AfterEach
   void tearDown() {
      lookup.close();
   }

   @Test
   void aSingleNodeClusterIsNotAsked() {
      when(cluster.getServerClusterNodes()).thenReturn(Set.of("node1"));

      assertNull(lookup.findRemote(entry, false));

      verify(cluster, never()).affinityCallAll(any(), any());
   }

   @Test
   void theSheetOpenedFirstOnAnyNodeWins() {
      returnFromNodes(holder("u3", "ws-9"), null, holder("u2", "ws-5"));

      SheetLockHolder holder = lookup.findRemote(entry, false);

      assertNotNull(holder);
      assertEquals("u2", holder.getUser());
   }

   @Test
   void noHolderWhenNoNodeHasASheetThatHoldsTheLock() {
      returnFromNodes(null, null);

      assertNull(lookup.findRemote(entry, false));
   }

   @Test
   void asksTheNodesThatHoldRuntimeSheetsWithTheEntryAndTheCallerNode() {
      returnFromNodes((SheetLockHolder) null);

      lookup.findRemote(entry, true);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<AffinityCallable<SheetLockHolder>> task = ArgumentCaptor.forClass(
         AffinityCallable.class);
      verify(cluster).affinityCallAll(eq(CACHE), task.capture());
      assertInstanceOf(SheetLockLookup.Task.class, task.getValue());
   }

   @Test
   void anErrorOfAnotherNodeIsIgnored() {
      when(cluster.affinityCallAll(any(), any())).thenThrow(new RuntimeException("node2 failed"));

      assertNull(lookup.findRemote(entry, false));
   }

   @Test
   void aFailureToReadTheNodesIsIgnored() {
      when(cluster.getServerClusterNodes()).thenThrow(new IllegalStateException("cluster closed"));

      assertNull(lookup.findRemote(entry, false));
   }

   @Test
   void aNodeThatDoesNotAnswerInTimeIsIgnored() throws Exception {
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch interrupted = new CountDownLatch(1);

      when(cluster.affinityCallAll(any(), any())).thenAnswer(inv -> {
         entered.countDown();

         try {
            // a node that is still starting: the real call would wait for minutes
            Thread.sleep(60_000L);
         }
         catch(InterruptedException ex) {
            interrupted.countDown();
            throw ex;
         }

         return List.of();
      });

      try(SheetLockLookup fast = new SheetLockLookup(cluster, CACHE, 200L)) {
         long start = System.nanoTime();

         assertNull(fast.findRemote(entry, false));

         long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
         assertTrue(entered.await(5, TimeUnit.SECONDS), "the cluster was not asked");
         assertTrue(millis < 5000L, "the lookup waited " + millis + " ms for a node");
         assertTrue(interrupted.await(5, TimeUnit.SECONDS),
                    "the abandoned call must be interrupted so it does not keep a thread");
      }
   }

   @Test
   void aFullPoolIsIgnoredWithoutWaiting() throws Exception {
      int threads = 8;
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch allBlocked = new CountDownLatch(threads);
      AtomicInteger calls = new AtomicInteger();

      when(cluster.affinityCallAll(any(), any())).thenAnswer(inv -> {
         calls.incrementAndGet();
         allBlocked.countDown();
         release.await(30, TimeUnit.SECONDS);
         return List.of();
      });

      ExecutorService callers = Executors.newFixedThreadPool(threads);

      try(SheetLockLookup patient = new SheetLockLookup(cluster, CACHE, 30_000L)) {
         for(int i = 0; i < threads; i++) {
            callers.submit(() -> patient.findRemote(entry, false));
         }

         assertTrue(allBlocked.await(10, TimeUnit.SECONDS), "the lookups did not start");

         long start = System.nanoTime();
         assertNull(patient.findRemote(entry, false));

         long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
         assertTrue(millis < 2000L, "a full pool must reject at once, took " + millis + " ms");
         assertEquals(threads, calls.get(), "the rejected lookup must not reach the cluster");
      }
      finally {
         release.countDown();
         callers.shutdownNow();
      }
   }

   @Test
   void anInterruptedCallerStopsWaitingAndKeepsItsInterruptFlag() throws Exception {
      CountDownLatch entered = new CountDownLatch(1);

      when(cluster.affinityCallAll(any(), any())).thenAnswer(inv -> {
         entered.countDown();
         Thread.sleep(60_000L);
         return List.of();
      });

      ExecutorService caller = Executors.newSingleThreadExecutor();

      try(SheetLockLookup patient = new SheetLockLookup(cluster, CACHE, 30_000L)) {
         Future<Boolean> result = caller.submit(() -> {
            SheetLockHolder holder = patient.findRemote(entry, false);
            return holder == null && Thread.currentThread().isInterrupted();
         });

         assertTrue(entered.await(10, TimeUnit.SECONDS));
         caller.shutdownNow();

         assertTrue(result.get(10, TimeUnit.SECONDS),
                    "the lookup must fail open and keep the interrupt of its thread");
      }
   }

   private void returnFromNodes(SheetLockHolder... holders) {
      when(cluster.<SheetLockHolder>affinityCallAll(any(), any())).thenReturn(
         Arrays.asList(holders));
   }

   private static SheetLockHolder holder(String user, String sheetId) {
      return new SheetLockHolder(user, sheetId);
   }
}
