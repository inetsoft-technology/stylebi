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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XDataSourceWrapper;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77561: older versions kept the connection test query of an additional connection in
 * SreeEnv under the additional connection name alone. Saving the parent data source with an
 * additional connection renamed or dropped, or renaming the additional connection node, must
 * remove the old name's key.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DatabaseDatasourcesServiceAdditionalTestQueryTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseDatasourcesServiceAdditionalTestQueryTest {
   private static final String PARENT = "parentDs";
   private static final String[] NAMES = { PARENT, "addOld", "addNew", "addGone", "addKeep" };
   private static final String URL = "jdbc:derby:memory:bug77561;create=true";

   // AdditionalConnectionDataSource gets the registry and the repository from Spring, so they
   // are beans that are created before the context
   private static final DataSourceRegistry REGISTRY =
      mock(DataSourceRegistry.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
   private static final XRepository REPOSITORY = mock(XRepository.class);

   static {
      stubLifecycle();
   }

   private Map<String, Object> store;
   private DatabaseDatasourcesService service;
   private Principal principal;
   private Principal oldContext;

   @BeforeEach
   void setUp() throws Exception {
      oldContext = ThreadContext.getContextPrincipal();
      store = new HashMap<>();
      reset(REGISTRY, REPOSITORY);
      stubLifecycle();

      // an in-memory registry storage, so that the real additional connection methods run
      doReturn(true).when(REGISTRY).checkPermission(any(), anyString(), any());
      doAnswer(inv -> store.get(inv.<String>getArgument(0)) instanceof XDataSourceWrapper wrapper ?
         wrapper.getSource() : null).when(REGISTRY).getDataSource(anyString());
      doReturn(null).when(REGISTRY).getDataSourceFolder(anyString());
      doAnswer(inv -> store.keySet().toArray(new String[0]))
         .when(REGISTRY).getDataSourceFullNames();
      XDataModel model = mock(XDataModel.class);
      when(model.getPartitionNames()).thenReturn(new String[0]);
      when(model.getLogicalModelNames()).thenReturn(new String[0]);
      doReturn(model).when(REGISTRY).getDataModel(anyString());
      doAnswer(inv -> {
         String prefix = inv.getArgument(0);
         return store.keySet().stream()
            .filter(path -> path.startsWith(prefix) && !path.equals(prefix))
            .map(path -> new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.DATA_SOURCE, path, null))
            .toArray(AssetEntry[]::new);
      }).when(REGISTRY).getEntries(anyString(), any(AssetEntry.Type.class));
      // the storage holds data sources only, no folders
      doAnswer(inv -> inv.<AssetEntry>getArgument(0).getType() == AssetEntry.Type.DATA_SOURCE &&
         store.containsKey(inv.<AssetEntry>getArgument(0).getPath()))
         .when(REGISTRY).containObject(any(AssetEntry.class));
      doAnswer(inv -> store.get(inv.<AssetEntry>getArgument(0).getPath()))
         .when(REGISTRY).getObject(any(AssetEntry.class), anyBoolean());
      doAnswer(inv -> store.put(inv.<AssetEntry>getArgument(0).getPath(), inv.getArgument(1)))
         .when(REGISTRY).setObject(any(AssetEntry.class), any());
      doAnswer(inv -> store.remove(inv.<AssetEntry>getArgument(0).getPath()))
         .when(REGISTRY).removeObject(any(AssetEntry.class));

      doAnswer(inv -> REGISTRY.getDataSource(inv.getArgument(0)))
         .when(REPOSITORY).getDataSource(anyString());
      when(REPOSITORY.getDataSourceNames()).thenReturn(new String[] { PARENT });
      when(REPOSITORY.getDataSourceFullNames()).thenReturn(new String[] { PARENT });

      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      service = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         security, mock(DatabaseSettingsService.class), REPOSITORY,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), REGISTRY, mock(RenameTransformHandler.class));

      principal = asOrg("orga");
      JDBCDataSource parent = customSource(PARENT);
      store.put(PARENT, new XDataSourceWrapper(parent));
      parent.addDatasource(customSource("addOld"));
      parent.addDatasource(customSource("addGone"));
      parent.addDatasource(customSource("addKeep"));
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      for(String name : NAMES) {
         SreeEnv.remove(key(name));

         for(String org : new String[] { "orga", "orgb" }) {
            SreeEnv.remove("inetsoft.org." + org + "." + key(name));
         }
      }

      ThreadContext.setContextPrincipal(oldContext);
   }

   @Test
   void parentSaveRemovesTheKeysOfRenamedAndDroppedAdditionalConnections() throws Exception {
      JDBCUtil.setConnectionTestQuery("addOld", "SELECT OLD");
      JDBCUtil.setConnectionTestQuery("addGone", "SELECT GONE");
      JDBCUtil.setConnectionTestQuery("addKeep", "SELECT KEEP");
      JDBCDataSource parent = stored(PARENT);

      // addOld is renamed to addNew, addKeep is kept and addGone is dropped
      DatabaseDefinition renamed = edit(parent.getDataSource("addOld"));
      renamed.setOldName("addOld");
      renamed.setName("addNew");
      DatabaseDefinition kept = edit(parent.getDataSource("addKeep"));
      kept.setOldName("addKeep");
      saveParent(parent, renamed, kept);

      assertEquals(Set.of(PARENT, PARENT + "/addNew", PARENT + "/addKeep"), store.keySet());
      assertNull(JDBCUtil.getConnectionTestQuery("addOld"),
                 "the renamed additional connection's test query was left behind");
      assertNull(JDBCUtil.getConnectionTestQuery("addGone"),
                 "the dropped additional connection's test query was left behind");
      assertNull(JDBCUtil.getConnectionTestQuery("addKeep"));

      // the editor saved the legacy test queries in the pool properties
      parent = stored(PARENT);
      assertEquals("SELECT OLD", testQuery(parent.getDataSource("addNew")));
      assertEquals("SELECT KEEP", testQuery(parent.getDataSource("addKeep")),
                   "the kept additional connection lost its test query");
   }

   @Test
   void parentSaveKeepsTheKeyOfADataSourceOfThatName() throws Exception {
      store.put("addGone", new XDataSourceWrapper(customSource("addGone")));
      JDBCUtil.setConnectionTestQuery("addGone", "SELECT ROOT");
      JDBCDataSource parent = stored(PARENT);

      DatabaseDefinition kept = edit(parent.getDataSource("addKeep"));
      kept.setOldName("addKeep");
      saveParent(parent, kept);

      assertFalse(store.containsKey(PARENT + "/addGone"));
      assertEquals("SELECT ROOT", JDBCUtil.getConnectionTestQuery("addGone"),
                   "the test query of another data source was removed");
   }

   @Test
   void parentSaveDoesNotTouchOtherOrgs() throws Exception {
      asOrg("orgb");
      JDBCUtil.setConnectionTestQuery("addGone", "SELECT ORGB");
      principal = asOrg("orga");
      JDBCUtil.setConnectionTestQuery("addGone", "SELECT GONE");
      JDBCDataSource parent = stored(PARENT);

      saveParent(parent);

      assertNull(JDBCUtil.getConnectionTestQuery("addGone"));
      asOrg("orgb");
      assertEquals("SELECT ORGB", JDBCUtil.getConnectionTestQuery("addGone"));
   }

   @Test
   void additionalConnectionRenameRemovesTheOldKey() throws Exception {
      JDBCUtil.setConnectionTestQuery("addOld", "SELECT OLD");
      // the EM repository tree reads the additional connection through its parent, which sets
      // its base data source
      JDBCDataSource additional = stored(PARENT).getDataSource("addOld");
      assertNotNull(additional.getBaseDatasource());

      DatabaseDefinition definition = edit(additional);
      definition.setName("addNew");
      service.saveDatabase(PARENT + "/addOld", DataSourceSettingsModel.builder()
         .uploadEnabled(false).dataSource(definition).build(),
                           ActionRecord.ACTION_NAME_EDIT, principal);

      assertTrue(store.containsKey(PARENT + "/addNew"));
      assertFalse(store.containsKey(PARENT + "/addOld"));
      assertNull(JDBCUtil.getConnectionTestQuery("addOld"),
                 "the renamed additional connection's test query was left behind");
      assertEquals("SELECT OLD", testQuery(stored(PARENT).getDataSource("addNew")));
   }

   private void saveParent(JDBCDataSource parent, DatabaseDefinition... additionals)
      throws Exception
   {
      DataSourceSettingsModel model = DataSourceSettingsModel.builder()
         .uploadEnabled(false)
         .dataSource(edit(parent))
         .additionalDataSources(additionals)
         .build();
      service.saveDatabase(PARENT, model, ActionRecord.ACTION_NAME_EDIT, principal);
   }

   private JDBCDataSource stored(String path) {
      return (JDBCDataSource) REGISTRY.getDataSource(path);
   }

   private static JDBCDataSource customSource(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }

   // the definition the data source editor loads
   private static DatabaseDefinition edit(JDBCDataSource dataSource) {
      return JDBCUtil.buildDatabaseDefinition(
         dataSource, JDBCUtil.getJDBCDatabaseType(CustomDatabaseType.TYPE));
   }

   private static String testQuery(JDBCDataSource dataSource) {
      return dataSource.getPoolProperties().get(JDBCUtil.CONNECTION_TEST_QUERY_PROPERTY);
   }

   private static String key(String name) {
      return "inetsoft.uql.jdbc.pool." + name + ".connectionTestQuery";
   }

   private static Principal asOrg(String org) {
      Principal principal = new SRPrincipal(new IdentityID("admin", org), new IdentityID[0],
                                            new String[0], org, Tool.getSecureRandom().nextLong());
      ThreadContext.setContextPrincipal(principal);
      return principal;
   }

   private static void stubLifecycle() {
      doNothing().when(REGISTRY).setListeners();
      doNothing().when(REGISTRY).shutdown();
   }

   @Configuration
   static class Beans {
      // JDBCDataSource's constructor needs the CredentialService bean, whose constructor is
      // package private
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public DataSourceRegistry dataSourceRegistry() {
         return REGISTRY;
      }

      @Bean
      public XRepository xRepository() {
         return REPOSITORY;
      }

      @Bean
      public Config config() {
         return mock(Config.class);
      }
   }
}
