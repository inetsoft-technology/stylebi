/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.Config;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82390, SUM and AVG of a tinyint, smallint or integer column accumulate in the column
 * type on SQL Server, DB2 and Derby, so the sum of valid values raised an overflow error.
 * The pushed aggregate casts the column to BIGINT on those three helpers only, in the select,
 * order by and having text alike. SQL Server and DB2 are not available to the test, the
 * generated text is checked and the Derby text is executed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, AggregateBigintWideningTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AggregateBigintWideningTest {
   @BeforeAll
   static void createTable() throws SQLException {
      conn = DriverManager.getConnection("jdbc:derby:memory:bug82390;create=true");
      stmt = conn.createStatement();
      stmt.execute("create table t (k int, v int, s smallint, \"MixedCase\" int)");
      stmt.execute("insert into t values (1, 2000000000, 30000, 2000000000), " +
         "(1, 2000000000, 30000, 2000000000), (1, 2000000000, 30000, 2000000000), " +
         "(1, 2000000000, 30000, 2000000000), (1, 2000000000, 30000, 2000000000), " +
         "(1, 2000000000, 30000, 2000000000)");
   }

   @AfterAll
   static void dropTable() throws SQLException {
      stmt.close();
      conn.close();

      try {
         DriverManager.getConnection("jdbc:derby:memory:bug82390;drop=true");
      }
      catch(SQLException ignore) {
         // a successful drop is reported as an exception
      }
   }

   @Test
   void typeGate() {
      for(String db : new String[] { "sql server", "db2", "derby" }) {
         for(AggregateFormula f : new AggregateFormula[] { AggregateFormula.SUM, AggregateFormula.AVG }) {
            for(String type : new String[] { XSchema.INTEGER, XSchema.SHORT, XSchema.BYTE }) {
               assertTrue(AggregateRef.needsBigintCast(db, f, type), db + " " + f + " " + type);
            }

            for(String type : new String[] { XSchema.STRING, XSchema.DOUBLE, XSchema.LONG, XSchema.FLOAT,
                                             XSchema.DATE, null })
            {
               assertFalse(AggregateRef.needsBigintCast(db, f, type), db + " " + f + " " + type);
            }
         }

         assertFalse(AggregateRef.needsBigintCast(db, AggregateFormula.COUNT_ALL, XSchema.INTEGER));
         assertFalse(AggregateRef.needsBigintCast(db, AggregateFormula.SUMWT, XSchema.INTEGER));
         assertFalse(AggregateRef.needsBigintCast(db, AggregateFormula.MAX, XSchema.INTEGER));
      }

      // informix extends the db2 helper, sybase, h2, oracle and an unknown helper: unchanged
      for(String db : new String[] { "informix", "sybase", "h2", "oracle", "mysql", "postgresql", null }) {
         assertFalse(AggregateRef.needsBigintCast(db, AggregateFormula.SUM, XSchema.INTEGER), db);
         assertFalse(AggregateRef.needsBigintCast(db, AggregateFormula.AVG, XSchema.INTEGER), db);
      }
   }

   @Test
   void aggregateRefText() {
      AggregateRef sum = new AggregateRef(new ColumnRef(new AttributeRef("t", "v")), AggregateFormula.SUM);
      AggregateRef avg = new AggregateRef(new ColumnRef(new AttributeRef("t", "v")), AggregateFormula.AVG);
      AggregateRef cnt = new AggregateRef(new ColumnRef(new AttributeRef("t", "v")), AggregateFormula.COUNT_ALL);
      assertEquals("SUM(CAST(t.v AS BIGINT))", sum.getExpression(helper("sql server"), XSchema.INTEGER));
      assertEquals("SUM(CAST(t.v AS BIGINT))", sum.getExpression(helper("db2"), XSchema.SHORT));
      assertEquals("AVG(CAST(t.v AS BIGINT))", avg.getExpression(helper("derby"), XSchema.BYTE));
      assertEquals("SUM(t.v)", sum.getExpression(helper("informix"), XSchema.INTEGER));
      assertEquals("SUM(t.v)", sum.getExpression(helper("sybase"), XSchema.INTEGER));
      assertEquals("SUM(t.v)", sum.getExpression(helper("h2"), XSchema.INTEGER));
      assertEquals("SUM(t.v)", sum.getExpression(helper("oracle"), XSchema.INTEGER));
      assertEquals("SUM(t.v)", sum.getExpression(helper(null), XSchema.INTEGER));
      assertEquals("SUM(t.v)", sum.getExpression(helper("sql server"), XSchema.STRING));
      assertEquals("SUM(t.v)", sum.getExpression(helper("sql server"), null));
      assertEquals("SUM(t.v)", sum.getExpression(helper("sql server"), XSchema.DOUBLE));
      assertEquals("SUM(t.v)", sum.getExpression(helper("sql server"), XSchema.LONG));
      assertEquals("COUNT(t.v)", cnt.getExpression(helper("sql server"), XSchema.INTEGER));
      // the one argument form is what every other caller still gets
      assertEquals("SUM(t.v)", sum.getExpression(helper("sql server")));
   }

   /**
    * The type PreAssetQuery gives the aggregate is the type of the table's column (findColumn):
    * an expression column typed integer in the dialog carries it, an untyped one is a string.
    */
   @Test
   void expressionColumnTyping() {
      ExpressionRef typed = new ExpressionRef(null, "INTV");
      typed.setExpression("CAST(2000000000 AS INTEGER)");
      typed.setDataType(XSchema.INTEGER);
      ColumnRef typedCol = new ColumnRef(typed);
      assertEquals(XSchema.INTEGER, typedCol.getDataType());
      assertTrue(AggregateRef.needsBigintCast("sql server", AggregateFormula.SUM, typedCol.getDataType()));

      ExpressionRef untyped = new ExpressionRef(null, "INTV");
      untyped.setExpression("CAST(2000000000 AS INTEGER)");
      ColumnRef untypedCol = new ColumnRef(untyped);
      assertFalse(AggregateRef.needsBigintCast("sql server", AggregateFormula.SUM, untypedCol.getDataType()));
   }

   /**
    * A plain column written quoted: the select, order by and having text are the same widened
    * text, and the quoted MixedCase column is kept as written.
    */
   @Test
   void derbyQuotedMixedCaseColumn() throws Exception {
      String agg = "SUM(CAST(t.\"MixedCase\" AS BIGINT))";
      UniformSQL sql = aggregateSql("derby", agg, "MixedCase", true, true);
      String generated = regenerate(sql);
      assertEquals("select SUM(CAST(t.\"MixedCase\" AS BIGINT)), t.k from t " +
                      "group by t.k having SUM(CAST(t.\"MixedCase\" AS BIGINT)) > 0 " +
                      "order by SUM(CAST(t.\"MixedCase\" AS BIGINT)) desc",
                   generated);
      assertEquals(1, countOf(generated, "having " + agg + " > 0"));
      assertEquals(1, countOf(generated, "order by " + agg + " desc"));
      assertEquals(1, countOf(generated, "select " + agg + ","));

      try(ResultSet rs = stmt.executeQuery(generated)) {
         assertTrue(rs.next(), generated);
         assertEquals(12000000000L, rs.getLong(1), generated);
      }
   }

   /**
    * The widened SUM is pushed through the generated SQL and the sum exceeds the int range.
    */
   @ParameterizedTest
   @ValueSource(strings = { "v", "s" })
   void derbyExecutesWidenedSum(String column) throws Exception {
      String agg = "SUM(CAST(t." + column + " AS BIGINT))";
      String generated = regenerate(aggregateSql("derby", agg, null, true, true));
      long expected = "v".equals(column) ? 12000000000L : 180000L;

      try(ResultSet rs = stmt.executeQuery(generated)) {
         assertTrue(rs.next(), generated);
         assertEquals(expected, rs.getLong(1), generated);
      }

      // the same sum without the cast is the reported failure on Derby
      String plain = regenerate(aggregateSql("derby", "SUM(t.v)", null, false, false));

      if("v".equals(column)) {
         SQLException ex = assertThrows(SQLException.class, () -> stmt.executeQuery(plain).next());
         assertEquals("22003", ex.getSQLState());
      }
   }

   /**
    * The widened text of a column of another helper's generation is repaired as the plain one.
    */
   @Test
   void otherHelpersKeepTheirTextAndWidenedHelpersCast() throws Exception {
      for(String db : new String[] { "sql server", "db2", "derby" }) {
         String generated = regenerate(aggregateSql(db, "SUM(CAST(t.v AS BIGINT))", null, false, false));
         assertEquals(1, countOf(generated.toLowerCase(), "sum(cast("), db + ": " + generated);
         assertTrue(generated.toLowerCase().contains(" as bigint))"), db + ": " + generated);
      }

      for(String db : new String[] { "informix", "sybase", "h2", "oracle" }) {
         String generated = regenerate(aggregateSql(db, "SUM(t.v)", null, false, false));
         assertFalse(generated.toLowerCase().contains("bigint"), db + ": " + generated);
      }
   }

   // select t.k, <agg> from t group by t.k [having agg > 0] [order by agg desc]
   private static UniformSQL aggregateSql(String db, String agg, String quotedSegment,
                                          boolean having, boolean order)
   {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(source(db));
      sql.addTable("t");
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      selection.addColumn("t.k");
      selection.setTable("t.k", "t");
      int index = selection.addColumn(agg);
      selection.setExpression(index, false);
      selection.setAggregate(agg, true);

      if(quotedSegment != null) {
         selection.setQuotedAggregate(index, quotedSegment);
      }

      sql.setGroupBy(new Object[] { "t.k" });

      if(having) {
         XFilterNode condition = new XBinaryCondition(new XExpression(agg, XExpression.EXPRESSION),
                                                      new XExpression("0", XExpression.VALUE), ">");
         condition.setClause(XFilterNode.HAVING);
         sql.setHaving(condition);
      }

      if(order) {
         sql.setOrderBy(agg, "desc");
      }

      return sql;
   }

   private static int countOf(String text, String part) {
      int count = 0;

      for(int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
         count++;
      }

      return count;
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static AggregateHelper helper(String type) {
      return new AggregateHelper() {
         @Override
         public String getColumnString(DataRef column) {
            return column == null ? null : "t.v";
         }

         @Override
         public String getDBType() {
            return type;
         }

         @Override
         public String getAggregateFunction(String func) {
            return null;
         }
      };
   }

   private static JDBCDataSource source(String product) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds82390" + product);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:x");
      ds.setRuntimeProductName(product);
      // otherwise the oracle helper asks the repository for it
      ds.setProductVersion("10.0");
      return ds;
   }

   private static Connection conn;
   private static Statement stmt;

   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenReturn("H2");
         return config;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }
}
