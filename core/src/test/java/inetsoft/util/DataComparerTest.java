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
package inetsoft.util;

import inetsoft.report.Comparer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class DataComparerTest {

   // -----------------------------------------------------------------------
   // getDataComparer factory
   // -----------------------------------------------------------------------

   @Test
   void getDataComparerForStringReturnsStringComparer() {
      assertSame(DataComparer.STRING_COMPARER, DataComparer.getDataComparer(String.class));
   }

   @Test
   void getDataComparerForCharacterReturnsCharComparer() {
      assertSame(DataComparer.CHAR_COMPARER, DataComparer.getDataComparer(Character.class));
   }

   @Test
   void getDataComparerForBooleanReturnsBoolComparer() {
      assertSame(DataComparer.BOOL_COMPARER, DataComparer.getDataComparer(Boolean.class));
   }

   @Test
   void getDataComparerForIntegerReturnsNumComparer() {
      assertSame(DataComparer.NUM_COMPARER, DataComparer.getDataComparer(Integer.class));
   }

   @Test
   void getDataComparerForLongReturnsNumComparer() {
      assertSame(DataComparer.NUM_COMPARER, DataComparer.getDataComparer(Long.class));
   }

   @Test
   void getDataComparerForDoubleReturnsNumComparer() {
      assertSame(DataComparer.NUM_COMPARER, DataComparer.getDataComparer(Double.class));
   }

   @Test
   void getDataComparerForFloatReturnsNumComparer() {
      assertSame(DataComparer.NUM_COMPARER, DataComparer.getDataComparer(Float.class));
   }

   @Test
   void getDataComparerForBigDecimalReturnsNumComparer() {
      assertSame(DataComparer.NUM_COMPARER, DataComparer.getDataComparer(BigDecimal.class));
   }

   @Test
   void getDataComparerForBigIntegerReturnsNumComparer() {
      assertSame(DataComparer.NUM_COMPARER, DataComparer.getDataComparer(BigInteger.class));
   }

   @Test
   void getDataComparerForDateReturnsDateComparer() {
      assertSame(DataComparer.DATE_COMPARER, DataComparer.getDataComparer(Date.class));
   }

   @Test
   void getDataComparerForSqlDateReturnsDateComparer() {
      assertSame(DataComparer.DATE_COMPARER, DataComparer.getDataComparer(java.sql.Date.class));
   }

   @Test
   void getDataComparerForSqlTimeReturnsDateComparer() {
      assertSame(DataComparer.DATE_COMPARER, DataComparer.getDataComparer(java.sql.Time.class));
   }

   @Test
   void getDataComparerForSqlTimestampReturnsDateComparer() {
      assertSame(DataComparer.DATE_COMPARER, DataComparer.getDataComparer(java.sql.Timestamp.class));
   }

   @Test
   void getDataComparerForUnknownTypeReturnsDefaultComparer() {
      assertSame(DataComparer.DEFAULT_COMPARER, DataComparer.getDataComparer(Object.class));
   }

   @Test
   void getDataComparerForUnknownTypeReturnsIgnoredFromCompare() {
      Comparer comparer = DataComparer.getDataComparer(Object.class);
      assertEquals(DataComparer.IGNORED, comparer.compare(new Object(), new Object()));
   }

   // -----------------------------------------------------------------------
   // compare(double, double) — static utility
   // -----------------------------------------------------------------------

   @Test
   void compareDoubleEqualValuesReturnsZero() {
      assertEquals(0, DataComparer.compare(1.0, 1.0));
   }

   @Test
   void compareDoubleFirstGreaterReturnsPositive() {
      assertTrue(DataComparer.compare(2.0, 1.0) > 0);
   }

   @Test
   void compareDoubleFirstLessReturnsNegative() {
      assertTrue(DataComparer.compare(1.0, 2.0) < 0);
   }

   @Test
   void compareDoubleWithinToleranceReturnsZero() {
      // difference of 5e-7 is within the ±1e-6 tolerance
      assertEquals(0, DataComparer.compare(1.0, 1.0000005));
   }

   @Test
   void compareDoubleBeyondToleranceReturnsNonZero() {
      // difference of 2e-5 is beyond tolerance
      assertTrue(DataComparer.compare(1.00002, 1.0) > 0);
   }

   @Test
   void compareDoubleFirstIsNullDoubleReturnsNegative() {
      assertEquals(-1, DataComparer.compare(Tool.NULL_DOUBLE, 1.0));
   }

   @Test
   void compareDoubleSecondIsNullDoubleReturnsPositive() {
      assertEquals(1, DataComparer.compare(1.0, Tool.NULL_DOUBLE));
   }

   @Test
   void compareDoubleBothNullDoubleReturnsZero() {
      assertEquals(0, DataComparer.compare(Tool.NULL_DOUBLE, Tool.NULL_DOUBLE));
   }

   // -----------------------------------------------------------------------
   // IGNORED constant
   // -----------------------------------------------------------------------

   @Test
   void ignoredConstantValue() {
      assertEquals(Integer.MIN_VALUE + 1, DataComparer.IGNORED);
   }

   // -----------------------------------------------------------------------
   // DefaultComparer
   // -----------------------------------------------------------------------

   @Test
   void defaultComparerCompareObjectsReturnsIgnored() {
      assertEquals(DataComparer.IGNORED,
         DataComparer.DEFAULT_COMPARER.compare(new Object(), new Object()));
   }

   @Test
   void defaultComparerCompareDoubleReturnsIgnored() {
      assertEquals(DataComparer.IGNORED, DataComparer.DEFAULT_COMPARER.compare(1.0, 2.0));
   }

   @Test
   void defaultComparerCompareFloatReturnsIgnored() {
      assertEquals(DataComparer.IGNORED, DataComparer.DEFAULT_COMPARER.compare(1.0f, 2.0f));
   }

   @Test
   void defaultComparerCompareLongReturnsIgnored() {
      assertEquals(DataComparer.IGNORED, DataComparer.DEFAULT_COMPARER.compare(1L, 2L));
   }

   @Test
   void defaultComparerCompareIntReturnsIgnored() {
      assertEquals(DataComparer.IGNORED, DataComparer.DEFAULT_COMPARER.compare(1, 2));
   }

   @Test
   void defaultComparerCompareShortReturnsIgnored() {
      assertEquals(DataComparer.IGNORED,
         DataComparer.DEFAULT_COMPARER.compare((short) 1, (short) 2));
   }

   // -----------------------------------------------------------------------
   // StringComparer
   // -----------------------------------------------------------------------

   @Test
   void stringComparerEqualStringsReturnsZero() {
      assertEquals(0, DataComparer.STRING_COMPARER.compare("hello", "hello"));
   }

   @Test
   void stringComparerFirstLessReturnsNegative() {
      assertTrue(DataComparer.STRING_COMPARER.compare("apple", "banana") < 0);
   }

   @Test
   void stringComparerFirstGreaterReturnsPositive() {
      assertTrue(DataComparer.STRING_COMPARER.compare("banana", "apple") > 0);
   }

   @Test
   void stringComparerBothNullReturnsZero() {
      assertEquals(0, DataComparer.STRING_COMPARER.compare(null, null));
   }

   @Test
   void stringComparerFirstNullReturnsNegative() {
      assertEquals(-1, DataComparer.STRING_COMPARER.compare(null, "something"));
   }

   @Test
   void stringComparerSecondNullReturnsPositive() {
      assertEquals(1, DataComparer.STRING_COMPARER.compare("something", null));
   }

   @Test
   void stringComparerTrimsWhitespace() {
      // " hello " trimmed equals "hello"
      assertEquals(0, DataComparer.STRING_COMPARER.compare(" hello ", "hello"));
   }

   @Test
   void stringComparerDoubleNullSentinelBothEqualReturnsZero() {
      assertEquals(0, DataComparer.STRING_COMPARER.compare(Tool.NULL_DOUBLE, Tool.NULL_DOUBLE));
   }

   @Test
   void stringComparerDoubleNullSentinelFirstReturnsNegative() {
      assertEquals(-1, DataComparer.STRING_COMPARER.compare(Tool.NULL_DOUBLE, 1.0));
   }

   @Test
   void stringComparerDoubleNullSentinelSecondReturnsPositive() {
      assertEquals(1, DataComparer.STRING_COMPARER.compare(1.0, Tool.NULL_DOUBLE));
   }

   // -----------------------------------------------------------------------
   // NumberComparer
   // -----------------------------------------------------------------------

   @Test
   void numberComparerEqualIntsReturnsZero() {
      assertEquals(0, DataComparer.NUM_COMPARER.compare(Integer.valueOf(5), Integer.valueOf(5)));
   }

   @Test
   void numberComparerFirstLessReturnsNegative() {
      assertTrue(DataComparer.NUM_COMPARER.compare(Integer.valueOf(3), Integer.valueOf(7)) < 0);
   }

   @Test
   void numberComparerFirstGreaterReturnsPositive() {
      assertTrue(DataComparer.NUM_COMPARER.compare(Integer.valueOf(7), Integer.valueOf(3)) > 0);
   }

   @Test
   void numberComparerBothNullReturnsZero() {
      assertEquals(0, DataComparer.NUM_COMPARER.compare(null, null));
   }

   @Test
   void numberComparerFirstNullReturnsNegative() {
      assertEquals(-1, DataComparer.NUM_COMPARER.compare(null, Integer.valueOf(1)));
   }

   @Test
   void numberComparerSecondNullReturnsPositive() {
      assertEquals(1, DataComparer.NUM_COMPARER.compare(Integer.valueOf(1), null));
   }

   @Test
   void numberComparerLongValuesComparedAsLong() {
      // Both longs: use long comparison path
      assertEquals(0, DataComparer.NUM_COMPARER.compare(Long.valueOf(100L), Long.valueOf(100L)));
      assertTrue(DataComparer.NUM_COMPARER.compare(Long.valueOf(50L), Long.valueOf(100L)) < 0);
   }

   @Test
   void numberComparerDoubleNullSentinelFirstReturnsNegative() {
      assertEquals(-1, DataComparer.NUM_COMPARER.compare(Tool.NULL_DOUBLE, 1.0));
   }

   @Test
   void numberComparerDoubleNullSentinelSecondReturnsPositive() {
      assertEquals(1, DataComparer.NUM_COMPARER.compare(1.0, Tool.NULL_DOUBLE));
   }

   @Test
   void numberComparerDoubleNullSentinelBothReturnsZero() {
      assertEquals(0, DataComparer.NUM_COMPARER.compare(Tool.NULL_DOUBLE, Tool.NULL_DOUBLE));
   }

   // -----------------------------------------------------------------------
   // DateComparer
   // -----------------------------------------------------------------------

   @Test
   void dateComparerEqualDatesReturnsZero() {
      Date d = new Date(1000L);
      assertEquals(0, DataComparer.DATE_COMPARER.compare(d, new Date(1000L)));
   }

   @Test
   void dateComparerFirstEarlierReturnsNegative() {
      assertTrue(DataComparer.DATE_COMPARER.compare(new Date(1000L), new Date(2000L)) < 0);
   }

   @Test
   void dateComparerFirstLaterReturnsPositive() {
      assertTrue(DataComparer.DATE_COMPARER.compare(new Date(2000L), new Date(1000L)) > 0);
   }

   @Test
   void dateComparerBothNullReturnsZero() {
      assertEquals(0, DataComparer.DATE_COMPARER.compare(null, null));
   }

   @Test
   void dateComparerFirstNullReturnsNegative() {
      assertEquals(-1, DataComparer.DATE_COMPARER.compare(null, new Date()));
   }

   @Test
   void dateComparerSecondNullReturnsPositive() {
      assertEquals(1, DataComparer.DATE_COMPARER.compare(new Date(), null));
   }

   @Test
   void dateComparerDoubleNullSentinelFirstReturnsNegative() {
      assertEquals(-1, DataComparer.DATE_COMPARER.compare(Tool.NULL_DOUBLE, 1.0));
   }

   // -----------------------------------------------------------------------
   // BooleanComparer
   // -----------------------------------------------------------------------

   @Test
   void booleanComparerTrueTrueReturnsZero() {
      assertEquals(0, DataComparer.BOOL_COMPARER.compare(Boolean.TRUE, Boolean.TRUE));
   }

   @Test
   void booleanComparerFalseFalseReturnsZero() {
      assertEquals(0, DataComparer.BOOL_COMPARER.compare(Boolean.FALSE, Boolean.FALSE));
   }

   @Test
   void booleanComparerTrueGreaterThanFalse() {
      assertTrue(DataComparer.BOOL_COMPARER.compare(Boolean.TRUE, Boolean.FALSE) > 0);
   }

   @Test
   void booleanComparerFalseLessThanTrue() {
      assertTrue(DataComparer.BOOL_COMPARER.compare(Boolean.FALSE, Boolean.TRUE) < 0);
   }

   @Test
   void booleanComparerBothNullReturnsZero() {
      assertEquals(0, DataComparer.BOOL_COMPARER.compare(null, null));
   }

   @Test
   void booleanComparerFirstNullReturnsNegative() {
      assertEquals(-1, DataComparer.BOOL_COMPARER.compare(null, Boolean.FALSE));
   }

   @Test
   void booleanComparerSecondNullReturnsPositive() {
      assertEquals(1, DataComparer.BOOL_COMPARER.compare(Boolean.FALSE, null));
   }
}
