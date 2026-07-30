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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorPalettes;
import inetsoft.util.css.CSSDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gated modern default categorical chart palette (visualization Phase 8). Rides the org-scoped
 * modern gate; applied to a render-time color frame only, never serialized. User series colors and
 * a customer format.css ChartPalette rule still win (checked before defaults in
 * CategoricalColorFrame.getColor). The palettes resolve from the Modern/Modern Dark ChartPalette
 * rules in defaults.css (or an org's format.css override), with the head constants below as the
 * fallback when a CSS declaration is missing or incomplete.
 */
public final class VSChartPaletteDefaults {
   private VSChartPaletteDefaults() {
   }

   public static boolean isModern() {
      return VSDensityDefaults.isModern() &&
         !"false".equals(SreeEnv.getProperty("viewsheet.modernChartPalette", false, true));
   }

   public static Color[] modernPalette() {
      return resolve(MODERN_NAME, MODERN_HEAD);
   }

   public static Color[] darkPalette() {
      return resolve(DARK_NAME, DARK_HEAD);
   }

   /**
    * The palette a modern chart renders. Does not check the modern gate; callers do.
    */
   public static Color[] activePalette() {
      return VSDensityDefaults.isDark() ? darkPalette() : modernPalette();
   }

   /**
    * The 40 colors a chart color picker should offer for the current gate state.
    */
   public static Color[] pickerPalette() {
      return isModern()
         ? activePalette()
         : fromFrame(getPaletteSafely(DEFAULT_NAME), CategoricalColorFrame.COLOR_PALETTE);
   }

   public static void applyModernPalette(CategoricalColorFrame frame) {
      if(frame != null && isModern()) {
         frame.setDefaultColors(activePalette());
      }
   }

   /**
    * Head colors followed by the legacy tail, so high-cardinality charts keep 40 distinct
    * colors and do not wrap early.
    */
   static Color[] spliceLegacy(Color[] head) {
      List<Color> palette = new ArrayList<>(Arrays.asList(head));
      Color[] legacy = CategoricalColorFrame.COLOR_PALETTE;
      palette.addAll(Arrays.asList(legacy).subList(head.length, legacy.length));
      return palette.toArray(new Color[0]);
   }

   /**
    * Colors copied out of a palette frame by index, or the legacy splice when the frame is
    * absent, short, or has undeclared holes. Copies rather than aliases - the frame is a shared
    * per-org cached instance.
    */
   static Color[] fromFrame(CategoricalColorFrame frame, Color[] head) {
      if(frame == null) {
         return spliceLegacy(head);
      }

      int count = frame.getColorCount();

      if(count < CategoricalColorFrame.COLOR_PALETTE.length) {
         return spliceLegacy(head);
      }

      Color[] colors = new Color[count];

      for(int i = 0; i < count; i++) {
         colors[i] = frame.getDefaultColor(i);

         if(colors[i] == null) {
            return spliceLegacy(head);
         }
      }

      return colors;
   }

   /**
    * Named palette colors, memoized per org until the CSS changes. ColorPalettes.getPalette
    * locks on its own class, so memoizing keeps concurrent chart renders off that monitor.
    */
   private static Color[] resolve(String name, Color[] head) {
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      long ts = CSSDictionary.getOrgScopedCSSLastModified(CSSDictionary.getDictionary());
      String memoKey = orgID + "|" + name;
      String stamp = orgID + "|" + name + "|" + ts;
      Memo memo = MEMO.get(memoKey);

      if(memo != null && memo.stamp().equals(stamp)) {
         return memo.colors().clone();
      }

      Color[] resolved = fromFrame(getPaletteSafely(name), head);
      MEMO.put(memoKey, new Memo(stamp, resolved));
      return resolved.clone();
   }

   /**
    * A broken or unreadable format.css should fall back to the head constants, not fail the
    * render. ColorPalettes.getPalette can throw (or NPE) when parsing a malformed ChartPalette
    * rule breaks its lazy load for the current org.
    */
   private static CategoricalColorFrame getPaletteSafely(String name) {
      try {
         return ColorPalettes.getPalette(name);
      }
      catch(Exception ex) {
         LOG.debug("Failed to resolve palette " + name, ex);
         return null;
      }
   }

   static void clearMemo() {
      MEMO.clear();
   }

   private record Memo(String stamp, Color[] colors) {
   }

   private static final String MODERN_NAME = "Modern";
   private static final String DARK_NAME = "Modern Dark";
   private static final String DEFAULT_NAME = "Default";
   private static final Map<String, Memo> MEMO = new ConcurrentHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(VSChartPaletteDefaults.class);

   private static final Color[] MODERN_HEAD = {
      new Color(0x00D4E8), new Color(0x00B87A), new Color(0xF59E0B), new Color(0xF43F5E),
      new Color(0x8B5CF6), new Color(0x3B82F6), new Color(0x0D9488), new Color(0x64748B)
   };

   private static final Color[] DARK_HEAD = {
      new Color(0x22D3EE), new Color(0x10B981), new Color(0xFBB724), new Color(0xFB6181),
      new Color(0xA78BFA), new Color(0x60A5FA), new Color(0x2DD4BF), new Color(0x94A3B8)
   };
}
