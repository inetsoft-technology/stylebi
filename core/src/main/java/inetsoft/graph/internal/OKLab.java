/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.internal;

import java.awt.Color;

/**
 * sRGB to OKLab and OKLCH conversion. OKLab is perceptually uniform, so an equal step in L is an
 * equal step in apparent lightness regardless of hue - which neither sRGB channel scaling
 * (ColorFrame.process) nor HSB brightness can provide.
 */
public final class OKLab {
   private OKLab() {
   }

   public static double[] fromColor(Color c) {
      double r = toLinear(c.getRed());
      double g = toLinear(c.getGreen());
      double b = toLinear(c.getBlue());

      double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
      double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
      double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

      double l2 = Math.cbrt(l);
      double m2 = Math.cbrt(m);
      double s2 = Math.cbrt(s);

      return new double[] {
         0.2104542553 * l2 + 0.7936177850 * m2 - 0.0040720468 * s2,
         1.9779984951 * l2 - 2.4285922050 * m2 + 0.4505937099 * s2,
         0.0259040371 * l2 + 0.7827717662 * m2 - 0.8086757660 * s2
      };
   }

   public static Color toColor(double l, double a, double b) {
      double[] rgb = toLinearRGB(l, a, b);
      return new Color(fromLinear(rgb[0]), fromLinear(rgb[1]), fromLinear(rgb[2]));
   }

   public static double[] toLCH(double[] lab) {
      double h = Math.toDegrees(Math.atan2(lab[2], lab[1]));
      return new double[] { lab[0], Math.hypot(lab[1], lab[2]), h < 0 ? h + 360 : h };
   }

   public static double[] fromLCH(double l, double c, double h) {
      double rad = Math.toRadians(h);
      return new double[] { l, c * Math.cos(rad), c * Math.sin(rad) };
   }

   /**
    * The colour at this lightness, chroma and hue, brought into sRGB by reducing chroma only.
    * Clamping channels instead would shift the hue, which the companion rule forbids.
    */
   public static Color toColorInGamut(double l, double c, double h) {
      if(inGamut(l, c, h)) {
         return toColor(fromLCH(l, c, h)[0], fromLCH(l, c, h)[1], fromLCH(l, c, h)[2]);
      }

      double lo = 0;
      double hi = c;

      for(int i = 0; i < 60; i++) {
         double mid = (lo + hi) / 2;

         if(inGamut(l, mid, h)) {
            lo = mid;
         }
         else {
            hi = mid;
         }
      }

      double[] lab = fromLCH(l, lo, h);
      return toColor(lab[0], lab[1], lab[2]);
   }

   private static boolean inGamut(double l, double c, double h) {
      double[] lab = fromLCH(l, c, h);
      double[] rgb = toLinearRGB(lab[0], lab[1], lab[2]);

      for(double v : rgb) {
         if(v < -1e-6 || v > 1 + 1e-6) {
            return false;
         }
      }

      return true;
   }

   private static double[] toLinearRGB(double l, double a, double b) {
      double l2 = l + 0.3963377774 * a + 0.2158037573 * b;
      double m2 = l - 0.1055613458 * a - 0.0638541728 * b;
      double s2 = l - 0.0894841775 * a - 1.2914855480 * b;

      double l3 = l2 * l2 * l2;
      double m3 = m2 * m2 * m2;
      double s3 = s2 * s2 * s2;

      return new double[] {
         4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
         -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
         -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
      };
   }

   private static double toLinear(int v) {
      double c = v / 255.0;
      return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
   }

   private static int fromLinear(double c) {
      double v = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
      return Math.max(0, Math.min(255, (int) Math.round(v * 255)));
   }
}
