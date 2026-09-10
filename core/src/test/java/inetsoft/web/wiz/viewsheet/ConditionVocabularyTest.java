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
package inetsoft.web.wiz.viewsheet;

import inetsoft.uql.JunctionOperator;
import inetsoft.uql.XCondition;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.condition.ConditionValueModel;
import inetsoft.web.composer.model.condition.ExpressionValueModel;
import inetsoft.web.composer.model.condition.JunctionOperatorModel;
import inetsoft.web.composer.model.condition.RankingValueModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ConditionVocabularyTest {
   private static DataRefModel field(String name) {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn(name);
      return field;
   }

   private static final DataRefModel[] FIELDS =
      { field("Region"), field("Revenue"), field("OrderDate") };

   private static ConditionVocabulary.Clause clause(String field, String operator,
                                                    List<Object> values, String junction)
   {
      return new ConditionVocabulary.Clause(field, operator, values, junction, false, false, 0);
   }

   private static ConditionVocabulary.Clause clause(String field, String operator,
                                                    List<Object> values, String junction,
                                                    boolean equal, int level)
   {
      return new ConditionVocabulary.Clause(field, operator, values, junction, false, equal, level);
   }

   // ── the alternating array ─────────────────────────────────────────────────

   @Test
   void buildsASingleConditionWithNoJunction() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals", List.of("East"), null)), FIELDS);

      assertEquals(1, list.length);
      ConditionModel condition = assertInstanceOf(ConditionModel.class, list[0]);
      assertEquals(XCondition.EQUAL_TO, condition.getOperation());
   }

   /**
    * ConditionUtil gates every value-model branch on {@code getType().equals(VALUE)}; a
    * literal that doesn't match the constant (e.g. a lowercase "value") silently falls through
    * to a generic fallback instead of the value's real type-specific handling.
    */
   @Test
   void builtValuesCarryTheValueTypeConstant() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals", List.of("East"), null)), FIELDS);

      ConditionModel condition = (ConditionModel) list[0];
      assertEquals(ConditionValueModel.VALUE, condition.getValues()[0].getType());
   }

   @Test
   void alternatesConditionJunctionCondition() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "one_of", List.of("East", "West"), "and"),
                 clause("Revenue", ">", List.of(10000), null)),
         FIELDS);

      assertEquals(3, list.length);
      assertInstanceOf(ConditionModel.class, list[0]);
      assertInstanceOf(JunctionOperatorModel.class, list[1]);
      assertInstanceOf(ConditionModel.class, list[2]);
   }

   @Test
   void buildsThreeConditionsWithTwoJunctions() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals", List.of("East"), "or"),
                 clause("Revenue", ">", List.of(1), "and"),
                 clause("OrderDate", "null", List.of(), null)),
         FIELDS);

      assertEquals(5, list.length);
      assertInstanceOf(JunctionOperatorModel.class, list[1]);
      assertInstanceOf(JunctionOperatorModel.class, list[3]);
   }

   @Test
   void mapsTheJunctionTokens() {
      Object[] and = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals", List.of("E"), "and"),
                 clause("Revenue", ">", List.of(1), null)), FIELDS);
      Object[] or = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals", List.of("E"), "or"),
                 clause("Revenue", ">", List.of(1), null)), FIELDS);

      assertEquals(JunctionOperator.AND, ((JunctionOperatorModel) and[1]).getType());
      assertEquals(JunctionOperator.OR, ((JunctionOperatorModel) or[1]).getType());
   }

   @Test
   void anEmptyListBuildsAnEmptyArray() {
      assertEquals(0, ConditionVocabulary.toConditionList(List.of(), FIELDS).length);
      assertEquals(0, ConditionVocabulary.toConditionList(null, FIELDS).length);
   }

   // ── the arity invariant: the highest-value guard here ─────────────────────

   @Test
   void refusesATrailingJunctionNamingTheIndex() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ConditionVocabulary.toConditionList(
            List.of(clause("Region", "equals", List.of("East"), "and")), FIELDS));

      assertTrue(thrown.getMessage().contains("0"));
      assertTrue(thrown.getMessage().contains("orphan"),
                 "the refusal should say what a trailing junction becomes");
   }

   @Test
   void refusesAMissingMiddleJunctionNamingTheIndex() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ConditionVocabulary.toConditionList(
            List.of(clause("Region", "equals", List.of("East"), null),
                    clause("Revenue", ">", List.of(1), null)),
            FIELDS));

      assertTrue(thrown.getMessage().contains("0"));
      assertTrue(thrown.getMessage().contains("1"), "name the condition it should join to");
   }

   @Test
   void refusesABlankJunctionAsIfItWereMissing() {
      assertThrows(IllegalArgumentException.class,
                   () -> ConditionVocabulary.toConditionList(
                      List.of(clause("Region", "equals", List.of("E"), "  "),
                              clause("Revenue", ">", List.of(1), null)),
                      FIELDS));
   }

   @Test
   void refusesAnUnknownJunction() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ConditionVocabulary.toConditionList(
            List.of(clause("Region", "equals", List.of("E"), "xor"),
                    clause("Revenue", ">", List.of(1), null)),
            FIELDS));

      assertTrue(thrown.getMessage().contains("xor"));
   }

   /** Nothing may be built before the whole list is checked, or a cast finds a half-array. */
   @Test
   void validatesTheWholeListBeforeBuildingAnyOfIt() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ConditionVocabulary.toConditionList(
            List.of(clause("Region", "equals", List.of("E"), "and"),
                    clause("Nope", ">", List.of(1), null)),
            FIELDS));

      assertTrue(thrown.getMessage().contains("Nope"));
   }

   // ── the recorded cast-crash trigger ───────────────────────────────────────

   @Test
   void refusesAFieldTheAssemblyCannotFilterOnListingWhatItCan() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ConditionVocabulary.toConditionList(
            List.of(clause("Profit", "equals", List.of(1), null)), FIELDS));

      assertTrue(thrown.getMessage().contains("Profit"));
      assertTrue(thrown.getMessage().contains("Region"), "list the fields that do exist");
      assertTrue(thrown.getMessage().contains("cast"),
                 "say why, since this is a recorded downstream crash");
   }

   @Test
   void matchesAFieldNameCaseInsensitively() {
      assertDoesNotThrow(() -> ConditionVocabulary.toConditionList(
         List.of(clause("region", "equals", List.of("East"), null)), FIELDS));
   }

   @Test
   void refusesAMissingField() {
      assertThrows(IllegalArgumentException.class,
                   () -> ConditionVocabulary.toConditionList(
                      List.of(clause(null, "equals", List.of(1), null)), FIELDS));
   }

   // ── operator aliases, from the recorded multi-value defect ────────────────

   @Test
   void resolvesTheOneOfAliases() {
      for(String token : List.of("one_of", "oneOf", "IN", "in")) {
         Object[] list = ConditionVocabulary.toConditionList(
            List.of(clause("Region", token, List.of("E", "W"), null)), FIELDS);
         assertEquals(XCondition.ONE_OF, ((ConditionModel) list[0]).getOperation(),
                      "'" + token + "' should resolve to ONE_OF");
      }
   }

   @Test
   void resolvesTheComparisonAliases() {
      assertEquals(XCondition.EQUAL_TO, operationOf("="));
      assertEquals(XCondition.EQUAL_TO, operationOf("equals"));
      assertEquals(XCondition.LESS_THAN, operationOf("<"));
      assertEquals(XCondition.GREATER_THAN, operationOf(">"));
      assertEquals(XCondition.CONTAINS, operationOf("contains"));
      assertEquals(XCondition.STARTING_WITH, operationOf("startsWith"));
   }

   @Test
   void refusesAnUnknownOperatorListingTheValid() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class, () -> operationOf("=~"));

      assertTrue(thrown.getMessage().contains("=~"));
      assertTrue(thrown.getMessage().contains("contains"));
   }

   // ── value arity ───────────────────────────────────────────────────────────

   @Test
   void refusesBetweenWithoutTwoValues() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ConditionVocabulary.toConditionList(
            List.of(clause("Revenue", "between", List.of(1), null)), FIELDS));

      assertTrue(thrown.getMessage().contains("two"));
   }

   @Test
   void acceptsBetweenWithExactlyTwo() {
      assertDoesNotThrow(() -> ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", "between", List.of(1, 100), null)), FIELDS));
   }

   @Test
   void refusesAValuedOperatorWithNoValues() {
      assertThrows(IllegalArgumentException.class,
                   () -> ConditionVocabulary.toConditionList(
                      List.of(clause("Region", "one_of", List.of(), null)), FIELDS));
   }

   @Test
   void acceptsNullWithNoValues() {
      assertDoesNotThrow(() -> ConditionVocabulary.toConditionList(
         List.of(clause("Region", "null", List.of(), null)), FIELDS));
   }

   @Test
   void refusesNullWithValues() {
      assertThrows(IllegalArgumentException.class,
                   () -> ConditionVocabulary.toConditionList(
                      List.of(clause("Region", "null", List.of("x"), null)), FIELDS));
   }

   @Test
   void acceptsIsNullWithNoValues() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "is_null", List.of(), null)), FIELDS);

      assertEquals(1, list.length);
      assertEquals(XCondition.NULL, ((ConditionModel) list[0]).getOperation());
   }

   // ── round trip ────────────────────────────────────────────────────────────

   @Test
   void readsBackTheFlatVocabularyIncludingJunctions() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "one_of", List.of("East", "West"), "and"),
                 clause("Revenue", ">", List.of(10000), null)),
         FIELDS);

      List<Map<String, Object>> described = ConditionVocabulary.describe(list);

      assertEquals(2, described.size());
      assertEquals("Region", described.get(0).get("field"));
      assertEquals("one_of", described.get(0).get("operator"));
      assertEquals(List.of("East", "West"), described.get(0).get("values"));
      assertEquals("and", described.get(0).get("junction"));
      assertNull(described.get(1).get("junction"), "the last condition carries no junction");
   }

   @Test
   void readsBackACanonicalOperatorRatherThanAnAlias() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", ">", List.of(1), null)), FIELDS);

      assertEquals("greater_than", ConditionVocabulary.describe(list).get(0).get("operator"),
                   "reading back an alias would make the round trip lossy in appearance");
   }

   @Test
   void describesAnEmptyOrNullListAsEmpty() {
      assertTrue(ConditionVocabulary.describe(null).isEmpty());
      assertTrue(ConditionVocabulary.describe(new Object[0]).isEmpty());
   }

   @Test
   void aBuiltListRoundTripsBackToItself() {
      List<ConditionVocabulary.Clause> clauses = List.of(
         clause("Region", "one_of", List.of("East"), "or"),
         clause("Revenue", "between", List.of(1, 2), "and"),
         clause("OrderDate", "null", List.of(), null));

      List<Map<String, Object>> described =
         ConditionVocabulary.describe(ConditionVocabulary.toConditionList(clauses, FIELDS));

      assertEquals(3, described.size());
      assertEquals("or", described.get(0).get("junction"));
      assertEquals("and", described.get(1).get("junction"));
      assertNull(described.get(2).get("junction"));
   }

   // ── L8 parity finding 1: TOP_N/BOTTOM_N ranking values ─────────────────────

   @Test
   void bottomNIsARecognizedOperator() {
      assertEquals(XCondition.BOTTOM_N, operationOfRanking("bottom_n"));
   }

   @Test
   void topNBuildsARankingValueModelFromNAndGroupField() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", "top_n",
                        List.of(Map.of("n", 5, "groupField", "Region")), null)),
         FIELDS);

      ConditionModel condition = (ConditionModel) list[0];
      assertEquals(XCondition.TOP_N, condition.getOperation());
      RankingValueModel ranking = assertInstanceOf(
         RankingValueModel.class, condition.getValues()[0].getValue());
      assertEquals(5, ranking.getN());
      assertEquals("Region", ranking.getDataRef().getName());
   }

   @Test
   void topNRefusesANonObjectValue() {
      assertThrows(IllegalArgumentException.class, () -> ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", "top_n", List.of(5), null)), FIELDS));
   }

   @Test
   void topNRefusesANonPositiveN() {
      assertThrows(IllegalArgumentException.class, () -> ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", "top_n",
                        List.of(Map.of("n", 0, "groupField", "Region")), null)),
         FIELDS));
   }

   @Test
   void topNRefusesAnUnknownGroupFieldListingWhatItCan() {
      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> ConditionVocabulary.toConditionList(
            List.of(clause("Revenue", "top_n",
                           List.of(Map.of("n", 5, "groupField", "Nope")), null)),
            FIELDS));

      assertTrue(thrown.getMessage().contains("Nope"));
      assertTrue(thrown.getMessage().contains("Region"));
   }

   @Test
   void topNRefusesMoreThanOneValue() {
      assertThrows(IllegalArgumentException.class, () -> ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", "top_n",
                        List.of(Map.of("n", 5, "groupField", "Region"),
                                Map.of("n", 1, "groupField", "Region")),
                        null)),
         FIELDS));
   }

   @Test
   void topNReadsBackNAndGroupField() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", "top_n",
                        List.of(Map.of("n", 5, "groupField", "Region")), null)),
         FIELDS);

      @SuppressWarnings("unchecked")
      List<Object> values = (List<Object>) ConditionVocabulary.describe(list).get(0).get("values");
      Map<?, ?> ranking = (Map<?, ?>) values.get(0);

      assertEquals(5, ranking.get("n"));
      assertEquals("Region", ranking.get("groupField"));
   }

   private static int operationOfRanking(String operator) {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", operator,
                        List.of(Map.of("n", 5, "groupField", "Region")), null)),
         FIELDS);
      return ((ConditionModel) list[0]).getOperation();
   }

   // ── L8 parity finding 3: "or equal to" ──────────────────────────────────────

   @Test
   void equalDefaultsToFalse() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", "less_than", List.of(10), null)), FIELDS);

      assertFalse(((ConditionModel) list[0]).isEqual());
   }

   @Test
   void equalCanBeSetTrueAndReadBack() {
      ConditionVocabulary.Clause c = new ConditionVocabulary.Clause(
         "Revenue", "less_than", List.of(10), null, false, true, 0);

      Object[] list = ConditionVocabulary.toConditionList(List.of(c), FIELDS);

      assertTrue(((ConditionModel) list[0]).isEqual());
      assertEquals(true, ConditionVocabulary.describe(list).get(0).get("equal"));
   }

   // ── L8 parity finding 4: nesting level ──────────────────────────────────────

   @Test
   void levelDefaultsToZero() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals", List.of("East"), null)), FIELDS);

      assertEquals(0, ((ConditionModel) list[0]).getLevel());
   }

   @Test
   void levelCanBeSetAndReadBack() {
      ConditionVocabulary.Clause c = new ConditionVocabulary.Clause(
         "Region", "equals", List.of("East"), null, false, false, 2);

      Object[] list = ConditionVocabulary.toConditionList(List.of(c), FIELDS);

      assertEquals(2, ((ConditionModel) list[0]).getLevel());
      assertEquals(2, ConditionVocabulary.describe(list).get(0).get("level"));
   }

   /**
    * The destructive round trip this finding fixes: get_condition's own output, fed straight
    * back into set_condition as an apparently unchanged edit, used to silently flatten a nested
    * condition's level to 0.
    */
   @Test
   void aNonZeroLevelSurvivesAGetConditionThenSetConditionRoundTrip() {
      ConditionVocabulary.Clause authored = new ConditionVocabulary.Clause(
         "Region", "equals", List.of("East"), null, false, false, 1);
      Object[] originalList = ConditionVocabulary.toConditionList(List.of(authored), FIELDS);

      Map<String, Object> described = ConditionVocabulary.describe(originalList).get(0);
      assertEquals(1, described.get("level"));

      @SuppressWarnings("unchecked")
      ConditionVocabulary.Clause replayed = new ConditionVocabulary.Clause(
         (String) described.get("field"), (String) described.get("operator"),
         (List<Object>) described.get("values"), null, false, false,
         (int) described.get("level"));
      Object[] replayedList = ConditionVocabulary.toConditionList(List.of(replayed), FIELDS);

      assertEquals(1, ((ConditionModel) replayedList[0]).getLevel(),
                   "replaying get_condition's own output must not flatten the nesting level");
   }

   // ── L8 parity finding 5: typed condition values ─────────────────────────────

   @Test
   void aPlainScalarValueStillMeansValueType() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals", List.of("East"), null)), FIELDS);

      ConditionValueModel value = ((ConditionModel) list[0]).getValues()[0];
      assertEquals(ConditionValueModel.VALUE, value.getType());
      assertEquals("East", value.getValue());
   }

   @Test
   void fieldTypedValueComparesToAnotherColumn() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", "greater_than",
                        List.of(Map.of("type", "field", "field", "Region")), null)),
         FIELDS);

      ConditionValueModel value = ((ConditionModel) list[0]).getValues()[0];
      assertEquals(ConditionValueModel.FIELD, value.getType());
      assertEquals("Region", ((DataRefModel) value.getValue()).getName());
   }

   @Test
   void fieldTypedValueRefusesAnUnknownColumn() {
      assertThrows(IllegalArgumentException.class, () -> ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", "greater_than",
                        List.of(Map.of("type", "field", "field", "Nope")), null)),
         FIELDS));
   }

   @Test
   void variableTypedValueBuildsTheDollarParenForm() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals",
                        List.of(Map.of("type", "variable", "name", "region")), null)),
         FIELDS);

      ConditionValueModel value = ((ConditionModel) list[0]).getValues()[0];
      assertEquals(ConditionValueModel.VARIABLE, value.getType());
      assertEquals("$(region)", value.getValue());
   }

   @Test
   void sessionDataTypedValueAcceptsOnlyTheKnownNames() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals",
                        List.of(Map.of("type", "session_data", "name", "_USER_")), null)),
         FIELDS);

      ConditionValueModel value = ((ConditionModel) list[0]).getValues()[0];
      assertEquals(ConditionValueModel.SESSION_DATA, value.getType());
      assertEquals("$(_USER_)", value.getValue());

      assertThrows(IllegalArgumentException.class, () -> ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals",
                        List.of(Map.of("type", "session_data", "name", "_BOGUS_")), null)),
         FIELDS));
   }

   @Test
   void expressionTypedValueBuildsAnExpressionValueModel() {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Revenue", "greater_than",
                        List.of(Map.of("type", "expression", "expression", "field['Cost'] * 2",
                                       "language", "js")),
                        null)),
         FIELDS);

      ConditionValueModel value = ((ConditionModel) list[0]).getValues()[0];
      assertEquals(ConditionValueModel.EXPRESSION, value.getType());
      ExpressionValueModel expr = assertInstanceOf(
         ExpressionValueModel.class, value.getValue());
      assertEquals("field['Cost'] * 2", expr.getExpression());
      assertEquals(ExpressionValueModel.JS, expr.getType());
   }

   @Test
   void anUnknownTypedValueDiscriminatorFailsLoudRatherThanFallingBackToValue() {
      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> ConditionVocabulary.toConditionList(
            List.of(clause("Region", "equals", List.of(Map.of("type", "bogus", "x", "y")), null)),
            FIELDS));

      assertTrue(thrown.getMessage().contains("bogus"));
   }

   @Test
   void vocabularyExplainsTheJunctionRule() {
      assertTrue(String.valueOf(ConditionVocabulary.vocabulary().get("note")).contains("NEXT"));
   }

   private static int operationOf(String operator) {
      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("Region", operator, List.of("x", "y"), null)), FIELDS);
      return ((ConditionModel) list[0]).getOperation();
   }
}
