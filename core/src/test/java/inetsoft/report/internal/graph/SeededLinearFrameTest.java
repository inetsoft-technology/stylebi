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
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
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
class SeededLinearFrameTest {
   /**
    * Guards the fixture itself. fixVisualFrame only reaches the linear seed when the ref is a
    * measure, so a fixture that drifts categorical would make every other test here pass by
    * never running the branch under test.
    */
   @Test
   void theFixtureReachesTheMeasureBranch() {
      assertFalse(GraphUtil.isCategorical(measureColorRef().getDataRef()),
                  "fixture must read as a measure or the seed branch is never reached");
   }

   @Test
   void graphUtilSeedsTealForAModernChart() {
      assertInstanceOf(TealColorFrame.class, seedThrough(VizContext.of(VizMark.MODERN_LIGHT)));
   }

   @Test
   void graphUtilSeedsTealForAModernDarkChart() {
      assertInstanceOf(TealColorFrame.class, seedThrough(VizContext.of(VizMark.MODERN_DARK)));
   }

   @Test
   void graphUtilSeedsBluesForAClassicChart() {
      assertInstanceOf(BluesColorFrame.class, seedThrough(VizContext.LEGACY));
   }

   /**
    * The threading itself, end to end: a marked assembly info, through VizContext.of(info), the
    * processor's context field, fixVisualFrames, fixVisualFrames0 and fixVisualFrame. The three
    * tests above call fixVisualFrame directly and would pass even if no caller threaded a context.
    */
   @Test
   void aModernMarkedAssemblyIsBornOnTeal() {
      assertInstanceOf(TealColorFrame.class, seedThroughProcessor(VizMark.MODERN_LIGHT));
   }

   @Test
   void anUnmarkedAssemblyIsBornOnBlues() {
      assertInstanceOf(BluesColorFrame.class, seedThroughProcessor(null));
   }

   /** Runs the whole chain the way a real chart-type change does. */
   private VisualFrame seedThroughProcessor(VizMark mark) {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(mark);

      VSChartInfo cinfo = info.getVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.addYField(measureField());
      cinfo.setColorField(measureColorRef());

      assertNull(cinfo.getColorField().getVisualFrame(),
                 "fixture must start with no colour frame or nothing is seeded");

      ChartInfo processed = new ChangeChartTypeProcessor(
         GraphTypes.CHART_BAR, GraphTypes.CHART_BAR, null, cinfo, VizContext.of(info)).process();

      return processed.getColorField().getVisualFrame();
   }

   /** A y-axis measure, so the processor is not forced to auto for want of one. */
   private VSChartAggregateRef measureField() {
      VSChartAggregateRef aggr = new VSChartAggregateRef();
      aggr.setColumnValue("Quantity");
      aggr.setFormula(AggregateFormula.SUM);

      return aggr;
   }

   private VisualFrame seedThrough(VizContext ctx) {
      AestheticRef ref = measureColorRef();

      GraphUtil.fixVisualFrame(ref, ChartConstants.AESTHETIC_COLOR, GraphTypes.CHART_BAR,
                               new VSChartInfo(), ctx);

      return ref.getVisualFrame();
   }

   /** A colour aesthetic bound to a measure, carrying no frame yet. */
   private AestheticRef measureColorRef() {
      VSAggregateRef aggr = new VSAggregateRef();
      aggr.setColumnValue("Total");
      aggr.setFormula(AggregateFormula.SUM);

      VSAestheticRef ref = new VSAestheticRef();
      ref.setDataRef(aggr);

      return ref;
   }
}
