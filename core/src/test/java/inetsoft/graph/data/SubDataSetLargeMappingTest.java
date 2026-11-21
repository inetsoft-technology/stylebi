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
package inetsoft.graph.data;

import inetsoft.test.*;
import inetsoft.util.swap.XIntFragment;
import inetsoft.util.swap.XSwapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82040, a SubDataSet whose row mapping has more than 65535 rows (kept in an XIntFragment
 * once it exceeds 1000 rows) reported rows mod 65536: a 70000-row facet cell showed 4464 rows,
 * a 65536-row one none, and reading a row past the cut threw ArrayIndexOutOfBoundsException.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class SubDataSetLargeMappingTest {
   @ParameterizedTest
   @ValueSource(ints = { 65536, 70000 })
   void mappingKeepsEveryRow(int n) throws Exception {
      SubDataSet sub = new SubDataSet(base(n + 1000), range(n));
      XIntFragment mapping = mapping2(sub);
      // getConditionKey() reads the mapping without access(), so keep the shared swapper off it
      XSwapper.getSwapper().deregister(mapping);

      try {
         assertEquals(n, sub.getRowCount());
         assertEquals(n - 1, sub.getBaseRow(n - 1));
         assertEquals(n - 1, sub.getData("k", n - 1));
         assertEquals(n + ":0-" + (n - 1), sub.getConditionKey());
      }
      finally {
         mapping.dispose();
      }
   }

   @Test
   void swappedMappingKeepsEveryRow() throws Exception {
      SubDataSet sub = new SubDataSet(base(71000), range(70000));
      XIntFragment mapping = mapping2(sub);
      XSwapper.getSwapper().deregister(mapping);

      try {
         assertTrue(mapping.swap(), "mapping was not swapped");
         assertFalse(mapping.isValid(), "mapping is still in memory");

         assertEquals(5000, sub.getRowFromBase(5000));
         assertEquals(69999, sub.getRowFromBase(69999));
         assertEquals(70000, mapping.getArray().length);
         assertEquals(70000, sub.getRowCount());
         assertEquals(69999, sub.getBaseRow(69999));
      }
      finally {
         mapping.dispose();
      }
   }

   @Test
   void facetSubDataSetKeepsEveryRow() {
      DataSetIndex index = new DataSetIndex(base(140000), Collections.singleton("g"), false);
      DataSet facet = index.createSubDataSet(Collections.singletonMap("g", "A"), true);

      assertEquals(70000, facet.getRowCount());
      // row i of g=A is base row 2 * i
      assertEquals(139998, facet.getData("k", 69999));
   }

   @Test
   void conditionSubDataSetKeepsEveryRow() {
      SubDataSet sub = new SubDataSet(base(140000), Collections.singletonMap("g", "B"));

      assertEquals(70000, sub.getRowCount());
      assertEquals(139999, sub.getData("k", 69999));
   }

   /**
    * A data set with columns k and g, row i = { i, i is even ? "A" : "B" }.
    */
   private static DefaultDataSet base(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "k", "g" };

      for(int i = 0; i < rows; i++) {
         data[i + 1] = new Object[] { i, i % 2 == 0 ? "A" : "B" };
      }

      return new DefaultDataSet(data);
   }

   private static int[] range(int n) {
      int[] mapping = new int[n];

      for(int i = 0; i < n; i++) {
         mapping[i] = i;
      }

      return mapping;
   }

   private static XIntFragment mapping2(SubDataSet sub) throws Exception {
      Field field = SubDataSet.class.getDeclaredField("mapping2");
      field.setAccessible(true);
      return (XIntFragment) field.get(sub);
   }
}
