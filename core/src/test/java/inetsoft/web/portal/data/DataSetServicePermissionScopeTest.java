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
package inetsoft.web.portal.data;

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.web.RecycleBin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Bug #76963 regression coverage.
 *
 * FileAuthorizationProvider keys permission grants by (ResourceType, orgID, path) only -- no
 * scope, no owner (see FileAuthorizationProviderTest). A private-scope asset and a global-scope
 * asset that share a path therefore collide on the same permission key. DataSetService's
 * rename/delete handlers used to read/move/remove that shared permission unconditionally; if the
 * entry being renamed or deleted was actually the private-scope one, the operation would
 * silently steal or wipe the same-named global entry's permission. The fix guards every such
 * mutation on `entry.getScope() == AssetRepository.GLOBAL_SCOPE`, mirroring the pre-existing
 * guard in AbstractAssetEngine.updatePermission() (community PR #4634).
 *
 * [Op: rename folder][Global]   global-scope oldEntry + renameFolder  -> setPermission(newPath)/removePermission(oldPath) called
 * [Op: rename folder][Private]  private-scope oldEntry + renameFolder -> setPermission/removePermission never called (global entry untouched)
 * [Op: delete folder][Global]   global-scope entry + deleteFolder (move to bin)      -> removePermission(oldPath)/setPermission(binPath) called
 * [Op: delete folder][Private]  private-scope entry + deleteFolder (move to bin)     -> removePermission/setPermission never called
 * [Op: purge folder][Global]    global-scope entry + deleteFolder (permanent, in bin) -> removePermission(path) called
 * [Op: purge folder][Private]   private-scope entry + deleteFolder (permanent, in bin) -> removePermission never called
 * [Op: delete worksheet][Global] global-scope entry + deleteWorksheet(force) -> removePermission(path) called
 * [Op: delete worksheet][Private] private-scope entry + deleteWorksheet(force) -> removePermission never called
 *
 * Bug #77009 extends the same guard to the recycle-bin capture and to drag-move of worksheets:
 * [Op: delete folder][Private]   private-scope entry + deleteFolder (move to bin) -> recycle entry records no permission
 * [Op: delete folder][Global]    global-scope entry + deleteFolder (move to bin)  -> recycle entry records the permission
 * [Op: trash worksheet][Private] private-scope entry + deleteWorksheet (to bin)   -> recycle entry records no permission
 * [Op: trash worksheet][Global]  global-scope entry + deleteWorksheet (to bin)    -> recycle entry records the permission
 * [Op: move worksheet][Private]  private A/ws -> private B + moveDataSet -> setPermission never called (global B/ws untouched)
 * [Op: move worksheet][Global]   global A/ws -> global B + moveDataSet   -> setPermission(B/ws) called
 * The restore side is covered by inetsoft.web.RecycleUtilsPermissionScopeTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
class DataSetServicePermissionScopeTest {
   @Mock private SecurityProvider securityProvider;
   @Mock private SecurityEngine securityEngine;
   @Mock private AssetRepository assetRepository;
   @Mock private DataSetSearchService dataSetSearchService;
   @Mock private RecycleBin recycleBin;
   @Mock private DependencyHandler dependencyHandler;
   @Mock private RenameTransformHandler renameTransformHandler;
   @Mock private Principal principal;

   private DataSetService service;

   private static final String PATH = "RT";

   @BeforeEach
   void setup() throws Exception {
      service = new DataSetService(securityProvider, securityEngine, assetRepository,
                                   dataSetSearchService, recycleBin, dependencyHandler,
                                   renameTransformHandler);
      lenient().when(principal.getName()).thenReturn("admin");
      lenient().when(assetRepository.containsEntry(any())).thenReturn(true);
   }

   // [Op: rename folder][Global]
   @Test
   void renameFolder_globalScopeEntry_migratesPermission() throws Exception {
      AssetEntry globalEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                              AssetEntry.Type.FOLDER, PATH, null);
      when(assetRepository.getEntries(any(), any(), any(), any()))
         .thenReturn(new AssetEntry[]{ globalEntry });
      when(assetRepository.getAssetEntry(any())).thenReturn(globalEntry);
      Permission permission = new Permission();
      when(securityProvider.getPermission(ResourceType.ASSET, PATH)).thenReturn(permission);

      WorksheetBrowserInfo info = worksheetFolderInfo(PATH, AssetRepository.GLOBAL_SCOPE);
      service.renameFolder(PATH, info, "RT2", AssetRepository.GLOBAL_SCOPE, principal);

      verify(securityProvider).setPermission(ResourceType.ASSET, "RT2", permission);
      verify(securityProvider).removePermission(ResourceType.ASSET, PATH);
   }

   // [Op: rename folder][Private]
   @Test
   void renameFolder_privateScopeEntry_doesNotTouchGlobalPermission() throws Exception {
      AssetEntry privateEntry = new AssetEntry(AssetRepository.USER_SCOPE,
                                               AssetEntry.Type.FOLDER, PATH,
                                               new IdentityID("alice", "host"));
      when(assetRepository.getEntries(any(), any(), any(), any()))
         .thenReturn(new AssetEntry[]{ privateEntry });
      when(assetRepository.getAssetEntry(any())).thenReturn(privateEntry);
      when(securityProvider.getPermission(ResourceType.ASSET, PATH)).thenReturn(new Permission());

      WorksheetBrowserInfo info = worksheetFolderInfo(PATH, AssetRepository.USER_SCOPE);
      service.renameFolder(PATH, info, "RT2", AssetRepository.USER_SCOPE, principal);

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
      verify(securityProvider, never()).removePermission(any(), anyString());
   }

   // [Op: delete folder][Global] -- move-to-recycle-bin branch (live path)
   @Test
   void deleteFolder_globalScopeEntry_migratesPermissionToRecycleBin() throws Exception {
      AssetEntry globalEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                              AssetEntry.Type.FOLDER, PATH, null);
      when(assetRepository.getAssetEntry(any())).thenReturn(globalEntry);
      Permission permission = new Permission();
      when(securityProvider.getPermission(ResourceType.ASSET, PATH)).thenReturn(permission);

      service.deleteFolder(PATH, PATH, AssetRepository.GLOBAL_SCOPE, principal);

      verify(securityProvider).removePermission(ResourceType.ASSET, PATH);
      verify(securityProvider).setPermission(eq(ResourceType.ASSET), anyString(), eq(permission));
   }

   // [Op: delete folder][Private] -- move-to-recycle-bin branch (live path)
   @Test
   void deleteFolder_privateScopeEntry_doesNotTouchGlobalPermission() throws Exception {
      AssetEntry privateEntry = new AssetEntry(AssetRepository.USER_SCOPE,
                                               AssetEntry.Type.FOLDER, PATH,
                                               new IdentityID("alice", "host"));
      when(assetRepository.getAssetEntry(any())).thenReturn(privateEntry);
      when(securityProvider.getPermission(ResourceType.ASSET, PATH)).thenReturn(new Permission());

      service.deleteFolder(PATH, PATH, AssetRepository.USER_SCOPE, principal);

      verify(securityProvider, never()).removePermission(any(), anyString());
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // [Op: purge folder][Global] -- permanent delete from recycle bin
   @Test
   void deleteFolder_globalScopeEntry_permanentDeleteFromBin_removesPermission() throws Exception {
      String binPath = "Recycle Bin/" + PATH;
      AssetEntry globalEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                              AssetEntry.Type.FOLDER, binPath, null);
      when(assetRepository.getAssetEntry(any())).thenReturn(globalEntry);

      service.deleteFolder(binPath, binPath, AssetRepository.GLOBAL_SCOPE, principal);

      verify(securityProvider).removePermission(ResourceType.ASSET, binPath);
   }

   // [Op: purge folder][Private] -- permanent delete from recycle bin
   @Test
   void deleteFolder_privateScopeEntry_permanentDeleteFromBin_doesNotRemovePermission() throws Exception {
      String binPath = "Recycle Bin/" + PATH;
      AssetEntry privateEntry = new AssetEntry(AssetRepository.USER_SCOPE,
                                               AssetEntry.Type.FOLDER, binPath,
                                               new IdentityID("alice", "host"));
      when(assetRepository.getAssetEntry(any())).thenReturn(privateEntry);

      service.deleteFolder(binPath, binPath, AssetRepository.USER_SCOPE, principal);

      verify(securityProvider, never()).removePermission(any(), anyString());
   }

   // [Op: delete worksheet][Global] -- force delete
   @Test
   void deleteWorksheet_globalScopeEntry_forceDelete_removesPermission() throws Exception {
      service.deleteWorksheet(PATH, AssetRepository.GLOBAL_SCOPE, principal, true);

      verify(securityProvider).removePermission(ResourceType.ASSET, PATH);
   }

   // [Op: delete worksheet][Private] -- force delete
   @Test
   void deleteWorksheet_privateScopeEntry_forceDelete_doesNotRemovePermission() throws Exception {
      service.deleteWorksheet(PATH, AssetRepository.USER_SCOPE, principal, true);

      verify(securityProvider, never()).removePermission(any(), anyString());
   }

   // [Op: delete folder][Private] -- Bug #77009: the global "RT" permission is not captured
   @Test
   void deleteFolder_privateScopeEntry_recordsNoPermissionInRecycleBin() throws Exception {
      IdentityID alice = new IdentityID("alice", "host");
      AssetEntry privateEntry = new AssetEntry(AssetRepository.USER_SCOPE,
                                               AssetEntry.Type.FOLDER, PATH, alice);
      when(assetRepository.getAssetEntry(any())).thenReturn(privateEntry);
      lenient().when(principal.getName()).thenReturn(alice.convertToKey());
      lenient().when(securityProvider.getPermission(ResourceType.ASSET, PATH))
         .thenReturn(new Permission());

      service.deleteFolder(PATH, PATH, AssetRepository.USER_SCOPE, principal);

      verify(recycleBin).addEntry(anyString(), eq(PATH), eq(PATH), isNull(),
                                  eq(RepositoryEntry.WORKSHEET_FOLDER),
                                  eq(AssetRepository.USER_SCOPE), eq(alice));
   }

   // [Op: delete folder][Global] -- Bug #77009 control
   @Test
   void deleteFolder_globalScopeEntry_recordsPermissionInRecycleBin() throws Exception {
      AssetEntry globalEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                              AssetEntry.Type.FOLDER, PATH, null);
      when(assetRepository.getAssetEntry(any())).thenReturn(globalEntry);
      Permission permission = new Permission();
      when(securityProvider.getPermission(ResourceType.ASSET, PATH)).thenReturn(permission);

      service.deleteFolder(PATH, PATH, AssetRepository.GLOBAL_SCOPE, principal);

      verify(recycleBin).addEntry(anyString(), eq(PATH), eq(PATH), same(permission),
                                  eq(RepositoryEntry.WORKSHEET_FOLDER),
                                  eq(AssetRepository.GLOBAL_SCOPE), isNull());
   }

   // [Op: trash worksheet][Private] -- Bug #77009: the global "A/ws" permission is not captured
   @Test
   void deleteWorksheet_privateScopeEntry_toBin_recordsNoPermission() throws Exception {
      IdentityID alice = new IdentityID("alice", "host");
      AssetEntry privateEntry = new AssetEntry(AssetRepository.USER_SCOPE,
                                               AssetEntry.Type.WORKSHEET, "A/ws", alice);
      when(assetRepository.getAssetEntry(any())).thenReturn(privateEntry);
      lenient().when(principal.getName()).thenReturn(alice.convertToKey());
      lenient().when(securityProvider.getPermission(ResourceType.ASSET, "A/ws"))
         .thenReturn(new Permission());

      service.deleteWorksheet("A/ws", AssetRepository.USER_SCOPE, principal, false);

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
      verify(securityProvider, never()).removePermission(any(), anyString());
      verify(recycleBin).addEntry(anyString(), eq("A/ws"), eq("ws"), isNull(),
                                  eq(RepositoryEntry.WORKSHEET),
                                  eq(AssetRepository.USER_SCOPE), eq(alice));
   }

   // [Op: trash worksheet][Global] -- Bug #77009 control
   @Test
   void deleteWorksheet_globalScopeEntry_toBin_recordsPermission() throws Exception {
      AssetEntry globalEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                              AssetEntry.Type.WORKSHEET, "A/ws", null);
      when(assetRepository.getAssetEntry(any())).thenReturn(globalEntry);
      Permission permission = new Permission();
      when(securityProvider.getPermission(ResourceType.ASSET, "A/ws")).thenReturn(permission);

      service.deleteWorksheet("A/ws", AssetRepository.GLOBAL_SCOPE, principal, false);

      verify(securityProvider).removePermission(ResourceType.ASSET, "A/ws");
      verify(recycleBin).addEntry(anyString(), eq("A/ws"), eq("ws"), same(permission),
                                  eq(RepositoryEntry.WORKSHEET),
                                  eq(AssetRepository.GLOBAL_SCOPE), isNull());
   }

   // [Op: move worksheet][Private] -- Bug #77009: global "B/ws" must not be overwritten/cleared
   @Test
   void moveDataSet_privateToPrivate_doesNotTouchGlobalPermission() throws Exception {
      IdentityID alice = new IdentityID("alice", "host");
      AssetEntry privateEntry = new AssetEntry(AssetRepository.USER_SCOPE,
                                               AssetEntry.Type.WORKSHEET, "A/ws", alice);
      when(assetRepository.getAssetEntry(any())).thenReturn(privateEntry);
      lenient().when(principal.getName()).thenReturn(alice.convertToKey());
      lenient().when(securityEngine.getPermission(ResourceType.ASSET, "A/ws")).thenReturn(null);
      MoveCommand command = mock(MoveCommand.class);
      when(command.getPath()).thenReturn("B");

      service.moveDataSet("A/ws", command, AssetRepository.USER_SCOPE,
                          AssetRepository.USER_SCOPE, principal, null);

      verify(assetRepository).changeSheet(any(), argThat(e -> "B/ws".equals(e.getPath())),
                                          any(), anyBoolean());
      verify(securityEngine, never()).setPermission(any(), anyString(), any());
      verify(securityEngine, never()).removePermission(any(), anyString());
   }

   // [Op: move worksheet][Global] -- Bug #77009 control
   @Test
   void moveDataSet_globalToGlobal_movesPermission() throws Exception {
      AssetEntry globalEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                              AssetEntry.Type.WORKSHEET, "A/ws", null);
      when(assetRepository.getAssetEntry(any())).thenReturn(globalEntry);
      Permission permission = new Permission();
      when(securityEngine.getPermission(ResourceType.ASSET, "A/ws")).thenReturn(permission);
      MoveCommand command = mock(MoveCommand.class);
      when(command.getPath()).thenReturn("B");

      service.moveDataSet("A/ws", command, AssetRepository.GLOBAL_SCOPE,
                          AssetRepository.GLOBAL_SCOPE, principal, null);

      verify(securityEngine).setPermission(ResourceType.ASSET, "B/ws", permission);
   }

   private static WorksheetBrowserInfo worksheetFolderInfo(String path, int scope) {
      return WorksheetBrowserInfo.builder()
         .name(path)
         .path(path)
         .type(AssetEntry.Type.FOLDER)
         .scope(scope)
         .id("")
         .createdDate(0)
         .createdDateLabel("")
         .modifiedDate(0)
         .modifiedDateLabel("")
         .editable(true)
         .deletable(true)
         .materialized(false)
         .canMaterialize(false)
         .hasSubFolder(false)
         .workSheetType(0)
         .build();
   }
}
