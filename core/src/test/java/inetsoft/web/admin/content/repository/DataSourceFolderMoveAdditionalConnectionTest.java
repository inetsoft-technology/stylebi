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
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77689: a data source folder move must move the permission "parent::name" of an
 * additional connection and keep its bare name, whatever order the registry lists the entries
 * of the folder in. The registry and the repository are the real ones, the permissions are kept
 * in a map, and the order of the data source entries of the folder is forced with a spy.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceFolderMoveAdditionalConnectionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceFolderMoveAdditionalConnectionTest {
   private static final String URL = "jdbc:derby:memory:bug77689;create=true";
   private static final String TABULAR = "folderMoveTabular";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   @Autowired
   private Config config;
   private final Map<String, Permission> store = new HashMap<>();
   private final List<String> order = new ArrayList<>();
   private SecurityEngine security;
   private SecurityProvider provider;
   private MockedStatic<SecurityEngine> securityStatic;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      store.clear();
      order.clear();
      provider = mock(SecurityProvider.class);
      when(provider.isVirtual()).thenReturn(false);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      // a non-virtual engine over the map, which the registry also gets from getSecurity()
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(security.getSecurityProvider()).thenReturn(provider);
      when(security.isSecurityEnabled()).thenReturn(true);
      when(security.getPermission(eq(ResourceType.DATA_SOURCE), anyString()))
         .thenAnswer(inv -> store.get(inv.<String>getArgument(1)));
      doAnswer(inv -> store.put(inv.getArgument(1), inv.getArgument(2)))
         .when(security).setPermission(eq(ResourceType.DATA_SOURCE), anyString(), any());
      doAnswer(inv -> store.remove(inv.<String>getArgument(1)))
         .when(security).removePermission(eq(ResourceType.DATA_SOURCE), anyString());
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      securityStatic.close();
   }

   // EM Move of a folder, an additional connection listed before its parent
   @Test
   void emFolderMoveChildFirst() throws Exception {
      addFolder("emF");
      addFolder("emDest");
      addParent("emF/emP", "emA", "emB");
      Permission parent = grant("emF/emP");
      Permission a = grant("emF/emP::emA");
      Permission b = grant("emF/emP::emB");

      emMove(childFirst(), "emF", "emDest");

      assertChildFirst("emF/emP");
      assertMoved("emDest/emF/emP", "emF/emP", parent, Map.of("emA", a, "emB", b));

      // a later save of the parent doesn't add the additional connections again under it
      JDBCDataSource moved = (JDBCDataSource) registry.getDataSource("emDest/emF/emP");
      repository.updateDataSource(moved, "emDest/emF/emP");
      assertChildren("emDest/emF/emP", "emA", "emB");
      registry.clearCache();
      assertNull(registry.getDataSource("emDest/emF/emP/emDest/emF/emP/emA"));
   }

   // portal move of a folder, an additional connection listed before its parent
   @Test
   void portalFolderMoveChildFirst() throws Exception {
      addFolder("ptF");
      addFolder("ptDest");
      addParent("ptF/ptP", "ptA");
      Permission parent = grant("ptF/ptP");
      Permission a = grant("ptF/ptP::ptA");
      DataSourceRegistry spy = childFirst();
      DataSourceBrowserService service = new DataSourceBrowserService(
         security, objectService(spy), repository, mock(DataSourceService.class), spy,
         mock(Config.class), mock(RenameTransformHandler.class));
      MoveCommand move = new MoveCommand();
      move.setOldPath("ptF");
      move.setPath("ptDest/ptF");
      move.setName("ptF");
      move.setType(PortalDataType.DATA_SOURCE_FOLDER.name());

      service.moveDataSource(new MoveCommand[] { move }, principal);

      assertChildFirst("ptF/ptP");
      assertMoved("ptDest/ptF/ptP", "ptF/ptP", parent, Map.of("ptA", a));
   }

   // EM Move of a folder holding a tabular (connector) data source with additional connections,
   // an additional connection listed before its parent
   @Test
   void emFolderMoveChildFirstTabular() throws Exception {
      addFolder("tbF");
      addFolder("tbDest");
      addTabularParent("tbF/tbP", "tbA", "tbB");
      Permission parent = grant("tbF/tbP");
      Permission a = grant("tbF/tbP::tbA");
      Permission b = grant("tbF/tbP::tbB");

      emMove(childFirst(), "tbF", "tbDest");

      assertChildFirst("tbF/tbP");
      assertMoved("tbDest/tbF/tbP", "tbF/tbP", parent, Map.of("tbA", a, "tbB", b));

      // a later save of the parent doesn't add the additional connections again under it
      XDataSource moved = registry.getDataSource("tbDest/tbF/tbP");
      repository.updateDataSource(moved, "tbDest/tbF/tbP");
      assertChildren("tbDest/tbF/tbP", "tbA", "tbB");
      registry.clearCache();
      assertNull(registry.getDataSource("tbDest/tbF/tbP/tbDest/tbF/tbP/tbA"));
   }

   // a data source in a subfolder of the moved folder
   @Test
   void nestedFolderMoveChildFirst() throws Exception {
      addFolder("nF");
      addFolder("nF/nG");
      addFolder("nDest");
      addParent("nF/nG/nP", "nA");
      Permission parent = grant("nF/nG/nP");
      Permission a = grant("nF/nG/nP::nA");

      emMove(childFirst(), "nF", "nDest");

      assertChildFirst("nF/nG/nP");
      assertNotNull(registry.getDataSourceFolder("nDest/nF/nG"));
      assertMoved("nDest/nF/nG/nP", "nF/nG/nP", parent, Map.of("nA", a));
   }

   // the parent can't be read, so it isn't renamed on its own and the folder's renameObjects
   // moves it with its additional connection. The permission of the additional connection must
   // move too.
   @Test
   void folderMoveWithAnUnreadableParent() throws Exception {
      addFolder("rdF");
      addFolder("rdDest");
      addParent("rdF/rdP", "rdA");
      Permission a = grant("rdF/rdP::rdA");
      DataSourceRegistry spy = childFirst();
      doReturn(false).when(spy)
         .checkPermission(ResourceType.DATA_SOURCE, "rdF/rdP", ResourceAction.READ);

      emMove(spy, "rdF", "rdDest");

      assertChildFirst("rdF/rdP");
      assertSame(a, perm("rdDest/rdF/rdP::rdA"));
      assertNull(perm("rdF/rdP::rdA"));
      assertTrue(containsDataSource("rdDest/rdF/rdP/rdA"));
      assertFalse(containsDataSource("rdF/rdP/rdA"));
      registry.clearCache();
      assertEquals("rdA", registry.getDataSource("rdDest/rdF/rdP/rdA").getFullName());
   }

   // the connector of the parent isn't installed. The EM and portal moves fail before the
   // rename for such a folder (DependencyTransformer.createDatasourceFolderDependencyInfo), so
   // the registry is called directly.
   @Test
   void folderMoveWithAParentOfAnUninstalledConnector() throws Exception {
      addFolder("unF");
      addFolder("unDest");
      addTabularParent("unF/unP", "unA");
      Permission a = grant("unF/unP::unA");
      DataSourceRegistry spy = childFirst();
      doReturn(null).when(config).getDataSourceClass(TABULAR);

      try {
         registry.clearCache();
         assertNull(registry.getDataSource("unF/unP"), "the connector is still installed");
         spy.renameDataSourceFolder("unF", "unDest/unF");
      }
      finally {
         doReturn(TestTabularDataSource.class.getName()).when(config).getDataSourceClass(TABULAR);
         registry.clearCache();
      }

      assertChildFirst("unF/unP");
      assertSame(a, perm("unDest/unF/unP::unA"));
      assertNull(perm("unF/unP::unA"));
      assertTrue(containsDataSource("unDest/unF/unP/unA"));
      assertFalse(containsDataSource("unF/unP/unA"));
   }

   // regression: an additional connection listed after its parent
   @Test
   void emFolderMoveParentFirst() throws Exception {
      addFolder("pfF");
      addFolder("pfDest");
      addParent("pfF/pfP", "pfA");
      addParent("pfF/pfQ");
      Permission parent = grant("pfF/pfP");
      Permission a = grant("pfF/pfP::pfA");
      Permission q = grant("pfF/pfQ");

      emMove(ordered(Comparator.naturalOrder()), "pfF", "pfDest");

      assertEquals("pfF/pfP", order.get(0), "the order isn't parent first: " + order);
      assertMoved("pfDest/pfF/pfP", "pfF/pfP", parent, Map.of("pfA", a));
      assertSame(q, perm("pfDest/pfF/pfQ"));
      assertNotNull(registry.getDataSource("pfDest/pfF/pfQ"));
   }

   // regression: a folder rename through XEngine.updateDataSourceFolder (portal and EM rename)
   @Test
   void folderRenameThroughTheRepository() throws Exception {
      addFolder("xrF");
      addParent("xrF/xrP", "xrA");
      Permission parent = grant("xrF/xrP");
      Permission a = grant("xrF/xrP::xrA");

      repository.updateDataSourceFolder(
         new DataSourceFolder("xrG", LocalDateTime.now(), null), "xrF");

      assertNull(registry.getDataSourceFolder("xrF"));
      assertNotNull(registry.getDataSourceFolder("xrG"));
      assertMoved("xrG/xrP", "xrF/xrP", parent, Map.of("xrA", a));
   }

   private void assertChildFirst(String parent) {
      assertTrue(order.indexOf(parent) > 0 && order.get(0).startsWith(parent + "/"),
                 "the order isn't child first: " + order);
   }

   private void assertMoved(String newParent, String oldParent, Permission parent,
                            Map<String, Permission> additionals)
   {
      registry.clearCache();
      assertNull(registry.getDataSource(oldParent));
      assertSame(parent, perm(newParent));
      assertNull(perm(oldParent));
      assertChildren(newParent, additionals.keySet().toArray(new String[0]));

      additionals.forEach((name, permission) -> {
         assertSame(permission, perm(newParent + "::" + name), newParent + "::" + name);
         assertNull(perm(oldParent + "::" + name), oldParent + "::" + name);
         assertEquals(name, registry.getDataSource(newParent + "/" + name).getFullName(),
                      "the stored name of " + newParent + "/" + name);
      });
   }

   private void assertChildren(String parentPath, String... names) {
      // read from the storage, not from instances cached by the move
      registry.clearCache();
      XDataSource parent = registry.getDataSource(parentPath);
      assertNotNull(parent, parentPath);
      String[] expected = names.clone();
      Arrays.sort(expected);
      String[] children = ((AdditionalConnectionDataSource<?>) parent).getDataSourceNames();
      Arrays.sort(children);
      assertArrayEquals(expected, children, "additional connections of " + parentPath);
   }

   private boolean containsDataSource(String path) {
      return registry.containObject(new AssetEntry(
         inetsoft.uql.asset.AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null));
   }

   // the registry, listing the data source entries of a folder in reverse path order, so an
   // additional connection comes before its parent
   private DataSourceRegistry childFirst() {
      return ordered(Comparator.reverseOrder());
   }

   private DataSourceRegistry ordered(Comparator<String> comparator) {
      DataSourceRegistry spy = spy(registry);
      doAnswer(inv -> {
         AssetEntry[] entries = (AssetEntry[]) inv.callRealMethod();
         Arrays.sort(entries, Comparator.comparing(AssetEntry::getPath, comparator));

         // the order of the folder that is moved, the first one listed with more than one entry
         if(entries.length > 1 && order.isEmpty()) {
            Arrays.stream(entries).map(AssetEntry::getPath).forEach(order::add);
         }

         return entries;
      }).when(spy).getEntries(anyString(), eq(AssetEntry.Type.DATA_SOURCE));
      return spy;
   }

   private void emMove(DataSourceRegistry spy, String folder, String destination)
      throws Exception
   {
      MoveCopyTreeNodesRequest request = MoveCopyTreeNodesRequest.builder()
         .source(List.of(node(folder)))
         .destination(node(destination))
         .build();
      objectService(spy).moveFiles(request, true, principal);
   }

   private RepositoryObjectService objectService(DataSourceRegistry spy) throws Exception {
      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.DATA_SOURCE_FOLDER, inv.<String>getArgument(1)));
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      return new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class), provider,
         permissions, repository, mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), spy, mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         repletRegistries, mock(DashboardRegistryManager.class));
   }

   private static ContentRepositoryTreeNode node(String path) {
      int index = path.lastIndexOf('/');
      return ContentRepositoryTreeNode.builder()
         .label(index < 0 ? path : path.substring(index + 1))
         .path(path)
         .type(RepositoryEntry.DATA_SOURCE_FOLDER)
         .build();
   }

   // the names start with a prefix of their own, since every test of the class saves to the
   // same storage
   private void addParent(String path, String... additionals) throws Exception {
      registry.setDataSource(source(path), false);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(path);

      for(String name : additionals) {
         parent.addDatasource(source(name));
      }
   }

   private void addTabularParent(String path, String... additionals) throws Exception {
      TestTabularDataSource parent = new TestTabularDataSource();
      parent.setName(path);
      registry.setDataSource(parent, false);
      parent = (TestTabularDataSource) registry.getDataSource(path);

      for(String additional : additionals) {
         TestTabularDataSource child = new TestTabularDataSource();
         child.setName(additional);
         parent.addDatasource(child);
      }
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   private Permission grant(String resource) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ, Set.of(new Permission.PermissionIdentity(
         "alice", Organization.getDefaultOrganizationID())));
      store.put(resource, permission);
      return permission;
   }

   private Permission perm(String resource) {
      return store.get(resource);
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

   public static class TestTabularDataSource extends TabularDataSource<TestTabularDataSource> {
      public TestTabularDataSource() {
         super(TABULAR, TestTabularDataSource.class);
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

      // knows the tabular data source type of this test
      @Bean
      @Primary
      public Config testConfig(Plugins plugins) throws Exception {
         Config config = spy(new Config(plugins));
         doReturn(TestTabularDataSource.class.getName()).when(config).getDataSourceClass(TABULAR);
         doReturn(TestTabularDataSource.class).when(config)
            .getClass(TABULAR, TestTabularDataSource.class.getName());
         return config;
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
