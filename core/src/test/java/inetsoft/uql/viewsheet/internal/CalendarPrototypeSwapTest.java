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

import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The calendar's show-type default swap. It installs a static FormatInfo prototype, so it has two
 * failure modes worth pinning: aliasing the static (which corrupts every later calendar in the
 * JVM), and an equality guard that stops matching once the format carries seeded values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalendarPrototypeSwapTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private CalendarVSAssemblyInfo newCalendar() {
      Viewsheet vs = new Viewsheet();
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      calendar.getVSAssemblyInfo().initDefaultFormat();
      return (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
   }

   /** Drive the show-type change through copyViewInfo, the way the property dialog does. */
   private CalendarVSAssemblyInfo swapToDropdown(CalendarVSAssemblyInfo info) {
      CalendarVSAssemblyInfo source = newCalendar();
      source.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.copyInfo(source);
      return info;
   }

   @Test
   void theSwapDoesNotAliasTheStaticPrototype() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      CalendarVSAssemblyInfo info = swapToDropdown(newCalendar());

      // an author edits a format on the swapped calendar
      info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
         .getUserDefinedFormat().setBackgroundValue("0x123456");

      // a calendar created afterwards must not inherit that edit
      CalendarVSAssemblyInfo fresh = swapToDropdown(newCalendar());
      assertNull(fresh.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
                    .getUserDefinedFormat().getBackgroundValue(),
                 "the prototype was aliased: an edit to one calendar reached every later one");
   }

   @Test
   void theSwapStillFiresForAMarkedCalendar() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      CalendarVSAssemblyInfo info = newCalendar();
      assertNotNull(info.getVizMark(), "precondition: the gate is on, so creation marks it");

      String before = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
         .getDefaultFormat().getBackgroundValue();
      swapToDropdown(info);
      String after = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
         .getDefaultFormat().getBackgroundValue();

      assertNotEquals(before, after,
                      "the guard compared against an unseeded prototype and never matched");
      assertEquals("0xffffff", after, "the dropdown prototype's white object fill");
   }

   @Test
   void theSwapStillFiresForAnUnmarkedCalendar() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      CalendarVSAssemblyInfo info = swapToDropdown(newCalendar());

      assertEquals("0xffffff", info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
                      .getDefaultFormat().getBackgroundValue());
   }

   /**
    * Drive a show-type change the way the property dialog does: a clone of the assembly's own info
    * with the new show type set on it, so copyViewInfo sees an equal FormatInfo and keeps this
    * assembly's own rather than the source's. Works in both directions, unlike swapToDropdown.
    */
   private static CalendarVSAssemblyInfo swapShowType(CalendarVSAssemblyInfo info, int showType) {
      CalendarVSAssemblyInfo source = (CalendarVSAssemblyInfo) info.clone();
      source.setShowTypeValue(showType);
      info.copyInfo(source);
      return info;
   }

   private static VSFormat titleDefault(CalendarVSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH).getDefaultFormat();
   }

   /**
    * The two show types carry different titles, and the swap installs a seeded clone: a gate-off
    * seed that wrote the normal title's values would turn the dropdown's boxed white field into a
    * filled band with a single hairline.
    */
   @Test
   void theGateOffDropdownSwapInstallsTheDropdownTitle() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      VSFormat def = titleDefault(
         swapShowType(newCalendar(), CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE));

      assertEquals(new Insets(StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
                              StyleConstants.THIN_LINE, StyleConstants.THIN_LINE),
                   def.getBordersValue(), "the dropdown title is boxed on all four sides");
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, def.getBorderColorsValue().bottomColor);
      assertEquals("0xffffff", def.getBackgroundValue(),
                   "the dropdown title is white, not the legacy title band");
   }

   /** And back: the normal prototype's filled band with its one 0xC0C0C0 hairline. */
   @Test
   void theGateOffSwapBackRestoresTheNormalTitle() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      CalendarVSAssemblyInfo info =
         swapShowType(newCalendar(), CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      VSFormat def =
         titleDefault(swapShowType(info, CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE));

      assertEquals(new Insets(0, 0, StyleConstants.THIN_LINE, 0), def.getBordersValue());
      assertEquals(new Color(0xC0C0C0), def.getBorderColorsValue().bottomColor);
      assertEquals(VSAssemblyInfo.DEFAULT_TITLE_BG, def.getBackgroundValue());
   }

   /**
    * The dropdown prototype stores the object and title paths only, and the gate-off seed must not
    * add to that - the render path synthesises the cell formats from the object format.
    */
   @Test
   void theGateOffDropdownSwapStoresOnlyThePrototypePaths() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      CalendarVSAssemblyInfo info =
         swapShowType(newCalendar(), CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      assertEquals(2, info.getFormatInfo().getPaths().length);
      assertNull(info.getFormatInfo().getFormat(
                    new TableDataPath(-1, TableDataPath.MONTH_CALENDAR)));
   }

   /**
    * With the gate on the seed has nowhere to write unless it installs the cell formats, and a dark
    * dropdown calendar then paints black date cells on the dark card.
    */
   @Test
   void theDarkDropdownSwapSeedsTheCellFormats() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VizContext dark = VizContext.ofGate();
      CalendarVSAssemblyInfo info =
         swapShowType(newCalendar(), CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      assertEquals(cellForeground(VSTableStructureDefaults.bodyForeground(dark)),
                   cellForegroundValue(info, TableDataPath.MONTH_CALENDAR));
      assertEquals(cellForeground(VSTableStructureDefaults.bodyForeground(dark)),
                   cellForegroundValue(info, TableDataPath.YEAR_CALENDAR));
      assertEquals(cellForeground(VSTableStructureDefaults.headerForeground(dark)),
                   cellForegroundValue(info, TableDataPath.CALENDAR_TITLE));
   }

   private static String cellForeground(Color c) {
      return c == null ? null : String.format("0x%06x", c.getRGB() & 0xFFFFFF);
   }

   private static String cellForegroundValue(CalendarVSAssemblyInfo info, int type) {
      VSCompositeFormat fmt = info.getFormatInfo().getFormat(new TableDataPath(-1, type));
      return fmt == null ? null : fmt.getDefaultFormat().getForegroundValue();
   }
}
