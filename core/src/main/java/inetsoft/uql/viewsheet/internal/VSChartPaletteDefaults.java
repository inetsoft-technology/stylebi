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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Gated modern default categorical chart palette (visualization Phase 8). Rides the org-scoped
 * modern gate; applied to a render-time color frame only, never serialized. User series colors and
 * a customer format.css ChartPalette rule still win (checked before defaults in
 * CategoricalColorFrame.getColor).
 */
public final class VSChartPaletteDefaults {
   private VSChartPaletteDefaults() {
   }

   public static boolean isModern() {
      return VSDensityDefaults.isModern() &&
         !"false".equals(SreeEnv.getProperty("viewsheet.modernChartPalette", false, true));
   }

   public static Color[] modernPalette() {
      // 8 modern head + legacy tail (indices 9..40 unchanged) so high-cardinality
      // charts keep 40 distinct colors and do not wrap early.
      List<Color> palette = new ArrayList<>(Arrays.asList(MODERN_HEAD));
      Color[] legacy = CategoricalColorFrame.COLOR_PALETTE;
      palette.addAll(Arrays.asList(legacy).subList(MODERN_HEAD.length, legacy.length));
      return palette.toArray(new Color[0]);
   }

   public static void applyModernPalette(CategoricalColorFrame frame) {
      if(frame != null && isModern()) {
         frame.setDefaultColors(modernPalette());
      }
   }

   private static final Color[] MODERN_HEAD = {
      new Color(0x00D4E8), new Color(0x00B87A), new Color(0xF59E0B), new Color(0xF43F5E),
      new Color(0x8B5CF6), new Color(0x3B82F6), new Color(0x0D9488), new Color(0x64748B)
   };
}
