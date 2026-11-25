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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #82055: the box plot statistics must keep full double precision. Values with more
 * than 7 significant digits (beyond float's 24-bit mantissa) must not be rounded.
 */
@Tag("core")
class BoxDataSetPrecisionTest {
   private static DefaultDataSet singleGroup(double... values) {
      Object[][] rows = new Object[values.length + 1][2];
      rows[0][0] = "grp";
      rows[0][1] = "val";

      for(int i = 0; i < values.length; i++) {
         rows[i + 1][0] = "G1";
         rows[i + 1][1] = values[i];
      }

      return new DefaultDataSet(rows);
   }

   private static BoxDataSet box(double... values) {
      return new BoxDataSet(singleGroup(values), new String[]{ "grp" }, new String[]{ "val" });
   }

   private static double stat(BoxDataSet box, String prefix, int row) {
      return ((Number) box.getData(box.indexOfHeader(prefix + "val"), row)).doubleValue();
   }

   @Test
   void medianOfNineDigitValuesIsExact() {
      BoxDataSet box = box(100000001, 100000002, 100000003, 100000004, 100000005);

      assertEquals(100000001d, stat(box, BoxDataSet.MIN_PREFIX, 0));
      assertEquals(100000002d, stat(box, BoxDataSet.Q25_PREFIX, 0));
      assertEquals(100000003d, stat(box, BoxDataSet.MEDIUM_PREFIX, 0));
      assertEquals(100000004d, stat(box, BoxDataSet.Q75_PREFIX, 0));
      assertEquals(100000005d, stat(box, BoxDataSet.MAX_PREFIX, 0));
   }

   @Test
   void valuesJustAboveTwoToThe24thAreExact() {
      BoxDataSet box = box(16777217, 16777218, 16777219);

      assertEquals(16777217d, stat(box, BoxDataSet.MIN_PREFIX, 0));
      assertEquals(16777218d, stat(box, BoxDataSet.MEDIUM_PREFIX, 0));
      assertEquals(16777219d, stat(box, BoxDataSet.MAX_PREFIX, 0));
   }

   @Test
   void twoValueGroupKeepsExactValuesAndMidpoint() {
      BoxDataSet box = box(100000001, 100000004);

      assertEquals(100000001d, stat(box, BoxDataSet.MIN_PREFIX, 0));
      assertEquals(100000001d, stat(box, BoxDataSet.Q25_PREFIX, 0));
      assertEquals(100000002.5d, stat(box, BoxDataSet.MEDIUM_PREFIX, 0));
      assertEquals(100000004d, stat(box, BoxDataSet.Q75_PREFIX, 0));
      assertEquals(100000004d, stat(box, BoxDataSet.MAX_PREFIX, 0));
   }

   @Test
   void singleValueGroupIsExact() {
      BoxDataSet box = box(123456789);

      for(String prefix : new String[]{ BoxDataSet.MIN_PREFIX, BoxDataSet.Q25_PREFIX,
         BoxDataSet.MEDIUM_PREFIX, BoxDataSet.Q75_PREFIX, BoxDataSet.MAX_PREFIX })
      {
         assertEquals(123456789d, stat(box, prefix, 0), prefix);
      }
   }

   @Test
   void fractionalValueKeepsDoublePrecision() {
      // 0.1 is not exactly representable; as a float it becomes 0.10000000149011612.
      BoxDataSet box = box(0.1);

      assertEquals(0.1d, stat(box, BoxDataSet.MEDIUM_PREFIX, 0));
   }

   @Test
   void largeOutlierKeepsExactValueAndWhiskerIsExact() {
      // n = 8: q25 = values[2] = 100000003, q75 = values[6] = 100000007, iqr = 4,
      // upper fence = 100000013, so 1000000123 is an outlier.
      BoxDataSet box = box(100000001, 100000002, 100000003, 100000004, 100000005,
                           100000006, 100000007, 100000008, 1000000123);

      assertEquals(2, box.getRowCount());
      assertEquals(100000003d, stat(box, BoxDataSet.Q25_PREFIX, 0));
      assertEquals(100000005d, stat(box, BoxDataSet.MEDIUM_PREFIX, 0));
      assertEquals(100000007d, stat(box, BoxDataSet.Q75_PREFIX, 0));
      assertEquals(100000013d, stat(box, BoxDataSet.MAX_PREFIX, 0));

      Number outlier = (Number) box.getData(box.indexOfHeader("val"), 1);
      assertEquals(1000000123d, outlier.doubleValue());
   }
}
