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

import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79610: contains / starting with conditions pushed to SQL treated {@code _} and
 * {@code %} in the value as LIKE wildcards. The value must match literally, as it does in
 * memory and in a materialized view. Rows are compared on embedded Derby against
 * {@code Condition.evaluate}; the other dialects are checked on generated text only.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class,
                                  ConditionListHandlerLikeEscapeTest.Cfg.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionListHandlerLikeEscapeTest {
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

   private static final String[] ROWS = {
      "a_b", "aXb", "a-b", "100%", "100 apples", "1000", "abc", "O'Brien", "a\\b", "a\\_b",
      "FY_2024", "FY12024", "a!b", "a!_b", "a[b]", "ab", "user_id", "userXid"
   };

   private static Connection conn;

   @BeforeAll
   static void createTable() throws Exception {
      conn = DriverManager.getConnection("jdbc:derby:memory:bug79610;create=true");
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

   private static JDBCDataSource dataSource(String name, String driver, String url,
                                            String product)
   {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug79610-" + name);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      return ds;
   }

   private static JDBCDataSource derby() {
      return dataSource("derby", "org.apache.derby.jdbc.EmbeddedDriver",
                        "jdbc:derby:memory:bug79610", "derby");
   }

   /**
    * Generate the SQL a worksheet condition produces, with the variables spliced in the way
    * JDBCHandler does it.
    */
   private static String generate(JDBCDataSource ds, int op, String value) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString("select t.v from t");
         sql.wait();
      }

      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(op);
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef("t", "v"), cond, 0));
      VariableTable vars = new VariableTable();
      XFilterNode node = new ConditionListHandler().createXFilterNode(list, sql, vars);
      sql.setWhere(node);
      sql.clearSQLString();
      String text = sql.getSQLString();

      VarSQL varsql = new VarSQL();
      varsql.setBackslashIsEscapeChar(SQLHelper.getSQLHelper(sql).isBackslashEscapeChar());
      varsql.setSQLType(VarSQL.SQLType.STATEMENT);
      return varsql.replaceVariables(text, vars).replaceAll("\\s+", " ").trim();
   }

   private static List<String> inMemory(int op, String value) {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(op);
      cond.addValue(value);
      List<String> rows = new ArrayList<>();

      for(String row : ROWS) {
         if(cond.evaluate(row)) {
            rows.add(row);
         }
      }

      return rows;
   }

   private static List<String> query(String sql) throws Exception {
      List<String> rows = new ArrayList<>();

      try(Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
         while(rs.next()) {
            rows.add(rs.getString(1));
         }
      }

      return rows;
   }

   static Stream<Arguments> operatorsAndValues() {
      List<Arguments> args = new ArrayList<>();
      String[] values = { "a_b", "100%", "FY_2024", "user_id", "a\\b", "a\\_b", "O'Brien",
                          "a!b", "a!_b", "a[b", "abc", "a" };

      for(int op : new int[] { XCondition.CONTAINS, XCondition.STARTING_WITH }) {
         for(String value : values) {
            args.add(Arguments.of(op, value));
         }
      }

      return args.stream();
   }

   @ParameterizedTest
   @MethodSource("operatorsAndValues")
   void derbyRowsMatchInMemory(int op, String value) throws Exception {
      List<String> db = query(generate(derby(), op, value));
      List<String> mem = inMemory(op, value);
      Collections.sort(db);
      Collections.sort(mem);
      assertEquals(mem, db, "op=" + op + " value=" + value);
   }

   @Test
   void underscoreNoLongerMatchesAnyCharacter() throws Exception {
      List<String> db = query(generate(derby(), XCondition.CONTAINS, "a_b"));
      assertTrue(db.contains("a_b"));
      assertFalse(db.contains("aXb"));
      assertFalse(db.contains("a-b"));
   }

   @Test
   void generatedTextCarriesEscapeClause() throws Exception {
      assertTrue(generate(derby(), XCondition.CONTAINS, "a_b")
                    .endsWith("LIKE '%a!_b%' ESCAPE '!'"));
      assertTrue(generate(derby(), XCondition.STARTING_WITH, "100%")
                    .endsWith("LIKE '100!%%' ESCAPE '!'"));
   }

   @Test
   void likeOperatorStaysAPattern() throws Exception {
      String sql = generate(derby(), XCondition.LIKE, "a_b%");
      assertTrue(sql.endsWith("LIKE 'a_b%'"), sql);
   }

   static Stream<Arguments> escapingDialects() {
      return Stream.of(
         Arguments.of("mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost/x",
                      "mysql"),
         Arguments.of("pg", "org.postgresql.Driver", "jdbc:postgresql://localhost/x",
                      "postgresql"),
         Arguments.of("oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:x",
                      "oracle"),
         Arguments.of("db2", "com.ibm.db2.jcc.DB2Driver", "jdbc:db2://localhost/x", "db2"),
         Arguments.of("derby2", "org.apache.derby.jdbc.EmbeddedDriver", "jdbc:derby:memory:x",
                      "derby"));
   }

   @ParameterizedTest
   @MethodSource("escapingDialects")
   void escapingDialectsEmitEscapeClause(String name, String driver, String url, String product)
      throws Exception
   {
      String sql = generate(dataSource(name, driver, url, product), XCondition.CONTAINS, "a_b");
      assertTrue(sql.endsWith("LIKE '%a!_b%' ESCAPE '!'"), sql);
   }

   @Test
   void mySqlBackslashIsDoubledByTheLiteralAndNotAnEscapeInLike() throws Exception {
      // ESCAPE '!' makes backslash an ordinary LIKE character; VarSQL's own doubling only
      // protects the string literal
      String sql = generate(dataSource("mysql2", "com.mysql.cj.jdbc.Driver",
                                       "jdbc:mysql://localhost/x", "mysql"),
                            XCondition.CONTAINS, "a\\_b");
      assertTrue(sql.endsWith("LIKE '%a\\\\!_b%' ESCAPE '!'"), sql);
   }

   @Test
   void sqlServerAlsoEscapesOpeningBracket() throws Exception {
      String sql = generate(dataSource("mssql", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                                       "jdbc:sqlserver://localhost;databaseName=x", "sql server"),
                            XCondition.CONTAINS, "a[b_");
      assertTrue(sql.endsWith("LIKE '%a![b!_%' ESCAPE '!'"), sql);
   }

   @Test
   void dialectWithoutEscapeSupportIsUnchanged() throws Exception {
      String sql = generate(dataSource("ch", "com.clickhouse.jdbc.ClickHouseDriver",
                                       "jdbc:clickhouse://localhost/x", "clickhouse"),
                            XCondition.CONTAINS, "a_b");
      assertTrue(sql.endsWith("LIKE '%a_b%'"), sql);
      assertFalse(sql.contains("ESCAPE"), sql);
   }

   @Test
   void escapeLikeValueEscapesSpecialsAndEscapeChar() {
      SQLHelper helper = SQLHelper.getSQLHelper("derby");
      assertEquals("a!_b!%c!!d", helper.escapeLikeValue("a_b%c!d"));
      assertEquals("plain", helper.escapeLikeValue("plain"));
      assertNull(new SQLHelper().getLikeEscapeChar());
      assertEquals("a_b", new SQLHelper().escapeLikeValue("a_b"));
   }
}
