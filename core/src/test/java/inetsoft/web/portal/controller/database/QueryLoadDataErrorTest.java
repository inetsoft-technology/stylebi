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
import inetsoft.sree.SreeEnv;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Bug #77855. The worksheet Database Query dialog's Preview of SQL the database rejects must give a
 * 400 with the database message as a plain String body (the client shows it and calls
 * {@code startsWith} on it), without an ERROR log, while an outage still gives 500 + ERROR. Runs the
 * real QueryController, QueryManagerService and JDBCHandler on embedded Derby, through the
 * production advices (in bean order), message source and message converters.
 * <p>
 * Bug #77870. A database error raised while reading the rows (after the statement executed) must
 * not give a 200 with no or only part of the rows, and an expired query session must still give
 * its message.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  QueryLoadDataErrorTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
// 10-20 s alone, most of it the Spring context start-up
@Tag("slow")
class QueryLoadDataErrorTest {
   private static final String DB = "memory:bug77855loaddata";
   private static final String SOURCE = "bug77855";
   private static final String UNREACHABLE = "bug77855unreachable";
   private static final String RID = "rq-77855";
   private static final String URL = "/api/data/datasource/query/load/data";
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
      public ConnectionPoolFactory connectionPoolFactory() throws SQLException {
         // what a Hikari pool throws when the database is down
         DataSource down = mock(DataSource.class);
         when(down.getConnection()).thenThrow(new SQLTransientConnectionException(
            "bug77855 - Connection is not available, request timed out", "08001"));
         when(down.getConnection(any(), any())).thenThrow(new SQLTransientConnectionException(
            "bug77855 - Connection is not available, request timed out", "08001"));
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
         when(sessionManager.getSession()).thenReturn("bug77855");
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

         try {
            stmt.executeUpdate("drop table BIG");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table BIG (ID int)");

         for(int i = 1; i <= 300; i++) {
            stmt.addBatch("insert into BIG values (" + i + ")");
         }

         stmt.executeBatch();
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
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> metaData(inv.getArgument(1), inv.getArgument(2)));

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
   void validSqlLoadsRows() throws Exception {
      MockHttpServletResponse response = preview(SOURCE, "select ID, NAME from EMP");
      String body = response.getContentAsString(StandardCharsets.UTF_8);
      assertEquals(200, response.getStatus(), body);
      assertTrue(body.contains("\"a\""), body);
   }

   @Test
   void missingTableIsBadRequestWithTheDatabaseMessage() throws Exception {
      String body = expectBadRequest("select * from NO_SUCH_TABLE");
      // the client prefixes its syntax-error caption when the body starts with this
      assertTrue(body.startsWith("java.sql.SQLSyntaxErrorException: "), body);
      assertTrue(body.contains("NO_SUCH_TABLE"), body);
   }

   @Test
   void badSyntaxIsBadRequestWithTheDatabaseMessage() throws Exception {
      String body = expectBadRequest("selec ID frm EMP");
      assertTrue(body.startsWith("java.sql.SQLSyntaxErrorException: "), body);
   }

   @Test
   void missingColumnIsBadRequestWithTheDatabaseMessage() throws Exception {
      String body = expectBadRequest("select NO_SUCH_COLUMN from EMP");
      assertTrue(body.startsWith("java.sql.SQLSyntaxErrorException: "), body);
   }

   @Test
   void nonLatinMessageIsSentAsUtf8() throws Exception {
      // a table name typed in Chinese; Derby echoes it back in its message
      String table = "\u5458\u5de5\u8868";
      MockHttpServletResponse response = preview(SOURCE, "select * from \"" + table + "\"");
      String body = response.getContentAsString(StandardCharsets.UTF_8);
      assertEquals(400, response.getStatus(), body);
      assertTrue(body.contains("'" + table + "'"), body);
      assertEquals(StandardCharsets.UTF_8,
                   MediaType.parseMediaType(response.getContentType()).getCharset(),
                   response.getContentType());
   }

   @Test
   void unreachableDatabaseIsStillServerError() throws Exception {
      MockHttpServletResponse response = preview(UNREACHABLE, "select ID from EMP");
      assertEquals(500, response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));

      List<ILoggingEvent> errors = appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.ERROR))
         .filter(e -> ControllerErrorHandler.class.getName().equals(e.getLoggerName()))
         .toList();
      assertEquals(1, errors.size(), () -> "ControllerErrorHandler ERROR events: " + errors);
      assertNotNull(errors.get(0).getThrowableProxy());
   }

   @Test
   void errorReadingTheFirstRowIsBadRequest() throws Exception {
      // Derby raises the division by zero while reading the row, not when executing
      String body = expectFetchTimeBadRequest("select 1/0 from EMP");
      assertTrue(body.contains("divide by zero"), body);
   }

   @Test
   void errorAfterSomeRowsIsBadRequestNotPartialRows() throws Exception {
      // the first 149 rows read fine, before the fix they were returned as the whole result
      String body = expectFetchTimeBadRequest("select ID, 1/(ID-150) from BIG");
      assertTrue(body.contains("divide by zero"), body);
   }

   @Test
   void errorAfterSomeRowsIsBadRequestWithoutStreaming() throws Exception {
      String streaming = SreeEnv.getProperty("replet.streaming");

      try {
         // rows read in the request thread instead of a background loader
         SreeEnv.setProperty("replet.streaming", "false");
         String body = expectFetchTimeBadRequest("select ID, 1/(ID-150) from BIG");
         assertTrue(body.contains("divide by zero"), body);
      }
      finally {
         SreeEnv.setProperty("replet.streaming", streaming);
      }
   }

   @Test
   void allRowsStillLoadWithoutAnError() throws Exception {
      MockHttpServletResponse response = preview(SOURCE, "select ID from BIG");
      String body = response.getContentAsString(StandardCharsets.UTF_8);
      assertEquals(200, response.getStatus(), body);
      assertTrue(body.contains("[\"300\"]"), body);
   }

   @Test
   void connectionLostWhileReadingRowsIsServerError() throws Exception {
      // an outage while reading the rows is not the user's SQL: still 500, but not partial rows
      doReturn(new FailingTableNode(new RuntimeException(
         "java.sql.SQLRecoverableException: bug77870 connection reset",
         new SQLRecoverableException("bug77870 connection reset", "08006"))))
         .when(repository).execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any());

      MockHttpServletResponse response = preview(SOURCE, "select ID from EMP");
      String body = response.getContentAsString(StandardCharsets.UTF_8);
      assertEquals(500, response.getStatus(), body);
      assertTrue(body.contains("bug77870 connection reset"), body);
      assertFalse(body.contains("row1"), body);
   }

   @Test
   void expiredQuerySessionKeepsItsMessage() throws Exception {
      preview(SOURCE, "select ID from EMP");
      when(runtimeQueryService.getRuntimeQuery(RID)).thenReturn(null);
      Principal principal = () -> "admin";
      MockHttpServletResponse response = mockMvc.perform(get(URL)
                                                            .principal(principal)
                                                            .header("Accept", ANGULAR_ACCEPT)
                                                            .param("runtimeId", RID))
         .andReturn().getResponse();
      String body = response.getContentAsString(StandardCharsets.UTF_8);
      // the client reads the message from this {error, message} body (Bug #77870 part A)
      assertEquals(500, response.getStatus(), body);
      assertTrue(body.contains("\"error\":\"messageException\""), body);
      assertTrue(body.contains("\"message\":"), body);
   }

   private String expectFetchTimeBadRequest(String sql) throws Exception {
      MockHttpServletResponse response = preview(SOURCE, sql);
      String body = response.getContentAsString(StandardCharsets.UTF_8);
      assertEquals(400, response.getStatus(), body);
      MediaType contentType = MediaType.parseMediaType(response.getContentType());
      assertTrue(MediaType.TEXT_PLAIN.isCompatibleWith(contentType), contentType.toString());
      assertEquals(StandardCharsets.UTF_8, contentType.getCharset(), contentType.toString());

      // JDBCTableNode still logs the read error itself, but it is not reported as a server error
      List<ILoggingEvent> errors = appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.ERROR))
         .filter(e -> ControllerErrorHandler.class.getName().equals(e.getLoggerName()))
         .toList();
      assertTrue(errors.isEmpty(), () -> "ControllerErrorHandler ERROR events: " + errors);
      return body;
   }

   /**
    * A result that reads two rows and then fails, like a driver whose connection drops.
    */
   private static final class FailingTableNode extends XTableNode {
      FailingTableNode(RuntimeException failure) {
         this.failure = failure;
      }

      @Override
      public boolean next() {
         if(++row > 2) {
            throw failure;
         }

         return true;
      }

      @Override
      public int getColCount() {
         return 1;
      }

      @Override
      public String getName(int col) {
         return "ID";
      }

      @Override
      public Class getType(int col) {
         return String.class;
      }

      @Override
      public Object getObject(int col) {
         return "row" + row;
      }

      @Override
      public XMetaInfo getXMetaInfo(int col) {
         return null;
      }

      @Override
      public boolean rewind() {
         return false;
      }

      @Override
      public boolean isRewindable() {
         return false;
      }

      private final RuntimeException failure;
      private int row;
   }

   private String expectBadRequest(String sql) throws Exception {
      MockHttpServletResponse response = preview(SOURCE, sql);
      String body = response.getContentAsString(StandardCharsets.UTF_8);
      assertEquals(400, response.getStatus(), body);
      // a raw String (not ProblemDetail / JSON) body, so error.error is a string in the client
      MediaType contentType = MediaType.parseMediaType(response.getContentType());
      assertTrue(MediaType.TEXT_PLAIN.isCompatibleWith(contentType), contentType.toString());
      // the message may hold non-Latin-1 text (identifiers, localized server messages)
      assertEquals(StandardCharsets.UTF_8, contentType.getCharset(), contentType.toString());

      List<ILoggingEvent> logged = appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN))
         .toList();
      assertTrue(logged.isEmpty(), () -> "WARN/ERROR events: " + logged);
      return body;
   }

   private MockHttpServletResponse preview(String source, String sqlString) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(true);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(dataSource(source));
      query.setSQLDefinition(sql);
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         new RuntimeQueryService.RuntimeXQuery(query, RID, source);
      runtimeQuery.setVariables(new VariableTable());
      when(runtimeQueryService.getRuntimeQuery(RID)).thenReturn(runtimeQuery);

      appender.list.clear();
      Principal principal = () -> "admin";
      return mockMvc.perform(get(URL)
                                .principal(principal)
                                .header("Accept", ANGULAR_ACCEPT)
                                .param("runtimeId", RID)
                                .param("sqlString", sqlString))
         .andReturn().getResponse();
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
   private RuntimeQueryService runtimeQueryService;
   private MockMvc mockMvc;
   private Logger root;
   private ListAppender<ILoggingEvent> appender;
}
