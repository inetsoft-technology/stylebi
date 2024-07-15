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

import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome()
class ToolTest {
   @Test
   void testByteEncode() {
      String actual = Tool.byteEncode("A & B");
      assertEquals("A ~_26_~ B", actual);
   }

   @Test
   void testByteDecode() {
      Map<String, String> tests = new HashMap<>();
      tests.put("This string has no encoding", "This string has no encoding");
      tests.put("Not [encoded]", "Not [encoded]");
      tests.put("Not ~_encoded_~", "Not ~_encoded_~");
      tests.put("A[20][26][20]B", "A & B");
      tests.put("A~_20_~~_26_~~_20_~B", "A & B");
      tests.put("^[20]^", "^[20]^");
      tests.put("^[A[20][26][20]B]^", "^[A & B]^");

      for(Map.Entry<String, String> e : tests.entrySet()) {
         String input = e.getKey();
         String expected = e.getValue();
         String actual = Tool.byteDecode(input);
         assertEquals(expected, actual);
      }
   }

   static String[] invalidDates() {
      return new String[] {
         "05/21/2015 12:40:40 - 07/15/2017 04:54:16",
         "2015",
         "/5/2015",
         "/05/2015",
         "user1",
         "0123456/5/15",
         "abcdef/5/15",
         "220305",
         "20220305",
         "2015, notamonth 05",
         "05 notamonth 2015",
         "notamonth 05 2015",
      };
   }

   @ParameterizedTest(name = "Invalid date: {arguments}")
   @MethodSource("invalidDates")
   void invalidDate(String invalidDate) {
      assertFalse(Tool.isDate(invalidDate), String.format("Invalid date failed date check: %s", invalidDate));
   }

   static String[] validDates() {
      return new String[] {
         "2022-06-28T00:00:00.000-04:00",
         "2022-06-28T00:00:00.000+04:00",
         "2022-06-28T00:00:00.000+0400",
         "2022-06-28T00:00:00.000-0400",
         "2022-06-28T00:00:00.000+04",
         "2022-06-28T00:00:00.000-04",
         "2022-06-28T00:00:00.000+04:30",
         "2022-06-28T00:00:00.000-04:30",
         "2022-06-28",

         "2020-02-05T19:33:34.045871Z",
         "05/21/2015 12:40:40",
         "05/21/2015",
         "21/05/2015",
         "2015/05/21",
         "2013-07-05 00:00:00.0",

         "1/31/2022 6:13:00 AM",
         "1/31/2022 6:13:00 PM",
         "1/31/2022 6:13:00 am",
         "1/31/2022 6:13:00 pm",
         "1/31/2022 6:13:00 A.M.",
         "1/31/2022 6:13:00 P.M.",

         "2022, Jan 9",
         "2022, Jan 09",
         "2022, January 9",
         "2022, January 09",
         "Jan 9, 2022",
         "Jan 09, 2022",
         "9 Jan, 2022",
         "09 Jan, 2022",
      };
   };

   @ParameterizedTest(name = "Valid date: {arguments}")
   @MethodSource("validDates")
   void validDate(String validDate) {
      assertTrue(Tool.isDate(validDate), String.format("Valid date failed date check: %s", validDate));
   }
}
