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
package inetsoft.web;

import inetsoft.mv.SharedMVUtil;
import inetsoft.sree.*;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77009 regression coverage.
 *
 * FileAuthorizationProvider keys permission grants by (ResourceType, orgID, path) only, so a
 * private asset "RT" and a global asset "RT" share one permission key. Moving a private asset to
 * the recycle bin used to capture the global asset's (or its nearest ancestor's) permission, and
 * restoring it wrote that stale value back onto the global key, overwriting or clearing the
 * global asset's permission. The fix only captures/writes back path-keyed permissions when the
 * entry is GLOBAL_SCOPE (same guard as AbstractAssetEngine.updatePermission()).
 *
 * [Op: restore WS folder][Private]  -> setPermission(ASSET, "RT") never called; bin key removed
 * [Op: restore WS folder][Global]   -> setPermission(ASSET, "RT", saved) called
 * [Op: restore worksheet][Private]  -> setPermission(ASSET, "A/ws") never called
 * [Op: restore viewsheet][Private]  -> setPermission(REPORT, "X/vs") never called
 * [Op: restore viewsheet][Global]   -> setPermission(REPORT, "X/vs", saved) called
 * [Op: overwrite-restore children][Private] -> global "RT/ws1" permission not cleared
 * [Op: overwrite-restore children][Global]  -> "RT/ws1" permission cleared (em.archiveSecurity.permissionClear)
 * [Op: trash WS folder][Private]    -> no ancestor permission captured, bin key not written
 * [Op: trash WS folder][Global]     -> ancestor permission captured and written to bin key
 * [Op: trash viewsheet][Private]    -> no permission captured, bin key not written
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class RecycleUtilsPermissionScopeTest {
   @Mock private SecurityEngine securityEngine;
   @Mock private AssetRepository assetRepository;
   @Mock private RecycleBin recycleBin;
   @Mock private Principal principal;

   private final IdentityID alice = new IdentityID("alice", "host");
   private static final String BIN_PATH = "Recycle Bin/abc";

   @BeforeEach
   void setup() throws Exception {
      when(principal.getName()).thenReturn(alice.convertToKey());
      when(assetRepository.containsEntry(any())).thenReturn(true);
   }

   // [Op: restore WS folder][Private]
   @Test
   void restoreWSFolder_privateScope_doesNotWriteGlobalPermission() throws Exception {
      AssetEntry binEntry = folder(AssetRepository.USER_SCOPE, BIN_PATH, alice);
      stubFolderLookup(binEntry);
      RecycleBin.Entry rEntry = recycleEntry(BIN_PATH, "RT", AssetRepository.USER_SCOPE, alice,
                                             RepositoryEntry.WORKSHEET_FOLDER, new Permission());

      withStatics(false, () -> RecycleUtils.restoreWSFolder(rEntry, false, principal, recycleBin));

      verify(assetRepository).changeFolder(any(), argThat(e -> "RT".equals(e.getPath())),
                                           any(), anyBoolean());
      verify(securityEngine).removePermission(ResourceType.ASSET, BIN_PATH);
      verify(securityEngine, never()).setPermission(any(), eq("RT"), any());
   }

   // [Op: restore WS folder][Global]
   @Test
   void restoreWSFolder_globalScope_writesSavedPermission() throws Exception {
      AssetEntry binEntry = folder(AssetRepository.GLOBAL_SCOPE, BIN_PATH, null);
      stubFolderLookup(binEntry);
      Permission saved = new Permission();
      RecycleBin.Entry rEntry = recycleEntry(BIN_PATH, "RT", AssetRepository.GLOBAL_SCOPE, null,
                                             RepositoryEntry.WORKSHEET_FOLDER, saved);

      withStatics(false, () -> RecycleUtils.restoreWSFolder(rEntry, false, principal, recycleBin));

      verify(securityEngine).removePermission(ResourceType.ASSET, BIN_PATH);
      verify(securityEngine).setPermission(ResourceType.ASSET, "RT", saved);
   }

   // [Op: restore worksheet][Private]
   @Test
   void restoreSheet_privateWorksheet_doesNotWriteGlobalPermission() throws Exception {
      AssetEntry binWs = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                                        BIN_PATH, alice);
      when(assetRepository.getAssetEntry(any())).thenReturn(binWs);
      RecycleBin.Entry rEntry = recycleEntry(BIN_PATH, "A/ws", AssetRepository.USER_SCOPE, alice,
                                             RepositoryEntry.WORKSHEET, new Permission());

      withStatics(false, () -> RecycleUtils.restoreSheet(rEntry, false, principal, recycleBin));

      verify(assetRepository).changeSheet(any(), argThat(e -> "A/ws".equals(e.getPath())),
                                          any(), anyBoolean());
      verify(securityEngine, never()).setPermission(any(), eq("A/ws"), any());
   }

   // [Op: restore viewsheet][Private]
   @Test
   void restoreSheet_privateViewsheet_doesNotWriteGlobalPermission() throws Exception {
      AssetEntry binVs = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        BIN_PATH, alice);
      when(assetRepository.getAssetEntry(any())).thenReturn(binVs);
      RecycleBin.Entry rEntry = recycleEntry(BIN_PATH, "My Dashboards/X/vs",
                                             AssetRepository.USER_SCOPE, alice,
                                             RepositoryEntry.VIEWSHEET, new Permission());

      withStatics(false, () -> RecycleUtils.restoreSheet(rEntry, false, principal, recycleBin));

      verify(securityEngine, never()).setPermission(any(), eq("X/vs"), any());
   }

   // [Op: restore viewsheet][Global]
   @Test
   void restoreSheet_globalViewsheet_writesSavedPermission() throws Exception {
      AssetEntry binVs = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        BIN_PATH, null);
      when(assetRepository.getAssetEntry(any())).thenReturn(binVs);
      Permission saved = new Permission();
      RecycleBin.Entry rEntry = recycleEntry(BIN_PATH, "X/vs", AssetRepository.GLOBAL_SCOPE, null,
                                             RepositoryEntry.VIEWSHEET, saved);

      withStatics(false, () -> RecycleUtils.restoreSheet(rEntry, false, principal, recycleBin));

      verify(securityEngine).setPermission(ResourceType.REPORT, "X/vs", saved);
   }

   // [Op: overwrite-restore children][Private]
   @Test
   void restoreWSChildren_privateOverwrite_doesNotClearGlobalChildPermission() throws Exception {
      AssetEntry child = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                                        BIN_PATH + "/ws1", alice);
      when(assetRepository.getAssetEntry(any())).thenReturn(child);
      RecycleBin.Entry rEntry = recycleEntry(BIN_PATH, "RT", AssetRepository.USER_SCOPE, alice,
                                             RepositoryEntry.WORKSHEET_FOLDER, null);

      withStatics(true, () -> RecycleUtils.restoreWSChildren(
         new AssetEntry[]{ child }, rEntry, true, principal, recycleBin));

      verify(securityEngine, never()).setPermission(any(), eq("RT/ws1"), any());
      verify(securityEngine, never()).removePermission(any(), eq("RT/ws1"));
   }

   // [Op: overwrite-restore children][Global] -- documented behaviour, unchanged
   @Test
   void restoreWSChildren_globalOverwrite_clearsChildPermission() throws Exception {
      AssetEntry child = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        BIN_PATH + "/ws1", null);
      when(assetRepository.getAssetEntry(any())).thenReturn(child);
      RecycleBin.Entry rEntry = recycleEntry(BIN_PATH, "RT", AssetRepository.GLOBAL_SCOPE, null,
                                             RepositoryEntry.WORKSHEET_FOLDER, null);

      withStatics(true, () -> RecycleUtils.restoreWSChildren(
         new AssetEntry[]{ child }, rEntry, true, principal, recycleBin));

      verify(securityEngine).setPermission(ResourceType.ASSET, "RT/ws1", null);
   }

   // [Op: trash WS folder][Private]
   @Test
   void moveAssetFolderToRecycleBin_privateScope_capturesNoPermission() throws Exception {
      AssetEntry priv = folder(AssetRepository.USER_SCOPE, "P/RT", alice);
      when(securityEngine.getPermission(ResourceType.ASSET, "P")).thenReturn(new Permission());

      withStatics(false,
                  () -> RecycleUtils.moveAssetFolderToRecycleBin(priv, principal, recycleBin, true));

      verify(recycleBin).addEntry(anyString(), eq("P/RT"), eq("RT"), isNull(),
                                  eq(RepositoryEntry.WORKSHEET_FOLDER),
                                  eq(AssetRepository.USER_SCOPE), eq(alice));
      verify(securityEngine, never()).setPermission(any(), anyString(), any());
   }

   // [Op: trash WS folder][Global] -- nearest-ancestor capture is unchanged for global entries
   @Test
   void moveAssetFolderToRecycleBin_globalScope_capturesAncestorPermission() throws Exception {
      AssetEntry global = folder(AssetRepository.GLOBAL_SCOPE, "P/RT", null);
      Permission parent = new Permission();
      when(securityEngine.getPermission(ResourceType.ASSET, "P")).thenReturn(parent);

      withStatics(false,
                  () -> RecycleUtils.moveAssetFolderToRecycleBin(global, principal, recycleBin, true));

      verify(recycleBin).addEntry(anyString(), eq("P/RT"), eq("RT"), same(parent),
                                  eq(RepositoryEntry.WORKSHEET_FOLDER),
                                  eq(AssetRepository.GLOBAL_SCOPE), isNull());
      verify(securityEngine).setPermission(eq(ResourceType.ASSET), startsWith("Recycle Bin/"),
                                           same(parent));
   }

   // [Op: trash viewsheet][Private]
   @Test
   void moveSheetToRecycleBin_privateViewsheet_capturesNoPermission() throws Exception {
      AssetEntry priv = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                       "X/vs", alice);
      when(securityEngine.getPermission(ResourceType.REPORT, "X/vs")).thenReturn(new Permission());

      withStatics(false,
                  () -> RecycleUtils.moveSheetToRecycleBin(priv, principal, recycleBin, true));

      verify(recycleBin).addEntry(anyString(), eq("X/vs"), eq("vs"), isNull(),
                                  eq(RepositoryEntry.VIEWSHEET),
                                  eq(AssetRepository.USER_SCOPE), eq(alice));
      verify(securityEngine, never()).setPermission(any(), anyString(), any());
   }

   private void stubFolderLookup(AssetEntry binEntry) throws Exception {
      when(assetRepository.getAssetEntry(any())).thenReturn(binEntry);
      when(assetRepository.getEntries(any(), any(), any(), any()))
         .thenReturn(new AssetEntry[]{ binEntry });
      when(assetRepository.getEntries(any(), any(), any())).thenReturn(new AssetEntry[0]);
   }

   private static AssetEntry folder(int scope, String path, IdentityID user) {
      return new AssetEntry(scope, AssetEntry.Type.FOLDER, path, user);
   }

   private static RecycleBin.Entry recycleEntry(String path, String originalPath, int scope,
                                                IdentityID user, int type, Permission permission)
   {
      RecycleBin.Entry rEntry = new RecycleBin.Entry();
      rEntry.setPath(path);
      rEntry.setOriginalPath(originalPath);
      rEntry.setOriginalScope(scope);
      rEntry.setOriginalUser(user);
      rEntry.setType(type);
      rEntry.setPermission(permission);
      return rEntry;
   }

   private void withStatics(boolean duplicated, ThrowingRunnable r) throws Exception {
      try(MockedStatic<AssetUtil> au = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
          MockedStatic<SecurityEngine> se = mockStatic(SecurityEngine.class);
          MockedStatic<RepletRegistryManager> rm = mockStatic(RepletRegistryManager.class);
          MockedStatic<SharedMVUtil> mv = mockStatic(SharedMVUtil.class))
      {
         RepletRegistryManager mgr = mock(RepletRegistryManager.class);
         rm.when(RepletRegistryManager::getInstance).thenReturn(mgr);
         when(mgr.getRegistry()).thenReturn(mock(RepletRegistry.class));
         when(mgr.getRegistry(any(IdentityID.class))).thenReturn(mock(RepletRegistry.class));
         au.when(() -> AssetUtil.getAssetRepository(anyBoolean())).thenReturn(assetRepository);
         au.when(() -> AssetUtil.isDuplicatedEntry(any(), any())).thenReturn(duplicated);
         se.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
         r.run();
      }
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }
}
