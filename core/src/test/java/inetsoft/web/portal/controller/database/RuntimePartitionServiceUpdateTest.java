/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.portal.controller.database;

import inetsoft.sree.internal.cluster.*;
import inetsoft.test.*;
import inetsoft.uql.erm.XPartition;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import org.apache.ignite.transactions.TransactionHeuristicException;
import org.apache.ignite.transactions.TransactionTimeoutException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.cache.Cache;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78428, the update of a physical view runtime runs in a short transaction that is retried
 * a few times, then fails with a message, and can't be nested.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RuntimePartitionServiceUpdateTest {
   @SuppressWarnings({ "unchecked", "rawtypes" })
   @BeforeEach
   void setUp() {
      store = new ConcurrentHashMap<>();
      cache = mock(Cache.class);
      when(cache.get(any())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
      doAnswer(i -> { store.put(i.getArgument(0), i.getArgument(1)); return null; })
         .when(cache).put(any(), any());
      when(cache.remove(any())).thenAnswer(i -> store.remove(i.<String>getArgument(0)) != null);
      cluster = mock(Cluster.class);
      when(cluster.getCache(anyString(), anyBoolean(), any())).thenReturn((Cache) cache);
      failures = new AtomicInteger();
      failure = new TransactionTimeoutException("lock timeout");
      when(cluster.runInTransaction(anyLong(), any(), any())).thenAnswer(i -> {
         if(failures.getAndDecrement() > 0) {
            throw new DistributedTransactionException("The transaction failed", failure);
         }

         return i.<TransactionalAction<?, ?>>getArgument(2).run();
      });

      service = new RuntimePartitionService(cluster);
      service.init();
      id = service.createModel(new XPartition("v"), "ds").getId();
   }

   @Test
   void updateWritesTheMutatedRuntime() {
      long version = service.getRuntimePartition(id).getVersion();

      assertEquals("done", service.update(id, rp -> {
         rp.setGraphWidth(42);
         return "done";
      }));

      assertEquals(42, service.getRuntimePartition(id).getGraphWidth());
      assertEquals(version + 1, service.getRuntimePartition(id).getVersion());
      verify(cluster).runInTransaction(eq(RuntimePartitionService.TX_TIMEOUT), any(), any());
   }

   @Test
   void updateOfMissingRuntimeDoesNothing() {
      AtomicInteger runs = new AtomicInteger();

      assertNull(service.update("missing", rp -> runs.incrementAndGet()));
      assertNull(service.update(null, rp -> runs.incrementAndGet()));
      assertEquals(0, runs.get());
      verify(cache, never()).put(eq("missing"), any());
   }

   @Test
   void lockTimeoutIsRetried() {
      failures.set(RuntimePartitionService.MAX_ATTEMPTS - 1);
      AtomicInteger runs = new AtomicInteger();

      service.update(id, rp -> {
         rp.setGraphWidth(42);
         return runs.incrementAndGet();
      });

      assertEquals(1, runs.get());
      assertEquals(42, service.getRuntimePartition(id).getGraphWidth());
      verify(cluster, times(RuntimePartitionService.MAX_ATTEMPTS))
         .runInTransaction(anyLong(), any(), any());
   }

   @Test
   void persistentLockTimeoutFailsWithMessage() {
      failures.set(Integer.MAX_VALUE);

      MessageException ex = assertThrows(MessageException.class, () -> service.update(id, rp -> {
         rp.setGraphWidth(42);
         return null;
      }));

      assertEquals(Catalog.getCatalog().getString("data.physicalmodel.runtimeBusy"),
                   ex.getMessage());
      assertInstanceOf(DistributedTransactionException.class, ex.getCause());
      assertNotEquals(42, service.getRuntimePartition(id).getGraphWidth());
      verify(cluster, times(RuntimePartitionService.MAX_ATTEMPTS))
         .runInTransaction(anyLong(), any(), any());
   }

   @Test
   void heuristicFailureIsNotRetried() {
      failures.set(Integer.MAX_VALUE);
      failure = new TransactionHeuristicException("heuristic");

      assertThrows(MessageException.class, () -> service.update(id, rp -> null));
      verify(cluster, times(1)).runInTransaction(anyLong(), any(), any());
   }

   @Test
   void nestedUpdateIsRejected() {
      assertThrows(IllegalStateException.class, () -> service.update(id, rp ->
         service.update(id, inner -> null)));

      // the thread can update again
      service.update(id, rp -> {
         rp.setGraphWidth(42);
         return null;
      });
      assertEquals(42, service.getRuntimePartition(id).getGraphWidth());
   }

   @Test
   void closeLocksOriginBeforePaneInOneTransaction() throws Exception {
      String pane = service.openNewRuntimePartition(id);
      clearInvocations(cache, cluster);

      service.closeRuntimePartition(id, pane, true, rp -> rp.setGraphWidth(42));

      InOrder order = inOrder(cache);
      order.verify(cache).get(id);
      order.verify(cache).get(pane);
      order.verify(cache).put(eq(id), any());
      order.verify(cache).remove(pane);
      verify(cluster, times(1)).runInTransaction(anyLong(), any(), any());
      assertEquals(42, service.getRuntimePartition(id).getGraphWidth());
      assertEquals(id, service.getRuntimePartition(id).getId());
      assertNull(service.getRuntimePartition(pane));
   }

   private Map<String, RuntimePartitionService.RuntimeXPartition> store;
   private Cache<String, RuntimePartitionService.RuntimeXPartition> cache;
   private Cluster cluster;
   private AtomicInteger failures;
   private RuntimeException failure;
   private RuntimePartitionService service;
   private String id;
}
