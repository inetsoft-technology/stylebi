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

import inetsoft.graph.aesthetic.CategoricalColorFrame;

import java.awt.Color;

/**
 * Supplies the modern object-chrome default colors (object-frame border + viewsheet page background)
 * for the VizContext it is handed. Applied as DESIGN-TIME defaults: the seed is written to the
 * assembly's default format at creation (VSAssemblyInfo.setDefaultFormat and
 * ViewsheetVSAssemblyInfo.setDefaultFormat), so an object created modern carries the modern default
 * (visible in the format editor, effective in the viewer and export). A user format or a format.css
 * class still overrides it via the normal USER > CSS > DEFAULT tier precedence.
 */
public final class VSObjectChromeDefaults {
   private VSObjectChromeDefaults() {
   }

   /** Object-frame border default — the shared structural neutral (= --border-default), dark in dark mode. */
   public static Color objectBorderColor(VizContext ctx) {
      return ctx.dark ? OBJECT_BORDER_DARK : OBJECT_BORDER;
   }

   /**
    * Active/current-state indicator — the same modern selection teal used client-side for
    * bd-selected-cell and the composer's selection border (= --inet-viz-selected-border-modern /
    * -dark in _viz-tokens.scss), for a persistent "this is the active one" indicator baked into an
    * assembly's own stored format (e.g. Tab's active-tab border) rather than transient editing
    * chrome. Deliberately distinct from objectBorderColor: reusing the neutral object-border color
    * here would make an active and inactive state read as the same color.
    */
   public static Color activeIndicatorColor(VizContext ctx) {
      return ctx.dark ? ACTIVE_INDICATOR_DARK : ACTIVE_INDICATOR;
   }

   /** Viewsheet page/canvas background default, as a CSS hex string (= --surface-canvas), dark in dark mode. */
   public static String pageBackgroundCss(VizContext ctx) {
      Color bg = ctx.dark ? PAGE_BG_DARK : PAGE_BG;
      return String.format("#%06x", bg.getRGB() & 0xFFFFFF);
   }

   /**
    * Object-card background default as a CSS hex string. White in legacy and light modern (modern
    * keeps white cards); a lifted dark surface in dark mode so light chart/output chrome stays legible
    * on the card (= --dark-surface-default, one step above the darker page).
    */
   public static String cardBackgroundCss(VizContext ctx) {
      Color bg = cardBackground(ctx);
      return String.format("#%06x", bg.getRGB() & 0xFFFFFF);
   }

   /**
    * The default fill of a selection's measure bar. A modern selection takes a tint of the first
    * palette colour over its card: pale enough that it does not read as a chart series, and
    * from the palette so it does not look foreign. Any other selection keeps the classic first
    * colour.
    */
   public static Color dataBarColor(VizContext ctx) {
      if(ctx == null || !ctx.modern) {
         return CategoricalColorFrame.COLOR_PALETTE.get(0);
      }

      return blend(VSChartPaletteDefaults.activePalette(ctx)[0], ctx.dark ? 0.7f : 0.55f,
                   cardBackground(ctx));
   }

   /**
    * The default fill of a data bar below zero. A modern bar takes the palette's down color
    * softened the same way as the positive bar, so the pair reads as one set; any other bar keeps
    * the classic red.
    */
   public static Color dataBarNegativeColor(VizContext ctx) {
      if(ctx == null || !ctx.modern) {
         return CLASSIC_NEGATIVE_BAR;
      }

      return blend(VSChartPaletteDefaults.directionDown(ctx), ctx.dark ? 0.7f : 0.55f,
                   cardBackground(ctx));
   }

   /**
    * The default Traffic Light colors (green, amber, red) on a modern table: status colors with
    * the modern palette's saturation, kept apart from the series colors because a traffic light
    * has to read as go, caution and stop. Null for a classic table, which keeps the presenter's own.
    */
   public static Color[] trafficLightColors(VizContext ctx) {
      if(ctx == null || !ctx.modern) {
         return null;
      }

      return (ctx.dark ? TRAFFIC_LIGHT_DARK : TRAFFIC_LIGHT).clone();
   }

   /**
    * The default Bullet Graph range bands on a modern table, darkest first: a neutral grey ramp
    * instead of the presenter's faintly purple one. Null for a classic table.
    */
   public static Color[] bulletBandColors(VizContext ctx) {
      if(ctx == null || !ctx.modern) {
         return null;
      }

      return (ctx.dark ? BULLET_BANDS_DARK : BULLET_BANDS).clone();
   }

   /**
    * The default Button face on a modern light table: a soft neutral in place of the classic
    * light grey. Null otherwise: the button's text is always black, so a dark table keeps the
    * light face it is readable on.
    */
   public static Color buttonFaceColor(VizContext ctx) {
      return ctx != null && ctx.modern && !ctx.dark ? BUTTON_FACE : null;
   }

   /**
    * The default Shadow face on a dark modern table: the card surface, in place of a white box.
    * Null otherwise: white already fits a light card.
    */
   public static Color shadowShadingColor(VizContext ctx) {
      return ctx != null && ctx.modern && ctx.dark ? cardBackground(ctx) : null;
   }

   /**
    * The default Shadow text on a dark modern table: the dark-theme text color, in place of black.
    * Null otherwise.
    */
   public static Color shadowTextColor(VizContext ctx) {
      return ctx != null && ctx.modern && ctx.dark ? TEXT_FG_DARK : null;
   }

   /** Opaque object-card color: white in light, the lifted surface in dark. */
   public static Color cardBackground(VizContext ctx) {
      return VSChartPaletteDefaults.token(ctx.dark, VSChartPaletteDefaults.TOKEN_CARD,
                                          ctx.dark ? CARD_BG_DARK : CARD_BG);
   }

   /**
    * The fill a spreadsheet writes in place of the card tint: the opaque card for a chart or table,
    * none for any other card, whose dark-mode ink is put back to near-black on white cells.
    */
   public static Color excelCardFill(VSAssemblyInfo info) {
      return info instanceof ChartVSAssemblyInfo || info instanceof TableDataVSAssemblyInfo ?
         cardBackground(VizContext.of(info)) : null;
   }

   /** The card tint as a default-tier background value. */
   public static String cardTintValue() {
      return String.format("#%06x", CARD_TINT_RGB);
   }

   /** The opaque result of the card tint over a backdrop; a null backdrop counts as white. */
   public static Color cardTintOver(Color backdrop) {
      return blend(new Color(CARD_TINT_RGB), CARD_TINT_ALPHA / 100f, backdrop);
   }

   /** The card tint over the page, as the opaque colour a card shows. */
   public static Color cardComposite(VizContext ctx) {
      return cardTintOver(ctx.dark ? PAGE_BG_DARK : PAGE_BG);
   }

   /** The opaque colour a translucent fill shows over a backdrop; a null backdrop is white. */
   public static Color flattenOver(Color fill, Color backdrop) {
      return fill == null || fill.getAlpha() == 255 ? fill :
         blend(fill, fill.getAlpha() / 255f, backdrop);
   }

   private static Color blend(Color fill, float a, Color backdrop) {
      Color base = backdrop == null ? Color.WHITE : backdrop;
      return new Color(Math.round(base.getRed() * (1 - a) + fill.getRed() * a),
                       Math.round(base.getGreen() * (1 - a) + fill.getGreen() * a),
                       Math.round(base.getBlue() * (1 - a) + fill.getBlue() * a));
   }

   /** Object-card corner radius default, in pixels; the top step of the DOM radius scale. */
   public static int cardCornerRadius() {
      return CARD_CORNER_RADIUS;
   }

   /**
    * The dark-mode light text value. Public because the selection-cell and slider seeds write it
    * into a stored DEFAULT tier at creation rather than substituting it at read time.
    */
   public static String darkForegroundValue() {
      Color ink = VSChartPaletteDefaults.token(true, VSChartPaletteDefaults.TOKEN_TEXT,
                                               TEXT_FG_DARK);
      return String.format("0x%06x", ink.getRGB() & 0xFFFFFF);
   }

   /**
    * The cell foreground a gate-off creation writes, and what the legacy branch of the seed
    * restores. Excel's list and tree exporters substitute it back over a seeded dark value: their
    * cells are unfilled white, so the light neutral would be invisible there.
    */
   public static String legacyCellForegroundValue() {
      return String.format("0x%06x", LEGACY_CELL_FG.getRGB() & 0xFFFFFF);
   }

   /** Card tint background RGB, written to the default tier with {@link #CARD_TINT_ALPHA}. */
   public static final int CARD_TINT_RGB = 0xAAAAAA;
   /** Card tint alpha value, in percent. */
   public static final int CARD_TINT_ALPHA = 15;

   // modern warm-neutral object chrome (light mode)
   private static final Color OBJECT_BORDER = new Color(0xD9D5CC);
   private static final Color PAGE_BG = new Color(0xF8F7F4);
   private static final Color CARD_BG = new Color(0xFFFFFF);

   // dark object chrome; page = --dark-surface-canvas, card = --dark-surface-default, border = --dark-border-default
   private static final Color OBJECT_BORDER_DARK = new Color(0x49454F);
   private static final Color PAGE_BG_DARK = new Color(0x1C1B1F);
   private static final Color CARD_BG_DARK = new Color(0x252428);
   // dark object text = strong light neutral (matches table body / calendar text)
   private static final Color TEXT_FG_DARK = new Color(0xE6E0E9);
   // the selection cell's creation default, and Excel's deliberate exception
   private static final Color LEGACY_CELL_FG = new Color(0x2b2b2b);

   // modern object-card corner radius, px; = --inet-radius-xl, the DOM scale's top step
   private static final int CARD_CORNER_RADIUS = 6;

   // modern selection teal, mirroring --inet-viz-selected-border-modern/-dark in _viz-tokens.scss
   private static final Color CLASSIC_NEGATIVE_BAR = new Color(0xFF4040);

   // green, amber, red
   private static final Color[] TRAFFIC_LIGHT = {
      new Color(0x22C55E), new Color(0xF59E0B), new Color(0xEF4444)};
   private static final Color[] TRAFFIC_LIGHT_DARK = {
      new Color(0x34D874), new Color(0xFBBF24), new Color(0xF87171)};
   // the four range bands, darkest first
   private static final Color[] BULLET_BANDS = {
      new Color(0x8C8F94), new Color(0xAEB1B6), new Color(0xCFD1D5), new Color(0xEBECEE)};
   private static final Color[] BULLET_BANDS_DARK = {
      new Color(0x505359), new Color(0x686B72), new Color(0x83868D), new Color(0x9EA1A7)};
   private static final Color BUTTON_FACE = new Color(0xE9EBEF);
   private static final Color ACTIVE_INDICATOR = new Color(0xBFDDE5);
   private static final Color ACTIVE_INDICATOR_DARK = new Color(0x2DD4BF);
}
