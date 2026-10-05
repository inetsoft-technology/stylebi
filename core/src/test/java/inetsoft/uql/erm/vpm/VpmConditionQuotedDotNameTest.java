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

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Bug #77782, a quoted column with a dot (T."A.B") was split at its last dot (T."A and B"),
 * and the fragment with the closing quote was escaped and quoted again (o."A."B"""), which
 * is not valid SQL. A name reported by the parser with a doubled quote (A""B of "A""""B")
 * was taken as escaped, which names another column (A"B).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class,
                                  VpmConditionQuotedDotNameTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VpmConditionQuotedDotNameTest {
   static Stream<Arguments> expressions() {
      return Stream.of(
         // a quoted column with a dot, the parser reads the whole expression
         Arguments.of(POSTGRESQL, "T.\"A.B\" || T.C", "o.\"A.B\" || o.\"C\""),
         Arguments.of(POSTGRESQL, "T.\"A\"\".B\" || T.C", "o.\"A\"\".B\" || o.\"C\""),
         Arguments.of(POSTGRESQL, "T.\"A.B.C\" || T.C", "o.\"A.B.C\" || o.\"C\""),
         Arguments.of(POSTGRESQL, "T.\"A.\"\"B\" || T.C", "o.\"A.\"\"B\" || o.\"C\""),
         Arguments.of(POSTGRESQL, "T.\"A.\" || T.C", "o.\"A.\" || o.\"C\""),
         Arguments.of(POSTGRESQL, "T.\".A\" || T.C", "o.\".A\" || o.\"C\""),
         Arguments.of(POSTGRESQL, "T.\"\"\"A.B\"\"\" || T.C", "o.\"\"\"A.B\"\"\" || o.\"C\""),
         Arguments.of(POSTGRESQL, "\"T\".\"A.B\" || T.C", "o.\"A.B\" || o.\"C\""),
         Arguments.of(POSTGRESQL, "T.\"A.B\" || T.\"A\"\"\"\"B\"",
                      "o.\"A.B\" || o.\"A\"\"\"\"B\""),
         Arguments.of(POSTGRESQL, "concat(T.\"A\"\"B\", T.\"A.B\")",
                      "concat(o.\"A\"\"B\", o.\"A.B\")"),
         Arguments.of(null, "T.\"A.B\" || T.C", "o.\"A.B\" || o.C"),
         Arguments.of(ORACLE, "T.\"A.B\" || T.C", "o.\"A.B\" || o.C"),
         Arguments.of(SQL_SERVER, "T.\"A.B\" + T.C", "o.\"A.B\" + o.C"),
         Arguments.of(H2, "T.\"A.B\" || T.C", "o.\"A.B\" || o.C"),
         // the parser stops early (between, is, div) or fails ($(a.b)), the columns are
         // found by ColumnIterator
         Arguments.of(POSTGRESQL, "T.\"A.B\" between 1 and 2", "o.\"A.B\" between 1 and 2"),
         Arguments.of(POSTGRESQL, "T.\"A.B\" is null", "o.\"A.B\" is null"),
         Arguments.of(POSTGRESQL, "T.\"A.B\" div 2", "o.\"A.B\" div 2"),
         Arguments.of(POSTGRESQL, "$(a.b) + T.\"A.B\"", "$(a.b) + o.\"A.B\""),
         Arguments.of(POSTGRESQL, "T.\"A.\" between 1 and 2", "o.\"A.\" between 1 and 2"),
         Arguments.of(POSTGRESQL, "T.\"\"\"A.B\"\"\" between 1 and 2",
                      "o.\"\"\"A.B\"\"\" between 1 and 2"),
         // a name with a doubled quote, the parser reports it without the escape (A""B)
         Arguments.of(POSTGRESQL, "T.\"A\"\"\"\"B\"", "o.\"A\"\"\"\"B\""),
         Arguments.of(POSTGRESQL, "T.\"A\"\"B\" + T.\"C\"\"\"\"D\"",
                      "o.\"A\"\"B\" + o.\"C\"\"\"\"D\""),
         Arguments.of(ORACLE, "T.\"A\"\"\"\"B\"", "o.\"A\"\"\"\"B\""),
         Arguments.of(H2, "T.\"A\"\"\"\"B\"", "o.\"A\"\"\"\"B\""),
         Arguments.of(POSTGRESQL, "T.\"A\"\"B\"", "o.\"A\"\"B\""),
         // ColumnIterator reports the name as written ("A""""B")
         Arguments.of(POSTGRESQL, "T.\"A\"\"\"\"B\" between 1 and 2",
                      "o.\"A\"\"\"\"B\" between 1 and 2"),
         Arguments.of(POSTGRESQL, "T.\"A\"\"B\" between 1 and 2",
                      "o.\"A\"\"B\" between 1 and 2"),
         Arguments.of(POSTGRESQL, "T.\"A\"\"B\" div 2", "o.\"A\"\"B\" div 2"),
         // a bracket quoted column with a dot stays quoted in brackets
         Arguments.of(SQL_SERVER, "T.[A.B] + T.C", "o.[A.B] + o.C"),
         Arguments.of(SQL_SERVER, "T.[A.B] between 1 and 2", "o.[A.B] between 1 and 2"),
         Arguments.of(SQL_SERVER, "[T].[A.B] + T.C", "o.[A.B] + o.C"),
         // mysql quotes the column with a dot in backquotes. A backquoted column with a dot
         // reported by the parser is not found, as before Bug #77782
         Arguments.of(MYSQL, "T.\"A.B\" + T.C", "o.`\"A.B\"` + o.C"),
         Arguments.of(MYSQL, "T.`A.B` + T.C", "T.`A.B` + o.C"),
         Arguments.of(MYSQL, "T.`A.B` between 1 and 2", "o.`A.B` between 1 and 2"),
         Arguments.of(MYSQL, "T.\"A\"\"B\" + T.C", "o.\"A\"\"B\" + o.C"),
         // the parser reports the name "A" as T."A", which is not split as a quoted name
         // (not replaced, as before Bug #77782), ColumnIterator reports it as written
         Arguments.of(POSTGRESQL, "T.\"\"\"A\"\"\"", "T.\"\"\"A\"\"\""),
         Arguments.of(POSTGRESQL, "T.\"\"\"A\"\"\" between 1 and 2",
                      "o.\"\"\"A\"\"\" between 1 and 2"));
   }

   @ParameterizedTest
   @MethodSource("expressions")
   void tableOfQuotedNameIsReplaced(String product, String exp, String expected)
      throws Exception
   {
      assertEquals(expected + " = 1", evaluate(exp, product));
   }

   private static String evaluate(String exp, String product) throws Exception {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable("T");
      cond.setCondition(new XBinaryCondition(new XExpression(exp, XExpression.EXPRESSION),
                                             new XExpression("1", XExpression.VALUE), "="));
      JDBCDataSource source = null;

      if(product != null) {
         source = new JDBCDataSource();
         source.setName("ds");
         source.setRuntimeProductName(product);
      }

      XPrincipal user = new XPrincipal(new IdentityID("viewer", null));
      return cond.evaluate(null, new String[] { "T" }, new String[] { "o" },
                           new String[0], source, new VariableTable(), user, false);
   }

   private static final String POSTGRESQL = "postgresql";
   private static final String ORACLE = "oracle";
   private static final String SQL_SERVER = "sql server";
   private static final String H2 = "h2";
   private static final String MYSQL = "mysql";

   @Configuration
   static class TestConfig {
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }
}
