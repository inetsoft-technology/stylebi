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

import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
      void like_percentAndQuestionMarkAreWildcards() throws Exception {
         Condition cond = resolved(XSchema.STRING, XCondition.LIKE, "$(v)", "a%d?");

         assertTrue(cond.evaluate("abcde"));
         assertTrue(cond.evaluate("abcd?"));
         assertFalse(cond.evaluate("abcd"));
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

   @Nested
   class HardeningRows {
      private java.util.TimeZone oldZone;
      private java.util.Locale oldLocale;

      @BeforeEach
      void saveDefaults() {
         oldZone = java.util.TimeZone.getDefault();
         oldLocale = java.util.Locale.getDefault();
      }

      @AfterEach
      void restoreDefaults() {
         java.util.TimeZone.setDefault(oldZone);
         java.util.Locale.setDefault(oldLocale);
         // the cases above create the CoreTool thread local formats on first use under the
         // temporary default zone, and the thread local keeps that zone for every later test
         // that runs on this thread (CoreToolBcDateTest, GregorianCalendarDefaultLocaleTest)
         inetsoft.util.CoreTool.yearFmt.remove();
         inetsoft.util.CoreTool.monthFmt.remove();
         inetsoft.util.CoreTool.dayFmt.remove();
         inetsoft.util.CoreTool.dateFmt.remove();
         inetsoft.util.CoreTool.timeFmt_old.remove();
         inetsoft.util.CoreTool.timeInstantFmt_old.remove();
         inetsoft.util.CoreTool.timeFmt.remove();
         inetsoft.util.CoreTool.timeInstantFmt.remove();
      }

      @DisplayName("C16 DATE_IN this year follows the default time zone")
      @ParameterizedTest(name = "[{index}] {0}")
      @ValueSource(strings = { "UTC", "America/Los_Angeles", "Pacific/Auckland" })
      void c16_dateInThisYear(String zone) throws Exception {
         java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone));
         java.util.Calendar cal = java.util.Calendar.getInstance();
         int year = cal.get(java.util.Calendar.YEAR);

         cal.clear();
         cal.set(year, java.util.Calendar.JANUARY, 1, 0, 0, 0);
         Date start = cal.getTime();
         cal.set(year, java.util.Calendar.DECEMBER, 31, 23, 59, 59);
         Date end = cal.getTime();

         Condition cond = resolved(XSchema.DATE, XCondition.DATE_IN, "$(v)", "this year");
         assertTrue(cond.evaluate(start), "Jan 1 00:00");
         assertTrue(cond.evaluate(end), "Dec 31 23:59:59");
         assertFalse(cond.evaluate(new Date(start.getTime() - 1)), "1 ms before");
         assertFalse(cond.evaluate(new Date(end.getTime() + 1000)),
                     "Jan 1 next year 00:00:00");
      }

      @Disabled("mismatch C17 #81112")
      @DisplayName("C17 case-insensitive CONTAINS/STARTING_WITH under tr-TR")
      @ParameterizedTest(name = "[{index}] op {0}")
      @CsvSource({ "CONTAINS, title", "STARTING_WITH, ti" })
      void c17_turkishLocale(String op, String text) throws Exception {
         java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
         int code = op.equals("CONTAINS") ? XCondition.CONTAINS : XCondition.STARTING_WITH;
         Condition cond = condition(XSchema.STRING, code, text);
         cond.setCaseSensitive(false);
         assertTrue(cond.evaluate("TITLE"));
      }

      @Disabled("mismatch C18 #81118")
      @DisplayName("C18 numeric equality keeps long precision")
      @ParameterizedTest(name = "[{index}] {0}")
      @ValueSource(strings = { XSchema.LONG, XSchema.INTEGER })
      void c18_longPrecision(String type) {
         Condition cond = condition(type, XCondition.EQUAL_TO, 9007199254740993L);
         assertFalse(cond.evaluate(9007199254740992L), "different long");
         assertTrue(cond.evaluate(9007199254740993L), "same long");
      }

      @DisplayName("C05b CONTAINS honours the caseSensitive flag in memory")
      @Test
      void c05b_containsCaseFlag() {
         Condition insensitive = condition(XSchema.STRING, XCondition.CONTAINS, "A");
         insensitive.setCaseSensitive(false);
         Condition sensitive = condition(XSchema.STRING, XCondition.CONTAINS, "A");
         sensitive.setCaseSensitive(true);

         for(String row : new String[] { "a", "A", "b" }) {
            assertEquals(!row.equals("b"), insensitive.evaluate(row), "insensitive " + row);
            assertEquals(row.equals("A"), sensitive.evaluate(row), "sensitive " + row);
         }
      }

      @DisplayName("C18b numeric equality of small values")
      @ParameterizedTest(name = "[{index}] {0}")
      @ValueSource(strings = { XSchema.INTEGER, XSchema.LONG })
      void c18b_smallValues(String type) {
         Condition cond = condition(type, XCondition.EQUAL_TO, 42);
         assertTrue(cond.evaluate(42), "42");
         assertTrue(cond.evaluate(42L), "42L");
         assertTrue(cond.evaluate(42.0), "42.0");
         assertFalse(cond.evaluate(43), "43");
         assertFalse(cond.evaluate(42.5), "42.5");
      }
   }
}
