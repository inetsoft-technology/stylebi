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
package inetsoft.uql.service;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XDataSource;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77535: the connection test query of a JDBC data source is kept in SreeEnv under the data
 * source full name. Every rename, move and delete of a data source, from any UI or the API,
 * reaches DataSourceRegistry.renameDatasource or removeDataSource, which must move or remove
 * the key in the current organization's scope.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DataSourceRegistryConnectionTestQueryTest.CredentialServiceConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceRegistryConnectionTestQueryTest {
   private static final String HOST = Organization.getDefaultOrganizationID();
   private static final String[] NAMES = { "f/ds", "g/ds", "ds", "moved", "add1", "add2" };

   private DataSourceRegistry registry;
   private Map<String, XDataSource> sources;
   private Principal oldContext;

   @BeforeEach
   void setUp() {
      oldContext = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(null);
      sources = new HashMap<>();
      registry = mock(DataSourceRegistry.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

      // an in-memory registry storage, so that the real rename and remove methods run
      doReturn(true).when(registry).checkPermission(any(), anyString(), any());
      doAnswer(inv -> sources.get(inv.<String>getArgument(0)))
         .when(registry).getDataSource(anyString());
      doReturn(new DataSourceFolder()).when(registry).getDataSourceFolder(anyString());
      doReturn(null).when(registry).getDomain(anyString());
      doReturn(null).when(registry).getDataModel(anyString());
      doAnswer(inv -> entries(inv.getArgument(0)))
         .when(registry).getEntries(anyString());
      doAnswer(inv -> inv.getArgument(1) == AssetEntry.Type.DATA_SOURCE ?
         entries(inv.getArgument(0)) : new AssetEntry[0])
         .when(registry).getEntries(anyString(), any(AssetEntry.Type.class));
      doAnswer(inv -> sources.containsKey(inv.<AssetEntry>getArgument(0).getPath()))
         .when(registry).containObject(any(AssetEntry.class));
      doAnswer(inv -> {
         if(inv.getArgument(2) == AssetEntry.Type.DATA_SOURCE) {
            sources.put(inv.getArgument(1), sources.remove(inv.<String>getArgument(0)));
         }

         return null;
      }).when(registry).updateObject(anyString(), anyString(), any(), any());
      doAnswer(inv -> {
         String oprefix = inv.getArgument(0);
         String nprefix = inv.getArgument(1);

         for(String path : new ArrayList<>(sources.keySet())) {
            if(path.startsWith(oprefix)) {
               sources.put(nprefix + path.substring(oprefix.length()), sources.remove(path));
            }
         }

         return null;
      }).when(registry).renameObjects(anyString(), anyString(), anyBoolean(), anyBoolean());
      doNothing().when(registry).renameObjects(anyString(), anyString());
      doAnswer(inv -> sources.remove(inv.<AssetEntry>getArgument(0).getPath()))
         .when(registry).removeObject(any(AssetEntry.class));
      doAnswer(inv -> {
         for(AssetEntry entry : inv.<AssetEntry[]>getArgument(0)) {
            sources.remove(entry.getPath());
         }

         return null;
      }).when(registry).removeObjects(any(AssetEntry[].class));
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      for(String name : NAMES) {
         SreeEnv.remove(key(name));

         for(String org : new String[] { "orga", "orgb", HOST }) {
            SreeEnv.remove(orgKey(org, name));
         }
      }

      ThreadContext.setContextPrincipal(oldContext);
   }

   @Test
   void folderRenameMovesTheKey() {
      asOrg("orga");
      addSource("f/ds");
      addSource("f/ds/add1");
      JDBCUtil.setConnectionTestQuery("f/ds", "SELECT A");

      registry.renameDataSourceFolder("f", "g");

      assertTrue(sources.containsKey("g/ds"));
      assertEquals("SELECT A", JDBCUtil.getConnectionTestQuery("g/ds"),
                   "the moved data source lost its connection test query");
      assertNull(SreeEnv.getProperty(orgKey("orga", "f/ds"), false, false),
                 "the old connection test query key was left behind");
   }

   @Test
   void dataSourceRenameMovesTheKey() {
      asOrg("orga");
      addSource("f/ds");
      JDBCUtil.setConnectionTestQuery("f/ds", "SELECT A");

      registry.renameDatasource("f/ds", "moved");

      assertEquals("SELECT A", JDBCUtil.getConnectionTestQuery("moved"));
      assertNull(JDBCUtil.getConnectionTestQuery("f/ds"));
   }

   @Test
   void renameClearsAStaleKeyAtTheNewName() {
      asOrg("orga");
      addSource("f/ds");
      JDBCUtil.setConnectionTestQuery("moved", "SELECT STALE");

      registry.renameDatasource("f/ds", "moved");

      assertNull(JDBCUtil.getConnectionTestQuery("moved"),
                 "the renamed data source shows a deleted data source's test query");
   }

   @Test
   void removeRemovesTheKeyAndTheAdditionalConnectionKeys() {
      asOrg("orga");
      addSource("ds");
      addSource("ds/add1");
      addSource("ds/add2");
      addSource("add2");   // a data source whose full name is an additional connection name
      JDBCUtil.setConnectionTestQuery("ds", "SELECT A");
      JDBCUtil.setConnectionTestQuery("add1", "SELECT ADD1");
      JDBCUtil.setConnectionTestQuery("add2", "SELECT ADD2");

      registry.removeDataSource("ds");

      assertFalse(sources.containsKey("ds"));
      assertNull(SreeEnv.getProperty(orgKey("orga", "ds"), false, false),
                 "the removed data source's connection test query was left behind");
      assertNull(JDBCUtil.getConnectionTestQuery("add1"),
                 "the additional connection's connection test query was left behind");
      assertEquals("SELECT ADD2", JDBCUtil.getConnectionTestQuery("add2"),
                   "the test query of another data source was removed");
   }

   @Test
   void folderRemoveRemovesTheKeys() {
      asOrg("orga");
      addSource("f/ds");
      JDBCUtil.setConnectionTestQuery("f/ds", "SELECT A");

      registry.removeDataSourceFolder("f");

      assertNull(JDBCUtil.getConnectionTestQuery("f/ds"));
   }

   @Test
   void removeAndRenameDoNotTouchOtherOrgs() {
      asOrg("orga");
      JDBCUtil.setConnectionTestQuery("f/ds", "SELECT A");
      JDBCUtil.setConnectionTestQuery("ds", "SELECT A");
      asOrg("orgb");
      addSource("f/ds");
      addSource("ds");
      JDBCUtil.setConnectionTestQuery("f/ds", "SELECT B");
      JDBCUtil.setConnectionTestQuery("ds", "SELECT B");

      registry.renameDatasource("f/ds", "moved");
      registry.removeDataSource("ds");

      assertEquals("SELECT B", JDBCUtil.getConnectionTestQuery("moved"));
      assertNull(JDBCUtil.getConnectionTestQuery("ds"));
      asOrg("orga");
      assertEquals("SELECT A", JDBCUtil.getConnectionTestQuery("f/ds"));
      assertEquals("SELECT A", JDBCUtil.getConnectionTestQuery("ds"));
      assertNull(JDBCUtil.getConnectionTestQuery("moved"));
   }

   @Test
   void hostOrgRenameAndRemoveHandleTheLegacyGlobalKey() {
      SreeEnv.setProperty(key("f/ds"), "SELECT LEGACY");
      asOrg(HOST);
      addSource("f/ds");

      registry.renameDatasource("f/ds", "moved");

      assertNull(SreeEnv.getProperty(key("f/ds"), false, false),
                 "the legacy global key was left behind");
      assertEquals("SELECT LEGACY", JDBCUtil.getConnectionTestQuery("moved"));

      registry.removeDataSource("moved");
      assertNull(JDBCUtil.getConnectionTestQuery("moved"));
   }

   @Test
   void nonHostOrgDoesNotMoveTheLegacyGlobalKey() {
      SreeEnv.setProperty(key("f/ds"), "SELECT LEGACY");
      asOrg("orgb");
      addSource("f/ds");

      registry.renameDatasource("f/ds", "moved");
      registry.removeDataSource("moved");

      assertEquals("SELECT LEGACY", SreeEnv.getProperty(key("f/ds"), false, false));
   }

   @Test
   void editorRenameThatClearsTheFieldLeavesNoTestQuery() throws Exception {
      asOrg("orga");
      addSource("f/ds");
      JDBCUtil.setConnectionTestQuery("f/ds", "SELECT A");

      // the editor save renames the data source, then saves the test query
      registry.renameDatasource("f/ds", "moved");
      saveTestQuery("f/ds", "moved", "");

      assertNull(JDBCUtil.getConnectionTestQuery("moved"),
                 "the cleared test query is shown again after the rename");
      assertNull(JDBCUtil.getConnectionTestQuery("f/ds"));
   }

   private void addSource(String path) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(path);
      sources.put(path, dataSource);
   }

   private AssetEntry[] entries(String prefix) {
      return sources.keySet().stream()
         .filter(path -> path.startsWith(prefix))
         .sorted()
         .map(path -> new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                     path, null))
         .toArray(AssetEntry[]::new);
   }

   private static String key(String name) {
      return "inetsoft.uql.jdbc.pool." + name + ".connectionTestQuery";
   }

   private static String orgKey(String org, String name) {
      return "inetsoft.org." + org.toLowerCase() + "." + key(name);
   }

   private void asOrg(String org) {
      ThreadContext.setContextPrincipal(
         new SRPrincipal(new IdentityID("admin", org), new IdentityID[0], new String[0], org,
                         Tool.getSecureRandom().nextLong()));
   }

   private static void saveTestQuery(String oldSource, String newSource, String query)
      throws Exception
   {
      DatabaseDatasourcesService service = mock(DatabaseDatasourcesService.class,
                                                withSettings().defaultAnswer(CALLS_REAL_METHODS));
      Method method = DatabaseDatasourcesService.class.getDeclaredMethod(
         "saveTestQuery", String.class, String.class, String.class);
      method.setAccessible(true);
      method.invoke(service, oldSource, newSource, query);
   }

   // JDBCDataSource's constructor needs the CredentialService bean, whose constructor is
   // package private
   @Configuration
   static class CredentialServiceConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }
}
