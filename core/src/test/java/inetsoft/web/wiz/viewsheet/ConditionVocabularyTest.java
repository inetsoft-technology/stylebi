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
import inetsoft.web.composer.model.condition.JunctionOperatorModel;
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
      return new ConditionVocabulary.Clause(field, operator, values, junction, false);
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
