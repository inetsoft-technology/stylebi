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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.XMLSerializable;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.controller.VSCloseObjectWizardService;
import inetsoft.web.vswizard.handler.SyncChartHandler;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.handler.VSWizardObjectHandler;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.model.VSWizardOriginalModel;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Insets;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Per-type values live on a descriptor the wizard shares and swaps; each chart that receives one
 * ends up with its own type's values, and the author's survive.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PerTypeSeedSharedDescriptorTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // ---- wizard cancel ------------------------------------------------------------------------

   @Test
   void aCancelledWizardLeavesTheOriginalsPerTypeValues() throws Exception {
      gateOn();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly bar = barOn(vs, "Chart1");
      ChartVSAssembly temp = tempSharing(bar);
      ChartDescriptor shared = temp.getChartDescriptor();
      assertSame(bar.getChartDescriptor(), shared, "premise: the temp chart shares the descriptor");
      assertTrue(defaultVisible(xTitle(shared)), "premise: the point seed showed the x title");
      assertEquals(StyleConstants.THIN_LINE, defaultXGrid(shared),
                   "premise: the point seed drew an x grid");

      cancel(vs, temp);

      ChartDescriptor desc = bar.getChartDescriptor();
      assertFalse(defaultVisible(xTitle(desc)), "x title");
      assertEquals(StyleConstants.NONE, defaultXGrid(desc), "x grid");
   }

   @Test
   void anEmptyFinishLeavesTheOriginalsPerTypeValues() throws Exception {
      gateOn();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly bar = barOn(vs, "Chart1");
      ChartVSAssembly temp = tempSharing(bar);
      ChartDescriptor shared = temp.getChartDescriptor();
      assertSame(bar.getChartDescriptor(), shared, "premise: the temp chart shares the descriptor");
      assertEquals(StyleConstants.THIN_LINE, defaultXGrid(shared),
                   "premise: the point seed drew an x grid");
      assertNull(vs.getLatestTempAssembly(), "premise: no primary assembly to save");

      close(vs, temp, true);

      ChartDescriptor desc = bar.getChartDescriptor();
      assertFalse(defaultVisible(xTitle(desc)), "x title");
      assertEquals(StyleConstants.NONE, defaultXGrid(desc), "x grid");
   }

   @Test
   void aClassicChartLosesTheMarkedTempChartsValuesOnCancel() throws Exception {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly bar = barOn(vs, "Chart1");
      assertNull(bar.getVSAssemblyInfo().getVizMark(), "premise: classic");
      ChartVSAssembly temp = markedTempSharing(bar, GraphTypes.CHART_POINT,
                                               refs(agg("w")), refs(agg("v")));
      assertEquals(StyleConstants.THIN_LINE, defaultXGrid(bar.getChartDescriptor()),
                   "premise: the marked point seed drew an x grid");

      cancel(vs, temp);

      ChartDescriptor desc = bar.getChartDescriptor();

      for(TitleDescriptor title : titles(desc)) {
         assertTrue(defaultVisible(title), "titles");
      }

      assertEquals(StyleConstants.NONE, defaultXGrid(desc), "x grid");
      assertFalse(desc.getPlotDescriptor().isSmoothLines(), "a classic bar's smoothing");
   }

   @Test
   void aClassicAreaGetsItsTitlesAndCurvesBackOnCancel() throws Exception {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly area = chartOn(vs, "Chart1", GraphTypes.CHART_AREA,
                                     refs(dim("state")), refs(agg("v")));
      String before = xml(area.getChartDescriptor());
      assertTrue(area.getChartDescriptor().getPlotDescriptor().isSmoothLines(),
                 "premise: a classic area is smooth");
      ChartVSAssembly temp = markedTempSharing(area, GraphTypes.CHART_LINE,
                                               refs(dim("state")), refs(agg("w")));
      assertFalse(defaultVisible(xTitle(area.getChartDescriptor())),
                  "premise: the marked line seed hid the x title");
      assertFalse(area.getChartDescriptor().getPlotDescriptor().isSmoothLines(),
                  "premise: the marked line seed straightened the lines");

      cancel(vs, temp);

      ChartDescriptor desc = area.getChartDescriptor();

      for(TitleDescriptor title : titles(desc)) {
         assertTrue(defaultVisible(title), "titles");
      }

      assertTrue(desc.getPlotDescriptor().isSmoothLines(), "the classic area's curves");
      assertEquals(before, xml(desc), "the descriptor's XML");
   }

   @Test
   void aClassicChartsAuthorValuesSurviveTheMarkedTempChart() throws Exception {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly bar = barOn(vs, "Chart1");
      ChartVSAssemblyInfo info = bar.getChartInfo();
      xTitle(bar.getChartDescriptor()).setVisible(false);
      info.setUserSmoothLines(true);
      bar.getChartDescriptor().getPlotDescriptor().setSmoothLines(true);
      ChartVSAssembly temp = markedTempSharing(bar, GraphTypes.CHART_POINT,
                                               refs(agg("w")), refs(agg("v")));

      cancel(vs, temp);

      ChartDescriptor desc = bar.getChartDescriptor();
      assertTrue(xTitle(desc).hasVisibleUserValue(), "the author's title tier");
      assertFalse(xTitle(desc).isVisible(), "the author's hidden title");
      assertTrue(info.isUserSmoothLines(), "the author's smoothing flag");
      assertTrue(desc.getPlotDescriptor().isSmoothLines(), "the author's smoothing");
   }

   @Test
   void aClassicChartsXmlIsUnchangedByCancel() throws Exception {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly bar = barOn(vs, "Chart1");
      String descBefore = xml(bar.getChartDescriptor());
      String infoBefore = xml(bar.getChartInfo());
      ChartVSAssembly temp = markedTempSharing(bar, GraphTypes.CHART_POINT,
                                               refs(agg("w")), refs(agg("v")));

      cancel(vs, temp);

      assertEquals(descBefore, xml(bar.getChartDescriptor()), "the descriptor");
      assertEquals(infoBefore, xml(bar.getChartInfo()), "the whole chart");
   }

   @Test
   void authorValuesSurvive() throws Exception {
      gateOn();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly bar = barOn(vs, "Chart1");
      ChartVSAssemblyInfo info = bar.getChartInfo();
      TitleDescriptor x = xTitle(bar.getChartDescriptor());
      assertFalse(defaultVisible(x), "premise: the bar seed hid the x title");
      x.setVisible(true);
      info.setUserSmoothLines(true);
      bar.getChartDescriptor().getPlotDescriptor().setSmoothLines(true);
      ChartVSAssembly temp = tempSharing(bar);

      cancel(vs, temp);

      ChartDescriptor desc = bar.getChartDescriptor();
      assertTrue(xTitle(desc).hasVisibleUserValue(), "the author's title tier");
      assertTrue(xTitle(desc).isVisible(), "the author's title");
      assertTrue(info.isUserSmoothLines(), "the author's smoothing flag");
      assertTrue(desc.getPlotDescriptor().isSmoothLines(), "the author's smoothing");
   }

   // ---- wizard save --------------------------------------------------------------------------

   @Test
   void aWizardSaveReseedsTheFinalChartForItsOwnType() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly bar = barOn(vs, "Chart1");
      ChartVSAssembly point = pointOn(vs, "Chart2");
      PlotDescriptor before = point.getChartDescriptor().getPlotDescriptor();
      assertEquals(StyleConstants.THIN_LINE, defaultXGrid(point.getChartDescriptor()),
                   "premise: the point seed drew an x grid");

      save(bar, point);

      assertNotSame(before, point.getChartDescriptor().getPlotDescriptor(),
                    "premise: the bar's plot was swapped in");
      assertEquals(StyleConstants.THIN_LINE, defaultXGrid(point.getChartDescriptor()));
   }

   @Test
   void anAuthorsSmoothingTravelsWithTheSwappedPlot() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly line = chartOn(vs, "Chart1", GraphTypes.CHART_LINE,
                                     refs(dim("state")), refs(agg("v")));
      line.getChartInfo().setUserSmoothLines(true);
      line.getChartDescriptor().getPlotDescriptor().setSmoothLines(true);
      ChartVSAssembly area = chartOn(vs, "Chart2", GraphTypes.CHART_AREA,
                                     refs(dim("state")), refs(agg("v")));
      assertFalse(area.getChartDescriptor().getPlotDescriptor().isSmoothLines(),
                  "premise: a modern area is straight");

      save(line, area);

      assertTrue(area.getChartInfo().isUserSmoothLines(), "the flag");
      assertTrue(area.getChartDescriptor().getPlotDescriptor().isSmoothLines(), "the value");
   }

   @Test
   void aClassicSaveAfterAMarkedTempChartTakesTheClassicValues() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly bar = barOn(vs, "Chart1");
      ChartVSAssembly temp = markedTempSharing(bar, GraphTypes.CHART_POINT,
                                               refs(agg("w")), refs(agg("v")));
      ChartVSAssembly target = barOn(vs, "Chart2");
      assertNull(target.getVSAssemblyInfo().getVizMark(), "premise: classic");
      assertEquals(StyleConstants.THIN_LINE, defaultXGrid(bar.getChartDescriptor()),
                   "premise: the marked point seed drew an x grid on the shared plot");
      PlotDescriptor before = target.getChartDescriptor().getPlotDescriptor();

      save(bar, target, temp);

      assertNotSame(before, target.getChartDescriptor().getPlotDescriptor(),
                    "premise: the original's plot was swapped in");
      assertEquals(StyleConstants.NONE, defaultXGrid(target.getChartDescriptor()));
   }

   @Test
   void aClassicSaveAfterAMarkedTempChartKeepsTheAuthorsValues() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly line = chartOn(vs, "Chart1", GraphTypes.CHART_LINE,
                                     refs(dim("state")), refs(agg("v")));
      ChartVSAssemblyInfo from = line.getChartInfo();
      PlotDescriptor fromPlot = line.getChartDescriptor().getPlotDescriptor();
      from.setPadding(new Insets(25, 25, 25, 25));
      from.setUserPadding(true);
      from.setUserSmoothLines(true);
      fromPlot.setSmoothLines(true);
      from.setUserBarCornerRadius(true);
      fromPlot.setBarCornerRadius(0.5);
      ChartVSAssembly temp = markedTempSharing(line, GraphTypes.CHART_POINT,
                                               refs(agg("w")), refs(agg("v")));
      ChartVSAssembly target = chartOn(vs, "Chart2", GraphTypes.CHART_LINE,
                                       refs(dim("state")), refs(agg("v")));

      save(line, target, temp);

      ChartVSAssemblyInfo info = target.getChartInfo();
      PlotDescriptor plot = target.getChartDescriptor().getPlotDescriptor();
      assertEquals(new Insets(25, 25, 25, 25), info.getPadding(), "padding");
      assertTrue(info.isUserPadding(), "padding flag");
      assertTrue(plot.isSmoothLines(), "smoothing");
      assertTrue(info.isUserSmoothLines(), "smoothing flag");
      assertEquals(0.5, plot.getBarCornerRadius(), 0.0001, "corner radius");
      assertTrue(info.isUserBarCornerRadius(), "corner radius flag");
   }

   // ---- a classic dashboard: the temp chart is unmarked too, so nothing is seeded ------------

   @Test
   void aClassicDashboardsSaveKeepsTheCopiedSmoothing() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly area = chartOn(vs, "Chart1", GraphTypes.CHART_AREA,
                                     refs(dim("state")), refs(agg("v")));
      ChartVSAssembly temp = tempSharing(area);
      assertNull(temp.getVSAssemblyInfo().getVizMark(), "premise: an unmarked temp chart");
      ChartVSAssembly line = chartOn(vs, "Chart2", GraphTypes.CHART_LINE,
                                     refs(dim("state")), refs(agg("v")));
      assertTrue(area.getChartDescriptor().getPlotDescriptor().isSmoothLines(),
                 "premise: a classic area is smooth");
      assertFalse(line.getChartDescriptor().getPlotDescriptor().isSmoothLines(),
                  "premise: a classic line is straight");

      save(area, line, temp);

      // the Save copies the area's plot, curves included
      assertTrue(line.getChartDescriptor().getPlotDescriptor().isSmoothLines());
   }

   @Test
   void aClassicDashboardsCancelLeavesTheChartsXmlAlone() throws Exception {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly bar = barOn(vs, "Chart1");
      // an unflagged value a legacy pass would replace, as an old file can carry
      bar.getChartDescriptor().getPlotDescriptor().setSmoothLines(true);
      String descBefore = xml(bar.getChartDescriptor());
      String infoBefore = xml(bar.getChartInfo());
      ChartVSAssembly temp = tempSharing(bar);
      assertNull(temp.getVSAssemblyInfo().getVizMark(), "premise: an unmarked temp chart");

      cancel(vs, temp);

      assertEquals(descBefore, xml(bar.getChartDescriptor()), "the descriptor");
      assertEquals(infoBefore, xml(bar.getChartInfo()), "the whole chart");
   }

   // ---- copyViewInfo -------------------------------------------------------------------------

   @Test
   void copyViewInfoReseedsForTheReceivingChartsType() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      ChartVSAssemblyInfo bar = barOn(vs, "Chart1").getChartInfo();
      ChartVSAssemblyInfo point = pointOn(vs, "Chart2").getChartInfo();

      bar.copyViewInfo(point, true);

      assertSame(point.getChartDescriptor(), bar.getChartDescriptor(),
                 "premise: the point's descriptor was taken");
      // the copy runs on open and refresh too, so it seeds nothing itself
      assertEquals(StyleConstants.THIN_LINE, defaultXGrid(bar.getChartDescriptor()),
                   "the copy itself");

      // as the receiving chart's next update does
      bar.reseedIfResolvedTypeChanged();

      assertEquals(GraphTypes.CHART_BAR, bar.getSeededType().type(), "premise: the bar's key");
      assertFalse(defaultVisible(xTitle(bar.getChartDescriptor())), "x title");
      assertEquals(StyleConstants.NONE, defaultXGrid(bar.getChartDescriptor()), "x grid");
   }

   // ---- legend frame sync --------------------------------------------------------------------

   @Test
   void aClassicOriginalSyncedIntoAMarkedTargetStampsNothing() {
      gateOff();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      gateOn();
      ChartVSAssembly target = barOn(new Viewsheet(), "Chart2");
      LegendsDescriptor from = legends(original);
      LegendsDescriptor legends = legends(target);
      Color seededColor = legends.getBorderColor();
      int seededBorder = legends.getBorder();
      assertNotEquals(from.getBorderColor(), seededColor, "premise: the colours differ");
      assertNotEquals(from.getBorder(), seededBorder, "premise: the borders differ");
      from.setLayout(LegendsDescriptor.BOTTOM);
      assertNotEquals(LegendsDescriptor.BOTTOM, legends.getLayout(), "premise: the layouts differ");

      syncPreview(original, target);

      assertEquals(LegendsDescriptor.BOTTOM, legends.getLayout(), "premise: the legends synced");
      assertFalse(legends.hasBorderColorUserValue(), "border colour");
      assertFalse(legends.hasBorderUserValue(), "border");
      assertEquals(seededColor, legends.getBorderColor(), "the seeded colour");
      assertEquals(seededBorder, legends.getBorder(), "the seeded border");
   }

   @Test
   void anAuthorsLegendFrameReachesAMarkedTarget() {
      gateOff();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      legends(original).setBorderColor(Color.RED);
      legends(original).setBorder(StyleConstants.MEDIUM_LINE);
      gateOn();
      ChartVSAssembly target = barOn(new Viewsheet(), "Chart2");
      LegendsDescriptor legends = legends(target);

      syncPreview(original, target);

      assertTrue(legends.hasBorderColorUserValue(), "border colour tier");
      assertEquals(Color.RED, legends.getBorderColor(), "border colour");
      assertTrue(legends.hasBorderUserValue(), "border tier");
      assertEquals(StyleConstants.MEDIUM_LINE, legends.getBorder(), "border");
   }

   @Test
   void anUnmarkedTargetStillTakesTheOriginalsFrame() {
      gateOn();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      LegendsDescriptor from = legends(original);
      gateOff();
      ChartVSAssembly target = barOn(new Viewsheet(), "Chart2");
      LegendsDescriptor legends = legends(target);
      assertNotEquals(from.getBorderColor(), legends.getBorderColor(),
                      "premise: the colours differ");
      assertNotEquals(from.getBorder(), legends.getBorder(), "premise: the borders differ");

      syncPreview(original, target);

      assertTrue(legends.hasBorderColorUserValue(), "border colour tier");
      assertEquals(from.getBorderColor(), legends.getBorderColor(), "border colour");
      assertTrue(legends.hasBorderUserValue(), "border tier");
      assertEquals(from.getBorder(), legends.getBorder(), "border");
   }

   // ---- harness ------------------------------------------------------------------------------

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   // a seeded bar, marked when the gate is on
   private static ChartVSAssembly barOn(Viewsheet vs, String name) {
      return chartOn(vs, name, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
   }

   // a seeded scatter, marked when the gate is on
   private static ChartVSAssembly pointOn(Viewsheet vs, String name) {
      return chartOn(vs, name, GraphTypes.CHART_POINT, refs(agg("w")), refs(agg("v")));
   }

   private static ChartVSAssembly chartOn(Viewsheet vs, String name, int type, ChartRef[] x,
                                          ChartRef[] y)
   {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = chart.getChartInfo();
      VSChartInfo cinfo = info.getVSChartInfo();

      for(ChartRef ref : x) {
         cinfo.addXField(ref);
      }

      for(ChartRef ref : y) {
         cinfo.addYField(ref);
      }

      cinfo.setChartType(type);
      info.seedChromeDefaults(VizContext.of(info));
      return chart;
   }

   // the wizard's temp chart, with the host's mark: takes the original's descriptor, then
   // becomes a scatter
   private static ChartVSAssembly tempSharing(ChartVSAssembly original) {
      ChartVSAssembly temp =
         new ChartVSAssembly(original.getViewsheet(), VSWizardConstants.TEMP_CHART_NAME);
      return bindTemp(original, temp, GraphTypes.CHART_POINT, refs(agg("w")), refs(agg("v")));
   }

   // a marked temp chart sharing a classic original's descriptor
   private static ChartVSAssembly markedTempSharing(ChartVSAssembly original, int type,
                                                    ChartRef[] x, ChartRef[] y)
   {
      ChartVSAssembly temp =
         new ChartVSAssembly(original.getViewsheet(), VSWizardConstants.TEMP_CHART_NAME);
      temp.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      return bindTemp(original, temp, type, x, y);
   }

   private static ChartVSAssembly bindTemp(ChartVSAssembly original, ChartVSAssembly temp,
                                           int type, ChartRef[] x, ChartRef[] y)
   {
      wizardHandler().syncChartAssembly(original, temp);
      ChartVSAssemblyInfo info = temp.getChartInfo();
      // its first update records the key
      info.reseedIfResolvedTypeChanged();

      VSChartInfo cinfo = info.getVSChartInfo();

      for(ChartRef ref : x) {
         cinfo.addXField(ref);
      }

      for(ChartRef ref : y) {
         cinfo.addYField(ref);
      }

      cinfo.setChartType(type);
      // its next update re-seeds for the new type
      info.reseedIfResolvedTypeChanged();
      assertEquals(type, info.getSeededType().type(), "premise: the temp chart's key");
      return temp;
   }

   // the wizard's Cancel, through the close service
   private static void cancel(Viewsheet vs, ChartVSAssembly temp) throws Exception {
      close(vs, temp, false);
   }

   // with no primary assembly in the sheet, a Finish is handled as a Cancel
   private static void close(Viewsheet vs, ChartVSAssembly temp, boolean save) throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getViewsheet(eq(VS_ID), any())).thenReturn(rvs);

      VSWizardOriginalModel originalModel = new VSWizardOriginalModel();
      originalModel.setOriginalName("Chart1");
      VSTemporaryInfo tempInfo = new VSTemporaryInfo();
      tempInfo.setOriginalModel(originalModel);
      tempInfo.setTempChart(temp);
      VSWizardTemporaryInfoService temps = mock(VSWizardTemporaryInfoService.class);
      when(temps.getVSTemporaryInfo(rvs)).thenReturn(tempInfo);

      VSWizardObjectHandler objects = mock(VSWizardObjectHandler.class);
      when(objects.getOriginalRViewsheet(any(), any())).thenReturn(rvs);

      new VSCloseObjectWizardService(null, engine, objects, null, wizardHandler(), null, null,
                                     temps)
         .closeHandle(VS_ID, save, null, null, null, mock(CommandDispatcher.class));
   }

   // the wizard's Save syncs the original into the final chart this way
   private static void save(ChartVSAssembly original, ChartVSAssembly target) {
      save(original, target, null);
   }

   private static void save(ChartVSAssembly original, ChartVSAssembly target,
                            ChartVSAssembly temp)
   {
      VSTemporaryInfo tempInfo = new VSTemporaryInfo();
      tempInfo.setTempChart(temp);
      new SyncChartHandler(new ChartRegionHandler())
         .syncChart(tempInfo, original, target, false, false);
   }

   // the wizard preview's sync, legends included
   private static void syncPreview(ChartVSAssembly original, ChartVSAssembly target) {
      new SyncChartHandler(new ChartRegionHandler())
         .syncChart(new VSTemporaryInfo(), original, target, true, true);
   }

   // syncChartAssembly touches none of the handler's collaborators
   private static VSWizardBindingHandler wizardHandler() {
      return new VSWizardBindingHandler(
         null, null, null, null, null, null, null, null, null, null, null, null, null, null);
   }

   private static LegendsDescriptor legends(ChartVSAssembly chart) {
      return chart.getChartDescriptor().getLegendsDescriptor();
   }

   private static TitleDescriptor xTitle(ChartDescriptor desc) {
      return desc.getTitlesDescriptor().getXTitleDescriptor();
   }

   private static TitleDescriptor[] titles(ChartDescriptor desc) {
      TitlesDescriptor titles = desc.getTitlesDescriptor();
      return new TitleDescriptor[] {
         titles.getXTitleDescriptor(), titles.getX2TitleDescriptor(),
         titles.getYTitleDescriptor(), titles.getY2TitleDescriptor()
      };
   }

   private static String xml(XMLSerializable obj) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      obj.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   // the DEFAULT tier, read off a copy with the higher tiers cleared
   private static boolean defaultVisible(TitleDescriptor title) {
      TitleDescriptor copy = (TitleDescriptor) title.clone();
      copy.resetCompositeValues(CompositeValue.Type.USER);
      copy.resetCompositeValues(CompositeValue.Type.CSS);
      return copy.isVisible();
   }

   private static int defaultXGrid(ChartDescriptor desc) {
      PlotDescriptor copy = (PlotDescriptor) desc.getPlotDescriptor().clone();
      copy.resetCompositeValues(CompositeValue.Type.USER);
      copy.resetCompositeValues(CompositeValue.Type.CSS);
      return copy.getXGridStyle();
   }

   private static ChartRef[] refs(ChartRef... refs) {
      return refs;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setDataType(XSchema.STRING);
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      return agg;
   }

   private static final String VS_ID = "vs1";
}
