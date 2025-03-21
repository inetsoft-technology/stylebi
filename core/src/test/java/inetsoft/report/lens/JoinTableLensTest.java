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
package inetsoft.report.lens;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.TableLens;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.test.SreeHome;
import inetsoft.test.XTableUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;

class JoinTableLensTest {
   private static String forceHash;

   @BeforeAll
   public static void beforeAll() {
      forceHash = SreeEnv.getProperty("join.table.forceHash");
   }

   @AfterAll
   public static void afterAll() {
      SreeEnv.setProperty("join.table.forceHash", forceHash);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("joinTestProvider")
   void testJoin(@SuppressWarnings("unused") String name, boolean hash, int type, int[] cols,
                 TableLens left, TableLens right, TableLens expected)
   {
      SreeEnv.setProperty("join.table.forceHash", Boolean.toString(hash));
      JoinTableLens joined = new JoinTableLens(left, right, cols, cols, type, true);
      int[] sortCols = IntStream.range(0, joined.getColCount()).toArray();
      SortFilter actual = new SortFilter(joined, sortCols, true);
//      Util.printTable(actual, 100);
//      System.err.println("----------");
      sortCols = IntStream.range(0, expected.getColCount()).toArray();
      SortFilter sorted = new SortFilter(expected, sortCols, true);
      XTableUtil.assertEquals(sorted, actual);
   }

   static Stream<Arguments> joinTestProvider() throws Exception {
      return Stream.of(
         arguments("Simple Inner Hash", true, JoinTableLens.INNER_JOIN, new int[] { 0 },
                   loadTable("simple-join-left.json"), loadTable("simple-join-right.json"),
                   loadTable("simple-join-expected.json")),
         arguments("Simple Inner Merge", false, JoinTableLens.INNER_JOIN, new int[] { 0 },
                   loadTable("simple-join-left.json"), loadTable("simple-join-right.json"),
                   loadTable("simple-join-expected.json")),
         arguments("Bug #46758 Outer Hash", true, JoinTableLens.RIGHT_OUTER_JOIN, new int[] { 0 },
                   loadTable("bug-46758-outer-left.json"), loadTable("bug-46758-outer-right.json"),
                   loadTable("bug-46758-outer-expected.json")),
         arguments("Bug #46758 Outer Merge", false, JoinTableLens.RIGHT_OUTER_JOIN, new int[] { 0 },
                   loadTable("bug-46758-outer-left.json"), loadTable("bug-46758-outer-right.json"),
                   loadTable("bug-46758-outer-expected.json")),
         arguments("Bug #46758 Inner Hash", true, JoinTableLens.INNER_JOIN, new int[] { 2 },
                   loadTable("bug-46758-inner-left.json"), loadTable("bug-46758-inner-right.json"),
                   loadTable("bug-46758-inner-expected.json")),
         arguments("Bug #46758 Inner Merge", false, JoinTableLens.INNER_JOIN, new int[] { 2 },
                   loadTable("bug-46758-inner-left.json"), loadTable("bug-46758-inner-right.json"),
                   loadTable("bug-46758-inner-expected.json"))
      );
   }

   private static TableLens loadTable(String resource) throws Exception {
      try(InputStream input = JoinTableLens.class.getResourceAsStream(resource)) {
         TableJson table = new ObjectMapper().readValue(input, TableJson.class);
         Object[][] data = table.getRows().toArray(new Object[0][]);
         return new DefaultTableLens(data);
      }
   }

   public static final class TableJson {
      public List<Object[]> getRows() {
         if(rows == null) {
            rows = new ArrayList<>();
         }

         return rows;
      }

      public void setRows(List<Object[]> rows) {
         if(rows != null) {
            Pattern pattern = Pattern.compile(
               "^\\{ts '(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d{6})?)'}$");

            for(Object[] row : rows) {
               if(row != null) {
                  for(int i = 0; i < row.length; i++) {
                     if(row[i] instanceof String) {
                        Matcher matcher = pattern.matcher((String) row[i]);

                        if(matcher.matches()) {
                           row[i] = Timestamp.valueOf(matcher.group(1));
                        }
                     }
                  }
               }
            }
         }
         this.rows = rows;
      }

      private List<Object[]> rows;
   }
}
