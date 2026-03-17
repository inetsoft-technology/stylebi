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

import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class TimeZoneUtilTest {

   // Helper: build a TimeZone from an ID and invoke getTimeZoneID
   private String getMapped(String tzId) {
      TimeZone tz = TimeZone.getTimeZone(tzId);
      return TimeZoneUtil.getTimeZoneID(tz);
   }

   @Test
   void gmtNegativeHoursMapToEtcGmtPositive() {
      // e.g. GMT-5 → Etc/GMT+5  (POSIX sign convention)
      assertEquals("Etc/GMT+5", getMapped("GMT-5"));
   }

   @Test
   void gmtPositiveHoursMapToEtcGmtNegative() {
      // e.g. GMT+8 → Etc/GMT-8
      assertEquals("Etc/GMT-8", getMapped("GMT+8"));
   }

   @Test
   void gmtZeroNegativeMapsToEtcGmtPlusZero() {
      assertEquals("Etc/GMT+0", getMapped("GMT-0"));
   }

   @Test
   void gmtZeroPositiveMapsToEtcGmtMinusZero() {
      assertEquals("Etc/GMT-0", getMapped("GMT+0"));
   }

   @Test
   void gmtMinusTwelveMapped() {
      assertEquals("Etc/GMT+12", getMapped("GMT-12"));
   }

   @Test
   void gmtPlusTwelveMapped() {
      assertEquals("Etc/GMT-12", getMapped("GMT+12"));
   }

   @Test
   void gmtPlusOneWithLeadingZeroMapped() {
      // GMT+01 → Etc/GMT-1
      assertEquals("Etc/GMT-1", getMapped("GMT+01"));
   }

   @Test
   void gmtMinusOneWithLeadingZeroMapped() {
      // GMT-01 → Etc/GMT+1
      assertEquals("Etc/GMT+1", getMapped("GMT-01"));
   }

   @Test
   void halfHourOffsetPositiveFiveThirtyStripsMinutes() {
      // GMT+5:30 → key is "GMT+5" → Etc/GMT-5
      // The implementation strips everything after ":" to get the key
      assertEquals("Etc/GMT-5", getMapped("GMT+5:30"));
   }

   @Test
   void halfHourOffsetPositiveNineThirtyStripsMinutes() {
      // GMT+9:30 → key is "GMT+9" → Etc/GMT-9
      assertEquals("Etc/GMT-9", getMapped("GMT+9:30"));
   }

   @Test
   void namedTimeZoneNotInMappingReturnsOriginalId() {
      // "America/New_York" is not in idMappings, should be returned as-is
      String result = getMapped("America/New_York");
      assertEquals("America/New_York", result);
   }

   @Test
   void utcNotInMappingReturnsUtc() {
      String result = getMapped("UTC");
      assertEquals("UTC", result);
   }

   @Test
   void gmtLiteralNotInMappingReturnsGmt() {
      // "GMT" alone is not in idMappings (only "GMT+n" / "GMT-n" are)
      String result = getMapped("GMT");
      assertEquals("GMT", result);
   }
}
