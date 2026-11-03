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
package inetsoft.uql.viewsheet.internal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class PageMarkCheckTest {
   @Test
   void aLightMarkIsOfferedDarkWhenThePageGoesDark() {
      assertEquals(VizMark.MODERN_DARK,
                   PageMarkCheck.offer(hex("#F8F7F4"), hex("#000000"), VizMark.MODERN_LIGHT));
   }

   @Test
   void aSecondDarkColourOffersNothing() {
      assertNull(PageMarkCheck.offer(hex("#000000"), hex("#1A2B3C"), VizMark.MODERN_LIGHT));
   }

   @Test
   void returningToTheMarksSideOffersNothing() {
      assertNull(PageMarkCheck.offer(hex("#000000"), hex("#FFFFFF"), VizMark.MODERN_LIGHT));
   }

   @Test
   void aDarkMarkIsOfferedLightWhenThePageGoesLight() {
      assertEquals(VizMark.MODERN_LIGHT,
                   PageMarkCheck.offer(hex("#1C1B1F"), hex("#FFFFFF"), VizMark.MODERN_DARK));
   }

   @Test
   void aLegacySheetIsNeverOffered() {
      assertNull(PageMarkCheck.offer(hex("#FFFFFF"), hex("#000000"), null));
      assertNull(PageMarkCheck.offer(hex("#000000"), hex("#FFFFFF"), null));
   }

   @Test
   void nullColoursOfferNothing() {
      assertNull(PageMarkCheck.offer(null, hex("#000000"), VizMark.MODERN_LIGHT));
      assertNull(PageMarkCheck.offer(hex("#FFFFFF"), null, VizMark.MODERN_LIGHT));
      assertNull(PageMarkCheck.offer(null, hex("#FFFFFF"), VizMark.MODERN_DARK));
      assertNull(PageMarkCheck.offer(hex("#000000"), null, VizMark.MODERN_DARK));
   }

   @Test
   void theThresholdIsEqualContrast() {
      // #757575 has luminance 0.1779, #767676 has 0.1812
      assertTrue(PageMarkCheck.isDark(hex("#757575")));
      assertFalse(PageMarkCheck.isDark(hex("#767676")));
      assertEquals(VizMark.MODERN_DARK,
                   PageMarkCheck.offer(Color.WHITE, hex("#757575"), VizMark.MODERN_LIGHT));
      assertNull(PageMarkCheck.offer(Color.WHITE, hex("#767676"), VizMark.MODERN_LIGHT));
   }

   private static Color hex(String value) {
      return Color.decode(value);
   }
}
