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
package inetsoft.web.vswizard.handler;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link WizChartInfoIndex#resolve(int, int, int)}, the index resolver that lets
 * {@code VSWizardBindingHandler.addChartVSAssembly} build the chart sub-type a caller actually
 * selected across the combined {@code chartInfos ++ prefInfos} space.
 */
@Tag("core")
class WizChartInfoIndexTest {
   @Test
   void indexWithinChartInfosIsReturnedUnchanged() {
      // chartInfos=[0,1,2], prefInfos=[3,4]; selecting chartInfos[1] stays 1.
      assertEquals(1, WizChartInfoIndex.resolve(1, 3, 2));
   }

   @Test
   void indexIntoPrefInfosIsHonored() {
      // The bug: a preference-scored type (e.g. "point") lives in prefInfos at combined index
      // chartInfosSize+i. It must be honored, NOT clamped to chartInfos[0] (the default bar).
      assertEquals(3, WizChartInfoIndex.resolve(3, 3, 2));
      assertEquals(4, WizChartInfoIndex.resolve(4, 3, 2));
   }

   @Test
   void outOfRangeIndexClampsToZero() {
      // total = 3 + 2 = 5, so index 5 is out of range -> first info.
      assertEquals(0, WizChartInfoIndex.resolve(5, 3, 2));
   }

   @Test
   void negativeIndexClampsToZeroWhenInfosExist() {
      assertEquals(0, WizChartInfoIndex.resolve(-1, 3, 0));
   }

   @Test
   void noInfosReturnsMinusOne() {
      // Preserves the "No valid subtype for chart!" guard in addChartVSAssembly.
      assertEquals(-1, WizChartInfoIndex.resolve(0, 0, 0));
      assertEquals(-1, WizChartInfoIndex.resolve(-1, 0, 0));
   }

   @Test
   void prefInfosOnlyIsHonored() {
      // No base chartInfos, only preference-scored infos: index 0 and 1 are both valid.
      assertEquals(0, WizChartInfoIndex.resolve(0, 0, 2));
      assertEquals(1, WizChartInfoIndex.resolve(1, 0, 2));
   }
}
