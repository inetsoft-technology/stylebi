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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #77481, a legacy outer join ((+), *= or =*) that compares with an op other than =, or that is
 * under a NOT or an OR set, can't be represented: the outer join is generated in the
 * from clause as an "=" join, which lost the op ("a.id >= b.id(+)" became
 * "ON a.id = b.id") or took the join out of the NOT/OR ("not (a.id = b.id(+) and
 * a.k = 1)" became "ON a.id = b.id where not (a.k = 1)"). These fail the parse, so the
 * original sql runs. A negated outer join itself is kept: Oracle applies
 * "not (a.id = b.id(+))" as the join condition, which returns the same rows as the
 * generated "ON a.id <> b.id" (compared on Oracle 23 Free).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  UniformSQLOuterJoinNotOpTest.DataSourceConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOuterJoinNotOpTest {
   @ParameterizedTest
   @ValueSource(strings = {
      // reporter's example
      "select e.ename from emp e, dept d where e.deptno >= d.deptno(+)",
      // other comparison ops with (+)
      "select a.x from a, b where a.id > b.id(+)",
      "select a.x from a, b where a.id < b.id(+)",
      "select a.x from a, b where a.id <= b.id(+)",
      "select a.x from a, b where a.id <> b.id(+)",
      "select a.x from a, b where a.id != b.id(+)",
      "select a.x from a, b where a.id = b.id(+) and a.k >= b.k(+)",
      // an outer join under a NOT or an OR
      "select a.x from a, b where not (a.id = b.id(+) and a.k = 1)",
      "select a.x from a, b where a.k = 1 or a.id = b.id(+)",
      "select a.x from a, b where a.id *= b.id or a.k = 1",
      "select a.x from a, b where a.k = 1 and (a.id = b.id(+) or a.k = 2)",
      "select a.x from a, b, c where a.id = b.id(+) and (a.cid = c.id(+) or a.k = 2)",
      // an OR of outer joins between different tables (ORA-01719), of different types,
      // or next to another join between the same tables, which the ON would lose
      "select a.x from a, b, c where a.id = b.id(+) or a.id = c.id(+)",
      "select a.x from a, b where a.id = b.id(+) or a.k (+)= b.k",
      "select a.x from a, b where a.id = b.id(+) and (a.k = b.k(+) or a.j = b.j(+))",
      "select a.x from a, b where a.id = b.id and (a.k = b.k(+) or a.j = b.j(+))",
      // subqueries and derived tables are checked at their own level
      "select a.x from a where exists (select 1 from c, d where not (c.id = d.id(+) or c.k = 1))",
      "select a.x from a where a.k in (select c.k from c, d where c.id >= d.id(+))",
      "select * from (select a.id from a, b where a.k = 1 or a.id = b.id(+)) t",
   })
   void unsupportedOuterJoinFailsCleanly(String text) {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text));
      assertTrue(ex.getMessage().contains("Unsupported outer join condition"), ex.getMessage());

      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      assertEquals(text, sql.getSQLString());
      assertTrue(sql.isLossy());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.x from a, b where a.id = b.id(+) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id",
      "select a.x from a, b where a.id (+)= b.id | " +
         "select a.x from a RIGHT OUTER JOIN b ON a.id = b.id",
      "select a.x from a, b where (a.id = b.id(+)) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id",
      "select a.x from a, b where a.id = b.id(+) and a.k = b.k(+) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id AND a.k = b.k",
      "select a.x from a, b where a.id = b.id(+) and not (a.k = 1) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id where not (a.k = 1)",
      "select a.x from a, b where a.id = b.id(+) and (a.k = 1 or a.k = 2) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id where (a.k = 1 or a.k = 2)",
      "select a.x from a, b, c where a.id = b.id(+) and a.cid = c.id | " +
         "select a.x from (a INNER JOIN c ON a.cid = c.id ) LEFT OUTER JOIN b ON a.id = b.id",
      "select a.x from a left join b on a.id = b.id | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id",
   })
   void supportedOuterJoinRegeneratesUnchanged(String text, String expected) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      String generated = normalize(sql.getSQLString());
      assertEquals(expected, generated);

      // round trip: the regenerated sql parses and regenerates to itself
      UniformSQL sql2 = parse(generated);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql2.getParseResult());
      assertEquals(generated, normalize(sql2.getSQLString()));
   }

   /**
    * Oracle applies an OR of outer joins between the same two tables as one join
    * condition, which is the generated "ON a.id = b.id OR a.k = b.k" (compared on Oracle
    * 23 Free). Without the ANSI option the Oracle helper keeps the (+) joins. With it, the
    * sql is refused (Bug #77548), and the structure parsed without the data source keeps
    * the case of the select list, which the Oracle helper's parse changes.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "true | select a.x from a, b where a.id = b.id(+) or a.k = b.k(+) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id OR a.k = b.k",
      "true | select a.x from a, b where a.f = 1 and (a.id = b.id(+) or a.k = b.k(+)) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id OR a.k = b.k where a.f = 1",
      "false | select a.x from a, b where a.id = b.id(+) or a.k = b.k(+) | " +
         "select A.X from a, b where (a.id = b.id(+) or a.k = b.k(+) )",
   })
   void orOfOuterJoinsIsJoinCondition(boolean ansi, String text, String expected)
      throws Exception
   {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(oracle(ansi));

      // a where clause outer join is refused with ansi join (Bug #77548), so its structure is
      // parsed without the data source, and generated with it
      if(ansi) {
         UniformSQL refused = sql;
         assertThrows(antlr.SemanticException.class, () -> refused.parse(
            text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD));
         sql = new UniformSQL();
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      sql.setDataSource(oracle(ansi));
      assertEquals(expected, normalize(sql.getSQLString()));
   }

   /**
    * A negated outer join is kept and generated as the negated join condition, which is
    * what Oracle does with "not (a.id = b.id(+))". The generated "ON a.id <> b.id" doesn't
    * parse again, since an outer join ON must be column = column (#77410), so it then
    * runs as written. Both return the same rows.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select e.ename from emp e, dept d where not (e.deptno = d.deptno(+)) | " +
         "select e.ename from emp e LEFT OUTER JOIN dept d ON e.deptno <> d.deptno",
      "select a.x from a, b where not a.id = b.id(+) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id <> b.id",
      "select a.x from a, b where a.k = 1 and not (a.id = b.id(+)) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id <> b.id where a.k = 1",
      "select a.x from a left join b on not (a.id = b.id) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id <> b.id",
   })
   void negatedOuterJoinIsJoinCondition(String text, String expected) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      String generated = normalize(sql.getSQLString());
      assertEquals(expected, generated);

      UniformSQL sql2 = new UniformSQL();
      new SQLProcessor(sql2).parse(generated);
      assertEquals(UniformSQL.PARSE_FAILED, sql2.getParseResult());
      assertEquals(generated, sql2.getSQLString());
   }

   // NOT and other ops on inner joins and on plain conditions are not affected
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.x from a, b where not (a.id = b.id) | not (a.id = b.id)",
      "select a.x from a, b where a.id >= b.id | a.id >= b.id",
      // getJoins() doesn't collect a join under an OR
      "select a.x from a, b where a.id = b.id or a.k = 1 | ",
      "select a.x from a join b on not (a.id = b.id) | not (a.id = b.id)",
   })
   void innerJoinIsAccepted(String text, String expected) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals(expected == null ? "" : expected, Arrays.stream(sql.getJoins()).map(Object::toString)
         .collect(Collectors.joining(", ")));
   }

   /**
    * The Oracle non-ANSI generator renders any (+) op as "=", so a non-= op is refused
    * there as well, while a plain and a negated (+) join are kept.
    */
   @Test
   void oracleNonAnsi() throws Exception {
      JDBCDataSource ds = oracle(false);

      for(String text : new String[] {
         "select e.ename from emp e, dept d where e.deptno >= d.deptno(+)",
         "select e.ename from emp e, dept d where not (e.deptno = d.deptno(+) and e.ename = 'A')",
      })
      {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
         assertEquals(text, sql.getSQLString());
      }

      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(
         "select e.ename from emp e, dept d where e.deptno = d.deptno(+)");
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals("select e.ename from emp e, dept d where e.deptno = d.deptno(+)",
                   normalize(sql.getSQLString()));

      sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(
         "select e.ename from emp e, dept d where not (e.deptno = d.deptno(+))");
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals("select e.ename from emp e, dept d where not (e.deptno = d.deptno(+))",
                   normalize(sql.getSQLString()));
   }

   private static JDBCDataSource oracle(boolean ansi) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setAnsiJoin(ansi);
      ds.setName("ds");
      ds.setDriver("oracle.jdbc.OracleDriver");
      ds.setURL("jdbc:oracle:thin:@localhost:1521:db");
      // a version so the helper lookup doesn't query the database
      ds.setProductVersion("19.0");
      assertEquals("oracle", SQLHelper.getProductName(ds));
      return ds;
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
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
