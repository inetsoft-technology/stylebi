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
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77486, an outer join with no ON or USING ("a left join b") parsed with no join and was
 * regenerated as "a, b". H2 and SQLite run it as an outer join on true, so the regenerated
 * query returned no rows when b was empty. It must fail the parse so the original sql runs.
 * A union join is refused with or without ON, since with ON it was regenerated as an inner
 * join. A plain or inner join with no ON is a cross join (MySQL, MariaDB, H2, SQLite) and
 * still parses as "a, b".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLOnlessOuterJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOnlessOuterJoinTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String[] REFUSED_TYPES = {
      "left", "left outer", "LEFT OUTER", "right", "right outer", "full", "full outer", "union"
   };

   // %s is the join type of the join with no ON
   private static final String[] REFUSED_SHAPES = {
      "select * from a %s join b",
      "select a.id from a as t1 %s join b as t2 where a.k = 1",
      // chained: before or after a join with an ON, and after a comma item
      "select * from a %s join b left join c on b.id = c.id",
      "select * from a %s join b join c on a.id = c.id",
      "select * from a left join b on a.id = b.id %s join c",
      "select * from a join b on a.id = b.id %s join c",
      "select * from a, b %s join c",
      // parenthesized operands
      "select * from a %s join (b left join c on b.id = c.id)",
      "select * from (a left join b on a.id = b.id) %s join c",
      // subqueries
      "select * from (select a.id from a %s join b) t",
      "select * from x where exists (select 1 from a %s join b)",
      "select * from x where x.id in (select a.id from a %s join b)",
      "select x.id, (select count(*) from a %s join b) from x",
   };

   static Stream<Arguments> refused() {
      Stream.Builder<Arguments> args = Stream.builder();

      for(String shape : REFUSED_SHAPES) {
         for(String type : REFUSED_TYPES) {
            args.add(Arguments.of(String.format(shape, type)));
         }
      }

      // a union join with an ON was an inner join, and a natural outer join has no ON
      args.add(Arguments.of("select * from a union join b on a.id = b.id"));
      args.add(Arguments.of("select * from a UNION JOIN b on a.id = b.id left join c on b.id = c.id"));
      args.add(Arguments.of("select * from a left join b on a.id = b.id union join c on b.id = c.id"));
      args.add(Arguments.of("select * from a natural left join b"));
      return args.build();
   }

   @ParameterizedTest
   @MethodSource("refused")
   void joinWithoutConditionFailsParse(String text) {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);

      UniformSQL fresh = new UniformSQL();
      fresh.setSQLString(text, false);
      assertEquals(text, fresh.getSQLString());
      assertTrue(fresh.isLossy(), text);

      for(String type : new String[] { "h2", "postgresql", "oracle" }) {
         assertFalse(XUtil.isQueryMergeable(query(text, dataSource(type))), type + ": " + text);
      }
   }

   // keyword case, comments and line breaks between the join keywords, an outer join with
   // no ON inside a parenthesized operand of a join with an ON, and after a natural join
   @ParameterizedTest
   @ValueSource(strings = {
      "select * from a lEfT jOiN b",
      "select * from a RiGhT oUtEr JoIn b",
      "select * from a Union Join b on a.id = b.id",
      "select * from a   left    outer   join   b",
      "select * from a left /* c1 */ outer /* c2 */ join b",
      "select * from a left -- c\n outer\n join b",
      "select * from a\nleft\nouter\njoin\nb",
      "select * from a\tfull\t\tjoin\tb",
      "select * from a left join (b left join c) on a.id = b.id",
      "select * from a join (b left join c) on a.id = b.id",
      "select * from a natural join b left join c",
   })
   void joinWithoutConditionVariantsFailParse(String text) {
      joinWithoutConditionFailsParse(text);
   }

   // a dotless/dotted i must not change the join type, so the type is not upper-cased
   // with the default locale
   @ParameterizedTest
   @ValueSource(strings = {
      "select * from a right join b",
      "select * from a right outer join b",
      "select * from a left join b",
      "select * from a full join b",
      "select * from a union join b on a.id = b.id",
   })
   void joinWithoutConditionFailsParseInTurkishLocale(String text) {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));
         UniformSQL sql = new UniformSQL();
         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   // the expression check judges the syntax only, so a refused join in a scalar subquery is
   // still a valid expression (#77493)
   @Test
   void scalarSubqueryExpressionIsValid() {
      assertTrue(XUtil.isSQLExpressionValid("(select count(*) from a left join b)"));
      assertTrue(XUtil.isSQLExpressionValid("(select count(*) from a join b)"));
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from a left join b on a.id = b.id)"));
   }

   // a join with no ON that is a cross join stays "a, b"
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select * from a join b|select * from a, b",
      "select * from a inner join b|select * from a, b",
      "select * from a INNER JOIN b|select * from a, b",
      "select * from a cross join b|select * from a, b",
      "select * from a join b join c on a.id = c.id|select * from a, b, c where a.id = c.id",
      "select * from (select a.id from a join b) t|select * from ( select a.id from a, b) t",
   })
   void crossJoinWithoutConditionParses(String text, String expected) throws Exception {
      assertAccepted(text, expected);
      assertTrue(XUtil.isQueryMergeable(query(text, dataSource("h2"))), text);
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select * from a left join b on a.id = b.id|" +
         "select * from a LEFT OUTER JOIN b ON a.id = b.id",
      "select * from a left outer join b on a.id = b.id|" +
         "select * from a LEFT OUTER JOIN b ON a.id = b.id",
      "select * from a right join b on a.id = b.id|" +
         "select * from a RIGHT OUTER JOIN b ON a.id = b.id",
      "select * from a full outer join b on a.id = b.id|" +
         "select * from a FULL OUTER JOIN b ON a.id = b.id",
      "select * from a join b on a.id = b.id left join c on b.id = c.id|" +
         "select * from (a INNER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c ON b.id = c.id",
      "select * from (a left join b on a.id = b.id) left join c on a.id = c.id|" +
         "select * from (a LEFT OUTER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c ON a.id = c.id",
      "select * from x where x.id in (select a.id from a right join b on a.id = b.id)|" +
         "select * from x where x.id IN ( select a.id from a RIGHT OUTER JOIN b ON a.id = b.id)",
   })
   void outerJoinWithConditionParses(String text, String expected) throws Exception {
      assertAccepted(text, expected);
   }

   // an ON of a nested operand comes before the ON of the outer join, and the join
   // condition is also the only spec for using
   @ParameterizedTest
   @ValueSource(strings = {
      "select * from a left join b left join c on b.id = c.id on a.id = b.id",
      "select * from a left join (b left join c on b.id = c.id) on a.id = b.id",
      "select * from a left join b using (id)",
   })
   void nestedConditionParses(String text) {
      UniformSQL sql = new UniformSQL();
      // Bug #77434 refuses a nested join on the right of an outer join without a data source
      sql.setDataSource(GenericJDBCDataSource.create());
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
   }

   @Test
   void oracleOuterJoinParses() throws Exception {
      String text = "select * from a, b where a.id = b.id(+)";
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals(1, sql.getJoins().length);
      assertTrue(sql.getJoins()[0].isOuterJoin());
   }

   // the sql the query editor writes for its joins must still parse
   @ParameterizedTest
   @ValueSource(strings = { "*=", "=*", "*=*", "=" })
   void editorJoinsReparse(String op) throws Exception {
      for(String type : new String[] { "default", "h2", "h2-ansi", "postgresql", "oracle",
                                       "oracle-ansi" })
      {
         UniformSQL sql = new UniformSQL();
         sql.addTable("a");
         sql.addTable("b");
         sql.addTable("c");
         sql.getSelection().addColumn("a.id");
         sql.getSelection().addColumn("c.k");
         sql.addJoin(join("a.id", op, "b.id"));
         sql.addJoin(join("b.k", "*=", "c.k"));
         sql.setDataSource("default".equals(type) ? null : dataSource(type));
         sql.clearSQLString();
         String generated = normalize(sql.getSQLString());

         UniformSQL reparsed = new UniformSQL();
         new SQLProcessor(reparsed).parse(generated);
         assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), type + ": " + generated);
         assertFalse(reparsed.isLossy(), generated);
         assertEquals(2, reparsed.getJoins().length, generated);
         reparsed.setDataSource(sql.getDataSource());
         reparsed.clearSQLString();
         assertEquals(generated, normalize(reparsed.getSQLString()), type + " round trip");
      }
   }

   private static void assertAccepted(String text, String expected) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());
      assertEquals(expected, generated);

      // round trip
      UniformSQL reparsed = parse(generated);
      reparsed.clearSQLString();
      assertEquals(generated, normalize(reparsed.getSQLString()), "round trip");
   }

   private static XJoin join(String left, String op, String right) {
      return new XJoin(new XExpression(left, XExpression.FIELD),
                       new XExpression(right, XExpression.FIELD), op);
   }

   private static JDBCQuery query(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      sql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
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
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      String helper = SQLHelper.getSQLHelper(ds).getSQLHelperType();
      assertEquals(type.replace("-ansi", ""), helper, "helper for " + type);
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
}
