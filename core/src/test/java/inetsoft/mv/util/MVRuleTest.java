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
package inetsoft.mv.util;

import inetsoft.sree.SreeEnv;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome
class MVRuleTest {

   // -----------------------------------------------------------------------
   // parseDuration
   // -----------------------------------------------------------------------

   @Test
   void parseDurationNullReturnsZero() {
      assertEquals(0L, MVRule.parseDuration(null));
   }

   @Test
   void parseDurationNumericMillisecondString() {
      assertEquals(5000L, MVRule.parseDuration("5000"));
   }

   @Test
   void parseDurationNumericZeroString() {
      assertEquals(0L, MVRule.parseDuration("0"));
   }

   @Test
   void parseDurationIso8601Minutes() {
      // PT5M = 5 minutes = 300000 ms
      assertEquals(300000L, MVRule.parseDuration("PT5M"));
   }

   @Test
   void parseDurationIso8601Hours() {
      // PT1H = 1 hour = 3600000 ms
      assertEquals(3600000L, MVRule.parseDuration("PT1H"));
   }

   @Test
   void parseDurationIso8601Seconds() {
      // PT30S = 30 seconds = 30000 ms
      assertEquals(30000L, MVRule.parseDuration("PT30S"));
   }

   @Test
   void parseDurationInvalidIso8601ThrowsException() {
      // An invalid ISO 8601 string that is also not numeric should throw
      assertThrows(Exception.class, () -> MVRule.parseDuration("not-a-duration"));
   }

   // -----------------------------------------------------------------------
   // isStale — uses "mv.freshness" property
   // -----------------------------------------------------------------------

   @Test
   void isStaleReturnsFalseWhenFreshnessPropertyNotSet() {
      SreeEnv.remove("mv.freshness");
      // Without a property, duration is 0, so isStale always returns false
      assertFalse(MVRule.isStale(System.currentTimeMillis() - 1_000_000L));
   }

   @Test
   void isStaleReturnsTrueWhenTimestampOlderThanFreshness() {
      // freshness = 1000ms; timestamp 2 seconds ago => stale
      SreeEnv.setProperty("mv.freshness", "1000");
      long twoSecondsAgo = System.currentTimeMillis() - 2000L;

      try {
         assertTrue(MVRule.isStale(twoSecondsAgo));
      }
      finally {
         SreeEnv.remove("mv.freshness");
      }
   }

   @Test
   void isStaleReturnsFalseWhenTimestampWithinFreshness() {
      // freshness = 60000ms (1 min); timestamp is very recent
      SreeEnv.setProperty("mv.freshness", "60000");
      long recentTimestamp = System.currentTimeMillis() - 100L;

      try {
         assertFalse(MVRule.isStale(recentTimestamp));
      }
      finally {
         SreeEnv.remove("mv.freshness");
      }
   }

   // -----------------------------------------------------------------------
   // isExpired — uses "mv.maxAge" property
   // -----------------------------------------------------------------------

   @Test
   void isExpiredReturnsFalseWhenMaxAgePropertyNotSet() {
      SreeEnv.remove("mv.maxAge");
      assertFalse(MVRule.isExpired(System.currentTimeMillis() - 1_000_000L));
   }

   @Test
   void isExpiredReturnsTrueWhenTimestampOlderThanMaxAge() {
      // maxAge = 1000ms; timestamp 2 seconds ago => expired
      SreeEnv.setProperty("mv.maxAge", "1000");
      long twoSecondsAgo = System.currentTimeMillis() - 2000L;

      try {
         assertTrue(MVRule.isExpired(twoSecondsAgo));
      }
      finally {
         SreeEnv.remove("mv.maxAge");
      }
   }

   @Test
   void isExpiredReturnsFalseWhenTimestampWithinMaxAge() {
      // maxAge = 60000ms; timestamp is very recent
      SreeEnv.setProperty("mv.maxAge", "60000");
      long recentTimestamp = System.currentTimeMillis() - 100L;

      try {
         assertFalse(MVRule.isExpired(recentTimestamp));
      }
      finally {
         SreeEnv.remove("mv.maxAge");
      }
   }

   @Test
   void isExpiredWithIso8601MaxAge() {
      // maxAge = PT1S (1 second); timestamp 2 seconds ago => expired
      SreeEnv.setProperty("mv.maxAge", "PT1S");
      long twoSecondsAgo = System.currentTimeMillis() - 2000L;

      try {
         assertTrue(MVRule.isExpired(twoSecondsAgo));
      }
      finally {
         SreeEnv.remove("mv.maxAge");
      }
   }

   // -----------------------------------------------------------------------
   // isAuto
   // -----------------------------------------------------------------------

   @Test
   void isAutoReturnsFalseWhenNeitherPropertySet() {
      SreeEnv.remove("mv.freshness");
      SreeEnv.remove("mv.maxAge");
      assertFalse(MVRule.isAuto());
   }

   @Test
   void isAutoReturnsTrueWhenFreshnessPropertySet() {
      SreeEnv.setProperty("mv.freshness", "5000");

      try {
         assertTrue(MVRule.isAuto());
      }
      finally {
         SreeEnv.remove("mv.freshness");
      }
   }

   @Test
   void isAutoReturnsTrueWhenMaxAgePropertySet() {
      SreeEnv.setProperty("mv.maxAge", "5000");

      try {
         assertTrue(MVRule.isAuto());
      }
      finally {
         SreeEnv.remove("mv.maxAge");
      }
   }

   @Test
   void isAutoReturnsTrueWhenBothPropertiesSet() {
      SreeEnv.setProperty("mv.freshness", "5000");
      SreeEnv.setProperty("mv.maxAge", "10000");

      try {
         assertTrue(MVRule.isAuto());
      }
      finally {
         SreeEnv.remove("mv.freshness");
         SreeEnv.remove("mv.maxAge");
      }
   }
}
