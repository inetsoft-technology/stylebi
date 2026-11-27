/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.jdbc.util;

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82475: on SQL Server a condition on a TIME column failed with "The data types time and
 * datetime are incompatible", because the java.sql.Time value is bound as a datetime parameter
 * by the driver. The placeholder of a Time value is cast to TIME for every operator. Date and
 * Timestamp values and the other dialects are unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class,
                                  ConditionListHandlerSqlServerTimeTest.Cfg.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionListHandlerSqlServerTimeTest {
   @Configuration
   static class Cfg {
      @Bean
      XRepository repository() {
         return Mockito.mock(XRepository.class);
      }

      @Bean
      inetsoft.uql.service.DataSourceRegistry dataSourceRegistry() {
         return Mockito.mock(inetsoft.uql.service.DataSourceRegistry.class);
      }
   }

   private static final java.sql.Time TIME = java.sql.Time.valueOf("12:30:45");
   private static final java.sql.Time TIME2 = java.sql.Time.valueOf("18:00:00");

   private static JDBCDataSource sqlServer() {
      return dataSource("mssql", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                        "jdbc:sqlserver://localhost;databaseName=x", "sql server");
   }

   private static JDBCDataSource mysql() {
      return dataSource("mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost/x", "mysql");
   }

   private static JDBCDataSource postgres() {
      return dataSource("pg", "org.postgresql.Driver", "jdbc:postgresql://localhost/x",
                        "postgresql");
   }

   private static JDBCDataSource dataSource(String name, String driver, String url,
                                            String product)
   {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug82475-" + name);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      return ds;
   }

   /**
    * Generate the where clause a worksheet condition produces. The variables are not
    * replaced, so the placeholder text is visible.
    */
   private static String generate(JDBCDataSource ds, String type, int op, boolean negated,
                                  boolean equal, Object... values) throws Exception
   {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString("select t.v from t");
         sql.wait();
      }

      Condition cond = new Condition(type);
      cond.setOperation(op);
      cond.setNegated(negated);
      cond.setEqual(equal);

      for(Object value : values) {
         cond.addValue(value);
      }

      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef("t", "v"), cond, 0));
      XFilterNode node = new ConditionListHandler().createXFilterNode(list, sql, new VariableTable());
      sql.setWhere(node);
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static int count(String text, String part) {
      return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
   }

   static Stream<Arguments> operators() {
      return Stream.of(
         // op, equal, number of Time values, expected placeholder count
         Arguments.of(XCondition.EQUAL_TO, false, 1),
         Arguments.of(XCondition.ONE_OF, false, 2),
         Arguments.of(XCondition.BETWEEN, false, 2),
         Arguments.of(XCondition.LESS_THAN, false, 1),
         Arguments.of(XCondition.LESS_THAN, true, 1),
         Arguments.of(XCondition.GREATER_THAN, false, 1),
         Arguments.of(XCondition.GREATER_THAN, true, 1));
   }

   private static Object[] values(int n) {
      return n == 1 ? new Object[] { TIME } : new Object[] { TIME, TIME2 };
   }

   @ParameterizedTest
   @MethodSource("operators")
   void sqlServerTimeIsCastForEveryOperatorAndNegation(int op, boolean equal, int n)
      throws Exception
   {
      for(boolean negated : new boolean[] { false, true }) {
         String sql = generate(sqlServer(), XSchema.TIME, op, negated, equal, values(n));
         assertEquals(n, count(sql, "CAST($("), "op=" + op + " not=" + negated + ": " + sql);
         assertEquals(n, count(sql, " AS TIME)"), sql);
         assertEquals(n, count(sql, "$("), sql);
      }
   }

   @ParameterizedTest
   @MethodSource("operators")
   void sqlServerTimestampAndDateAreUnchanged(int op, boolean equal, int n) throws Exception {
      Object[] ts = n == 1 ? new Object[] { new java.sql.Timestamp(1000L) } :
         new Object[] { new java.sql.Timestamp(1000L), new java.sql.Timestamp(2000L) };
      Object[] dt = n == 1 ? new Object[] { java.sql.Date.valueOf("2024-01-02") } :
         new Object[] { java.sql.Date.valueOf("2024-01-02"), java.sql.Date.valueOf("2024-01-03") };

      for(String sql : new String[] {
         generate(sqlServer(), XSchema.TIME_INSTANT, op, false, equal, ts),
         generate(sqlServer(), XSchema.DATE, op, false, equal, dt) })
      {
         assertFalse(sql.contains("CAST("), sql);
         assertEquals(n, count(sql, "$("), sql);
      }
   }

   @ParameterizedTest
   @MethodSource("operators")
   void otherDialectsAreUnchanged(int op, boolean equal, int n) throws Exception {
      String pg = generate(postgres(), XSchema.TIME, op, false, equal, values(n));
      assertFalse(pg.contains("CAST("), pg);
      assertEquals(n, count(pg, "$("), pg);

      String my = generate(mysql(), XSchema.TIME, op, false, equal, values(n));
      assertFalse(my.contains("CAST("), my);

      // MySQL keeps its time() wrapper for '=' only
      assertEquals(op == XCondition.EQUAL_TO, my.contains("time($("), my);
   }

   @Test
   void sqlServerTimeEqualsTextIsCastPlaceholder() throws Exception {
      String sql = generate(sqlServer(), XSchema.TIME, XCondition.EQUAL_TO, false, false, TIME);
      assertTrue(sql.matches(".*= CAST\\(\\$\\([^)]+\\) AS TIME\\)"), sql);
   }
}
