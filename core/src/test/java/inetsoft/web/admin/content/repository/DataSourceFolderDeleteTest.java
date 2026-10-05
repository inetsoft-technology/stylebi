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
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XDataSourceWrapper;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.model.database.StringWrapper;
import inetsoft.web.portal.model.database.events.CheckDependenciesEvent;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77731: a data source folder delete must cover the whole folder, at any depth. It removes
 * the permission of every folder it removes, so that a folder created later at the path of one
 * doesn't get it, and it checks the dependencies and the DELETE permission of every data source
 * and the DELETE permission of every subfolder before it deletes anything.
 * <p>
 * The registry, the repository, the dependency storage and the services are the real ones. The
 * permissions are kept in a map behind a mocked security engine and provider, with DELETE denied
 * per resource. The entry points are the EM delete ({@code deleteNodes}), the portal folder
 * delete, the portal multi-selection delete and the portal dependency checks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AdditionalConnectionPermissionRemovalTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceFolderDeleteTest {
   private static final String URL = "jdbc:derby:memory:bug77731;create=true";
   private static final String DENIED_SOURCE = "Permission denied to delete datasource";
   private static final String DENIED_FOLDER = "Permission denied to delete datasource folder";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private final Map<String, Permission> store = new HashMap<>();
   // the resources the user may not delete
   private final Set<String> denied = new HashSet<>();
   private MockedStatic<SecurityEngine> securityStatic;
   private RepositoryObjectService objectService;
   private DataSourceBrowserService browserService;
   private DataSourceController controller;
   private Principal principal;
   private Field storageField;
   private IndexedStorage storage;
   // the saves of the index of the registry fail from the failAt-th one, once or persistently
   private int rootSaves;
   private int failAt;
   private boolean persistent;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      store.clear();
      denied.clear();
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.isVirtual()).thenReturn(false);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenAnswer(
         inv -> allowed(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
      doAnswer(inv -> store.remove(key(inv.getArgument(0), inv.getArgument(1))))
         .when(provider).removePermission(any(ResourceType.class), anyString());
      // a non-virtual engine over the map, which the registry also gets from getSecurity()
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenAnswer(
         inv -> allowed(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
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
      objectService = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class), provider,
         permissions, repository, mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), registry, mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         repletRegistries, mock(DashboardRegistryManager.class));
      browserService = new DataSourceBrowserService(
         security, objectService, repository, mock(DataSourceService.class), registry,
         mock(Config.class), mock(RenameTransformHandler.class));
      DatasourcesService datasourcesService = new DatasourcesService(
         repository, security, mock(DataSourceStatusService.class), registry, mock(Config.class));
      controller = new DataSourceController(
         datasourcesService, browserService, mock(DatabaseDatasourcesService.class), security,
         mock(DataSourceStatusService.class), mock(FileSystemService.class));
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());

      // the storage of the registry, failing the saves of its index when armed
      String rootKey = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                      AssetEntry.Type.DATA_SOURCE_FOLDER, "/", null).toIdentifier();
      storageField = DataSourceRegistry.class.getDeclaredField("indexedStorage");
      storageField.setAccessible(true);
      storage = (IndexedStorage) storageField.get(registry);
      IndexedStorage failing = mock(IndexedStorage.class, delegatesTo(storage));
      doAnswer(inv -> {
         if(failAt > 0 && inv.getArgument(0).equals(rootKey)) {
            rootSaves++;

            if(rootSaves == failAt || persistent && rootSaves > failAt) {
               throw new IOException("simulated storage write failure");
            }
         }

         storage.putXMLSerializable(inv.getArgument(0), inv.getArgument(1));
         return null;
      }).when(failing).putXMLSerializable(anyString(), any());
      storageField.set(registry, failing);
   }

   @AfterEach
   void tearDown() throws Exception {
      failAt = 0;
      storageField.set(registry, storage);
      registry.clearCache();
      securityStatic.close();
   }

   // the reported case: the permissions of the subfolders at two levels are removed with the
   // folder, and the folders created again at their paths don't get them
   @ParameterizedTest
   @ValueSource(strings = { "em", "portal" })
   void subfolderPermissionsAreRemoved(String via) throws Exception {
      String f = via + "GrF";
      addFolder(f);
      addFolder(f + "/G");
      addFolder(f + "/G/H");
      addParent(f + "/G/P", "add");
      grant(ResourceType.DATA_SOURCE_FOLDER, f);
      grant(ResourceType.DATA_SOURCE_FOLDER, f + "/G");
      grant(ResourceType.DATA_SOURCE_FOLDER, f + "/G/H");
      grant(ResourceType.DATA_SOURCE, f + "/G/P");
      grant(ResourceType.DATA_SOURCE, f + "/G/P::add");

      assertNull(delete(via, f, false));

      assertFalse(folderListed(f));
      assertFalse(folderListed(f + "/G"));
      assertFalse(folderListed(f + "/G/H"));
      assertFalse(sourceListed(f + "/G/P"));
      assertTrue(store.isEmpty(), () -> "permissions left: " + store.keySet());

      addFolder(f);
      addFolder(f + "/G");
      addFolder(f + "/G/H");
      assertNull(perm(ResourceType.DATA_SOURCE_FOLDER, f + "/G"),
                 "the recreated subfolder got the old grant");
      assertNull(perm(ResourceType.DATA_SOURCE_FOLDER, f + "/G/H"),
                 "the recreated subfolder got the old grant");
   }

   // a dependency on a nested data source refuses the delete unless forced, and nothing is
   // deleted, a direct data source listed before it neither
   @ParameterizedTest
   @ValueSource(strings = { "em", "portal" })
   void nestedDependencyIsReported(String via) throws Exception {
      String f = via + "NdF";
      addFolder(f);
      addFolder(f + "/G");
      addSource(f + "/A");
      addSource(f + "/G/N");
      grant(ResourceType.DATA_SOURCE_FOLDER, f + "/G");
      dependOn(f + "/G/N");

      ConnectionStatus status = delete(via, f, false);

      assertNotNull(status);
      assertTrue(status.getStatus().contains("ws" + f), status.getStatus());
      assertAllListed(f, f + "/G", f + "/A", f + "/G/N");
      assertNotNull(perm(ResourceType.DATA_SOURCE_FOLDER, f + "/G"));

      assertNull(delete(via, f, true));

      assertFalse(folderListed(f));
      assertFalse(folderListed(f + "/G"));
      assertFalse(sourceListed(f + "/G/N"));
      assertNull(perm(ResourceType.DATA_SOURCE_FOLDER, f + "/G"));
   }

   // control: a dependency on a direct data source was already reported
   @ParameterizedTest
   @ValueSource(strings = { "em", "portal" })
   void directDependencyIsReported(String via) throws Exception {
      String f = via + "DdF";
      addFolder(f);
      addSource(f + "/D");
      dependOn(f + "/D");

      assertNotNull(delete(via, f, false));

      assertAllListed(f, f + "/D");
   }

   // DELETE denied on a nested data source refuses the delete, even forced, and nothing is
   // deleted
   @ParameterizedTest
   @ValueSource(strings = { "em", "portal" })
   void nestedSourceDeleteDenied(String via) throws Exception {
      String f = via + "NsF";
      addFolder(f);
      addFolder(f + "/G");
      addSource(f + "/A");
      addSource(f + "/G/N");
      grant(ResourceType.DATA_SOURCE_FOLDER, f + "/G");
      deny(ResourceType.DATA_SOURCE, f + "/G/N");

      ConnectionStatus status = delete(via, f, true);

      assertNotNull(status);
      assertEquals(DENIED_SOURCE, status.getStatus());
      assertAllListed(f, f + "/G", f + "/A", f + "/G/N");
      assertNotNull(perm(ResourceType.DATA_SOURCE_FOLDER, f + "/G"));
   }

   // DELETE denied on a nested folder refuses the delete, even forced, and nothing is deleted
   @ParameterizedTest
   @ValueSource(strings = { "em", "portal" })
   void nestedFolderDeleteDenied(String via) throws Exception {
      String f = via + "NfF";
      addFolder(f);
      addFolder(f + "/G");
      addFolder(f + "/G/H");
      addSource(f + "/A");
      deny(ResourceType.DATA_SOURCE_FOLDER, f + "/G/H");

      ConnectionStatus status = delete(via, f, true);

      assertNotNull(status);
      assertEquals(DENIED_FOLDER, status.getStatus());
      assertAllListed(f, f + "/G", f + "/G/H", f + "/A");
   }

   // control: DELETE denied on a direct data source was already refused
   @ParameterizedTest
   @ValueSource(strings = { "em", "portal" })
   void directSourceDeleteDenied(String via) throws Exception {
      String f = via + "DsF";
      addFolder(f);
      addSource(f + "/D");
      deny(ResourceType.DATA_SOURCE, f + "/D");

      ConnectionStatus status = delete(via, f, true);

      assertNotNull(status);
      assertEquals(DENIED_SOURCE, status.getStatus());
      assertAllListed(f, f + "/D");
   }

   // a folder of another folder whose path contains the path of the deleted folder is neither
   // checked nor deleted
   @Test
   void siblingFolderIsNotChecked() throws Exception {
      String f = "ovF";
      addFolder(f);
      addFolder(f + "/G");
      addFolder(f + "/x" + f);
      addFolder(f + "/x" + f + "/G");
      addSource(f + "/G/in");
      addSource(f + "/x" + f + "/G/out");
      dependOn(f + "/x" + f + "/G/out");
      deny(ResourceType.DATA_SOURCE, f + "/x" + f + "/G/out");

      assertNull(delete("em", f + "/G", false));

      assertFalse(sourceListed(f + "/G/in"));
      assertAllListed(f + "/x" + f + "/G", f + "/x" + f + "/G/out");
   }

   // a data source that can't be loaded in the folder is deleted with its permission
   @Test
   void corruptSourceIsDeleted() throws Exception {
      String f = "coDelF";
      addFolder(f);
      addFolder(f + "/G");
      addSource(f + "/G/X");
      String key = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                  f + "/G/X", null).toIdentifier();
      storage.putXMLSerializable(key, new XDataSourceWrapper());
      registry.clearCache();
      assertNull(registry.getDataSource(f + "/G/X"));
      grant(ResourceType.DATA_SOURCE, f + "/G/X");
      grant(ResourceType.DATA_SOURCE_FOLDER, f + "/G");

      assertNull(delete("em", f, false));

      assertFalse(folderListed(f + "/G"));
      assertFalse(sourceListed(f + "/G/X"));
      assertTrue(store.isEmpty(), () -> "permissions left: " + store.keySet());
   }

   // a data source at the path of the deleted folder (a "clash") doesn't hide the data sources
   // of the folder from the checks
   @Test
   void clashWithADataSourceAtTheFolderPath() throws Exception {
      String a = "clA";
      addFolder(a);
      addFolder(a + "/F");
      addSource(a + "/F");
      addSource(a + "/F/x");
      dependOn(a + "/F/x");

      assertNotNull(delete("em", a + "/F", false));
      assertAllListed(a + "/F", a + "/F/x");
      assertTrue(sourceListed(a + "/F"));

      DependencyStorageService.getInstance().remove(sourceId(a + "/F/x"));
      deny(ResourceType.DATA_SOURCE, a + "/F/x");

      ConnectionStatus status = delete("portal", a + "/F", true);

      assertNotNull(status);
      assertEquals(DENIED_SOURCE, status.getStatus());
      assertAllListed(a + "/F", a + "/F/x");
      assertTrue(sourceListed(a + "/F"));
   }

   // the folder check of the portal reports an outer dependency of a nested data source
   @Test
   void folderCheckReportsNestedOuterDependency() throws Exception {
      String f = "ocF";
      addFolder(f);
      addFolder(f + "/G");
      addSource(f + "/D");
      addSource(f + "/G/N");
      outerDependOn(f + "/G/N", "lmN" + f);
      outerDependOn(f + "/D", "lmD" + f);

      // direct control
      StringWrapper direct = controller.checkDsFolderOuterDependencies(folderEvent(f), principal);
      assertNotNull(direct);
      assertTrue(direct.getBody().contains("lmD" + f), direct.getBody());

      registry.getDataModel(f + "/D").removeLogicalModel("lmD" + f);
      registry.clearCache();

      StringWrapper nested = controller.checkDsFolderOuterDependencies(folderEvent(f), principal);
      assertNotNull(nested, "the outer dependency of the nested data source wasn't reported");
      assertTrue(nested.getBody().contains("lmN" + f), nested.getBody());

      StringWrapper selected = controller.checkDsOuterDependenciesSelected(
         request(List.of(), List.of(f)), principal);
      assertNotNull(selected);
      assertTrue(selected.getBody().contains("lmN" + f), selected.getBody());
   }

   // the multi-selection delete checks the DELETE permission on every data source and
   // subfolder of a selected folder before it deletes anything, and so does its check
   @ParameterizedTest
   @ValueSource(strings = { "source", "folder" })
   void selectedDeleteRefusesANestedDenial(String what) throws Exception {
      String f = what + "SdF";
      String r = what + "SdR";
      addSource(r);
      addFolder(f);
      addFolder(f + "/G");
      addSource(f + "/G/N");

      if("source".equals(what)) {
         deny(ResourceType.DATA_SOURCE, f + "/G/N");
      }
      else {
         deny(ResourceType.DATA_SOURCE_FOLDER, f + "/G");
      }

      assertThrows(SecurityException.class, () -> controller.checkDsOuterDependenciesSelected(
         request(List.of(r), List.of(f)), principal));
      assertThrows(SecurityException.class, () -> controller.deleteDataSources(
         request(List.of(r), List.of(f)), principal));

      assertAllListed(r, f, f + "/G", f + "/G/N");
   }

   // control: the multi-selection delete deletes it all, with the permissions of the subfolders
   @Test
   void selectedDeleteDeletesAll() throws Exception {
      String f = "saF";
      String r = "saR";
      addSource(r);
      addFolder(f);
      addFolder(f + "/G");
      addSource(f + "/G/N");
      grant(ResourceType.DATA_SOURCE_FOLDER, f + "/G");
      grant(ResourceType.DATA_SOURCE, f + "/G/N");

      controller.deleteDataSources(request(List.of(r), List.of(f)), principal);

      assertFalse(sourceListed(r));
      assertFalse(folderListed(f));
      assertFalse(folderListed(f + "/G"));
      assertFalse(sourceListed(f + "/G/N"));
      assertTrue(store.isEmpty(), () -> "permissions left: " + store.keySet());
   }

   // a subfolder whose removal fails to save the index is still listed by the stored index, so
   // it keeps its permission, although the cached index no longer lists it. The subfolder
   // removed before it loses its permission. The subfolders are removed deepest first.
   @Test
   void failedIndexSaveKeepsThePermission() throws Exception {
      String f = "fiF";
      addFolder(f);
      addFolder(f + "/G");
      addFolder(f + "/G/H");
      grant(ResourceType.DATA_SOURCE_FOLDER, f + "/G");
      grant(ResourceType.DATA_SOURCE_FOLDER, f + "/G/H");
      // the removal of f/G/H saves the index first, then that of f/G fails, and every later save
      arm(2, true);

      delete("em", f, false);
      failAt = 0;

      assertFalse(folderListed(f + "/G/H"));
      assertNull(perm(ResourceType.DATA_SOURCE_FOLDER, f + "/G/H"));
      assertTrue(folderListed(f + "/G"));
      assertNotNull(perm(ResourceType.DATA_SOURCE_FOLDER, f + "/G"),
                    "the permission of a folder that is still listed was removed");
   }

   // the save fails once: the next save writes the index without the failed folder either, so
   // the folder is no longer listed and loses its permission
   @Test
   void indexSaveFailingOnceRemovesThePermission() throws Exception {
      String f = "f1F";
      addFolder(f);
      addFolder(f + "/G");
      addFolder(f + "/G/H");
      grant(ResourceType.DATA_SOURCE_FOLDER, f + "/G");
      grant(ResourceType.DATA_SOURCE_FOLDER, f + "/G/H");
      arm(2, false);

      delete("em", f, false);
      failAt = 0;

      assertFalse(folderListed(f + "/G"));
      assertFalse(folderListed(f + "/G/H"));
      assertNull(perm(ResourceType.DATA_SOURCE_FOLDER, f + "/G"));
      assertNull(perm(ResourceType.DATA_SOURCE_FOLDER, f + "/G/H"));
   }

   private ConnectionStatus delete(String via, String folder, boolean force) throws Exception {
      if("em".equals(via)) {
         int index = folder.lastIndexOf('/');
         TreeNodeInfo node = TreeNodeInfo.builder()
            .label(index < 0 ? folder : folder.substring(index + 1))
            .path(folder)
            .type(RepositoryEntry.DATA_SOURCE_FOLDER)
            .build();
         return objectService.deleteNodes(new TreeNodeInfo[] { node }, principal, force, false);
      }

      return browserService.deleteDataSourceFolder(folder, folder, force, principal);
   }

   private void arm(int failAt, boolean persistent) {
      this.rootSaves = 0;
      this.persistent = persistent;
      this.failAt = failAt;
   }

   private boolean allowed(ResourceType type, String path, ResourceAction action) {
      return action != ResourceAction.DELETE || !denied.contains(key(type, path));
   }

   private void deny(ResourceType type, String path) {
      denied.add(key(type, path));
   }

   private static String key(ResourceType type, String path) {
      return type + "|" + path;
   }

   private Permission grant(ResourceType type, String path) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ, Set.of(new Permission.PermissionIdentity(
         "alice", Organization.getDefaultOrganizationID())));
      store.put(key(type, path), permission);
      return permission;
   }

   private Permission perm(ResourceType type, String path) {
      return store.get(key(type, path));
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

   // a worksheet depends on the data source
   private void dependOn(String path) throws Exception {
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(new ArrayList<>(List.of(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws" + path.split("/")[0],
         null))));
      DependencyStorageService.getInstance().put(sourceId(path), info);
      assertFalse(DependencyTool.getDependencies(sourceId(path)).isEmpty(), "not seeded");
   }

   // a worksheet depends on a logical model of the data source
   private void outerDependOn(String path, String model) {
      XDataModel dataModel = registry.getDataModel(path);

      if(dataModel == null) {
         dataModel = new XDataModel(path);
         registry.setDataModel(dataModel);
      }

      XLogicalModel logicalModel = new XLogicalModel(model);
      logicalModel.addOuterDependency(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws" + model, null));
      dataModel.addLogicalModel(logicalModel);
      registry.clearCache();
      XLogicalModel stored = registry.getDataModel(path).getLogicalModel(model);
      assertNotNull(stored, "not seeded");
      assertEquals(1, stored.getOuterDependencies().length, "not seeded");
   }

   private static String sourceId(String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null)
         .toIdentifier();
   }

   // read from the stored index, not from the cache
   private boolean folderListed(String path) {
      return listed(AssetEntry.Type.DATA_SOURCE_FOLDER, path);
   }

   private boolean sourceListed(String path) {
      return listed(AssetEntry.Type.DATA_SOURCE, path);
   }

   private boolean listed(AssetEntry.Type type, String path) {
      registry.clearCache();
      return Arrays.stream(registry.getEntries(path, type))
         .anyMatch(entry -> entry.getPath().equals(path));
   }

   // each path is a listed folder or data source
   private void assertAllListed(String... paths) {
      for(String path : paths) {
         assertTrue(folderListed(path) || sourceListed(path), path + " was deleted");
      }
   }

   private static CheckDependenciesEvent folderEvent(String path) {
      CheckDependenciesEvent event = new CheckDependenciesEvent();
      event.setDatasourceFolderPath(path);
      return event;
   }

   private static SelectedDataSourcesRequest request(List<String> dataSources,
                                                     List<String> folders)
   {
      return ImmutableSelectedDataSourcesRequest.builder()
         .dataSources(dataSources.stream().map(DataSourceFolderDeleteTest::item).toList())
         .folders(folders.stream().map(DataSourceFolderDeleteTest::item).toList())
         .build();
   }

   private static SelectedDataSourceItem item(String path) {
      int index = path.lastIndexOf('/');
      return ImmutableSelectedDataSourceItem.builder()
         .name(index < 0 ? path : path.substring(index + 1)).path(path).build();
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
}
