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
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;

import java.awt.Color;

/**
 * Supplies the modern object-title-bar palette (background / foreground / bottom-border) for titled
 * viewsheet assemblies, gated by the org modern-visualization setting.
 *
 * The title bar is rendered from the server title VSFormat (getFormatInfo().getFormat(TITLEPATH))
 * with no browser CSS hook and is re-drawn in export, so it is server-owned. Unlike chart chrome, the
 * title VSFormat's CSS tier is dictionary-backed (VSCSSFormat reads format.css on demand) and has no
 * writable slot, so the modern default cannot be written to the CSS tier the way VSChartChromeDefaults
 * does. Instead applyModernDefaults substitutes the modern neutral on the DEFAULT tier at read time
 * (live model build, export title draw), only when neither the user (USER tier) nor a format.css TITLE
 * class (CSS tier) has set the value — so a user title format and a customer format.css class both
 * still win. applyModernDefaults returns a clone (never mutates the source); applyModernDefaultsInPlace
 * mutates directly, for the export copy where the viewsheet is already cloned. Mirrors
 * VSChartChromeDefaults' gate.
 */
public final class VSTitleChromeDefaults {
   private VSTitleChromeDefaults() {
   }

   /**
    * Whether modern object chrome is active: the modern-visualization gate plus its chrome toggle,
    * which defaults on when modern is enabled.
    */
   public static boolean isModern() {
      // default on when the modern gate is on; only an explicit "false" opts out (there is no
      // default-value overload on getBooleanProperty, so read the raw property)
      return VSDensityDefaults.isModern() &&
         !"false".equals(SreeEnv.getProperty("viewsheet.modernObjectChrome", false, true));
   }

   /** Title-bar background — quiet warm neutral, equal to the table header background so chrome reads as one system. */
   public static Color titleBackground() {
      return TITLE_BG;
   }

   /** Title-bar text color — muted, equal to the table header / chart label foreground. */
   public static Color titleForeground() {
      return TITLE_FG;
   }

   /** Title→body bottom-border color — the shared structural border (matches the table header rule). */
   public static Color titleBorderColor() {
      return TITLE_BORDER;
   }

   /**
    * Return a title format with the modern neutrals substituted, or the original format unchanged
    * (gate off, or already customized). Applied to the DEFAULT tier of a clone, so the stored format
    * is never mutated or serialized and a user (USER tier) or format.css (CSS tier) title color still
    * wins. Substitution is keyed on whether the user / format.css has set the value — NOT on matching
    * a specific default color — because legacy title backgrounds vary by widget (white, #f5f5f5,
    * transparent); modern mode gives them all one consistent title bar.
    */
   public static VSCompositeFormat applyModernDefaults(VSCompositeFormat titleFmt) {
      if(!isModern() || titleFmt == null) {
         return titleFmt;
      }

      boolean bg = !isBackgroundCustomized(titleFmt);
      boolean fg = !isForegroundCustomized(titleFmt);

      if(!bg && !fg) {
         return titleFmt;
      }

      VSCompositeFormat clone = titleFmt.clone();
      applyTo(clone.getDefaultFormat(), bg, fg);
      return clone;
   }

   /**
    * In-place variant for the export copy (the viewsheet is cloned before export): mutate the title
    * format's DEFAULT tier to the modern neutrals so every per-widget / per-format export title draw
    * resolves the modern chrome from the one shared format rather than needing each draw site wrapped.
    * No-op when the gate is off or the value is already user / format.css customized; never touches a
    * persisted format (export clones upstream).
    */
   public static void applyModernDefaultsInPlace(VSCompositeFormat titleFmt) {
      if(!isModern() || titleFmt == null) {
         return;
      }

      applyTo(titleFmt.getDefaultFormat(),
              !isBackgroundCustomized(titleFmt), !isForegroundCustomized(titleFmt));
   }

   private static void applyTo(VSFormat def, boolean bg, boolean fg) {
      if(bg) {
         def.setBackgroundValue(toValue(TITLE_BG));
      }

      if(fg) {
         def.setForegroundValue(toValue(TITLE_FG));
      }
   }

   // A title color counts as customized (and is preserved) only when the user picker (USER tier) or a
   // format.css TITLE class (CSS tier) sets it; a bare default — of any color — is modernized.
   private static boolean isBackgroundCustomized(VSCompositeFormat f) {
      return f.getUserDefinedFormat().isBackgroundValueDefined() ||
         f.getCSSFormat().isBackgroundValueDefined();
   }

   private static boolean isForegroundCustomized(VSCompositeFormat f) {
      return f.getUserDefinedFormat().isForegroundValueDefined() ||
         f.getCSSFormat().isForegroundValueDefined();
   }

   private static String toValue(Color c) {
      return String.format("0x%06x", c.getRGB() & 0xFFFFFF);
   }

   // modern warm-neutral title chrome; light mode only, dark deferred. Coordinated with
   // VSTableStructureDefaults (header background/foreground/separator) and VSChartChromeDefaults so
   // the title bar, table header, and chart chrome read as one warm-neutral system.
   private static final Color TITLE_BG = new Color(0xF1EFEA);
   private static final Color TITLE_FG = new Color(0x6A685F);
   private static final Color TITLE_BORDER = new Color(0xD9D5CC);
}
