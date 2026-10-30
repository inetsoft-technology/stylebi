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
package inetsoft.uql.viewsheet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #79829: the default date-option message printed "range null and null". */
@Tag("core")
class DateColumnOptionMessageTest {
   @Test
   void defaultMessageWithoutRangeNamesTheValueAndNeverPrintsNull() {
      String msg = new DateColumnOption(null, null, null, true).getErrorMessage("2026-02-30");
      assertTrue(msg.contains("2026-02-30"), msg);
      assertFalse(msg.contains("null"), msg);
   }

   @Test
   void defaultMessageWithOneBoundNeverPrintsNull() {
      String msg = new DateColumnOption("2026-12-31", null, null, true).getErrorMessage("2027-01-01");
      assertTrue(msg.contains("2026-12-31"), msg);
      assertFalse(msg.contains("null"), msg);
      msg = new DateColumnOption(null, "2026-01-01", null, true).getErrorMessage("2025-01-01");
      assertTrue(msg.contains("2026-01-01"), msg);
      assertFalse(msg.contains("null"), msg);
   }

   @Test
   void configuredMessageStillWinsAndSubstitutesTheValue() {
      assertEquals("bad 2026-02-30",
                   new DateColumnOption(null, null, "bad {0}", true).getErrorMessage("2026-02-30"));
   }

   @Test
   void defaultMessageWithBothBoundsKeepsTheRange() {
      String msg = new DateColumnOption("2026-12-31", "2026-01-01", null, true).getErrorMessage("x");
      assertTrue(msg.contains("2026-01-01") && msg.contains("2026-12-31"), msg);
   }
}
