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
package inetsoft.web.admin.file;

/*
 * Regression coverage for Bug #77391. The repository folder repair (run by the
 * repair-repository-folders endpoint and after restore-storage) looked up the schedule
 * task root folder with the hard-coded key "1^6^__NULL__^/", which has no org suffix and
 * so never matches the stored key "1^6^__NULL__^/^<org>". The lookup returned null and
 * repairScheduleTaskFolder threw an NPE on folder.getEntries(). The special case for
 * root-level schedule tasks compared against "1^6^__NULL__^" and never matched either, so
 * a stray folder was written at "1^6^__NULL__^^<org>".
 */

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.IndexedStorage;
import inetsoft.util.XMLSerializable;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class FileServiceRepairFoldersTest {
   private static final String ORG = "host-org";
   private static final String ROOT_KEY = "1^6^__NULL__^/^" + ORG;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private ContentRepositoryTreeService contentService;
   private IndexedStorage indexedStorage;
   private Map<String, XMLSerializable> store;
   private List<String> putKeys;
   private FileService service;

   @BeforeEach
   void setUp() throws Exception {
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      store = new HashMap<>();
      putKeys = new ArrayList<>();
      indexedStorage = mock(IndexedStorage.class);
      when(indexedStorage.getXMLSerializable(anyString(), any()))
         .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
      when(indexedStorage.getXMLSerializable(anyString(), any(), anyString()))
         .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
      doAnswer(inv -> {
         String key = inv.getArgument(0);
         putKeys.add(key);
         store.put(key, inv.getArgument(1));
         return null;
      }).when(indexedStorage).putXMLSerializable(anyString(), any());

      contentService = mock(ContentRepositoryTreeService.class);
      when(contentService.getParentAssetEntryMap()).thenReturn(new HashMap<>());

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[0]);

      RepletRegistryManager registryManager = mock(RepletRegistryManager.class);
      when(registryManager.getRegistry(anyString())).thenReturn(mock(RepletRegistry.class));

      service = new FileService(contentService, securityProvider, indexedStorage, registryManager);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   @Test
   void repairsScheduleTaskFoldersStoredWithOrgSuffixedKeys() throws Exception {
      AssetEntry taskA = task("admin~;~host-org:taskA");
      AssetEntry taskB = task("admin~;~host-org:taskB");
      AssetEntry sub = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, "sub", null, ORG);

      AssetFolder root = new AssetFolder();
      root.addEntry(taskA);
      root.addEntry(sub);
      store.put(ROOT_KEY, root);

      AssetFolder subFolder = new AssetFolder();
      subFolder.addEntry(taskB);
      subFolder.addEntry(taskA); // duplicate, sub folders are visited first so root loses it
      store.put(sub.toIdentifier(), subFolder);

      invokeRepair();

      assertTrue(putKeys.contains(ROOT_KEY));
      assertTrue(putKeys.contains(sub.toIdentifier()));
      assertFalse(((AssetFolder) store.get(ROOT_KEY)).containsEntry(taskA));
      assertTrue(((AssetFolder) store.get(ROOT_KEY)).containsEntry(sub));
      assertTrue(((AssetFolder) store.get(sub.toIdentifier())).containsEntry(taskA));
      assertTrue(((AssetFolder) store.get(sub.toIdentifier())).containsEntry(taskB));
      assertAllKeysHaveOrgSegment();
   }

   @Test
   void missingScheduleTaskRootDoesNotThrow() throws Exception {
      invokeRepair();
      assertFalse(putKeys.contains(ROOT_KEY));
      assertAllKeysHaveOrgSegment();
   }

   @Test
   void rootLevelTasksAreWrittenToOrgRootNotStrayFolder() throws Exception {
      AssetEntry taskA = task("admin~;~host-org:taskA");
      AssetEntry taskC = task("admin~;~host-org:taskC");
      AssetFolder root = new AssetFolder();
      root.addEntry(taskA);
      store.put(ROOT_KEY, root);

      AssetEntry emptyParent = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, "", null, ORG);
      Map<AssetEntry, List<AssetEntry>> parents = new HashMap<>();
      parents.put(emptyParent, new ArrayList<>(List.of(taskA, taskC)));
      when(contentService.getParentAssetEntryMap()).thenReturn(parents);

      invokeRepair();

      assertFalse(putKeys.contains(emptyParent.toIdentifier()),
                  "stray folder written at " + emptyParent.toIdentifier());
      assertTrue(((AssetFolder) store.get(ROOT_KEY)).containsEntry(taskC));
      assertAllKeysHaveOrgSegment();
   }

   private static AssetEntry task(String path) {
      return new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK, path, null, ORG);
   }

   private void assertAllKeysHaveOrgSegment() {
      for(String key : putKeys) {
         assertEquals(4, key.chars().filter(c -> c == '^').count(), "bad key " + key);
         assertTrue(key.endsWith("^" + ORG), "bad key " + key);
      }
   }

   private void invokeRepair() throws Exception {
      service.repairRepositoryFolders();
   }
}
