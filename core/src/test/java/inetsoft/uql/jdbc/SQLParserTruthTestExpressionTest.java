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

import antlr.Token;
import inetsoft.test.*;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.StringReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77735, a statement with a truth test, x IS [NOT] TRUE/FALSE/UNKNOWN, fails to parse.
 * The refusal is for a statement only: a sql expression (a calc field, the columns of a VPM
 * condition) and condition text without a statement (a VPM condition, a where clause) still
 * accept a truth test, also in a scalar subquery, which the parser reads inside the expression.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLParserTruthTestExpressionTest {
   @ParameterizedTest
   @ValueSource(strings = {
      "(select max(b.k) from b where (b.k = 1) is true)",
      "(select max(b.k) from b where b.id = 2 and (b.k = 1) is not unknown)",
      "(select case when (b.k = 1) is true then 1 end from b)",
      "case when (a.k = 1) is not false then 1 else 0 end",
      "case when (a.k = 1) is true and a.id > 0 then a.x end",
   })
   void expressionIsValid(String exp) throws Exception {
      assertTrue(XUtil.isSQLExpressionValid(exp), exp);
      XUtil.parseSQLExpressionSyntax(exp);
   }

   // the columns of a VPM condition expression, VpmCondition reads them with value_exp
   @Test
   void expressionColumns() throws Exception {
      SQLParser parser = parser("case when (a.k = 1) is true then a.x else a.y end");
      parser.setPreferQuote(false);
      parser.value_exp();
      assertEquals(Token.EOF_TYPE, parser.LA(1));
      assertEquals(Set.of("a.k", "a.x", "a.y"), new HashSet<>(List.of(parser.getColumns())));
   }

   // a VPM condition and a where clause are parsed without a statement
   @ParameterizedTest
   @ValueSource(strings = {
      "(a.k = 1) is true",
      "(a.k = 1) is not unknown and a.id in (select b.id from b)",
      "a.id in (select b.id from b where (b.k = 1) is true)",
      "a.k = 1 or not ((a.id = 2) is false)",
   })
   void conditionParses(String text) throws Exception {
      SQLParser parser = parser(text);
      XFilterNode node = parser.search_condition();
      assertNotNull(node, text);
      assertEquals(Token.EOF_TYPE, parser.LA(1), text);

      XFilterNode where = parser("where " + text).where_clause();
      assertEquals(node.toString(), where.toString(), text);

      assertNotNull(UniformSQLTruthTestRefuseTest.findTruthTest(node), node.toString());
   }

   private static SQLParser parser(String text) {
      return new SQLParser(new SQLLexer(new StringReader(text)));
   }
}
