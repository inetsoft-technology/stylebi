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

import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #78312: strict date literal validation shared by the condition writers. */
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

   @Test
   void nonDateTypesPassThrough() {
      assertEquals("abc", DateConditionLiteral.canonicalize(XSchema.STRING, "abc"));
      assertFalse(DateConditionLiteral.isDateType(XSchema.INTEGER));
      assertTrue(DateConditionLiteral.isDateType(XSchema.TIME_INSTANT));
   }
}
