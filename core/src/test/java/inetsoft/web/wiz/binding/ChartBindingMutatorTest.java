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
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartGeoRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.VSMapInfo;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.aesthetic.StaticColorModel;
import inetsoft.web.binding.model.graph.aesthetic.StaticLineModel;
import inetsoft.web.binding.model.graph.aesthetic.StaticShapeModel;
import inetsoft.web.binding.model.graph.aesthetic.StaticSizeModel;
import inetsoft.web.binding.model.graph.aesthetic.StaticTextureModel;
import inetsoft.web.binding.model.graph.calc.RunningTotalCalcInfo;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

   // ── chartType survives a shelf rewrite (Bug #76689, VCS-005) ──────────────────────────────
   //
   // toChartRef never sets a chartType on the refs it builds (requireNoInboundChartType refuses
   // one arriving inbound, by design), so every setShelf call used to silently reset whatever a
   // prior set_chart_type had stored -- confirmed live to be specific to this write path, not
   // StyleBI generally: the native Composer UI's own drag-and-drop add does not reset an existing
   // measure's type.

   @Test
   void preservesChartTypeWhenAddingAFieldToAnAlreadyTypedYShelf() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null),
                 new FieldRef("Orders", "measure", "DistinctCount", null, null)));

      // Simulate a prior set_chart_type(field: "DistinctCount(Orders)", type: line) write.
      ((ChartAggregateRefModel) model.getYFields().get(1)).setChartType(GraphTypes.CHART_LINE);

      // An ordinary incremental edit -- add a third field, the other two unchanged -- not a
      // literal resend.
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null),
                 new FieldRef("Orders", "measure", "DistinctCount", null, null),
                 new FieldRef("Quantity", "measure", "Sum", null, null)));

      assertEquals(GraphTypes.CHART_LINE,
                   ((ChartAggregateRefModel) model.getYFields().get(1)).getChartType(),
                   "the previously-typed measure must keep its chartType across the rewrite");
      assertEquals(GraphTypes.CHART_AUTO,
                   ((ChartAggregateRefModel) model.getYFields().get(2)).getChartType(),
                   "the newly-added measure has nothing to restore -- stays auto");
   }

   @Test
   void preservesChartTypeOnTheLiteralResendCase() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));
      ((ChartAggregateRefModel) model.getYFields().get(0)).setChartType(GraphTypes.CHART_LINE);

      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));

      assertEquals(GraphTypes.CHART_LINE,
                   ((ChartAggregateRefModel) model.getYFields().get(0)).getChartType());
   }

   @Test
   void removingATypedFieldDropsItsTypeRatherThanMisapplyingItElsewhere() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null),
                 new FieldRef("Orders", "measure", "DistinctCount", null, null)));
      ((ChartAggregateRefModel) model.getYFields().get(1)).setChartType(GraphTypes.CHART_LINE);

      // Orders is dropped entirely -- nothing should crash, and Sales must not inherit its type.
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));

      assertEquals(1, model.getYFields().size());
      assertEquals(GraphTypes.CHART_AUTO,
                   ((ChartAggregateRefModel) model.getYFields().get(0)).getChartType());
   }

   /**
    * Two measures sharing the same column+aggregate, differing only by {@code secondaryY} (the
    * VCS-014 collision shape) -- each must restore onto its OWN match, not both onto whichever is
    * found first.
    */
   @Test
   void restoresEachCollidingMeasuresOwnChartTypeSeparately() {
      ChartBindingModel model = new ChartBindingModel();
      FieldRef primary = new FieldRef("Total", "measure", "Sum", null, null, null, null, null,
                                      null, null, false);
      FieldRef secondary = new FieldRef("Total", "measure", "Sum", null, null, null, null, null,
                                        null, null, true);
      ChartBindingMutator.setShelf(model, "y", List.of(primary, secondary));

      ((ChartAggregateRefModel) model.getYFields().get(0)).setChartType(GraphTypes.CHART_BAR);
      ((ChartAggregateRefModel) model.getYFields().get(1)).setChartType(GraphTypes.CHART_LINE);

      ChartBindingMutator.setShelf(model, "y", List.of(primary, secondary));

      assertEquals(GraphTypes.CHART_BAR,
                   ((ChartAggregateRefModel) model.getYFields().get(0)).getChartType());
      assertEquals(GraphTypes.CHART_LINE,
                   ((ChartAggregateRefModel) model.getYFields().get(1)).getChartType());
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

   /**
    * A bare {@code sortByField}/{@code measure} that names a column bound as a measure more than
    * once (under different aggregates) used to silently resolve to whichever binding came first,
    * with no error. Bug #76689, VCS-014.
    */
   @Test
   void rejectsAnAmbiguousBareSortByField() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Total", "measure", "Sum", null, null),
                 new FieldRef("Total", "measure", "Average", null, null)));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setSort(model, "x", "Region", null,
            new DimensionSortRanking.Sort("value_desc", "Total", null)));
      assertTrue(thrown.getMessage().contains("Sum(Total)"));
      assertTrue(thrown.getMessage().contains("Average(Total)"));
   }

   /** The already-qualified form is unambiguous by construction and always passes through. */
   @Test
   void acceptsAnAlreadyQualifiedSortByFieldEvenWhenAnAmbiguousSiblingExists() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Total", "measure", "Sum", null, null),
                 new FieldRef("Total", "measure", "Average", null, null)));

      ChartBindingMutator.setSort(model, "x", "Region", null,
         new DimensionSortRanking.Sort("value_desc", "Average(Total)", null));

      Map<String, Object> described = ChartBindingMutator.describeSorts(model, "x");
      assertEquals("Average(Total)", ((Map<?, ?>) described.get("Region")).get("sortByField"));
   }

   /** Same ambiguity, same fix, for ranking's {@code measure} parameter. */
   @Test
   void rejectsAnAmbiguousBareRankingMeasure() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Total", "measure", "Sum", null, null),
                 new FieldRef("Total", "measure", "Average", null, null)));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setRanking(model, "x", "Region", null,
            new DimensionSortRanking.Ranking("top", 5, "Total", null)));
      assertTrue(thrown.getMessage().contains("Sum(Total)"));
      assertTrue(thrown.getMessage().contains("Average(Total)"));
   }

   /** A bare name matching exactly one bound measure is unambiguous and unaffected. */
   @Test
   void acceptsAnUnambiguousBareSortByField() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Total", "measure", "Sum", null, null)));

      ChartBindingMutator.setSort(model, "x", "Region", null,
         new DimensionSortRanking.Sort("value_desc", "Total", null));

      Map<String, Object> described = ChartBindingMutator.describeSorts(model, "x");
      assertEquals("Total", ((Map<?, ?>) described.get("Region")).get("sortByField"));
   }

   /**
    * Round-trip review finding on this same PR: the already-qualified fast path used to return
    * on the *first* ref whose qualified name matched, without checking whether a SECOND ref
    * stringifies identically -- exactly the collision {@code preserveChartTypes} already handles
    * for VCS-005 (same column+aggregate, differing only by {@code secondaryY}), reproducing the
    * same silent-first-match failure one level up, at the qualified-name granularity instead of
    * the bare-column one. There is no further qualified string to offer here, so this must be
    * refused outright rather than accepted as if it named one binding.
    */
   @Test
   void rejectsAnAlreadyQualifiedSortByFieldThatIsItselfAmbiguous() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Total", "measure", "Sum", null, null, null, null, null, null, null,
                              false),
                 new FieldRef("Total", "measure", "Sum", null, null, null, null, null, null, null,
                              true)));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setSort(model, "x", "Region", null,
            new DimensionSortRanking.Sort("value_desc", "Sum(Total)", null)));
      assertTrue(thrown.getMessage().contains("Sum(Total)"));
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

   // ── sort/ranking survives a shelf rewrite (bug #76881, porting VTB-004/Redmine #76574) ────
   //
   // setShelf/FieldRefFactory.toChartRef used to build a brand-new ChartDimensionRefModel for
   // every dimension on every write, discarding order/sortByCol/rankingOpt/rankingN/rankingCol/
   // manualOrder/groupOthers/others even for a field that did not change -- the same defect
   // shape VTB-004 closed for TableBindingMutator.dimensions(), never ported to this chart
   // path. Matches a new field to the shelf's own previous list by same absolute index + same
   // column (case-insensitive) + same date level, mirroring dimensions()'s matches()/copyOf().

   @Test
   void resubmittingTheIdenticalXShelfPreservesSortAndRanking() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));
      ChartBindingMutator.setSort(model, "x", "Region", null,
         new DimensionSortRanking.Sort("value_desc", "Sales", null));
      ChartBindingMutator.setRanking(model, "x", "Region", null,
         new DimensionSortRanking.Ranking("top", 5, "Sales", null));

      // The filed repro: resubmit the identical field list to the same shelf.
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));

      Map<?, ?> region = (Map<?, ?>) ChartBindingMutator.describeSorts(model, "x").get("Region");
      assertEquals("value_desc", region.get("direction"),
         "an unchanged x dimension's sort must survive a shelf resubmission");
      assertEquals("Sales", region.get("sortByField"));
      assertEquals("top", region.get("ranking"));
      assertEquals("5", region.get("rankingN"));
      assertEquals("Sales", region.get("rankingMeasure"));
   }

   @Test
   void resubmittingTheIdenticalYShelfPreservesSortOnADimension() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Region", "dimension", null, null, null)));
      ChartBindingMutator.setSort(model, "y", "Region", null,
         new DimensionSortRanking.Sort("desc", null, null));

      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Region", "dimension", null, null, null)));

      Map<?, ?> region = (Map<?, ?>) ChartBindingMutator.describeSorts(model, "y").get("Region");
      assertEquals("desc", region.get("direction"),
         "an unchanged y dimension's sort must survive a shelf resubmission");
   }

   @Test
   void resubmittingTheIdenticalGroupShelfPreservesSortAndRanking() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));
      ChartBindingMutator.setShelf(model, "group",
         List.of(new FieldRef("Category", "dimension", null, null, null)));
      ChartBindingMutator.setSort(model, "group", "Category", null,
         new DimensionSortRanking.Sort("value_desc", "Sales", null));

      ChartBindingMutator.setShelf(model, "group",
         List.of(new FieldRef("Category", "dimension", null, null, null)));

      Map<?, ?> category =
         (Map<?, ?>) ChartBindingMutator.describeSorts(model, "group").get("Category");
      assertEquals("value_desc", category.get("direction"),
         "an unchanged group dimension's sort must survive a shelf resubmission");
      assertEquals("Sales", category.get("sortByField"));
   }

   @Test
   void addingAFieldToXPreservesAnExistingDimensionsSortAndRanking() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));
      ChartBindingMutator.setSort(model, "x", "Region", null,
         new DimensionSortRanking.Sort("desc", null, null));

      // An ordinary incremental edit -- append a second dimension -- not a literal resend.
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null),
                 new FieldRef("Category", "dimension", null, null, null)));

      Map<?, ?> region = (Map<?, ?>) ChartBindingMutator.describeSorts(model, "x").get("Region");
      assertEquals("desc", region.get("direction"),
         "REGION stays at index 0, so appending CATEGORY after it must not reset its sort");
   }

   @Test
   void resubmittingADuplicateBoundColumnOnXKeepsEachOccurrencesSortSeparate() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Order Date", "dimension", null, "year", null),
                 new FieldRef("Order Date", "dimension", null, "quarter", null)));
      ChartBindingMutator.setSort(model, "x", "Order Date", 0,
         new DimensionSortRanking.Sort("desc", null, null));
      ChartBindingMutator.setSort(model, "x", "Order Date", 1,
         new DimensionSortRanking.Sort("manual", null, List.of("Q1", "Q2", "Q3", "Q4")));

      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Order Date", "dimension", null, "year", null),
                 new FieldRef("Order Date", "dimension", null, "quarter", null)));

      assertEquals(XConstants.SORT_DESC,
         ((ChartDimensionRefModel) model.getXFields().get(0)).getOrder(),
         "the year occurrence's sort must not cross-contaminate with the quarter occurrence's");
      assertEquals(XConstants.SORT_SPECIFIC,
         ((ChartDimensionRefModel) model.getXFields().get(1)).getOrder());
      assertEquals(List.of("Q1", "Q2", "Q3", "Q4"),
         ((ChartDimensionRefModel) model.getXFields().get(1)).getManualOrder());
   }

   /**
    * Mirrors {@code TableBindingMutatorTest}'s identical PR #5178 review finding, ported to the
    * chart path: {@code order} copied forward from a matched previous ref can carry a stray
    * {@code SORT_SPECIFIC} bit once the incoming field no longer supplies the named group
    * backing it.
    */
   @Test
   void resubmittingANamedGroupedDimensionWithoutNamedGroupClearsSortSpecific() {
      ChartBindingModel model = new ChartBindingModel();
      FieldRef.NamedGroupValues coastal = new FieldRef.NamedGroupValues(
         List.of(new FieldRef.NamedGroupValues.Clause("West", List.of("CA", "OR"))), null);
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null, null, null, coastal)));
      assertEquals(XConstants.SORT_SPECIFIC,
         ((ChartDimensionRefModel) model.getXFields().get(0)).getOrder());

      // Resubmit REGION at the same index WITHOUT namedGroupValues -- dropping it.
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));

      ChartDimensionRefModel dimension = (ChartDimensionRefModel) model.getXFields().get(0);
      assertEquals(0, dimension.getOrder() & XConstants.SORT_SPECIFIC,
         "order must not be left with the SORT_SPECIFIC bit set once the named group backing " +
         "it is dropped");
      assertNull(dimension.getNamedGroupInfo());
   }

   // ── measure calculateInfo/secondaryY survive a shelf rewrite (bug #76896) ────────────────
   //
   // toChartRef's MEASURE branch builds a fresh ChartAggregateRefModel on every setShelf call:
   // calculateInfo is copied only when the incoming field itself supplies one, and secondaryY is
   // forced unconditionally to false whenever the incoming field omits it -- neither ever had a
   // previous-state fallback. The crosstab side of this exact shape was fixed for bug #76881;
   // this ports the same idea to the chart aggregate-ref path (preserveAggregateState, matched
   // via sameMeasure -- the same column+formula identity preserveChartTypes already uses for
   // chartType, minus its secondaryY tiebreak, which would be circular here).

   private static RunningTotalCalcInfo runningTotal(String aggregate) {
      RunningTotalCalcInfo calc = new RunningTotalCalcInfo();
      calc.setAggregate(aggregate);
      return calc;
   }

   private static FieldRef measureWithCalc(String column, String aggregate,
                                           RunningTotalCalcInfo calc)
   {
      return new FieldRef(column, "measure", aggregate, null, null, null, null, null, calc);
   }

   private static FieldRef measureWithSecondaryY(String column, String aggregate,
                                                 Boolean secondaryY)
   {
      return new FieldRef(column, "measure", aggregate, null, null, null, null, null, null, null,
                          secondaryY);
   }

   @Test
   void resubmittingTheIdenticalYShelfPreservesAMeasuresCalculateInfo() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(measureWithCalc("Sales", "Sum", runningTotal("Sum"))));

      // The incoming field omits calculateInfo entirely on the resubmit.
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));

      RunningTotalCalcInfo calc = (RunningTotalCalcInfo)
         ((ChartAggregateRefModel) model.getYFields().get(0)).getCalculateInfo();
      assertNotNull(calc, "a measure's calculateInfo must survive a shelf resubmission");
      assertEquals("Sum", calc.getAggregate());
   }

   @Test
   void resubmittingTheIdenticalXShelfPreservesAMeasuresCalculateInfo() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(measureWithCalc("Sales", "Sum", runningTotal("Sum"))));

      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));

      assertNotNull(((ChartAggregateRefModel) model.getXFields().get(0)).getCalculateInfo(),
         "calculateInfo has no shelf restriction -- must survive on x too");
   }

   @Test
   void resubmittingTheIdenticalGroupShelfPreservesAMeasuresCalculateInfo() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "group",
         List.of(measureWithCalc("Sales", "Sum", runningTotal("Sum"))));

      ChartBindingMutator.setShelf(model, "group",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));

      assertNotNull(((ChartAggregateRefModel) model.getGroupFields().get(0)).getCalculateInfo(),
         "calculateInfo has no shelf restriction -- must survive on group too");
   }

   /**
    * {@code secondaryY} is only ever {@code true} on {@code y} -- the plugin layer refuses it
    * outright on {@code x}/{@code group} (bug #76608), so there is no x/group analogue to write
    * here: nothing there can ever have a previous {@code true} value to lose.
    */
   @Test
   void resubmittingTheIdenticalYShelfPreservesAMeasuresSecondaryY() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(measureWithSecondaryY("Sales", "Sum", true)));

      // The incoming field omits secondaryY entirely (null, not an explicit false).
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));

      assertTrue(((ChartAggregateRefModel) model.getYFields().get(0)).isSecondaryY(),
         "an omitted secondaryY must preserve the measure's previous value, not reset to false");
   }

   @Test
   void explicitlyClearingSecondaryYActuallyClearsItRatherThanBeingTreatedAsOmitted() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(measureWithSecondaryY("Sales", "Sum", true)));

      // The caller explicitly sends secondaryY: false this time -- must actually clear it.
      ChartBindingMutator.setShelf(model, "y",
         List.of(measureWithSecondaryY("Sales", "Sum", false)));

      assertFalse(((ChartAggregateRefModel) model.getYFields().get(0)).isSecondaryY(),
         "an explicit false must clear a previously-true secondaryY, not be treated as omitted");
   }

   @Test
   void newCalculateInfoOnResubmitOverridesThePreservedOne() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(measureWithCalc("Sales", "Sum", runningTotal("Sum"))));

      ChartBindingMutator.setShelf(model, "y",
         List.of(measureWithCalc("Sales", "Sum", runningTotal("Average"))));

      RunningTotalCalcInfo calc = (RunningTotalCalcInfo)
         ((ChartAggregateRefModel) model.getYFields().get(0)).getCalculateInfo();
      assertEquals("Average", calc.getAggregate(),
         "an explicitly-supplied calculateInfo must override the preserved one, not be ignored");
   }

   @Test
   void resubmittingADuplicateBoundMeasureKeepsEachFormulasCalculateInfoSeparate() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(measureWithCalc("Total", "Sum", runningTotal("Sum")),
                 measureWithCalc("Total", "Average", runningTotal("Average"))));

      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Total", "measure", "Sum", null, null),
                 new FieldRef("Total", "measure", "Average", null, null)));

      RunningTotalCalcInfo sum = (RunningTotalCalcInfo)
         ((ChartAggregateRefModel) model.getYFields().get(0)).getCalculateInfo();
      RunningTotalCalcInfo average = (RunningTotalCalcInfo)
         ((ChartAggregateRefModel) model.getYFields().get(1)).getCalculateInfo();
      assertEquals("Sum", sum.getAggregate());
      assertEquals("Average", average.getAggregate());
   }

   /**
    * The VCS-014 collision shape ({@code preserveChartTypes}'s own tiebreaker case) -- same
    * column+formula, differing only by {@code secondaryY}. Each occurrence must preserve its own
    * state without swapping onto the other.
    */
   @Test
   void resubmittingTwoCollidingMeasuresPreservesEachOwnCalculateInfoAndSecondaryYSeparately() {
      ChartBindingModel model = new ChartBindingModel();
      FieldRef primary = new FieldRef("Total", "measure", "Sum", null, null, null, null, null,
                                      runningTotal("Sum"), null, false);
      FieldRef secondary = new FieldRef("Total", "measure", "Sum", null, null, null, null, null,
                                        runningTotal("Average"), null, true);
      ChartBindingMutator.setShelf(model, "y", List.of(primary, secondary));

      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Total", "measure", "Sum", null, null),
                 new FieldRef("Total", "measure", "Sum", null, null)));

      ChartAggregateRefModel first = (ChartAggregateRefModel) model.getYFields().get(0);
      ChartAggregateRefModel second = (ChartAggregateRefModel) model.getYFields().get(1);
      assertEquals("Sum", ((RunningTotalCalcInfo) first.getCalculateInfo()).getAggregate());
      assertFalse(first.isSecondaryY());
      assertEquals("Average", ((RunningTotalCalcInfo) second.getCalculateInfo()).getAggregate());
      assertTrue(second.isSecondaryY());
   }

   /**
    * The case that distinguishes a consume-based match from an index-based one: inserting a new
    * measure AHEAD of an existing one shifts the existing one's index, so a plain
    * {@code oldRefs.get(i)} lookup would miss it entirely -- the same weakness
    * {@code preserveChartTypes} itself was written to avoid for {@code chartType}.
    */
   @Test
   void insertingAMeasureAheadOfAnExistingOnePreservesItsCalculateInfoAndSecondaryY() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));
      ChartAggregateRefModel sales = (ChartAggregateRefModel) model.getYFields().get(0);
      sales.setCalculateInfo(runningTotal("Sum"));
      sales.setSecondaryY(true);

      // An ordinary incremental edit -- insert Profit ahead of Sales -- Sales shifts from
      // index 0 to index 1.
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Profit", "measure", "Sum", null, null),
                 new FieldRef("Sales", "measure", "Sum", null, null)));

      ChartAggregateRefModel resubmittedSales = (ChartAggregateRefModel) model.getYFields().get(1);
      assertNotNull(resubmittedSales.getCalculateInfo(),
         "Sales's calculateInfo must survive despite shifting to a new index");
      assertTrue(resubmittedSales.isSecondaryY(),
         "Sales's secondaryY must survive despite shifting to a new index");
   }

   // ── dimension timeSeries on the chart shelf write path (bug #77021) ─────────────────────
   //
   // toChartRef's dimension branch never applied field.timeSeries() at all -- a brand-new
   // dimension always read back false regardless of what was requested. Separately,
   // preserveDimensionState copied a matched previous ref's timeSeries unconditionally, with no
   // field.timeSeries() == null guard -- so even once toChartRef applies an explicit request,
   // rebinding the same dimension would have that request immediately clobbered by whatever the
   // matched previous ref already had. Both are fixed together here, mirroring the null-guarded
   // pattern preserveAggregateState already gets right for calculateInfo/secondaryY above.

   private static FieldRef dimensionWithTimeSeries(String column, String dateLevel,
                                                    Boolean timeSeries)
   {
      return new FieldRef(column, "dimension", null, dateLevel, null, null, null, null, null, null,
                          null, null, timeSeries);
   }

   @Test
   void settingTimeSeriesTrueOnANewChartDimensionAppliesIt() {
      ChartBindingModel model = new ChartBindingModel();

      ChartBindingMutator.setShelf(model, "x",
         List.of(dimensionWithTimeSeries("Order Date", "quarter", true)));

      assertTrue(((ChartDimensionRefModel) model.getXFields().get(0)).isTimeSeries(),
         "an explicit timeSeries on a brand-new chart dimension must be applied, not dropped");
   }

   @Test
   void resubmittingAChartDimensionWithNoTimeSeriesKeyPreservesItsPriorState() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(dimensionWithTimeSeries("Order Date", "quarter", true)));

      // The same column + date level so it matches the previous ref, with 'timeSeries' entirely
      // omitted (null) this time.
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Order Date", "dimension", null, "quarter", null)));

      assertTrue(((ChartDimensionRefModel) model.getXFields().get(0)).isTimeSeries(),
         "a write that omits 'timeSeries' must preserve the shelf position's prior state, not " +
         "reset it to false");
   }

   @Test
   void explicitlySettingTimeSeriesFalseOnAChartDimensionClearsAPreviouslySetFlag() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(dimensionWithTimeSeries("Order Date", "quarter", true)));

      // The falsifiable case from the diagnosis: an explicit 'false' on rebind must not be
      // clobbered by preserveDimensionState's carry-forward of the matched previous ref's 'true'.
      ChartBindingMutator.setShelf(model, "x",
         List.of(dimensionWithTimeSeries("Order Date", "quarter", false)));

      assertFalse(((ChartDimensionRefModel) model.getXFields().get(0)).isTimeSeries(),
         "an explicit false must clear a previously-true timeSeries, not be treated as omitted");
   }

   // ── secondaryColumn survives a shelf rewrite (bug #77014, VCS-020) ────────────────────────
   //
   // toChartRef only sets secondaryColumnValue when the incoming FieldRef explicitly carries one
   // -- the identical shape as calculateInfo/secondaryY above -- so without a matching
   // preserveAggregateState branch, a Covariance/Correlation/WeightedAverage measure's second
   // column silently reverted to null on the very next ordinary set_chart_shelf call that omitted
   // it (ChartBindingMutatorTest review round 1 on stylebi#5736).

   private static FieldRef measureWithSecondaryColumn(String column, String aggregate,
                                                       String secondaryColumn)
   {
      return new FieldRef(column, "measure", aggregate, null, null, null, null, null, null, null,
                          null, null, null, secondaryColumn);
   }

   @Test
   void resubmittingTheIdenticalYShelfPreservesAMeasuresSecondaryColumn() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(measureWithSecondaryColumn("DISCOUNT", "Covariance", "PAID")));

      // The incoming field omits secondaryColumn entirely on the resubmit.
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("DISCOUNT", "measure", "Covariance", null, null)));

      assertEquals("PAID",
         ((ChartAggregateRefModel) model.getYFields().get(0)).getSecondaryColumnValue(),
         "an omitted secondaryColumn must preserve the measure's previous value, not reset to null");
   }

   /** secondaryColumn has no shelf restriction (unlike secondaryY) -- confirm x survives too. */
   @Test
   void resubmittingTheIdenticalXShelfPreservesAMeasuresSecondaryColumn() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(measureWithSecondaryColumn("DISCOUNT", "Covariance", "PAID")));

      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("DISCOUNT", "measure", "Covariance", null, null)));

      assertEquals("PAID",
         ((ChartAggregateRefModel) model.getXFields().get(0)).getSecondaryColumnValue(),
         "secondaryColumn has no shelf restriction -- must survive on x too");
   }

   @Test
   void newSecondaryColumnOnResubmitOverridesThePreservedOne() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(measureWithSecondaryColumn("DISCOUNT", "Covariance", "PAID")));

      ChartBindingMutator.setShelf(model, "y",
         List.of(measureWithSecondaryColumn("DISCOUNT", "Covariance", "TAX")));

      assertEquals("TAX",
         ((ChartAggregateRefModel) model.getYFields().get(0)).getSecondaryColumnValue(),
         "an explicitly-supplied secondaryColumn must override the preserved one, not be ignored");
   }

   /**
    * Switching a resubmitted measure to a single-column formula changes its {@code formula},
    * which fails {@code sameMeasure}'s column+formula match -- the resubmit builds a brand-new,
    * unmatched aggregate rather than reusing the Covariance one, so there is no previous ref to
    * preserve from in the first place. This is the natural, already-existing "clear" path: no
    * special-case code is needed for it, and none should be added -- without this test a stale
    * secondaryColumnValue left over from a since-abandoned two-column formula could linger
    * unnoticed if a future change altered the matching rule.
    */
   @Test
   void changingToASingleColumnFormulaOnResubmitDoesNotCarryOverAStaleSecondaryColumn() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(measureWithSecondaryColumn("DISCOUNT", "Covariance", "PAID")));

      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("DISCOUNT", "measure", "Sum", null, null)));

      assertNull(((ChartAggregateRefModel) model.getYFields().get(0)).getSecondaryColumnValue(),
         "a formula change to a single-column aggregate must not carry over a stale secondaryColumn");
   }

   // ── measure per-measure visual frames survive a shelf rewrite (bug #76904) ───────────────
   //
   // set_visual_frame/reset_visual_frame (ChartAestheticMutator.assignAggregateFrame) write
   // colorFrame/shapeFrame/sizeFrame/lineFrame/textureFrame directly onto the live ref instances
   // currently on the shelf, but toChartRef builds a brand-new ChartAggregateRefModel on every
   // setShelf call and never sets any of the five -- so they were silently lost on the very next
   // resubmit. Unlike calculateInfo/secondaryY above, FieldRef has no frame fields at all, so the
   // copy is unconditional -- there is no "caller supplied a new one" case to guard against.

   @Test
   void resubmittingTheIdenticalYShelfPreservesAMeasuresColorFrame() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));
      StaticColorModel color = new StaticColorModel();
      color.setColor("#FF0000");
      ((ChartAggregateRefModel) model.getYFields().get(0)).setColorFrame(color);

      // The exact same, unchanged field -- no visual-frame arguments involved at all.
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));

      assertSame(color, ((ChartAggregateRefModel) model.getYFields().get(0)).getColorFrame(),
         "a measure's colorFrame must survive a shelf resubmission");
   }

   @Test
   void resubmittingTheIdenticalYShelfPreservesAMeasuresShapeSizeLineAndTextureFrames() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));
      ChartAggregateRefModel sales = (ChartAggregateRefModel) model.getYFields().get(0);
      StaticShapeModel shape = new StaticShapeModel();
      StaticSizeModel size = new StaticSizeModel();
      StaticLineModel line = new StaticLineModel();
      StaticTextureModel texture = new StaticTextureModel();
      sales.setShapeFrame(shape);
      sales.setSizeFrame(size);
      sales.setLineFrame(line);
      sales.setTextureFrame(texture);

      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));

      ChartAggregateRefModel resubmitted = (ChartAggregateRefModel) model.getYFields().get(0);
      assertSame(shape, resubmitted.getShapeFrame(), "shapeFrame must survive a resubmission");
      assertSame(size, resubmitted.getSizeFrame(), "sizeFrame must survive a resubmission");
      assertSame(line, resubmitted.getLineFrame(), "lineFrame must survive a resubmission");
      assertSame(texture, resubmitted.getTextureFrame(),
         "textureFrame must survive a resubmission");
   }

   /**
    * Guards against over-broad matching in {@code preserveAggregateFrames}: resubmitting a
    * different, unrelated measure on the same shelf must not inherit the previous measure's
    * frame.
    */
   @Test
   void resubmittingADifferentMeasureDoesNotInheritThePreviousMeasuresColorFrame() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null)));
      StaticColorModel color = new StaticColorModel();
      color.setColor("#FF0000");
      ((ChartAggregateRefModel) model.getYFields().get(0)).setColorFrame(color);

      // Sales is replaced outright by an unrelated measure -- no match in unconsumedAggregates.
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Orders", "measure", "Count", null, null)));

      assertNull(((ChartAggregateRefModel) model.getYFields().get(0)).getColorFrame(),
         "an unrelated measure must not inherit a previous, unrelated measure's colorFrame");
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

         // A measure here, not a dimension -- x/y hold lat/lon measures on a map, so this
         // exercises the column-limit logic (1 y + 1 geo + 1 new x = 3, within the limit of 3)
         // without tripping the dimension-on-x/y guard below.
         ChartBindingMutator.setShelf(
            model, "x", List.of(new FieldRef("Longitude", "measure", "Sum", null, null)),
            null, null, null, chartInfo);

         assertEquals(1, model.getXFields().size());
      }
      finally {
         SreeEnv.setProperty("max.col.count", original);
      }
   }

   // ── dimension-on-x/y refused for a map (VBS-007) ──────────────────────────

   @Test
   void refusesADimensionOnXForAMapChart() {
      VSMapInfo chartInfo = new VSMapInfo();
      ChartBindingModel model = new ChartBindingModel();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(
            model, "x", List.of(new FieldRef("State", "dimension", null, null, null)),
            null, null, null, chartInfo));

      assertTrue(thrown.getMessage().contains("x"), thrown.getMessage());
      assertTrue(thrown.getMessage().toLowerCase().contains("map"), thrown.getMessage());
   }

   @Test
   void refusesADimensionOnYForAMapChart() {
      VSMapInfo chartInfo = new VSMapInfo();
      ChartBindingModel model = new ChartBindingModel();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(
            model, "y", List.of(new FieldRef("State", "dimension", null, null, null)),
            null, null, null, chartInfo));

      assertTrue(thrown.getMessage().contains("y"), thrown.getMessage());
      assertTrue(thrown.getMessage().toLowerCase().contains("map"), thrown.getMessage());
   }

   @Test
   void allowsADimensionOnGroupForAMapChart() throws Exception {
      VSMapInfo chartInfo = new VSMapInfo();
      ChartBindingModel model = new ChartBindingModel();

      ChartBindingMutator.setShelf(
         model, "group", List.of(new FieldRef("Category", "dimension", null, null, null)),
         null, null, null, chartInfo);

      assertEquals(1, model.getGroupFields().size());
      assertInstanceOf(ChartDimensionRefModel.class, model.getGroupFields().get(0));
   }

   // ── single-shelf same-field resend carries state forward (bug #78188) ────────────────────
   //
   // setSingleShelf built a fresh ref via toChartRef and assigned it with no carry-forward, so
   // an omitted timeSeries (and sort/ranking, calculateInfo) reset on every same-field resend.
   // Every toChartRef caller that replaces an existing ref must carry state forward on a
   // same-field resend; setShelf already did, setSingleShelf and the aesthetic path did not.

   private static ChartDimensionRefModel singleDim(ChartBindingModel model, String shelf) {
      return (ChartDimensionRefModel) ChartBindingMutator.readSingleShelf(model, shelf);
   }

   /** A dimension ref already stored with timeSeries=true, as native drag-and-drop or a legacy
    *  asset leaves it; the plugin's own write path may refuse to create this state (#78214). */
   private static ChartDimensionRefModel nativeTimeSeriesDim(String column, String level) {
      ChartDimensionRefModel ref = new ChartDimensionRefModel();
      ref.setColumnValue(column);
      ref.setName(column);
      ref.setDateLevel(DateLevels.normalize(level));
      ref.setTimeSeries(true);
      return ref;
   }

   @Test
   void singleShelfSourceResendWithoutTimeSeriesKeyPreservesIt() {
      ChartBindingModel model = new ChartBindingModel();
      model.setSourceField(nativeTimeSeriesDim("Order Date", "day"));

      ChartBindingMutator.setSingleShelf(model, "source",
         new FieldRef("Order Date", "dimension", null, "day", null));

      assertTrue(singleDim(model, "source").isTimeSeries());
   }

   @Test
   void singleShelfPathResendWithoutTimeSeriesKeyPreservesIt() {
      ChartBindingModel model = new ChartBindingModel();
      model.setPathField(nativeTimeSeriesDim("Order Date", "day"));

      ChartBindingMutator.setSingleShelf(model, "path",
         new FieldRef("Order Date", "dimension", null, "day", null));

      assertTrue(singleDim(model, "path").isTimeSeries());
   }

   @Test
   void singleShelfResendWithExplicitFalseClearsTimeSeries() {
      ChartBindingModel model = new ChartBindingModel();
      model.setSourceField(nativeTimeSeriesDim("Order Date", "day"));

      ChartBindingMutator.setSingleShelf(model, "source",
         dimensionWithTimeSeries("Order Date", "day", false));

      assertFalse(singleDim(model, "source").isTimeSeries());
   }

   @Test
   void singleShelfRebindToDifferentColumnDoesNotCarryTimeSeries() {
      ChartBindingModel model = new ChartBindingModel();
      model.setSourceField(nativeTimeSeriesDim("Order Date", "day"));

      ChartBindingMutator.setSingleShelf(model, "source",
         new FieldRef("Ship Date", "dimension", null, "day", null));

      assertFalse(singleDim(model, "source").isTimeSeries());
   }

   @Test
   void singleShelfRebindToDifferentDateLevelDoesNotCarryTimeSeries() {
      ChartBindingModel model = new ChartBindingModel();
      model.setSourceField(nativeTimeSeriesDim("Ship Date", "day"));

      ChartBindingMutator.setSingleShelf(model, "source",
         new FieldRef("Ship Date", "dimension", null, "month", null));

      assertFalse(singleDim(model, "source").isTimeSeries());
   }

   @Test
   void singleShelfSameMeasureResendPreservesCalculateInfoButDifferentMeasureDoesNot() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setSingleShelf(model, "close",
         measureWithCalc("Price", "Sum", runningTotal("Sum")));

      ChartBindingMutator.setSingleShelf(model, "close",
         new FieldRef("Price", "measure", "Sum", null, null));
      assertNotNull(((ChartAggregateRefModel) model.getCloseField()).getCalculateInfo());

      ChartBindingMutator.setSingleShelf(model, "close",
         new FieldRef("Cost", "measure", "Sum", null, null));
      assertNull(((ChartAggregateRefModel) model.getCloseField()).getCalculateInfo());
   }

   // ── timeSeries refused where the native UI never offers it (bug #78214) ──────────────────
   //
   // The rule is a port of dimension-editor.component.ts timeSeriesSupported()/isTimeVisible()
   // and chart-fieldmc.component.ts isOuterDimRef(), checked on the post-write model.

   private static IllegalArgumentException refused(org.junit.jupiter.api.function.Executable write) {
      return assertThrows(IllegalArgumentException.class, write);
   }

   private static FieldRef date(String level, Boolean timeSeries) {
      return dimensionWithTimeSeries("Order Date", level, timeSeries);
   }

   private static ChartDimensionRefModel storedDate(String level, boolean timeSeries) {
      ChartDimensionRefModel ref = new ChartDimensionRefModel();
      ref.setColumnValue("Order Date");
      ref.setName("Order Date");
      ref.setDateLevel(DateLevels.normalize(level));
      ref.setTimeSeries(timeSeries);
      return ref;
   }

   private static ChartDimensionRefModel dimAt(List<?> refs, int index) {
      return (ChartDimensionRefModel) refs.get(index);
   }

   @Test
   void timeSeriesTrueOnANonDateDimensionIsRefused() {
      ChartBindingModel model = new ChartBindingModel();

      IllegalArgumentException e = refused(() -> ChartBindingMutator.setShelf(model, "x",
         List.of(dimensionWithTimeSeries("Category", null, true))));

      assertTrue(e.getMessage().contains("timeSeries") && e.getMessage().contains("Category") &&
                 e.getMessage().contains("date level"), e.getMessage());
      assertTrue(model.getXFields().isEmpty(), "a refused write must not stay applied");
   }

   @Test
   void timeSeriesTrueOnAColumnTheSourceReportsAsStringIsRefusedEvenWithALevel() {
      ChartBindingModel model = new ChartBindingModel();
      BindingModel.SourceTable table = new BindingModel.SourceTable();
      BindingModel.SourceTableColumn column = new BindingModel.SourceTableColumn();
      column.setName("Category");
      column.setDataType("string");
      table.setColumns(List.of(column));
      model.setTables(List.of(table));

      IllegalArgumentException e = refused(() -> ChartBindingMutator.setShelf(model, "x",
         List.of(dimensionWithTimeSeries("Category", "month", true))));

      assertTrue(e.getMessage().contains("not a date column"), e.getMessage());
   }

   @Test
   void timeSeriesTrueOnTheGroupShelfIsRefused() {
      ChartBindingModel model = new ChartBindingModel();

      IllegalArgumentException e = refused(() -> ChartBindingMutator.setShelf(model, "group",
         List.of(date("month", true))));

      assertTrue(e.getMessage().contains("group shelf"), e.getMessage());
      assertTrue(model.getGroupFields().isEmpty());
   }

   @Test
   void timeSeriesTrueOnAnOuterDateDimensionIsRefusedButTheInnerOneIsAccepted() {
      ChartBindingModel model = new ChartBindingModel();

      IllegalArgumentException e = refused(() -> ChartBindingMutator.setShelf(model, "x",
         List.of(date("year", true), date("month", true))));
      assertTrue(e.getMessage().contains("outer"), e.getMessage());

      ChartBindingMutator.setShelf(model, "x", List.of(date("year", null), date("month", true)));

      assertFalse(dimAt(model.getXFields(), 0).isTimeSeries());
      assertTrue(dimAt(model.getXFields(), 1).isTimeSeries());
   }

   @Test
   void aDateDimensionFollowedByAMeasureOnTheSameShelfIsOuter() {
      ChartBindingModel model = new ChartBindingModel();

      refused(() -> ChartBindingMutator.setShelf(model, "x",
         List.of(date("month", true), new FieldRef("Sales", "measure", "Sum", null, null))));
   }

   @Test
   void timeSeriesTrueOnAPieChartIsRefused() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_PIE);

      IllegalArgumentException e = refused(() -> ChartBindingMutator.setShelf(model, "x",
         List.of(date("month", true))));

      assertTrue(e.getMessage().contains("pie"), e.getMessage());
   }

   @Test
   void timeSeriesTrueOnAWaterfallChartIsRefused() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_WATERFALL);

      IllegalArgumentException e = refused(() -> ChartBindingMutator.setShelf(model, "x",
         List.of(date("month", true))));

      assertTrue(e.getMessage().contains("waterfall"), e.getMessage());
   }

   @Test
   void aChartTypeLeftAtAutoUsesItsRuntimeTypeForTheTimeVisibleRule() {
      ChartBindingModel model = new ChartBindingModel();
      model.setRTChartType(GraphTypes.CHART_DONUT);

      refused(() -> ChartBindingMutator.setShelf(model, "x", List.of(date("month", true))));
   }

   @Test
   void timeSeriesTrueOnAnInnerMonthOnALineChartIsAccepted() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_LINE);

      ChartBindingMutator.setShelf(model, "x", List.of(date("month", true)));
      assertTrue(dimAt(model.getXFields(), 0).isTimeSeries());

      ChartBindingMutator.setShelf(model, "x",
         List.of(dimensionWithTimeSeries("Region", null, null), date("month", true)));
      assertTrue(dimAt(model.getXFields(), 1).isTimeSeries());

      ChartBindingMutator.setShelf(model, "y", List.of(date("day", true)));
      assertTrue(dimAt(model.getYFields(), 0).isTimeSeries());
   }

   @Test
   void explicitFalseIsAlwaysAccepted() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_PIE);

      ChartBindingMutator.setShelf(model, "x", List.of(date("year", false), date("month", false)));
      ChartBindingMutator.setShelf(model, "group", List.of(date("month", false)));
      ChartBindingMutator.setSingleShelf(model, "path", date("day", false));

      assertFalse(dimAt(model.getXFields(), 1).isTimeSeries());
   }

   @Test
   void aPartOfDateLevelIsRefused() {
      ChartBindingModel model = new ChartBindingModel();

      IllegalArgumentException e = refused(() -> ChartBindingMutator.setShelf(model, "x",
         List.of(date("month_of_year", true))));

      assertTrue(e.getMessage().contains("part-of-date"), e.getMessage());
   }

   @Test
   void aDynamicDateLevelIsAcceptedForAnUntypedColumn() {
      ChartBindingModel model = new ChartBindingModel();

      ChartBindingMutator.setShelf(model, "x", List.of(date("$(level)", true)));

      assertTrue(dimAt(model.getXFields(), 0).isTimeSeries());
   }

   @Test
   void echoingAStoredTrueFlagIsAcceptedEvenWhereANewTrueWouldBeRefused() {
      // Native drag-and-drop stores true on both year and month (the year dim is outer), and
      // get_binding echoes both back with an explicit timeSeries:true.
      ChartBindingModel model = new ChartBindingModel();
      model.setXFields(new ArrayList<>(List.of(storedDate("year", true), storedDate("month", true))));

      ChartBindingMutator.setShelf(model, "x",
         List.of(dimensionWithTimeSeries("Region", null, null), date("year", true),
                 date("month", true)));

      assertEquals(3, model.getXFields().size());
      assertTrue(dimAt(model.getXFields(), 1).isTimeSeries());
      assertTrue(dimAt(model.getXFields(), 2).isTimeSeries());

      // ... but enabling it on a different level is a new enablement and is still refused.
      refused(() -> ChartBindingMutator.setShelf(model, "x",
         List.of(date("quarter", true), date("month", true))));
   }

   @Test
   void echoingAStoredTrueFlagOnAPieChartIsAccepted() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_PIE);
      model.setXFields(new ArrayList<>(List.of(storedDate("month", true))));

      ChartBindingMutator.setShelf(model, "x", List.of(date("month", true)));

      assertTrue(dimAt(model.getXFields(), 0).isTimeSeries());
   }

   @Test
   void mergedChartTypesAreRefusedButStockCandleAndBoxplotAreAllowed() {
      for(int type : new int[] {GraphTypes.CHART_SCATTER_CONTOUR, GraphTypes.CHART_MAP,
                                GraphTypes.CHART_RADAR, GraphTypes.CHART_TREEMAP,
                                GraphTypes.CHART_GANTT, GraphTypes.CHART_FUNNEL})
      {
         ChartBindingModel model = new ChartBindingModel();
         model.setChartType(type);

         refused(() -> ChartBindingMutator.setShelf(model, "x", List.of(date("month", true))));
      }

      for(int type : new int[] {GraphTypes.CHART_STOCK, GraphTypes.CHART_CANDLE,
                                GraphTypes.CHART_BOXPLOT})
      {
         ChartBindingModel model = new ChartBindingModel();
         model.setChartType(type);

         ChartBindingMutator.setShelf(model, "x", List.of(date("month", true)));
         assertTrue(dimAt(model.getXFields(), 0).isTimeSeries(), "type " + type);
      }
   }

   @Test
   void underMultiStylesAnyMeasureTypeThatIsPolarMakesItRefused() {
      ChartBindingModel model = new ChartBindingModel();
      model.setMultiStyles(true);
      ChartBindingMutator.setShelf(model, "y",
         List.of(new FieldRef("Sales", "measure", "Sum", null, null),
                 new FieldRef("Orders", "measure", "Sum", null, null)));
      ((ChartAggregateRefModel) model.getYFields().get(1)).setChartType(GraphTypes.CHART_PIE);

      refused(() -> ChartBindingMutator.setShelf(model, "x", List.of(date("month", true))));

      ((ChartAggregateRefModel) model.getYFields().get(1)).setChartType(GraphTypes.CHART_LINE);
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", true)));
      assertTrue(dimAt(model.getXFields(), 0).isTimeSeries());
   }

   @Test
   void aYDimensionIsNotRefusedForAnXShelfThatHasNoMeasureYetButIsForStockAndCandle() {
      // x=[Region] then y=[Date ts]: whether y is outer depends on the x write that may follow.
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(dimensionWithTimeSeries("Region", null, null)));

      ChartBindingMutator.setShelf(model, "y", List.of(date("month", true)));
      assertTrue(dimAt(model.getYFields(), 0).isTimeSeries());

      ChartBindingModel stock = new ChartBindingModel();
      stock.setChartType(GraphTypes.CHART_STOCK);
      refused(() -> ChartBindingMutator.setShelf(stock, "y", List.of(date("month", true))));
   }

   @Test
   void timeSeriesTrueOnASingleShelfDimensionIsRefusedButAStoredOneCanBeEchoed() {
      ChartBindingModel model = new ChartBindingModel();

      IllegalArgumentException e = refused(
         () -> ChartBindingMutator.setSingleShelf(model, "path", date("day", true)));
      assertTrue(e.getMessage().contains("single-field shelf"), e.getMessage());
      assertNull(ChartBindingMutator.readSingleShelf(model, "path"));

      model.setPathField(storedDate("day", true));
      ChartBindingMutator.setSingleShelf(model, "path", date("day", true));
      assertTrue(singleDim(model, "path").isTimeSeries());
   }

   @Test
   void aRefusedWriteLeavesThePreviousShelfInPlace() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", null)));

      refused(() -> ChartBindingMutator.setShelf(model, "x",
         List.of(date("year", true), date("month", null))));

      assertEquals(1, model.getXFields().size());
   }

   // sort: a time-series date dimension always renders ascending (ChartVSAQuery forces it).

   private static DimensionSortRanking.Sort sortOf(String direction) {
      return new DimensionSortRanking.Sort(direction, null, null);
   }

   @Test
   void aNonAscendingSortOnAnEffectiveTimeSeriesDimensionIsRefusedButAscIsAllowed() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", true)));

      for(String direction : List.of("desc", "descending", "manual")) {
         IllegalArgumentException e = refused(() -> ChartBindingMutator.setSort(
            model, "x", "Order Date", null,
            new DimensionSortRanking.Sort(direction, null, List.of("a"))));
         assertTrue(e.getMessage().contains("time-series"), e.getMessage());
      }

      refused(() -> ChartBindingMutator.setSort(model, "x", "Order Date", null,
         new DimensionSortRanking.Sort("value_desc", "Sum(Sales)", null)));

      ChartBindingMutator.setSort(model, "x", "Order Date", null, sortOf("asc"));
      ChartBindingMutator.setSort(model, "x", "Order Date", null, sortOf("none"));
      assertTrue(dimAt(model.getXFields(), 0).isTimeSeries());
   }

   @Test
   void aDescSortOnAnOuterDimensionWithAStoredFlagClearsTheFlagAndApplies() {
      // Native drag-and-drop leaves true on the outer year dim; the server would force it
      // ascending, so the sort only takes effect once the flag is cleared (as the editor does).
      ChartBindingModel model = new ChartBindingModel();
      model.setXFields(new ArrayList<>(List.of(storedDate("year", true), storedDate("month", true))));

      ChartBindingMutator.setSort(model, "x", "Order Date", 0, sortOf("desc"));

      ChartDimensionRefModel year = dimAt(model.getXFields(), 0);
      assertFalse(year.isTimeSeries());
      assertEquals(XConstants.SORT_DESC, year.getOrder());
      assertTrue(dimAt(model.getXFields(), 1).isTimeSeries(), "the inner dimension is untouched");
   }

   @Test
   void aDescSortOnAYDimensionOuterOnlyBecauseXHasNoMeasureClearsTheFlagAndApplies() {
      // The binding is final on the sort path, so the UI's "x has dimensions but no measure" y-outer
      // clause applies: the Composer does not treat this dimension as time series.
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
         List.of(new FieldRef("Region", "dimension", null, null, null)));
      model.setYFields(new ArrayList<>(List.of(storedDate("month", true))));

      ChartBindingMutator.setSort(model, "y", "Order Date", null, sortOf("desc"));

      ChartDimensionRefModel month = dimAt(model.getYFields(), 0);
      assertFalse(month.isTimeSeries());
      assertEquals(XConstants.SORT_DESC, month.getOrder());
   }

   @Test
   void aDescSortOnAStockYDimensionWithAStoredFlagClearsTheFlagAndApplies() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_STOCK);
      model.setYFields(new ArrayList<>(List.of(storedDate("month", true))));

      ChartBindingMutator.setSort(model, "y", "Order Date", null, sortOf("desc"));

      ChartDimensionRefModel month = dimAt(model.getYFields(), 0);
      assertFalse(month.isTimeSeries());
      assertEquals(XConstants.SORT_DESC, month.getOrder());
   }

   @Test
   void aDescSortOnAPartOfDateLevelIsNeverRefused() {
      ChartBindingModel model = new ChartBindingModel();
      model.setXFields(new ArrayList<>(List.of(storedDate("month_of_year", true))));

      ChartBindingMutator.setSort(model, "x", "Order Date", null, sortOf("desc"));

      assertEquals(XConstants.SORT_DESC, dimAt(model.getXFields(), 0).getOrder());
   }
}
