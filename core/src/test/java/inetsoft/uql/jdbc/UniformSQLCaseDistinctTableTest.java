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

import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77544, the table lookups of UniformSQL matched case-insensitively and took the first
 * hit, so two FROM tables whose names differ only in case ("A" and "a") collapsed into one.
 * The ANSI FROM clause came out as {@code A LEFT OUTER JOIN A ON A.id = a.id , a}.
 * <p>
 * The parser still drops the quotes of a quoted table name (a separate defect), so the
 * regenerated SQL names the tables bare. On a case-folding database (Derby, H2, Oracle) bare
 * {@code A} and {@code a} are the same table, so rows are compared on Derby with the
 * PostgreSQL helper, which quotes every identifier.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLCaseDistinctTableTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLCaseDistinctTableTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the derby, mysql and oracle helpers ask the repository for the database version
      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String[] PLAIN = { "none", "derby", "h2", "mysql" };
   private static final String[] ANSI = { "derby-ansi", "h2-ansi", "mysql-ansi", "oracle-ansi" };

   // each lookup returns the table whose name matches exactly, in either table order
   @Test
   void lookupsPreferExactMatch() {
      for(boolean upperFirst : new boolean[] { true, false }) {
         UniformSQL sql = upperFirst ? model("A", "a") : model("a", "A");
         int upper = upperFirst ? 0 : 1;
         int lower = 1 - upper;
         String msg = "upperFirst=" + upperFirst;

         assertEquals(upper, sql.getTableIndex("A"), msg);
         assertEquals(lower, sql.getTableIndex("a"), msg);
         assertEquals("A", sql.getTableAlias("A"), msg);
         assertEquals("a", sql.getTableAlias("a"), msg);
         assertEquals("A", sql.getTableName("A"), msg);
         assertEquals("a", sql.getTableName("a"), msg);
         assertSame(sql.getSelectTable(upper), sql.getSelectTable("A"), msg);
         assertSame(sql.getSelectTable(lower), sql.getSelectTable("a"), msg);

         sql.removeTable("a");
         assertEquals(1, sql.getTableCount(), msg);
         assertEquals("A", sql.getTableAlias(0), msg);

         sql = upperFirst ? model("A", "a") : model("a", "A");
         sql.removeTable("A");
         assertEquals(1, sql.getTableCount(), msg);
         assertEquals("a", sql.getTableAlias(0), msg);

         // setTable replaces the table with the exact alias
         sql = upperFirst ? model("A", "a") : model("a", "A");
         sql.setTable("a", "t_lower");
         assertEquals("t_lower", sql.getTableName("a"), msg);
         assertEquals("A", sql.getTableName("A"), msg);
      }
   }

   // getSelectTable(String) matches an alias or a name; an exact match on either wins
   @Test
   void selectTableByAliasOrName() {
      UniformSQL sql = new UniformSQL();
      sql.addTable("x", "a");
      sql.addTable("y", "A");

      assertEquals("y", sql.getSelectTable("A").getAlias());
      assertEquals("x", sql.getSelectTable("a").getAlias());
      assertEquals("y", sql.getTableAlias("A"));
      assertEquals("x", sql.getTableAlias("a"));
      // no exact match: the first case-insensitive match, as before
      assertEquals("x", sql.getSelectTable("X").getAlias());
      assertEquals(0, sql.getTableIndex("X"));
   }

   // an alias that equals another table's name in a different case (from T1 A, a y): every
   // alias lookup resolves a name to the same table, the one with the alias, as SQL does
   @ParameterizedTest
   @CsvSource({ "A,T1,y,a,a", "a,T1,y,A,A", "A,T1,y,a,A", "a,T1,y,A,a" })
   void aliasLookupsAgreeAcrossRows(String alias0, String name0, String alias1, String name1,
                                    String key)
   {
      UniformSQL sql = new UniformSQL();
      sql.addTable(alias0, name0);
      sql.addTable(alias1, name1);

      int index = sql.getTableIndex(key);
      assertEquals(0, index, key);
      assertSame(sql.getSelectTable(index), sql.getSelectTable(key), key);
      assertEquals(name0, sql.getTableName(key), key);
      // getTableAlias(String) looks up by table name, so it finds the other table, as before
      assertEquals(alias1, sql.getTableAlias(key), key);

      sql.removeTable(key);
      assertEquals(1, sql.getTableCount(), key);
      assertEquals(alias1, sql.getTableAlias(0), key);
   }

   // a name that is both a table name and another table's alias resolves to the alias
   @Test
   void aliasBeatsNameOfAnotherTable() {
      UniformSQL sql = new UniformSQL();
      sql.addTable("x", "a");
      sql.addTable("a", "T");

      for(String key : new String[] { "a", "A" }) {
         assertEquals(1, sql.getTableIndex(key), key);
         assertEquals("T", sql.getTableName(key), key);
         assertSame(sql.getSelectTable(1), sql.getSelectTable(key), key);
      }
   }

   // a table without a name doesn't break the lookup by name
   @Test
   void nullNameRow() {
      UniformSQL sql = new UniformSQL();
      sql.addTable(new SelectTable("n", null));
      sql.addTable("x", "a");

      assertEquals("x", sql.getSelectTable("A").getAlias());
      assertNull(sql.getSelectTable("zz"));
   }

   // when at most one table matches a key case-insensitively, each lookup returns exactly
   // what the previous case-insensitive first-match loops returned
   @Test
   void singleMatchSameAsCaseInsensitiveLookup() {
      String[][] models = {
         { "EMP", "EMP", "b", "b" },
         { "e", "Emp", "b", "B" },
         { "Orders", "SALES.ORDERS", "c", "Customers" },
         { "x", "a", "y", "b" },
      };
      String[] keys = { "EMP", "emp", "Emp", "e", "E", "b", "B", "orders", "ORDERS",
                        "sales.orders", "C", "customers", "X", "a", "A", "Y", "zz" };

      for(String[] tables : models) {
         for(String key : keys) {
            UniformSQL sql = new UniformSQL();

            for(int i = 0; i < tables.length; i += 2) {
               sql.addTable(tables[i], tables[i + 1]);
            }

            String msg = Arrays.toString(tables) + " " + key;
            assertEquals(oldTableIndex(sql, key), sql.getTableIndex(key), msg);
            assertEquals(oldTableName(sql, key), sql.getTableName(key), msg);
            assertEquals(oldTableAlias(sql, key), sql.getTableAlias(key), msg);
            assertSame(oldSelectTable(sql, key), sql.getSelectTable(key), msg);

            int removed = oldTableIndex(sql, key);
            sql.removeTable(key);
            assertEquals(tables.length / 2 - (removed >= 0 ? 1 : 0), sql.getTableCount(), msg);
         }
      }
   }

   // the lookups before #77544: the first case-insensitive match
   private static int oldTableIndex(UniformSQL sql, String key) {
      for(int i = 0; i < sql.getTableCount(); i++) {
         if(sql.getSelectTable(i).getAlias().equalsIgnoreCase(key)) {
            return i;
         }
      }

      return -1;
   }

   private static Object oldTableName(UniformSQL sql, String key) {
      int index = oldTableIndex(sql, key);
      return index >= 0 ? sql.getSelectTable(index).getName() : null;
   }

   private static String oldTableAlias(UniformSQL sql, String key) {
      for(int i = 0; i < sql.getTableCount(); i++) {
         if(key.equalsIgnoreCase((String) sql.getSelectTable(i).getName())) {
            return sql.getSelectTable(i).getAlias();
         }
      }

      return null;
   }

   private static SelectTable oldSelectTable(UniformSQL sql, String key) {
      for(int i = 0; i < sql.getTableCount(); i++) {
         SelectTable table = sql.getSelectTable(i);

         if(table.getAlias().equalsIgnoreCase(key) ||
            key.equalsIgnoreCase(table.getName().toString()))
         {
            return table;
         }
      }

      return null;
   }

   // a single table referenced with another case resolves as before
   @Test
   void singleMatchIgnoresCase() {
      UniformSQL sql = model("EMP", "b");

      assertEquals(0, sql.getTableIndex("Emp"));
      assertEquals(0, sql.getTableIndex("emp"));
      assertEquals("EMP", sql.getTableAlias("Emp"));
      assertEquals("EMP", sql.getTableName("emp"));
      assertSame(sql.getSelectTable(0), sql.getSelectTable("eMp"));
      assertEquals(-1, sql.getTableIndex("c"));
      assertNull(sql.getTableAlias("c"));
      assertNull(sql.getTableName("c"));
      assertNull(sql.getSelectTable("c"));

      sql.removeTable("Emp");
      assertEquals(1, sql.getTableCount());
      assertEquals("b", sql.getTableAlias(0));
   }

   // two tables that differ only in case, across join types, with and without the ANSI join
   // option, on each helper
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      // reported shape
      "from \"A\" left join \"a\" on \"A\".id = \"a\".id|" +
         "from \"A\" LEFT OUTER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from \"A\" LEFT OUTER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from \"A\" LEFT OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\" LEFT OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\", \"a\" where \"A\".id = \"a\".id(+)",
      "from \"a\" left join \"A\" on \"a\".id = \"A\".id|" +
         "from \"a\" LEFT OUTER JOIN \"A\" ON \"a\".id = \"A\".id|" +
         "from \"a\" LEFT OUTER JOIN \"A\" ON \"a\".id = \"A\".id|" +
         "from \"a\" LEFT OUTER JOIN \"A\" ON \"a\".\"id\" = \"A\".\"id\"|" +
         "from \"a\" LEFT OUTER JOIN \"A\" ON \"a\".\"id\" = \"A\".\"id\"|" +
         "from \"a\", \"A\" where \"a\".id = \"A\".id(+)",
      "from \"A\" right join \"a\" on \"A\".id = \"a\".id|" +
         "from \"A\" RIGHT OUTER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from \"A\" RIGHT OUTER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from \"A\" RIGHT OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\" RIGHT OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\", \"a\" where \"A\".id (+)= \"a\".id",
      "from \"A\" full outer join \"a\" on \"A\".id = \"a\".id|" +
         "from \"A\" FULL OUTER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from \"A\" FULL OUTER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from \"A\" FULL OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\" FULL OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\" FULL OUTER JOIN \"a\" ON \"A\".id = \"a\".id",
      // inner only: collapsed only with the ANSI join option
      "from \"A\" join \"a\" on \"A\".id = \"a\".id|" +
         "from \"A\", \"a\" where \"A\".id = \"a\".id|" +
         "from \"A\" INNER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from \"A\", \"a\" where \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\" INNER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\", \"a\" where \"A\".id = \"a\".id",
      "from \"A\", \"a\" where \"A\".id = \"a\".id|" +
         "from \"A\", \"a\" where \"A\".id = \"a\".id|" +
         "from \"A\" INNER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from \"A\", \"a\" where \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\" INNER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\", \"a\" where \"A\".id = \"a\".id",
      // inner and outer joins mixed
      "from \"A\" join \"a\" on \"A\".id = \"a\".id left join b on \"a\".id = b.id|" +
         "from (\"A\" INNER JOIN \"a\" " +
         "ON \"A\".id = \"a\".id ) LEFT OUTER JOIN b ON \"a\".id = b.id|" +
         "from (\"A\" INNER JOIN \"a\" " +
         "ON \"A\".id = \"a\".id ) LEFT OUTER JOIN b ON \"a\".id = b.id|" +
         "from (\"A\" INNER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\" ) LEFT OUTER JOIN \"b\" " +
         "ON \"a\".\"id\" = \"b\".\"id\"|" +
         "from (\"A\" INNER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\" ) LEFT OUTER JOIN \"b\" " +
         "ON \"a\".\"id\" = \"b\".\"id\"|" +
         "from \"A\", \"a\", b where \"A\".id = \"a\".id and \"a\".id = b.id(+)",
      "from \"A\" left join \"a\" on \"A\".id = \"a\".id join b on \"a\".id = b.id|" +
         "from (\"A\" LEFT OUTER JOIN \"a\" " +
         "ON \"A\".id = \"a\".id ) INNER JOIN b ON \"a\".id = b.id|" +
         "from (\"A\" LEFT OUTER JOIN \"a\" " +
         "ON \"A\".id = \"a\".id ) INNER JOIN b ON \"a\".id = b.id|" +
         "from (\"A\" LEFT OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\" ) INNER JOIN \"b\" " +
         "ON \"a\".\"id\" = \"b\".\"id\"|" +
         "from (\"A\" LEFT OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\" ) INNER JOIN \"b\" " +
         "ON \"a\".\"id\" = \"b\".\"id\"|" +
         "from \"A\", \"a\", b where \"A\".id = \"a\".id(+) and \"a\".id = b.id",
      "from b left join \"A\" on b.id = \"A\".id join \"a\" on \"A\".id = \"a\".id|" +
         "from (b LEFT OUTER JOIN \"A\" " +
         "ON b.id = \"A\".id ) INNER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from (b LEFT OUTER JOIN \"A\" " +
         "ON b.id = \"A\".id ) INNER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from (\"b\" LEFT OUTER JOIN \"A\" ON \"b\".\"id\" = \"A\".\"id\" ) INNER JOIN \"a\" " +
         "ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from (\"b\" LEFT OUTER JOIN \"A\" ON \"b\".\"id\" = \"A\".\"id\" ) INNER JOIN \"a\" " +
         "ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from b, \"A\", \"a\" where b.id = \"A\".id(+) and \"A\".id = \"a\".id",
      // unquoted and backtick names (distinct tables on MySQL with case-sensitive names)
      "from A left join a on A.id = a.id|" +
         "from A LEFT OUTER JOIN a ON A.id = a.id|" +
         "from A LEFT OUTER JOIN a ON A.id = a.id|" +
         "from \"A\" LEFT OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\" LEFT OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from A, a where A.id = a.id(+)",
      "from `A` left join `a` on `A`.id = `a`.id|" +
         "from \"A\" LEFT OUTER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from \"A\" LEFT OUTER JOIN \"a\" ON \"A\".id = \"a\".id|" +
         "from \"A\" LEFT OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\" LEFT OUTER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\"|" +
         "from \"A\", \"a\" where \"A\".id = \"a\".id(+)",
      // schema-qualified names
      "from s.\"A\" left join s.\"a\" on s.\"A\".id = s.\"a\".id|" +
         "from s.\"A\" LEFT OUTER JOIN s.\"a\" ON s.\"A\".id = s.\"a\".id|" +
         "from s.\"A\" LEFT OUTER JOIN s.\"a\" ON s.\"A\".id = s.\"a\".id|" +
         "from \"s\".\"A\" LEFT OUTER JOIN \"s\".\"a\" ON \"s\".\"A\".\"id\" = \"s\".\"a\".\"id\"|" +
         "from \"s\".\"A\" LEFT OUTER JOIN \"s\".\"a\" ON \"s\".\"A\".\"id\" = \"s\".\"a\".\"id\"|" +
         "from s.\"A\", s.\"a\" where s.\"A\".id = s.\"a\".id(+)",
   })
   void caseDistinctTablesStayDistinct(String from, String plain, String ansi, String pg,
                                       String pgAnsi, String oracle) throws Exception
   {
      String text = "select * " + from;

      for(String type : PLAIN) {
         assertGenerated(withQuote("select * " + plain, type), text, type);
      }

      for(String type : ANSI) {
         assertGenerated(withQuote("select * " + ansi, type), text, type);
      }

      assertGenerated("select * " + pg, text, "postgresql");
      assertGenerated("select * " + pgAnsi, text, "postgresql-ansi");
      assertGenerated("select * " + oracle, text, "oracle");
   }

   // a single table referenced in another case is unchanged
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select * from EMP left join b on Emp.id = b.id|" +
         "select * from EMP LEFT OUTER JOIN b ON Emp.id = b.id|" +
         "select * from \"EMP\" LEFT OUTER JOIN \"b\" ON \"Emp\".\"id\" = \"b\".\"id\"",
      "select * from Emp left join b on EMP.id = b.id|" +
         "select * from Emp LEFT OUTER JOIN b ON EMP.id = b.id|" +
         "select * from \"Emp\" LEFT OUTER JOIN \"b\" ON \"EMP\".\"id\" = \"b\".\"id\"",
   })
   void singleMixedCaseReferenceUnchanged(String text, String expected, String pg)
      throws Exception
   {
      for(String type : PLAIN) {
         assertGenerated(expected, text, type);
      }

      for(String type : ANSI) {
         assertGenerated(expected, text, type);
      }

      assertGenerated(pg, text, "postgresql");
      assertGenerated(pg, text, "postgresql-ansi");
   }

   @Test
   void singleMixedCaseInnerUnchanged() throws Exception {
      String text = "select EMP.k from Emp join b on EMP.id = b.id";

      for(String type : PLAIN) {
         assertGenerated("select EMP.k from Emp, b where EMP.id = b.id", text, type);
      }

      for(String type : ANSI) {
         assertGenerated("select EMP.k from Emp INNER JOIN b ON EMP.id = b.id", text, type);
      }
   }

   // with the data source set before parsing (as the SQL query dialog does) the non-PostgreSQL
   // helpers produce the same text, apart from the MySQL quote. PostgreSQL is left out, since
   // its parser then stores the names quoted and outer joins re-list every table (#77518).
   @Test
   void dataSourceBeforeParse() throws Exception {
      String text = "select * from \"A\" left join \"a\" on \"A\".id = \"a\".id join b on " +
         "\"a\".id = b.id";
      String expected = "select * from (\"A\" LEFT OUTER JOIN \"a\" ON \"A\".id = \"a\".id ) " +
         "INNER JOIN b ON \"a\".id = b.id";

      for(String type : new String[] { "derby", "derby-ansi", "h2", "h2-ansi", "mysql",
                                       "oracle-ansi" })
      {
         assertEquals(withQuote(expected, type), generate(text, dataSource(type), true), type);
      }
   }

   // the regenerated SQL returns the same rows as the original. The PostgreSQL helper quotes
   // every name, so its output can run on Derby where "A" and "a" are distinct tables.
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "\"A\" left join \"a\" on \"A\".\"id\" = \"a\".\"id\"|false",
      "\"A\" right join \"a\" on \"A\".\"id\" = \"a\".\"id\"|false",
      "\"a\" left join \"A\" on \"a\".\"id\" = \"A\".\"id\"|false",
      "\"A\" join \"a\" on \"A\".\"id\" = \"a\".\"id\"|true",
      "\"A\", \"a\" where \"A\".\"id\" = \"a\".\"id\"|true",
      "\"A\" join \"a\" on \"A\".\"id\" = \"a\".\"id\" left join \"b\" on \"a\".\"id\" = " +
         "\"b\".\"id\"|false",
      "\"A\" left join \"a\" on \"A\".\"id\" = \"a\".\"id\" join \"b\" on \"a\".\"id\" = " +
         "\"b\".\"id\"|true",
   })
   void sameRowsOnDerby(String from, boolean ansi) throws Exception {
      String text = "select \"A\".\"id\" ai, \"A\".\"k\" ak, \"a\".\"id\" li, \"a\".\"k\" lk " +
         "from " + from;
      String generated = generate(text, dataSource(ansi ? "postgresql-ansi" : "postgresql"),
                                  false);
      assertFalse(generated.contains("JOIN \"A\" ON \"A\"") ||
                     generated.contains("JOIN \"a\" ON \"a\""), generated);
      assertEquals(rows(text), rows(generated), generated);
   }

   // a quoted table name is generated with the quote of the helper (#77569)
   private static String withQuote(String expected, String type) {
      return type.startsWith("mysql") ? expected.replace('"', '`') : expected;
   }

   private static void assertGenerated(String expected, String text, String type)
      throws Exception
   {
      JDBCDataSource ds = type.equals("none") ? null : dataSource(type);
      String generated = generate(text, ds, false);
      assertEquals(expected, generated, type);
      // round trip guard: the regenerated SQL regenerates to itself
      assertEquals(generated, generate(generated, ds, false), "round trip " + type);
   }

   private static UniformSQL model(String... tables) {
      UniformSQL sql = new UniformSQL();

      for(String table : tables) {
         sql.addTable(table);
      }

      return sql;
   }

   private static String generate(String text, JDBCDataSource ds, boolean dsFirst)
      throws Exception
   {
      UniformSQL sql = new UniformSQL();

      if(dsFirst) {
         sql.setDataSource(ds);
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.setDataSource(ds);
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static JDBCDataSource dataSource(String type) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds_" + type);
      ds.setProductVersion("19.0");

      switch(type.replace("-ansi", "")) {
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "derby" -> {
         ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
         ds.setURL("jdbc:derby:memory:test;create=true");
         ds.setProductVersion("10.17");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost/test");
         ds.setProductVersion("8.0");
      }
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      assertEquals(type.replace("-ansi", ""), SQLHelper.getSQLHelper(ds).getSQLHelperType(),
                   "helper for " + type);
      return ds;
   }

   // rows as a sorted multiset in an in-memory Derby database with tables "A", "a" and "b"
   private static List<String> rows(String query) throws Exception {
      Driver driver = (Driver) Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver")
         .getDeclaredConstructor().newInstance();
      List<String> rows = new ArrayList<>();

      try(Connection con = driver.connect("jdbc:derby:memory:rows77544;create=true",
                                          new Properties());
          Statement st = con.createStatement())
      {
         String[][] data = {
            { "A", "(1, 10)", "(2, 20)", "(3, 30)" },
            { "a", "(1, 1)", "(4, 4)" },
            { "b", "(1, 100)", "(4, 400)" },
         };

         for(String[] table : data) {
            try {
               st.execute("drop table \"" + table[0] + "\"");
            }
            catch(SQLException ignore) {
            }

            st.execute("create table \"" + table[0] + "\" (\"id\" int, \"k\" int)");

            for(int i = 1; i < table.length; i++) {
               st.execute("insert into \"" + table[0] + "\" values " + table[i]);
            }
         }

         try(ResultSet rs = st.executeQuery(query)) {
            ResultSetMetaData meta = rs.getMetaData();
            TreeMap<String, Integer> columns = new TreeMap<>();

            for(int i = 1; i <= meta.getColumnCount(); i++) {
               columns.put(meta.getColumnLabel(i).toLowerCase(), i);
            }

            while(rs.next()) {
               StringBuilder row = new StringBuilder();

               for(int i : columns.values()) {
                  row.append(rs.getObject(i)).append('|');
               }

               rows.add(row.toString());
            }
         }
      }

      Collections.sort(rows);
      assertFalse(rows.isEmpty(), query);
      return rows;
   }
}
