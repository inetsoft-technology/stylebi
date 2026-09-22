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
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.calc.RunningTotalCalcInfo;
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
}
