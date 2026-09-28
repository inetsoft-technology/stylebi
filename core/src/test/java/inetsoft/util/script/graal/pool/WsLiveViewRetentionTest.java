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
package inetsoft.util.script.graal.pool;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The frame of a live Java-argument view holds its views weakly (bug #77123, verify B3): an
 * exec that hands Java a new array of objects on every call keeps only the views Java still
 * reaches, not one per call and element until the exec ends; the views Java keeps still get
 * the script's final state and drop their guest value, frame and slot.
 */
@Tag("core")
class WsLiveViewRetentionTest {
   /**
    * Host helpers the scripts call.
    */
   public static final class Helper {
      public double sumV(Object value) {
         List<?> list = (List<?>) value;
         double sum = 0;

         for(int i = 0; i < list.size(); i++) {
            sum += ((Number) ((Map<?, ?>) list.get(i)).get("v")).doubleValue();
         }

         return sum;
      }

      public Object keep(Object value) {
         kept.add(value);
         return value;
      }

      /**
       * @return the frame's view count once the collected views are purged (bounded wait).
       */
      public int settledViews(int bound) throws InterruptedException {
         int views = Integer.MAX_VALUE;

         for(int i = 0; i < 50 && views > bound; i++) {
            System.gc();
            Thread.sleep(20);
            views = WsExecContext.currentFrame().size();
         }

         maxViews = Math.max(maxViews, views);
         return views;
      }

      public long usedMb() throws InterruptedException {
         for(int i = 0; i < 3; i++) {
            System.gc();
            Thread.sleep(50);
         }

         Runtime rt = Runtime.getRuntime();
         return (rt.totalMemory() - rt.freeMemory()) >> 20;
      }

      final List<Object> kept = new ArrayList<>();
      int maxViews;
   }

   @Test
   void anExecPassingANewArrayPerCallKeepsOnlyTheViewsJavaReaches() throws Exception {
      WorksheetScriptEnv env = env();
      Helper h = new Helper();
      env.put("h", h);
      // 3000 calls x (1 array + 50 element views): 153,000 views without the weak hold
      Object r = run(env,
         "var before = h.usedMb(); var t = 0; var peak = 0; " +
         "for (var k = 0; k < 3000; k++) { var a = []; " +
         "  for (var i = 0; i < 50; i++) a.push({v: i}); t += h.sumV(a); " +
         "  if (k % 300 == 0) { h.keep(a); a.push({v: 1000}); } " +
         "  if (k % 1000 == 999) peak = Math.max(peak, h.settledViews(500)); } " +
         "t + '|' + peak + '|' + (h.usedMb() - before)");
      String[] parts = String.valueOf(r).split("\\|");
      assertEquals(3000 * 1225.0, Double.parseDouble(parts[0]));
      int views = Integer.parseInt(parts[1]);
      // the kept arrays (10) and the few views not yet collected
      assertTrue(views <= 500, "views kept by the frame: " + views);
      long growth = Long.parseLong(parts[2]);
      assertTrue(growth < 48, "heap growth in the exec (MB): " + growth);

      // the kept views got the final state (the push after the call), then were dropped
      assertEquals(10, h.kept.size());

      for(Object view : h.kept) {
         List<?> list = (List<?>) view;
         assertEquals(51, list.size(), String.valueOf(list.get(50)));
         assertEquals(1000, ((Map<?, ?>) list.get(50)).get("v"));
         assertFalse(((LiveList) view).attached());
         assertNull(field(view, "frame"), "a kept view pins no frame, slot or thread");
      }

      assertNull(WsExecContext.currentFrame());
   }

   private static Object field(Object o, String name) throws Exception {
      Field f = o.getClass().getDeclaredField(name);
      f.setAccessible(true);
      return f.get(o);
   }
}
