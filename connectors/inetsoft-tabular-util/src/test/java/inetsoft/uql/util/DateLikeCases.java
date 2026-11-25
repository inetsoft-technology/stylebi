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

import inetsoft.uql.XTableNode;
import inetsoft.util.pojava.datetime.DateTime;

import java.util.Arrays;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared data for the tests of date-like text cells in the JSON table classes (Bug #81945).
 */
final class DateLikeCases {
   private DateLikeCases() {
   }

   /**
    * Columns of text values that end up as a string column. Each cell must show its source text.
    */
   static final String[][] STRING_COLUMNS = {
      // date first
      { "4/9/2030", "test" },
      // text first
      { "test", "4/9/2030" },
      // interleaved
      { "test", "4/9/2030", "x", "4/10/2030" },
      // several dates, then text
      { "4/9/2030", "5/1/2030", "N/A" },
      // ISO date-time, then text
      { "2030-04-09T10:11:12", "test" },
      { "test", "2030-04-09T10:11:12" },
      // ISO date, then text
      { "2030-04-09", "test" },
      // time of day
      { "1:02:03", "test" },
      { "test", "1:02:03" },
   };

   /**
    * JSON array of records, each with a "col" text value.
    */
   static String toJson(String[] values) {
      StringBuilder json = new StringBuilder("[");

      for(int i = 0; i < values.length; i++) {
         json.append(i == 0 ? "" : ",").append("{\"col\": \"").append(values[i]).append("\"}");
      }

      return json.append("]").toString();
   }

   /**
    * JSON with the records inside an array under "values", the path the expanded table reads.
    */
   static String toExpandedJson(String[] values) {
      return "[{\"values\": " + toJson(values) + "}]";
   }

   /**
    * Checks that columns whose values are all date-like keep their date, date-time or time type.
    */
   static void assertTypedColumns(Function<String[], XTableNode> factory) {
      assertTyped(factory, new String[] { "4/9/2030", "5/1/2030" }, java.sql.Date.class,
                  new java.sql.Date(DateTime.parse("4/9/2030").toMillis()));
      // a single date-like cell
      assertTyped(factory, new String[] { "4/9/2030" }, java.sql.Date.class,
                  new java.sql.Date(DateTime.parse("4/9/2030").toMillis()));
      assertTyped(factory, new String[] { "2030-04-09", "2030-04-10" }, java.sql.Date.class,
                  java.sql.Date.valueOf("2030-04-09"));
      assertTyped(factory, new String[] { "2030-04-09T10:11:12", "2030-04-09T10:11:13" },
                  java.sql.Timestamp.class,
                  java.sql.Timestamp.valueOf("2030-04-09 10:11:12"));
      assertTyped(factory, new String[] { "01:02:03", "1:02:04" }, java.sql.Time.class,
                  java.sql.Time.valueOf("01:02:03"));
   }

   private static void assertTyped(Function<String[], XTableNode> factory, String[] col,
                                   Class<?> type, Object first)
   {
      XTableNode table = factory.apply(col);
      String msg = Arrays.toString(col);

      assertEquals(type, table.getType(0), msg);
      assertEquals(true, table.next(), msg);
      assertEquals(type, table.getObject(0).getClass(), msg);
      assertEquals(first, table.getObject(0), msg);
   }
}
