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
 * Bug #77768, since Bug #77661 the parser reports a quoted name with a doubled quote
 * ("A""B") without the escape (A"B), so the VPM condition no longer found the column in the
 * expression and kept the table name instead of the table alias. A column found by
 * ColumnIterator, when the parser stops early, keeps the doubled quote.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class,
                                  VpmConditionEscapedQuoteNameTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VpmConditionEscapedQuoteNameTest {
   static Stream<Arguments> expressions() {
      return Stream.of(
         // the parser reads the whole expression
         Arguments.of(POSTGRESQL, "T.\"A\"\"B\"", "o.\"A\"\"B\""),
         Arguments.of(POSTGRESQL, "T.\"A\"\"B\" + T.\"C\"\"D\"", "o.\"A\"\"B\" + o.\"C\"\"D\""),
         Arguments.of(POSTGRESQL, "T.\"A\"\"B\" || T.C", "o.\"A\"\"B\" || o.\"C\""),
         // the parser stops at div, the columns are found by ColumnIterator
         Arguments.of(POSTGRESQL, "T.\"A\"\"B\" div 2", "o.\"A\"\"B\" div 2"),
         // a literal with the same text is not a name
         Arguments.of(POSTGRESQL, "T.\"A\"\"B\" || 'T.\"A\"\"B\"'",
                      "o.\"A\"\"B\" || 'T.\"A\"\"B\"'"),
         Arguments.of(H2, "T.\"A\"\"B\"", "o.\"A\"\"B\""),
         Arguments.of(ORACLE, "T.\"A\"\"B\"", "o.\"A\"\"B\""));
   }

   @ParameterizedTest
   @MethodSource("expressions")
   void tableOfEscapedQuoteNameIsReplaced(String product, String exp, String expected)
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
      JDBCDataSource source = new JDBCDataSource();
      source.setName("ds");
      source.setRuntimeProductName(product);
      XPrincipal user = new XPrincipal(new IdentityID("viewer", null));
      return cond.evaluate(null, new String[] { "T" }, new String[] { "o" },
                           new String[0], source, new VariableTable(), user, false);
   }

   private static final String POSTGRESQL = "postgresql";
   private static final String H2 = "h2";
   private static final String ORACLE = "oracle";

   @Configuration
   static class TestConfig {
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }
}
