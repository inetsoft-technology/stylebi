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
    * How thick a bar presenter's bar is on a modern table, px. The signed bar always draws a bar
    * this thick (its 12px box less 4), so the plain bar is set to match. Zero for a classic table,
    * which draws the plain bar the cell height less 4.
    */
   public static int dataBarThickness(VizContext ctx) {
      return ctx != null && ctx.modern ? DATA_BAR_THICKNESS : 0;
   }

   /**
    * The color of the Stars (Counter) presenter's stars on a modern table: the palette's amber
    * (its sixth color), in place of the built-in orange image. Null for a classic table.
    */
   public static Color starColor(VizContext ctx) {
      if(ctx == null || !ctx.modern) {
         return null;
      }

      Color[] palette = VSChartPaletteDefaults.activePalette(ctx);

      // the palette's sixth color is its amber; a palette with fewer colors falls back to it
      return palette.length > STAR_SLOT ? palette[STAR_SLOT] : ctx.dark ? STAR_DARK : STAR;
   }

   /**
    * The fill of a checked box in the Check mark presenter on a modern table: the first palette
    * color over the card, as strong as the data bar, so it is the bar color. Null for a classic
    * table, which keeps the built-in image.
    */
   public static Color checkMarkColor(VizContext ctx) {
      if(ctx == null || !ctx.modern) {
         return null;
      }

      return blend(VSChartPaletteDefaults.activePalette(ctx)[0], BULLET_BAND_STRENGTH[0],
                   cardBackground(ctx));
   }

   /**
    * The border of an empty box in the Check mark presenter on a modern table: the palette's
    * neutral. Null for a classic table.
    */
   public static Color checkBoxBorderColor(VizContext ctx) {
      return ctx != null && ctx.modern ? VSChartPaletteDefaults.directionNeutral(ctx) : null;
   }

   /**
    * The color of the box around the Traffic Light's lights on a modern table: a mid neutral from
    * the text color over the card, so it follows the theme and stays visible on a tinted row (the
    * object border neutral, 1.5:1 on a white card, was nearly invisible). Null for a classic table,
    * which keeps its own box.
    */
   public static Color trafficLightHousingColor(VizContext ctx) {
      return ctx != null && ctx.modern ? blend(ink(ctx), HOUSING_STRENGTH, cardBackground(ctx)) : null;
   }

   /**
    * The default Bullet Graph range bands on a modern table, darkest first: four steps of the
    * first palette color over the card, the family of the data bars, in place of the presenter's
    * faintly purple grey. Null for a classic table.
    */
   public static Color[] bulletBandColors(VizContext ctx) {
      if(ctx == null || !ctx.modern) {
         return null;
      }

      Color first = VSChartPaletteDefaults.activePalette(ctx)[0];
      Color card = cardBackground(ctx);
      Color[] bands = new Color[BULLET_BAND_STRENGTH.length];

      for(int i = 0; i < bands.length; i++) {
         bands[i] = blend(first, BULLET_BAND_STRENGTH[i], card);
      }

      return bands;
   }

   /**
    * The default Bullet Graph measure bar on a modern table: the first palette color deepened with
    * the text color, so it is a dark ink in light mode and a light one in dark mode, and reads
    * against the bands either way. It follows the palette and the text token, so an org's theme
    * carries through. Null for a classic table.
    */
   public static Color bulletBarColor(VizContext ctx) {
      if(ctx == null || !ctx.modern) {
         return null;
      }

      return blend(VSChartPaletteDefaults.activePalette(ctx)[0], BULLET_BAR_STRENGTH, ink(ctx));
   }

   /**
    * The default Bullet Graph target line on a modern table: the measure bar's color, so the
    * target and the bar read as one pair. Null for a classic table.
    */
   public static Color bulletTargetColor(VizContext ctx) {
      return bulletBarColor(ctx);
   }

   /**
    * The default color of the tick in a checked Check mark box on a modern table: the same deep
    * ink as the Bullet Graph's bar. Null for a classic table.
    */
   public static Color checkMarkTickColor(VizContext ctx) {
      return bulletBarColor(ctx);
   }

   /**
    * The default Button face on a modern light table: a soft neutral in place of the classic
    * light grey. Null otherwise: the button's text is always black, so a dark table keeps the
    * light face it is readable on.
    */
   public static Color buttonFaceColor(VizContext ctx) {
      if(ctx == null || !ctx.modern || ctx.dark) {
         return null;
      }

      // a little of the text color over the card, so it follows both
      return blend(ink(ctx), BUTTON_FACE_STRENGTH, cardBackground(ctx));
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
      return ctx != null && ctx.modern && ctx.dark ? ink(ctx) : null;
   }

   /** The text color of the mark's theme: the text token, or the classic dark or light default. */
   private static Color ink(VizContext ctx) {
      return VSChartPaletteDefaults.token(ctx.dark, VSChartPaletteDefaults.TOKEN_TEXT,
                                          ctx.dark ? TEXT_FG_DARK : LEGACY_CELL_FG);
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
   // how much of the text color the Traffic Light's housing has, over the card
   private static final float HOUSING_STRENGTH = 0.55f;
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

   // green, amber, red: the status colors, which stay fixed hues so the light still reads as go,
   // caution and stop. The dark set is the same colors lifted toward white, so it follows them.
   private static final Color[] TRAFFIC_LIGHT = {
      new Color(0x22C55E), new Color(0xF59E0B), new Color(0xEF4444)};
   private static final float TRAFFIC_LIGHT_DARK_LIFT = 0.15f;
   private static final Color[] TRAFFIC_LIGHT_DARK = {
      blend(Color.WHITE, TRAFFIC_LIGHT_DARK_LIFT, TRAFFIC_LIGHT[0]),
      blend(Color.WHITE, TRAFFIC_LIGHT_DARK_LIFT, TRAFFIC_LIGHT[1]),
      blend(Color.WHITE, TRAFFIC_LIGHT_DARK_LIFT, TRAFFIC_LIGHT[2])};
   // how much of the first palette color each of the four range bands has, darkest first
   private static final float[] BULLET_BAND_STRENGTH = {0.55f, 0.40f, 0.26f, 0.14f};
   // how much of the first palette color the Bullet Graph's bar has, against the text color
   private static final float BULLET_BAR_STRENGTH = 0.35f;
   // how much of the text color a Button's face has, over the card
   private static final float BUTTON_FACE_STRENGTH = 0.08f;
   // the signed bar's box is 12px high and the bar is that less 4
   private static final int DATA_BAR_THICKNESS = 8;
   // the palette's amber is its sixth color; these stand in when a palette has fewer colors
   private static final int STAR_SLOT = 5;
   private static final Color STAR = new Color(0xFFB020);
   private static final Color STAR_DARK = new Color(0xFFCB82);
   private static final Color ACTIVE_INDICATOR = new Color(0xBFDDE5);
   private static final Color ACTIVE_INDICATOR_DARK = new Color(0x2DD4BF);
}
