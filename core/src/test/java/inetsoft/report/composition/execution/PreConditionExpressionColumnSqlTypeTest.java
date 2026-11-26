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
package inetsoft.report.composition.execution;

import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82379: worksheets saved before Bug #71789 (2025-07) store the old default
 * sqlType="12" (VARCHAR) on every ColumnRef, including the ref of an SQL expression column.
 * PreConditionListHandler copied that sqlType onto the condition field, and
 * ConditionListHandler.fixConditionValue (reached only by = and its negation) turned a numeric
 * value into a String, which PostgreSQL rejects ("integer = character varying") and MySQL
 * compares as a double. An expression has no database column behind it, so its condition
 * value must be bound with its typed Java value, like a worksheet built today. A real VARCHAR
 * physical column keeps its string coercion.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PreConditionExpressionColumnSqlTypeTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PreConditionExpressionColumnSqlTypeTest {
   private static final String DB = "memory:bug82379";
   /** The SQL text of each query sent to the database, before VarSQL. */
   private static final List<String> SENT = Collections.synchronizedList(new ArrayList<>());
   /** The condition values bound into the SQL ($(dataselcond*) entries of the variables). */
   private static final List<Object> BOUND = Collections.synchronizedList(new ArrayList<>());

   @Configuration
   static class TestConfig {
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
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster, eventPublisher);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         DataSource ds = derby();
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
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

      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }

      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      // records the bound condition values, then runs the SQL on Derby with the real JDBCHandler
      @Bean
      public XSessionManager xSessionManager(XSessionService sessionService) throws Exception {
         XDataService dataService = mock(XDataService.class);
         when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
            .thenAnswer(inv -> {
               XQuery query = inv.getArgument(1);
               VariableTable vars = inv.getArgument(2);

               if(query instanceof JDBCQuery jquery) {
                  SENT.add(jquery.getSQLAsString());
               }

               if(vars != null) {
                  for(Enumeration<String> keys = vars.keys(); keys.hasMoreElements(); ) {
                     String key = keys.nextElement();

                     if(key.startsWith("dataselcond")) {
                        BOUND.add(vars.get(key));
                     }
                  }
               }

               JDBCHandler handler = new JDBCHandler();
               handler.connect(query.getDataSource(), vars);
               return handler.execute(query, vars, inv.getArgument(3), inv.getArgument(5));
            });
         return new XSessionManager(dataService, sessionService, mock(DataSourceRegistry.class));
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table cats");
         }
         catch(SQLException ignore) {
            // first run in this JVM
         }

         stmt.executeUpdate("create table cats (id bigint, code varchar(40))");
         stmt.executeUpdate("insert into cats values (1, '1')");
         stmt.executeUpdate("insert into cats values (2, '2')");
         stmt.executeUpdate("insert into cats values (3, '3')");
         stmt.executeUpdate("insert into cats values (null, null)");
      }
   }

   @BeforeEach
   void setUp() {
      AssetDataCache.getCache().clearCache();
      SENT.clear();
      BOUND.clear();
   }

   @AfterEach
   void tearDown() {
      AssetDataCache.getCache().clearCache();
   }

   @Test
   void legacyConditionRefCarriesVarcharSqlType() throws Exception {
      ColumnRef ref = legacyRef(sqlTable(new Worksheet()), "TWICE");
      assertTrue(ref.isSqlTypeSet(), "legacy XML must load with its sqlType set");
      assertEquals(Types.VARCHAR, ref.getSqlType());
      assertTrue(ref.isExpression());
   }

   @Test
   void equalToOnLegacyExpressionColumnBindsInteger() throws Exception {
      assertEquals(List.of(2), ids("TWICE", 4, false), "sql " + lastSql());
      assertBound(Integer.class, 4);
   }

   @Test
   void notEqualToOnLegacyExpressionColumnBindsInteger() throws Exception {
      assertEquals(List.of(1, 3), ids("TWICE", 4, true), "sql " + lastSql());
      assertBound(Integer.class, 4);
   }

   @Test
   void equalToOnLegacyExpressionColumnKeepsLongAboveDoublePrecision() throws Exception {
      // 2^53 + 3, not representable as a double: a String bind makes MySQL compare doubles
      long value = 9007199254740995L;
      assertEquals(List.of(), ids("TWICE", value, false), "sql " + lastSql());
      assertBound(Long.class, value);
   }

   @Test
   void greaterThanOnLegacyExpressionColumnBindsInteger() throws Exception {
      assertEquals(List.of(3), ids("TWICE", 4, false, XCondition.GREATER_THAN), "sql " + lastSql());
      assertBound(Integer.class, 4);
   }

   /** Control: a real VARCHAR physical column still has its value coerced to a String. */
   @Test
   void equalToOnVarcharPhysicalColumnStillBindsString() throws Exception {
      assertEquals(List.of(2), ids("code", 2, false), "sql " + lastSql());
      assertBound(String.class, "2");
   }

   private static void assertBound(Class<?> type, Object value) {
      assertEquals(1, BOUND.size(), "one bound condition value expected: " + BOUND +
                   "; sql " + lastSql());
      Object bound = BOUND.get(0);
      assertNotNull(bound, "bound value; sql " + lastSql());
      assertEquals(type, bound.getClass(), "bound value " + bound + "; sql " + lastSql());
      assertEquals(value, bound);
   }

   private static String lastSql() {
      return SENT.isEmpty() ? "-" : SENT.get(SENT.size() - 1);
   }

   private List<Object> ids(String column, Object value, boolean negated) throws Exception {
      return ids(column, value, negated, XCondition.EQUAL_TO);
   }

   /**
    * Runs "column op value" as a pre-condition of a SQL-bound table. The condition's column
    * ref is loaded from XML carrying sqlType="12", as a worksheet saved before Bug #71789 has.
    */
   private List<Object> ids(String column, Object value, boolean negated, int op)
      throws Exception
   {
      AssetDataCache.getCache().clearCache();
      SENT.clear();
      BOUND.clear();
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = sqlTable(ws);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(op);
      cond.setType(XSchema.INTEGER);
      cond.setNegated(negated);
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(legacyRef(table, column), cond, 0));
      table.setPreConditionList(list);

      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      TableLens lens = box.getTableLens("T1", AbstractSheet.SHEET_RUNTIME_MODE);
      assertNotNull(lens);
      assertTrue(lastSql().contains("where"), "the condition must be merged into the SQL: " +
                 lastSql());

      List<Object> ids = new ArrayList<>();
      lens.moreRows(Integer.MAX_VALUE);
      assertTrue(lens.getColCount() >= 2, "query failed, placeholder lens returned");

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         ids.add(lens.getObject(r, 0));
      }

      ids.sort(Comparator.nullsFirst(Comparator.comparing(o -> ((Number) o).intValue())));
      return ids.stream().map(o -> o == null ? null : (Object) ((Number) o).intValue()).toList();
   }

   /**
    * The table's column re-read from XML whose sqlType attribute is "12", the value every
    * ColumnRef was written with before Bug #71789 changed the default from VARCHAR to unset.
    */
   private static ColumnRef legacyRef(SQLBoundTableAssembly table, String column) throws Exception {
      ColumnRef ref = (ColumnRef) table.getColumnSelection(false).getAttribute(column);
      StringWriter out = new StringWriter();

      try(PrintWriter writer = new PrintWriter(out)) {
         ref.writeXML(writer);
      }

      String xml = out.toString();
      assertTrue(xml.contains("sqlType=\""), "ColumnRef XML must carry a sqlType: " + xml);
      xml = xml.replaceFirst("sqlType=\"-?\\d+\"", "sqlType=\"12\"");
      Document doc = Tool.parseXML(new StringReader(xml));
      return (ColumnRef) AbstractDataRef.createDataRef(doc.getDocumentElement());
   }

   private static SQLBoundTableAssembly sqlTable(Worksheet ws) throws Exception {
      String text = "select cats.id, cats.code from cats";
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class, long.class);
      parse.setAccessible(true);
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug82379");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      usql.setDataSource(ds);

      JDBCQuery query = new JDBCQuery();
      query.setName("T1");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));

      ColumnSelection columns = new ColumnSelection();

      AttributeRef id = new AttributeRef("id");
      id.setSqlType(Types.BIGINT);
      ColumnRef idRef = new ColumnRef(id);
      idRef.setDataType(XSchema.LONG);
      columns.addAttribute(idRef);

      // a varchar column whose worksheet data type the user set to integer
      AttributeRef code = new AttributeRef("code");
      code.setSqlType(Types.VARCHAR);
      ColumnRef codeRef = new ColumnRef(code);
      codeRef.setDataType(XSchema.INTEGER);
      columns.addAttribute(codeRef);

      // an SQL expression column, built the way ExpressionDialogService builds it
      ExpressionRef exp = new ExpressionRef(null, "TWICE");
      exp.setName("TWICE");
      exp.setExpression("field['id'] * 2");
      ColumnRef twice = new ColumnRef(exp);
      twice.setSQL(true);
      twice.setDataType(XSchema.INTEGER);
      columns.addAttribute(twice);

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
