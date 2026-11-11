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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81090, out-of-range integral text must not wrap/saturate into a different number.
 */
@Tag("core")
class CoreToolIntegralRangeTest {
   @Test
   void byteInRangeKept() {
      assertEquals((byte) 127, CoreTool.getData("byte", "127"));
      assertEquals((byte) -128, CoreTool.getData("byte", "-128"));
   }

   @Test
   void byteOutOfRangeIsNull() {
      assertNull(CoreTool.getData("byte", "300"));
      assertNull(CoreTool.getData("byte", "128"));
      assertNull(CoreTool.getData("byte", "-129"));
   }

   @Test
   void shortRange() {
      assertEquals((short) 32767, CoreTool.getData("short", "32767"));
      assertNull(CoreTool.getData("short", "32768"));
      assertNull(CoreTool.getData("short", "70000"));
   }

   @Test
   void integerRange() {
      assertEquals(Integer.MAX_VALUE, CoreTool.getData("integer", "2147483647"));
      assertEquals(Integer.MIN_VALUE, CoreTool.getData("integer", "-2147483648"));
      assertNull(CoreTool.getData("integer", "2147483648"));
      assertNull(CoreTool.getData("integer", "3000000000"));
      assertNull(CoreTool.getData("integer", "-2147483649"));
   }

   @Test
   void longRange() {
      assertEquals(Long.MAX_VALUE, CoreTool.getData("long", "9223372036854775807"));
      assertNull(CoreTool.getData("long", "9223372036854775808"));
      assertNull(CoreTool.getData("long", "-9223372036854775809"));
   }

   @Test
   void invalidTextIsNull() {
      assertNull(CoreTool.getData("byte", "abc"));
      assertNull(CoreTool.getData("integer", "abc"));
      assertNull(CoreTool.getData("long", "abc"));
   }

   @Test
   void decimalIsStillTruncated() {
      assertEquals((byte) 5, CoreTool.getData("byte", "5.7"));
      assertEquals((short) 5, CoreTool.getData("short", "5.7"));
      assertEquals(5, CoreTool.getData("integer", "5.7"));
      assertEquals((byte) -5, CoreTool.getData("byte", "-5.7"));
      assertEquals(5.7, CoreTool.getData("long", "5.7"));
   }

   @Test
   void longExtremeExponentsAreFastAndKeepLegacyResult() {
      // BigDecimal.toBigInteger() on these would take seconds to forever
      assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
         assertEquals(0.0, CoreTool.getData("long", "1e-100000000"));
         assertEquals(0.0, CoreTool.getData("long", "1e-999999999"));
         assertEquals(-0.0, CoreTool.getData("long", "-1e-999999999"));
         assertNull(CoreTool.getData("long", "1e999999999"));
         assertNull(CoreTool.getData("long", "-1e999999999"));
         assertNull(CoreTool.getData("long", "1e30"));
      });
   }

   @Test
   void longBoundaryWithDecimalSyntax() {
      assertEquals(9.223372036854775807E18, CoreTool.getData("long", "9223372036854775807.5"));
      assertNull(CoreTool.getData("long", "9223372036854775808.0"));
      assertNull(CoreTool.getData("long", "9.223372036854775808E18"));
      assertEquals(-9.223372036854775808E18, CoreTool.getData("long", "-9223372036854775808.5"));
      assertNull(CoreTool.getData("long", "-9223372036854775809.0"));
      assertEquals(1.0E18, CoreTool.getData("long", "1e18"));
   }

   @Test
   void narrowTypesExtremeExponentsAreFast() {
      assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
         assertEquals(0, CoreTool.getData("integer", "1e-999999999"));
         assertNull(CoreTool.getData("integer", "1e999999999"));
         assertEquals((byte) 0, CoreTool.getData("byte", "1e-999999999"));
      });
   }
}
