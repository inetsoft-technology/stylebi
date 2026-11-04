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

import inetsoft.report.StyleFont;
import inetsoft.report.TableDataPath;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builds the look slots of one format path: foreground, background, gradient, font, borders,
 * border colours, transparency and round corner. Alignment, wrapping and number formats are not
 * slots, so a reset never touches them.
 */
public final class FormatChromeSlots {
   public enum Property {
      FOREGROUND, BACKGROUND, GRADIENT, FONT, BORDERS, BORDER_COLORS, ALPHA, ROUND_CORNER
   }

   private FormatChromeSlots() {
   }

   public static String key(TableDataPath path, Property prop) {
      return "format:" + path.getType() + ":" + path.getLevel() + ":" + path.getIndex() + ":" +
         path.isRow() + ":" + path.isCol() + ":" + String.join("/", path.getPath()) + ":" + prop;
   }

   public static List<ChromeSlot> forPath(TableDataPath path, boolean residueEligible) {
      boolean objectLevel = VSAssemblyInfo.OBJECTPATH.equals(path);
      List<ChromeSlot> slots = new ArrayList<>();

      for(Property prop : Property.values()) {
         slots.add(slot(path, prop, objectLevel, residueEligible));
      }

      return slots;
   }

   private static ChromeSlot slot(TableDataPath path, Property prop, boolean objectLevel,
                                  boolean residueEligible)
   {
      Predicate<VSAssemblyInfo> set = info -> {
         VSFormat user = user(info, path);
         return user != null && isDefined(user, prop);
      };
      Predicate<VSAssemblyInfo> expression = info -> {
         VSFormat user = user(info, path);

         if(user == null || !set.test(info)) {
            return false;
         }

         String value = prop == Property.FOREGROUND ? user.getForegroundValue() :
            prop == Property.BACKGROUND ? user.getBackgroundValue() : null;
         return value != null && (value.startsWith("=") || value.startsWith("$("));
      };
      Function<VSAssemblyInfo, Object> author = info -> {
         VSFormat user = user(info, path);
         return user == null ? null : authorValue(user, prop);
      };
      Function<VSAssemblyInfo, Object> resolved = info -> {
         VSCompositeFormat fmt = info.getFormatInfo().getFormat(path, false);
         return fmt == null ? null : resolvedValue(fmt, prop);
      };
      java.util.function.Consumer<VSAssemblyInfo> clear = info -> {
         VSFormat user = user(info, path);

         if(user != null) {
            clear(user, prop);
         }
      };
      java.util.function.BiConsumer<VSAssemblyInfo, VSAssemblyInfo> reset =
         prop == Property.FONT && !VSAssemblyInfo.TITLEPATH.equals(path)
            ? (info, fresh) -> resetFont(path, info, fresh) : null;

      return ChromeSlot.tiered(key(path, prop), objectLevel, residueEligible, set, expression,
                               author, resolved, clear, reset);
   }

   private static VSFormat user(VSAssemblyInfo info, TableDataPath path) {
      VSCompositeFormat fmt = info.getFormatInfo().getFormat(path);
      return fmt == null ? null : fmt.getUserDefinedFormat();
   }

   private static boolean isDefined(VSFormat user, Property prop) {
      switch(prop) {
      case FOREGROUND:
         return user.isForegroundValueDefined();
      case BACKGROUND:
         return user.isBackgroundValueDefined();
      case GRADIENT:
         return user.isGradientColorValueDefined();
      case FONT:
         return user.isFontValueDefined();
      case BORDERS:
         return user.isBordersValueDefined();
      case BORDER_COLORS:
         return user.isBorderColorsValueDefined();
      case ALPHA:
         return user.isAlphaValueDefined();
      default:
         return user.isRoundCornerValueDefined();
      }
   }

   private static Object authorValue(VSFormat user, Property prop) {
      switch(prop) {
      case FOREGROUND:
         return color(user.getForegroundValue());
      case BACKGROUND:
         return color(user.getBackgroundValue());
      case GRADIENT:
         return user.getGradientColorValue();
      case FONT:
         return user.getFontValue();
      case BORDERS:
         return user.getBordersValue();
      case BORDER_COLORS:
         return user.getBorderColorsValue();
      case ALPHA:
         return user.getAlphaValue();
      default:
         return user.getRoundCornerValue();
      }
   }

   private static Object resolvedValue(VSCompositeFormat fmt, Property prop) {
      switch(prop) {
      case FOREGROUND:
         return color(fmt.getForegroundValue());
      case BACKGROUND:
         return color(fmt.getBackgroundValue());
      case GRADIENT:
         return fmt.getGradientColorValue();
      case FONT:
         return fmt.getFontValue();
      case BORDERS:
         return fmt.getBordersValue();
      case BORDER_COLORS:
         return fmt.getBorderColorsValue();
      case ALPHA:
         return fmt.getAlphaValue();
      default:
         return fmt.getRoundCornerValue();
      }
   }

   private static void clear(VSFormat user, Property prop) {
      switch(prop) {
      case FOREGROUND:
         user.setForegroundValue(null, false);
         break;
      case BACKGROUND:
         user.setBackgroundValue(null, false);
         break;
      case GRADIENT:
         user.setGradientColorValue(null, false);
         break;
      case FONT:
         user.setFontValue(null);
         break;
      case BORDERS:
         user.setBordersValue(null, false);
         break;
      case BORDER_COLORS:
         user.setBorderColorsValue(null, false);
         break;
      case ALPHA:
         user.setAlphaValue(100, false);
         break;
      default:
         user.setRoundCornerValue(0, false);
         break;
      }
   }

   // keeps the author's size and style bits, takes the fresh default family
   private static void resetFont(TableDataPath path, VSAssemblyInfo info, VSAssemblyInfo fresh) {
      VSFormat user = user(info, path);

      if(user == null || !user.isFontValueDefined()) {
         return;
      }

      Font author = user.getFontValue();

      if(author == null) {
         user.setFontValue(null);
         return;
      }

      String family = null;

      if(fresh != null) {
         family = defaultFamily(fresh.getFormatInfo().getFormat(path));

         if(family == null) {
            family = defaultFamily(fresh.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH));
         }
      }

      if(family == null) {
         family = defaultFamily(info.getFormatInfo().getFormat(path));
      }

      if(family == null) {
         return;
      }

      int style = author.getStyle();
      int underline = author instanceof StyleFont ? ((StyleFont) author).getUnderlineStyle() : 0;
      int strike = author instanceof StyleFont ? ((StyleFont) author).getStrikelineStyle() : 0;
      user.setFontValue(new StyleFont(family, style, author.getSize(), underline, strike));
   }

   private static Color color(String value) {
      try {
         return value == null ? null : new Color(Integer.decode(value));
      }
      catch(NumberFormatException ex) {
         return null;
      }
   }

   private static String defaultFamily(VSCompositeFormat fmt) {
      Font font = fmt == null ? null : fmt.getDefaultFormat().getFontValue();
      return font == null ? null :
         StyleFont.isDefaultFont(font) ? StyleFont.DEFAULT_FONT_FAMILY : font.getName();
   }
}
