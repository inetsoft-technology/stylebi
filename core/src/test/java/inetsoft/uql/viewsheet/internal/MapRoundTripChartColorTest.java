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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.report.internal.graph.ChangeChartTypeProcessor;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.TealColorFrameWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked chart that goes bar, map, bar through the type picker ends on the palette's first
 * colour, not on the classic one the map conversion restores.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MapRoundTripChartColorTest {
   private static final Color MODERN_1 = new Color(0x0490FF);
   private static final Color MODERN_2 = new Color(0xFF5A35);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("map.default.color", null);
      VSChartPaletteDefaults.clearMemo();
   }

   @Test
   void aModernBarKeepsItsColourThroughAMapAndBack() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setVSChartInfo(bar());
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(MODERN_1, chartColor(info).getDefaultColor(), "premise: a modern bar");
      assertEquals(List.of(MODERN_1), measureColors(info), "premise: the measure is modern too");

      toMap(info);
      assertNotNull(info.getVSChartInfo().getColorField(), "premise: the map is shaded by a measure");

      toBar(info);
      assertEquals(MODERN_1, chartColor(info).getDefaultColor());
      // the measure that shaded the map comes back as a new ref, which starts on the classic slot
      assertEquals(List.of(MODERN_1), measureColors(info));
   }

   @Test
   void aChosenMeasureColourSurvivesATypeChange() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setVSChartInfo(bar());
      info.seedChromeDefaults(VizContext.of(info));
      ChartAggregateRef ref = (ChartAggregateRef) info.getVSChartInfo().getBindingRefs(false)[1];
      ((StaticColorFrameWrapper) ref.getColorFrameWrapper()).setDefaultColor(MODERN_2);

      VSChartInfo old = info.getVSChartInfo();
      info.setVSChartInfo((VSChartInfo) new ChangeChartTypeProcessor(
         GraphTypes.CHART_BAR, GraphTypes.CHART_LINE, false, false, null, old, false,
         info.getChartDescriptor(), VizContext.of(info)).process());
      info.reseedIfResolvedTypeChanged();

      assertEquals(List.of(MODERN_2), measureColors(info));
   }

   private static List<Color> measureColors(ChartVSAssemblyInfo info) {
      List<Color> colors = new ArrayList<>();

      for(ChartRef ref : info.getVSChartInfo().getBindingRefs(false)) {
         if(ref instanceof ChartAggregateRef) {
            ColorFrameWrapper wrapper = ((ChartAggregateRef) ref).getColorFrameWrapper();

            if(wrapper instanceof StaticColorFrameWrapper) {
               colors.add(((StaticColorFrameWrapper) wrapper).getDefaultColor());
            }
         }
      }

      return colors;
   }

   // the type picker, as ChangeChartTypeService runs it
   private static void toMap(ChartVSAssemblyInfo info) {
      VSChartInfo old = info.getVSChartInfo();
      VSChartInfo converted = (VSChartInfo) new ChangeChartTypeProcessor(
         GraphTypes.CHART_BAR, GraphTypes.CHART_MAP, false, false, null, old, false,
         info.getChartDescriptor(), VizContext.of(info)).process();
      assertInstanceOf(VSMapInfo.class, converted, "premise: a map");
      ((VSMapInfo) converted).addGeoField(geo());
      // the map shades its regions by the measure, so the static colour gives way to a ramp
      VSAestheticRef shade = new VSAestheticRef();
      shade.setDataRef(agg("v"));
      shade.setVisualFrameWrapper(new TealColorFrameWrapper());
      converted.setColorField(shade);
      info.setVSChartInfo(converted);
      new ChangeChartProcessor().fixMapFrame(old, converted);
      info.reseedIfResolvedTypeChanged();
   }

   private static void toBar(ChartVSAssemblyInfo info) {
      VSChartInfo old = info.getVSChartInfo();
      VSChartInfo converted = (VSChartInfo) new ChangeChartTypeProcessor(
         GraphTypes.CHART_MAP, GraphTypes.CHART_BAR, false, false, null, old, false,
         info.getChartDescriptor(), VizContext.of(info)).process();
      info.setVSChartInfo(converted);
      new ChangeChartProcessor().fixMapFrame(old, converted);
      info.reseedIfResolvedTypeChanged();
   }

   private static StaticColorFrameWrapper chartColor(ChartVSAssemblyInfo info) {
      return (StaticColorFrameWrapper) info.getVSChartInfo().getColorFrameWrapper();
   }

   private static VSChartInfo bar() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addXField(dim("state"));
      info.addYField(agg("v"));
      info.setChartType(GraphTypes.CHART_BAR);
      return info;
   }

   private static VSChartGeoRef geo() {
      VSChartGeoRef geo = new VSChartGeoRef(new AttributeRef(null, "state"));
      geo.setGroupColumnValue("state");
      geo.setDataType(XSchema.STRING);
      geo.getGeographicOption().setLayerValue("2");
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

   private static ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }
}
