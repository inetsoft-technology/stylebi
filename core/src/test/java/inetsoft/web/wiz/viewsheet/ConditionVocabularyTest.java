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

import inetsoft.test.*;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ConditionVocabularyTest {
   private static DataRefModel field(String name) {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn(name);
      return field;
   }

   private static DataRefModel field(String name, String dataType) {
      DataRefModel field = field(name);
      when(field.getDataType()).thenReturn(dataType);
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

   // ── bug 76741 / VSC-008: junctionLevel, so `level` can express AND/OR grouping ──

   /**
    * The withdrawn first-draft default was {@code Math.max(clause.level(), nextClause.level())},
    * which collapses this exact shape (MD/CA at level 1 joined by "or", West at level 0 joined
    * by "and") to junction levels {@code {1, 1}} -- no level-0 junction survives, so
    * {@code HierarchyList.validate()}'s compaction pass flattens everything back to 0. The
    * corrected default is {@code Math.min}, which leaves the "and" at level 0 so validate() has
    * a level-0 junction to anchor on.
    */
   @Test
   void defaultJunctionLevelIsMinOfFlankingLevelsNotMax() {
      ConditionVocabulary.Clause md = new ConditionVocabulary.Clause(
         "Region", "equals", List.of("MD"), "or", false, false, 1, null);
      ConditionVocabulary.Clause ca = new ConditionVocabulary.Clause(
         "Region", "equals", List.of("CA"), "and", false, false, 1, null);
      ConditionVocabulary.Clause west = new ConditionVocabulary.Clause(
         "Region", "equals", List.of("West"), null, false, false, 0, null);

      Object[] list = ConditionVocabulary.toConditionList(List.of(md, ca, west), FIELDS);

      assertEquals(1, ((JunctionOperatorModel) list[1]).getLevel(),
                   "the 'or' between two level-1 conditions stays at level 1");
      assertEquals(0, ((JunctionOperatorModel) list[3]).getLevel(),
                   "the 'and' at a genuine level transition must be the shallower (min) value, "
                   + "not the deeper (max) one, or HierarchyList.validate() flattens everything");
   }

   /**
    * Explicit {@code junctionLevel} is the only way to express two independent, side-by-side
    * groups -- {@code (A OR B) AND (C OR D)} -- since both flanking conditions of the joining
    * "and" sit at the same level and no flanking-neighbor formula can tell that case apart from
    * a flat run. Builds through the real {@code ConditionVocabulary} output, then evaluates the
    * resulting junction levels through the real {@code ConditionList}/{@code ConditionGroup}
    * pipeline (including {@code HierarchyList.validate()}) against the 4-input truth table.
    */
   @Test
   void explicitJunctionLevelProducesTwoIndependentGroups() {
      ConditionVocabulary.Clause a = new ConditionVocabulary.Clause(
         "Region", "equals", List.of("a"), "or", false, false, 1, null);
      ConditionVocabulary.Clause b = new ConditionVocabulary.Clause(
         "Revenue", "equals", List.of("b"), "and", false, false, 1, 0);
      ConditionVocabulary.Clause c = new ConditionVocabulary.Clause(
         "OrderDate", "equals", List.of("c"), "or", false, false, 1, null);
      ConditionVocabulary.Clause d = new ConditionVocabulary.Clause(
         "Region", "equals", List.of("d"), null, false, false, 1, null);

      Object[] list = ConditionVocabulary.toConditionList(List.of(a, b, c, d), FIELDS);

      assertEquals(1, ((JunctionOperatorModel) list[1]).getLevel(), "first 'or' stays at level 1");
      assertEquals(0, ((JunctionOperatorModel) list[3]).getLevel(),
                   "the joining 'and' must be given the explicit, strictly shallower level");
      assertEquals(1, ((JunctionOperatorModel) list[5]).getLevel(), "second 'or' stays at level 1");

      // Mirror the levels ConditionVocabulary computed into the real ConditionList/ConditionItem/
      // JunctionOperator pipeline (the same classes ConditionUtil.fromModelToConditionList
      // builds), so ConditionGroup construction exercises the real HierarchyList.validate() pass.
      inetsoft.uql.ConditionList conditionList = new inetsoft.uql.ConditionList();

      for(int i = 0; i < list.length; i++) {
         if(i % 2 == 0) {
            ConditionModel cm = (ConditionModel) list[i];
            inetsoft.uql.Condition xcond =
               new inetsoft.uql.Condition(inetsoft.uql.schema.XSchema.STRING);
            xcond.setOperation(inetsoft.uql.XCondition.EQUAL_TO);
            xcond.addValue(cm.getValues()[0].getValue());
            inetsoft.uql.ConditionItem item = new inetsoft.uql.ConditionItem();
            item.setLevel(cm.getLevel());
            item.setXCondition(xcond);
            conditionList.append(item);
         }
         else {
            JunctionOperatorModel jm = (JunctionOperatorModel) list[i];
            conditionList.append(new inetsoft.uql.JunctionOperator(jm.getType(), jm.getLevel()));
         }
      }

      inetsoft.report.filter.ConditionGroup group =
         new inetsoft.report.filter.ConditionGroup(0, conditionList);
      // each row supplies the same single column value against all four single-column conditions,
      // matching Test 4's four-input truth table from the diagnosis
      assertFalse(group.evaluate(new Object[] { "a" }),
                  "(a OR b) AND (c OR d): only 'a' true -> (T OR F) AND (F OR F) = false");
      assertFalse(group.evaluate(new Object[] { "c" }),
                  "(a OR b) AND (c OR d): only 'c' true -> (F OR F) AND (T OR F) = false");
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

   /**
    * The same destructive round trip as above, but for an explicit {@code junctionLevel} on a
    * two-independent-groups joining junction: {@code describe()} must surface the junction's own
    * level (not just its type), or replaying get_condition's own output into set_condition falls
    * onto the default Math.min formula and silently reintroduces the flat/misgroup bug this PR
    * exists to fix.
    */
   @Test
   void anExplicitJunctionLevelSurvivesAGetConditionThenSetConditionRoundTrip() {
      ConditionVocabulary.Clause a = new ConditionVocabulary.Clause(
         "Region", "equals", List.of("a"), "or", false, false, 1, null);
      ConditionVocabulary.Clause b = new ConditionVocabulary.Clause(
         "Revenue", "equals", List.of("b"), "and", false, false, 1, 0);
      ConditionVocabulary.Clause c = new ConditionVocabulary.Clause(
         "OrderDate", "equals", List.of("c"), "or", false, false, 1, null);
      ConditionVocabulary.Clause d = new ConditionVocabulary.Clause(
         "Region", "equals", List.of("d"), null, false, false, 1, null);

      Object[] originalList = ConditionVocabulary.toConditionList(List.of(a, b, c, d), FIELDS);
      List<Map<String, Object>> described = ConditionVocabulary.describe(originalList);

      assertEquals(0, described.get(1).get("junctionLevel"),
                   "get_condition must surface the joining 'and's explicit junctionLevel, "
                   + "not just its type");

      List<ConditionVocabulary.Clause> replayed = new ArrayList<>();

      for(Map<String, Object> described_clause : described) {
         @SuppressWarnings("unchecked")
         List<Object> values = (List<Object>) described_clause.get("values");
         replayed.add(new ConditionVocabulary.Clause(
            (String) described_clause.get("field"), (String) described_clause.get("operator"),
            values, (String) described_clause.get("junction"),
            (boolean) described_clause.get("negated"), (boolean) described_clause.get("equal"),
            (int) described_clause.get("level"),
            (Integer) described_clause.get("junctionLevel")));
      }

      Object[] replayedList = ConditionVocabulary.toConditionList(replayed, FIELDS);

      assertEquals(0, ((JunctionOperatorModel) replayedList[3]).getLevel(),
                   "replaying get_condition's own output must not flatten the joining 'and's "
                   + "level back to the default-inferred value");
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

   // ── bug 76522 / DCG-010: a bare string that can't coerce to the field's type ─

   @Test
   void refusesAStringValueThatCannotCoerceToTheFieldsDataType() {
      DataRefModel[] fields = { field("Region"), field("OrderId", "integer"), field("OrderDate") };

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> ConditionVocabulary.toConditionList(
            List.of(clause("OrderId", "greater_than", List.of("CUSTOMER_ID"), null)), fields));

      assertTrue(thrown.getMessage().contains("CUSTOMER_ID"));
      assertTrue(thrown.getMessage().contains("OrderId"));
      assertTrue(thrown.getMessage().contains("integer"));
   }

   @Test
   void acceptsAStringValueThatDoesCoerceToTheFieldsDataType() {
      DataRefModel[] fields = { field("Region"), field("OrderId", "integer"), field("OrderDate") };

      Object[] list = ConditionVocabulary.toConditionList(
         List.of(clause("OrderId", "greater_than", List.of("42"), null)), fields);

      ConditionValueModel value = ((ConditionModel) list[0]).getValues()[0];
      assertEquals(ConditionValueModel.VALUE, value.getType());
      assertEquals("42", value.getValue(), "the literal is passed through unchanged; "
         + "ConditionUtil performs the actual coercion downstream, as before");
   }

   @Test
   void acceptsAStringLiteralAgainstAStringTypedField() {
      DataRefModel[] fields = { field("Region", "string"), field("Revenue"), field("OrderDate") };

      assertDoesNotThrow(() -> ConditionVocabulary.toConditionList(
         List.of(clause("Region", "equals", List.of("CUSTOMER_ID"), null)), fields));
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
