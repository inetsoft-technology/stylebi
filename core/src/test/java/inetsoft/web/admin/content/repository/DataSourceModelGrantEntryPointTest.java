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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78203, through the service entry points of the three delete paths (EM Content &gt;
 * Repository {@code RepositoryObjectService}, the portal Data tab {@code DatasourcesService}, and
 * the public API force delete, which calls {@code XRepository.removeDataSource(path, true)} and
 * then removes the data source grant) and the EM JDBC editor that drops an additional
 * connection, with the production security engine and file permission store and the production
 * permission check. A data source created again with the same name, logical models and data
 * model folder, restricted to another user, must not be readable by the user who had grants on
 * the old models and folders; a sibling data source "P2" keeps its grants.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AdditionalConnectionPermissionRemovalTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceModelGrantEntryPointTest {
   private static final String URL = "jdbc:derby:memory:bug78203entry;create=true";
   private static final String ORG = Organization.getDefaultOrganizationID();

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private SecurityEngine engine;
   private SecurityProvider provider;
   private RepositoryObjectService objectService;
   private DatasourcesService portalService;
   private DatabaseDatasourcesService databaseService;
   private Principal admin;
   private final List<String[]> written = new ArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(new FileAuthenticationProvider()));
      authcChain.saveConfiguration();
      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();
      engine = SecurityEngine.getSecurity();
      engine.init();
      provider = engine.getSecurityProvider();
      registry.init();

      // the services' own permission checks of the admin pass, the permission writes are real
      SecurityProvider emProvider = mock(SecurityProvider.class);
      when(emProvider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      doAnswer(inv -> {
         provider.removePermission(inv.getArgument(0), inv.<String>getArgument(1));
         return null;
      }).when(emProvider).removePermission(any(ResourceType.class), anyString());
      SecurityEngine adminEngine =
         mock(SecurityEngine.class, org.mockito.AdditionalAnswers.delegatesTo(engine));
      doReturn(true).when(adminEngine).checkPermission(any(), any(), anyString(), any());

      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      objectService = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class), emProvider,
         mock(ResourcePermissionService.class), repository, mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), registry, mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         repletRegistries, mock(DashboardRegistryManager.class));
      portalService = new DatasourcesService(
         repository, adminEngine, mock(DataSourceStatusService.class), registry,
         Config.getConfig());
      databaseService = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         adminEngine, mock(DatabaseSettingsService.class), repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
      admin = new SRPrincipal(new IdentityID("admin", ORG), new IdentityID[0], new String[0], ORG,
                              Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      for(String[] key : written) {
         provider.removePermission(ResourceType.valueOf(key[0]), key[1]);
      }

      written.clear();
      registry.clearCache();
      SreeEnv.remove("security.enabled");
   }

   @Test
   void emRepositoryDelete() throws Exception {
      deleteAndRecreate("EmOrders", p -> {
         Method method = RepositoryObjectService.class.getDeclaredMethod(
            "deleteDataSource", String.class, boolean.class, Principal.class);
         method.setAccessible(true);
         assertNull(method.invoke(objectService, p, true, admin));
      });
   }

   @Test
   void portalDataTabDelete() throws Exception {
      deleteAndRecreate("PtOrders", p -> assertNull(portalService.deleteDataSource(p, p, true)));
   }

   // what DataSourceApiService.deleteDataSource (enterprise public API, force) does
   @Test
   void publicApiForceDelete() throws Exception {
      deleteAndRecreate("ApOrders", p -> {
         assertTrue(repository.removeDataSource(p, true));
         engine.removePermission(ResourceType.DATA_SOURCE, p);
      });
   }

   // the EM JDBC editor saves the data source without one of its additional connections
   @Test
   void emJdbcEditorDropOfAdditionalConnection() throws Exception {
      String p = "EdOrders";
      seed(p);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(p);
      parent.addDatasource(source("keep"));
      registry.setDataSource(parent, false);
      registry.clearCache();
      grant(ResourceType.DATA_MODEL_FOLDER, p + "::keep/F", "alice");
      assertTrue(canRead("alice", ResourceType.DATA_MODEL_FOLDER, p + "::add/F"), "precondition");

      parent = (JDBCDataSource) registry.getDataSource(p);
      DatabaseDefinition definition = edit(parent);
      DatabaseDefinition keep = edit(parent.getDataSource("keep"));
      keep.setOldName("keep");
      assertNull(databaseService.saveDatabase(
         p, DataSourceSettingsModel.builder().uploadEnabled(false).dataSource(definition)
            .additionalDataSources(keep).build(), ActionRecord.ACTION_NAME_EDIT, admin));
      registry.clearCache();

      assertNull(provider.getPermission(ResourceType.DATA_MODEL_FOLDER, p + "::add/F"));
      assertNotNull(provider.getPermission(ResourceType.DATA_MODEL_FOLDER, p + "::keep/F"));

      // the connection is added again, restricted to bob
      parent = (JDBCDataSource) registry.getDataSource(p);
      parent.addDatasource(source("add"));
      registry.setDataSource(parent, false);
      registry.clearCache();
      restrictToBob(ResourceType.DATA_SOURCE, p + "::add");
      assertFalse(canRead("alice", ResourceType.DATA_MODEL_FOLDER, p + "::add/F"),
                  "alice reads the folder of the re-created additional connection");
      assertTrue(canRead("alice", ResourceType.DATA_MODEL_FOLDER, p + "::keep/F"));
      // the data source's own models and folders are not touched by the editor save
      assertTrue(canRead("alice", ResourceType.QUERY, "LMR::" + p));
      assertTrue(canRead("alice", ResourceType.DATA_MODEL_FOLDER, p + "/F"));
   }

   private interface Delete {
      void delete(String path) throws Exception;
   }

   private void deleteAndRecreate(String p, Delete delete) throws Exception {
      String sibling = p + "2";
      seed(p);
      seed(sibling);

      for(String[] key : modelKeys(p)) {
         assertTrue(canRead("alice", ResourceType.valueOf(key[0]), key[1]), "precondition " +
            key[1]);
      }

      delete.delete(p);
      registry.clearCache();
      assertNull(registry.getDataSource(p), "precondition: deleted");

      // re-created with the same names, no grant given, restricted to bob
      createObjects(p);
      restrictToBob(ResourceType.DATA_SOURCE, p);
      restrictToBob(ResourceType.DATA_SOURCE, p + "::add");
      List<Executable> checks = new ArrayList<>();

      for(String[] key : modelKeys(p)) {
         ResourceType type = ResourceType.valueOf(key[0]);
         checks.add(() -> assertFalse(canRead("alice", type, key[1]),
                                      "alice reads the re-created " + key[0] + " " + key[1]));
         checks.add(() -> assertTrue(canRead("bob", type, key[1]),
                                     "bob doesn't read the re-created " + key[0] + " " + key[1]));
         checks.add(() -> assertNull(provider.getPermission(type, key[1]),
                                     "left in the store: " + key[0] + " " + key[1]));
      }

      assertAll(checks);

      for(String[] key : modelKeys(sibling)) {
         assertNotNull(provider.getPermission(ResourceType.valueOf(key[0]), key[1]),
                       "sibling lost " + key[1]);
         assertTrue(canRead("alice", ResourceType.valueOf(key[0]), key[1]),
                    "sibling " + key[1]);
      }
   }

   // JDBC data source p with additional connection "add", data model folder F, logical models
   // LMR at the root and LMF in F; p and p::add restricted to bob; alice granted the models and
   // folders
   private void seed(String p) {
      createObjects(p);
      restrictToBob(ResourceType.DATA_SOURCE, p);
      restrictToBob(ResourceType.DATA_SOURCE, p + "::add");

      for(String[] key : modelKeys(p)) {
         grant(ResourceType.valueOf(key[0]), key[1], "alice");
      }
   }

   private void createObjects(String p) {
      registry.setDataSource(source(p), false);
      JDBCDataSource dataSource = (JDBCDataSource) registry.getDataSource(p);
      dataSource.addDatasource(source("add"));
      registry.setDataSource(dataSource, false);
      XDataModel model = registry.getDataModel(p);

      if(model == null) {
         registry.setDataModel(new XDataModel(p));
         model = registry.getDataModel(p);
      }

      model.addFolder("F");
      registry.setDataModel(model);
      model.addLogicalModel(logicalModel("LMR", null));
      model.addLogicalModel(logicalModel("LMF", "F"));
      registry.clearCache();
      assertNotNull(registry.getDataModel(p).getLogicalModel("LMF"), "not created: " + p);
      assertNotNull(((JDBCDataSource) registry.getDataSource(p)).getDataSource("add"),
                    "not created: " + p + "::add");
   }

   private static List<String[]> modelKeys(String p) {
      return List.of(
         new String[] { "QUERY", "LMR::" + p },
         new String[] { "QUERY", "LMF::" + p + "^__^F" },
         new String[] { "DATA_MODEL_FOLDER", p + "/F" },
         new String[] { "DATA_MODEL_FOLDER", p + "::add/F" });
   }

   private boolean canRead(String user, ResourceType type, String resource) throws Exception {
      SRPrincipal principal = new SRPrincipal(new IdentityID(user, ORG),
                                              new IdentityID[] { new IdentityID("Everyone", ORG) },
                                              new String[0], ORG,
                                              Tool.getSecureRandom().nextLong());
      // the check of SecurityEngine.checkPermission after its login gate
      return provider.checkPermission(principal, type, resource, ResourceAction.READ);
   }

   private void grant(ResourceType type, String resource, String user) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ,
                               Set.of(new Permission.PermissionIdentity(user, ORG)));
      permission.updateGrantAllByOrg(ORG, true);
      provider.setPermission(type, resource, permission);
      written.add(new String[] { type.name(), resource });
   }

   private void restrictToBob(ResourceType type, String resource) {
      grant(type, resource, "bob");
   }

   private static XLogicalModel logicalModel(String name, String folder) {
      XLogicalModel model = new XLogicalModel(name);
      model.setFolder(folder);
      return model;
   }

   private static DatabaseDefinition edit(JDBCDataSource dataSource) {
      return JDBCUtil.buildDatabaseDefinition(
         dataSource, JDBCUtil.getJDBCDatabaseType(CustomDatabaseType.TYPE));
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
