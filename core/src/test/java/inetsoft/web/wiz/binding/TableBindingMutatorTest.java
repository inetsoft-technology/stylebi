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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.SummaryAttr;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.AttachedAssembly;
import inetsoft.uql.asset.DefaultNamedGroupAssembly;
import inetsoft.uql.asset.NamedGroupInfo;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class TableBindingMutatorTest {
   private static FieldRef dim(String column) {
      return new FieldRef(column, "dimension", null, null, null);
   }

   private static FieldRef measure(String column, String aggregate) {
      return new FieldRef(column, "measure", aggregate, null, null);
   }

   // ── crosstab shelves ──────────────────────────────────────────────────────

   @Test
   void setsCrosstabRows() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      assertEquals(1, model.getRows().size());
      assertEquals("Region", model.getRows().get(0).getColumnValue());
   }

   @Test
   void setsCrosstabColsAndAggregates() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setShelf(model, "cols", List.of(dim("Year")));
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Sales", "Sum")));

      assertEquals(1, model.getCols().size());
      assertEquals(1, model.getAggregates().size());
      assertEquals("Sum", model.getAggregates().get(0).getFormula());
   }

   @Test
   void acceptsTheNaturalShelfAliases() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setShelf(model, "rowHeaders", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "columns", List.of(dim("Year")));

      assertEquals(1, model.getRows().size());
      assertEquals(1, model.getCols().size());
   }

   @Test
   void clearsAShelfOnAnEmptyList() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      TableBindingMutator.setShelf(model, "rows", List.of());

      assertTrue(model.getRows().isEmpty());
   }

   // ── table shelves ─────────────────────────────────────────────────────────

   /**
    * A plain Table has no grouping or aggregation in StyleBI — that is Crosstab's job.
    * {@code TableBindingModel.groups}/{@code aggregates} exist as wire-format fields, but
    * {@code VSTableBindingFactory.updateTableAssembly} never reads them back into the real
    * {@code TableVSAssemblyInfo}, so accepting a write here used to silently drop it: the call
    * reported {@code {"ok":true}} and nothing was applied. Refusing by name is the fix.
    */
   @Test
   void refusesTableGroupsAndAggregatesShelves() {
      TableBindingModel model = new TableBindingModel();

      Exception groups = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.setShelf(model, "groups", List.of(dim("Region"))));
      Exception aggregates = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.setShelf(model, "aggregates",
                                            List.of(measure("Sales", "Sum"))));

      assertTrue(groups.getMessage().contains("groups"));
      assertTrue(aggregates.getMessage().contains("aggregates"));
      assertTrue(groups.getMessage().contains("details"),
                 "the refusal should name the one shelf a table actually has");
   }

   /**
    * Every other shelf holds a dimension or aggregate ref; {@code details} holds a plain
    * {@code ColumnRefModel}. See the note in docs/superpowers/plans/2026-08-13-needs-your-input.md.
    */
   @Test
   void setsTableDetailsAsPlainColumns() {
      TableBindingModel model = new TableBindingModel();

      TableBindingMutator.setShelf(model, "details", List.of(dim("OrderID")));

      assertEquals(1, model.getDetails().size());
      ColumnRefModel detail = assertInstanceOf(ColumnRefModel.class, model.getDetails().get(0));
      assertEquals("OrderID", detail.getName());
      assertNotNull(detail.getDataRefModel(), "a detail column wraps an attribute ref");
   }

   @Test
   void refusesAnAggregateOnDetailsPointingAtTheAggregatesShelf() {
      TableBindingModel model = new TableBindingModel();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setShelf(model, "details",
                                            List.of(measure("Sales", "Sum"))));

      assertTrue(thrown.getMessage().contains("aggregates"));
   }

   // ── shelf validity is per object type ─────────────────────────────────────

   @Test
   void refusesACrosstabShelfOnATableNamingBoth() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setShelf(new TableBindingModel(), "rows", List.of()));

      assertTrue(thrown.getMessage().contains("rows"));
      assertTrue(thrown.getMessage().contains("details"),
                 "the refusal should list the shelves this object type does have");
   }

   @Test
   void refusesATableShelfOnACrosstab() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setShelf(new CrosstabBindingModel(), "details", List.of()));

      assertTrue(thrown.getMessage().contains("details"));
      assertTrue(thrown.getMessage().contains("rows"));
   }

   @Test
   void refusesAMeasureOnADimensionShelfPointingAtTheAggregatesShelf() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setShelf(new CrosstabBindingModel(), "rows",
                                            List.of(measure("Sales", "Sum"))));

      assertTrue(thrown.getMessage().contains("Sales"));
      assertTrue(thrown.getMessage().contains("aggregates"));
   }

   @Test
   void refusesADimensionOnTheAggregatesShelf() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setShelf(new CrosstabBindingModel(), "aggregates",
                                            List.of(dim("Region"))));

      assertTrue(thrown.getMessage().contains("Region"));
   }

   @Test
   void refusesAnUnknownShelf() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setShelf(new CrosstabBindingModel(), "z", List.of()));

      assertTrue(thrown.getMessage().contains("z"));
   }

   // ── add / remove / move ───────────────────────────────────────────────────

   @Test
   void appendsToAShelf() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      TableBindingMutator.addField(model, "rows", dim("Category"), null);

      assertEquals(2, model.getRows().size());
      assertEquals("Category", model.getRows().get(1).getColumnValue());
   }

   @Test
   void insertsAtAPosition() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region"), dim("Category")));

      TableBindingMutator.addField(model, "rows", dim("Year"), 0);

      assertEquals("Year", model.getRows().get(0).getColumnValue());
      assertEquals(3, model.getRows().size());
   }

   @Test
   void refusesAPositionPastTheEndRatherThanClamping() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.addField(model, "rows", dim("Year"), 9));

      assertTrue(thrown.getMessage().contains("9"));
   }

   @Test
   void removesAFieldByColumnName() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region"), dim("Category")));

      TableBindingMutator.removeField(model, "rows", "Region");

      assertEquals(1, model.getRows().size());
      assertEquals("Category", model.getRows().get(0).getColumnValue());
   }

   @Test
   void refusesToRemoveAFieldThatIsNotOnTheShelf() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.removeField(model, "rows", "Nope"));

      assertTrue(thrown.getMessage().contains("Nope"));
      assertTrue(thrown.getMessage().contains("Region"), "list what is actually on the shelf");
   }

   /** The rows↔cols pivot: the single most common crosstab edit, and one checkpoint. */
   @Test
   void movesAFieldBetweenShelves() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region"), dim("Year")));

      TableBindingMutator.moveField(model, "rows", "cols", "Year", null);

      assertEquals(1, model.getRows().size());
      assertEquals(1, model.getCols().size());
      assertEquals("Year", model.getCols().get(0).getColumnValue());
   }

   @Test
   void aMoveKeepsTheFieldWhenTheDestinationIsInvalid() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      assertThrows(IllegalArgumentException.class,
                   () -> TableBindingMutator.moveField(model, "rows", "details", "Region", null));
      assertEquals(1, model.getRows().size(),
                   "a rejected move must not have already removed the field");
   }

   /**
    * The UI's own drag-and-drop pivot (VSCrosstabDndService.dnd() ->
    * ConvertTableRefService.convertTableRef0()) silently converts a field's kind rather than
    * refusing the move -- moveField matches that instead of throwing.
    */
   @Test
   void movingAMeasureOntoADimensionShelfConvertsItInstead() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Sales", "Sum")));

      TableBindingMutator.moveField(model, "aggregates", "rows", "Sales", null);

      assertEquals(0, model.getAggregates().size(),
                   "the field must actually move, not stay behind");
      assertEquals(1, model.getRows().size());
      assertEquals("Sales", model.getRows().get(0).getColumnValue());
   }

   @Test
   void movingANumericDimensionOntoAggregatesDefaultsToSum() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(List.of(sourceTable("Orders", "QUANTITY", "integer")));
      TableBindingMutator.setShelf(model, "rows", List.of(dim("QUANTITY")));

      TableBindingMutator.moveField(model, "rows", "aggregates", "QUANTITY", null);

      assertEquals(0, model.getRows().size());
      assertEquals(1, model.getAggregates().size());
      assertEquals("Sum", model.getAggregates().get(0).getFormula(),
                   "a numeric column defaults to Sum, matching AssetUtil.getDefaultFormula()");
   }

   /**
    * Claude-review finding on PR #4976: a column from a joined/merged worksheet table can be
    * reported qualified ({@code "table.attribute"}) while the field naming it on the shelf is
    * bare, or vice versa -- {@code dataTypeOf}'s lookup must resolve either direction, the same
    * symmetric matching {@link TableBindingService#unqualified} exists for. Before the fix, this
    * silently defaulted to Count instead of Sum, since the exact-match-only lookup never found
    * the qualified column's data type.
    */
   @Test
   void movingANumericDimensionWithAQualifiedSourceNameOntoAggregatesDefaultsToSum() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(List.of(sourceTable("Orders", "Orders.QUANTITY", "integer")));
      TableBindingMutator.setShelf(model, "rows", List.of(dim("QUANTITY")));

      TableBindingMutator.moveField(model, "rows", "aggregates", "QUANTITY", null);

      assertEquals("Sum", model.getAggregates().get(0).getFormula(),
                   "a qualified source column name must still resolve the bare field's data type");
   }

   @Test
   void movingANonNumericDimensionOntoAggregatesDefaultsToCount() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(List.of(sourceTable("Orders", "REGION", "string")));
      TableBindingMutator.setShelf(model, "rows", List.of(dim("REGION")));

      TableBindingMutator.moveField(model, "rows", "aggregates", "REGION", null);

      assertEquals("Count", model.getAggregates().get(0).getFormula());
   }

   private static BindingModel.SourceTable sourceTable(String name, String column,
                                                        String dataType)
   {
      BindingModel.SourceTable table = new BindingModel.SourceTable();
      table.setName(name);
      table.setColumns(List.of(new BindingModel.SourceTableColumn(column, dataType)));
      return table;
   }

   // ── preservation ──────────────────────────────────────────────────────────

   @Test
   void aShelfWriteLeavesTheOtherShelvesAndOptionsUntouched() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "cols", List.of(dim("Year")));
      Object colsBefore = model.getCols();
      // "Year:cols0" -- the real composite key VSCrosstabBindingFactory reads/writes
      // (column + ":" + shelf + position), not the bare column name. A bare-keyed entry is
      // exactly what the bug this test's sibling below guards against would have accepted and
      // then silently dropped.
      model.getSuppressGroupTotal().put("Year:cols0", Boolean.TRUE);

      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      assertSame(colsBefore, model.getCols());
      assertEquals(Boolean.TRUE, model.getSuppressGroupTotal().get("Year:cols0"),
                   "suppressGroupTotal is keyed by column+shelf+position and must survive an " +
                   "unrelated write");
   }

   /**
    * {@code suppressGroupTotal} is keyed by {@code "<column>:<shelf><position>"} -- the same
    * composite form {@link inetsoft.web.binding.service.VSCrosstabBindingFactory} reads and
    * writes on the real assembly, needed because a column can be bound twice at different date
    * levels (a Year > Quarter drill) with independent suppression state per occurrence. A key
    * whose position no longer exists must be pruned, or it grows the map unboundedly across
    * edits and can resurrect suppression if the same column is ever rebound at that position.
    * See spec 2d risk 1. (Comparing against *bare* column names here, instead of this composite
    * form, previously treated every legitimately-keyed entry as an orphan and deleted all of
    * them on every write -- the exact defect that made {@code suppressGroupTotal} a complete
    * no-op through the wiz agent surface.)
    */
   @Test
   void prunesSuppressGroupTotalEntriesForPositionsNoLongerBound() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region"), dim("Year")));
      model.getSuppressGroupTotal().put("Region:rows0", Boolean.TRUE);
      model.getSuppressGroupTotal().put("Year:rows1", Boolean.TRUE);

      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      assertEquals(Boolean.TRUE, model.getSuppressGroupTotal().get("Region:rows0"),
                   "Region is still bound at rows0, so its suppression entry must survive");
      assertNull(model.getSuppressGroupTotal().get("Year:rows1"),
                 "Year is no longer bound at rows1 -- an orphaned suppression entry must be " +
                 "pruned, not left to resurrect later");
   }

   /**
    * The regression for the bug this whole pruning function existed to fix in the first place:
    * a caller who supplies the exact, correctly-formatted composite key must have it survive --
    * before this fix, {@code pruneOrphanedSuppression} compared every key (correct format or
    * not) against bare column names, so even a perfectly-formatted key was deleted as an
    * "orphan" and the option could never be durably set through this tool at all. Pruning runs
    * on every shelf write (not only ones that touch {@code suppressGroupTotal}), so re-setting
    * an unrelated shelf is enough to exercise it -- and is exactly the live-observed trigger
    * (any {@code set_table_fields}/{@code add_table_field}/{@code move_table_field} call).
    */
   @Test
   void aCorrectlyKeyedSuppressionEntrySurvivesPruning() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "cols", List.of(dim("Region")));
      model.getSuppressGroupTotal().put("Region:cols0", Boolean.FALSE);

      TableBindingMutator.setShelf(model, "rows", List.of(dim("Year")));

      assertEquals(Boolean.FALSE, model.getSuppressGroupTotal().get("Region:cols0"),
                   "a correctly composite-keyed entry must not be pruned as an orphan");
   }

   @Test
   void aBareNameKeyIsPrunedRatherThanSurviving() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "cols", List.of(dim("Region")));
      model.getSuppressGroupTotal().put("Region", Boolean.TRUE);

      TableBindingMutator.setShelf(model, "rows", List.of(dim("Year")));

      assertNull(model.getSuppressGroupTotal().get("Region"),
                 "a bare column name is not the real key shape and must not masquerade as a " +
                 "valid entry");
   }

   // ── sorting and ranking (2d Phase 2) ──────────────────────────────────────

   @Test
   void sortsADimensionOnTheRowsShelf() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      TableBindingMutator.setSort(model, "rows", "Region", null,
                                  new DimensionSortRanking.Sort("desc", null, null));

      assertEquals(inetsoft.uql.XConstants.SORT_DESC, model.getRows().get(0).getOrder());
   }

   /**
    * A crosstab binds the same date column twice on purpose — that is how a Year › Quarter drill
    * is built, and {@code VSCrosstabBindingHandler.setDateLevel} auto-advances the level on the
    * second drop. Sort and ranking live on each ref, so the product never addresses a dimension
    * by name; this layer does, and with duplicates it silently took the FIRST match.
    *
    * <p>So a caller sorting "the quarter one" quietly sorted the year one, and the model reported
    * a single sort entry for both. Found live on local-1200 running case 29 step 5.
    */
   @Test
   void refusesAnAmbiguousColumnRatherThanSortingTheFirstMatch() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
                                   List.of(dated("ORDER_DATE", "year"),
                                           dated("ORDER_DATE", "quarter")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setSort(model, "rows", "ORDER_DATE", null,
                                           new DimensionSortRanking.Sort("desc", null, null)));

      assertTrue(thrown.getMessage().contains("index"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("0"), "the message must name the positions");
      assertTrue(thrown.getMessage().contains("1"));
      // Having told callers to write "quarter", reporting "date level 4" back asks them to
      // translate a number this codebase deliberately hides.
      assertTrue(thrown.getMessage().contains("year"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("quarter"), thrown.getMessage());
   }

   @Test
   void sortsTheDimensionNamedByIndexWhenAColumnIsBoundTwice() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
                                   List.of(dated("ORDER_DATE", "year"),
                                           dated("ORDER_DATE", "quarter")));

      TableBindingMutator.setSort(model, "rows", "ORDER_DATE", 1,
                                  new DimensionSortRanking.Sort("desc", null, null));

      assertEquals(inetsoft.uql.XConstants.SORT_DESC, model.getRows().get(1).getOrder(),
                   "index 1 must be the second ref");
      assertNotEquals(inetsoft.uql.XConstants.SORT_DESC, model.getRows().get(0).getOrder(),
                      "the first ref must be untouched");
   }

   @Test
   void refusesAnIndexThatNamesNoRefOfThatColumn() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
                                   List.of(dated("ORDER_DATE", "year"),
                                           dated("ORDER_DATE", "quarter")));

      assertThrows(IllegalArgumentException.class,
                   () -> TableBindingMutator.setSort(model, "rows", "ORDER_DATE", 7,
                                                     new DimensionSortRanking.Sort("desc", null,
                                                                                   null)));
   }

   /** Read side of the same bug: one key for two refs meant one of them was invisible. */
   @Test
   void describesBothRefsWhenAColumnIsBoundTwice() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
                                   List.of(dated("ORDER_DATE", "year"),
                                           dated("ORDER_DATE", "quarter")));

      Map<String, Object> sorts = TableBindingMutator.describeSorts(model, "rows");

      assertEquals(2, sorts.size(), "both refs must be reported, got: " + sorts.keySet());
   }

   private static FieldRef dated(String column, String level) {
      return new FieldRef(column, "dimension", null, level, null);
   }

   @Test
   void ranksADimensionByAMeasureName() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(new FieldRef("Sales", "measure", "sum", null, null)));

      TableBindingMutator.setRanking(model, "rows", "Region", null,
                                     new DimensionSortRanking.Ranking("top", 5, "Sales", null));

      assertEquals("5", model.getRows().get(0).getRankingN());
      assertEquals("Sales", model.getRows().get(0).getRankingCol());
   }

   @Test
   void refusesRankingByAMeasureThatIsNotBound() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(new FieldRef("Sales", "measure", "sum", null, null)));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setRanking(model, "rows", "Region", null,
            new DimensionSortRanking.Ranking("top", 5, "TOTAL_REVENUE", null)));

      assertTrue(thrown.getMessage().contains("TOTAL_REVENUE"));
      assertTrue(thrown.getMessage().contains("Sales"), "name what is actually bound");
   }

   @Test
   void acceptsRankingByAMeasuresFullAggregateName() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(new FieldRef("Sales", "measure", "sum", null, null)));

      TableBindingMutator.setRanking(model, "rows", "Region", null,
         new DimensionSortRanking.Ranking("top", 5, "sum(Sales)", null));

      assertEquals("sum(Sales)", model.getRows().get(0).getRankingCol());
   }

   /**
    * A bound aggregate whose formula takes a second column or an N (Correlation, Covariance,
    * WeightedAvg, NthLargest, NthSmallest, PthPercentile) renders a real full name shaped like
    * {@code "formula(column, extra)"} that {@link TableBindingMutator}'s wire-format {@link
    * FieldRef} has no way to reconstruct exactly (no second-column/N value carried) -- the
    * validation must accept a prefix match for these formulas rather than refusing every such
    * reference as unbound, which would reject a value the real product resolves correctly.
    */
   @Test
   void acceptsATwoColumnAggregatesFullNameByPrefix() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(new FieldRef("Sales", "measure", "Correlation", null, null)));

      TableBindingMutator.setRanking(model, "rows", "Region", null,
         new DimensionSortRanking.Ranking("top", 5, "Correlation(Sales, Cost)", null));

      assertEquals("Correlation(Sales, Cost)", model.getRows().get(0).getRankingCol());
   }

   @Test
   void acceptsAnNArgAggregatesFullNameByPrefix() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(new FieldRef("Product", "measure", "NthMostFrequent", null, null)));

      TableBindingMutator.setRanking(model, "rows", "Region", null,
         new DimensionSortRanking.Ranking("top", 5, "NthMostFrequent(Product, 3)", null));

      assertEquals("NthMostFrequent(Product, 3)", model.getRows().get(0).getRankingCol());
   }

   @Test
   void stillRefusesAnUnboundMeasureWhenATwoColumnAggregateIsBound() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(new FieldRef("Sales", "measure", "Correlation", null, null)));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setRanking(model, "rows", "Region", null,
            new DimensionSortRanking.Ranking("top", 5, "TOTAL_REVENUE", null)));

      assertTrue(thrown.getMessage().contains("TOTAL_REVENUE"));
   }

   @Test
   void refusesSortingByFieldThatIsNotBound() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(new FieldRef("Sales", "measure", "sum", null, null)));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setSort(model, "rows", "Region", null,
            new DimensionSortRanking.Sort("value_desc", "TOTAL_REVENUE", null)));

      assertTrue(thrown.getMessage().contains("TOTAL_REVENUE"));
      assertTrue(thrown.getMessage().contains("Sales"), "name what is actually bound");
   }

   @Test
   void refusesToSortAColumnThatIsNotOnTheShelf() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setSort(model, "rows", "Nope", null,
                                           new DimensionSortRanking.Sort("asc", null, null)));

      assertTrue(thrown.getMessage().contains("Nope"));
      assertTrue(thrown.getMessage().contains("Region"));
   }

   @Test
   void refusesToSortTheAggregatesOrDetailsShelf() {
      CrosstabBindingModel crosstab = new CrosstabBindingModel();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setSort(crosstab, "aggregates", "Sales", null,
                                           new DimensionSortRanking.Sort("asc", null, null)));

      assertTrue(thrown.getMessage().contains("measures"));
   }

   @Test
   void describesTheSortsOnAShelf() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(new FieldRef("Sales", "measure", "sum", null, null)));
      TableBindingMutator.setRanking(model, "rows", "Region", null,
                                     new DimensionSortRanking.Ranking("top", 5, "Sales", null));

      Map<String, Object> described = TableBindingMutator.describeSorts(model, "rows");

      assertTrue(described.containsKey("Region"));
   }

   // ── column labels (2d Phase 2) ────────────────────────────────────────────

   /**
    * The label never reached the header. {@code name2Labels} is read and written by
    * {@code BaseTableBindingModel} and by nothing else in the product — a dead field — so the
    * write landed nowhere, {@code columnLabels} read back empty, and the tool still reported
    * "Relabelled 1 column(s)". Verified live on local-1200: the header stayed "Sum(PAID)".
    *
    * <p>The tests here asserted on {@code getName2Labels()}, so they passed while the feature did
    * nothing — they checked that we wrote to the dead field, which is exactly what was wrong.
    *
    * <p>Renaming a header really means a {@code TableDataPath} cell override, which needs the
    * rendered table lens to locate the header cell and differs between crosstab and table. That
    * is a design job, not a patch. Until it exists the tool refuses, so an agent surfaces a
    * missing capability instead of believing the header changed.
    */
   @Test
   void refusesBecauseTheLabelWouldNeverReachTheHeader() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      Exception thrown = assertThrows(
         UnsupportedOperationException.class,
         () -> TableBindingMutator.setColumnLabels(model, Map.of("Region", "Sales Region")));

      assertTrue(thrown.getMessage().toLowerCase().contains("not supported"),
                 "the message must say the capability is missing, got: " + thrown.getMessage());
   }

   /** The unbound-column check still runs first: a wrong name is a different mistake. */
   @Test
   void stillNamesAnUnboundColumnBeforeReportingTheGap() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setColumnLabels(model, Map.of("Profit", "P")));

      assertTrue(thrown.getMessage().contains("never be shown"));
   }


   @Test
   void refusesAnEmptyLabelMap() {
      assertThrows(IllegalArgumentException.class,
                   () -> TableBindingMutator.setColumnLabels(new CrosstabBindingModel(),
                                                             Map.of()));
   }

   // ── options (2d Phase 3) ──────────────────────────────────────────────────

   /**
    * percentageBy is stored as an XConstants number, and PERCENTAGE_BY_COL is 1 — which is also
    * the default. So a fresh crosstab read back {@code percentageBy: "1"}, a value in no
    * vocabulary this tool accepts, looking like a setting somebody had chosen. Found live on
    * local-1201: setting it to "col" appeared to do nothing because it was already col.
    */
   @Test
   void reportsThePercentageDirectionByNameNotItsConstant() {
      assertEquals("none", TableBindingMutator.percentageByName("0"));
      assertEquals("col", TableBindingMutator.percentageByName("1"));
      assertEquals("row", TableBindingMutator.percentageByName("2"));
      assertNull(TableBindingMutator.percentageByName(null));
   }


   /**
    * The crosstab totals are dynamic-value strings that StyleBI reads as booleans, so anything
    * but "true" reads as false. A real boolean has to normalize to the string form.
    */
   @Test
   void normalizesARealBooleanToTheStringForm() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setOptions(model, Map.of("rowTotals", true, "colTotals", false));

      assertEquals("true", model.getOption().getRowTotalVisibleValue());
      assertEquals("false", model.getOption().getColTotalVisibleValue());
   }

   @Test
   void acceptsTheStringSpellingsToo() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setOptions(model, Map.of("rowTotals", "TRUE"));

      assertEquals("true", model.getOption().getRowTotalVisibleValue());
   }

   /** "yes" reads as false downstream, so it would silently turn the total off. */
   @Test
   void refusesAnAmbiguousBooleanSpellingRatherThanTurningTheSettingOff() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(new CrosstabBindingModel(),
                                              Map.of("rowTotals", "yes")));

      assertTrue(thrown.getMessage().contains("silently turn the setting off"));
   }

   /**
    * Finding 10 (parity audit, lane L5): rowTotalVisibleValue/colTotalVisibleValue are genuine
    * DynamicValue fields on the real assembly (resolved via getRuntimeValue() at render time,
    * matching the UI's own VALUE|VARIABLE|EXPRESSION dynamic-combo-box) -- a "$(var)" reference
    * must pass through unresolved rather than being refused as an unrecognized boolean spelling.
    */
   @Test
   void acceptsAVariableReferenceForRowAndColTotals() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setOptions(model, Map.of("rowTotals", "$(ShowRowTotals)",
                                                   "colTotals", "$(ShowColTotals)"));

      assertEquals("$(ShowRowTotals)", model.getOption().getRowTotalVisibleValue());
      assertEquals("$(ShowColTotals)", model.getOption().getColTotalVisibleValue());
   }

   @Test
   void acceptsAnExpressionForRowTotals() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setOptions(model, Map.of("rowTotals", "=Now() > Date(2026,1,1)"));

      assertEquals("=Now() > Date(2026,1,1)", model.getOption().getRowTotalVisibleValue());
   }

   /**
    * summarySideBySide is a plain boolean on the real assembly (VSCrosstabInfo.
    * setSummarySideBySide(boolean)), not a DynamicValue -- unlike rowTotals/colTotals, a
    * variable reference here must still be refused: Boolean.parseBoolean() would otherwise read
    * it as false, silently.
    */
   @Test
   void stillRefusesAVariableReferenceForSummarySideBySide() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(new CrosstabBindingModel(),
                                              Map.of("summarySideBySide", "$(Foo)")));

      assertTrue(thrown.getMessage().contains("silently turn the setting off"));
   }

   /**
    * The shelf-populated guard (col needs a bound cols shelf; row needs a bound rows shelf)
    * cannot be evaluated for a reference that only resolves to none/row/col at render time, so
    * it must not fire for one -- confirming the dynamic-reference check runs before, not after,
    * that validation.
    */
   @Test
   void aVariableReferenceForPercentageByBypassesTheShelfPopulatedGuard() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setOptions(model, Map.of("percentageBy", "$(PercentMode)"));

      assertEquals("$(PercentMode)", model.getOption().getPercentageByValue(),
                   "must not throw the 'needs a bound shelf' refusal for an unresolved reference");
   }

   @Test
   void setsPercentageByOnlyWhenTheShelfItNeedsIsPopulated() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "cols", List.of(dim("Year")));

      TableBindingMutator.setOptions(model, Map.of("percentageBy", "col"));

      assertEquals(String.valueOf(inetsoft.uql.XConstants.PERCENTAGE_BY_COL),
                   model.getOption().getPercentageByValue());
   }

   /** By-column with no columns bound renders zeros rather than failing. */
   @Test
   void refusesPercentageByColWithNoColumnsBound() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(new CrosstabBindingModel(),
                                              Map.of("percentageBy", "col")));

      assertTrue(thrown.getMessage().contains("zeros"));
   }

   @Test
   void refusesAnUnknownPercentageByToken() {
      assertThrows(IllegalArgumentException.class,
                   () -> TableBindingMutator.setOptions(new CrosstabBindingModel(),
                                                        Map.of("percentageBy", "diagonal")));
   }

   /**
    * A plain Table has no options a write here can actually apply — {@code grandTotal}/
    * {@code distinct} on {@code TableOptionInfo} are wire-format leftovers nothing in
    * {@code VSTableBindingFactory.updateTableAssembly} ever reads, so accepting them used to
    * report {@code {"ok":true}} and apply nothing. Refusing by name is the fix.
    */
   @Test
   void refusesAnyTableOption() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(new TableBindingModel(),
                                              Map.of("grandTotal", true, "distinct", false)));

      assertTrue(thrown.getMessage().contains("grandTotal"));
      assertTrue(thrown.getMessage().contains("distinct"));
   }

   /** An option belonging to the other object type would be accepted and do nothing. */
   @Test
   void refusesACrosstabOptionOnATable() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(new TableBindingModel(),
                                              Map.of("rowTotals", true)));

      assertTrue(thrown.getMessage().contains("rowTotals"));
   }

   @Test
   void refusesAnEmptyOptionMap() {
      assertThrows(IllegalArgumentException.class,
                   () -> TableBindingMutator.setOptions(new CrosstabBindingModel(), Map.of()));
   }

   @Test
   void optionVocabularySeparatesTheTwoObjectTypes() {
      Map<String, Object> vocabulary = TableBindingMutator.optionVocabulary();

      assertTrue(String.valueOf(vocabulary.get("crosstab")).contains("rowTotals"));
      assertEquals(List.of(), vocabulary.get("table"),
                   "a table has no options this write path can actually apply");
   }

   // ── namedGroup resolution (rvs-aware setShelf) ────────────────────────────

   private static final SourceInfo QUERY1_SOURCE =
      new SourceInfo(SourceInfo.ASSET, null, "Query1");

   private static FieldRef dimWithNamedGroup(String column, String namedGroup) {
      return new FieldRef(column, "dimension", null, null, namedGroup);
   }

   private static RuntimeViewsheet rvsWithWorksheet(Worksheet ws) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseWorksheet()).thenReturn(ws);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static DataRefModelFactoryService refModelService() {
      DataRefModelFactoryService service = mock(DataRefModelFactoryService.class);
      when(service.createDataRefModel(any())).thenReturn(mock(DataRefModel.class));
      return service;
   }

   @Test
   void setShelfResolvesAWorksheetLocalNamedGroupAndForcesSortSpecific() throws Exception {
      Condition condition = mock(Condition.class);
      when(condition.getOperation()).thenReturn(Condition.EQUAL_TO);
      when(condition.getValues()).thenReturn(List.of("CA"));
      ConditionList conditionList = new ConditionList();
      conditionList.append(new ConditionItem(new AttributeRef(null, "REGION"), condition, 0));
      NamedGroupInfo namedGroupInfo = new NamedGroupInfo();
      namedGroupInfo.setGroupCondition("West", conditionList);

      DefaultNamedGroupAssembly ngAssembly = mock(DefaultNamedGroupAssembly.class);
      when(ngAssembly.getName()).thenReturn("Coastal");
      when(ngAssembly.getAttachedType()).thenReturn(AttachedAssembly.COLUMN_ATTACHED);
      when(ngAssembly.getAttachedSource()).thenReturn(QUERY1_SOURCE);
      when(ngAssembly.getAttachedAttribute()).thenReturn(new AttributeRef(null, "REGION"));
      when(ngAssembly.getNamedGroupInfo()).thenReturn(namedGroupInfo);

      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[]{ ngAssembly });

      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dimWithNamedGroup("REGION", "Coastal")),
                                   rvsWithWorksheet(ws), QUERY1_SOURCE, refModelService());

      BDimensionRefModel dim = model.getRows().get(0);
      assertEquals(XConstants.SORT_SPECIFIC, dim.getOrder());
      assertEquals(XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO, dim.getNamedGroupInfo().getType());
      assertEquals(1, dim.getNamedGroupInfo().getConditions().size());
      assertEquals("West", dim.getNamedGroupInfo().getConditions().get(0).getName());
   }

   @Test
   void setShelfRefusesAnUnrecognizedNamedGroupNamingTheFieldAndColumn() {
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[0]);
      CrosstabBindingModel model = new CrosstabBindingModel();

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(null);

         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> TableBindingMutator.setShelf(
               model, "rows", List.of(dimWithNamedGroup("REGION", "NoSuchGroup")),
               rvsWithWorksheet(ws), QUERY1_SOURCE, refModelService()));

         assertTrue(thrown.getMessage().contains("NoSuchGroup"));
         assertTrue(thrown.getMessage().contains("REGION"));
      }
   }

   /**
    * The 3-arg {@code setShelf} used by {@link TableBindingMutator#addField}/{@link
    * TableBindingMutator#moveField} to reapply a whole shelf has no runtime context, so a field
    * carrying {@code namedGroup} through that path is left unresolved rather than throwing --
    * the same, pre-existing gap as before this class learned to resolve named groups at all.
    *
    * <p>This is about calling the plain, no-context {@code setShelf} directly (still a real,
    * documented gap for a caller that has no context to give it) -- not the defect the
    * addField/removeField/moveField tests below cover, which is that those three ALREADY HAVE
    * {@code rvs}/{@code source}/{@code refModelService} in scope (via
    * {@code TableBindingService}) but were routing through this no-context overload anyway.
    */
   @Test
   void theThreeArgSetShelfLeavesANamedGroupUnresolved() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setShelf(model, "rows",
                                   List.of(dimWithNamedGroup("REGION", "Coastal")));

      assertNull(model.getRows().get(0).getNamedGroupInfo());
   }

   // ── addField/removeField/moveField must not drop an unrelated field's resolved
   // ── named group when they reapply the whole shelf (L6 reopened) ──────────────
   //
   // Uses the repository-registered ("predefined") named group shape, not the worksheet-local
   // Expert one setShelfResolvesAWorksheetLocalNamedGroupAndForcesSortSpecific above uses:
   // FieldRefFactory.resolveNamedGroupInfo's EXPERT_NAMEDGROUP_INFO branch never calls
   // NamedGroupInfoModel.setName(...), so a re-read of the model after the FIRST setShelf
   // reconstructs a FieldRef with namedGroup()==null regardless of rvs -- a separate,
   // pre-existing round-trip gap, not the addField/removeField/moveField defect these tests
   // target. The ASSET_NAMEDGROUP_INFO_REF branch does set the name, so it round-trips and
   // isolates the one thing under test here: whether rvs/source/refModelService actually reach
   // setShelf on a second call.

   private record NamedGroupFixture(CrosstabBindingModel model, RuntimeViewsheet rvs,
                                    DataRefModelFactoryService refModelService) {}

   /**
    * A crosstab with "REGION" already bound to a repository-registered "Tiers" named group on
    * rows, plus an unrelated second field, "Category", also on rows. Must run inside the
    * caller's {@code MockedStatic<AssetUtil>}/{@code MockedStatic<SummaryAttr>} scope.
    */
   private static NamedGroupFixture crosstabWithRegionBoundToARegisteredNamedGroupAndCategory(
      MockedStatic<AssetUtil> assetUtil, MockedStatic<SummaryAttr> summaryAttr) throws Exception
   {
      AssetRepository rep = mock(AssetRepository.class);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(rep);

      AssetNamedGroupInfo tiers = mock(AssetNamedGroupInfo.class);
      when(tiers.getName()).thenReturn("Tiers");
      summaryAttr.when(() -> SummaryAttr.getAssetNamedGroupInfos(any(), eq(rep), isNull()))
         .thenReturn(new AssetNamedGroupInfo[]{ tiers });

      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[0]);
      RuntimeViewsheet rvs = rvsWithWorksheet(ws);
      DataRefModelFactoryService refModelService = refModelService();

      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(dimWithNamedGroup("REGION", "Tiers"), dim("Category")),
         rvs, QUERY1_SOURCE, refModelService);

      return new NamedGroupFixture(model, rvs, refModelService);
   }

   private static BDimensionRefModel regionOn(CrosstabBindingModel model) {
      return model.getRows().stream()
         .filter(r -> "REGION".equals(r.getColumnValue()))
         .findFirst()
         .orElseThrow();
   }

   @Test
   void addFieldPreservesAnAlreadyResolvedNamedGroupOnAnotherFieldOnTheSameShelf()
      throws Exception
   {
      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class);
          MockedStatic<SummaryAttr> summaryAttr = mockStatic(SummaryAttr.class))
      {
         NamedGroupFixture fx =
            crosstabWithRegionBoundToARegisteredNamedGroupAndCategory(assetUtil, summaryAttr);

         TableBindingMutator.addField(fx.model(), "rows", dim("Year"), null,
                                      fx.rvs(), QUERY1_SOURCE, fx.refModelService());

         BDimensionRefModel region = regionOn(fx.model());
         assertEquals(XConstants.SORT_SPECIFIC, region.getOrder());
         assertNotNull(region.getNamedGroupInfo(),
            "an unrelated addField on the same shelf must not drop REGION's resolved named " +
            "group");
         assertEquals("Tiers", region.getNamedGroupInfo().getName());
      }
   }

   @Test
   void removeFieldPreservesAnAlreadyResolvedNamedGroupOnAnotherFieldOnTheSameShelf()
      throws Exception
   {
      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class);
          MockedStatic<SummaryAttr> summaryAttr = mockStatic(SummaryAttr.class))
      {
         NamedGroupFixture fx =
            crosstabWithRegionBoundToARegisteredNamedGroupAndCategory(assetUtil, summaryAttr);

         TableBindingMutator.removeField(fx.model(), "rows", "Category",
                                         fx.rvs(), QUERY1_SOURCE, fx.refModelService());

         assertEquals(1, fx.model().getRows().size());
         BDimensionRefModel region = regionOn(fx.model());
         assertEquals(XConstants.SORT_SPECIFIC, region.getOrder());
         assertNotNull(region.getNamedGroupInfo(),
            "an unrelated removeField on the same shelf must not drop REGION's resolved " +
            "named group");
         assertEquals("Tiers", region.getNamedGroupInfo().getName());
      }
   }

   @Test
   void moveFieldPreservesAnAlreadyResolvedNamedGroupOnAnotherFieldOnTheSameShelf()
      throws Exception
   {
      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class);
          MockedStatic<SummaryAttr> summaryAttr = mockStatic(SummaryAttr.class))
      {
         NamedGroupFixture fx =
            crosstabWithRegionBoundToARegisteredNamedGroupAndCategory(assetUtil, summaryAttr);

         TableBindingMutator.moveField(fx.model(), "rows", "cols", "Category", null,
                                       fx.rvs(), QUERY1_SOURCE, fx.refModelService());

         assertEquals(1, fx.model().getRows().size(), "only REGION should remain on rows");
         BDimensionRefModel region = regionOn(fx.model());
         assertEquals(XConstants.SORT_SPECIFIC, region.getOrder());
         assertNotNull(region.getNamedGroupInfo(),
            "moving an unrelated field off the same shelf must not drop REGION's resolved " +
            "named group");
         assertEquals("Tiers", region.getNamedGroupInfo().getName());
      }
   }
}
