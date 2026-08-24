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

import inetsoft.graph.aesthetic.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChangeChartTypeProcessorTest {
   @Test
   void pieToTreemapMovesDimensionToGroup() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Category"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_PIE, info);
      info = changeType(GraphTypes.CHART_PIE, GraphTypes.CHART_TREEMAP, info);

      assertEquals(1, info.getGroupFieldCount(),
                   "the pie dimension must land on the treemap's group shelf, not stay " +
                   "stranded on the aesthetic channel");
      assertNull(info.getColorField(),
                "the dimension must be moved off color, not left there and also duplicated " +
                "onto group");
   }

   @Test
   void treemapToBarMovesGroupDimensionToX() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Category"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_TREEMAP, info);
      assertEquals(1, info.getGroupFieldCount(), "sanity check on the treemap intermediate state");

      info = changeType(GraphTypes.CHART_TREEMAP, GraphTypes.CHART_BAR, info);

      assertEquals(1, info.getXFieldCount(),
                   "the dimension must move back to x when leaving treemap");
      assertEquals(0, info.getGroupFieldCount(),
                   "the dimension must not be left stranded on the group shelf bar never reads");
   }

   /**
    * If a chart is already left with the same dimension on both {@code x} and {@code group}
    * (e.g. from an earlier, separately-caused inconsistency) when it transitions out of
    * treemap, {@code moveGroupFieldsToX} must not blindly append the group copy onto x --
    * that would duplicate the dimension on x while still emptying group.
    */
   @Test
   void treemapToBarDoesNotDuplicateADimensionAlreadyOnXAndGroup() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Category"));
      info.addGroupField(dimension("Category"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));

      info = changeType(GraphTypes.CHART_TREEMAP, GraphTypes.CHART_BAR, info);

      assertEquals(1, info.getXFieldCount(),
                   "must not duplicate a dimension already present on x");
      assertEquals(0, info.getGroupFieldCount());
   }

   /** Same defect, same fix, the other merged type that shares {@code moveGroupFieldsToX}. */
   @Test
   void mekkoToBarDoesNotDuplicateADimensionAlreadyOnXAndGroup() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Category"));
      info.addGroupField(dimension("Category"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));

      info = changeType(GraphTypes.CHART_MEKKO, GraphTypes.CHART_BAR, info);

      assertEquals(1, info.getXFieldCount(),
                   "must not duplicate a dimension already present on x");
      assertEquals(0, info.getGroupFieldCount());
   }

   @Test
   void treemapToBarPreservesGradientColorFrame() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Category"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setColorFrame(new GradientColorFrame());

      info = changeType(GraphTypes.CHART_TREEMAP, GraphTypes.CHART_BAR, info);

      assertInstanceOf(GradientColorFrame.class, info.getColorFrame(),
                        "a gradient color frame explicitly set by the user must survive a " +
                        "type change that crosses the merged/non-merged boundary");
   }

   @Test
   void barToBarStackPreservesCategoricalColorFrame() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Category"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setColorFrame(new CategoricalColorFrame());

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_BAR_STACK, info);

      assertInstanceOf(CategoricalColorFrame.class, info.getColorFrame(),
                        "a categorical color frame must survive a same-family type change " +
                        "that reuses the same ChartInfo object");
   }

   @Test
   void barToScatterContourStillSwitchesToBluesColorFrame() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Category"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setColorFrame(new StaticColorFrame());

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_SCATTER_CONTOUR, info);

      assertInstanceOf(BluesColorFrame.class, info.getColorFrame(),
                        "entering contour must still switch to a Blues color frame");
   }

   @Test
   void scatterContourToBarStillResetsToStaticColorFrame() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Category"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setColorFrame(new BluesColorFrame());

      info = changeType(GraphTypes.CHART_SCATTER_CONTOUR, GraphTypes.CHART_BAR, info);

      assertInstanceOf(StaticColorFrame.class, info.getColorFrame(),
                        "leaving contour with a Linear/Blues frame still attached must reset " +
                        "to a static color frame");
   }

   private static ChartInfo changeType(int oldType, int newType, ChartInfo info) {
      return new ChangeChartTypeProcessor(oldType, newType, null, info).process();
   }

   private static VSChartDimensionRef dimension(String field) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue(field);
      return dim;
   }

   private static VSChartAggregateRef aggregate(String column, AggregateFormula formula) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue(column);
      agg.setFormula(formula);
      agg.setAggregated(true);
      return agg;
   }
}
