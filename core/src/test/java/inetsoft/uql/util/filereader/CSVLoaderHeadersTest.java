/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.uql.util.filereader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Bug #78663: headers equal ignoring case must be made unique, otherwise column names resolve
 * to the same column at query time and the later column silently loses its data.
 */
@Tag("core")
public class CSVLoaderHeadersTest {
   private static List<String> headers(boolean removeQuote, String... line) {
      Object[] result = CSVLoader.getHeaders(line, true, -1, removeQuote);
      List<String> list = new ArrayList<>();

      for(Object o : result) {
         list.add((String) o);
      }

      return list;
   }

   private static List<String> headers(String... line) {
      return headers(false, line);
   }

   private static void assertUniqueIgnoringCase(List<String> headers) {
      Set<String> seen = new HashSet<>();

      for(String header : headers) {
         Assertions.assertTrue(seen.add(header.toLowerCase(Locale.ROOT)),
                               "duplicate ignoring case: " + header + " in " + headers);
      }
   }

   @Test
   public void caseOnlyAndExactDuplicatesRenamed() {
      Assertions.assertEquals(List.of("Sales", "sales 1", "Region", "Region 1"),
                              headers("Sales", "sales", "Region", "Region"));
   }

   @Test
   public void generatedNameCollidingWithLaterHeader() {
      Assertions.assertEquals(List.of("Region", "region 1", "Region 2"),
                              headers("Region", "region 1", "Region"));
   }

   @Test
   public void generatedNameCollidingWithRealHeaders() {
      List<String> result = headers("a", "A", "a 1", "A 1");
      Assertions.assertEquals(List.of("a", "A 1", "a 1 1", "A 1 2"), result);
      assertUniqueIgnoringCase(result);
   }

   @Test
   public void threeWayCaseVariants() {
      Assertions.assertEquals(List.of("Sales", "SALES 1", "sales 2"),
                              headers("Sales", "SALES", "sales"));
   }

   @Test
   public void uniqueHeadersUnchanged() {
      List<String> line = List.of("Sales", "sales 1", "Region", "Region 1", "salesX");
      Assertions.assertEquals(line, headers(line.toArray(new String[0])));
   }

   @Test
   public void blankHeaderGeneratedNameStaysUnique() {
      List<String> result = headers("", "COL0", "", "col2");
      Assertions.assertEquals(List.of("col0", "COL0 1", "col2", "col2 1"), result);
      assertUniqueIgnoringCase(result);
   }

   @Test
   public void suffixBasedOnCleanedQuotedHeader() {
      Assertions.assertEquals(List.of("A", "a 1"), headers(true, "\"A\"", "\"a\""));
   }

   @Test
   public void firstRowFalseGeneratesUniqueNames() {
      Object[] result = CSVLoader.getHeaders(new String[] { "x", "X" }, false, -1, false);
      Assertions.assertArrayEquals(new Object[] { "col0", "col1" }, result);
   }

   /**
    * ExcelLoader de-duplicates its header row with the same helper.
    */
   @Test
   public void sharedHelperUsedByExcel() {
      Set<String> existing = new HashSet<>();
      Assertions.assertEquals("Sales", CSVLoader.uniqueHeader("Sales", existing));
      Assertions.assertEquals("sales 1", CSVLoader.uniqueHeader("sales", existing));
      Assertions.assertEquals("SALES 2", CSVLoader.uniqueHeader("SALES", existing));
      Assertions.assertEquals("other", CSVLoader.uniqueHeader("other", existing));
   }
}
