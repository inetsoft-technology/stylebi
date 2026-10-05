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
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.log.LogManager;
import inetsoft.web.GlobalExceptionHandler;
import inetsoft.web.portal.controller.ControllerErrorHandler;
import inetsoft.web.portal.model.database.events.GetSqlColumnsEvent;
import org.apache.derby.jdbc.EmbeddedDataSource;
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
import java.io.FileNotFoundException;
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
 * Bug #77826. Inline view SQL that the database rejects, blank SQL and SQL that returns
 * duplicate column names are bad requests: the column lookup must answer 400 with a message and
 * must not log an ERROR. A connection failure is not a bad request and must still answer 500 and
 * log an ERROR, and a missing data source and a permission failure keep their existing handling.
 *
 * Runs the real controller, service and JDBCHandler on embedded Derby, with the real
 * exception handler advices, which MockMvc applies by their package scope.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  PhysicalModelViewColumnsErrorTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PhysicalModelViewColumnsErrorTest {
   private static final String DB = "memory:bug77826viewcolumns";
   private static final String SOURCE = "bug77826";
   private static final String OUTAGE = "bug77826outage";
   private static final String MISSING = "bug77826missing";
   private static final String DENIED = "bug77826denied";
   private static final String URL = "/api/data/physicalmodel/views/columns";

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

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() throws Exception {
         DataSource derby = derby();
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
            return OUTAGE.equals(ds.getName()) ? down : derby;
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
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table EMP");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table EMP (ID int, NAME varchar(20))");
         stmt.executeUpdate("insert into EMP values (1, 'a')");
      }
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

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.getDataSource(SOURCE, null)).thenReturn(dataSource(SOURCE));
      when(dataSourceService.getDataSource(OUTAGE, null)).thenReturn(dataSource(OUTAGE));
      doThrow(new inetsoft.sree.security.SecurityException("Unauthorized access"))
         .when(dataSourceService).checkDataModelEditPermission(eq(DENIED), any(), any());

      XSessionManager sessionManager = mock(XSessionManager.class);
      when(sessionManager.getSession()).thenReturn("bug77826");

      PhysicalModelService service = new PhysicalModelService(
         mock(RuntimePartitionService.class), repository, null, dataSourceService, sessionManager);
      PhysicalModelController controller = new PhysicalModelController(
         mock(RuntimePartitionService.class), mock(DatabaseTreeService.class), service,
         dataSourceService, repository, mock(PhysicalModelManagerService.class),
         mock(SecurityEngine.class));

      mockMvc = MockMvcBuilders.standaloneSetup(controller)
         .setControllerAdvice(new GlobalExceptionHandler(),
                              new ControllerErrorHandler(mock(LogManager.class)))
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
   void validSqlReturnsColumns() throws Exception {
      request(SOURCE, "select ID, NAME from EMP")
         .andExpect(status().isOk())
         .andExpect(jsonPath("$", contains("ID", "NAME")));
      assertNoErrorLogged();
   }

   @Test
   void missingTableIsBadRequest() throws Exception {
      expectInvalidSql("select * from NO_SUCH_TABLE");
   }

   @Test
   void badSyntaxIsBadRequest() throws Exception {
      expectInvalidSql("selec ID frm EMP");
   }

   @Test
   void missingColumnIsBadRequest() throws Exception {
      expectInvalidSql("select NO_SUCH_COLUMN from EMP");
   }

   @Test
   void blankSqlIsBadRequest() throws Exception {
      request(SOURCE, "   ")
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.detail").value("The SQL query is required."));
      assertNoErrorLogged();
   }

   @Test
   void duplicateColumnsAreBadRequest() throws Exception {
      request(SOURCE, "select ID, ID from EMP")
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.detail").value(
            "The SQL statement returns more than one column with the same name."));
      assertNoErrorLogged();
   }

   @Test
   void connectionFailureIsStillServerError() throws Exception {
      request(OUTAGE, "select ID, NAME from EMP")
         .andExpect(status().isInternalServerError())
         .andExpect(result -> assertInstanceOf(
            SQLTransientConnectionException.class, result.getResolvedException()))
         .andExpect(content().string(containsString("Connection is not available")));

      List<ILoggingEvent> errors = errors();
      assertEquals(1, errors.size(), () -> "ERROR events: " + errors);
      assertEquals(ControllerErrorHandler.class.getName(), errors.get(0).getLoggerName());
      assertNotNull(errors.get(0).getThrowableProxy());
   }

   @Test
   void missingDataSourceIsNotConverted() throws Exception {
      // the FileNotFoundException reaches the advices unchanged; GlobalExceptionHandler's
      // IOException handler or ControllerErrorHandler's 404 handler takes it, by advice order
      request(MISSING, "select ID from EMP")
         .andExpect(status().is(not(400)))
         .andExpect(result -> assertInstanceOf(
            FileNotFoundException.class, result.getResolvedException()));
   }

   @Test
   void noPermissionIsForbidden() throws Exception {
      request(DENIED, "select ID from EMP").andExpect(status().isForbidden());
      assertNoErrorLogged();
   }

   private void expectInvalidSql(String sql) throws Exception {
      request(SOURCE, sql)
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.status").value(400))
         .andExpect(jsonPath("$.detail").value("The SQL statement is invalid."));
      assertNoErrorLogged();
   }

   private ResultActions request(String source, String sql) throws Exception {
      GetSqlColumnsEvent event = new GetSqlColumnsEvent();
      event.setDatabase(source);
      event.setSql(sql);
      appender.list.clear();
      Principal principal = () -> "admin";
      return mockMvc.perform(post(URL)
                                .principal(principal)
                                .accept(MediaType.APPLICATION_JSON)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(event)));
   }

   private List<ILoggingEvent> errors() {
      return appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.ERROR))
         .toList();
   }

   private void assertNoErrorLogged() {
      List<ILoggingEvent> errors = errors();
      assertTrue(errors.isEmpty(), () -> "ERROR events: " + errors);
   }

   private static JDBCDataSource dataSource(String name) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }

   private MockMvc mockMvc;
   private Logger root;
   private ListAppender<ILoggingEvent> appender;
}
