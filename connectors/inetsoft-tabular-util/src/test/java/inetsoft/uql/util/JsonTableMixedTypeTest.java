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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82350: the type inferred from the first date-like text of a column never rewrites a later
 * value. A later value that is not a date of the cached type makes the column a string column
 * with the raw text, and a date-time next to dates makes a timestamp column that keeps the time.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class JsonTableMixedTypeTest {
   private static String json(String... v) {
      StringBuilder b = new StringBuilder("[");

      for(int i = 0; i < v.length; i++) {
         b.append(i == 0 ? "" : ",").append("{\"col\":\"").append(v[i]).append("\"}");
      }

      return b.append("]").toString();
   }

   private static XTableNode[] tables(Boolean dmyOrder, String... v) {
      JsonTable j = new JsonTable();
      j.setDmyOrder(dmyOrder);
      j.load(json(v));
      ExpandedJsonTable e = new ExpandedJsonTable("values");
      e.setDmyOrder(dmyOrder);
      e.load("[{\"values\":" + json(v) + "}]");
      HeadersAndRowsJsonTable h = new HeadersAndRowsJsonTable("headers", "rows");
      h.setDmyOrder(dmyOrder);
      StringBuilder rows = new StringBuilder();

      for(int i = 0; i < v.length; i++) {
         rows.append(i == 0 ? "" : ",").append("[\"").append(v[i]).append("\"]");
      }

      h.load("{\"headers\":[\"col\"],\"rows\":[" + rows + "]}");
      return new XTableNode[] { j, e, h };
   }

   private static List<Object> cells(XTableNode t) {
      List<Object> l = new ArrayList<>();
      t.rewind();

      while(t.next()) {
         l.add(t.getObject(0));
      }

      return l;
   }

   private static void assertString(Boolean dmyOrder, String... v) {
      for(XTableNode t : tables(dmyOrder, v)) {
         String msg = t.getClass().getSimpleName() + " " + dmyOrder + " " + Arrays.toString(v);
         assertEquals(String.class, t.getType(0), msg);
         assertEquals(Arrays.asList(v), cells(t), msg);
      }
   }

   private static void assertTimestamps(String[] v, String... expected) {
      for(XTableNode t : tables(null, v)) {
         String msg = t.getClass().getSimpleName() + " " + Arrays.toString(v);
         assertEquals(Timestamp.class, t.getType(0), msg);
         List<Object> exp = new ArrayList<>();

         for(String e : expected) {
            exp.add(Timestamp.valueOf(e));
         }

         assertEquals(exp, cells(t), msg);
      }
   }

   @Test
   void twoDigitNumberAfterDateIsNotADate() {
      // pojava reads a bare "12" as month 12 of this year when the order is month first
      for(Boolean order : new Boolean[] { false, null, true }) {
         assertString(order, "2026-01-12", "12");
      }
   }

   @Test
   void dateLikeTextAfterDateKeepsRawText() {
      for(Boolean order : new Boolean[] { false, null, true }) {
         assertString(order, "2026-03-01", "20260312");
         assertString(order, "2026-03-01", "March 5");
         assertString(order, "2026-03-01", "12 2026");
         assertString(order, "2026-03-01", "3/2026");
      }
   }

   @Test
   void dateTimeAfterDateMakesTimestampColumn() {
      assertTimestamps(new String[] { "2026-03-01", "2026-03-12 22:30" },
                       "2026-03-01 00:00:00", "2026-03-12 22:30:00");
      assertTimestamps(new String[] { "2026-03-01", "2026-03-12T22:30:00" },
                       "2026-03-01 00:00:00", "2026-03-12 22:30:00");
   }

   @Test
   void slashDatesStillMix() {
      for(Boolean order : new Boolean[] { true, false, null }) {
         for(XTableNode t : tables(order, "5/10/2026", "5/10/26")) {
            assertEquals(Date.class, t.getType(0), t.getClass().getSimpleName() + order);
         }
      }

      for(XTableNode t : tables(null, "4/9/2030", "5/1/2030 10:00")) {
         assertEquals(Timestamp.class, t.getType(0), t.getClass().getSimpleName());
      }
   }

   @Test
   void offsetAfterTextualDateKeepsWrittenWallClock() {
      // "Mar 5, 2026" fails the ISO parse; the offset value must not be moved to the server zone
      for(XTableNode t : tables(null, "Mar 5, 2026", "2026-03-01T23:00:00-05:00")) {
         String msg = t.getClass().getSimpleName();
         assertEquals(Timestamp.class, t.getType(0), msg);
         assertEquals(Timestamp.valueOf("2026-03-01 23:00:00"), cells(t).get(1), msg);
      }
   }

   @Test
   void localeFormattedDateColumnsStayDates() {
      for(XTableNode t : tables(null, "Mar 5, 2026", "Apr 6, 2026")) {
         assertEquals(Date.class, t.getType(0), t.getClass().getSimpleName());
      }
   }
}
