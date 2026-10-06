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
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.Drivers;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.log.LogManager;
import inetsoft.web.GlobalExceptionHandler;
import inetsoft.web.factory.RemainingPathResolver;
import inetsoft.web.portal.controller.ControllerErrorHandler;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.security.Principal;
import java.sql.*;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bug #77848. The auto-join dialog lists join candidates across all tables of a physical model.
 * An inline view whose SQL the database rejects must not fail the whole request with a 500 and an
 * ERROR log: the view is skipped and the other tables still offer their joins. A connection
 * failure is not the user's SQL and must still answer 500 and log an ERROR. Inline view SQL runs
 * on the model's own (additional) connection.
 *
 * Runs the real controller, services and JDBCHandler on embedded Derby, with the real exception
 * handler advices and the production {@code @RemainingPath} resolver.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  PhysicalModelAutoJoinErrorTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
// about 20 s alone, almost all of it the Spring context start-up
@Tag("slow")
class PhysicalModelAutoJoinErrorTest {
   private static final String MAIN_DB = "memory:bug77848autojoin";
   private static final String ADDITIONAL_DB = "memory:bug77848autojoinadd";
   private static final String OUTAGE_DB = "memory:bug77848autojoinoutage";
   private static final String SOURCE = "bug77848";
   private static final String OUTAGE = "bug77848outage";
   private static final String ADDITIONAL = "ADDCONN";
   private static final String URL = "/api/data/physicalmodel/autoJoin/";

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
         // what a Hikari pool throws when the database is down
         DataSource down = mock(DataSource.class);
         when(down.getConnection()).thenThrow(new SQLTransientConnectionException(
            "HikariPool-1 - Connection is not available, request timed out after 30000ms.",
            "08001"));
         when(down.getConnection(any(), any())).thenThrow(new SQLTransientConnectionException(
            "HikariPool-1 - Connection is not available, request timed out after 30000ms.",
            "08001"));
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenAnswer(inv -> {
            JDBCDataSource ds = inv.getArgument(0);
            String url = ds.getURL();
            return url.endsWith(OUTAGE_DB) ? down :
               url.endsWith(ADDITIONAL_DB) ? derby(ADDITIONAL_DB) : derby(MAIN_DB);
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
   }

   @BeforeAll
   static void createTables() throws Exception {
      createTable(MAIN_DB, "EMP");
      // only exists on the additional connection
      createTable(ADDITIONAL_DB, "ONLY_ADD");
   }

   @BeforeEach
   void setUp() throws Exception {
      XRepository repository = mock(XRepository.class);
      // what XEngine.getHandler does for a JDBC source
      when(repository.getHandler(any(), any(), any())).thenAnswer(inv -> {
         JDBCHandler handler = new JDBCHandler();
         handler.connect(inv.getArgument(1), inv.getArgument(2));
         return handler;
      });
      when(repository.getDataModel(any())).thenReturn(mock(XDataModel.class));

      // no primary key information, so every shared column name is a candidate
      DefaultMetaDataProvider metaData = mock(DefaultMetaDataProvider.class);
      when(metaData.getPrimaryKeys(any())).thenReturn(new XNode());

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.getDataSource(SOURCE, null))
         .thenReturn(dataSource(SOURCE, MAIN_DB));
      when(dataSourceService.getDataSource(SOURCE, ADDITIONAL))
         .thenReturn(dataSource(SOURCE, ADDITIONAL_DB));
      when(dataSourceService.getDataSource(OUTAGE, null))
         .thenReturn(dataSource(OUTAGE, OUTAGE_DB));
      when(dataSourceService.getDefaultMetaDataProvider(any(), any())).thenReturn(metaData);

      XSessionManager sessionManager = mock(XSessionManager.class);
      when(sessionManager.getSession()).thenReturn("bug77848");

      RuntimePartitionService runtimePartitionService = mock(RuntimePartitionService.class);
      PhysicalModelService service = new PhysicalModelService(
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

   @Test
   void validViewsOfferJoins() throws Exception {
      request(SOURCE, null, view("V1", "select ID from EMP"), view("V2", "select ID from EMP"))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.nameColumns[*].column", only("ID")))
         .andExpect(jsonPath("$.nameColumns[0].tables", tables("V1", "V2")));
      assertNothingLogged();
   }

   @Test
   void missingTableViewIsSkipped() throws Exception {
      expectBrokenViewSkipped("select * from NO_SUCH_TABLE");
   }

   @Test
   void badSyntaxViewIsSkipped() throws Exception {
      expectBrokenViewSkipped("selec ID frm EMP");
   }

   @Test
   void missingColumnViewIsSkipped() throws Exception {
      expectBrokenViewSkipped("select NO_SUCH_COLUMN from EMP");
   }

   @Test
   void viewRunsOnTheModelsAdditionalConnection() throws Exception {
      request(SOURCE, ADDITIONAL, view("V1", "select ID from ONLY_ADD"),
              view("V2", "select ID from ONLY_ADD"))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.nameColumns[*].column", only("ID")))
         .andExpect(jsonPath("$.nameColumns[0].tables", tables("V1", "V2")));
      assertNothingLogged();
   }

   @Test
   void connectionFailureIsStillServerError() throws Exception {
      request(OUTAGE, null, view("V1", "select ID from EMP"), view("V2", "select ID from EMP"))
         .andExpect(status().isInternalServerError())
         .andExpect(content().string(containsString("Connection is not available")));

      List<ILoggingEvent> errors = appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.ERROR))
         .toList();
      assertEquals(1, errors.size(), () -> "ERROR events: " + errors);
      assertEquals(ControllerErrorHandler.class.getName(), errors.get(0).getLoggerName());
      assertNotNull(errors.get(0).getThrowableProxy());
   }

   private void expectBrokenViewSkipped(String sql) throws Exception {
      request(SOURCE, null, view("BAD", sql), view("V1", "select ID from EMP"),
              view("V2", "select ID from EMP"))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.nameColumns[*].column", only("ID")))
         .andExpect(jsonPath("$.nameColumns[0].tables", tables("V1", "V2")));
      assertNothingLogged();
   }

   private ResultActions request(String source, String connection, String... tables)
      throws Exception
   {
      String body = "{\"name\":\"model\",\"tables\":[" + String.join(",", tables) + "]" +
         (connection == null ? "" : ",\"connection\":\"" + connection + "\"") + "}";
      appender.list.clear();
      Principal principal = () -> "admin";
      return mockMvc.perform(post(URL + source)
                                .principal(principal)
                                .accept(MediaType.APPLICATION_JSON)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body));
   }

   // an inline view; TableType serializes as its int value (1 = VIEW)
   private static String view(String name, String sql) {
      return "{\"name\":\"" + name + "\",\"qualifiedName\":\"" + name + "\",\"type\":1," +
         "\"sql\":\"" + sql + "\",\"joins\":[],\"autoAliases\":[]}";
   }

   // the tables of a join candidate, in any order
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private static Matcher<Object> tables(String... names) {
      return (Matcher) containsInAnyOrder(names);
   }

   // exactly these values, in this order
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private static Matcher<Object> only(String... values) {
      return (Matcher) contains(values);
   }

   private void assertNothingLogged() {
      List<ILoggingEvent> logged = appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN))
         .toList();
      assertTrue(logged.isEmpty(), () -> "WARN/ERROR events: " + logged);
   }

   private static void createTable(String db, String table) throws Exception {
      try(Connection conn = derby(db).getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table " + table);
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table " + table + " (ID int, NAME varchar(20))");
         stmt.executeUpdate("insert into " + table + " values (1, 'a')");
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

   private static DataSource derby(String db) {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(db);
      ds.setCreateDatabase("create");
      return ds;
   }

   private MockMvc mockMvc;
   private Logger root;
   private ListAppender<ILoggingEvent> appender;
}
