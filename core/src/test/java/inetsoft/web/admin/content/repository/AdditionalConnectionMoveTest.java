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
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularView;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Bug #77670: an additional connection (path parent/name) must not be moved or saved by its path
 * out from under its parent data source, which would turn it into a standalone data source. A
 * data source in a data source folder (path folder/name) is still moved as before. The registry
 * and the repository are the real ones, so that a move runs through XEngine.updateDataSource.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AdditionalConnectionMoveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AdditionalConnectionMoveTest {
   private static final String URL = "jdbc:derby:memory:bug77670;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private SecurityEngine security;
   private RepositoryObjectService objectService;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.DATA_SOURCE, inv.<String>getArgument(1)));
      // the permission checks of the repository throw if denied, a mock lets everything pass
      RepletRegistryService registryService = mock(RepletRegistryService.class);
      // a move saves the replet registry of the destination owner at the end
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class))).thenReturn(mock(RepletRegistry.class));
      objectService = new RepositoryObjectService(
         registryService, mock(ContentRepositoryTreeService.class), mock(SecurityProvider.class),
         permissions, repository, mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), registry, mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         repletRegistries, mock(DashboardRegistryManager.class));
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   // EM Move (menu or drag-and-drop) of an additional connection to the root is refused, the
   // parent keeps it and a top-level data source with its name is left alone
   @Test
   void emMoveOfAnAdditionalConnectionIsRefused() throws Exception {
      JDBCDataSource topLevel = source("emTwin");
      topLevel.setDescription("top level");
      registry.setDataSource(topLevel, false);
      addParent("emParent", "emTwin", "emKeep");

      MessageException ex = assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(root(), node("emParent/emTwin", RepositoryEntry.DATA_SOURCE)), true, principal));
      assertTrue(ex.getMessage().contains("additional connection"), ex.getMessage());

      assertChildren("emParent", "emKeep", "emTwin");
      assertEquals("top level", registry.getDataSource("emTwin").getDescription());

      // a hand-made request with the type of a top-level data source is refused the same way
      assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(root(), node("emParent/emKeep",
                              RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER)),
         true, principal));
      assertChildren("emParent", "emKeep", "emTwin");
      assertNull(registry.getDataSource("emKeep"));
   }

   // a batch that mixes a top-level data source with an additional connection moves nothing
   @Test
   void emMoveOfAMixedBatchMovesNothing() throws Exception {
      registry.setDataSourceFolder(new DataSourceFolder("mixDest", LocalDateTime.now(), null));
      registry.setDataSource(source("mixTop"), false);
      addParent("mixParent", "mixAdd", "mixKeep");

      assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(folder("mixDest"),
                 node("mixTop", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER),
                 node("mixParent/mixAdd", RepositoryEntry.DATA_SOURCE)),
         true, principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("mixTop"));
      assertNull(registry.getDataSource("mixDest/mixTop"));
      assertNull(registry.getDataSource("mixDest/mixAdd"));
      assertChildren("mixParent", "mixAdd", "mixKeep");
   }

   // a data source in a data source folder isn't taken for an additional connection, it is moved
   // to the root and to another folder as before, together with its additional connections
   @Test
   void emMoveOfADataSourceInAFolder() throws Exception {
      registry.setDataSourceFolder(new DataSourceFolder("srcFolder", LocalDateTime.now(), null));
      registry.setDataSourceFolder(new DataSourceFolder("dstFolder", LocalDateTime.now(), null));
      addParent("srcFolder/inFolder", "inFolderAdd");
      assertFalse(registry.isAdditionalConnectionPath("srcFolder/inFolder"));
      assertTrue(registry.isAdditionalConnectionPath("srcFolder/inFolder/inFolderAdd"));

      objectService.moveFiles(
         request(root(), node("srcFolder/inFolder",
                              RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER)),
         true, principal);

      registry.clearCache();
      assertNull(registry.getDataSource("srcFolder/inFolder"));
      assertChildren("inFolder", "inFolderAdd");

      objectService.moveFiles(
         request(folder("dstFolder"), node("inFolder",
                                           RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER)),
         true, principal);

      registry.clearCache();
      assertNull(registry.getDataSource("inFolder"));
      assertChildren("dstFolder/inFolder", "inFolderAdd");
   }

   // moving a data source folder carries the additional connections of its data sources along
   @Test
   void emMoveOfAFolderWithAdditionalConnections() throws Exception {
      registry.setDataSourceFolder(new DataSourceFolder("carry", LocalDateTime.now(), null));
      registry.setDataSourceFolder(new DataSourceFolder("carryDest", LocalDateTime.now(), null));
      addParent("carry/carryParent", "carryAdd", "carryKeep");

      objectService.moveFiles(
         request(folder("carryDest"), node("carry", RepositoryEntry.DATA_SOURCE_FOLDER)),
         true, principal);

      registry.clearCache();
      assertNull(registry.getDataSource("carry/carryParent"));
      assertChildren("carryDest/carry/carryParent", "carryAdd", "carryKeep");
      assertTrue(registry.isAdditionalConnectionPath("carryDest/carry/carryParent/carryAdd"));
   }

   // a hand-made PUT /api/portal/data/datasources/{name} of an additional connection is refused,
   // whether the path is in the name or split into parentPath and name
   @Test
   void portalUpdateOfAnAdditionalConnectionIsRefused() throws Exception {
      addParent("putParent", "putAdd", "putKeep");
      DatasourcesService service = new DatasourcesService(
         repository, security, mock(DataSourceStatusService.class), registry, mock(Config.class));

      assertThrows(MessageException.class, () -> service.updateDataSource(
         "putParent/putAdd", definition("", "putAdd"), principal));
      assertThrows(MessageException.class, () -> service.updateDataSource(
         "putAdd", definition("putParent", "putAdd"), principal));

      assertChildren("putParent", "putAdd", "putKeep");
      assertNull(registry.getDataSource("putAdd"));
      assertNull(registry.getDataSource("putParent/putParent/putAdd"));
   }

   // a hand-made portal browser move (POST /api/data/datasources/move) of an additional
   // connection is refused
   @Test
   void portalBrowserMoveOfAnAdditionalConnectionIsRefused() throws Exception {
      addParent("browseParent", "browseAdd", "browseKeep");
      DataSourceBrowserService service = new DataSourceBrowserService(
         security, objectService, repository, mock(DataSourceService.class), registry,
         mock(Config.class), mock(RenameTransformHandler.class));
      MoveCommand move = new MoveCommand();
      move.setOldPath("browseParent/browseAdd");
      move.setPath("browseAdd");
      move.setName("browseAdd");
      move.setType(PortalDataType.DATABASE.name());

      assertThrows(MessageException.class,
                   () -> service.moveDataSource(new MoveCommand[] { move }, principal));

      assertChildren("browseParent", "browseAdd", "browseKeep");
      assertNull(registry.getDataSource("browseAdd"));
   }

   // the names of the additional connections start with a prefix of their own, since every test
   // of the class saves to the same storage
   private void addParent(String path, String... additionals) throws Exception {
      registry.setDataSource(source(path), false);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(path);

      for(String name : additionals) {
         parent.addDatasource(source(name));
      }
   }

   private void assertChildren(String parentPath, String... names) {
      // read from the storage, not from instances cached by the move
      registry.clearCache();
      XDataSource parent = registry.getDataSource(parentPath);
      assertNotNull(parent, parentPath);
      String[] expected = names.clone();
      Arrays.sort(expected);
      String[] children = ((JDBCDataSource) parent).getDataSourceNames();
      Arrays.sort(children);
      assertArrayEquals(expected, children, "additional connections of " + parentPath);

      for(String name : names) {
         assertNotNull(registry.getDataSource(parentPath + "/" + name), parentPath + "/" + name);
      }
   }

   private static MoveCopyTreeNodesRequest request(ContentRepositoryTreeNode destination,
                                                   ContentRepositoryTreeNode... source)
   {
      return MoveCopyTreeNodesRequest.builder()
         .source(List.of(source))
         .destination(destination)
         .build();
   }

   private static ContentRepositoryTreeNode root() {
      return node("/", RepositoryEntry.DATA_SOURCE_FOLDER);
   }

   private static ContentRepositoryTreeNode folder(String path) {
      return node(path, RepositoryEntry.DATA_SOURCE_FOLDER);
   }

   private static ContentRepositoryTreeNode node(String path, int type) {
      int index = path.lastIndexOf('/');
      return ContentRepositoryTreeNode.builder()
         .label(index < 0 ? path : path.substring(index + 1))
         .path(path)
         .type(type)
         .build();
   }

   // the definition that a hand-made PUT sends
   private static DataSourceDefinition definition(String parentPath, String name) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setType("jdbc");
      definition.setParentPath(parentPath);
      definition.setName(name);
      definition.setDescription("via PUT");
      definition.setTabularView(new TabularView());
      return definition;
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
