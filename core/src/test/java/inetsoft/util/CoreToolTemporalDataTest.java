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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79858: java.time values returned by JDBC drivers for TIMESTAMP WITH TIME ZONE columns
 * must convert to a time instant instead of null.
 */
@Tag("core")
class CoreToolTemporalDataTest {
   private static final Instant INSTANT = Instant.parse("2020-01-02T02:30:00Z");

   @Test
   void offsetDateTimeKeepsInstant() {
      OffsetDateTime odt = OffsetDateTime.parse("2020-01-02T10:30:00+08:00");
      Timestamp ts = CoreTool.getTimeInstantData(odt);
      assertNotNull(ts);
      assertEquals(INSTANT.toEpochMilli(), ts.getTime());
      assertEquals(ts, Tool.getData(Timestamp.class, odt));
   }

   @Test
   void zonedDateTimeAndInstant() {
      assertEquals(INSTANT.toEpochMilli(),
                   CoreTool.getTimeInstantData(INSTANT.atZone(ZoneId.of("Asia/Tokyo"))).getTime());
      assertEquals(INSTANT.toEpochMilli(), CoreTool.getTimeInstantData(INSTANT).getTime());
   }

   @Test
   void localDateTimeUsesDefaultZone() {
      LocalDateTime ldt = LocalDateTime.of(2020, 1, 2, 10, 30);
      assertEquals(Timestamp.valueOf(ldt), CoreTool.getTimeInstantData(ldt));
   }

   @Test
   void dateAndTimeSiblings() {
      OffsetDateTime odt = OffsetDateTime.parse("2020-01-02T10:30:00+08:00");
      assertNotNull(CoreTool.getDateData(odt));
      assertEquals(INSTANT.toEpochMilli(), CoreTool.getTimeData(odt).getTime());
   }

   @Test
   void existingTypesUnchanged() {
      Timestamp ts = new Timestamp(1000L);
      assertSame(ts, CoreTool.getTimeInstantData(ts));
      assertEquals(5L, CoreTool.getTimeInstantData(5L).getTime());
      assertEquals(7L, CoreTool.getTimeInstantData(new java.util.Date(7L)).getTime());
      assertNull(CoreTool.getTimeInstantData(new Object()));
      assertNull(CoreTool.getTimeInstantData(null));
   }

   @Test
   void unrelatedTemporalIsNull() {
      assertNull(CoreTool.getTimeInstantData(LocalDate.of(2020, 1, 1)));
   }
}
