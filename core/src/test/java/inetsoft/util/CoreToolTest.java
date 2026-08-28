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
package inetsoft.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("core")
class CoreToolTest {
   private static final Date NOW = new Date();
   private static final Locale LOCALE = Locale.US;

   @ParameterizedTest
   @ValueSource(strings = { "full", "long", "medium", "short" })
   void createDateFormatTimeKindProducesTimeInstance(String keyword) {
      int style = styleFor(keyword);
      SimpleDateFormat fmt = CoreTool.createDateFormat(keyword, LOCALE, CoreTool.DateFormatKind.TIME);

      String actual = fmt.format(NOW);
      String expectedTime = DateFormat.getTimeInstance(style, LOCALE).format(NOW);
      String dateOnly = DateFormat.getDateInstance(style, LOCALE).format(NOW);

      assertEquals(expectedTime, actual);
      assertNotEquals(dateOnly, actual);
   }

   @ParameterizedTest
   @ValueSource(strings = { "full", "long", "medium", "short" })
   void createDateFormatDateTimeKindProducesDateTimeInstance(String keyword) {
      int style = styleFor(keyword);
      SimpleDateFormat fmt = CoreTool.createDateFormat(keyword, LOCALE, CoreTool.DateFormatKind.DATE_TIME);

      String actual = fmt.format(NOW);
      String expectedDateTime = DateFormat.getDateTimeInstance(style, style, LOCALE).format(NOW);
      String dateOnly = DateFormat.getDateInstance(style, LOCALE).format(NOW);

      assertEquals(expectedDateTime, actual);
      assertNotEquals(dateOnly, actual);
   }

   @ParameterizedTest
   @ValueSource(strings = { "full", "long", "medium", "short" })
   void createDateFormatTimeKindWithNullLocaleProducesTimeInstance(String keyword) {
      int style = styleFor(keyword);
      SimpleDateFormat fmt = CoreTool.createDateFormat(keyword, null, CoreTool.DateFormatKind.TIME);

      String actual = fmt.format(NOW);
      String expectedTime = DateFormat.getTimeInstance(style).format(NOW);
      String dateOnly = DateFormat.getDateInstance(style).format(NOW);

      assertEquals(expectedTime, actual);
      assertNotEquals(dateOnly, actual);
   }

   @ParameterizedTest
   @ValueSource(strings = { "full", "long", "medium", "short" })
   void createDateFormatOneArgOverloadStillProducesDateInstance(String keyword) {
      int style = styleFor(keyword);
      SimpleDateFormat fmt = CoreTool.createDateFormat(keyword);
      assertEquals(DateFormat.getDateInstance(style).format(NOW), fmt.format(NOW));
   }

   @ParameterizedTest
   @ValueSource(strings = { "full", "long", "medium", "short" })
   void createDateFormatTwoArgOverloadStillProducesDateInstance(String keyword) {
      int style = styleFor(keyword);
      SimpleDateFormat fmt = CoreTool.createDateFormat(keyword, LOCALE);
      assertEquals(DateFormat.getDateInstance(style, LOCALE).format(NOW), fmt.format(NOW));
   }

   private static int styleFor(String keyword) {
      return switch(keyword.toLowerCase()) {
         case "full" -> DateFormat.FULL;
         case "long" -> DateFormat.LONG;
         case "medium" -> DateFormat.MEDIUM;
         case "short" -> DateFormat.SHORT;
         default -> throw new IllegalArgumentException(keyword);
      };
   }
}
