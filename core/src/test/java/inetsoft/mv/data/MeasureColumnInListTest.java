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
package inetsoft.mv.data;

import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #79409: an IN / NOT IN list on a measure column is hashed from 16 values on. The result must
 * be identical to the plain {@code ==} scan of every list value for every row, for list lengths
 * below, at and above the threshold.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MeasureColumnInListTest {
   private static final double NAN2 = Double.longBitsToDouble(0x7ff8000000001234L);
   private static final double NAN3 = Double.longBitsToDouble(0xfff8000000000001L);

   @Test
   void lookupMatchesEquality() {
      double[] special = { 0.0, -0.0, Double.NaN, NAN2, NAN3, Tool.NULL_DOUBLE,
                           Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1, -1, 1e300,
                           Double.MIN_VALUE, Double.MAX_VALUE };

      for(double inList : special) {
         double[] vals = fill(inList, 20);
         AbstractMeasureColumn.DoubleLookup lookup = new AbstractMeasureColumn.DoubleLookup(vals);

         for(double probe : special) {
            assertEquals(inList == probe, lookup.contains(probe), inList + " vs " + probe);
         }
      }
   }

   @Test
   void emptyAndDuplicateLists() {
      assertFalse(new AbstractMeasureColumn.DoubleLookup(new double[0]).contains(0));
      assertTrue(new AbstractMeasureColumn.DoubleLookup(fill(3, 100)).contains(3));
   }

   @Test
   void columnMatchesReference() {
      double[] data = { 0.0, -0.0, Double.NaN, NAN2, NAN3, Tool.NULL_DOUBLE,
                        Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1, 2, 3, -1, 1e300 };
      Random rnd = new Random(79409);
      double[] rows = new double[3000];

      for(int i = 0; i < rows.length; i++) {
         rows[i] = rnd.nextInt(4) == 0 ? data[rnd.nextInt(data.length)] : rnd.nextInt(60);
      }

      MVDoubleColumn col = new MVDoubleColumn(null, 0, null, rows.length, true);

      for(int i = 0; i < rows.length; i++) {
         col.setValue(i, rows[i]);
      }

      int[] sizes = { 0, 1, 2, 15, 16, 17, 50, 200 };

      for(int size : sizes) {
         for(int seed = 0; seed < 6; seed++) {
            Random r = new Random(seed * 31L + size);
            double[] vals = new double[size];

            for(int k = 0; k < size; k++) {
               vals[k] = r.nextInt(3) == 0 ? data[r.nextInt(data.length)] : r.nextInt(80);
            }

            long[] bits = new long[size];

            for(int k = 0; k < size; k++) {
               bits[k] = Double.doubleToRawLongBits(vals[k]);
            }

            for(boolean not : new boolean[] { false, true }) {
               for(boolean cnull : new boolean[] { false, true }) {
                  assertEquals(reference(rows, vals, not),
                               col.getRows("IN", bits, not, cnull),
                               "size=" + size + " seed=" + seed + " not=" + not);
               }
            }
         }
      }
   }

   private static BitSet reference(double[] rows, double[] vals, boolean not) {
      BitSet set = new BitSet();

      for(int i = 0; i < rows.length; i++) {
         boolean found = false;

         for(double v : vals) {
            if(rows[i] == v) {
               found = true;
               break;
            }
         }

         if(not) {
            found = !found;

            if(found && rows[i] == Tool.NULL_DOUBLE) {
               found = false;
            }
         }

         if(found) {
            set.add(i);
         }
      }

      set.complete();
      return set;
   }

   private static double[] fill(double v, int n) {
      double[] arr = new double[n];
      Arrays.fill(arr, 5.5);
      arr[n / 2] = v;
      return arr;
   }
}
