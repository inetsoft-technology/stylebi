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
package inetsoft.test;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;

import java.util.function.Supplier;

/**
 * Drives the <code>week.start</code> property from a test. This is the only supported way to
 * vary the first day of week: {@link Tool#getFirstDayOfWeek()} is a pure function of that
 * property and deliberately ignores the thread locale.
 */
public final class WeekStartUtil {
   private WeekStartUtil() {
   }

   /**
    * Run an action with <code>week.start</code> set to the given day name (or an empty
    * string / null for the default), restoring the previous value afterwards.
    */
   public static void withWeekStart(String day, Runnable action) {
      withWeekStart(day, () -> {
         action.run();
         return null;
      });
   }

   /**
    * Evaluate a supplier with <code>week.start</code> set to the given day name.
    */
   public static <T> T withWeekStart(String day, Supplier<T> action) {
      String old = SreeEnv.getProperty("week.start");
      setWeekStart(day);

      try {
         return action.get();
      }
      finally {
         setWeekStart(old);
      }
   }

   private static void setWeekStart(String day) {
      SreeEnv.setProperty("week.start", day == null ? "" : day);
      Tool.clearWeekStartCache();
   }
}
