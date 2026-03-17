/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Condition}.
 */
public class ConditionTest {

   // ---- EQUAL_TO ----------------------------------------------------------------

   @Test
   void equalToString_matches() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("hello");
      assertTrue(cond.evaluate("hello"));
   }

   @Test
   void equalToString_doesNotMatch() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("hello");
      assertFalse(cond.evaluate("world"));
   }

   @Test
   void equalToInteger_matches() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(42);
      assertTrue(cond.evaluate(42));
   }

   @Test
   void equalToInteger_doesNotMatch() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(42);
      assertFalse(cond.evaluate(99));
   }

   @Test
   void equalToNull_valueIsNull_notMatches() {
      // EQUAL_TO with null value — for null checks use NULL operator
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("test");
      assertFalse(cond.evaluate(null));
   }

   @Test
   void equalToString_caseInsensitive() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setCaseSensitive(false);
      cond.addValue("Hello");
      assertTrue(cond.evaluate("hello"));
   }

   @Test
   void equalToString_caseSensitive() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setCaseSensitive(true);
      cond.addValue("Hello");
      assertFalse(cond.evaluate("hello"));
   }

   // ---- NOT EQUAL (negated EQUAL_TO) -------------------------------------------

   @Test
   void notEqualTo_matches() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setNegated(true);
      cond.addValue("hello");
      assertTrue(cond.evaluate("world"));
   }

   @Test
   void notEqualTo_doesNotMatch() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setNegated(true);
      cond.addValue("hello");
      assertFalse(cond.evaluate("hello"));
   }

   // ---- LESS_THAN ---------------------------------------------------------------

   @Test
   void lessThan_valueIsLess() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.LESS_THAN);
      cond.addValue(10);
      assertTrue(cond.evaluate(5));
   }

   @Test
   void lessThan_valueIsEqual_withoutEqual() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.LESS_THAN);
      cond.setEqual(false);
      cond.addValue(10);
      assertFalse(cond.evaluate(10));
   }

   @Test
   void lessThan_valueIsEqual_withEqual() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.LESS_THAN);
      cond.setEqual(true);
      cond.addValue(10);
      assertTrue(cond.evaluate(10));
   }

   @Test
   void lessThan_valueIsGreater() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.LESS_THAN);
      cond.addValue(10);
      assertFalse(cond.evaluate(15));
   }

   @Test
   void lessThan_nullValue_returnsFalse() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.LESS_THAN);
      cond.addValue(10);
      assertFalse(cond.evaluate(null));
   }

   // ---- GREATER_THAN ------------------------------------------------------------

   @Test
   void greaterThan_valueIsGreater() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(10);
      assertTrue(cond.evaluate(15));
   }

   @Test
   void greaterThan_valueIsEqual_withoutEqual() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setEqual(false);
      cond.addValue(10);
      assertFalse(cond.evaluate(10));
   }

   @Test
   void greaterThan_valueIsEqual_withEqual() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setEqual(true);
      cond.addValue(10);
      assertTrue(cond.evaluate(10));
   }

   @Test
   void greaterThan_valueIsLess() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(10);
      assertFalse(cond.evaluate(5));
   }

   @Test
   void greaterThan_nullValue_returnsFalse() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(10);
      assertFalse(cond.evaluate(null));
   }

   // ---- BETWEEN -----------------------------------------------------------------

   @Test
   void between_valueInRange() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(1);
      cond.addValue(10);
      assertTrue(cond.evaluate(5));
   }

   @Test
   void between_valueAtLowerBound() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(1);
      cond.addValue(10);
      assertTrue(cond.evaluate(1));
   }

   @Test
   void between_valueAtUpperBound() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(1);
      cond.addValue(10);
      assertTrue(cond.evaluate(10));
   }

   @Test
   void between_valueOutOfRange() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(1);
      cond.addValue(10);
      assertFalse(cond.evaluate(11));
   }

   @Test
   void between_valueBelowRange() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(1);
      cond.addValue(10);
      assertFalse(cond.evaluate(0));
   }

   @Test
   void between_nullValue_returnsFalse() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(1);
      cond.addValue(10);
      assertFalse(cond.evaluate(null));
   }

   // ---- ONE_OF ------------------------------------------------------------------

   @Test
   void oneOf_valueInList() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.addValue("a");
      cond.addValue("b");
      cond.addValue("c");
      assertTrue(cond.evaluate("b"));
   }

   @Test
   void oneOf_valueNotInList() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.addValue("a");
      cond.addValue("b");
      cond.addValue("c");
      assertFalse(cond.evaluate("d"));
   }

   @Test
   void oneOf_nullValue_returnsFalse() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.addValue("a");
      assertFalse(cond.evaluate(null));
   }

   @Test
   void oneOf_negated_valueInList() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.setNegated(true);
      cond.addValue("a");
      cond.addValue("b");
      assertFalse(cond.evaluate("a"));
   }

   @Test
   void oneOf_negated_valueNotInList() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.setNegated(true);
      cond.addValue("a");
      cond.addValue("b");
      assertTrue(cond.evaluate("z"));
   }

   @Test
   void oneOf_integerValues() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.ONE_OF);
      cond.addValue(1);
      cond.addValue(2);
      cond.addValue(3);
      assertTrue(cond.evaluate(2));
      assertFalse(cond.evaluate(9));
   }

   // ---- NULL --------------------------------------------------------------------

   @Test
   void nullOp_nullValue() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.NULL);
      assertTrue(cond.evaluate(null));
   }

   @Test
   void nullOp_emptyString() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.NULL);
      assertTrue(cond.evaluate(""));
   }

   @Test
   void nullOp_nonNullValue() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.NULL);
      assertFalse(cond.evaluate("notnull"));
   }

   @Test
   void nullOp_negated_nonNullValue() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.NULL);
      cond.setNegated(true);
      assertTrue(cond.evaluate("notnull"));
   }

   @Test
   void nullOp_negated_nullValue() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.NULL);
      cond.setNegated(true);
      assertFalse(cond.evaluate(null));
   }

   // ---- STARTING_WITH -----------------------------------------------------------

   @Test
   void startingWith_matches() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.STARTING_WITH);
      cond.addValue("hel");
      assertTrue(cond.evaluate("hello"));
   }

   @Test
   void startingWith_doesNotMatch() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.STARTING_WITH);
      cond.addValue("hel");
      assertFalse(cond.evaluate("world"));
   }

   @Test
   void startingWith_nullValue_returnsFalse() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.STARTING_WITH);
      cond.addValue("hel");
      assertFalse(cond.evaluate(null));
   }

   @Test
   void startingWith_caseInsensitive() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.STARTING_WITH);
      cond.setCaseSensitive(false);
      cond.addValue("HEL");
      assertTrue(cond.evaluate("hello"));
   }

   @Test
   void startingWith_caseSensitive_doesNotMatch() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.STARTING_WITH);
      cond.setCaseSensitive(true);
      cond.addValue("HEL");
      assertFalse(cond.evaluate("hello"));
   }

   // ---- CONTAINS ----------------------------------------------------------------

   @Test
   void contains_matches() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.CONTAINS);
      cond.addValue("ell");
      assertTrue(cond.evaluate("hello"));
   }

   @Test
   void contains_doesNotMatch() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.CONTAINS);
      cond.addValue("xyz");
      assertFalse(cond.evaluate("hello"));
   }

   @Test
   void contains_nullValue_returnsFalse() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.CONTAINS);
      cond.addValue("ell");
      assertFalse(cond.evaluate(null));
   }

   @Test
   void contains_caseInsensitive() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.CONTAINS);
      cond.setCaseSensitive(false);
      cond.addValue("ELL");
      assertTrue(cond.evaluate("hello"));
   }

   @Test
   void contains_caseSensitive_doesNotMatch() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.CONTAINS);
      cond.setCaseSensitive(true);
      cond.addValue("ELL");
      assertFalse(cond.evaluate("hello"));
   }

   // ---- LIKE --------------------------------------------------------------------

   @Test
   void like_percentWildcard_matches() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.LIKE);
      cond.addValue("he%");
      assertTrue(cond.evaluate("hello"));
   }

   @Test
   void like_percentWildcardInMiddle_matches() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.LIKE);
      cond.addValue("h%o");
      assertTrue(cond.evaluate("hello"));
   }

   @Test
   void like_questionMarkWildcard_matches() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.LIKE);
      cond.addValue("hell?");
      assertTrue(cond.evaluate("hello"));
   }

   @Test
   void like_exactMatch() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.LIKE);
      cond.addValue("hello");
      assertTrue(cond.evaluate("hello"));
      assertFalse(cond.evaluate("world"));
   }

   @Test
   void like_doesNotMatch() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.LIKE);
      cond.addValue("he%");
      assertFalse(cond.evaluate("world"));
   }

   @Test
   void like_nullValue_returnsFalse() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.LIKE);
      cond.addValue("he%");
      assertFalse(cond.evaluate(null));
   }

   @Test
   void like_caseInsensitive_matches() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.LIKE);
      cond.setCaseSensitive(false);
      cond.addValue("HE%");
      assertTrue(cond.evaluate("hello"));
   }

   // ---- getLikePattern (static) -------------------------------------------------

   @Test
   void getLikePattern_escapesSpecialRegexChars() {
      // Ensure dots in LIKE pattern are treated literally
      java.util.regex.Pattern p = Condition.getLikePattern("a.b", true);
      assertFalse(p.matcher("axb").find()); // dot should be literal
      assertTrue(p.matcher("a.b").find());
   }

   @Test
   void getLikePattern_percentBecomesWildcard() {
      java.util.regex.Pattern p = Condition.getLikePattern("a%b", true);
      assertTrue(p.matcher("axyzb").find());
      assertTrue(p.matcher("ab").find());
   }

   @Test
   void getLikePattern_questionMarkBecomesAnyChar() {
      java.util.regex.Pattern p = Condition.getLikePattern("a?b", true);
      assertTrue(p.matcher("axb").find());
      assertFalse(p.matcher("ab").find());
   }

   // ---- isVariable (static) ----------------------------------------------------

   @Test
   void isVariable_validVariable() {
      assertTrue(Condition.isVariable("$(myVar)"));
   }

   @Test
   void isVariable_emptyName_returnsFalse() {
      assertFalse(Condition.isVariable("$()"));
   }

   @Test
   void isVariable_plainString_returnsFalse() {
      assertFalse(Condition.isVariable("hello"));
   }

   @Test
   void isVariable_null_returnsFalse() {
      assertFalse(Condition.isVariable(null));
   }

   // ---- Negation ----------------------------------------------------------------

   @Test
   void negated_lessThan_reverseResult() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.LESS_THAN);
      cond.setNegated(true);
      cond.addValue(10);
      // 15 is NOT less than 10, but negated => true
      assertTrue(cond.evaluate(15));
      // 5 IS less than 10, but negated => false
      assertFalse(cond.evaluate(5));
   }

   @Test
   void negated_between_reverseResult() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.setNegated(true);
      cond.addValue(1);
      cond.addValue(10);
      assertFalse(cond.evaluate(5));  // 5 is in range; negated => false
      assertTrue(cond.evaluate(20));  // 20 is not in range; negated => true
   }

   // ---- String comparisons via double / integer --------------------------------

   @Test
   void doubleEqualTo_matches() {
      Condition cond = new Condition(XSchema.DOUBLE);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(3.14);
      assertTrue(cond.evaluate(3.14));
      assertFalse(cond.evaluate(3.15));
   }

   @Test
   void doubleLessThan_matches() {
      Condition cond = new Condition(XSchema.DOUBLE);
      cond.setOperation(XCondition.LESS_THAN);
      cond.addValue(5.0);
      assertTrue(cond.evaluate(4.9));
      assertFalse(cond.evaluate(5.1));
   }

   // ---- ignored flag -----------------------------------------------------------

   @Test
   void ignored_alwaysReturnsTrue() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("hello");
      cond.setIgnored(true);
      // Even "world" should return true when ignored
      assertTrue(cond.evaluate("world"));
      assertTrue(cond.evaluate(null));
   }

   // ---- getValueCount / addValue / removeAllValues -----------------------------

   @Test
   void getValueCount_afterAddingValues() {
      Condition cond = new Condition(XSchema.STRING);
      cond.addValue("a");
      cond.addValue("b");
      assertEquals(2, cond.getValueCount());
   }

   @Test
   void removeAllValues_clearsValues() {
      Condition cond = new Condition(XSchema.STRING);
      cond.addValue("a");
      cond.addValue("b");
      cond.removeAllValues();
      assertEquals(0, cond.getValueCount());
   }

   @Test
   void getValue_outOfBounds_returnsNull() {
      Condition cond = new Condition(XSchema.STRING);
      assertNull(cond.getValue(0));
      assertNull(cond.getValue(-1));
   }

   // ---- DATE_IN date range -----------------------------------------------------

   @Test
   void dateIn_thisYear_matchesCurrentYear() {
      Condition cond = new Condition(XSchema.DATE);
      cond.setOperation(XCondition.DATE_IN);
      cond.addValue("this year");

      Calendar cal = Calendar.getInstance();
      Date thisYear = cal.getTime();
      assertTrue(cond.evaluate(thisYear));
   }

   @Test
   void dateIn_thisYear_doesNotMatchLastYear() {
      Condition cond = new Condition(XSchema.DATE);
      cond.setOperation(XCondition.DATE_IN);
      cond.addValue("this year");

      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.YEAR, -1);
      Date lastYear = cal.getTime();
      assertFalse(cond.evaluate(lastYear));
   }

   @Test
   void dateIn_lastYear_matchesPreviousYear() {
      Condition cond = new Condition(XSchema.DATE);
      cond.setOperation(XCondition.DATE_IN);
      cond.addValue("last year");

      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.YEAR, -1);
      Date lastYear = cal.getTime();
      assertTrue(cond.evaluate(lastYear));
   }

   @Test
   void dateIn_thisMonth_matchesCurrentMonth() {
      Condition cond = new Condition(XSchema.DATE);
      cond.setOperation(XCondition.DATE_IN);
      cond.addValue("this month");

      Date now = new Date();
      assertTrue(cond.evaluate(now));
   }

   @Test
   void dateIn_thisMonth_doesNotMatchLastMonth() {
      Condition cond = new Condition(XSchema.DATE);
      cond.setOperation(XCondition.DATE_IN);
      cond.addValue("this month");

      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.MONTH, -1);
      assertFalse(cond.evaluate(cal.getTime()));
   }

   @Test
   void dateIn_thisWeek_matchesCurrentWeek() {
      Condition cond = new Condition(XSchema.DATE);
      cond.setOperation(XCondition.DATE_IN);
      cond.addValue("this week");

      Date now = new Date();
      assertTrue(cond.evaluate(now));
   }

   @Test
   void dateIn_lastWeek_doesNotMatchCurrentWeek() {
      Condition cond = new Condition(XSchema.DATE);
      cond.setOperation(XCondition.DATE_IN);
      cond.addValue("last week");

      Date now = new Date();
      assertFalse(cond.evaluate(now));
   }

   @Test
   void dateIn_nullValue_returnsFalse() {
      Condition cond = new Condition(XSchema.DATE);
      cond.setOperation(XCondition.DATE_IN);
      cond.addValue("this year");
      assertFalse(cond.evaluate(null));
   }

   @Test
   void dateIn_nonDateValue_returnsFalse() {
      Condition cond = new Condition(XSchema.DATE);
      cond.setOperation(XCondition.DATE_IN);
      cond.addValue("this year");
      assertFalse(cond.evaluate("not a date"));
   }

   // ---- isInDateRange (public helper) ------------------------------------------

   @Test
   void isInDateRange_thisYear() {
      Condition cond = new Condition();
      Date now = new Date();
      assertTrue(cond.isInDateRange("this year", now));
   }

   @Test
   void isInDateRange_lastYear() {
      Condition cond = new Condition();
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.YEAR, -1);
      assertTrue(cond.isInDateRange("last year", cal.getTime()));
   }

   @Test
   void isInDateRange_nullDate() {
      Condition cond = new Condition();
      assertFalse(cond.isInDateRange("this year", null));
   }

   @Test
   void isInDateRange_nullRange() {
      Condition cond = new Condition();
      assertFalse(cond.isInDateRange(null, new Date()));
   }

   @Test
   void isInDateRange_nonDateObject() {
      Condition cond = new Condition();
      assertFalse(cond.isInDateRange("this year", "not a date"));
   }

   // ---- Parameterized: operator symmetry ----------------------------------------

   static Stream<Arguments> comparisonOperators() {
      // Arguments: (op, conditionValue, evaluatedValue, expectedResult)
      // LESS_THAN: evaluate(testValue) → testValue < condValue
      // GREATER_THAN: evaluate(testValue) → testValue > condValue
      return Stream.of(
         Arguments.of(XCondition.EQUAL_TO, 5, 5, true),
         Arguments.of(XCondition.EQUAL_TO, 5, 6, false),
         Arguments.of(XCondition.LESS_THAN, 10, 5, true),   // 5 < 10 → true
         Arguments.of(XCondition.LESS_THAN, 5, 10, false),  // 10 < 5 → false
         Arguments.of(XCondition.GREATER_THAN, 5, 10, true), // 10 > 5 → true
         Arguments.of(XCondition.GREATER_THAN, 10, 5, false) // 5 > 10 → false
      );
   }

   @ParameterizedTest
   @MethodSource("comparisonOperators")
   void comparisonOperator_integerValues(int op, int condValue, int testValue, boolean expected) {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(op);
      cond.addValue(condValue);
      assertEquals(expected, cond.evaluate(testValue));
   }
}
