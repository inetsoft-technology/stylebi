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

import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Calendar;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Decision table for evaluate()
 * -----------------------------------------------------------------------------------------------
 * evaluate() has two axes: op (which if/else branch) and declared type (which Comparer, only when
 * the op calls compare). Full op×type matrix is wasteful; one type per op is incomplete. Prefer
 * shared parameterized tests per cross-cutting dimension, not one suite per op.
 *
 * | op                          | uses comp? | op-specific branches (Section 1)                          | shares in Section 2 |
 * |------------------------------|-----------|------------------------------------------------------------|----------------------|
 * | EQUAL_TO                     | yes       | null does NOT short-circuit (falls into comp.compare)      | 2b comparer-type, 2e ignoreNullValue |
 * | LESS_THAN / GREATER_THAN     | yes       | equal-flag (<= / < , >= / >), null short-circuits            | 2a null, 2b comparer-type |
 * | BETWEEN                      | yes       | 5 boundary outcomes + reversed bounds + partial value list   | 2a null, 2b comparer-type |
 * | ONE_OF                       | yes       | in/not-in list, negated, cross-type numeric-string fallback   | 2a null, 2b comparer-type |
 * | STARTING_WITH / CONTAINS / LIKE | no (string-coerced via stringValue()) | wildcard semantics per op | 2a null, 2c case-sensitivity |
 * | NULL                          | no        | null/empty-string/non-null, negated                          | (negation itself is 2d, shared once) |
 * | DATE_IN                       | no (delegates to isInDateRange) | merged DC-period families                | none |
 * | ALL ops (end of evaluate())   | n/a       | -                                                            | 2d negation flips result once, mechanism is op-independent |
 *
 * Section 2b coverage (ops that use `comp`):
 * - EQUAL_TO: full sweep of all 5 Comparer types (proves the shared `if(comp == null)` selection once).
 * - LESS_THAN / GREATER_THAN / BETWEEN: DATE only — op-specific compare consumption, not re-selection.
 * - ONE_OF: BOOLEAN (single-element list) + DATE (sort+binarySearch); path is heavier than one compare().
 * - ROLE + ordering ops: skipped — RoleComparer returns a mismatch score, not a true ordering / no UI path.
 *
 * -----------------------------------------------------------------------------------------------
 * Intent vs implementation suspects
 * -----------------------------------------------------------------------------------------------
 *
 * [Fixed] "tomorrow" in isInDateRange used `(d1 + 1) == d2` — same as "yesterday", so real
 *         tomorrow was false and yesterday was true for "tomorrow". Fixed to `(d1 - 1) == d2`.
 *         -> KnownBugs.isInDateRangeTomorrowActuallyChecksYesterday /
 *            isInDateRangeTomorrowDoesNotMatchYesterday now assert the corrected behavior.
 *
 * [Note] stringValue's Object[] comma-join branch looks unreachable: evaluate() unwraps arrays and
 *        normalizeValue() already takes the first element. Only ROLE + STARTING_WITH/CONTAINS/LIKE
 *        could hit it; no known UI path — not tested.
 */
@Tag("core")
class ConditionTest {

   // =============================================================================================
   // SECTION 1: evaluate() - operator-specific branches (each is an independent decision-tree
   // outcome for THAT op; cross-cutting concerns shared by multiple ops live in Section 2).
   // =============================================================================================

   // ---- EQUAL_TO: only the null-handling shape is op-specific (no short-circuit guard) --------

   @Test
   void equalTo_nullValue_fallsThroughToComparerInsteadOfShortCircuiting() {
      // Unlike LESS_THAN/GREATER_THAN/BETWEEN/ONE_OF/STARTING_WITH/CONTAINS/LIKE (Section 2a),
      // EQUAL_TO has no `if(value == null) return false;` guard - it falls straight into
      // comp.compare(value, obj), so this is a distinct code path worth its own test.
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("test");
      assertFalse(cond.evaluate(null));
   }

   // ---- LESS_THAN / GREATER_THAN: equal-flag boundary --------------------------------------

   @Test
   void lessThan_valueIsLess_matchesRegardlessOfEqualFlag() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.LESS_THAN);
      cond.addValue(10);
      assertTrue(cond.evaluate(5));
   }

   @Test
   void lessThan_valueIsGreater_neverMatches() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.LESS_THAN);
      cond.addValue(10);
      assertFalse(cond.evaluate(15));
   }

   @Test
   void lessThan_valueIsEqual_withoutEqualFlag_doesNotMatch() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.LESS_THAN);
      cond.setEqual(false);
      cond.addValue(10);
      assertFalse(cond.evaluate(10));
   }

   @Test
   void lessThan_valueIsEqual_withEqualFlag_matches() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.LESS_THAN);
      cond.setEqual(true);
      cond.addValue(10);
      assertTrue(cond.evaluate(10));
   }

   @Test
   void greaterThan_valueIsGreater_matchesRegardlessOfEqualFlag() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(10);
      assertTrue(cond.evaluate(15));
   }

   @Test
   void greaterThan_valueIsLess_neverMatches() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(10);
      assertFalse(cond.evaluate(5));
   }

   @Test
   void greaterThan_valueIsEqual_withoutEqualFlag_doesNotMatch() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setEqual(false);
      cond.addValue(10);
      assertFalse(cond.evaluate(10));
   }

   @Test
   void greaterThan_valueIsEqual_withEqualFlag_matches() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setEqual(true);
      cond.addValue(10);
      assertTrue(cond.evaluate(10));
   }

   // setOperation/setEqual ordering: AbstractCondition.setOperation always resets equal to false,
   // so setEqual() must be called AFTER setOperation() or its value is silently discarded.

   @Test
   void setEqualBeforeSetOperation_isSilentlyReset() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setEqual(true);
      cond.setOperation(XCondition.LESS_THAN); // resets equal back to false
      cond.addValue(10);

      assertFalse(cond.isEqual());
      assertFalse(cond.evaluate(10));
   }

   // ---- BETWEEN: boundary outcomes + reversed/partial value list ------------------------------

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
   void between_valueBelowRange() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(1);
      cond.addValue(10);
      assertFalse(cond.evaluate(0));
   }

   @Test
   void between_valueAboveRange() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(1);
      cond.addValue(10);
      assertFalse(cond.evaluate(11));
   }

   @Test
   void between_onlyOneValueAdded_neverMatches() {
      // BETWEEN requires values.size() > 1; a single bound never matches anything
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(1);
      assertFalse(cond.evaluate(1));
      assertFalse(cond.evaluate(100));
   }

   @Test
   void between_reversedBounds_alwaysReturnsFalse() {
      // addValue(10) then addValue(1) means "lower" bound (10) > "upper" bound (1): an
      // impossible/empty range that never matches
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(10);
      cond.addValue(1);
      assertFalse(cond.evaluate(1));
      assertFalse(cond.evaluate(5));
      assertFalse(cond.evaluate(10));
   }

   // ---- ONE_OF: membership, negation, cross-type fallback --------------------------------------

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
   void oneOf_negated_valueInList_becomesFalse() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.setNegated(true);
      cond.addValue("a");
      cond.addValue("b");
      assertFalse(cond.evaluate("a"));
   }

   @Test
   void oneOf_negated_valueNotInList_becomesTrue() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.setNegated(true);
      cond.addValue("a");
      cond.addValue("b");
      assertTrue(cond.evaluate("z"));
   }

   @Test
   void oneOf_noValuesAdded_returnsFalse() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      assertFalse(cond.evaluate("anything"));
   }

   @Test
   void oneOf_integerInput_matchesNumericLookingStringValues() {
      // When the evaluated value's runtime type differs from both the condition's declared type
      // and its stored values' type, evaluate() falls back to an anyMatch() scan (instead of
      // binarySearch) that still numerically matches "2" against Integer 2 via CoreTool.compare's
      // string-to-number coercion.
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.addValue("1");
      cond.addValue("2");
      cond.addValue("3");

      assertTrue(cond.evaluate(2));
      assertFalse(cond.evaluate(9));
   }

   // ---- STARTING_WITH / CONTAINS / LIKE: wildcard semantics (case-sensitivity is Section 2c) --

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
   void like_percentWildcardAtEnd_matches() {
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
   void like_questionMarkWildcard_matchesSingleChar() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.LIKE);
      cond.addValue("hell?");
      assertTrue(cond.evaluate("hello"));
   }

   @Test
   void like_exactPattern_requiresFullMatch() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.LIKE);
      cond.addValue("hello");
      assertTrue(cond.evaluate("hello"));
      assertFalse(cond.evaluate("world"));
   }

   // getLikePattern (static helper behind LIKE) - regex-metacharacter escaping is independent of
   // any Condition instance, so it's tested directly rather than only indirectly via evaluate().

   @Test
   void getLikePattern_escapesRegexMetacharsLiterally() {
      java.util.regex.Pattern p = Condition.getLikePattern("a.b", true);
      assertFalse(p.matcher("axb").find()); // dot must be literal, not "any char"
      assertTrue(p.matcher("a.b").find());
   }

   @Test
   void getLikePattern_percentBecomesWildcard() {
      java.util.regex.Pattern p = Condition.getLikePattern("a%b", true);
      assertTrue(p.matcher("axyzb").find());
      assertTrue(p.matcher("ab").find());
   }

   @Test
   void getLikePattern_questionMarkBecomesSingleCharWildcard() {
      java.util.regex.Pattern p = Condition.getLikePattern("a?b", true);
      assertTrue(p.matcher("axb").find());
      assertFalse(p.matcher("ab").find());
   }

   // ---- NULL: value nullity/emptiness, independent of any comparer -----------------------------

   @Test
   void nullOp_nullValue_matches() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.NULL);
      assertTrue(cond.evaluate(null));
   }

   @Test
   void nullOp_emptyString_matches() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.NULL);
      assertTrue(cond.evaluate(""));
   }

   @Test
   void nullOp_nonNullValue_doesNotMatch() {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.NULL);
      assertFalse(cond.evaluate("notnull"));
   }

   // ---- DATE_IN / isInDateRange: DC period families, merged by formula shape ------------------
   // (representative case per distinct formula shape; exhaustive coverage of all ~40 period
   // strings would violate "few, high-value tests" without exercising any new code path)

   @Test
   void dateIn_wrapsIsInDateRange() {
      // confirms the DATE_IN operator dispatches through isInDateRange() rather than duplicating
      // its logic; the DC-period formula shapes themselves are tested directly below
      Condition cond = new Condition(XSchema.DATE);
      cond.setOperation(XCondition.DATE_IN);
      cond.addValue("this year");
      assertTrue(cond.evaluate(new Date()));
      assertFalse(cond.evaluate("not a date"));
      assertFalse(cond.evaluate(null));
   }

   @Nested
   class IsInDateRangeFormulaShapes {

      @Test
      void year_thisLastNext() {
         Condition cond = new Condition();
         Calendar cal = Calendar.getInstance();
         cal.set(Calendar.MONTH, Calendar.JUNE);
         cal.set(Calendar.DAY_OF_MONTH, 15);
         cal.set(Calendar.HOUR_OF_DAY, 12);
         cal.set(Calendar.MINUTE, 0);
         cal.set(Calendar.SECOND, 0);
         cal.set(Calendar.MILLISECOND, 0);

         assertTrue(cond.isInDateRange("this year", cal.getTime()));

         Calendar last = (Calendar) cal.clone();
         last.add(Calendar.YEAR, -1);
         assertTrue(cond.isInDateRange("last year", last.getTime()));
         assertFalse(cond.isInDateRange("this year", last.getTime()));

         Calendar next = (Calendar) cal.clone();
         next.add(Calendar.YEAR, 1);
         assertTrue(cond.isInDateRange("next year", next.getTime()));
      }

      @Test
      void quarter_thisLast() {
         Condition cond = new Condition();
         // "now" is trivially within its own quarter
         assertTrue(cond.isInDateRange("this quarter", new Date()));

         Calendar threeMonthsAgo = Calendar.getInstance();
         // subtracting exactly 3 months always shifts the quarter index by exactly 1
         threeMonthsAgo.add(Calendar.MONTH, -3);
         assertTrue(cond.isInDateRange("last quarter", threeMonthsAgo.getTime()));
      }

      @Test
      void namedQuarterOrdinalWithinYear() {
         Condition cond = new Condition();
         Calendar cal = Calendar.getInstance();
         cal.set(Calendar.MONTH, Calendar.JANUARY); // quarter index 0
         cal.set(Calendar.DAY_OF_MONTH, 15);
         assertTrue(cond.isInDateRange("1st quarter this year", cal.getTime()));
         assertFalse(cond.isInDateRange("2nd quarter this year", cal.getTime()));
      }

      @Test
      void halfYear_thisFirstSecond() {
         Condition cond = new Condition();
         Calendar cal = Calendar.getInstance();
         cal.set(Calendar.MONTH, Calendar.JANUARY);
         cal.set(Calendar.DAY_OF_MONTH, 15);
         assertTrue(cond.isInDateRange("1st half of this year", cal.getTime()));
         assertFalse(cond.isInDateRange("2nd half of this year", cal.getTime()));
      }

      @Test
      void month_thisLastNext() {
         Condition cond = new Condition();
         Calendar cal = Calendar.getInstance();
         cal.set(Calendar.DAY_OF_MONTH, 15);
         cal.set(Calendar.HOUR_OF_DAY, 12);
         cal.set(Calendar.MINUTE, 0);
         cal.set(Calendar.SECOND, 0);
         cal.set(Calendar.MILLISECOND, 0);

         assertTrue(cond.isInDateRange("this month", cal.getTime()));

         Calendar last = (Calendar) cal.clone();
         last.add(Calendar.MONTH, -1);
         assertTrue(cond.isInDateRange("last month", last.getTime()));
         assertFalse(cond.isInDateRange("this month", last.getTime()));

         Calendar next = (Calendar) cal.clone();
         next.add(Calendar.MONTH, 1);
         assertTrue(cond.isInDateRange("next month", next.getTime()));
      }

      @Test
      void namedMonthWithinYear() {
         Condition cond = new Condition();
         Calendar cal = Calendar.getInstance();
         cal.set(Calendar.MONTH, Calendar.JANUARY);
         cal.set(Calendar.DAY_OF_MONTH, 15);
         assertTrue(cond.isInDateRange("this january", cal.getTime()));
         assertFalse(cond.isInDateRange("this january",
            new Date(cal.getTime().getTime() + 31L * 24 * 60 * 60 * 1000))); // ~February
      }

      @Test
      void week_thisLastNextAndWeekBeforeLast() {
         Condition cond = new Condition();
         Calendar cal = Calendar.getInstance();
         cal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY);
         cal.set(Calendar.HOUR_OF_DAY, 12);
         cal.set(Calendar.MINUTE, 0);
         cal.set(Calendar.SECOND, 0);
         cal.set(Calendar.MILLISECOND, 0);

         assertTrue(cond.isInDateRange("this week", cal.getTime()));
         assertFalse(cond.isInDateRange("last week", cal.getTime()));

         Calendar last = (Calendar) cal.clone();
         last.add(Calendar.WEEK_OF_YEAR, -1);
         assertTrue(cond.isInDateRange("last week", last.getTime()));

         Calendar next = (Calendar) cal.clone();
         next.add(Calendar.WEEK_OF_YEAR, 1);
         assertTrue(cond.isInDateRange("next week", next.getTime()));

         Calendar weekBeforeLast = (Calendar) cal.clone();
         weekBeforeLast.add(Calendar.WEEK_OF_YEAR, -2);
         assertTrue(cond.isInDateRange("week before last week", weekBeforeLast.getTime()));
      }

      @Test
      void day_todayYesterdayAndLastNDays() {
         Condition cond = new Condition();
         assertTrue(cond.isInDateRange("today", new Date()));

         Calendar yesterday = Calendar.getInstance();
         yesterday.set(Calendar.HOUR_OF_DAY, 12);
         yesterday.set(Calendar.MINUTE, 0);
         yesterday.set(Calendar.SECOND, 0);
         yesterday.set(Calendar.MILLISECOND, 0);
         yesterday.add(Calendar.DAY_OF_MONTH, -1);
         assertTrue(cond.isInDateRange("yesterday", yesterday.getTime()));

         Calendar threeDaysAgo = (Calendar) yesterday.clone();
         threeDaysAgo.add(Calendar.DAY_OF_MONTH, -2);
         assertTrue(cond.isInDateRange("last 7 days", threeDaysAgo.getTime()));
      }

      @Test
      void toDate_year_quarter_month() {
         Condition cond = new Condition();
         // "now" is trivially within its own year/quarter/month-to-date range
         assertTrue(cond.isInDateRange("year to date", new Date()));
         assertTrue(cond.isInDateRange("quarter to date", new Date()));
         assertTrue(cond.isInDateRange("month to date", new Date()));
      }

      // "last N months" family: last 1/3/6/12/18/24/36/48/60/72/84/96/108/120 months are all the
      // SAME isBoolMonth(0, N+1, date, now) formula with only N varying - a genuine merge
      // candidate, and previously completely untested.
      static Stream<Arguments> lastNMonthsCases() {
         // Arguments: (period, monthsAgo, expected) - offsets chosen with a full month of margin
         // on either side of the boundary so noon-vs-midnight truncation can't flip the result
         return Stream.of(
            Arguments.of("last 3 months", 2, true),
            Arguments.of("last 3 months", 4, false),
            Arguments.of("last 12 months", 6, true),
            Arguments.of("last 12 months", 13, false)
         );
      }

      @ParameterizedTest
      @MethodSource("lastNMonthsCases")
      void lastNMonths_family(String period, int monthsAgo, boolean expected) {
         Condition cond = new Condition();
         Calendar cal = Calendar.getInstance();
         cal.set(Calendar.HOUR_OF_DAY, 12);
         cal.set(Calendar.MINUTE, 0);
         cal.set(Calendar.SECOND, 0);
         cal.set(Calendar.MILLISECOND, 0);
         cal.add(Calendar.MONTH, -monthsAgo);
         assertEquals(expected, cond.isInDateRange(period, cal.getTime()));
      }

      @Test
      void nullDate_nullPeriod_nonDateObject_allReturnFalse() {
         Condition cond = new Condition();
         assertFalse(cond.isInDateRange("this year", null));
         assertFalse(cond.isInDateRange(null, new Date()));
         assertFalse(cond.isInDateRange("this year", "not a date"));
      }
   }

   // =============================================================================================
   // SECTION 2: evaluate() - cross-cutting dimensions shared by MULTIPLE ops. Each dimension gets
   // exactly one (or a small, arity-driven family of) parameterized test, instead of being
   // re-verified per operator section as in Section 1 / the original file.
   // =============================================================================================

   // ---- 2a. null short-circuits before any operator-specific logic ---------------------------
   // LESS_THAN/GREATER_THAN/BETWEEN/ONE_OF/STARTING_WITH/CONTAINS/LIKE all begin with an
   // unconditional `if(value == null) return false;` before ever looking at the condition's own
   // values, so no values need to be added for this guard to be exercised. (EQUAL_TO is excluded -
   // see Section 1's equalTo_nullValue_fallsThroughToComparerInsteadOfShortCircuiting.)

   @ParameterizedTest
   @ValueSource(ints = {
      XCondition.LESS_THAN, XCondition.GREATER_THAN, XCondition.BETWEEN, XCondition.ONE_OF,
      XCondition.STARTING_WITH, XCondition.CONTAINS, XCondition.LIKE
   })
   void nullValue_returnsFalseAcrossOperators(int op) {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(op);
      assertFalse(cond.evaluate(null));
   }

   // ---- 2b. comparer-type selection: evaluate()'s `if(comp == null)` block ---------------------
   // Split into one small parameterized method per op (arities differ: BETWEEN needs 2 bounds,
   // ONE_OF needs a variable-length value list), but grouped together here as ONE dimension.

   static Stream<Arguments> equalToComparerSelectionCases() {
      // (type, conditionValue, testValue, expected) - the last two rows contrast ROLE's
      // "any array element matches" against every other type's "array input unwrapped to its
      // first element only" (see decision-table note above).
      return Stream.of(
         Arguments.of(XSchema.BOOLEAN, "true", Boolean.TRUE, true),
         Arguments.of(XSchema.BOOLEAN, "true", Boolean.FALSE, false),
         Arguments.of(XSchema.DATE, java.sql.Date.valueOf("2024-06-15"),
                      java.sql.Date.valueOf("2024-06-15"), true),
         Arguments.of(XSchema.DATE, java.sql.Date.valueOf("2024-06-15"),
                      java.sql.Date.valueOf("2024-06-16"), false),
         Arguments.of(XSchema.TIME, java.sql.Time.valueOf("10:30:00"),
                      java.sql.Time.valueOf("10:30:00"), true),
         Arguments.of(XSchema.TIME, java.sql.Time.valueOf("10:30:00"),
                      java.sql.Time.valueOf("11:30:00"), false),
         Arguments.of(XSchema.TIME_INSTANT, java.sql.Timestamp.valueOf("2024-06-15 10:30:00"),
                      java.sql.Timestamp.valueOf("2024-06-15 10:30:00"), true),
         Arguments.of(XSchema.TIME_INSTANT, java.sql.Timestamp.valueOf("2024-06-15 10:30:00"),
                      java.sql.Timestamp.valueOf("2024-06-15 11:30:00"), false),
         Arguments.of(XSchema.ROLE, "admin", new Object[] {"user", "admin"}, true),
         Arguments.of(XSchema.ROLE, "admin", new Object[] {"user", "guest"}, false),
         Arguments.of(XSchema.STRING, "hello", new Object[] {"hello", "world"}, true),
         Arguments.of(XSchema.STRING, "hello", new Object[] {"world", "hello"}, false)
      );
   }

   @ParameterizedTest
   @MethodSource("equalToComparerSelectionCases")
   void equalTo_comparerSelectionByType(String type, Object conditionValue, Object testValue, boolean expected) {
      Condition cond = new Condition(type);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(conditionValue);
      assertEquals(expected, cond.evaluate(testValue));
   }

   static Stream<Arguments> orderingComparerSelectionCases() {
      // (op, type, conditionValue, testValue, expected) - the comparer-SELECTION mechanism
      // itself (`if(comp == null) { ... }`) is shared code, already proven exhaustively across
      // all 5 types via equalToComparerSelectionCases above; repeating DATE/TIME/TIME_INSTANT
      // here would just be a parameter variant of that same proof (Risk 1). What IS new here is
      // whether LESS_THAN/GREATER_THAN's own `isEqual() ? r<=0 : r<0` (resp. `r>=0 : r>0`)
      // ternary correctly interprets a non-default comparer's result - one representative type
      // (DATE) is enough to establish that; it doesn't matter which comparer produced the sign.
      return Stream.of(
         Arguments.of(XCondition.LESS_THAN, XSchema.DATE, java.sql.Date.valueOf("2024-06-15"),
                      java.sql.Date.valueOf("2024-06-10"), true),
         Arguments.of(XCondition.LESS_THAN, XSchema.DATE, java.sql.Date.valueOf("2024-06-15"),
                      java.sql.Date.valueOf("2024-06-20"), false),
         Arguments.of(XCondition.GREATER_THAN, XSchema.DATE, java.sql.Date.valueOf("2024-06-15"),
                      java.sql.Date.valueOf("2024-06-20"), true),
         Arguments.of(XCondition.GREATER_THAN, XSchema.DATE, java.sql.Date.valueOf("2024-06-15"),
                      java.sql.Date.valueOf("2024-06-10"), false)
      );
   }

   @ParameterizedTest
   @MethodSource("orderingComparerSelectionCases")
   void lessThanGreaterThan_comparerSelectionByType(
      int op, String type, Object conditionValue, Object testValue, boolean expected)
   {
      Condition cond = new Condition(type);
      cond.setOperation(op);
      cond.addValue(conditionValue);
      assertEquals(expected, cond.evaluate(testValue));
   }

   static Stream<Arguments> betweenComparerSelectionCases() {
      // (type, lowerBound, upperBound, testValue, expected) - one representative non-default
      // type is enough; see orderingComparerSelectionCases for why TIME/TIME_INSTANT would be
      // Risk-1 repeats of the already-proven selection mechanism.
      return Stream.of(
         Arguments.of(XSchema.DATE, java.sql.Date.valueOf("2024-06-01"),
                      java.sql.Date.valueOf("2024-06-30"), java.sql.Date.valueOf("2024-06-15"), true),
         Arguments.of(XSchema.DATE, java.sql.Date.valueOf("2024-06-01"),
                      java.sql.Date.valueOf("2024-06-30"), java.sql.Date.valueOf("2024-07-01"), false)
      );
   }

   @ParameterizedTest
   @MethodSource("betweenComparerSelectionCases")
   void between_comparerSelectionByType(String type, Object lower, Object upper, Object testValue, boolean expected) {
      Condition cond = new Condition(type);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(lower);
      cond.addValue(upper);
      assertEquals(expected, cond.evaluate(testValue));
   }

   static Stream<Arguments> oneOfComparerSelectionCases() {
      // (type, conditionValues, testValue, expected) - ONE_OF's consuming logic
      // (Collections.sort + binarySearch, with an anyMatch fallback) is meaningfully more
      // involved than EQUAL_TO/LESS_THAN/GREATER_THAN/BETWEEN's single compare() call, so it
      // gets two representative non-default types instead of one: BOOLEAN (exercises the
      // single-element list path) and DATE (exercises sort+binarySearch across multiple real,
      // orderable values). TIME/TIME_INSTANT would add nothing beyond what DATE already proves.
      return Stream.of(
         Arguments.of(XSchema.BOOLEAN, new Object[] {"true"}, Boolean.TRUE, true),
         Arguments.of(XSchema.BOOLEAN, new Object[] {"true"}, Boolean.FALSE, false),
         Arguments.of(XSchema.DATE,
                      new Object[] {java.sql.Date.valueOf("2024-06-15"), java.sql.Date.valueOf("2024-07-01")},
                      java.sql.Date.valueOf("2024-06-15"), true),
         Arguments.of(XSchema.DATE,
                      new Object[] {java.sql.Date.valueOf("2024-06-15"), java.sql.Date.valueOf("2024-07-01")},
                      java.sql.Date.valueOf("2024-08-01"), false)
      );
   }

   @ParameterizedTest
   @MethodSource("oneOfComparerSelectionCases")
   void oneOf_comparerSelectionByType(String type, Object[] conditionValues, Object testValue, boolean expected) {
      Condition cond = new Condition(type);
      cond.setOperation(XCondition.ONE_OF);

      for(Object v : conditionValues) {
         cond.addValue(v);
      }

      assertEquals(expected, cond.evaluate(testValue));
   }

   // ---- 2c. STRING case sensitivity: shared by EQUAL_TO/STARTING_WITH/CONTAINS/LIKE -----------

   static Stream<Arguments> caseSensitivityAcrossStringOperators() {
      // (op, conditionValue, caseSensitive, expected) - all evaluated against "hello"
      return Stream.of(
         Arguments.of(XCondition.EQUAL_TO, "Hello", false, true),
         Arguments.of(XCondition.EQUAL_TO, "Hello", true, false),
         Arguments.of(XCondition.STARTING_WITH, "HEL", false, true),
         Arguments.of(XCondition.STARTING_WITH, "HEL", true, false),
         Arguments.of(XCondition.CONTAINS, "ELL", false, true),
         Arguments.of(XCondition.CONTAINS, "ELL", true, false),
         Arguments.of(XCondition.LIKE, "HE%", false, true),
         Arguments.of(XCondition.LIKE, "HE%", true, false)
      );
   }

   @ParameterizedTest
   @MethodSource("caseSensitivityAcrossStringOperators")
   void caseSensitivity_acrossStringOperators(
      int op, String conditionValue, boolean caseSensitive, boolean expected)
   {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(op);
      cond.setCaseSensitive(caseSensitive);
      cond.addValue(conditionValue);
      assertEquals(expected, cond.evaluate("hello"));
   }

   // ---- 2d. negation: applied once at the very end of evaluate(), independent of op -----------
   // `return isNegated() ? !result : result;` is a single shared line - proving it flips the
   // result for ONE op is sufficient; re-verifying it per op (as the original file did for both
   // LESS_THAN and BETWEEN) is a parameter variant of the same mechanism, not a new branch.

   @Test
   void negated_flipsResultRegardlessOfOperator() {
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.setNegated(true);
      cond.addValue(1);
      cond.addValue(10);
      assertFalse(cond.evaluate(5));  // 5 is in range; negated => false
      assertTrue(cond.evaluate(20));  // 20 is not in range; negated => true
   }

   // ---- 2e. isIgnoreNullValue: gates EQUAL_TO's null-condition-value auto-match ---------------
   // A condition value can be explicitly set to null via setValue() (unlike addValue(), which
   // skips nulls). EQUAL_TO then checks `if(obj == null && isIgnoreNullValue()) return true;`
   // before ever calling the comparer - a previously untested, genuinely subtle contract.

   @Nested
   class IgnoreNullValueTests {

      @Test
      void ignoreNullValueTrue_nullConditionValue_autoMatchesAnything() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue("placeholder");
         cond.setValue(0, null); // setValue(), unlike addValue(), allows storing null
         assertTrue(cond.isIgnoreNullValue()); // default

         assertTrue(cond.evaluate("anything"));
      }

      @Test
      void ignoreNullValueFalse_nullConditionValue_onlyMatchesNullInput() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue("placeholder");
         cond.setValue(0, null);
         cond.setIgnoreNullValue(false);

         assertFalse(cond.evaluate("anything"));
         assertTrue(cond.evaluate(null)); // both sides null -> comparer says equal
      }
   }

   // =============================================================================================
   // SECTION 3: non-evaluate() methods, grouped by method/method-family.
   // =============================================================================================

   @Nested
   class IsVariableTests {

      @Test
      void validVariable_returnsTrue() {
         assertTrue(Condition.isVariable("$(myVar)"));
      }

      @Test
      void emptyName_returnsFalse() {
         assertFalse(Condition.isVariable("$()"));
      }

      @Test
      void plainString_returnsFalse() {
         assertFalse(Condition.isVariable("hello"));
      }

      @Test
      void nullValue_returnsFalse() {
         assertFalse(Condition.isVariable(null));
      }

      @Test
      void unrestrictedFlag_allowsEmptyNamePlaceholder() {
         assertTrue(Condition.isVariable("$()", true));
         assertFalse(Condition.isVariable("$()", false));
      }
   }

   @Nested
   class GetRawValueStringTests {

      @Test
      void nullValue_returnsEmptyString() {
         assertEquals("", Condition.getRawValueString(null));
      }

      @Test
      void userVariable_returnsDollarReference() {
         assertEquals("$(myVar)", Condition.getRawValueString(new UserVariable("myVar")));
      }

      @Test
      void expressionValue_returnsRawExpression() {
         ExpressionValue expr = new ExpressionValue();
         expr.setExpression("field['A'] + 1");
         assertEquals("field['A'] + 1", Condition.getRawValueString(expr));
      }

      @Test
      void plainObject_usesToString() {
         assertEquals("42", Condition.getRawValueString(42));
      }
   }

   @Nested
   class IsSessionVariableTests {

      @Test
      void userRolesGroups_returnTrue() {
         assertTrue(Condition.isSessionVariable("$(_USER_)"));
         assertTrue(Condition.isSessionVariable("$(_ROLES_)"));
         assertTrue(Condition.isSessionVariable("$(_GROUPS_)"));
      }

      @Test
      void regularVariable_returnsFalse() {
         assertFalse(Condition.isSessionVariable("$(myVar)"));
      }

      @Test
      void plainStringOrNull_returnsFalse() {
         assertFalse(Condition.isSessionVariable("plain"));
         assertFalse(Condition.isSessionVariable(null));
      }
   }

   @Nested
   class SetTypeTests {

      @Test
      void nullType_defaultsToString() {
         Condition cond = new Condition(XSchema.INTEGER);
         cond.setType(null);
         assertEquals(XSchema.STRING, cond.getType());
      }

      @Test
      void changingType_reconvertsExistingValues() {
         // setType() calls the private no-arg convertType(), which re-parses stored String
         // values against the newly-set type
         Condition cond = new Condition(false, false); // ctype=false: addValue leaves strings raw
         cond.setType(XSchema.STRING);
         cond.addValue("true");
         assertEquals("true", cond.getValue(0)); // still a raw string, no BOOLEAN parsing yet

         cond.setConvertingType(true);
         cond.setType(XSchema.BOOLEAN); // triggers convertType() re-parse under ctype=true
         assertEquals(Boolean.TRUE, cond.getValue(0));
      }
   }

   @Nested
   class AddValueTests {

      @Test
      void nullValue_isNotAdded() {
         Condition cond = new Condition(XSchema.STRING);
         cond.addValue(null);
         assertEquals(0, cond.getValueCount());
      }

      @Test
      void dupcheckEnabled_duplicateValueIsSkipped() {
         Condition cond = new Condition(true, true);
         cond.setType(XSchema.STRING);
         cond.addValue("a");
         cond.addValue("a");
         assertEquals(1, cond.getValueCount());
      }

      @Test
      void dupcheckDisabled_duplicateValueIsAdded() {
         Condition cond = new Condition(XSchema.STRING); // dupcheck off by default
         cond.addValue("a");
         cond.addValue("a");
         assertEquals(2, cond.getValueCount());
      }
   }

   @Nested
   class SetValueTests {

      @Test
      void objectArray_convertsEachElement() {
         // convertType() only parses strings for DATE/TIME/TIME_INSTANT/BOOLEAN/DOUBLE (not
         // INTEGER), so DOUBLE is used here to exercise the per-element conversion.
         Condition cond = new Condition(XSchema.DOUBLE);
         cond.addValue(0d); // placeholder to create index 0
         cond.setValue(0, new Object[] {"1.5", "2.5"});

         Object val = cond.getValue(0);
         assertInstanceOf(Object[].class, val);
         assertArrayEquals(new Object[] {1.5, 2.5}, (Object[]) val);
      }
   }

   @Nested
   class SetDynamicValueTests {

      @Test
      void oneOf_stringUserValue_isSplitOnComma() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.ONE_OF);
         cond.addValue("placeholder");
         cond.setDynamicValue(0, "a,b,c", false);

         assertArrayEquals(new Object[] {"a", "b", "c"}, (Object[]) cond.getValue(0));
      }

      @Test
      void oneOf_objectArrayUserValue_isFlattened() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.ONE_OF);
         cond.addValue("placeholder");
         cond.setDynamicValue(0, new Object[] {"x,y", "z"}, false);

         assertArrayEquals(new Object[] {"x", "y", "z"}, (Object[]) cond.getValue(0));
      }

      @Test
      void oneOf_objectArrayWithNonStringElement_passesThroughUnsplit() {
         // flattenParameters() only splits String elements on comma; non-String elements are
         // added to the result as-is
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.ONE_OF);
         cond.addValue("placeholder");
         cond.setDynamicValue(0, new Object[] {"a,b", 5}, false);

         assertArrayEquals(new Object[] {"a", "b", 5}, (Object[]) cond.getValue(0));
      }

      @Test
      void oneOf_asIsTrue_bypassesSplitting() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.ONE_OF);
         cond.addValue("placeholder");
         cond.setDynamicValue(0, "a,b,c", true);

         assertEquals("a,b,c", cond.getValue(0));
      }

      @Test
      void nonOneOfOperation_bypassesSplitting() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue("placeholder");
         cond.setDynamicValue(0, "a,b,c", false);

         assertEquals("a,b,c", cond.getValue(0));
      }
   }

   @Nested
   class ValueListAccessTests {

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

      @Test
      void getDataRefValues_filtersNonDataRefEntries() {
         Condition cond = new Condition(XSchema.STRING);
         cond.addValue(new AttributeRef("attr1"));
         cond.addValue("plain");

         DataRef[] refs = cond.getDataRefValues();
         assertEquals(1, refs.length);
         assertEquals("attr1", refs[0].getAttribute());
      }
   }

   @Nested
   class ContainsValueTests {

      @Test
      void betweenOperation_alwaysReturnsFalse() {
         // containsValue() short-circuits to false for BETWEEN regardless of stored content
         Condition cond = new Condition(XSchema.INTEGER);
         cond.setOperation(XCondition.BETWEEN);
         cond.addValue(1);
         cond.addValue(10);
         assertFalse(cond.containsValue(1));
      }

      @Test
      void nonBetweenOperation_scansStoredValues() {
         Condition cond = new Condition(XSchema.STRING);
         cond.addValue("a");
         cond.addValue("b");
         assertTrue(cond.containsValue("a"));
         assertFalse(cond.containsValue("z"));
      }
   }

   @Nested
   class ReplaceVariableTests {

      @Test
      void userVariableResolvedFromTable_replacesValueAndClearsIgnored() throws Exception {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue(new UserVariable("myVar"));

         VariableTable vart = new VariableTable();
         vart.put("myVar", "resolved");
         cond.replaceVariable(vart);

         assertEquals("resolved", cond.getValue(0));
         assertFalse(cond.isIgnored());
      }

      @Test
      void userVariableNotFoundInTable_setsIgnored() throws Exception {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue(new UserVariable("missingVar"));

         cond.replaceVariable(new VariableTable());
         assertTrue(cond.isIgnored());
      }

      @Test
      void stringVariableReference_resolvedFromTable() throws Exception {
         Condition cond = new Condition(false, false);
         cond.setType(XSchema.STRING);
         cond.setOperation(XCondition.STARTING_WITH);
         cond.addValue("$(myVar)");

         VariableTable vart = new VariableTable();
         vart.put("myVar", "resolved");
         cond.replaceVariable(vart);

         assertEquals("resolved", cond.getValue(0));
      }

      @Test
      void oneOf_singleNullElementArray_setsIgnored() throws Exception {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.ONE_OF);
         cond.addValue(new UserVariable("myVar"));

         VariableTable vart = new VariableTable() {
            @Override
            public Object get(UserVariable var) {
               return new Object[] {null};
            }
         };
         cond.replaceVariable(vart);

         assertTrue(cond.isIgnored());
      }

      @Test
      void nullTable_resetsIgnoredButLeavesValuesUnchanged() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue("plain");
         cond.setIgnored(true);

         cond.replaceVariable(null);

         assertFalse(cond.isIgnored());
         assertEquals("plain", cond.getValue(0));
      }

      @Test
      void tableLookupThrows_isSwallowedAndSetsIgnored() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue(new UserVariable("myVar"));

         VariableTable vart = new VariableTable() {
            @Override
            public Object get(UserVariable var) throws Exception {
               throw new Exception("boom");
            }
         };

         assertDoesNotThrow(() -> cond.replaceVariable(vart));
         assertTrue(cond.isIgnored());
      }
   }

   @Nested
   class GetAllVariablesTests {

      @Test
      void userVariableValue_isReturned() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue(new UserVariable("myVar"));

         UserVariable[] vars = cond.getAllVariables();
         assertEquals(1, vars.length);
         assertEquals("myVar", vars[0].getName());
      }

      @Test
      void stringVariableReference_isReturned() {
         Condition cond = new Condition(false, false);
         cond.setType(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue("$(myVar)");

         UserVariable[] vars = cond.getAllVariables();
         assertEquals(1, vars.length);
         assertEquals("myVar", vars[0].getName());
      }

      @Test
      void builtinVariable_isExcluded() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue(new UserVariable("_USER_"));
         assertEquals(0, cond.getAllVariables().length);
      }

      @Test
      void duplicateNamedVariable_isNotAddedTwice() {
         // dupcheck is off by default, so both instances land in values[]; getAllVariables()
         // still dedups them via list.contains() (UserVariable.equals() is name/label-based)
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue(new UserVariable("myVar"));
         cond.addValue(new UserVariable("myVar"));

         assertEquals(2, cond.getValueCount());
         assertEquals(1, cond.getAllVariables().length);
      }

      @Test
      void oneOfOperation_marksMultipleSelection() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.ONE_OF);
         cond.addValue(new UserVariable("myVar"));

         assertTrue(cond.getAllVariables()[0].isMultipleSelection());
      }

      @Test
      void plainValue_isNotReturnedAsVariable() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue("plain");
         assertEquals(0, cond.getAllVariables().length);
      }
   }

   @Nested
   class IsValidTests {

      @Test
      void plainStringValue_isValid() {
         Condition cond = new Condition(XSchema.STRING);
         cond.addValue("hello");
         assertTrue(cond.isValid());
      }

      @Test
      void userVariableWithEmptyName_isInvalid() {
         Condition cond = new Condition(XSchema.STRING);
         cond.addValue(new UserVariable(""));
         assertFalse(cond.isValid());
      }

      @Test
      void emptyVariablePlaceholderString_isInvalid() {
         Condition cond = new Condition(false, false);
         cond.setType(XSchema.STRING);
         cond.addValue("$()");
         assertFalse(cond.isValid());
      }

      @Test
      void userVariableWithName_isValid() {
         Condition cond = new Condition(XSchema.STRING);
         cond.addValue(new UserVariable("myVar"));
         assertTrue(cond.isValid());
      }
   }

   @Nested
   class CloneAndEqualityTests {

      @Test
      void clone_isIndependentCopy() {
         Condition cond = new Condition(XSchema.STRING);
         cond.setOperation(XCondition.ONE_OF);
         cond.addValue("a");

         Condition clone = cond.clone();
         clone.addValue("b");

         assertEquals(1, cond.getValueCount());
         assertEquals(2, clone.getValueCount());
      }

      @Test
      void equals_sameTypeOpAndValues_areEqual() {
         Condition a = new Condition(XSchema.STRING);
         a.setOperation(XCondition.EQUAL_TO);
         a.addValue("x");

         Condition b = new Condition(XSchema.STRING);
         b.setOperation(XCondition.EQUAL_TO);
         b.addValue("x");

         assertEquals(a, b);
         assertEquals(a.hashCode(), b.hashCode());
      }

      @Test
      void equals_differentValues_areNotEqual() {
         Condition a = new Condition(XSchema.STRING);
         a.setOperation(XCondition.EQUAL_TO);
         a.addValue("x");

         Condition b = new Condition(XSchema.STRING);
         b.setOperation(XCondition.EQUAL_TO);
         b.addValue("y");

         assertNotEquals(a, b);
      }

      @Test
      void equals_nullOperation_comparesStrictMatchNullInsteadOfValues() {
         Condition a = new Condition(XSchema.STRING);
         a.setOperation(XCondition.NULL);
         a.setStrictMatchNull(true);

         Condition b = new Condition(XSchema.STRING);
         b.setOperation(XCondition.NULL);
         b.setStrictMatchNull(false);

         // both have zero values, but differ in strictMatchNull -> not equal
         assertNotEquals(a, b);
      }
   }

   // ---- normalizeValue, via LESS_THAN/BETWEEN against Date-typed conditions -------------------
   // (evaluate() normalizes a stored Date value before comparing; distinct from DATE_IN)

   @Nested
   class NormalizeValueDateComparisonTests {

      @Test
      void lessThan_dateType_comparesChronologically() {
         Condition cond = new Condition(XSchema.DATE);
         cond.setOperation(XCondition.LESS_THAN);
         Calendar cal = Calendar.getInstance();
         cal.set(2024, Calendar.JUNE, 15, 0, 0, 0);
         cal.set(Calendar.MILLISECOND, 0);
         cond.addValue(cal.getTime());

         Calendar earlier = Calendar.getInstance();
         earlier.set(2024, Calendar.JUNE, 10, 0, 0, 0);
         earlier.set(Calendar.MILLISECOND, 0);
         assertTrue(cond.evaluate(earlier.getTime()));

         Calendar later = Calendar.getInstance();
         later.set(2024, Calendar.JUNE, 20, 0, 0, 0);
         later.set(Calendar.MILLISECOND, 0);
         assertFalse(cond.evaluate(later.getTime()));
      }
   }

   // =============================================================================================
   // SECTION 4: known, confirmed defects (documented, not fixed in this pass)
   // =============================================================================================

   @Nested
   class KnownBugs {

      // Fixed: Condition.java:1296-1300 "tomorrow" branch used the same formula as "yesterday"
      // (d1 + 1 == d2); corrected to `result = (d1 - 1) == d2;`
      @Test
      void isInDateRangeTomorrowActuallyChecksYesterday() {
         Condition cond = new Condition();
         Calendar cal = Calendar.getInstance();
         cal.set(Calendar.HOUR_OF_DAY, 12);
         cal.set(Calendar.MINUTE, 0);
         cal.set(Calendar.SECOND, 0);
         cal.set(Calendar.MILLISECOND, 0);
         cal.add(Calendar.DAY_OF_MONTH, 1);

         assertTrue(cond.isInDateRange("tomorrow", cal.getTime()));
      }

      // Negative case: yesterday's date must not match "tomorrow" (the actual reported symptom).
      @Test
      void isInDateRangeTomorrowDoesNotMatchYesterday() {
         Condition cond = new Condition();
         Calendar cal = Calendar.getInstance();
         cal.set(Calendar.HOUR_OF_DAY, 12);
         cal.set(Calendar.MINUTE, 0);
         cal.set(Calendar.SECOND, 0);
         cal.set(Calendar.MILLISECOND, 0);
         cal.add(Calendar.DAY_OF_MONTH, -1);

         assertFalse(cond.isInDateRange("tomorrow", cal.getTime()));
      }
   }
}
