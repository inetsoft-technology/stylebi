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
package inetsoft.uql.jdbc;

import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.security.Principal;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77754. A binary minus and a right operand starting with a unary minus were generated
 * without a space, so x - -1 was generated as x-- 1, a line comment. A VPM subquery with such
 * an expression was sent as written, so its sentinel parameters were bound as their text, and
 * a generated query lost the rest of the line.
 *
 * The query runs through the real {@link JDBCHandler#execute} with a user on Derby. The test
 * {@link VpmProcessor} evaluates the real {@link VpmCondition} and adds it to the query the way
 * the enterprise VpmUtil.applyConditions does (an XExpressionCondition ANDed to the WHERE).
 *
 * b holds the rows of two owners and three roles, so the VPM subquery's identity predicates
 * filter rows: user u1 with roles r1, r2 may see the b rows of owner u1 and role r1 or r2.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XUtilSentinelMinusOfNegativeTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilSentinelMinusOfNegativeTest {
   private static final String DB = "memory:bug77754vpm";
   private static final String NV = XConstants.CONDITION_NULL_VALUE;
   private static final String ES = XConstants.CONDITION_EMPTY_STRING;
   // the row-security subquery: the user's own rows, of the user's roles, with key p
   private static final String SUB = "select b.id from b where b.owner = $(_USER_) and " +
      "b.k = $(p) and b.id - -1 > b.id and b.r in ($(_ROLES_))";

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
         DataSource ds = recording(derby());
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

      // DerbyHelper asks the repository for the product version
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeAll
   static void setUp() throws Exception {
      Field field = vpmProcessorField();
      oldVpmProcessor = field.get(null);
      field.set(null, new TestVpmProcessor());

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "b" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(SQLException ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table a (id int)");
         stmt.executeUpdate("insert into a values (1), (3), (5), (7), (9)");
         stmt.executeUpdate("create table b (id int, k varchar(20), owner varchar(20), " +
                               "r varchar(20))");
         stmt.executeUpdate("insert into b values (1, null, 'u1', 'r1'), (3, '', 'u1', 'r2'), " +
                               "(5, 'NULL_VALUE', 'u1', 'r1'), (5, 'EMPTY_STRING', 'u1', 'r2'), " +
                               "(9, null, 'u2', 'r1'), (9, '', 'u2', 'r2'), " +
                               "(7, null, 'u1', 'r9'), (7, '', 'u1', 'r9'), (1, 'n1', 'u1', 'r1')");
      }
   }

   @AfterAll
   static void restoreVpmProcessor() throws Exception {
      vpmProcessorField().set(null, oldVpmProcessor);
   }

   @BeforeEach
   void clearVpm() {
      vpm = null;
   }

   // the row-security subquery with a minus of a negative number, which was generated as
   // b.id-- 1 and commented out the rest of its line. The round-trip guard then sent the
   // editor's text as written, so a sentinel was bound as its text. b.id - -1 > b.id holds
   // for every row, so the identity predicates decide the rows.
   @Test
   void vpmSubqueryKeepsEveryPredicate() throws Exception {
      for(Supplier<UniformSQL> sub : List.<Supplier<UniformSQL>>of(
         () -> new UniformSQL(SUB, false), () -> parsed(SUB)))
      {
         vpm = in(sub.get());
         // b.k is null for u1/r1|r2: id 1; b.k = '': id 3 (a literal binding gives 5)
         Run run = run(NV);
         assertEquals(List.of("1"), run.rows, run.condition);
         assertTrue(run.condition.contains("b.k is null"), run.condition);
         assertSecured(run.condition);

         run = run(ES);
         assertEquals(List.of("3"), run.rows, run.condition);
         assertSecured(run.condition);
      }
   }

   // the example of the issue, a query that keeps its sql string (rewriteSentinels) and one
   // whose sql is generated (a worksheet merge, a structured edit). b.id = 6-- 3 would read
   // b.id = 6 and return no row.
   @Test
   void queryWithMinusOfNegative() throws Exception {
      String text = "select b.id from b where b.k = $(p) and b.id = 6 - -3";

      for(boolean kept : new boolean[] { true, false }) {
         // the u2 row with key null, and the one with key ''
         assertEquals(List.of("9"), runQuery(text, kept, NV).rows, "kept=" + kept);
         assertEquals(List.of("9"), runQuery(text, kept, ES).rows, "kept=" + kept);
         assertFalse(executed.get().contains("--"), executed.get());
      }
   }

   private static void assertSecured(String condition) {
      assertFalse(condition.contains("--"), condition);
      assertTrue(condition.contains("b.id- - 1 > b.id"), condition);
      assertTrue(condition.contains("b.owner = $(_user_)"), condition);
      assertTrue(condition.contains("b.r in ($(_roles_))"), condition);
   }

   private static Run runQuery(String text, boolean kept, String p) throws Exception {
      VariableTable vars = new VariableTable();
      vars.put("p", p);
      UniformSQL usql = parsed(text);

      if(kept) {
         // as SQLProcessor keeps the string after parsing
         usql.setSQLString(text, false);
      }

      usql.setDataSource(dataSource());
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77754");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);

      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      executed.set(null);
      XNode node = handler.execute(query, vars, null, null);
      XNodeTableLens table = new XNodeTableLens(node);
      table.moreRows(Integer.MAX_VALUE);
      List<String> rows = new ArrayList<>();

      for(int r = 1; r < table.getRowCount(); r++) {
         rows.add(String.valueOf(table.getObject(r, 0)));
      }

      Collections.sort(rows);
      Run run = new Run();
      run.rows = rows;
      assertNotNull(executed.get(), "no sql executed");
      return run;
   }

   private static UniformSQL parsed(String sql) {
      try {
         UniformSQL usql = new UniformSQL();
         usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
         assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
         usql.clearSQLString();
         return usql;
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private static VpmCondition in(UniformSQL sub) {
      return cond(new XBinaryCondition(new XExpression("a.id", XExpression.FIELD),
                                       new XExpression(sub, XExpression.SUBQUERY), "IN"));
   }

   private static VpmCondition cond(XFilterNode node) {
      VpmCondition cond = new VpmCondition("cond1");
      cond.setType(VpmCondition.TABLE);
      cond.setTable("a");
      cond.setCondition(node);
      return cond;
   }

   private static Run run(String p) throws Exception {
      VariableTable vars = new VariableTable();
      // as JDBCHandler and VpmUtil set them for the user
      vars.put("_USER_", "u1");
      vars.put("_ROLES_", new String[] { "r1", "r2" });
      vars.put("q", "'x'");

      if(p != null) {
         vars.put("p", p);
      }

      UniformSQL usql = parsed("select a.id from a");
      usql.setDataSource(dataSource());
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77706vpm");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);

      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      condition.set(null);
      executed.set(null);
      XNode node = handler.execute(query, vars, USER, null);
      XNodeTableLens table = new XNodeTableLens(node);
      table.moreRows(Integer.MAX_VALUE);
      List<String> rows = new ArrayList<>();

      for(int r = 1; r < table.getRowCount(); r++) {
         rows.add(String.valueOf(table.getObject(r, 0)));
      }

      Collections.sort(rows);
      Run run = new Run();
      run.rows = rows;
      run.condition = norm(condition.get());
      assertNotNull(condition.get(), "the vpm condition wasn't applied");
      assertNotNull(executed.get(), "no sql executed");
      assertFalse(executed.get().contains("$("), executed.get());
      return run;
   }

   private static String norm(String sql) {
      return sql == null ? "" : sql.replaceAll("\\s+", " ").trim().toLowerCase();
   }

   /**
    * Applies the vpm condition of the test the way the enterprise VpmUtil.applyConditions
    * does: VpmCondition.evaluate generates the condition, which is ANDed to the WHERE as an
    * XExpressionCondition, and the sql string is cleared.
    */
   private static final class TestVpmProcessor extends VpmProcessor {
      @Override
      public XQuery applyConditions(XQuery query, VariableTable vars, boolean checkVariable,
                                    Principal user)
      {
         if(user == null || vpm == null) {
            return query;
         }

         try {
            JDBCQuery clone = (JDBCQuery) query.clone();
            UniformSQL usql = (UniformSQL) clone.getSQLDefinition();
            String cond = vpm.evaluate(null, new String[] { "a" }, new String[] { "a" },
                                       new String[] { "id" }, clone.getDataSource(), vars,
                                       user, checkVariable);
            condition.set(cond);

            if(cond != null) {
               usql.setSQLString(null);
               usql.combineWhereByAnd(new XExpressionCondition(
                  new XExpression(cond, XExpression.EXPRESSION)));
            }

            return clone;
         }
         catch(Exception ex) {
            throw new RuntimeException(ex);
         }
      }

      @Override
      public XQuery applyHiddenColumns(XQuery query, VariableTable vars, Principal user) {
         return query;
      }
   }

   private static Field vpmProcessorField() throws Exception {
      Field field = VpmProcessor.class.getDeclaredField("processor");
      field.setAccessible(true);
      return field;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77706vpm");
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

   // record the sql that reaches the connection
   private static DataSource recording(DataSource ds) {
      return proxy(DataSource.class, ds);
   }

   @SuppressWarnings("unchecked")
   private static <T> T proxy(Class<T> type, T target) {
      return (T) Proxy.newProxyInstance(
         XUtilSentinelMinusOfNegativeTest.class.getClassLoader(), new Class<?>[] { type },
         (p, method, args) -> {
            String name = method.getName();

            if(args != null && args.length > 0 && args[0] instanceof String &&
               (name.startsWith("prepare") || name.startsWith("execute")) &&
               ((String) args[0]).trim().toLowerCase().startsWith("select"))
            {
               executed.set((String) args[0]);
            }

            Object result;

            try {
               result = method.invoke(target, args);
            }
            catch(InvocationTargetException e) {
               throw e.getCause();
            }

            if(result instanceof Connection && type != Connection.class) {
               return proxy(Connection.class, (Connection) result);
            }

            if(result instanceof Statement && !(result instanceof PreparedStatement)) {
               return proxy(Statement.class, (Statement) result);
            }

            return result;
         });
   }

   private static final class Run {
      List<String> rows;
      String condition;
   }

   private static final Principal USER = new XPrincipal(new IdentityID("u1", "host-org"));
   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static final ThreadLocal<String> condition = new ThreadLocal<>();
   private static volatile VpmCondition vpm;
   private static Object oldVpmProcessor;
}
