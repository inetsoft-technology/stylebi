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
import com.fasterxml.jackson.databind.ObjectMapper;
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
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.calc.PercentCalcInfo;
import inetsoft.web.binding.model.graph.calc.RunningTotalCalcInfo;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.wiz.binding.model.ColumnLabelEntry;
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

   /** A measure carrying the N/P operand of an Nth/Pth formula (bug #78856). */
   private static FieldRef nth(String column, String aggregate, String operand) {
      return new FieldRef(column, "measure", aggregate, null, null, null, null, null, null, null,
                          null, null, null, null, operand);
   }

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

   private static FieldRef measureWithSecondaryColumn(String column, String aggregate,
                                                      String secondaryColumn)
   {
      return new FieldRef(column, "measure", aggregate, null, null, null, null, null, null,
                          null, null, null, null, secondaryColumn);
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

   /**
    * Bug #76931 (VTB-034): {@code FieldRef.secondaryColumn} -- the second column a
    * Covariance/Correlation/WeightedAverage aggregate compares its own {@code column} against --
    * must reach {@code BAggregateRefModel.getSecondaryColumnValue()} on write.
    */
   @Test
   void setsAggregatesSecondaryColumn() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithSecondaryColumn("DISCOUNT", "Correlation", "PAID")));

      assertEquals("PAID", model.getAggregates().get(0).getSecondaryColumnValue());
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

   /**
    * Redmine #78157 (VTB-036), ROWS case. Suppressing State mid-shelf, then removing a
    * different, earlier field (Reseller) that never names State, used to delete State's
    * suppression entry outright: its old key ({@code State:rows1}) no longer matched State's
    * new position ({@code rows0}) once Reseller's removal shifted everything after it down one
    * slot, and {@link TableBindingMutator#removeField} never asked to change State's
    * suppression at all. {@code pruneOrphanedSuppression} could only delete the stale key, never
    * rename it -- {@code rekeySuppression} now runs first and carries the value forward.
    */
   @Test
   void removingAnEarlierFieldCarriesALaterSuppressedFieldsEntryToItsNewKey() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(dim("Reseller"), dim("State"), dim("Region")));
      model.getSuppressGroupTotal().put("State:rows1", Boolean.TRUE);

      TableBindingMutator.removeField(model, "rows", "Reseller");

      assertEquals(Map.of("State:rows0", Boolean.TRUE), model.getSuppressGroupTotal(),
         "State is still bound, just one slot earlier -- its suppression must follow it to " +
         "rows0, not be deleted as if it were orphaned, and Region (never suppressed) must not " +
         "gain an entry either");
   }

   /**
    * Redmine #78157 (VTB-036), COLS case. A plain reorder -- nothing added or removed, just two
    * already-bound dimensions trading places -- shifts both of their absolute indexes the same
    * way a remove/insert does, so it hits the identical stale-key-gets-deleted defect. State's
    * suppression must follow it from {@code cols1} to {@code cols0}.
    */
   @Test
   void reorderingTwoColsCarriesTheSuppressedOnesEntryToItsNewKey() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "cols", List.of(dim("Region"), dim("State")));
      model.getSuppressGroupTotal().put("State:cols1", Boolean.TRUE);

      TableBindingMutator.setShelf(model, "cols", List.of(dim("State"), dim("Region")));

      assertEquals(Map.of("State:cols0", Boolean.TRUE), model.getSuppressGroupTotal(),
         "a plain reorder must carry State's suppression to its new position, cols0, rather " +
         "than losing it the way pruneOrphanedSuppression's pure delete-only logic used to");
   }

   /**
    * A true swap between two occurrences of the SAME duplicate-bound column (e.g. a Year/Quarter
    * drill trading places) is the one shape where a naive remove-then-reinsert, one pair at a
    * time, can read back a value this same rekey pass already overwrote -- because one rekey's
    * new key is literally another rekey's old key. Both occurrences' distinct values must
    * survive the swap, not collapse into one or the other.
    */
   @Test
   void swappingTwoOccurrencesOfADuplicateBoundColumnKeepsBothSuppressionValuesSeparate() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(dated("ORDER_DATE", "year"), dated("ORDER_DATE", "quarter")));
      model.getSuppressGroupTotal().put("ORDER_DATE:rows0", Boolean.TRUE);
      model.getSuppressGroupTotal().put("ORDER_DATE:rows1", Boolean.FALSE);

      // Swap the two occurrences: quarter now first, year now second.
      TableBindingMutator.setShelf(model, "rows",
         List.of(dated("ORDER_DATE", "quarter"), dated("ORDER_DATE", "year")));

      assertEquals(Map.of("ORDER_DATE:rows0", Boolean.FALSE, "ORDER_DATE:rows1", Boolean.TRUE),
         model.getSuppressGroupTotal(),
         "year's TRUE must follow it to rows1 and quarter's FALSE must follow it to rows0 -- " +
         "neither value may be lost or duplicated across the swap");
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

   /**
    * Bug #77036: the plugin defaulted the key's number to 0 for any dimension not bound twice,
    * so suppressing one that is not first on the shelf was pruned right after and reported ok.
    * With no position, the key now resolves to where the column is actually bound. The column
    * name contains a colon, as a real entity-qualified one does.
    */
   @Test
   void aSuppressionKeyWithNoPositionResolvesToTheBoundPosition() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Product:Category"),
                                                         dim("Customer:Region"),
                                                         dim("Product:Name")));

      TableBindingMutator.setOptions(model,
         Map.of("suppressGroupTotal", Map.of("customer:region:rows", true)));

      assertEquals(Map.of("Customer:Region:rows1", Boolean.TRUE), model.getSuppressGroupTotal(),
                   "stored under the bound spelling and position pruneOrphanedSuppression keeps");
   }

   @Test
   void aSuppressionKeyAtAPositionTheColumnIsNotBoundAtIsRefused() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Category"), dim("Region")));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(model,
            Map.of("suppressGroupTotal", Map.of("Region:rows0", true))));

      assertTrue(thrown.getMessage().contains("rows[1]"), thrown.getMessage());
      assertTrue(model.getSuppressGroupTotal().isEmpty());
   }

   @Test
   void aSuppressionKeyForAnUnboundColumnIsRefused() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(model,
            Map.of("suppressGroupTotal", Map.of("Regoin:rows", true))));

      assertTrue(thrown.getMessage().contains("not bound"), thrown.getMessage());
   }

   @Test
   void aSuppressionKeyForATwiceBoundColumnNeedsItsPosition() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Order Date"), dim("Order Date")));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(model,
            Map.of("suppressGroupTotal", Map.of("Order Date:rows", true))));

      assertTrue(thrown.getMessage().contains("[0, 1]"), thrown.getMessage());

      TableBindingMutator.setOptions(model,
         Map.of("suppressGroupTotal", Map.of("Order Date:rows1", true)));

      assertEquals(Map.of("Order Date:rows1", Boolean.TRUE), model.getSuppressGroupTotal());
   }

   @Test
   void aMalformedSuppressionKeyIsRefused() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(model,
            Map.of("suppressGroupTotal", Map.of("Region", true))));
      assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(model,
            Map.of("suppressGroupTotal", Map.of("Region:aggregates0", true))));
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
         List.of(nth("Product", "NthMostFrequent", "3")));

      TableBindingMutator.setRanking(model, "rows", "Region", null,
         new DimensionSortRanking.Ranking("top", 5, "NthMostFrequent(Product, 3)", null));

      assertEquals("NthMostFrequent(Product, 3)", model.getRows().get(0).getRankingCol());
   }

   /**
    * Claude-review finding on PR #4976: {@code First}/{@code Last} both override {@code
    * isTwoColumns()} to {@code true} (confirmed directly in {@code AggregateFormula.java}), are
    * genuinely user-selectable (the frontend's own formula picker marks both {@code twoColumns:
    * true}), and render a {@code "formula(column, col2)"} full name via {@code
    * VSAggregateRef.getFullName()} -- exactly like {@code Correlation}/{@code Covariance}, which
    * {@link #MULTI_ARG_FORMULA_NAMES} already covers. Before this, {@code requireKnownMeasure}
    * wrongly refused a sort/ranking reference to an existing {@code First(Sales, OrderDate)}-
    * style bound aggregate by its real full name.
    */
   @Test
   void acceptsAFirstAggregatesFullNameByPrefix() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(new FieldRef("Sales", "measure", "First", null, null)));

      TableBindingMutator.setRanking(model, "rows", "Region", null,
         new DimensionSortRanking.Ranking("top", 5, "First(Sales, OrderDate)", null));

      assertEquals("First(Sales, OrderDate)", model.getRows().get(0).getRankingCol());
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

   /**
    * Bug #76766: a {@code "$(ComponentName)"} sortByField/measure names no bound aggregate by
    * construction -- it resolves against a live Form component's value at render time, not
    * against this assembly's aggregates shelf now -- so {@code requireKnownMeasure} must not
    * refuse it as "not a bound aggregate" the way a genuinely unbound literal name is refused.
    */
   @Test
   void acceptsADynamicSortByFieldWithoutMatchingAnyBoundAggregate() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(new FieldRef("Sales", "measure", "sum", null, null)));

      TableBindingMutator.setSort(model, "rows", "Region", null,
         new DimensionSortRanking.Sort("value_desc", "$(Spinner1)", null));

      assertEquals("$(Spinner1)", model.getRows().get(0).getSortByCol());
   }

   @Test
   void acceptsADynamicRankingMeasureWithoutMatchingAnyBoundAggregate() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(new FieldRef("Sales", "measure", "sum", null, null)));

      TableBindingMutator.setRanking(model, "rows", "Region", null,
         new DimensionSortRanking.Ranking("top", 5, "$(Spinner1)", null));

      assertEquals("$(Spinner1)", model.getRows().get(0).getRankingCol());
   }

   /**
    * A genuinely unbound LITERAL name must still be refused exactly as before -- the new dynamic-
    * value early return must not widen into accepting an arbitrary unresolved string.
    */
   @Test
   void stillRefusesAnUnboundLiteralSortByFieldNotShapedLikeADynamicReference() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(new FieldRef("Sales", "measure", "sum", null, null)));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setSort(model, "rows", "Region", null,
            new DimensionSortRanking.Sort("value_desc", "$Spinner1", null)));

      assertTrue(thrown.getMessage().contains("$Spinner1"));
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

   // ── sort/ranking survives a shelf rewrite (VTB-004, Redmine #76574) ────────
   //
   // setShelf/dimensions() used to build a brand-new BDimensionRefModel for every field on
   // every write, discarding order/rankingOpt/rankingN/rankingCol/manualOrder/groupOthers/
   // namedOthers even for a field that did not change. The fix matches a new field to the
   // shelf's own previous list by same absolute index + same column (case-insensitive) + same
   // date level -- the same position-keyed identity requireDimension/pruneOrphanedSuppression
   // already use to disambiguate a column bound twice, not a dateLevel-content key. See
   // 03-fix.md for why that (strategy (a)) was chosen over matching by (column, dateLevel)
   // alone (strategy (b)).

   @Test
   void resubmittingTheIdenticalFieldListPreservesSortAndRanking() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(new FieldRef("Sales", "measure", "sum", null, null)));
      TableBindingMutator.setSort(model, "rows", "Region", null,
                                  new DimensionSortRanking.Sort("desc", null, null));
      TableBindingMutator.setRanking(model, "rows", "Region", null,
                                     new DimensionSortRanking.Ranking("top", 5, "Sales", null));

      // The filed repro: resubmit the identical field list to the same shelf.
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      assertEquals(XConstants.SORT_DESC, model.getRows().get(0).getOrder(),
         "an unchanged field's sort must survive a shelf resubmission");
      assertEquals("5", model.getRows().get(0).getRankingN());
      assertEquals("Sales", model.getRows().get(0).getRankingCol());
   }

   /**
    * VTB-006: clearing a ranking must also clear the "group others" flag it carried, and that
    * cleared flag must stick across a subsequent shelf resubmission (copyOf()'s job is only to
    * preserve whatever the model currently holds, per VTB-004).
    */
   @Test
   void clearingRankingClearsGroupOthersAcrossAShelfResubmission() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(new FieldRef("Sales", "measure", "sum", null, null)));
      TableBindingMutator.setRanking(model, "rows", "Region", null,
                                     new DimensionSortRanking.Ranking("top", 5, "Sales", true));

      TableBindingMutator.setRanking(model, "rows", "Region", null,
                                     new DimensionSortRanking.Ranking("none", null, null, null));

      // The filed repro: resubmit the identical field list to the same shelf after the clear.
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      assertFalse(model.getRows().get(0).isGroupOthers(),
         "a cleared ranking's 'group others' flag must not be copied forward by a shelf resubmission");
   }

   @Test
   void addFieldPreservesSortOnAFieldThatStaysAtTheSamePosition() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setSort(model, "rows", "Region", null,
                                  new DimensionSortRanking.Sort("desc", null, null));

      TableBindingMutator.addField(model, "rows", dim("Year"), null); // appended after Region

      assertEquals(XConstants.SORT_DESC, model.getRows().get(0).getOrder(),
         "REGION stays at index 0, so appending YEAR after it must not reset its sort");
   }

   @Test
   void removeFieldPreservesSortOnAFieldThatStaysAtTheSamePosition() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region"), dim("Category")));
      TableBindingMutator.setSort(model, "rows", "Region", null,
                                  new DimensionSortRanking.Sort("desc", null, null));

      TableBindingMutator.removeField(model, "rows", "Category");

      assertEquals(XConstants.SORT_DESC, model.getRows().get(0).getOrder(),
         "REGION stays at index 0 after removing the later CATEGORY field, so its sort must " +
         "survive");
   }

   @Test
   void moveFieldPreservesSortOnAFieldThatStaysOnTheSameShelfAtTheSamePosition() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region"), dim("Category")));
      TableBindingMutator.setSort(model, "rows", "Region", null,
                                  new DimensionSortRanking.Sort("desc", null, null));

      TableBindingMutator.moveField(model, "rows", "cols", "Category", null);

      assertEquals(1, model.getRows().size());
      assertEquals(XConstants.SORT_DESC, model.getRows().get(0).getOrder(),
         "REGION stays at rows index 0, so moving CATEGORY off the shelf must not reset its " +
         "sort");
   }

   @Test
   void resubmittingADuplicateBoundColumnKeepsEachOccurrencesSortSeparate() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(dated("ORDER_DATE", "year"), dated("ORDER_DATE", "quarter")));
      TableBindingMutator.setSort(model, "rows", "ORDER_DATE", 0,
         new DimensionSortRanking.Sort("desc", null, null));
      TableBindingMutator.setSort(model, "rows", "ORDER_DATE", 1,
         new DimensionSortRanking.Sort("manual", null, List.of("Q1", "Q2", "Q3", "Q4")));

      TableBindingMutator.setShelf(model, "rows",
         List.of(dated("ORDER_DATE", "year"), dated("ORDER_DATE", "quarter")));

      assertEquals(XConstants.SORT_DESC, model.getRows().get(0).getOrder(),
         "the year occurrence's sort must not cross-contaminate with the quarter occurrence's");
      assertEquals(XConstants.SORT_SPECIFIC, model.getRows().get(1).getOrder());
      assertEquals(List.of("Q1", "Q2", "Q3", "Q4"), model.getRows().get(1).getManualOrder());
   }

   /**
    * PR #5178 review round 1 (Important): {@code copyOf()} carries {@code order} forward from
    * the matched previous ref unconditionally, but the named-group reapply block below it only
    * ever SETS {@code SORT_SPECIFIC} when the incoming field supplies {@code namedGroupValues}/
    * {@code namedGroup} -- there was no path to clear it back off when the incoming field drops
    * the group. A field previously bound to a named group, resubmitted at the same column/index
    * without {@code namedGroupValues}/{@code namedGroup}, must not be left at
    * {@code SORT_SPECIFIC} with no {@code namedGroupInfo} backing it. Mirrors the self-heal
    * {@code BDimensionRefModel.createDataRef()} already applies at conversion time.
    */
   @Test
   void resubmittingANamedGroupedFieldWithoutNamedGroupClearsSortSpecific() throws Exception {
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
      assertEquals(XConstants.SORT_SPECIFIC, model.getRows().get(0).getOrder());

      // Resubmit REGION at the same index WITHOUT namedGroupValues/namedGroup -- dropping it.
      TableBindingMutator.setShelf(model, "rows", List.of(dim("REGION")),
                                   rvsWithWorksheet(ws), QUERY1_SOURCE, refModelService());

      BDimensionRefModel dim = model.getRows().get(0);
      assertEquals(0, dim.getOrder() & XConstants.SORT_SPECIFIC,
         "order must not be left with the SORT_SPECIFIC bit set once the named group backing " +
         "it is dropped");
      assertNull(dim.getNamedGroupInfo(),
         "namedGroupInfo is null here, so order being SORT_SPECIFIC would be inconsistent");
   }

   /** Bug #78937: re-sending an already-grouped dimension must not reset its sort to manual. */
   @Test
   void resendingAnAlreadyGroupedFieldKeepsItsSortDirection() throws Exception {
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
      TableBindingMutator.setSort(model, "rows", "REGION", null,
                                  new DimensionSortRanking.Sort("desc", null, null));
      assertEquals(XConstants.SORT_DESC, model.getRows().get(0).getOrder());

      TableBindingMutator.setShelf(model, "rows", List.of(dimWithNamedGroup("REGION", "Coastal")),
                                   rvsWithWorksheet(ws), QUERY1_SOURCE, refModelService());

      BDimensionRefModel dim = model.getRows().get(0);
      assertEquals(XConstants.SORT_DESC, dim.getOrder(),
         "the echo of an already-grouped field must keep its desc sort, not become manual");
      assertNotNull(dim.getNamedGroupInfo());
      assertNotEquals(0, dim.getNamedGroupInfo().getType());
   }

   private static FieldRef dimWithInlineGroup(String column) {
      return new FieldRef(column, "dimension", null, null, null, null, null,
         new FieldRef.NamedGroupValues(
            List.of(new FieldRef.NamedGroupValues.Clause("West", List.of("CA", "OR")),
                    new FieldRef.NamedGroupValues.Clause("East", List.of("NY"))), null),
         null, null, null, null, null, null);
   }

   private static Worksheet worksheetWithGroupNamed(String name) {
      Condition condition = mock(Condition.class);
      when(condition.getOperation()).thenReturn(Condition.EQUAL_TO);
      when(condition.getValues()).thenReturn(List.of("CA"));
      ConditionList conditionList = new ConditionList();
      conditionList.append(new ConditionItem(new AttributeRef(null, "REGION"), condition, 0));
      NamedGroupInfo namedGroupInfo = new NamedGroupInfo();
      namedGroupInfo.setGroupCondition("West", conditionList);

      DefaultNamedGroupAssembly ngAssembly = mock(DefaultNamedGroupAssembly.class);
      when(ngAssembly.getName()).thenReturn(name);
      when(ngAssembly.getAttachedType()).thenReturn(AttachedAssembly.COLUMN_ATTACHED);
      when(ngAssembly.getAttachedSource()).thenReturn(QUERY1_SOURCE);
      when(ngAssembly.getAttachedAttribute()).thenReturn(new AttributeRef(null, "REGION"));
      when(ngAssembly.getNamedGroupInfo()).thenReturn(namedGroupInfo);

      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[]{ ngAssembly });
      return ws;
   }

   /** Bug #78937, the vtb-070 shape: an inline group re-sent with namedGroupValues. */
   @Test
   void resendingAnInlineGroupWithNamedGroupValuesKeepsItsDescSort() throws Exception {
      Worksheet ws = worksheetWithGroupNamed("Unused");
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dimWithInlineGroup("REGION")),
                                   rvsWithWorksheet(ws), QUERY1_SOURCE, refModelService());
      TableBindingMutator.setSort(model, "rows", "REGION", null,
                                  new DimensionSortRanking.Sort("desc", null, null));
      assertEquals(XConstants.SORT_DESC, model.getRows().get(0).getOrder());

      TableBindingMutator.setShelf(model, "rows", List.of(dimWithInlineGroup("REGION")),
                                   rvsWithWorksheet(ws), QUERY1_SOURCE, refModelService());

      BDimensionRefModel dim = model.getRows().get(0);
      assertEquals(XConstants.SORT_DESC, dim.getOrder());
      assertNotNull(dim.getNamedGroupInfo());
      assertEquals(XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO, dim.getNamedGroupInfo().getType());
   }

   /** Bug #78937, the {@code echoed} branch: the "Custom" placeholder resolves to a real group. */
   @Test
   void resendingAnExpertGroupThroughTheEchoBranchKeepsItsDescSort() throws Exception {
      Worksheet ws = worksheetWithGroupNamed("Custom");
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dimWithNamedGroup("REGION", "Custom")),
                                   rvsWithWorksheet(ws), QUERY1_SOURCE, refModelService());
      TableBindingMutator.setSort(model, "rows", "REGION", null,
                                  new DimensionSortRanking.Sort("desc", null, null));
      assertEquals(XConstants.SORT_DESC, model.getRows().get(0).getOrder());

      TableBindingMutator.setShelf(model, "rows", List.of(dimWithNamedGroup("REGION", "Custom")),
                                   rvsWithWorksheet(ws), QUERY1_SOURCE, refModelService());

      BDimensionRefModel dim = model.getRows().get(0);
      assertEquals(XConstants.SORT_DESC, dim.getOrder());
      assertEquals(XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO, dim.getNamedGroupInfo().getType());
   }

   /** Bug #78937 contrast: a group newly added to an ungrouped desc field is still SPECIFIC. */
   @Test
   void addingAGroupToAnUngroupedDescFieldStillBecomesSortSpecific() throws Exception {
      Worksheet ws = worksheetWithGroupNamed("Unused");
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("REGION")));
      TableBindingMutator.setSort(model, "rows", "REGION", null,
                                  new DimensionSortRanking.Sort("desc", null, null));
      assertEquals(XConstants.SORT_DESC, model.getRows().get(0).getOrder());

      TableBindingMutator.setShelf(model, "rows", List.of(dimWithInlineGroup("REGION")),
                                   rvsWithWorksheet(ws), QUERY1_SOURCE, refModelService());

      assertEquals(XConstants.SORT_SPECIFIC, model.getRows().get(0).getOrder());
   }

   /**
    * Removing CATEGORY (index 0) shifts ORDER_DATE from index 1 to index 0. A wholesale replace
    * (set_table_fields) still matches by absolute index and so does not follow it, but removeField
    * knows which field it removed and carries every other entry's own model, so ORDER_DATE keeps
    * its sort (bug #78362; this used to be the documented boundary for this edit).
    */
   @Test
   void removingAnEarlierFieldKeepsALaterFieldsSort() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Category"), dim("ORDER_DATE")));
      TableBindingMutator.setSort(model, "rows", "ORDER_DATE", null,
         new DimensionSortRanking.Sort("desc", null, null));

      TableBindingMutator.removeField(model, "rows", "Category");

      assertEquals(1, model.getRows().size());
      assertEquals(XConstants.SORT_DESC, model.getRows().get(0).getOrder(),
         "ORDER_DATE shifted from index 1 to index 0 but is carried whole, sort included");
   }

   @Test
   void replacingTheShelfWithAnEarlierFieldRemovedKeepsTheLaterFieldsState() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Category"), dim("ORDER_DATE")));
      TableBindingMutator.setSort(model, "rows", "ORDER_DATE", null,
         new DimensionSortRanking.Sort("desc", null, null));

      TableBindingMutator.setShelf(model, "rows", List.of(dim("ORDER_DATE")));

      assertEquals(XConstants.SORT_DESC, model.getRows().get(0).getOrder(),
         "the field is matched by column and level, not by the index it used to sit at");
   }

   /**
    * Refuter's edge case on 02-refute.md: {@code requireKnownMeasure} validation only runs from
    * {@link TableBindingMutator#setSort}/{@link TableBindingMutator#setRanking}, never from
    * {@code setShelf}/{@code dimensions()} -- so a preserved ranking reference can go stale if
    * its aggregate is removed in a companion edit. Confirmed here that this does not throw or
    * otherwise corrupt the model; it is a pre-existing gap in {@code requireKnownMeasure}'s
    * coverage, out of scope for VTB-004's own fix (see 03-fix.md).
    */
   @Test
   void removingTheReferencedAggregateLeavesAStaleRankingReferenceRatherThanBreaking() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(new FieldRef("Sales", "measure", "sum", null, null)));
      TableBindingMutator.setRanking(model, "rows", "Region", null,
         new DimensionSortRanking.Ranking("top", 5, "Sales", null));

      TableBindingMutator.removeField(model, "aggregates", "Sales");

      assertNull(model.getRows().get(0).getRankingCol(),
         "a ranking whose aggregate was removed is cleared rather than left pointing at it " +
         "(bug #77025 R2)");
      assertEquals(String.valueOf(inetsoft.uql.XCondition.NONE),
                   model.getRows().get(0).getRankingOption());
   }

   // ── calculateInfo survives an aggregates shelf rewrite (bug #76881) ────────
   //
   // aggregates() used to build a brand-new BAggregateRefModel on every write with no previous
   // list to fall back to at all -- VTB-004's own 03-fix.md explicitly left this shelf unfixed.
   // FieldRef.calculateInfo's own javadoc already documents null as "leave it unchanged", so a
   // matched previous ref's calculateInfo is copied forward only when the incoming field's own
   // is null; an incoming field that supplies its own still overrides it. Matched by same
   // absolute index + same column (case-insensitive) + same formula (case-insensitive).

   @Test
   void resubmittingTheIdenticalAggregatesShelfPreservesCalculateInfo() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Sales", "Sum", runningTotal("Sum"))));

      // The filed repro: resubmit the identical field list (omitting calculateInfo, the way an
      // agent resubmitting only one changed field on the shelf would) via set_table_fields'
      // backing call.
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Sales", "Sum")));

      BAggregateRefModel sales = model.getAggregates().get(0);
      assertNotNull(sales.getCalculateInfo(),
         "an unchanged aggregate's calculateInfo must survive a shelf resubmission");
      assertEquals("Sum", ((RunningTotalCalcInfo) sales.getCalculateInfo()).getAggregate());
   }

   // ── bug #78688: an omitted 'aggregate' is the default formula, so it is the same measure ──

   @Test
   void resubmittingANumericMeasureWithOmittedAggregateKeepsCalculateInfo() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(List.of(sourceTable("Orders", "Total", "double")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Total", "Sum", runningTotal("Sum"))));

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Total", null)));

      BAggregateRefModel total = model.getAggregates().get(0);
      assertEquals("Sum", total.getFormula());
      assertNotNull(total.getCalculateInfo(),
         "omitting aggregate means the default Sum, the same measure, so the calculator stays");
   }

   @Test
   void resubmittingANonNumericMeasureWithOmittedAggregateKeepsCalculateInfo() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(List.of(sourceTable("Orders", "Region", "string")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Region", "Count", runningTotal("Sum"))));

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Region", null)));

      BAggregateRefModel region = model.getAggregates().get(0);
      assertEquals("Count", region.getFormula());
      assertNotNull(region.getCalculateInfo());
   }

   @Test
   void resubmittingWithOmittedAggregateKeepsTheCaption() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(List.of(sourceTable("Orders", "Total", "double")));
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Total", "Sum")));
      model.getAggregates().get(0).setCaption("Cap");

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Total", null)));

      assertEquals("Cap", model.getAggregates().get(0).getCaption());
   }

   @Test
   void resubmittingAnAggregateCalcFieldWithOmittedAggregateKeepsCalculateInfo()
      throws Exception
   {
      CrosstabBindingModel model = new CrosstabBindingModel();
      RuntimeViewsheet rvs = rvsWithCalcField("Orders", "Discount Share", false);
      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(measureWithCalc("Discount Share", null,
                                                           runningTotal("Sum"))),
                                   rvs, ORDERS_SOURCE, refModelService());

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Discount Share", null)),
                                   rvs, ORDERS_SOURCE, refModelService());

      BAggregateRefModel ref = model.getAggregates().get(0);
      assertEquals("None", ref.getFormula());
      assertNotNull(ref.getCalculateInfo());
   }

   @Test
   void omittedAggregateOccurrenceCountingKeepsTheCalculatorOnTheFirstOccurrence() {
      for(boolean omitFirst : new boolean[] { true, false }) {
         CrosstabBindingModel model = new CrosstabBindingModel();
         model.setTables(List.of(sourceTable("Orders", "Total", "double")));
         TableBindingMutator.setShelf(model, "aggregates",
            List.of(measureWithCalc("Total", "Sum", runningTotal("Sum")),
                    measure("Total", "Sum")));

         TableBindingMutator.setShelf(model, "aggregates",
            omitFirst ? List.of(measure("Total", null), measure("Total", "Sum"))
                      : List.of(measure("Total", "Sum"), measure("Total", null)));

         assertNotNull(model.getAggregates().get(0).getCalculateInfo(),
            "calculator stays on occurrence #0 (omitFirst=" + omitFirst + ")");
         assertNull(model.getAggregates().get(1).getCalculateInfo(),
            "occurrence #1 must not also receive it (omitFirst=" + omitFirst + ")");
      }
   }

   /** Formula is part of an aggregate's identity: a changed formula is a different measure. */
   @Test
   void omittedAggregateOverADifferentPreviousFormulaDoesNotCarryTheCalculator() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(List.of(sourceTable("Orders", "Total", "double")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Total", "Average", runningTotal("Sum"))));

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Total", null)));

      assertEquals("Sum", model.getAggregates().get(0).getFormula());
      assertNull(model.getAggregates().get(0).getCalculateInfo());
   }

   @Test
   void resubmittingAnAggregateWithANewCalculateInfoOverridesThePreservedOne() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Sales", "Sum", runningTotal("Sum"))));

      // The incoming field DOES supply its own calculateInfo -- it must win, not the preserved
      // one, proving the fix's copy-forward-only-when-absent design doesn't block an explicit
      // override.
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Sales", "Sum", runningTotal("Average"))));

      BAggregateRefModel sales = model.getAggregates().get(0);
      assertEquals("Average", ((RunningTotalCalcInfo) sales.getCalculateInfo()).getAggregate(),
         "an incoming field's own calculateInfo must override a matched previous one");
   }

   @Test
   void resubmittingADuplicateBoundAggregateColumnKeepsEachFormulasCalculateInfoSeparate() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Total", "Sum", runningTotal("Sum")),
                 measure("Total", "Average")));

      // Resubmit both occurrences unchanged, omitting calculateInfo on both.
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Total", "Sum"), measure("Total", "Average")));

      assertNotNull(model.getAggregates().get(0).getCalculateInfo(),
         "the Sum occurrence's calculateInfo must survive");
      assertNull(model.getAggregates().get(1).getCalculateInfo(),
         "the Average occurrence never had a calculateInfo and must not inherit the Sum one");
   }

   /**
    * Regression guard for the fix, not a new capability: the refuter's own throwaway tests
    * already showed {@code addField}/{@code removeField}/{@code moveField} preserve an
    * untouched aggregate's {@code calculateInfo} today, via their {@code read()}-then-{@code
    * setShelf()} round trip (an accident of {@code FieldRef}'s own wire shape, not a
    * preservation mechanism) -- confirms the fix does not disturb that already-working path.
    */
   @Test
   void addFieldPreservesAnUntouchedAggregatesCalculateInfo() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Sales", "Sum", runningTotal("Sum"))));

      TableBindingMutator.addField(model, "aggregates", measure("Quantity", "Sum"), null);

      assertNotNull(model.getAggregates().get(0).getCalculateInfo(),
         "adding a different field to the shelf must not reset SALES's calculateInfo");
   }

   @Test
   void removeFieldPreservesAnUntouchedAggregatesCalculateInfo() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Sales", "Sum", runningTotal("Sum")),
                 measure("Quantity", "Sum")));

      TableBindingMutator.removeField(model, "aggregates", "Quantity");

      assertNotNull(model.getAggregates().get(0).getCalculateInfo(),
         "removing a different field from the shelf must not reset SALES's calculateInfo");
   }

   @Test
   void moveFieldPreservesAnUntouchedAggregatesCalculateInfo() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Sales", "Sum", runningTotal("Sum")),
                 measure("Quantity", "Sum")));

      TableBindingMutator.moveField(model, "aggregates", "rows", "Quantity", null);

      assertNotNull(model.getAggregates().get(0).getCalculateInfo(),
         "moving a different field off the shelf must not reset SALES's calculateInfo");
   }

   // ── column labels (2d Phase 2) ────────────────────────────────────────────

   /**
    * VTB-011 (Redmine #76574): {@code set_column_labels} is now real rather than an
    * unconditional 501 stub. Table needs no rendered lens at all — {@code ColumnRefModel.alias}
    * already round-trips through the existing, unmodified {@code VSTableBindingFactory
    * .updateTableAssembly} write path, so setting it here is a complete rename.
    */
   @Test
   void setsATableColumnsAliasDirectly() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details", List.of(dim("REGION")));

      TableBindingMutator.ColumnLabelWrite write =
         TableBindingMutator.setColumnLabels(model, Map.of("REGION", "Sales Region"));

      ColumnRefModel column = (ColumnRefModel) model.getDetails().get(0);
      assertEquals("Sales Region", column.getAlias());
      assertEquals(1, write.tableRenames().size());
      assertEquals("REGION", write.tableRenames().get(0).oldDisplayName());
      assertEquals("Sales Region", write.tableRenames().get(0).newDisplayName());
      assertTrue(write.crosstabTargets().isEmpty());
   }

   /** An empty string clears a Table column's alias rather than setting a blank one. */
   @Test
   void anEmptyLabelClearsATableColumnsAlias() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details", List.of(dim("REGION")));
      TableBindingMutator.setColumnLabels(model, Map.of("REGION", "Sales Region"));

      TableBindingMutator.ColumnLabelWrite write =
         TableBindingMutator.setColumnLabels(model, Map.of("REGION", ""));

      ColumnRefModel column = (ColumnRefModel) model.getDetails().get(0);
      assertNull(column.getAlias());
      assertEquals("REGION", write.tableRenames().get(0).newDisplayName(),
         "the header reverts to the bare attribute name once the alias is cleared");
   }

   /**
    * Native {@code ColumnRef.setAlias} treats an alias equal to the column's own attribute name
    * as no alias at all ({@code ColumnRef.java:326-341}) -- matched explicitly in the mutator's
    * own write (rather than left to the downstream {@code createDataRef()} call this model-level
    * test cannot see), so renaming a column back to its own name is a harmless no-op, not an
    * error or a literal self-referential alias.
    */
   @Test
   void renamingAColumnToItsOwnNameIsTreatedAsNoAlias() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details", List.of(dim("REGION")));

      TableBindingMutator.setColumnLabels(model, Map.of("REGION", "REGION"));

      ColumnRefModel column = (ColumnRefModel) model.getDetails().get(0);
      assertNull(column.getAlias());
   }

   /**
    * Bug #77036: a Table keys its columns by display name, so a label equal to another
    * column's made that column vanish from the render and the export. The Composer refuses it
    * (vs.table.duplicateAlias); so does this.
    */
   @Test
   void refusesATableLabelThatIsAnotherColumnsLabel() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details",
                                   List.of(dim("Customer:Region"), dim("Customer:State")));
      TableBindingMutator.setColumnLabels(model, Map.of("Customer:Region", "Area"));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.setColumnLabels(model, Map.of("Customer:State", "Area")));

      assertTrue(thrown.getMessage().contains("label of two columns"), thrown.getMessage());
      assertNull(((ColumnRefModel) model.getDetails().get(1)).getAlias());
   }

   /** The Composer's common.conflictingColumnAttribute case. */
   @Test
   void refusesATableLabelThatIsAnotherColumnsName() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details",
                                   List.of(dim("Customer:Region"), dim("Customer:State")));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.setColumnLabels(model,
            Map.of("Customer:State", "Customer:Region")));

      assertTrue(thrown.getMessage().contains("name of another column"), thrown.getMessage());
   }

   @Test
   void refusesTwoTableLabelsInOneCallThatCollide() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details", List.of(dim("Region"), dim("State")));

      assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.setColumnLabels(model,
            Map.of("Region", "Area", "State", "Area")));
   }

   /**
    * Judged by the labels the columns end up with, not the order the map is visited in:
    * Region takes State's name only because State is relabeled in the same call.
    */
   @Test
   void aRenameChainInOneCallIsAllowedWhateverTheOrder() {
      for(boolean regionFirst : new boolean[]{ true, false }) {
         TableBindingModel model = new TableBindingModel();
         TableBindingMutator.setShelf(model, "details", List.of(dim("Region"), dim("State")));
         Map<String, String> labels = new java.util.LinkedHashMap<>();

         if(regionFirst) {
            labels.put("Region", "State");
            labels.put("State", "Area");
         }
         else {
            labels.put("State", "Area");
            labels.put("Region", "State");
         }

         TableBindingMutator.setColumnLabels(model, labels);

         assertEquals("State", ((ColumnRefModel) model.getDetails().get(0)).getAlias());
         assertEquals("Area", ((ColumnRefModel) model.getDetails().get(1)).getAlias());
      }
   }

   @Test
   void swappingTwoTableLabelsInOneCallIsAllowed() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details", List.of(dim("Region"), dim("State")));
      TableBindingMutator.setColumnLabels(model, Map.of("Region", "A", "State", "B"));

      TableBindingMutator.setColumnLabels(model, Map.of("Region", "B", "State", "A"));

      assertEquals("B", ((ColumnRefModel) model.getDetails().get(0)).getAlias());
      assertEquals("A", ((ColumnRefModel) model.getDetails().get(1)).getAlias());
   }

   @Test
   void relabelingATableColumnToItsOwnLabelIsAllowed() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details", List.of(dim("Region"), dim("State")));
      TableBindingMutator.setColumnLabels(model, Map.of("Region", "Area"));

      assertDoesNotThrow(() -> TableBindingMutator.setColumnLabels(model,
                                                                  Map.of("Region", "Area")));
      assertEquals("Area", ((ColumnRefModel) model.getDetails().get(0)).getAlias());
   }

   /**
    * Crosstab has no alias field to write directly -- a header rename there is a {@code
    * TableDataPath} cell override that needs a rendered lens {@code TableBindingMutator} does not
    * have, so this only resolves the label down to a shelf position and hands it back for {@code
    * TableBindingService} (which does have the live assembly and lens) to turn into the actual
    * write.
    */
   @Test
   void resolvesACrosstabLabelToAShelfPositionWithoutWritingItItself() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Sales", "Sum")));

      TableBindingMutator.ColumnLabelWrite write = TableBindingMutator.setColumnLabels(
         model, Map.of("Region", "Sales Region", "Sum(Sales)", "Revenue"));

      assertTrue(write.tableRenames().isEmpty());
      assertEquals(2, write.crosstabTargets().size());

      TableBindingMutator.CrosstabTarget rowTarget = write.crosstabTargets().stream()
         .filter(t -> "rows".equals(t.shelf())).findFirst().orElseThrow();
      assertEquals(0, rowTarget.index());
      assertEquals("Sales Region", rowTarget.label());

      TableBindingMutator.CrosstabTarget aggTarget = write.crosstabTargets().stream()
         .filter(t -> "aggregates".equals(t.shelf())).findFirst().orElseThrow();
      assertEquals(0, aggTarget.index());
      assertEquals("Revenue", aggTarget.label());
   }

   /**
    * A column bound twice (a Year/Quarter drill) cannot be labelled unambiguously by a bare
    * {@code labels} key -- refused, directing the caller to {@code entries} instead of guessing
    * which occurrence was meant.
    */
   @Test
   void refusesAnAmbiguousLabelForADuplicateBoundColumn() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(dim("Order Date"), dim("Order Date")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setColumnLabels(model, Map.of("Order Date", "Year")));

      assertTrue(thrown.getMessage().contains("ambiguous"));
      assertTrue(thrown.getMessage().contains("entries"));
   }

   /** {@code entries}' explicit {@code index} disambiguates what a bare {@code labels} key cannot. */
   @Test
   void entriesDisambiguateADuplicateBoundColumnByIndex() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(dim("Order Date"), dim("Order Date")));

      TableBindingMutator.ColumnLabelWrite write = TableBindingMutator.setColumnLabels(
         model, null,
         List.of(new ColumnLabelEntry("rows", "Order Date", 0, "Year"),
                 new ColumnLabelEntry("rows", "Order Date", 1, "Quarter")));

      assertEquals(2, write.crosstabTargets().size());
      TableBindingMutator.CrosstabTarget first = write.crosstabTargets().get(0);
      TableBindingMutator.CrosstabTarget second = write.crosstabTargets().get(1);
      assertEquals(0, first.index());
      assertEquals("Year", first.label());
      assertEquals(1, second.index());
      assertEquals("Quarter", second.label());
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

   @Test
   void refusesWhenBothLabelsAndEntriesAreEmpty() {
      assertThrows(IllegalArgumentException.class,
                   () -> TableBindingMutator.setColumnLabels(new CrosstabBindingModel(),
                                                             Map.of(), List.of()));
   }

   // ── crosstab column visibility (bug #76869, VTB-030) ──────────────────────

   /**
    * {@code resolveVisibilityTarget} shares {@code set_column_labels}' own name-first resolver
    * ({@code resolveShelfTarget}) -- the ordinary, unambiguous case resolves a bare column name to
    * its binding-order shelf position with no {@code shelf}/{@code index} needed from the caller.
    */
   @Test
   void resolvesAnUnambiguousColumnToItsShelfPosition() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Sales", "Sum")));

      TableBindingMutator.ShelfIndex rowTarget =
         TableBindingMutator.resolveVisibilityTarget(model, null, "Region", null);
      assertEquals("rows", rowTarget.shelf());
      assertEquals(0, rowTarget.index());

      TableBindingMutator.ShelfIndex aggTarget =
         TableBindingMutator.resolveVisibilityTarget(model, null, "Sum(Sales)", null);
      assertEquals("aggregates", aggTarget.shelf());
      assertEquals(0, aggTarget.index());
   }

   /** A column bound twice (a Year/Quarter drill) cannot be hidden unambiguously by bare name. */
   @Test
   void refusesAnAmbiguousVisibilityTargetForADuplicateBoundColumn() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(dim("Order Date"), dim("Order Date")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.resolveVisibilityTarget(model, null, "Order Date", null));

      assertTrue(thrown.getMessage().contains("ambiguous"));
      assertTrue(thrown.getMessage().contains("entries"));
   }

   /** An explicit {@code index} disambiguates what a bare column name cannot. */
   @Test
   void indexDisambiguatesADuplicateBoundVisibilityTarget() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(dim("Order Date"), dim("Order Date")));

      TableBindingMutator.ShelfIndex second =
         TableBindingMutator.resolveVisibilityTarget(model, "rows", "Order Date", 1);

      assertEquals("rows", second.shelf());
      assertEquals(1, second.index());
   }

   /**
    * The "not bound" message is feature-specific text, not {@code set_column_labels}' "a label
    * for it would never be shown" -- confirming {@code resolveShelfTarget}'s shared error path was
    * actually parameterized, not just copy-pasted with the label wording left in place.
    */
   @Test
   void namesAnUnboundColumnWithVisibilitySpecificWording() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.resolveVisibilityTarget(model, null, "Profit", null));

      assertTrue(thrown.getMessage().contains("nothing to hide or show"));
      assertFalse(thrown.getMessage().contains("label"));
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

   /** By-column with no columns bound is refused: every cell would be its own total (100%). */
   @Test
   void refusesPercentageByColWithNoColumnsBound() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(new CrosstabBindingModel(),
                                              Map.of("percentageBy", "col")));

      assertTrue(thrown.getMessage().contains("100%"));
      assertFalse(thrown.getMessage().contains("zeros"));
   }

   /** The row twin: by-row with no rows bound is refused for the same reason. */
   @Test
   void refusesPercentageByRowWithNoRowsBound() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(new CrosstabBindingModel(),
                                              Map.of("percentageBy", "row")));

      assertTrue(thrown.getMessage().contains("100%"));
      assertFalse(thrown.getMessage().contains("zeros"));
   }

   private static FieldRef percentMeasure(String column) {
      return new FieldRef(column, "measure", "Sum", null, null, null, null, null,
                          new PercentCalcInfo(), null, null, null, null, null, null);
   }

   private static void storePercentageBy(CrosstabBindingModel model, String token) {
      inetsoft.web.binding.model.table.CrosstabOptionInfo info = model.getOption() == null
         ? new inetsoft.web.binding.model.table.CrosstabOptionInfo() : model.getOption();
      info.setPercentageByValue(token);
      model.setOption(info);
   }

   private static boolean percentageByAccepts(CrosstabBindingModel model, String direction) {
      try {
         TableBindingMutator.setOptions(model, Map.of("percentageBy", direction));
         return true;
      }
      catch(IllegalArgumentException e) {
         return false;
      }
   }

   /** Bug #78982: no rows and no cols is one grand-total cell; 100% is true and nothing to advise. */
   @Test
   void aPercentOnlyCrosstabWithNoRowsOrColsGetsNoDegenerateWarning() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates", List.of(percentMeasure("Sales")));

      assertNull(TableBindingMutator.degeneratePercentWarning(model));
   }

   @Test
   void aPercentOnlyCrosstabWithStoredRowDirectionAndNoShelvesGetsNoDegenerateWarning() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates", List.of(percentMeasure("Sales")));
      storePercentageBy(model, "2");

      assertNull(TableBindingMutator.degeneratePercentWarning(model));
   }

   @Test
   void aRowsOnlyPercentCrosstabStillWarnsAndAdvisesRow() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates", List.of(percentMeasure("Sales")));

      String warning = TableBindingMutator.degeneratePercentWarning(model);

      assertNotNull(warning);
      assertTrue(warning.contains("Set percentageBy to row"));
      assertTrue(percentageByAccepts(model, "row"), "the advised direction must be followable");
   }

   @Test
   void aColsOnlyPercentCrosstabWithRowDirectionStillWarnsAndAdvisesCol() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "cols", List.of(dim("Year")));
      TableBindingMutator.setShelf(model, "aggregates", List.of(percentMeasure("Sales")));
      storePercentageBy(model, "2");

      String warning = TableBindingMutator.degeneratePercentWarning(model);

      assertNotNull(warning);
      assertTrue(warning.contains("Set percentageBy to col"));
      assertTrue(percentageByAccepts(model, "col"), "the advised direction must be followable");
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

   /**
    * Bug #78359: the chart-side inline-group read rule must not reach a crosstab -- adding an
    * unrelated field never turns an expert (worksheet-group) dimension into an inline group, and its
    * read keeps its named-group read rather than flipping to inline values.
    */
   @Test
   void addFieldNeverTurnsAnExpertNamedGroupIntoAnInlineOne() throws Exception {
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
      FieldRef before = TableBindingMutator.read(model, "rows").get(0);
      assertNull(before.namedGroupValues());

      TableBindingMutator.addField(model, "rows", dim("Year"), null);

      // The no-rvs addField overload drops a group whose read carries no name (pre-existing,
      // unrelated to #78359); what must not happen is the group coming back as an inline one.
      NamedGroupInfoModel after = model.getRows().get(0).getNamedGroupInfo();
      assertTrue(after == null || after.getType() != XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO);
      FieldRef read = TableBindingMutator.read(model, "rows").get(0);
      assertEquals(before.namedGroup(), read.namedGroup());
      assertNull(read.namedGroupValues());
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

   // ── timeSeries (bug #76793) ─────────────────────────────────────────────────

   @Test
   void settingTimeSeriesTrueOnAWriteAppliesItToTheDimension() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setShelf(model, "rows",
         List.of(new FieldRef("Order Date", "dimension", null, "quarter", null, null, null, null,
                              null, null, null, null, true)));

      assertTrue(model.getRows().get(0).isTimeSeries());
   }

   /**
    * The refuter's required addition to the diagnosis's fix-direction: a write that omits
    * {@code timeSeries} must not silently reset a previously-set {@code true} back to
    * {@code false} -- copyOf() must forward the matched previous ref's value, the same as it
    * already does for sort/ranking (VTB-004).
    */
   @Test
   void resubmittingWithNoTimeSeriesKeyPreservesItsPriorState() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(new FieldRef("Order Date", "dimension", null, "quarter", null, null, null, null,
                              null, null, null, null, true)));

      // The filed repro: resubmit the same field (same column + date level, so copyOf() matches
      // it to the previous ref) with no 'timeSeries' key at all.
      TableBindingMutator.setShelf(model, "rows",
         List.of(new FieldRef("Order Date", "dimension", null, "quarter", null)));

      assertTrue(model.getRows().get(0).isTimeSeries(),
         "a write that omits 'timeSeries' must preserve the shelf position's prior state, not " +
         "reset it to false");
   }

   @Test
   void explicitlySettingTimeSeriesFalseClearsAPreviouslySetFlag() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(new FieldRef("Order Date", "dimension", null, "quarter", null, null, null, null,
                              null, null, null, null, true)));

      TableBindingMutator.setShelf(model, "rows",
         List.of(new FieldRef("Order Date", "dimension", null, "quarter", null, null, null, null,
                              null, null, null, null, false)));

      assertFalse(model.getRows().get(0).isTimeSeries(),
         "an explicit 'timeSeries: false' must still win over the prior state");
   }

   // ── default aggregate when 'aggregate' is omitted (bug #76949, VCF-001) ────

   /**
    * Bug #76949 (VCF-001): {@code aggregate} is optional on the agent's {@code aggregates}
    * shelf, and omitting it used to reach {@code VSCrosstabBindingFactory#getDefaultFormula()}
    * with a null formula, refType 0 and a null wrapped ref -- which threw, surfacing as an
    * opaque 500 with the shelf left unchanged.
    *
    * <p>This is the plain-measure half, which bug #76650's fix never covered: its
    * AGG_CALC/AGG_EXPR branch only fires for a stamped refType, and nothing in this package
    * stamped one.
    */
   @Test
   void settingANumericMeasureWithNoAggregateDefaultsToSum() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(List.of(sourceTable("Orders", "Total", "double")));

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Total", null)));

      assertEquals(1, model.getAggregates().size());
      assertEquals("Sum", model.getAggregates().get(0).getFormula(),
                   "a numeric measure with no explicit aggregate defaults to Sum, the same " +
                   "default move_table_field already applied");
   }

   @Test
   void settingANonNumericMeasureWithNoAggregateDefaultsToCount() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(List.of(sourceTable("Orders", "Region", "string")));

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Region", null)));

      assertEquals("Count", model.getAggregates().get(0).getFormula());
   }

   /**
    * An unknown column has no reported data type, so it falls back to Count rather than
    * throwing -- the point of the fix is that no shape of this call produces a 500.
    */
   @Test
   void settingAnUnknownMeasureWithNoAggregateDefaultsToCount() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Mystery", null)));

      assertEquals("Count", model.getAggregates().get(0).getFormula());
   }

   /**
    * The calc-field half of #76949, and the one #76650 aimed at: an aggregate-mode calc field
    * (baseOnDetail false) is already an aggregated value, so it takes None rather than being
    * wrapped in a second formula that would aggregate it twice. The refType must be stamped
    * too -- that is the bit #76650's branch tests and this package never set.
    */
   @Test
   void settingAnAggregateCalcFieldWithNoAggregateDefaultsToNoneAndStampsAggCalc()
      throws Exception
   {
      CrosstabBindingModel model = new CrosstabBindingModel();
      RuntimeViewsheet rvs = rvsWithCalcField("Orders", "Discount Share", false);

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Discount Share", null)),
                                   rvs, ORDERS_SOURCE, refModelService());

      BAggregateRefModel ref = model.getAggregates().get(0);
      assertEquals("None", ref.getFormula(),
                   "an already-aggregated calc field must not be wrapped in a second formula");
      assertEquals(DataRef.AGG_CALC, ref.getRefType() & DataRef.AGG_CALC,
                   "the AGG_CALC bit is what VSCrosstabBindingFactory#getDefaultFormula tests");
   }

   /**
    * The ref type is stamped whether or not a formula came in. A {@code FieldRef} carries no
    * ref type, so every read-modify-write of the shelf -- which is what each add/remove/move
    * does -- would otherwise drop the bit from the aggregates that were already there.
    */
   @Test
   void settingAnAggregateCalcFieldWithAnExplicitAggregateStillStampsAggCalc() throws Exception {
      CrosstabBindingModel model = new CrosstabBindingModel();
      RuntimeViewsheet rvs = rvsWithCalcField("Orders", "Discount Share", false);

      TableBindingMutator.setShelf(model, "aggregates",
                                   List.of(measure("Discount Share", "None")),
                                   rvs, ORDERS_SOURCE, refModelService());

      BAggregateRefModel ref = model.getAggregates().get(0);
      assertEquals("None", ref.getFormula(), "an explicit aggregate is never overwritten");
      assertEquals(DataRef.AGG_CALC, ref.getRefType() & DataRef.AGG_CALC);
   }

   /**
    * A detail-mode calc field is an ordinary column, not an already-aggregated value, so it
    * takes the data type's default and no AGG_CALC bit.
    */
   @Test
   void settingADetailCalcFieldWithNoAggregateDefaultsToTheDataTypeFormula() throws Exception {
      CrosstabBindingModel model = new CrosstabBindingModel();
      RuntimeViewsheet rvs = rvsWithCalcField("Orders", "Net Sales", true, "double");

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Net Sales", null)),
                                   rvs, ORDERS_SOURCE, refModelService());

      BAggregateRefModel ref = model.getAggregates().get(0);
      assertEquals("Sum", ref.getFormula());
      assertEquals(0, ref.getRefType() & DataRef.AGG_CALC,
                   "a detail calc field is an ordinary column, not an aggregate");
   }

   @Test
   void settingAStringDetailCalcFieldWithNoAggregateDefaultsToCount() throws Exception {
      CrosstabBindingModel model = new CrosstabBindingModel();
      RuntimeViewsheet rvs = rvsWithCalcField("Orders", "Label", true, "string");

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Label", null)),
                                   rvs, ORDERS_SOURCE, refModelService());

      assertEquals("Count", model.getAggregates().get(0).getFormula());
   }

   @Test
   void settingAnUntypedDetailCalcFieldWithNoAggregateDefaultsToCount() throws Exception {
      CrosstabBindingModel model = new CrosstabBindingModel();
      RuntimeViewsheet rvs = rvsWithCalcField("Orders", "Net Sales", true, null);

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Net Sales", null)),
                                   rvs, ORDERS_SOURCE, refModelService());

      assertEquals("Count", model.getAggregates().get(0).getFormula());
   }

   @Test
   void chartMeasureOnADetailCalcFieldWithNoAggregateDefaultsToSum() throws Exception {
      RuntimeViewsheet rvs = rvsWithCalcField("Orders", "Net Sales", true, "double");
      BAggregateRefModel ref = new BAggregateRefModel();

      FieldRefFactory.applyAggregateDefaults(ref, new ChartBindingModel(), rvs, ORDERS_SOURCE,
                                             "Net Sales");

      assertEquals("Sum", ref.getFormula());
   }

   /**
    * Moving an aggregate calc field onto the shelf used to default it to Count -- {@code
    * convertForShelf} only knew about data types, and a calc field has no reported one. It now
    * shares the same resolver the set path uses.
    */
   @Test
   void movingAnAggregateCalcFieldOntoAggregatesDefaultsToNone() throws Exception {
      CrosstabBindingModel model = new CrosstabBindingModel();
      RuntimeViewsheet rvs = rvsWithCalcField("Orders", "Discount Share", false);
      // Seeded without context: Bug #78530 refuses an aggregate calc field on rows, so this is
      // the already-stuck state an earlier server left, which moving it off rows must clear.
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Discount Share")));

      TableBindingMutator.moveField(model, "rows", "aggregates", "Discount Share", null, rvs,
                                    ORDERS_SOURCE, refModelService());

      assertEquals("None", model.getAggregates().get(0).getFormula());
   }

   private static final SourceInfo ORDERS_SOURCE =
      new SourceInfo(SourceInfo.ASSET, null, "Orders");

   private static RuntimeViewsheet rvsWithCalcField(String table, String column,
                                                    boolean baseOnDetail)
   {
      return rvsWithCalcField(table, column, baseOnDetail, null);
   }

   private static RuntimeViewsheet rvsWithCalcField(String table, String column,
                                                    boolean baseOnDetail, String dataType)
   {
      CalculateRef calc = new CalculateRef(baseOnDetail);
      calc.setDataRef(new AttributeRef(column));

      if(dataType != null) {
         calc.setDataType(dataType);
      }

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getCalcField(table, column)).thenReturn(calc);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   /**
    * Redmine #78436: re-sending a shelf dimension spelled differently only by case lands on the
    * same index, so the key string -- not just the position -- must be compared, or the entry
    * stored under the old spelling is pruned as an orphan.
    */
   @Test
   void aCaseOnlyRespellingAtTheSameIndexCarriesTheSuppressionEntry() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Customer:State"),
                                                          dim("Customer:Region")));
      model.getSuppressGroupTotal().put("Customer:State:rows0", Boolean.TRUE);

      TableBindingMutator.setShelf(model, "rows", List.of(dim("customer:state"),
                                                          dim("Customer:Region")));

      assertEquals(Map.of("customer:state:rows0", Boolean.TRUE),
                   model.getSuppressGroupTotal(),
                   "State's suppression must follow its respelled key, not be pruned");
   }

   // Redmine #78437: a same-named calc field on another table must not decide the default.

   private static RuntimeViewsheet rvsWithCalcsByTable(String table, String column,
                                                       boolean baseOnDetail, String dataType)
   {
      CalculateRef calc = new CalculateRef(baseOnDetail);
      calc.setDataRef(new AttributeRef(column));
      calc.setDataType(dataType);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getCalcField(table, column)).thenReturn(calc);
      when(vs.getCalcFieldSources()).thenReturn(List.of(table));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static CrosstabBindingModel projectionAndRatioModel(String source) {
      CrosstabBindingModel model = new CrosstabBindingModel();

      if(source != null) {
         inetsoft.web.binding.model.SourceInfo modelSource =
            new inetsoft.web.binding.model.SourceInfo();
         modelSource.setSource(source);
         model.setSource(modelSource);
      }

      model.setTables(List.of(sourceTable("Ratio", "Amount", "double"),
                              sourceTable("Projection", "Revenue", "double")));
      return model;
   }

   // ── bug #77025 P3/P4/P6: per-ref state FieldRef cannot carry ──────────────

   @Test
   void aggregatesWriteKeepsNthNAcrossResubmitAddAndShift() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Cost", "Sum"), nth("Sales", "NthLargest", "3")));

      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Cost", "Sum"), measure("Sales", "NthLargest")));
      assertEquals("3", model.getAggregates().get(1).getNumValue(), "resubmit");

      TableBindingMutator.addField(model, "aggregates", measure("Qty", "Sum"), null);
      assertEquals("3", model.getAggregates().get(1).getNumValue(), "add");

      // Removing Cost shifts Sales from index 1 to 0; an index-locked match would lose N.
      TableBindingMutator.removeField(model, "aggregates", "Cost");
      assertEquals("Sales", model.getAggregates().get(0).getName());
      assertEquals("3", model.getAggregates().get(0).getNumValue(), "shift by remove");
   }

   @Test
   void aggregatesWriteDoesNotCarryNWhenTheFormulaChanges() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates", List.of(nth("Sales", "NthLargest", "3")));

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Sales", "Sum")));

      assertNull(model.getAggregates().get(0).getNumValue());
   }

   @Test
   void aggregatesWriteMatchesDuplicateMeasuresByOccurrence() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(nth("Sales", "NthLargest", "2"), nth("Sales", "NthLargest", "5")));
      model.getAggregates().get(1).setCaption("Fifth");

      // Both shift by one position behind a new leading aggregate.
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Cost", "Sum"), measure("Sales", "NthLargest"),
                 measure("Sales", "NthLargest")));

      assertEquals("2", model.getAggregates().get(1).getNumValue());
      assertEquals("5", model.getAggregates().get(2).getNumValue());
      assertEquals("Fifth", model.getAggregates().get(2).getCaption());
   }

   // ── bug #78856: the N/P operand of Nth/Pth aggregates ─────────────────────

   private static final List<String> OPERAND_FORMULAS =
      List.of("NthLargest", "NthSmallest", "NthMostFrequent", "PthPercentile");

   private static String operandOnShelf(CrosstabBindingModel model, int index) {
      return TableBindingMutator.read(model, "aggregates").get(index).operand();
   }

   @Test
   void aFreshOperandFormulaTakesItsExplicitOperandAndReadsItBack() {
      for(String formula : OPERAND_FORMULAS) {
         CrosstabBindingModel model = new CrosstabBindingModel();
         TableBindingMutator.setShelf(model, "aggregates", List.of(nth("Total", formula, "90")));

         assertEquals("90", model.getAggregates().get(0).getNumValue(), formula);
         assertEquals("90", operandOnShelf(model, 0), formula);
      }
   }

   @Test
   void refusesABareOperandFormulaOnAFreshAggregateNamingTheColumnAndFormula() {
      for(String formula : OPERAND_FORMULAS) {
         // Median first, so the matched-previous rule cannot supply anything: the formula changes.
         CrosstabBindingModel model = new CrosstabBindingModel();
         TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Total", "Median")));

         IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            TableBindingMutator.setShelf(model, "aggregates",
                                         List.of(measure("Total", formula))), formula);

         assertTrue(e.getMessage().contains("'Total'") && e.getMessage().contains(formula),
                    e.getMessage());
         assertEquals("Median", model.getAggregates().get(0).getFormula(),
                      "a refused write must leave the shelf as it was");
      }
   }

   @Test
   void refusesABareOperandFormulaAddedThroughAddField() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Cost", "Sum")));

      assertThrows(IllegalArgumentException.class, () ->
         TableBindingMutator.addField(model, "aggregates", measure("Total", "PthPercentile"),
                                      null));

      TableBindingMutator.addField(model, "aggregates", nth("Total", "PthPercentile", "90"),
                                   null);
      assertEquals("90", model.getAggregates().get(1).getNumValue());
   }

   @Test
   void anExplicitOperandBeatsTheOneInheritedFromAMatchedPreviousRef() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(nth("Total", "PthPercentile", "90")));

      TableBindingMutator.setShelf(model, "aggregates",
         List.of(nth("Total", "PthPercentile", "50")));

      assertEquals("50", model.getAggregates().get(0).getNumValue());
   }

   @Test
   void aBareResubmitOfAnExistingOperandAggregateKeepsItsOperand() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(nth("Total", "PthPercentile", "90")));

      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Total", "PthPercentile")));

      assertEquals("90", model.getAggregates().get(0).getNumValue());
   }

   @Test
   void anUnrelatedEditDoesNotRefuseAnExistingOperandAggregateThatStoresNoOperand() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      BAggregateRefModel legacy = new BAggregateRefModel();
      legacy.setName("Total");
      legacy.setColumnValue("Total");
      legacy.setFormula("NthLargest");
      BAggregateRefModel cost = new BAggregateRefModel();
      cost.setName("Cost");
      cost.setColumnValue("Cost");
      cost.setFormula("Sum");
      model.setAggregates(new java.util.ArrayList<>(List.of(legacy, cost)));

      TableBindingMutator.removeField(model, "aggregates", "Cost");

      assertEquals(1, model.getAggregates().size());
      assertEquals("NthLargest", model.getAggregates().get(0).getFormula());
   }

   @Test
   void rangeChecksALiteralOperandInsteadOfLettingTheEngineClampIt() {
      for(String[] bad : new String[][] {
         {"PthPercentile", "0"}, {"PthPercentile", "101"}, {"PthPercentile", "-5"},
         {"NthLargest", "0"}, {"NthSmallest", "-1"}, {"NthMostFrequent", "2147483648"},
         {"PthPercentile", "ninety"}, {"NthLargest", "2.5"}})
      {
         CrosstabBindingModel model = new CrosstabBindingModel();
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            TableBindingMutator.setShelf(model, "aggregates",
                                         List.of(nth("Total", bad[0], bad[1]))),
            bad[0] + " " + bad[1]);
         assertTrue(e.getMessage().contains(bad[1]), e.getMessage());
      }

      CrosstabBindingModel ok = new CrosstabBindingModel();
      TableBindingMutator.setShelf(ok, "aggregates",
         List.of(nth("A", "PthPercentile", "1"), nth("B", "PthPercentile", "100"),
                 nth("C", "NthLargest", "2147483647")));
      assertEquals("100", ok.getAggregates().get(1).getNumValue());
   }

   @Test
   void passesADynamicOperandThroughUnchecked() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(nth("A", "PthPercentile", "$(p)"), nth("B", "NthLargest", "=1+1")));

      assertEquals("$(p)", model.getAggregates().get(0).getNumValue());
      assertEquals("=1+1", operandOnShelf(model, 1));
   }

   @Test
   void refusesAnOperandOnAFormulaThatTakesNone() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         TableBindingMutator.setShelf(model, "aggregates", List.of(nth("Total", "Sum", "90"))));

      assertTrue(e.getMessage().contains("'Total'") && e.getMessage().contains("Sum"),
                 e.getMessage());
   }

   @Test
   void readsNoOperandBackForAFormulaThatTakesNone() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Total", "Sum")));
      // The runtime stores "1" as the N of every aggregate, an Nth/Pth one or not.
      model.getAggregates().get(0).setNumValue("1");

      assertNull(operandOnShelf(model, 0));
   }

   // ── bug #78865: clearing a measure's calculator ───────────────────────────

   private static FieldRef clearingCalc(String column, String aggregate) {
      try {
         return new ObjectMapper().readValue(
            "{\"column\":\"" + column + "\",\"type\":\"measure\",\"aggregate\":\"" + aggregate +
            "\",\"calculateInfo\":null}", FieldRef.class);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @Test
   void anExplicitNullCalculateInfoClearsItButKeepsTheRestOfTheMatch() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(new FieldRef("Total", "measure", "NthLargest", null, null, null, null, null,
                              new PercentCalcInfo(), null, null, null, null, null, "3")));
      model.getAggregates().get(0).setCaption("Third");
      assertNotNull(model.getAggregates().get(0).getCalculateInfo());

      TableBindingMutator.drainNotes();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(clearingCalc("Total", "NthLargest")));

      BAggregateRefModel total = model.getAggregates().get(0);
      assertNull(total.getCalculateInfo());
      assertEquals("3", total.getNumValue(), "only the calculator copy is skipped");
      assertEquals("Third", total.getCaption());
      assertTrue(TableBindingMutator.drainNotes().isEmpty(), "a clear is not a kept calculator");
   }

   @Test
   void anOmittedCalculateInfoKeepsItAndSaysSoOnAReplace() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Total", "Sum", runningTotal("Sum")),
                 measure("Cost", "Sum")));

      TableBindingMutator.drainNotes();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Total", "Sum"), measure("Cost", "Sum")));

      assertNotNull(model.getAggregates().get(0).getCalculateInfo());
      List<String> notes = TableBindingMutator.drainNotes();
      assertEquals(1, notes.size(), "only the entry that actually kept a calculator: " + notes);
      assertTrue(notes.get(0).startsWith(TableBindingMutator.KEPT_NOTE_PREFIX), notes.get(0));
      assertTrue(notes.get(0).contains("'Total'") && notes.get(0).contains("calculateInfo"),
                 notes.get(0));
   }

   @Test
   void addAndRemoveNeverClaimTheyKeptACalculatorTheCallerDidNotName() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Total", "Sum", runningTotal("Sum")), measure("Cost", "Sum")));

      TableBindingMutator.drainNotes();
      TableBindingMutator.addField(model, "aggregates", measure("Qty", "Sum"), null);
      TableBindingMutator.removeField(model, "aggregates", "Qty");

      assertNotNull(model.getAggregates().get(0).getCalculateInfo());
      assertTrue(TableBindingMutator.drainNotes().isEmpty());
   }

   @Test
   void anExplicitNullCalculateInfoIsHarmlessOnADimensionAndOnAMeasureWithNone() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      FieldRef nullDim = jsonDimWithNullCalc("Region");
      assertTrue(nullDim.clearsCalculateInfo(), "built from JSON, so it carries the clear request");

      TableBindingMutator.setShelf(model, "rows", List.of(nullDim));
      TableBindingMutator.setShelf(model, "aggregates", List.of(clearingCalc("Total", "Sum")));

      assertEquals("Region", model.getRows().get(0).getColumnValue());
      assertNull(model.getAggregates().get(0).getCalculateInfo());

      CrosstabBindingModel cols = new CrosstabBindingModel();
      TableBindingMutator.setShelf(cols, "cols", List.of(jsonDimWithNullCalc("Region")));
      assertEquals("Region", cols.getCols().get(0).getColumnValue());

      TableBindingModel table = new TableBindingModel();
      TableBindingMutator.setShelf(table, "details", List.of(jsonDimWithNullCalc("Region")));
      assertEquals(1, table.getDetails().size());
   }

   private static FieldRef jsonDimWithNullCalc(String column) {
      try {
         return new ObjectMapper().readValue(
            "{\"column\":\"" + column + "\",\"type\":\"dimension\",\"calculateInfo\":null}",
            FieldRef.class);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private static TableBindingModel tableWithLabels() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details",
         List.of(dim("Region"), dim("State"), dim("Zip")));
      TableBindingMutator.setColumnLabels(model, Map.of("Region", "Area", "State", "St"));
      ((ColumnRefModel) model.getDetails().get(1)).setWidth(120);
      return model;
   }

   private static String alias(TableBindingModel model, int index) {
      return ((ColumnRefModel) model.getDetails().get(index)).getAlias();
   }

   @Test
   void detailsWritesKeepHeaderAliasesAndWidths() {
      TableBindingModel model = tableWithLabels();

      TableBindingMutator.setShelf(model, "details",
         List.of(dim("Region"), dim("State"), dim("Zip")));
      assertEquals("Area", alias(model, 0), "replace");
      assertEquals("St", alias(model, 1));
      assertEquals(120, ((ColumnRefModel) model.getDetails().get(1)).getWidth());

      TableBindingMutator.addField(model, "details", dim("City"), null);
      assertEquals("Area", alias(model, 0), "add");
      assertEquals("St", alias(model, 1));
      assertNull(alias(model, 3));

      // Removing Region shifts State from index 1 to 0; the alias must follow the column.
      TableBindingMutator.removeField(model, "details", "Region");
      assertEquals("St", alias(model, 0), "remove shifts");
      assertEquals(120, ((ColumnRefModel) model.getDetails().get(0)).getWidth());
   }

   @Test
   void detailsReplaceHonoursAnExplicitLabelOverThePreviousAlias() {
      TableBindingModel model = tableWithLabels();

      TableBindingMutator.setShelf(model, "details",
         List.of(new FieldRef("Region", "dimension", null, null, null, null, null, null, null,
                              "Zone"),
                 dim("State")));

      assertEquals("Zone", alias(model, 0));
      assertEquals("St", alias(model, 1));
   }

   @Test
   void detailsWithoutLabelsIsUnchanged() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details", List.of(dim("Region")));
      TableBindingMutator.addField(model, "details", dim("State"), null);

      assertNull(alias(model, 0));
      assertNull(alias(model, 1));
   }

   private static CrosstabBindingModel rankedCrosstab(String rankingCol, String sortCol) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Sales", "Sum"), measure("Cost", "Sum")));
      model.getRows().get(0).setRankingCol(rankingCol);
      model.getRows().get(0).setSortByCol(sortCol);
      return model;
   }

   @Test
   void aCrossTableStringCalcFieldDoesNotMakeABoundDoubleColumnCount() {
      RuntimeViewsheet rvs = rvsWithCalcsByTable("Ratio", "Revenue", true, "string");
      BAggregateRefModel ref = new BAggregateRefModel();

      FieldRefFactory.applyAggregateDefaults(ref, projectionAndRatioModel("Projection"), rvs,
         new SourceInfo(SourceInfo.ASSET, null, "Projection"), "Revenue");

      assertEquals("Sum", ref.getFormula());
   }

   @Test
   void aNullAssemblySourceFallsBackToTheModelSource() {
      RuntimeViewsheet rvs = rvsWithCalcsByTable("Ratio", "Revenue", true, "string");
      BAggregateRefModel ref = new BAggregateRefModel();

      FieldRefFactory.applyAggregateDefaults(
         ref, projectionAndRatioModel("Projection"), rvs, null, "Revenue");

      assertEquals("Sum", ref.getFormula());
   }

   @Test
   void aCrossTableAggregateCalcFieldIsNotStampedOntoARealColumn() {
      RuntimeViewsheet rvs = rvsWithCalcsByTable("Ratio", "Revenue", false, "double");
      BAggregateRefModel ref = new BAggregateRefModel();

      FieldRefFactory.applyAggregateDefaults(ref, projectionAndRatioModel("Projection"), rvs,
         new SourceInfo(SourceInfo.ASSET, null, "Projection"), "Revenue");

      assertEquals("Sum", ref.getFormula());
      assertEquals(0, ref.getRefType() & DataRef.AGG_CALC);
   }

   @Test
   void aSameNamedStringColumnOnAnotherTableListedFirstDoesNotDecideTheDefault() {
      Viewsheet vs = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(List.of(sourceTable("Other", "Revenue", "string"),
                              sourceTable("Projection", "Revenue", "double")));
      BAggregateRefModel ref = new BAggregateRefModel();

      FieldRefFactory.applyAggregateDefaults(ref, model, rvs,
         new SourceInfo(SourceInfo.ASSET, null, "Projection"), "Revenue");

      assertEquals("Sum", ref.getFormula());
   }

   @Test
   void aBoundTableDetailCalcFieldReachedOnANullSourceKeepsItsOwnType() {
      RuntimeViewsheet rvs = rvsWithCalcsByTable("Projection", "Margin", true, "double");
      BAggregateRefModel ref = new BAggregateRefModel();

      FieldRefFactory.applyAggregateDefaults(
         ref, projectionAndRatioModel("Projection"), rvs, null, "Margin");

      assertEquals("Sum", ref.getFormula());
   }

   @Test
   void aCalcFieldKeyedUnderADifferentNameStillResolvesThroughTheScan() {
      RuntimeViewsheet rvs = rvsWithCalcsByTable("Order Model", "Net Sales", false, "double");

      CalculateRef found = FieldRefFactory.findCalcField(
         projectionAndRatioModel("Projection"), rvs,
         new SourceInfo(SourceInfo.ASSET, null, "Projection"), "Net Sales");

      assertNotNull(found);
      assertFalse(found.isBaseOnDetail());
   }

   @Test
   void formulaChangeRewritesFullNameRankingAndSortReferences() {
      CrosstabBindingModel model = rankedCrosstab("Sum(Sales)", "Sum(Sales)");

      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Sales", "Average"), measure("Cost", "Sum")));

      assertEquals("Average(Sales)", model.getRows().get(0).getRankingCol());
      assertEquals("Average(Sales)", model.getRows().get(0).getSortByCol());
   }

   @Test
   void formulaChangeLeavesBareAndUnrelatedReferencesAlone() {
      CrosstabBindingModel model = rankedCrosstab("Sales", "Sum(Cost)");

      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Sales", "Average"), measure("Cost", "Sum")));

      assertEquals("Sales", model.getRows().get(0).getRankingCol(),
                   "a bare column name is formula-agnostic");
      assertEquals("Sum(Cost)", model.getRows().get(0).getSortByCol());
   }

   @Test
   void formulaChangeDoesNotRewriteWhileTheOldFullNameIsStillBound() {
      CrosstabBindingModel model = rankedCrosstab("Sum(Sales)", null);

      // Sum(Sales) stays and an Average(Sales) is added: no aggregate changed formula.
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Sales", "Sum"), measure("Sales", "Average"), measure("Cost", "Sum")));

      assertEquals("Sum(Sales)", model.getRows().get(0).getRankingCol());
   }

   @Test
   void formulaChangeSurvivesAPositionShiftInTheSameWrite() {
      CrosstabBindingModel model = rankedCrosstab("Sum(Sales)", null);

      // Cost moves ahead and Sales changes formula in one replace.
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Cost", "Sum"), measure("Sales", "Max")));

      assertEquals("Max(Sales)", model.getRows().get(0).getRankingCol());
   }

   @Test
   void formulaChangeRewritesCalcAggregateBaseNameToo() {
      CrosstabBindingModel model = rankedCrosstab("Sum(Sales)", "Sum(Sales)");
      BDimensionRefModel.CalcAggregate calc = new BDimensionRefModel.CalcAggregate();
      calc.setBaseAggregateName("Sum(Sales)");
      model.getRows().get(0).setRankingAgg(calc);

      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Sales", "Average"), measure("Cost", "Sum")));

      assertEquals("Average(Sales)", calc.getBaseAggregateName());
   }

   // ── bug #77025 P1/P2: one occurrence of a duplicate-bound column; cross-shelf suppression ──

   private static CrosstabBindingModel drillCrosstab() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(dated("D", "year"), dated("D", "quarter"), dim("R")));
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Sales", "Sum"), measure("Sales", "Average"), measure("Q", "Sum")));
      return model;
   }

   private static String levelAt(CrosstabBindingModel model, String shelf, int index) {
      return DateLevels.name(TableBindingMutator.read(model, shelf).get(index).dateLevel());
   }

   @Test
   void removeOfADuplicateBoundColumnWithoutAnIndexIsRefusedAndListsTheOccurrences() {
      CrosstabBindingModel model = drillCrosstab();

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.removeField(model, "rows", "D"));

      assertTrue(thrown.getMessage().contains("[0] D year"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("[1] D quarter"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("'index'"), thrown.getMessage());
      assertEquals(3, model.getRows().size(), "a refused remove must leave the shelf alone");

      IllegalArgumentException agg = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.removeField(model, "aggregates", "Sales"));
      assertTrue(agg.getMessage().contains("[0] Sales Sum"), agg.getMessage());
      assertEquals(3, model.getAggregates().size());
   }

   @Test
   void removeWithAnIndexRemovesOnlyThatOccurrence() throws Exception {
      CrosstabBindingModel model = drillCrosstab();

      TableBindingMutator.removeField(model, "rows", "D", 1, null, null, null);
      assertEquals(2, model.getRows().size());
      assertEquals("D", model.getRows().get(0).getName());
      assertEquals("R", model.getRows().get(1).getName());
      assertEquals("year", levelAt(model, "rows", 0), "the year occurrence must be the one that stays");

      TableBindingMutator.removeField(model, "aggregates", "Sales", 0, null, null, null);
      assertEquals(2, model.getAggregates().size());
      assertEquals("Average", model.getAggregates().get(0).getFormula());
   }

   @Test
   void removeWithAnOutOfRangeIndexNamesTheField() {
      CrosstabBindingModel model = drillCrosstab();

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.removeField(model, "rows", "D", 2, null, null, null));
      assertTrue(thrown.getMessage().contains("'index' 2"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("[0] D"), thrown.getMessage());
      assertEquals(3, model.getRows().size());
   }

   @Test
   void removeOfASingleOccurrenceNeedsNoIndex() {
      CrosstabBindingModel model = drillCrosstab();

      TableBindingMutator.removeField(model, "rows", "R");
      assertEquals(2, model.getRows().size());
   }

   @Test
   void moveOfADuplicateBoundColumnWithoutAFromIndexIsRefused() {
      CrosstabBindingModel model = drillCrosstab();

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.moveField(model, "rows", "cols", "D", null));

      assertTrue(thrown.getMessage().contains("'fromIndex'"), thrown.getMessage());
      assertEquals(3, model.getRows().size());
      assertTrue(model.getCols().isEmpty());
   }

   @Test
   void moveWithAFromIndexMovesOnlyThatOccurrenceAndKeepsTheOther() throws Exception {
      CrosstabBindingModel model = drillCrosstab();

      TableBindingMutator.moveField(model, "rows", "cols", "D", 1, null, null, null, null);

      assertEquals(2, model.getRows().size(), "the other D and R stay on rows");
      assertEquals("year", levelAt(model, "rows", 0));
      assertEquals(1, model.getCols().size());
      assertEquals("quarter", levelAt(model, "cols", 0), "the quarter occurrence is the one that moved");
   }

   @Test
   void moveOfOneAggregateOccurrenceKeepsTheOtherFormula() throws Exception {
      CrosstabBindingModel model = drillCrosstab();

      TableBindingMutator.moveField(model, "aggregates", "rows", "Sales", 1, null, null, null,
                                    null);

      assertEquals(2, model.getAggregates().size());
      assertEquals("Sum", model.getAggregates().get(0).getFormula());
      assertEquals("Q", model.getAggregates().get(1).getName());
      assertEquals("Sales", model.getRows().get(3).getName());
   }

   @Test
   void movingWithinTheSameShelfIsRefusedInsteadOfDuplicatingTheField() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details",
                                   List.of(dim("Zip"), dim("Region"), dim("State")));

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.moveField(model, "details", "details", "Zip", 2));

      assertTrue(thrown.getMessage().contains("details"), thrown.getMessage());
      assertEquals(3, model.getDetails().size());
   }

   @Test
   void crossShelfMoveCarriesSuppressGroupTotalToTheDestinationKey() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("A"), dim("B"), dim("R")));
      TableBindingMutator.setShelf(model, "cols", List.of(dim("C")));
      model.getSuppressGroupTotal().put("R:rows2", Boolean.TRUE);
      model.getSuppressGroupTotal().put("C:cols0", Boolean.FALSE);

      TableBindingMutator.moveField(model, "rows", "cols", "R", 0);

      assertEquals(Map.of("R:cols0", Boolean.TRUE, "C:cols1", Boolean.FALSE),
                   model.getSuppressGroupTotal(),
                   "R's suppression follows it to cols0 and C's shifts to cols1");

      TableBindingMutator.moveField(model, "cols", "rows", "R", 1);

      assertEquals(Map.of("R:rows1", Boolean.TRUE, "C:cols0", Boolean.FALSE),
                   model.getSuppressGroupTotal());
   }

   @Test
   void crossShelfMoveOfAnUnsuppressedFieldDoesNotInheritAStaleDestinationEntry() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("A"), dim("B")));
      TableBindingMutator.setShelf(model, "cols", List.of(dim("C")));
      model.getSuppressGroupTotal().put("C:cols0", Boolean.TRUE);

      TableBindingMutator.moveField(model, "rows", "cols", "A", 0);

      assertEquals(Map.of("C:cols1", Boolean.TRUE), model.getSuppressGroupTotal(),
                   "the suppression belongs to C, not to the field now sitting at cols0");
   }

   @Test
   void crossShelfMoveKeepsSortRankingAndOthersOfTheMovedDimension() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("A"), dim("R")));
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Q", "Sum")));
      TableBindingMutator.setRanking(model, "rows", "R", null,
         new DimensionSortRanking.Ranking("top", 3, "Q", true));
      TableBindingMutator.setSort(model, "rows", "R", null,
                                  new DimensionSortRanking.Sort("desc", null, null));

      TableBindingMutator.moveField(model, "rows", "cols", "R", null);
      TableBindingMutator.moveField(model, "cols", "rows", "R", 0);

      BDimensionRefModel r = model.getRows().get(0);
      assertEquals("R", r.getName());
      assertEquals(XConstants.SORT_DESC, r.getOrder());
      assertEquals("3", r.getRankingN());
      assertTrue(r.isGroupOthers());
   }

   // ── bug #77025 R1/R2/R3: identity carry across reorder/insert/remove, one column once ──

   private static CrosstabBindingModel rankedStateCity(String shelf) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Total", "Sum")));
      TableBindingMutator.setShelf(model, shelf, List.of(dim("State"), dim("City")));
      TableBindingMutator.setSort(model, shelf, "State", null,
         new DimensionSortRanking.Sort("value_desc", "Sum(Total)", null));
      TableBindingMutator.setRanking(model, shelf, "State", null,
         new DimensionSortRanking.Ranking("top", 3, "Sum(Total)", true));
      TableBindingMutator.setSort(model, shelf, "City", null,
         new DimensionSortRanking.Sort("desc", null, null));
      return model;
   }

   private static List<BDimensionRefModel> shelfOf(CrosstabBindingModel model, String shelf) {
      return "rows".equals(shelf) ? model.getRows() : model.getCols();
   }

   private static void assertStateKept(BDimensionRefModel state) {
      assertEquals(XConstants.SORT_VALUE_DESC, state.getOrder());
      assertEquals("Sum(Total)", state.getSortByCol());
      assertEquals("3", state.getRankingN());
      assertEquals("Sum(Total)", state.getRankingCol());
      assertTrue(state.isGroupOthers());
   }

   @Test
   void reorderingTheShelfWholesaleKeepsEachDimensionsSortAndRanking() {
      for(String shelf : List.of("rows", "cols")) {
         CrosstabBindingModel model = rankedStateCity(shelf);

         TableBindingMutator.setShelf(model, shelf, List.of(dim("City"), dim("State")));

         List<BDimensionRefModel> out = shelfOf(model, shelf);
         assertEquals("City", out.get(0).getColumnValue(), shelf);
         assertEquals(XConstants.SORT_DESC, out.get(0).getOrder(), shelf);
         assertEquals("State", out.get(1).getColumnValue(), shelf);
         assertStateKept(out.get(1));
      }
   }

   @Test
   void insertingAFieldAheadViaReplaceKeepsTheLaterDimensionsState() {
      for(String shelf : List.of("rows", "cols")) {
         CrosstabBindingModel model = rankedStateCity(shelf);

         TableBindingMutator.setShelf(model, shelf,
            List.of(dim("Region"), dim("State"), dim("City")));

         List<BDimensionRefModel> out = shelfOf(model, shelf);
         assertEquals(XConstants.SORT_ASC, out.get(0).getOrder(), shelf);
         assertStateKept(out.get(1));
         assertEquals(XConstants.SORT_DESC, out.get(2).getOrder(), shelf);
      }
   }

   @Test
   void aDifferentColumnAtTheSamePositionStillGetsAFreshRef() {
      CrosstabBindingModel model = rankedStateCity("rows");

      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region"), dim("City")));

      BDimensionRefModel region = model.getRows().get(0);
      assertEquals(XConstants.SORT_ASC, region.getOrder());
      assertNull(region.getSortByCol());
      assertNull(region.getRankingCol());
      assertEquals(XConstants.SORT_DESC, model.getRows().get(1).getOrder());
   }

   @Test
   void theSameColumnAtADifferentDateLevelStillGetsAFreshRef() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows",
         List.of(new FieldRef("Order Date", "dimension", null, "year", null)));
      TableBindingMutator.setSort(model, "rows", "Order Date", null,
         new DimensionSortRanking.Sort("desc", null, null));

      TableBindingMutator.setShelf(model, "rows",
         List.of(new FieldRef("Order Date", "dimension", null, "month", null)));

      assertEquals(XConstants.SORT_ASC, model.getRows().get(0).getOrder());
   }

   private static CrosstabBindingModel twoTotals() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates", List.of(
         measureWithCalc("Total", "Sum", runningTotal("Sum")), measure("Total", "Sum")));
      BAggregateRefModel first = model.getAggregates().get(0);
      first.setCaption("ALPHA");
      first.setNumValue("7");
      return model;
   }

   private static void assertFirstTotalState(BAggregateRefModel agg) {
      assertNotNull(agg.getCalculateInfo());
      assertEquals("ALPHA", agg.getCaption());
      assertEquals("7", agg.getNumValue());
   }

   private static void assertBlank(BAggregateRefModel agg) {
      assertNull(agg.getCalculateInfo());
      assertNull(agg.getCaption());
      assertNull(agg.getNumValue());
   }

   @Test
   void removingTheFirstOfTwoIdenticalMeasuresDoesNotHandItsStateToTheSurvivor() throws Exception {
      CrosstabBindingModel model = twoTotals();

      TableBindingMutator.removeField(model, "aggregates", "Total", 0, null, null, null);

      assertEquals(1, model.getAggregates().size());
      assertBlank(model.getAggregates().get(0));
   }

   @Test
   void removingTheSecondOfTwoIdenticalMeasuresKeepsTheFirstsState() throws Exception {
      CrosstabBindingModel model = twoTotals();

      TableBindingMutator.removeField(model, "aggregates", "Total", 1, null, null, null);

      assertEquals(1, model.getAggregates().size());
      assertFirstTotalState(model.getAggregates().get(0));
   }

   @Test
   void insertingAPlainMeasureAheadOfAnIdenticalOneDoesNotInheritItsState() throws Exception {
      CrosstabBindingModel model = twoTotals();

      TableBindingMutator.addField(model, "aggregates", measure("Total", "Sum"), 0, null, null,
                                   null);

      assertEquals(3, model.getAggregates().size());
      assertBlank(model.getAggregates().get(0));
      assertFirstTotalState(model.getAggregates().get(1));
      assertBlank(model.getAggregates().get(2));
   }

   @Test
   void movingAMeasureOffAggregatesKeepsTheSiblingsOwnState() throws Exception {
      CrosstabBindingModel model = twoTotals();

      TableBindingMutator.moveField(model, "aggregates", "rows", "Total", 0, null, null, null,
                                    null);

      assertEquals(1, model.getAggregates().size());
      assertBlank(model.getAggregates().get(0));
   }

   @Test
   void movingAMeasureOntoAggregatesAheadOfAnIdenticalOneDoesNotInheritItsState()
      throws Exception
   {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measureWithCalc("Total", "Sum", runningTotal("Sum"))));
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Total")));

      TableBindingMutator.moveField(model, "rows", "aggregates", "Total", null, 0, null, null,
                                    null);

      assertEquals(2, model.getAggregates().size());
      assertNull(model.getAggregates().get(0).getCalculateInfo());
      assertNotNull(model.getAggregates().get(1).getCalculateInfo());
   }

   @Test
   void aWholesaleAggregatesReplaceKeepsTheOccurrenceRule() {
      CrosstabBindingModel model = twoTotals();

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Total", "Sum")));

      assertFirstTotalState(model.getAggregates().get(0));
   }

   @Test
   void movingTheReferencedAggregateAwayClearsTheRankingThatNamedIt() throws Exception {
      CrosstabBindingModel model = rankedStateCity("rows");

      TableBindingMutator.moveField(model, "aggregates", "cols", "Total", null, null, null,
                                    null, null);

      BDimensionRefModel state = model.getRows().get(0);
      assertEquals(XConstants.SORT_ASC, state.getOrder());
      assertNull(state.getSortByCol());
      assertNull(state.getRankingCol());
   }

   @Test
   void removingOneOfTwoIdenticalAggregatesKeepsTheRankingThatNamedBoth() throws Exception {
      CrosstabBindingModel model = rankedStateCity("rows");
      TableBindingMutator.addField(model, "aggregates", measure("Total", "Sum"), null);

      TableBindingMutator.removeField(model, "aggregates", "Total", 1, null, null, null);
      // aggregates() above ran without a runtime context, as the fixtures here do

      assertStateKept(model.getRows().get(0));
   }

   @Test
   void aTableDetailsShelfRefusesTheSameColumnTwiceOnReplace() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details", List.of(dim("Region")));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.setShelf(model, "details",
            List.of(dim("State"), dim("Region"), dim("State"))));

      assertTrue(e.getMessage().contains("'State'"), e.getMessage());
      assertTrue(e.getMessage().contains("position 0"), e.getMessage());
      assertTrue(e.getMessage().contains("set_table_fields"), e.getMessage());
      assertEquals(1, model.getDetails().size(), "a refused write leaves the shelf unchanged");
   }

   @Test
   void aTableDetailsShelfRefusesTheSameColumnTwiceCaseInsensitively() {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(model, "details", List.of(dim("State"), dim("Region")));

      assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.addField(model, "details", dim("state"), 0));
      assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.addField(model, "details", dim("State"), null));
      assertEquals(2, model.getDetails().size());
   }

   @Test
   void aWholesaleAggregatesReplaceThatDropsTheReferencedMeasureResetsLikeARemove() {
      CrosstabBindingModel model = rankedStateCity("rows");
      TableBindingMutator.drainNotes();

      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Profit", "Sum")));

      BDimensionRefModel state = model.getRows().get(0);
      assertEquals(XConstants.SORT_ASC, state.getOrder());
      assertNull(state.getSortByCol());
      assertNull(state.getRankingCol());
   }

   @Test
   void aResetIsReportedNamingTheDimensionAndTheMeasure() throws Exception {
      CrosstabBindingModel model = rankedStateCity("rows");
      TableBindingMutator.drainNotes();

      TableBindingMutator.removeField(model, "aggregates", "Total", null, null, null, null);

      List<String> notes = TableBindingMutator.drainNotes();
      assertEquals(2, notes.size(), notes.toString());
      assertTrue(notes.stream().allMatch(n -> n.startsWith(TableBindingMutator.NOTE_PREFIX)));
      assertTrue(notes.get(0).contains("'State' on rows") && notes.get(0).contains("sort"),
                 notes.get(0));
      assertTrue(notes.get(1).contains("'State' on rows") && notes.get(1).contains("ranking"),
                 notes.get(1));
      assertTrue(TableBindingMutator.drainNotes().isEmpty(), "drain clears");
   }

   @Test
   void aWriteThatKeepsTheReferencedMeasureReportsNothing() {
      CrosstabBindingModel model = rankedStateCity("rows");
      TableBindingMutator.drainNotes();

      TableBindingMutator.setShelf(model, "aggregates",
         List.of(measure("Total", "Sum"), measure("Profit", "Sum")));

      assertTrue(TableBindingMutator.drainNotes().isEmpty());
      assertStateKept(model.getRows().get(0));
   }

   // ── timeSeries vs a non-ascending sort on a crosstab date dimension (bug #78751 VTB-054) ──

   private static FieldRef monthTs(Boolean timeSeries) {
      return new FieldRef("Order Date", "dimension", null, "month", null, null, null, null, null,
                          null, null, null, timeSeries);
   }

   private static CrosstabBindingModel crosstabOf(String shelf, Boolean timeSeries) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Sales", "Sum")));
      TableBindingMutator.setShelf(model, shelf, List.of(monthTs(timeSeries)));
      return model;
   }

   private static List<DimensionSortRanking.Sort> overridingSorts() {
      return List.of(new DimensionSortRanking.Sort("desc", null, null),
                     new DimensionSortRanking.Sort("value_asc", "Sum(Sales)", null),
                     new DimensionSortRanking.Sort("value_desc", "Sum(Sales)", null),
                     new DimensionSortRanking.Sort("manual", null, List.of("a", "b")));
   }

   private static BDimensionRefModel first(CrosstabBindingModel model, String shelf) {
      return ("rows".equals(shelf) ? model.getRows() : model.getCols()).get(0);
   }

   @Test
   void sortOverATimeSeriesDateDimensionIsRefusedOnRowsAndCols() {
      for(String shelf : List.of("rows", "cols")) {
         for(DimensionSortRanking.Sort sort : overridingSorts()) {
            CrosstabBindingModel model = crosstabOf(shelf, true);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
               () -> TableBindingMutator.setSort(model, shelf, "Order Date", null, sort),
               shelf + " " + sort.direction());
            assertTrue(e.getMessage().contains("timeSeries"), e.getMessage());
            assertEquals(XConstants.SORT_ASC, first(model, shelf).getOrder(),
                         "the refused sort must not be stored");
         }
      }
   }

   @Test
   void ascSortOverATimeSeriesDateDimensionIsAccepted() {
      CrosstabBindingModel model = crosstabOf("rows", true);
      assertDoesNotThrow(() -> TableBindingMutator.setSort(model, "rows", "Order Date", null,
         new DimensionSortRanking.Sort("asc", null, null)));
   }

   @Test
   void timeSeriesOverAStoredNonAscendingSortIsRefusedOnRowsAndCols() {
      for(String shelf : List.of("rows", "cols")) {
         for(DimensionSortRanking.Sort sort : overridingSorts()) {
            CrosstabBindingModel model = crosstabOf(shelf, false);
            TableBindingMutator.setSort(model, shelf, "Order Date", null, sort);
            int order = first(model, shelf).getOrder();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
               () -> TableBindingMutator.setShelf(model, shelf, List.of(monthTs(true))),
               shelf + " " + sort.direction());
            assertTrue(e.getMessage().contains("timeSeries") && e.getMessage().contains("asc"),
                       e.getMessage());
            assertFalse(first(model, shelf).isTimeSeries());
            assertEquals(order, first(model, shelf).getOrder());
         }
      }
   }

   @Test
   void timeSeriesOverAnAscendingOrUnsortedDimensionIsAccepted() {
      CrosstabBindingModel model = crosstabOf("rows", false);
      TableBindingMutator.setShelf(model, "rows", List.of(monthTs(true)));
      assertTrue(first(model, "rows").isTimeSeries());
   }

   @Test
   void timeSeriesFalseOrOmittedOverANonAscendingSortIsAccepted() {
      CrosstabBindingModel model = crosstabOf("rows", false);
      TableBindingMutator.setSort(model, "rows", "Order Date", null,
                                  new DimensionSortRanking.Sort("desc", null, null));
      assertDoesNotThrow(() -> TableBindingMutator.setShelf(model, "rows", List.of(monthTs(false))));
      assertDoesNotThrow(() -> TableBindingMutator.setShelf(model, "rows", List.of(monthTs(null))));
   }

   /** A stored ts:true plus a dormant sort (older assets) stays writable: the echo is tolerated. */
   @Test
   void echoOfAStoredTimeSeriesFlagIsNotRefused() {
      CrosstabBindingModel model = crosstabOf("rows", false);
      TableBindingMutator.setSort(model, "rows", "Order Date", null,
                                  new DimensionSortRanking.Sort("desc", null, null));
      first(model, "rows").setTimeSeries(true);

      assertDoesNotThrow(() -> TableBindingMutator.setShelf(model, "rows", List.of(monthTs(true))));
      assertDoesNotThrow(() -> TableBindingMutator.setShelf(model, "rows", List.of(monthTs(null))));
   }

   @Test
   void aNonDateColumnIsNotRefused() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      BindingModel.SourceTableColumn column = new BindingModel.SourceTableColumn();
      column.setName("Order Date");
      column.setDataType("string");
      BindingModel.SourceTable table = new BindingModel.SourceTable();
      table.setColumns(List.of(column));
      model.setTables(List.of(table));
      TableBindingMutator.setShelf(model, "rows", List.of(monthTs(false)));
      TableBindingMutator.setSort(model, "rows", "Order Date", null,
                                  new DimensionSortRanking.Sort("desc", null, null));

      assertDoesNotThrow(() -> TableBindingMutator.setShelf(model, "rows", List.of(monthTs(true))));
   }
}
