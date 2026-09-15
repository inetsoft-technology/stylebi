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
