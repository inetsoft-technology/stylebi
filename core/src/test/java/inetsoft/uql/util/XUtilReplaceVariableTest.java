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
package inetsoft.uql.util;

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilReplaceVariableTest {
   // Bug #77450, java.time reads dates before 1582-10-15 as proleptic Gregorian
   @Test
   void formatsJulianDate() {
      Date date = date(1012, 2, 29);
      assertEquals("1012-02-29", replace(new Timestamp(date.getTime())));
      assertEquals("1012-02-29", replace(new java.sql.Date(date.getTime())));
   }

   // Bug #77450, java.time reads dates before 1901 in local mean time (+5:21:10 in Kolkata)
   @Test
   void formatsDateBefore1901InKolkata() {
      TimeZone oldZone = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
         Date date = date(1882, 6, 1);
         assertEquals("1882-06-01", replace(new Timestamp(date.getTime())));
         assertEquals("1882-06-01", replace(date));
      }
      finally {
         TimeZone.setDefault(oldZone);
      }
   }

   @Test
   void formatsModernDate() {
      GregorianCalendar cal = new GregorianCalendar(2024, Calendar.MARCH, 10, 23, 59, 59);
      assertEquals("2024-03-10", replace(new Timestamp(cal.getTimeInMillis())));
      assertEquals("2024-03-10", replace(new java.sql.Date(cal.getTimeInMillis())));
   }

   private static String replace(Date value) {
      VariableTable vars = new VariableTable();
      vars.put("d", value);
      vars.putFormat("d", "yyyy-MM-dd");
      return XUtil.replaceVariable("$(d)", vars);
   }

   private static Date date(int year, int month, int day) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, month - 1, day);
      return cal.getTime();
   }
}
