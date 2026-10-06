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
import inetsoft.uql.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.log.LogManager;
import inetsoft.web.*;
import inetsoft.web.portal.controller.ControllerErrorHandler;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Bug #77848. When the free-form SQL editor can't parse a statement, the client runs it
 * ({@code executeQuery=true}) to get its columns. SQL the database rejects there is the user's
 * error: the endpoint keeps its 200 answer (the client has no error callback) and must not log an
 * ERROR. An outage is not the user's SQL and must still log an ERROR. Runs the real
 * QueryController, QueryManagerService and JDBCHandler on embedded Derby, through the production
 * advices (in bean order), message source and message converters.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  QueryFreeFormSqlErrorTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
// about 20 s alone, almost all of it the Spring context start-up
@Tag("slow")
class QueryFreeFormSqlErrorTest {
   private static final String DB = "memory:bug77848freesql";
   private static final String SOURCE = "bug77848";
   private static final String UNREACHABLE = "bug77848unreachable";
   private static final String RID = "rq-77848";
   private static final String URL = "/api/data/datasource/query/save/freeSQLModel";

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
      public ConnectionPoolFactory connectionPoolFactory() throws SQLException {
         // what a Hikari pool throws when the database is down
         DataSource down = mock(DataSource.class);
         when(down.getConnection()).thenThrow(new SQLTransientConnectionException(
            "bug77848 - Connection is not available, request timed out", "08001"));
         when(down.getConnection(any(), any())).thenThrow(new SQLTransientConnectionException(
            "bug77848 - Connection is not available, request timed out", "08001"));
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenAnswer(inv -> {
            JDBCDataSource ds = inv.getArgument(0);
            return UNREACHABLE.equals(ds.getName()) ? down : derby();
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

      // SQLHelper.getSQLHelper looks the repository up to read the Derby product version
      @Bean
      public XRepository repository() {
         return mock(XRepository.class);
      }

      // SQLTypes reads the root meta data through the session manager's session
      @Bean
      public XSessionManager sessionManager() {
         XSessionManager sessionManager = mock(XSessionManager.class);
         when(sessionManager.getSession()).thenReturn("bug77848");
         return sessionManager;
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
      // XEngine.execute connects a handler for the data source and passes its exceptions through
      reset(repository);
      when(repository.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            XQuery query = inv.getArgument(1);
            VariableTable vars = inv.getArgument(2);
            JDBCHandler handler = new JDBCHandler();
            handler.connect(query.getDataSource(), vars);
            return handler.execute(query, vars, inv.getArgument(3), null);
         });

      // XEngine.getMetaData reads the database meta data through a connected handler
      when(repository.getMetaData(any(), any(), any()))
         .thenAnswer(inv -> metaData(inv.getArgument(1), inv.getArgument(2)));
      when(repository.getMetaData(any(), any(), any(), anyBoolean()))
         .thenAnswer(inv -> metaData(inv.getArgument(1), inv.getArgument(2)));
      failFixUp = false;
      fixUpFailed = false;
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            // a class 42 error (insufficient privilege) while fixing up the parsed SQL's tables
            if(failFixUp && StackWalker.getInstance().walk(frames -> frames.anyMatch(
               f -> "fixUniformSQLInfo".equals(f.getMethodName()))))
            {
               fixUpFailed = true;
               throw new SQLSyntaxErrorException("bug77848 - permission denied", "42501");
            }

            return metaData(inv.getArgument(1), inv.getArgument(2));
         });

      runtimeQueryService = mock(RuntimeQueryService.class);
      QueryManagerService service = new QueryManagerService(
         runtimeQueryService, repository, mock(DataSourceService.class),
         mock(SecurityEngine.class), mock(ColumnCache.class));
      QueryController controller = new QueryController(
         runtimeQueryService, service, mock(SecurityEngine.class));

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
   void validSqlIsRunWithoutError() throws Exception {
      expectOkWithoutError(SOURCE, "select ID, NAME from EMP");
   }

   @Test
   void badSyntaxIsNotLoggedAsError() throws Exception {
      expectOkWithoutError(SOURCE, "selec ID frm EMP");
   }

   @Test
   void missingTableIsNotLoggedAsError() throws Exception {
      expectOkWithoutError(SOURCE, "select * from NO_SUCH_TABLE");
   }

   @Test
   void missingColumnIsNotLoggedAsError() throws Exception {
      expectOkWithoutError(SOURCE, "select NO_SUCH_COLUMN from EMP");
   }

   @Test
   void dataErrorIsNotLoggedAsError() throws Exception {
      expectOkWithoutError(SOURCE, "select cast('abc' as int) from EMP");
   }

   @Test
   void metaDataFixUpFailureIsStillLoggedAsError() throws Exception {
      // only the database rejecting the statement itself is the user's error; the same kind of
      // SQLException from the meta data fix-up after the statement ran is still a server error
      failFixUp = true;
      MockHttpServletResponse response = save(SOURCE, "select * from EMP");
      assertEquals(200, response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
      assertTrue(fixUpFailed, "the meta data fix-up was not reached");

      List<ILoggingEvent> errors = errors();
      assertEquals(1, errors.size(), () -> "QueryManagerService ERROR events: " + errors);
      assertNotNull(errors.get(0).getThrowableProxy());
      assertTrue(errors.get(0).getFormattedMessage().contains("permission denied"),
                 errors.get(0).getFormattedMessage());
   }

   @Test
   void unreachableDatabaseIsStillLoggedAsError() throws Exception {
      MockHttpServletResponse response = save(UNREACHABLE, "select ID from EMP");
      assertEquals(200, response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));

      // one from the meta data read, one from running the query
      List<ILoggingEvent> errors = errors();
      assertEquals(2, errors.size(), () -> "QueryManagerService ERROR events: " + errors);
      errors.forEach(e -> {
         assertNotNull(e.getThrowableProxy());
         assertTrue(e.getFormattedMessage().contains("Connection is not available"),
                    e.getFormattedMessage());
      });
   }

   private void expectOkWithoutError(String source, String sql) throws Exception {
      MockHttpServletResponse response = save(source, sql);
      String body = response.getContentAsString(StandardCharsets.UTF_8);
      assertEquals(200, response.getStatus(), body);
      // the client ignores errorMsg on the execute leg; it stays null as before
      assertTrue(body.contains("\"errorMsg\":null") || !body.contains("errorMsg"), body);
      List<ILoggingEvent> errors = errors();
      assertTrue(errors.isEmpty(), () -> "QueryManagerService ERROR events: " + errors);
   }

   private MockHttpServletResponse save(String source, String sqlString) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(true);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(dataSource(source));
      query.setSQLDefinition(sql);
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         new RuntimeQueryService.RuntimeXQuery(query, RID, source);
      runtimeQuery.setVariables(new VariableTable());
      when(runtimeQueryService.getRuntimeQuery(RID)).thenReturn(runtimeQuery);

      String body = "{\"runtimeId\":\"" + RID + "\",\"executeQuery\":true," +
         "\"freeFormSqlPaneModel\":{\"sqlString\":\"" + sqlString.replace("\"", "\\\"") +
         "\",\"parseSql\":true}}";
      appender.list.clear();
      Principal principal = () -> "admin";
      return mockMvc.perform(post(URL)
                                .principal(principal)
                                .accept(MediaType.APPLICATION_JSON)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
         .andReturn().getResponse();
   }

   private List<ILoggingEvent> errors() {
      return appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.ERROR))
         .filter(e -> QueryManagerService.class.getName().equals(e.getLoggerName()))
         .toList();
   }

   private static XNode metaData(XDataSource dx, XNode mtype) throws Exception {
      JDBCHandler handler = new JDBCHandler();
      handler.connect(dx, new VariableTable());
      return handler.getMetaData(mtype);
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

   @Autowired
   private XRepository repository;
   private boolean failFixUp;
   private boolean fixUpFailed;
   private RuntimeQueryService runtimeQueryService;
   private MockMvc mockMvc;
   private Logger root;
   private ListAppender<ILoggingEvent> appender;
}
