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

import inetsoft.report.CellBinding;
import inetsoft.report.GroupableCellBinding;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.web.binding.model.table.CellBindingInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core")
class CalcCellVocabularyTest {
   private static Map<String, Object> spec(Object... pairs) {
      Map<String, Object> spec = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         spec.put((String) pairs[i], pairs[i + 1]);
      }

      return spec;
   }

   // ── content ───────────────────────────────────────────────────────────────

   @Test
   void mapsTheContentTokensToCellBindingConstants() {
      assertEquals(CellBinding.BIND_TEXT, CalcCellVocabulary.content("text"));
      assertEquals(CellBinding.BIND_COLUMN, CalcCellVocabulary.content("column"));
      assertEquals(CellBinding.BIND_FORMULA, CalcCellVocabulary.content("formula"));
   }

   @Test
   void mapsTheGroupingTokens() {
      assertEquals(CellBinding.GROUP, CalcCellVocabulary.grouping("group"));
      assertEquals(CellBinding.DETAIL, CalcCellVocabulary.grouping("detail"));
      assertEquals(CellBinding.SUMMARY, CalcCellVocabulary.grouping("summary"));
   }

   @Test
   void mapsTheExpansionTokensIncludingTheShortSpellings() {
      assertEquals(GroupableCellBinding.EXPAND_NONE, CalcCellVocabulary.expand("none"));
      assertEquals(GroupableCellBinding.EXPAND_V, CalcCellVocabulary.expand("vertical"));
      assertEquals(GroupableCellBinding.EXPAND_V, CalcCellVocabulary.expand("v"));
      assertEquals(GroupableCellBinding.EXPAND_H, CalcCellVocabulary.expand("horizontal"));
      assertEquals(GroupableCellBinding.EXPAND_H, CalcCellVocabulary.expand("h"));
   }

   @Test
   void matchesTokensCaseInsensitively() {
      assertEquals(CellBinding.BIND_COLUMN, CalcCellVocabulary.content("  Column "));
      assertEquals(CellBinding.SUMMARY, CalcCellVocabulary.grouping("SUMMARY"));
   }

   // ── the naming hazard: five different `type` fields ───────────────────────

   /**
    * {@code type} means five unrelated things across this binding surface. Accepting it here
    * with any of those meanings is how a plausible wrong guess lands in a valid-looking field
    * and renders a silently wrong table.
    */
   @Test
   void refusesTypeAtTheCellLevelNamingTheCorrectKey() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec("type", "column")));

      assertTrue(thrown.getMessage().contains("type"));
      assertTrue(thrown.getMessage().contains("content"),
                 "the refusal must name the key that was meant");
   }

   @Test
   void refusesBtypeNamingGrouping() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec("btype", "group")));

      assertTrue(thrown.getMessage().contains("btype"));
      assertTrue(thrown.getMessage().contains("grouping"));
   }

   @Test
   void refusesExpansionNamingExpand() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec("expansion", "vertical")));

      assertTrue(thrown.getMessage().contains("expand"));
   }

   /**
    * `role` is excluded deliberately: it is the wrong key in the recorded fieldConfigs defect,
    * and giving it a new meaning inside the same plugin family would be its own trap.
    */
   @Test
   void refusesRoleRatherThanGivingItANewMeaning() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec("role", "group")));

      assertTrue(thrown.getMessage().contains("role"));
   }

   @Test
   void acceptsTheCanonicalKeys() {
      assertDoesNotThrow(() -> CalcCellVocabulary.validate(
         spec("content", "column", "grouping", "group", "expand", "vertical",
              "field", Map.of("column", "Region", "type", "dimension"))));
   }

   // ── integer constants never appear ────────────────────────────────────────

   @Test
   void refusesAnIntegerWhereATokenIsExpected() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class, () -> CalcCellVocabulary.content("2"));

      assertTrue(thrown.getMessage().contains("2"));
      assertTrue(thrown.getMessage().contains("column"),
                 "list the tokens rather than accepting the raw constant");
   }

   @Test
   void refusesAnUnknownToken() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class, () -> CalcCellVocabulary.grouping("aggregate"));

      assertTrue(thrown.getMessage().contains("aggregate"));
      assertTrue(thrown.getMessage().contains("summary"));
   }

   @Test
   void refusesAMissingToken() {
      assertThrows(IllegalArgumentException.class, () -> CalcCellVocabulary.content(null));
   }

   // ── the read direction ────────────────────────────────────────────────────

   @Test
   void describesACellBackInTokensNeverIntegers() {
      CellBindingInfo info = new CellBindingInfo();
      info.setType(CellBinding.BIND_COLUMN);
      info.setBtype(CellBinding.GROUP);
      info.setExpansion(GroupableCellBinding.EXPAND_V);
      info.setValue("Region");

      Map<String, Object> described = CalcCellVocabulary.describe(info);

      assertEquals("column", described.get("content"));
      assertEquals("group", described.get("grouping"));
      assertEquals("vertical", described.get("expand"));
      assertEquals("Region", described.get("value"));
      assertFalse(described.containsKey("type"), "the ambiguous keys must not come back out");
      assertFalse(described.containsKey("btype"));
   }

   @Test
   void describesNullAsNull() {
      assertNull(CalcCellVocabulary.describe(null));
   }

   @Test
   void describesAnUnrecognizedConstantWithoutInventingAToken() {
      CellBindingInfo info = new CellBindingInfo();
      info.setType(99);

      Map<String, Object> described = CalcCellVocabulary.describe(info);

      assertEquals("unknown(99)", described.get("content"),
                   "reporting the raw constant beats guessing a token that would read as fact");
   }

   // ── binding completeness ──────────────────────────────────────────────────

   /**
    * A cell bound to nothing renders blank, which reads as a data problem rather than a
    * binding error — so an incomplete binding is refused at the boundary.
    */
   @Test
   void refusesColumnContentWithNoField() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec("content", "column")));

      assertTrue(thrown.getMessage().contains("field"));
   }

   @Test
   void refusesFormulaContentWithNoFormula() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec("content", "formula")));

      assertTrue(thrown.getMessage().contains("formula"));
   }

   @Test
   void refusesTextContentWithNoValue() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec("content", "text")));

      assertTrue(thrown.getMessage().contains("value"));
   }

   @Test
   void acceptsTextContentWithAValue() {
      assertDoesNotThrow(
         () -> CalcCellVocabulary.validate(spec("content", "text", "value", "Total")));
   }

   /**
    * get_cell_binding echoes a formula cell's script back under 'value' (see
    * CalcCellVocabulary.describe), so a caller that feeds that read straight back into
    * set_cell_binding supplies 'value' rather than 'formula'. That must round-trip without
    * the caller renaming the key.
    */
   @Test
   void acceptsValueAsAnAliasForFormulaContent() {
      assertDoesNotThrow(
         () -> CalcCellVocabulary.validate(spec("content", "formula", "value", "Sum(Sales)")));
   }

   // ── sort.rankBy / topn.rankBy shape ──────────────────────────────────────────

   @Test
   void mapsValueAscAndValueDescSortTokens() {
      assertEquals(XConstants.SORT_VALUE_ASC, CalcCellVocabulary.sortDirection("value_asc"));
      assertEquals(XConstants.SORT_VALUE_DESC, CalcCellVocabulary.sortDirection("value_desc"));
   }

   @Test
   void refusesValueAscSortWithNoRankBy() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec(
            "content", "text", "value", "x",
            "sort", spec("direction", "value_asc"))));

      assertTrue(thrown.getMessage().contains("rankBy"));
   }

   @Test
   void acceptsValueDescSortWithRankBy() {
      assertDoesNotThrow(() -> CalcCellVocabulary.validate(spec(
         "content", "text", "value", "x",
         "sort", spec("direction", "value_desc",
                      "rankBy", spec("column", "Revenue", "formula", "Sum")))));
   }

   @Test
   void refusesTopnRankByMissingColumn() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec(
            "content", "text", "value", "x",
            "topn", spec("mode", "top", "rankBy", spec("formula", "Sum")))));

      assertTrue(thrown.getMessage().contains("column"));
   }

   // ── field.namedGroup inline ('Customize') shape ──────────────────────────────
   //
   // Checked eagerly in validate() (before sessions.mutate opens a checkpoint the caller then
   // has to undo), same as sort/topn -- the actual condition-list-building for a valid shape is
   // CalcTableService.applyInlineNamedGroup's job, not this stateless vocabulary's.

   @Test
   void refusesInlineNamedGroupWithNoGroups() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec(
            "content", "column", "grouping", "group", "expand", "vertical",
            "field", spec("column", "Region", "type", "dimension",
                          "namedGroup", spec("groups", List.of())))));

      assertTrue(thrown.getMessage().contains("groups"));
   }

   @Test
   void refusesInlineNamedGroupGroupWithBlankName() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec(
            "content", "column", "grouping", "group", "expand", "vertical",
            "field", spec("column", "Region", "type", "dimension",
                          "namedGroup", spec("groups", List.of(
                             spec("name", " ",
                                  "conditions", List.of(spec("operator", "one_of",
                                                             "values", List.of("CA"))))))))));

      assertTrue(thrown.getMessage().contains("name"));
   }

   @Test
   void refusesInlineNamedGroupGroupWithNoConditions() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec(
            "content", "column", "grouping", "group", "expand", "vertical",
            "field", spec("column", "Region", "type", "dimension",
                          "namedGroup", spec("groups", List.of(
                             spec("name", "West", "conditions", List.of())))))));

      assertTrue(thrown.getMessage().contains("condition"));
   }

   @Test
   void refusesInlineNamedGroupConditionThatIsNotAnObject() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec(
            "content", "column", "grouping", "group", "expand", "vertical",
            "field", spec("column", "Region", "type", "dimension",
                          "namedGroup", spec("groups", List.of(
                             spec("name", "West", "conditions", List.of("not-an-object"))))))));

      assertTrue(thrown.getMessage().contains("object"));
   }

   @Test
   void refusesInlineNamedGroupConditionNamingADifferentField() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec(
            "content", "column", "grouping", "group", "expand", "vertical",
            "field", spec("column", "Region", "type", "dimension",
                          "namedGroup", spec("groups", List.of(
                             spec("name", "West",
                                  "conditions", List.of(spec("field", "State",
                                                             "operator", "one_of",
                                                             "values", List.of("CA"))))))))));

      assertTrue(thrown.getMessage().contains("Region"));
   }

   @Test
   void acceptsAWellFormedInlineNamedGroup() {
      assertDoesNotThrow(() -> CalcCellVocabulary.validate(spec(
         "content", "column", "grouping", "group", "expand", "vertical",
         "field", spec("column", "Region", "type", "dimension",
                       "namedGroup", spec("groups", List.of(
                          spec("name", "West",
                               "conditions", List.of(spec("operator", "one_of",
                                                          "values", List.of("CA"))))),
                                          "others", "leave")))));
   }

   @Test
   void ignoresByNameNamedGroupStringsEntirely() {
      // The by-name form ("a predefined group's name") needs runtime session state to resolve --
      // not this stateless vocabulary's job. A plain string must pass through untouched here.
      assertDoesNotThrow(() -> CalcCellVocabulary.validate(spec(
         "content", "column", "grouping", "group", "expand", "vertical",
         "field", spec("column", "Region", "type", "dimension", "namedGroup", "Coastal"))));
   }

   // ── describe(info, aggregates): resolving sortByCol/sumCol back to {column, formula} ────────

   @Test
   void describeResolvesSortByColBackToRankBy() {
      CellBindingInfo info = new CellBindingInfo();
      info.setType(CellBinding.BIND_COLUMN);
      info.setBtype(CellBinding.GROUP);
      info.getOrder().setType(XConstants.SORT_VALUE_DESC);
      info.getOrder().setSortCol(1);

      AggregateRef sum = mock(AggregateRef.class);
      when(sum.getDataRef()).thenReturn(new AttributeRef(null, "PAID"));
      when(sum.getFormulaName()).thenReturn("Sum");
      AggregateRef avg = mock(AggregateRef.class);
      when(avg.getDataRef()).thenReturn(new AttributeRef(null, "PAID"));
      when(avg.getFormulaName()).thenReturn("Average");

      Map<String, Object> described =
         CalcCellVocabulary.describe(info, new AggregateRef[]{ sum, avg });
      @SuppressWarnings("unchecked")
      Map<String, Object> sort = (Map<String, Object>) described.get("sort");
      @SuppressWarnings("unchecked")
      Map<String, Object> rankBy = (Map<String, Object>) sort.get("rankBy");

      assertEquals("value_desc", sort.get("direction"));
      assertEquals("PAID", rankBy.get("column"));
      assertEquals("Average", rankBy.get("formula"));
   }

   @Test
   void describeOmitsRankByWhenAggregatesAreUnavailable() {
      CellBindingInfo info = new CellBindingInfo();
      info.setType(CellBinding.BIND_COLUMN);
      info.setBtype(CellBinding.GROUP);
      info.getOrder().setType(XConstants.SORT_VALUE_ASC);
      info.getOrder().setSortCol(0);

      // The single-argument overload -- no aggregate context available -- must not report a
      // bare index under 'rankBy' (exactly the brittle-under-reorder shape this vocabulary
      // avoids everywhere else); it omits the key instead.
      @SuppressWarnings("unchecked")
      Map<String, Object> sort = (Map<String, Object>) CalcCellVocabulary.describe(info).get("sort");

      assertNull(sort.get("rankBy"));
   }
}
