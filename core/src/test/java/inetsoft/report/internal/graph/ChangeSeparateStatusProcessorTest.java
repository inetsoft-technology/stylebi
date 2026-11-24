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
package inetsoft.report.internal.graph;

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Switching a chart between one shared axis / one style and separate axes / one style per
 * measure ({@link ChangeSeparateStatusProcessor}) moves axis attributes, text formats and
 * legend descriptors between the chart level and the individual measures. The expected
 * values come from the comments in the processor: "all global axis attributes should be
 * copied to each individual aggregates axes" when separated, the first measure's axis
 * becomes the global axis when not, and "copy from plot to aggr" / "copy from aggr to plot"
 * for the text format and legends when the multi-style flag changes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChangeSeparateStatusProcessorTest {
   private VSChartInfo info;
   private ChartDescriptor cdesc;
   private VSChartAggregateRef sales;
   private VSChartAggregateRef profit;

   @BeforeEach
   void setUp() {
      info = new DefaultVSChartInfo();
      // a new chart info starts separated; start from the shared-axis state
      info.setSeparatedGraph(false);
      cdesc = new ChartDescriptor();
      sales = measure("Sales");
      profit = measure("Profit");
      info.addYField(sales);
      info.addYField(profit);
   }

   private static VSChartAggregateRef measure(String column) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new ColumnRef(new AttributeRef(null, column)));
      return ref;
   }

   private void process(boolean separated, boolean multi) {
      new ChangeSeparateStatusProcessor(info, cdesc).process(separated, multi);
   }

   private static void setScale(AxisDescriptor axis, double min, double max, double inc) {
      axis.setMinimum(min);
      axis.setMaximum(max);
      axis.setIncrement(inc);
   }

   @Test
   void flagsFollowTheArguments() {
      process(true, true);

      assertTrue(info.isSeparatedGraph());
      assertTrue(info.isMultiStyles());

      process(false, false);

      assertFalse(info.isSeparatedGraph());
      assertFalse(info.isMultiStyles());
   }

   @Test
   void separatingCopiesTheGlobalAxisAttributesToEveryMeasure() {
      AxisDescriptor global = info.getAxisDescriptor();
      setScale(global, 10, 100, 20);
      boolean measureDefault = profit.getAxisDescriptor().isTicksVisible();
      global.setTicksVisible(!measureDefault);
      global.setLineColor(Color.RED);

      process(true, false);

      for(VSChartAggregateRef ref : new VSChartAggregateRef[] { sales, profit }) {
         AxisDescriptor axis = ref.getAxisDescriptor();
         assertEquals(10.0, axis.getMinimum().doubleValue(), ref.getFullName() + " minimum");
         assertEquals(100.0, axis.getMaximum().doubleValue(), ref.getFullName() + " maximum");
         assertEquals(20.0, axis.getIncrement().doubleValue(), ref.getFullName() + " increment");
         assertEquals(!measureDefault, axis.isTicksVisible(), ref.getFullName() + " ticks");
         assertEquals(Color.RED, axis.getLineColor(), ref.getFullName() + " line color");
      }
   }

   /** "keep the y axis settings/format": the first measure takes over the global descriptor. */
   @Test
   void separatingGivesTheFirstMeasureTheGlobalDescriptor() {
      process(true, false);

      assertSame(info.getAxisDescriptor(), sales.getAxisDescriptor());
      assertNotSame(info.getAxisDescriptor(), profit.getAxisDescriptor());
   }

   @Test
   void unseparatingTakesTheAxisOfTheFirstMeasure() {
      info.setSeparatedGraph(true);
      AxisDescriptor own = new AxisDescriptor();
      setScale(own, 5, 50, 5);
      sales.setAxisDescriptor(own);
      setScale(profit.getAxisDescriptor(), 1, 9, 1);
      setScale(info.getAxisDescriptor(), 10, 100, 20);

      process(false, false);

      AxisDescriptor global = info.getAxisDescriptor();
      assertEquals(5.0, global.getMinimum().doubleValue());
      assertEquals(50.0, global.getMaximum().doubleValue());
      assertEquals(5.0, global.getIncrement().doubleValue());
   }

   @Test
   void unchangedSeparateFlagLeavesTheAxesAlone() {
      setScale(info.getAxisDescriptor(), 10, 100, 20);
      setScale(sales.getAxisDescriptor(), 7, 70, 7);

      process(false, false);

      assertEquals(10.0, info.getAxisDescriptor().getMinimum().doubleValue());
      assertEquals(7.0, sales.getAxisDescriptor().getMinimum().doubleValue());
   }

   /** Separating a chart that has no measure on the axis must not fail. */
   @Test
   void separatingAChartWithoutMeasuresIsHarmless() {
      info.removeYFields();
      setScale(info.getAxisDescriptor(), 10, 100, 20);

      process(true, false);

      assertTrue(info.isSeparatedGraph());
      assertEquals(10.0, info.getAxisDescriptor().getMinimum().doubleValue());
   }

   /**
    * Format is not copied by the attribute copy ("Format is intentionally omitted"); it is kept
    * because the first measure's descriptor becomes the global descriptor.
    */
   @Test
   void unseparatingKeepsTheNumberFormatOfTheFirstMeasureAxis() {
      info.setSeparatedGraph(true);
      XFormatInfo format = new XFormatInfo(XConstants.DECIMAL_FORMAT, "#,##0.0");
      sales.getAxisDescriptor().getAxisLabelTextFormat().getUserDefinedFormat().setFormat(format);

      process(false, false);

      assertEquals(format,
                   info.getAxisDescriptor().getAxisLabelTextFormat().getUserDefinedFormat().getFormat());
   }

   /** Bug 74013: runtime refs must get the updated design-time axis descriptors. */
   @Test
   void separatingSyncsTheRuntimeRefsWithTheDesignTimeAxisDescriptors() throws Exception {
      VSChartAggregateRef runtimeSales = (VSChartAggregateRef) sales.clone();
      info.setRTYFields(new ChartRef[] { runtimeSales });
      AxisDescriptor stale = runtimeSales.getAxisDescriptor();
      setScale(info.getAxisDescriptor(), 10, 100, 20);

      process(true, false);

      AxisDescriptor runtimeAxis = info.getRTYFields()[0].getAxisDescriptor();
      assertSame(sales.getAxisDescriptor(), runtimeAxis);
      assertNotSame(stale, runtimeAxis);
   }

   @Test
   void multiStylesCopiesThePlotTextFormatToEachMeasure() {
      CompositeTextFormat plotFormat = cdesc.getPlotDescriptor().getTextFormat();
      plotFormat.getUserDefinedFormat().setColor(Color.BLUE);

      process(false, true);

      for(VSChartAggregateRef ref : new VSChartAggregateRef[] { sales, profit }) {
         CompositeTextFormat format = ref.getTextFormat();
         assertEquals(Color.BLUE, format.getUserDefinedFormat().getColor(),
                      ref.getFullName() + " text color");
         assertNotSame(plotFormat, format, ref.getFullName() + " must get its own copy");
      }
   }

   @Test
   void leavingMultiStylesCopiesTheFirstMeasureTextFormatToThePlot() {
      info.setMultiStyles(true);
      CompositeTextFormat format = new CompositeTextFormat();
      format.getUserDefinedFormat().setColor(Color.GREEN);
      sales.setTextFormat(format);

      process(false, false);

      assertEquals(Color.GREEN,
                   cdesc.getPlotDescriptor().getTextFormat().getUserDefinedFormat().getColor());
   }

   @Test
   void multiStylesCopiesTheColorLegendToTheColorField() {
      LegendDescriptor colorLegend = new LegendDescriptor();
      colorLegend.setVisible(false);
      cdesc.getLegendsDescriptor().setColorLegendDescriptor(colorLegend);
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "Region"))));
      sales.setColorField(color);

      process(false, true);

      LegendDescriptor copy = sales.getColorField().getLegendDescriptor();
      assertNotNull(copy);
      assertFalse(copy.isVisible());
      assertNotSame(colorLegend, copy);
   }

   /**
    * The shape and size fields are given the legend settings of their own legend, as the
    * reverse direction does (leaving multi styles reads the shape and size legend
    * descriptors of the fields back into the shape and size legends).
    */
   @Test
   @Disabled("Not filed (in the code since the initial import, nobody reported it): switching to "
      + "multiple styles gives the shape and size fields a clone of the COLOR legend descriptor "
      + "(legends.getColorLegendDescriptor() in all three branches); the other direction reads "
      + "the shape and size descriptors back into the shape and size legends")
   void multiStylesCopiesTheShapeAndSizeLegendsToTheirOwnFields() {
      LegendsDescriptor legends = cdesc.getLegendsDescriptor();
      LegendDescriptor colorLegend = new LegendDescriptor();
      colorLegend.setVisible(false);
      LegendDescriptor shapeLegend = new LegendDescriptor();
      shapeLegend.setVisible(true);
      LegendDescriptor sizeLegend = new LegendDescriptor();
      sizeLegend.setVisible(true);
      legends.setColorLegendDescriptor(colorLegend);
      legends.setShapeLegendDescriptor(shapeLegend);
      legends.setSizeLegendDescriptor(sizeLegend);

      VSAestheticRef shape = new VSAestheticRef();
      shape.setDataRef(new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "Region"))));
      VSAestheticRef size = new VSAestheticRef();
      size.setDataRef(new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "Quantity"))));
      sales.setShapeField(shape);
      sales.setSizeField(size);

      process(false, true);

      assertTrue(sales.getShapeField().getLegendDescriptor().isVisible(),
                 "the shape field must get the shape legend (visible), not the color legend");
      assertTrue(sales.getSizeField().getLegendDescriptor().isVisible(),
                 "the size field must get the size legend (visible), not the color legend");
   }
}
