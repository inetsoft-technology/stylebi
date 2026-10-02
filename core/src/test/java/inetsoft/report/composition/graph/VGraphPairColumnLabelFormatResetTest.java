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

import inetsoft.report.StyleFont;
import inetsoft.test.*;
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
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      VSChartDimensionRef region = new VSChartDimensionRef(new AttributeRef("Region"));
      VSChartAggregateRef revenue = new VSChartAggregateRef();
      revenue.setDataRef(new AttributeRef("Revenue"));
      revenue.setFormula(AggregateFormula.SUM);
      cinfo.addXField(region);
      cinfo.addYField(revenue);

      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(cinfo);
      assertTrue(cinfo.isSeparatedGraph());

      // binding creates the measure's per-column label format by cloning the axis label format
      GraphFormatUtil.fixDefaultNumberFormat(info.getChartDescriptor(), cinfo);
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
