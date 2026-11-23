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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.service.database.PhysicalGraphService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.controller.WizDataModelWire.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The kind-agnostic data model routes (browse, folders, move, dependencies, delete, rename) and
 * the stateless physical view operations, driven through the real controllers, the unchanged
 * Portal services and the real data source registry. Only permission checks, the dependency
 * store and the transform queue are mocked. Every write is checked by reading the registry
 * directly, not through the bridge.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  WizDataModelBridgeTest.DependencyStorageConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class WizDataModelBridgeTest {
   @Configuration
   static class DependencyStorageConfig {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }

   private static int count = 0;
   @Autowired private DependencyStorageService dependencyStorage;
   private final Principal bob = () -> "bob";
   private String ds;
   private XDataModel dm;
   private XRepository repository;
   private DataSourceService dss;
   private SecurityEngine security;
   private LogicalModelService lms;
   private RenameTransformHandler transformHandler;
   private WizDataModelBrowseController browse;
   private WizDataModelFolderController folders;
   private WizDataModelItemController items;
   private LogicalModelTreeService tree;
   private DatabaseModelBrowserService browser;
   private final Set<String> deniedFolderDelete = new HashSet<>();

   @BeforeEach
   void setUp() throws Exception {
      reset(dependencyStorage);
      DataSourceRegistry.getRegistry().init();
      ds = "dmbridge_" + (++count);
      dm = new XDataModel(ds);
      XPartition pv = new XPartition("pv");
      pv.addTable("T1", null, null);
      dm.addPartition(pv);

      repository = mock(XRepository.class);
      JDBCDataSource jdbc = mock(JDBCDataSource.class);
      when(repository.getDataSource(ds)).thenReturn(jdbc);
      when(repository.getDataModel(ds)).thenReturn(dm);
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any(ResourceAction.class)))
         .thenReturn(true);
      dss = mock(DataSourceService.class);
      allowAll();
      when(dss.checkDataModelFolderPermission(anyString(), any(ResourceAction.class), any()))
         .thenReturn(true);
      when(dss.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      when(dss.getDataModel(ds)).thenReturn(dm);
      when(dss.isRootDataModelFolder(any())).thenAnswer(i -> {
         String f = i.getArgument(0);
         return f == null || f.isEmpty() || "/".equals(f);
      });
      transformHandler = mock(RenameTransformHandler.class);
      DependencyHandler dependencyHandler = mock(DependencyHandler.class);
      lms = new LogicalModelService(security, repository, dss, null, null, mock(AssetRepository.class),
                                    dependencyHandler, transformHandler);
      PhysicalModelManagerService pmms = new PhysicalModelManagerService(
         dss, mock(PhysicalModelService.class), mock(RuntimePartitionService.class),
         new PhysicalGraphService(), repository, null, dependencyHandler, transformHandler);
      DataModelFolderManagerService folderService = new DataModelFolderManagerService(
         repository, security, lms, dss, mock(DatasourcesService.class), pmms);
      browser = new DatabaseModelBrowserService(
         dss, repository, pmms, lms, security, folderService, transformHandler);
      tree = mock(LogicalModelTreeService.class);
      List<WizModelKindOps> ops = List.of(new WizPhysicalViewOps(dss, pmms), new FakeLogicalOps(lms));
      browse = new WizDataModelBrowseController(browser, dss, lms, tree, repository);
      folders = new WizDataModelFolderController(browser, folderService, dss, lms, security, repository);
      items = new WizDataModelItemController(ops, folderService, dss, lms, repository);
   }

   private void allowAll() throws Exception {
      when(dss.checkPermission(anyString(), any(ResourceAction.class), any(Principal.class)))
         .thenReturn(true);
      when(dss.checkPermission(any(), any(), any(), any())).thenReturn(true);
   }

   // -- helpers ---------------------------------------------------------------------------

   private void addFolder(String name) {
      dm.addFolder(name);
   }

   private XLogicalModel addLogical(String name, String view, String folder) {
      XLogicalModel model = new XLogicalModel(name);
      model.setPartition(view);
      model.setFolder(folder);
      model.addEntity(new XEntity("E"));
      dm.addLogicalModel(model);
      return dm.getLogicalModel(name);
   }

   private void addView(String name, String folder) {
      XPartition view = new XPartition(name);
      view.addTable("T1", null, null);
      view.setFolder(folder);
      dm.addPartition(view);
   }

   private XDataModel registry() {
      DataSourceRegistry.getRegistry().clearCache();
      XDataModel fresh = new XDataModel(ds);
      return fresh;
   }

   private void seedDependency(AssetEntry.Type type, String path, String worksheet)
      throws Exception
   {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null);
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(List.of((AssetObject) new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, worksheet, null)));
      when(dependencyStorage.getWithOrg(eq(entry.toIdentifier()), any())).thenReturn(info);
   }

   @SuppressWarnings("unchecked")
   private <T> T ok(Object body, Class<T> type) {
      assertTrue(type.isInstance(body), "expected " + type.getSimpleName() + " but got " + body);
      return (T) body;
   }

   private Failure failure(Object body, String reason) {
      Failure failure = ok(body, Failure.class);
      assertFalse(failure.ok());
      assertEquals(reason, failure.reason(), failure.message());
      return failure;
   }

   private static <T> T only(List<T> list) {
      assertEquals(1, list.size(), String.valueOf(list));
      return list.get(0);
   }

   // -- source guard (DM-A14) -------------------------------------------------------------

   @Test
   void everyRouteRefusesANonDatabaseSourceByName() throws Exception {
      when(repository.getDataSource("xmla1")).thenReturn(
         mock(inetsoft.uql.xmla.XMLADataSource.class));

      Failure failure = failure(browse.browse(
         new BrowseRequest("xmla1", null, null, null, null), bob), NOT_JDBC);
      assertEquals("database", failure.field());
      assertTrue(failure.message().contains("\"xmla1\""), failure.message());
      failure(items.rename(new RenameRequest("xmla1", KIND_VPM, "a", "b", null), bob), NOT_JDBC);
      failure(folders.createFolder(new FolderCreateRequest("xmla1", "F"), bob), NOT_JDBC);
      failure(browse.browse(new BrowseRequest("nope", null, null, null, null), bob), NOT_FOUND);
   }

   @Test
   void aMissingDatabaseIsABadRequest() {
      assertThrows(IllegalArgumentException.class,
                   () -> browse.browse(new BrowseRequest("", null, null, null, null), bob));
      assertThrows(IllegalArgumentException.class,
                   () -> browse.browse(new BrowseRequest(ds, null, null, List.of("table"), null), bob));
      assertThrows(IllegalArgumentException.class,
                   () -> browse.browse(new BrowseRequest(ds, null, null, null, 0), bob));
   }

   // -- browse ----------------------------------------------------------------------------

   @Test
   void browseListsViewsModelsVpmsAndFoldersSortedWithTotal() throws Exception {
      PhysicalModel zebra = (PhysicalModel) physical("zebra", "F");
      PhysicalModel alpha = (PhysicalModel) physical("Alpha", null);
      PhysicalModel child = (PhysicalModel) physical("kid", null);
      alpha.setExtendViews(List.of(child));
      LogicalModel lm = new LogicalModel();
      lm.setName("lm1");
      lm.setFolderName("F");
      lm.setEditable(true);
      DataModelFolder folder = new DataModelFolder();
      folder.setName("F");
      folder.setEditable(true);
      when(dss.getPhysicalModels(eq(ds), any(), any(), anyBoolean()))
         .thenReturn(List.of(zebra, alpha));
      when(dss.getLogicalModels(eq(ds), any(), any(), anyBoolean())).thenReturn(List.of(lm));
      Vpm vpm = new Vpm();
      vpm.setName("v1");
      AssetListBrowseModel vpms = new AssetListBrowseModel();
      vpms.setItems(new AssetItem[] { vpm });
      when(dss.getVpmBrowseModel(eq(ds), any())).thenReturn(vpms);
      addFolder("F");
      doReturn(true).when(dss).checkDataModelFolderPermission(anyString(), any(), any());

      BrowseResponse response = ok(
         browse.browse(new BrowseRequest(ds, null, null, null, null), bob), BrowseResponse.class);

      assertEquals(List.of("F"), response.folders().stream().map(BrowseFolder::name).toList());
      assertEquals(List.of("Alpha", "kid", "lm1", "v1", "zebra"),
                   response.items().stream().map(BrowseItem::name).toList());
      assertEquals(5, response.total());
      BrowseItem kid = response.items().get(1);
      assertTrue(kid.extended());
      assertEquals("Alpha", kid.parent());
      assertNull(response.items().get(3).folder(), "a VPM is never in a folder");

      BrowseResponse limited = ok(
         browse.browse(new BrowseRequest(ds, null, null, List.of(KIND_LOGICAL_MODEL, KIND_VPM), 1),
                       bob), BrowseResponse.class);
      assertEquals(List.of("lm1"), limited.items().stream().map(BrowseItem::name).toList());
      assertEquals(2, limited.total(), "total is counted before the limit");
      assertTrue(limited.folders().isEmpty(), "folders were not asked for");
   }

   @Test
   void browseOfAMissingFolderIsFolderNotFound() throws Exception {
      failure(browse.browse(new BrowseRequest(ds, "Nope", null, null, null), bob),
              FOLDER_NOT_FOUND);
   }

   private AssetItem physical(String name, String folder) {
      PhysicalModel view = new PhysicalModel();
      view.setName(name);
      view.setFolderName(folder);
      return view;
   }

   @Test
   void physicalTablesNeedReadOnTheSourceBeforeTheTreeIsTouched() throws Exception {
      when(dss.checkPermission(eq(ds), eq(ResourceAction.READ), any(Principal.class)))
         .thenReturn(false);

      // answered exactly like a source that does not exist (review R1-4), tree never touched
      Failure denied = failure(
         browse.physicalTables(new PhysicalTablesRequest(ds, "pv", null), bob), NOT_FOUND);
      Failure missing = failure(
         browse.physicalTables(new PhysicalTablesRequest("nosuch", "pv", null), bob), NOT_FOUND);
      assertEquals(missing.message().replace("nosuch", ds), denied.message());
      verifyNoInteractions(tree);
   }

   @Test
   void physicalTablesListTablesAndColumns() throws Exception {
      XAttributeModel column = new XAttributeModel();
      column.setName("ID");
      column.setDataType("integer");
      TreeNodeModel table = TreeNodeModel.builder().label("T1")
         .addChildren(TreeNodeModel.builder().label("ID").data(column).leaf(true).build())
         .build();
      when(tree.getPhysicalModelTree(ds, "pv", null, null, null)).thenReturn(
         TreeNodeModel.builder().label("root").addChildren(table).build());

      PhysicalTablesResponse response = ok(
         browse.physicalTables(new PhysicalTablesRequest(ds, "pv", null), bob),
         PhysicalTablesResponse.class);

      PhysicalTable t1 = only(response.tables());
      assertEquals("T1", t1.name());
      assertEquals("integer", only(t1.columns()).dataType());
      failure(browse.physicalTables(new PhysicalTablesRequest(ds, "missing", null), bob), NOT_FOUND);
   }

   // -- folders (DM-A7) -------------------------------------------------------------------

   @Test
   void folderCreateReadsBackAndRefusesADuplicate() throws Exception {
      FolderCreateResponse created = ok(
         folders.createFolder(new FolderCreateRequest(ds, "Reports"), bob),
         FolderCreateResponse.class);
      assertEquals("Reports", created.name());
      assertTrue(Arrays.asList(dm.getFolders()).contains("Reports"));

      Failure duplicate = failure(
         folders.createFolder(new FolderCreateRequest(ds, "Reports"), bob), DUPLICATE_NAME);
      assertEquals(KIND_FOLDER, duplicate.existingKind());
      assertThrows(IllegalArgumentException.class,
                   () -> folders.createFolder(new FolderCreateRequest(ds, "a/b"), bob));
   }

   @Test
   void folderRenameMovesContainedModelsAndReadsBack() throws Exception {
      addFolder("Old");
      addLogical("lm", "pv", "Old");
      addView("v2", "Old");

      FolderRenameResponse response = ok(
         folders.renameFolder(new FolderRenameRequest(ds, "Old", "New"), bob),
         FolderRenameResponse.class);

      assertEquals(List.of(new Ref(KIND_LOGICAL_MODEL, "lm", null),
                           new Ref(KIND_PHYSICAL_VIEW, "v2", null)), response.moved());
      XDataModel stored = registry();
      assertEquals("New", stored.getLogicalModel("lm").getFolder());
      assertEquals("New", stored.getPartition("v2").getFolder());
      assertTrue(Arrays.asList(dm.getFolders()).contains("New"));
      assertFalse(Arrays.asList(dm.getFolders()).contains("Old"));
   }

   @Test
   void folderRenameOntoAnExistingFolderIsRefusedAndMergesNothing() throws Exception {
      addFolder("A");
      addFolder("B");
      addLogical("lm", "pv", "A");

      Failure failure = failure(
         folders.renameFolder(new FolderRenameRequest(ds, "A", "B"), bob), DUPLICATE_NAME);

      assertEquals(KIND_FOLDER, failure.existingKind());
      assertEquals("A", registry().getLogicalModel("lm").getFolder());
      assertTrue(Arrays.asList(dm.getFolders()).contains("A"));
   }

   @Test
   void folderRenameThatFailsOnTheServerIsAnErrorNeverSuccess() throws Exception {
      addFolder("Old");
      doThrow(new IllegalStateException("registry write failed"))
         .when(repository).updateDataModel(any());

      // a server failure is a failure body whose text is generic: the exception text of a
      // server failure can carry a credential and is never returned (review R1-1)
      Failure failed = failure(
         folders.renameFolder(new FolderRenameRequest(ds, "Old", "New"), bob), UNKNOWN);
      assertFalse(failed.message().contains("registry write failed"), failed.message());
      failure(folders.renameFolder(new FolderRenameRequest(ds, "Missing", "X"), bob), NOT_FOUND);
   }

   // -- move ------------------------------------------------------------------------------

   @Test
   void moveMovesAndReadsBackAndReportsPerItemFailures() throws Exception {
      addFolder("F");
      addLogical("lm", "pv", null);
      addView("v2", null);
      seedDependency(AssetEntry.Type.LOGIC_MODEL, ds + "/lm", "ws1");

      MoveResponse response = ok(folders.move(new MoveRequest(ds, "F", List.of(
         new Ref(KIND_LOGICAL_MODEL, "lm", null), new Ref(KIND_PHYSICAL_VIEW, "v2", null),
         new Ref(KIND_VPM, "x", null), new Ref(KIND_FOLDER, "F", null),
         new Ref(KIND_LOGICAL_MODEL, "gone", null), new Ref(KIND_VPM, "pv", null),
         new Ref(KIND_LOGICAL_MODEL, "pv", null))), bob), MoveResponse.class);

      List<MoveResult> results = response.results();
      assertTrue(results.get(0).ok());
      assertNull(results.get(0).from());
      assertEquals("F", results.get(0).to());
      assertTrue(results.get(1).ok());
      assertEquals(INVALID, results.get(2).reason());
      assertEquals(INVALID, results.get(3).reason());
      assertEquals(NOT_FOUND, results.get(4).reason());
      assertEquals(WRONG_KIND, results.get(6).reason());
      XDataModel stored = registry();
      assertEquals("F", stored.getLogicalModel("lm").getFolder());
      assertEquals("F", stored.getPartition("v2").getFolder());
      verify(transformHandler, atLeastOnce()).addTransformTask(any(RenameDependencyInfo.class));
   }

   @Test
   void moveToAMissingFolderMovesNothing() throws Exception {
      addLogical("lm", "pv", null);

      failure(folders.move(new MoveRequest(ds, "Nope", List.of(
         new Ref(KIND_LOGICAL_MODEL, "lm", null))), bob), FOLDER_NOT_FOUND);

      assertNull(registry().getLogicalModel("lm").getFolder());
   }

   @Test
   void moveDeniedForOneItemMovesTheOthers() throws Exception {
      addFolder("F");
      addLogical("lm", "pv", null);
      addView("v2", null);
      when(dss.checkPermission(eq(ds), isNull(), eq(ResourceAction.WRITE), any(Principal.class)))
         .thenAnswer(i -> true);
      // the caller may not WRITE+DELETE the logical model
      LogicalModelService denying = mock(LogicalModelService.class);
      when(denying.checkPermission(anyString(), any(), eq("lm"), any(), any(), any()))
         .thenAnswer(i -> i.getArgument(4) == ResourceAction.READ);
      WizDataModelFolderController controller = new WizDataModelFolderController(
         browser, mock(DataModelFolderManagerService.class), dss, denying, security, repository);

      MoveResponse response = ok(controller.move(new MoveRequest(ds, "F", List.of(
         new Ref(KIND_LOGICAL_MODEL, "lm", null), new Ref(KIND_PHYSICAL_VIEW, "v2", null))), bob),
                                 MoveResponse.class);

      assertEquals(PERMISSION_DENIED, response.results().get(0).reason());
      assertTrue(response.results().get(1).ok());
      XDataModel stored = registry();
      assertNull(stored.getLogicalModel("lm").getFolder(), "the denied item did not move");
      assertEquals("F", stored.getPartition("v2").getFolder());
   }

   // -- dependencies ----------------------------------------------------------------------

   @Test
   void dependenciesOfAViewListUsersAndRecordedDependents() throws Exception {
      addLogical("lm", "pv", null);
      VirtualPrivateModel vpm = new VirtualPrivateModel("vp");
      inetsoft.uql.erm.vpm.VpmCondition condition = new inetsoft.uql.erm.vpm.VpmCondition();
      condition.setName("c");
      condition.setType(inetsoft.uql.erm.vpm.VpmCondition.PHYSICMODEL);
      condition.setTable("pv");
      vpm.addCondition(condition);
      dm.addVirtualPrivateModel(vpm, true);
      seedDependency(AssetEntry.Type.PARTITION, ds + "/pv", "wsView");

      DependenciesResponse response = ok(items.dependencies(new DependenciesRequest(ds, List.of(
         new Ref(KIND_PHYSICAL_VIEW, "pv", null), new Ref(KIND_PHYSICAL_VIEW, "ghost", null),
         new Ref(KIND_LOGICAL_MODEL, "pv", null))), bob), DependenciesResponse.class);

      DepItem view = response.items().get(0);
      assertTrue(view.exists());
      assertTrue(view.deletable());
      Set<String> names = new HashSet<>();
      view.dependents().forEach(d -> names.add(d.type() + ":" + d.name()));
      assertEquals(Set.of("logicalModel:lm", "vpm:vp", "worksheet:wsView"), names);
      assertEquals(List.of("logicalModel", "vpm"),
                   view.danglingAfter().stream().map(Dangling::kind).sorted().toList());
      assertFalse(response.items().get(1).exists());
      assertEquals(NOT_FOUND, response.items().get(1).reason());
      assertEquals(WRONG_KIND, response.items().get(2).reason());
      assertTrue(response.coverage().startsWith("Coverage:"));
   }

   @Test
   void dependenciesOfAFolderListContentsAndExtendedChildrenGoingWithTheBase() throws Exception {
      addFolder("F");
      XLogicalModel base = addLogical("base", "pv", "F");
      base.addLogicalModel(new XLogicalModel("kid"), true);
      addView("fv", "F");
      addLogical("outside", "fv", null);

      DepItem folder = only(ok(items.dependencies(new DependenciesRequest(ds, List.of(
         new Ref(KIND_FOLDER, "F", null))), bob), DependenciesResponse.class).items());

      assertEquals(List.of(new Ref(KIND_LOGICAL_MODEL, "base", null),
                           new Ref(KIND_PHYSICAL_VIEW, "fv", null)), folder.contains());
      assertEquals(List.of("kid"), folder.alsoDeleted().stream().map(AlsoDeleted::name).toList());
      assertEquals(List.of("outside"),
                   folder.dependents().stream().map(Dependent::name).toList(),
                   "a model outside the folder uses a view inside it");
   }

   @Test
   void dependenciesNeedReadOnTheSource() throws Exception {
      when(dss.checkPermission(eq(ds), eq(ResourceAction.READ), any(Principal.class)))
         .thenReturn(false);

      Failure denied = failure(items.dependencies(
         new DependenciesRequest(ds, List.of(new Ref(KIND_PHYSICAL_VIEW, "pv", null))), bob),
                               NOT_FOUND);
      Failure missing = failure(items.dependencies(
         new DependenciesRequest("nosuch", List.of(new Ref(KIND_PHYSICAL_VIEW, "pv", null))), bob),
                                NOT_FOUND);
      assertEquals(missing.message().replace("nosuch", ds), denied.message());
   }

   // -- delete (DM-A4) --------------------------------------------------------------------

   @Test
   void deleteOfAViewInUseIsRefusedUnlessForced() throws Exception {
      addView("used", null);
      addLogical("lm", "used", null);

      DeleteResult refused = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "used", null, false, null))), bob),
                                     DeleteResponse.class).results());

      assertFalse(refused.ok());
      assertEquals(VIEW_IN_USE, refused.reason());
      assertNotNull(registry().getPartition("used"), "nothing was deleted");

      // force never deletes a view that models still use (review r3)
      DeleteResult forced = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "used", null, true, null))), bob),
                                    DeleteResponse.class).results());
      assertFalse(forced.ok());
      assertEquals(VIEW_IN_USE, forced.reason());
      assertNotNull(registry().getPartition("used"), "force did not delete a used view");
   }

   @Test
   void forceNeverDeletesAViewUsedOnlyByModelsTheCallerCannotSee() throws Exception {
      addView("V", null);
      addLogical("SecretModel", "V", null);
      LogicalModelService denying = mock(LogicalModelService.class);
      when(denying.checkPermission(anyString(), any(), anyString(), any(), any(), any()))
         .thenAnswer(i -> !"SecretModel".equals(i.getArgument(2)));
      WizDataModelItemController controller = new WizDataModelItemController(
         List.of(new WizPhysicalViewOps(dss, lmsPmms())),
         mock(DataModelFolderManagerService.class), dss, denying, repository);

      DeleteResult result = only(ok(controller.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "V", null, true, null))), bob),
                                    DeleteResponse.class).results());

      assertEquals(VIEW_IN_USE, result.reason());
      assertFalse(result.message().contains("SecretModel"), result.message());
      assertNotNull(registry().getPartition("V"), "the view was not deleted");
      assertNotNull(registry().getLogicalModel("SecretModel"));

      // the dependency answer says VIEW_IN_USE too and keeps the nameless placeholder
      DepItem item = only(ok(controller.dependencies(new DependenciesRequest(ds, List.of(
         new Ref(KIND_PHYSICAL_VIEW, "V", null))), bob), DependenciesResponse.class).items());
      assertEquals(VIEW_IN_USE, item.reason());
      assertFalse(String.valueOf(item.message()).contains("SecretModel"), item.message());
      assertTrue(item.dependents().stream().anyMatch(d -> d.name().contains("may not see")));
      assertTrue(item.dependents().stream().noneMatch(d -> "SecretModel".equals(d.name())));
   }

   @Test
   void aViewWhoseOnlyUsersAreDeletedInTheSameCallIsDeleted() throws Exception {
      addView("V", null);
      addLogical("lm", "V", null);

      // the view is listed FIRST: the users go before it all the same
      List<DeleteResult> results = ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "V", null, false, null),
         new DeleteItem(KIND_LOGICAL_MODEL, "lm", null, false, null))), bob),
                                       DeleteResponse.class).results();

      assertTrue(results.get(0).ok(), results.get(0).message());
      assertTrue(results.get(1).ok(), results.get(1).message());
      assertNull(registry().getPartition("V"));
      assertNull(registry().getLogicalModel("lm"));
   }

   @Test
   void aViewIsKeptWhenAUserListedInTheSameCallIsNotDeleted() throws Exception {
      addView("V", null);
      addLogical("lm", "V", null);
      addLogical("other", "V", null);

      List<DeleteResult> results = ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "V", null, true, null),
         new DeleteItem(KIND_LOGICAL_MODEL, "lm", null, false, null))), bob),
                                       DeleteResponse.class).results();

      assertEquals(VIEW_IN_USE, results.get(0).reason(), "'other' still uses it");
      assertNotNull(registry().getPartition("V"));
   }

   @Test
   void deleteOfAFreeViewAppliesAndReadsBack() throws Exception {
      addView("free", null);

      DeleteResult result = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "free", null, null, null))), bob),
                                    DeleteResponse.class).results());

      assertTrue(result.ok());
      assertEquals(List.of(new Ref(KIND_PHYSICAL_VIEW, "free", null)), result.deleted());
      assertNull(registry().getPartition("free"));
   }

   @Test
   void deleteWithRecordedDependentsNeedsForce() throws Exception {
      addView("free", null);
      seedDependency(AssetEntry.Type.PARTITION, ds + "/free", "wsUses");

      DeleteResult refused = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "free", null, false, null))), bob),
                                     DeleteResponse.class).results());

      assertEquals(HAS_DEPENDENCIES, refused.reason());
      assertEquals("wsUses", only(refused.dependents()).name());
      assertNotNull(registry().getPartition("free"));
   }

   @Test
   void deleteOfABaseModelListsItsExtendedChildren() throws Exception {
      XLogicalModel base = addLogical("base", "pv", null);
      base.addLogicalModel(new XLogicalModel("kid"), true);

      DeleteResult result = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_LOGICAL_MODEL, "base", null, false, null))), bob),
                                    DeleteResponse.class).results());

      assertTrue(result.ok(), result.message());
      assertNull(registry().getLogicalModel("base"));
   }

   @Test
   void folderDeleteIsBoundToTheContentsOfTheDryRun() throws Exception {
      addFolder("F");
      addLogical("lm", "pv", "F");
      List<Ref> expected = List.of(new Ref(KIND_LOGICAL_MODEL, "lm", null));
      // a model arrives after the dry run
      addLogical("late", "pv", "F");

      DeleteResult mismatch = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_FOLDER, "F", null, false, expected))), bob),
                                      DeleteResponse.class).results());

      assertEquals(EXPECT_MISMATCH, mismatch.reason());
      assertTrue(mismatch.message().contains("late"), mismatch.message());
      assertNotNull(registry().getLogicalModel("lm"), "nothing was deleted");
      assertTrue(Arrays.asList(dm.getFolders()).contains("F"));

      List<Ref> exact = List.of(new Ref(KIND_LOGICAL_MODEL, "late", null),
                                new Ref(KIND_LOGICAL_MODEL, "lm", null));
      DeleteResult applied = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_FOLDER, "F", null, false, exact))), bob),
                                     DeleteResponse.class).results());

      assertTrue(applied.ok(), applied.message());
      assertNull(registry().getLogicalModel("lm"));
      assertNull(registry().getLogicalModel("late"));
      assertFalse(Arrays.asList(dm.getFolders()).contains("F"));
   }

   @Test
   void folderDeleteNeedsTheExpectList() throws Exception {
      addFolder("F");

      assertThrows(IllegalArgumentException.class, () -> items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_FOLDER, "F", null, false, null))), bob));
   }

   @Test
   void folderDeleteDeniedForAContainedModelDeletesNothing() throws Exception {
      addFolder("F");
      addLogical("lm", "pv", "F");
      addLogical("locked", "pv", "F");
      LogicalModelService denying = mock(LogicalModelService.class);
      when(denying.checkPermission(anyString(), any(), anyString(), any(), any(), any()))
         .thenAnswer(i -> !"locked".equals(i.getArgument(2)) ||
            i.getArgument(4) == ResourceAction.READ);
      WizDataModelItemController controller = new WizDataModelItemController(
         List.of(), mock(DataModelFolderManagerService.class), dss, denying, repository);

      DeleteResult result = only(ok(controller.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_FOLDER, "F", null, false, List.of(
            new Ref(KIND_LOGICAL_MODEL, "lm", null), new Ref(KIND_LOGICAL_MODEL, "locked", null))))),
                                                      bob), DeleteResponse.class).results());

      assertEquals(CONTAINED_NOT_DELETABLE, result.reason());
      assertTrue(result.message().contains("locked"));
      assertNotNull(registry().getLogicalModel("lm"));
   }

   @Test
   void folderWithAModelOutsideUsingItsViewNeedsForce() throws Exception {
      addFolder("F");
      addView("fv", "F");
      addLogical("outside", "fv", null);
      List<Ref> expected = List.of(new Ref(KIND_PHYSICAL_VIEW, "fv", null));

      // a model outside uses the view: refused with or without force (review r4)
      for(boolean force : new boolean[] { false, true }) {
         DeleteResult refused = only(ok(items.delete(new DeleteRequest(ds, List.of(
            new DeleteItem(KIND_FOLDER, "F", null, force, expected))), bob),
                                        DeleteResponse.class).results());
         assertTrue(Set.of(HAS_DEPENDENCIES, VIEW_IN_USE).contains(refused.reason()),
                    refused.reason());
         assertNotNull(registry().getPartition("fv"));
      }
   }

   @Test
   void deleteRefusesAModelInsideAFolderThatIsAlsoListed() throws Exception {
      addFolder("F");
      addLogical("lm", "pv", "F");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         items.delete(new DeleteRequest(ds, List.of(
            new DeleteItem(KIND_FOLDER, "F", null, false, List.of()),
            new DeleteItem(KIND_LOGICAL_MODEL, "lm", null, false, null))), bob));
      assertTrue(e.getMessage().contains("lm") && e.getMessage().contains("F"), e.getMessage());
   }

   @Test
   void deleteOfAnUnknownOrWrongKindItemIsReportedPerItem() throws Exception {
      addView("free", null);

      List<DeleteResult> results = ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "ghost", null, false, null),
         new DeleteItem(KIND_VPM, "free", null, false, null),
         new DeleteItem(KIND_PHYSICAL_VIEW, "free", null, false, null))), bob),
                                      DeleteResponse.class).results();

      assertEquals(NOT_FOUND, results.get(0).reason());
      assertEquals(WRONG_KIND, results.get(1).reason());
      assertTrue(results.get(2).ok(), "a failing item does not stop the others");
   }

   // -- rename (DM-A8, C2) ----------------------------------------------------------------

   @Test
   void renameOfAViewKeepsItsDescriptionAndReadsBack() throws Exception {
      XPartition view = dm.getPartition("pv");
      view.setDescription("the sales view");
      dm.addPartition(view);

      RenameResponse response = ok(items.rename(
         new RenameRequest(ds, KIND_PHYSICAL_VIEW, "pv", "pv2", null), bob), RenameResponse.class);

      assertEquals("pv2", response.name());
      XDataModel stored = registry();
      assertNull(stored.getPartition("pv"));
      assertEquals("the sales view", stored.getPartition("pv2").getDescription(),
                   "a rename without a description keeps the stored one");
   }

   @Test
   void renameWithADescriptionReplacesIt() throws Exception {
      items.rename(new RenameRequest(ds, KIND_PHYSICAL_VIEW, "pv", "pv2", "new text"), bob);

      assertEquals("new text", registry().getPartition("pv2").getDescription());
   }

   @Test
   void renameOntoATakenNameOfAnyKindNamesTheExistingKindAndChangesNothing() throws Exception {
      addLogical("lm", "pv", null);
      VirtualPrivateModel vpm = new VirtualPrivateModel("vp");
      dm.addVirtualPrivateModel(vpm, true);

      Failure onModel = failure(items.rename(
         new RenameRequest(ds, KIND_PHYSICAL_VIEW, "pv", "lm", null), bob), DUPLICATE_NAME);
      Failure onVpm = failure(items.rename(
         new RenameRequest(ds, KIND_PHYSICAL_VIEW, "pv", "vp", null), bob), DUPLICATE_NAME);

      assertEquals(KIND_LOGICAL_MODEL, onModel.existingKind());
      assertEquals(KIND_VPM, onVpm.existingKind());
      assertNotNull(registry().getPartition("pv"));
   }

   @Test
   void renameRefusesWrongKindMissingBadNameAndFolderKind() throws Exception {
      failure(items.rename(new RenameRequest(ds, KIND_VPM, "pv", "x", null), bob), WRONG_KIND);
      failure(items.rename(new RenameRequest(ds, KIND_PHYSICAL_VIEW, "ghost", "x", null), bob),
              NOT_FOUND);
      assertThrows(IllegalArgumentException.class, () -> items.rename(
         new RenameRequest(ds, KIND_PHYSICAL_VIEW, "pv", "a/b", null), bob));
      assertThrows(IllegalArgumentException.class, () -> items.rename(
         new RenameRequest(ds, KIND_FOLDER, "F", "G", null), bob));
      assertThrows(IllegalArgumentException.class, () -> items.rename(
         new RenameRequest(ds, KIND_PHYSICAL_VIEW, "pv", "Domain", null), bob));
   }

   @Test
   void renameWithoutWriteOnTheViewIsDeniedAndChangesNothing() throws Exception {
      when(dss.checkPermission(anyString(), any(), any(ResourceAction.class), any(Principal.class)))
         .thenReturn(false);

      // the caller cannot see a view it cannot write: not found, never a hint that it exists
      failure(items.rename(new RenameRequest(ds, KIND_PHYSICAL_VIEW, "pv", "pv2", null), bob),
              NOT_FOUND);
      assertNotNull(registry().getPartition("pv"));
   }

   @Test
   void renameOfAKindWithoutAnImplementationSaysSo() throws Exception {
      Failure failure = failure(items.rename(
         new RenameRequest(ds, KIND_VPM, "vp", "vp2", null), bob), NOT_FOUND);
      assertNotNull(failure);
      dm.addVirtualPrivateModel(new VirtualPrivateModel("vp"), true);

      Failure unavailable = failure(items.rename(
         new RenameRequest(ds, KIND_VPM, "vp", "vp2", null), bob), UNKNOWN);
      assertTrue(unavailable.message().contains("not available in this build"));
   }

   // -- review r1 ---------------------------------------------------------------------------

   /** A caller who can read but not write: sees logical models, not VPMs or views. */
   private void readOnlyCaller() throws Exception {
      when(dss.checkPermission(anyString(), any(ResourceAction.class), any(Principal.class)))
         .thenAnswer(i -> i.getArgument(1) == ResourceAction.READ);
      when(dss.checkPermission(anyString(), any(), any(ResourceAction.class), any()))
         .thenAnswer(i -> i.getArgument(2) == ResourceAction.READ);
   }

   @Test
   void renameByAReadOnlyCallerNamesNeitherTheKindNorTheClash() throws Exception {
      addLogical("SalesModel", "pv", null);
      dm.addVirtualPrivateModel(new VirtualPrivateModel("SecretVpm"), true);
      LogicalModelService readOnly = mock(LogicalModelService.class);
      when(readOnly.checkPermission(anyString(), any(), anyString(), any(), any(), any()))
         .thenAnswer(i -> i.getArgument(4) == ResourceAction.READ);
      WizDataModelItemController controller = new WizDataModelItemController(
         List.of(new FakeLogicalOps(readOnly) {
            @Override
            public boolean mayRename(Ctx ctx, ModelRef ref) {
               return false;
            }
         }), mock(DataModelFolderManagerService.class), dss, readOnly, repository);

      // may not rename the model: the answer does not depend on whether the new name is taken
      SecurityException taken = assertThrows(SecurityException.class, () -> controller.rename(
         new RenameRequest(ds, KIND_LOGICAL_MODEL, "SalesModel", "SecretVpm", null), bob));
      SecurityException free = assertThrows(SecurityException.class, () -> controller.rename(
         new RenameRequest(ds, KIND_LOGICAL_MODEL, "SalesModel", "NoSuchName", null), bob));
      assertEquals(taken.getMessage(), free.getMessage());
   }

   @Test
   void renameOfAnInvisibleItemAnswersLikeAMissingOneWithNoKind() throws Exception {
      dm.addVirtualPrivateModel(new VirtualPrivateModel("SecretVpm"), true);
      when(dss.checkPermission(eq(ds), eq(ResourceAction.WRITE), any(Principal.class)))
         .thenReturn(false);

      Failure invisible = failure(items.rename(
         new RenameRequest(ds, KIND_LOGICAL_MODEL, "SecretVpm", "x", null), bob), NOT_FOUND);
      Failure missing = failure(items.rename(
         new RenameRequest(ds, KIND_LOGICAL_MODEL, "NoSuch", "x", null), bob), NOT_FOUND);

      assertEquals(missing.message().replace("NoSuch", "SecretVpm"), invisible.message());
      assertNull(invisible.existingKind());
      assertFalse(invisible.message().contains("is a vpm"));
   }

   @Test
   void aClashWithAnInvisibleKindIsRefusedWithoutNamingIt() throws Exception {
      addLogical("lm", "pv", null);
      dm.addVirtualPrivateModel(new VirtualPrivateModel("SecretVpm"), true);
      when(dss.checkPermission(eq(ds), eq(ResourceAction.WRITE), any(Principal.class)))
         .thenReturn(false);
      // the caller may rename lm but cannot see VPMs (no WRITE on the source)

      Failure clash = failure(items.rename(
         new RenameRequest(ds, KIND_LOGICAL_MODEL, "lm", "SecretVpm", null), bob), DUPLICATE_NAME);

      assertNull(clash.existingKind());
      assertFalse(clash.message().toLowerCase().contains("vpm named"), clash.message());
      assertNotNull(registry().getLogicalModel("lm"), "nothing was renamed");
   }

   @Test
   void browseOfAFolderTheCallerCannotReadIsFolderNotFoundNotAnEmptyList() throws Exception {
      addFolder("Secret");
      when(dss.checkDataModelFolderPermission(eq(ds + "/Secret"), any(ResourceAction.class), any()))
         .thenReturn(false);

      Failure hidden = failure(browse.browse(new BrowseRequest(ds, "Secret", null, null, null), bob),
                               FOLDER_NOT_FOUND);
      Failure missing = failure(browse.browse(new BrowseRequest(ds, "Nope", null, null, null), bob),
                                FOLDER_NOT_FOUND);
      assertEquals(missing.message().replace("Nope", "Secret"), hidden.message());
   }

   @Test
   void aSourceWithoutReadIsTheSameForBrowseAndForAMissingSource() throws Exception {
      when(dss.checkPermission(eq(ds), eq(ResourceAction.READ), any(Principal.class)))
         .thenReturn(false);
      when(repository.getDataSource("xmla1")).thenReturn(
         mock(inetsoft.uql.xmla.XMLADataSource.class));

      Failure real = failure(browse.browse(new BrowseRequest(ds, null, null, null, null), bob),
                             NOT_FOUND);
      Failure missing = failure(browse.browse(new BrowseRequest("nosuch", null, null, null, null),
                                              bob), NOT_FOUND);
      when(dss.checkPermission(eq("xmla1"), eq(ResourceAction.READ), any(Principal.class)))
         .thenReturn(false);
      Failure typed = failure(browse.browse(new BrowseRequest("xmla1", null, null, null, null),
                                            bob), NOT_FOUND);

      assertEquals(missing.message().replace("nosuch", ds), real.message());
      assertEquals(missing.message().replace("nosuch", "xmla1"), typed.message(),
                   "the type of a source the caller cannot read is not revealed");
   }

   @Test
   void dependenciesOfAFolderTheCallerMayNotDeleteCarryNothingButTheDenial() throws Exception {
      addFolder("F");
      addLogical("lm", "pv", "F");
      addView("fv", "F");
      when(dss.checkPermission(eq(ds), eq("F"), eq(ResourceAction.DELETE), any(Principal.class)))
         .thenReturn(false);

      DepItem denied = only(ok(items.dependencies(new DependenciesRequest(ds, List.of(
         new Ref(KIND_FOLDER, "F", null))), bob), DependenciesResponse.class).items());

      assertEquals(PERMISSION_DENIED, denied.reason());
      assertTrue(denied.contains().isEmpty(), "no folder contents in a denied answer");
      assertTrue(denied.dependents().isEmpty());
      assertTrue(denied.alsoDeleted().isEmpty());
   }

   @Test
   void dependenciesListOnlyWhatTheCallerMaySee() throws Exception {
      addFolder("F");
      addLogical("seen", "pv", "F");
      addLogical("hidden", "pv", "F");
      addView("fv", "F");
      addLogical("outsideSeen", "fv", null);
      addLogical("outsideHidden", "fv", null);
      dm.addVirtualPrivateModel(vpmUsing("fv"), true);
      LogicalModelService denying = mock(LogicalModelService.class);
      when(denying.checkPermission(anyString(), any(), anyString(), any(), any(), any()))
         .thenAnswer(i -> !i.<String>getArgument(2).contains("idden"));
      // the caller cannot see VPMs
      when(dss.checkPermission(eq(ds), eq(ResourceAction.WRITE), any(Principal.class)))
         .thenReturn(false);
      WizDataModelItemController controller = new WizDataModelItemController(
         List.of(new WizPhysicalViewOps(dss, mock(PhysicalModelManagerService.class))),
         mock(DataModelFolderManagerService.class), dss, denying, repository);

      DepItem folder = only(ok(controller.dependencies(new DependenciesRequest(ds, List.of(
         new Ref(KIND_FOLDER, "F", null))), bob), DependenciesResponse.class).items());
      DepItem view = only(ok(controller.dependencies(new DependenciesRequest(ds, List.of(
         new Ref(KIND_PHYSICAL_VIEW, "fv", null))), bob), DependenciesResponse.class).items());

      assertEquals(List.of("seen"), folder.contains().stream().filter(r ->
         KIND_LOGICAL_MODEL.equals(r.kind())).map(Ref::name).toList(),
                   "a model the caller cannot read is not listed in the folder");
      assertFalse(folder.deletable(), "an item the caller cannot see blocks the delete");
      assertEquals(List.of("outsideSeen", "2 item(s) the caller may not see"),
                   view.dependents().stream().map(Dependent::name).toList(),
                   "neither the unreadable model nor the VPM is named, but they still count");
      assertEquals(List.of("outsideSeen"),
                   view.danglingAfter().stream().map(Dangling::name).toList());
   }

   @Test
   void folderDeleteBlockedByAnInvisibleModelNamesNoModel() throws Exception {
      addFolder("F");
      addLogical("seen", "pv", "F");
      addLogical("hidden", "pv", "F");
      LogicalModelService denying = mock(LogicalModelService.class);
      when(denying.checkPermission(anyString(), any(), anyString(), any(), any(), any()))
         .thenAnswer(i -> !"hidden".equals(i.getArgument(2)));
      WizDataModelItemController controller = new WizDataModelItemController(
         List.of(), mock(DataModelFolderManagerService.class), dss, denying, repository);

      DeleteResult result = only(ok(controller.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_FOLDER, "F", null, false, List.of(
            new Ref(KIND_LOGICAL_MODEL, "seen", null))))), bob), DeleteResponse.class).results());

      assertEquals(CONTAINED_NOT_DELETABLE, result.reason());
      assertFalse(result.message().contains("hidden"), result.message());
      assertTrue(result.message().contains("1 item(s) the caller may not see"), result.message());
      assertNotNull(registry().getLogicalModel("hidden"));
   }

   private static VirtualPrivateModel vpmUsing(String view) {
      VirtualPrivateModel vpm = new VirtualPrivateModel("vp");
      inetsoft.uql.erm.vpm.VpmCondition condition = new inetsoft.uql.erm.vpm.VpmCondition();
      condition.setName("c");
      condition.setType(inetsoft.uql.erm.vpm.VpmCondition.PHYSICMODEL);
      condition.setTable(view);
      vpm.addCondition(condition);
      return vpm;
   }

   @Test
   void aServerFailureDuringDeleteIsNeverReturnedAsAMessage() throws Exception {
      addLogical("lm", "pv", null);
      String secret = "jdbc:mysql://u:Sup3rSecret@h/x";
      WizModelKindOps boom = new FakeLogicalOps(lms) {
         @Override
         public OpResult delete(Ctx ctx, ModelRef ref, boolean force) throws Exception {
            throw new IllegalStateException("could not connect " + secret);
         }
      };
      WizDataModelItemController controller = new WizDataModelItemController(
         List.of(boom), mock(DataModelFolderManagerService.class), dss, lms, repository);

      DeleteResult result = only(ok(controller.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_LOGICAL_MODEL, "lm", null, false, null))), bob),
                                    DeleteResponse.class).results());

      assertFalse(result.ok());
      assertEquals(UNKNOWN, result.reason());
      assertFalse(result.message().contains("Sup3rSecret"), result.message());
      assertFalse(result.message().contains("jdbc:"), result.message());
      assertTrue(result.message().contains("lm"), "the item is named: " + result.message());
   }

   @Test
   void aNonBusinessExceptionInARouteBodyIsAGenericFailureNotItsText() throws Exception {
      Object body = WizDataModelErrors.run(() -> {
         throw new IllegalStateException("open failed jdbc:mysql://u:Sup3rSecret@h/x");
      });

      Failure failure = ok(body, Failure.class);
      assertEquals(UNKNOWN, failure.reason());
      assertFalse(failure.message().contains("Sup3rSecret"), failure.message());
      // business and mapped exceptions are unchanged
      assertEquals(NOT_FOUND, ok(WizDataModelErrors.run(() -> {
         throw new java.io.FileNotFoundException("D/x");
      }), Failure.class).reason());
      assertThrows(IllegalArgumentException.class, () -> WizDataModelErrors.run(() -> {
         throw new IllegalArgumentException("bad");
      }));
      assertThrows(SecurityException.class, () -> WizDataModelErrors.run(() -> {
         throw new SecurityException("no");
      }));
      assertThrows(UnsupportedOperationException.class, () -> WizDataModelErrors.run(() -> {
         throw new UnsupportedOperationException("later");
      }));
   }

   @Test
   void moveThatMovesNothingWritesNothing() throws Exception {
      addFolder("F");
      addLogical("lm", "pv", "F");
      reset(repository);
      when(repository.getDataSource(ds)).thenReturn(mock(JDBCDataSource.class));
      when(repository.getDataModel(ds)).thenReturn(dm);

      // already in F: nothing to move, nothing to write
      MoveResponse already = ok(folders.move(new MoveRequest(ds, "F", List.of(
         new Ref(KIND_LOGICAL_MODEL, "lm", null))), bob), MoveResponse.class);
      MoveResponse invalid = ok(folders.move(new MoveRequest(ds, "F", List.of(
         new Ref(KIND_VPM, "x", null))), bob), MoveResponse.class);

      assertTrue(already.results().get(0).ok());
      assertEquals(INVALID, invalid.results().get(0).reason());
      verify(repository, never()).updateDataModel(any());
   }

   private PhysicalModelManagerService lmsPmms() {
      return new PhysicalModelManagerService(
         dss, mock(PhysicalModelService.class), mock(RuntimePartitionService.class),
         new PhysicalGraphService(), repository, null, mock(DependencyHandler.class),
         transformHandler);
   }

   @Test
   void viewInUseByAnInvisibleModelIsRefusedWithoutNamingIt() throws Exception {
      addView("V", null);
      addLogical("SecretModel", "V", null);
      LogicalModelService denying = mock(LogicalModelService.class);
      when(denying.checkPermission(anyString(), any(), anyString(), any(), any(), any()))
         .thenAnswer(i -> !"SecretModel".equals(i.getArgument(2)));
      WizDataModelItemController controller = new WizDataModelItemController(
         List.of(new WizPhysicalViewOps(dss, mock(PhysicalModelManagerService.class))),
         mock(DataModelFolderManagerService.class), dss, denying, repository);

      DeleteResult result = only(ok(controller.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "V", null, false, null))), bob),
                                    DeleteResponse.class).results());

      // still refused: the decision is made on every user, visible or not
      assertEquals(VIEW_IN_USE, result.reason());
      assertFalse(result.message().contains("SecretModel"), result.message());
      assertTrue(result.dependents().stream().noneMatch(d -> "SecretModel".equals(d.name())),
                 String.valueOf(result.dependents()));
      assertNotNull(registry().getPartition("V"), "nothing was deleted");
   }

   @Test
   void aMoveThatThrowsWritesNothing() throws Exception {
      addFolder("F");
      addLogical("lm", "pv", null);
      reset(repository);
      when(repository.getDataSource(ds)).thenReturn(mock(JDBCDataSource.class));
      when(repository.getDataModel(ds)).thenReturn(dm);
      DatabaseModelBrowserService failing = mock(DatabaseModelBrowserService.class);
      doThrow(new SecurityException("denied by the service")).when(failing)
         .moveDataModels(anyString(), any(), any(), any());
      WizDataModelFolderController controller = new WizDataModelFolderController(
         failing, mock(DataModelFolderManagerService.class), dss, lms, security, repository);

      MoveResponse response = ok(controller.move(new MoveRequest(ds, "F", List.of(
         new Ref(KIND_LOGICAL_MODEL, "lm", null))), bob), MoveResponse.class);

      assertEquals(PERMISSION_DENIED, response.results().get(0).reason());
      verify(repository, never()).updateDataModel(any());
   }

   // -- review r4 -----------------------------------------------------------------------------

   @Test
   void aViewIsNeverForcedEvenWhenItsUserCannotBeDeletedInTheSameCall() throws Exception {
      addView("V", null);
      addLogical("L", "V", null);
      WizModelKindOps denyDeleteOfL = new FakeLogicalOps(lms) {
         @Override
         public boolean mayDelete(Ctx ctx, ModelRef ref) {
            return false;
         }
      };
      WizDataModelItemController controller = new WizDataModelItemController(
         List.of(new WizPhysicalViewOps(dss, lmsPmms()), denyDeleteOfL),
         mock(DataModelFolderManagerService.class), dss, lms, repository);

      List<DeleteResult> results = ok(controller.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_LOGICAL_MODEL, "L", null, false, null),
         new DeleteItem(KIND_PHYSICAL_VIEW, "V", null, true, null))), bob),
                                      DeleteResponse.class).results();

      assertEquals(PERMISSION_DENIED, results.get(0).reason());
      assertEquals(VIEW_IN_USE, results.get(1).reason(),
                   "the service refuses the view: it is never forced at apply time");
      assertNotNull(registry().getPartition("V"));
      assertNotNull(registry().getLogicalModel("L"));
   }

   @Test
   void aViewWithOnlyARecordedSheetDependentIsDeletedWithForce() throws Exception {
      addView("free", null);
      seedDependency(AssetEntry.Type.PARTITION, ds + "/free", "wsUses");

      DeleteResult result = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "free", null, true, null))), bob),
                                    DeleteResponse.class).results());

      assertTrue(result.ok(), result.message());
      assertNull(registry().getPartition("free"));
   }

   @Test
   void aViewWithASheetAndAModelUserIsRefusedWithForce() throws Exception {
      addView("used", null);
      addLogical("lm", "used", null);
      seedDependency(AssetEntry.Type.PARTITION, ds + "/used", "wsUses");

      DeleteResult result = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "used", null, true, null))), bob),
                                    DeleteResponse.class).results());

      assertEquals(VIEW_IN_USE, result.reason());
      assertNotNull(registry().getPartition("used"));
   }

   @Test
   void aFolderWhoseViewIsUsedByAModelOutsideIsNeverForced() throws Exception {
      addFolder("F");
      addView("fv", "F");
      addLogical("outside", "fv", null);
      List<Ref> expected = List.of(new Ref(KIND_PHYSICAL_VIEW, "fv", null));

      DeleteResult result = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_FOLDER, "F", null, true, expected))), bob),
                                    DeleteResponse.class).results());

      assertEquals(VIEW_IN_USE, result.reason());
      assertTrue(result.message().contains("outside"), result.message());
      assertNotNull(registry().getPartition("fv"), "nothing was deleted");
      assertTrue(Arrays.asList(dm.getFolders()).contains("F"));
   }

   @Test
   void aFolderWhoseOnlyOutsideDependentsAreNotModelsIsDeletedWithForceAndExpect() throws Exception {
      addFolder("F");
      XLogicalModel model = addLogical("lm", "pv", "F");
      model.addOuterDependency(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "recorded", null));
      dm.addLogicalModel(model);
      List<Ref> expected = List.of(new Ref(KIND_LOGICAL_MODEL, "lm", null));

      DeleteResult plain = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_FOLDER, "F", null, false, expected))), bob),
                                   DeleteResponse.class).results());
      assertEquals(HAS_DEPENDENCIES, plain.reason(), "without force the dependents are surfaced");

      DeleteResult forced = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_FOLDER, "F", null, true, expected))), bob),
                                    DeleteResponse.class).results());
      assertTrue(forced.ok(), forced.message());
      assertNull(registry().getLogicalModel("lm"));
   }

   // -- Bug #82067: an extended logical model is resolved by its parent -------------------------

   private WizDataModelItemController withParentAwareOps() {
      return new WizDataModelItemController(
         List.of(new WizPhysicalViewOps(dss, mock(PhysicalModelManagerService.class)), new ParentAwareOps(lms)),
         mock(DataModelFolderManagerService.class), dss, lms, repository);
   }

   private void addExtended(String base, String child) {
      XLogicalModel baseModel = addLogical(base, "pv", null);
      baseModel.addLogicalModel(new XLogicalModel(child), true);
   }

   @Test
   void dependenciesOfAnExtendedLogicalModelWithParentAreFoundNotMissing() throws Exception {
      addExtended("base", "kid");
      WizDataModelItemController controller = withParentAwareOps();

      DepItem item = only(ok(controller.dependencies(new DependenciesRequest(ds, List.of(
         new Ref(KIND_LOGICAL_MODEL, "kid", "base"))), bob), DependenciesResponse.class).items());

      assertTrue(item.exists(), "an extended model that the lookup finds exists");
      assertNull(item.reason());
      assertTrue(item.deletable());
      assertTrue(item.alsoDeleted().isEmpty(), "an extended model has no children");
   }

   @Test
   void dependenciesOfAnExtendedLogicalModelWithoutParentOrWithAWrongParentStayNotFound()
      throws Exception
   {
      addExtended("base", "kid");
      addLogical("other", "pv", null);
      WizDataModelItemController controller = withParentAwareOps();

      DependenciesResponse response = ok(controller.dependencies(new DependenciesRequest(ds,
         List.of(new Ref(KIND_LOGICAL_MODEL, "kid", null),
                 new Ref(KIND_LOGICAL_MODEL, "kid", "other"),
                 new Ref(KIND_LOGICAL_MODEL, "kid", "nobase"))), bob), DependenciesResponse.class);

      for(DepItem item : response.items()) {
         assertFalse(item.exists(), item.name());
         assertEquals(NOT_FOUND, item.reason());
      }
   }

   @Test
   void dependenciesOfABaseModelAreUnchangedAndListItsExtendedChildren() throws Exception {
      addExtended("base", "kid");

      DepItem item = only(ok(withParentAwareOps().dependencies(new DependenciesRequest(ds,
         List.of(new Ref(KIND_LOGICAL_MODEL, "base", null))), bob),
                             DependenciesResponse.class).items());

      assertTrue(item.exists());
      assertEquals(List.of("kid"), item.alsoDeleted().stream().map(AlsoDeleted::name).toList());
   }

   @Test
   void deleteOfAnExtendedLogicalModelWithParentReachesExtendedUnsupported() throws Exception {
      addExtended("base", "kid");

      DeleteResult result = only(ok(withParentAwareOps().delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_LOGICAL_MODEL, "kid", "base", false, null))), bob),
                                    DeleteResponse.class).results());

      assertFalse(result.ok());
      assertEquals(WizDataModelWire.EXTENDED_UNSUPPORTED, result.reason());
      assertNotNull(registry().getLogicalModel("base"), "nothing was deleted");
      assertNotNull(registry().getLogicalModel("base").getLogicalModel("kid"),
                    "the extended model is still under its base");
   }

   @Test
   void deleteOfAnExtendedLogicalModelWithoutParentIsStillNotFound() throws Exception {
      addExtended("base", "kid");

      DeleteResult result = only(ok(withParentAwareOps().delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_LOGICAL_MODEL, "kid", null, false, null))), bob),
                                    DeleteResponse.class).results());

      assertFalse(result.ok());
      assertEquals(NOT_FOUND, result.reason());
   }

   @Test
   void deleteOfABaseModelStillWorks() throws Exception {
      addLogical("plain", "pv", null);

      DeleteResult result = only(ok(withParentAwareOps().delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_LOGICAL_MODEL, "plain", null, false, null))), bob),
                                    DeleteResponse.class).results());

      assertTrue(result.ok(), result.message());
      assertNull(registry().getLogicalModel("plain"));
   }

   // -- Bug #82069: an extended physical view is resolved by its parent --------------------------

   private void addExtendedView(String base, String child) {
      if(dm.getPartition(base) == null) {
         addView(base, null);
      }

      dm.getPartition(base).addPartition(new XPartition(child), false);
   }

   @Test
   void dependenciesOfAnExtendedPhysicalViewWithParentAreFoundWithNoUsers() throws Exception {
      addExtendedView("pv", "viewKid");
      assertNull(dm.getPartition("viewKid"), "an extended view is not in the base registry");

      DepItem item = only(ok(items.dependencies(new DependenciesRequest(ds, List.of(
         new Ref(KIND_PHYSICAL_VIEW, "viewKid", "pv"))), bob), DependenciesResponse.class).items());

      assertTrue(item.exists(), "an extended view that the lookup finds exists");
      assertNull(item.reason());
      assertTrue(item.deletable());
      assertTrue(item.dependents().isEmpty());
   }

   @Test
   void anExtendedViewNamedLikeAUsedBaseViewIsNotBlockedByThatBasesUsers() throws Exception {
      // logical model lm uses the BASE view "pv"; the extended view "pv" under another base is a
      // different item that nothing uses
      addLogical("lm", "pv", null);
      addExtendedView("other", "pv");

      DepItem item = only(ok(items.dependencies(new DependenciesRequest(ds, List.of(
         new Ref(KIND_PHYSICAL_VIEW, "pv", "other"))), bob), DependenciesResponse.class).items());

      assertTrue(item.exists());
      assertNull(item.reason(), "the base view's users are not this view's users");
      assertTrue(item.dependents().isEmpty());
      assertTrue(item.danglingAfter().isEmpty());
   }

   @Test
   void dependenciesOfAnExtendedPhysicalViewWithoutParentOrWithAWrongParentStayNotFound()
      throws Exception
   {
      addExtendedView("pv", "viewKid");
      addView("elsewhere", null);

      for(String parent : new String[] { null, "elsewhere" }) {
         DepItem item = only(ok(items.dependencies(new DependenciesRequest(ds, List.of(
            new Ref(KIND_PHYSICAL_VIEW, "viewKid", parent))), bob),
                                DependenciesResponse.class).items());
         assertFalse(item.exists());
         assertEquals(NOT_FOUND, item.reason());
      }
   }

   @Test
   void deleteOfAnExtendedPhysicalViewWithParentRemovesOnlyThatView() throws Exception {
      addExtendedView("pv", "viewKid");
      PhysicalModelManagerService pmms = mock(PhysicalModelManagerService.class);
      when(pmms.removeModel(eq(ds), any(), eq("viewKid"), eq("pv"), anyBoolean(), any()))
         .thenReturn(true);
      WizDataModelItemController controller = new WizDataModelItemController(
         List.of(new WizPhysicalViewOps(dss, pmms), new FakeLogicalOps(lms)),
         mock(DataModelFolderManagerService.class), dss, lms, repository);

      DeleteResult result = only(ok(controller.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "viewKid", "pv", false, null))), bob),
                                    DeleteResponse.class).results());

      assertTrue(result.ok(), result.message());
      assertEquals(List.of(new Ref(KIND_PHYSICAL_VIEW, "viewKid", "pv")), result.deleted());
      verify(pmms).removeModel(eq(ds), any(), eq("viewKid"), eq("pv"), anyBoolean(), any());
      verify(pmms, never()).removeModel(eq(ds), any(), eq("pv"), any(), anyBoolean(), any());
   }

   @Test
   void deleteOfAnExtendedPhysicalViewWithoutParentIsStillNotFound() throws Exception {
      addExtendedView("pv", "viewKid");

      DeleteResult result = only(ok(items.delete(new DeleteRequest(ds, List.of(
         new DeleteItem(KIND_PHYSICAL_VIEW, "viewKid", null, false, null))), bob),
                                    DeleteResponse.class).results());

      assertFalse(result.ok());
      assertEquals(NOT_FOUND, result.reason());
   }

   // -- a logical-model SPI stand-in (J2 owns the real one) ---------------------------------

   private static class FakeLogicalOps implements WizModelKindOps {
      FakeLogicalOps(LogicalModelService service) {
         this.service = service;
      }

      @Override
      public Kind kind() {
         return Kind.LOGICAL_MODEL;
      }

      @Override
      public Optional<ModelInfo> find(Ctx ctx, ModelRef ref) {
         XLogicalModel model = ctx.dataModel().getLogicalModel(ref.name());
         return model == null ? Optional.empty() : Optional.of(new ModelInfo(
            Kind.LOGICAL_MODEL, ref.name(), model.getFolder(), null, false,
            model.getDescription()));
      }

      @Override
      public boolean mayDelete(Ctx ctx, ModelRef ref) {
         return true;
      }

      @Override
      public boolean mayRename(Ctx ctx, ModelRef ref) {
         return true;
      }

      @Override
      public List<DependentRef> dependents(Ctx ctx, ModelRef ref) {
         return List.of();
      }

      @Override
      public OpResult rename(Ctx ctx, ModelRef ref, String newName, String description)
         throws Exception
      {
         String folder = ctx.dataModel().getLogicalModel(ref.name()).getFolder();
         service.renameModel(ctx.database(), folder, newName, ref.name(), description,
                             ctx.principal());
         return new OpResult(true, null, null, List.of());
      }

      @Override
      public OpResult delete(Ctx ctx, ModelRef ref, boolean force) throws Exception {
         String folder = ctx.dataModel().getLogicalModel(ref.name()).getFolder();
         service.removeModel(ctx.database(), folder, ref.name(), null, ctx.principal());
         return new OpResult(true, null, null, List.of(ref));
      }

      private final LogicalModelService service;
   }

   /** The real logical ops look an extended model up through its parent and refuse its delete. */
   private static class ParentAwareOps extends FakeLogicalOps {
      ParentAwareOps(LogicalModelService service) {
         super(service);
      }

      @Override
      public Optional<ModelInfo> find(Ctx ctx, ModelRef ref) {
         XLogicalModel model = WizLogicalModelOps.lookup(ctx.dataModel(), ref);
         return model == null ? Optional.empty() : Optional.of(new ModelInfo(
            Kind.LOGICAL_MODEL, ref.name(), model.getFolder(), ref.parent(),
            ref.parent() != null && !ref.parent().isEmpty(), model.getDescription()));
      }

      @Override
      public OpResult delete(Ctx ctx, ModelRef ref, boolean force) throws Exception {
         if(ref.parent() != null && !ref.parent().isEmpty()) {
            return new OpResult(false, WizDataModelWire.EXTENDED_UNSUPPORTED,
                                "extended models are not supported by this tool in this version",
                                List.of());
         }

         return super.delete(ctx, ref, force);
      }
   }
}
