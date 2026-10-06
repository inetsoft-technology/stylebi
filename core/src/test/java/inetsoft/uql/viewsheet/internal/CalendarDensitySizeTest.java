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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalendarDensitySizeTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private static Dimension sizeAt(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      return VSDensityDefaults.calendarSize(VizContext.of(VizMark.MODERN_LIGHT));
   }

   // created the way the composer creates one: the host's mark is stamped, then initDefaultFormat
   private static CalendarVSAssembly created(String density, VizMark mark) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity(density);
      vs.getVSAssemblyInfo().setVizMark(mark);
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      calendar.initDefaultFormat();
      vs.addAssembly(calendar);
      return calendar;
   }

   private static CalendarVSAssemblyInfo info(CalendarVSAssembly calendar) {
      return (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
   }

   // no viewsheet, so the context takes the org's density
   private static CalendarVSAssemblyInfo marked(String orgDensity, Dimension size) {
      SreeEnv.setProperty("viewsheet.density", orgDensity);
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(size);
      return info;
   }

   @Test
   void eachTierStacksItsRowsUnderTheLaneAndBand() {
      assertEquals(new Dimension(300, 332), sizeAt("comfortable"));
      assertEquals(new Dimension(300, 300), sizeAt("compact"));
      assertEquals(new Dimension(300, 266), sizeAt("dense"));
   }

   @Test
   void unmarkedIsTheLegacySize() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(new Dimension(300, 300),
                   VSDensityDefaults.calendarSize(VizContext.of((VizMark) null)));
   }

   // every density matrix falls back to dense for a value it does not know
   @Test
   void anUnrecognizedDensityTakesTheDenseSize() {
      assertEquals(new Dimension(300, 266), sizeAt("spacious"));
   }

   @Test
   void theRecognizerAcceptsOnlySeededSizes() {
      assertTrue(VSDensityDefaults.isSeededCalendarSize(new Dimension(300, 300)));
      assertTrue(VSDensityDefaults.isSeededCalendarSize(new Dimension(300, 332)));
      assertTrue(VSDensityDefaults.isSeededCalendarSize(new Dimension(300, 266)));
      assertFalse(VSDensityDefaults.isSeededCalendarSize(new Dimension(300, 162)));
      assertFalse(VSDensityDefaults.isSeededCalendarSize(new Dimension(600, 300)));
      assertFalse(VSDensityDefaults.isSeededCalendarSize(new Dimension(300, 301)));
      assertFalse(VSDensityDefaults.isSeededCalendarSize(null));
   }

   @Test
   void aNewCalendarTakesItsTiersBox() {
      assertEquals(new Dimension(300, 332),
                   info(created("comfortable", VizMark.MODERN_LIGHT)).getPixelSize());
      assertEquals(new Dimension(300, 300),
                   info(created("compact", VizMark.MODERN_LIGHT)).getPixelSize());
      assertEquals(new Dimension(300, 266),
                   info(created("dense", VizMark.MODERN_LIGHT)).getPixelSize());
   }

   @Test
   void aNewUnmarkedCalendarKeepsTheLegacySize() {
      assertEquals(new Dimension(300, 300), info(created("comfortable", null)).getPixelSize());
   }

   @Test
   void theConstructorSizeIsTheLegacySize() {
      assertEquals(VSDensityDefaults.calendarSize(VizContext.of((VizMark) null)),
                   new CalendarVSAssemblyInfo().getPixelSize());
   }

   @Test
   void theSeedLeavesAnAuthorFlaggedBoxAlone() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(300, 300));
      info.setUserSize(true);

      info.seedDensitySize(VizContext.of(info));

      assertEquals(new Dimension(300, 300), info.getPixelSize());
   }

   @Test
   void theSeedLeavesASizeOffTheRecognizedSetAlone() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(400, 300));

      info.seedDensitySize(VizContext.of(info));

      assertEquals(new Dimension(400, 300), info.getPixelSize());
   }

   @Test
   void theSeedLeavesAnUnmarkedCalendarAlone() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(300, 266));
      info.setVizMark(null);

      info.seedDensitySize(VizContext.of(info));

      assertEquals(new Dimension(300, 266), info.getPixelSize());
   }

   // a dropdown draws only its stored width, so its height can follow harmlessly
   @Test
   void aDropdownIsSizedToo() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(300, 300));
      info.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      info.seedDensitySize(VizContext.of(info));

      assertEquals(new Dimension(300, 332), info.getPixelSize());
   }

   @Test
   void aToggledDoubleWidthLeavesTheRuleAndTogglingBackReturnsIt() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(600, 332));
      assertFalse(info.followsDensitySize());

      info.setPixelSize(new Dimension(300, 332));
      assertTrue(info.followsDensitySize());
   }

   @Test
   void resetSizeReturnsAnAuthorSizeToTheTier() {
      CalendarVSAssemblyInfo info = marked("comfortable", new Dimension(400, 500));
      info.setUserSize(true);
      assertTrue(info.takesDensitySize());

      info.resetSize(VizContext.of(info));

      assertEquals(new Dimension(300, 332), info.getPixelSize());
      assertFalse(info.isUserSize());
   }
}
