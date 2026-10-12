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

import antlr.RecognitionException;
import inetsoft.test.*;
import inetsoft.uql.util.Config;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77440, the outer join table check resolves a join column's table the same way with
 * every data source, and a query with an outer join can't join a table outside its own
 * from clause (a correlated subquery). A data source whose helper quotes identifiers (e.g. PostgreSQL)
 * stores an unaliased table with its quoted name ("a"), while the join column's table
 * part is unquoted (a), and the two must still be the same table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  UniformSQLOuterJoinDialectTest.DataSourceConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOuterJoinDialectTest {
   // the data source type is found from the driver class, as for a real data source
   static Stream<Arguments> dataSources() {
      return Stream.of(
         Arguments.of("postgresql", "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/db"),
         Arguments.of("snowflake", "net.snowflake.client.jdbc.SnowflakeDriver",
                      "jdbc:snowflake://x.snowflakecomputing.com"),
         Arguments.of("exasol", "com.exasol.jdbc.EXADriver", "jdbc:exa:localhost:8563"),
         Arguments.of("mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost/db"),
         Arguments.of("sql server", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                      "jdbc:sqlserver://localhost;databaseName=db"),
         Arguments.of("access", "net.ucanaccess.jdbc.UcanaccessDriver", "jdbc:ucanaccess://db.accdb"),
         Arguments.of("oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:db"),
         Arguments.of("oracle ansi", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:db"),
         Arguments.of("db2", "com.ibm.db2.jcc.DB2Driver", "jdbc:db2://localhost:50000/db"),
         Arguments.of("h2", "org.h2.Driver", "jdbc:h2:mem:db"),
         Arguments.of("h2 ansi", "org.h2.Driver", "jdbc:h2:mem:db"),
         Arguments.of("postgresql ansi", "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/db"),
         Arguments.of("sybase", "net.sourceforge.jtds.jdbc.Driver", "jdbc:jtds:sybase://localhost/db"),
         Arguments.of("hive", "org.apache.hive.jdbc.HiveDriver", "jdbc:hive2://localhost:10000/db"),
         Arguments.of("google bigquery", "com.simba.googlebigquery.jdbc42.Driver",
                      "jdbc:bigquery://localhost"),
         Arguments.of("databricks", "com.databricks.client.jdbc.Driver", "jdbc:databricks://localhost"));
   }

   static final String QUOTED_TABLES =
      "select a.x from \"a\" left join \"b\" on \"b\".\"id\" = \"a\".\"id\"";

   static final String[] ACCEPTED = {
      "select a.x from a left join b on b.id = a.id",
      "select a.x from a right join b on b.id = a.id",
      "select a.x from a left join b on b.id = a.id and b.k = a.k",
      "select Ab.x from Ab left join Bc on Bc.id = Ab.id",
      "select t1.x from a t1 left join b t2 on t2.id = t1.id",
      "select sch.a.id from sch.a left join sch.b on sch.b.id = sch.a.id",
      "select a.x from a left join b on a.k = b.k left join c on c.id = b.id",
      QUOTED_TABLES,
      "select * from \"My A\" left join b on b.id = \"My A\".id"
   };

   static final String[] REFUSED = {
      "select a.x from a left join b on a.k = b.k left join c on a.id = b.id",
      "select * from a left join b on id = bid",
      "select * from a left join b on b.id = zz.id"
   };

   // Bug #77434, a join group on the right side of an outer join is only accepted when
   // the generated sql has the same joins (#77475 generates them in text order). Oracle
   // without ansi join generates (+) joins without the group and is refused
   static final String[] NESTED = {
      "select a.x from a left join (b left join c on c.id = b.id) on b.id = a.id",
      "select a.x from (a left join b on a.id = b.id) left join " +
         "(c left join d on d.id = c.id) on c.id = a.id"
   };

   // an outer join to a table outside the query's from clause, the generated from
   // clause would join the outer table into the subquery
   static final String[] OUTSIDE_TABLE_REFUSED = {
      "select * from a where exists (select 1 from c, d where c.id = d.id(+) and c.k = a.k(+))",
      "select * from a where exists (select 1 from c where c.id *= a.id)",
      "select * from a where exists (select 1 from c left join d on d.id = c.id " +
         "where c.k = zz.k(+))"
   };

   // a query with an outer join whose other joins are to a table outside its from
   // clause. They stay in its where clause (#77480), so the correlation is kept
   static final String[] OUTSIDE_TABLE_JOIN_ACCEPTED = {
      "select * from a where exists (select 1 from c left join d on d.id = c.id where c.id = a.id)",
      "select * from a where not exists (select 1 from c left join d on d.id = c.id " +
         "where a.id = c.id and d.k = 1)",
      "select * from a where a.id in (select c.id from c left join d on d.id = c.id where c.k = a.k)",
      "select * from a where exists (select 1 from c right join d on d.id = c.id where d.id = a.id)",
      "select * from a where exists (select 1 from c, d where c.id = d.id(+) and c.id = a.id)"
   };

   // a correlated subquery without an outer join keeps its joins in the where clause
   static final String[] CORRELATED_ACCEPTED = {
      "select * from a where exists (select 1 from c where c.id = a.id)",
      "select * from a where exists (select 1 from c, d where c.id = d.id and c.id = a.id)",
      "select * from a where exists (select 1 from c join d on d.id = c.id where c.id = a.id)",
      "select * from a where a.id in (select c.id from c where c.k = a.k)",
      "select * from a left join b on b.id = a.id where exists (select 1 from c where c.id = b.id)"
   };

   // Bug #77440 (I2), a join column may name an unaliased schema-qualified table by its
   // bare table name, which is ordinary legacy (e.g. Oracle) sql and not correlated
   static final String[] SCHEMA_BARE_NAME_ACCEPTED = {
      "select * from scott.emp, scott.dept where emp.deptno = dept.deptno(+)",
      "select * from scott.emp left join scott.dept on emp.deptno = dept.deptno",
      "select * from scott.emp right join scott.dept on dept.deptno = emp.deptno",
      "select * from scott.emp left join scott.dept on scott.dept.deptno = emp.deptno",
      "select * from \"scott\".\"emp\" left join \"scott\".\"dept\" " +
         "on \"emp\".\"deptno\" = \"dept\".\"deptno\""
   };

   // the bare name matches no table when two unaliased tables share it (ambiguous), or
   // when the schema table has an alias (the alias hides the table name)
   static final String[] SCHEMA_BARE_NAME_REFUSED = {
      "select * from s1.emp, s2.emp, dept where emp.deptno = dept.deptno(+)",
      "select * from s1.emp, s2.emp left join dept on dept.deptno = emp.deptno",
      "select * from scott.emp e, scott.dept where emp.deptno = dept.deptno(+)",
      "select * from scott.emp e left join scott.dept on dept.deptno = emp.deptno"
   };

   static Stream<Arguments> outsideTableRefusedCases() {
      return Stream.concat(cases(OUTSIDE_TABLE_REFUSED), cases(SCHEMA_BARE_NAME_REFUSED));
   }

   // a data source with ansi joins keeps the correlation in the subquery's where
   // clause too (#77480)
   static Stream<Arguments> correlatedAcceptedCases() {
      return cases(CORRELATED_ACCEPTED);
   }

   static Stream<Arguments> outsideTableJoinAcceptedCases() {
      return cases(OUTSIDE_TABLE_JOIN_ACCEPTED);
   }

   static Stream<Arguments> acceptedCases() {
      return Stream.concat(cases(ACCEPTED), cases(SCHEMA_BARE_NAME_ACCEPTED));
   }

   static Stream<Arguments> refusedCases() {
      return cases(REFUSED);
   }

   static Stream<Arguments> nestedCases() {
      return cases(NESTED);
   }

   private static Stream<Arguments> cases(String[] texts) {
      List<Arguments> list = new ArrayList<>();
      dataSources().forEach(ds -> {
         for(String text : texts) {
            Object[] args = ds.get();
            list.add(Arguments.of(args[0], args[1], args[2], text));
         }
      });
      return list.stream();
   }

   @ParameterizedTest(name = "{0}: {3}")
   @MethodSource("acceptedCases")
   void outerJoinOnJoinedTableParses(String type, String driver, String url, String text)
      throws Exception
   {
      JDBCDataSource ds = dataSource(type, driver, url);

      // snowflake and exasol fold a.x to A.X, so a and "a" are two names and the query is
      // refused (Bug #77643). It fails on these databases as written, "a" is no table
      if(text.equals(QUOTED_TABLES) && (type.startsWith("snowflake") || type.startsWith("exasol"))) {
         assertThrows(antlr.SemanticException.class, () -> parse(text, ds));
         return;
      }

      UniformSQL sql = parseStructure(text, type, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());

      for(XJoin join : sql.getJoins()) {
         int index1 = sql.getJoinTableIndex(join.getTable1(sql));
         int index2 = sql.getJoinTableIndex(join.getTable2(sql));
         assertTrue(index1 >= 0 && index2 >= 0 && index1 != index2, join.toString());
      }

      // the generated sql lists each table once and parses back to itself. A left join
      // to a nested join is generated as a right join (#77475) whose ON columns are in
      // the other order when it is parsed again, so compare from the second generation on
      String generated = normalize(sql.getSQLString());
      UniformSQL reparsed = parse(generated, ds);
      assertEquals(sql.getTableCount(), reparsed.getTableCount(), generated);
      generated = normalize(reparsed.getSQLString());
      assertEquals(generated, normalize(parse(generated, ds).getSQLString()));
   }

   @ParameterizedTest(name = "{0}: {3}")
   @MethodSource("refusedCases")
   void outerJoinNotOnJoinedTableFails(String type, String driver, String url, String text) {
      JDBCDataSource ds = dataSource(type, driver, url);
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text, ds));
      assertTrue(ex.getMessage().contains("Unsupported outer join condition"), ex.getMessage());
   }

   @ParameterizedTest(name = "{0}: {3}")
   @MethodSource("nestedCases")
   void outerJoinOfNestedJoinParsesWithSameJoins(String type, String driver, String url,
                                                 String text)
      throws Exception
   {
      JDBCDataSource ds = dataSource(type, driver, url);

      if("oracle".equals(type)) {
         RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text, ds));
         assertTrue(ex.getMessage().contains("Unsupported nested join"), ex.getMessage());

         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
         return;
      }

      // the generated sql keeps the group and parses back to itself. A left join to a
      // nested join is generated as a right join (#77475) whose ON columns are in the
      // other order when it is parsed again, so compare from the second generation on
      UniformSQL sql = parse(text, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      String generated = normalize(sql.getSQLString());
      assertTrue(generated.contains("("), generated);
      generated = normalize(parse(generated, ds).getSQLString());
      assertEquals(generated, normalize(parse(generated, ds).getSQLString()));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.x from a left join b on b.id = a.id | " +
         "select \"a\".\"x\" from \"a\" LEFT OUTER JOIN \"b\" ON \"a\".\"id\" = \"b\".\"id\"",
      "select A.x from A left join B on A.id = B.id | " +
         "select \"A\".\"x\" from \"A\" LEFT OUTER JOIN \"B\" ON \"A\".\"id\" = \"B\".\"id\"",
      "select sch.a.id from sch.a left join sch.b on sch.b.id = sch.a.id | " +
         "select \"sch\".\"a\".\"id\" from \"sch\".\"a\" LEFT OUTER JOIN \"sch\".\"b\" " +
         "ON \"sch\".\"a\".\"id\" = \"sch\".\"b\".\"id\"",
      "select a.x from a left join b on a.k = b.k left join c on c.id = b.id | " +
         "select \"a\".\"x\" from (\"a\" LEFT OUTER JOIN \"b\" ON \"a\".\"k\" = \"b\".\"k\" ) " +
         "LEFT OUTER JOIN \"c\" ON \"b\".\"id\" = \"c\".\"id\""
   })
   void postgresqlOuterJoinGeneratesEachTableOnce(String text, String expected) throws Exception {
      JDBCDataSource ds = dataSource("postgresql", "org.postgresql.Driver",
                                     "jdbc:postgresql://localhost:5432/db");
      assertEquals(expected, normalize(parse(text, ds).getSQLString()));
   }

   @ParameterizedTest(name = "{0}: {3}")
   @MethodSource("outsideTableRefusedCases")
   void outerJoinQueryJoiningOutsideTableFails(String type, String driver, String url,
                                               String text)
   {
      JDBCDataSource ds = dataSource(type, driver, url);
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text, ds));
      assertTrue(ex.getMessage().contains("Unsupported"), ex.getMessage());

      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }

   @ParameterizedTest(name = "{0}: {3}")
   @MethodSource("outsideTableJoinAcceptedCases")
   void outerJoinQueryJoiningOutsideTableInWhereParses(String type, String driver, String url,
                                                       String text)
      throws Exception
   {
      JDBCDataSource ds = dataSource(type, driver, url);

      // Bug #77434, oracle without ansi join generates the right join and the correlation
      // as (+) joins, which don't have the same joins
      if("oracle".equals(type) && text.contains(" right join ")) {
         RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text, ds));
         assertTrue(ex.getMessage().contains("Unsupported RIGHT or FULL join"), ex.getMessage());
         return;
      }

      UniformSQL sql = parseStructure(text, type, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      String generated = normalize(sql.getSQLString());
      String unquoted = generated.replace("\"", "");
      // the outer table isn't joined into the subquery, whose where clause keeps the
      // correlation to it
      assertFalse(unquoted.matches("(?i).*\\( select .* (join|from|,) a( |\\)).*"), generated);
      assertTrue(unquoted.matches("(?i).*\\( select .* where .*\\ba\\.(id|k)\\b.*"), generated);

      // the correlation of a structure parsed without a quoting data source is quoted by the
      // parse of its generated sql, so its round trip starts from the second generation
      if(isWhereOuterJoinRefused(type, text)) {
         // snowflake and exasol fold the unquoted correlation a.id to A.ID, so its mix with
         // the quoted "c"."id" is refused (Bug #77643)
         if(type.startsWith("snowflake") || type.startsWith("exasol")) {
            String mixed = generated;
            assertThrows(antlr.SemanticException.class, () -> parse(mixed, ds));
            return;
         }

         generated = normalize(parse(generated, ds).getSQLString());
      }

      assertEquals(generated, normalize(parse(generated, ds).getSQLString()));
   }

   @ParameterizedTest(name = "{0}: {3}")
   @MethodSource("correlatedAcceptedCases")
   void correlatedSubqueryWithoutOuterJoinParses(String type, String driver, String url,
                                                 String text)
      throws Exception
   {
      JDBCDataSource ds = dataSource(type, driver, url);
      UniformSQL sql = parse(text, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      String generated = normalize(sql.getSQLString());
      // the subquery keeps its own tables and the correlation stays in its where clause
      assertTrue(generated.replace("\"", "").matches(".*\\( select [^()]* where [^()]*\\.\\w+ = [^()]*\\)$"),
                 generated);
      assertEquals(generated, normalize(parse(generated, ds).getSQLString()));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "sql server | select * from a left join b on b.id = a.id | " +
         "select * from a LEFT OUTER JOIN b ON a.id = b.id",
      "sql server | select * from a where exists (select 1 from c join d on d.id = c.id " +
         "where c.id = a.id) | select * from a where EXISTS ( select 1 from c, d " +
         "where d.id = c.id and c.id = a.id)",
      "oracle | select * from a left join b on b.id = a.id where exists " +
         "(select 1 from c where c.id = b.id) | select * from a, b where a.id = b.id(+) " +
         "and EXISTS ( select 1 from c where c.id = b.id)",
      "oracle ansi | select * from a left join b on b.id = a.id | " +
         "select * from a LEFT OUTER JOIN b ON a.id = b.id",
      "postgresql | select * from a where exists (select 1 from c where c.id = a.id) | " +
         "select * from \"a\" where EXISTS ( select 1 from \"c\" where \"c\".\"id\" = \"a\".\"id\")",
      // a legacy where outer join lists each quoted table once
      "postgresql | select a.x, b.x from a, b where a.id = b.id(+) | " +
         "select \"a\".\"x\", \"b\".\"x\" from \"a\" LEFT OUTER JOIN \"b\" ON \"a\".\"id\" = \"b\".\"id\"",
      "postgresql | select a.x, b.x from a, b where a.id *= b.id | " +
         "select \"a\".\"x\", \"b\".\"x\" from \"a\" LEFT OUTER JOIN \"b\" ON \"a\".\"id\" = \"b\".\"id\"",
      // oracle (+) preserves the earlier table, a case-mismatched join used to put the
      // (+) on the wrong side (the nested join case is refused on oracle without ansi
      // join by Bug #77434, see NESTED)
      "oracle | select A.x, B.x from A left join B on b.id = a.id | " +
         "select A.X, B.X from A, B where a.id = b.id(+)",
      // a bare table name refers to its unaliased schema table (I2), legacy oracle sql is
      // kept, and the ansi helpers no longer list the tables twice
      "oracle | select * from scott.emp, scott.dept where emp.deptno = dept.deptno(+) | " +
         "select * from scott.emp, scott.dept where emp.deptno = dept.deptno(+)",
      "oracle | select * from scott.emp left join scott.dept on emp.deptno = dept.deptno | " +
         "select * from scott.emp, scott.dept where emp.deptno = dept.deptno(+)",
      "oracle | select * from scott.emp right join scott.dept on dept.deptno = emp.deptno | " +
         "select * from scott.emp, scott.dept where emp.deptno (+)= dept.deptno",
      "oracle ansi | select * from scott.emp, scott.dept where emp.deptno = dept.deptno(+) | " +
         "select * from scott.emp LEFT OUTER JOIN scott.dept ON emp.deptno = dept.deptno",
      "sql server | select * from scott.emp right join scott.dept on dept.deptno = emp.deptno | " +
         "select * from scott.emp RIGHT OUTER JOIN scott.dept ON emp.deptno = dept.deptno",
      "postgresql | select * from scott.emp left join scott.dept on emp.deptno = dept.deptno | " +
         "select * from \"scott\".\"emp\" LEFT OUTER JOIN \"scott\".\"dept\" " +
         "ON \"emp\".\"deptno\" = \"dept\".\"deptno\"",
      "google bigquery | select * from scott.emp left join scott.dept on emp.deptno = dept.deptno | " +
         "select * from `scott.emp` LEFT OUTER JOIN `scott.dept` ON emp.deptno = dept.deptno",
      // quoted names are case-sensitive, \"A\" and a are two tables. The where clause
      // join stays in the where clause (#77475)
      "postgresql | select * from \"A\", a left join c on c.id = a.id where \"A\".id = a.id | " +
         "select * from \"a\" LEFT OUTER JOIN \"c\" ON \"a\".\"id\" = \"c\".\"id\" , \"A\" " +
         "where \"A\".\"id\" = \"a\".\"id\"",
      // an inner join ON of a quoted table is generated in the from clause, as a where
      // condition it would drop the rows of c that the right join keeps
      "postgresql | select * from \"My A\" join b on \"My A\".id = b.id right join c " +
         "on c.id = b.id | select * from (\"My A\" INNER JOIN \"b\" ON \"My A\".\"id\" = \"b\".\"id\" ) " +
         "RIGHT OUTER JOIN \"c\" ON \"b\".\"id\" = \"c\".\"id\"",
      // a join to a table outside the from clause that isn't an outer join stays in the
      // where clause of its query (#77480)
      "h2 | select * from (select c.id from c left join d on d.id = c.id where c.k = a.k) t, a | " +
         "select * from ( select c.id from c LEFT OUTER JOIN d ON c.id = d.id where c.k = a.k) t, a",
      "h2 | select * from a left join b on b.id = a.id where a.x = zz.y | " +
         "select * from a LEFT OUTER JOIN b ON a.id = b.id where a.x = zz.y"
   })
   void generatesSql(String type, String text, String expected) throws Exception {
      JDBCDataSource ds = dataSource(type, null, null);
      assertEquals(expected, normalize(parseStructure(text, type, ds).getSQLString()));
   }

   // a bare table name resolves to the one unaliased schema table with that last segment
   @Test
   void bareNameResolvesToSingleUnaliasedSchemaTable() {
      UniformSQL sql = new UniformSQL();
      sql.addTable("scott.emp");
      sql.addTable("\"scott\".\"dept\"");
      sql.addTable("e", "hr.emp2");
      assertEquals(0, sql.getJoinTableIndex("emp"));
      assertEquals(0, sql.getJoinTableIndex("EMP"));
      assertEquals(1, sql.getJoinTableIndex("dept"));
      assertEquals(1, sql.getJoinTableIndex("\"dept\""));
      // an aliased table is only referred to by its alias
      assertEquals(-1, sql.getJoinTableIndex("emp2"));
      assertEquals(2, sql.getJoinTableIndex("e"));
      // a qualified table part is not a bare name
      assertEquals(-1, sql.getJoinTableIndex("x.emp"));

      // ambiguous, two unaliased tables named emp
      UniformSQL ambiguous = new UniformSQL();
      ambiguous.addTable("s1.emp");
      ambiguous.addTable("s2.emp");
      assertEquals(-1, ambiguous.getJoinTableIndex("emp"));

      // a case-sensitive match is preferred over a case-insensitive one
      UniformSQL cased = new UniformSQL();
      cased.addTable("s1.EMP");
      cased.addTable("s2.emp");
      assertEquals(1, cased.getJoinTableIndex("emp"));
      assertEquals(0, cased.getJoinTableIndex("EMP"));
      assertEquals(-1, cased.getJoinTableIndex("Emp"));
   }

   private static JDBCDataSource dataSource(String type, String driver, String url) {
      boolean ansi = type.endsWith(" ansi");
      String product = ansi ? type.substring(0, type.length() - 5) : type;

      if(driver == null) {
         Arguments args = dataSources().filter(a -> a.get()[0].equals(type)).findFirst().get();
         driver = (String) args.get()[1];
         url = (String) args.get()[2];
      }

      JDBCDataSource ds = new JDBCDataSource();
      ds.setAnsiJoin(ansi);
      ds.setName("ds");
      ds.setDriver(driver);
      ds.setURL(url);
      // a version so the helper lookup doesn't query the database
      ds.setProductVersion("19.0");
      assertEquals(product, SQLHelper.getProductName(ds));
      return ds;
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   /**
    * Parse sql with a data source. A where clause outer join (*=, =* or (+)) is refused with a
    * data source that writes ANSI joins, every one but oracle without ansi join (Bug #77548),
    * so its structure is parsed without the data source, which is set afterwards, as for sql
    * parsed without one.
    */
   private static UniformSQL parseStructure(String text, String type, JDBCDataSource ds)
      throws Exception
   {
      if(!isWhereOuterJoinRefused(type, text)) {
         return parse(text, ds);
      }

      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text, ds));
      assertTrue(ex.getMessage().contains("Unsupported outer join in the where clause"),
                 ex.getMessage());
      UniformSQL sql = parse(text, null);
      sql.setDataSource(ds);
      return sql;
   }

   private static boolean isWhereOuterJoinRefused(String type, String text) {
      return !"oracle".equals(type) &&
         (text.contains("(+)") || text.contains("*=") || text.contains("=*"));
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   @Configuration
   static class DataSourceConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean()))
            .thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }

      @Bean
      public Config config() {
         return new Config(mock(Plugins.class));
      }
   }
}
