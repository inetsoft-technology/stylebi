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
package inetsoft.graph.treemap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shared fixtures and assertions for the treemap layout tests.
 */
final class TreemapTestSupport {
   private TreemapTestSupport() {
   }

   /** Absolute tolerance used for rectangle coordinates. */
   static final double EPS = 1e-9;

   /** Items with the given sizes, order = index, default bounds. */
   static MapItem[] items(double... sizes) {
      MapItem[] items = new MapItem[sizes.length];

      for(int i = 0; i < sizes.length; i++) {
         items[i] = new MapItem(sizes[i], i);
      }

      return items;
   }

   static MapModel model(Mappable[] items) {
      return () -> items;
   }

   static void assertRect(String msg, double x, double y, double w, double h, Rect actual) {
      assertRect(msg, x, y, w, h, actual, EPS);
   }

   static void assertRect(String msg, double x, double y, double w, double h, Rect actual,
                          double eps)
   {
      assertNotNull(actual, msg);
      assertEquals(x, actual.x, eps, msg + " x: " + actual);
      assertEquals(y, actual.y, eps, msg + " y: " + actual);
      assertEquals(w, actual.w, eps, msg + " w: " + actual);
      assertEquals(h, actual.h, eps, msg + " h: " + actual);
   }

   static void assertRect(String msg, double x, double y, double w, double h, Mappable item) {
      assertRect(msg, x, y, w, h, item.getBounds(), EPS);
   }

   /** Assert items[i] has rects[i] = {x, y, w, h}. */
   static void assertRects(Mappable[] items, double[]... rects) {
      assertEquals(rects.length, items.length);

      for(int i = 0; i < items.length; i++) {
         assertRect("item " + i, rects[i][0], rects[i][1], rects[i][2], rects[i][3], items[i]);
      }
   }

   static void assertFinite(Mappable[] items) {
      for(int i = 0; i < items.length; i++) {
         Rect r = items[i].getBounds();
         assertTrue(Double.isFinite(r.x) && Double.isFinite(r.y) && Double.isFinite(r.w) &&
                    Double.isFinite(r.h), "item " + i + " not finite: " + r);
      }
   }

   static double[] snapshot(Mappable[] items) {
      double[] s = new double[items.length * 4];

      for(int i = 0; i < items.length; i++) {
         Rect r = items[i].getBounds();
         s[i * 4] = r.x;
         s[i * 4 + 1] = r.y;
         s[i * 4 + 2] = r.w;
         s[i * 4 + 3] = r.h;
      }

      return s;
   }
}
