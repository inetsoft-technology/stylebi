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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.AggregateFormula;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@code VSLayoutTool.getAggregateColumn()}, which maps a crosstab cell binding value to
 * the physical column name in the result table (#75799).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSLayoutToolAggregateColumnTest {
   @Test
   void nullColumnReturnsNull() {
      assertNull(VSLayoutTool.getAggregateColumn(null, "Sum(price)"));
   }

   @Test
   void columnEqualToAggregateNameIsReturned() {
      assertEquals("price", VSLayoutTool.getAggregateColumn("price", "price"));
      assertEquals("price", VSLayoutTool.getAggregateColumn("Sum(price)", "price"));
   }

   @Test
   void preAggregatedColumnKeepsItsPhysicalName() {
      // aggregating the worksheet column "Min(ftime)" produces "Min(Min(ftime))"; the lookup
      // key must stay "Min(ftime)", not the bare "ftime"
      assertEquals("Min(ftime)", VSLayoutTool.getAggregateColumn("Min(ftime)", "Min(Min(ftime))"));
      assertEquals("Min(ftime)", VSLayoutTool.getAggregateColumn(
         "Min(ftime)", "Min(Min(ftime))", AggregateFormula.MIN));
   }

   @Test
   void unrelatedAggregateStripsWrapper() {
      assertEquals("price", VSLayoutTool.getAggregateColumn("Sum(price)", "Count(qty)"));
   }

   @Test
   void squareBracketsAreRemoved() {
      assertEquals("a", VSLayoutTool.getAggregateColumn("[a]", "x"));
   }

   @Test
   void twoColumnFormulaKeepsFirstColumn() {
      assertEquals("x", VSLayoutTool.getAggregateColumn(
         "Correlation(x,y)", "z", AggregateFormula.CORRELATION));
   }

   @Test
   void formulaWithNKeepsFirstColumn() {
      assertEquals("x", VSLayoutTool.getAggregateColumn(
         "NthLargest(x,2)", "z", AggregateFormula.NTH_LARGEST));
   }

   @Test
   void singleColumnFormulaKeepsComma() {
      assertEquals("x,y", VSLayoutTool.getAggregateColumn(
         "Sum(x,y)", "z", AggregateFormula.SUM));
   }
}
