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

import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.aesthetic.ShapeFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.element.PointElement;
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug 76811: a zero-span Gantt row (start == end, a "milestone" row) rendered with zero visible
 * area. {@code GraphGenerator}'s Gantt bar-creation branch never called
 * {@code IntervalElement.setZeroHeight(1)} the way the generic interval/bar branch does, so
 * {@code BarVO}'s zero-interval fallback used the unset default of 0, producing a bar with no
 * drawable area for any zero-span row.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GanttGraphGeneratorZeroSpanBarTest {
   @Test
   void zeroSpanGanttBarGetsNonZeroZeroHeightAndMilestoneKeepsItsDiamond() {
      GanttVSChartInfo info = new GanttVSChartInfo();
      info.setChartType(GraphTypes.CHART_GANTT);

      VSChartAggregateRef start = new VSChartAggregateRef();
      start.setDataRef(new AttributeRef("START"));
      start.setFormula(AggregateFormula.NONE);
      info.setStartField(start);
      info.setRTStartField(start);

      VSChartAggregateRef end = new VSChartAggregateRef();
      end.setDataRef(new AttributeRef("END"));
      end.setFormula(AggregateFormula.NONE);
      info.setEndField(end);
      info.setRTEndField(end);

      VSChartAggregateRef milestone = new VSChartAggregateRef();
      milestone.setDataRef(new AttributeRef("MILESTONE"));
      milestone.setFormula(AggregateFormula.NONE);
      // no shape explicitly configured on the ref -- this is the "frame == null" case that
      // GraphGenerator.java:3793 falls back to FILLED_DIAMOND for.
      milestone.setShapeFrame(null);
      info.setMilestoneField(milestone);
      info.setRTMilestoneField(milestone);

      String startName = GraphUtil.getName(start);
      String endName = GraphUtil.getName(end);
      String milestoneName = GraphUtil.getName(milestone);

      // a single zero-span row: start == end == milestone.
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { startName, endName, milestoneName },
         { 100.0, 100.0, 100.0 },
      });

      GanttGraphGenerator gen = new GanttGraphGenerator(
         info, new ChartDescriptor(), null, data, null, null, 0, new Dimension(400, 300));

      gen.createElement(GraphTypes.CHART_GANTT,
         new String[] { startName, endName, milestoneName }, null);

      assertEquals(2, gen.graph.getElementCount(),
         "expected the bar IntervalElement plus the milestone PointElement");

      IntervalElement bar = (IntervalElement) gen.graph.getElement(0);
      assertTrue(bar.getZeroHeight() > 0,
         "a zero-span Gantt bar must have a non-zero zeroHeight or BarVO renders it with zero " +
         "visible area (bug 76811)");

      PointElement marker = (PointElement) gen.graph.getElement(1);
      ShapeFrame frame = marker.getShapeFrame();
      assertNotNull(frame, "milestone marker should have an explicit shape frame");
      assertEquals(GShape.FILLED_DIAMOND, frame.getShape((Object) null),
         "the milestone marker's diamond must survive initElement's Gantt shape-frame handling, " +
         "not be overwritten with the chart's generic default shape");
   }
}
