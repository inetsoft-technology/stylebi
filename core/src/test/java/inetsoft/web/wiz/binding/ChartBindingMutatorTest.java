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
package inetsoft.web.wiz.binding;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartGeoRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.VSMapInfo;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@WizAgentTestSupport
class ChartBindingMutatorTest {
   @Test
   void setsTheXShelfFromFieldRefs() {
      ChartBindingModel model = new ChartBindingModel();

      ChartBindingMutator.setShelf(model, "x",
                                   List.of(new FieldRef("Region", "dimension", null, null, null)));

      assertEquals(1, model.getXFields().size());
      assertInstanceOf(ChartDimensionRefModel.class, model.getXFields().get(0));
   }

   @Test
   void setsAMeasureOnTheYShelfCarryingItsAggregate() {
      ChartBindingModel model = new ChartBindingModel();

      ChartBindingMutator.setShelf(model, "y",
                                   List.of(new FieldRef("Sales", "measure", "Sum", null, null)));

      assertEquals(1, model.getYFields().size());
      assertInstanceOf(ChartAggregateRefModel.class, model.getYFields().get(0));
   }

   @Test
   void leavesEveryAestheticFieldUntouched() {
      ChartBindingModel model = new ChartBindingModel();
      Map<String, Object> before = ChartBindingFields.snapshotAesthetics(model);

      ChartBindingMutator.setShelf(model, "x",
                                   List.of(new FieldRef("Region", "dimension", null, null, null)));

      assertEquals(before, ChartBindingFields.snapshotAesthetics(model),
                   "a shelf write must not disturb the aesthetic fields spec 2c owns");
   }

   @Test
   void rejectsAnUnknownShelfNamingTheValidOnes() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(new ChartBindingModel(), "z", List.of()));
      assertTrue(thrown.getMessage().contains("z"));
      assertTrue(thrown.getMessage().contains("x"));
   }

   @Test
   void rejectsAFieldWithoutATypeNamingTheField() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(
            new ChartBindingModel(), "x",
            List.of(new FieldRef("Region", null, null, null, null))));
      assertTrue(thrown.getMessage().contains("Region"));
   }

   @Test
   void clearsAShelfWhenGivenNoFields() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
                                   List.of(new FieldRef("Region", "dimension", null, null, null)));

      ChartBindingMutator.setShelf(model, "x", List.of());

      assertTrue(model.getXFields().isEmpty());
   }

   // ── specialized shelves (2b Phase 2) ──────────────────────────────────────
   //
   // These hold ONE field each, not a list: a candlestick has one close, a Gantt one start.
   // They are separate from x/y/group because a chart type that uses them ignores those, and
   // binding to the wrong family renders an empty chart with no error.

   @Test
   void setsEachSingleFieldShelf() {
      for(String shelf : List.of("open", "high", "low", "close", "path", "source", "target",
                                 "start", "end", "milestone"))
      {
         ChartBindingModel model = new ChartBindingModel();

         ChartBindingMutator.setSingleShelf(
            model, shelf, new FieldRef("Price", "measure", "Sum", null, null));

         assertNotNull(ChartBindingMutator.readSingleShelf(model, shelf),
                       shelf + " must be readable after being set");
      }
   }

   @Test
   void clearsASingleFieldShelfWithAnExplicitNull() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setSingleShelf(
         model, "close", new FieldRef("Price", "measure", "Sum", null, null));

      ChartBindingMutator.setSingleShelf(model, "close", null);

      assertNull(ChartBindingMutator.readSingleShelf(model, "close"));
   }

   @Test
   void rejectsAnUnknownSingleShelfNamingTheValidOnes() {
      ChartBindingModel model = new ChartBindingModel();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setSingleShelf(
            model, "volume", new FieldRef("V", "measure", "Sum", null, null)));
      assertTrue(thrown.getMessage().contains("volume"));
      assertTrue(thrown.getMessage().contains("close"), "list the shelves that do exist");
   }

   /**
    * x/y/group hold lists; the specialized shelves hold one field. Routing a single-field shelf
    * through set_chart_shelf would silently bind only the first of a list, so the two families
    * refuse each other by name.
    */
   @Test
   void theTwoShelfFamiliesRefuseEachOther() {
      ChartBindingModel model = new ChartBindingModel();

      Exception listOnSingle = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(
            model, "close", List.of(new FieldRef("Price", "measure", "Sum", null, null))));
      assertTrue(listOnSingle.getMessage().contains("close"));
      assertTrue(listOnSingle.getMessage().contains("set_chart_single_shelf"));

      Exception singleOnList = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setSingleShelf(
            model, "x", new FieldRef("Region", "dimension", null, null, null)));
      assertTrue(singleOnList.getMessage().contains("set_chart_shelf"));
   }

   @Test
   void theDeclaredAestheticSplitCoversThirteenFields() {
      assertEquals(13, ChartBindingFields.AESTHETIC.size(),
                   "the 2b/2c split is declared once; changing it changes both sides");
   }

   // ── per-dimension sort/ranking (bug #76350, PCB-001) ──────────────────────
   //
   // ChartDimensionRefModel extends BDimensionRefModel, the same class TableBindingMutator
   // already drives with DimensionSortRanking for a crosstab's rows/cols — these mirror that
   // suite's shape for a chart's x/y/group shelves.

   @Test
   void sortsADimensionByABoundMeasuresValue() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));

      ChartBindingMutator.setSort(model, "x", "Region", null,
         new DimensionSortRanking.Sort("value_desc", "Sales", null));

      Map<String, Object> described = ChartBindingMutator.describeSorts(model, "x");
      assertEquals("value_desc", ((Map<?, ?>) described.get("Region")).get("direction"));
      assertEquals("Sales", ((Map<?, ?>) described.get("Region")).get("sortByField"));
   }

   @Test
   void ranksADimensionByABoundMeasure() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));

      ChartBindingMutator.setRanking(model, "x", "Region", null,
         new DimensionSortRanking.Ranking("top", 5, "Sales", true));

      Map<String, Object> described = ChartBindingMutator.describeSorts(model, "x");
      Map<?, ?> region = (Map<?, ?>) described.get("Region");
      assertEquals("top", region.get("ranking"));
      assertEquals("5", region.get("rankingN"));
      assertEquals("Sales", region.get("rankingMeasure"));
   }

   @Test
   void rejectsSortingAColumnNotOnTheShelfNamingWhatIsBound() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setSort(model, "x", "Product", null,
            new DimensionSortRanking.Sort("asc", null, null)));
      assertTrue(thrown.getMessage().contains("Product"));
      assertTrue(thrown.getMessage().contains("Region"));
   }

   @Test
   void doesNotConfuseAMeasureOnTheShelfWithADimension() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setSort(model, "y", "Sales", null,
            new DimensionSortRanking.Sort("asc", null, null)));
      assertTrue(thrown.getMessage().contains("Sales"));
   }

   // ── org column-count limit (L3-Group1 finding G1-1) ───────────────────────
   //
   // VSChartDndService.addColumns refuses a drag-drop add that would push a chart's total
   // bound-field count past Util.getOrganizationMaxColumn() -- the agent path had no equivalent
   // check at all, live-confirmed 2026-09-01 by binding 286 fields to one chart's x shelf in a
   // single call with zero rejection. These exercise the new check via the chartInfo-carrying
   // overloads only -- the no-chartInfo overloads every other test in this class uses are
   // untouched (chartInfo == null skips the check by design, matching a caller with no live
   // chart to total against).

   @Test
   void refusesAShelfWriteThatWouldExceedTheOrgColumnLimit() throws Exception {
      String original = SreeEnv.getProperty("max.col.count");

      try {
         SreeEnv.setProperty("max.col.count", "2");
         VSChartInfo chartInfo = new VSChartInfo();
         chartInfo.addYField(new VSChartAggregateRef());
         chartInfo.addYField(new VSChartAggregateRef());
         ChartBindingModel model = new ChartBindingModel();

         Exception thrown = assertThrows(
            IllegalArgumentException.class,
            () -> ChartBindingMutator.setShelf(
               model, "x", List.of(new FieldRef("Region", "dimension", null, null, null)),
               null, null, null, chartInfo));

         assertTrue(thrown.getMessage().toLowerCase().contains("column")
                    || thrown.getMessage().contains("2"), thrown.getMessage());
         assertTrue(model.getXFields().isEmpty(),
                    "the refused write must not have mutated the model");
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }

   @Test
   void allowsAShelfWriteWithinTheOrgColumnLimit() throws Exception {
      String original = SreeEnv.getProperty("max.col.count");

      try {
         SreeEnv.setProperty("max.col.count", "2");
         VSChartInfo chartInfo = new VSChartInfo();
         chartInfo.addYField(new VSChartAggregateRef());
         ChartBindingModel model = new ChartBindingModel();

         ChartBindingMutator.setShelf(
            model, "x", List.of(new FieldRef("Region", "dimension", null, null, null)),
            null, null, null, chartInfo);

         assertEquals(1, model.getXFields().size());
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }

   @Test
   void replacingAShelfDoesNotDoubleCountItsOwnPriorFields() throws Exception {
      // The check must subtract the shelf's OWN current size before adding the new size --
      // otherwise re-setting a shelf to the same field count it already has would look like
      // growth and eventually refuse a no-op write.
      String original = SreeEnv.getProperty("max.col.count");

      try {
         SreeEnv.setProperty("max.col.count", "1");
         VSChartInfo chartInfo = new VSChartInfo();
         chartInfo.addXField(new VSChartAggregateRef());
         ChartBindingModel model = new ChartBindingModel();
         model.getXFields().add(new ChartDimensionRefModel());

         ChartBindingMutator.setShelf(
            model, "x", List.of(new FieldRef("Region", "dimension", null, null, null)),
            null, null, null, chartInfo);

         assertEquals(1, model.getXFields().size());
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }

   @Test
   void growingAShelfDoesNotDoubleCountItsOwnPriorFieldsAgainstTheLimit() throws Exception {
      // Unlike replacingAShelfDoesNotDoubleCountItsOwnPriorFields (a net-neutral edit that now
      // short-circuits through requireColumnLimit's net-growth-only early return before ever
      // reaching the subtraction below), this drives an actual GROWTH of the shelf
      // (newShelfCount > oldShelfCount) so the "- oldShelfCount" term in
      //   chartInfo.getFields().length + geoSize - oldShelfCount + newShelfCount
      // is the thing standing between pass and fail. chartInfo already carries the shelf's own
      // 2 prior fields (mirroring the model's 2), so a version of the formula that forgot to
      // subtract oldShelfCount would double-count them: 2 + 3 = 5 > 3, refused. Correctly
      // subtracting them gives 2 - 2 + 3 = 3, which is exactly at the limit and must be allowed.
      String original = SreeEnv.getProperty("max.col.count");

      try {
         SreeEnv.setProperty("max.col.count", "3");
         VSChartInfo chartInfo = new VSChartInfo();
         chartInfo.addXField(new VSChartAggregateRef());
         chartInfo.addXField(new VSChartAggregateRef());
         ChartBindingModel model = new ChartBindingModel();
         model.getXFields().add(new ChartDimensionRefModel());
         model.getXFields().add(new ChartDimensionRefModel());

         // Grow x from 2 fields to 3 -- a strict increase -- which must be allowed because the
         // shelf's own 2 prior fields are subtracted back out before comparing to the limit.
         ChartBindingMutator.setShelf(
            model, "x",
            List.of(new FieldRef("A", "dimension", null, null, null),
                    new FieldRef("B", "dimension", null, null, null),
                    new FieldRef("C", "dimension", null, null, null)),
            null, null, null, chartInfo);

         assertEquals(3, model.getXFields().size());
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }

   @Test
   void refusesASingleShelfWriteThatWouldExceedTheOrgColumnLimit() throws Exception {
      String original = SreeEnv.getProperty("max.col.count");

      try {
         SreeEnv.setProperty("max.col.count", "1");
         VSChartInfo chartInfo = new VSChartInfo();
         chartInfo.addYField(new VSChartAggregateRef());
         ChartBindingModel model = new ChartBindingModel();

         Exception thrown = assertThrows(
            IllegalArgumentException.class,
            () -> ChartBindingMutator.setSingleShelf(
               model, "close", new FieldRef("Price", "measure", "Sum", null, null),
               null, null, null, chartInfo));

         assertTrue(thrown.getMessage().toLowerCase().contains("column")
                    || thrown.getMessage().contains("1"), thrown.getMessage());
         assertNull(ChartBindingMutator.readSingleShelf(model, "close"));
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }

   // ── net-growth-only column limit (PR #4921 round-1 finding 1) ────────────
   //
   // requireColumnLimit compared the ABSOLUTE post-edit total against the org limit, so a chart
   // already over budget (grandfathered, or the limit lowered by an admin after creation) became
   // permanently unable to have ANY shelf edited via the wiz path -- even a strict shrink --
   // because native's VSChartDndService.removeColumns has no limit check at all while addColumns
   // does. Live-confirmed 2026-09-01: shrinking a 5-field shelf to 4 fields under max.col.count=3
   // still threw. The fix gates the check on net growth of the shelf being written.

   @Test
   void allowsANetDecreaseOnAShelfEvenWhenTheChartIsAlreadyOverTheLimit() throws Exception {
      String original = SreeEnv.getProperty("max.col.count");

      try {
         SreeEnv.setProperty("max.col.count", "3");
         // The chart is already over budget: 5 fields on x alone, against a limit of 3.
         VSChartInfo chartInfo = new VSChartInfo();
         chartInfo.addXField(new VSChartAggregateRef());
         chartInfo.addXField(new VSChartAggregateRef());
         chartInfo.addXField(new VSChartAggregateRef());
         chartInfo.addXField(new VSChartAggregateRef());
         chartInfo.addXField(new VSChartAggregateRef());
         ChartBindingModel model = new ChartBindingModel();
         model.getXFields().add(new ChartDimensionRefModel());
         model.getXFields().add(new ChartDimensionRefModel());
         model.getXFields().add(new ChartDimensionRefModel());
         model.getXFields().add(new ChartDimensionRefModel());
         model.getXFields().add(new ChartDimensionRefModel());

         // Shrink x from 5 fields to 4 -- a strict decrease -- and it must be allowed even though
         // the chart's total (before and after) is still over the limit of 3.
         ChartBindingMutator.setShelf(
            model, "x",
            List.of(new FieldRef("A", "dimension", null, null, null),
                    new FieldRef("B", "dimension", null, null, null),
                    new FieldRef("C", "dimension", null, null, null),
                    new FieldRef("D", "dimension", null, null, null)),
            null, null, null, chartInfo);

         assertEquals(4, model.getXFields().size());
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }

   @Test
   void allowsANetNeutralEditOnAShelfEvenWhenTheChartIsAlreadyOverTheLimit() throws Exception {
      String original = SreeEnv.getProperty("max.col.count");

      try {
         SreeEnv.setProperty("max.col.count", "1");
         VSChartInfo chartInfo = new VSChartInfo();
         chartInfo.addXField(new VSChartAggregateRef());
         chartInfo.addXField(new VSChartAggregateRef());
         ChartBindingModel model = new ChartBindingModel();
         model.getXFields().add(new ChartDimensionRefModel());
         model.getXFields().add(new ChartDimensionRefModel());

         // Same field count in, same count out -- no growth at all -- must be allowed despite
         // the chart already sitting at twice the limit.
         ChartBindingMutator.setShelf(
            model, "x",
            List.of(new FieldRef("A", "dimension", null, null, null),
                    new FieldRef("B", "dimension", null, null, null)),
            null, null, null, chartInfo);

         assertEquals(2, model.getXFields().size());
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }

   @Test
   void stillRefusesANetIncreaseThatPushesAnAlreadyOverLimitChartFurtherOver() throws Exception {
      // Net growth must still be checked -- the fix only exempts neutral/decreasing edits, not
      // every edit on an over-limit chart.
      String original = SreeEnv.getProperty("max.col.count");

      try {
         SreeEnv.setProperty("max.col.count", "3");
         VSChartInfo chartInfo = new VSChartInfo();
         chartInfo.addXField(new VSChartAggregateRef());
         chartInfo.addXField(new VSChartAggregateRef());
         chartInfo.addXField(new VSChartAggregateRef());
         chartInfo.addXField(new VSChartAggregateRef());
         chartInfo.addXField(new VSChartAggregateRef());
         ChartBindingModel model = new ChartBindingModel();
         model.getXFields().add(new ChartDimensionRefModel());
         model.getXFields().add(new ChartDimensionRefModel());
         model.getXFields().add(new ChartDimensionRefModel());
         model.getXFields().add(new ChartDimensionRefModel());
         model.getXFields().add(new ChartDimensionRefModel());

         Exception thrown = assertThrows(
            IllegalArgumentException.class,
            () -> ChartBindingMutator.setShelf(
               model, "x",
               List.of(new FieldRef("A", "dimension", null, null, null),
                       new FieldRef("B", "dimension", null, null, null),
                       new FieldRef("C", "dimension", null, null, null),
                       new FieldRef("D", "dimension", null, null, null),
                       new FieldRef("E", "dimension", null, null, null),
                       new FieldRef("F", "dimension", null, null, null)),
               null, null, null, chartInfo));

         assertTrue(thrown.getMessage().toLowerCase().contains("column")
                    || thrown.getMessage().contains("3"), thrown.getMessage());
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }

   // ── VSMapInfo geo-field branch (PR #4921 round-1 finding 4) ───────────────
   //
   // requireColumnLimit adds VSMapInfo.getGeoFieldCount() to the chart's own getFields().length
   // total, since a map's geo fields are not part of getFields() at all -- this exercises that
   // branch, which no prior test in this class touched.

   @Test
   void countsGeoFieldsTowardTheLimitOnAMapChart() throws Exception {
      String original = SreeEnv.getProperty("max.col.count");

      try {
         SreeEnv.setProperty("max.col.count", "2");
         VSMapInfo chartInfo = new VSMapInfo();
         chartInfo.addYField(new VSChartAggregateRef());
         chartInfo.addGeoField(new VSChartGeoRef());
         ChartBindingModel model = new ChartBindingModel();

         // 1 y field + 1 geo field + 1 new x field = 3, over a limit of 2 -- refused only because
         // the geo field is counted; without it the total would be 2 and would pass.
         Exception thrown = assertThrows(
            IllegalArgumentException.class,
            () -> ChartBindingMutator.setShelf(
               model, "x", List.of(new FieldRef("Region", "dimension", null, null, null)),
               null, null, null, chartInfo));

         assertTrue(thrown.getMessage().toLowerCase().contains("column")
                    || thrown.getMessage().contains("2"), thrown.getMessage());
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }

   @Test
   void allowsAMapChartWriteWhenGeoAndFieldsTogetherStayWithinTheLimit() throws Exception {
      String original = SreeEnv.getProperty("max.col.count");

      try {
         SreeEnv.setProperty("max.col.count", "3");
         VSMapInfo chartInfo = new VSMapInfo();
         chartInfo.addYField(new VSChartAggregateRef());
         chartInfo.addGeoField(new VSChartGeoRef());
         ChartBindingModel model = new ChartBindingModel();

         ChartBindingMutator.setShelf(
            model, "x", List.of(new FieldRef("Region", "dimension", null, null, null)),
            null, null, null, chartInfo);

         assertEquals(1, model.getXFields().size());
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }
}
