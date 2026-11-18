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
package inetsoft.report.filter;

import inetsoft.report.StyleConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80952: DistinctCountFormula must apply the percentage for any numeric total,
 * not only an Integer total.
 */
@Tag("core")
class DistinctCountFormulaPercentageTest {
   private DistinctCountFormula formula;

   @BeforeEach
   void setUp() {
      formula = new DistinctCountFormula();
      formula.addValue("a");
      formula.addValue("b");
      formula.addValue("a");
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GRANDTOTAL);
   }

   private void assertPercentage(Object total) {
      formula.setTotal(total);
      assertEquals(0.5, ((Number) formula.getResult()).doubleValue(), 1e-9);
      assertEquals(0.5, formula.getDoubleResult(), 1e-9);
   }

   @Test
   void integerTotal() {
      assertPercentage(4);
   }

   @Test
   void longTotal() {
      assertPercentage(4L);
   }

   @Test
   void doubleTotal() {
      assertPercentage(4.0);
   }

   @Test
   void shortTotal() {
      assertPercentage((short) 4);
   }

   @Test
   void zeroTotal() {
      formula.setTotal(0L);
      formula.setDefaultResult(true);
      assertEquals(0.0, formula.getDoubleResult());
      assertEquals(0.0, formula.getResult());
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void nonNumberTotalReturnsRawCount() {
      formula.setTotal("4");
      assertEquals(2, formula.getResult());
      assertEquals(2.0, formula.getDoubleResult());
      formula.setTotal(null);
      assertEquals(2, formula.getResult());
   }

   @Test
   void noPercentageIgnoresTotal() {
      formula.setPercentageType(StyleConstants.PERCENTAGE_NONE);
      formula.setTotal(4L);
      assertEquals(2, formula.getResult());
   }
}
