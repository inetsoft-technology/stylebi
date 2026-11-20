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
package inetsoft.report.composition.graph;

import inetsoft.graph.guide.form.TargetParameter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TODAY value of a date target (e.g. a Gantt's "today" line) is translated when the chart is
 * generated, to the last instant of the current day.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TargetParameterWrapperTodayTest {
   @Test
   void todayIsTheEndOfTheCurrentDay() {
      Calendar before = new GregorianCalendar();
      long ms = (long) unwrap("TODAY", true).getConstantValue();
      Calendar after = new GregorianCalendar();

      Calendar value = new GregorianCalendar();
      value.setTimeInMillis(ms);

      assertEquals(23, value.get(Calendar.HOUR_OF_DAY));
      assertEquals(59, value.get(Calendar.MINUTE));
      assertEquals(59, value.get(Calendar.SECOND));
      assertEquals(999, value.get(Calendar.MILLISECOND));
      // the day the chart was generated (tolerating a midnight rollover during the test)
      assertTrue(value.get(Calendar.DAY_OF_YEAR) == before.get(Calendar.DAY_OF_YEAR) ||
                 value.get(Calendar.DAY_OF_YEAR) == after.get(Calendar.DAY_OF_YEAR));
   }

   @Test
   void todayIgnoresCaseAndSurroundingSpaces() {
      long upper = (long) unwrap("TODAY", true).getConstantValue();
      long lower = (long) unwrap("  today ", true).getConstantValue();

      // both are the end of the same day, unless the day rolled over between the two calls
      assertTrue(upper == lower || Math.abs(upper - lower) <= 24L * 60 * 60 * 1000);
   }

   @Test
   void aFixedDateIsStillParsed() {
      Calendar expected = new GregorianCalendar(2026, Calendar.NOVEMBER, 19, 10, 30, 0);
      assertEquals(expected.getTimeInMillis() / 1000,
                   (long) unwrap("2026-11-19 10:30:00", true).getConstantValue() / 1000);
   }

   @Test
   void aNumberTargetIsUnchanged() {
      assertEquals(5, (long) unwrap("5", false).getConstantValue());
   }

   private static TargetParameter unwrap(String value, boolean dateField) {
      TargetParameterWrapper wrapper = new TargetParameterWrapper();
      wrapper.setDateField(dateField);
      wrapper.setConstantValue(value);
      TargetParameter[] params = wrapper.unwrap();
      assertEquals(1, params.length);
      return params[0];
   }
}
