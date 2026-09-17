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
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ChartTailDerivationTest {
   // The eight re-tuned head colours, duplicated here on purpose: this test is the authority on
   // what the rule produces, so it must not read the constants the rule is used to check.
   private static final Color[] MODERN_HEAD = {
      new Color(0x0490FF), new Color(0xFF5A35), new Color(0x241C4F), new Color(0x03D9B3),
      new Color(0x9A2DDC), new Color(0xFFB020), new Color(0xE5197E), new Color(0x8ED604)
   };

   // Port validation. ENGINE §3 publishes its first three generated slots, derived with ONE ring.
   // Reproducing them is what proves this OKLab port agrees with the one the handoff was written
   // against, before any shipped hex is derived from it. Slot 11 is excluded deliberately: its
   // published chroma is 0.202 where the head's mean is 0.188, so it did not come from the stated
   // rule. Recorded in the design's decision 3.
   @Test
   void singleRingReproducesTheHandoffsPublishedSlots() {
      Color[] tail = ChartTailDerivation.derive(MODERN_HEAD, 3, 1, 0);

      assertEquals(new Color(0x009FB6), tail[0], "ENGINE §3 slot 9");
      assertEquals(new Color(0x9E9000), tail[1], "ENGINE §3 slot 10");
   }

   @Test
   void theRuleIsAPureFunctionOfTheHead() {
      assertArrayEquals(ChartTailDerivation.derive(MODERN_HEAD),
                        ChartTailDerivation.derive(MODERN_HEAD),
                        "two runs on the same head must agree, or the shipped literals drift");
   }

   @Test
   void theShippingConfigurationProducesThirtyTwoSlots() {
      assertEquals(32, ChartTailDerivation.derive(MODERN_HEAD).length);
   }

   private static final Color[] DARK_HEAD = {
      new Color(0x4FA5FF), new Color(0xFF8367), new Color(0x49447D), new Color(0x2DEEC6),
      new Color(0xAE41F5), new Color(0xFFCB82), new Color(0xFE3290), new Color(0x9FEB28)
   };

   // The drift guard. If the head is ever re-tuned again, the tail fails loudly here instead of
   // silently belonging to the previous head.
   @Test
   void shippedTailsMatchTheRule() throws Exception {
      assertArrayEquals(ChartTailDerivation.derive(MODERN_HEAD), colorArray("MODERN_TAIL"),
                        "MODERN_TAIL must be what the rule derives from MODERN_HEAD");
      assertArrayEquals(ChartTailDerivation.derive(DARK_HEAD), colorArray("DARK_TAIL"),
                        "DARK_TAIL must be what the rule derives from DARK_HEAD");
   }

   @Test
   void shippedTailsAreThirtyTwoSlotsEach() throws Exception {
      assertEquals(32, colorArray("MODERN_TAIL").length);
      assertEquals(32, colorArray("DARK_TAIL").length);
   }

   private static Color[] colorArray(String fieldName) throws Exception {
      Field field = VSChartPaletteDefaults.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      return (Color[]) field.get(null);
   }
}
