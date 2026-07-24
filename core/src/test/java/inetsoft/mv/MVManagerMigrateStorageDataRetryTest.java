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
package inetsoft.mv;

import inetsoft.mv.data.MVStorage;
import inetsoft.mv.fs.FSService;
import inetsoft.mv.fs.XBlockSystem;
import inetsoft.mv.fs.XDataNode;
import inetsoft.mv.fs.XFileSystem;
import inetsoft.mv.fs.XServerNode;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the retry loop that {@link MVManager#migrateStorageData} added around
 * {@code mvs.put(...)}: up to 3 attempts, ~500ms apart, tolerating transient
 * {@link IOException}s while the target org's storage is still initializing, and rethrowing
 * once attempts are exhausted rather than silently dropping the migrated MV definition.
 */
@Tag("core")
class MVManagerMigrateStorageDataRetryTest {
   @Test
   void succeedsOnceStorageBecomesAvailableWithinThreeAttempts() throws Exception {
      MVManager manager = newManagerWithMockedDefMap();
      Organization oorg = new Organization("orgA");
      Organization norg = new Organization("orgB");

      try(MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class);
          MockedStatic<AssetEntry> assetEntryStatic = mockStatic(AssetEntry.class);
          MockedStatic<MVStorage> mvStorageStatic = mockStatic(MVStorage.class);
          MockedStatic<FSService> fsServiceStatic = mockStatic(FSService.class))
      {
         stubAssetEntry(assetEntryStatic);
         // migrateStorageData() proceeds to migrateMVStorage() once the retry loop succeeds, so
         // its (unrelated) dependencies need to be harmless no-ops for this test.
         stubEmptyMigrateMVStorage(mvStorageStatic, fsServiceStatic);

         // fails twice with a transient IOException (storage still initializing), then
         // succeeds on the third attempt.
         orgManagerStatic.when(() -> OrganizationManager.runInOrgScope(eq("orgB"), any()))
            .thenThrow(new IOException("storage initializing"))
            .thenThrow(new IOException("storage initializing"))
            .thenAnswer(runTheCallable());

         manager.migrateStorageData(oorg, norg, true);

         orgManagerStatic.verify(() -> OrganizationManager.runInOrgScope(eq("orgB"), any()), times(3));
      }
   }

   @Test
   void rethrowsAfterExhaustingThreeAttemptsWhenStorageNeverBecomesAvailable() throws Exception {
      MVManager manager = newManagerWithMockedDefMap();
      Organization oorg = new Organization("orgA");
      Organization norg = new Organization("orgB");
      IOException persistentFailure = new IOException("storage initializing");

      try(MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class);
          MockedStatic<AssetEntry> assetEntryStatic = mockStatic(AssetEntry.class))
      {
         stubAssetEntry(assetEntryStatic);

         orgManagerStatic.when(() -> OrganizationManager.runInOrgScope(eq("orgB"), any()))
            .thenThrow(persistentFailure);

         IOException thrown = assertThrows(IOException.class,
            () -> manager.migrateStorageData(oorg, norg, true));

         assertSame(persistentFailure, thrown);
         // 3 attempts, not 4 -- the loop rethrows as soon as the 3rd attempt fails rather than
         // trying a 4th time.
         orgManagerStatic.verify(() -> OrganizationManager.runInOrgScope(eq("orgB"), any()), times(3));
      }
   }

   /** Builds a Callable-invoking answer that simply runs the lambda passed to runInOrgScope. */
   @SuppressWarnings("unchecked")
   private static Answer<Object> runTheCallable() {
      return inv -> {
         java.util.concurrent.Callable<Object> callable = inv.getArgument(1);
         return callable.call();
      };
   }

   /**
    * Creates an {@code MVManager} without running its real constructor (which would otherwise
    * construct a real, I/O-backed {@code MVDefMap}), and swaps in a stubbed {@code MVDefMap} that
    * reports exactly one MV definition to migrate.
    */
   private static MVManager newManagerWithMockedDefMap() throws Exception {
      MVManager manager = mock(MVManager.class, CALLS_REAL_METHODS);
      MVDefMap defMap = mock(MVDefMap.class);

      MVDef mvDef = mock(MVDef.class);
      when(mvDef.deepClone()).thenReturn(mvDef);
      when(mvDef.getMVName()).thenReturn("mv1_orgA");
      when(mvDef.getVsId()).thenReturn("vsId1");

      Set<String> keys = new LinkedHashSet<>();
      keys.add("key1_orgA");

      when(defMap.isEmpty()).thenReturn(false);
      when(defMap.keySet("orgA")).thenReturn(keys);
      when(defMap.get("key1_orgA", "orgA")).thenReturn(mvDef);

      Field field = MVManager.class.getDeclaredField("mvs");
      field.setAccessible(true);
      field.set(manager, defMap);

      return manager;
   }

   /** Makes {@code migrateMVStorage()} a harmless no-op by reporting zero files to migrate. */
   private static void stubEmptyMigrateMVStorage(
      MockedStatic<MVStorage> mvStorageStatic, MockedStatic<FSService> fsServiceStatic)
      throws Exception
   {
      MVStorage mvStorage = mock(MVStorage.class);
      when(mvStorage.listFiles(anyString())).thenReturn(List.of());
      mvStorageStatic.when(MVStorage::getInstance).thenReturn(mvStorage);

      XFileSystem fsys = mock(XFileSystem.class);
      XServerNode server = mock(XServerNode.class);
      when(server.getFSystem()).thenReturn(fsys);
      XDataNode dataNode = mock(XDataNode.class);
      XBlockSystem bsys = mock(XBlockSystem.class);
      when(dataNode.getBSystem()).thenReturn(bsys);

      fsServiceStatic.when(() -> FSService.getServer(anyString())).thenReturn(server);
      fsServiceStatic.when(() -> FSService.getDataNode(anyString())).thenReturn(dataNode);
   }

   /** Short-circuits {@code AssetEntry} parsing so {@code updateMVDef} is a no-op pass-through. */
   private static void stubAssetEntry(MockedStatic<AssetEntry> assetEntryStatic) {
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.cloneAssetEntry(any(Organization.class))).thenReturn(entry);
      when(entry.toIdentifier(true)).thenReturn("clonedVsId");
      assetEntryStatic.when(() -> AssetEntry.createAssetEntry(anyString())).thenReturn(entry);
   }
}
