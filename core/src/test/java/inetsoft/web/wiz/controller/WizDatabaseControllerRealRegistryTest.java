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

/*
 * Exercises WizDatabaseController.deleteDatasources() against a REAL DataSourceRegistry (not
 * mocked) -- the one test class in this run that a wiz-only-hide implementation could not pass by
 * coincidence (charter counter-assertion "a wiz-side delete must be a real StyleBI-side deletion"),
 * and the one that resolves, by actually running it rather than reading the recursion, whether
 * deleting a folder orphans a datasource nested inside one of its sub-folders (charter counter-
 * assertion SS-C6, 03-reconcile.md).
 *
 * Every verification reads back through DataSourceRegistry directly (getDataSource/
 * getDataSourceFolder), never through WizDatabaseController's own browse endpoint -- so this
 * cannot be satisfied by a bridge that merely filters what browse() returns.
 */

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.DependenciesInfo;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.ConnectionPoolFactory;
import inetsoft.uql.jdbc.DriverService;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.SQLExecutor;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.*;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.wiz.model.WizDatasourceDeleteItemResult;
import inetsoft.web.wiz.model.WizDatasourceDeleteResult;
import inetsoft.web.wiz.request.WizDatasourceDeleteRequest;
import inetsoft.web.wiz.request.WizDatasourceRef;
import inetsoft.web.wiz.service.EndpointCatalogReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import inetsoft.sree.internal.cluster.Cluster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  WizDatabaseControllerRealRegistryTest.IndexedStorageConfig.class,
                                  WizDatabaseControllerRealRegistryTest.CredentialServiceConfig.class,
                                  WizDatabaseControllerRealRegistryTest.DataSourceConfig.class,
                                  WizDatabaseControllerRealRegistryTest.DependencyStorageConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WizDatabaseControllerRealRegistryTest {
   /** Same reason as {@code OrgLifecycleAssetContentMigrationTest}: must be the bean, not a bare {@code new}. */
   @Configuration
   static class IndexedStorageConfig {
      @Bean
      public IndexedStorage indexedStorage(BlobStorageManager blobStorageManager) {
         return new BlobIndexedStorage(blobStorageManager);
      }
   }

   /**
    * {@code JDBCDataSource}'s constructor needs a real {@code CredentialService} instance --
    * {@code BaseTestConfiguration} does not provide one. Reflection reaches its package-private
    * constructor the same way {@code OrgLifecycleAssetContentMigrationTest} already does.
    */
   @Configuration
   static class CredentialServiceConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }

   /**
    * {@code XDataSourceWrapper.parseXML()} resolves a data source's concrete class by type name
    * via {@code Config} -> {@code Drivers} -> {@code Plugins}. With zero plugins installed,
    * {@code Drivers.getDriverClass()} silently returns null and the deserialized wrapper is left
    * hollow -- a placeholder {@code DriverService} seeded via reflection (there is no public API
    * to register one without a real plugin) makes a plain {@code JDBCDataSource} round-trip
    * through real storage correctly, the same fix {@code OrgLifecycleAssetContentMigrationTest}
    * already established.
    */
   @Configuration
   static class DataSourceConfig {
      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster, eventPublisher);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         return mock(ConnectionPoolFactory.class);
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory)
         throws Exception
      {
         Drivers drivers = new Drivers(plugins, connectionPoolFactory);
         Field field = Drivers.class.getDeclaredField("driverServices");
         field.setAccessible(true);
         Map<String, List<DriverService>> services = new HashMap<>();
         services.put("test-placeholder", List.of(new DriverService() {
            @Override
            public boolean matches(String driver, String url) {
               return false;
            }

            @Override
            public SQLExecutor getSQLExecutor() {
               return null;
            }

            @Override
            public Set<String> getDrivers() {
               return Set.of();
            }
         }));
         field.set(drivers, services);
         return drivers;
      }
   }

   /**
    * {@code DependencyStorageService} is a {@code @Service} picked up by component scan in
    * production, not present in this minimal test context. {@code RepositoryObjectService}'s
    * dependency check reaches it through the static
    * {@code DependencyStorageService.getInstance()} -> {@code ConfigurationContext.getSpringBean()},
    * which throws when the bean is missing, and {@code DependencyTool.getDependencies} swallows
    * that into "no dependencies" -- exactly the wrong answer for the has-a-real-dependency fixture
    * below, so this bean has to be present and stubbed per test.
    */
   @Configuration
   static class DependencyStorageConfig {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      reset(dependencyStorageService);

      dataSourceRegistry = new DataSourceRegistry(indexedStorage, config, cluster);
      dataSourceRegistry.setListeners();
      // Creates the root AssetFolder if storage has none yet -- without this, getRoot() returns
      // null and every recursive delete/enumeration silently no-ops instead of finding anything.
      dataSourceRegistry.init();

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      RepositoryObjectService repositoryObjectService = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class),
         securityProvider, mock(ResourcePermissionService.class), mock(XRepository.class),
         mock(RepositoryDashboardService.class), mock(DataModelFolderManagerService.class),
         dataSourceRegistry, mock(LibManagerProvider.class), mock(RecycleBin.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         mock(RepletRegistryManager.class), mock(DashboardRegistryManager.class));

      DataSourceBrowserService dataSourceBrowserService = new DataSourceBrowserService(
         mock(SecurityEngine.class), repositoryObjectService, mock(XRepository.class),
         mock(DataSourceService.class), dataSourceRegistry, config, mock(RenameTransformHandler.class));

      SecurityEngine controllerSecurityEngine = mock(SecurityEngine.class);
      when(controllerSecurityEngine.checkPermission(any(), any(), anyString(), any()))
         .thenReturn(true);

      controller = new WizDatabaseController(
         dataSourceBrowserService, mock(DataSourceStatusService.class),
         mock(DatabaseDatasourcesService.class), mock(DatabaseTypeService.class),
         controllerSecurityEngine, config, mock(XRepository.class),
         mock(EndpointCatalogReader.class), mock(DatasourcesService.class));

      principal = mock(Principal.class);
   }

   /**
    * Charter assertion 3, corrected (03-reconcile.md): force=false refuses a folder delete when a
    * direct child has a REAL outer dependency; force=true deletes it anyway. Both halves verified
    * against the real registry, read back independently of the delete call.
    */
   @Test
   void deleteFolder_forceFalseRefusesWhenChildHasARealOuterDependency() throws Exception {
      seedFolder("dep-folder");
      seedDataSource("dep-folder/ds1");
      seedDependency("dep-folder/ds1");

      WizDatasourceDeleteResult result = controller.deleteDatasources(
         new WizDatasourceDeleteRequest(
            List.of(new WizDatasourceRef("dep-folder", "dep-folder", true)), false),
         principal);

      WizDatasourceDeleteItemResult item = result.results().get(0);
      assertFalse(item.ok(), "a child with a real outer dependency must refuse force=false");
      assertEquals(WizDatasourceDeleteItemResult.HAS_DEPENDENCIES, item.reason());
      assertNotNull(dataSourceRegistry.getDataSource("dep-folder/ds1"),
                    "a refused delete must not have removed the child");
      assertNotNull(dataSourceRegistry.getDataSourceFolder("dep-folder"),
                    "a refused delete must not have removed the folder");
   }

   @Test
   void deleteFolder_forceTrueDeletesBothWhenChildHasARealOuterDependency() throws Exception {
      seedFolder("dep-folder-force");
      seedDataSource("dep-folder-force/ds1");
      seedDependency("dep-folder-force/ds1");

      WizDatasourceDeleteResult result = controller.deleteDatasources(
         new WizDatasourceDeleteRequest(
            List.of(new WizDatasourceRef("dep-folder-force", "dep-folder-force", true)), true),
         principal);

      assertTrue(result.results().get(0).ok());
      // Independent read path -- DataSourceRegistry directly, not WizDatabaseController's browse
      // endpoint -- so a wiz-only-hide implementation could not pass this by coincidence.
      assertNull(dataSourceRegistry.getDataSource("dep-folder-force/ds1"));
      assertNull(dataSourceRegistry.getDataSourceFolder("dep-folder-force"));
   }

   /**
    * Pins the corrected rule itself, not just the old wrong one: a plain, dependency-free child
    * must delete cleanly WITHOUT force, because the real gate is dependency-based, not emptiness-
    * based -- a non-empty folder is not, by itself, a reason to refuse.
    */
   @Test
   void deleteFolder_forceFalseSucceedsWhenChildHasNoOuterDependency() throws Exception {
      seedFolder("clean-folder");
      seedDataSource("clean-folder/ds1");
      // no seedDependency() call -- dependencyStorageService stays unstubbed for this path, i.e.
      // DependencyTool.getDependencies() reports none.

      WizDatasourceDeleteResult result = controller.deleteDatasources(
         new WizDatasourceDeleteRequest(
            List.of(new WizDatasourceRef("clean-folder", "clean-folder", true)), false),
         principal);

      assertTrue(result.results().get(0).ok(),
                 "a non-empty folder with no dependency conflict must delete without force");
      assertNull(dataSourceRegistry.getDataSource("clean-folder/ds1"));
      assertNull(dataSourceRegistry.getDataSourceFolder("clean-folder"));
   }

   /**
    * Charter counter-assertion SS-C6 (03-reconcile.md): deleting a folder must not orphan a
    * datasource nested inside a sub-folder of it. Resolved here by actually running it: confirmed
    * NOT orphaned -- DataSourceRegistry.removeDataSourceFolder's own low-level recursive cleanup
    * (a separate step from RepositoryObjectService's direct-children-only dependency-check loop)
    * matches by path PREFIX, reaching any depth, not just direct children. See 04-build.md for the
    * full mechanism this test pins.
    */
   @Test
   void deleteFolder_doesNotOrphanADatasourceNestedInASubfolder() throws Exception {
      seedFolder("nest");
      seedFolder("nest/sub");
      seedDataSource("nest/sub/ds");

      WizDatasourceDeleteResult result = controller.deleteDatasources(
         new WizDatasourceDeleteRequest(List.of(new WizDatasourceRef("nest", "nest", true)), true),
         principal);

      assertTrue(result.results().get(0).ok());
      assertNull(dataSourceRegistry.getDataSource("nest/sub/ds"),
                 "the nested datasource must not be left stranded/unreachable");
      assertNull(dataSourceRegistry.getDataSourceFolder("nest/sub"));
      assertNull(dataSourceRegistry.getDataSourceFolder("nest"));
   }

   private void seedFolder(String path) {
      dataSourceRegistry.setDataSourceFolder(
         new DataSourceFolder(path, java.time.LocalDateTime.now(), "admin"));
   }

   private void seedDataSource(String path) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(path);
      ds.setCustom(true);
      ds.setDriver("org.h2.Driver");
      ds.setURL("jdbc:h2:mem:" + path.replace('/', '_'));
      ds.setRequireLogin(false);
      dataSourceRegistry.setDataSource(ds, false);
   }

   /** Stubs a REAL outer dependency on the given data source path -- not merely non-empty. */
   private void seedDependency(String dataSourcePath) throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, dataSourcePath, null);
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(List.of((AssetObject) new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null)));
      when(dependencyStorageService.getWithOrg(eq(entry.toIdentifier()), any()))
         .thenReturn(info);
   }

   @Autowired private DependencyStorageService dependencyStorageService;
   @Autowired private IndexedStorage indexedStorage;
   @Autowired private Config config;
   @Autowired private Cluster cluster;

   private DataSourceRegistry dataSourceRegistry;
   private WizDatabaseController controller;
   private Principal principal;
}
