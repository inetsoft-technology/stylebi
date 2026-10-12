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
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import inetsoft.web.*;
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
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.security.Principal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bug #77826. Runs the inline view column lookup through the pieces of the production MVC chain
 * that {@link PhysicalModelViewColumnsErrorTest} leaves out: the advices get the production
 * {@link CatalogMessageSource} (which the container injects because ResponseEntityExceptionHandler
 * is MessageSourceAware, and which rewrites ProblemDetail fields), the response is written by the
 * production message converters from {@link WebConfig}, and the request carries the Accept header
 * Angular's HttpClient sends. The outages use real HikariCP pools, the pool implementation
 * DefaultConnectionPoolFactory creates: one whose database cannot be reached and one that has been
 * closed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  PhysicalModelViewColumnsProductionChainTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PhysicalModelViewColumnsProductionChainTest {
   private static final String DB = "memory:bug77826prodchain";
   private static final String SOURCE = "bug77826prod";
   private static final String UNREACHABLE = "bug77826unreachable";
   private static final String CLOSED = "bug77826closed";
   private static final String URL = "/api/data/physicalmodel/views/columns";
   private static final String ANGULAR_ACCEPT = "application/json, text/plain, */*";

   @Configuration
   static class JdbcConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         var ctor = CredentialService.class.getDeclaredConstructor();
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
      public ConnectionPoolFactory connectionPoolFactory() {
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenAnswer(inv -> {
            JDBCDataSource ds = inv.getArgument(0);

            if(UNREACHABLE.equals(ds.getName())) {
               return unreachablePool;
            }
            else if(CLOSED.equals(ds.getName())) {
               return closedPool;
            }

            return derby();
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
   static void createPoolsAndTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table EMP");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table EMP (ID int, NAME varchar(20))");
      }

      // a database that does not exist and is not created: every connection attempt fails
      HikariConfig unreachable = new HikariConfig();
      unreachable.setPoolName("bug77826-unreachable");
      unreachable.setJdbcUrl("jdbc:derby:memory:bug77826doesnotexist");
      unreachable.setInitializationFailTimeout(-1);
      unreachable.setConnectionTimeout(250);
      unreachablePool = new HikariDataSource(unreachable);

      HikariConfig closed = new HikariConfig();
      closed.setPoolName("bug77826-closed");
      closed.setJdbcUrl("jdbc:derby:" + DB);
      closedPool = new HikariDataSource(closed);
      closedPool.close();
   }

   @AfterAll
   static void closePools() {
      if(unreachablePool != null) {
         unreachablePool.close();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getHandler(any(), any(), any())).thenAnswer(inv -> {
         JDBCHandler handler = new JDBCHandler();
         handler.connect(inv.getArgument(1), inv.getArgument(2));
         return handler;
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);

      for(String name : List.of(SOURCE, UNREACHABLE, CLOSED)) {
         when(dataSourceService.getDataSource(name, null)).thenReturn(dataSource(name));
      }

      XSessionManager sessionManager = mock(XSessionManager.class);
      when(sessionManager.getSession()).thenReturn("bug77826prod");

      PhysicalModelService service = new PhysicalModelService(
         mock(RuntimePartitionService.class), repository, null, dataSourceService, sessionManager);
      PhysicalModelController controller = new PhysicalModelController(
         mock(RuntimePartitionService.class), mock(DatabaseTreeService.class), service,
         dataSourceService, repository, mock(PhysicalModelManagerService.class),
         mock(SecurityEngine.class));

      // production wiring: WebConfig's message source and converters, advices in bean order
      WebConfig webConfig = new WebConfig();
      GlobalExceptionHandler global = new GlobalExceptionHandler();
      ControllerErrorHandler portal = new ControllerErrorHandler(mock(LogManager.class));
      global.setMessageSource(webConfig.messageSource());
      portal.setMessageSource(webConfig.messageSource());
      List<HttpMessageConverter<?>> converters = new ArrayList<>();
      webConfig.configureMessageConverters(converters);

      mockMvc = MockMvcBuilders.standaloneSetup(controller)
         .setControllerAdvice(global, portal)
         .setMessageConverters(converters.toArray(new HttpMessageConverter<?>[0]))
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
   void missingTableGivesProblemDetailThroughProductionChain() throws Exception {
      expectBadRequest("select * from NO_SUCH_TABLE", "The SQL statement is invalid.");
   }

   @Test
   void badSyntaxGivesProblemDetailThroughProductionChain() throws Exception {
      expectBadRequest("selec ID frm EMP", "The SQL statement is invalid.");
   }

   @Test
   void blankSqlGivesProblemDetailThroughProductionChain() throws Exception {
      expectBadRequest("  \n ", "The SQL query is required.");
   }

   @Test
   void duplicateColumnsGiveProblemDetailThroughProductionChain() throws Exception {
      expectBadRequest("select ID, ID from EMP",
                       "The SQL statement returns more than one column with the same name.");
   }

   @Test
   void unreachableDatabaseIsStillServerError() throws Exception {
      expectServerError(UNREACHABLE);
   }

   @Test
   void closedPoolIsStillServerError() throws Exception {
      expectServerError(CLOSED);
   }

   private void expectBadRequest(String sql, String detail) throws Exception {
      request(SOURCE, sql)
         .andExpect(status().isBadRequest())
         .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
         .andExpect(jsonPath("$.status").value(400))
         .andExpect(jsonPath("$.title").value("Bad Request"))
         .andExpect(jsonPath("$.detail").value(detail));

      List<ILoggingEvent> logged = appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN))
         .toList();
      assertTrue(logged.isEmpty(), () -> "WARN/ERROR events: " + logged);
   }

   private void expectServerError(String source) throws Exception {
      String body = request(source, "select ID from EMP")
         .andExpect(status().isInternalServerError())
         .andReturn().getResponse().getContentAsString();
      assertFalse(body.contains("The SQL statement is invalid."), body);

      List<ILoggingEvent> errors = appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.ERROR))
         .filter(e -> ControllerErrorHandler.class.getName().equals(e.getLoggerName()))
         .toList();
      assertEquals(1, errors.size(), () -> "ControllerErrorHandler ERROR events: " + errors);
      assertNotNull(errors.get(0).getThrowableProxy());
   }

   private ResultActions request(String source, String sql) throws Exception {
      GetSqlColumnsEvent event = new GetSqlColumnsEvent();
      event.setDatabase(source);
      event.setSql(sql);
      appender.list.clear();
      Principal principal = () -> "admin";
      return mockMvc.perform(post(URL)
                                .principal(principal)
                                .header("Accept", ANGULAR_ACCEPT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(event)));
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

   private static HikariDataSource unreachablePool;
   private static HikariDataSource closedPool;
   private MockMvc mockMvc;
   private Logger root;
   private ListAppender<ILoggingEvent> appender;
}
