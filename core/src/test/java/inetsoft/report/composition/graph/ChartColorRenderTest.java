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
package inetsoft.report.composition.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.LegendSpec;
import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PolygonElement;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.VariableTable;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The region colour of a map with points is drawn at render, so it follows the chart's mark there.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartColorRenderTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
      SreeEnv.setProperty("map.default.color", null);
      SreeEnv.setProperty("map.empty.color", null);
   }

   @Test
   void aModernMapWithPointsDrawsTheTintedRegions() {
      assertEquals(new Color(0xA6D0F0), regionColor(VizMark.MODERN_LIGHT));
   }

   @Test
   void aClassicMapWithPointsKeepsThePaleOrange() {
      assertEquals(new Color(0xFBD29A), regionColor(null));
   }

   @Test
   void theServerPropertyBeatsTheSeededNoDataColour() {
      SreeEnv.setProperty("map.empty.color", "#123456");
      assertEquals(new Color(0x123456), GraphUtil.getMapEmptyColor(plot(VizMark.MODERN_LIGHT)));
   }

   @Test
   void aModernMapDrawsTheSeededNoDataColourWithoutTheProperty() {
      assertEquals(new Color(0xD6D2C8), GraphUtil.getMapEmptyColor(plot(VizMark.MODERN_LIGHT)));
   }

   @Test
   void anAuthorNoDataColourBeatsTheProperty() {
      SreeEnv.setProperty("map.empty.color", "#123456");
      PlotDescriptor plot = plot(VizMark.MODERN_LIGHT);
      plot.setEmptyColor(Color.RED);
      assertEquals(Color.RED, GraphUtil.getMapEmptyColor(plot));
   }

   @Test
   void aClassicMapStillReadsTheProperty() {
      SreeEnv.setProperty("map.empty.color", "#123456");
      assertEquals(new Color(0x123456), GraphUtil.getMapEmptyColor(plot(null)));
   }

   @Test
   void aMalformedPropertyFallsThroughToTheSeed() {
      SreeEnv.setProperty("map.empty.color", "not-a-colour");
      assertEquals(new Color(0xD6D2C8), GraphUtil.getMapEmptyColor(plot(VizMark.MODERN_LIGHT)));
      assertEquals(GraphUtil.MAP_EMPTY_DATA_COLOR, GraphUtil.getMapEmptyColor(plot(null)));
   }

   @Test
   void aClearedAuthorColourStillShadowsTheCssColour() {
      PlotDescriptor plot = plot(null);
      plot.setEmptyColor(Color.GREEN, CompositeValue.Type.CSS);
      plot.setEmptyColor(null, CompositeValue.Type.USER);
      assertEquals(GraphUtil.MAP_EMPTY_DATA_COLOR, GraphUtil.getMapEmptyColor(plot));

      SreeEnv.setProperty("map.empty.color", "#123456");
      assertEquals(new Color(0x123456), GraphUtil.getMapEmptyColor(plot));
   }

   @Test
   void aModernSizeLegendNextToAColourFieldUsesTheLabelGrey() {
      LegendSpec spec = sizeLegendSpec(VizMark.MODERN_LIGHT);
      assertEquals(new Color(0x6A685F), spec.getSymbolFallbackColor());
      assertEquals(new Color(0xD6D2C8), spec.getSymbolBorderColor());
   }

   @Test
   void aClassicOneIsUnset() {
      LegendSpec spec = sizeLegendSpec(null);
      assertNull(spec.getSymbolFallbackColor());
      assertNull(spec.getSymbolBorderColor());
   }

   @Test
   void aModernShapeLineAndTextureLegendNextToAColourFieldUsesTheLabelGrey() {
      for(Kind kind : Kind.values()) {
         assertEquals(new Color(0x6A685F), otherFieldLegendSpec(VizMark.MODERN_LIGHT, kind)
            .getSymbolFallbackColor(), kind + " light");
         assertEquals(new Color(0xCAC4D0), otherFieldLegendSpec(VizMark.MODERN_DARK, kind)
            .getSymbolFallbackColor(), kind + " dark");
      }
   }

   @Test
   void aModernShapeLegendLeavesTheBorderUnset() {
      // those legend items never read the border colour
      assertNull(otherFieldLegendSpec(VizMark.MODERN_LIGHT, Kind.SHAPE).getSymbolBorderColor());
   }

   @Test
   void aClassicShapeLineAndTextureLegendIsUnset() {
      for(Kind kind : Kind.values()) {
         assertNull(otherFieldLegendSpec(null, kind).getSymbolFallbackColor(), kind.toString());
         assertNull(otherFieldLegendSpec(null, kind).getSymbolBorderColor(), kind.toString());
      }
   }

   private enum Kind { SHAPE, LINE, TEXTURE }

   // the legend spec of the frame bound to a second field, next to a colour bound to Region
   private static LegendSpec otherFieldLegendSpec(VizMark mark, Kind kind) {
      Object[][] rows = {
         { "State", "Region", "Segment", "Sum(Total)" },
         { "CA", "West", "A", 100.0 },
         { "NY", "East", "B", 200.0 },
         { "TX", "South", "C", 300.0 },
      };

      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField("Total"));
      total.setFormulaValue("Sum");

      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(new VSChartDimensionRef(new BaseField("Region")));
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      VSAestheticRef other = new VSAestheticRef();
      other.setDataRef(new VSChartDimensionRef(new BaseField("Segment")));

      VSChartInfo cinfo = new DefaultVSChartInfo();
      int type = kind == Kind.SHAPE ? GraphTypes.CHART_POINT :
         kind == Kind.LINE ? GraphTypes.CHART_LINE : GraphTypes.CHART_BAR;
      cinfo.setChartType(type);
      cinfo.setRTChartType(type);
      cinfo.addXField(new VSChartDimensionRef(new BaseField("State")));
      cinfo.addYField(total);
      cinfo.setColorField(color);

      switch(kind) {
      case SHAPE -> {
         other.setVisualFrameWrapper(new CategoricalShapeFrameWrapper());
         cinfo.setShapeField(other);
      }
      case LINE -> {
         other.setVisualFrameWrapper(new CategoricalLineFrameWrapper());
         cinfo.setShapeField(other);
      }
      default -> {
         other.setVisualFrameWrapper(new CategoricalTextureFrameWrapper());
         cinfo.setShapeField(other);
      }
      }

      cinfo.updateChartType(false);

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(assemblyInfo);

      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null).createEGraph();
      GraphElement elem = graph.getElement(0);
      VisualFrame frame = kind == Kind.SHAPE ? elem.getShapeFrame() :
         kind == Kind.LINE ? elem.getLineFrame() : elem.getTextureFrame();
      assertNotNull(frame, "premise: the element draws a " + kind + " frame");
      return frame.getLegendSpec();
   }

   // the size frame's legend spec of a point chart with a colour field and a size measure
   private static LegendSpec sizeLegendSpec(VizMark mark) {
      Object[][] rows = {
         { "State", "Region", "Sum(Total)", "Sum(Qty)" },
         { "CA", "West", 100.0, 5.0 },
         { "NY", "East", 200.0, 9.0 },
         { "TX", "South", 300.0, 2.0 },
      };

      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField("Total"));
      total.setFormulaValue("Sum");
      VSChartAggregateRef qty = new VSChartAggregateRef();
      qty.setDataRef(new BaseField("Qty"));
      qty.setFormulaValue("Sum");

      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(new VSChartDimensionRef(new BaseField("Region")));
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      VSAestheticRef size = new VSAestheticRef();
      size.setDataRef(qty);
      size.setVisualFrameWrapper(new LinearSizeFrameWrapper());

      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_POINT);
      cinfo.setRTChartType(GraphTypes.CHART_POINT);
      cinfo.addXField(new VSChartDimensionRef(new BaseField("State")));
      cinfo.addYField(total);
      cinfo.setColorField(color);
      cinfo.setSizeField(size);
      cinfo.updateChartType(false);

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(assemblyInfo);

      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null).createEGraph();
      SizeFrame frame = graph.getElement(0).getSizeFrame();
      assertNotNull(frame, "premise: the point draws a size frame");
      return frame.getLegendSpec();
   }

   private static PlotDescriptor plot(VizMark mark) {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(new VSMapInfo());
      info.setChartDescriptor(new ChartDescriptor());
      info.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(info);
      return info.getChartDescriptor().getPlotDescriptor();
   }

   // the colour the polygon element draws a region with data in
   private static Color regionColor(VizMark mark) {
      Object[][] rows = {
         { "State", "City" },
         { "California", "Los Angeles" },
         { "New York", "New York" },
         { "Texas", "Houston" },
      };

      VSMapInfo map = new VSMapInfo();
      map.setMeasureMapType("U.S.");

      for(VSChartGeoRef geo : new VSChartGeoRef[] { geo("State", "2"), geo("City", "1001") }) {
         map.addGeoField(geo);
         map.addRTGeoField(geo);
      }

      map.setChartType(GraphTypes.CHART_MAP);
      assertTrue(GraphUtil.containsMapPoint(map), "premise: the map has points");
      assertTrue(GraphUtil.containsMapPolygon(map), "premise: the map has regions");

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(map);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(assemblyInfo);

      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null).createEGraph();

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if(elem instanceof PolygonElement) {
            return ((GeoColorFrame) elem.getColorFrame()).getColor();
         }
      }

      return fail("premise: the map draws a polygon element");
   }

   private static VSChartGeoRef geo(String name, String layer) {
      VSChartGeoRef geo = new VSChartGeoRef(new AttributeRef(null, name));
      geo.setGroupColumnValue(name);
      geo.setDataType(XSchema.STRING);
      geo.getGeographicOption().setLayerValue(layer);
      return geo;
   }
}
