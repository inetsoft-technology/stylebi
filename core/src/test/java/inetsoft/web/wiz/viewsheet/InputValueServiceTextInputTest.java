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
package inetsoft.web.wiz.viewsheet;

import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug 78397 D5: a free-entry text input holds what the editor's own type can represent. The
 * shared option.validate leniently saturates integers and rolls impossible dates over, so the
 * wiz path checks the raw value strictly first.
 */
@Tag("core")
class InputValueServiceTextInputTest {
   private static TextInputVSAssemblyInfo text(ColumnOption option) {
      TextInputVSAssemblyInfo info = mock(TextInputVSAssemblyInfo.class);
      when(info.getColumnOption()).thenReturn(option);
      return info;
   }

   private static void check(ColumnOption option, Object value) {
      InputValueService.checkValue(text(option), "string", Arrays.asList(value), false, "Ti",
                                   "a text input");
   }

   private static void refuses(ColumnOption option, Object value, String... fragments) {
      Exception e = assertThrows(IllegalArgumentException.class, () -> check(option, value),
                                 String.valueOf(value));
      assertTrue(e.getMessage().contains("Ti"), e.getMessage());

      for(String f : fragments) {
         assertTrue(e.getMessage().contains(f), e.getMessage());
      }
   }

   @Test
   void refusesIntegerOverflowAndFractions() {
      IntegerColumnOption opt = new IntegerColumnOption();
      refuses(opt, "3000000000", "3000000000");
      refuses(opt, 3000000000L, "3000000000");
      refuses(opt, "12.7", "12.7");
      refuses(opt, "abc", "abc");
   }

   @Test
   void acceptsIntegerControls() {
      IntegerColumnOption opt = new IntegerColumnOption();
      assertDoesNotThrow(() -> check(opt, 42));
      assertDoesNotThrow(() -> check(opt, "42"));
      assertDoesNotThrow(() -> check(opt, 3.0));
      assertDoesNotThrow(() -> check(opt, "1e3"));
      assertDoesNotThrow(() -> check(opt, "-2147483648"));
   }

   @Test
   void enforcesIntegerBoundsServerSide() {
      IntegerColumnOption opt = new IntegerColumnOption(10, 1, null, false);
      refuses(opt, 11, "11");
      refuses(opt, 0, "0");
      assertDoesNotThrow(() -> check(opt, 5));
   }

   @Test
   void refusesImpossibleDates() {
      DateColumnOption opt = new DateColumnOption();
      refuses(opt, "2026-02-30", "2026-02-30");
      refuses(opt, "2026-13-45", "2026-13-45");
      refuses(opt, "garbage", "garbage");
   }

   @Test
   void acceptsRealDatesAndTimestamps() {
      DateColumnOption opt = new DateColumnOption();
      assertDoesNotThrow(() -> check(opt, "2026-03-01"));
      assertDoesNotThrow(() -> check(opt, "2024-02-29"));
      assertDoesNotThrow(() -> check(opt, "2026-03-01 10:30:00"));
   }

   @Test
   void enforcesDateBounds() {
      DateColumnOption opt = new DateColumnOption("2026-12-31", "2026-01-01", null, false);
      refuses(opt, "2027-01-01", "2027-01-01");
      assertDoesNotThrow(() -> check(opt, "2026-06-15"));
   }

   @Test
   void refusesNonDecimalFloats() {
      FloatColumnOption opt = new FloatColumnOption();
      refuses(opt, "0x10", "0x10");
      refuses(opt, "NaN", "NaN");
      assertDoesNotThrow(() -> check(opt, "1.5"));
      assertDoesNotThrow(() -> check(opt, 2.5));
   }

   @Test
   void refusesNonScalarValuesOnAnyEditor() {
      for(ColumnOption opt : new ColumnOption[]{
         new IntegerColumnOption(), new DateColumnOption(), new TextColumnOption()})
      {
         refuses(opt, Map.of("a", 1), "string, number or boolean");
         refuses(opt, List.of("a"), "string, number or boolean");
      }
   }

   @Test
   void leavesTextEditorsAndClearsAlone() {
      assertDoesNotThrow(() -> check(new TextColumnOption(), "anything 12.7"));
      assertDoesNotThrow(() -> check(new IntegerColumnOption(), null));
      assertDoesNotThrow(() -> check(new IntegerColumnOption(), ""));
      assertDoesNotThrow(() -> InputValueService.checkValue(
         text(new IntegerColumnOption()), "string", List.of(), false, "Ti", "a text input"));
   }
}
