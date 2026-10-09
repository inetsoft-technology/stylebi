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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.internal.cluster.*;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77879: the open sheets of a session are updated in a transaction that locks the session's
 * entry, instead of under an explicit cache-entry lock, and concurrent updates of one session
 * are still not lost.
 */
@Tag("core")
class RuntimeViewsheetManagerTest {
   @BeforeEach
   void setUp() {
      viewsheetService = mock(ViewsheetService.class);
      cluster = new MockCluster();
      manager = new RuntimeViewsheetManager(viewsheetService, cluster);
      manager.init();
      user = mock(XPrincipal.class);
      when(user.getSessionID()).thenReturn("session-1");
   }

   @Test
   void tracksOpenedAndClosedSheets() {
      manager.sheetOpened(user, "vs-1");
      manager.sheetOpened(user, "vs-2");
      manager.sheetClosed(user, "vs-1");

      assertEquals(Set.of("vs-2"), openSheets().get("session-1"));

      manager.sheetClosed(user, "vs-2");

      assertFalse(openSheets().containsKey("session-1"), "an empty session entry was kept");
   }

   @Test
   void sessionEndClosesItsSheets() {
      manager.sheetOpened(user, "vs-1");
      manager.sheetOpened(user, "vs-2");

      manager.sessionEnded(user);

      assertFalse(openSheets().containsKey("session-1"));
      verify(viewsheetService).affinityCallAsync(eq("vs-1"), any());
      verify(viewsheetService).affinityCallAsync(eq("vs-2"), any());
   }

   @Test
   void concurrentOpensOfOneSessionAreAllKept() throws Exception {
      int count = 20;
      ExecutorService executor = Executors.newFixedThreadPool(4);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<?>> futures = new ArrayList<>();

      try {
         for(int i = 0; i < count; i++) {
            String id = "vs-" + i;
            futures.add(executor.submit(() -> {
               start.await();
               manager.sheetOpened(user, id);
               return null;
            }));
         }

         start.countDown();

         for(Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
         }
      }
      finally {
         executor.shutdownNow();
      }

      assertEquals(count, openSheets().get("session-1").size());
   }

   /**
    * A transaction that fails, e.g. after waiting a minute for the session's lock, is logged:
    * the sheet is already open or closed, so the caller's request must not fail.
    */
   @Test
   void failedTransactionsAreLogged() {
      MockCluster failing = new MockCluster() {
         @Override
         public <T, E extends Exception> T runInTransaction(long timeout, TimeUnit unit,
                                                             TransactionalAction<T, E> action)
         {
            throw new DistributedTransactionException("The transaction timed out");
         }
      };
      RuntimeViewsheetManager failingManager =
         new RuntimeViewsheetManager(viewsheetService, failing);
      failingManager.init();

      assertDoesNotThrow(() -> failingManager.sheetOpened(user, "vs-1"));
      assertDoesNotThrow(() -> failingManager.sheetClosed(user, "vs-1"));
      assertDoesNotThrow(() -> failingManager.sessionEnded(user));
      verifyNoInteractions(viewsheetService);
   }

   private Map<String, Set<String>> openSheets() {
      return cluster.getReplicatedMap(RuntimeViewsheetManager.class.getName() + ".openSheetsMap");
   }

   private ViewsheetService viewsheetService;
   private MockCluster cluster;
   private RuntimeViewsheetManager manager;
   private XPrincipal user;
}
