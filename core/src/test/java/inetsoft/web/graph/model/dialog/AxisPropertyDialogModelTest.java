/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.graph.model.dialog;

import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.AxisDescriptor;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class AxisPropertyDialogModelTest {
   @BeforeEach
   void setup() {
      axisPropertyDialogModel = new AxisPropertyDialogModel();
      axisPropertyDialogModel.setLinear(true);

      AliasPaneModel aliasPaneModel = new AliasPaneModel();
      aliasPaneModel.setAliasList(new ModelAlias[0]);
      axisPropertyDialogModel.setAliasPaneModel(aliasPaneModel);

      AxisLabelPaneModel axisLabelPaneModel = new AxisLabelPaneModel();
      axisPropertyDialogModel.setAxisLabelPaneModel(axisLabelPaneModel);

      RotationRadioGroupModel rotationRadioGroupModel = new RotationRadioGroupModel();
      rotationRadioGroupModel.setRotation("0");
      axisLabelPaneModel.setRotationRadioGroupModel(rotationRadioGroupModel);

      AxisLinePaneModel axisLinePaneModel = new AxisLinePaneModel();
      axisPropertyDialogModel.setAxisLinePaneModel(axisLinePaneModel);
   }

   @Test
   void canHaveNullIncrements() throws Exception {
      AxisDescriptor axisDescriptor = new AxisDescriptor();
      axisPropertyDialogModel.updateAxisPropertyDialogModel(axisDescriptor, "", "", false,
                                                            VizContext.ofGate());

      assertNull(axisDescriptor.getMinimum());
      assertNull(axisDescriptor.getMaximum());
      assertNull(axisDescriptor.getIncrement());
      assertNull(axisDescriptor.getMinorIncrement());
   }

   @Test
   void getCorrectFormat() throws Exception {
      AxisDescriptor axisDescriptor = spy(new AxisDescriptor());
      axisPropertyDialogModel.updateAxisPropertyDialogModel(axisDescriptor, null, "", false,
                                                            VizContext.ofGate());
      verify(axisDescriptor, times(2)).getAxisLabelTextFormat();

      axisPropertyDialogModel.updateAxisPropertyDialogModel(axisDescriptor, "column1", "", false,
                                                            VizContext.ofGate());
      verify(axisDescriptor, times(2)).getColumnLabelTextFormat("column1");
   }

   @Test
   void getCorrectRotationFormat() throws Exception {
      String columnName = "column1";
      AxisDescriptor axisDescriptor = new AxisDescriptor();
      CompositeTextFormat cfmt = new CompositeTextFormat();
      cfmt.getUserDefinedFormat().setRotation(45.0);
      axisDescriptor.setColumnLabelTextFormat(columnName, cfmt);

      axisPropertyDialogModel.getAxisLabelPaneModel().getRotationRadioGroupModel().setRotation("auto");
      axisPropertyDialogModel.updateAxisPropertyDialogModel(axisDescriptor, columnName, "", false,
                                                            VizContext.ofGate());
      assertNull(axisDescriptor.getColumnLabelTextFormat(columnName).getUserDefinedFormat().getRotation());
   }

   @Test
   void aLegacyAxisShowsTheTwoPixelsItDraws() {
      assertEquals(2, AxisPropertyDialogModel.shownLabelGap(new AxisDescriptor(), VizContext.LEGACY));
      assertNull(AxisPropertyDialogModel.labelGapFollowsDefault(new AxisDescriptor(), VizContext.LEGACY));
   }

   @Test
   void aStoredAuthorZeroShowsTheTwoPixelsItDraws() {
      // an author's 0 is an opinion, and the engine draws it as 2
      AxisDescriptor axis = new AxisDescriptor();
      axis.setLabelGap(0);

      assertEquals(2, AxisPropertyDialogModel.shownLabelGap(axis, VizContext.LEGACY));
      assertEquals(2, AxisPropertyDialogModel.shownLabelGap(axis, modernAt("compact")));
   }

   @Test
   void aModernAxisShowsTheDashboardDensityDefault() {
      VizContext ctx = modernAt("comfortable");

      assertEquals(12, AxisPropertyDialogModel.shownLabelGap(new AxisDescriptor(), ctx));
      assertEquals(Boolean.TRUE, AxisPropertyDialogModel.labelGapFollowsDefault(new AxisDescriptor(), ctx));
   }

   @Test
   void aStylesheetGapIsShownWithTheBoxTicked() {
      AxisDescriptor axis = new AxisDescriptor();
      axis.setLabelGap(20, CompositeValue.Type.CSS);
      VizContext ctx = modernAt("compact");

      assertEquals(20, AxisPropertyDialogModel.shownLabelGap(axis, ctx));
      assertEquals(Boolean.TRUE, AxisPropertyDialogModel.labelGapFollowsDefault(axis, ctx));
   }

   @Test
   void anAuthorValueUnticksTheBox() {
      AxisDescriptor axis = new AxisDescriptor();
      axis.setLabelGap(5);
      VizContext ctx = modernAt("compact");

      assertEquals(5, AxisPropertyDialogModel.shownLabelGap(axis, ctx));
      assertEquals(Boolean.FALSE, AxisPropertyDialogModel.labelGapFollowsDefault(axis, ctx));
   }

   @Test
   void aLegacyOkWithoutEditsStoresNothing() throws Exception {
      AxisDescriptor axis = new AxisDescriptor();
      AxisLabelPaneModel pane = axisPropertyDialogModel.getAxisLabelPaneModel();
      pane.setLabelGap(AxisPropertyDialogModel.shownLabelGap(axis, VizContext.LEGACY));
      pane.setLabelGapFollowsDefault(null);

      axisPropertyDialogModel.updateAxisPropertyDialogModel(axis, "", "", false, VizContext.LEGACY);

      assertFalse(axis.hasLabelGapUserValue());
   }

   @Test
   void aLegacyEditIsStored() throws Exception {
      AxisDescriptor axis = new AxisDescriptor();
      axisPropertyDialogModel.getAxisLabelPaneModel().setLabelGap(1);

      axisPropertyDialogModel.updateAxisPropertyDialogModel(axis, "", "", false, VizContext.LEGACY);

      assertEquals(1, axis.getLabelGap());
   }

   @Test
   void tickingTheBoxHandsTheGapBackToTheStylesheet() throws Exception {
      AxisDescriptor axis = new AxisDescriptor();
      axis.setLabelGap(20, CompositeValue.Type.CSS);
      axis.setLabelGap(6);
      axisPropertyDialogModel.getAxisLabelPaneModel().setLabelGapFollowsDefault(true);

      axisPropertyDialogModel.updateAxisPropertyDialogModel(axis, "", "", false, modernAt("compact"));

      assertFalse(axis.hasLabelGapUserValue());
      assertEquals(20, axis.getLabelGap());
   }

   @Test
   void untickingStoresTheShownValue() throws Exception {
      AxisDescriptor axis = new AxisDescriptor();
      AxisLabelPaneModel pane = axisPropertyDialogModel.getAxisLabelPaneModel();
      pane.setLabelGap(8);
      pane.setLabelGapFollowsDefault(false);

      axisPropertyDialogModel.updateAxisPropertyDialogModel(axis, "", "", false, modernAt("compact"));

      assertTrue(axis.hasLabelGapUserValue());
      assertEquals(8, axis.getLabelGap());
   }

   @Test
   void anUnchangedLineDoesNotStampTheFlag() throws Exception {
      AxisDescriptor shownOn = new AxisDescriptor();
      applyLine(shownOn, true, false, VizContext.LEGACY);

      assertTrue(shownOn.isLineVisible());
      assertFalse(shownOn.isUserLineVisible(), "a line shown on");

      // a modern value axis the seed turned off
      AxisDescriptor seededOff = new AxisDescriptor();
      seededOff.setLineVisible(false);
      applyLine(seededOff, false, false, modernAt("compact"));

      assertFalse(seededOff.isLineVisible());
      assertFalse(seededOff.isUserLineVisible(), "a seeded line shown off");
   }

   @Test
   void turningTheLineOffStampsTheFlag() throws Exception {
      AxisDescriptor axis = new AxisDescriptor();
      applyLine(axis, false, false, VizContext.LEGACY);

      assertFalse(axis.isLineVisible());
      assertTrue(axis.isUserLineVisible());
   }

   @Test
   void aModernMaxModeApplyThatChangesNothingWritesNothing() throws Exception {
      VizContext ctx = modernAt("compact");
      AxisDescriptor axis = seededOffValueAxis();
      assertFalse(axis.isMaxModeLineVisible(ctx), "premise: shown off in max mode");

      applyLine(axis, false, true, ctx);

      assertTrue(axis.isMaxModeLineVisible(), "the stored max-mode line");
      assertFalse(axis.isLineVisible());
      assertFalse(axis.isUserLineVisible());
   }

   @Test
   void turningTheLineOnInModernMaxModeTurnsItOnEverywhere() throws Exception {
      VizContext ctx = modernAt("compact");
      AxisDescriptor axis = seededOffValueAxis();

      applyLine(axis, true, true, ctx);

      assertTrue(axis.isLineVisible());
      assertTrue(axis.isMaxModeLineVisible());
      assertTrue(axis.isUserLineVisible());
      assertTrue(axis.isMaxModeLineVisible(ctx), "shown on in max mode");
   }

   @Test
   void turningTheLineOffInModernMaxModeLeavesNormalModeAlone() throws Exception {
      VizContext ctx = modernAt("compact");
      AxisDescriptor axis = new AxisDescriptor();

      applyLine(axis, false, true, ctx);

      assertFalse(axis.isMaxModeLineVisible());
      assertTrue(axis.isLineVisible());
      assertFalse(axis.isUserLineVisible());
   }

   @Test
   void aClassicMaxModeApplyIsUnchanged() throws Exception {
      AxisDescriptor axis = new AxisDescriptor();

      applyLine(axis, false, true, VizContext.LEGACY);

      assertFalse(axis.isMaxModeLineVisible());
      assertTrue(axis.isLineVisible());
      assertFalse(axis.isUserLineVisible());
   }

   // line off by the seed, max-mode line stored on
   private static AxisDescriptor seededOffValueAxis() {
      AxisDescriptor axis = new AxisDescriptor();
      axis.setLineVisible(false);
      axis.setMaxModeLineVisible(true);
      return axis;
   }

   private void applyLine(AxisDescriptor axis, boolean line, boolean maxMode, VizContext ctx)
      throws Exception
   {
      axisPropertyDialogModel.getAxisLinePaneModel().setShowAxisLine(line);
      axisPropertyDialogModel.updateAxisPropertyDialogModel(axis, "", "", maxMode, ctx);
   }

   /** A marked chart's context on a dashboard of the given density. */
   private static VizContext modernAt(String density) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity(density);
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      return VizContext.of(chart.getVSAssemblyInfo());
   }

   private AxisPropertyDialogModel axisPropertyDialogModel;
}
