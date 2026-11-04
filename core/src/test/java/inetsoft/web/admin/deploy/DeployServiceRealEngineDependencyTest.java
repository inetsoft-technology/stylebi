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
package inetsoft.web.admin.deploy;

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.IndexedStorage;
import inetsoft.util.dep.*;
import inetsoft.web.admin.content.repository.*;
import inetsoft.web.admin.content.repository.model.SelectedAssetModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79435 against the real asset repository and storage (no mocked AssetRepository result):
 * the dependent-asset pass of an export for N dashboards of one folder must list the folder once
 * (not once per entry) and must not scan the storage keys per dashboard. Counts calls, so the
 * assertion does not depend on machine speed. The cost axes are N entries x parent size and
 * N dashboards x total storage keys.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DeployServiceRealEngineDependencyTest {
   private static final int N = 30;

   private AssetRepository repository;
   private SRPrincipal admin;
   private String orgId;
   private final List<AssetEntry> cleanup = new ArrayList<>();

   @BeforeEach
   void setUp() {
      AssetRepository.IGNORE_PERM.set(true);
      repository = AssetUtil.getAssetRepository(false);
      orgId = OrganizationManager.getInstance().getCurrentOrgID();
      admin = new SRPrincipal(new IdentityID("admin", orgId),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], orgId, 1L);
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         for(AssetEntry entry : cleanup) {
            if(repository.containsEntry(entry)) {
               repository.removeSheet(entry, null, true);
            }
         }
      }
      finally {
         cleanup.clear();
         AssetRepository.IGNORE_PERM.remove();
      }
   }

   @Test
   void dependentAssetPassListsFolderOnceAndNeverScansKeys() throws Exception {
      List<SelectedAssetModel> models = new ArrayList<>();
      Set<String> kept = new HashSet<>();

      for(int i = 0; i < N; i++) {
         String path = "vs79435real" + i;
         AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                           path, null, orgId);
         cleanup.add(entry);
         repository.setSheet(entry, new Viewsheet(), admin, true);
         models.add(SelectedAssetModel.builder()
                       .path(path).type(RepositoryEntry.VIEWSHEET).typeName("VIEWSHEET")
                       .typeLabel("").user(new IdentityID(XAsset.NULL, orgId)).build());
         kept.add(SUtil.getXAsset(ViewsheetAsset.VIEWSHEET, path, null).toIdentifier());
      }

      AssetRepository spyRepo = Mockito.spy(repository);
      IndexedStorage spyStorage = Mockito.spy(IndexedStorage.getIndexedStorage());
      RepletRegistryService registry = new RepletRegistryService(null, null, null, null, null);
      ContentRepositoryTreeService treeService = mock(ContentRepositoryTreeService.class);
      when(treeService.getUnscopedPath(anyString()))
         .thenAnswer(inv -> SUtil.getUnscopedPath(inv.getArgument(0)));
      DeployService deployService = new DeployService(treeService, null, null, null, null, null,
                                                      null, null, registry);

      List<XAsset> assets;

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
          MockedStatic<IndexedStorage> storage = mockStatic(IndexedStorage.class))
      {
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(spyRepo);
         storage.when(IndexedStorage::getIndexedStorage).thenReturn(spyStorage);

         // the same two steps as DeployService.getDependentAssetsList
         assets = deployService.getEntryAssets(models, kept, admin);
         DeployUtil.getDependentAssets(assets);
      }

      assertEquals(N, assets.size());

      long folderListings = Mockito.mockingDetails(spyRepo).getInvocations().stream()
         .filter(i -> "getEntries".equals(i.getMethod().getName()))
         .filter(i -> i.getLocation().toString().contains("RepletRegistryService"))
         .count();
      long keyScans = Mockito.mockingDetails(spyStorage).getInvocations().stream()
         .filter(i -> "getKeys".equals(i.getMethod().getName()))
         .filter(i -> i.getLocation().toString().contains("ViewsheetAsset.getDependencies"))
         .count();

      assertTrue(folderListings <= 1, "parent listed per entry: " + folderListings);
      assertEquals(0, keyScans, "key scans by ViewsheetAsset.getDependencies");
   }
}
