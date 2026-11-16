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
package inetsoft.graph.guide.axis;

import inetsoft.graph.scale.TimeScale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.text.Format;
import java.util.Calendar;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The compact date patterns follow the locale's order of year, month and day.
 */
@Tag("core")
class CompactDatesTest {
   @Test
   void monthFirstLocales() {
      assertArrayEquals(new String[] { "'Q'QQ yyyy", "'Q'QQ" },
                        CompactDates.patterns(TimeScale.QUARTER, Locale.US));
      assertArrayEquals(new String[] { "MMM yyyy", "MMM" },
                        CompactDates.patterns(TimeScale.MONTH, Locale.US));
      assertArrayEquals(new String[] { "MMM d, yyyy", "MMM d" },
                        CompactDates.patterns(TimeScale.DAY, Locale.US));
      assertArrayEquals(new String[] { "MMM d, yyyy", "MMM d" },
                        CompactDates.patterns(TimeScale.WEEK, Locale.US));
   }

   @Test
   void dayFirstLocales() {
      for(Locale locale : new Locale[] { Locale.GERMANY, Locale.UK }) {
         assertArrayEquals(new String[] { "d MMM yyyy", "d MMM" },
                           CompactDates.patterns(TimeScale.DAY, locale), locale.toString());
         assertArrayEquals(new String[] { "MMM yyyy", "MMM" },
                           CompactDates.patterns(TimeScale.MONTH, locale), locale.toString());
      }
   }

   @Test
   void yearFirstLocales() {
      assertArrayEquals(new String[] { "yyyy MMM", "MMM" },
                        CompactDates.patterns(TimeScale.MONTH, Locale.JAPAN));
      assertArrayEquals(new String[] { "yyyy MMM d", "MMM d" },
                        CompactDates.patterns(TimeScale.DAY, Locale.JAPAN));
      assertArrayEquals(new String[] { "yyyy 'Q'QQ", "'Q'QQ" },
                        CompactDates.patterns(TimeScale.QUARTER, Locale.JAPAN));
   }

   @Test
   void yearAndTimeUnitsHaveNone() {
      for(int unit : new int[] { TimeScale.YEAR, TimeScale.HOUR, TimeScale.MINUTE,
                                 TimeScale.SECOND })
      {
         assertNull(CompactDates.patterns(unit, Locale.US), "unit " + unit);
         assertNull(new CompactDates(unit, Locale.US).full(null), "unit " + unit);
      }
   }

   @Test
   void aQuarterFormats() {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(2025, Calendar.APRIL, 1);
      CompactDates dates = new CompactDates(TimeScale.QUARTER, Locale.US);
      Format full = dates.full(null);

      assertEquals("Q2 2025", full.format(cal.getTime()));
      assertEquals("Q2", dates.brief(null).format(cal.getTime()));
   }

   @Test
   void equalsUsesBothFields() {
      assertEquals(new CompactDates(TimeScale.MONTH, Locale.US),
                   new CompactDates(TimeScale.MONTH, Locale.US));
      assertEquals(new CompactDates(TimeScale.MONTH, Locale.US).hashCode(),
                   new CompactDates(TimeScale.MONTH, Locale.US).hashCode());
      assertNotEquals(new CompactDates(TimeScale.MONTH, Locale.US),
                      new CompactDates(TimeScale.DAY, Locale.US));
      assertNotEquals(new CompactDates(TimeScale.MONTH, Locale.US),
                      new CompactDates(TimeScale.MONTH, Locale.UK));
   }
}
