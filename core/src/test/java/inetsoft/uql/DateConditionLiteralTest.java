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
package inetsoft.uql;

import inetsoft.test.*;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #78312: strict date literal validation shared by the condition writers. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateConditionLiteralTest {
   @Test
   void parseReturnsNullExactlyWhereGetObjectFallsBack() {
      assertNull(AbstractCondition.parseDateConditionValue(XSchema.DATE, "abc"));
      assertNull(AbstractCondition.parseDateConditionValue(XSchema.DATE, "2026-13-45"));
      assertNull(AbstractCondition.parseDateConditionValue(XSchema.DATE, "10:30"));
      assertNull(AbstractCondition.parseDateConditionValue(XSchema.TIME, "abc:def"));
      assertNull(AbstractCondition.parseDateConditionValue(XSchema.TIME_INSTANT, "{garbage}"));
      assertNull(AbstractCondition.parseDateConditionValue(XSchema.STRING, "2024-01-01"));
      assertNull(AbstractCondition.parseDateConditionValue(XSchema.DATE, null));
      assertTrue(AbstractCondition.parseDateConditionValue(XSchema.DATE, "2024-01-15")
                    instanceof java.sql.Date);
      assertTrue(AbstractCondition.parseDateConditionValue(XSchema.TIME, "10:30:00")
                    instanceof java.sql.Time);
      assertTrue(AbstractCondition.parseDateConditionValue(XSchema.TIME_INSTANT,
                                                           "{ts '2024-01-15 10:30:00'}")
                    instanceof java.sql.Timestamp);
   }

   @Test
   void getObjectStillFallsBackToNowWithSqlTypes() {
      assertTrue(AbstractCondition.getObject(XSchema.DATE, "abc") instanceof java.sql.Date);
      assertTrue(AbstractCondition.getObject(XSchema.TIME, "abc") instanceof java.sql.Time);
      assertTrue(AbstractCondition.getObject(XSchema.TIME_INSTANT, "abc")
                    instanceof java.sql.Timestamp);
   }

   @Test
   void canonicalizeRefusesWhatEitherCoercerMisreads() {
      for(String bad : new String[] { "abc", "2026-13-45", "", "  ", "1", "12", "{garbage}" }) {
         assertNull(DateConditionLiteral.canonicalize(XSchema.DATE, bad), bad);
      }

      // getObject parses this, Condition.addValue stores 00:00:00
      assertNull(DateConditionLiteral.canonicalize(XSchema.TIME, "10:30:00 AM"));
      assertNull(DateConditionLiteral.canonicalize(XSchema.TIME_INSTANT, "10:30:00"));
      assertNull(DateConditionLiteral.canonicalize(XSchema.DATE, null));
   }

   @Test
   void canonicalizeAcceptsHonestFormsTrimmed() {
      assertEquals("2024-01-15", DateConditionLiteral.canonicalize(XSchema.DATE, " 2024-01-15 "));
      assertEquals("{d '2024-01-15'}",
                   DateConditionLiteral.canonicalize(XSchema.DATE, "{d '2024-01-15'}"));
      assertEquals("{ts '2024-01-15 10:30:00'}", DateConditionLiteral.canonicalize(
         XSchema.TIME_INSTANT, "{ts '2024-01-15 10:30:00'}"));
      assertEquals("10:30:00", DateConditionLiteral.canonicalize(XSchema.TIME, "10:30:00"));
   }

   // Bug #78351: braced / HH:mm:ss forms rolled an impossible calendar value over
   @Test
   void canonicalizeRefusesImpossibleBracedAndClockValues() {
      String[][] bad = {
         { XSchema.DATE, "{d '2024-02-30'}" }, { XSchema.DATE, "{d '2023-02-29'}" },
         { XSchema.DATE, "{d '2024-04-31'}" }, { XSchema.DATE, "{d '2024-13-45'}" },
         { XSchema.DATE, "{d '2024-00-10'}" }, { XSchema.DATE, "{d '2024-01-00'}" },
         { XSchema.TIME_INSTANT, "{ts '2024-02-30 12:00:00'}" },
         { XSchema.TIME_INSTANT, "{ts '2024-02-30 25:61:00'}" },
         { XSchema.TIME_INSTANT, "{ts '2024-01-15 25:61:00'}" },
         { XSchema.TIME_INSTANT, "{ts '2024-01-15 10:30:99'}" },
         { XSchema.TIME_INSTANT, "{d '2024-02-30 10:00:00'}" },
         { XSchema.TIME_INSTANT, "{d '2024-02-30'}" },
         { XSchema.TIME, "{t '25:00:00'}" }, { XSchema.TIME, "25:00:00" },
         { XSchema.TIME, "10:61:00" }, { XSchema.TIME, "10:30:99" },
         { XSchema.TIME, "{t '10:30:99'}" },
      };

      for(String[] c : bad) {
         assertNull(AbstractCondition.parseDateConditionValue(c[0], c[1], true), c[1]);
         assertNull(DateConditionLiteral.canonicalize(c[0], c[1]), c[1]);
      }
   }

   @Test
   void canonicalizeKeepsEveryValidBracedAndClockForm() {
      String[][] ok = {
         { XSchema.DATE, "{d '2024-2-3'}" }, { XSchema.DATE, "{d '2024-02-29'}" },
         { XSchema.DATE, "{d '2024-12-31'}" }, { XSchema.DATE, "2024-02-29" },
         { XSchema.DATE, "02/29/2024" },
         { XSchema.TIME_INSTANT, "{ts '2024-01-15 9:05:00'}" },
         { XSchema.TIME_INSTANT, "{ts '2024-02-29 00:00:00'}" },
         { XSchema.TIME_INSTANT, "{ts '2024-12-31 23:59:59'}" },
         { XSchema.TIME_INSTANT, "{d '2024-02-29'}" },
         { XSchema.TIME_INSTANT, "2024-02-29 10:00:00" },
         { XSchema.TIME, "00:00:00" }, { XSchema.TIME, "23:59:59" },
         { XSchema.TIME, "{t '00:00:00'}" }, { XSchema.TIME, "{t '23:59:59'}" },
      };

      for(String[] c : ok) {
         assertEquals(c[1], DateConditionLiteral.canonicalize(c[0], c[1]), c[1]);
      }
   }

   /** The stored-condition runtime path stays lenient; only the write-time check is strict. */
   @Test
   void storedConditionRuntimeParsingIsStillLenient() {
      Object lenient = AbstractCondition.getObject(XSchema.DATE, "{d '2024-02-30'}");
      assertEquals("2024-03-01", lenient.toString());
      assertEquals(lenient.toString(), AbstractCondition.parseDateConditionValue(
         XSchema.DATE, "{d '2024-02-30'}").toString());
      // a strict call must not poison the shared lenient format
      assertNull(AbstractCondition.parseDateConditionValue(XSchema.DATE, "{d '2024-02-30'}", true));
      assertEquals("2024-03-01", AbstractCondition.getObject(XSchema.DATE, "{d '2024-02-30'}")
         .toString());
   }

   // Bug #78356: plain 24:00:00 was parsed by a lenient java.time resolver and stored as 00:00:00
   @Test
   void canonicalizeRefusesTwentyFourHourTimeLiteral() {
      for(String bad : new String[] { "24:00:00", " 24:00:00 ", "24:00", "{t '24:00:00'}",
                                      "24:00:01", "23:59:60", "25:00:00" })
      {
         assertNull(AbstractCondition.parseDateConditionValue(XSchema.TIME, bad, true), bad);
         assertNull(DateConditionLiteral.canonicalize(XSchema.TIME, bad), bad);
      }

      for(String ok : new String[] { "23:59:59", "00:00:00", "09:05:00", "{t '00:00:00'}" }) {
         assertEquals(ok, DateConditionLiteral.canonicalize(XSchema.TIME, ok), ok);
      }

      assertEquals("{ts '2024-01-15 23:59:59'}", DateConditionLiteral.canonicalize(
         XSchema.TIME_INSTANT, "{ts '2024-01-15 23:59:59'}"));
      // single-digit fields: the strict parse form is unchanged from base (accepted); the
      // writer-level canonicalize already refused them on base (Condition.addValue disagrees)
      assertEquals("09:05:00", AbstractCondition.parseDateConditionValue(
         XSchema.TIME, "9:05:00", true).toString());
      assertEquals("09:05:00", AbstractCondition.parseDateConditionValue(
         XSchema.TIME, "9:5:00", true).toString());
      assertNull(AbstractCondition.parseDateConditionValue(XSchema.TIME, "9:05", true));
      assertEquals("09:05:00", DateConditionLiteral.canonicalize(XSchema.TIME, "9:05:00"));
      assertEquals("09:05:00", DateConditionLiteral.canonicalize(XSchema.TIME, "9:5:0"));
      assertEquals("{t '9:05:00'}", DateConditionLiteral.canonicalize(
         XSchema.TIME, "{t '9:05:00'}"));

      for(String bad : new String[] { "9:05", "9:05:00 AM", "24:00:00", "10:60:00" }) {
         assertNull(DateConditionLiteral.canonicalize(XSchema.TIME, bad), bad);
      }
      // runtime path unchanged
      assertEquals("00:00:00", AbstractCondition.parseDateConditionValue(
         XSchema.TIME, "24:00:00").toString());
      assertEquals("00:00:00", AbstractCondition.getObject(XSchema.TIME, "24:00:00").toString());
   }

   // Bug #78529: the plain forms and the time instant {d fallthrough rolled February 29 of a
   // century year that is not a leap year (1900, 2100) over to March 1
   @Test
   void canonicalizeRefusesFebruary29OfACenturyCommonYear() {
      String[][] bad = {
         { XSchema.DATE, "2100-02-29" }, { XSchema.DATE, "1900-02-29" },
         { XSchema.DATE, "02/29/2100" }, { XSchema.DATE, "2023-02-29" },
         { XSchema.TIME_INSTANT, "2100-02-29 10:00:00" },
         { XSchema.TIME_INSTANT, "2100-02-29" },
         { XSchema.TIME_INSTANT, "02/29/2100 10:00:00" },
         { XSchema.TIME_INSTANT, "{d '2100-02-29'}" },
         { XSchema.TIME_INSTANT, "{ts '2100-02-29 10:00:00'}" },
      };

      for(String[] c : bad) {
         assertNull(AbstractCondition.parseDateConditionValue(c[0], c[1], true), c[1]);
         assertNull(DateConditionLiteral.canonicalize(c[0], c[1]), c[1]);
      }

      String[][] ok = {
         { XSchema.DATE, "2000-02-29" }, { XSchema.DATE, "2024-02-29" },
         { XSchema.DATE, "2400-02-29" }, { XSchema.TIME_INSTANT, "2000-02-29 10:00:00" },
         { XSchema.TIME_INSTANT, "{d '2000-02-29'}" },
      };

      for(String[] c : ok) {
         assertEquals(c[1], DateConditionLiteral.canonicalize(c[0], c[1]), c[1]);
      }

      assertEquals("2000-02-29", AbstractCondition.parseDateConditionValue(
         XSchema.DATE, "2000-02-29", true).toString());
   }

   // Bug #79287: a plain literal with second 60 on a Jun/Dec 30th or 31st at 23:59 was accepted
   // and rolled over to the next day
   @Test
   void canonicalizeRefusesPlainLeapSecondLiteral() {
      String[] bad = {
         "2024-06-30 23:59:60", "2024-12-30 23:59:60", "2024-12-31 23:59:60",
         "2024-12-31T23:59:60", "2024-12-31 23:59:60.5", "31-Dec-2024 23:59:60",
         "2024-12-31 23:59:60 AM",
      };

      for(String value : bad) {
         assertNull(AbstractCondition.parseDateConditionValue(XSchema.TIME_INSTANT, value, true),
                    value);
         assertNull(DateConditionLiteral.canonicalize(XSchema.TIME_INSTANT, value), value);
         assertNull(AbstractCondition.parseDateConditionValue(XSchema.DATE, value, true), value);
         assertNull(DateConditionLiteral.canonicalize(XSchema.DATE, value), value);
      }

      for(String ok : new String[] { "2024-06-30 23:59:59", "2024-12-31 23:59:59" }) {
         assertEquals(ok, DateConditionLiteral.canonicalize(XSchema.TIME_INSTANT, ok), ok);
      }

      // the lenient runtime parse keeps the leap second allowance
      assertNotNull(AbstractCondition.parseDateConditionValue(
         XSchema.TIME_INSTANT, "2024-12-31 23:59:60"));
   }

   @Test
   void nonDateTypesPassThrough() {
      assertEquals("abc", DateConditionLiteral.canonicalize(XSchema.STRING, "abc"));
      assertFalse(DateConditionLiteral.isDateType(XSchema.INTEGER));
      assertTrue(DateConditionLiteral.isDateType(XSchema.TIME_INSTANT));
   }
}
