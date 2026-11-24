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
package inetsoft.graph.guide.legend;

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.ExtendedDecimalFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Numeric legend labels under the default K/M/B format. On a modern chart, labels that would read
 * the same are formatted at the step between the values shown. Every other legend keeps its text.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendDistinctLabelTest {
   private static final List<String> YEAR_TICKS =
      List.of("2024", "2024.2", "2024.4", "2024.6", "2024.8", "2025");

   @Test
   void aModernGradientOfTwoYearsReadsEachYear() {
      assertEquals(List.of("2024", "2025"),
                   bandLabels(new GradientColorFrame("m"), auto(), true, 2024, 2025));
   }

   @Test
   void aClassicGradientOfTwoYearsKeepsItsText() {
      assertEquals(List.of("2K", "2K"),
                   bandLabels(new GradientColorFrame("m"), auto(), false, 2024, 2025));
   }

   @Test
   void aModernSizeLegendOfTwoYearsReadsEachValue() {
      assertEquals(YEAR_TICKS, itemLabels(new LinearSizeFrame("m"), auto(), true, 2024, 2025));
   }

   @Test
   void aClassicSizeLegendOfTwoYearsKeepsItsText() {
      assertEquals(Collections.nCopies(6, "2K"),
                   itemLabels(new LinearSizeFrame("m"), auto(), false, 2024, 2025));
   }

   @Test
   void aModernShapeLegendOfTwoYearsReadsEachValue() {
      assertEquals(YEAR_TICKS, itemLabels(new FillShapeFrame("m"), auto(), true, 2024, 2025));
   }

   @Test
   void aClassicShapeLegendOfTwoYearsKeepsItsText() {
      assertEquals(Collections.nCopies(6, "2K"),
                   itemLabels(new FillShapeFrame("m"), auto(), false, 2024, 2025));
   }

   @ParameterizedTest
   @MethodSource("collidingRanges")
   void aModernGradientOfANarrowRangeReadsEachEnd(double min, double max, List<String> modern,
                                                 List<String> classic)
   {
      assertEquals(modern, bandLabels(new GradientColorFrame("m"), auto(), true, min, max));
   }

   @ParameterizedTest
   @MethodSource("collidingRanges")
   void aClassicGradientOfANarrowRangeKeepsItsText(double min, double max, List<String> modern,
                                                  List<String> classic)
   {
      assertEquals(classic, bandLabels(new GradientColorFrame("m"), auto(), false, min, max));
   }

   private static Stream<Arguments> collidingRanges() {
      return Stream.of(
         Arguments.of(1000.1, 1000.4, List.of("1000.1", "1000.4"), List.of("1K", "1K")),
         Arguments.of(0.001, 0.002, List.of("0.001", "0.002"), List.of("0", "0")),
         Arguments.of(2000000, 2040000, List.of("2000K", "2040K"), List.of("2M", "2M")),
         // "-0" next to "0"
         Arguments.of(-0.002, 0.003, List.of("-0.002", "0.003"), List.of("-0", "0")));
   }

   @ParameterizedTest
   @MethodSource("distinctRanges")
   void aModernGradientWithDistinctEndsKeepsItsText(double min, double max, List<String> text) {
      assertEquals(text, bandLabels(new GradientColorFrame("m"), auto(), true, min, max));
      assertEquals(text, bandLabels(new GradientColorFrame("m"), auto(), false, min, max));
   }

   private static Stream<Arguments> distinctRanges() {
      return Stream.of(
         Arguments.of(0, 20000, List.of("0", "20K")),
         Arguments.of(1, 1000000, List.of("0", "1M")),
         Arguments.of(-0.3, 0.2, List.of("-0.3", "0.2")));
   }

   @Test
   void aModernSizeLegendWithDistinctItemsKeepsItsText() {
      List<String> text = List.of("0", "5K", "10K", "15K", "20K");
      assertEquals(text, itemLabels(new LinearSizeFrame("m"), auto(), true, 0, 20000));
      assertEquals(text, itemLabels(new LinearSizeFrame("m"), auto(), false, 0, 20000));
   }

   @Test
   void aModernLegendKeepsAnotherFormat() {
      assertEquals(List.of("1000", "1000"), bandLabels(new GradientColorFrame("m"),
                                                       new DecimalFormat("0"), true, 1000.1, 1000.4));
   }

   @Test
   void aModernCategoricalLegendKeepsItsText() {
      assertEquals(List.of("2K", "2K"),
                   itemLabels(new CategoricalColorFrame("m"), auto(), true, 2024, 2025));
   }

   @Test
   void theFrameKeepsItsFormat() {
      GradientColorFrame frame = new GradientColorFrame("m");
      Format fmt = auto();
      bandLabels(frame, fmt, true, 2024, 2025);
      assertSame(fmt, frame.getLegendSpec().getTextSpec().getFormat());
      assertEquals(0, ((ExtendedDecimalFormat) fmt).getIncrement());
   }

   @Test
   void theFlagIsOffByDefault() {
      assertFalse(new EGraph().isLegendDistinctLabels());
   }

   // the chart default format of a number field on Color, Shape or Size
   private static ExtendedDecimalFormat auto() {
      return new ExtendedDecimalFormat(ExtendedDecimalFormat.AUTO_FORMAT,
                                       DecimalFormatSymbols.getInstance(Locale.US));
   }

   // the gradient's end labels
   private static List<String> bandLabels(VisualFrame frame, Format fmt, boolean distinct,
                                          double... values)
   {
      Legend legend = legend(frame, fmt, distinct, values);
      List<String> labels = new ArrayList<>();

      for(int i = 0; i < legend.getVisualCount(); i++) {
         if(legend.getVisual(i) instanceof VLabel label) {
            labels.add(label.getText());
         }
      }

      return labels;
   }

   private static List<String> itemLabels(VisualFrame frame, Format fmt, boolean distinct,
                                          double... values)
   {
      Legend legend = legend(frame, fmt, distinct, values);
      List<String> labels = new ArrayList<>();

      for(int i = 0; i < legend.getVisualCount(); i++) {
         if(legend.getVisual(i) instanceof LegendItem item) {
            labels.add(item.getLabel());
         }
      }

      return labels;
   }

   private static Legend legend(VisualFrame frame, Format fmt, boolean distinct,
                                double... values)
   {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { "m" };

      for(int i = 0; i < values.length; i++) {
         double v = values[i];
         rows[i + 1] = new Object[] { v == Math.rint(v) ? (Object) (int) v : v };
      }

      frame.init(new DefaultDataSet(rows));
      frame.getLegendSpec().setTitleVisible(false);
      frame.getLegendSpec().getTextSpec().setFormat(fmt);
      EGraph graph = new EGraph();
      graph.setCoordinate(new RectCoord());
      graph.setLegendDistinctLabels(distinct);
      return Legend.createLegend(frame, graph);
   }
}
