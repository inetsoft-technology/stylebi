/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.datasource;

import inetsoft.web.admin.idgen.MD5IdentifierGenerator;
import inetsoft.report.internal.license.ElasticLicenseService;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.DataSourceRenameException;
import inetsoft.uql.util.Drivers;
import inetsoft.util.*;
import inetsoft.util.audit.Audit;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77841: a rename or move of a data source through the public data source API (also used by
 * the shell DSL) that is refused must write nothing: no parent folder is created and the
 * dependents aren't renamed. A rename that moves the data source still renames its dependents,
 * also a move to a folder that keeps the name, for which the service queues the only transform.
 * Runs on the real registry, XEngine, dependency index and rename transform.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceServiceRenameRefusedTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceServiceRenameRefusedTest {
   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      clearInvocations(transformHandler);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(security.getSecurityProvider()).thenReturn(provider);
      when(security.isSecurityEnabled()).thenReturn(true);
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);
      audit = mockStatic(Audit.class);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      registry.clearCache();
      audit.close();
      securityStatic.close();
   }

   // a data source that shares its path with a non-empty folder (older data) can't be moved on
   // its own: no folder is created for the new path and its dependents keep its name
   @Test
   void refusedMoveCreatesNoFolderAndRenamesNoDependent() throws Exception {
      String wsId = seedClash("tP");

      assertThrows(MessageException.class, () -> service("id1", "tP", repository)
         .updateDataSource("id1", properties("tQ/tP2"), principal));

      assertNotNull(registry.getDataSource("tP"));
      assertNull(registry.getDataSource("tQ/tP2"));
      assertNull(registry.getDataSourceFolder("tQ"), "folder created by a refused move");
      assertNotRenamed(wsId, "tP");
   }

   @Test
   void refusedRenameRenamesNoDependent() throws Exception {
      String wsId = seedClash("uP");

      assertThrows(MessageException.class, () -> service("id2", "uP", repository)
         .updateDataSource("id2", properties("uP2"), principal));

      assertNotNull(registry.getDataSource("uP"));
      assertNull(registry.getDataSource("uP2"));
      assertNotRenamed(wsId, "uP");
   }

   // a write that fails with nothing moved doesn't rename the dependents
   @Test
   void failedMoveRenamesNoDependent() throws Exception {
      source("fP");
      String wsId = seedDependentWorksheet("ws77841f", "fP");
      XRepository failing = mock(XRepository.class, AdditionalAnswers.delegatesTo(repository));
      doThrow(new DataSourceRenameException("fP", "fQ/fP", "fP", Map.of(),
                                            new IOException("write failed")))
         .when(failing).updateDataSource(any(), eq("fP"), eq(false));

      assertThrows(DataSourceRenameException.class, () -> service("id3", "fP", failing)
         .updateDataSource("id3", properties("fQ/fP"), principal));

      assertNotRenamed(wsId, "fP");
   }

   // a write that fails after the data source was moved still renames its dependents
   @Test
   void partlyFailedMoveRenamesDependents() throws Exception {
      source("pP");
      String wsId = seedDependentWorksheet("ws77841p", "pP");
      XRepository failing = mock(XRepository.class, AdditionalAnswers.delegatesTo(repository));
      doAnswer(inv -> {
         repository.updateDataSource(inv.getArgument(0), "pP", false);
         throw new DataSourceRenameException("pP", "pQ/pP", "pP/x", Map.of("pP", "pQ/pP"),
                                             new IOException("write failed"));
      }).when(failing).updateDataSource(any(), eq("pP"), eq(false));

      assertThrows(DataSourceRenameException.class, () -> service("id4", "pP", failing)
         .updateDataSource("id4", properties("pQ/pP"), principal));

      assertEquals("pQ/pP", waitForRename(wsId, "pQ/pP"));
   }

   // a move to a folder that keeps the name: the service queues the only transform
   @Test
   void moveToFolderRenamesDependents() throws Exception {
      source("sP");
      String wsId = seedDependentWorksheet("ws77841s", "sP");

      service("id5", "sP", repository).updateDataSource("id5", properties("sQ/sP"), principal);

      assertNotNull(registry.getDataSource("sQ/sP"));
      assertNotNull(registry.getDataSourceFolder("sQ"));
      assertEquals("sQ/sP", waitForRename(wsId, "sQ/sP"));
   }

   @Test
   void renameRenamesDependents() throws Exception {
      source("vP");
      String wsId = seedDependentWorksheet("ws77841v", "vP");

      service("id6", "vP", repository).updateDataSource("id6", properties("vP2"), principal);

      assertNotNull(registry.getDataSource("vP2"));
      assertEquals("vP2", waitForRename(wsId, "vP2"));
   }

   /**
    * Seeds a data source at the path of a folder that holds a data source, which can't be created
    * any more but may exist (Bug #77691), and a worksheet that depends on the data source.
    */
   private String seedClash(String path) throws Exception {
      registry.setDataSourceFolder(new DataSourceFolder(path, LocalDateTime.now(), null));
      source(path + "/" + path + "X");
      JDBCDataSource dataSource = source(path);
      dataSource.addDatasource(newSource(path + "Add"));
      registry.setDataSource(dataSource, false);
      registry.clearCache();
      assertTrue(registry.getDataSourcePathClashes().contains(path), "clash not seeded");
      return seedDependentWorksheet("ws77841" + path, path);
   }

   private void assertNotRenamed(String wsId, String dataSource) throws Exception {
      verify(transformHandler, never()).addTransformTask(any(RenameDependencyInfo.class),
                                                         anyBoolean());
      // a task submitted anyway would have rewritten the worksheet by now
      Thread.sleep(2000L);
      assertEquals(dataSource, getQueryDataSource(wsId));
      assertFalse(DependencyTransformer.getDependencies(
         DependencyTransformer.getTabularAssetId(dataSource)).isEmpty(),
                  "the dependency index entry was moved");
   }

   private String waitForRename(String wsId, String target) throws Exception {
      long end = System.currentTimeMillis() + 30_000L;
      String name = getQueryDataSource(wsId);

      while(System.currentTimeMillis() < end && !target.equals(name)) {
         Thread.sleep(200L);
         name = getQueryDataSource(wsId);
      }

      return name;
   }

   private DataSourceService service(String id, String name, XRepository repository) {
      MD5IdentifierGenerator ids = mock(MD5IdentifierGenerator.class);
      when(ids.getName(id)).thenReturn(name);
      return new DataSourceService(repository, security, mock(DatabaseTypeService.class),
                                      ids, registry);
   }

   private String seedDependentWorksheet(String name, String dataSource) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.WORKSHEET, name, null);
      Worksheet ws = worksheet(dataSource);
      IndexedStorage.getIndexedStorage().putXMLSerializable(entry.toIdentifier(), ws);
      new LocalDependencyHandler(repository).updateSheetDependencies(ws, entry, true, false);
      assertFalse(DependencyTransformer.getDependencies(
         DependencyTransformer.getTabularAssetId(dataSource)).isEmpty(), "dependency not seeded");
      assertEquals(dataSource, getQueryDataSource(entry.toIdentifier()));
      return entry.toIdentifier();
   }

   /**
    * Gets the data source of the SQL query of the worksheet as stored. A renamed data source
    * that doesn't exist can't be parsed, so it is read from the XML.
    */
   private static String getQueryDataSource(String wsId) throws Exception {
      org.w3c.dom.Document doc = IndexedStorage.getIndexedStorage().getDocument(
         wsId, Organization.getDefaultOrganizationID());
      org.w3c.dom.NodeList list = doc.getElementsByTagName("query_jdbc");
      Set<String> names = new TreeSet<>();

      for(int i = 0; i < list.getLength(); i++) {
         org.w3c.dom.Element node = Tool.getChildNodeByTagName(
            (org.w3c.dom.Element) list.item(i), "datasource");

         if(node != null) {
            String attr = Tool.getAttribute(node, "name");
            names.add(attr != null ? attr : Tool.getValue(node));
         }
      }

      assertEquals(1, names.size(), names.toString());
      return names.iterator().next();
   }

   private static Worksheet worksheet(String dataSource) {
      Worksheet ws = new Worksheet();
      JDBCDataSource ds = newSource(dataSource);
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.setParseSQL(false);
      sql.setSQLString("select 1 from sysibm.sysdummy1", false);
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77841");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, dataSource, dataSource));
      table.setSQLEdited(true);
      ws.addAssembly(table);
      return ws;
   }

   private JDBCDataSource source(String path) {
      registry.setDataSource(newSource(path), false);
      return (JDBCDataSource) registry.getDataSource(path);
   }

   private static JDBCDataSource newSource(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver(DRIVER);
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }

   private static JdbcDataSourceProperties properties(String name) {
      JdbcDataSourceProperties properties = new JdbcDataSourceProperties();
      properties.setName(name);
      properties.setUrl(URL);
      properties.setDriver(DRIVER);
      properties.setTableName(JdbcDataSourceProperties.DEFAULT_OPTION);
      properties.setRequireLogin(false);
      return properties;
   }

   private static final String URL = "jdbc:derby:memory:bug77841;create=true";
   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";

   @Autowired private DataSourceRegistry registry;
   @Autowired private XRepository repository;
   @Autowired private RenameTransformHandler transformHandler;
   private SecurityEngine security;
   private Principal principal;
   private MockedStatic<SecurityEngine> securityStatic;
   private MockedStatic<Audit> audit;

   @Configuration
   static class Beans {
      @Bean
      public ElasticLicenseService elasticLicenseService() {
         return mock(ElasticLicenseService.class);
      }

      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      // the real handler, which submits the transform to the test cluster that runs it; a spy,
      // to check that nothing is submitted
      @Bean
      public RenameTransformHandler renameTransformHandler(Cluster cluster,
                                                           DependencyStorageService storage)
      {
         return spy(new RenameTransformHandler(cluster, storage));
      }

      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

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
