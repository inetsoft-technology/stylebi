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
package inetsoft.graph.data;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Shared fixtures of the time series tests. Dates are built and rendered in the current default
 * time zone, so a test never depends on the zone of the JVM.
 */
final class TimeSeriesRowFixtures {
   private TimeSeriesRowFixtures() {
   }

   /** Noon of the day in the default zone. */
   static Date at(int y, int m, int d) {
      return at(y, m, d, 12, 0, 0);
   }

   static Date at(int y, int m, int d, int h, int mi) {
      return at(y, m, d, h, mi, 0);
   }

   static Date at(int y, int m, int d, int h, int mi, int s) {
      return new GregorianCalendar(y, m - 1, d, h, mi, s).getTime();
   }

   /** One input row {d, g, v}. */
   static Object[] r(Object d, String g, Object v) {
      return new Object[]{ d, g, v };
   }

   /** Data set with header {d, g, v}. */
   static DefaultDataSet ds(Object[]... rows) {
      return build(new Object[]{ "d", "g", "v" }, rows);
   }

   /** Data set with header {d, g, v, w}; w is 5 in every row. */
   static DefaultDataSet ds2(Object[]... rows) {
      Object[][] out = new Object[rows.length][];

      for(int i = 0; i < rows.length; i++) {
         out[i] = Arrays.copyOf(rows[i], 4);
         out[i][3] = 5;
      }

      return build(new Object[]{ "d", "g", "v", "w" }, out);
   }

   static DefaultDataSet build(Object[] header, Object[]... rows) {
      Object[][] all = new Object[rows.length + 1][];
      all[0] = header;

      for(int i = 0; i < rows.length; i++) {
         all[i + 1] = rows[i].clone();
      }

      return new DefaultDataSet(all);
   }

   static TimeSeriesRow tsr(int level, Object missing) {
      TimeSeriesRow row = new TimeSeriesRow("d", level, missing);
      row.setGroupFields("g");
      return row;
   }

   /** Monthly rows of group g on the 10th: months given as 1-based numbers of 2023. */
   static Object[][] months(String g, int... months) {
      Object[][] rows = new Object[months.length][];

      for(int i = 0; i < months.length; i++) {
         rows[i] = r(at(2023, months[i], 10), g, 10 * months[i]);
      }

      return rows;
   }

   static Object[][] concat(Object[][]... parts) {
      List<Object[]> out = new ArrayList<>();

      for(Object[][] part : parts) {
         out.addAll(Arrays.asList(part));
      }

      return out.toArray(new Object[0][]);
   }

   static String fmt(Date d, String pattern) {
      SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.ROOT);
      f.setTimeZone(TimeZone.getDefault());
      return f.format(d);
   }

   /**
    * Render added rows as strings, one cell per column joined by '|', dates with the pattern,
    * sorted (the order of the groups in the result depends on a HashMap).
    */
   static List<String> render(List<Object[]> rows, String pattern) {
      List<String> out = new ArrayList<>();

      for(Object[] row : rows) {
         StringBuilder sb = new StringBuilder();

         for(int i = 0; i < row.length; i++) {
            if(i > 0) {
               sb.append('|');
            }

            sb.append(row[i] instanceof Date ? fmt((Date) row[i], pattern) : String.valueOf(row[i]));
         }

         out.add(sb.toString());
      }

      Collections.sort(out);
      return out;
   }

   static List<String> render(List<Object[]> rows) {
      return render(rows, "yyyy-MM-dd");
   }

   /** Expected rendering, sorted the same way. */
   static List<String> exp(String... rows) {
      List<String> out = new ArrayList<>(Arrays.asList(rows));
      Collections.sort(out);
      return out;
   }

   /** Read the rows of a data set from index {@code from} on, rendered like {@link #render}. */
   static List<String> renderFrom(DataSet data, int from, String pattern) {
      List<Object[]> rows = new ArrayList<>();

      for(int i = from; i < data.getRowCount(); i++) {
         Object[] row = new Object[data.getColCount()];

         for(int c = 0; c < row.length; c++) {
            row[c] = data.getData(c, i);
         }

         rows.add(row);
      }

      return render(rows, pattern);
   }
}
