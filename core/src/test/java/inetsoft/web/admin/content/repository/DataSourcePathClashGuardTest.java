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
package inetsoft.web.admin.content.repository;

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.uql.asset.sync.RenameDependencyInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.*;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77725: the guards on a data source and a data source folder at the same path (a "clash")
 * at the entry points that {@code DataSourcePathClashOperationsTest} doesn't reach: the portal
 * move, folder rename, folder delete, data source delete and multi-delete, and the removal of a
 * deleted self-organization user's resources. Also the states that must still be allowed (the
 * other side holds nothing, or only entries of its own), and the save of a data source with an
 * additional connection that can't be read.
 * <p>
 * The registry, the repository, the EM service and the portal services are the real ones. The
 * permissions are kept in a map behind a mocked security engine and provider.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourcePathClashGuardTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourcePathClashGuardTest {
   private static final String URL = "jdbc:derby:memory:bug77725guard;create=true";
   // a type that isn't registered, a data source of this type can't be read
   private static final String GONE = "Bug77725GuardGone";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private final Map<String, Permission> store = new HashMap<>();
   private MockedStatic<SecurityEngine> securityStatic;
   private SecurityEngine security;
   private RepositoryObjectService objectService;
   private DataSourceBrowserService browserService;
   private DatasourcesService datasourcesService;
   private RenameTransformHandler transformHandler;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      store.clear();
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.isVirtual()).thenReturn(false);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      doAnswer(inv -> store.remove(key(inv.getArgument(0), inv.getArgument(1))))
         .when(provider).removePermission(any(ResourceType.class), anyString());
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(security.getSecurityProvider()).thenReturn(provider);
      when(security.isSecurityEnabled()).thenReturn(true);
      when(security.getPermission(any(ResourceType.class), anyString()))
         .thenAnswer(inv -> store.get(key(inv.getArgument(0), inv.getArgument(1))));
      doAnswer(inv -> store.put(key(inv.getArgument(0), inv.getArgument(1)), inv.getArgument(2)))
         .when(security).setPermission(any(ResourceType.class), anyString(), any());
      doAnswer(inv -> store.remove(key(inv.getArgument(0), inv.getArgument(1))))
         .when(security).removePermission(any(ResourceType.class), anyString());
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);

      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.DATA_SOURCE_FOLDER, inv.<String>getArgument(1)));
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      transformHandler = mock(RenameTransformHandler.class);
      objectService = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class), provider,
         permissions, repository, mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), registry, mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(DependencyHandler.class), transformHandler,
         repletRegistries, mock(DashboardRegistryManager.class));
      browserService = new DataSourceBrowserService(
         security, objectService, repository, mock(DataSourceService.class), registry,
         mock(Config.class), transformHandler);
      datasourcesService = new DatasourcesService(
         repository, security, mock(DataSourceStatusService.class), registry,
         mock(Config.class));
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      registry.clearCache();
      securityStatic.close();
   }

   // ---- the portal entry points ----

   // a benign folder first and then a clashed one: nothing is moved
   @Test
   void portalMultiFolderMoveIsRefusedAsAWhole() {
      addFolder("pmDest");
      addFolder("pmA");
      addSource("pmA/pmX");
      grant(ResourceType.DATA_SOURCE, "pmA/pmX");
      clash("pmF", true, false);

      assertRefused(dataSourceNotEmpty("pmF"), () -> browserService.moveDataSource(new MoveCommand[] {
         move("pmA", "pmDest/pmA", PortalDataType.DATA_SOURCE_FOLDER),
         move("pmF", "pmDest/pmF", PortalDataType.DATA_SOURCE_FOLDER) }, principal),
                    "pmA", "pmF", "pmDest");
   }

   @Test
   void portalDataSourceMoveIsRefused() {
      addFolder("pdDest");
      clash("pdP", true, false);

      assertRefused(folderNotEmpty("pdP"), () -> browserService.moveDataSource(new MoveCommand[] {
         move("pdP", "pdDest/pdP", PortalDataType.DATA_SOURCE) }, principal), "pdP", "pdDest");
   }

   @Test
   void portalFolderRenameIsRefused() {
      clash("prF", true, false);

      assertRefused(dataSourceNotEmpty("prF"),
                    () -> browserService.renameFolder("prF", "prG", "prF", "prG", principal),
                    "prF", "prG");
   }

   @Test
   void portalFolderDeleteIsRefused() {
      clash("pfP", true, false);

      assertRefused(dataSourceNotEmpty("pfP"),
                    () -> browserService.deleteDataSourceFolder("pfP", "pfP", true, principal),
                    "pfP");
   }

   @Test
   void portalDataSourceDeleteIsRefused() {
      clash("psP", true, false);

      assertRefused(folderNotEmpty("psP"),
                    () -> datasourcesService.deleteDataSource("psP", "psP", true), "psP");
   }

   // the selected delete and its dependency check: a benign data source first and then a
   // clashed one, nothing is deleted
   @Test
   void portalSelectedDeleteIsRefusedAsAWhole() {
      addSource("ssOther");
      grant(ResourceType.DATA_SOURCE, "ssOther");
      clash("ssP", true, false);
      DataSourceController controller = new DataSourceController(
         datasourcesService, browserService, mock(DatabaseDatasourcesService.class), security,
         mock(DataSourceStatusService.class), mock(FileSystemService.class));
      SelectedDataSourcesRequest request = ImmutableSelectedDataSourcesRequest.builder()
         .dataSources(List.of(item("ssOther"), item("ssP")))
         .folders(List.of())
         .build();

      assertRefused(folderNotEmpty("ssP"),
                    () -> controller.checkDsOuterDependenciesSelected(request, principal),
                    "ssOther", "ssP");
      assertRefused(folderNotEmpty("ssP"), () -> controller.deleteDataSources(request, principal),
                    "ssOther", "ssP");
   }

   @Test
   void portalSelectedFolderDeleteIsRefused() {
      clash("sfP", true, false);
      DataSourceController controller = new DataSourceController(
         datasourcesService, browserService, mock(DatabaseDatasourcesService.class), security,
         mock(DataSourceStatusService.class), mock(FileSystemService.class));
      SelectedDataSourcesRequest request = ImmutableSelectedDataSourcesRequest.builder()
         .dataSources(List.of())
         .folders(List.of(item("sfP")))
         .build();

      assertRefused(dataSourceNotEmpty("sfP"),
                    () -> controller.deleteDataSources(request, principal), "sfP");
   }

   // ---- the resources of a deleted self-organization user ----

   // the refusal is logged and the user delete goes on; a resource without a clash is removed
   @Test
   void selfResourceRemovalKeepsAClashAndGoesOn() {
      clash("isP", true, false);
      addSource("isOther");
      registry.clearCache();
      IdentityService service = identityService();
      List<String> before = state("isP");

      assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
         service, "removeSelfResource", ResourceType.DATA_SOURCE, "isP"));
      assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
         service, "removeSelfResource", ResourceType.DATA_SOURCE_FOLDER, "isP"));
      ReflectionTestUtils.invokeMethod(
         service, "removeSelfResource", ResourceType.DATA_SOURCE, "isOther");

      assertEquals(before, state("isP"));
      assertFalse(containsDataSource("isOther"));
   }

   // ---- allowed: the other side holds nothing ----

   // the data source owns nothing under the path: the folder and its data sources are deleted,
   // the data source is kept
   @Test
   void folderDeleteWhenTheDataSourceOwnsNothing() throws Exception {
      for(String path : new String[] { "fnR", "fnX", "fnE" }) {
         addFolder(path);
         addFolder(path + "/" + path + "Sub");
         addSource(path + "/" + path + "M");
         addSource(path + "/" + path + "Sub/" + path + "Y");
         addSource(path);
         grant(ResourceType.DATA_SOURCE, path);
         grant(ResourceType.DATA_SOURCE, path + "/" + path + "M");
      }

      registry.clearCache();
      registry.removeDataSourceFolder("fnR");
      repository.removeDataSourceFolder("fnX");
      assertNull(objectService.deleteNodes(
         new TreeNodeInfo[] { node("fnE", RepositoryEntry.DATA_SOURCE_FOLDER) }, principal, true,
         false));

      registry.clearCache();

      for(String path : new String[] { "fnR", "fnX", "fnE" }) {
         assertStoredName(path, path);
         assertNotNull(perm(ResourceType.DATA_SOURCE, path));
         assertNull(registry.getDataSourceFolder(path), path);
         assertEquals(List.of("DATA_MODEL " + path + " [" + path + " []]",
                              "DATA_SOURCE " + path + " [" + path + "]",
                              "grant DATA_SOURCE|" + path), state(path), path);
      }
   }

   // the clash below the folder has an empty folder side: the folder is renamed with the data
   // source, its additional connection and its permission
   @Test
   void ancestorRenameWithAnEmptyFolderSide() throws Exception {
      for(String path : new String[] { "aeR", "aeX", "aeE" }) {
         addFolder(path);
         clash(path + "/" + path + "P", false, false);
      }

      addFolder("aeDest");
      registry.renameDataSourceFolder("aeR", "aeRn");
      repository.updateDataSourceFolder(new DataSourceFolder("aeXn", LocalDateTime.now(), null),
                                        "aeX");
      objectService.moveFiles(move("aeDest", tree("aeE", RepositoryEntry.DATA_SOURCE_FOLDER)),
                              true, principal);

      registry.clearCache();

      for(String[] paths : new String[][] {
         { "aeR", "aeRn" }, { "aeX", "aeXn" }, { "aeE", "aeDest/aeE" } })
      {
         String parent = paths[1] + "/" + paths[0] + "P";
         String additional = paths[0] + "PAdd";
         assertStoredName(parent, parent);
         assertStoredName(parent + "/" + additional, additional);
         assertNotNull(registry.getDataSourceFolder(parent), parent);
         assertNotNull(perm(ResourceType.DATA_SOURCE, parent + "::" + additional), parent);
         assertNull(perm(ResourceType.DATA_SOURCE,
                         paths[0] + "/" + paths[0] + "P::" + additional), parent);
         assertEquals(List.of(), state(paths[0]), "left at the old path");
      }
   }

   // the clash below the folder has an empty folder side: the folder is deleted with the data
   // source and its additional connection
   @Test
   void ancestorDeleteWithAnEmptyFolderSide() throws Exception {
      for(String path : new String[] { "adR", "adX", "adE" }) {
         addFolder(path);
         clash(path + "/" + path + "P", false, false);
      }

      registry.removeDataSourceFolder("adR");
      repository.removeDataSourceFolder("adX");
      assertNull(objectService.deleteNodes(
         new TreeNodeInfo[] { node("adE", RepositoryEntry.DATA_SOURCE_FOLDER) }, principal, true,
         false));

      registry.clearCache();

      for(String path : new String[] { "adR", "adX", "adE" }) {
         assertEquals(List.of(), state(path), path);
      }
   }

   // ---- the parts of the data source side and of the folder side ----

   // a data source in the folder has a data model with a logical model, and the data source
   // owns nothing: the folder side only, so the folder is renamed and the data source delete
   // is refused
   @Test
   void dataModelOfAFolderDataSourceIsOnTheFolderSide() {
      addFolder("dmF");
      addSource("dmF/dmX");
      addLogicalModel("dmF/dmX", "dmLm");
      addSource("dmF");
      registry.clearCache();

      assertRefused(folderNotEmpty("dmF"), () -> registry.removeDataSource("dmF"), "dmF");
      registry.renameDataSourceFolder("dmF", "dmG");

      registry.clearCache();
      assertStoredName("dmF", "dmF");
      assertStoredName("dmG/dmX", "dmG/dmX");
      assertEquals("dmG/dmX", registry.getDataModel("dmG/dmX").getDataSource());
      assertEquals(List.of("dmLm"),
                   List.of(registry.getDataModel("dmG/dmX").getLogicalModelNames()));
   }

   // a VPM or a physical view of the data source is on the data source side
   @Test
   void vpmAndPhysicalViewOfTheDataSourceAreOnTheDataSourceSide() {
      addFolder("vpF");
      addSource("vpF");
      dataModel("vpF").addVirtualPrivateModel(new VirtualPrivateModel("vpVpm"), true);
      addFolder("pvF");
      addSource("pvF");
      dataModel("pvF").addPartition(new XPartition("pvView"));
      registry.clearCache();

      assertRefused(dataSourceNotEmpty("vpF"), () -> registry.renameDataSourceFolder("vpF", "vpG"),
                    "vpF", "vpG");
      assertRefused(dataSourceNotEmpty("pvF"), () -> registry.removeDataSourceFolder("pvF"),
                    "pvF");
      // and the data source side doesn't keep the data source from being renamed
      assertDoesNotThrow(() -> registry.checkDataSourcePathClash("vpF"));
   }

   // ---- the way out: the folder side is listed and can be moved out (review F1) ----

   // the folder's data source and subfolder are listed and moved out with their dependencies,
   // through the EM and the portal, and then the data source can be renamed
   @Test
   void folderSideIsListedAndMovedOut() throws Exception {
      addFolder("woOut");
      clash("woP", true, true);
      clearInvocations(transformHandler);

      assertTrue(registry.getSubDataSourceNames("woP").contains("woP/woPX"),
                 registry.getSubDataSourceNames("woP").toString());
      assertTrue(registry.getSubDataSourceNames("woP", true).contains("woP/woPSub/woPY"));
      assertFalse(registry.isAdditionalConnectionPath("woP/woPX"));
      assertTrue(registry.isAdditionalConnectionPath("woP/woPAdd"));
      assertEquals(List.of("woP/woPSub/woPY"), List.copyOf(
         DependencyTransformer.createDatasourceFolderDependencyInfoMap(
            registry, "woP/woPSub", "woOut/woPSub").keySet()));

      objectService.moveFiles(move("woOut", tree("woP/woPX", RepositoryEntry.DATA_SOURCE)), true,
                              principal);
      verify(transformHandler, times(1)).addTransformTask(any(RenameDependencyInfo.class));
      browserService.moveDataSource(new MoveCommand[] {
         move("woP/woPSub", "woOut/woPSub", PortalDataType.DATA_SOURCE_FOLDER) }, principal);
      verify(transformHandler, times(2)).addTransformTask(any(RenameDependencyInfo.class));

      XDataSource dataSource = (XDataSource) registry.getDataSource("woP").clone();
      dataSource.setName("woQ");
      repository.updateDataSource(dataSource, "woP");
      registry.removeDataSourceFolder("woP");

      registry.clearCache();
      assertStoredName("woOut/woPX", "woOut/woPX");
      assertStoredName("woOut/woPSub/woPY", "woOut/woPSub/woPY");
      assertStoredName("woQ/woPAdd", "woPAdd");
      assertNotNull(perm(ResourceType.DATA_SOURCE, "woOut/woPX"));
      assertNotNull(perm(ResourceType.DATA_SOURCE, "woQ::woPAdd"));
      assertEquals(List.of(), state("woP"));
   }

   // the permission resource of the folder's data source is its own, not that of an additional
   // connection of the data source (review S2)
   @Test
   void permissionResourceOfTheFolderSide() {
      clash("rsP", true, true);

      assertEquals("rsP/rsPX",
                   ResourcePermissionService.getDataSourceResourceName("rsP/rsPX", registry));
      assertEquals("rsP/rsPSub/rsPY",
                   ResourcePermissionService.getDataSourceResourceName("rsP/rsPSub/rsPY", registry));
      assertEquals("rsP::rsPAdd",
                   ResourcePermissionService.getDataSourceResourceName("rsP/rsPAdd", registry));
   }

   // ---- a delete that removes both sides (review F2) ----

   // a folder above a clash whose sides both hold entries: everything is deleted, with the
   // permissions
   @Test
   void ancestorDeleteWithBothSides() throws Exception {
      for(String path : new String[] { "abR", "abX", "abE" }) {
         addFolder(path);
         clash(path + "/" + path + "P", true, true);
      }

      registry.removeDataSourceFolder("abR");
      repository.removeDataSourceFolder("abX");
      assertNull(objectService.deleteNodes(
         new TreeNodeInfo[] { node("abE", RepositoryEntry.DATA_SOURCE_FOLDER) }, principal, true,
         false));

      registry.clearCache();

      for(String path : new String[] { "abR", "abX", "abE" }) {
         assertEquals(List.of(), state(path), path);
      }
   }

   // the folder and the data source at its path are both selected, in either order: both are
   // deleted, with the permissions
   @Test
   void deleteOfBothSidesTogether() throws Exception {
      for(String path : new String[] { "btA", "btB", "btC" }) {
         clash(path, true, true);
      }

      assertNull(objectService.deleteNodes(new TreeNodeInfo[] {
         node("btA", RepositoryEntry.DATA_SOURCE_FOLDER),
         node("btA", RepositoryEntry.DATA_SOURCE) }, principal, true, false));
      assertNull(objectService.deleteNodes(new TreeNodeInfo[] {
         node("btB", RepositoryEntry.DATA_SOURCE),
         node("btB", RepositoryEntry.DATA_SOURCE_FOLDER) }, principal, true, false));
      DataSourceController controller = new DataSourceController(
         datasourcesService, browserService, mock(DatabaseDatasourcesService.class), security,
         mock(DataSourceStatusService.class), mock(FileSystemService.class));
      SelectedDataSourcesRequest request = ImmutableSelectedDataSourcesRequest.builder()
         .dataSources(List.of(item("btC")))
         .folders(List.of(item("btC")))
         .build();
      controller.deleteDataSources(request, principal);

      registry.clearCache();

      for(String path : new String[] { "btA", "btB", "btC" }) {
         assertEquals(List.of(), state(path), path);
      }
   }

   // ---- additional connections stored with a path name (review F3) ----

   // before Bug #77610 an additional connection could be stored with its own path. Without a
   // clash it is still listed, and a save of the data source doesn't write it again under it
   @Test
   void additionalConnectionStoredWithItsOwnPath() throws Exception {
      addParent("lgP", "lgOk");
      registry.setObject(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                        "lgP/lgAdd", null),
                         new XDataSourceWrapper(source("lgP/lgAdd")));
      registry.clearCache();
      List<String> before = state("lgP");

      assertEquals(List.of("lgAdd", "lgOk"), new TreeSet<>(List.of(
         ((AdditionalConnectionDataSource<?>) registry.getDataSource("lgP"))
            .getDataSourceNames())).stream().toList());
      repository.updateDataSource(registry.getDataSource("lgP"), "lgP");

      assertEquals(before, state("lgP"));
      assertFalse(containsDataSource("lgP/lgP/lgAdd"));
   }

   // an entry stored with another data source's path (L6 of the repro) is not listed
   @Test
   void entryStoredWithAnotherPathIsNotListed() {
      addParent("opP", "opOk");
      registry.setObject(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                        "opP/opX", null),
                         new XDataSourceWrapper(source("opQ/opX")));
      registry.clearCache();

      assertEquals(List.of("opOk"), List.of(
         ((AdditionalConnectionDataSource<?>) registry.getDataSource("opP"))
            .getDataSourceNames()));
   }

   // ---- a member that can't be read (review minor) ----

   @Test
   void unreadableMemberIsNamed() {
      addFolder("unF");
      addUnreadable("unF/unU", "unF/unU");
      addSource("unF");
      registry.clearCache();

      assertRefused(Catalog.getCatalog().getString(
         "common.datasource.pathClashUnreadable", "unF", "unF/unU"),
                    () -> registry.renameDataSourceFolder("unF", "unG"), "unF", "unG");

      // the clash below a renamed folder is named, not the folder
      addFolder("uaG");
      addFolder("uaG/uaP");
      addUnreadable("uaG/uaP/uaU", "uaG/uaP/uaU");
      addSource("uaG/uaP");
      registry.clearCache();

      assertRefused(Catalog.getCatalog().getString(
         "common.datasource.pathClashUnreadable", "uaG/uaP", "uaG/uaP/uaU"),
                    () -> registry.renameDataSourceFolder("uaG", "uaH"), "uaG", "uaH");
   }

   // ---- a save of a data source with an additional connection that can't be read ----

   @Test
   void saveWithAnUnreadableAdditionalConnection() throws Exception {
      addParent("ubP", "ubAdd");
      addUnreadable("ubP/ubU", "ubU");
      registry.clearCache();
      assertNull(registry.getDataSource("ubP/ubU"), "not seeded unreadable");
      List<String> before = state("ubP");

      assertEquals(List.of("ubAdd"), List.of(
         ((AdditionalConnectionDataSource<?>) registry.getDataSource("ubP")).getDataSourceNames()));
      repository.updateDataSource(registry.getDataSource("ubP"), "ubP");

      assertEquals(before, state("ubP"));
   }

   // the operation must throw a MessageException with the message and leave the stored state as
   // it was
   private void assertRefused(String message, Action action, String... roots) {
      List<String> before = state(roots);
      Throwable thrown = null;

      try {
         action.run();
      }
      catch(Throwable e) {
         thrown = e;
      }

      List<String> after = state(roots);
      List<String> removed = new ArrayList<>(before);
      removed.removeAll(after);
      List<String> added = new ArrayList<>(after);
      added.removeAll(before);
      Throwable error = thrown;
      assertTrue(removed.isEmpty() && added.isEmpty(),
                 () -> "the stored state changed (thrown: " + error + "); removed: " + removed +
                    "; added: " + added);
      assertInstanceOf(MessageException.class, thrown, "not refused");
      assertEquals(message, thrown.getMessage());
   }

   private static String folderNotEmpty(String path) {
      return Catalog.getCatalog().getString("common.datasource.pathClashFolderNotEmpty", path);
   }

   private static String dataSourceNotEmpty(String path) {
      return Catalog.getCatalog().getString("common.datasource.pathClashDataSourceNotEmpty", path);
   }

   // the stored entries at or under the roots, with the stored names of the data sources and
   // the data sources and logical models of the data models, and every permission key
   private List<String> state(String... roots) {
      registry.clearCache();
      List<String> lines = new ArrayList<>();

      for(AssetEntry entry : registry.getEntries("")) {
         String path = entry.getPath();

         if(Arrays.stream(roots).noneMatch(root -> Tool.isSameOrDescendantPath(root, path))) {
            continue;
         }

         String line = entry.getType() + " " + path;

         if(entry.getType() == AssetEntry.Type.DATA_SOURCE) {
            XDataSource dataSource = registry.getDataSource(path);
            line += " [" + (dataSource != null ? dataSource.getFullName() :
               registry.containObject(entry) ? "unreadable" : "missing") + "]";
         }
         else if(entry.getType() == AssetEntry.Type.DATA_MODEL) {
            XDataModel model = registry.getDataModel(path);
            line += model == null ? " [missing]" : " [" + model.getDataSource() + " " +
               new TreeSet<>(List.of(model.getLogicalModelNames())) + "]";
         }

         lines.add(line);
      }

      for(String key : store.keySet()) {
         String resource = key.substring(key.indexOf('|') + 1).replaceFirst("::.*", "");

         if(Arrays.stream(roots).anyMatch(root -> Tool.isSameOrDescendantPath(root, resource))) {
            lines.add("grant " + key);
         }
      }

      Collections.sort(lines);
      return lines;
   }

   // a data source P with an additional connection "<P>Add" (bare name) and a folder P. The
   // folder holds "P/<P>X" if member and "P/<P>Sub/<P>Y" if subfolder. Every object has a grant.
   private void clash(String path, boolean member, boolean subfolder) {
      String name = path.substring(path.lastIndexOf('/') + 1);
      addFolder(path);
      grant(ResourceType.DATA_SOURCE_FOLDER, path);

      if(member) {
         addSource(path + "/" + name + "X");
         grant(ResourceType.DATA_SOURCE, path + "/" + name + "X");
      }

      if(subfolder) {
         addFolder(path + "/" + name + "Sub");
         addSource(path + "/" + name + "Sub/" + name + "Y");
         grant(ResourceType.DATA_SOURCE_FOLDER, path + "/" + name + "Sub");
         grant(ResourceType.DATA_SOURCE, path + "/" + name + "Sub/" + name + "Y");
      }

      addParent(path, name + "Add");
      grant(ResourceType.DATA_SOURCE, path);
      grant(ResourceType.DATA_SOURCE, path + "::" + name + "Add");
      registry.clearCache();
      assertTrue(registry.getDataSourcePathClashes().contains(path), "not seeded: " + path);
      assertEquals(List.of(name + "Add"), List.of(
         ((AdditionalConnectionDataSource<?>) registry.getDataSource(path)).getDataSourceNames()),
         "not seeded: " + path);
   }

   private IdentityService identityService() {
      return new IdentityService(
         null, null, null, null, null, null, null, null, null, registry, null, null, null, null,
         Optional.empty(), null, null, null, null, null, null, null, null, null, null, null,
         null, null, Optional.empty());
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   private void addSource(String path) {
      registry.setDataSource(source(path), false);
   }

   private void addParent(String path, String... additionals) {
      addSource(path);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(path);

      for(String name : additionals) {
         parent.addDatasource(source(name));
      }
   }

   private XDataModel dataModel(String path) {
      XDataModel model = registry.getDataModel(path);

      if(model == null) {
         model = new XDataModel(path);
         registry.setDataModel(model);
      }

      return model;
   }

   private void addLogicalModel(String path, String name) {
      dataModel(path).addLogicalModel(new XLogicalModel(name));
   }

   // a data source of a type that isn't registered, stored at the path with the name
   private void addUnreadable(String path, String name) {
      GoneDataSource dataSource = new GoneDataSource();
      dataSource.setName(name);
      registry.setObject(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                        path, null), new XDataSourceWrapper(dataSource));
   }

   private void assertStoredName(String path, String name) {
      XDataSource dataSource = registry.getDataSource(path);
      assertNotNull(dataSource, path);
      assertEquals(name, dataSource.getFullName(), "the stored name of " + path);
   }

   private boolean containsDataSource(String path) {
      return registry.containObject(new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null));
   }

   private static String key(ResourceType type, String path) {
      return type + "|" + path;
   }

   private void grant(ResourceType type, String path) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ, Set.of(new Permission.PermissionIdentity(
         "alice", Organization.getDefaultOrganizationID())));
      store.put(key(type, path), permission);
   }

   private Permission perm(ResourceType type, String path) {
      return store.get(key(type, path));
   }

   private static SelectedDataSourceItem item(String path) {
      return ImmutableSelectedDataSourceItem.builder().name(path).path(path).build();
   }

   private static MoveCommand move(String oname, String nname, PortalDataType type) {
      MoveCommand move = new MoveCommand();
      move.setOldPath(oname);
      move.setPath(nname);
      move.setName(nname.substring(nname.lastIndexOf('/') + 1));
      move.setType(type.name());
      return move;
   }

   private static TreeNodeInfo node(String path, int type) {
      int index = path.lastIndexOf('/');
      return TreeNodeInfo.builder()
         .label(index < 0 ? path : path.substring(index + 1))
         .path(path)
         .type(type)
         .build();
   }

   private static ContentRepositoryTreeNode tree(String path, int type) {
      int index = path.lastIndexOf('/');
      return ContentRepositoryTreeNode.builder()
         .label(index < 0 ? path : path.substring(index + 1))
         .path(path)
         .type(type)
         .build();
   }

   private static MoveCopyTreeNodesRequest move(String destination,
                                                ContentRepositoryTreeNode... sources)
   {
      return MoveCopyTreeNodesRequest.builder()
         .source(List.of(sources))
         .destination(tree(destination, RepositoryEntry.DATA_SOURCE_FOLDER))
         .build();
   }

   private static JDBCDataSource source(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }

   @FunctionalInterface
   private interface Action {
      void run() throws Exception;
   }

   public static class GoneDataSource extends TabularDataSource<GoneDataSource> {
      public GoneDataSource() {
         super(GONE, GoneDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }
   }

   @Configuration
   static class Beans {
      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      // the constructors of these beans are package private
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      // loads the embedded Derby driver of the test sources
      @Bean
      @Primary
      public Drivers testDrivers() throws Exception {
         Drivers drivers = mock(Drivers.class);
         when(drivers.getDriverClass(anyString()))
            .thenAnswer(inv -> Class.forName(inv.<String>getArgument(0)));
         return drivers;
      }
   }
}
