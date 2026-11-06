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
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.dep.*;
import inetsoft.web.admin.content.repository.*;
import inetsoft.web.admin.content.repository.model.SelectedAssetModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79435, the dependent-asset step of an export looked up the registry entry of every
 * selected dashboard by listing its whole parent folder, so N dashboards of one folder cost N
 * listings of N children each. A folder must be listed once per call, the entry visibility
 * (ADMIN listing, selector) must still decide the result, and the two-argument lookup used by the
 * other callers must keep listing for every call.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DeployServiceParentListingTest {
   private static final String ORG = "host-org";

   private MockedStatic<AssetUtil> assetUtil;
   private AssetRepository repo;
   private RepletRegistryService registry;
   private DeployService deployService;
   private final Principal principal = mock(Principal.class);
   // parent identifier -> entries the user may see in that folder
   private final Map<String, List<AssetEntry>> folders = new HashMap<>();
   private final List<AssetEntry> listedParents = new ArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      repo = mock(AssetRepository.class);
      when(repo.getEntries(any(AssetEntry.class), any(), any(), any())).thenAnswer(inv -> {
         AssetEntry parent = inv.getArgument(0);
         listedParents.add(parent);
         return folders.getOrDefault(parent.toIdentifier(), List.of()).toArray(new AssetEntry[0]);
      });
      assetUtil = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repo);

      registry = new RepletRegistryService(null, null, null, null, null);
      ContentRepositoryTreeService treeService = mock(ContentRepositoryTreeService.class);
      when(treeService.getUnscopedPath(anyString()))
         .thenAnswer(inv -> SUtil.getUnscopedPath(inv.getArgument(0)));
      deployService = new DeployService(treeService, null, null, null, null, null, null, null,
                                        registry);
   }

   @AfterEach
   void tearDown() {
      assetUtil.close();
   }

   @Test
   void manyDashboardsOfOneFolderListTheFolderOnce() {
      List<SelectedAssetModel> models = new ArrayList<>();

      for(int i = 0; i < 50; i++) {
         models.add(viewsheet("folder/vs" + i));
         addVisible(viewsheetEntry("folder/vs" + i));
      }

      List<XAsset> assets = deployService.getEntryAssets(models, keptOf(models), principal);

      assertEquals(50, assets.size());
      assertEquals(1, listedParents.size());
      assertTrue(assets.stream().allMatch(a -> a instanceof ViewsheetAsset));
   }

   @Test
   void eachDistinctFolderIsListedOnce() {
      List<SelectedAssetModel> models = new ArrayList<>();

      for(String folder : List.of("a", "b", "a/c")) {
         for(int i = 0; i < 5; i++) {
            models.add(viewsheet(folder + "/vs" + i));
            addVisible(viewsheetEntry(folder + "/vs" + i));
         }
      }

      deployService.getEntryAssets(models, keptOf(models), principal);

      assertEquals(3, listedParents.size());
   }

   @Test
   void batchListingSkipsTheSheetLoadButTheTwoArgumentLookupDoesNot() throws Exception {
      AssetEntry vs = viewsheetEntry("folder/vs1");
      addVisible(vs);
      Map<String, Map<String, AssetEntry>> listings = new HashMap<>();

      assertNotNull(registry.getAssetEntry(vs.toIdentifier(), principal, listings));
      assertEquals("true", listedParents.get(0).getProperty("__oneoff__"));

      assertNotNull(registry.getAssetEntry(vs.toIdentifier(), principal));
      assertNull(listedParents.get(1).getProperty("__oneoff__"));
      assertNotNull(registry.getAssetEntry(vs.toIdentifier(), principal));
      assertEquals(3, listedParents.size());
   }

   @Test
   void entryNotVisibleInTheListingIsNullAndStaysAViewsheet() throws Exception {
      // the folder lists only vs1 for this user (no ADMIN on vs2)
      addVisible(viewsheetEntry("folder/vs1"));
      Map<String, Map<String, AssetEntry>> listings = new HashMap<>();

      assertNotNull(registry.getAssetEntry(viewsheetEntry("folder/vs1").toIdentifier(),
                                           principal, listings));
      assertNull(registry.getAssetEntry(viewsheetEntry("folder/vs2").toIdentifier(), principal,
                                        listings));
      assertEquals(1, listedParents.size());

      SelectedAssetModel hidden = viewsheet("folder/vs2");
      List<XAsset> assets = deployService.getEntryAssets(List.of(hidden), keptOf(List.of(hidden)),
                                                         principal);
      assertEquals(1, assets.size());
      assertInstanceOf(ViewsheetAsset.class, assets.get(0));
   }

   @Test
   void snapshotWithoutViewsheetBecomesSnapshotAsset() {
      AssetEntry snap = snapshotEntry("folder/snap1");
      addVisible(snap);
      SelectedAssetModel model = viewsheet("folder/snap1");

      List<XAsset> assets = deployService.getEntryAssets(List.of(model), keptOf(List.of(model)),
                                                         principal);

      assertEquals(1, assets.size());
      assertInstanceOf(VSSnapshotAsset.class, assets.get(0));
      // viewsheet probe and snapshot probe share the one listing
      assertEquals(1, listedParents.size());
   }

   @Test
   void sameFolderOfAnotherOrgIsListedSeparately() throws Exception {
      Map<String, Map<String, AssetEntry>> listings = new HashMap<>();
      AssetEntry mine = viewsheetEntry("folder/vs1");
      AssetEntry other = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "folder/vs1", null, "other-org");
      addVisible(mine);
      addVisible(other);

      assertNotNull(registry.getAssetEntry(mine.toIdentifier(), principal, listings));
      assertNotNull(registry.getAssetEntry(other.toIdentifier(), principal, listings));

      assertEquals(2, listedParents.size());
   }

   private void addVisible(AssetEntry entry) {
      folders.computeIfAbsent(entry.getParent().toIdentifier(), k -> new ArrayList<>()).add(entry);
   }

   private static AssetEntry viewsheetEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null,
                            ORG);
   }

   private static AssetEntry snapshotEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET_SNAPSHOT,
                            path, null, ORG);
   }

   private static SelectedAssetModel viewsheet(String path) {
      return SelectedAssetModel.builder()
         .path(path)
         .type(RepositoryEntry.VIEWSHEET)
         .typeName("VIEWSHEET")
         .typeLabel("")
         .user(new IdentityID(XAsset.NULL, ORG))
         .build();
   }

   // the identifiers of the assets, so the permission check (not under test) is skipped
   private Set<String> keptOf(List<SelectedAssetModel> models) {
      Set<String> kept = new HashSet<>();

      for(SelectedAssetModel m : models) {
         kept.add(SUtil.getXAsset(ViewsheetAsset.VIEWSHEET, m.path(), null).toIdentifier());
         kept.add(SUtil.getXAsset(VSSnapshotAsset.VSSNAPSHOT, m.path(), null).toIdentifier());
      }

      return kept;
   }
}
