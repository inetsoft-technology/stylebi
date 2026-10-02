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
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.internal.GDefaults;
import inetsoft.report.StyleFont;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77597 (finding 5a): after an object (whole-chart) font is set, rendered and then reset,
 * {@code VGraphPair.fixChartFormat} must reset the default layer of every per-column axis label
 * format ({@code colFmt}) back to the axis default. Before the fix the separated-axis loop only
 * copied the axis default font while the colFmt font was still a stock default, so a pushed object
 * font stuck forever; the radar label-axis loop reset neither the font nor the colour.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VGraphPairColumnLabelFormatResetTest {
   private static final Font OBJECT_FONT = new StyleFont("Arial", Font.BOLD, 14);
   private static final String OBJECT_COLOR = "#d00000";

   @Test
   void separatedMeasureAxisColumnLabelFontResetsAfterObjectFontIsCleared() throws Exception {
      // binding creates the measure's per-column label format by cloning the axis label format
      ChartVSAssemblyInfo info = separatedBarChart();
      VSChartAggregateRef revenue = (VSChartAggregateRef) info.getVSChartInfo().getYField(0);
      AxisDescriptor yAxis = revenue.getAxisDescriptor();
      CompositeTextFormat colFmt = yAxis.getColumnLabelTextFormat(revenue.getFullName());
      assertNotNull(colFmt, "binding should create the Y column label format");

      fixChartFormat(info);
      Font initialFont = colFmt.getDefaultFormat().getFont();
      Color initialColor = colFmt.getDefaultFormat().getColor();

      setObjectFormat(info);
      fixChartFormat(info);
      assertEquals(OBJECT_FONT, colFmt.getDefaultFormat().getFont());

      resetObjectFormat(info);
      fixChartFormat(info);

      Font axisFont = yAxis.getAxisLabelTextFormat().getDefaultFormat().getFont();
      assertEquals(axisFont, colFmt.getDefaultFormat().getFont());
      assertEquals(initialFont, colFmt.getDefaultFormat().getFont());
      assertEquals(initialColor, colFmt.getDefaultFormat().getColor());
   }

   @Test
   void radarLabelAxisColumnLabelFontAndColorResetAfterObjectFormatIsCleared() throws Exception {
      RadarVSChartInfo cinfo = new RadarVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_RADAR);

      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(cinfo);

      // created the way FormatPainterService creates it for the radar label axis
      AxisDescriptor labelAxis = cinfo.getLabelAxisDescriptor();
      CompositeTextFormat colFmt = new CompositeTextFormat();
      labelAxis.setColumnLabelTextFormat("_Parallel_Label_", colFmt);

      fixChartFormat(info);
      Font initialFont = colFmt.getDefaultFormat().getFont();
      Color initialColor = colFmt.getDefaultFormat().getColor();

      setObjectFormat(info);
      fixChartFormat(info);
      assertEquals(OBJECT_FONT, colFmt.getDefaultFormat().getFont());
      assertEquals(Color.decode(OBJECT_COLOR), colFmt.getDefaultFormat().getColor());

      resetObjectFormat(info);
      fixChartFormat(info);

      Font axisFont = labelAxis.getAxisLabelTextFormat().getDefaultFormat().getFont();
      assertEquals(axisFont, colFmt.getDefaultFormat().getFont());
      assertEquals(initialFont, colFmt.getDefaultFormat().getFont());
      assertEquals(initialColor, colFmt.getDefaultFormat().getColor());
   }

   @Test
   void renderedMeasureAxisTickLabelsUseDefaultFontAfterObjectFontIsCleared() throws Exception {
      ChartVSAssemblyInfo info = separatedBarChart();
      fixChartFormat(info);
      Font initialFont = singleFont(yTickLabels(info));

      setObjectFormat(info);
      fixChartFormat(info);
      assertEquals(OBJECT_FONT, singleFont(yTickLabels(info)));

      resetObjectFormat(info);
      fixChartFormat(info);
      List<VLabel> labels = yTickLabels(info);
      assertEquals(initialFont, singleFont(labels));
      assertEquals(GDefaults.DEFAULT_TEXT_COLOR, labels.get(0).getTextSpec().getColor());
   }

   @Test
   void userColumnLabelFontStillWinsOverObjectFontAndReset() throws Exception {
      ChartVSAssemblyInfo info = separatedBarChart();
      VSChartAggregateRef revenue = (VSChartAggregateRef) info.getVSChartInfo().getYField(0);
      Font userFont = new StyleFont("Verdana", Font.ITALIC, 12);
      revenue.getAxisDescriptor().getColumnLabelTextFormat(revenue.getFullName())
         .getUserDefinedFormat().setFont(userFont);

      setObjectFormat(info);
      fixChartFormat(info);
      assertEquals(userFont, singleFont(yTickLabels(info)));

      resetObjectFormat(info);
      fixChartFormat(info);
      assertEquals(userFont, singleFont(yTickLabels(info)));
   }

   private static ChartVSAssemblyInfo separatedBarChart() {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      VSChartDimensionRef region = new VSChartDimensionRef(new AttributeRef("Region"));
      VSChartAggregateRef revenue = new VSChartAggregateRef();
      revenue.setDataRef(new AttributeRef("Revenue"));
      revenue.setFormula(AggregateFormula.SUM);
      cinfo.addXField(region);
      cinfo.addYField(revenue);
      cinfo.setRTXFields(new ChartRef[] { region });
      cinfo.setRTYFields(new ChartRef[] { revenue });

      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(cinfo);
      assertTrue(cinfo.isSeparatedGraph());
      GraphFormatUtil.fixDefaultNumberFormat(info.getChartDescriptor(), cinfo);
      assertNotNull(revenue.getAxisDescriptor().getColumnLabelTextFormat(revenue.getFullName()));
      return info;
   }

   // the tick labels of the Sum(Revenue) axis in the real generated and laid-out VGraph
   private static List<VLabel> yTickLabels(ChartVSAssemblyInfo info) {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "Region", "Sum(Revenue)" }, { "East", 1000.0 }, { "West", 2500.0 }, { "North", 1800.0 }
      });
      GraphGenerator gen = GraphGenerator.getGenerator(info, null, data, new VariableTable(),
                                                       null, 0, new Dimension(400, 300));
      EGraph egraph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(gen.getData(), 0, 0, 400, 300);
      List<VLabel> labels = new ArrayList<>();

      for(Axis axis : vgraph.getCoordinate().getAxes(true)) {
         if(Arrays.asList(axis.getScale().getFields()).contains("Sum(Revenue)")) {
            Arrays.stream(axis.getLabels()).filter(Objects::nonNull).forEach(labels::add);
         }
      }

      assertFalse(labels.isEmpty(), "the measure axis should render tick labels");
      return labels;
   }

   private static Font singleFont(List<VLabel> labels) {
      Set<Font> fonts = new HashSet<>();
      labels.forEach(label -> fonts.add(label.getFont()));
      assertEquals(1, fonts.size(), "all tick labels should share one font: " + fonts);
      return fonts.iterator().next();
   }

   // set and clear the OBJECT user format directly. This leaves the same state as
   // FormatPainterService.changeFormat: setUserFormat writes setFontValue(font, !reset), so after a
   // reset the font and foreground values are undefined.
   private static void setObjectFormat(ChartVSAssemblyInfo info) {
      VSFormat user = objectUserFormat(info);
      user.setFontValue(OBJECT_FONT, true);
      user.setForegroundValue(OBJECT_COLOR, true);
   }

   private static void resetObjectFormat(ChartVSAssemblyInfo info) {
      VSFormat user = objectUserFormat(info);
      user.setFontValue(null, false);
      user.setForegroundValue(null, false);
   }

   private static VSFormat objectUserFormat(ChartVSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH).getUserDefinedFormat();
   }

   private static void fixChartFormat(ChartVSAssemblyInfo info) throws Exception {
      Method method = VGraphPair.class.getDeclaredMethod("fixChartFormat",
                                                         ChartVSAssemblyInfo.class);
      method.setAccessible(true);
      method.invoke(new VGraphPair(), info);
   }
}
