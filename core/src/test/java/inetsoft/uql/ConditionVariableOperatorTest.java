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
package inetsoft.uql;

import inetsoft.test.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A variable as the value of each operation: the text reference {@code $(name)} and a
 * UserVariable are replaced from the variable table, and a variable that is not set ignores the
 * condition. Array values for ONE_OF and BETWEEN are in ConditionVariableValueTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionVariableOperatorTest {
   private static Condition condition(String type, int op, Object value) {
      Condition cond = new Condition(type);
      cond.setOperation(op);
      cond.addValue(value);
      return cond;
   }

   private static Condition resolved(String type, int op, Object reference, Object value)
      throws Exception
   {
      Condition cond = condition(type, op, reference);
      VariableTable vars = new VariableTable();
      vars.put("v", value);
      cond.replaceVariable(vars);
      return cond;
   }

   @Nested
   class StringOperations {
      @ParameterizedTest
      @ValueSource(strings = { "$(v)" })
      void startingWith_textReference(String reference) throws Exception {
         Condition cond = resolved(XSchema.STRING, XCondition.STARTING_WITH, reference, "ab");

         assertFalse(cond.isIgnored());
         assertTrue(cond.evaluate("abc"));
         assertFalse(cond.evaluate("xab"));
      }

      @Test
      void startingWith_userVariable() throws Exception {
         Condition cond =
            resolved(XSchema.STRING, XCondition.STARTING_WITH, new UserVariable("v"), "ab");

         assertTrue(cond.evaluate("abc"));
         assertFalse(cond.evaluate("xab"));
      }

      @Test
      void contains_textReference() throws Exception {
         Condition cond = resolved(XSchema.STRING, XCondition.CONTAINS, "$(v)", "bc");

         assertTrue(cond.evaluate("abcd"));
         assertFalse(cond.evaluate("abd"));
      }

      @Test
      void contains_isCaseInsensitiveByDefault() throws Exception {
         Condition cond = resolved(XSchema.STRING, XCondition.CONTAINS, "$(v)", "BC");

         assertTrue(cond.evaluate("abcd"));
      }

      @Test
      void contains_canBeCaseSensitive() throws Exception {
         Condition cond = condition(XSchema.STRING, XCondition.CONTAINS, "$(v)");
         cond.setCaseSensitive(true);
         VariableTable vars = new VariableTable();
         vars.put("v", "BC");
         cond.replaceVariable(vars);

         assertFalse(cond.evaluate("abcd"));
         assertTrue(cond.evaluate("aBCd"));
      }

      @Test
      void like_percentAndUnderscoreAreWildcards() throws Exception {
         Condition cond = resolved(XSchema.STRING, XCondition.LIKE, "$(v)", "a%d_");

         assertTrue(cond.evaluate("abcde"));
         assertFalse(cond.evaluate("abcd"));
      }

      @Test
      void like_questionMarkIsLiteral() throws Exception {
         Condition cond = resolved(XSchema.STRING, XCondition.LIKE, "$(v)", "a%d?");

         assertTrue(cond.evaluate("abcd?"));
         assertFalse(cond.evaluate("abcde"));
      }

      @Test
      void like_regexCharactersInTheValueAreLiteral() throws Exception {
         Condition cond = resolved(XSchema.STRING, XCondition.LIKE, "$(v)", "a.c");

         assertTrue(cond.evaluate("a.c"));
         assertFalse(cond.evaluate("abc"));
      }

      @Test
      void numberVariableOnAStringOperation_isComparedAsText() throws Exception {
         Condition cond = resolved(XSchema.STRING, XCondition.STARTING_WITH, "$(v)", 12);

         assertTrue(cond.evaluate("123"));
         assertFalse(cond.evaluate("213"));
      }

      @Test
      void variableInsideTheTextIsNotReplaced() throws Exception {
         // only a value that is exactly $(name) is a variable
         Condition cond = resolved(XSchema.STRING, XCondition.STARTING_WITH, "pre-$(v)", "ab");

         assertEquals("pre-$(v)", cond.getValue(0));
         assertFalse(cond.isIgnored());
      }
   }

   @Nested
   class DateIn {
      @Test
      void periodFromAVariable_isUsed() throws Exception {
         Condition cond = resolved(XSchema.DATE, XCondition.DATE_IN, "$(v)", "this year");

         assertTrue(cond.evaluate(new Date()));
         assertFalse(cond.evaluate(new Date(0)));
      }

      @Test
      void unknownPeriodFromAVariable_matchesNothing() throws Exception {
         Condition cond = resolved(XSchema.DATE, XCondition.DATE_IN, "$(v)", "not a period");

         assertFalse(cond.isIgnored());
         assertFalse(cond.evaluate(new Date()));
      }
   }

   @Nested
   class MissingVariable {
      @ParameterizedTest
      @ValueSource(ints = { XCondition.EQUAL_TO, XCondition.LESS_THAN, XCondition.GREATER_THAN,
                            XCondition.STARTING_WITH, XCondition.CONTAINS, XCondition.LIKE,
                            XCondition.ONE_OF })
      void textReference_ignoresTheCondition(int op) throws Exception {
         Condition cond = condition(XSchema.STRING, op, "$(v)");
         cond.replaceVariable(new VariableTable());

         assertTrue(cond.isIgnored());
      }

      @ParameterizedTest
      @ValueSource(ints = { XCondition.EQUAL_TO, XCondition.STARTING_WITH, XCondition.CONTAINS,
                            XCondition.LIKE, XCondition.ONE_OF })
      void userVariable_ignoresTheCondition(int op) throws Exception {
         Condition cond = condition(XSchema.STRING, op, new UserVariable("v"));
         cond.replaceVariable(new VariableTable());

         assertTrue(cond.isIgnored());
      }

      @Test
      void dateIn_ignoresTheCondition() throws Exception {
         Condition cond = condition(XSchema.DATE, XCondition.DATE_IN, "$(v)");
         cond.replaceVariable(new VariableTable());

         assertTrue(cond.isIgnored());
      }

      @Test
      void nullVariableTable_changesNothing() {
         Condition cond = condition(XSchema.STRING, XCondition.EQUAL_TO, "$(v)");
         cond.replaceVariable(null);

         assertFalse(cond.isIgnored());
         assertEquals("$(v)", cond.getValue(0));
      }

      @Test
      void replacingAgainWithAValue_clearsIgnored() throws Exception {
         Condition cond = condition(XSchema.STRING, XCondition.EQUAL_TO, "$(v)");
         cond.replaceVariable(new VariableTable());
         assertTrue(cond.isIgnored());

         VariableTable vars = new VariableTable();
         vars.put("v", "x");
         cond.replaceVariable(vars);

         assertFalse(cond.isIgnored());
         assertTrue(cond.evaluate("x"));
      }
   }

   @Nested
   class TypedConditions {
      @Test
      void textReferenceOnANonStringType_isNotAVariable() throws Exception {
         // a text value of a typed condition is converted to the type when it is read, so
         // "$(v)" becomes 0 and is not seen as a variable; a typed condition holds a UserVariable
         Condition cond = resolved(XSchema.INTEGER, XCondition.EQUAL_TO, "$(v)", "5");

         assertEquals(0, cond.getValue(0));
         assertFalse(cond.isIgnored());
         assertFalse(cond.evaluate(5));
      }

      @Test
      void textReference_worksWhenTheValueTypeIsNotConverted() throws Exception {
         Condition cond = new Condition(false, false);
         cond.setType(XSchema.INTEGER);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue("$(v)");
         VariableTable vars = new VariableTable();
         vars.put("v", "5");
         cond.replaceVariable(vars);

         assertTrue(cond.evaluate(5));
      }

      @Test
      void integerCondition_stringVariableThatIsANumber_matches() throws Exception {
         Condition cond = resolved(XSchema.INTEGER, XCondition.EQUAL_TO,
                                   new UserVariable("v"), "5");

         assertTrue(cond.evaluate(5));
         assertFalse(cond.evaluate(6));
      }

      @Test
      void integerCondition_doubleVariable_isComparedNumerically() throws Exception {
         Condition cond = resolved(XSchema.INTEGER, XCondition.GREATER_THAN,
                                   new UserVariable("v"), 4.5);

         assertTrue(cond.evaluate(5));
         assertFalse(cond.evaluate(4));
      }

      @Test
      void integerCondition_nonNumericVariable_matchesNothingWithoutThrowing() throws Exception {
         Condition cond = resolved(XSchema.INTEGER, XCondition.EQUAL_TO,
                                   new UserVariable("v"), "abc");

         assertFalse(cond.isIgnored());
         assertDoesNotThrow(() -> cond.evaluate(5));
         assertFalse(cond.evaluate(5));
      }

      @Test
      void dateCondition_dateVariable_isComparedChronologically() throws Exception {
         Date limit = new Date(1710460800000L);
         Condition cond = resolved(XSchema.DATE, XCondition.LESS_THAN,
                                   new UserVariable("v"), limit);

         assertTrue(cond.evaluate(new Date(limit.getTime() - 1000)));
         assertFalse(cond.evaluate(new Date(limit.getTime() + 1000)));
      }

      @Test
      void booleanCondition_textVariable_isConverted() throws Exception {
         Condition cond = resolved(XSchema.BOOLEAN, XCondition.EQUAL_TO,
                                   new UserVariable("v"), "true");

         assertTrue(cond.evaluate(Boolean.TRUE));
         assertFalse(cond.evaluate(Boolean.FALSE));
      }
   }
}
