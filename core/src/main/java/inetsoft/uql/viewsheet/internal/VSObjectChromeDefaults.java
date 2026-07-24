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

import inetsoft.sree.SreeEnv;

import java.awt.Color;

/**
 * Supplies the modern object-chrome default colors (object-frame border + viewsheet page background),
 * gated by the org modern-visualization setting. Applied as DESIGN-TIME defaults: the gated seed is
 * written to the assembly's default format at creation (VSAssemblyInfo.setDefaultFormat and
 * ViewsheetVSAssemblyInfo.setDefaultFormat), so a new object created under the gate carries the modern
 * default (visible in the format editor, effective in the viewer and export). A user format or a
 * format.css class still overrides it via the normal USER > CSS > DEFAULT tier precedence. Shares the
 * viewsheet.modernObjectChrome toggle with VSTitleChromeDefaults so object chrome modernizes together.
 */
public final class VSObjectChromeDefaults {
   private VSObjectChromeDefaults() {
   }

   /**
    * Whether modern object chrome is active: the modern-visualization gate plus its chrome toggle,
    * which defaults on when modern is enabled.
    */
   public static boolean isModern() {
      return VSDensityDefaults.isModern() &&
         !"false".equals(SreeEnv.getProperty("viewsheet.modernObjectChrome", false, true));
   }

   /** Object-frame border default — the shared modern structural neutral (= --border-default). */
   public static Color objectBorderColor() {
      return OBJECT_BORDER;
   }

   /** Viewsheet page/canvas background default, as a CSS hex string (= --surface-canvas). */
   public static String pageBackgroundCss() {
      return String.format("#%06x", PAGE_BG.getRGB() & 0xFFFFFF);
   }

   // modern warm-neutral object chrome; light mode only, dark deferred.
   private static final Color OBJECT_BORDER = new Color(0xD9D5CC);
   private static final Color PAGE_BG = new Color(0xF8F7F4);
}
