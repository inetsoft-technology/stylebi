package inetsoft.graph.aesthetic;

import inetsoft.graph.internal.OKLab;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static inetsoft.graph.aesthetic.ChartRampDerivation.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ChartRampDerivationTest {
   /** Below this, an OKLab patch stops reading as a hue at all. A real collapse lands near zero. */
   private static final double CHROMA_FLOOR = 0.01;

   @Test
   void everyStopClearsBothSurfaces() {
      double light = lightnessOf(LIGHT_SURFACE);
      double dark = lightnessOf(DARK_SURFACE);

      for(String[] ramp : allRamps()) {
         for(String stop : ramp) {
            double l = lightnessOf(Color.decode("#" + stop));
            assertTrue(Math.abs(l - light) >= DELTA_L_MIN,
                       stop + " is within " + DELTA_L_MIN + " of the light canvas");
            assertTrue(Math.abs(l - dark) >= DELTA_L_MIN,
                       stop + " is within " + DELTA_L_MIN + " of the dark surface");
         }
      }
   }

   @Test
   void sequentialRampsAreMonotonicAndSpanEnough() {
      for(String[] ramp : new String[][] { amber(), teal() }) {
         double first = lightnessOf(Color.decode("#" + ramp[0]));
         double last = lightnessOf(Color.decode("#" + ramp[ramp.length - 1]));

         for(int i = 1; i < ramp.length; i++) {
            double prev = lightnessOf(Color.decode("#" + ramp[i - 1]));
            double cur = lightnessOf(Color.decode("#" + ramp[i]));
            assertTrue(cur < prev, "lightness must fall monotonically, broke at stop " + i);
         }

         // guards constant drift, not the algorithm: the derivation maps the source extremes onto
         // the whole band, so this fires only if DELTA_L_MIN, EPSILON or a surface colour moves far
         // enough to squeeze the band below what a sequential ramp needs
         assertTrue(Math.abs(first - last) >= 0.30, "lightness span must be at least 0.30");
      }
   }

   @Test
   void varianceCarriesMagnitudeInChromaNotLightness() {
      String[] ramp = variance();
      double lo = Double.MAX_VALUE;
      double hi = -Double.MAX_VALUE;

      for(String stop : ramp) {
         double l = lightnessOf(Color.decode("#" + stop));
         lo = Math.min(lo, l);
         hi = Math.max(hi, l);
      }

      // deriveDiverging gives every stop one lightness, so this cannot fail today. It guards the
      // regression that would matter most: a later change giving Variance per-stop lightness would
      // put magnitude back into the channel this ramp exists to keep it out of
      assertTrue(hi - lo <= 0.10, "Variance lightness must stay inside a 0.10 band, was " + (hi - lo));

      int mid = ramp.length / 2;
      assertEquals(0.0, chromaOf(ramp[mid]), 0.01, "the midpoint must be achromatic");

      for(int i = mid + 1; i < ramp.length; i++) {
         assertTrue(chromaOf(ramp[i]) > chromaOf(ramp[i - 1]), "chroma must rise outward, stop " + i);
      }

      for(int i = mid - 1; i >= 0; i--) {
         assertTrue(chromaOf(ramp[i]) > chromaOf(ramp[i + 1]), "chroma must rise outward, stop " + i);
      }
   }

   @Test
   void hueStaysFaithfulToTheSource() {
      assertHueMatches(AMBER_SOURCE, amber(), -1);
      assertHueMatches(TEAL_SOURCE, teal(), -1);
      assertHueMatches(VARIANCE_SOURCE, variance(), VARIANCE_SOURCE.length / 2);
   }

   @Test
   void everyStopHasLiveChromaExceptTheAchromaticMidpoint() {
      for(String stop : amber()) {
         assertTrue(chromaOf(stop) > CHROMA_FLOOR, stop + " collapsed toward grey (chroma <= " + CHROMA_FLOOR + ")");
      }

      for(String stop : teal()) {
         assertTrue(chromaOf(stop) > CHROMA_FLOOR, stop + " collapsed toward grey (chroma <= " + CHROMA_FLOOR + ")");
      }

      String[] variance = variance();
      int mid = variance.length / 2;

      for(int i = 0; i < variance.length; i++) {
         if(i == mid) {
            assertTrue(chromaOf(variance[i]) <= CHROMA_FLOOR,
                       "Variance's midpoint should be achromatic by construction, was chroma "
                       + chromaOf(variance[i]));
         }
         else {
            assertTrue(chromaOf(variance[i]) > CHROMA_FLOOR,
                       variance[i] + " collapsed toward grey (chroma <= " + CHROMA_FLOOR + ")");
         }
      }
   }

   private void assertHueMatches(String[] source, String[] derived, int exemptIndex) {
      for(int i = 0; i < source.length; i++) {
         if(i == exemptIndex) {
            continue;
         }

         double want = hueOf(source[i]);
         double got = hueOf(derived[i]);
         double delta = Math.abs(((want - got + 540) % 360) - 180);
         assertTrue(delta <= 5.0, "stop " + i + " drifted " + delta + "° from the source hue");
      }
   }

   private static double hueOf(String hex) {
      return OKLab.toLCH(OKLab.fromColor(Color.decode("#" + hex)))[2];
   }

   private static double chromaOf(String hex) {
      return OKLab.toLCH(OKLab.fromColor(Color.decode("#" + hex)))[1];
   }

   private static String[] amber() {
      return derive(AMBER_SOURCE, Kind.SEQUENTIAL);
   }

   private static String[] teal() {
      return derive(TEAL_SOURCE, Kind.SEQUENTIAL);
   }

   private static String[] variance() {
      return derive(VARIANCE_SOURCE, Kind.DIVERGING);
   }

   private static String[][] allRamps() {
      return new String[][] { amber(), teal(), variance() };
   }
}
