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

import inetsoft.graph.internal.DimensionD;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The legend position seed: by resolved type, colour ramp and the saved crowded verdict, never over
 * the author's position.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendPlacementSeedTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aNewModernBarHasATopLegend() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);

      assertEquals(LegendsDescriptor.TOP, design(info).getLayout());
      assertEquals(LegendsDescriptor.TOP, runtime(info).getLayout());
      assertFalse(design(info).isUserLayout());
      assertTrue(info.isLegendCountPending());
   }

   @Test
   void aNewModernPieHasARightLegend() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      info.getVSChartInfo().setChartType(GraphTypes.CHART_PIE);
      seed(info);

      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout());
      assertEquals(LegendsDescriptor.RIGHT, runtime(info).getLayout());
   }

   @Test
   void aMeasureOnColorGoesRight() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      info.getVSChartInfo().setColorField(colorOf(agg("w")));
      seed(info);

      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout());
   }

   @Test
   void theSavedVerdictMovesItRight() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);
      assertEquals(LegendsDescriptor.TOP, design(info).getLayout(), "premise");

      info.setLegendCrowded(true);
      seed(info);

      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout());
      assertEquals(LegendsDescriptor.RIGHT, runtime(info).getLayout());
   }

   @Test
   void aClassicChartKeepsRight() {
      gateOff();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);

      assertNull(info.getVizMark(), "premise: unmarked");
      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout());
      assertFalse(info.needsLegendCount());
   }

   @Test
   void anAuthorsPositionSurvivesEveryReseed() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);
      design(info).setAuthorLayout(LegendsDescriptor.BOTTOM);

      info.getVSChartInfo().setChartType(GraphTypes.CHART_LINE);
      info.reseedIfResolvedTypeChanged();
      assertEquals(LegendsDescriptor.BOTTOM, design(info).getLayout(), "type change");

      info.setVizMark(null);
      seed(info);
      assertEquals(LegendsDescriptor.BOTTOM, design(info).getLayout(), "revert");

      info.setVizMark(VizMark.MODERN_LIGHT);
      seed(info);
      assertEquals(LegendsDescriptor.BOTTOM, design(info).getLayout(), "modernize");
   }

   @Test
   void changingTheColorFieldReseedsAndAsksForACount() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);
      info.reseedIfResolvedTypeChanged();
      info.clearLegendCountPending();
      assertEquals(LegendsDescriptor.TOP, design(info).getLayout(), "premise");

      info.getVSChartInfo().setColorField(colorOf(agg("w")));
      info.reseedIfResolvedTypeChanged();

      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout());
      assertTrue(info.isLegendCountPending());
   }

   @Test
   void aViewerTypeChangeUsesTheVerdict() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);
      info.reseedIfResolvedTypeChanged();
      info.setLegendCrowded(true);

      info.getVSChartInfo().setChartType(GraphTypes.CHART_LINE);
      info.reseedIfResolvedTypeChanged();

      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout());
   }

   @Test
   void aBookmarkRestoreKeepsTheCountedPosition() throws Exception {
      gateOn();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = chartOn(vs);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVSChartInfo(bar());
      seed(info);
      assertEquals(LegendsDescriptor.TOP, design(info).getLayout(), "premise");
      String state = writeState(chart);

      info.setLegendCrowded(true);
      seed(info);
      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout(), "premise");
      info.clearLegendCountPending();

      restoreState(chart, state);

      ChartVSAssemblyInfo restored = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      assertEquals(LegendsDescriptor.RIGHT, design(restored).getLayout());
      assertFalse(restored.isLegendCountPending());
   }

   @Test
   void keepMovesAnUntouchedLegendToTheTop() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = chartOn(vs);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVSChartInfo(bar());
      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout(), "premise");

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(LegendsDescriptor.TOP, design(info).getLayout());
      assertTrue(info.isLegendCountPending());
   }

   @Test
   void keepLeavesAnAuthorsPosition() throws Exception {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = chartOn(vs);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVSChartInfo(bar());
      design(info).setLayout(LegendsDescriptor.BOTTOM);
      String xml = writeXml(info);
      xml = xml.replaceAll(" userLayout=\"[a-z]*\"", "");
      info.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(LegendsDescriptor.BOTTOM, design(info).getLayout());
   }

   @Test
   void keepLeavesAResizedLegend() throws Exception {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = chartOn(vs);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVSChartInfo(bar());
      design(info).setPreferredSize(new DimensionD(0.2, 1.0));
      String xml = writeXml(info);
      xml = xml.replaceAll(" userLayout=\"[a-z]*\"", "");
      info.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout());
      assertEquals(0.2, design(info).getPreferredSize().getWidth(), 1e-9);
      assertEquals(1.0, design(info).getPreferredSize().getHeight(), 1e-9);
   }

   @Test
   void revertPutsAnUntouchedLegendBackRight() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = chartOn(vs);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVSChartInfo(bar());

      gateOn();
      VizModernizeUtil.modernize(vs);
      assertEquals(LegendsDescriptor.TOP, design(info).getLayout(), "premise");

      VizModernizeUtil.revert(vs);

      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout());
      assertFalse(design(info).isUserLayout());
   }

   @Test
   void aTopLegendHasNoTitle() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);

      assertEquals(LegendsDescriptor.TOP, design(info).getLayout(), "premise");
      assertFalse(design(info).getColorLegendDescriptor().isTitleVisible());

      if(runtime(info).getColorLegendDescriptor() != null) {
         assertFalse(runtime(info).getColorLegendDescriptor().isTitleVisible());
      }
   }

   @Test
   void aRightLegendKeepsItsTitle() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      info.getVSChartInfo().setChartType(GraphTypes.CHART_PIE);
      seed(info);

      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout(), "premise");
      assertTrue(design(info).getColorLegendDescriptor().isTitleVisible());
   }

   @Test
   void aCrowdedLegendKeepsItsTitle() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);
      assertFalse(design(info).getColorLegendDescriptor().isTitleVisible(), "premise");

      info.setLegendCrowded(true);
      info.reseedLegendPlacement();
      info.reseedLegendTitles();

      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout(), "premise");
      assertTrue(design(info).getColorLegendDescriptor().isTitleVisible());
   }

   @Test
   void aCrowdedCountMovesAnUnflaggedLegendRight() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);
      assertEquals(LegendsDescriptor.TOP, design(info).getLayout(), "premise");
      assertTrue(info.isLegendCountPending(), "premise");

      assertTrue(info.applyLegendCount(true));

      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout());
      assertEquals(LegendsDescriptor.RIGHT, runtime(info).getLayout());
      assertTrue(info.isLegendCrowded());
      assertTrue(design(info).getColorLegendDescriptor().isTitleVisible());

      if(runtime(info).getColorLegendDescriptor() != null) {
         assertTrue(runtime(info).getColorLegendDescriptor().isTitleVisible());
      }

      assertFalse(info.isLegendCountPending());
   }

   @Test
   void aCountThatChangesNothingReturnsFalse() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);
      assertEquals(LegendsDescriptor.TOP, design(info).getLayout(), "premise");
      assertTrue(info.isLegendCountPending(), "premise");

      assertFalse(info.applyLegendCount(false));

      assertEquals(LegendsDescriptor.TOP, design(info).getLayout());
      assertFalse(info.isLegendCrowded());
      assertFalse(design(info).getColorLegendDescriptor().isTitleVisible());
      assertFalse(info.isLegendCountPending());
   }

   @Test
   void aCountOnAnUnmarkedChartWritesNothing() {
      gateOff();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);
      assertNull(info.getVizMark(), "premise: unmarked");
      info.requestLegendCount();

      assertFalse(info.applyLegendCount(true));

      assertEquals(LegendsDescriptor.RIGHT, design(info).getLayout());
      assertFalse(info.isLegendCrowded());
      assertTrue(info.isLegendCountPending());
   }

   @Test
   void aTypedTitleStaysOnATopLegend() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);
      design(info).getColorLegendDescriptor().setAuthorTitleValue("Brands");

      info.reseedLegendTitles();
      seed(info);

      assertEquals(LegendsDescriptor.TOP, design(info).getLayout(), "premise");
      assertTrue(design(info).getColorLegendDescriptor().isTitleVisible());
   }

   @Test
   void anAuthorsHiddenTitleStaysHiddenOnTheRight() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      info.getVSChartInfo().setChartType(GraphTypes.CHART_PIE);
      seed(info);
      design(info).getColorLegendDescriptor().setAuthorTitleVisible(false);

      seed(info);
      info.reseedLegendTitles();

      assertFalse(design(info).getColorLegendDescriptor().isTitleVisible());
   }

   @Test
   void aMultiStyleMeasuresLegendLosesItsTitle() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.addXField(dim("state"));
      VSChartAggregateRef y1 = agg("v");
      VSChartAggregateRef y2 = agg("w");
      y1.setColorField(colorOf(dim("region")));
      y2.setColorField(colorOf(dim("city")));
      cinfo.addYField(y1);
      cinfo.addYField(y2);
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setMultiStyles(true);
      info.setVSChartInfo(cinfo);
      info.setRTChartDescriptor((ChartDescriptor) info.getChartDescriptor().clone());
      seed(info);

      AestheticRef[] refs = cinfo.getAestheticRefs(false);
      assertEquals(2, refs.length, "premise");
      assertEquals(LegendsDescriptor.TOP, design(info).getLayout(), "premise");

      for(AestheticRef ref : refs) {
         assertFalse(ref.getLegendDescriptor().isTitleVisible());
      }
   }

   @Test
   void revertShowsEveryTitle() {
      gateOn();
      ChartVSAssemblyInfo info = boundBar();
      seed(info);
      assertFalse(design(info).getColorLegendDescriptor().isTitleVisible(), "premise");

      info.setVizMark(null);
      seed(info);

      assertTrue(design(info).getColorLegendDescriptor().isTitleVisible());
   }

   private static LegendsDescriptor design(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getLegendsDescriptor();
   }

   private static LegendsDescriptor runtime(ChartVSAssemblyInfo info) {
      return info.getRTChartDescriptor().getLegendsDescriptor();
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   // state by Sum(v) with region on Color
   private static VSChartInfo bar() {
      VSChartInfo bar = new DefaultVSChartInfo();
      bar.addXField(dim("state"));
      bar.addYField(agg("v"));
      bar.setColorField(colorOf(dim("region")));
      bar.setChartType(GraphTypes.CHART_BAR);
      return bar;
   }

   private static ChartVSAssemblyInfo boundBar() {
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(bar());
      info.setRTChartDescriptor((ChartDescriptor) info.getChartDescriptor().clone());
      return info;
   }

   private static VSAestheticRef colorOf(VSDataRef ref) {
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(ref);
      return color;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setGroupColumnValue(name);
      dim.setDataType(XSchema.STRING);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      return agg;
   }

   private static ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static void seed(ChartVSAssemblyInfo info) {
      info.seedChromeDefaults(VizContext.of(info));
   }

   private static String writeXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static String writeState(VSAssembly assembly) {
      StringWriter sw = new StringWriter();
      assembly.writeState(new PrintWriter(sw), true);
      return sw.toString();
   }

   private static void restoreState(VSAssembly assembly, String state) throws Exception {
      assembly.parseState(Tool.parseXML(new StringReader(state)).getDocumentElement());
   }

   private static ChartVSAssembly chartOn(Viewsheet vs) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      return chart;
   }
}
