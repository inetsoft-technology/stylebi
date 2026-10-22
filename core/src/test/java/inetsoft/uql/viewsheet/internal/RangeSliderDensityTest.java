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
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RangeSliderDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private static TimeSliderVSAssemblyInfo marked(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.initDefaultFormat();
      return info;
   }

   @ParameterizedTest
   @CsvSource({ "comfortable, 16", "compact, 12", "dense, 8" })
   void aMarkedSliderTakesTheCardPadding(String density, int inset) {
      assertEquals(new Insets(inset, inset, inset, inset), marked(density).getPadding());
   }

   @Test
   void anUnmarkedSliderHasNoPadding() {
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(null);
      info.initDefaultFormat();
      assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
   }

   @Test
   void anAuthorPaddingSurvivesAReseed() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setPadding(new Insets(0, 0, 0, 0));
      info.setUserPadding(true);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
   }

   @Test
   void revertWritesZeroPaddingAndNoCorner() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.ofTransition(null, null));
      assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
      assertEquals(0, info.getFormat().getDefaultFormat().getRoundCornerValue());
   }

   @Test
   void aMarkedSliderTakesTheCardCorner() {
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   marked("comfortable").getFormat().getDefaultFormat().getRoundCornerValue());
   }

   @ParameterizedTest
   @CsvSource({ "comfortable, 82", "compact, 74", "dense, 66" })
   void aMarkedSlidersBodyIsContentPlusPadding(String density, int body) {
      assertEquals(body, marked(density).getSliderBodyHeight());
   }

   @Test
   void anAuthorPaddingCountsInTheBody() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setPadding(new Insets(0, 16, 0, 16));
      assertEquals(50, info.getSliderBodyHeight());
   }

   @Test
   void anUnmarkedSlidersBodyIsItsListHeight() {
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(null);
      info.setListHeight(2);
      assertEquals(2 * AssetUtil.defh, info.getSliderBodyHeight());
   }

   @Test
   void anAdhocSliderReadsNoPaddingAndNoSpacing() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setAdhocFilter(true);
      assertEquals(new Insets(0, 0, 0, 0), info.getBodyPadding());
      assertFalse(info.takesLabelSpacing());
      info.setAdhocFilter(false);
      assertEquals(new Insets(16, 16, 16, 16), info.getBodyPadding());
      assertTrue(info.takesLabelSpacing());
   }
}
