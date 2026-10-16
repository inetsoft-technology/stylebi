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
package inetsoft.web.composer.vs.dialog;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
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
class RangeSliderTitleHeightDialogTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private TimeSliderVSAssemblyInfo slider(VizMark mark) {
      SreeEnv.setProperty("viewsheet.density", "compact");
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(mark);
      return info;
   }

   @Test
   void readShowsTheTierLaneAndOffersTheCheckbox() {
      SizePositionPaneModel model = new SizePositionPaneModel();
      RangeSliderPropertyDialogService.readTitleHeight(slider(VizMark.MODERN_LIGHT), model, true);

      assertEquals(26, model.getTitleHeight());
      assertTrue(model.getTitleHeightFollowsDensity());
   }

   @Test
   void readOffersNoCheckboxOnAnUnmarkedSlider() {
      SizePositionPaneModel model = new SizePositionPaneModel();
      RangeSliderPropertyDialogService.readTitleHeight(slider(null), model, true);

      assertEquals(AssetUtil.defh, model.getTitleHeight());
      assertNull(model.getTitleHeightFollowsDensity());
   }

   @Test
   void readOffersNoCheckboxOutsideASelectionContainer() {
      SizePositionPaneModel model = new SizePositionPaneModel();
      RangeSliderPropertyDialogService.readTitleHeight(slider(VizMark.MODERN_LIGHT), model, false);

      assertEquals(AssetUtil.defh, model.getTitleHeight());
      assertNull(model.getTitleHeightFollowsDensity());
   }

   @Test
   void applyFollowingReturnsAPinnedLaneToTheTier() {
      TimeSliderVSAssemblyInfo info = slider(VizMark.MODERN_LIGHT);
      info.setTitleHeightValue(25);
      info.setUserTitleHeight(true);
      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(25);
      model.setTitleHeightFollowsDensity(true);

      RangeSliderPropertyDialogService.applyTitleHeight(info, model);

      assertFalse(info.isUserTitleHeight());
      assertEquals(AssetUtil.defh, info.getTitleHeightValue());
      assertEquals(26, info.getTitleHeight());
   }

   @Test
   void applyNotFollowingPinsTheSubmittedLane() {
      TimeSliderVSAssemblyInfo info = slider(VizMark.MODERN_LIGHT);
      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(26);
      model.setTitleHeightFollowsDensity(false);

      RangeSliderPropertyDialogService.applyTitleHeight(info, model);

      assertTrue(info.isUserTitleHeight());
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(26, info.getTitleHeight(), "pinned against a density change");
   }

   @Test
   void applyWithoutAFlagPinsOnlyAnEditedLane() {
      TimeSliderVSAssemblyInfo info = slider(null);
      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(AssetUtil.defh);

      RangeSliderPropertyDialogService.applyTitleHeight(info, model);
      assertFalse(info.isUserTitleHeight(), "an untouched lane stays unpinned");

      model.setTitleHeight(26);
      RangeSliderPropertyDialogService.applyTitleHeight(info, model);
      assertTrue(info.isUserTitleHeight());
      assertEquals(26, info.getTitleHeightValue());
   }
}
