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
package inetsoft.report.script.formula;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Spec §6.1 (bug #76960), pool off: AssetQueryScope is reached by several threads without any
 * engine lock (a scope shared by formula lenses on different threads, and by pooled contexts),
 * so its tablemap/members must not lose entries or throw under concurrent access.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssetQueryScopeConcurrencyTest {
   @Test
   void concurrentLookupsKeepEveryTableMapEntry() throws Exception {
      AssetQueryScope scope = newScope();
      int before = map(scope, "tablemap").size();

      runConcurrently(t -> {
         for(int i = 0; i < OPS; i++) {
            scope.hasMember("id_" + t + "_" + i);
         }
      });

      assertEquals(before + THREADS * OPS, map(scope, "tablemap").size(), "lost tablemap entries");
   }

   @Test
   void concurrentWritesAndKeyReadsNeverThrow() throws Exception {
      AssetQueryScope scope = newScope();
      int before = scope.getMemberKeys().length;
      CountDownLatch writersDone = new CountDownLatch(THREADS - 1);

      runConcurrently(t -> {
         if(t == 0) {
            // read the keys for as long as the writers run; a fixed number of reads over a
            // growing map made this test quadratic and slow enough to time out under load
            do {
               scope.getMemberKeys();
            }
            while(writersDone.getCount() > 0);
         }
         else {
            for(int i = 0; i < OPS; i++) {
               scope.putMember("m_" + t + "_" + i, i);
            }

            writersDone.countDown();
         }
      });

      assertEquals(before + (THREADS - 1) * OPS, scope.getMemberKeys().length, "lost members");
   }

   @Test
   void nullMemberStaysPresent() {
      AssetQueryScope scope = newScope();
      scope.putMember("x", null);

      assertTrue(scope.hasMember("x"));
      assertNull(scope.getMember("x"));
      assertTrue(Arrays.asList(scope.getMemberKeys()).contains("x"));
      assertFalse(scope.removeMember("x"), "main returned false when the stored value was null");
      assertFalse(scope.hasMember("x"));
   }

   private static AssetQueryScope newScope() {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      doReturn(new Worksheet()).when(box).getWorksheet();
      return new AssetQueryScope(box);
   }

   private static void runConcurrently(IntConsumer body) throws Exception {
      ExecutorService pool = Executors.newFixedThreadPool(THREADS);
      CountDownLatch go = new CountDownLatch(1);
      List<Future<?>> futures = new ArrayList<>();

      try {
         for(int t = 0; t < THREADS; t++) {
            int id = t;
            futures.add(pool.submit(() -> {
               go.await();
               body.accept(id);
               return null;
            }));
         }

         go.countDown();

         for(Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
         }
      }
      finally {
         pool.shutdownNow();
      }
   }

   private static Map<?, ?> map(AssetQueryScope scope, String name) throws Exception {
      Field field = AssetQueryScope.class.getDeclaredField(name);
      field.setAccessible(true);
      return (Map<?, ?>) field.get(scope);
   }

   private static final int THREADS = 8;
   private static final int OPS = 20000;
}
