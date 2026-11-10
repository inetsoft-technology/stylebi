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
package inetsoft.uql.jdbc.util;

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The sentinel values NULL_VALUE, EMPTY_STRING and NULL_STRING in a condition pushed to SQL
 * must select the same rows as {@code Condition.evaluate}. Rows are compared on embedded Derby.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class,
                                  ConditionListHandlerSentinelTest.Cfg.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionListHandlerSentinelTest {
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

   // a null row, an empty string, the text "null", the sentinel names as text, and a plain value
   private static final String[] ROWS = {
      null, "", "null", "NULL_VALUE", "EMPTY_STRING", "NULL_STRING", "a"
   };

   private static Connection conn;

   @BeforeAll
   static void createTable() throws Exception {
      conn = DriverManager.getConnection("jdbc:derby:memory:sentinelcond;create=true");
      conn.createStatement().execute("create table t(v varchar(50))");

      for(String row : ROWS) {
         try(PreparedStatement ps = conn.prepareStatement("insert into t values(?)")) {
            ps.setString(1, row);
            ps.execute();
         }
      }
   }

   @AfterAll
   static void closeTable() throws Exception {
      conn.close();
   }

   private static Condition condition(int op, Object value) {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(op);
      cond.addValue(value);
      return cond;
   }

   private record Statement0(String sql, List<Object> parameters) {
   }

   private static Statement0 generate(Condition cond) throws Exception {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("sentinelcond-derby");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:sentinelcond");
      ds.setRuntimeProductName("derby");

      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString("select t.v from t");
         sql.wait();
      }

      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef("t", "v"), cond, 0));
      VariableTable vars = new VariableTable();
      XFilterNode node = new ConditionListHandler().createXFilterNode(list, sql, vars);
      sql.setWhere(node);
      sql.clearSQLString();

      VarSQL varsql = new VarSQL();
      varsql.setBackslashIsEscapeChar(SQLHelper.getSQLHelper(sql).isBackslashEscapeChar());
      varsql.setSQLType(VarSQL.SQLType.STATEMENT);
      String text = varsql.replaceVariables(sql.getSQLString(), vars)
         .replaceAll("\\s+", " ").trim();
      return new Statement0(text, varsql.getParameterValues());
   }

   private static Set<String> sqlRows(int op, Object value) throws Exception {
      Set<String> rows = new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));

      Statement0 statement = generate(condition(op, value));

      try(PreparedStatement ps = conn.prepareStatement(statement.sql())) {
         for(int i = 0; i < statement.parameters().size(); i++) {
            ps.setObject(i + 1, statement.parameters().get(i));
         }

         try(ResultSet rs = ps.executeQuery()) {
            while(rs.next()) {
               rows.add(rs.getString(1));
            }
         }
      }

      return rows;
   }

   private static Set<String> memoryRows(int op, Object value) {
      Condition cond = condition(op, value);
      Set<String> rows = new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));

      for(String row : ROWS) {
         if(cond.evaluate(row)) {
            rows.add(row);
         }
      }

      return rows;
   }

   private static Set<String> set(String... values) {
      Set<String> rows = new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
      rows.addAll(Arrays.asList(values));
      return rows;
   }

   private static void check(Set<String> expected, int op, Object value) throws Exception {
      check(expected, expected, op, value);
   }

   private static void check(Set<String> expectedInMemory, Set<String> expectedInSql, int op,
                             Object value)
      throws Exception
   {
      assertEquals(expectedInMemory, memoryRows(op, value), "in memory");
      assertEquals(expectedInSql, sqlRows(op, value), "in SQL");
   }

   @Test
   void equalTo_nullValue_selectsNullRows() throws Exception {
      // the in-memory NULL operation treats an empty string as null too, SQL IS NULL does not
      check(set(null, ""), set((String) null), XCondition.EQUAL_TO,
            XConstants.CONDITION_NULL_VALUE);
   }

   @Test
   void equalTo_emptyString_selectsEmptyStringOnly() throws Exception {
      check(set(""), XCondition.EQUAL_TO, XConstants.CONDITION_EMPTY_STRING);
   }

   @Test
   void equalTo_nullString_selectsTheTextNull() throws Exception {
      check(set("null"), XCondition.EQUAL_TO, XConstants.CONDITION_NULL_STRING);
   }

   @Test
   void oneOf_singleNullValue_selectsNullRows() throws Exception {
      check(set(null, ""), set((String) null), XCondition.ONE_OF,
            new Object[] { XConstants.CONDITION_NULL_VALUE });
   }

   @Test
   void oneOf_singleEmptyString_selectsEmptyStringOnly() throws Exception {
      check(set(""), XCondition.ONE_OF, new Object[] { XConstants.CONDITION_EMPTY_STRING });
   }

   @Test
   void oneOf_singleNullString_selectsTheTextNull() throws Exception {
      check(set("null"), XCondition.ONE_OF, new Object[] { XConstants.CONDITION_NULL_STRING });
   }

   @Test
   void oneOf_sentinelAmongOtherValues_isComparedAsText() throws Exception {
      check(set("NULL_VALUE", "a"), XCondition.ONE_OF,
            new Object[] { "a", XConstants.CONDITION_NULL_VALUE });
   }
}
