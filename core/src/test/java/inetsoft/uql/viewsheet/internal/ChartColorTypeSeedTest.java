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

import inetsoft.graph.aesthetic.BluesColorFrame;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.aesthetic.SpectralColorFrame;
import inetsoft.graph.aesthetic.StaticShapeFrame;
import inetsoft.graph.aesthetic.TealColorFrame;
import inetsoft.report.internal.graph.ChangeChartTypeProcessor;
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
import inetsoft.uql.viewsheet.graph.aesthetic.BluesColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.SpectralColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.TealColorFrameWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The chart-level colour follows the resolved type on a marked chart: grey relation edges, the
 * tinted region of a polygon-only map, the first palette colour elsewhere.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartColorTypeSeedTest {
   private static final Color LEGACY_1 = new Color(0x518DB9);
   private static final Color LEGACY_2 = new Color(0xB9DBF4);
   private static final Color MODERN_1 = new Color(0x0490FF);
   private static final Color MODERN_2 = new Color(0xFF5A35);
   private static final Color EDGE = new Color(0x85827A);
   private static final Color EDGE_DARK = new Color(0x7E7B86);
   private static final Color TINT = new Color(0xA6D0F0);
   private static final Color PALE_ORANGE = new Color(0xFBD29A);
   private static final Color GRID = new Color(0xD6D2C8);
   private static final Color GRID_DARK = new Color(0x4A484F);
   private static final String STATE_LAYER = "2";
   private static final String CITY_LAYER = "1001";

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
      SreeEnv.setProperty("map.default.color", null);
      SreeEnv.setProperty("map.empty.color", null);
      VSChartPaletteDefaults.clearMemo();
   }

   @Test
   void aModernTreeHasGreyEdges() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(tree());
      seed(info);
      assertEquals(EDGE, chartColor(info).getDefaultColor());

      info.setVizMark(VizMark.MODERN_DARK);
      seed(info);
      assertEquals(EDGE_DARK, chartColor(info).getDefaultColor());
   }

   @Test
   void aModernRadarTakesSlotOne() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(radar());
      seed(info);
      assertEquals(MODERN_1, chartColor(info).getDefaultColor());
   }

   @Test
   void aTreeConvertedToRadarTakesSlotOne() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(tree());
      seed(info);
      assertEquals(EDGE, chartColor(info).getDefaultColor(), "premise: the edges are grey");

      ChartInfo converted = new ChangeChartTypeProcessor(
         GraphTypes.CHART_TREE, GraphTypes.CHART_RADAR, false, false, null,
         info.getVSChartInfo(), false, info.getChartDescriptor(), VizContext.of(info)).process();
      info.setVSChartInfo((VSChartInfo) converted);
      info.reseedIfResolvedTypeChanged();

      assertEquals(MODERN_1, chartColor(info).getDefaultColor());
   }

   @Test
   void aModernPolygonMapTakesTheTint() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      VSMapInfo map = polygonMap();
      info.setVSChartInfo(map);
      assertTrue(ResolvedChartType.of(map).mapPolygonOnly(), "premise: polygons only");
      seed(info);
      assertEquals(TINT, chartColor(info).getDefaultColor());
   }

   @Test
   void addingPointsMovesTheChartColourToSlotOne() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      VSMapInfo map = polygonMap();
      info.setVSChartInfo(map);
      seed(info);
      assertEquals(TINT, chartColor(info).getDefaultColor(), "premise: the regions are tinted");

      map.addGeoField(geo("City", CITY_LAYER));
      info.reseedIfResolvedTypeChanged();
      assertEquals(MODERN_1, chartColor(info).getDefaultColor());

      map.removeGeoField(1);
      info.reseedIfResolvedTypeChanged();
      assertEquals(TINT, chartColor(info).getDefaultColor());
   }

   @Test
   void aSetMapPropertyBeatsTheTint() {
      SreeEnv.setProperty("map.default.color", "#112233");
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(polygonMap());
      seed(info);
      assertEquals(new Color(0x112233), chartColor(info).getDefaultColor());
   }

   @Test
   void revertRestoresTheClassicColours() {
      ChartVSAssemblyInfo tree = newChart();
      tree.setVSChartInfo(tree());
      assertEquals(LEGACY_1, chartColor(tree).getDefaultColor(), "premise: the classic edges");
      modernize(tree);
      assertEquals(EDGE, chartColor(tree).getDefaultColor(), "premise: modernized");
      revert(tree);
      assertEquals(LEGACY_1, chartColor(tree).getDefaultColor());

      ChartVSAssemblyInfo map = newChart();
      map.setVSChartInfo(polygonMap());
      chartColor(map).setDefaultColor(PALE_ORANGE);
      modernize(map);
      assertEquals(TINT, chartColor(map).getDefaultColor(), "premise: modernized");
      revert(map);
      assertEquals(PALE_ORANGE, chartColor(map).getDefaultColor());
   }

   @Test
   void aRadarKeepsItsPalettePositionThroughRevert() {
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(radar());
      chartColor(info).setDefaultColor(LEGACY_2);
      modernize(info);
      assertEquals(MODERN_2, chartColor(info).getDefaultColor(), "premise: modernized");
      revert(info);
      assertEquals(LEGACY_2, chartColor(info).getDefaultColor());
   }

   @Test
   void anAuthorEdgeColourIsLeftAlone() {
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(tree());
      chartColor(info).setUserColor(Color.RED);

      modernize(info);
      assertEquals(EDGE, chartColor(info).getDefaultColor(), "premise: the seed ran");
      assertEquals(Color.RED, chartColor(info).getUserColor());
      assertEquals(Color.RED, chartColor(info).getColor());

      revert(info);
      assertEquals(Color.RED, chartColor(info).getUserColor());
      assertEquals(LEGACY_1, chartColor(info).getDefaultColor());
   }

   @Test
   void aClassicMapsXmlIsUnchanged() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(polygonMap());
      assertEquals(LEGACY_1, chartColor(info).getDefaultColor(), "premise: an old file's colour");
      String before = writeXml(info);
      seed(info);
      assertEquals(before, writeXml(info));
   }

   @Test
   void aModernMapsNoDataColourIsTheGridColour() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(polygonMap());
      seed(info);
      assertEquals(GRID, emptyColor(info).get(CompositeValue.Type.DEFAULT));
      assertFalse(emptyColor(info).hasUserValue());

      info.setVizMark(VizMark.MODERN_DARK);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(GRID_DARK, emptyColor(info).get(CompositeValue.Type.DEFAULT));
   }

   @Test
   void revertClearsTheSeededNoDataColour() {
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(polygonMap());
      modernize(info);
      assertEquals(GRID, emptyColor(info).get(CompositeValue.Type.DEFAULT), "premise: seeded");
      revert(info);
      assertNull(emptyColor(info).get(CompositeValue.Type.DEFAULT));
   }

   @Test
   void aClassicChartSavesNoNoDataColour() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(polygonMap());
      String before = writeXml(info);
      seed(info);
      assertNull(emptyColor(info).get(CompositeValue.Type.DEFAULT));
      assertEquals(before, writeXml(info));
   }

   @Test
   void anAuthorNoDataColourSurvivesAReseed() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(polygonMap());
      info.getChartDescriptor().getPlotDescriptor().setEmptyColor(Color.RED);
      seed(info);
      assertEquals(Color.RED, info.getChartDescriptor().getPlotDescriptor().getEmptyColor());
      assertEquals(Color.RED, info.getChartDescriptor().getPlotDescriptor().getAuthoredEmptyColor());
   }

   @Test
   void theParetoLineColourTravelsAndReverts() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      modernize(info);
      CompositeValue<?> line = info.getChartDescriptor().getPlotDescriptor()
         .chromeValue("paretoLineColor");
      assertEquals(MODERN_2, line.get(CompositeValue.Type.DEFAULT));
      assertTrue(writeXml(info).contains(Integer.toString(MODERN_2.getRGB())),
                 "the default tier is saved");
      revert(info);
      assertEquals(LEGACY_1, line.get(CompositeValue.Type.DEFAULT));
      assertFalse(writeXml(info).contains(Integer.toString(MODERN_2.getRGB())));
   }

   @Test
   void aClassicChartSavesNoParetoLineColour() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      String before = writeXml(info);
      seed(info);
      assertEquals(before, writeXml(info));
   }

   @Test
   void modernizeTurnsAContoursBluesTeal() {
      ChartVSAssemblyInfo info = newChart();
      info.getVSChartInfo().setChartType(GraphTypes.CHART_SCATTER_CONTOUR);
      info.getVSChartInfo().setColorFrameWrapper(new BluesColorFrameWrapper());

      info.seedChromeDefaults(VizContext.ofTransition(null, VizMark.MODERN_LIGHT));

      assertInstanceOf(TealColorFrame.class, info.getVSChartInfo().getColorFrame());
   }

   @Test
   void revertTurnsItBack() {
      ChartVSAssemblyInfo info = newChart();
      info.getVSChartInfo().setChartType(GraphTypes.CHART_SCATTER_CONTOUR);
      info.getVSChartInfo().setColorFrameWrapper(new TealColorFrameWrapper());

      info.seedChromeDefaults(VizContext.ofTransition(null, null));

      assertInstanceOf(BluesColorFrame.class, info.getVSChartInfo().getColorFrame());
   }

   @Test
   void anAuthorsSpectralContourIsKept() {
      ChartVSAssemblyInfo info = newChart();
      info.getVSChartInfo().setChartType(GraphTypes.CHART_SCATTER_CONTOUR);
      info.getVSChartInfo().setColorFrameWrapper(new SpectralColorFrameWrapper());

      info.seedChromeDefaults(VizContext.ofTransition(null, VizMark.MODERN_LIGHT));

      assertInstanceOf(SpectralColorFrame.class, info.getVSChartInfo().getColorFrame());
   }

   @Test
   void aRestoreNeverReplacesAContoursRamp() {
      ChartVSAssemblyInfo info = newChart();
      info.getVSChartInfo().setChartType(GraphTypes.CHART_SCATTER_CONTOUR);
      info.getVSChartInfo().setColorFrameWrapper(new BluesColorFrameWrapper());
      info.setVizMark(VizMark.MODERN_LIGHT);

      info.seedChromeDefaults(VizContext.of(info));

      assertInstanceOf(BluesColorFrame.class, info.getVSChartInfo().getColorFrame());
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "bar", "tree", "polygon map", "pareto", "scatter contour" })
   void modernizeThenRevertKeepsTheChartXml(String kind) {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      bindClassic(info, kind);
      assertNull(info.getVizMark(), "premise: a classic chart");
      String before = chartXml(info);

      gateOn();
      VizModernizeUtil.modernize(vs);
      assertEquals(VizMark.MODERN_LIGHT, info.getVizMark(), "premise: modernized");
      assertNotEquals(before, chartXml(info), "premise: Modernize moved a colour");

      VizModernizeUtil.revert(vs);
      assertEquals(before, chartXml(info));
   }

   // a classic chart of each kind, holding the colours a classic binding leaves
   private static void bindClassic(ChartVSAssemblyInfo info, String kind) {
      VSChartInfo cinfo = info.getVSChartInfo();

      switch(kind) {
      case "bar":
         VSChartAggregateRef second = agg("w");
         ((StaticColorFrameWrapper) second.getColorFrameWrapper()).setDefaultColor(LEGACY_2);
         cinfo.addXField(dim("state"));
         cinfo.addYField(agg("v"));
         cinfo.addYField(second);
         cinfo.setChartType(GraphTypes.CHART_BAR);
         break;
      case "tree":
         info.setVSChartInfo(tree());
         break;
      case "polygon map":
         info.setVSChartInfo(polygonMap());
         // the region colour a classic binding writes over slot one
         chartColor(info).setDefaultColor(PALE_ORANGE);
         break;
      case "pareto":
         cinfo.addXField(dim("state"));
         cinfo.addYField(agg("v"));
         cinfo.setChartType(GraphTypes.CHART_PARETO);
         break;
      case "scatter contour":
         cinfo.addXField(agg("w"));
         cinfo.addYField(agg("v"));
         cinfo.setChartType(GraphTypes.CHART_SCATTER_CONTOUR);
         cinfo.setColorFrameWrapper(new BluesColorFrameWrapper());
         break;
      default:
         fail("unknown chart kind " + kind);
      }
   }

   // the chart info and the chart descriptor as saved
   private static String chartXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.getVSChartInfo().writeXML(writer);
      info.getChartDescriptor().writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static CompositeValue<?> emptyColor(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getPlotDescriptor().chromeValue("emptyColor");
   }

   // ---- harness -----------------------------------------------------------------------------

   private static StaticColorFrameWrapper chartColor(ChartVSAssemblyInfo info) {
      return (StaticColorFrameWrapper) info.getVSChartInfo().getColorFrameWrapper();
   }

   // parent to child
   private static RelationVSChartInfo tree() {
      RelationVSChartInfo tree = new RelationVSChartInfo();
      tree.setSourceField(dim("parent"));
      tree.setTargetField(dim("child"));
      tree.setChartType(GraphTypes.CHART_TREE);
      return tree;
   }

   // state by Sum(v)
   private static RadarVSChartInfo radar() {
      RadarVSChartInfo radar = new RadarVSChartInfo();
      radar.addXField(dim("state"));
      radar.addYField(agg("v"));
      radar.setChartType(GraphTypes.CHART_RADAR);
      return radar;
   }

   // a state map with no point layer and no anchor points
   private static VSMapInfo polygonMap() {
      VSMapInfo map = new VSMapInfo();
      map.setMeasureMapType("U.S.");
      map.addGeoField(geo("State", STATE_LAYER));
      ((StaticShapeFrame) map.getShapeFrame()).setShape(GShape.NIL);
      map.setChartType(GraphTypes.CHART_MAP);
      return map;
   }

   private static VSChartGeoRef geo(String name, String layer) {
      VSChartGeoRef geo = new VSChartGeoRef(new AttributeRef(null, name));
      geo.setGroupColumnValue(name);
      geo.setDataType(XSchema.STRING);
      geo.getGeographicOption().setLayerValue(layer);
      return geo;
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

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
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

   private static void modernize(ChartVSAssemblyInfo info) {
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
   }

   private static void revert(ChartVSAssemblyInfo info) {
      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of(info));
   }

   private static String writeXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }
}
