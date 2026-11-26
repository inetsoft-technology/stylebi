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
package inetsoft.mv.data;

import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #82376: with an MV, LIKE on a numeric column matched every value containing the
 * condition value (LIKE 2 also returned 12, 20-29, ...). The value reaches the column as a
 * number (no wildcard), and a LIKE without a wildcard matches the whole value, as without MV.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MeasureColumnLikeTest {
   private static final double NULL = Tool.NULL_DOUBLE;

   @Test
   void intColumnLikeMatchesTheWholeValue() {
      double[] vals = { 2, 12, 20, 25, 32, NULL, 2, -2, 0, 102 };
      MVIntColumn col = new MVIntColumn(vals.length, -2, 102);

      for(int i = 0; i < vals.length; i++) {
         col.setValue(i, vals[i]);
      }

      assertEquals(List.of(0, 6), rows(col.getLikeOpRows("LIKE", bits(2), false), vals.length));
      assertEquals(List.of(7), rows(col.getLikeOpRows("LIKE", bits(-2), false), vals.length));
      // a value with a wildcard is converted to 0 before it reaches the column
      assertEquals(List.of(8), rows(col.getLikeOpRows("LIKE", bits(0), false), vals.length));
      // NOT LIKE: every non-null row except the matching ones
      assertEquals(List.of(1, 2, 3, 4, 7, 8, 9),
                   rows(col.getLikeOpRows("LIKE", bits(2), true), vals.length));
      // an empty value gives no rows
      assertEquals(List.of(), rows(col.getLikeOpRows("LIKE", bits(NULL), false), vals.length));

      // CONTAINS and STARTSWITH are not changed
      assertEquals(List.of(0, 1, 2, 3, 4, 6, 7, 9),
                   rows(col.getLikeOpRows("CONTAINS", bits(2), false), vals.length));
      assertEquals(List.of(0, 2, 3, 6), rows(col.getLikeOpRows("STARTSWITH", bits(2), false),
                                             vals.length));
   }

   @Test
   void doubleColumnLikeMatchesTheWholeValue() {
      double[] vals = { 1, 10, 11, 19, 129, 123456.789, NULL, 1.5 };
      MVDoubleColumn col = new MVDoubleColumn(null, 0, null, vals.length, true);

      for(int i = 0; i < vals.length; i++) {
         col.setValue(i, vals[i]);
      }

      assertEquals(List.of(0), rows(col.getLikeOpRows("LIKE", bits(1), false), vals.length));
      assertEquals(List.of(4), rows(col.getLikeOpRows("LIKE", bits(129), false), vals.length));
      assertEquals(List.of(5), rows(col.getLikeOpRows("LIKE", bits(123456.789), false),
                                    vals.length));
      assertEquals(List.of(7), rows(col.getLikeOpRows("LIKE", bits(1.5), false), vals.length));
      assertEquals(List.of(1, 2, 3, 4, 5, 7),
                   rows(col.getLikeOpRows("LIKE", bits(1), true), vals.length));
      assertEquals(List.of(0, 1, 2, 3, 4, 5, 7),
                   rows(col.getLikeOpRows("CONTAINS", bits(1), false), vals.length));
   }

   @Test
   void floatColumnLikeComparesAsTheStoredFloat() {
      // the default MV stores a double column as float: 123456.789 is kept as 123456.7890625
      double[] vals = { 1, 10, 123456.789, 3.14159265358979, NULL };
      MVFloatColumn col = new MVFloatColumn(null, 0, null, vals.length, true);

      for(int i = 0; i < vals.length; i++) {
         col.setValue(i, vals[i]);
      }

      assertEquals(List.of(0), rows(col.getLikeOpRows("LIKE", bits(1), false), vals.length));
      assertEquals(List.of(2), rows(col.getLikeOpRows("LIKE", bits(123456.789), false),
                                    vals.length));
      assertEquals(List.of(3), rows(col.getLikeOpRows("LIKE", bits(3.14159265358979), false),
                                    vals.length));
      assertEquals(List.of(1, 2, 3), rows(col.getLikeOpRows("LIKE", bits(1), true), vals.length));
   }

   private static long bits(double val) {
      return Double.doubleToLongBits(val);
   }

   private static List<Integer> rows(BitSet set, int count) {
      List<Integer> list = new ArrayList<>();

      for(int i = 0; i < count; i++) {
         if(set.get(i)) {
            list.add(i);
         }
      }

      return list;
   }
}
