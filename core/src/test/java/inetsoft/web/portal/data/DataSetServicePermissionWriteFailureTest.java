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
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.MessageException;
import inetsoft.web.RecycleBin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77798: the permission writers throw when the storage write fails. The recycle-bin moves
 * and deletes of DataSetService record or drop the recycle-bin entry before the permission
 * write, so a failed write is reported without leaving the asset in the bin with no restore
 * entry, or a bin entry for an asset that no longer exists.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class DataSetServicePermissionWriteFailureTest {
   @Mock private SecurityProvider securityProvider;
   @Mock private SecurityEngine securityEngine;
   @Mock private AssetRepository assetRepository;
   @Mock private DataSetSearchService dataSetSearchService;
   @Mock private RecycleBin recycleBin;
   @Mock private DependencyHandler dependencyHandler;
   @Mock private RenameTransformHandler renameTransformHandler;
   @Mock private Principal principal;

   private DataSetService service;

   @BeforeEach
   void setup() throws Exception {
      service = new DataSetService(securityProvider, securityEngine, assetRepository,
                                   dataSetSearchService, recycleBin, dependencyHandler,
                                   renameTransformHandler);
      lenient().when(principal.getName()).thenReturn("admin");
      lenient().when(assetRepository.containsEntry(any())).thenReturn(true);
   }

   @Test
   void trashWorksheet_permissionMoveFails_binEntryRecordedThenFailureReported() throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.WORKSHEET, "A/ws", null);
      when(assetRepository.getAssetEntry(any())).thenReturn(entry);
      Permission permission = new Permission();
      when(securityProvider.getPermission(ResourceType.ASSET, "A/ws")).thenReturn(permission);
      doThrow(new MessageException("may not have been saved"))
         .when(securityProvider).removePermission(ResourceType.ASSET, "A/ws");

      assertThrows(MessageException.class, () ->
         service.deleteWorksheet("A/ws", AssetRepository.GLOBAL_SCOPE, principal, false));

      verify(assetRepository).changeSheet(any(), any(), any(), anyBoolean());
      verify(recycleBin).addEntry(anyString(), eq("A/ws"), eq("ws"), same(permission),
                                  eq(RepositoryEntry.WORKSHEET),
                                  eq(AssetRepository.GLOBAL_SCOPE), isNull());
   }

   @Test
   void trashFolder_permissionMoveFails_binEntryRecordedThenFailureReported() throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.FOLDER, "RT", null);
      when(assetRepository.getAssetEntry(any())).thenReturn(entry);
      Permission permission = new Permission();
      when(securityProvider.getPermission(ResourceType.ASSET, "RT")).thenReturn(permission);
      doThrow(new MessageException("may not have been saved"))
         .when(securityProvider).setPermission(eq(ResourceType.ASSET), anyString(), any());

      assertThrows(MessageException.class, () ->
         service.deleteFolder("RT", "RT", AssetRepository.GLOBAL_SCOPE, principal));

      verify(assetRepository).changeFolder(any(), any(), any(), anyBoolean());
      verify(recycleBin).addEntry(anyString(), eq("RT"), eq("RT"), same(permission),
                                  eq(RepositoryEntry.WORKSHEET_FOLDER),
                                  eq(AssetRepository.GLOBAL_SCOPE), isNull());
   }

   @Test
   void purgeFolder_revokeFails_binEntryRemovedThenFailureReported() throws Exception {
      String binPath = "Recycle Bin/RT";
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.FOLDER, binPath, null);
      when(assetRepository.getAssetEntry(any())).thenReturn(entry);
      doThrow(new MessageException("may not have been saved"))
         .when(securityProvider).removePermission(ResourceType.ASSET, binPath);

      assertThrows(MessageException.class, () ->
         service.deleteFolder(binPath, binPath, AssetRepository.GLOBAL_SCOPE, principal));

      verify(assetRepository).removeFolder(any(), any(), anyBoolean());
      verify(recycleBin).removeEntry(binPath);
   }

   @Test
   void purgeWorksheet_revokeFails_binEntryRemovedThenFailureReported() throws Exception {
      String binPath = "Recycle Bin/ws";
      doThrow(new MessageException("may not have been saved"))
         .when(securityProvider).removePermission(ResourceType.ASSET, binPath);

      assertThrows(MessageException.class, () ->
         service.deleteWorksheet(binPath, AssetRepository.GLOBAL_SCOPE, principal, false));

      verify(assetRepository).removeSheet(any(), any(), anyBoolean());
      verify(recycleBin).removeEntry(binPath);
   }
}
