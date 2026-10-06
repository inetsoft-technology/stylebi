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
package inetsoft.web.portal.controller.database;

import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.sree.internal.cluster.ignite.IgniteClusterTestUtils;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XNode;
import inetsoft.uql.XQuery;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.Config;
import inetsoft.web.composer.model.ws.BasicSQLQueryModel;
import inetsoft.util.ThreadContext;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import inetsoft.web.portal.controller.database.RuntimeQueryService.RuntimeXQuery;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77858, the query editor saves a parse-on query with quoted names through
 * QueryManagerService into the RuntimeQueryService cache of a production configured
 * IgniteCluster, and another node gets it back and runs it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  RuntimeQueryServiceQuotedSqlClusterTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RuntimeQueryServiceQuotedSqlClusterTest {
   @BeforeAll
   static void startCluster() throws Exception {
      clusterDir = Files.createTempDirectory("cluster-77858");
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);
      node1 = IgniteClusterTestUtils.getIgniteCluster("q77858-1", ipFinder, clusterDir);
      node2 = IgniteClusterTestUtils.getIgniteCluster("q77858-2", ipFinder, clusterDir);
      service1 = new RuntimeQueryService(node1);
      service1.init();
      service2 = new RuntimeQueryService(node2);
      service2.init();
   }

   @AfterAll
   static void stopCluster() throws Exception {
      ThreadContext.setContextPrincipal(null);

      if(node2 != null) {
         node2.close();
      }

      if(node1 != null) {
         node1.close();
      }

      if(clusterDir != null) {
         try(var paths = Files.walk(clusterDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
         }
      }
   }

   @BeforeEach
   void setUp() {
      principal = new XPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()));
      ThreadContext.setContextPrincipal(principal);
      reset(repository);

      try {
         // a database without catalogs whose tables have no known columns
         when(repository.getMetaData(any(), any(), any(), anyBoolean(), any()))
            .thenAnswer(inv -> {
               XNode root = new XNode("root");
               root.setAttribute("hasCatalog", "false");
               root.setAttribute("hasSchema", "true");
               return root;
            });
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }

      security = mock(SecurityEngine.class);
      manager1 = new QueryManagerService(service1, repository, mock(DataSourceService.class),
                                         security, null);
      manager2 = new QueryManagerService(service2, repository, mock(DataSourceService.class),
                                         security, null);
   }

   @Test
   void quotedColumnsSaveAndLoadOnAnotherNode() throws Exception {
      String text = "select \"CATEGORY_ID\", \"CATEGORY_NAME\" from \"CATEGORIES\"";
      String id = openEditor();

      // save/freeSQLModel: parse and save on node 1, read back by the controller
      assertNull(manager1.parseSqlString(id, text, false, true, principal));
      RuntimeXQuery saved = service2.getRuntimeQuery(id);
      UniformSQL sql = (UniformSQL) saved.getQuery().getSQLDefinition();
      assertEquals(1, sql.getTableCount());
      assertEquals(2, sql.getSelection().getColumnCount());
      assertTrue(((JDBCSelection) sql.getSelection()).isQuoted(0));

      // load/data on node 2 runs the query with its table
      assertRunsWithTable(id, text, "\"CATEGORIES\"");
   }

   @Test
   void quotedAliasSaveAndLoadOnAnotherNode() throws Exception {
      // the put of the alias quoting failed, the save was swallowed and load/data ran the
      // empty query of the opened editor: "No tables selected!"
      String text = "select CATEGORY_ID as \"Id\", c.\"CATEGORY_NAME\" from CATEGORIES c";
      String id = openEditor();

      assertNull(manager1.parseSqlString(id, text, false, true, principal));
      assertRunsWithTable(id, text, "CATEGORIES");

      UniformSQL sql = (UniformSQL) service2.getRuntimeQuery(id).getQuery().getSQLDefinition();
      assertEquals(text, sql.getSQLString());
      assertEquals(1, sql.getTableCount());
      assertEquals(Boolean.TRUE, ((JDBCSelection) sql.getSelection()).isAliasQuoted(0));
   }

   @Test
   void derivedTableSaveAndLoadOnAnotherNode() throws Exception {
      String text = "select d.\"CITY\" from (select c.\"CUSTOMER_ID\", c.\"CITY\" " +
         "from \"CUSTOMERS\" c) d";
      String id = openEditor();

      assertNull(manager1.parseSqlString(id, text, false, true, principal));
      UniformSQL sql = (UniformSQL) service2.getRuntimeQuery(id).getQuery().getSQLDefinition();
      assertEquals(1, sql.getTableCount());
      assertInstanceOf(UniformSQL.class, sql.getSelectTable(0).getName());

      assertRunsWithTable(id, text, "\"CUSTOMERS\"");
   }

   @Test
   void sqlDialogUpdateOnAnotherNode() throws Exception {
      // the worksheet sql dialog preview (sql-query-dialog/query/update)
      String text = "select \"CATEGORY_ID\" as \"Id\", \"CATEGORY_NAME\" from \"CATEGORIES\"";
      String id = openEditor();
      BasicSQLQueryModel model = new BasicSQLQueryModel();
      model.setTables(new HashMap<>());
      model.setSqlEdited(true);
      model.setSqlString(text);
      JDBCDataSource ds = dataSource();
      when(repository.getDataSource("ds")).thenReturn(ds);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      manager1.updateQuery(id, model, "ds", principal);
      UniformSQL sql = (UniformSQL) service2.getRuntimeQuery(id).getQuery().getSQLDefinition();
      assertEquals(1, sql.getTableCount());
      assertTrue(((JDBCSelection) sql.getSelection()).isQuoted(0));
      assertEquals(Boolean.TRUE, ((JDBCSelection) sql.getSelection()).isAliasQuoted(0));
   }

   /**
    * load/data on node 2, the executed query must hold the parsed table.
    */
   private void assertRunsWithTable(String id, String text, String table) throws Exception {
      List<String> ran = new ArrayList<>();
      when(repository.execute(anyString(), any(XQuery.class), any(VariableTable.class),
                              any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            UniformSQL sql = (UniformSQL) ((JDBCQuery) inv.getArgument(1)).getSQLDefinition();
            assertTrue(sql.getTableCount() > 0, "No tables selected: " + sql.getSQLString());
            ran.add(sql.getSQLString());
            return null;
         });

      manager2.loadQueryData(id, text, principal);
      assertEquals(1, ran.size());
      assertTrue(ran.get(0).contains(table), ran.get(0));
   }

   /**
    * Open the free sql editor: an empty parse-on query saved on node 1.
    */
   private String openEditor() {
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(dataSource());
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(true);
      query.setSQLDefinition(sql);
      String id = UUID.randomUUID().toString();
      RuntimeXQuery runtimeQuery = new RuntimeXQuery(query, id, "ds");
      runtimeQuery.setOwner(RuntimeQueryService.getOwnerKey(principal));
      runtimeQuery.setVariables(new VariableTable());
      service1.saveRuntimeQuery(runtimeQuery);
      return id;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds");
      ds.setDriver("org.postgresql.Driver");
      ds.setURL("jdbc:postgresql://localhost:5432/db");
      return ds;
   }

   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenReturn("POSTGRESQL");
         return config;
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }

      @Bean
      XSessionManager sessionManager() {
         return mock(XSessionManager.class);
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean()))
            .thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }

   private static Path clusterDir;
   private static IgniteCluster node1;
   private static IgniteCluster node2;
   private static RuntimeQueryService service1;
   private static RuntimeQueryService service2;
   private Principal principal;
   @Autowired
   private XRepository repository;
   private SecurityEngine security;
   private QueryManagerService manager1;
   private QueryManagerService manager2;
}
