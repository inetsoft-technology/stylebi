/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.script.formula;

import inetsoft.report.filter.DefaultComparer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

public class DatePartComparerTest {
   private DefaultComparer monthNameComparer;
   private DefaultComparer weekdayNameComparer;

   @BeforeEach
   void setUp() {
      monthNameComparer = DatePartComparer.getMonthNameComparer();
      weekdayNameComparer = DatePartComparer.getWeekdayNameComparer();
   }

   @Test
   void testCompareMonthNames() {
      assertTrue(monthNameComparer.compare(Calendar.JANUARY, Calendar.FEBRUARY) < 0);
      assertTrue(monthNameComparer.compare(Calendar.DECEMBER, Calendar.NOVEMBER) > 0);
      assertTrue(monthNameComparer.compare(Calendar.DECEMBER, Calendar.JANUARY) > 0);
      assertEquals(0, monthNameComparer.compare(Calendar.MARCH, Calendar.MARCH));
   }

   @Test
   void testCompareWeekdayNames() {
      assertTrue(weekdayNameComparer.compare(Calendar.SUNDAY, Calendar.MONDAY) < 0);
      assertTrue(weekdayNameComparer.compare(Calendar.SATURDAY, Calendar.FRIDAY) > 0);
      assertEquals(0, weekdayNameComparer.compare(Calendar.WEDNESDAY, Calendar.WEDNESDAY));
   }
}
