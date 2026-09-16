package inetsoft.graph.aesthetic;

import inetsoft.graph.internal.OKLab;

import java.awt.Color;

/**
 * The ENGINE §4 derivation rule. Lives in test sources because nothing derives a ramp at runtime -
 * the three house ramps are fixed, authored literally, and this class exists to prove the authored
 * hexes are still what the rule produces.
 */
public final class ChartRampDerivation {
   public enum Kind { SEQUENTIAL, DIVERGING }

   public static final Color LIGHT_SURFACE = new Color(0xF8F7F4);
   public static final Color DARK_SURFACE = new Color(0x252428);

   /** Every stop clears both surfaces by at least this much OKLab lightness. */
   public static final double DELTA_L_MIN = 0.12;
   /** Chroma probe: large enough that toColorInGamut always bisects down to the ceiling. */
   public static final double CHROMA_PROBE = 0.40;
   /** How much of the available chroma ceiling a fully-saturated stop takes. */
   public static final double CHROMA_FILL = 0.90;
   /** Inset from the C1 boundary, so 8-bit quantization cannot round a stop across it. */
   public static final double EPSILON = 0.01;

   public static final String[] AMBER_SOURCE = {
      "FCEFE0", "F7DDBB", "EFBE87", "DE9A4A", "C1731A", "96520B", "5E3204"
   };
   public static final String[] TEAL_SOURCE = {
      "E2F4F2", "BCE6E1", "7FCEC7", "35AFA6", "1D8A86", "12665F", "0A3F3B"
   };
   public static final String[] VARIANCE_SOURCE = {
      "12665F", "35AFA6", "A8D8D3", "EFEDE7", "F2C89A", "DE9A4A", "96520B"
   };

   private ChartRampDerivation() {
   }

   /** The low end of the usable lightness band: dark surface plus the clearance. */
   public static double bandLow() {
      return lightnessOf(DARK_SURFACE) + DELTA_L_MIN;
   }

   /** The high end of the usable lightness band: light surface minus the clearance. */
   public static double bandHigh() {
      return lightnessOf(LIGHT_SURFACE) - DELTA_L_MIN;
   }

   public static double lightnessOf(Color c) {
      return OKLab.fromColor(c)[0];
   }

   /**
    * The rule.
    *
    * Sequential: keep each source stop's hue, remap its lightness linearly from the source range
    * onto the usable band, and give it the same relative share of the chroma available at its new
    * lightness. The near-white low end therefore becomes a light but chromatic colour instead of a
    * tint of the canvas.
    *
    * Diverging: hold lightness flat at the band's midpoint so lightness carries no signal, keep the
    * wings' hues, and run chroma from zero at the centre to the ceiling at each end. "At target"
    * becomes an achromatic mark rather than an absent one.
    */
   public static String[] derive(String[] sourceHex, Kind kind) {
      double[][] lch = new double[sourceHex.length][];

      for(int i = 0; i < sourceHex.length; i++) {
         lch[i] = OKLab.toLCH(OKLab.fromColor(Color.decode("#" + sourceHex[i])));
      }

      return kind == Kind.SEQUENTIAL ? deriveSequential(lch) : deriveDiverging(lch);
   }

   private static String[] deriveSequential(double[][] lch) {
      double srcLo = Double.MAX_VALUE;
      double srcHi = -Double.MAX_VALUE;
      double srcCMax = 0;

      for(double[] stop : lch) {
         srcLo = Math.min(srcLo, stop[0]);
         srcHi = Math.max(srcHi, stop[0]);
         srcCMax = Math.max(srcCMax, stop[1]);
      }

      String[] out = new String[lch.length];

      for(int i = 0; i < lch.length; i++) {
         double t = (lch[i][0] - srcLo) / (srcHi - srcLo);
         double l = (bandLow() + EPSILON) + t * ((bandHigh() - EPSILON) - (bandLow() + EPSILON));
         double hue = lch[i][2];
         double share = srcCMax == 0 ? 0 : lch[i][1] / srcCMax;

         out[i] = hex(OKLab.toColorInGamut(l, share * chromaCeiling(l, hue) * CHROMA_FILL, hue));
      }

      return out;
   }

   private static String[] deriveDiverging(double[][] lch) {
      int mid = lch.length / 2;
      double l = ((bandLow() + EPSILON) + (bandHigh() - EPSILON)) / 2;
      String[] out = new String[lch.length];

      for(int i = 0; i < lch.length; i++) {
         if(i == mid) {
            out[i] = hex(OKLab.toColorInGamut(l, 0, 0));
            continue;
         }

         double hue = lch[i][2];
         double share = Math.abs(i - mid) / (double) mid;

         out[i] = hex(OKLab.toColorInGamut(l, share * chromaCeiling(l, hue) * CHROMA_FILL, hue));
      }

      return out;
   }

   /** The most chroma sRGB can hold at this lightness and hue. */
   public static double chromaCeiling(double l, double hue) {
      Color capped = OKLab.toColorInGamut(l, CHROMA_PROBE, hue);
      return OKLab.toLCH(OKLab.fromColor(capped))[1];
   }

   public static String hex(Color c) {
      return String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
   }

   /** The seven stops joined as AbstractSplineColorFrame.getColorRamps() expects them. */
   public static String joined(String[] stops) {
      return String.join("", stops);
   }
}
