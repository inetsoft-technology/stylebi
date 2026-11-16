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
package inetsoft.sree;

import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80416: RepletRequest.clone() must not share the date-time list with the original.
 */
@Tag("core")
class RepletRequestCloneTest {
   @BeforeEach
   void setUp() {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(2024, Calendar.JUNE, 3, 0, 0, 0);
      original = new RepletRequest();
      original.setParameter("d", cal.getTime(), XSchema.TIME_INSTANT);
      clone = (RepletRequest) original.clone();
   }

   @Test
   void removeOnCloneKeepsOriginalMark() {
      assertTrue(clone.containsDateTime("d"));
      clone.remove("d");

      assertTrue(original.contains("d"));
      assertTrue(original.containsDateTime("d"));
   }

   @Test
   void removeAllOnOriginalKeepsCloneMark() {
      original.removeAll();

      assertTrue(clone.contains("d"));
      assertTrue(clone.containsDateTime("d"));
   }

   @Test
   void addDateTimeOnCloneDoesNotLeakToOriginal() {
      clone.addDateTime("x");

      assertTrue(clone.containsDateTime("x"));
      assertFalse(original.containsDateTime("x"));
   }

   private RepletRequest original;
   private RepletRequest clone;
}
