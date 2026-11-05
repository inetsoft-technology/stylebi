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

import inetsoft.report.TableDataPath;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;

/**
 * Today's creation defaults for the reset to modern defaults: a new instance of an assembly's
 * type, and the copy of its DEFAULT look onto the assembly.
 */
public final class ChromeReset {
   private ChromeReset() {
   }

   /**
    * A new instance of info's type in info's sheet, stamped with the mark, with the defaults a
    * new assembly in info's place gets; null when the type cannot be built.
    */
   public static VSAssemblyInfo freshDefaults(VSAssemblyInfo info, VizMark mark) {
      try {
         VSAssemblyInfo fresh = info.getClass().getDeclaredConstructor().newInstance();
         fresh.setViewsheet(info.getViewsheet());
         fresh.setVizMark(mark);
         fresh.initDefaultFormat();
         applyCreationContext(info, fresh);
         return fresh;
      }
      catch(Exception ex) {
         LOG.debug("Failed to build the defaults of {}", info.getClass().getName(), ex);
         return null;
      }
   }

   // what creation writes after initDefaultFormat
   private static void applyCreationContext(VSAssemblyInfo info, VSAssemblyInfo fresh) {
      if(info instanceof CalendarVSAssemblyInfo calendar) {
         ((CalendarVSAssemblyInfo) fresh).installCreationFormats(calendar.getShowType());
      }

      if(isInSelectionContainer(info)) {
         CurrentSelectionVSAssemblyInfo.setChildBorder(fresh);
      }
   }

   private static boolean isInSelectionContainer(VSAssemblyInfo info) {
      Viewsheet vs = info.getViewsheet();
      Assembly assembly = vs == null || info.getName() == null ? null :
         vs.getAssembly(info.getName());
      return assembly instanceof VSAssembly child &&
         child.getContainer() instanceof CurrentSelectionVSAssembly;
   }

   /**
    * Pin the title to fresh's title font when the object keeps an author font, which a title
    * would otherwise take as its default. Charts clear their object font, so they never need it.
    */
   public static void pinTitleFont(VSAssemblyInfo info, VSAssemblyInfo fresh) {
      VSCompositeFormat object = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH);
      VSCompositeFormat title = info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH);
      VSCompositeFormat freshTitle = fresh == null ? null :
         fresh.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH);
      Font font = freshTitle == null ? null : freshTitle.getDefaultFormat().getFontValue();

      if(object == null || title == null || font == null ||
         !object.getUserDefinedFormat().isFontValueDefined())
      {
         return;
      }

      title.getUserDefinedFormat().setFontValue(font);
      title.getUserDefinedFormat().setFont(font, true);
   }

   /**
    * Copy the look properties of fresh's DEFAULT tiers into info's, path by path. Writes into the
    * composites info holds, so its USER and CSS tiers stay; a path it lacks gets a new composite.
    */
   public static void refreshDefaults(VSAssemblyInfo info, VSAssemblyInfo fresh) {
      if(fresh == null) {
         return;
      }

      FormatInfo formats = info.getFormatInfo();

      for(TableDataPath path : fresh.getFormatInfo().getPaths()) {
         VSCompositeFormat from = fresh.getFormatInfo().getFormat(path);
         VSCompositeFormat to = formats.getFormat(path);

         if(from == null) {
            continue;
         }

         if(to == null) {
            to = new VSCompositeFormat();
            formats.setFormat(path, to);
         }

         for(FormatChromeSlots.Property prop : FormatChromeSlots.Property.values()) {
            copy(from.getDefaultFormat(), to.getDefaultFormat(), prop);
         }
      }
   }

   // the value as a save keeps it; the runtime tier is cleared so it cannot shadow the value
   private static void copy(VSFormat from, VSFormat to, FormatChromeSlots.Property prop) {
      switch(prop) {
      case FOREGROUND:
         to.setForegroundValue(from.getForegroundValue(), from.isForegroundValueDefined());
         to.setForeground(null, false);
         break;
      case BACKGROUND:
         to.setBackgroundValue(from.getBackgroundValue(), from.isBackgroundValueDefined());
         to.setBackground(null, false);
         break;
      case GRADIENT:
         to.setGradientColorValue(saved(from.getGradientColorValue(), from.getGradientColor()),
                                  from.isGradientColorValueDefined());
         to.setGradientColor(null, false);
         break;
      case FONT:
         to.setFontValue(saved(from.getFontValue(), from.getFont()), from.isFontValueDefined());
         to.setFont(null, false);
         break;
      case BORDERS:
         to.setBordersValue(saved(from.getBordersValue(), from.getBorders()),
                            from.isBordersValueDefined());
         to.setBorders(null, false);
         break;
      case BORDER_COLORS:
         to.setBorderColorsValue(saved(from.getBorderColorsValue(), from.getBorderColors()),
                                 from.isBorderColorsValueDefined());
         to.setBorderColors(null, false);
         break;
      case ALPHA:
         to.setAlphaValue(from.getAlphaValue(), from.isAlphaValueDefined());
         break;
      default:
         to.setRoundCornerValue(from.getRoundCornerValue(), from.isRoundCornerValueDefined());
         break;
      }
   }

   // a type that writes only the runtime value saves that one
   private static <T> T saved(T design, T runtime) {
      return design != null ? design : runtime;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ChromeReset.class);
}
