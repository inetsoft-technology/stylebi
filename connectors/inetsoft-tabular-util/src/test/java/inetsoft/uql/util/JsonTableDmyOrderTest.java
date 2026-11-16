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
import inetsoft.util.pojava.datetime.DateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81424: a source that declares the order of its slash separated dates is read in that
 * order, whatever the width of the day and month.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class JsonTableDmyOrderTest {
   private static List<Object> load(Boolean dmyOrder, String... values) {
      List<Object> records = new ArrayList<>();

      for(String value : values) {
         records.add(Collections.singletonMap("v", value));
      }

      JsonTable table = new JsonTable();
      table.setDmyOrder(dmyOrder);
      table.load(records);
      List<Object> cells = new ArrayList<>();
      XTableNode node = table;
      node.rewind();

      while(node.next()) {
         cells.add(node.getObject(0));
      }

      return cells;
   }

   private static Date date(int year, int month, int day) {
      return Date.valueOf(LocalDate.of(year, month, day));
   }

   @Test
   void dayFirstReadsOneDigitDateDayFirst() {
      assertEquals(Arrays.asList(date(2026, 10, 5), date(2026, 3, 7)),
                   load(true, "5/10/2026", "7/3/2026"));
   }

   @Test
   void monthFirstReadsOneDigitDateMonthFirst() {
      assertEquals(Arrays.asList(date(2026, 10, 5), date(2026, 3, 7)),
                   load(false, "10/5/2026", "3/7/2026"));
   }

   @Test
   void dayFirstReadsTwoDigitDate() {
      assertEquals(Collections.singletonList(date(2026, 10, 25)), load(true, "25/10/2026"));
      assertEquals(Collections.singletonList(date(2026, 10, 25)), load(false, "10/25/2026"));
   }

   @Test
   void dayFirstReadsDateTime() {
      assertEquals(
         Collections.singletonList(Timestamp.valueOf(LocalDateTime.of(2026, 10, 5, 8, 30, 15))),
         load(true, "5/10/2026 8:30:15"));
      assertEquals(
         Collections.singletonList(Timestamp.valueOf(LocalDateTime.of(2026, 10, 5, 8, 30, 15))),
         load(false, "10/5/2026 8:30:15"));
   }

   @Test
   void declaredOrderAppliesToTheFallbackParse() {
      // a two digit year is rejected by the strict formatters and parsed by the fallback
      assertEquals(Collections.singletonList(date(2026, 10, 5)), load(true, "5/10/26"));
      assertEquals(Collections.singletonList(date(2026, 10, 5)), load(false, "10/5/26"));
   }

   @Test
   void isoDatesAreNotAffected() {
      assertEquals(Collections.singletonList(date(2026, 10, 5)), load(true, "2026-10-05"));
      assertEquals(Collections.singletonList(date(2026, 10, 5)), load(false, "2026-10-05"));
   }

   @Test
   void undeclaredOrderKeepsTheServerBehavior() {
      // one digit text is rejected by the strict formatters, so the result is the server default
      // parse, which depends on the server locale
      assertEquals(
         Collections.singletonList(new Date(DateTime.parse("10/5/2026").toMillis())),
         load(null, "10/5/2026"));
   }
}
