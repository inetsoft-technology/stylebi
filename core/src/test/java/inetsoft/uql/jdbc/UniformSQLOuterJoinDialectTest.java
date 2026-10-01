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

   static final String[] ACCEPTED = {
      "select a.x from a left join b on b.id = a.id",
      "select a.x from a right join b on b.id = a.id",
      "select a.x from a left join b on b.id = a.id and b.k = a.k",
      "select Ab.x from Ab left join Bc on Bc.id = Ab.id",
      "select t1.x from a t1 left join b t2 on t2.id = t1.id",
      "select sch.a.id from sch.a left join sch.b on sch.b.id = sch.a.id",
      "select a.x from a left join b on a.k = b.k left join c on c.id = b.id",
      "select a.x from a left join (b left join c on c.id = b.id) on b.id = a.id",
      "select a.x from \"a\" left join \"b\" on \"b\".\"id\" = \"a\".\"id\"",
      "select * from \"My A\" left join b on b.id = \"My A\".id"
   };

   static final String[] REFUSED = {
      "select a.x from a left join b on a.k = b.k left join c on a.id = b.id",
      "select * from a left join b on id = bid",
      "select * from a left join b on b.id = zz.id"
   };

   // a query with an outer join that joins a table outside its from clause, the
   // generated from clause would join the outer table into the subquery
   static final String[] OUTSIDE_TABLE_REFUSED = {
      "select * from a where exists (select 1 from c left join d on d.id = c.id where c.id = a.id)",
      "select * from a where not exists (select 1 from c left join d on d.id = c.id " +
         "where a.id = c.id and d.k = 1)",
      "select * from a where a.id in (select c.id from c left join d on d.id = c.id where c.k = a.k)",
      "select * from a where exists (select 1 from c right join d on d.id = c.id where d.id = a.id)",
      "select * from a where exists (select 1 from c, d where c.id = d.id(+) and c.id = a.id)",
      "select * from (select c.id from c left join d on d.id = c.id where c.k = a.k) t, a",
      "select * from a left join b on b.id = a.id where a.x = zz.y"
   };

   // a correlated subquery without an outer join keeps its joins in the where clause
   static final String[] CORRELATED_ACCEPTED = {
      "select * from a where exists (select 1 from c where c.id = a.id)",
      "select * from a where exists (select 1 from c, d where c.id = d.id and c.id = a.id)",
      "select * from a where exists (select 1 from c join d on d.id = c.id where c.id = a.id)",
      "select * from a where a.id in (select c.id from c where c.k = a.k)",
      "select * from a left join b on b.id = a.id where exists (select 1 from c where c.id = b.id)"
   };

   static Stream<Arguments> outsideTableRefusedCases() {
      return cases(OUTSIDE_TABLE_REFUSED);
   }

   // a data source with ansi joins generates every join of the subquery in its from
   // clause, which loses the correlation (a separate, existing problem), so only the
   // data sources that keep the subquery's joins in its where clause are checked
   static Stream<Arguments> correlatedAcceptedCases() {
      return cases(CORRELATED_ACCEPTED).filter(a -> !((String) a.get()[0]).endsWith(" ansi"));
   }

   static Stream<Arguments> acceptedCases() {
      return cases(ACCEPTED);
   }

   static Stream<Arguments> refusedCases() {
      return cases(REFUSED);
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
      UniformSQL sql = parse(text, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());

      for(XJoin join : sql.getJoins()) {
         int index1 = sql.getJoinTableIndex(join.getTable1(sql));
         int index2 = sql.getJoinTableIndex(join.getTable2(sql));
         assertTrue(index1 >= 0 && index2 >= 0 && index1 != index2, join.toString());
      }

      // the generated sql lists each table once and parses back to itself
      String generated = normalize(sql.getSQLString());
      UniformSQL reparsed = parse(generated, ds);
      assertEquals(sql.getTableCount(), reparsed.getTableCount(), generated);
      assertEquals(generated, normalize(reparsed.getSQLString()));
   }

   @ParameterizedTest(name = "{0}: {3}")
   @MethodSource("refusedCases")
   void outerJoinNotOnJoinedTableFails(String type, String driver, String url, String text) {
      JDBCDataSource ds = dataSource(type, driver, url);
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text, ds));
      assertTrue(ex.getMessage().contains("Unsupported outer join condition"), ex.getMessage());
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
      // quoted names are case-sensitive, \"A\" and a are two tables
      "postgresql | select * from \"A\", a left join c on c.id = a.id where \"A\".id = a.id | " +
         "select * from (\"A\" INNER JOIN \"a\" ON \"A\".\"id\" = \"a\".\"id\" ) " +
         "LEFT OUTER JOIN \"c\" ON \"a\".\"id\" = \"c\".\"id\""
   })
   void generatesSql(String type, String text, String expected) throws Exception {
      JDBCDataSource ds = dataSource(type, null, null);
      assertEquals(expected, normalize(parse(text, ds).getSQLString()));
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
