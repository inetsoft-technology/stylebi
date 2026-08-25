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
class CellHeightFollowDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void readSendsTheEffectiveCellHeightAndTheFollowFlag() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      SizePositionPaneModel model = new SizePositionPaneModel();
      int cellHeight = info.getEffectiveCellHeight();
      model.setCellHeight(cellHeight <= 0 ? AssetUtil.defh : cellHeight);
      model.setCellHeightFollowsDensity(
         info.getVizMark() == null ? null : !info.isUserCellHeight());

      assertEquals(24, model.getCellHeight(), "the compact cell row");
      assertTrue(model.getCellHeightFollowsDensity());
   }

   @Test
   void applyFollowingResetsToTheLegacyDefaultAndClearsTheFlag() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserCellHeight(true);
      info.setCellHeight(18);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setCellHeightFollowsDensity(true);
      model.setCellHeight(24);

      applyCellHeight(model, info);

      assertFalse(info.isUserCellHeight(), "the flag is cleared");
      assertEquals(AssetUtil.defh, info.getCellHeight(), "the pinned height is discarded");
      assertEquals(24, info.getEffectiveCellHeight(), "the row now resolves the cell");
   }

   @Test
   void tickingTheControlReturnsAPinnedCellHeightToTheDensityRow() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserCellHeight(true);
      info.setCellHeight(18);
      assertEquals(18, info.getEffectiveCellHeight(), "pinned before");

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setCellHeightFollowsDensity(true);
      model.setCellHeight(18);

      applyCellHeight(model, info);

      assertEquals(28, info.getEffectiveCellHeight(), "following the comfortable row after");
   }

   @Test
   void applyNotFollowingPinsTheSubmittedCellHeight() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setCellHeightFollowsDensity(false);
      model.setCellHeight(24);

      applyCellHeight(model, info);

      assertTrue(info.isUserCellHeight(), "the flag is set");
      assertEquals(24, info.getEffectiveCellHeight(), "pinned at the value shown");

      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(24, info.getEffectiveCellHeight(), "pinned against a density change");
   }

   @Test
   void applyLeavesAnUnchangedCellHeightAloneWhenTheFollowFlagIsMissing() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setCellHeight(info.getEffectiveCellHeight());

      applyCellHeight(model, info);

      assertFalse(info.isUserCellHeight(), "unmarked content keeps a clean flag");
      assertEquals(AssetUtil.defh, info.getCellHeight(), "the stored height is unmoved");
   }

   @Test
   void applyStillPinsAChangedCellHeightWhenTheFollowFlagIsMissing() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();

      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setCellHeight(24);

      applyCellHeight(model, info);

      assertTrue(info.isUserCellHeight(), "an edited height pins without the flag");
      assertEquals(24, info.getCellHeight());
   }

   @Test
   void readSendsNoFollowFlagForAnUnmarkedAssembly() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo unmarked = new SelectionListVSAssemblyInfo();
      SelectionListVSAssemblyInfo marked = new SelectionListVSAssemblyInfo();
      marked.setVizMark(VizMark.MODERN_LIGHT);

      SizePositionPaneModel unmarkedModel = new SizePositionPaneModel();
      unmarkedModel.setCellHeightFollowsDensity(
         unmarked.getVizMark() == null ? null : !unmarked.isUserCellHeight());

      SizePositionPaneModel markedModel = new SizePositionPaneModel();
      markedModel.setCellHeightFollowsDensity(
         marked.getVizMark() == null ? null : !marked.isUserCellHeight());

      assertNull(unmarkedModel.getCellHeightFollowsDensity(), "no checkbox on classic content");
      assertNotNull(markedModel.getCellHeightFollowsDensity(), "the checkbox is offered");
   }

   // the rule the two selection services share, in one place
   private void applyCellHeight(SizePositionPaneModel model, SelectionListVSAssemblyInfo info) {
      Boolean follows = model.getCellHeightFollowsDensity();

      if(follows == null) {
         if(model.getCellHeight() != info.getEffectiveCellHeight()) {
            info.setUserCellHeight(true);
            info.setCellHeight(model.getCellHeight());
         }
      }
      else if(follows) {
         info.setUserCellHeight(false);
         info.setCellHeight(AssetUtil.defh);
      }
      else {
         info.setUserCellHeight(true);
         info.setCellHeight(model.getCellHeight());
      }
   }
}
