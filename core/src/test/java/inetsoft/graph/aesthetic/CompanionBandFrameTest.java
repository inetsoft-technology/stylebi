package inetsoft.graph.aesthetic;

import inetsoft.graph.internal.OKLab;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class CompanionBandFrameTest {
   @Test
   void aSingleBandIsExactlyTheResolvedCompanion() {
      CategoricalColorFrame frame = CategoricalColorFrame.companionBands(
         new Color(0x0490FF), new Color(0x97BEEB), 1);
      assertEquals(new Color(0x97BEEB), frame.getDefaultColor(0));
   }

   @Test
   void anAuthoredCompanionIsUsedVerbatimRatherThanRederived() {
      // light slot 6 (Amber) is the authored hand-tune the rule does not reproduce: passing it
      // in must yield it back, which is what proves the override reaches the band
      CategoricalColorFrame frame = CategoricalColorFrame.companionBands(
         new Color(0xFFB020), new Color(0xFFE7C7), 1);
      assertEquals(new Color(0xFFE7C7), frame.getDefaultColor(0));
   }

   @Test
   void multipleBandsWalkLightnessTowardTheBaseAtOneHue() {
      Color base = new Color(0x0490FF);
      CategoricalColorFrame frame =
         CategoricalColorFrame.companionBands(base, new Color(0x97BEEB), 3);

      double baseHue = OKLab.toLCH(OKLab.fromColor(base))[2];
      double baseL = OKLab.toLCH(OKLab.fromColor(base))[0];
      double previousL = Double.MAX_VALUE;

      for(int i = 0; i < 3; i++) {
         double[] lch = OKLab.toLCH(OKLab.fromColor(frame.getDefaultColor(i)));
         assertEquals(baseHue, lch[2], 2.0, "band " + i + " hue");
         assertTrue(lch[0] < previousL, "band " + i + " must be darker than the last");
         assertTrue(lch[0] > baseL, "band " + i + " must not reach the base");
         previousL = lch[0];
      }
   }

   @Test
   void theFirstBandIsTheCompanionRegardlessOfCount() {
      for(int n : new int[] { 1, 2, 5 }) {
         CategoricalColorFrame frame = CategoricalColorFrame.companionBands(
            new Color(0x0490FF), new Color(0x97BEEB), n);
         assertEquals(new Color(0x97BEEB), frame.getDefaultColor(0), "count " + n);
      }
   }

   @Test
   void darkBandsDeepenFromTheirOwnCompanion() {
      CategoricalColorFrame frame = CategoricalColorFrame.companionBands(
         new Color(0x4FA5FF), new Color(0x00569C), 1);
      assertEquals(new Color(0x00569C), frame.getDefaultColor(0));
   }
}
