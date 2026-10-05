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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RangeSliderTitleLaneTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private TimeSliderVSAssemblyInfo slider(String density, VizMark mark) {
      SreeEnv.setProperty("viewsheet.density", density);
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(mark);
      return info;
   }

   @Test
   void aMarkedSliderTakesTheLaneAtEachTier() {
      assertEquals(30, slider("comfortable", VizMark.MODERN_LIGHT).getTitleHeight());
      assertEquals(26, slider("compact", VizMark.MODERN_LIGHT).getTitleHeight());
      assertEquals(20, slider("dense", VizMark.MODERN_LIGHT).getTitleHeight());
   }

   @Test
   void anAuthorLaneIsKept() {
      TimeSliderVSAssemblyInfo info = slider("comfortable", VizMark.MODERN_LIGHT);
      info.setTitleHeightValue(25);
      info.setUserTitleHeight(true);
      assertEquals(25, info.getTitleHeight());
   }

   @Test
   void anUnmarkedSliderKeepsItsStoredLane() {
      assertEquals(AssetUtil.defh, slider("comfortable", null).getTitleHeight());
   }

   // the lane is 30 now, but a slider collapsed before that was stored at 20
   @Test
   void aMarkedSliderIsCollapsedByItsHiddenFlag() {
      TimeSliderVSAssemblyInfo info = slider("comfortable", VizMark.MODERN_LIGHT);
      info.setHidden(true);
      assertTrue(info.isCollapsedInContainer(20));

      info.setHidden(false);
      assertFalse(info.isCollapsedInContainer(30));
   }

   @Test
   void anUnmarkedSliderIsCollapsedByItsStoredHeight() {
      TimeSliderVSAssemblyInfo info = slider("comfortable", null);
      info.setHidden(false);
      assertTrue(info.isCollapsedInContainer(AssetUtil.defh), "the old test, unchanged");
      assertFalse(info.isCollapsedInContainer(60));
   }
}
