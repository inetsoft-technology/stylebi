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
         Arguments.of("T.A /* T.A */", "o.A /* T.A */"));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("expressions")
   void tableIsReplacedOutsideLiterals(String exp, String expected) throws Exception {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable("T");
      cond.setCondition(new XBinaryCondition(new XExpression(exp, XExpression.EXPRESSION),
                                             new XExpression("1", XExpression.VALUE), "="));

      XPrincipal user = new XPrincipal(new IdentityID("viewer", null));
      String result = cond.evaluate(null, new String[] { "T" }, new String[] { "o" },
                                    new String[0], null, new VariableTable(), user, false);

      assertEquals(expected + " = 1", result);
   }

   @Configuration
   static class TestConfig {
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }
}
