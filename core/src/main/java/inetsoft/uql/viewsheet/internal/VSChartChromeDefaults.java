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

import inetsoft.graph.internal.GDefaults;

import java.awt.Color;

/**
 * Supplies the modern in-graph chart-chrome palette for viewsheet charts, for the VizContext it is
 * handed: gridline / facet-grid / legend-border colors, and the axis/legend label and title text
 * colors.
 *
 * The gridline and legend-border colors are written to the CSS tier of the descriptor CompositeValues
 * (in CSSChartStyles.apply) as a baseline that the format.css dictionary overrides and a user picker
 * (USER tier) beats; the CSS tier is not serialized, so this behaves as a default without dirtying
 * saved charts. The label/title text colors seed descriptor default formats.
 */
public final class VSChartChromeDefaults {
   private VSChartChromeDefaults() {
   }

   /** Interior gridline / facet-grid color — matches the table gridline so chrome reads as one system. */
   public static Color gridlineColor(VizContext ctx) {
      return ctx.dark ? GRIDLINE_DARK : GRIDLINE;
   }

   /** Legend border color — the same hairline neutral as the gridlines. */
   public static Color legendBorderColor(VizContext ctx) {
      return ctx.dark ? GRIDLINE_DARK : GRIDLINE;
   }

   /** Chrome label text color (axis tick labels, legend content) — quiet muted neutral. */
   public static Color labelColor(VizContext ctx) {
      return ctx.dark ? LABEL_DARK : LABEL;
   }

   /** Chrome title text color (axis titles, legend title) — slightly stronger than labels. */
   public static Color titleColor(VizContext ctx) {
      return ctx.dark ? TITLE_DARK : TITLE;
   }

   /**
    * Legend background default: a dark surface in dark mode so the light legend text stays legible;
    * white otherwise (light modern and legacy unchanged). Seeded only when the context is modern,
    * matching how label/title colors are applied.
    */
   public static Color legendBackground(VizContext ctx) {
      return ctx.dark ? LEGEND_BG_DARK : Color.WHITE;
   }

   /**
    * Resolve an axis-line color: when the context is modern and the color is still the legacy
    * default, substitute the modern gridline neutral so the axis line unifies with the gridlines;
    * otherwise (a customer/user color, or a legacy context) leave it unchanged. Compared against the
    * hardcoded fallback so a format.css or user-picker color, which resolves to something else, is
    * preserved.
    */
   public static Color resolveAxisLineColor(Color current, VizContext ctx) {
      return ctx.modern && GDefaults.DEFAULT_LINE_COLOR.equals(current)
         ? (ctx.dark ? GRIDLINE_DARK : GRIDLINE) : current;
   }

   /**
    * Resolve a gridline color for display: when the context is modern and the color is still the
    * legacy default, substitute the modern gridline neutral; otherwise (a customer/user color, or a
    * legacy context) leave it unchanged. Compared against the hardcoded fallback so a format.css or
    * user-picker color is preserved.
    */
   public static Color resolveGridlineColor(Color current, VizContext ctx) {
      return ctx.modern && GDefaults.DEFAULT_GRIDLINE_COLOR.equals(current)
         ? (ctx.dark ? GRIDLINE_DARK : GRIDLINE) : current;
   }

   /**
    * Resolve a legend-border color for display: modern neutral iff the context is modern and the
    * color is still the legacy default; otherwise unchanged.
    */
   public static Color resolveLegendBorderColor(Color current, VizContext ctx) {
      return ctx.modern && GDefaults.DEFAULT_LINE_COLOR.equals(current)
         ? (ctx.dark ? GRIDLINE_DARK : GRIDLINE) : current;
   }

   /**
    * Resolve the gap between an axis's labels and the plot: the spec value when the context is
    * modern and the descriptor still carries no opinion, otherwise unchanged. Zero is the
    * descriptor's unset marker - DefaultAxis.getLabelGap renders it as 2 - so it doubles as the
    * legacy-default comparison.
    */
   public static int resolveAxisLabelGap(int current, VizContext ctx) {
      return ctx.modern && current == 0 ? AXIS_LABEL_GAP : current;
   }

   /**
    * Resolve the gap between an axis title and what it abuts: the plot-adjacent gap when its own
    * labels are not drawn, its own gap when they are. This is the card's "hidden means zero" rule -
    * each gap belongs to the element on its inner side, so a hidden label band hands its gap to
    * whatever now abuts the plot, and no band degrades to an empty stub.
    *
    * Whether the labels are drawn is a parameter rather than a lookup: the answer is mode-dependent
    * (GraphGenerator reads isMaxModeLabelVisible in max mode) and only the caller knows the mode.
    *
    * Gated on the context as well as on the value: unmarked, a title gap of 0 against a rendered
    * label gap of 2 would move a legacy chart's axis title by 2px whenever its labels are hidden.
    */
   public static int resolveAxisTitleGap(int current, boolean abuttingLabelsDrawn, VizContext ctx) {
      if(!ctx.modern || current != 0) {
         return current;
      }

      return abuttingLabelsDrawn ? AXIS_TITLE_GAP : AXIS_LABEL_GAP;
   }

   /**
    * Resolve the gap between the legend column and the plot. The spec asks for 16px between them and
    * VGraph adds a fixed 2px of its own between the legend area and the content, so the descriptor
    * carries the remaining 14 - the two compose to the specified total. Do not "correct" this to 16
    * without also removing VGraph's constant.
    */
   public static int resolveLegendGap(int current, boolean hasOpinion, VizContext ctx) {
      return ctx.modern && !hasOpinion ? LEGEND_GAP : current;
   }

   // modern warm-neutral chrome. Warmer/subtler than the legacy GDefaults #EEEEEE, and equal to
   // VSTableStructureDefaults.gridlineColor().
   private static final Color GRIDLINE = new Color(0xE8E5DE);
   // label = shell muted text (= table headerForeground); title = shell default text. Quieter than
   // today's GDefaults #4B4B4B label / #2B2B2B title while staying legible.
   private static final Color LABEL = new Color(0x6A685F);
   private static final Color TITLE = new Color(0x35342F);

   // dark chrome; gridline matches the table gridline, label/title lift for on-dark legibility
   private static final Color GRIDLINE_DARK = new Color(0x3A383D);
   private static final Color LABEL_DARK = new Color(0xCAC4D0);
   private static final Color TITLE_DARK = new Color(0xE6E0E9);
   // dark legend panel = --dark-surface-default, so it reads as part of the dark chart card
   private static final Color LEGEND_BG_DARK = new Color(0x252428);

   // modern interior gap scale, px. 4 = --inet-space-2, 8 = --inet-space-4, and the legend's 14 plus
   // VGraph's fixed 2 makes the 16 of --inet-space-6.
   private static final int AXIS_TITLE_GAP = 4;
   private static final int AXIS_LABEL_GAP = 8;
   private static final int LEGEND_GAP = 14;
}
