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
package inetsoft.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.*;
import inetsoft.graph.element.PointElement;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticTextureFrameWrapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the legend candidate selection in {@link EGraph#getVisualFrames()}. The two directions
 * below pull against each other and have each regressed the other in the past, so both are pinned:
 * a color frame that is painted by another legend must be suppressed (Bug #72971), but only for the
 * element that actually shares it, not for equal frames owned by other elements (Bug #76065).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class EGraphVisualFramesTest {
   /**
    * Bug #76065, a boxplot creates a box element and an outlier point element per measure, each
    * with its own equal-valued measure color frame. Binding a per-measure texture makes the
    * outlier element share its colors into the texture legend, but the box element's color frame
    * must keep its own legend.
    */
   @Test
   void boxplotKeepsColorLegendWithTextureLegend() {
      VisualFrame[] frames = boxplot(true).getVisualFrames();

      assertEquals(2, frames.length,
                   "expected separate color and texture legends, got " + describe(frames));
      assertEquals(1, count(frames, ColorFrame.class), "missing color legend: " + describe(frames));
      assertEquals(1, count(frames, TextureFrame.class),
                   "missing texture legend: " + describe(frames));
   }

   /**
    * Without a second aesthetic there is only the shared measure color legend.
    */
   @Test
   void boxplotWithoutTextureHasSingleColorLegend() {
      VisualFrame[] frames = boxplot(false).getVisualFrames();

      assertEquals(1, frames.length, "expected a single legend, got " + describe(frames));
      assertInstanceOf(ColorFrame.class, frames[0]);
   }

   /**
    * Bug #76065, minimal form: two elements with distinct but equal color frames, where only one of
    * them shares its colors into a shape legend. The other element keeps its color legend, whether
    * it comes before or after the element carrying the shape frame.
    */
   @Test
   void equalColorFrameOfOtherElementKeepsItsLegend() {
      for(boolean shapeOnFirst : new boolean[]{ false, true }) {
         VisualFrame[] frames = twoElements(false, shapeOnFirst).getVisualFrames();

         assertEquals(2, frames.length,
                      "shapeOnFirst=" + shapeOnFirst +
                         ", expected separate color and shape legends, got " + describe(frames));
         assertEquals(1, count(frames, ColorFrame.class),
                      "missing color legend: " + describe(frames));
         assertEquals(1, count(frames, ShapeFrame.class),
                      "missing shape legend: " + describe(frames));
      }
   }

   /**
    * Bug #72971, the reverse direction: when both elements share the very same color frame
    * instance and one of them paints those colors into its shape legend, the color frame must not
    * also get a legend of its own. Returning it while it also delegates via getLegendFrame() is
    * the inconsistency that #72971 fixed.
    */
   @Test
   void sharedColorFrameDoesNotGetItsOwnLegend() {
      for(boolean shapeOnFirst : new boolean[]{ false, true }) {
         EGraph graph = twoElements(true, shapeOnFirst);
         VisualFrame[] frames = graph.getVisualFrames();
         ColorFrame color = graph.getElement(0).getColorFrame();

         assertSame(color, graph.getElement(1).getColorFrame(), "elements should share the frame");
         assertEquals(1, frames.length,
                      "shapeOnFirst=" + shapeOnFirst + ", expected only the shape legend, got " +
                         describe(frames));
         assertInstanceOf(ShapeFrame.class, frames[0]);
         assertTrue(Arrays.stream(frames).noneMatch(f -> f == color),
                    "shared color frame must not be its own legend");
         assertSame(frames[0], color.getLegendFrame(),
                    "shared color frame should delegate to the shape legend");
      }
   }

   private static int count(VisualFrame[] frames, Class<?> type) {
      return (int) Arrays.stream(frames).filter(type::isInstance).count();
   }

   /**
    * Two point elements bound to the same field, one of which also has a shape frame.
    *
    * @param shareColor   true to give both elements the same color frame instance, false to give
    *                     each its own distinct but equal frame.
    * @param shapeOnFirst true to put the shape frame on the first element, false the second.
    */
   private static EGraph twoElements(boolean shareColor, boolean shapeOnFirst) {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "State", "Total" }, { "CA", 1.0 }, { "NY", 2.0 }, { "TX", 3.0 }
      });

      EGraph graph = new EGraph();
      CategoricalColorFrame sharedColor = shareColor ? createColorFrame(data) : null;

      for(int i = 0; i < 2; i++) {
         PointElement elem = new PointElement();
         elem.addDim("State");
         elem.addVar("Total");
         elem.setColorFrame(shareColor ? sharedColor : createColorFrame(data));

         if(i == (shapeOnFirst ? 0 : 1)) {
            CategoricalShapeFrame shape = new CategoricalShapeFrame();
            shape.setField("State");
            shape.init(data);
            elem.setShapeFrame(shape);
         }

         graph.addElement(elem);
      }

      return graph;
   }

   private static CategoricalColorFrame createColorFrame(DataSet data) {
      CategoricalColorFrame color = new CategoricalColorFrame();
      color.setField("State");
      color.init(data);
      return color;
   }

   private static EGraph boxplot(boolean texture) {
      Object[][] rows = new Object[21][];
      rows[0] = new Object[]{ "State", "Total", "Paid" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[]{ i % 3 == 0 ? "CA" : (i % 3 == 1 ? "NY" : "TX"),
                                 (double) (i * 100), (double) (i * 37) };
      }

      VSChartInfo info = new DefaultVSChartInfo();
      info.setSeparatedGraph(true);
      info.setMultiStyles(false);
      info.setChartType(GraphTypes.CHART_BOXPLOT);
      info.setRTChartType(GraphTypes.CHART_BOXPLOT);
      info.addXField(new VSChartDimensionRef(new BaseField("State")));

      VSChartAggregateRef total = createAggregate("Total");
      VSChartAggregateRef paid = createAggregate("Paid");

      if(texture) {
         StaticTextureFrameWrapper wrapper = new StaticTextureFrameWrapper();
         wrapper.setTexture(1);
         wrapper.setChanged(true);
         paid.setTextureFrameWrapper(wrapper);
      }

      info.addYField(total);
      info.addYField(paid);
      info.updateChartType(false);

      return GraphGenerator.getGenerator(
         info, new ChartDescriptor(), null, null, new DefaultDataSet(rows), new VariableTable(),
         XSourceInfo.NONE, null).createEGraph();
   }

   private static VSChartAggregateRef createAggregate(String name) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new BaseField(name));
      return ref;
   }

   private static String describe(VisualFrame[] frames) {
      return Arrays.toString(Arrays.stream(frames).map(f -> f.getClass().getName())
                                .toArray(String[]::new));
   }
}
