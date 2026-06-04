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
package inetsoft.util.graphics.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SVGAnimationInjector#computePathLength}.
 *
 * <p>Regression coverage for smooth (curved) lines being truncated by the dashoffset
 * draw-on: a cubic segment's arc length must be measured along the curve, not as the
 * straight chord, or the dasharray underestimates the path and hides its tail.
 */
class SVGAnimationInjectorTest {
   @Test
   void straightPathLengthIsChordSum() {
      // 3-4-5 triangle leg + horizontal leg: 5 + 4 = 9.
      double len = SVGAnimationInjector.computePathLength("M0,0 L3,4 L7,4");
      assertEquals(9.0, len, 1e-6);
   }

   @Test
   void cubicArcLengthExceedsChord() {
      // A bulging curve from (0,0) to (100,0). Chord = 100; arc must be measurably longer.
      String d = "M0,0 C0,50 100,50 100,0";
      double arc = SVGAnimationInjector.computePathLength(d);
      double chord = Math.hypot(100, 0);
      assertTrue(arc > chord + 10,
                 "curved arc length (" + arc + ") should exceed the chord (" + chord + ")");
   }

   @Test
   void cubicArcLengthMatchesFineFlattening() {
      // Compare against a much finer manual flattening of the same cubic.
      double x0 = 0, y0 = 0, x1 = 0, y1 = 50, x2 = 100, y2 = 50, x3 = 100, y3 = 0;
      double ref = 0, px = x0, py = y0;
      int n = 4000;

      for(int i = 1; i <= n; i++) {
         double t = (double) i / n, u = 1 - t;
         double a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, dd = t * t * t;
         double nx = a * x0 + b * x1 + c * x2 + dd * x3;
         double ny = a * y0 + b * y1 + c * y2 + dd * y3;
         ref += Math.hypot(nx - px, ny - py);
         px = nx; py = ny;
      }

      double arc = SVGAnimationInjector.computePathLength("M0,0 C0,50 100,50 100,0");
      assertEquals(ref, arc, 0.5);
   }

   @Test
   void quadArcLengthExceedsChord() {
      // A bulging quadratic from (0,0) to (100,0). Chord = 100; arc must be measurably longer.
      String d = "M0,0 Q50,50 100,0";
      double arc = SVGAnimationInjector.computePathLength(d);
      double chord = Math.hypot(100, 0);
      assertTrue(arc > chord + 5,
                 "quadratic arc length (" + arc + ") should exceed the chord (" + chord + ")");
   }

   @Test
   void quadArcLengthMatchesFineFlattening() {
      // Compare against a much finer manual flattening of the same quadratic.
      double x0 = 0, y0 = 0, x1 = 50, y1 = 50, x2 = 100, y2 = 0;
      double ref = 0, px = x0, py = y0;
      int n = 4000;

      for(int i = 1; i <= n; i++) {
         double t = (double) i / n, u = 1 - t;
         double a = u * u, b = 2 * u * t, c = t * t;
         double nx = a * x0 + b * x1 + c * x2;
         double ny = a * y0 + b * y1 + c * y2;
         ref += Math.hypot(nx - px, ny - py);
         px = nx; py = ny;
      }

      double arc = SVGAnimationInjector.computePathLength("M0,0 Q50,50 100,0");
      assertEquals(ref, arc, 0.5);
   }

   @Test
   void relativeAndAbsoluteCurvesMeasureEqually() {
      // Relative c/q must measure the same arc as their absolute C/Q forms.
      assertEquals(SVGAnimationInjector.computePathLength("M0,0 C0,50 100,50 100,0"),
                   SVGAnimationInjector.computePathLength("M0,0 c0,50 100,50 100,0"), 1e-6);
      assertEquals(SVGAnimationInjector.computePathLength("M0,0 Q50,50 100,0"),
                   SVGAnimationInjector.computePathLength("M0,0 q50,50 100,0"), 1e-6);
   }

   @Test
   void straightLineUnaffectedByCurveHandling() {
      // No curve commands: result is still the exact polyline length.
      double len = SVGAnimationInjector.computePathLength("M10,10 L10,110");
      assertEquals(100.0, len, 1e-6);
   }
}
