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
package inetsoft.uql.erm.vpm;

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.VarSQL;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Bug #77663, the table names of the fields of an expression in a VPM condition were replaced
 * by the table alias inside string literals too, which turned a literal into a column or took
 * its closing quote, and in names ending with the table name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class,
                                  VpmConditionLiteralTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VpmConditionLiteralTest {
   static Stream<Arguments> expressions() {
      return Stream.of(
         // a literal is not changed
         Arguments.of("T.A || 'T.A'", "o.A || 'T.A'"),
         Arguments.of("concat(T.A, ' T.A')", "concat(o.A, ' T.A')"),
         Arguments.of("case when T.A = 'T.Ax' then 1 end", "case when o.A = 'T.Ax' then 1 end"),
         Arguments.of("T.A || 'it''s T.A'", "o.A || 'it''s T.A'"),
         // a name ending with the table name is not the table
         Arguments.of("XT.A || T.A", "XT.A || o.A"),
         // a double quoted name is a column
         Arguments.of("upper(\"T\".\"A\")", "upper(o.A)"),
         Arguments.of("upper(\"T\".A)", "upper(o.A)"),
         Arguments.of("T.\"A\" || 'x'", "o.A || 'x'"),
         // the expression doesn't lex ($(a.b)), so the columns are found by the column
         // iterator
         Arguments.of("concat($(a.b), T.A, '\" ', T.B)", "concat($(a.b), o.A, '\" ', o.B)"),
         Arguments.of("concat($(a.b), 'say \"hi\" T.B now', T.A)",
                      "concat($(a.b), 'say \"hi\" T.B now', o.A)"),
         Arguments.of("$(a.b) + T.B", "$(a.b) + o.B"),
         Arguments.of("concat($(a.b), \"T\".\"A\", T.B)", "concat($(a.b), o.A, o.B)"),
         // a quote that is not closed is not a literal
         Arguments.of("T.A || 'x", "o.A || 'x"),
         // a comment is not changed
         Arguments.of("T.A /* T.A */", "o.A /* T.A */"),
         // a ' in a double quoted or backquoted string or name doesn't open a literal
         Arguments.of("concat(\"x'y\", T.A, 'z')", "concat(\"x'y\", o.A, 'z')"),
         Arguments.of("concat($(a.b), \"x'y\", T.A, 'z')", "concat($(a.b), \"x'y\", o.A, 'z')"),
         Arguments.of("concat(`x'y`, T.A, 'z')", "concat(`x'y`, o.A, 'z')"),
         // a quote that is not closed after a mysql backslash escape is an ordinary character
         Arguments.of("T.A || \"say \\\"hi\" || T.B || 'x'",
                      "o.A || \"say \\\"hi\" || o.B || 'x'"),
         Arguments.of("concat($(a.b), T.A, \"say \\\"hi\", T.B, 'x')",
                      "concat($(a.b), o.A, \"say \\\"hi\", o.B, 'x')"));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("expressions")
   void tableIsReplacedOutsideLiterals(String exp, String expected) throws Exception {
      assertEquals(expected + " = 1", evaluate("T", "o", exp));
   }

   // a name ending with a unicode table name is not the table
   @Test
   void unicodeNameEndingWithTableIsNotTable() throws Exception {
      String table = "订单";
      String exp = "历史" + table + ".AMT || " + table + ".AMT";

      assertEquals("历史" + table + ".AMT || o.AMT = 1", evaluate(table, "o", exp));
   }

   // a regex char in a name or a $ in the alias is not a regex
   @Test
   void regexCharsInNamesAreLiteral() throws Exception {
      assertEquals("\"V$X\".A || 'T.A' = 1", evaluate("T", "V$X", "T.A || 'T.A'"));
      assertEquals("concat($(a.b), o.\"A(\", o.B) = 1",
                   evaluate("T", "o", "concat($(a.b), \"T\".\"A(\", T.B)"));
   }

   /**
    * Bug #77697, the parser stopped without an error at a token it doesn't know, the column
    * iterator didn't know the quoting and comment rules of the database, a variable was taken
    * for a column, and a name starting with the column name was replaced.
    */
   static Stream<Arguments> dialectExpressions() {
      return Stream.of(
         // a ' in a bracket quoted name (item 1)
         Arguments.of(MSSQL, "concat($(a.b), T.A, [it's], 'x', T.B)",
                      "concat($(a.b), o.A, [it's], 'x', o.B)"),
         Arguments.of(null, "concat($(a.b), T.A, [it's], 'x', T.B)",
                      "concat($(a.b), o.A, [it's], 'x', o.B)"),
         Arguments.of(MSSQL, "T.A ^ 1 + [it's] + 'x' + T.B", "o.A ^ 1 + [it's] + 'x' + o.B"),
         Arguments.of(MSSQL, "T.A % 2 + [Customer's Name] + T.B",
                      "o.A % 2 + [Customer's Name] + o.B"),
         Arguments.of(MSSQL, "T.A + [it's] + T.B;", "o.A + [it's] + o.B;"),
         Arguments.of(null, "T.A || [it's] || 'x' || T.B", "o.A || [it's] || 'x' || o.B"),
         // a bracket quoted name is a column (item 1)
         Arguments.of(MSSQL, "T.[Name] + T.B", "o.[Name] + o.B"),
         Arguments.of(MSSQL, "[T].[Name] + T.B", "o.[Name] + o.B"),
         Arguments.of(MSSQL, "T.[Customer's] + 'x' + T.B", "o.[Customer's] + 'x' + o.B"),
         Arguments.of(MSSQL, "concat($(a.b), T.[Customer's], 'x', T.B)",
                      "concat($(a.b), o.[Customer's], 'x', o.B)"),
         // the name before a literal, and the columns after a token the parser stops at
         // (item 2)
         Arguments.of(null, "T.A^'x'", "o.A^'x'"),
         Arguments.of(null, "T.A^'x' || T.B", "o.A^'x' || o.B"),
         Arguments.of(MSSQL, "T.A^'x' + T.B", "o.A^'x' + o.B"),
         Arguments.of(null, "concat($(p), T.A^'x')", "concat($(p), o.A^'x')"),
         Arguments.of(null, "concat($(a.b), T.A^'x', T.B)", "concat($(a.b), o.A^'x', o.B)"),
         Arguments.of(MYSQL, "T.A div 2 + T.B", "o.A div 2 + o.B"),
         Arguments.of(POSTGRESQL, "T.A ->> 'k' || T.B", "o.\"A\" ->> 'k' || o.\"B\""),
         Arguments.of(null, "T.A + T.B over (partition by T.C)",
                      "o.A + o.B over (partition by o.C)"),
         Arguments.of(null, "T.A in ('O''Brien', T.B)", "o.A in ('O''Brien', o.B)"),
         // a variable is not a column (item 3)
         Arguments.of(null, "concat(T.C, $(T.B))", "concat(o.C, $(T.B))"),
         Arguments.of(null, "case when $(T.B) is null then 1 else T.C end",
                      "case when $(T.B) is null then 1 else o.C end"),
         // a name starting with the column name is not the column (item 4)
         Arguments.of(null, "T.DATE + T.DATE_ID", "o.\"DATE\" + o.DATE_ID"),
         Arguments.of(null, "T.DATE_ID + T.DATE", "o.DATE_ID + o.\"DATE\""),
         Arguments.of(MSSQL, "T.DATE + T.DATE_ID", "o.\"DATE\" + o.DATE_ID"),
         Arguments.of(MYSQL, "T.DATE + T.DATE_ID", "o.`DATE` + o.DATE_ID"),
         Arguments.of(null, "concat($(a.b), T.DATE, T.DATE$X)",
                      "concat($(a.b), o.\"DATE\", o.\"DATE$X\")"),
         // a mysql # comment (item 5)
         Arguments.of(MYSQL, "T.A # note\n + T.B", "o.A # note\n + o.B"),
         Arguments.of(MYSQL, "T.A # it's\n + T.B", "o.A # it's\n + o.B"),
         Arguments.of(MYSQL, "T.A # it's\n + T.B + 'x'", "o.A # it's\n + o.B + 'x'"),
         Arguments.of(MYSQL, "concat(T.A, # it's T.B\n T.B, 'x')",
                      "concat(o.A, # it's T.B\n o.B, 'x')"),
         // a mysql backslash escape (item 6)
         Arguments.of(MYSQL, "concat(T.A, \"it\\\"s T.B\")", "concat(o.A, \"it\\\"s T.B\")"),
         Arguments.of(MYSQL, "concat(T.A, \"it\\\"s T.B\", T.B)",
                      "concat(o.A, \"it\\\"s T.B\", o.B)"),
         Arguments.of(MYSQL, "concat(T.A, 'it\\'s', T.B)", "concat(o.A, 'it\\'s', o.B)"),
         Arguments.of(MYSQL, "concat(T.A, 'it\\'s T.B', T.B)",
                      "concat(o.A, 'it\\'s T.B', o.B)"),
         // the rules of the other databases are not applied
         Arguments.of(null, "concat($(a.b), T.A, 'C:\\', T.B)",
                      "concat($(a.b), o.A, 'C:\\', o.B)"),
         Arguments.of(null, "concat($(a.b), m['a]b'], T.B)", "concat($(a.b), m['a]b'], o.B)"),
         Arguments.of(POSTGRESQL, "concat($(a.b), T.A, '[x', T.B, 'y]')",
                      "concat($(a.b), o.\"A\", '[x', o.\"B\", 'y]')"),
         Arguments.of(MYSQL, "concat(T.A, '-- x', T.B) /* it's T.B */",
                      "concat(o.A, '-- x', o.B) /* it's T.B */"),
         // a double quoted name is still a column
         Arguments.of(MYSQL, "upper(\"T\".\"A\")", "upper(o.A)"),
         Arguments.of(MSSQL, "upper(\"T\".\"A\") + T.[B]", "upper(o.A) + o.[B]"),
         // realistic expressions the parser doesn't read to the end, so the column iterator
         // finds their columns, keep every column replaced
         Arguments.of(null, "T.A in (select X.B from X where X.C = T.D)",
                      "o.A in (select X.B from X where X.C = o.D)"),
         Arguments.of(null, "T.A between 1 and T.B", "o.A between 1 and o.B"),
         Arguments.of(POSTGRESQL, "T.A[1] + T.B", "o.\"A\"[1] + o.\"B\""),
         Arguments.of(POSTGRESQL, "(T.A)[1] || T.B", "(o.\"A\")[1] || o.\"B\""),
         Arguments.of(POSTGRESQL, "T.A = ANY(ARRAY[T.B, T.C])",
                      "o.\"A\" = ANY(ARRAY[o.\"B\", o.\"C\"])"),
         Arguments.of(POSTGRESQL, "T.A->'k' || T.B", "o.\"A\"->'k' || o.\"B\""),
         Arguments.of(POSTGRESQL, "T.A #> '{a,b}' || T.B", "o.\"A\" #> '{a,b}' || o.\"B\""),
         Arguments.of(null, "T.A || N'it''s' || T.B", "o.A || N'it''s' || o.B"),
         Arguments.of(null, "N'x' || T.A", "N'x' || o.A"),
         Arguments.of(MYSQL, "concat($(p), _utf8'x', T.A, T.B)",
                      "concat($(p), _utf8'x', o.A, o.B)"),
         Arguments.of(MYSQL, "T.A->'$.k' || T.B", "o.A->'$.k' || o.B"),
         Arguments.of(null, "T.A || 'x' || T.B\r\n + T.C", "o.A || 'x' || o.B\r\n + o.C"),
         // expressions the parser reads to the end are not changed
         Arguments.of(POSTGRESQL, "T.A::text || T.B", "o.\"A\"::text || o.\"B\""),
         Arguments.of(POSTGRESQL, "T.A COLLATE \"C\" || T.B", "o.\"A\" COLLATE \"C\" || o.\"B\""),
         Arguments.of(null, "substring(T.A from 2 for 3) || T.B",
                      "substring(o.A from 2 for 3) || o.B"),
         Arguments.of(ORACLE, "case T.A when 'it''s' then T.B else nvl(T.C, 0) end",
                      "case o.A when 'it''s' then o.B else nvl(o.C, 0) end"));
   }

   @ParameterizedTest(name = "{0}: {1}")
   @MethodSource("dialectExpressions")
   void tableIsReplacedByDatabaseRules(String product, String exp, String expected)
      throws Exception
   {
      assertEquals(expected + " = 1", evaluate("T", "o", exp, product));
   }

   // Bug #77697, a name starting with the column name is not the column, it is a column of
   // another table (item 4)
   @Test
   void columnOfTableNamedAfterColumnIsNotReplaced() throws Exception {
      VpmCondition cond = createCondition("T", "T.A + T.AB.C");
      XPrincipal user = new XPrincipal(new IdentityID("viewer", null));

      assertEquals("o.A + p.C = 1",
                   cond.evaluate(null, new String[] { "T", "T.AB" }, new String[] { "o", "p" },
                                 new String[0], null, new VariableTable(), user, false));
   }

   // Bug #77697, a variable with a dot was renamed by the table alias, so it was bound as
   // null (item 3)
   @Test
   void variableIsNotRenamed() throws Exception {
      VariableTable vars = new VariableTable();
      vars.put("a.b", "v");
      VpmCondition cond = createCondition("a", "case when $(a.b) is null then 1 else a.c end");
      XPrincipal user = new XPrincipal(new IdentityID("viewer", null));
      String result = cond.evaluate(null, new String[] { "a" }, new String[] { "o" },
                                    new String[0], null, vars, user, false);

      assertEquals("case when $(a.b) is null then 1 else o.c end = 1", result);

      VarSQL varsql = new VarSQL();
      varsql.replaceVariables("select * from a o where " + result, vars);
      assertEquals(List.of("v"), varsql.getParameterValues());
   }

   private static String evaluate(String table, String alias, String exp) throws Exception {
      return evaluate(table, alias, exp, null);
   }

   private static String evaluate(String table, String alias, String exp, String product)
      throws Exception
   {
      VpmCondition cond = createCondition(table, exp);
      JDBCDataSource source = null;

      if(product != null) {
         source = new JDBCDataSource();
         source.setName("ds");
         source.setRuntimeProductName(product);
      }

      XPrincipal user = new XPrincipal(new IdentityID("viewer", null));
      return cond.evaluate(null, new String[] { table }, new String[] { alias },
                           new String[0], source, new VariableTable(), user, false);
   }

   private static VpmCondition createCondition(String table, String exp) {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable(table);
      cond.setCondition(new XBinaryCondition(new XExpression(exp, XExpression.EXPRESSION),
                                             new XExpression("1", XExpression.VALUE), "="));
      return cond;
   }

   private static final String MSSQL = "sql server";
   private static final String MYSQL = "mysql";
   private static final String POSTGRESQL = "postgresql";
   private static final String ORACLE = "oracle";

   @Configuration
   static class TestConfig {
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }
}
