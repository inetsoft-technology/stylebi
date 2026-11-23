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
package inetsoft.uql.util;

import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.util.pojava.datetime.DateTimeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Date;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82029: a date-time text with a UTC offset in a date column keeps the calendar date as
 * written, whatever the zone of the server.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class JsonTableOffsetDateTest {
   private TimeZone defaultZone;

   @BeforeEach
   void saveZone() {
      defaultZone = TimeZone.getDefault();
   }

   @AfterEach
   void restoreZone() {
      setZone(defaultZone);
   }

   // the date parser caches the default zone, so it is rebuilt after the zone changes
   private static void setZone(TimeZone zone) {
      TimeZone.setDefault(zone);
      DateTimeConfig.setGlobalDefault(null);
   }

   private static List<Object> load(String... values) {
      List<Object> records = new ArrayList<>();

      for(String value : values) {
         records.add(Collections.singletonMap("v", value));
      }

      JsonTable table = new JsonTable();
      table.load(records);
      List<Object> cells = new ArrayList<>();
      XTableNode node = table;
      node.rewind();

      while(node.next()) {
         cells.add(node.getObject(0));
      }

      return cells;
   }

   // the calendar date of each cell, as text
   private static List<String> dates(List<Object> cells) {
      List<String> list = new ArrayList<>();

      for(Object cell : cells) {
         assertInstanceOf(Date.class, cell);
         list.add(cell.toString());
      }

      return list;
   }

   // a plain date first makes the column a date column, then the offset text is read as a date
   private static void assertKeepsWrittenDate(String text) {
      assertEquals(Arrays.asList("2026-03-01", "2026-03-01"), dates(load("2026-03-01", text)),
                   text);
   }

   @ParameterizedTest
   @ValueSource(strings = { "UTC", "America/New_York", "Asia/Shanghai", "Pacific/Kiritimati" })
   void offsetSpellingsKeepTheWrittenDate(String zone) {
      setZone(TimeZone.getTimeZone(zone));

      assertKeepsWrittenDate("2026-03-01T01:00:00+02:00");
      assertKeepsWrittenDate("2026-03-01T01:00:00Z");
      assertKeepsWrittenDate("2026-03-01T01:00:00+0200");
      assertKeepsWrittenDate("2026-03-01 01:00:00 +02:00");
      assertKeepsWrittenDate("2026-03-01T01:00:00.123+02:00");
      assertKeepsWrittenDate("2026-03-01T23:00:00-05:00");
      assertKeepsWrittenDate("2026-03-01T01:00+02");
   }

   @ParameterizedTest
   @ValueSource(strings = { "UTC", "America/New_York" })
   void plainDateAndOffsetlessDateTimeAreUnchanged(String zone) {
      setZone(TimeZone.getTimeZone(zone));

      assertEquals(Arrays.asList("2026-03-01", "2026-03-02"),
                   dates(load("2026-03-01", "2026-03-02")));
      assertKeepsWrittenDate("2026-03-01T01:00:00");
      assertKeepsWrittenDate("2026-03-01 01:00:00");
   }

   @Test
   void plainDateAfterOffsetCellKeepsTheWrittenDate() {
      setZone(TimeZone.getTimeZone("UTC"));

      assertEquals(Arrays.asList("2026-03-01", "2026-03-01", "2026-03-02"),
                   dates(load("2026-03-01", "2026-03-01T01:00:00+02:00", "2026-03-02")));
   }

   @Test
   void timestampColumnIsNotAffected() {
      setZone(TimeZone.getTimeZone("UTC"));

      // the offset text first makes a timestamp column, which reads the written wall clock
      List<Object> cells = load("2026-03-01T01:00:00+02:00", "2026-03-01T05:30:00+02:00");
      assertEquals(Arrays.asList(
         java.sql.Timestamp.valueOf("2026-03-01 01:00:00"),
         java.sql.Timestamp.valueOf("2026-03-01 05:30:00")), cells);
   }
}
