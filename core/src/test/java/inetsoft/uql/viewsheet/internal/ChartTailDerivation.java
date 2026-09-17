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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rule that produces the Modern and Modern Dark palette tails, slots 9-40.
 *
 * This lives in test sources on purpose. Nothing derives a tail at runtime - the palettes ship
 * literal hexes in defaults.css and VSChartPaletteDefaults, and ChartTailDerivationTest re-derives
 * and compares. Keeping the rule out of main means the frame stays free of the purity, caching and
 * CSS-reachability constraints that a runtime generator would have carried.
 *
 * This rule reproduces the shipped literals under HotSpot's Math.pow on x86-64, verified on
 * Temurin 17 and 21. widestGapMidpoint contains exact ties - bisecting a hue gap of width W
 * produces two halves of width exactly W/2, which compare equal - and a 1-ulp difference in
 * Math.pow upstream, inside OKLab, resolves that tie the other way. A JVM without HotSpot's
 * x86-64 _dpow intrinsic (fdlibm instead) therefore derives a different tail from the same rule.
 * The symptom is ChartTailDerivationTest.shippedTailsMatchTheRule failing with what looks like
 * corrupted constants. Reproduce it with
 * -XX:+UnlockDiagnosticVMOptions -XX:-UseLibmIntrinsic.
 */
final class ChartTailDerivation {
   private ChartTailDerivation() {
   }

   /** The shipping configuration: 32 slots, three rings, 0.12 apart. */
   static Color[] derive(Color[] head) {
      return derive(head, 32, 3, 0.12);
   }

   /**
    * Bisect the widest remaining hue gap, count times, seeded with the head's hues. Each generated
    * hue joins the circle for the next round, so the gaps close evenly.
    *
    * Lightness is the interesting part. A single ring at the mean lightness collapses at 32 slots -
    * hue alone cannot carry forty categories. So there are three rings, and each slot takes
    * whichever ring separates it furthest from everything already placed, head included. Ties
    * break on the interleave order, which keeps the walk deterministic and therefore keeps the
    * shipped literals reproducible.
    */
   static Color[] derive(Color[] head, int count, int rings, double lSpread) {
      List<Double> hues = new ArrayList<>();
      double sumL = 0;
      double sumC = 0;

      for(Color c : head) {
         double[] lch = OKLab.toLCH(OKLab.fromColor(c));
         hues.add(lch[2]);
         sumL += lch[0];
         sumC += lch[1];
      }

      double meanL = sumL / head.length;
      double meanC = sumC / head.length;
      List<Color> out = new ArrayList<>();
      List<Color> placed = new ArrayList<>(List.of(head));

      for(int i = 0; i < count; i++) {
         double mid = widestGapMidpoint(hues);
         Color best = null;
         double bestSeparation = -1;

         for(int r = 0; r < rings; r++) {
            int ringIndex = rings <= 1 ? 0 : (i + r) % rings;
            double l = meanL + (rings <= 1 ? 0 : (ringIndex - (rings - 1) / 2.0) * lSpread);
            Color candidate = OKLab.toColorInGamut(l, meanC, mid);
            double separation = Double.MAX_VALUE;

            for(Color p : placed) {
               separation = Math.min(separation, deltaE(candidate, p));
            }

            if(separation > bestSeparation) {
               bestSeparation = separation;
               best = candidate;
            }
         }

         out.add(best);
         placed.add(best);
         hues.add(mid);
      }

      return out.toArray(new Color[0]);
   }

   /** Euclidean distance in OKLab. */
   static double deltaE(Color a, Color b) {
      double[] x = OKLab.fromColor(a);
      double[] y = OKLab.fromColor(b);

      return Math.sqrt(Math.pow(x[0] - y[0], 2)
                          + Math.pow(x[1] - y[1], 2)
                          + Math.pow(x[2] - y[2], 2));
   }

   private static double widestGapMidpoint(List<Double> hues) {
      List<Double> sorted = new ArrayList<>(hues);
      Collections.sort(sorted);
      double widest = -1;
      double midpoint = 0;

      for(int i = 0; i < sorted.size(); i++) {
         double lo = sorted.get(i);
         double hi = i + 1 < sorted.size() ? sorted.get(i + 1) : sorted.get(0) + 360;

         if(hi - lo > widest) {
            widest = hi - lo;
            midpoint = (lo + hi) / 2 % 360;
         }
      }

      return midpoint;
   }
}
