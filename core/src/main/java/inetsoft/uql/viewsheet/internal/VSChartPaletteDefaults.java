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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gated modern default categorical chart palette (visualization Phase 8). Rides the org-scoped
 * modern gate; applied to a chart's color frame, whose default colors are serialized as part of
 * the frame (CategoricalColorFrameWrapper.writeContents()/parseContents()). User series colors and
 * a customer format.css ChartPalette rule still win (checked before defaults in
 * CategoricalColorFrame.getColor). The palettes resolve from the Modern/Modern Dark ChartPalette
 * rules in defaults.css (or an org's format.css override), with the head constants below as the
 * fallback when a CSS declaration is missing or incomplete.
 */
public final class VSChartPaletteDefaults {
   private VSChartPaletteDefaults() {
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
   public static Color[] activePalette(VizContext ctx) {
      return ctx.dark ? darkPalette() : modernPalette();
   }

   /**
    * The 40 colors a chart color picker should offer for the current gate state.
    */
   public static Color[] pickerPalette(VizContext ctx) {
      return seedPalette(ctx);
   }

   /**
    * The categorical palette a chart should hold for the given context: total across the mark, so
    * it is safe to write unconditionally at Modernize, Revert, or creation. Never touches a frame
    * itself - callers write the result on, so nothing is clobbered by calling this alone.
    */
   public static Color[] seedPalette(VizContext ctx) {
      return ctx.modern ? activePalette(ctx) : legacyPalette();
   }

   /**
    * The classic 40-color palette, honoring a customer's format.css Default ChartPalette rule
    * (unlike the raw COLOR_PALETTE constant, which does not).
    */
   static Color[] legacyPalette() {
      return fromFrame(getPaletteSafely(DEFAULT_NAME), CategoricalColorFrame.COLOR_PALETTE);
   }

   public static void applyModernPalette(CategoricalColorFrame frame, VizContext ctx) {
      if(frame != null && ctx.modern) {
         frame.setDefaultColors(activePalette(ctx));
      }
   }

   /**
    * Palette names a picker should mark hidden for the given context. A modern chart is not
    * offered the single-hue ramps; a classic chart keeps everything. Never removes a name from
    * resolution - getPalette must still answer for every one of these.
    */
   public static Set<String> hiddenPaletteNames(VizContext ctx) {
      return ctx.modern ? MODERN_HIDDEN : Set.of();
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
   private static final Set<String> MODERN_HIDDEN =
      Set.of("Pastel", "Heat 8", "Heat 16", "Heat 24", "Blue", "Green", "Red", "Orange", "Gray");
   private static final Map<String, Memo> MEMO = new ConcurrentHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(VSChartPaletteDefaults.class);

   private static final Color[] MODERN_HEAD = {
      new Color(0x0490FF), new Color(0xFF5A35), new Color(0x241C4F), new Color(0x03D9B3),
      new Color(0x9A2DDC), new Color(0xFFB020), new Color(0xE5197E), new Color(0x8ED604)
   };

   private static final Color[] DARK_HEAD = {
      new Color(0x4FA5FF), new Color(0xFF8367), new Color(0x49447D), new Color(0x2DEEC6),
      new Color(0xAE41F5), new Color(0xFFCB82), new Color(0xFE3290), new Color(0x9FEB28)
   };
}
