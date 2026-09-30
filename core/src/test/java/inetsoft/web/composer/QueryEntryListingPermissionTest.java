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
package inetsoft.web.composer;

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.util.ColumnCache;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.LoadAssetTreeNodesEvent;
import inetsoft.web.composer.ws.WSUtilControllers;
import inetsoft.web.composer.ws.dialog.GroupingAssemblyDialogController;
import inetsoft.web.composer.ws.dialog.GroupingAssemblyDialogServiceProxy;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.controller.database.VPMController.BrowserData;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77189: the composer and Data-tab endpoints that list the children of a client-built
 * query-scope entry, or read column values of a client-named source, must check the source
 * (and logical model) the asset engine resolves from the entry's properties, because the engine
 * itself does not. Labels:
 * <ul>
 *    <li>T POST /api/composer/asset_tree (target entry and expanded descendants)</li>
 *    <li>G POST /api/composer/ws/grouping-assembly-tree-model</li>
 *    <li>A POST /api/composer/ws/asset-entry-attributes</li>
 *    <li>D POST /api/composer/ws/asset-entry-attribute-data</li>
 *    <li>B POST /api/data/vpm/browserData</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class QueryEntryListingPermissionTest {
   private static final String ALLOWED = "DS_OK";
   private static final String DENIED = "DS1";
   private static final String MODEL = "LM";
   private static final String MODEL_FOLDER = "F";

   private SecurityEngine securityEngine;
   private XRepository repository;
   private AssetRepository assetRepository;
   private DataSourceService dataSourceService;
   private QueryManagerService queryManager;
   private AssetTreeController treeController;
   private GroupingAssemblyDialogController groupingController;
   private WSUtilControllers wsUtilControllers;
   private VPMController vpmController;
   private final Principal principal = () -> "bob";

   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      repository = mock(XRepository.class);
      assetRepository = mock(AssetRepository.class);
      dataSourceService = mock(DataSourceService.class);
      queryManager = new QueryManagerService(mock(RuntimeQueryService.class), repository,
                                             dataSourceService, securityEngine,
                                             mock(ColumnCache.class));
      treeController = new AssetTreeController(
         assetRepository, mock(AssetTreeServiceProxy.class), securityEngine,
         mock(LibManagerProvider.class), repository, queryManager);
      groupingController = new GroupingAssemblyDialogController(
         assetRepository, mock(GroupingAssemblyDialogServiceProxy.class), securityEngine,
         queryManager);
      wsUtilControllers = new WSUtilControllers();
      wsUtilControllers.setQueryManagerService(queryManager);
      wsUtilControllers.setAnalyticAssistant(mock(AnalyticAssistant.class));
      wsUtilControllers.setDataRefModelFactoryService(mock(DataRefModelFactoryService.class));
      vpmController = new VPMController(
         mock(DataRefModelFactoryService.class), mock(DatabaseTreeService.class),
         dataSourceService, repository, securityEngine, mock(DependencyHandler.class),
         mock(RenameTransformHandler.class), mock(ColumnCache.class), queryManager);

      when(assetRepository.getEntries(any(), any(), any(), any())).thenReturn(new AssetEntry[0]);
      grantPhysicalAccess(true);
      grantRead(ALLOWED);
      grantModelRead(true);

      // a logical model whose folder is only known to the server
      XDataModel dataModel = mock(XDataModel.class);
      XLogicalModel logicalModel = mock(XLogicalModel.class);
      when(logicalModel.getFolder()).thenReturn(MODEL_FOLDER);
      when(dataModel.getLogicalModel(MODEL)).thenReturn(logicalModel);
      when(repository.getDataModel(ALLOWED)).thenReturn(dataModel);
   }

   private void grantRead(String... names) throws Exception {
      Set<String> readable = Set.of(names);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_SOURCE), nullable(String.class), eq(ResourceAction.READ)))
         .thenAnswer(inv -> readable.contains(inv.<String>getArgument(2)));
   }

   private void grantPhysicalAccess(boolean allowed) throws Exception {
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.PHYSICAL_TABLE), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(allowed);
   }

   private void grantModelRead(boolean allowed) throws Exception {
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_MODEL_FOLDER), anyString(), eq(ResourceAction.READ)))
         .thenReturn(true);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.QUERY), anyString(), eq(ResourceAction.READ)))
         .thenReturn(allowed);
   }

   private static void assertDenied(Executable call) {
      assertThrows(java.lang.SecurityException.class, call);
   }

   private void verifyNothingListed() throws Exception {
      verify(assetRepository, never()).getEntries(any(), any(), any(), any());
      verify(assetRepository, never()).getEntries(any(), any(), any());
   }

   private static AssetEntry queryEntry(AssetEntry.Type type, String path, String prefix) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null);

      if(prefix != null) {
         entry.setProperty("prefix", prefix);
      }

      return entry;
   }

   private static AssetEntry dataSourceEntry(String name) {
      return queryEntry(AssetEntry.Type.DATA_SOURCE, name, name);
   }

   private static AssetEntry modelEntry(AssetEntry.Type type, String prefix) {
      AssetEntry entry = queryEntry(type, prefix + "/" + MODEL, prefix);
      entry.setProperty("source", MODEL);
      entry.setProperty("type", SourceInfo.MODEL + "");
      return entry;
   }

   private LoadAssetTreeNodesEvent event(AssetEntry target, LoadAssetTreeNodesEvent... descendants) {
      return LoadAssetTreeNodesEvent.builder()
         .targetEntry(target)
         .expandedDescendants(List.of(descendants))
         .build();
   }

   private void expand(LoadAssetTreeNodesEvent event) throws Exception {
      try(MockedStatic<MVManager> mvManager = mockStatic(MVManager.class)) {
         mvManager.when(MVManager::getManager).thenReturn(mock(MVManager.class));
         treeController.getNodes0(true, true, true, true, true, true, true, false, false, true,
                                  event, principal, true, true, true);
      }
   }

   // ---- T: composer asset tree ----

   @Test
   void tDataSourceDeniedWithoutRead() throws Exception {
      assertDenied(() -> expand(event(dataSourceEntry(DENIED))));
      verifyNothingListed();
      // the connection parameter lookup is keyed on the path and runs before the listing
      verify(repository, never()).getConnectionParameters(any(), anyString());
   }

   @Test
   void tDataSourceDeniedWhenPathDiffersFromPrefix() throws Exception {
      AssetEntry entry = queryEntry(AssetEntry.Type.DATA_SOURCE, DENIED, ALLOWED);

      assertDenied(() -> expand(event(entry)));
      verifyNothingListed();
      verify(repository, never()).getConnectionParameters(any(), anyString());
   }

   @Test
   void tDataSourceAllowedWithRead() throws Exception {
      AssetEntry entry = dataSourceEntry(ALLOWED);

      expand(event(entry));
      verify(assetRepository).getEntries(same(entry), same(principal), eq(ResourceAction.READ), any());
   }

   @Test
   void tPhysicalTableDeniedOnPrefix() throws Exception {
      AssetEntry entry = queryEntry(AssetEntry.Type.PHYSICAL_TABLE, ALLOWED + "/T", DENIED);

      assertDenied(() -> expand(event(entry)));
      verifyNothingListed();
   }

   @Test
   void tPhysicalFolderDeniedWithoutPhysicalTableAccess() throws Exception {
      // the reporter's PHYSICAL_TABLE bypass: skip the data source level and expand below it
      grantPhysicalAccess(false);
      AssetEntry entry = queryEntry(AssetEntry.Type.PHYSICAL_FOLDER, ALLOWED + "/S", ALLOWED);

      assertDenied(() -> expand(event(entry)));
      verifyNothingListed();
   }

   @Test
   void tPhysicalFolderAllowedWithReadAndAccess() throws Exception {
      AssetEntry entry = queryEntry(AssetEntry.Type.PHYSICAL_FOLDER, ALLOWED + "/S", ALLOWED);

      expand(event(entry));
      verify(assetRepository).getEntries(same(entry), same(principal), eq(ResourceAction.READ), any());
   }

   @Test
   void tLogicalModelDeniedOnPrefix() throws Exception {
      assertDenied(() -> expand(event(modelEntry(AssetEntry.Type.LOGIC_MODEL, DENIED))));
      verifyNothingListed();
   }

   @Test
   void tLogicalModelDeniedWithoutModelReadUsingServerFolder() throws Exception {
      grantModelRead(false);
      AssetEntry entry = modelEntry(AssetEntry.Type.LOGIC_MODEL, ALLOWED);
      // a forged folder must not change the resource that is checked
      entry.setProperty("folder", "Other");
      entry.setProperty("folder_description", "Other");

      assertDenied(() -> expand(event(entry)));
      verifyNothingListed();
      // the name the permission editors store the model's permission under (Bug #77400)
      verify(securityEngine).checkPermission(
         principal, ResourceType.QUERY, MODEL + "::" + ALLOWED + "^__^" + MODEL_FOLDER,
         ResourceAction.READ);
   }

   @Test
   void tLogicalModelInFolderDeniedByExplicitModelPermission() throws Exception {
      // Bug #77400: only the model itself is denied, not its data source or folder
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.QUERY), anyString(), eq(ResourceAction.READ)))
         .thenAnswer(inv -> !(MODEL + "::" + ALLOWED + "^__^" + MODEL_FOLDER)
            .equals(inv.getArgument(2)));

      assertDenied(() -> expand(event(modelEntry(AssetEntry.Type.LOGIC_MODEL, ALLOWED))));
      verifyNothingListed();
   }

   @Test
   void tLogicalModelWithoutFolderCheckedWithoutFolderSuffix() throws Exception {
      when(queryManagerModel().getFolder()).thenReturn(null);
      AssetEntry entry = modelEntry(AssetEntry.Type.LOGIC_MODEL, ALLOWED);

      expand(event(entry));
      verify(securityEngine).checkPermission(
         principal, ResourceType.QUERY, MODEL + "::" + ALLOWED, ResourceAction.READ);
      verify(assetRepository).getEntries(same(entry), same(principal), eq(ResourceAction.READ), any());
   }

   private XLogicalModel queryManagerModel() throws Exception {
      return repository.getDataModel(ALLOWED).getLogicalModel(MODEL);
   }

   @Test
   void tEntityDeniedWithoutModelRead() throws Exception {
      grantModelRead(false);
      AssetEntry entry = modelEntry(AssetEntry.Type.TABLE, ALLOWED);
      entry.setProperty("entity", "E");

      assertDenied(() -> expand(event(entry)));
      verifyNothingListed();
   }

   @Test
   void tLogicalModelAllowedWithRead() throws Exception {
      AssetEntry entry = modelEntry(AssetEntry.Type.LOGIC_MODEL, ALLOWED);

      expand(event(entry));
      verify(assetRepository).getEntries(same(entry), same(principal), eq(ResourceAction.READ), any());
   }

   @Test
   void tForgedCubeTableWithDeniedPrefixDenied() throws Exception {
      AssetEntry entry = modelEntry(AssetEntry.Type.TABLE, DENIED);
      entry.setProperty("CUBE_TABLE", "true");

      assertDenied(() -> expand(event(entry)));
      verifyNothingListed();
   }

   @Test
   void tCubesFolderAndCubeTableWithoutPrefixAllowed() throws Exception {
      // the server's own prefix-less entries (AssetTreeController.appendCubes, getCubeNode)
      AssetEntry cubes = queryEntry(AssetEntry.Type.FOLDER, "Cubes", null);
      cubes.setProperty("entryName", "cubeRoot");
      AssetEntry cube = queryEntry(AssetEntry.Type.TABLE, "XMLA/Cube", null);
      cube.setProperty("CUBE_TABLE", "true");
      cube.setProperty("source", "baseWorksheet");

      assertDoesNotThrow(() -> expand(event(cubes)));
      assertDoesNotThrow(() -> expand(event(cube)));
      verify(assetRepository).getEntries(same(cube), same(principal), eq(ResourceAction.READ), any());
   }

   @Test
   void tForgedExpandedDescendantDenied() throws Exception {
      // the server lists a readable child, the client reuses its identity with a denied prefix
      AssetEntry parent = dataSourceEntry(ALLOWED);
      AssetEntry child = queryEntry(AssetEntry.Type.PHYSICAL_FOLDER, ALLOWED + "/S", ALLOWED);
      when(assetRepository.getEntries(same(parent), any(), any(), any()))
         .thenReturn(new AssetEntry[] { child });
      AssetEntry forged = queryEntry(AssetEntry.Type.PHYSICAL_FOLDER, ALLOWED + "/S", DENIED);

      assertDenied(() -> expand(event(parent, event(forged))));
      verify(assetRepository, never()).getEntries(same(forged), any(), any(), any());
   }

   @Test
   void tExpandedDescendantAllowedWithRead() throws Exception {
      AssetEntry parent = dataSourceEntry(ALLOWED);
      AssetEntry child = queryEntry(AssetEntry.Type.PHYSICAL_FOLDER, ALLOWED + "/S", ALLOWED);
      when(assetRepository.getEntries(same(parent), any(), any(), any()))
         .thenReturn(new AssetEntry[] { child });
      AssetEntry descendant = (AssetEntry) child.clone();

      expand(event(parent, event(descendant)));
      verify(assetRepository).getEntries(same(descendant), same(principal),
                                         eq(ResourceAction.READ), any());
   }

   // ---- G: grouping dialog tree ----

   @Test
   void gLogicalModelDeniedOnPrefix() throws Exception {
      assertDenied(() -> groupingController.getNodes(
         modelEntry(AssetEntry.Type.LOGIC_MODEL, DENIED), principal));
      verifyNothingListed();
   }

   @Test
   void gPhysicalTableDeniedWithoutPhysicalTableAccess() throws Exception {
      grantPhysicalAccess(false);

      assertDenied(() -> groupingController.getNodes(
         queryEntry(AssetEntry.Type.PHYSICAL_TABLE, ALLOWED + "/T", ALLOWED), principal));
      verifyNothingListed();
   }

   @Test
   void gDataSourceAllowedWithRead() throws Exception {
      AssetEntry entry = dataSourceEntry(ALLOWED);

      assertNotNull(groupingController.getNodes(entry, principal));
      verify(assetRepository).getEntries(same(entry), same(principal), eq(ResourceAction.READ), any());
   }

   // ---- A / D: grouping dialog attributes and attribute values ----

   /** Not query scope: the source is built from the properties whatever the scope. */
   private static AssetEntry forgedSourceEntry(String prefix) {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
                                        "x", null);
      entry.setProperty("type", SourceInfo.MODEL + "");
      entry.setProperty("prefix", prefix);
      entry.setProperty("source", MODEL);
      return entry;
   }

   @Test
   void aAttributesDeniedOnPrefixWhateverTheScope() throws Exception {
      try(MockedStatic<AssetEventUtil> util = mockStatic(AssetEventUtil.class)) {
         assertDenied(() -> wsUtilControllers.getAttributes(forgedSourceEntry(DENIED), principal));
         util.verifyNoInteractions();
      }
   }

   @Test
   void aAttributesDeniedWithoutModelRead() throws Exception {
      grantModelRead(false);

      try(MockedStatic<AssetEventUtil> util = mockStatic(AssetEventUtil.class)) {
         assertDenied(() -> wsUtilControllers.getAttributes(forgedSourceEntry(ALLOWED), principal));
         util.verifyNoInteractions();
      }
   }

   @Test
   void aAttributesAllowedWithRead() throws Exception {
      try(MockedStatic<AssetEventUtil> util = mockStatic(AssetEventUtil.class)) {
         util.when(() -> AssetEventUtil.getAttributesBySource(any(), any(), any()))
            .thenReturn(new ColumnSelection());
         ReflectionTestUtils.setField(wsUtilControllers, "wsEngine",
                                      mock(ViewsheetService.class));

         assertEquals(0, wsUtilControllers.getAttributes(
            modelEntry(AssetEntry.Type.LOGIC_MODEL, ALLOWED), principal).length);
         util.verify(() -> AssetEventUtil.getAttributesBySource(any(), same(principal), any()));
      }
   }

   @Test
   void aAttributesDeniedWithoutPhysicalTableAccess() throws Exception {
      // Bug #77400: a physical table source also requires PHYSICAL_TABLE ACCESS
      grantPhysicalAccess(false);
      AssetEntry entry = forgedSourceEntry(ALLOWED);
      entry.setProperty("type", SourceInfo.PHYSICAL_TABLE + "");

      try(MockedStatic<AssetEventUtil> util = mockStatic(AssetEventUtil.class)) {
         assertDenied(() -> wsUtilControllers.getAttributes(entry, principal));
         util.verifyNoInteractions();
      }
   }

   @Test
   void dAttributeDataDeniedOnPrefixWhateverTheScope() throws Exception {
      try(MockedStatic<AssetEventUtil> util = mockStatic(AssetEventUtil.class)) {
         assertDenied(() -> wsUtilControllers.getAttributeData(
            mock(HttpServletRequest.class), forgedSourceEntry(DENIED), "E:A", principal));
         util.verifyNoInteractions();
      }
   }

   @Test
   void dAttributeDataDeniedWithoutModelRead() throws Exception {
      grantModelRead(false);

      try(MockedStatic<AssetEventUtil> util = mockStatic(AssetEventUtil.class)) {
         assertDenied(() -> wsUtilControllers.getAttributeData(
            mock(HttpServletRequest.class), forgedSourceEntry(ALLOWED), "E:A", principal));
         util.verifyNoInteractions();
      }
   }

   // ---- B: VPM condition value browser ----

   private BrowserData browserData() throws Exception {
      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getDataSource()).thenReturn(DENIED);
      when(repository.getDataModel("DM")).thenReturn(dataModel);
      BrowserData data = new BrowserData();
      data.setDatabase("DM");
      data.setTableName("T");
      data.setColumnName("C");
      return data;
   }

   @Test
   void bBrowserDataDeniedWithoutRead() throws Exception {
      BrowserData data = browserData();

      assertDenied(() -> vpmController.getBrowserData(data, principal));
      verify(dataSourceService, never()).getDataSource(anyString());
   }

   @Test
   void bBrowserDataPassesTheCheckWithRead() throws Exception {
      BrowserData data = browserData();
      grantRead(DENIED);

      // past the check, the source is loaded (and the mock source cannot be queried)
      assertThrows(Exception.class, () -> vpmController.getBrowserData(data, principal));
      verify(dataSourceService).getDataSource(DENIED);
   }
}
