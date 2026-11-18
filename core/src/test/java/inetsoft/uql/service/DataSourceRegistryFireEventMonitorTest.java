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
package inetsoft.uql.service;

/*
 * Bug #79879: DataSourceRegistry.fireEvent must not hold the registry monitor while it queries the
 * indexed storage, which can block on a cluster load.
 */

import inetsoft.test.*;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DataSourceRegistryFireEventMonitorTest {
   @Test
   void fireEventDoesNotHoldMonitorWhileStorageBlocks() throws Exception {
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      IndexedStorage storage = mock(IndexedStorage.class);
      when(storage.lastModified(any(IndexedStorage.Filter.class))).thenAnswer(inv -> {
         entered.countDown();
         release.await(30, TimeUnit.SECONDS);
         return 0L;
      });
      DataSourceRegistry registry = new DataSourceRegistry(storage, null, null);
      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
         Future<?> blocked = pool.submit(
            () -> registry.fireEvent(new StorageRefreshEvent(storage, 100L)));
         assertTrue(entered.await(10, TimeUnit.SECONDS));

         Future<?> other = pool.submit(() -> {
            synchronized(registry) {
               return null;
            }
         });

         // would time out when fireEvent holds the monitor across the blocked storage query
         assertDoesNotThrow(() -> other.get(5, TimeUnit.SECONDS));
         assertFalse(blocked.isDone());
         release.countDown();
         blocked.get(10, TimeUnit.SECONDS);
      }
      finally {
         release.countDown();
         pool.shutdownNow();
      }
   }

   @Test
   void newerDatasourceTimestampStillRefreshesOnce() throws Exception {
      IndexedStorage storage = mock(IndexedStorage.class);
      when(storage.lastModified(any(IndexedStorage.Filter.class))).thenReturn(100L);
      DataSourceRegistry registry = new DataSourceRegistry(storage, null, null);
      AtomicInteger refreshed = new AtomicInteger();
      CountDownLatch notified = new CountDownLatch(1);
      registry.addRefreshedListener(e -> {
         refreshed.incrementAndGet();
         notified.countDown();
      });

      registry.fireEvent(new StorageRefreshEvent(storage, 100L));
      // the same timestamp again is not a change
      registry.fireEvent(new StorageRefreshEvent(storage, 100L));

      assertTrue(notified.await(10, TimeUnit.SECONDS));
      Thread.sleep(300);
      assertEquals(1, refreshed.get());
   }

   @Test
   void unrelatedChangeDoesNotRefresh() throws Exception {
      IndexedStorage storage = mock(IndexedStorage.class);
      // storage changed (event ts 100) but no datasource entry is newer than what is known
      when(storage.lastModified(any(IndexedStorage.Filter.class))).thenReturn(0L);
      DataSourceRegistry registry = new DataSourceRegistry(storage, null, null);
      AtomicInteger refreshed = new AtomicInteger();
      registry.addRefreshedListener(e -> refreshed.incrementAndGet());

      registry.fireEvent(new StorageRefreshEvent(storage, 100L));
      Thread.sleep(300);

      assertEquals(0, refreshed.get());
   }
}
