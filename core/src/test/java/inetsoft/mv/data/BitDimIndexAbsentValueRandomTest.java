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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #79870: random 'in' / 'not in' / 'not equal' lists mixing present and absent (negative)
 * dictionary codes, compared with SQL semantics: non-null rows whose key is not (is) in the list.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BitDimIndexAbsentValueRandomTest {
   @Test
   void randomListsMatchSqlSemantics() {
      Random rnd = new Random(42);

      for(int iter = 0; iter < 300; iter++) {
         int card = 1 + rnd.nextInt(6);
         int n = 1 + rnd.nextInt(12);
         int[] keys = new int[n];
         BitDimIndex bit = new BitDimIndex();
         MVDimColumn dim = new MVDimColumn(iter % 2 == 0 ? 8 : 100, n, true);

         for(int i = 0; i < n; i++) {
            keys[i] = rnd.nextInt(card + 1); // 0 is null
            bit.addKey(keys[i], i);
            dim.setValue(i, keys[i]);
         }

         bit.complete();
         dim.complete();

         int m = rnd.nextInt(4);
         long[] vals = new long[m];
         Set<Integer> present = new HashSet<>();

         for(int i = 0; i < m; i++) {
            if(rnd.nextBoolean()) {
               vals[i] = 1 + rnd.nextInt(card);
               present.add((int) vals[i]);
            }
            else {
               vals[i] = -1 - rnd.nextInt(card + 1);
            }
         }

         BitSet notIn = new BitSet();
         BitSet in = new BitSet();

         for(int i = 0; i < n; i++) {
            if(keys[i] != 0) {
               (present.contains(keys[i]) ? in : notIn).set(i);
            }
         }

         for(XDimIndex idx : new XDimIndex[] { bit, dim }) {
            String msg = idx.getClass().getSimpleName() + " keys=" + Arrays.toString(keys) +
               " vals=" + Arrays.toString(vals);
            assertEquals(in.toString(), idx.getRows("IN", vals, false, true).toString(), "IN " + msg);

            if(m > 0) {
               assertEquals(notIn.toString(), idx.getRows("IN", vals, true, true).toString(),
                            "NOT IN " + msg);
            }

            if(m == 1) {
               assertEquals(notIn.toString(), idx.getRows("=", vals[0], true, true).toString(),
                            "NOT EQUAL " + msg);
            }
         }
      }
   }
}
