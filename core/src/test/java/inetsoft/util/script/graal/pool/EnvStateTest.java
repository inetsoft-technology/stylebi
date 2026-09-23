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
package inetsoft.util.script.graal.pool;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class EnvStateTest {
   @Test
   void putGetRemoveAndTombstones() {
      EnvState state = new EnvState();
      long v1 = state.put("a", 1);
      long v2 = state.put("b", 2);
      long v3 = state.remove("a");

      assertTrue(v1 < v2 && v2 < v3);
      assertNull(state.get("a"));
      assertEquals(2, state.get("b"));
      assertEquals(Map.of("b", 2), state.snapshot().vars());

      List<EnvState.Change> after = state.snapshot().after(v1);
      assertEquals(List.of("b", "a"), after.stream().map(EnvState.Change::name).toList());
      assertTrue(after.get(1).removed());
   }

   @Test
   void compactedToOneEntryPerName() {
      EnvState state = new EnvState();

      for(int i = 0; i < 100; i++) {
         state.put("x", i);
      }

      assertEquals(1, state.snapshot().changes().size());
      assertEquals(99, state.get("x"));
      assertEquals(100, state.snapshot().version());
   }

   @Test
   void nullNameOrValueIsRejectedLikeHashtable() {
      EnvState state = new EnvState();
      assertThrows(NullPointerException.class, () -> state.put("a", null));
      assertThrows(NullPointerException.class, () -> state.put(null, 1));
   }

   @Test
   void concurrentPutsAreNeverLost() throws Exception {
      EnvState state = new EnvState();
      ExecutorService pool = Executors.newFixedThreadPool(4);
      // start gate: all four writers begin together so their CAS loops contend
      CyclicBarrier start = new CyclicBarrier(4);

      try {
         List<Future<?>> futures = new ArrayList<>();

         for(int t = 0; t < 4; t++) {
            int id = t;
            futures.add(pool.submit(() -> {
               start.await(30, TimeUnit.SECONDS);

               for(int i = 0; i < 500; i++) {
                  state.put("k" + id + "_" + i, i);
               }

               return null;
            }));
         }

         for(Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
         }
      }
      finally {
         pool.shutdownNow();
      }

      assertEquals(2000, state.snapshot().vars().size());
      assertEquals(2000, state.snapshot().version());
   }

   @Test
   void snapshotIsImmutableAndUnaffectedByLaterWrites() {
      EnvState state = new EnvState();
      state.put("a", 1);
      EnvState.Snapshot before = state.snapshot();
      state.put("a", 2);
      state.remove("a");

      assertEquals(1L, before.version());
      assertEquals(Map.of("a", 1), before.vars());
      assertThrows(UnsupportedOperationException.class,
                   () -> before.changes().put("b", new EnvState.Change("b", 1, false, 9L)));
      assertEquals(3L, state.snapshot().version());
      assertTrue(state.snapshot().vars().isEmpty());
   }
}
