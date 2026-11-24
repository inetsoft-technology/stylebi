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
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Swapping the X and Y bindings ({@link SwapXYBindingProcessor}) exchanges the X and Y field
 * lists, the X / Y axis titles, the band colors and sizes, and renumbers the field references
 * in the tooltip. The expected values come from the comments in the processor ("swap band
 * color and size", "update reference to parameter value when x/y are swapped") and from the
 * meaning of a swap: whatever was on X is on Y afterwards and the other way round.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SwapXYBindingProcessorTest {
   private VSChartInfo info;
   private ChartDescriptor desc;

   @BeforeEach
   void setUp() {
      info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      desc = new ChartDescriptor();
   }

   private static VSChartDimensionRef dim(String column) {
      return new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, column)));
   }

   private static VSChartAggregateRef measure(String column) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new ColumnRef(new AttributeRef(null, column)));
      return ref;
   }

   private void swap() {
      new SwapXYBindingProcessor(info, desc).process();
   }

   private static List<String> names(ChartRef[] refs) {
      List<String> list = new ArrayList<>();

      for(ChartRef ref : refs) {
         list.add(ref.getName());
      }

      return list;
   }

   @Test
   void swapExchangesTheXAndYFields() {
      info.addXField(dim("Region"));
      info.addYField(measure("Sales"));

      swap();

      assertEquals(Arrays.asList("Sales"), names(info.getXFields()));
      assertEquals(Arrays.asList("Region"), names(info.getYFields()));
   }

   @Test
   void swapTwiceRestoresTheBindings() {
      info.addXField(dim("Region"));
      info.addXField(dim("State"));
      info.addYField(measure("Sales"));
      info.addYField(measure("Profit"));
      List<String> x = names(info.getXFields());
      List<String> y = names(info.getYFields());

      swap();
      swap();

      assertEquals(x, names(info.getXFields()));
      assertEquals(y, names(info.getYFields()));
   }

   @Test
   void swapKeepsTheFieldCounts() {
      info.addXField(dim("Region"));
      info.addXField(dim("State"));
      info.addXField(dim("City"));
      info.addYField(measure("Sales"));

      swap();

      assertEquals(1, info.getXFieldCount());
      assertEquals(3, info.getYFieldCount());
   }

   @Test
   void swapExchangesTheAxisTitles() {
      TitlesDescriptor titles = desc.getTitlesDescriptor();
      titles.getXTitleDescriptor().setTitleValue("X title");
      titles.getYTitleDescriptor().setTitleValue("Y title");
      titles.getX2TitleDescriptor().setTitleValue("X2 title");
      titles.getY2TitleDescriptor().setTitleValue("Y2 title");

      swap();

      assertEquals("Y title", titles.getXTitleDescriptor().getTitleValue());
      assertEquals("X title", titles.getYTitleDescriptor().getTitleValue());
      assertEquals("Y2 title", titles.getX2TitleDescriptor().getTitleValue());
      assertEquals("X2 title", titles.getY2TitleDescriptor().getTitleValue());
   }

   @Test
   void swapExchangesTheBandColorsAndSizes() {
      PlotDescriptor plot = desc.getPlotDescriptor();
      plot.setXBandColor(Color.RED);
      plot.setXBandSize(3);
      plot.setYBandColor(Color.BLUE);
      plot.setYBandSize(9);

      swap();

      assertEquals(Color.BLUE, plot.getXBandColor());
      assertEquals(9, plot.getXBandSize());
      assertEquals(Color.RED, plot.getYBandColor());
      assertEquals(3, plot.getYBandSize());
   }

   private String swappedTip(String tip, int xcount, int ycount) {
      for(int i = 0; i < xcount; i++) {
         info.addXField(dim("X" + i));
      }

      for(int i = 0; i < ycount; i++) {
         info.addYField(measure("Y" + i));
      }

      info.setToolTipValue(tip);
      swap();
      return info.getToolTipValue();
   }

   /** A tooltip placeholder {n} names the n-th field in X then Y order; the order flips. */
   @Test
   void tooltipReferencesFollowTheirFieldsToTheOtherAxis() {
      assertEquals("{1} and {0}", swappedTip("{0} and {1}", 1, 1));
   }

   @Test
   void tooltipReferencesAreRenumberedWhenTheAxesHaveDifferentCounts() {
      // 2 X fields (0, 1) and 1 Y field (2): afterwards Y is 0 and X are 1, 2
      assertEquals("{1}-{2}-{0}", swappedTip("{0}-{1}-{2}", 2, 1));
   }

   @Test
   void tooltipReferencesWithAFormatKeepTheFormat() {
      assertEquals("{1,#,##0.00} / {0}", swappedTip("{0,#,##0.00} / {1}", 1, 1));
   }

   @Test
   void tooltipWithoutReferencesIsLeftAlone() {
      assertEquals("plain text", swappedTip("plain text", 1, 1));
   }

   @Test
   void dynamicTooltipIsLeftAlone() {
      assertEquals("=field['Sales']", swappedTip("=field['Sales']", 1, 1));
   }

   /** A measure whose chart type cannot be inverted is not moved to the X axis. */
   @Test
   void measureOfATypeThatCannotBeInvertedIsDroppedFromTheNewXAxis() {
      info.setMultiStyles(true);
      info.addXField(dim("Region"));
      VSChartAggregateRef sales = measure("Sales");
      sales.setChartType(GraphTypes.CHART_BAR);
      VSChartAggregateRef radar = measure("Score");
      radar.setChartType(GraphTypes.CHART_RADAR);
      info.addYField(sales);
      info.addYField(radar);

      swap();

      assertEquals(Arrays.asList("Sales"), names(info.getXFields()));
      assertEquals(Arrays.asList("Region"), names(info.getYFields()));
   }

   /** The dropped measure may come first; the others must still land on the X axis. */
   @Test
   void droppedMeasureBeforeAKeptMeasureDoesNotBreakTheSwap() {
      info.setMultiStyles(true);
      info.addXField(dim("Region"));
      VSChartAggregateRef radar = measure("Score");
      radar.setChartType(GraphTypes.CHART_RADAR);
      VSChartAggregateRef sales = measure("Sales");
      sales.setChartType(GraphTypes.CHART_BAR);
      info.addYField(radar);
      info.addYField(sales);

      swap();

      assertEquals(Arrays.asList("Sales"), names(info.getXFields()));
      assertEquals(Arrays.asList("Region"), names(info.getYFields()));
   }

   // ---- neighbouring scenarios ----

   /** A reference in the format form {n,fmt} on the Y side is renumbered too. */
   @Test
   void tooltipReferencesOnTheYSideWithAFormatAreRenumbered() {
      assertEquals("{0,#.0}", swappedTip("{1,#.0}", 1, 1));
   }

   /** Two-digit field numbers: {1} must not be taken for a part of {11}. */
   @Test
   void tooltipReferencesWithTwoDigitNumbersAreRenumbered() {
      // 1 X field (0) and 11 Y fields (1 to 11): X field 0 -> 11, Y field k -> k - 1
      assertEquals("{11} {0} {10}", swappedTip("{0} {1} {11}", 1, 11));
   }

   @Test
   void tooltipReferenceToAFieldThatDoesNotExistIsLeftAlone() {
      assertEquals("{5}", swappedTip("{5}", 1, 1));
   }

   /** A script or variable tooltip is evaluated later; its text must not be rewritten. */
   @Test
   void scriptTooltipWithBracesIsLeftAlone() {
      assertEquals("={0}+{1}", swappedTip("={0}+{1}", 1, 1));
   }

   /** The field lists are ordered dimensions first (VComparator), whatever the input order. */
   @Test
   void swappedFieldsAreOrderedDimensionsFirst() {
      info.addXField(measure("Sales"));
      info.addXField(dim("Region"));
      info.addYField(measure("Profit"));
      info.addYField(dim("State"));

      swap();

      assertEquals(Arrays.asList("State", "Profit"), names(info.getXFields()));
      assertEquals(Arrays.asList("Region", "Sales"), names(info.getYFields()));
   }

   @Test
   void fieldsOfTheSameKindKeepTheirOrder() {
      info.addYField(dim("A"));
      info.addYField(dim("B"));
      info.addYField(dim("C"));

      swap();

      assertEquals(Arrays.asList("A", "B", "C"), names(info.getXFields()));
   }

   /** A map swaps only its dimensions; the measures stay where they are. */
   @Test
   void mapSwapsOnlyTheDimensions() {
      VSMapInfo map = new VSMapInfo();
      map.addXField(dim("City"));
      map.addXField(measure("Sales"));
      map.addYField(dim("State"));
      map.addYField(measure("Profit"));

      new SwapXYBindingProcessor(map, desc).process();

      assertEquals(Arrays.asList("State", "Sales"), names(map.getXFields()));
      assertEquals(Arrays.asList("City", "Profit"), names(map.getYFields()));
   }

   @Test
   void mapSwapDoesNotTouchTheAxisTitles() {
      desc.getTitlesDescriptor().getXTitleDescriptor().setTitleValue("X title");
      VSMapInfo map = new VSMapInfo();
      map.addXField(dim("City"));
      map.addYField(dim("State"));

      new SwapXYBindingProcessor(map, desc).process();

      assertEquals("X title", desc.getTitlesDescriptor().getXTitleDescriptor().getTitleValue());
   }
}
