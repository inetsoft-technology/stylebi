package inetsoft.graph.aesthetic;

import inetsoft.graph.internal.OKLab;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class CategoricalColorFrameCompanionTest {
   @Test
   void lightNonAnchorLiftsLightnessAndCutsChroma() {
      CategoricalColorFrame frame = frameWith(new Color(0x0490FF));
      assertEquals(new Color(0x97BEEB), frame.getCompanionColor(0, false));
   }

   @Test
   void lightAnchorIsSentToTheCanvasEnd() {
      // Ink measures L 0.269, below the 0.40 light threshold
      CategoricalColorFrame frame = frameWith(new Color(0x241C4F));
      assertEquals(new Color(0xCCCCE9), frame.getCompanionColor(0, false));
   }

   @Test
   void darkNonAnchorDeepensAndHoldsChroma() {
      CategoricalColorFrame frame = frameWith(new Color(0x4FA5FF));
      assertEquals(new Color(0x00569C), frame.getCompanionColor(0, true));
   }

   @Test
   void darkAnchorLiftsBecauseItCannotDeepen() {
      // the dark anchor measures L 0.420 - ABOVE the light threshold of 0.40, which is why
      // the dark threshold is 0.50. A flat 0.40 would yield #0E0033 here.
      CategoricalColorFrame frame = frameWith(new Color(0x49447D));
      assertEquals(new Color(0x8988AB), frame.getCompanionColor(0, true));
   }

   @Test
   void hueIsNeverModified() {
      int[] bases = { 0x0490FF, 0xFF5A35, 0x241C4F, 0x03D9B3,
                      0x9A2DDC, 0xFFB020, 0xE5197E, 0x8ED604 };

      for(int rgb : bases) {
         CategoricalColorFrame frame = frameWith(new Color(rgb));
         double baseHue = OKLab.toLCH(OKLab.fromColor(new Color(rgb)))[2];

         for(boolean dark : new boolean[] { false, true }) {
            Color companion = frame.getCompanionColor(0, dark);
            double hue = OKLab.toLCH(OKLab.fromColor(companion))[2];
            assertEquals(baseHue, hue, 2.0,
                         Integer.toHexString(rgb) + (dark ? " dark" : " light"));
         }
      }
   }

   @Test
   void aBaseTooLightToLiftDeepensInstead() {
      // lifting these by 0.14 lands past white, and toColorInGamut only reduces chroma, so the
      // companion collapsed to #ffffff and the mark rendered with no fill at all
      assertEquals(new Color(0xB6B7B8), frameWith(new Color(0xDADFE1)).getCompanionColor(0, false));
      assertEquals(new Color(0xAEBABC), frameWith(new Color(0xC5EFF7)).getCompanionColor(0, false));
      assertEquals(new Color(0xBEB7A6), frameWith(new Color(0xFDE3A7)).getCompanionColor(0, false));
   }

   @Test
   void aNearNeutralBaseStaysDistinctFromItself() {
      // desaturating cannot separate a colour that carries almost no chroma to begin with
      Color base = new Color(0xCCCCCC);
      Color companion = frameWith(base).getCompanionColor(0, false);

      assertEquals(new Color(0xB7B7B7), companion);
      assertNotEquals(base, companion);
   }

   @Test
   void theLightestHeadSlotStillLifts() {
      // Amber measures L 0.813, the closest any head colour comes to the ceiling. it must stay on
      // the lift rule: the drift guard owns its exact value, and the designer already hand-tuned
      // the authored entry for it
      Color base = new Color(0xFFB020);
      Color companion = frameWith(base).getCompanionColor(0, false);

      assertTrue(OKLab.toLCH(OKLab.fromColor(companion))[0] >
                    OKLab.toLCH(OKLab.fromColor(base))[0],
                 "amber's companion must be lighter than amber, not deepened");
   }

   @Test
   void theCeilingRuleHoldsHue() {
      // #dadfe1 and #cccccc are deliberately absent: they carry almost no chroma, so their hue is
      // not meaningful and 8-bit rounding moves it by tens of degrees either way
      int[] bases = { 0xC5EFF7, 0xFDE3A7, 0xB9DBF4, 0xCCCC66 };

      for(int rgb : bases) {
         double baseHue = OKLab.toLCH(OKLab.fromColor(new Color(rgb)))[2];
         Color companion = frameWith(new Color(rgb)).getCompanionColor(0, false);
         double hue = OKLab.toLCH(OKLab.fromColor(companion))[2];

         assertEquals(baseHue, hue, 2.0, Integer.toHexString(rgb));
      }
   }

   @Test
   void darkModeIsNotSubjectToTheCeiling() {
      // the dark rule deepens, so a light base has room; its floor is the anchor case instead
      Color companion = frameWith(new Color(0xDADFE1)).getCompanionColor(0, true);
      double l = OKLab.toLCH(OKLab.fromColor(companion))[0];

      assertEquals(0.900 - 0.26, l, 0.02);
   }

   @Test
   void anAbsentBaseHasNoCompanion() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      assertNull(frame.getCompanionColor(-1, false));
      assertNull(frame.getCompanionColor(9999, false));
   }

   private CategoricalColorFrame frameWith(Color base) {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDefaultColor(0, base);
      return frame;
   }
}
