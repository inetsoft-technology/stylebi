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
class TitleHeightFollowDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void readSendsTheEffectiveHeightAndTheFollowFlag() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(VSDensityDefaults.titleHeight(info, info.getTitleHeightValue()));
      model.setTitleHeightFollowsDensity(
         info.getVizMark() == null ? null : !info.isUserTitleHeight());

      assertEquals(26, model.getTitleHeight(), "the dialog shows the derived lane");
      assertTrue(model.getTitleHeightFollowsDensity());
   }

   @Test
   void readSendsTheStoredHeightWhenTheAuthorSetIt() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setTitleHeightValue(25);
      info.setUserTitleHeight(true);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(VSDensityDefaults.titleHeight(info, info.getTitleHeightValue()));
      model.setTitleHeightFollowsDensity(
         info.getVizMark() == null ? null : !info.isUserTitleHeight());

      assertEquals(25, model.getTitleHeight());
      assertFalse(model.getTitleHeightFollowsDensity());
   }

   @Test
   void applyFollowingResetsToTheLegacyDefaultAndClearsTheFlag() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserTitleHeight(true);
      info.setTitleHeightValue(25);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeightFollowsDensity(true);
      model.setTitleHeight(26);

      applyTitleHeight(model, info);

      assertFalse(info.isUserTitleHeight(), "the flag is cleared");
      assertEquals(AssetUtil.defh, info.getTitleHeightValue(), "the pinned height is discarded");
      assertEquals(26, info.getTitleHeight(), "the row now resolves the lane");
   }

   @Test
   void tickingTheControlReturnsAPinnedHeightToTheDensityRow() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserTitleHeight(true);
      info.setTitleHeightValue(25);
      assertEquals(25, info.getTitleHeight(), "pinned before");

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeightFollowsDensity(true);
      model.setTitleHeight(25);

      applyTitleHeight(model, info);

      assertEquals(30, info.getTitleHeight(), "following the comfortable lane after");
   }

   @Test
   void applyNotFollowingPinsTheSubmittedHeight() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeightFollowsDensity(false);
      model.setTitleHeight(26);

      applyTitleHeight(model, info);

      assertTrue(info.isUserTitleHeight(), "the flag is set");
      assertEquals(26, info.getTitleHeightValue(), "the displayed value is pinned");
      assertEquals(26, info.getTitleHeight(), "and stays 26 at any density");

      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(26, info.getTitleHeight(), "pinned against a density change");
   }

   @Test
   void applyFollowingOnAnUnmarkedAssemblyLeavesTheLegacyLane() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeightFollowsDensity(true);
      model.setTitleHeight(AssetUtil.defh);

      applyTitleHeight(model, info);

      assertEquals(AssetUtil.defh, info.getTitleHeight(), "unmarked content never moves");
   }

   @Test
   void applyLeavesAnUnchangedHeightAloneWhenTheFollowFlagIsMissing() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(info.getTitleHeightValue());

      applyTitleHeight(model, info);

      assertFalse(info.isUserTitleHeight(), "unmarked content keeps a clean flag");
      assertEquals(AssetUtil.defh, info.getTitleHeightValue(), "the stored height is unmoved");
   }

   @Test
   void applyStillPinsAChangedHeightWhenTheFollowFlagIsMissing() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setTitleHeight(26);

      applyTitleHeight(model, info);

      assertTrue(info.isUserTitleHeight(), "an edited height pins without the flag");
      assertEquals(26, info.getTitleHeightValue());
   }

   @Test
   void readSendsNoFollowFlagForAnUnmarkedAssembly() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo unmarked = new ChartVSAssemblyInfo();
      ChartVSAssemblyInfo marked = new ChartVSAssemblyInfo();
      marked.setVizMark(VizMark.MODERN_LIGHT);

      SizePositionPaneModel unmarkedModel = new SizePositionPaneModel();
      unmarkedModel.setTitleHeightFollowsDensity(
         unmarked.getVizMark() == null ? null : !unmarked.isUserTitleHeight());

      SizePositionPaneModel markedModel = new SizePositionPaneModel();
      markedModel.setTitleHeightFollowsDensity(
         marked.getVizMark() == null ? null : !marked.isUserTitleHeight());

      assertNull(unmarkedModel.getTitleHeightFollowsDensity(), "no checkbox on classic content");
      assertNotNull(markedModel.getTitleHeightFollowsDensity(), "the checkbox is offered");
   }

   // the rule the eight services share, in one place
   private void applyTitleHeight(SizePositionPaneModel model, ChartVSAssemblyInfo info) {
      Boolean follows = model.getTitleHeightFollowsDensity();

      if(follows == null) {
         if(model.getTitleHeight() != info.getTitleHeightValue()) {
            info.setUserTitleHeight(true);
            info.setTitleHeightValue(model.getTitleHeight());
         }
      }
      else if(follows) {
         info.setUserTitleHeight(false);
         info.setTitleHeightValue(info.getLegacyTitleHeight());
      }
      else {
         info.setUserTitleHeight(true);
         info.setTitleHeightValue(model.getTitleHeight());
      }
   }
}
