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

import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;

import java.awt.Color;

/**
 * Supplies the dark-mode calendar body defaults (weekday-header + date-cell text and surface) for
 * viewsheet calendars. The MONTH_CALENDAR / YEAR_CALENDAR formats are shared by the weekday-header
 * and the date cells and default to black text with no fill, which is dark-on-dark once the viewsheet
 * goes dark. In dark mode this substitutes a light text + dark surface on the DEFAULT tier of a clone
 * at read time (live model build and export), only when neither the user (USER tier) nor a format.css
 * class (CSS tier) has set the value — so a user format and a format.css class both still win. It is
 * a no-op in light modern and legacy (returns the format unchanged), and must be driven server-side
 * (not via browser CSS) so the exported calendar matches the live view. Applied at both VSCalendarModel
 * and the shared VSCalendar export painter. Mirrors VSTitleChromeDefaults.
 */
public final class VSCalendarChromeDefaults {
   private VSCalendarChromeDefaults() {
   }

   /**
    * Return the calendar body format with the dark neutrals substituted, or the original unchanged
    * (not dark, or already customized). Applied to the DEFAULT tier of a clone, so the stored format
    * is never mutated or serialized and a user (USER tier) or format.css (CSS tier) color still wins.
    */
   public static VSCompositeFormat applyModernDefaults(VSCompositeFormat fmt) {
      if(!VSDensityDefaults.isDark() || fmt == null) {
         return fmt;
      }

      boolean fg = !isForegroundCustomized(fmt);
      boolean bg = !isBackgroundCustomized(fmt);

      if(!fg && !bg) {
         return fmt;
      }

      VSCompositeFormat clone = fmt.clone();
      applyTo(clone.getDefaultFormat(), fg, bg);
      return clone;
   }

   private static void applyTo(VSFormat def, boolean fg, boolean bg) {
      if(fg) {
         def.setForegroundValue(toValue(CELL_FG_DARK));
      }

      if(bg) {
         def.setBackgroundValue(toValue(CELL_BG_DARK));
      }
   }

   // a value counts as customized (and is preserved) only when the user picker (USER tier) or a
   // format.css class (CSS tier) sets it; a bare default — copied down from the object format — is themed
   private static boolean isForegroundCustomized(VSCompositeFormat f) {
      return f.getUserDefinedFormat().isForegroundValueDefined() ||
         f.getCSSFormat().isForegroundValueDefined();
   }

   private static boolean isBackgroundCustomized(VSCompositeFormat f) {
      return f.getUserDefinedFormat().isBackgroundValueDefined() ||
         f.getCSSFormat().isBackgroundValueDefined();
   }

   private static String toValue(Color c) {
      return String.format("0x%06x", c.getRGB() & 0xFFFFFF);
   }

   // dark calendar body: strong light text on the shared card surface (matches the card/table dark family)
   private static final Color CELL_FG_DARK = new Color(0xE6E0E9);
   private static final Color CELL_BG_DARK = new Color(0x252428);
}
