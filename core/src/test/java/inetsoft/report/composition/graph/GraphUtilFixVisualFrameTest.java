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
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GraphUtilFixVisualFrameTest {
   @Test
   void colorValueFrameIsPreservedForDimension() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);

      VSAestheticRef color = createColorRef(createDimensionRef());
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      wrapper.setColorValueFrame(true);
      color.setVisualFrameWrapper(wrapper);
      info.setColorField(color);

      assertInstanceOf(ColorValueColorFrame.class, color.getVisualFrame(),
                       "wrapper should hand out a ColorValueColorFrame when the option is on");

      boolean changed = GraphUtil.fixVisualFrame(
         color, ChartConstants.AESTHETIC_COLOR, GraphTypes.CHART_BAR, info);

      assertFalse(changed, "a ColorValueColorFrame should not be normalized away");
      assertSame(wrapper, color.getVisualFrameWrapper(), "the wrapper should not be replaced");
      assertTrue(((CategoricalColorFrameWrapper) color.getVisualFrameWrapper()).isColorValueFrame(),
                 "the colorValueFrame option should survive");
   }

   @Test
   void colorValueFrameIsPreservedForMeasure() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);

      VSAestheticRef color = createColorRef(new VSChartAggregateRef());
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      wrapper.setColorValueFrame(true);
      color.setVisualFrameWrapper(wrapper);
      info.setColorField(color);

      boolean changed = GraphUtil.fixVisualFrame(
         color, ChartConstants.AESTHETIC_COLOR, GraphTypes.CHART_BAR, info);

      assertFalse(changed, "a ColorValueColorFrame should not be replaced by a linear color frame");
      assertSame(wrapper, color.getVisualFrameWrapper());
      assertTrue(((CategoricalColorFrameWrapper) color.getVisualFrameWrapper()).isColorValueFrame());
   }

   @Test
   void wrongFrameTypeIsStillNormalizedForDimension() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);

      VSAestheticRef color = createColorRef(createDimensionRef());
      // a size frame on the color region is not a valid color frame and must be fixed
      color.setVisualFrame(new CategoricalSizeFrame());
      info.setColorField(color);

      boolean changed = GraphUtil.fixVisualFrame(
         color, ChartConstants.AESTHETIC_COLOR, GraphTypes.CHART_BAR, info);

      assertTrue(changed, "an invalid color frame should still be normalized");
      assertInstanceOf(CategoricalColorFrame.class, color.getVisualFrame());
   }

   @Test
   void plainCategoricalColorFrameIsLeftAlone() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);

      VSAestheticRef color = createColorRef(createDimensionRef());
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      color.setVisualFrameWrapper(wrapper);
      info.setColorField(color);

      boolean changed = GraphUtil.fixVisualFrame(
         color, ChartConstants.AESTHETIC_COLOR, GraphTypes.CHART_BAR, info);

      assertFalse(changed);
      assertSame(wrapper, color.getVisualFrameWrapper());
      assertFalse(((CategoricalColorFrameWrapper) color.getVisualFrameWrapper()).isColorValueFrame());
   }

   private static VSChartDimensionRef createDimensionRef() {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue("Color");
      return dim;
   }

   private static VSAestheticRef createColorRef(inetsoft.uql.erm.DataRef dataRef) {
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dataRef);
      return color;
   }
}
