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
package inetsoft.web.viewsheet.service;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.CheckBoxVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
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
class OptionHeightsDialogTest {
   @BeforeEach
   void density() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private static CheckBoxVSAssemblyInfo checkBox(VizMark mark) {
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      info.setVizMark(mark);
      info.setPixelSize(new Dimension(200, 58));
      return info;
   }

   private static SizePositionPaneModel read(CheckBoxVSAssemblyInfo info) {
      SizePositionPaneModel model = new SizePositionPaneModel();
      VSDialogService.readOptionHeights(info, model);
      return model;
   }

   @Test
   void readShowsTheResolvedHeightsAndOffersBothCheckboxes() {
      SizePositionPaneModel model = read(checkBox(VizMark.MODERN_LIGHT));

      assertEquals(30, model.getTitleHeight());
      assertEquals(28, model.getCellHeight());
      assertEquals(Boolean.TRUE, model.getTitleHeightFollowsDensity());
      assertEquals(Boolean.TRUE, model.getCellHeightFollowsDensity());
   }

   @Test
   void readOffersNoCheckboxWhenUnmarked() {
      SizePositionPaneModel model = read(checkBox(null));

      assertEquals(AssetUtil.defh, model.getTitleHeight());
      assertNull(model.getTitleHeightFollowsDensity());
      assertNull(model.getCellHeightFollowsDensity());
   }

   @Test
   void anUntouchedApplyAtComfortableFlagsNothing() {
      CheckBoxVSAssemblyInfo info = checkBox(VizMark.MODERN_LIGHT);
      SizePositionPaneModel model = read(info);
      model.setTitleHeightFollowsDensity(null);
      model.setCellHeightFollowsDensity(null);

      VSDialogService.applyOptionHeights(info, model);

      assertFalse(info.isUserTitleHeight());
      assertFalse(info.isUserCellHeight());
      assertEquals(AssetUtil.defh, info.getTitleHeightValue());
      assertEquals(AssetUtil.defh, info.getCellHeightValue());
   }

   @Test
   void untickingFlagsAndStoresTheTypedValues() {
      CheckBoxVSAssemblyInfo info = checkBox(VizMark.MODERN_LIGHT);
      SizePositionPaneModel model = read(info);
      model.setTitleHeightFollowsDensity(false);
      model.setTitleHeight(34);
      model.setCellHeightFollowsDensity(false);
      model.setCellHeight(32);

      VSDialogService.applyOptionHeights(info, model);

      assertTrue(info.isUserTitleHeight());
      assertEquals(34, info.getTitleHeight());
      assertTrue(info.isUserCellHeight());
      assertEquals(32, info.getCellHeight());
   }

   @Test
   void tickingClearsTheFlagsAndStoresTheLegacyValues() {
      CheckBoxVSAssemblyInfo info = checkBox(VizMark.MODERN_LIGHT);
      info.setTitleHeightValue(34);
      info.setUserTitleHeight(true);
      info.setCellHeight(32);
      info.setUserCellHeight(true);
      SizePositionPaneModel model = read(info);
      model.setTitleHeightFollowsDensity(true);
      model.setCellHeightFollowsDensity(true);

      VSDialogService.applyOptionHeights(info, model);

      assertFalse(info.isUserTitleHeight());
      assertEquals(30, info.getTitleHeight());
      assertFalse(info.isUserCellHeight());
      assertEquals(28, info.getCellHeight());
   }

   @Test
   void anUnmarkedEditIsStoredAndFlagged() {
      CheckBoxVSAssemblyInfo info = checkBox(null);
      SizePositionPaneModel model = read(info);
      model.setCellHeight(25);

      VSDialogService.applyOptionHeights(info, model);

      assertEquals(25, info.getCellHeightValue());
      assertTrue(info.isUserCellHeight());
      assertFalse(info.isUserTitleHeight(), "an untouched title is not flagged");
   }

   private static CheckBoxVSAssemblyInfo inViewsheet(Viewsheet vs, int height, int row) {
      CheckBoxVSAssembly assembly = new CheckBoxVSAssembly(vs, "CheckBox1");
      vs.addAssembly(assembly);
      CheckBoxVSAssemblyInfo info = (CheckBoxVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(200, height));
      info.setCellHeight(row);
      return info;
   }

   private static SizePositionPaneModel readWithSize(CheckBoxVSAssemblyInfo info) {
      SizePositionPaneModel model = read(info);
      VSDialogService.readSizeFollowsDensity(info, model, true);
      model.setWidth(info.getPixelSize().width);
      model.setHeight(info.getPixelSize().height);
      return model;
   }

   @Test
   void anAuthorLaneWithAFollowedBoxSizesTheBoxFromTheNewLane() {
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssemblyInfo info = inViewsheet(vs, 58, 20);
      SizePositionPaneModel model = readWithSize(info);
      model.setTitleHeightFollowsDensity(false);
      model.setTitleHeight(40);

      new VSDialogService(null, null).applyOptionListSize(info, model, vs);

      assertEquals(68, info.getPixelSize().height);
      assertEquals(40, info.getTitleHeight());
   }

   @Test
   void anUntouchedOkOnAFormerBoxLandsOnTheTierBox() {
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssemblyInfo info = inViewsheet(vs, 60, 40);
      SizePositionPaneModel model = readWithSize(info);

      new VSDialogService(null, null).applyOptionListSize(info, model, vs);

      assertEquals(58, info.getPixelSize().height);
      assertEquals(20, info.getCellHeightValue());
      assertEquals(28, info.getCellHeight());
   }

   @Test
   void anUnflaggedAuthorRowReadsUntickedAndAnOkKeepsIt() {
      CheckBoxVSAssemblyInfo info = checkBox(VizMark.MODERN_LIGHT);
      info.setCellHeight(35);
      SizePositionPaneModel model = read(info);

      assertEquals(Boolean.FALSE, model.getCellHeightFollowsDensity());
      assertEquals(35, model.getCellHeight());

      VSDialogService.applyOptionHeights(info, model);

      assertEquals(35, info.getCellHeightValue());
      assertTrue(info.isUserCellHeight());
   }

   @Test
   void anUnflaggedAuthorTitleReadsUntickedAndAnOkKeepsIt() {
      CheckBoxVSAssemblyInfo info = checkBox(VizMark.MODERN_LIGHT);
      info.setTitleHeightValue(35);
      SizePositionPaneModel model = read(info);

      assertEquals(Boolean.FALSE, model.getTitleHeightFollowsDensity());
      assertEquals(35, model.getTitleHeight());

      VSDialogService.applyOptionHeights(info, model);

      assertEquals(35, info.getTitleHeight());
      assertTrue(info.isUserTitleHeight());
   }

   @Test
   void aFormerRowReadsTicked() {
      CheckBoxVSAssemblyInfo info = checkBox(VizMark.MODERN_LIGHT);
      info.setCellHeight(40);

      assertEquals(Boolean.TRUE, read(info).getCellHeightFollowsDensity());
   }

   @Test
   void theRadioDialogReadsTheSameWay() {
      RadioButtonVSAssemblyInfo info = new RadioButtonVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      SizePositionPaneModel model = new SizePositionPaneModel();

      VSDialogService.readOptionHeights(info, model);

      assertEquals(30, model.getTitleHeight());
      assertEquals(28, model.getCellHeight());
      assertEquals(Boolean.TRUE, model.getTitleHeightFollowsDensity());
      assertEquals(Boolean.TRUE, model.getCellHeightFollowsDensity());
   }
}
