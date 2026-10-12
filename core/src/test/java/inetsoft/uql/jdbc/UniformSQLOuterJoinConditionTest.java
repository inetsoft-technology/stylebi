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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77410, an outer join ON condition that is an AND of column = column joins between
 * the same two tables parses as outer joins, and any other outer join ON condition fails
 * as an ordinary parse error instead of a ClassCastException.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class UniformSQLOuterJoinConditionTest {
   @ParameterizedTest
   @ValueSource(strings = { "left", "left outer", "right", "right outer", "full", "full outer" })
   void multiColumnOuterJoinParses(String type) throws Exception {
      UniformSQL sql = parse("select a.x, b.y from a " + type + " join b on a.id = b.id and a.k = b.k");

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertFalse(sql.isLossy());
      XJoin[] joins = sql.getJoins();
      assertEquals(2, joins.length);

      for(XJoin join : joins) {
         assertTrue(join.isOuterJoin(), join.toString());
      }

      String generated = normalize(sql.getSQLString());
      String keyword = type.split(" ")[0].toUpperCase() + " OUTER JOIN";
      assertTrue(generated.contains(keyword), generated);
      assertTrue(generated.contains(" ON a.id = b.id AND a.k = b.k"), generated);
      assertFalse(generated.contains(" where "), generated);

      UniformSQL reparsed = parse(generated);
      assertEquals(2, reparsed.getJoins().length);
      assertEquals(generated, normalize(reparsed.getSQLString()));
   }

   @Test
   void nestedAndWithSwappedOperandsParses() throws Exception {
      UniformSQL sql = parse("select a.x from a left join b on (b.id = a.id and (a.k = b.k))");
      XJoin[] joins = sql.getJoins();
      assertEquals(2, joins.length);
      assertTrue(joins[0].isOuterJoin());
      assertTrue(joins[1].isOuterJoin());
      assertTrue(normalize(sql.getSQLString()).contains("LEFT OUTER JOIN b ON"));
   }

   @Test
   void chainedMultiColumnOuterJoinParses() throws Exception {
      UniformSQL sql = parse("select a.x from a left join b on a.id = b.id " +
                             "left join c on a.id = c.id and a.k = c.k");
      assertEquals(3, sql.getJoins().length);

      for(XJoin join : sql.getJoins()) {
         assertTrue(join.isOuterJoin(), join.toString());
      }

      String generated = normalize(sql.getSQLString());
      assertTrue(generated.contains("(a LEFT OUTER JOIN b ON a.id = b.id ) " +
                                    "LEFT OUTER JOIN c ON a.id = c.id AND a.k = c.k"), generated);
      assertEquals(generated, normalize(parse(generated).getSQLString()));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // reporter's examples
      "select * from a left outer join b on b.id is null",
      "select * from a left join b on 1 = 1",
      "select * from a right join b on 1 = 1",
      "select * from a full outer join b on b.id is null",
      // column = constant, between
      "select * from a left join b on b.x = 1",
      "select * from a left join b on a.id between b.lo and b.hi",
      // a join that is not '=' would otherwise be regenerated as '='
      "select * from a left join b on a.id < b.id",
      "select * from a right join b on a.id <> b.id",
      // or, mixed, negated and 'is true' conditions
      "select * from a left join b on a.id = b.id or a.k = b.k",
      "select * from a left join b on a.id = b.id and b.x is null",
      "select * from a left join b on a.id = b.id and 1 = 1",
      "select * from a left join b on not (a.id = b.id and a.k = b.k)",
      // joins between different pairs of tables, or between unknown tables
      "select * from a left join b on a.id = b.id left join c on c.id = a.id and c.k = b.k",
      "select * from a left join b on id = bid and k = bk",
      "select * from a right join b on id = bid and k = bk",
      "select * from a full outer join b on a.id = b.id and k = bk",
      // chained, nested and subquery positions
      "select * from a left join b on a.id = b.id left join c on c.x is null",
      "select * from a left join (b left join c on 1 = 1) on a.id = b.id",
      "select * from (select a.id from a left join b on b.id is null) t",
      "select * from a where exists (select 1 from b left join c on 1 = 1)"
   })
   void unsupportedOuterJoinConditionFailsCleanly(String text) {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text));
      assertTrue(ex.getMessage().contains("Unsupported outer join condition"), ex.getMessage());

      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }

   // a truth test fails the parse at the IS (#77735), before the outer join is checked
   @Test
   void truthTestOuterJoinConditionFailsCleanly() {
      String text = "select * from a left join b on (a.id = b.id) is true";
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text));
      assertTrue(ex.getMessage().contains("Unsupported truth test"), ex.getMessage());

      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }

   @ParameterizedTest
   @ValueSource(strings = { "inner join", "join" })
   void innerJoinConditionMovesToWhere(String type) throws Exception {
      UniformSQL sql = parse("select a.x from a " + type + " b on b.id is null");
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals("select a.x from a, b where b.id is null", normalize(sql.getSQLString()));
   }

   @Test
   void editorMultiColumnOuterJoinReparses() throws Exception {
      // the query editor adds one outer join per column pair
      UniformSQL sql = parse("select a.x, b.y from a, b");
      sql.addJoin(join("a.id", "b.id"));
      sql.addJoin(join("a.k", "b.k"));
      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());
      assertTrue(generated.contains("LEFT OUTER JOIN b ON a.id = b.id AND a.k = b.k"), generated);

      UniformSQL reparsed = parse(generated);
      assertEquals(2, reparsed.getJoins().length);
      assertEquals(generated, normalize(reparsed.getSQLString()));
   }

   private static XJoin join(String column1, String column2) {
      return new XJoin(new XExpression(column1, XExpression.FIELD),
                       new XExpression(column2, XExpression.FIELD), "*=");
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
