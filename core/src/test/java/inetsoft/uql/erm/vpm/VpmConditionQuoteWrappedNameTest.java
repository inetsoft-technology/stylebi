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
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Bug #77787, the parser reports a quoted name that starts and ends with a quote ("""A""",
 * the name "A") as T."A", which was unquoted as the column A. The table of the column was
 * not replaced, and the replacement named the column A. SQL Server ["A"] was not replaced
 * either, with or without the parser. A column with the raw name is matched only written
 * quoted with the quote doubled, so T."C" (the column C) is not taken as the column "C".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class,
                                  VpmConditionQuoteWrappedNameTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VpmConditionQuoteWrappedNameTest {
   static Stream<Arguments> expressions() {
      // the expression and its replacement, and its replacement by the parser path on
      // mysql and access (which quote a name in backquotes) if it is different. {C} is the
      // column C, quoted or not as the helper writes it
      String[][] cases = {
         { "T.\"\"\"A\"\"\"", "o.\"\"\"A\"\"\"" },
         { "T.\"\"\"A B\"\"\"", "o.\"\"\"A B\"\"\"", "o.`\"A B\"`" },
         { "T.\"\"\"A\"\"B\"\"\"", "o.\"\"\"A\"\"B\"\"\"" },
         { "T.\"\"\"\"\"A\"\"\"\"\"", "o.\"\"\"\"\"A\"\"\"\"\"" },
         { "\"T\".\"\"\"A\"\"\"", "o.\"\"\"A\"\"\"" },
         { "upper(T.\"\"\"A\"\"\")", "upper(o.\"\"\"A\"\"\")" },
         { "T.\"\"\"A\"\"\" + T.C", "o.\"\"\"A\"\"\" + {C}" },
         // a quoted name (the column C) and the raw name with quotes (the column "C"), in
         // both orders of the parser's columns
         { "T.\"C\" + T.\"\"\"C\"\"\"", "{C} + o.\"\"\"C\"\"\"" },
         { "T.\"\"\"C\"\"\" + T.\"C\"", "o.\"\"\"C\"\"\" + {C}" },
         { "T.\"A\" + T.\"\"\"A\"\"\"", "{A} + o.\"\"\"A\"\"\"" },
         { "T.\"X\" + T.\"\"\"X\"\"\"", "{X} + o.\"\"\"X\"\"\"" },
         { "T.\"ID\" + T.\"\"\"ID\"\"\"", "{ID} + o.\"\"\"ID\"\"\"" },
         // a name of a quote, or with a quote at one end only, was replaced before
         { "T.\"\"\"\" + T.C", "o.\"\"\"\" + {C}" },
         { "T.\"\"\"A\"", "o.\"\"\"A\"" },
         { "T.\"A\"\"\"", "o.\"A\"\"\"" },
         { "T.\".A\" + T.C", "o.\".A\" + {C}", "o.`.A` + {C}" },
      };
      List<Arguments> args = new ArrayList<>();

      for(String product : PRODUCTS) {
         boolean pg = POSTGRESQL.equals(product);
         boolean backquote = MYSQL.equals(product) || ACCESS.equals(product);

         for(String[] row : cases) {
            // the parser reads the whole expression, or stops at between and the columns
            // are found by ColumnIterator
            String expected = expand(backquote && row.length > 2 ? row[2] : row[1], pg);
            String iexpected = expand(row[1], pg);
            args.add(Arguments.of(product, row[0], expected, true));
            args.add(Arguments.of(product, row[0] + " between 1 and 2",
                                  iexpected + " between 1 and 2", false));
         }
      }

      return args.stream();
   }

   private static String expand(String expected, boolean pg) {
      return Pattern.compile("\\{(\\w+)}").matcher(expected)
         .replaceAll(m -> pg ? "o.\"" + m.group(1) + "\"" : "o." + m.group(1));
   }

   @ParameterizedTest
   @MethodSource("expressions")
   void tableOfQuoteWrappedNameIsReplaced(String product, String exp, String expected,
                                          boolean parsed)
      throws Exception
   {
      assertEquals(parsed, isParsed(exp), "parser path");
      assertEquals(expected + " = 1", evaluate(exp, product, "T", new String[] { "T" }));
   }

   static Stream<Arguments> guardExpressions() {
      // the raw name is replaced only where it is a column of the table, not in a literal
      // or another table, and not as the column A written unquoted or quoted
      String[][] cases = {
         { "T.\"\"\"A\"\"\" + 'T.\"\"\"A\"\"\"'", "o.\"\"\"A\"\"\" + 'T.\"\"\"A\"\"\"'" },
         { "TT.\"\"\"A\"\"\" + T.\"\"\"A\"\"\"", "TT.\"\"\"A\"\"\" + o.\"\"\"A\"\"\"" },
         { "T.\"\"\"A\"\"\" + T.A + T.\"A\"", "o.\"\"\"A\"\"\" + {A} + {A}" },
         { "T.\"\"\"A.B\"\"\" + T.\"\"\"A\"\"\"", "o.\"\"\"A.B\"\"\" + o.\"\"\"A\"\"\"" },
      };
      List<Arguments> args = new ArrayList<>();

      for(String product : PRODUCTS) {
         boolean pg = POSTGRESQL.equals(product);

         for(String[] row : cases) {
            String expected = expand(row[1], pg);
            args.add(Arguments.of(product, row[0], expected, true));
            args.add(Arguments.of(product, row[0] + " between 1 and 2",
                                  expected + " between 1 and 2", false));
         }
      }

      return args.stream();
   }

   @ParameterizedTest
   @MethodSource("guardExpressions")
   void quoteWrappedNameIsReplacedOnlyAsTheColumn(String product, String exp, String expected,
                                                  boolean parsed)
      throws Exception
   {
      assertEquals(parsed, isParsed(exp), "parser path");
      assertEquals(expected + " = 1", evaluate(exp, product, "T", new String[] { "T" }));
   }

   static Stream<Arguments> bracketExpressions() {
      return Stream.of(SQL_SERVER, ACCESS).flatMap(product -> Stream.of(
         Arguments.of(product, "T.[\"A\"]", "o.[\"A\"]"),
         Arguments.of(product, "T.[\"A\"] between 1 and 2", "o.[\"A\"] between 1 and 2"),
         Arguments.of(product, "[T].[\"A B\"] + T.C", "o.[\"A B\"] + o.C"),
         Arguments.of(product, "[T].[\"A B\"] between 1 and 2",
                      "o.[\"A B\"] between 1 and 2"),
         Arguments.of(product, "T.\"\"\"A\"\"\" + T.[\"A\"]",
                      "o.\"\"\"A\"\"\" + o.[\"A\"]"),
         Arguments.of(product, "T.\"\"\"A\"\"\" + T.[\"A\"] between 1 and 2",
                      "o.\"\"\"A\"\"\" + o.[\"A\"] between 1 and 2"),
         // the quoted name A is not the name "A"
         Arguments.of(product, "T.\"A\" + T.[\"A\"]", "o.A + o.[\"A\"]"),
         Arguments.of(product, "T.\"A\" + T.[\"A\"] between 1 and 2",
                      "o.A + o.[\"A\"] between 1 and 2"),
         // a quote inside a bracket quoted name, replaced before
         Arguments.of(product, "T.[A\"B] + T.C", "o.[A\"B] + o.C"),
         Arguments.of(product, "T.[A\"B] between 1 and 2", "o.[A\"B] between 1 and 2"),
         Arguments.of(product, "T.[\"A] between 1 and 2", "o.[\"A] between 1 and 2")));
   }

   @ParameterizedTest
   @MethodSource("bracketExpressions")
   void tableOfBracketQuoteNameIsReplaced(String product, String exp, String expected)
      throws Exception
   {
      assertEquals(expected + " = 1", evaluate(exp, product, "T", new String[] { "T" }));
   }

   static Stream<Arguments> schemaExpressions() {
      return Stream.of(POSTGRESQL, ORACLE, H2).flatMap(product -> Stream.of(
         Arguments.of(product, "S.T.\"\"\"A\"\"\"", "o.\"\"\"A\"\"\""),
         Arguments.of(product, "S.T.\"\"\"A\"\"\" between 1 and 2",
                      "o.\"\"\"A\"\"\" between 1 and 2")));
   }

   @ParameterizedTest
   @MethodSource("schemaExpressions")
   void tableWithSchemaIsReplaced(String product, String exp, String expected)
      throws Exception
   {
      assertEquals(expected + " = 1", evaluate(exp, product, "S.T", new String[] { "S.T" }));
   }

   static Stream<Arguments> quotedTableExpressions() {
      return Stream.of(null, POSTGRESQL, ORACLE, H2).flatMap(product -> Stream.of(
         Arguments.of(product, "\"S.T\".\"\"\"A\"\"\"", "o.\"\"\"A\"\"\""),
         Arguments.of(product, "\"S.T\".\"\"\"A\"\"\" between 1 and 2",
                      "o.\"\"\"A\"\"\" between 1 and 2"),
         Arguments.of(product, "\"S.T\".\"A\"\"B\"", "o.\"A\"\"B\""),
         Arguments.of(product, "\"S.T\".\"A\"\"B\" between 1 and 2",
                      "o.\"A\"\"B\" between 1 and 2")));
   }

   // the query table is quoted ("S.T"), and is found as the start of the column
   @ParameterizedTest
   @MethodSource("quotedTableExpressions")
   void quotedTableIsReplaced(String product, String exp, String expected)
      throws Exception
   {
      assertEquals(expected + " = 1",
                   evaluate(exp, product, "S.T", new String[] { "\"S.T\"" }));
   }

   private static boolean isParsed(String exp) throws Exception {
      SQLParser parser = new SQLParser(new SQLLexer(new StringReader(exp)));
      parser.setPreferQuote(false);
      parser.value_exp();
      return parser.LA(1) == antlr.Token.EOF_TYPE;
   }

   private static String evaluate(String exp, String product, String table, String[] tables)
      throws Exception
   {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable(table);
      cond.setCondition(new XBinaryCondition(new XExpression(exp, XExpression.EXPRESSION),
                                             new XExpression("1", XExpression.VALUE), "="));
      JDBCDataSource source = null;

      if(product != null) {
         source = new JDBCDataSource();
         source.setName("ds");
         source.setRuntimeProductName(product);
      }

      XPrincipal user = new XPrincipal(new IdentityID("viewer", null));
      return cond.evaluate(null, tables, new String[] { "o" }, new String[0], source,
                           new VariableTable(), user, false);
   }

   private static final String POSTGRESQL = "postgresql";
   private static final String ORACLE = "oracle";
   private static final String SQL_SERVER = "sql server";
   private static final String ACCESS = "access";
   private static final String H2 = "h2";
   private static final String MYSQL = "mysql";
   private static final String[] PRODUCTS =
      { null, POSTGRESQL, ORACLE, SQL_SERVER, ACCESS, H2, MYSQL };

   @Configuration
   static class TestConfig {
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }
}
