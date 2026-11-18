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
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSChartChromeDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shape legend symbol colour on a marked point chart.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernShapeLegendTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedShapeLegendTakesTheSizeLegendFallback() {
      VizContext ctx = VizContext.of(VizMark.MODERN_LIGHT);
      LegendSpec spec = shapeLegend(VizMark.MODERN_LIGHT);

      assertNotNull(spec.getSymbolFallbackColor());
      assertEquals(VSChartChromeDefaults.labelColor(ctx), spec.getSymbolFallbackColor());
   }

   @Test
   void aClassicShapeLegendHasNoFallback() {
      LegendSpec spec = shapeLegend(null);

      assertNull(spec.getSymbolFallbackColor());
   }

   @Test
   void aMarkedTextureLegendKeepsItsSymbols() {
      LegendSpec spec = legend(VizMark.MODERN_LIGHT, GraphTypes.CHART_BAR,
                               new CategoricalTextureFrame(), false);

      assertNull(spec.getSymbolFallbackColor());
   }

   @Test
   void aMarkedLineLegendKeepsItsSymbols() {
      LegendSpec spec = legend(VizMark.MODERN_LIGHT, GraphTypes.CHART_LINE,
                               new CategoricalLineFrame(), false);

      assertNull(spec.getSymbolFallbackColor());
   }

   private static LegendSpec shapeLegend(VizMark mark) {
      return legend(mark, GraphTypes.CHART_POINT, new CategoricalShapeFrame(), true);
   }

   private static LegendSpec legend(VizMark mark, int type, VisualFrame frame, boolean isShape) {
      VSChartInfo cinfo = new VSChartInfo();
      VSChartAggregateRef price = agg("Price");
      cinfo.addXField(dim("State"));
      cinfo.addYField(price);
      VSAestheticRef shape = new VSAestheticRef();
      shape.setDataRef(dim("Region"));
      shape.setVisualFrame(frame);
      cinfo.setShapeField(shape);
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dim("State"));
      cinfo.setColorField(color);
      cinfo.setChartType(type);
      cinfo.setRTChartType(type);
      cinfo.updateChartType(false);
      Object[][] rows = {
         { "State", "Region", GraphUtil.getName(price) },
         { "CA", "West", 1.0 },
         { "NY", "East", 2.0 },
         { "TX", "South", 3.0 },
      };

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      EGraph graph = GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(rows),
                                                 new VariableTable(), null, XSourceInfo.NONE,
                                                 new Dimension(400, 400)).createEGraph();

      assertTrue(graph.getElementCount() > 0, "premise: an element");
      GraphElement elem = graph.getElement(0);
      VisualFrame found = isShape ? elem.getShapeFrame()
         : frame instanceof TextureFrame ? elem.getTextureFrame() : elem.getLineFrame();
      assertNotNull(found, "premise: the frame under test");
      return found.getLegendSpec();
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      BaseField field = new BaseField(name);
      field.setDataType(XSchema.DOUBLE);
      agg.setDataRef(field);
      agg.setFormulaValue("Sum");
      return agg;
   }
}
