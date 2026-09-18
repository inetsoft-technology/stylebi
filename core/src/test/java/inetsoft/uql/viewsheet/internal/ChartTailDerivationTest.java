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

import inetsoft.graph.internal.OKLab;
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

   // Port validation. An external handoff publishes the first three generated slots, derived with
   // ONE ring. Reproducing them is what proves this OKLab port agrees with the one the handoff was
   // written against, before any shipped hex is derived from it. Slot 11 is excluded deliberately:
   // its published chroma is 0.202 where the head's mean is 0.188, so it did not come from the
   // stated rule.
   @Test
   void singleRingReproducesTheHandoffsPublishedSlots() {
      Color[] tail = ChartTailDerivation.derive(MODERN_HEAD, 2, 1, 0);

      assertEquals(new Color(0x009FB6), tail[0], "the handoff's published slot 9");
      assertEquals(new Color(0x9E9000), tail[1], "slot 10");
   }

   @Test
   void theShippingConfigurationProducesThirtyTwoSlots() {
      assertEquals(32, ChartTailDerivation.derive(MODERN_HEAD).length);
   }

   private static final Color[] DARK_HEAD = {
      new Color(0x4FA5FF), new Color(0xFF8367), new Color(0x49447D), new Color(0x2DEEC6),
      new Color(0xAE41F5), new Color(0xFFCB82), new Color(0xFE3290), new Color(0x9FEB28)
   };

   // Disambiguates the drift guard below. The heads above are duplicated rather than read, so a
   // re-tune that lands in production but not here would surface as "the tail does not match the
   // rule" when the real fault is a stale fixture. This says which one it is.
   @Test
   void theDuplicatedHeadsMatchTheShippedHeads() throws Exception {
      assertArrayEquals(MODERN_HEAD, colorArray("MODERN_HEAD"),
                        "this test's MODERN_HEAD copy is stale");
      assertArrayEquals(DARK_HEAD, colorArray("DARK_HEAD"),
                        "this test's DARK_HEAD copy is stale");
   }

   // The drift guard. If the head is ever re-tuned again, the tail fails loudly here instead of
   // silently belonging to the previous head.
   @Test
   void shippedTailsMatchTheRule() throws Exception {
      assertArrayEquals(ChartTailDerivation.derive(MODERN_HEAD), colorArray("MODERN_TAIL"),
                        "MODERN_TAIL must be what the rule derives from MODERN_HEAD");
      assertArrayEquals(ChartTailDerivation.derive(DARK_HEAD), colorArray("DARK_TAIL"),
                        "DARK_TAIL must be what the rule derives from DARK_HEAD");
   }

   private static Color[] colorArray(String fieldName) throws Exception {
      Field field = VSChartPaletteDefaults.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      return (Color[]) field.get(null);
   }

   // The companion rule's light-mode escape hatch: above this, getCompanionColor deepens instead of
   // lifting, because a lift would land past white. Mirrors CategoricalColorFrame.LIGHT_MAX_L.
   private static final double LIGHT_MAX_L = 0.96;

   // The companion rule's anchor threshold in dark mode, from CategoricalColorFrame.
   private static final double DARK_ANCHOR_MAX_L = 0.50;

   // The whole point of the slice. Holding the tail inside the head's lightness band means the
   // companion rule covers every generated slot by construction, so no slot recedes in the opposite
   // direction from its neighbours. Today's legacy tail trips this three times by slot 16.
   @Test
   void noModernSlotNeedsTheLightEndException() throws Exception {
      for(Color c : colorArray("MODERN_FALLBACK")) {
         double l = OKLab.toLCH(OKLab.fromColor(c))[0];
         assertTrue(l + 0.14 <= LIGHT_MAX_L,
                    "no Modern slot may need the light-end exception, but " + hex(c)
                       + " sits at L " + l);
      }
   }

   // Dark's mirror. Exactly one slot may take the anchor branch - the anchor itself, #49447D, which
   // is the set's darkest member and is meant to. Today's tail adds three more.
   @Test
   void onlyTheAnchorTakesTheDarkAnchorBranch() throws Exception {
      int anchors = 0;

      for(Color c : colorArray("DARK_FALLBACK")) {
         if(OKLab.toLCH(OKLab.fromColor(c))[0] < DARK_ANCHOR_MAX_L) {
            anchors++;
         }
      }

      assertEquals(1, anchors, "only #49447D may take the dark anchor branch");
   }

   // The rings, not the head's full band. The head spans 0.269-0.813 on Modern, so asserting
   // against it would pass a tail slot at L 0.28 - precisely the slot that recedes in the opposite
   // direction from its neighbours. The rule puts every generated slot at meanL +/- lSpread, so
   // that is what gets pinned. The tolerance covers gamut mapping and the round to 8-bit channels;
   // the measured worst deviation is 1.3e-3.
   private static final double RING_TOLERANCE = 5e-3;

   @Test
   void everyModernTailSlotSitsOnARing() throws Exception {
      assertTailSitsOnARing(MODERN_HEAD, "MODERN_TAIL");
   }

   @Test
   void everyDarkTailSlotSitsOnARing() throws Exception {
      assertTailSitsOnARing(DARK_HEAD, "DARK_TAIL");
   }

   private static void assertTailSitsOnARing(Color[] head, String tailField) throws Exception {
      double sumL = 0;

      for(Color c : head) {
         sumL += OKLab.toLCH(OKLab.fromColor(c))[0];
      }

      double meanL = sumL / head.length;
      double lo = meanL - 0.12 - RING_TOLERANCE;
      double hi = meanL + 0.12 + RING_TOLERANCE;

      for(Color c : colorArray(tailField)) {
         double l = OKLab.toLCH(OKLab.fromColor(c))[0];
         assertTrue(l >= lo && l <= hi,
                    hex(c) + " at L " + l + " sits outside the rings " + lo + "-" + hi);
      }
   }

   // The measured separation figures. Asserted with a tolerance rather than as floors, so
   // a change to the rule has to restate its cost instead of silently coasting under a round number.
   @Test
   void separationMatchesTheMeasuredFigures() throws Exception {
      assertEquals(0.1311, minDeltaE("MODERN_FALLBACK", 12), 5e-4, "Modern at n=12");
      assertEquals(0.1127, minDeltaE("MODERN_FALLBACK", 16), 5e-4, "Modern at n=16");
      assertEquals(0.0364, minDeltaE("MODERN_FALLBACK", 40), 5e-4, "Modern at n=40");
      assertEquals(0.1491, minDeltaE("DARK_FALLBACK", 12), 5e-4, "Modern Dark at n=12");
      assertEquals(0.1079, minDeltaE("DARK_FALLBACK", 16), 5e-4, "Modern Dark at n=16");
      assertEquals(0.0383, minDeltaE("DARK_FALLBACK", 40), 5e-4, "Modern Dark at n=40");
   }

   // A tail colour that reads as a head colour is the failure the aggregate figures hide, because it
   // pairs slot 1 with slot 34. Modern beats the legacy tail's 0.0365 here; dark's 0.0405 is below
   // the legacy tail's 0.0461 and is recorded in the design as the one axis that does not improve.
   @Test
   void worstHeadToTailPairMatchesTheMeasuredFigures() throws Exception {
      assertEquals(0.0521, worstHeadToTail("MODERN_FALLBACK"), 5e-4, "Modern");
      assertEquals(0.0405, worstHeadToTail("DARK_FALLBACK"), 5e-4, "Modern Dark");
   }

   private static double minDeltaE(String fieldName, int n) throws Exception {
      Color[] palette = colorArray(fieldName);
      double worst = Double.MAX_VALUE;

      for(int i = 0; i < n; i++) {
         for(int j = i + 1; j < n; j++) {
            worst = Math.min(worst, ChartTailDerivation.deltaE(palette[i], palette[j]));
         }
      }

      return worst;
   }

   private static double worstHeadToTail(String fieldName) throws Exception {
      Color[] palette = colorArray(fieldName);
      double worst = Double.MAX_VALUE;

      for(int i = 0; i < 8; i++) {
         for(int j = 8; j < palette.length; j++) {
            worst = Math.min(worst, ChartTailDerivation.deltaE(palette[i], palette[j]));
         }
      }

      return worst;
   }

   private static String hex(Color c) {
      return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
   }
}
