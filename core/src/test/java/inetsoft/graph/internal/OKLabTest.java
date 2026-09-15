package inetsoft.graph.internal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class OKLabTest {
   @Test
   void whiteAndBlackHitKnownEndpoints() {
      assertEquals(1.0, OKLab.fromColor(Color.WHITE)[0], 0.001);
      assertEquals(0.0, OKLab.fromColor(Color.BLACK)[0], 0.001);
   }

   @Test
   void knownBasesMeasureTheirPublishedLightness() {
      // the eight Modern bases, used by the derivation rule in CategoricalColorFrame
      assertEquals(0.650, OKLab.toLCH(OKLab.fromColor(new Color(0x0490FF)))[0], 0.002);
      assertEquals(0.269, OKLab.toLCH(OKLab.fromColor(new Color(0x241C4F)))[0], 0.002);
      assertEquals(0.813, OKLab.toLCH(OKLab.fromColor(new Color(0xFFB020)))[0], 0.002);
      // the dark anchor sits ABOVE 0.40, which is why the dark threshold is 0.50
      assertEquals(0.420, OKLab.toLCH(OKLab.fromColor(new Color(0x49447D)))[0], 0.002);
   }

   @Test
   void roundTripsEverySrgbValueOnTheGreyAxisAndASample() {
      for(int i = 0; i <= 255; i++) {
         Color c = new Color(i, i, i);
         double[] lab = OKLab.fromColor(c);
         assertEquals(c, OKLab.toColor(lab[0], lab[1], lab[2]), "grey " + i);
      }

      int[] samples = { 0x0490FF, 0xFF5A35, 0x241C4F, 0x03D9B3,
                        0x9A2DDC, 0xFFB020, 0xE5197E, 0x8ED604 };

      for(int rgb : samples) {
         Color c = new Color(rgb);
         double[] lab = OKLab.fromColor(c);
         assertEquals(c, OKLab.toColor(lab[0], lab[1], lab[2]), Integer.toHexString(rgb));
      }
   }

   @Test
   void lchRoundTripsThroughLab() {
      Color c = new Color(0x9A2DDC);
      double[] lch = OKLab.toLCH(OKLab.fromColor(c));
      double[] lab = OKLab.fromLCH(lch[0], lch[1], lch[2]);
      assertEquals(c, OKLab.toColor(lab[0], lab[1], lab[2]));
   }

   @Test
   void inGamutRequestPassesThroughUnchanged() {
      // Azure's light companion is comfortably inside sRGB. Derive L/C/H from the base rather
      // than hardcoding - toLCH normalises hue to [0,360), so a hand-copied atan2 value is wrong.
      double[] lch = OKLab.toLCH(OKLab.fromColor(new Color(0x0490FF)));
      assertEquals(251.913, lch[2], 0.01, "hue is normalised to [0,360)");
      assertEquals(new Color(0x97BEEB),
                   OKLab.toColorInGamut(lch[0] + 0.14, lch[1] * 0.40, lch[2]));
   }

   @Test
   void outOfGamutRequestReducesChromaAndHoldsHue() {
      // Amber's light companion target is outside sRGB at this lightness
      double[] lch = OKLab.toLCH(OKLab.fromColor(new Color(0xFFB020)));
      Color mapped = OKLab.toColorInGamut(lch[0] + 0.14, lch[1] * 0.40, lch[2]);
      double[] out = OKLab.toLCH(OKLab.fromColor(mapped));

      // hue held within rounding, chroma reduced, lightness held
      assertEquals(lch[2], out[2], 1.5, "hue must not shift");
      assertTrue(out[1] < lch[1] * 0.40, "chroma must be reduced");
      assertEquals(lch[0] + 0.14, out[0], 0.01, "lightness held");
   }
}
