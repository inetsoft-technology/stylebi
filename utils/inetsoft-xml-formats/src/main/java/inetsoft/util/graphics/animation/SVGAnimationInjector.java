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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;

/**
 * Static helper utilities used by {@link SVGAnimationDOMInjector} to compute path geometry,
 * build CSS animation style strings, and classify SVG fill colors.
 *
 * <p>All methods operate purely on strings or primitive data with no dependency on any SVG
 * library, making them safe to call from any rendering path.
 */
public final class SVGAnimationInjector {

   private SVGAnimationInjector() {}

   // ---------------------------------------------------------------------------
   // Shared helpers (used by SVGAnimationDOMInjector)
   // ---------------------------------------------------------------------------

   /**
    * Compute the approximate arc length of an SVG path by summing Euclidean distances between
    * successive anchor points.
    */
   static double computePathLength(String d) {
      if(d == null || d.isEmpty()) {
         return 0;
      }

      Pattern tokenPat = Pattern.compile(
         "([MLCQZmlcqz])|(-?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)");
      Matcher m = tokenPat.matcher(d.replace(',', ' '));

      List<String> cmdList = new ArrayList<>();
      List<List<Double>> argsList = new ArrayList<>();
      String curCmd = "M";
      List<Double> curArgs = new ArrayList<>();

      while(m.find()) {
         if(m.group(1) != null) {
            cmdList.add(curCmd);
            argsList.add(curArgs);
            curCmd = m.group(1);
            curArgs = new ArrayList<>();
         }
         else {
            curArgs.add(Double.parseDouble(m.group(2)));
         }
      }

      cmdList.add(curCmd);
      argsList.add(curArgs);

      double totalLen = 0, cx = 0, cy = 0, sx = 0, sy = 0;

      for(int i = 1; i < cmdList.size(); i++) {
         String c = cmdList.get(i);
         List<Double> args = argsList.get(i);

         if("M".equals(c) && args.size() >= 2) {
            cx = args.get(0); cy = args.get(1); sx = cx; sy = cy;
         }
         else if("m".equals(c) && args.size() >= 2) {
            cx += args.get(0); cy += args.get(1); sx = cx; sy = cy;
         }
         else if("L".equals(c)) {
            for(int j = 0; j + 1 < args.size(); j += 2) {
               double nx = args.get(j), ny = args.get(j + 1);
               totalLen += Math.hypot(nx - cx, ny - cy);
               cx = nx; cy = ny;
            }
         }
         else if("l".equals(c)) {
            for(int j = 0; j + 1 < args.size(); j += 2) {
               double dx = args.get(j), dy = args.get(j + 1);
               totalLen += Math.hypot(dx, dy);
               cx += dx; cy += dy;
            }
         }
         else if("C".equals(c)) {
            for(int j = 0; j + 5 < args.size(); j += 6) {
               double ex = args.get(j + 4), ey = args.get(j + 5);
               totalLen += Math.hypot(ex - cx, ey - cy);
               cx = ex; cy = ey;
            }
         }
         else if("Q".equals(c)) {
            for(int j = 0; j + 3 < args.size(); j += 4) {
               double ex = args.get(j + 2), ey = args.get(j + 3);
               totalLen += Math.hypot(ex - cx, ey - cy);
               cx = ex; cy = ey;
            }
         }
         else if("Z".equals(c) || "z".equals(c)) {
            totalLen += Math.hypot(sx - cx, sy - cy);
            cx = sx; cy = sy;
         }
      }

      return totalLen;
   }

   /**
    * Build a fill polygon path from a line path by extending it down to the chart baseline.
    * In the y-flipped local coordinate system used by Batik the x-axis sits at y=0, so the
    * polygon is: {@code M x0 0  L x0 y0  [rest of line]  L xN 0  Z}.
    */
   static String buildFillPolygon(String d) {
      Matcher mFirst = Pattern.compile(
         "M\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)" +
         "[,\\s]+(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)").matcher(d);

      if(!mFirst.find()) {
         return d;
      }

      double x0 = Double.parseDouble(mFirst.group(1));
      double y0 = Double.parseDouble(mFirst.group(2));
      String rest = d.substring(mFirst.end()).trim();

      // Scan all numbers to find the second-to-last, used as the closing X coordinate.
      // Assumes the path consists of M/L commands only (straight-line segments from Batik).
      // For bezier paths (C/Q), the last two numbers would be control-point coordinates,
      // not the endpoint, producing an incorrect polygon. Batik emits straight-line paths
      // for line chart series, so this is safe for the current use case.
      Pattern numPat = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?");
      Matcher nm = numPat.matcher(d);
      List<Double> nums = new ArrayList<>();
      while(nm.find()) nums.add(Double.parseDouble(nm.group()));
      double lastX = nums.size() >= 2 ? nums.get(nums.size() - 2) : x0;

      return String.format(Locale.US, "M%.4f,0 L%.4f,%.4f %s L%.4f,0 Z",
                           x0, x0, y0, rest, lastX);
   }

   static double findBarBaseline(List<double[]> bounds, boolean horizontal) {
      int iMin = horizontal ? 0 : 1;
      int iMax = horizontal ? 2 : 3;

      Map<Long, Integer> freqMin = new HashMap<>();
      Map<Long, Integer> freqMax = new HashMap<>();

      for(double[] b : bounds) {
         freqMin.merge(Math.round(b[iMin]), 1, Integer::sum);
         freqMax.merge(Math.round(b[iMax]), 1, Integer::sum);
      }

      Set<Long> allKeys = new HashSet<>(freqMin.keySet());
      allKeys.addAll(freqMax.keySet());

      long bestKey = 0;
      int bestAbsImbalance = -1;
      int bestTotal = -1;

      for(long key : allKeys) {
         int fMin = freqMin.getOrDefault(key, 0);
         int fMax = freqMax.getOrDefault(key, 0);
         int absImbalance = Math.abs(fMin - fMax);
         int total = fMin + fMax;

         if(absImbalance > bestAbsImbalance ||
            (absImbalance == bestAbsImbalance && total > bestTotal)) {
            bestAbsImbalance = absImbalance;
            bestTotal = total;
            bestKey = key;
         }
      }

      return (double) bestKey;
   }

   static String buildAnimStyle(String origin, String growAnim, double delay) {
      return String.format(Locale.US,
         "transform-box:fill-box;transform-origin:%s;" +
         "animation:%s 1.2s cubic-bezier(0.34,1.4,0.64,1) %.2fs both," +
         "inetsoft-bar-fade 0.45s linear %.2fs both",
         origin, growAnim, delay, delay);
   }

   static String buildFadeStyle(double delay) {
      return String.format(Locale.US,
         "animation:inetsoft-bar-fade 0.8s ease-out %.2fs both", delay);
   }

   static double[] parseBarBounds(String d) {
      if(d == null || d.isEmpty()) {
         return new double[]{0, 0, 0, 0};
      }

      double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
      double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
      Matcher m = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?").matcher(d);
      int idx = 0;

      // Treats every even-indexed number as X and every odd-indexed as Y. This is correct
      // for simple M/L rectangle paths (where numbers strictly alternate x,y). It would
      // produce wrong bounds for paths with arc commands (A rx ry ...) whose first five
      // numbers are arc parameters, not coordinates. Batik emits simple M/L paths for bar
      // shapes, so arc paths are not expected here.
      while(m.find()) {
         double v = Double.parseDouble(m.group());

         if(idx % 2 == 0) {
            minX = Math.min(minX, v);
            maxX = Math.max(maxX, v);
         }
         else {
            minY = Math.min(minY, v);
            maxY = Math.max(maxY, v);
         }

         idx++;
      }

      return minX == Double.MAX_VALUE ? new double[]{0, 0, 0, 0}
                                      : new double[]{minX, minY, maxX, maxY};
   }

}
