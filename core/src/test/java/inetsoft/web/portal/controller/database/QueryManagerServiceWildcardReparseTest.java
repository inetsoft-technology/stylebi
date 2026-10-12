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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #78384. A sql selecting a wildcard (t.*) that was parsed while its table had no columns
 * (e.g. the table didn't exist yet) is refused (Bug #77617, PARSE_FAILED, the wildcard removed
 * from the selection), and the refusal is saved with the query. It was never re-derived: an
 * unedited OK of the query dialog kept it, and a re-parse found the empty column list cached
 * by {@link JDBCUtil} and by {@link XEngine} (in memory and on disk), so the query stayed
 * unparsed, and refused by vpm, after the table was created.
 * <p>
 * The metadata comes from a real {@link XEngine} whose handler call is replaced, with its
 * metadata cache in a temporary directory, so both cache layers and a restart are covered.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  QueryManagerServiceWildcardReparseTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceWildcardReparseTest {
   private static final String SQL = "select z.* from \"RG_LATE\" z";

   @Configuration
   static class JdbcConfig {
      // JDBCDataSource's constructor needs CredentialService, whose constructor is
      // package-private.
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
      public ConnectionPoolFactory connectionPoolFactory() {
         return mock(ConnectionPoolFactory.class);
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   /**
    * The engine with the metadata of the database: RG_LATE has no columns until it is
    * created. A missing table is not an error for the handler, it returns an empty result.
    */
   private static class TestEngine extends XEngine {
      final AtomicInteger tableFetches = new AtomicInteger();

      TestEngine(Cluster cluster) {
         super(cluster, null, mock(DataSourceRegistry.class), mock(ConnectionPoolFactory.class));
      }

      @Override
      protected XNode getMetaDataInternal(Object session, XDataSource dx, XNode mtype) {
         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         tableFetches.incrementAndGet();
         return tableMeta(created ? new String[] { "ID", "SECRET" } : new String[0]);
      }
   }

   @BeforeEach
   void setUp(@TempDir Path tempDir) {
      savedMetadataDir = SreeEnv.getProperty("inetsoft.metadata.dir");
      metadataDir = tempDir.toFile();
      SreeEnv.setProperty("inetsoft.metadata.dir", tempDir.toString());
      created = false;
      engine = new TestEngine(cluster);
      ds = dataSource();
   }

   @AfterEach
   void tearDown() {
      if(savedMetadataDir == null) {
         SreeEnv.remove("inetsoft.metadata.dir");
      }
      else {
         SreeEnv.setProperty("inetsoft.metadata.dir", savedMetadataDir);
      }
   }

   /**
    * The reported case: saved while the table was missing, then the table is created and the
    * dialog is OKed unedited on the same server, whose caches hold the empty column list.
    */
   @Test
   void unexpandedWildcardIsParsedOnOkAfterTableCreated() throws Exception {
      UniformSQL saved = savedWhileMissing();
      created = true;

      QueryManagerService service = service(engine, saved);
      assertTrue(service.reparseUnexpandedWildcard(RUNTIME_ID, USER));

      UniformSQL sql = runtimeSql();
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertTrue(XUtil.isParsedSQL(sql));
      assertEquals("[z.ID, z.SECRET]", columns(sql));
      assertEquals(SQL, sql.getSQLString());

      // the fresh column list replaced the cached empty one in both layers
      int fetches = engine.tableFetches.get();
      assertEquals(2, JDBCUtil.getTableColumns(table(), engine, null, ds).getChildCount());
      assertEquals(fetches, engine.tableFetches.get());
      awaitDiskColumns(2);
   }

   /**
    * After a restart only the metadata cache on disk holds the empty column list.
    */
   @Test
   void unexpandedWildcardIsParsedOnOkAfterRestart() throws Exception {
      UniformSQL saved = savedWhileMissing();
      created = true;
      JDBCUtil.clearTableMeta();
      TestEngine restarted = new TestEngine(cluster);

      // without the fix the disk cache answers
      assertEquals(0, JDBCUtil.getTableColumns(table(), restarted, null, ds).getChildCount());
      assertEquals(0, restarted.tableFetches.get());

      QueryManagerService service = service(restarted, saved);
      assertTrue(service.reparseUnexpandedWildcard(RUNTIME_ID, USER));
      assertEquals(UniformSQL.PARSE_SUCCESS, runtimeSql().getParseResult());
      assertEquals("[z.ID, z.SECRET]", columns(runtimeSql()));
      awaitDiskColumns(2);
   }

   // Parse Now (parseSqlString) of the unchanged text doesn't need a metadata refresh either
   @Test
   void parseNowAfterTableCreated() throws Exception {
      UniformSQL saved = savedWhileMissing();
      created = true;

      QueryManagerService service = service(engine, saved);
      assertNull(service.parseSqlString(RUNTIME_ID, SQL, false, true, USER));

      assertEquals(UniformSQL.PARSE_SUCCESS, runtimeSql().getParseResult());
      assertEquals("[z.ID, z.SECRET]", columns(runtimeSql()));
      awaitDiskColumns(2);
   }

   // the table is still missing: the refusal is kept, the query fails closed
   @Test
   void stillMissingTableStaysRefused() throws Exception {
      UniformSQL saved = savedWhileMissing();
      QueryManagerService service = service(engine, saved);
      JDBCQuery query = runtimeQuery.getQuery();

      assertFalse(service.reparseUnexpandedWildcard(RUNTIME_ID, USER));

      assertSame(saved, query.getSQLDefinition());
      assertEquals(UniformSQL.PARSE_FAILED, saved.getParseResult());
      assertFalse(XUtil.isParsedSQL(saved));
      assertEquals("[]", columns(saved));
      awaitDiskColumns(0);
   }

   /**
    * Only a refusal of an unexpandable wildcard is re-derived. A sql the grammar refuses, or
    * a refused sql without a wildcard (e.g. refused by an older grammar), is kept as saved,
    * also when the columns are known. A parse-off sql is never parsed.
    */
   @Test
   void otherRefusalsAreKept() throws Exception {
      created = true;

      for(String text : new String[] {
         "select z.* from \"RG_LATE\" z where",
         "select count(*) from \"RG_LATE\" z",
         "select z.ID from \"RG_LATE\" z" })
      {
         UniformSQL saved = refused(text, true);
         QueryManagerService service = service(engine, saved);

         assertFalse(service.reparseUnexpandedWildcard(RUNTIME_ID, USER), text);
         assertSame(saved, runtimeQuery.getQuery().getSQLDefinition(), text);
         assertEquals(UniformSQL.PARSE_FAILED, saved.getParseResult(), text);
      }

      UniformSQL parseOff = refused(SQL, false);
      QueryManagerService service = service(engine, parseOff);

      assertFalse(service.reparseUnexpandedWildcard(RUNTIME_ID, USER));
      assertSame(parseOff, runtimeQuery.getQuery().getSQLDefinition());
      assertFalse(parseOff.isParseSQL());
      assertEquals(0, engine.tableFetches.get());
   }

   /**
    * A cached empty column list is fetched again only when the caller asks for it, and a
    * cached non-empty list is never fetched again.
    */
   @Test
   void emptyColumnsAreFetchedAgainOnlyWhenAsked() throws Exception {
      assertEquals(0, JDBCUtil.getTableColumns(table(), engine, null, ds).getChildCount());
      assertEquals(1, engine.tableFetches.get());

      created = true;
      assertEquals(0, JDBCUtil.getTableColumns(table(), engine, null, ds).getChildCount());
      assertEquals(1, engine.tableFetches.get());

      assertEquals(2, JDBCUtil.getTableColumns(table(), engine, null, ds, true).getChildCount());
      assertEquals(2, engine.tableFetches.get());

      assertEquals(2, JDBCUtil.getTableColumns(table(), engine, null, ds, true).getChildCount());
      assertEquals(2, JDBCUtil.getTableColumns(table(), engine, null, ds).getChildCount());
      assertEquals(2, engine.tableFetches.get());
      awaitDiskColumns(2);
   }

   // the query parsed and saved while RG_LATE was missing, as loaded from the worksheet
   private UniformSQL savedWhileMissing() throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.setParseSQL(true);

      synchronized(usql) {
         usql.setSQLString(SQL);
         usql.wait();
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      JDBCUtil.fixUniformSQLInfo(usql, engine, null, ds);
      assertEquals(UniformSQL.PARSE_FAILED, usql.getParseResult());
      awaitDiskColumns(0);

      UniformSQL loaded = load(toXML(usql));
      assertEquals(UniformSQL.PARSE_FAILED, loaded.getParseResult());
      assertEquals("[]", columns(loaded));
      return loaded;
   }

   // a saved refused sql
   private UniformSQL refused(String text, boolean parse) {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.setParseSQL(parse);
      usql.setSQLString(text, false);
      usql.setParseResult(UniformSQL.PARSE_FAILED);
      return usql;
   }

   private QueryManagerService service(XRepository repository, UniformSQL sql) {
      JDBCQuery query = new JDBCQuery();
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RUNTIME_ID, ds.getFullName());

      RuntimeQueryService runtimeQueryService = mock(RuntimeQueryService.class);
      when(runtimeQueryService.getRuntimeQuery(RUNTIME_ID)).thenReturn(runtimeQuery);
      return new QueryManagerService(runtimeQueryService, repository,
                                     mock(DataSourceService.class), null, null);
   }

   private UniformSQL runtimeSql() {
      return (UniformSQL) runtimeQuery.getQuery().getSQLDefinition();
   }

   /**
    * Wait until the metadata cache on disk has the columns of RG_LATE, it is written by a
    * background thread, which must not outlive the test.
    */
   private void awaitDiskColumns(int count) {
      await().atMost(Duration.ofSeconds(10)).until(() -> diskColumns() == count);
   }

   private int diskColumns() {
      File[] files = metadataDir.listFiles();

      for(File file : files == null ? new File[0] : files) {
         try(ObjectInputStream in = new ObjectInputStream(
            new BufferedInputStream(new FileInputStream(file))))
         {
            in.readObject(); // build number
            XNode result = ((XNode) in.readObject()).getNode("this.Result");

            if(result != null) {
               return result.getChildCount();
            }
         }
         catch(Exception ignore) {
            // being written
         }
      }

      return -1;
   }

   private static XNode table() {
      XNode table = new XNode("RG_LATE");
      table.setAttribute("type", "TABLE");
      return table;
   }

   private static XTypeNode tableMeta(String[] columns) {
      XTypeNode result = new XTypeNode("Result");

      for(String column : columns) {
         result.addChild(XSchema.createPrimitiveType(column, String.class));
      }

      XTypeNode meta = new XTypeNode("meta");
      meta.addChild(result);
      return meta;
   }

   private static String columns(UniformSQL usql) {
      XSelection selection = usql.getSelection();
      StringBuilder buffer = new StringBuilder("[");

      for(int i = 0; i < selection.getColumnCount(); i++) {
         buffer.append(i > 0 ? ", " : "").append(selection.getColumn(i));
      }

      return buffer.append("]").toString();
   }

   private static String toXML(UniformSQL usql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         usql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static UniformSQL load(String xml) throws Exception {
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return loaded;
   }

   // the table metadata is cached by data source name, use a new name each time
   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds78384_" + (++sources) + "_" + RUN);
      ds.setDriver("org.postgresql.Driver");
      ds.setURL("jdbc:postgresql://localhost/db");
      ds.setRequireLogin(false);
      ds.setRuntimeProductName("postgresql");
      ds.setProductVersion("10.0");
      ds.setFromPortal(true);
      return ds;
   }

   private static final String RUNTIME_ID = "rq78384";
   private static final Principal USER = () -> "admin";
   private static final long RUN = System.nanoTime();
   private static int sources;
   private static volatile boolean created;

   @Autowired
   private Cluster cluster;
   private String savedMetadataDir;
   private File metadataDir;
   private TestEngine engine;
   private JDBCDataSource ds;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;
}
