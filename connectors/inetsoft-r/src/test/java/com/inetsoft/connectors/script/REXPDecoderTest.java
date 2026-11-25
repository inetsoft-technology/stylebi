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
package com.inetsoft.connectors.script;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rosuda.REngine.*;

import java.sql.Timestamp;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class REXPDecoderTest {
   private TimeZone savedZone;

   @BeforeEach
   void setZone() {
      savedZone = TimeZone.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
   }

   @AfterEach
   void restoreZone() {
      TimeZone.setDefault(savedZone);
   }

   private static REXPList attrs(String key, REXP value) {
      return new REXPList(new RList(new REXP[] { value }, new String[] { key }));
   }

   private static REXPList attrs(REXP cls, REXP levels) {
      return new REXPList(new RList(new REXP[] { cls, levels }, new String[] { "class", "levels" }));
   }

   private static RList frame(String[] names, REXP... cols) {
      return new RList(cols, names);
   }

   @Test
   void dateKeepsCalendarDayInNonUtcZoneAndNaIsNull() throws Exception {
      REXP col = new REXPDouble(new double[] { 18993, REXPDouble.NA, Double.NaN },
                                attrs("class", new REXPString("Date")));
      Object[] v = REXPDecoder.decodeColumn("d", col);
      assertEquals("2022-01-01", v[0].toString());
      assertTrue(v[0] instanceof java.sql.Date);
      assertNull(v[1]);
      assertNull(v[2]);
   }

   @Test
   void integerStoredDateIsConverted() throws Exception {
      REXP col = new REXPInteger(new int[] { 18993, REXPInteger.NA },
                                 attrs("class", new REXPString(new String[] { "IDate", "Date" })));
      Object[] v = REXPDecoder.decodeColumn("d", col);
      assertEquals("2022-01-01", v[0].toString());
      assertNull(v[1]);
   }

   @Test
   void posixctBecomesTimestamp() throws Exception {
      REXP col = new REXPDouble(new double[] { 1640995200.5, REXPDouble.NA, Double.POSITIVE_INFINITY },
         attrs("class", new REXPString(new String[] { "POSIXct", "POSIXt" })));
      Object[] v = REXPDecoder.decodeColumn("t", col);
      assertEquals(new Timestamp(1640995200500L), v[0]);
      assertNull(v[1]);
      assertNull(v[2]);
   }

   @Test
   void logicalNaIsNull() throws Exception {
      Object[] v = REXPDecoder.decodeColumn(
         "l", new REXPLogical(new byte[] { 1, 0, REXPLogical.NA }));
      assertArrayEquals(new Object[] { true, false, null }, v);
   }

   @Test
   void doubleNaIsNullButNaNStays() throws Exception {
      Object[] v = REXPDecoder.decodeColumn(
         "x", new REXPDouble(new double[] { 1.5, REXPDouble.NA, Double.NaN }));
      assertEquals(1.5, v[0]);
      assertNull(v[1]);
      assertTrue(v[2] instanceof Double && ((Double) v[2]).isNaN());
   }

   @Test
   void orderedFactorArrivesAsIntegerWithLevelsAndGivesLabels() throws Exception {
      REXP col = new REXPInteger(new int[] { 2, 1, REXPInteger.NA },
         attrs(new REXPString(new String[] { "ordered", "factor" }),
               new REXPString(new String[] { "lo", "hi" })));
      assertFalse(col instanceof REXPFactor);
      assertArrayEquals(new Object[] { "hi", "lo", null }, REXPDecoder.decodeColumn("f", col));
   }

   @Test
   void unorderedFactorStillGivesLabels() throws Exception {
      REXP col = new REXPFactor(new int[] { 1, 2 }, new String[] { "a", "b" });
      assertArrayEquals(new Object[] { "a", "b" }, REXPDecoder.decodeColumn("f", col));
   }

   @Test
   void integerAndCharacterUnchanged() throws Exception {
      assertArrayEquals(new Object[] { 1, null, 3 },
         REXPDecoder.decodeColumn("i", new REXPInteger(new int[] { 1, REXPInteger.NA, 3 })));
      assertArrayEquals(new Object[] { "a", null },
         REXPDecoder.decodeColumn("s", new REXPString(new String[] { "a", null })));
   }

   @Test
   void rawIsUnsigned() throws Exception {
      assertArrayEquals(new Object[] { 255, 1 },
         REXPDecoder.decodeColumn("r", new REXPRaw(new byte[] { (byte) 255, 1 })));
   }

   @Test
   void listColumnIsRefusedWithColumnName() {
      REXP col = new REXPGenericVector(new RList(new REXP[] { new REXPInteger(1) }));
      IllegalStateException e = assertThrows(IllegalStateException.class,
         () -> REXPDecoder.decodeFrame(frame(new String[] { "big" }, col)));
      assertTrue(e.getMessage().contains("'big'"));
   }

   @Test
   void unequalLengthsAreRefusedWithColumnNames() {
      RList list = frame(new String[] { "a", "b" },
                         new REXPInteger(new int[] { 1, 2, 3 }), new REXPInteger(new int[] { 1, 2 }));
      IllegalStateException e = assertThrows(IllegalStateException.class,
                                             () -> REXPDecoder.decodeFrame(list));
      assertTrue(e.getMessage().contains("'a'") && e.getMessage().contains("'b'"));
   }

   @Test
   void duplicateNamesKeepOwnDataAndGetUniqueHeaders() throws Exception {
      RList list = frame(new String[] { "a", "a" },
                         new REXPInteger(new int[] { 1, 2 }), new REXPInteger(new int[] { 10, 20 }));
      Object[][] cells = REXPDecoder.decodeFrame(list);
      assertArrayEquals(new Object[] { "a", "a.1" }, cells[0]);
      assertArrayEquals(new Object[] { 1, 10 }, cells[1]);
      assertArrayEquals(new Object[] { 2, 20 }, cells[2]);
   }

   @Test
   void wholeFrameHasTypedCells() throws Exception {
      RList list = frame(new String[] { "d", "l", "x" },
         new REXPDouble(new double[] { 18993 }, attrs("class", new REXPString("Date"))),
         new REXPLogical(new byte[] { REXPLogical.NA }),
         new REXPDouble(new double[] { REXPDouble.NA }));
      Object[][] cells = REXPDecoder.decodeFrame(list);
      assertEquals(2, cells.length);
      assertEquals("2022-01-01", cells[1][0].toString());
      assertNull(cells[1][1]);
      assertNull(cells[1][2]);
   }
}
