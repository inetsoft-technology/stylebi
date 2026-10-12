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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.erm.XRelationship;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.log.LogManager;
import inetsoft.web.GlobalExceptionHandler;
import inetsoft.web.factory.RemainingPathResolver;
import inetsoft.web.portal.controller.ControllerErrorHandler;
import inetsoft.web.portal.model.database.JoinCardinality;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.security.Principal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bug #77896. When a join to an inline view is added to a physical model, its cardinality is
 * detected from primary keys. An inline view's key columns are not known, so it counts as
 * unkeyed: its SQL is not run (it used to run on the default connection even for a model on an
 * additional connection, and the key lookup then read the keys of a table literally named
 * "table"), and no pooled connection is left checked out.
 *
 * Runs the real controller, services, JDBCHandler and DefaultMetaDataProvider on embedded Derby,
 * through both join paths: POST /cardinality (edit table, add join) and POST /add/autoJoin.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  PhysicalModelInlineViewCardinalityTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
// 8-10 s alone, almost all of it the Spring context start-up
@Tag("slow")
class PhysicalModelInlineViewCardinalityTest {
   // default connection of SOURCE: EMP(ID pk, DEPT_ID), DEPT(DEPT_ID pk, NAME)
   private static final String MAIN_DB = "memory:bug77896main";
   // additional connection ADDCONN of SOURCE: ONLYB(ID pk, V)
   private static final String ADD_DB = "memory:bug77896add";
   // additional connection KEYCONN of SOURCE, and default connection of TABLE_SOURCE:
   // "table"(ID pk), T1(ID pk), T2(ID, X) without a key
   private static final String KEY_DB = "memory:bug77896key";
   private static final String SOURCE = "bug77896";
   private static final String TABLE_SOURCE = "bug77896table";
   private static final String ADDCONN = "ADDCONN";
   private static final String KEYCONN = "KEYCONN";
   private static final String RUNTIME_ID = "bug77896runtime";
   private static final Map<String, CountingDataSource> POOLS = new HashMap<>();

   @Configuration
   static class JdbcConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster,
                            eventPublisher);
      }

      @Bean(destroyMethod = "")
      public ConnectionPoolFactory connectionPoolFactory() throws Exception {
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenAnswer(inv -> {
            String url = ((JDBCDataSource) inv.getArgument(0)).getURL();
            return pool(url.substring("jdbc:derby:".length()));
         });
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      // JDBCHandler reads these through XRepository.getRepository() and
      // XSessionManager.getSessionManager()
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }

      @Bean
      public XSessionManager xSessionManager() {
         XSessionManager sessionManager = mock(XSessionManager.class);
         when(sessionManager.getSession()).thenReturn("bug77896");
         return sessionManager;
      }
   }

   @BeforeAll
   static void createTables() throws Exception {
      execute(MAIN_DB, "create table EMP (ID int not null primary key, DEPT_ID int)",
              "create table DEPT (DEPT_ID int not null primary key, NAME varchar(20))");
      execute(ADD_DB, "create table ONLYB (ID int not null primary key, V varchar(20))");
      execute(KEY_DB, "create table \"table\" (ID int not null primary key)",
              "create table T1 (ID int not null primary key)",
              "create table T2 (ID int, X int)");
   }

   @BeforeEach
   void setUp() throws Exception {
      reset(repository);
      // what XEngine does for a JDBC source
      when(repository.getHandler(any(), any(), any())).thenAnswer(
         inv -> handler(inv.getArgument(1)));
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(
         inv -> handler(inv.getArgument(1)).getMetaData(inv.getArgument(2)));
      when(repository.getDataModel(any())).thenReturn(mock(XDataModel.class));

      // what DatabaseModelUtil.getDatasource does: the named additional connection, else the
      // default one
      dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.getDataSource(any(), any())).thenAnswer(inv -> {
         String source = inv.getArgument(0);
         String additional = inv.getArgument(1);

         if(TABLE_SOURCE.equals(source)) {
            return dataSource(TABLE_SOURCE, KEY_DB);
         }

         return ADDCONN.equals(additional) ? dataSource(SOURCE, ADD_DB) :
            KEYCONN.equals(additional) ? dataSource(SOURCE, KEY_DB) :
            dataSource(SOURCE, MAIN_DB);
      });
      when(dataSourceService.getDefaultMetaDataProvider(any(), any())).thenAnswer(inv -> {
         DefaultMetaDataProvider metaData = new DefaultMetaDataProvider(repository);
         metaData.setDataSource(inv.getArgument(0));
         metaData.setDataModel(inv.getArgument(1));
         metaData.setPortalData(true);
         return metaData;
      });

      runtimePartitionService = mock(RuntimePartitionService.class);
      service = new PhysicalModelService(
         runtimePartitionService, repository, null, dataSourceService, sessionManager);
      PhysicalModelManagerService manager = new PhysicalModelManagerService(
         dataSourceService, service, runtimePartitionService, null, repository, null, null, null);
      PhysicalModelController controller = new PhysicalModelController(
         runtimePartitionService, mock(DatabaseTreeService.class), service, dataSourceService,
         repository, manager, mock(SecurityEngine.class));

      mockMvc = MockMvcBuilders.standaloneSetup(controller)
         .setControllerAdvice(new GlobalExceptionHandler(),
                              new ControllerErrorHandler(mock(LogManager.class)))
         .setCustomArgumentResolvers(new RemainingPathResolver())
         .build();

      appender = new ListAppender<>();
      appender.start();
      root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
      root.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      root.detachAppender(appender);
   }

   // C1: the reported case. The view exists only on the model's additional connection.
   @Test
   void viewOnAdditionalConnectionIsUnkeyed() throws Exception {
      String model = model(ADDCONN, physical("ONLYB"), view("V_B", "select ID, V from ONLYB"));
      expectCardinality(SOURCE, ADDCONN, model, "ONLYB", "ID", "V_B", "ID",
                        JoinCardinality.ONE_TO_MANY);
   }

   // K1: an additional connection whose database has a real table named "table" with a key on ID
   @Test
   void tableNamedTableDoesNotKeyViewOnAdditionalConnection() throws Exception {
      String model = model(KEYCONN, physical("T1"), view("V_T2", "select ID, X from T2"));
      expectCardinality(SOURCE, KEYCONN, model, "T1", "ID", "V_T2", "ID",
                        JoinCardinality.ONE_TO_MANY);
   }

   // T1def: the same on the default connection
   @Test
   void tableNamedTableDoesNotKeyViewOnDefaultConnection() throws Exception {
      String model = model(null, physical("T1"), view("V_T2", "select ID, X from T2"));
      expectCardinality(TABLE_SOURCE, null, model, "T1", "ID", "V_T2", "ID",
                        JoinCardinality.ONE_TO_MANY);
   }

   // D1: control, already right before the fix
   @Test
   void keyToViewOnDefaultConnection() throws Exception {
      String model = model(null, physical("EMP"), view("V_E", "select ID, DEPT_ID from EMP"));
      expectCardinality(SOURCE, null, model, "EMP", "ID", "V_E", "ID",
                        JoinCardinality.ONE_TO_MANY);
   }

   // D1 reversed: control, view to a primary key
   @Test
   void viewToKeyOnDefaultConnection() throws Exception {
      String model = model(null, physical("EMP"), view("V_E", "select ID, DEPT_ID from EMP"));
      expectCardinality(SOURCE, null, model, "V_E", "ID", "EMP", "ID",
                        JoinCardinality.MANY_TO_ONE);
   }

   // D3: control, physical tables only
   @Test
   void physicalTablesOnly() throws Exception {
      String model = model(null, physical("EMP"), physical("DEPT"));
      expectCardinality(SOURCE, null, model, "EMP", "DEPT_ID", "DEPT", "DEPT_ID",
                        JoinCardinality.MANY_TO_ONE);
   }

   // T3def: the view as the dependent end, joined to a primary key; it used to read the keys of
   // "table" and give ONE_TO_ONE
   @Test
   void tableNamedTableDoesNotKeyViewToKeyOnDefaultConnection() throws Exception {
      String model = model(null, physical("T1"), view("V_T2", "select ID, X from T2"));
      expectCardinality(TABLE_SOURCE, null, model, "V_T2", "ID", "T1", "ID",
                        JoinCardinality.MANY_TO_ONE);
   }

   // control: an alias of a physical table is still a physical table, so its keys are read
   @Test
   void aliasOfPhysicalTableKeepsItsKeys() throws Exception {
      String model = model(null, physical("DEPT"), alias("E2", "EMP"));
      expectCardinality(SOURCE, null, model, "E2", "ID", "DEPT", "DEPT_ID",
                        JoinCardinality.ONE_TO_ONE);
   }

   // the edit permission on the source is still checked before any metadata is read
   @Test
   void cardinalityRequiresEditPermission() throws Exception {
      doThrow(new SecurityException("Unauthorized access to resource \"" + SOURCE + "\""))
         .when(dataSourceService).checkDataModelEditPermission(SOURCE, ADDCONN, PRINCIPAL);
      String model = model(ADDCONN, physical("ONLYB"), view("V_B", "select ID, V from ONLYB"));

      mockMvc.perform(post("/api/data/physicalmodel/cardinality")
                         .param("database", SOURCE)
                         .param("additional", ADDCONN)
                         .principal(PRINCIPAL)
                         .accept(MediaType.APPLICATION_JSON)
                         .contentType(MediaType.APPLICATION_JSON)
                         .content("{\"table\":\"ONLYB\",\"model\":" + model + ",\"join\":" +
                                     join("ONLYB", "ID", "V_B", "ID") + "}"))
         .andExpect(status().isForbidden());

      verify(dataSourceService, never()).getDataSource(any(), any());
   }

   // D5bad: a view whose SQL the database rejects counts as unkeyed like any other view; it is
   // validated when its columns are listed, not here
   @Test
   void brokenViewIsUnkeyed() throws Exception {
      String model = model(null, physical("EMP"), view("V_BAD", "select ID from NO_SUCH_TABLE"));
      expectCardinality(SOURCE, null, model, "EMP", "ID", "V_BAD", "ID",
                        JoinCardinality.ONE_TO_MANY);
   }

   private void expectCardinality(String source, String connection, String model,
                                  String table, String column, String foreignTable,
                                  String foreignColumn, JoinCardinality expected)
      throws Exception
   {
      String join = join(table, column, foreignTable, foreignColumn);
      int borrowed = borrowedConnections();
      appender.list.clear();

      // edit table dialog: the client sends the model's connection as "additional"
      mockMvc.perform(post("/api/data/physicalmodel/cardinality")
                         .param("database", source)
                         .param("additional", connection == null ? "" : connection)
                         .principal(PRINCIPAL)
                         .accept(MediaType.APPLICATION_JSON)
                         .contentType(MediaType.APPLICATION_JSON)
                         .content("{\"table\":\"" + table + "\",\"model\":" + model +
                                     ",\"join\":" + join + "}"))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.cardinality").value(expected.ordinal()));

      // auto-join dialog: the relationship is stored in the open (runtime) model
      XPartition partition = service.createPartition(
         new com.fasterxml.jackson.databind.ObjectMapper().readValue(
            model, inetsoft.web.portal.model.database.PhysicalModelDefinition.class));
      when(runtimePartitionService.getRuntimePartition(RUNTIME_ID)).thenReturn(
         new RuntimePartitionService.RuntimeXPartition(partition, RUNTIME_ID, source));
      mockMvc.perform(post("/api/data/physicalmodel/add/autoJoin")
                         .principal(PRINCIPAL)
                         .contentType(MediaType.APPLICATION_JSON)
                         .content("{\"id\":\"" + RUNTIME_ID + "\",\"joinItems\":[{\"actionType\":\"add\",\"table\":" +
                                     "{\"name\":\"" + table + "\",\"qualifiedName\":\"" + table +
                                     "\"},\"join\":" + join + "}]}"))
         .andExpect(status().isOk());

      XRelationship relationship = partition.findRelationship(table, foreignTable);
      assertNotNull(relationship, "auto-join was not added");
      assertEquals(expected, cardinality(relationship), relationship::toString);

      List<ILoggingEvent> warnings = appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN))
         .filter(e -> e.getLoggerName().equals(PhysicalModelService.class.getName()) ||
            e.getLoggerName().equals(PhysicalModelManagerService.class.getName()))
         .toList();
      assertTrue(warnings.isEmpty(), () -> "WARN/ERROR events: " + warnings);
      assertEquals(borrowed, borrowedConnections(), "JDBC connections left checked out");
   }

   private static JoinCardinality cardinality(XRelationship relationship) {
      boolean dependentOne = relationship.getDependentCardinality() == XRelationship.ONE;
      boolean independentOne = relationship.getIndependentCardinality() == XRelationship.ONE;
      return dependentOne ?
         (independentOne ? JoinCardinality.ONE_TO_ONE : JoinCardinality.ONE_TO_MANY) :
         (independentOne ? JoinCardinality.MANY_TO_ONE : JoinCardinality.MANY_TO_MANY);
   }

   private JDBCHandler handler(XDataSource dataSource) throws Exception {
      JDBCHandler handler = new JDBCHandler();
      handler.setRepository(repository);
      handler.setSession("bug77896");
      handler.connect(dataSource, new VariableTable());
      return handler;
   }

   private static String model(String connection, String... tables) {
      return "{\"name\":\"model\",\"tables\":[" + String.join(",", tables) + "]" +
         (connection == null ? "" : ",\"connection\":\"" + connection + "\"") + "}";
   }

   // an inline view; TableType serializes as its int value (1 = VIEW)
   private static String view(String name, String sql) {
      return "{\"name\":\"" + name + "\",\"qualifiedName\":\"" + name + "\",\"type\":1," +
         "\"sql\":\"" + sql + "\",\"joins\":[],\"autoAliases\":[]}";
   }

   // a physical table (0 = PHYSICAL)
   private static String physical(String name) {
      return "{\"name\":\"" + name + "\",\"qualifiedName\":\"" + name + "\",\"type\":0," +
         "\"joins\":[],\"autoAliases\":[]}";
   }

   // an alias of a physical table
   private static String alias(String alias, String table) {
      return "{\"name\":\"" + table + "\",\"qualifiedName\":\"" + table + "\",\"alias\":\"" +
         alias + "\",\"aliasSource\":\"" + table + "\",\"type\":0," +
         "\"joins\":[],\"autoAliases\":[]}";
   }

   // the client always proposes MANY_TO_ONE (2); enums serialize as their ordinal
   private static String join(String table, String column, String foreignTable,
                              String foreignColumn)
   {
      return "{\"type\":0,\"mergingRule\":0,\"cardinality\":2,\"orderPriority\":1," +
         "\"weak\":false,\"table\":\"" + table + "\",\"column\":\"" + column + "\"," +
         "\"foreignTable\":\"" + foreignTable + "\",\"foreignColumn\":\"" + foreignColumn +
         "\"}";
   }

   private static int borrowedConnections() {
      synchronized(POOLS) {
         return POOLS.values().stream().mapToInt(CountingDataSource::open).sum();
      }
   }

   private static void execute(String db, String... sql) throws Exception {
      try(Connection conn = pool(db).getConnection(); Statement stmt = conn.createStatement()) {
         for(String statement : sql) {
            stmt.executeUpdate(statement);
         }
      }
   }

   private static JDBCDataSource dataSource(String name, String db) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + db);
      ds.setRequireLogin(false);
      return ds;
   }

   private static DataSource pool(String db) {
      synchronized(POOLS) {
         return POOLS.computeIfAbsent(db, CountingDataSource::new).proxy;
      }
   }

   /**
    * A connection pool over an embedded Derby database that counts the connections handed out
    * and not yet closed.
    */
   private static final class CountingDataSource {
      CountingDataSource(String db) {
         EmbeddedDataSource derby = new EmbeddedDataSource();
         derby.setDatabaseName(db);
         derby.setCreateDatabase("create");
         proxy = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] { DataSource.class },
            (p, method, args) -> {
               Object result = invoke(derby, method, args);

               if(result instanceof Connection conn) {
                  open.incrementAndGet();
                  return connection(conn);
               }

               return result;
            });
      }

      int open() {
         return open.get();
      }

      private Connection connection(Connection conn) {
         AtomicInteger closed = new AtomicInteger();
         return (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] { Connection.class },
            (p, method, args) -> {
               if("close".equals(method.getName()) && closed.getAndIncrement() == 0) {
                  open.decrementAndGet();
               }

               return invoke(conn, method, args);
            });
      }

      private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
         try {
            return method.invoke(target, args);
         }
         catch(InvocationTargetException e) {
            throw e.getCause();
         }
      }

      private final DataSource proxy;
      private final AtomicInteger open = new AtomicInteger();
   }

   private static final Principal PRINCIPAL = () -> "admin";

   @Autowired
   private XRepository repository;
   @Autowired
   private XSessionManager sessionManager;
   private DataSourceService dataSourceService;
   private RuntimePartitionService runtimePartitionService;
   private PhysicalModelService service;
   private MockMvc mockMvc;
   private Logger root;
   private ListAppender<ILoggingEvent> appender;
}
