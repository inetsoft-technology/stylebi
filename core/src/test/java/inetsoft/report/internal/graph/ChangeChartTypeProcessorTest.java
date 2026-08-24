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
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.Set;

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

   @Test
   void barToPieWithDimensionOnColorDisplacesItToAShelf() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Month"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setColorField(aestheticRef(dimension("Year")));

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_PIE, info);

      assertNotNull(info.getColorField(), "the migration must still fire and bind color");
      assertEquals("Month", info.getColorField().getDataRef().getName(),
                   "the x dimension is the one that migrates onto color");
      assertTrue(info.getXFieldCount() > 0 || info.getYFieldCount() > 0,
                 "the displaced color dimension ('Year') must land on a shelf, not vanish");
      assertFalse(
         info.getXFieldCount() > 0 && "Month".equals(info.getXField(0).getName()),
         "the migrated field must not also be left behind on x");
   }

   @Test
   void barToPieWithDimensionOnShapeStillMigrates() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Month"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setShapeField(aestheticRef(dimension("Quarter")));

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_PIE, info);

      assertEquals(0, info.getXFieldCount(),
                   "a dimension on shape must not block the x -> color migration");
      assertNotNull(info.getColorField());
      assertEquals("Month", info.getColorField().getDataRef().getName());
      assertNotNull(info.getShapeField(), "shape's own dimension must survive untouched");
   }

   @Test
   void barToPieWithMeasureOnColorRefusesAndMutatesNothing() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Month"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setColorField(aestheticRef(aggregate("Discount", AggregateFormula.SUM)));

      assertThrows(IllegalArgumentException.class,
                   () -> changeTypeStrictly(GraphTypes.CHART_BAR, GraphTypes.CHART_PIE, info));

      assertEquals(1, info.getXFieldCount(), "x must be untouched after the refusal");
      assertEquals("Month", info.getXField(0).getName());
      assertEquals(1, info.getYFieldCount(), "y must be untouched after the refusal");
      assertNotNull(info.getColorField(), "color must still hold its original measure");
      assertEquals("Discount", info.getColorField().getDataRef().getName());
   }

   @Test
   void barToPieWithMeasureOnShapeMigratesAndLeavesItUntouched() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Month"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setShapeField(aestheticRef(aggregate("Discount", AggregateFormula.SUM)));

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_PIE, info);

      assertNotNull(info.getColorField());
      assertEquals("Month", info.getColorField().getDataRef().getName());
      assertNotNull(info.getShapeField(), "the measure on shape is never at risk, only color is");
      assertEquals("Discount", info.getShapeField().getDataRef().getName());
   }

   @Test
   void barToTreemapWithAllAestheticChannelsOccupiedRefusesAndMutatesNothing() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Month"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setColorField(aestheticRef(dimension("Year")));
      info.setShapeField(aestheticRef(dimension("Quarter")));
      info.setSizeField(aestheticRef(aggregate("Discount", AggregateFormula.SUM)));

      assertThrows(IllegalArgumentException.class,
                   () -> changeTypeStrictly(GraphTypes.CHART_BAR, GraphTypes.CHART_TREEMAP, info));

      assertEquals(1, info.getXFieldCount(), "x must be untouched after the refusal");
      assertEquals(1, info.getYFieldCount(), "y must be untouched after the refusal");
      assertEquals(0, info.getGroupFieldCount(), "group must be untouched after the refusal");
      assertEquals("Year", info.getColorField().getDataRef().getName());
      assertEquals("Quarter", info.getShapeField().getDataRef().getName());
      assertEquals("Discount", info.getSizeField().getDataRef().getName());
   }

   @Test
   void barToTreemapWithFreeChannelsStillMigrates() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Month"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_TREEMAP, info);

      assertEquals(1, info.getGroupFieldCount(), "the x dimension must move onto group");
      assertEquals("Month", info.getGroupField(0).getName());
      assertNotNull(info.getSizeField(), "the leftover measure must land on the free size channel");
      assertEquals("Sales", info.getSizeField().getDataRef().getName());
   }

   @Test
   void ganttToNetworkPreservesStartEndMilestone() {
      ChartInfo info = ganttInfo("Start", "End", "Milestone");

      info = changeType(GraphTypes.CHART_GANTT, GraphTypes.CHART_NETWORK, info);

      assertEquals("Start", nameOrNull(info.getColorField()),
                   "start must land on an aesthetic channel, not vanish");
      assertEquals("End", nameOrNull(info.getShapeField()),
                   "end must land on an aesthetic channel, not vanish");
      assertEquals("Milestone", nameOrNull(info.getSizeField()),
                   "milestone must land on an aesthetic channel, not vanish");
   }

   @Test
   void ganttToTreemapPreservesStartEndMilestone() {
      ChartInfo info = ganttInfo("Start", "End", "Milestone");

      info = changeType(GraphTypes.CHART_GANTT, GraphTypes.CHART_TREEMAP, info);

      assertEquals("Start", nameOrNull(info.getColorField()),
                   "start must land on an aesthetic channel, not vanish");
      assertEquals("End", nameOrNull(info.getShapeField()),
                   "end must land on an aesthetic channel, not vanish");
      assertEquals("Milestone", nameOrNull(info.getSizeField()),
                   "milestone must land on an aesthetic channel, not vanish");
   }

   @Test
   void ganttToMekkoPreservesStartEndMilestone() {
      ChartInfo info = ganttInfo("Start", "End", "Milestone");

      info = changeType(GraphTypes.CHART_GANTT, GraphTypes.CHART_MEKKO, info);

      Set<String> bound = allBoundNames(info);
      assertTrue(bound.contains("Start"), "start must not be silently dropped: " + bound);
      assertTrue(bound.contains("End"), "end must not be silently dropped: " + bound);
      assertTrue(bound.contains("Milestone"), "milestone must not be silently dropped: " + bound);
   }

   /**
    * The lenient default, which every caller but {@code ChangeChartTypeService} keeps. A script
    * doing {@code chart.chartStyle = PIE}, a report chart element or a date-comparison rebuild is
    * running inside someone else's operation: there is no dispatcher to report a refusal through
    * and nothing to catch it, so an exception would turn a chart that used to render imperfectly
    * into a script error. The measure is dropped, as it always was, but now with a LOG.warn.
    */
   @Test
   void barToPieWithMeasureOnColorDegradesRatherThanThrowsForNonInteractiveCallers() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Month"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setColorField(aestheticRef(aggregate("Discount", AggregateFormula.SUM)));

      ChartInfo result =
         assertDoesNotThrow(() -> changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_PIE, info));

      assertNotNull(result.getColorField(), "the migration still runs on the lenient path");
      assertEquals("Month", result.getColorField().getDataRef().getName());
   }

   /** The treemap half of the same contract. */
   @Test
   void barToTreemapWithNoFreeChannelDegradesRatherThanThrowsForNonInteractiveCallers() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Month"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setColorField(aestheticRef(dimension("Year")));
      info.setShapeField(aestheticRef(dimension("Quarter")));
      info.setSizeField(aestheticRef(aggregate("Discount", AggregateFormula.SUM)));

      ChartInfo result = assertDoesNotThrow(
         () -> changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_TREEMAP, info));

      assertEquals(1, result.getGroupFieldCount(),
                   "the dimension must still move onto group on the lenient path");
   }

   /**
    * Finding 16 sibling (Site A, fixPieDimensions): a categorical color frame the caller
    * already planted on the top-level "color" slot while the channel was unbound (e.g. via
    * set_visual_frame) must carry onto the dimension the pie migration is about to bind to
    * color, not be silently replaced by a bare default.
    */
   @Test
   void barToPieCarriesForwardAnExistingTopLevelCategoricalColorFrame() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Month"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));

      CategoricalColorFrame custom = new CategoricalColorFrame();
      info.setColorFrame(custom);

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_PIE, info);

      assertNotNull(info.getColorField());
      assertSame(custom, info.getColorField().getVisualFrame(),
                 "a categorical frame already sitting in the top-level slot before any field " +
                 "was bound to color must carry onto the field the retype just bound, not be " +
                 "silently replaced by a bare default");
   }

   /**
    * Finding 16 sibling (Site B, addAestheticField via changeToMap): a linear/gradient color
    * frame already sitting in the ORIGINAL chart's top-level "color" slot must carry onto the
    * measure that changeToMap displaces from x/y onto the newly-created map's color channel.
    */
   @Test
   void barToMapCarriesForwardAnExistingTopLevelGradientColorFrameOntoTheDisplacedMeasure() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Region"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));

      GradientColorFrame custom = new GradientColorFrame();
      info.setColorFrame(custom);

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_MAP, info);

      assertNotNull(info.getColorField(),
                     "the measure displaced off x/y by the map conversion must land on color");
      assertEquals("Sales", info.getColorField().getDataRef().getName());
      assertSame(custom, info.getColorField().getVisualFrame(),
                 "a gradient frame already sitting in the chart's top-level color slot before " +
                 "the map conversion must carry onto the measure it just bound to color, not be " +
                 "silently replaced by a bare BluesColorFrame default");
   }

   /**
    * Finding 16 sibling (Site C, copyToMekko): same defect shape as Site A, reached via the
    * mekko assembly's own first-bind-into-a-free-color-channel branch.
    */
   @Test
   void barToMekkoCarriesForwardAnExistingTopLevelCategoricalColorFrame() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Region"));
      info.addXField(dimension("Category"));
      info.addXField(dimension("Segment"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));

      CategoricalColorFrame custom = new CategoricalColorFrame();
      info.setColorFrame(custom);

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_MEKKO, info);

      assertNotNull(info.getColorField(), "the leftover dimension must land on the free color channel");
      assertEquals("Segment", info.getColorField().getDataRef().getName());
      assertSame(custom, info.getColorField().getVisualFrame(),
                 "a categorical frame already sitting in the top-level slot before any field " +
                 "was bound to color must carry onto the dimension mekko assembly just bound, " +
                 "not be silently replaced by a bare default");
   }

   /**
    * Finding 16 sibling (Class 2, copyToTreemap): the measure-overflow placements construct a
    * frame just as directly as the dimension sites above, and a gradient is exactly as
    * user-customizable for a measure as a categorical frame is for a dimension.
    */
   @Test
   void barToTreemapCarriesForwardAnExistingTopLevelGradientColorFrameOntoTheOverflowMeasure() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Category"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setSizeField(aestheticRef(dimension("Region")));

      GradientColorFrame custom = new GradientColorFrame();
      info.setColorFrame(custom);

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_TREEMAP, info);

      assertNotNull(info.getColorField(), "the leftover measure must land on the free color channel");
      assertEquals("Sales", info.getColorField().getDataRef().getName());
      assertSame(custom, info.getColorField().getVisualFrame(),
                 "a gradient frame already sitting in the top-level slot before any field was " +
                 "bound to color must carry onto the overflow measure the treemap conversion " +
                 "just bound, not be silently replaced by a bare BluesColorFrame default");
   }

   /** Same Class 2 defect shape as copyToTreemap above, reached via copyToRelation instead. */
   @Test
   void barToNetworkCarriesForwardAnExistingTopLevelGradientColorFrameOntoTheOverflowMeasure() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Region"));
      info.addGroupField(dimension("Category"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));
      info.setSizeField(aestheticRef(dimension("Segment")));

      GradientColorFrame custom = new GradientColorFrame();
      info.setColorFrame(custom);

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_NETWORK, info);

      assertInstanceOf(RelationChartInfo.class, info);
      assertNotNull(info.getColorField(), "the leftover measure must land on the free color channel");
      assertEquals("Sales", info.getColorField().getDataRef().getName());
      assertSame(custom, info.getColorField().getVisualFrame(),
                 "a gradient frame already sitting in the top-level slot before any field was " +
                 "bound to color must carry onto the overflow measure the relation conversion " +
                 "just bound, not be silently replaced by a bare BluesColorFrame default");
   }

   @Test
   void barToNetworkStillMovesDimensionsToSourceAndTarget() {
      ChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Region"));
      info.addGroupField(dimension("Category"));
      info.addYField(aggregate("Sales", AggregateFormula.SUM));

      info = changeType(GraphTypes.CHART_BAR, GraphTypes.CHART_NETWORK, info);

      assertInstanceOf(RelationChartInfo.class, info);
      RelationChartInfo relation = (RelationChartInfo) info;
      assertNotNull(relation.getSourceField(), "a dimension must migrate to source");
      assertNotNull(relation.getTargetField(), "a dimension must migrate to target");
      assertNotNull(info.getSizeField(), "the leftover measure must land on the free size channel");
      assertEquals("Sales", info.getSizeField().getDataRef().getName());
   }

   private static ChartInfo changeType(int oldType, int newType, ChartInfo info) {
      return new ChangeChartTypeProcessor(oldType, newType, null, info).process();
   }

   /**
    * A retype made the way {@code ChangeChartTypeService} makes it — the one caller that asked for
    * this retype on someone's behalf and can report a refusal back to them, so the only one that
    * turns strict field placement on. Every other caller (script, report element, date comparison)
    * keeps the lenient default, which drops rather than throws.
    */
   private static ChartInfo changeTypeStrictly(int oldType, int newType, ChartInfo info) {
      return new ChangeChartTypeProcessor(oldType, newType, null, info)
         .setStrictFieldPlacement(true)
         .process();
   }

   private static ChartInfo ganttInfo(String start, String end, String milestone) {
      GanttVSChartInfo info = new GanttVSChartInfo();
      info.setStartField(dimension(start));
      info.setEndField(dimension(end));
      info.setMilestoneField(dimension(milestone));
      return info;
   }

   private static String nameOrNull(AestheticRef aref) {
      return aref == null ? null : aref.getDataRef().getName();
   }

   private static Set<String> allBoundNames(ChartInfo info) {
      Set<String> names = new HashSet<>();

      for(VSDataRef field : info.getFields()) {
         names.add(field.getName());
      }

      for(String name : new String[] {
         nameOrNull(info.getColorField()), nameOrNull(info.getShapeField()),
         nameOrNull(info.getSizeField()), nameOrNull(info.getTextField())
      })
      {
         if(name != null) {
            names.add(name);
         }
      }

      return names;
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

   private static AestheticRef aestheticRef(DataRef dataRef) {
      VSAestheticRef aref = new VSAestheticRef();
      aref.setDataRef(dataRef);
      return aref;
   }
}
