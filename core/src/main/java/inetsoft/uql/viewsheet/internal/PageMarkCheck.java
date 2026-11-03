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

import java.awt.Color;

/**
 * Whether a change to a dashboard's page background contradicts its mark, so the composer can
 * offer to switch the mark. Nothing is stored: each check compares one change's old and new colour.
 */
public final class PageMarkCheck {
   private PageMarkCheck() {
   }

   /**
    * The WCAG relative luminance at which black and white text have equal contrast. A page below
    * it reads better with light ink.
    */
   public static final double DARK_PAGE_LUMINANCE = 0.179;

   /**
    * Whether a page of this colour counts as dark. Alpha is ignored.
    */
   public static boolean isDark(Color c) {
      return luminance(c) < DARK_PAGE_LUMINANCE;
   }

   /**
    * The mark to offer for a page background change from before to after, or null when the change
    * does not cross the threshold against the mark. A legacy sheet or an unresolved colour is never
    * offered anything.
    */
   public static VizMark offer(Color before, Color after, VizMark mark) {
      if(before == null || after == null || mark == null) {
         return null;
      }

      boolean wasDark = isDark(before);
      boolean nowDark = isDark(after);

      if(mark == VizMark.MODERN_LIGHT && !wasDark && nowDark) {
         return VizMark.MODERN_DARK;
      }

      if(mark == VizMark.MODERN_DARK && wasDark && !nowDark) {
         return VizMark.MODERN_LIGHT;
      }

      return null;
   }

   // own copy, not GTool's: loading GTool initializes the report renderer
   private static double luminance(Color c) {
      return 0.2126 * linear(c.getRed()) + 0.7152 * linear(c.getGreen()) +
         0.0722 * linear(c.getBlue());
   }

   private static double linear(int channel) {
      double c = channel / 255.0;
      return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
   }
}
