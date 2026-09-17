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
