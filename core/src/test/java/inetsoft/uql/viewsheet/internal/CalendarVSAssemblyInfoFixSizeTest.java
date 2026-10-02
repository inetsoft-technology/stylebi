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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Behavior of the shared CalendarVSAssemblyInfo.fixCalendarSize() for a calendar that
 * is not inside a tab container (Bug #77372 added a title-height floor to it).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalendarVSAssemblyInfoFixSizeTest {
   @Test
   void fitCalendarHeightToTitleKeepsHeightWhenTitleIsShorter() {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setTitleHeightValue(50);

      assertEquals(300, info.fitCalendarHeightToTitle(300));
      // a title just below the height still leaves a (small) body, keep it
      assertEquals(300, calendarWithTitle(299).fitCalendarHeightToTitle(300));
   }

   @Test
   void fitCalendarHeightToTitleGrowsWhenTitleFillsHeight() {
      int body = CalendarVSAssemblyInfo.CALENDAR_BODY_HEIGHT;

      assertEquals(144, body);
      assertEquals(300 + body, calendarWithTitle(300).fitCalendarHeightToTitle(300));
      assertEquals(310 + body, calendarWithTitle(310).fitCalendarHeightToTitle(300));
   }

   @Test
   void fixCalendarSizeIsNoOpWithoutScriptOrTypeChange() {
      CalendarVSAssemblyInfo info = calendarWithTitle(310);
      info.setPixelSize(new Dimension(210, 100));

      info.fixCalendarSize();

      assertEquals(new Dimension(210, 100), info.getPixelSize());
   }

   @Test
   void fixCalendarSizeUsesDefaultHeightWhenTitleIsShorter() {
      CalendarVSAssemblyInfo info = calendarWithTitle(20);
      info.setPixelSize(new Dimension(210, 300));
      info.setShowType(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE);

      info.fixCalendarSize();

      assertEquals(new Dimension(210, CalendarVSAssemblyInfo.DEFAULT_CALENDAR_HEIGHT),
                   info.getPixelSize());
   }

   @Test
   void fixCalendarSizeGrowsWhenTitleFillsDefaultHeight() {
      CalendarVSAssemblyInfo info = calendarWithTitle(310);
      info.setPixelSize(new Dimension(210, 300));
      info.setShowType(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE);

      info.fixCalendarSize();

      assertEquals(new Dimension(210, 454), info.getPixelSize());
   }

   @Test
   void fixCalendarSizeDropdownIgnoresTitleHeight() {
      CalendarVSAssemblyInfo info = calendarWithTitle(310);
      info.setPixelSize(new Dimension(210, 300));
      info.setShowType(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      info.fixCalendarSize();

      assertEquals(new Dimension(210, CalendarVSAssemblyInfo.DEFAULT_CALENDAR_ROW_HEIGHT),
                   info.getPixelSize());
   }

   @Test
   void writeXmlRoundTripKeepsGrownSize() throws Exception {
      CalendarVSAssemblyInfo info = calendarWithTitle(310);
      info.setPixelSize(new Dimension(210, 300));
      info.setShowType(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE);
      // the property dialog applies the size before the sheet is saved; writeAttributes()
      // prints the pixel size (super) before it re-runs fixCalendarSize()
      info.fixCalendarSize();

      CalendarVSAssemblyInfo reloaded = roundTrip(info);

      assertEquals(new Dimension(210, 454), info.getPixelSize());
      assertEquals(new Dimension(210, 454), reloaded.getPixelSize());
      assertEquals(310, reloaded.getTitleHeightValue());
   }

   @Test
   void writeXmlRoundTripKeepsUserSizeWhenShowTypeNotScripted() throws Exception {
      CalendarVSAssemblyInfo info = calendarWithTitle(50);
      info.setPixelSize(new Dimension(210, 300));

      CalendarVSAssemblyInfo reloaded = roundTrip(info);

      assertEquals(new Dimension(210, 300), info.getPixelSize());
      assertEquals(new Dimension(210, 300), reloaded.getPixelSize());
   }

   @Test
   void calendarBodyHeightMatchesFrontend() throws Exception {
      // the frontend VSUtil.CALENDAR_BODY_HEIGHT is CALENDAR_ROW_HEIGHT * CALENDAR_BODY_ROWS
      // tests run in core/target/test-workdir, resolve from the core module directory
      Path file = Paths.get(System.getProperty("basedir", "."), "..", "web", "projects",
                            "portal", "src", "app", "vsobjects", "util", "vs-util.ts");
      assumeTrue(Files.isRegularFile(file), "frontend sources not available: " + file);
      String source = Files.readString(file, StandardCharsets.UTF_8);

      assertEquals(CalendarVSAssemblyInfo.CALENDAR_BODY_HEIGHT,
                   tsConstant(source, "CALENDAR_ROW_HEIGHT") *
                   tsConstant(source, "CALENDAR_BODY_ROWS"));
   }

   private static int tsConstant(String source, String name) {
      Matcher matcher = Pattern.compile("export const " + name + " = (\\d+);").matcher(source);
      assertTrue(matcher.find(), name + " not found in vs-util.ts");
      return Integer.parseInt(matcher.group(1));
   }

   private static CalendarVSAssemblyInfo calendarWithTitle(int titleHeight) {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setShowTypeValue(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE);
      info.setTitleHeightValue(titleHeight);
      return info;
   }

   private static CalendarVSAssemblyInfo roundTrip(CalendarVSAssemblyInfo info)
      throws Exception
   {
      StringWriter sw = new StringWriter();
      info.writeXML(new PrintWriter(sw));
      Document doc = Tool.parseXML(
         new ByteArrayInputStream(sw.toString().getBytes(StandardCharsets.UTF_8)), "UTF-8");
      CalendarVSAssemblyInfo reloaded = new CalendarVSAssemblyInfo();
      reloaded.parseXML(Tool.getFirstElement(doc));
      return reloaded;
   }
}
