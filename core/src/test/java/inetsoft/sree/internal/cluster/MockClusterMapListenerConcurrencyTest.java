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
package inetsoft.sree.internal.cluster;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production Ignite registers map listeners in a thread-safe registry, so callers are free to
 * add and remove listeners from any thread. MockCluster stands in for Ignite in every core test
 * (surefire sets inetsoft.sree.internal.cluster.implementation to it), so it has to offer the
 * same guarantee -- otherwise unrelated tests that legitimately exercise concurrency fail with
 * an artifact of the mock rather than a real defect.
 */
@Tag("core")
class MockClusterMapListenerConcurrencyTest {
   /**
    * Two threads closing the same KeyValueStorage each call removeReplicatedMapListener() with
    * the same listener instance. Against a plain ArrayList both threads can locate the element
    * and then both run fastRemove(), driving size to -1 and throwing
    * ArrayIndexOutOfBoundsException: Index -1 -- which surfaced as an intermittent failure of
    * OrgLifecycleDependencyMigrationTest.concurrentDuplicateRename_noExceptionNoDuplicateNoLoss.
    * Removing an already-removed listener must instead be a silent no-op.
    */
   @Test
   @Timeout(60)
   void concurrentRemoveOfSameMapListener_doesNotCorruptListenerList() throws Exception {
      ExecutorService executor = Executors.newFixedThreadPool(2);

      try {
         // the race window only opens once the removal loop is JIT-compiled, so a handful of
         // iterations would not detect a regression; 5000 caught the unfixed code in 4 of 5
         // local runs (typically by iteration ~400) and still completes in well under a
         // second once the list is thread-safe, at which point it can no longer fail at all
         for(int i = 0; i < 5000; i++) {
            MockCluster cluster = new MockCluster();
            MapChangeListener<String, String> listener = new NoOpMapChangeListener();
            cluster.addMapListener(MAP_NAME, listener);

            CyclicBarrier barrier = new CyclicBarrier(2);
            Callable<Void> remove = () -> {
               barrier.await(10, TimeUnit.SECONDS);
               cluster.removeMapListener(MAP_NAME, listener);
               return null;
            };

            for(Future<Void> future : executor.invokeAll(List.of(remove, remove))) {
               final int iteration = i;
               assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS),
                                  "removing the same map listener from two threads must not " +
                                  "throw (iteration " + iteration + ")");
            }
         }
      }
      finally {
         executor.shutdownNow();
      }
   }

   /**
    * A listener that unregisters itself while an entry event is being dispatched must not break
    * the dispatch loop -- a plain ArrayList would throw ConcurrentModificationException here.
    */
   @Test
   @Timeout(60)
   void removeMapListenerDuringDispatch_doesNotBreakIteration() {
      MockCluster cluster = new MockCluster();
      DistributedMap<String, String> map = cluster.getMap(MAP_NAME);
      MapChangeListener<String, String> second = new NoOpMapChangeListener();
      MapChangeListener<String, String> first = new NoOpMapChangeListener() {
         @Override
         public void entryAdded(EntryEvent<String, String> event) {
            cluster.removeMapListener(MAP_NAME, this);
            cluster.removeMapListener(MAP_NAME, second);
         }
      };

      cluster.addMapListener(MAP_NAME, first);
      cluster.addMapListener(MAP_NAME, second);

      assertDoesNotThrow(() -> map.put("k", "v"),
                         "a listener unregistering itself mid-dispatch must not break the " +
                         "dispatch loop");
   }

   private static class NoOpMapChangeListener implements MapChangeListener<String, String> {
      @Override
      public void entryAdded(EntryEvent<String, String> event) {
      }

      @Override
      public void entryUpdated(EntryEvent<String, String> event) {
      }

      @Override
      public void entryRemoved(EntryEvent<String, String> event) {
      }
   }

   private static final String MAP_NAME = "mockClusterListenerConcurrencyTestMap";
}
