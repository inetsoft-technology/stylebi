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
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77509, the parser records a column comparison written with != as a join with the op
 * "!=", which had no ANSI join type. An inner join ON != (or not (.. != ..)) on the
 * null-supplying side of an outer join was moved to WHERE, which removes the null-extended
 * rows. A != join in an ON must be generated like the same join written with &lt;&gt;, and a
 * != in WHERE stays in WHERE.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class SQLHelperNotEqualJoinTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the derby helper asks the repository for the database version
      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String SEL = "select a.id ai, a.k ak, b.id bi, b.k bk, c.id ci, c.k ck ";
   private static final String GEN_SEL =
      "select a.id as ai, a.k as ak, b.id as bi, b.k as bk, c.id as ci, c.k as ck ";
   private static final String SEL2 = "select a.id ai, a.k ak, b.id bi, b.k bk ";

   // the defect: an inner join ON != on the null-supplying side of a RIGHT or nested outer
   // join, and its negation. The ANSI option doesn't matter since the outer join forces ANSI.
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a left join (b join c on b.id = c.id and b.k != c.k) on a.id = b.id|" +
         "from (b INNER JOIN c ON b.id = c.id AND b.k != c.k ) RIGHT OUTER JOIN a ON " +
         "a.id = b.id",
      "from a join c on a.id = c.id and a.k != c.k right join b on a.id = b.id|" +
         "from (a INNER JOIN c ON a.id = c.id AND a.k != c.k ) RIGHT OUTER JOIN b ON " +
         "a.id = b.id",
      "from a left join (b join c on b.id = c.id and not (b.k != c.k)) on a.id = b.id|" +
         "from (b INNER JOIN c ON b.id = c.id AND b.k = c.k ) RIGHT OUTER JOIN a ON " +
         "a.id = b.id",
      "from a join c on a.id = c.id and not (a.k != c.k) right join b on a.id = b.id|" +
         "from (a INNER JOIN c ON a.id = c.id AND a.k = c.k ) RIGHT OUTER JOIN b ON " +
         "a.id = b.id",
   })
   void innerOnNotEqualOnNullSupplyingSide(String tail, String expected) throws Exception {
      for(String type : new String[] { "h2", "h2-ansi", "derby", "derby-ansi", "oracle-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(SEL + tail, ds);
         assertEquals(expected, from(generated), type);
         assertRoundTrip(generated, ds);
      }

      assertEquals(0, RowCompare.diffCount(SEL + tail, generate(SEL + tail, dataSource("derby")),
                                           120), tail);
   }

   // the operand swap of the nested round trip is the same with = only, so it's not caused
   // by the != join
   @Test
   void nestedRoundTripSwapsOuterOperands() throws Exception {
      JDBCDataSource ds = dataSource("h2");
      String generated = generate(SEL + "from a left join (b join c on b.id = c.id) on " +
                                     "a.id = b.id", ds);
      assertEquals("from (b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON a.id = b.id",
                   from(generated));
      assertEquals("from (b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON b.id = a.id",
                   from(generate(generated, ds)));
   }

   // an ON != of a query in text join order (parsed, with an outer join) is generated like the
   // same join written with <> (the op itself is kept)
   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "from a left join (b join c on b.id = c.id and b.k != c.k) on a.id = b.id",
      SEL + "from a join c on a.id = c.id and a.k != c.k right join b on a.id = b.id",
      SEL + "from a left join (b join c on b.id = c.id and not (b.k != c.k)) on a.id = b.id",
      SEL + "from a left join b on a.id = b.id join c on a.id = c.id and a.k != c.k",
      SEL + "from a left join b on a.id = b.id join c on b.id = c.id and b.k != c.k",
      SEL + "from a right join b on a.id = b.id join c on b.id = c.id and a.k != c.k",
   })
   void onNotEqualLikeLessGreater(String text) throws Exception {
      for(String type : new String[] { "h2", "h2-ansi", "derby-ansi", "postgresql-ansi",
                                       "oracle-ansi" })
      {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(text, ds);
         String twin = generate(text.replace("!=", "<>"), ds);
         assertEquals(twin.replace("<>", "!="), generated, type);
         assertRoundTrip(generated, ds);
      }
   }

   // without text join order (no outer join with the ANSI option) a != is
   // written in place as before. Moving it into the from clause there joins c twice for the
   // 3-table shapes (as <> does), and with no outer join WHERE and ON are the same rows
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "h2-ansi|from a join b on a.id = b.id join c on b.id = c.id and a.k != c.k|" +
         "from (a INNER JOIN b ON a.id = b.id ) INNER JOIN c ON b.id = c.id where a.k != c.k",
      "derby-ansi|from a join b on a.id = b.id join c on b.id = c.id and a.k != c.k|" +
         "from (a INNER JOIN b ON a.id = b.id ) INNER JOIN c ON b.id = c.id where a.k != c.k",
      "h2-ansi|from a join b on a.k != b.k|from a, b where a.k != b.k",
      "h2-ansi|from a join b on a.id = b.id and not (a.k != b.k)|" +
         "from a INNER JOIN b ON a.id = b.id where not (a.k != b.k)",
      "mongo-ansi|from a join b on a.id = b.id join c on b.id = c.id and a.k != c.k|" +
         "from a INNER JOIN b ON a.id = b.id INNER JOIN c ON b.id = c.id where a.k != c.k",
   })
   void notEqualInPlaceWithoutTextJoinOrder(String type, String tail, String expected)
      throws Exception
   {
      JDBCDataSource ds = dataSource(type);
      String text = select(tail) + tail;
      String generated = generate(text, ds);
      assertEquals(expected, from(generated), type);
      assertRoundTrip(generated, ds);
      assertEquals(0, RowCompare.diffCount(text, generated, 120), type + ": " + tail);
   }

   // a query saved before the join clause was recorded loads its joins with no clause. Its
   // != is written in place as before, so a WHERE != doesn't join c twice
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "h2-ansi|from a, b, c where a.id = b.id and b.id = c.id and a.k != c.k|" +
         "from (a INNER JOIN b ON a.id = b.id ) INNER JOIN c ON b.id = c.id where a.k != c.k",
      "h2|from a left join b on a.id = b.id join c on a.id = c.id where b.k != c.k|" +
         "from (a INNER JOIN c ON a.id = c.id ) LEFT OUTER JOIN b ON a.id = b.id where b.k != c.k",
      "h2-ansi|from a left join b on a.id = b.id join c on a.id = c.id where not (b.k != c.k)|" +
         "from (a INNER JOIN c ON a.id = c.id ) LEFT OUTER JOIN b ON a.id = b.id where " +
         "not (b.k != c.k)",
      "derby|from a right join b on a.id = b.id join c on b.id = c.id and a.k != c.k|" +
         "from (a RIGHT OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON b.id = c.id where a.k != c.k",
      "mongo|from a left join b on a.id = b.id join c on a.id = c.id where b.k != c.k|" +
         "from a INNER JOIN c ON a.id = c.id LEFT OUTER JOIN b ON a.id = b.id where b.k != c.k",
      "h2-ansi|from a, b where a.k != b.k|from a, b where a.k != b.k",
      "h2-ansi|from a join b on a.id = b.id and not (a.k != b.k)|" +
         "from a INNER JOIN b ON a.id = b.id where not (a.k != b.k)",
   })
   void savedWithoutJoinClause(String type, String tail, String expected) throws Exception {
      JDBCDataSource ds = dataSource(type);
      String text = select(tail) + tail;
      String generated = generateSaved(text, ds);
      assertEquals(expected, from(generated), type);
      assertEquals(0, RowCompare.diffCount(text, generated, 120), type + ": " + tail);
   }

   // the reporter's two cases, already right on main since #6064/#6080: a WHERE != stays a
   // WHERE condition, with and without the ANSI option, on every helper
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a left join b on a.id = b.id where a.k != b.k|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where a.k != b.k",
      "from a, b where a.k != b.k|from a, b where a.k != b.k",
      "from a, b where a.id = b.id and a.k != b.k|" +
         "from a INNER JOIN b ON a.id = b.id where a.k != b.k",
      "from a inner join b on a.id = b.id where a.k != b.k|" +
         "from a INNER JOIN b ON a.id = b.id where a.k != b.k",
      "from a, b where not (a.k != b.k)|from a, b where not (a.k != b.k)",
   })
   void reporterCasesStayInWhere(String tail, String expected) throws Exception {
      for(String type : new String[] { "h2-ansi", "derby-ansi", "mongo-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(SEL2 + tail, ds);
         assertEquals(expected, from(generated), type);
         assertRoundTrip(generated, ds);
      }

      for(String type : new String[] { "derby", "derby-ansi" }) {
         assertEquals(0, RowCompare.diffCount(SEL2 + tail, generate(SEL2 + tail,
                                                                   dataSource(type)), 120),
                      type + ": " + tail);
      }
   }

   // a WHERE != stays in WHERE next to WHERE-syntax outer joins (*= and Oracle (+)), which
   // turn off the text join order. Such sql is refused with a data source that writes ANSI
   // joins (Bug #77548), so the structure is parsed without one, and regenerated with it
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a, b where a.id *= b.id and a.k != b.k|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where a.k != b.k",
      "from a, b where a.id = b.id(+) and a.k != b.k|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where a.k != b.k",
   })
   void whereOuterJoinSyntax(String tail, String expected) throws Exception {
      for(String type : new String[] { "h2", "h2-ansi", "derby-ansi", "oracle-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL refused = new UniformSQL();
         refused.setDataSource(ds);
         new SQLProcessor(refused).parse(SEL2 + tail);
         assertEquals(UniformSQL.PARSE_FAILED, refused.getParseResult(), type);

         String generated = generateParsedWithoutSource(SEL2 + tail, ds);
         assertEquals(expected, from(generated), type);
         // the Oracle helper changes the case of the select list when it parses, so its round
         // trip starts from the second generation
         assertRoundTrip("oracle-ansi".equals(type) ? generate(generated, ds) : generated, ds);
      }

      String generated = generateParsedWithoutSource(SEL2 + tail, dataSource("postgresql"));
      assertEquals("from \"a\" LEFT OUTER JOIN \"b\" ON \"a\".\"id\" = \"b\".\"id\" where " +
                      "\"a\".\"k\" != \"b\".\"k\"",
                   from(generated));
      assertEquals(0, RowCompare.diffCount(
         SEL2 + "from a left join b on a.id = b.id where a.k != b.k",
         generateParsedWithoutSource(SEL2 + tail, dataSource("derby")), 120), tail);
   }

   // Oracle without the ANSI option writes (+) and the != in WHERE, unchanged. Parsed without
   // the data source as before, since the Oracle helper changes the case of the select list
   // when it parses, and these shapes have no join order check
   @Test
   void oracleNonAnsiUnchanged() throws Exception {
      assertEquals("select a.id as \"ai\", a.k as \"ak\", b.id as \"bi\", b.k as \"bk\" from " +
                      "a, b where a.id = b.id(+) and a.k != b.k",
                   generateParsedWithoutSource(
                      SEL2 + "from a left join b on a.id = b.id where a.k != b.k",
                      dataSource("oracle")));
      assertEquals("select a.id as \"ai\", a.k as \"ak\", b.id as \"bi\", b.k as \"bk\" from " +
                      "a, b where a.id = b.id and a.k != b.k",
                   generateParsedWithoutSource(
                      SEL2 + "from a join b on a.id = b.id and a.k != b.k",
                      dataSource("oracle")));
   }

   // Bug #77434: a negated comparison is the comparison with the negated op (not (x != y) is
   // x = y), also when x or y is null: both are unknown then, so they're the same in WHERE, in
   // an OR, and under another NOT. The join order check compares them that way
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "!=|=", "<>|=", "=|<>", "<|>=", "<=|>", ">|<=", ">=|<",
   })
   void negatedComparisonSameAsNegatedOp(String op, String negated) throws Exception {
      Driver driver = (Driver) Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver")
         .getDeclaredConstructor().newInstance();
      Integer[] values = { null, 0, 1, 2 };

      try(Connection con = driver.connect("jdbc:derby:memory:not77434;create=true",
                                          new Properties());
          Statement st = con.createStatement())
      {
         try {
            st.execute("drop table t");
         }
         catch(SQLException ignore) {
         }

         st.execute("create table t (x int, y int)");

         for(Integer x : values) {
            for(Integer y : values) {
               st.execute("insert into t values (" + x + ", " + y + ")");
            }
         }

         String[][] pairs = {
            { "not (x " + op + " y)", "x " + negated + " y" },
            { "not (y " + op + " x)", "y " + negated + " x" },
            { "not (x " + op + " y) or x is null", "x " + negated + " y or x is null" },
            { "not (x " + op + " y) or y = 1", "x " + negated + " y or y = 1" },
            { "not (not (x " + op + " y) and y = 1)", "not (x " + negated + " y and y = 1)" },
            { "not (not (x " + op + " y))", "not (x " + negated + " y)" },
         };

         for(String[] pair : pairs) {
            List<String> rows = RowCompare.rows(con, "select x, y from t where " + pair[0]);
            assertEquals(rows, RowCompare.rows(con, "select x, y from t where " + pair[1]),
                         pair[0]);
            assertNotEquals(rows, RowCompare.rows(con, "select x, y from t"), pair[0]);
         }
      }
   }

   // a negated join on the null-supplying side of a nested or RIGHT outer join is regenerated
   // with the negated op and accepted, with the same rows as the original
   @ParameterizedTest
   @ValueSource(strings = {
      "from a left join (b join c on b.id = c.id and not (b.k = c.k)) on a.id = b.id",
      "from a left join (b join c on b.id = c.id and not (b.k < c.k)) on a.id = b.id",
      "from a left join (b join c on b.id = c.id and not (c.k <= b.k)) on a.id = b.id",
      "from a left join (b join c on b.id = c.id and not (c.k != b.k)) on a.id = b.id",
      "from a left join (b join c on b.id = c.id and not (b.k <> c.k)) on a.id = b.id",
      "from a left join (b join c on b.id = c.id and not (not (b.k = c.k))) on a.id = b.id",
      "from a join c on a.id = c.id and not (c.k <> a.k) right join b on a.id = b.id",
      "from a join c on a.id = c.id and not (a.k > c.k) right join b on a.id = b.id",
   })
   void negatedJoinOnNullSupplyingSide(String tail) throws Exception {
      for(String type : new String[] { "h2", "h2-ansi", "derby", "derby-ansi", "oracle-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(SEL + tail, ds);
         assertFalse(generated.toLowerCase().contains("not "), type + ": " + generated);
         assertRoundTrip(generated, ds);
      }

      assertEquals(0, RowCompare.diffCount(SEL + tail, generate(SEL + tail, dataSource("derby")),
                                           120), tail);
   }

   // a negated join is never compared as the join without the not, and a negation the key
   // can't move into the op (of an OR, or of an AND group) keeps the query refused
   @Test
   void negatedJoinStructure() throws Exception {
      String nested = SEL + "from a left join (b join c on b.id = c.id and %s) on a.id = b.id";
      assertEquals(structure(String.format(nested, "b.k = c.k")),
                   structure(String.format(nested, "not (b.k != c.k)")));
      assertEquals(structure(String.format(nested, "b.k = c.k")),
                   structure(String.format(nested, "not (c.k <> b.k)")));
      assertEquals(structure(String.format(nested, "b.k <> c.k")),
                   structure(String.format(nested, "not (b.k = c.k)")));
      assertEquals(structure(String.format(nested, "c.k <= b.k")),
                   structure(String.format(nested, "not (b.k < c.k)")));
      assertEquals(structure(String.format(nested, "b.k = c.k")),
                   structure(String.format(nested, "not (not (b.k = c.k))")));
      assertNotEquals(structure(String.format(nested, "b.k = c.k")),
                      structure(String.format(nested, "not (b.k = c.k)")));
      assertNotEquals(structure(String.format(nested, "b.k != c.k")),
                      structure(String.format(nested, "not (b.k != c.k)")));
      assertNotEquals(structure(String.format(nested, "b.k < c.k")),
                      structure(String.format(nested, "not (b.k < c.k)")));

      for(String cond : new String[] { "(not (b.k != c.k) or b.k = 1)",
                                       "not (b.k != c.k or b.j = c.j)",
                                       "not (b.k != c.k and b.j = c.j)" })
      {
         for(String type : new String[] { "h2-ansi", "derby", "oracle-ansi" }) {
            UniformSQL sql = new UniformSQL();
            sql.setDataSource(dataSource(type));
            assertThrows(Exception.class, () -> sql.parse(
               String.format(nested, cond), UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD),
                         type + ": " + cond);
         }
      }
   }

   // not (not (..)) is the condition itself, it was parsed as not (..)
   @ParameterizedTest
   @ValueSource(strings = {
      "from a, b where not (not (a.k = b.k))",
      "from a join b on a.id = b.id and not (not (a.k != b.k))",
      "from a, b where not (not (a.k = 1 or b.k = 1))",
      "from a, b where not (not (a.k is null))",
      "from a, b where not (a.k not in (1, 2))",
   })
   void doubleNegation(String tail) throws Exception {
      for(String type : new String[] { "derby", "derby-ansi" }) {
         assertEquals(0, RowCompare.diffCount(SEL2 + tail, generate(SEL2 + tail, dataSource(type)),
                                              120), type + ": " + tail);
      }
   }

   // the join structure of a query, as the join order check compares it
   private static String structure(String text) throws Exception {
      inetsoft.uql.util.sqlparser.SQLParser parser = new inetsoft.uql.util.sqlparser.SQLParser(
         new inetsoft.uql.util.sqlparser.SQLLexer(new StringReader(text)));
      parser.setTime(UniformSQL.PARSE_PERIOD);
      UniformSQL sql = new UniformSQL();
      parser.direct_select_stmt_n_rows(sql);
      return parser.getJoinStructure(sql);
   }

   // the other ON != shapes return the same rows as the original
   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "from a left join b on a.id = b.id join c on a.id = c.id and a.k != c.k",
      SEL + "from a left join b on a.id = b.id join c on b.id = c.id and b.k != c.k",
      SEL2 + "from a join b on a.k != b.k",
      SEL2 + "from a join b on a.id = b.id and not (a.k != b.k)",
   })
   void sameRowsOnDerby(String text) throws Exception {
      for(String type : new String[] { "derby", "derby-ansi" }) {
         assertEquals(0, RowCompare.diffCount(text, generate(text, dataSource(type)), 120),
                      type + ": " + text);
      }
   }

   // a != mixed with <> and = in one ON keeps every predicate in that ON
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a left join (b join c on b.id = c.id and b.k != c.k and b.j <> c.j) on a.id = b.id|" +
         "from (b INNER JOIN c ON b.id = c.id AND b.k != c.k AND b.j <> c.j ) RIGHT OUTER " +
         "JOIN a ON a.id = b.id",
      "from a join c on a.id = c.id and a.k <> c.k and a.j != c.j right join b on a.id = b.id|" +
         "from (a INNER JOIN c ON a.id = c.id AND a.k <> c.k AND a.j != c.j ) RIGHT OUTER " +
         "JOIN b ON a.id = b.id",
      "from a left join b on a.id = b.id join c on b.id = c.id and b.k != c.k and b.j <> c.j|" +
         "from (a LEFT OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON b.id = c.id AND " +
         "b.k != c.k AND b.j <> c.j",
   })
   void mixedNotEqualOps(String tail, String expected) throws Exception {
      for(String type : new String[] { "h2", "h2-ansi", "derby", "derby-ansi", "oracle-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(SEL + tail, ds);
         assertEquals(expected, from(generated), type);
         assertRoundTrip(generated, ds);
      }

      assertEquals(0, RowCompare.diffCount(SEL + tail, generate(SEL + tail, dataSource("derby")),
                                           120), tail);
   }

   // an inner join ON != next to a FULL join stays in the inner join's ON, where a WHERE would
   // remove the null-extended rows. Derby has no FULL join, so only the text is checked
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a full join b on a.id = b.id join c on b.id = c.id and b.k != c.k|" +
         "from (a FULL OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON b.id = c.id AND b.k != c.k",
      "from a join c on a.id = c.id and a.k != c.k full join b on a.id = b.id|" +
         "from (a INNER JOIN c ON a.id = c.id AND a.k != c.k ) FULL OUTER JOIN b ON a.id = b.id",
      "from a full join (b join c on b.id = c.id and b.k != c.k) on a.id = b.id|" +
         "from (b INNER JOIN c ON b.id = c.id AND b.k != c.k ) FULL OUTER JOIN a ON a.id = b.id",
   })
   void fullJoinOnNotEqual(String tail, String expected) throws Exception {
      for(String type : new String[] { "h2", "h2-ansi", "oracle", "oracle-ansi" }) {
         assertEquals(expected, from(generate(SEL + tail, dataSource(type))), type);
      }
   }

   // the select list for the tables of a from clause
   private static String select(String tail) {
      return tail.contains(" c ") ? SEL : SEL2;
   }

   // the from and where clauses, without the select list (Oracle quotes the aliases)
   private static String from(String generated) {
      return generated.substring(generated.indexOf(" from ") + 1);
   }

   private static JDBCDataSource dataSource(String type) {
      return RowCompare.dataSource(type);
   }

   static String generate(String text, JDBCDataSource ds) throws Exception {
      // parse with the data source, as a query of the data source is parsed, since a
      // RIGHT/FULL join mixed with an inner join, or a nested join on the right of an outer
      // join, is checked with its regenerated sql and refused without one (Bug #77434)
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   // parse without a data source and generate with ds, for a shape that has no join order
   // check (no RIGHT/FULL join mixed with an inner join, no nested join under an outer join)
   private static String generateParsedWithoutSource(String text, JDBCDataSource ds)
      throws Exception
   {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.setDataSource(ds);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   // the regenerated SQL re-parses to itself. The one exception is the RIGHT OUTER JOIN written
   // for a nested left join, whose ON operands are swapped once on the first round trip for
   // every op (see nestedRoundTripSwapsOuterOperands), so the second generation is checked
   // to be the fixed point
   // generate from a model saved before #6080 recorded where each join was parsed: an XML
   // round trip with the joinClause attribute removed, as in the XML of an older build
   private static String generateSaved(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      String xml = buffer.toString();
      assertTrue(xml.contains(" joinClause=\""), xml);
      xml = xml.replaceAll(" joinClause=\"-?\\d+\"", "");
      UniformSQL saved = new UniformSQL();
      saved.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      for(XJoin join : saved.getJoins()) {
         assertEquals(XJoin.UNKNOWN_CLAUSE, join.getJoinClause(), text);
      }

      saved.setDataSource(ds);
      saved.clearSQLString();
      return normalize(saved.getSQLString());
   }

   private static void assertRoundTrip(String generated, JDBCDataSource ds) throws Exception {
      String again = generate(generated, ds);

      if(!again.equals(generated)) {
         String unswapped = again.replaceAll(
            "RIGHT OUTER JOIN (\"?a\"?) ON (\"?b\"?\\.\"?id\"?) = (\"?a\"?\\.\"?id\"?)",
            "RIGHT OUTER JOIN $1 ON $3 = $2");
         assertEquals(generated, unswapped, "round trip");
         assertEquals(again, generate(again, ds), "second round trip");
      }
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   /**
    * Builds data sources with real drivers and URLs, and compares the rows of two queries on
    * random small datasets in an in-memory Derby database.
    */
   static final class RowCompare {
      static JDBCDataSource dataSource(String type) {
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
         case "mongo" -> {
            ds.setDriver("mongodb.jdbc.MongoDriver");
            ds.setURL("jdbc:mongo://localhost:27017/test");
         }
         default -> throw new IllegalArgumentException(type);
         }

         ds.setAnsiJoin(type.endsWith("-ansi"));
         String helper = SQLHelper.getSQLHelper(ds).getSQLHelperType();
         assertEquals(type.replace("-ansi", ""), helper, "helper for " + type);
         return ds;
      }

      static int diffCount(String original, String generated, int datasets) throws Exception {
         Driver driver = (Driver) Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver")
            .getDeclaredConstructor().newInstance();
         Random random = new Random(77509);
         Integer[] values = { null, 0, 1, 2 };
         int diff = 0;

         try(Connection con = driver.connect("jdbc:derby:memory:rows77509;create=true",
                                             new Properties());
             Statement st = con.createStatement())
         {
            for(String table : new String[] { "a", "b", "c" }) {
               try {
                  st.execute("drop table " + table);
               }
               catch(SQLException ignore) {
               }

               st.execute("create table " + table + " (id int, k int, j int)");
            }

            for(int n = 0; n < datasets; n++) {
               for(String table : new String[] { "a", "b", "c" }) {
                  st.execute("delete from " + table);

                  for(int r = random.nextInt(5); r > 0; r--) {
                     st.execute("insert into " + table + " values (" +
                                   values[random.nextInt(4)] + ", " + values[random.nextInt(4)] +
                                   ", " + values[random.nextInt(4)] + ")");
                  }
               }

               if(!rows(con, original).equals(rows(con, generated))) {
                  diff++;
               }
            }
         }

         return diff;
      }

      // rows as a sorted multiset, with columns ordered by label since regeneration can
      // reorder the select list
      static List<String> rows(Connection con, String query) throws SQLException {
         List<String> rows = new ArrayList<>();

         try(Statement st = con.createStatement(); ResultSet rs = st.executeQuery(query)) {
            ResultSetMetaData meta = rs.getMetaData();
            TreeMap<String, Integer> columns = new TreeMap<>();

            for(int i = 1; i <= meta.getColumnCount(); i++) {
               columns.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), i);
            }

            while(rs.next()) {
               StringBuilder row = new StringBuilder();

               for(int i : columns.values()) {
                  row.append(rs.getObject(i)).append('|');
               }

               rows.add(row.toString());
            }
         }

         Collections.sort(rows);
         return rows;
      }
   }
}
