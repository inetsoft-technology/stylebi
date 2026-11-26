/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

/*
 * Test strategy
 *
 * RepositoryRecycleBinController has real in-controller logic in three areas:
 *
 *   clearRecycleBin() — permission guard via securityEngine.getSecurityProvider().checkPermission();
 *     throws SecurityException when denied. When granted, drains indexedStorage (asset repository
 *     entries) and recycleBin.getEntries(), then calls AutoSaveUtils.deleteRecycledAutoSaveFiles().
 *
 *   deleteRecycleBinFolder() — for each table item, calls SUtil.isMyDashboard(originalPath) to
 *     decide whether to prepend Tool.MY_DASHBOARD to the path before looking up the recycle bin
 *     entry; then delegates to repositoryObjectService.deleteNodes().
 *
 *   restoreNode() — looks up the entry; returns null when absent; dispatches to
 *     RecycleUtils.restoreSheet() for sheets and RecycleUtils.restoreWSFolder() for WS folders.
 *
 * RecycleBin.Entry is a public static final class — created as a real instance via setters.
 * SUtil.isMyDashboard() and Tool.MY_DASHBOARD are pure string utilities, called for real.
 * AssetUtil.getAssetRepository() and AutoSaveUtils / RecycleUtils static methods are intercepted
 * with MockedStatic (always closed in try-with-resources).
 *
 * getRecycleBinFolderSettings() and getRepositoryRecycleBinEntryModel() call
 * AssetUtil.getAssetRepository() deeply and are covered by E2E tests.
 *
 * Coverage scope:
 *   [permission denied]           checkPermission false → SecurityException; no storage touched
 *   [permission granted]          empty storage/bin → removeEntry never called; deleteRecycledAutoSaveFiles called
 *   [non-My-Dashboard path]       isMyDashboard false → getEntry(path) with original path
 *   [My Portal Dashboards path]   isMyDashboard true → getEntry(Tool.MY_DASHBOARD + "/" + path)
 *   [entry not found]             getEntry returns null → restoreNode returns null
 *   [sheet entry]                 isSheet() true → RecycleUtils.restoreSheet() called
 *   [WS folder entry]             isWSFolder() true → RecycleUtils.restoreWSFolder() called
 */

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import inetsoft.web.*;
import inetsoft.web.admin.content.repository.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class RepositoryRecycleBinControllerTest {

   @Mock private RepositoryObjectService repositoryObjectService;
   @Mock private ContentRepositoryTreeService contentRepositoryTreeService;
   @Mock private SecurityProvider securityProvider;
   @Mock private RepletRegistryService registryManager;
   @Mock private IndexedStorage indexedStorage;
   @Mock private RecycleBin recycleBin;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider engineProvider;
   @Mock private Principal principal;

   private RepositoryRecycleBinController controller;

   @BeforeEach
   void setUp() {
      controller = new RepositoryRecycleBinController(
         repositoryObjectService, contentRepositoryTreeService, securityProvider,
         registryManager, indexedStorage, recycleBin, securityEngine);
   }

   private static RecycleBin.Entry makeEntry(String path, String name, int type) {
      RecycleBin.Entry e = new RecycleBin.Entry();
      e.setPath(path);
      e.setName(name);
      e.setType(type);
      e.setOriginalPath(path);
      e.setTimestamp(new Date());
      return e;
   }

   // -------------------------------------------------------------------------
   // clearRecycleBin()
   // -------------------------------------------------------------------------

   // [permission denied] checkPermission returns false → SecurityException; indexedStorage never touched
   @Test
   void clearRecycleBin_permissionDenied_throwsSecurityException() {
      when(securityEngine.getSecurityProvider()).thenReturn(engineProvider);
      when(engineProvider.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/content/repository", ResourceAction.ACCESS))
         .thenReturn(false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> controller.clearRecycleBin(principal));
      verify(indexedStorage, never()).getKeys(any());
   }

   // [permission granted] empty storage + empty recycle bin → no removals; deleteRecycledAutoSaveFiles called
   @Test
   void clearRecycleBin_withPermission_clearsRecycleBin() throws Exception {
      when(securityEngine.getSecurityProvider()).thenReturn(engineProvider);
      when(engineProvider.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/content/repository", ResourceAction.ACCESS))
         .thenReturn(true);
      when(indexedStorage.getKeys(any())).thenReturn(Set.of());
      when(recycleBin.getEntries()).thenReturn(List.of());

      try(MockedStatic<AssetUtil> assetUtilMock = mockStatic(AssetUtil.class, withSettings().lenient());
          MockedStatic<AutoSaveUtils> autoSaveMock = mockStatic(AutoSaveUtils.class, withSettings().lenient()))
      {
         assetUtilMock.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(null);

         controller.clearRecycleBin(principal);

         autoSaveMock.verify(() -> AutoSaveUtils.deleteRecycledAutoSaveFiles(principal));
         verify(recycleBin, never()).removeEntry(any());
      }
   }

   // [repository folder] registry folder removed and saved before the guarded repository.removeFolder
   @Test
   void clearRecycleBin_repositoryFolder_removesRegistryFolderFirst() throws Exception {
      AssetEntry folder = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "Recycle Bin", null);
      AssetRepository repository = mock(AssetRepository.class);
      RepletRegistry registry = mock(RepletRegistry.class);
      RepletRegistryManager manager = mock(RepletRegistryManager.class);
      grantClear(folder);
      when(manager.getRegistry()).thenReturn(registry);
      when(registry.removeFolder("Recycle Bin")).thenReturn(true);

      try(MockedStatic<AssetUtil> assetUtilMock = mockStatic(AssetUtil.class, withSettings().lenient());
          MockedStatic<AutoSaveUtils> autoSave = mockStatic(AutoSaveUtils.class, withSettings().lenient());
          MockedStatic<RepletRegistryManager> mgr = mockStatic(RepletRegistryManager.class))
      {
         assetUtilMock.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);
         mgr.when(RepletRegistryManager::getInstance).thenReturn(manager);

         controller.clearRecycleBin(principal);

         InOrder order = inOrder(registry, repository);
         order.verify(registry).removeFolder("Recycle Bin");
         order.verify(registry).save();
         order.verify(repository).removeFolder(any(AssetEntry.class), eq(principal), eq(true));
      }
   }

   // [user-scope repository folder] registry path is prefixed with My Dashboards
   @Test
   void clearRecycleBin_userRepositoryFolder_usesMyDashboardPrefix() throws Exception {
      IdentityID owner = new IdentityID("alice", "host-org");
      AssetEntry folder = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "Recycle Bin", owner);
      AssetRepository repository = mock(AssetRepository.class);
      RepletRegistry registry = mock(RepletRegistry.class);
      RepletRegistryManager manager = mock(RepletRegistryManager.class);
      grantClear(folder);
      when(manager.getRegistry(owner)).thenReturn(registry);
      when(registry.removeFolder(Tool.MY_DASHBOARD + "/Recycle Bin")).thenReturn(true);

      try(MockedStatic<AssetUtil> assetUtilMock = mockStatic(AssetUtil.class, withSettings().lenient());
          MockedStatic<AutoSaveUtils> autoSave = mockStatic(AutoSaveUtils.class, withSettings().lenient());
          MockedStatic<RepletRegistryManager> mgr = mockStatic(RepletRegistryManager.class))
      {
         assetUtilMock.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);
         mgr.when(RepletRegistryManager::getInstance).thenReturn(manager);

         controller.clearRecycleBin(principal);

         verify(registry).removeFolder(Tool.MY_DASHBOARD + "/Recycle Bin");
         verify(registry).save();
         verify(repository).removeFolder(any(AssetEntry.class), eq(principal), eq(true));
      }
   }

   // [worksheet folder] registry never touched
   @Test
   void clearRecycleBin_worksheetFolder_doesNotTouchRegistry() throws Exception {
      AssetEntry folder = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "Recycle Bin", null);
      AssetRepository repository = mock(AssetRepository.class);
      grantClear(folder);

      try(MockedStatic<AssetUtil> assetUtilMock = mockStatic(AssetUtil.class, withSettings().lenient());
          MockedStatic<AutoSaveUtils> autoSave = mockStatic(AutoSaveUtils.class, withSettings().lenient());
          MockedStatic<RepletRegistryManager> mgr = mockStatic(RepletRegistryManager.class))
      {
         assetUtilMock.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);

         controller.clearRecycleBin(principal);

         mgr.verifyNoInteractions();
         verify(repository).removeFolder(any(AssetEntry.class), eq(principal), eq(true));
      }
   }

   // [registry removal fails] clear stops without calling the guarded repository.removeFolder
   @Test
   void clearRecycleBin_registryRemoveFails_stopsBeforeRepositoryRemove() throws Exception {
      AssetEntry folder = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "Recycle Bin", null);
      AssetRepository repository = mock(AssetRepository.class);
      RepletRegistry registry = mock(RepletRegistry.class);
      RepletRegistryManager manager = mock(RepletRegistryManager.class);
      grantClear(folder);
      when(manager.getRegistry()).thenReturn(registry);
      when(registry.removeFolder("Recycle Bin")).thenReturn(false);

      try(MockedStatic<AssetUtil> assetUtilMock = mockStatic(AssetUtil.class, withSettings().lenient());
          MockedStatic<AutoSaveUtils> autoSave = mockStatic(AutoSaveUtils.class, withSettings().lenient());
          MockedStatic<RepletRegistryManager> mgr = mockStatic(RepletRegistryManager.class))
      {
         assetUtilMock.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);
         mgr.when(RepletRegistryManager::getInstance).thenReturn(manager);

         assertThrows(MessageException.class, () -> controller.clearRecycleBin(principal));

         verify(registry, never()).save();
         verifyNoInteractions(repository);
         verify(recycleBin, never()).removeEntry(any());
      }
   }

   private void grantClear(AssetEntry trashEntry) throws Exception {
      when(securityEngine.getSecurityProvider()).thenReturn(engineProvider);
      when(engineProvider.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/content/repository", ResourceAction.ACCESS))
         .thenReturn(true);
      when(indexedStorage.getKeys(any())).thenReturn(Set.of(trashEntry.toIdentifier()));
      lenient().when(recycleBin.getEntries()).thenReturn(List.of());
   }

   // -------------------------------------------------------------------------
   // deleteRecycleBinFolder()
   // -------------------------------------------------------------------------

   // [non-My-Dashboard path] SUtil.isMyDashboard returns false → getEntry called with original path
   @Test
   void deleteRecycleBinFolder_nonMyDashboard_usesDirectPath() {
      RecycleBin.Entry entry = makeEntry("binPath", "myEntry", RepositoryEntry.VIEWSHEET);
      RepositoryFolderRecycleBinTableModel item =
         RepositoryFolderRecycleBinTableModel.builder()
            .path("binPath")
            .originalPath("Reports")
            .type("viewsheet")
            .originalUser("alice")
            .dateDeleted("")
            .build();
      RepositoryFolderRecycleBinSettingsModel model =
         RepositoryFolderRecycleBinSettingsModel.builder().table(List.of(item)).build();
      when(recycleBin.getEntry("binPath")).thenReturn(entry);

      controller.deleteRecycleBinFolder(model, principal);

      verify(recycleBin).getEntry("binPath");
      verify(repositoryObjectService).deleteNodes(any(), eq(principal), eq(false), eq(false));
   }

   // [My Portal Dashboards path] SUtil.isMyDashboard returns true → path prepended with Tool.MY_DASHBOARD
   @Test
   void deleteRecycleBinFolder_myDashboardPath_prependsMyDashboard() {
      String expectedKey = Tool.MY_DASHBOARD + "/binPath";
      RecycleBin.Entry entry = makeEntry(expectedKey, "myEntry", RepositoryEntry.VIEWSHEET);
      RepositoryFolderRecycleBinTableModel item =
         RepositoryFolderRecycleBinTableModel.builder()
            .path("binPath")
            .originalPath("My Portal Dashboards")
            .type("viewsheet")
            .originalUser("alice")
            .dateDeleted("")
            .build();
      RepositoryFolderRecycleBinSettingsModel model =
         RepositoryFolderRecycleBinSettingsModel.builder().table(List.of(item)).build();
      when(recycleBin.getEntry(expectedKey)).thenReturn(entry);

      controller.deleteRecycleBinFolder(model, principal);

      verify(recycleBin).getEntry(expectedKey);
      verify(repositoryObjectService).deleteNodes(any(), eq(principal), eq(false), eq(false));
   }

   // -------------------------------------------------------------------------
   // restoreNode()
   // -------------------------------------------------------------------------

   // [entry not found] getEntry returns null → null returned; no restore dispatched
   @Test
   void restoreNode_entryNotFound_returnsNull() throws Exception {
      when(recycleBin.getEntry("missing")).thenReturn(null);

      assertNull(controller.restoreNode("missing", false, principal));
   }

   // [sheet entry] isSheet() true → RecycleUtils.restoreSheet called
   @Test
   void restoreNode_sheetEntry_callsRestoreSheet() throws Exception {
      RecycleBin.Entry entry = makeEntry("sheetPath", "mySheet", RepositoryEntry.VIEWSHEET);
      when(recycleBin.getEntry("sheetPath")).thenReturn(entry);

      try(MockedStatic<RecycleUtils> recycleMock = mockStatic(RecycleUtils.class, withSettings().lenient())) {
         controller.restoreNode("sheetPath", false, principal);
         recycleMock.verify(() -> RecycleUtils.restoreSheet(entry, false, principal, recycleBin));
      }
   }

   // [WS folder entry] isFolder() + isWSFolder() true → RecycleUtils.restoreWSFolder called
   @Test
   void restoreNode_wsFolderEntry_callsRestoreWSFolder() throws Exception {
      RecycleBin.Entry entry = makeEntry("wsPath", "myFolder", RepositoryEntry.WORKSHEET_FOLDER);
      when(recycleBin.getEntry("wsPath")).thenReturn(entry);

      try(MockedStatic<RecycleUtils> recycleMock = mockStatic(RecycleUtils.class, withSettings().lenient())) {
         recycleMock.when(() -> RecycleUtils.restoreWSFolder(entry, false, principal, recycleBin))
            .thenReturn(null);

         controller.restoreNode("wsPath", false, principal);

         recycleMock.verify(() -> RecycleUtils.restoreWSFolder(entry, false, principal, recycleBin));
      }
   }
}
