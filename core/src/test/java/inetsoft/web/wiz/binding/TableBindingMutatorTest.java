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

import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

   @Test
   void setsTableGroupsAndAggregates() {
      TableBindingModel model = new TableBindingModel();

      TableBindingMutator.setShelf(model, "groups", List.of(dim("Region")));
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Sales", "Sum")));

      assertEquals(1, model.getGroups().size());
      assertEquals(1, model.getAggregates().size());
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
      assertTrue(thrown.getMessage().contains("groups"),
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

   @Test
   void movingAMeasureOntoADimensionShelfIsRefusedAndLeavesTheSourceIntact() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Sales", "Sum")));

      assertThrows(IllegalArgumentException.class,
                   () -> TableBindingMutator.moveField(model, "aggregates", "rows", "Sales",
                                                       null));
      assertEquals(1, model.getAggregates().size());
   }

   // ── preservation ──────────────────────────────────────────────────────────

   @Test
   void aShelfWriteLeavesTheOtherShelvesAndOptionsUntouched() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "cols", List.of(dim("Year")));
      Object colsBefore = model.getCols();
      model.getSuppressGroupTotal().put("Year", Boolean.TRUE);

      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      assertSame(colsBefore, model.getCols());
      assertEquals(Boolean.TRUE, model.getSuppressGroupTotal().get("Year"),
                   "suppressGroupTotal is keyed by field name and must survive an unrelated write");
   }

   /**
    * {@code suppressGroupTotal} is a Hashtable keyed by field name that nothing prunes, so a
    * removed field leaves an entry behind. It grows unboundedly across edits, and can
    * resurrect suppression if the name is ever reused. See spec 2d risk 1.
    */
   @Test
   void prunesSuppressGroupTotalEntriesForFieldsNoLongerBound() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region"), dim("Year")));
      model.getSuppressGroupTotal().put("Region", Boolean.TRUE);
      model.getSuppressGroupTotal().put("Year", Boolean.TRUE);

      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      assertEquals(Boolean.TRUE, model.getSuppressGroupTotal().get("Region"));
      assertNull(model.getSuppressGroupTotal().get("Year"),
                 "an orphaned suppression entry must be pruned, not left to resurrect later");
   }

   // ── sorting and ranking (2d Phase 2) ──────────────────────────────────────

   @Test
   void sortsADimensionOnTheRowsShelf() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      TableBindingMutator.setSort(model, "rows", "Region",
                                  new DimensionSortRanking.Sort("desc", null, null));

      assertEquals(inetsoft.uql.XConstants.SORT_DESC, model.getRows().get(0).getOrder());
   }

   @Test
   void ranksADimensionByAMeasureName() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      TableBindingMutator.setRanking(model, "rows", "Region",
                                     new DimensionSortRanking.Ranking("top", 5, "Sales", null));

      assertEquals("5", model.getRows().get(0).getRankingN());
      assertEquals("Sales", model.getRows().get(0).getRankingCol());
   }

   @Test
   void refusesToSortAColumnThatIsNotOnTheShelf() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setSort(model, "rows", "Nope",
                                           new DimensionSortRanking.Sort("asc", null, null)));

      assertTrue(thrown.getMessage().contains("Nope"));
      assertTrue(thrown.getMessage().contains("Region"));
   }

   @Test
   void refusesToSortTheAggregatesOrDetailsShelf() {
      CrosstabBindingModel crosstab = new CrosstabBindingModel();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setSort(crosstab, "aggregates", "Sales",
                                           new DimensionSortRanking.Sort("asc", null, null)));

      assertTrue(thrown.getMessage().contains("measures"));
   }

   @Test
   void describesTheSortsOnAShelf() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setRanking(model, "rows", "Region",
                                     new DimensionSortRanking.Ranking("top", 5, "Sales", null));

      Map<String, Object> described = TableBindingMutator.describeSorts(model, "rows");

      assertTrue(described.containsKey("Region"));
   }

   // ── column labels (2d Phase 2) ────────────────────────────────────────────

   @Test
   void setsAColumnLabelForABoundColumn() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      TableBindingMutator.setColumnLabels(model, Map.of("Region", "Sales Region"));

      assertEquals("Sales Region", model.getName2Labels().get("Region"));
   }

   /** A label for an unbound column would sit in name2Labels doing nothing. */
   @Test
   void refusesALabelForAColumnThatIsNotBound() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setColumnLabels(model, Map.of("Profit", "P")));

      assertTrue(thrown.getMessage().contains("never be shown"));
   }

   @Test
   void anEmptyLabelRemovesIt() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(dim("Region")));
      TableBindingMutator.setColumnLabels(model, Map.of("Region", "Sales Region"));

      TableBindingMutator.setColumnLabels(model, Map.of("Region", ""));

      assertFalse(model.getName2Labels().containsKey("Region"));
   }

   @Test
   void refusesAnEmptyLabelMap() {
      assertThrows(IllegalArgumentException.class,
                   () -> TableBindingMutator.setColumnLabels(new CrosstabBindingModel(),
                                                             Map.of()));
   }

   // ── options (2d Phase 3) ──────────────────────────────────────────────────

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

   @Test
   void setsTableOptions() {
      TableBindingModel model = new TableBindingModel();

      TableBindingMutator.setOptions(model, Map.of("grandTotal", true, "distinct", false));

      assertTrue(model.getOption().getGrandTotal());
      assertFalse(model.getOption().getDistinct());
   }

   /** An option belonging to the other object type would be accepted and do nothing. */
   @Test
   void refusesACrosstabOptionOnATable() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> TableBindingMutator.setOptions(new TableBindingModel(),
                                              Map.of("rowTotals", true)));

      assertTrue(thrown.getMessage().contains("rowTotals"));
      assertTrue(thrown.getMessage().contains("grandTotal"), "list what this type does take");
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
      assertTrue(String.valueOf(vocabulary.get("table")).contains("distinct"));
   }
}
