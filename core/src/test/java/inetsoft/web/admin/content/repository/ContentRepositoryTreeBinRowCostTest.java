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
package inetsoft.web.admin.content.repository;

import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.IndexedStorage;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * Bug #82406 round 2: per-row constant of the Recycle Bin rows in the EM repository tree.
 * Call-count based, no timing. The private builders are driven by reflection so the test does
 * not need the other collaborators getRootNodes() wires up.
 */
@Tag("core")
class ContentRepositoryTreeBinRowCostTest {
   private final RecycleBin recycleBin = mock(RecycleBin.class);

   private ContentRepositoryTreeService service() {
      return new ContentRepositoryTreeService(
         mock(SecurityProvider.class), mock(XRepository.class),
         mock(ResourcePermissionService.class), mock(RepletRegistryService.class),
         mock(ScheduleTaskFolderService.class), mock(MVManager.class),
         mock(SecurityEngine.class), mock(ScheduleManager.class),
         mock(DataSourceRegistry.class), mock(DashboardManager.class),
         mock(IndexedStorage.class), mock(DashboardRegistryManager.class),
         mock(LibManagerProvider.class), recycleBin, mock(RepletRegistryManager.class));
   }

   private static Map<String, RecycleBin.Entry> binEntries(int n) {
      Map<String, RecycleBin.Entry> map = new HashMap<>();

      for(int i = 0; i < n; i++) {
         String path = "Recycle Bin/sheet" + i;
         RecycleBin.Entry e = new RecycleBin.Entry();
         e.setPath(path);
         e.setName("sheet" + i);
         e.setType(1);
         map.put(path, e);
      }

      return map;
   }

   private static AssetEntry[] assetEntries(int n) {
      AssetEntry[] entries = new AssetEntry[n + 1];

      for(int i = 0; i < n; i++) {
         entries[i] = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                     "Recycle Bin/sheet" + i, null);
      }

      // a row that is not in the bin map must still be skipped
      entries[n] = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                  "Recycle Bin/not-in-bin", null);
      return entries;
   }

   @SuppressWarnings("unchecked")
   private List<ContentRepositoryTreeNode> buildAssetRows(
      int n, ArgumentCaptor<AssetEntry> parent) throws Exception
   {
      AssetRepository repository = mock(AssetRepository.class);
      when(repository.getEntries(parent.capture(), any(), any(), any()))
         .thenReturn(assetEntries(n));
      Principal principal = mock(Principal.class);
      AssetEntry.Selector selector = new AssetEntry.Selector(AssetEntry.Type.WORKSHEET,
                                                             AssetEntry.Type.FOLDER);

      try(MockedStatic<AssetUtil> util = mockStatic(AssetUtil.class)) {
         util.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);
         return (List<ContentRepositoryTreeNode>) ReflectionTestUtils.invokeMethod(
            service(), "getRecycleNodeFromAssets", principal, binEntries(n), selector,
            AssetEntry.Type.FOLDER);
      }
   }

   @Test
   void binRowsAreBuiltFromOneMapWithoutPerRowStorageReads() throws Exception {
      for(int n : new int[] { 10, 200 }) {
         clearInvocations(recycleBin);
         List<ContentRepositoryTreeNode> nodes =
            buildAssetRows(n, ArgumentCaptor.forClass(AssetEntry.class));

         assertEquals(n, nodes.size(), "row missing from the bin map must be skipped");
         verify(recycleBin, never()).getEntry(any());
         verify(recycleBin, never()).getEntries();
      }
   }

   @Test
   void binListingSkipsPerSheetLoadViaOneoffProperty() throws Exception {
      ArgumentCaptor<AssetEntry> parent = ArgumentCaptor.forClass(AssetEntry.class);
      buildAssetRows(3, parent);

      assertEquals("true", parent.getValue().getProperty("__oneoff__"));
   }

   @SuppressWarnings("unchecked")
   private <T> T runInLoaderTask(Supplier<T> supplier) throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         Supplier<List<ContentRepositoryTreeNode>> wrapped = () -> {
            // smuggle the result out through a node-less list; only the side effects matter
            result.set(supplier.get());
            return Collections.emptyList();
         };
         CompletableFuture<?> future = (CompletableFuture<?>) ReflectionTestUtils.invokeMethod(
            service(), "getNodesCompletableFuture", mock(Principal.class), "failed",
            wrapped, executor);
         future.get(10, TimeUnit.SECONDS);
         return (T) result.get();
      }
      finally {
         executor.shutdownNow();
      }
   }

   private final AtomicReference<Object> result = new AtomicReference<>();

   @Test
   void loaderTaskRunsInItsOwnPermissionScopeSoIdentityReadsAreMemoized() throws Exception {
      Object owner = new Object();

      for(int n : new int[] { 10, 200 }) {
         AtomicInteger providerCalls = new AtomicInteger();
         Boolean open = runInLoaderTask(() -> {
            for(int i = 0; i < n; i++) {
               PermissionRequestScope.memo(owner, "getRole", "r", () -> {
                  providerCalls.incrementAndGet();
                  return "role";
               });
            }

            return PermissionRequestScope.isOpen();
         });

         assertTrue(open);
         assertEquals(1, providerCalls.get(), "provider reads must not depend on row count N=" + n);
      }

      assertFalse(PermissionRequestScope.isOpen(), "scope must not leak onto the test thread");
   }

   @Test
   void loaderTaskScopeIsClosedAfterTheTaskEvenWhenTheSupplierFails() throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         CompletableFuture<?> failing = (CompletableFuture<?>) ReflectionTestUtils.invokeMethod(
            service(), "getNodesCompletableFuture", mock(Principal.class), "failed",
            (Supplier<List<ContentRepositoryTreeNode>>) () -> {
               throw new IllegalStateException("boom");
            }, executor);
         assertThrows(ExecutionException.class, () -> failing.get(10, TimeUnit.SECONDS));

         assertFalse(executor.submit(PermissionRequestScope::isOpen).get(),
                     "the worker thread must not keep the scope after the task");
      }
      finally {
         executor.shutdownNow();
      }
   }
}
