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
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.GroupCondition;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.wiz.viewsheet.ConditionVocabulary;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

   // -- bug #76814: the named group a group cell carries is read back in the write vocabulary --

   private static CellBindingInfo groupCell(NamedGroupInfoModel info, boolean others) {
      CellBindingInfo cell = new CellBindingInfo();
      cell.setType(CellBinding.BIND_COLUMN);
      cell.setBtype(CellBinding.GROUP);
      cell.setValue("Region");
      cell.getOrder().setInfo(info);
      cell.getOrder().setOthers(others);
      return cell;
   }

   private static ConditionExpression expression(String name, ConditionVocabulary.Clause... clauses)
   {
      DataRefModel region = mock(DataRefModel.class);
      when(region.getName()).thenReturn("Region");
      ConditionExpression expr = new ConditionExpression();
      expr.setName(name);
      expr.setList(ConditionVocabulary.toConditionList(List.of(clauses), new DataRefModel[]{ region }));
      return expr;
   }

   @Test
   @SuppressWarnings("unchecked")
   void describesAnInlineNamedGroupInTheVocabularySetCellBindingAccepts() {
      NamedGroupInfoModel info = new NamedGroupInfoModel();
      info.setType(XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO);
      info.setName("Custom");
      info.addCondition(expression("West", new ConditionVocabulary.Clause(
         "Region", "one_of", List.of("CA", "OR"), "or", false, false, 0),
         new ConditionVocabulary.Clause(
            "Region", "equals", List.of("WA"), null, false, false, 0)));

      Map<String, Object> described = CalcCellVocabulary.describe(groupCell(info, false));

      Map<String, Object> namedGroup = (Map<String, Object>) described.get("namedGroup");
      assertEquals("leave", namedGroup.get("others"));
      List<Map<String, Object>> groups = (List<Map<String, Object>>) namedGroup.get("groups");
      assertEquals(1, groups.size());
      assertEquals("West", groups.get(0).get("name"));
      List<Map<String, Object>> conditions =
         (List<Map<String, Object>>) groups.get(0).get("conditions");
      assertEquals(2, conditions.size());
      assertEquals("one_of", conditions.get(0).get("operator"));
      assertEquals(List.of("CA", "OR"), conditions.get(0).get("values"));
      assertEquals("or", conditions.get(0).get("junction"));
      assertEquals(Boolean.FALSE, conditions.get(0).get("negated"));
      assertEquals("equals", conditions.get(1).get("operator"));
      assertFalse(conditions.get(1).containsKey("junction"), "the last condition has none");
      assertFalse(conditions.get(0).containsKey("field"), "the field is always the cell's column");
      assertFalse(conditions.get(0).containsKey("level"));
   }

   @Test
   void describesARepositoryNamedGroupByItsName() {
      NamedGroupInfoModel info = new NamedGroupInfoModel();
      info.setType(XNamedGroupInfo.ASSET_NAMEDGROUP_INFO);
      info.setName("Tiers");

      assertEquals("Tiers", CalcCellVocabulary.describe(groupCell(info, true)).get("namedGroup"));
   }

   @Test
   @SuppressWarnings("unchecked")
   void describesAUiBuiltSimpleNamedGroupAsOneOfConditions() {
      NamedGroupInfoModel info = new NamedGroupInfoModel();
      info.setType(XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO);
      info.setName("Custom");
      info.addGroup(new GroupCondition("Coastal", List.of("CA", "OR")));

      Map<String, Object> namedGroup = (Map<String, Object>)
         CalcCellVocabulary.describe(groupCell(info, true)).get("namedGroup");

      assertEquals("group", namedGroup.get("others"));
      List<Map<String, Object>> groups = (List<Map<String, Object>>) namedGroup.get("groups");
      assertEquals("Coastal", groups.get(0).get("name"));
      List<Map<String, Object>> conditions =
         (List<Map<String, Object>>) groups.get(0).get("conditions");
      assertEquals("one_of", conditions.get(0).get("operator"));
      assertEquals(List.of("CA", "OR"), conditions.get(0).get("values"));
   }

   @Test
   void omitsNamedGroupWhenTheCellHasNone() {
      assertFalse(CalcCellVocabulary.describe(groupCell(new NamedGroupInfoModel(), true))
                     .containsKey("namedGroup"));
      assertFalse(CalcCellVocabulary.describe(groupCell(null, true)).containsKey("namedGroup"));
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

   // ── unknown keys inside field / sort / topn / rankBy ─────────────────────────

   private static Map<String, Object> rankBy() {
      return spec("column", "Revenue", "formula", "Sum");
   }

   private static void assertRefusesNestedKey(String path, String key, Map<String, Object> cell) {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> CalcCellVocabulary.validate(cell));
      assertTrue(e.getMessage().contains("'" + path + "' carries '" + key + "'"), e.getMessage());
      assertTrue(e.getMessage().contains("Recognized keys:"), e.getMessage());
      assertTrue(e.getMessage().contains("Nothing was changed."), e.getMessage());
   }

   @Test
   void refusesAnUnknownKeyInsideField() {
      assertRefusesNestedKey("field", "dateLevle", spec(
         "content", "column", "field", spec("column", "Region", "type", "string", "dateLevle", "Month")));
   }

   @Test
   void refusesAnUnknownKeyInsideSort() {
      assertRefusesNestedKey("sort", "manualOrdr", spec(
         "content", "text", "value", "x", "sort", spec("direction", "asc", "manualOrdr", List.of("a"))));
   }

   @Test
   void refusesAnUnknownKeyInsideTopn() {
      assertRefusesNestedKey("topn", "count", spec(
         "content", "text", "value", "x", "topn", spec("mode", "top", "count", 5)));
   }

   @Test
   void refusesAnUnknownKeyInsideSortRankBy() {
      Map<String, Object> rank = rankBy();
      rank.put("bogus", 1);
      assertRefusesNestedKey("sort.rankBy", "bogus", spec(
         "content", "text", "value", "x", "sort", spec("direction", "value_desc", "rankBy", rank)));
   }

   @Test
   void refusesAnUnknownKeyInsideTopnRankBy() {
      Map<String, Object> rank = rankBy();
      rank.put("bogus", 1);
      assertRefusesNestedKey("topn.rankBy", "bogus", spec(
         "content", "text", "value", "x", "topn", spec("mode", "top", "n", 3, "rankBy", rank)));
   }

   @Test
   void acceptsEveryRecognizedFieldKey() {
      assertDoesNotThrow(() -> CalcCellVocabulary.validate(spec(
         "content", "column",
         "field", spec("column", "Date", "type", "date", "dateLevel", "Month",
                       "dateInterval", 1, "namedGroup", "MyGroup"))));
   }

   @Test
   void acceptsTheSortAndTopnShapesDescribeReadsBack() {
      // value_desc with rankBy, manual order, topn none, topn top with rankBy
      for(Map<String, Object> sort : List.of(
         spec("direction", "value_desc", "rankBy", rankBy()),
         spec("direction", "manual", "manualOrder", List.of("a", "b")),
         spec("direction", "asc")))
      {
         assertDoesNotThrow(() -> CalcCellVocabulary.validate(
            spec("content", "text", "value", "x", "sort", sort)), sort.toString());
      }

      for(Map<String, Object> topn : List.of(
         spec("mode", "none"),
         spec("mode", "top", "n", 5, "rankBy", rankBy()),
         spec("mode", "bottom", "n", 5, "rankBy", rankBy())))
      {
         assertDoesNotThrow(() -> CalcCellVocabulary.validate(
            spec("content", "text", "value", "x", "topn", topn)), topn.toString());
      }
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

   // ── summary formula (Bug #78189) ─────────────────────────────────────────────

   private static Map<String, Object> summary(String formula) {
      return spec("content", "column", "grouping", "summary",
                  "field", Map.of("column", "Order:Total", "type", "measure"),
                  "formula", formula);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "First", "Last", "Correlation", "Covariance", "WeightedAverage",
      "First()", "Last( )", "Correlation(  )", "Covariance()", "WeightedAverage()",
      "First(3)", "Last(12)", "Correlation(3)", "Covariance( 7 )", "WeightedAverage(3)",
      "first", "First<-1>", "First()<-1>", "First(3)<-1>",
      "Weighted_Average()", "first-()", "first-", "FirstFormula", "CorrelationFormula()",
      "WeightedAvg()", "Weighted Avg", "WEIGHTED AVG(3)", "First(", "First(Order:Date",
      "Last_Formula()", "co-variance()", "First\t()", "Weighted\tAvg()", "First\n()"})
   void refusesATwoColumnFormulaWithoutASecondColumn(String formula) {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> CalcCellVocabulary.validate(summary(formula)));
      assertTrue(e.getMessage().contains("needs a second column"), e.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "First(Order:Date)", "First(Order:Date)<-1>", "First<-1>(Order:Date)",
      "Correlation(Order:Discount)", "Covariance(Order:Discount)",
      "WeightedAverage(Order:Quantity)", "First(Order:Total (USD))",
      "NthLargest(3)", "NthLargest", "PthPercentile(90)", "Sum", "Sum<-1>", "Average",
      "SumSQFormula", "SumSQ", "AVG", "avg(Order:Total)", "WeightedAvg(Order:Quantity)",
      "Weighted_Average(Order:Quantity)", "FirstFormula(Order:Date)",
      "CorrelationFormula(Order:Discount)", "None", "COUNT ALL", "Count_Distinct"})
   void acceptsWellFormedSummaryFormulas(String formula) {
      assertDoesNotThrow(() -> CalcCellVocabulary.validate(summary(formula)));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "Avrage", "Summ", "Foo(Bar)", "Std Deviation", "Formula", "(Sum)", "Sum<abc>",
      "Mystery(Order:Date)", "Sumary<-1>"})
   void refusesAnUnknownSummaryFormulaNamingIt(String formula) {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> CalcCellVocabulary.validate(summary(formula)));
      assertTrue(e.getMessage().contains("not a recognized aggregate formula"), e.getMessage());
      assertTrue(e.getMessage().contains("'" + formula + "'"), e.getMessage());
      assertTrue(e.getMessage().contains("Use one of: "), e.getMessage());
      assertTrue(e.getMessage().contains("Nothing was changed."), e.getMessage());
   }

   // The names list_cell_vocabulary advertises (AggregateFormula.getFormulaName() and getName()
   // values, minus the unsupported ones). AggregateFormula.getFormulas() needs SreeEnv, so the list
   // is spelled out here; every name must still pass the membership rule.
   @ParameterizedTest
   @ValueSource(strings = {
      "None", "Average", "Count", "DistinctCount", "Max", "Min", "Sum", "Median", "Mode",
      "Correlation", "Covariance", "Variance", "StandardDeviation", "PopulationVariance",
      "PopulationStandardDeviation", "SumSQFormula", "WeightedAverage", "First", "Last", "Product",
      "Concat", "NthLargest", "NthSmallest", "NthMostFrequent", "PthPercentile", "AVG",
      "COUNT ALL", "COUNT DISTINCT", "STANDARD DEVIATION", "POPULATION STANDARD DEVIATION",
      "WEIGHTED AVG", "SUMSQ"})
   void everyAdvertisedFormulaNamePassesTheMembershipRule(String formula) {
      try {
         CalcCellVocabulary.validate(summary(formula));
      }
      catch(IllegalArgumentException e) {
         // a second-column formula without its column is refused for that reason, not membership
         assertFalse(e.getMessage().contains("not a recognized aggregate formula"),
                     e.getMessage());
      }
   }

   @ParameterizedTest
   @ValueSource(strings = {"aggregate", "type", "fieldType", "formulaName", "Content", "rankBy"})
   void refusesAnUnknownBindingKeyNamingIt(String key) {
      Map<String, Object> cell = spec("content", "text", "value", "Total");
      cell.put(key, "x");

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> CalcCellVocabulary.validate(cell));
      assertTrue(e.getMessage().contains("'" + key + "'"), e.getMessage());
   }

   @Test
   void refusesAnUnknownBindingKeyBeforeTheContentChecks() {
      // no 'content' at all: the stray key is still what gets reported
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> CalcCellVocabulary.validate(spec("aggregate", "Sum")));
      assertTrue(e.getMessage().contains("'aggregate'"), e.getMessage());
      assertTrue(e.getMessage().contains("Recognized keys: content, grouping"), e.getMessage());
   }

   @Test
   void toleratesTheKeysDescribeEchoes() {
      Map<String, Object> cell = spec("content", "text", "value", "Total", "runtimeName", "c1",
                                      "dateLevel", "month", "dateInterval", 1, "namedGroup", "g");

      assertDoesNotThrow(() -> CalcCellVocabulary.validate(cell));
   }

   @Test
   void everyKeyDescribeEmitsIsWritableOrATolerableEcho() {
      CellBindingInfo info = new CellBindingInfo();
      info.setType(CellBinding.BIND_COLUMN);
      info.setBtype(CellBinding.GROUP);
      info.setValue("PAID");
      info.getOrder().setType(XConstants.SORT_ASC);

      Map<String, Object> described = CalcCellVocabulary.describe(info);
      Map<String, Object> echoed = new LinkedHashMap<>(described);
      echoed.values().removeIf(java.util.Objects::isNull);

      // A get -> set round trip must not trip the unknown-key refusal.
      try {
         CalcCellVocabulary.validate(echoed);
      }
      catch(IllegalArgumentException e) {
         assertFalse(e.getMessage().contains("does not recognize"), e.getMessage());
      }
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "SumWT", "SumWTFormula", "SumWTFormula(Order:Discount)", "Sum2", "Sum2Formula",
      "Sum2Formula(Order:Discount)", "Aggregate", "AggregateFormula", "aggregate",
      "sum_wt(Order:Discount)", "Sum-WT", "SUM WT Formula", "sum_2", "Aggregate()",
      "AggregateFormula(Order:Total)", "Sum\tWT(Order:Discount)", "Sum\nWT"})
   void refusesFormulasACalcCellCannotRun(String formula) {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> CalcCellVocabulary.validate(summary(formula)));
      assertTrue(e.getMessage().contains("not a recognized aggregate formula"), e.getMessage());
   }

   @Test
   void theFormulaRuleOnlyAppliesToSummaryColumnCells() {
      assertDoesNotThrow(() -> CalcCellVocabulary.validate(
         spec("content", "formula", "formula", "First")));
      assertDoesNotThrow(() -> CalcCellVocabulary.validate(
         spec("content", "column", "grouping", "detail",
              "field", Map.of("column", "Order:Total", "type", "measure"), "formula", "First")));
   }

   @Test
   void advertisedFormulasDropsTheUnsupportedAndKeepsSumSQ() {
      List<String> out = CalcCellVocabulary.advertisedFormulas(List.of(
         "Sum", "SumWTFormula", "Sum2Formula", "Aggregate", "SumSQFormula", "First"));

      assertEquals(List.of("Sum", "SumSQFormula", "First"), out);
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "NthLargest(abc)|abc", "NthLargest(2.5)|2.5", "NthSmallest(abc)|abc", "PthPercentile(xyz)|xyz",
      "NthMostFrequent(1.5)|1.5", "nth_largest(x)|x", "PthPercentileFormula(9e)|9e",
      "NthLargest(2.5)<-1>|2.5"})
   void refusesANonIntegerNthOrPercentileOperandNamingIt(String formula, String arg) {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> CalcCellVocabulary.validate(summary(formula)));
      assertTrue(e.getMessage().contains("must be an integer"), e.getMessage());
      assertTrue(e.getMessage().contains("'" + arg + "'"), e.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "NthLargest(3)", "NthSmallest( 2 )", "NthMostFrequent(1)", "PthPercentile(90)",
      "NthLargest(1)", "NthLargest(2147483647)", "PthPercentile(0)", "PthPercentile(100)",
      "NthLargest", "NthLargest()", "NthLargest(3)<-1>"})
   void acceptsIntegerOrAbsentNthAndPercentileOperands(String formula) {
      assertDoesNotThrow(() -> CalcCellVocabulary.validate(summary(formula)));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "NthLargest(0)|0|1 to 2147483647", "NthLargest(-2)|-2|1 to 2147483647",
      "NthSmallest(0)|0|1 to 2147483647", "NthMostFrequent(-1)|-1|1 to 2147483647",
      "nth_largest(0)|0|1 to 2147483647", "NthLargest(2147483648)|2147483648|1 to 2147483647",
      "NthLargest(99999999999)|99999999999|1 to 2147483647",
      "PthPercentile(150)|150|0 to 100", "PthPercentile(-5)|-5|0 to 100",
      "PthPercentile(101)|101|0 to 100", "PthPercentileFormula(150)|150|0 to 100",
      "PthPercentile<-1>(150)|150|0 to 100"})
   void refusesAnOutOfRangeNthOrPercentileOperand(String formula, String arg, String range) {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> CalcCellVocabulary.validate(summary(formula)));
      assertTrue(e.getMessage().contains("'" + arg + "'"), e.getMessage());
      assertTrue(e.getMessage().contains(range), e.getMessage());
      assertTrue(e.getMessage().contains("Nothing was changed."), e.getMessage());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "SUM|Sum", "sum|Sum", "SumSQ|SumSQFormula", "SUMSQFORMULA|SumSQFormula",
      "AVG|Average", "Count All|Count", "count_distinct|DistinctCount", "DistinctCount|DistinctCount",
      "Weighted Avg(Order:Qty)|WeightedAverage(Order:Qty)", "first(Order:Date)<-1>|First(Order:Date)<-1>",
      "nthlargest(3)|NthLargest(3)", "Sum<-1>|Sum<-1>", "Mystery|Mystery"})
   void canonicalFormulaRespellsTheNameAndKeepsSuffixes(String in, String expected) {
      assertEquals(expected, CalcCellVocabulary.canonicalFormula(in));
   }

   @Test
   void secondaryColumnOnlyForTwoColumnFormulasWithAColumn() {
      assertEquals("Order:Date", CalcCellVocabulary.secondaryColumn("First(Order:Date)"));
      assertEquals("NoSuchCol", CalcCellVocabulary.secondaryColumn("first(NoSuchCol)<-1>"));
      assertNull(CalcCellVocabulary.secondaryColumn("Sum"));
      assertNull(CalcCellVocabulary.secondaryColumn("NthLargest(3)"));
      assertNull(CalcCellVocabulary.secondaryColumn("First(3)"));
   }
}
