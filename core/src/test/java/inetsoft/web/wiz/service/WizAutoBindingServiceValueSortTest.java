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
package inetsoft.web.wiz.service;

import inetsoft.uql.XConstants;
import inetsoft.web.wiz.model.Ranking;
import inetsoft.web.wiz.service.WizAutoBindingService.DimensionSort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("core")
class WizAutoBindingServiceValueSortTest {
   private static Ranking ranking(int optionValue, String col) {
      Ranking r = new Ranking();
      r.setOptionValue(optionValue);
      r.setRankingN(25);
      r.setRankingCol(col);
      return r;
   }

   @Test
   void topNRankingDerivesValueDescSort() {
      DimensionSort s = WizAutoBindingService.computeDimensionSort(
         null, null, ranking(9, "Sum(total_price)"), false);
      assertEquals(XConstants.SORT_VALUE_DESC, s.order());
      assertEquals("Sum(total_price)", s.sortByCol());
   }

   @Test
   void bottomNRankingDerivesValueAscSort() {
      DimensionSort s = WizAutoBindingService.computeDimensionSort(
         null, null, ranking(10, "Sum(total_price)"), false);
      assertEquals(XConstants.SORT_VALUE_ASC, s.order());
      assertEquals("Sum(total_price)", s.sortByCol());
   }

   @Test
   void explicitOrderWinsOverRanking() {
      DimensionSort s = WizAutoBindingService.computeDimensionSort(
         XConstants.SORT_DESC, null, ranking(9, "Sum(total_price)"), false);
      assertEquals(XConstants.SORT_DESC, s.order());
      assertNull(s.sortByCol());
   }

   @Test
   void explicitSortByColKeptWithDerivedOrder() {
      DimensionSort s = WizAutoBindingService.computeDimensionSort(
         null, "Max(amount)", ranking(9, "Sum(total_price)"), false);
      assertEquals(XConstants.SORT_VALUE_DESC, s.order());
      assertEquals("Max(amount)", s.sortByCol());
   }

   @Test
   void timeSeriesSkipsAutoDerive() {
      DimensionSort s = WizAutoBindingService.computeDimensionSort(
         null, null, ranking(9, "Sum(total_price)"), true);
      assertNull(s.order());
      assertNull(s.sortByCol());
   }

   @Test
   void noRankingNoOrderYieldsNothing() {
      DimensionSort s = WizAutoBindingService.computeDimensionSort(null, null, null, false);
      assertNull(s.order());
      assertNull(s.sortByCol());
   }

   @Test
   void explicitOrderAndExplicitSortByColBothKept() {
      DimensionSort s = WizAutoBindingService.computeDimensionSort(
         XConstants.SORT_DESC, "Max(amount)", ranking(9, "Sum(total_price)"), false);
      assertEquals(XConstants.SORT_DESC, s.order());
      assertEquals("Max(amount)", s.sortByCol());
   }
}
