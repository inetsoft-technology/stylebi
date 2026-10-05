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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.composer.vs.controller.VSLayoutServiceProxy;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.viewsheet.EventAspect;
import inetsoft.web.viewsheet.EventAspectServiceProxy;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77819: the audit record of a data source or data source folder delete must say what the
 * delete did. A delete that is refused is audited as a failure, a delete that returns a prompt to
 * confirm it is not audited (the confirmed retry is), and a data source deleted with the folder at
 * its path, both being selected, gets a record of its own with the outcome of the folder delete.
 * <p>
 * The registry, the repository, the dependency storage, the EM {@code RepositoryObjectService},
 * the portal {@code DataSourceController}, {@code DatasourcesService} and
 * {@code DataSourceBrowserService} are the real ones, the portal services behind a proxy with the
 * real {@code @Audited} advice. Permissions are mocked, with DELETE denied per data source folder.
 * The audit records are captured from a mocked {@code Audit}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AdditionalConnectionPermissionRemovalTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceDeleteAuditTest {
   private static final String URL = "jdbc:derby:memory:bug77819;create=true";
   private static final String DENIED_FOLDER = "Permission denied to delete datasource folder";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private final Map<String, Permission> store = new HashMap<>();
   // the data source folders the user may not delete
   private final Set<String> deniedFolders = new HashSet<>();
   // the data sources a worksheet depends on
   private final List<String> dependencies = new ArrayList<>();
   private final List<ActionRecord> records = new ArrayList<>();
   private MockedStatic<SecurityEngine> securityStatic;
   private MockedStatic<Audit> auditStatic;
   private RepositoryObjectService objectService;
   private DataSourceBrowserService browserService;
   private DataSourceController controller;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      store.clear();
      deniedFolders.clear();
      records.clear();
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.isVirtual()).thenReturn(false);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenAnswer(inv ->
         !(inv.getArgument(1) == ResourceType.DATA_SOURCE_FOLDER &&
           deniedFolders.contains(inv.<String>getArgument(2))));
      doAnswer(inv -> store.remove(key(inv.getArgument(0), inv.getArgument(1))))
         .when(provider).removePermission(any(ResourceType.class), anyString());
      SecurityEngine security = mock(SecurityEngine.class);
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

      Audit audit = mock(Audit.class);
      doAnswer(inv -> records.add(inv.getArgument(0)))
         .when(audit).auditAction(any(ActionRecord.class), any());
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

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
      EventAspect aspect = new EventAspect(
         mock(RuntimeViewsheetRef.class), mock(CoreLifecycleService.class),
         mock(ViewsheetService.class), mock(VSLayoutServiceProxy.class),
         mock(EventAspectServiceProxy.class));
      browserService = audited(new DataSourceBrowserService(
         security, objectService, repository, mock(DataSourceService.class), registry,
         mock(Config.class), mock(RenameTransformHandler.class)), aspect);
      DatasourcesService datasourcesService = audited(new DatasourcesService(
         repository, security, mock(DataSourceStatusService.class), registry,
         mock(Config.class)), aspect);
      DatabaseDatasourcesService dbService = mock(DatabaseDatasourcesService.class);
      when(dbService.getDataSourceAuditPath(anyString(), any(), any())).thenAnswer(
         inv -> "Data Source/" + inv.getArgument(0));
      controller = new DataSourceController(
         datasourcesService, browserService, dbService, security,
         mock(DataSourceStatusService.class), mock(FileSystemService.class));
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      try {
         for(String path : dependencies) {
            DependencyStorageService.getInstance().remove(sourceId(path));
         }

         dependencies.clear();
         registry.clearCache();
      }
      finally {
         auditStatic.close();
         securityStatic.close();
      }
   }

   // the reported case (portal): the data source deleted with the folder at its path is audited
   @Test
   void portalDeleteOfBothSidesAuditsTheDataSource() throws Exception {
      clash("pb");
      controller.deleteDataSources(request(List.of(item("pb")), List.of(item("pb"))), principal);
      registry.clearCache();

      assertNull(registry.getDataSource("pb"), "precondition: the data source is deleted");
      assertRecords(record(ActionRecord.OBJECT_TYPE_DATASOURCE, "pb", true),
                    record(ActionRecord.OBJECT_TYPE_FOLDER, "pb", true));
   }

   // the data source deleted with the folder has the outcome of the folder delete
   @Test
   void portalDeleteOfBothSidesRefusedAuditsBothAsFailures() throws Exception {
      clash("pr");
      deniedFolders.add("pr/prSub");
      ConnectionStatus status = browserService.deleteDataSourceFolder(
         "pr", "Data Source/pr", true, true, principal);
      registry.clearCache();

      assertEquals(DENIED_FOLDER, status.getStatus());
      assertNotNull(registry.getDataSource("pr"), "precondition: the data source is kept");
      assertRecords(record(ActionRecord.OBJECT_TYPE_FOLDER, "pr", false),
                    record(ActionRecord.OBJECT_TYPE_DATASOURCE, "pr", false));
      assertTrue(records.stream().allMatch(r -> DENIED_FOLDER.equals(r.getActionError())));
   }

   // a portal folder delete refused by a returned status is audited as a failure
   @Test
   void portalFolderDeleteRefusedIsAuditedAsFailure() throws Exception {
      addFolder("pf");
      addFolder("pf/sub");
      deniedFolders.add("pf/sub");
      ConnectionStatus status = controller.deleteDatasourceFolder("pf", true, principal);
      registry.clearCache();

      assertEquals(DENIED_FOLDER, status.getStatus());
      assertNotNull(registry.getDataSourceFolder("pf"), "precondition: the folder is kept");
      assertRecords(record(ActionRecord.OBJECT_TYPE_FOLDER, "pf", false));
      assertEquals(DENIED_FOLDER, records.get(0).getActionError());
      // the refusal is still sent to the client as a plain status
      assertEquals("{\"status\":\"" + DENIED_FOLDER + "\",\"connected\":false}",
                   new ObjectMapper().writeValueAsString(status));
   }

   // the dependency prompt of a portal folder delete is not audited, the confirmed delete is
   @Test
   void portalFolderDeletePromptIsNotAudited() throws Exception {
      addFolder("pp");
      addSource("pp/ppX");
      dependOn("pp/ppX");
      ConnectionStatus status = controller.deleteDatasourceFolder("pp", false, principal);
      registry.clearCache();

      assertNotNull(status, "precondition: the user is asked to confirm");
      assertNotNull(registry.getDataSourceFolder("pp"), "precondition: the folder is kept");
      assertRecords();

      assertNull(controller.deleteDatasourceFolder("pp", true, principal));
      registry.clearCache();
      assertNull(registry.getDataSourceFolder("pp"), "precondition: the folder is deleted");
      assertRecords(record(ActionRecord.OBJECT_TYPE_FOLDER, "pp", true));
   }

   // a portal data source delete refused on the clash is still audited as a failure
   @Test
   void portalDataSourceDeleteRefusedIsAuditedAsFailure() {
      clash("ps");
      assertThrows(Exception.class, () -> controller.deleteDataSource("ps", "ps", true, principal));
      assertRecords(record(ActionRecord.OBJECT_TYPE_DATASOURCE, "ps", false));
   }

   // the reported case (EM): both are deleted, and each is audited once as deleted
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void emDeleteOfBothSidesAuditsBoth(boolean dataSourceFirst) throws Exception {
      String path = dataSourceFirst ? "es1" : "es2";
      clash(path);
      assertNull(objectService.deleteNodes(bothSides(path, dataSourceFirst), principal, true, false));
      registry.clearCache();

      assertNull(registry.getDataSource(path), "precondition: the data source is deleted");
      assertRecords(record(ActionRecord.OBJECT_TYPE_FOLDER, path, true),
                    record(ActionRecord.OBJECT_TYPE_DATASOURCE, path, true));
   }

   // the reported case (EM): the folder delete is refused, so the data source isn't deleted
   // either, in both orders of the nodes
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void emDeleteOfBothSidesRefusedAuditsBothAsFailures(boolean dataSourceFirst) throws Exception {
      String path = dataSourceFirst ? "er1" : "er2";
      clash(path);
      deniedFolders.add(path + "/" + path + "Sub");
      ConnectionStatus status =
         objectService.deleteNodes(bothSides(path, dataSourceFirst), principal, true, false);
      registry.clearCache();

      assertEquals(DENIED_FOLDER, status.getStatus());
      assertNotNull(registry.getDataSource(path), "precondition: the data source is kept");
      assertRecords(record(ActionRecord.OBJECT_TYPE_FOLDER, path, false),
                    record(ActionRecord.OBJECT_TYPE_DATASOURCE, path, false));
      assertTrue(records.stream().allMatch(r -> DENIED_FOLDER.equals(r.getActionError())));
   }

   // the dependency prompt of an EM delete of both sides deletes nothing and is not audited
   @Test
   void emDeleteOfBothSidesPromptIsNotAudited() throws Exception {
      clash("ep");
      dependOn("ep/epX");
      ConnectionStatus status =
         objectService.deleteNodes(bothSides("ep", true), principal, false, false);
      registry.clearCache();

      assertNotNull(status, "precondition: the user is asked to confirm");
      assertNotNull(registry.getDataSource("ep"), "precondition: the data source is kept");
      assertRecords();
   }

   // an EM folder delete refused by a returned status is audited as a failure
   @Test
   void emFolderDeleteRefusedIsAuditedAsFailure() throws Exception {
      addFolder("ef");
      addFolder("ef/sub");
      deniedFolders.add("ef/sub");
      ConnectionStatus status = objectService.deleteNodes(new TreeNodeInfo[] {
         node("ef", RepositoryEntry.DATA_SOURCE_FOLDER) }, principal, true, false);
      registry.clearCache();

      assertEquals(DENIED_FOLDER, status.getStatus());
      assertNotNull(registry.getDataSourceFolder("ef"), "precondition: the folder is kept");
      assertRecords(record(ActionRecord.OBJECT_TYPE_FOLDER, "ef", false));
      assertEquals(DENIED_FOLDER, records.get(0).getActionError());
   }

   // the dependency prompt of an EM data source delete is not audited, the confirmed delete is
   @Test
   void emDataSourceDeletePromptIsNotAudited() throws Exception {
      addSource("ed");
      dependOn("ed");
      TreeNodeInfo[] nodes = { node("ed", RepositoryEntry.DATA_SOURCE) };
      assertNotNull(objectService.deleteNodes(nodes, principal, false, false),
                    "precondition: the user is asked to confirm");
      registry.clearCache();
      assertNotNull(registry.getDataSource("ed"), "precondition: the data source is kept");
      assertRecords();

      assertNull(objectService.deleteNodes(nodes, principal, true, false));
      registry.clearCache();
      assertNull(registry.getDataSource("ed"), "precondition: the data source is deleted");
      assertRecords(record(ActionRecord.OBJECT_TYPE_DATASOURCE, "ed", true));
   }

   // the records, in any order, each as {object type, object name, status}
   private void assertRecords(String[]... expected) {
      List<String> actual = records.stream()
         .map(r -> r.getActionName() + "|" + r.getObjectType() + "|" + r.getObjectName() + "|" +
            r.getActionStatus())
         .sorted().toList();
      List<String> wanted = Arrays.stream(expected)
         .map(e -> ActionRecord.ACTION_NAME_DELETE + "|" + String.join("|", e))
         .sorted().toList();
      assertEquals(wanted, actual);
   }

   private static String[] record(String type, String path, boolean success) {
      return new String[] { type, "Data Source/" + path, success ?
         ActionRecord.ACTION_STATUS_SUCCESS : ActionRecord.ACTION_STATUS_FAILURE };
   }

   @SuppressWarnings("unchecked")
   private static <T> T audited(T target, EventAspect aspect) {
      AspectJProxyFactory factory = new AspectJProxyFactory(target);
      factory.setProxyTargetClass(true);
      factory.addAspect(aspect);
      return (T) factory.getProxy();
   }

   // a data source P with an additional connection, a folder P holding P/<P>X and P/<P>Sub/<P>Y
   private void clash(String path) {
      addFolder(path);
      addSource(path + "/" + path + "X");
      addFolder(path + "/" + path + "Sub");
      addSource(path + "/" + path + "Sub/" + path + "Y");
      addSource(path);
      ((JDBCDataSource) registry.getDataSource(path)).addDatasource(source(path + "Add"));
      registry.clearCache();
      assertTrue(registry.getDataSourcePathClashes().contains(path), "not seeded: " + path);
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   private void addSource(String path) {
      registry.setDataSource(source(path), false);
   }

   // a worksheet depends on the data source
   private void dependOn(String path) throws Exception {
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(new ArrayList<>(List.of(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws77819", null))));
      DependencyStorageService.getInstance().put(sourceId(path), info);
      dependencies.add(path);
      assertFalse(DependencyTool.getDependencies(sourceId(path)).isEmpty(), "not seeded");
   }

   private static String sourceId(String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null)
         .toIdentifier();
   }

   private static String key(ResourceType type, String path) {
      return type + "|" + path;
   }

   private static SelectedDataSourcesRequest request(List<SelectedDataSourceItem> dataSources,
                                                     List<SelectedDataSourceItem> folders)
   {
      return ImmutableSelectedDataSourcesRequest.builder()
         .dataSources(dataSources).folders(folders).build();
   }

   private static SelectedDataSourceItem item(String path) {
      return ImmutableSelectedDataSourceItem.builder().name(path).path(path).build();
   }

   private static TreeNodeInfo[] bothSides(String path, boolean dataSourceFirst) {
      TreeNodeInfo dataSource = node(path, RepositoryEntry.DATA_SOURCE);
      TreeNodeInfo folder = node(path, RepositoryEntry.DATA_SOURCE_FOLDER);
      return dataSourceFirst ? new TreeNodeInfo[] { dataSource, folder } :
         new TreeNodeInfo[] { folder, dataSource };
   }

   private static TreeNodeInfo node(String path, int type) {
      int index = path.lastIndexOf('/');
      return TreeNodeInfo.builder()
         .label(index < 0 ? path : path.substring(index + 1))
         .path(path)
         .type(type)
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
}
