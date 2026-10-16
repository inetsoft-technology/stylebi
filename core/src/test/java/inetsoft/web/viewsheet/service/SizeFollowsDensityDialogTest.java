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
import inetsoft.uql.viewsheet.TextInputVSAssembly;
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
class SizeFollowsDensityDialogTest {
   @BeforeEach
   void density() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private static CurrentSelectionVSAssemblyInfo container(VizMark mark, Dimension size,
                                                           boolean userSize)
   {
      CurrentSelectionVSAssemblyInfo info = new CurrentSelectionVSAssemblyInfo();
      info.setVizMark(mark);
      info.setPixelSize(size);
      info.setUserSize(userSize);
      return info;
   }

   private static SizePositionPaneModel model(Boolean follows, int width, int height) {
      SizePositionPaneModel model = new SizePositionPaneModel();
      model.setSizeFollowsDensity(follows);
      model.setWidth(width);
      model.setHeight(height);
      return model;
   }

   @Test
   void readOffersTheCheckboxForAGovernedMarkedBox() {
      SizePositionPaneModel following = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(
         container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false), following, true);
      assertEquals(Boolean.TRUE, following.getSizeFollowsDensity());

      SizePositionPaneModel authored = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(
         container(VizMark.MODERN_LIGHT, new Dimension(300, 240), true), authored, true);
      assertEquals(Boolean.FALSE, authored.getSizeFollowsDensity());
   }

   @Test
   void readUnticksAnUnflaggedBoxAtASizeTheRuleDoesNotOwn() {
      SizePositionPaneModel box = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(
         container(VizMark.MODERN_LIGHT, new Dimension(400, 300), false), box, true);
      assertEquals(Boolean.FALSE, box.getSizeFollowsDensity());

      SelectionListVSAssemblyInfo list = new SelectionListVSAssemblyInfo();
      list.setVizMark(VizMark.MODERN_LIGHT);
      list.setShowType(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      list.setPixelSize(new Dimension(200, 300));
      SizePositionPaneModel listModel = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(list, listModel, true);
      assertEquals(Boolean.FALSE, listModel.getSizeFollowsDensity());
   }

   @Test
   void readOffersNoCheckboxWhenNotGovernedUnmarkedOrWithoutADensitySize() {
      SizePositionPaneModel notGoverned = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(
         container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false), notGoverned, false);
      assertNull(notGoverned.getSizeFollowsDensity());

      SizePositionPaneModel unmarked = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(
         container(null, new Dimension(300, 240), false), unmarked, true);
      assertNull(unmarked.getSizeFollowsDensity());

      ChartVSAssemblyInfo chart = new ChartVSAssemblyInfo();
      chart.setVizMark(VizMark.MODERN_LIGHT);
      SizePositionPaneModel noDensitySize = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(chart, noDensitySize, true);
      assertNull(noDensitySize.getSizeFollowsDensity());
   }

   // the container dialog then hands this model to setContainerSize, which re-widens its children
   @Test
   void followingWritesTheTierSizeIntoTheBoxAndTheModel() {
      CurrentSelectionVSAssemblyInfo info = container(VizMark.MODERN_LIGHT, new Dimension(400, 500), true);
      SizePositionPaneModel model = model(true, 400, 500);

      VSDialogService.applyDensitySize(info, model);

      assertEquals(new Dimension(300, 360), info.getPixelSize());
      assertFalse(info.isUserSize());
      assertEquals(300, model.getWidth());
      assertEquals(360, model.getHeight());
   }

   @Test
   void followingUsesTheDashboardsOwnDensityPin() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("dense");
      CurrentSelectionVSAssemblyInfo info = container(VizMark.MODERN_LIGHT, new Dimension(400, 500), true);
      info.setViewsheet(vs);
      SizePositionPaneModel model = model(true, 400, 500);

      VSDialogService.applyDensitySize(info, model);

      assertEquals(new Dimension(300, 240), info.getPixelSize());
      assertEquals(240, model.getHeight());
   }

   @Test
   void tickingThroughTheDialogSequenceClearsTheFlag() {
      CurrentSelectionVSAssemblyInfo info = container(VizMark.MODERN_LIGHT, new Dimension(300, 500), true);
      SizePositionPaneModel model = model(true, 300, 500);
      Dimension shown = new Dimension(300, 500);

      VSDialogService.applyDensitySize(info, model);
      VSDialogService.recordAuthorSize(info, model, shown);

      assertFalse(info.isUserSize());
      assertEquals(new Dimension(300, 360), info.getPixelSize());
   }

   @Test
   void notFollowingSetsTheFlagEvenForAnUnchangedSize() {
      CurrentSelectionVSAssemblyInfo info = container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false);

      VSDialogService.recordAuthorSize(info, model(false, 300, 360), new Dimension(300, 360));

      assertTrue(info.isUserSize());
   }

   @Test
   void noAnswerSetsTheFlagOnlyOnAChange() {
      CurrentSelectionVSAssemblyInfo unchanged = container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false);
      VSDialogService.recordAuthorSize(unchanged, model(null, 300, 360), new Dimension(300, 360));
      assertFalse(unchanged.isUserSize(), "applying the dialog for a title change leaves the box following");

      CurrentSelectionVSAssemblyInfo changed = container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false);
      VSDialogService.recordAuthorSize(changed, model(null, 300, 240), new Dimension(300, 360));
      assertTrue(changed.isUserSize());

      CurrentSelectionVSAssemblyInfo wider = container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false);
      VSDialogService.recordAuthorSize(wider, model(null, 280, 360), new Dimension(300, 360));
      assertTrue(wider.isUserSize());
   }

   @Test
   void theHelpersLeaveATypeWithoutADensitySizeAlone() {
      ChartVSAssemblyInfo chart = new ChartVSAssemblyInfo();
      chart.setVizMark(VizMark.MODERN_LIGHT);
      chart.setPixelSize(new Dimension(400, 300));

      VSDialogService.applyDensitySize(chart, model(true, 400, 300));
      VSDialogService.recordAuthorSize(chart, model(false, 500, 300), new Dimension(400, 300));

      assertEquals(new Dimension(400, 300), chart.getPixelSize());
      assertFalse(chart.isUserSize());
   }

   private static TextInputVSAssemblyInfo textInput(Dimension size, boolean userSize) {
      TextInputVSAssemblyInfo info = new TextInputVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(size);
      info.setUserSize(userSize);
      return info;
   }

   @Test
   void readMarksAHeightOnlyType() {
      SizePositionPaneModel model = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(textInput(new Dimension(100, 30), false), model, true);

      assertEquals(Boolean.TRUE, model.getSizeFollowsDensity());
      assertTrue(model.isDensityHeightOnly());

      SizePositionPaneModel box = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(
         container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false), box, true);
      assertFalse(box.isDensityHeightOnly());
   }

   @Test
   void aTickedApplyKeepsATypedWidthOnAHeightOnlyType() {
      TextInputVSAssemblyInfo info = textInput(new Dimension(100, 40), true);
      SizePositionPaneModel model = model(true, 300, 40);

      VSDialogService.applyDensitySize(info, model);

      assertEquals(300, model.getWidth(), "the typed width survives");
      assertEquals(30, model.getHeight());
      assertFalse(info.isUserSize());
   }

   @Test
   void aWidthOnlyEditIsNotTheAuthorsHeight() {
      TextInputVSAssemblyInfo widened = textInput(new Dimension(100, 30), false);
      VSDialogService.recordAuthorSize(widened, model(null, 300, 30), new Dimension(100, 30));
      assertFalse(widened.isUserSize());

      TextInputVSAssemblyInfo taller = textInput(new Dimension(100, 30), false);
      VSDialogService.recordAuthorSize(taller, model(null, 100, 40), new Dimension(100, 30));
      assertTrue(taller.isUserSize());
   }

   @Test
   void theFollowingSetterRunsTheThreeStepsInOrder() {
      Viewsheet vs = new Viewsheet();
      TextInputVSAssembly assembly = new TextInputVSAssembly(vs, "TextInput1");
      vs.addAssembly(assembly);
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(100, 30));
      VSDialogService service = new VSDialogService(null, null);

      service.setAssemblySizeFollowingDensity(info, model(null, 250, 30), vs);
      assertEquals(new Dimension(250, 30), info.getPixelSize());
      assertFalse(info.isUserSize(), "a widened control keeps following");

      service.setAssemblySizeFollowingDensity(info, model(null, 250, 44), vs);
      assertEquals(new Dimension(250, 44), info.getPixelSize());
      assertTrue(info.isUserSize());

      service.setAssemblySizeFollowingDensity(info, model(true, 250, 44), vs);
      assertEquals(new Dimension(250, 30), info.getPixelSize());
      assertFalse(info.isUserSize());
   }
}
