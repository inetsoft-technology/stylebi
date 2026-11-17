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

import inetsoft.report.StyleConstants;
import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSChartChromeDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.composer.model.vs.PaddingPaneModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
public class LegendFormatDialogModelTest {
   @Test
   void anUnchangedApplyStampsNothing() {
      ChartVSAssemblyInfo info = seededBar();
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      LegendDescriptor legend = legends.getColorLegendDescriptor();
      assertEquals(LegendsDescriptor.TOP, legends.getLayout(), "premise: a seeded top legend");
      assertFalse(legend.isTitleVisible(), "premise: a hidden title");

      apply(info, legends, legend, shown(legends, legend));

      assertFalse(legends.isUserLayout());
      assertFalse(legend.isUserTitleVisible());
      assertEquals(LegendsDescriptor.TOP, legends.getLayout());
      assertFalse(legend.isTitleVisible());
   }

   @Test
   void changingThePositionStampsTheFlag() {
      ChartVSAssemblyInfo info = seededBar();
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      LegendDescriptor legend = legends.getColorLegendDescriptor();
      LegendFormatGeneralPaneModel pane = shown(legends, legend);
      pane.setPosition("Right");

      apply(info, legends, legend, pane);

      assertTrue(legends.isUserLayout());
      assertEquals(LegendsDescriptor.RIGHT, legends.getLayout());
   }

   @Test
   void typingATitleOnATopLegendShowsIt() {
      ChartVSAssemblyInfo info = seededBar();
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      LegendDescriptor legend = legends.getColorLegendDescriptor();
      LegendFormatGeneralPaneModel pane = shown(legends, legend);
      pane.setTitleValue("Region");

      apply(info, legends, legend, pane);

      assertEquals("Region", legend.getTitleValue());
      assertTrue(legend.isTitleVisible());
      assertTrue(legend.isUserTitleVisible());
   }

   @Test
   void aClassicHiddenTitleStaysHiddenWhenItsTextChanges() {
      ChartVSAssemblyInfo info = seededBar();
      info.setVizMark(null);
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      LegendDescriptor legend = legends.getColorLegendDescriptor();
      legend.setAuthorTitleVisible(false);
      LegendFormatGeneralPaneModel pane = shown(legends, legend);
      pane.setTitleValue("Region");
      pane.setVisible(false);

      apply(info, legends, legend, pane);

      assertEquals("Region", legend.getTitleValue());
      assertFalse(legend.isTitleVisible());
   }

   @Test
   void unTickingTheTitleStampsIt() {
      ChartVSAssemblyInfo info = seededBar();
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      LegendDescriptor legend = legends.getColorLegendDescriptor();
      legend.setTitleVisible(true);
      LegendFormatGeneralPaneModel pane = shown(legends, legend);
      pane.setVisible(false);

      apply(info, legends, legend, pane);

      assertFalse(legend.isTitleVisible());
      assertTrue(legend.isUserTitleVisible());
   }

   @Test
   void aChartWithNoLegendLayoutAppliesWithoutError() {
      ChartVSAssemblyInfo info = seededBar();
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      LegendDescriptor legend = legends.getColorLegendDescriptor();
      legends.setLayout(LegendsDescriptor.NO_LEGEND);
      LegendFormatGeneralPaneModel pane = shown(legends, legend);
      assertNull(pane.getPosition(), "premise: no position is shown");

      assertDoesNotThrow(() -> apply(info, legends, legend, pane));

      assertEquals(LegendsDescriptor.NO_LEGEND, legends.getLayout());
      assertFalse(legends.isUserLayout());
   }

   @Test
   void theDialogShowsTheTopGap() {
      ChartVSAssemblyInfo info = seededBar();
      info.getViewsheet().getViewsheetInfo().setVizDensity("compact");
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      assertEquals(LegendsDescriptor.TOP, legends.getLayout(), "premise: a seeded top legend");

      inetsoft.report.composition.region.ChartArea area =
         org.mockito.Mockito.mock(inetsoft.report.composition.region.ChartArea.class);
      inetsoft.report.composition.region.LegendsArea legendsArea =
         org.mockito.Mockito.mock(inetsoft.report.composition.region.LegendsArea.class);
      org.mockito.Mockito.when(area.getLegendsArea()).thenReturn(legendsArea);
      org.mockito.Mockito.when(legendsArea.getLegendAreas())
         .thenReturn(new inetsoft.report.composition.region.LegendArea[0]);

      LegendFormatDialogModel dialog = new LegendFormatDialogModel(
         info.getVSChartInfo(), legends, legends.getColorLegendDescriptor(), area,
         new java.util.ArrayList<>(), "color", null, "Color", true, false, false,
         VizContext.of(info));

      assertEquals(18, dialog.getLegendFormatGeneralPaneModel().getGap());
   }

   @Test
   void aModernTopLegendShowsStart() {
      ChartVSAssemblyInfo info = seededBar();
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();

      assertEquals("Start", builtDialog(info, legends).getLegendFormatGeneralPaneModel()
         .getAlignment());
   }

   @Test
   void aClassicLegendShowsCenterAndApplyWritesNothing() {
      ChartVSAssemblyInfo info = seededBar();
      info.setVizMark(null);
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      legends.setAlignment(0, CompositeValue.Type.DEFAULT);
      assertEquals(0, legends.getAlignment(), "premise: unset");
      LegendFormatGeneralPaneModel pane =
         builtDialog(info, legends).getLegendFormatGeneralPaneModel();
      assertEquals("Center", pane.getAlignment());

      apply(info, legends, legends.getColorLegendDescriptor(), pane);

      assertEquals(0, legends.getAlignment());
      assertFalse(legends.hasAlignmentUserValue());
   }

   @Test
   void pickingEndWritesTheAuthorValue() {
      ChartVSAssemblyInfo info = seededBar();
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      LegendFormatGeneralPaneModel pane =
         builtDialog(info, legends).getLegendFormatGeneralPaneModel();
      pane.setAlignment("End");

      apply(info, legends, legends.getColorLegendDescriptor(), pane);

      assertEquals(inetsoft.graph.GraphConstants.RIGHT_ALIGNMENT, legends.getAlignment());
      assertTrue(legends.hasAlignmentUserValue());
   }

   private static LegendFormatDialogModel builtDialog(ChartVSAssemblyInfo info,
                                                      LegendsDescriptor legends)
   {
      inetsoft.report.composition.region.ChartArea area =
         org.mockito.Mockito.mock(inetsoft.report.composition.region.ChartArea.class);
      inetsoft.report.composition.region.LegendsArea legendsArea =
         org.mockito.Mockito.mock(inetsoft.report.composition.region.LegendsArea.class);
      org.mockito.Mockito.when(area.getLegendsArea()).thenReturn(legendsArea);
      org.mockito.Mockito.when(legendsArea.getLegendAreas())
         .thenReturn(new inetsoft.report.composition.region.LegendArea[0]);
      return new LegendFormatDialogModel(
         info.getVSChartInfo(), legends, legends.getColorLegendDescriptor(), area,
         new java.util.ArrayList<>(), "color", null, "Color", true, false, false,
         VizContext.of(info));
   }

   // a bar chart seeded under the modern mark, its legend at the top
   private static ChartVSAssemblyInfo seededBar() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = chart.getChartInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, "state"));
      dim.setDataType(XSchema.STRING);
      dim.setGroupColumnValue("state");
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, "v"));
      agg.setColumnValue("v");
      agg.setFormula(AggregateFormula.SUM);
      cinfo.addXField(dim);
      cinfo.addYField(agg);
      cinfo.setChartType(GraphTypes.CHART_BAR);
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.reseedLegendPlacement();
      info.reseedLegendTitles();
      return info;
   }

   // the pane the dialog constructor builds from the descriptors
   private static LegendFormatGeneralPaneModel shown(LegendsDescriptor legends,
                                                     LegendDescriptor legend)
   {
      LegendFormatGeneralPaneModel pane = new LegendFormatGeneralPaneModel();
      pane.setVisible(legend.isTitleVisible());
      pane.setTitleValue(legend.getTitleValue());
      pane.setFillColor("#ffffff");
      pane.setGap(VSChartChromeDefaults.resolveLegendGap(
         legends.getGap(), legends.hasGapValue(), VizContext.ofGate(), legends.getLayout()));

      if(legends.getLayout() >= 1) {
         pane.setPosition(new String[] {"Top", "Right", "Bottom", "Left", "In Place"}[
            legends.getLayout() - 1]);
      }

      return pane;
   }

   private static void apply(ChartVSAssemblyInfo info, LegendsDescriptor legends,
                             LegendDescriptor legend, LegendFormatGeneralPaneModel pane)
   {
      LegendFormatDialogModel dialog = new LegendFormatDialogModel();
      dialog.setLegendFormatGeneralPaneModel(pane);
      dialog.setLegendScalePaneModel(new LegendScalePaneModel());
      AliasPaneModel alias = new AliasPaneModel();
      alias.setAliasList(new ModelAlias[0]);
      dialog.setAliasPaneModel(alias);
      dialog.updateLegendFormatDialogModel(info.getVSChartInfo(), legends, legend, null,
                                           VizContext.of(info));
   }

   @Test
   public void hasCorrectBorderColor() throws Exception {
      LegendFormatDialogModel legendFormatDialogModel = new LegendFormatDialogModel();
      LegendFormatGeneralPaneModel legendFormatGeneralPaneModel = new LegendFormatGeneralPaneModel();
      legendFormatGeneralPaneModel.setPosition("");
      legendFormatDialogModel.setLegendFormatGeneralPaneModel(legendFormatGeneralPaneModel);
      LegendScalePaneModel legendScalePaneModel = new LegendScalePaneModel();
      legendFormatDialogModel.setLegendScalePaneModel(legendScalePaneModel);
      AliasPaneModel aliasPaneModel = new AliasPaneModel();
      aliasPaneModel.setAliasList(new ModelAlias[0]);
      legendFormatDialogModel.setAliasPaneModel(aliasPaneModel);

      LegendDescriptor legendDescriptor = new LegendDescriptor();
      LegendsDescriptor legendsDescriptor = new LegendsDescriptor();
      legendFormatGeneralPaneModel.setFillColor("#ffffff");
      legendFormatDialogModel.updateLegendFormatDialogModel(new VSChartInfo(), legendsDescriptor,
                                                            legendDescriptor, null,
                                                            VizContext.ofGate());

      assertEquals(Color.WHITE, legendsDescriptor.getBorderColor());
   }

   @Test
   public void symbolRoundCornersIsWrittenToEditedLegendOnly() {
      LegendFormatDialogModel dialog = new LegendFormatDialogModel();
      LegendFormatGeneralPaneModel pane = new LegendFormatGeneralPaneModel();
      pane.setPosition("");
      pane.setFillColor("#ffffff");
      pane.setSymbolRoundCorners(false);
      dialog.setLegendFormatGeneralPaneModel(pane);
      dialog.setLegendScalePaneModel(new LegendScalePaneModel());
      AliasPaneModel aliasPaneModel = new AliasPaneModel();
      aliasPaneModel.setAliasList(new ModelAlias[0]);
      dialog.setAliasPaneModel(aliasPaneModel);

      LegendsDescriptor legendsDesc = new LegendsDescriptor();
      LegendDescriptor edited = new LegendDescriptor();
      LegendDescriptor unrelated = new LegendDescriptor();
      legendsDesc.setColorLegendDescriptor(edited);
      legendsDesc.setSizeLegendDescriptor(unrelated);

      dialog.updateLegendFormatDialogModel(new VSChartInfo(), legendsDesc, edited, null,
                                          VizContext.ofGate());

      assertFalse(edited.isSymbolRoundCorners(),
                  "the edited legend should reflect the dialog value");
      assertTrue(unrelated.isSymbolRoundCorners(),
                 "another legend on the same chart must not be affected");
   }

   @Test
   public void equalsContentDiscriminatesSymbolRoundCorners() {
      LegendDescriptor a = new LegendDescriptor();
      LegendDescriptor b = new LegendDescriptor();
      a.setSymbolRoundCorners(true);
      b.setSymbolRoundCorners(false);

      assertFalse(a.equalsContent(b));

      b.setSymbolRoundCorners(true);

      assertTrue(a.equalsContent(b));
   }

   @Test
   public void aStylesheetGapLeavesTheBoxTicked() {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(20, CompositeValue.Type.CSS);

      assertEquals(Boolean.TRUE,
                   LegendFormatDialogModel.gapFollowsDefault(legends, VizContext.of(VizMark.MODERN_LIGHT)));
   }

   @Test
   public void anAuthorZeroUnticksTheBox() {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(0);

      assertEquals(Boolean.FALSE,
                   LegendFormatDialogModel.gapFollowsDefault(legends, VizContext.of(VizMark.MODERN_LIGHT)));
      assertNull(LegendFormatDialogModel.gapFollowsDefault(legends, VizContext.LEGACY));
   }

   @Test
   public void aCompactChartShowsTheBuiltInValuesTicked() {
      LegendsDescriptor legends = new LegendsDescriptor();

      assertPane(LegendFormatDialogModel.paddingPane(legends, modernAt("compact")),
                 0, 4, 1, 4, Boolean.TRUE);
   }

   @Test
   public void aComfortableChartShowsItsDensityValues() {
      LegendsDescriptor legends = new LegendsDescriptor();

      assertPane(LegendFormatDialogModel.paddingPane(legends, modernAt("comfortable")),
                 1, 6, 2, 6, Boolean.TRUE);
   }

   @Test
   public void aStylesheetPaddingShowsTickedWithItsValue() {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setPadding(new Insets(5, 5, 5, 5), CompositeValue.Type.CSS);

      assertPane(LegendFormatDialogModel.paddingPane(legends, modernAt("compact")),
                 5, 5, 5, 5, Boolean.TRUE);
   }

   @Test
   public void anAuthorPaddingUnticksTheBox() {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setPadding(new Insets(3, 3, 3, 3));

      assertPane(LegendFormatDialogModel.paddingPane(legends, modernAt("compact")),
                 3, 3, 3, 3, Boolean.FALSE);
      assertNull(LegendFormatDialogModel.paddingPane(legends, VizContext.LEGACY).getFollowsDefault());
   }

   @Test
   public void okWhileTickedClearsTheAuthorTier() {
      VizContext ctx = modernAt("compact");
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setPadding(new Insets(5, 5, 5, 5), CompositeValue.Type.CSS);
      legends.setPadding(new Insets(3, 3, 3, 3));
      PaddingPaneModel pane = LegendFormatDialogModel.paddingPane(legends, ctx);
      pane.setFollowsDefault(true);

      ok(legends, pane, ctx);

      assertFalse(legends.hasPaddingUserValue());
      assertEquals(new Insets(5, 5, 5, 5), legends.getPadding());
   }

   @Test
   public void okWhileTickedLeavesAStylesheetPaddingStanding() {
      VizContext ctx = modernAt("compact");
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setPadding(new Insets(5, 5, 5, 5), CompositeValue.Type.CSS);

      ok(legends, LegendFormatDialogModel.paddingPane(legends, ctx), ctx);

      assertFalse(legends.hasPaddingUserValue());
      assertEquals(new Insets(5, 5, 5, 5), legends.getPadding());
   }

   @Test
   public void untickingOnCompactStoresTheShownValues() {
      VizContext ctx = modernAt("compact");
      LegendsDescriptor legends = new LegendsDescriptor();
      PaddingPaneModel pane = LegendFormatDialogModel.paddingPane(legends, ctx);
      pane.setFollowsDefault(false);

      ok(legends, pane, ctx);

      assertTrue(legends.hasPaddingUserValue());
      assertEquals(new Insets(0, 4, 1, 4), legends.getPadding());
   }

   @Test
   public void anUntickedEditIsStored() {
      VizContext ctx = modernAt("comfortable");
      LegendsDescriptor legends = new LegendsDescriptor();
      PaddingPaneModel pane = LegendFormatDialogModel.paddingPane(legends, ctx);
      pane.setTop(3);
      pane.setBottom(3);
      pane.setFollowsDefault(false);

      ok(legends, pane, ctx);

      assertEquals(new Insets(3, 6, 3, 6), legends.getPadding());
   }

   @Test
   public void aLegacyOkWithoutEditsStoresNothing() {
      LegendsDescriptor legends = new LegendsDescriptor();

      ok(legends, LegendFormatDialogModel.paddingPane(legends, VizContext.LEGACY), VizContext.LEGACY);

      assertFalse(legends.hasPaddingValue());
   }

   @Test
   public void aLegacyEditIsStored() {
      LegendsDescriptor legends = new LegendsDescriptor();
      PaddingPaneModel pane = LegendFormatDialogModel.paddingPane(legends, VizContext.LEGACY);
      pane.setTop(2);

      ok(legends, pane, VizContext.LEGACY);

      assertEquals(new Insets(2, 4, 1, 4), legends.getPadding());
   }

   @Test
   public void aRequestWithoutPaddingWritesNothing() {
      LegendsDescriptor legends = new LegendsDescriptor();

      assertDoesNotThrow(() -> ok(legends, null, modernAt("compact")));
      assertFalse(legends.hasPaddingValue());
   }

   @Test
   public void okAppliedTwiceStoresWhatOneOkStores() {
      // a relation chart's shared legend runs the dialog's OK twice on one descriptor
      VizContext ctx = modernAt("compact");

      LegendsDescriptor legacy = new LegendsDescriptor();
      PaddingPaneModel legacyPane = LegendFormatDialogModel.paddingPane(legacy, VizContext.LEGACY);
      legacyPane.setTop(2);
      ok(legacy, legacyPane, VizContext.LEGACY);
      ok(legacy, legacyPane, VizContext.LEGACY);
      assertEquals(new Insets(2, 4, 1, 4), legacy.getPadding(), "unmarked edit");

      LegendsDescriptor unticked = new LegendsDescriptor();
      PaddingPaneModel untickedPane = LegendFormatDialogModel.paddingPane(unticked, ctx);
      untickedPane.setTop(3);
      untickedPane.setFollowsDefault(false);
      ok(unticked, untickedPane, ctx);
      ok(unticked, untickedPane, ctx);
      assertEquals(new Insets(3, 4, 1, 4), unticked.getPadding(), "unticked edit");

      LegendsDescriptor ticked = new LegendsDescriptor();
      ticked.setPadding(new Insets(5, 5, 5, 5), CompositeValue.Type.CSS);
      ticked.setPadding(new Insets(3, 3, 3, 3));
      PaddingPaneModel tickedPane = LegendFormatDialogModel.paddingPane(ticked, ctx);
      tickedPane.setFollowsDefault(true);
      ok(ticked, tickedPane, ctx);
      ok(ticked, tickedPane, ctx);
      assertFalse(ticked.hasPaddingUserValue(), "ticked");
      assertEquals(new Insets(5, 5, 5, 5), ticked.getPadding(), "ticked");
   }

   @Test
   public void anUnchangedBorderStoresNoAuthorValueSoMovingInPlaceFramesIt() {
      VizContext ctx = modernAt("compact");
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.initDefaultFormat(ctx);
      assertEquals(StyleConstants.NONE, legends.getBorder(), "premise: unframed");
      LegendFormatGeneralPaneModel pane = generalPane(legends, ctx);
      pane.setStyle(legends.getBorder());
      pane.setPosition("In Place");

      submit(legends, pane, ctx);
      // the next render
      legends.initDefaultFormat(ctx);

      assertEquals(StyleConstants.THIN_LINE, legends.getBorder());
   }

   @Test
   public void aChangedBorderIsTheAuthors() {
      VizContext ctx = modernAt("compact");
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.initDefaultFormat(ctx);
      LegendFormatGeneralPaneModel pane = generalPane(legends, ctx);
      pane.setStyle(StyleConstants.DOUBLE_LINE);
      pane.setPosition("Right");

      submit(legends, pane, ctx);
      legends.initDefaultFormat(ctx);

      assertEquals(StyleConstants.DOUBLE_LINE, legends.getBorder());
   }

   private static void assertPane(PaddingPaneModel pane, int top, int left, int bottom, int right,
                                  Boolean followsDefault)
   {
      assertEquals(top, pane.getTop(), "top");
      assertEquals(left, pane.getLeft(), "left");
      assertEquals(bottom, pane.getBottom(), "bottom");
      assertEquals(right, pane.getRight(), "right");
      assertEquals(followsDefault, pane.getFollowsDefault(), "followsDefault");
   }

   /** Runs the dialog's OK with the given padding pane; every other field is left as shown. */
   private static void ok(LegendsDescriptor legends, PaddingPaneModel padding, VizContext ctx) {
      LegendFormatGeneralPaneModel pane = generalPane(legends, ctx);
      pane.setPosition("");
      pane.setPadding(padding);
      submit(legends, pane, ctx);
   }

   private static LegendFormatGeneralPaneModel generalPane(LegendsDescriptor legends,
                                                           VizContext ctx)
   {
      LegendFormatGeneralPaneModel pane = new LegendFormatGeneralPaneModel();
      pane.setFillColor("#ffffff");
      pane.setGap(VSChartChromeDefaults.resolveLegendGap(legends.getGap(), legends.hasGapValue(), ctx,
         legends.getLayout()));
      return pane;
   }

   private static void submit(LegendsDescriptor legends, LegendFormatGeneralPaneModel pane,
                              VizContext ctx)
   {
      LegendFormatDialogModel dialog = new LegendFormatDialogModel();
      dialog.setLegendFormatGeneralPaneModel(pane);
      dialog.setLegendScalePaneModel(new LegendScalePaneModel());
      AliasPaneModel alias = new AliasPaneModel();
      alias.setAliasList(new ModelAlias[0]);
      dialog.setAliasPaneModel(alias);
      dialog.updateLegendFormatDialogModel(new VSChartInfo(), legends, new LegendDescriptor(), null,
                                           ctx);
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
}
