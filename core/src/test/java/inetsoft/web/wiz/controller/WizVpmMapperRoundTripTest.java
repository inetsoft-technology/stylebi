/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.wiz.controller;

import inetsoft.test.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.web.portal.model.database.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Design 7.5: the wire condition tree survives the whole write path. A seeded property test builds
 * 500 random trees (depth 1 to 4, and/or groups with and without {@code not}, negated clauses, all
 * operand types and operator arities, hostile and Chinese text), writes each through the mapper's
 * flatten, the Portal's own builder ({@code VpmService.fromModel}) and a real registry, reads the
 * stored model back with the direct walk, and asserts it equals the generated tree. A second
 * test feeds the flat lists the Portal UI produces and checks the tree a reader expects.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  WizVpmTestSupport.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class WizVpmMapperRoundTripTest {
   private static final String[] BINARY = { "=", ">", "<", ">=", "<=", "<>", "LIKE", "MATCH", "IN" };
   private static final String[] UNARY = { "IS NULL" };
   private static final String[] TEXT = {
      "East", "区域 北", "a<b & c", "x\"y'z", "]]> cdata", "  padded  ", "", "100%", "a,b,c",
      "line1\nline2", "éè" };

   @Test
   void fiveHundredRandomTreesRoundTripThroughTheRealRegistry() throws Exception {
      WizVpmTestSupport support = new WizVpmTestSupport();
      Random random = new Random(20261120L);
      WizVpmValidator validator = new WizVpmValidator(null);

      for(int i = 0; i < 500; i++) {
         Map<String, Object> tree = tree(random, 1 + random.nextInt(4));
         Map<String, Object> wire = definition("Tree" + i, tree);
         assertTrue(validator.validate(support.fresh(), wire, WizVpmValidator.Mode.CREATE, null, null)
                       .errors().isEmpty(), "generated tree " + i + " is valid: " + wire);

         VPMDefinition portal = support.mapper.toDefinition(wire, support.ds, null);
         support.service.create(support.ds, portal, support.bob);
         VirtualPrivateModel stored = support.service.getStored(support.ds, "Tree" + i, support.bob);
         Map<String, Object> back = support.mapper.toWire(stored, support.jdbc);

         assertEquals(support.mapper.normalize(wire), support.mapper.normalize(back),
                      "tree " + i + " changed on the way through the server");
         // and the stored form is a fixed point: writing the read-back changes nothing
         assertNull(WizVpmController.firstDifference(
            support.mapper.normalize(back),
            support.mapper.normalize(support.mapper.toWire(stored, support.jdbc)), "t"));
      }
   }

   @Test
   void aConditionWithATargetAndNoClauseRoundTripsAsWritten() throws Exception {
      WizVpmTestSupport support = new WizVpmTestSupport();
      WizVpmValidator validator = new WizVpmValidator(null);

      for(String script : new String[] { null, "" }) {
         String name = "NoClause" + (script == null ? "Null" : "Empty");
         Map<String, Object> target = new LinkedHashMap<>();
         target.put("kind", "table");
         target.put("name", "ORDERS");
         Map<String, Object> condition = new LinkedHashMap<>();
         condition.put("name", "condition");
         condition.put("target", target);
         Map<String, Object> wire = new LinkedHashMap<>();
         wire.put("name", name);
         wire.put("conditions", new ArrayList<>(List.of(condition)));

         assertTrue(validator.validate(support.fresh(), wire, WizVpmValidator.Mode.CREATE, null, null)
                       .errors().isEmpty(), "a tree-less condition is valid");

         VPMDefinition portal = support.mapper.toDefinition(wire, support.ds, null);

         if(script != null) {
            portal.getConditions().get(0).setScript(script);
         }

         support.service.create(support.ds, portal, support.bob);
         VirtualPrivateModel stored = support.service.getStored(support.ds, name, support.bob);
         Map<String, Object> back = support.mapper.toWire(stored, support.jdbc);
         List<?> conditions = (List<?>) back.get("conditions");

         assertEquals(1, conditions.size());
         assertFalse(((Map<?, ?>) conditions.get(0)).containsKey("tree"),
                     "the read-back carries no tree");
         assertEquals(support.mapper.normalize(wire), support.mapper.normalize(back));
         // the get result fed back unchanged is a no-op
         assertNull(WizVpmController.firstDifference(
            support.mapper.normalize(back), support.mapper.normalize(wire), "t"));
      }
   }

   @Test
   void flatListsTheUiProducesReadAsTheTreeAReaderExpects() throws Exception {
      WizVpmTestSupport support = new WizVpmTestSupport();
      // A(0) AND(0) B(1) OR(1) C(1)
      assertEquals(
         group("and", false, leaf("A"), group("or", false, leaf("B"), leaf("C"))),
         readFlat(support, "F1", leaf("A"), conj(0, "and", false), leaf(1, "B"),
                  conj(1, "or", false), leaf(1, "C")));
      // A(1) OR(1) B(1) AND(0) C(0)
      assertEquals(
         group("and", false, group("or", false, leaf("A"), leaf("B")), leaf("C")),
         readFlat(support, "F2", leaf(1, "A"), conj(1, "or", false), leaf(1, "B"),
                  conj(0, "and", false), leaf("C")));
      // A AND B AND C is one group of three
      assertEquals(
         group("and", false, leaf("A"), leaf("B"), leaf("C")),
         readFlat(support, "F3", leaf("A"), conj(0, "and", false), leaf("B"),
                  conj(0, "and", false), leaf("C")));
      // A NOT-OR B is a negated group
      assertEquals(
         group("or", true, leaf("A"), leaf("B")),
         readFlat(support, "F4", leaf("A"), conj(0, "or", true), leaf("B")));
      // a lone clause is the tree itself
      assertEquals(expected("A"), readFlat(support, "F5", leaf("A")));
   }

   // -- generator ---------------------------------------------------------------------------

   private Map<String, Object> tree(Random random, int depth) {
      if(depth <= 1 || random.nextInt(100) < 25) {
         return clause(random);
      }

      Map<String, Object> group = new LinkedHashMap<>();
      group.put("op", random.nextBoolean() ? "and" : "or");

      if(random.nextInt(100) < 30) {
         group.put("not", true);
      }
      else if(random.nextInt(100) < 20) {
         group.put("not", false);
      }

      List<Object> items = new ArrayList<>();
      int count = 2 + random.nextInt(3);

      for(int i = 0; i < count; i++) {
         items.add(tree(random, depth - 1));
      }

      group.put("items", items);
      return group;
   }

   private Map<String, Object> clause(Random random) {
      Map<String, Object> clause = new LinkedHashMap<>();
      int kind = random.nextInt(10);
      clause.put("left", operand(random, true));

      if(kind == 0) {
         clause.put("op", UNARY[0]);
      }
      else if(kind == 1) {
         clause.put("op", "BETWEEN");
         clause.put("right", operand(random, false));
         clause.put("right2", operand(random, false));
      }
      else {
         clause.put("op", BINARY[random.nextInt(BINARY.length)]);
         clause.put("right", operand(random, false));
      }

      if(random.nextInt(100) < 25) {
         clause.put("not", true);
      }

      return clause;
   }

   private Map<String, Object> operand(Random random, boolean left) {
      Map<String, Object> operand = new LinkedHashMap<>();
      int type = random.nextInt(left ? 3 : 5);

      switch(type) {
      case 0:
         operand.put("type", "field");
         operand.put("expression", "ORDERS.COL" + random.nextInt(20));
         break;
      case 1:
         operand.put("type", "expression");
         operand.put("expression", "ORDERS.COL" + random.nextInt(5) + " + " + random.nextInt(9));
         break;
      case 2:
         operand.put("type", "value");
         operand.put("expression", TEXT[random.nextInt(TEXT.length)]);
         break;
      case 3:
         operand.put("type", "variable");
         operand.put("expression", "var" + random.nextInt(9));
         break;
      default:
         operand.put("type", "session");
         operand.put("expression", new String[] { "_USER_", "_GROUPS_", "_ROLES_" }[random.nextInt(3)]);
      }

      return operand;
   }

   private Map<String, Object> definition(String name, Map<String, Object> tree) {
      Map<String, Object> condition = new LinkedHashMap<>();
      condition.put("name", "c");
      Map<String, Object> target = new LinkedHashMap<>();
      target.put("kind", "table");
      target.put("name", "ORDERS");
      condition.put("target", target);
      condition.put("tree", tree);
      Map<String, Object> wire = new LinkedHashMap<>();
      wire.put("name", name);
      wire.put("conditions", new ArrayList<>(List.of(condition)));
      return wire;
   }

   // -- flat list helpers -------------------------------------------------------------------

   private Object readFlat(WizVpmTestSupport support, String name, Object... items)
      throws Exception
   {
      VPMDefinition portal = new VPMDefinition();
      portal.setName(name);
      Condition condition = new Condition();
      condition.setName("c");
      condition.setTableName("ORDERS");
      List<DataConditionItem> clauses = new ArrayList<>();

      for(Object item : items) {
         if(item instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> leaf = (Map<String, Object>) item;
            Clause clause = new Clause();
            clause.setLevel(leaf.containsKey("level") ? (Integer) leaf.get("level") : 0);
            Operation op = new Operation();
            op.setSymbol("=");
            clause.setOperation(op);
            clause.setValue1(new ClauseValue("Field", "ORDERS." + leaf.get("name")));
            clause.setValue2(new ClauseValue("Value", "1"));
            clauses.add(clause);
         }
         else {
            clauses.add((DataConditionItem) item);
         }
      }

      condition.setClauses(clauses);
      portal.setConditions(new ArrayList<>(List.of(condition)));
      support.service.create(support.ds, portal, support.bob);
      VirtualPrivateModel stored = support.service.getStored(support.ds, name, support.bob);
      List<Object> conditions = WizVpmMapper.list(support.mapper.toWire(stored, support.jdbc).get("conditions"));
      return WizVpmMapper.map(conditions.get(0)).get("tree");
   }

   private static Conjunction conj(int level, String relation, boolean not) {
      Conjunction conjunction = new Conjunction();
      conjunction.setLevel(level);
      conjunction.setConjunction(relation);
      conjunction.setIsNot(not);
      return conjunction;
   }

   /** A level-0 leaf placeholder, expanded by {@link #readFlat}. */
   private static Map<String, Object> leaf(String name) {
      return leaf(0, name);
   }

   private static Map<String, Object> leaf(int level, String name) {
      Map<String, Object> leaf = new LinkedHashMap<>();
      leaf.put("name", name);
      leaf.put("level", level);
      return leaf;
   }

   /** The canonical wire form of the leaf placeholder as the mapper reads it. */
   private static Map<String, Object> expected(String name) {
      Map<String, Object> clause = new LinkedHashMap<>();
      Map<String, Object> left = new LinkedHashMap<>();
      left.put("type", "field");
      left.put("expression", "ORDERS." + name);
      Map<String, Object> right = new LinkedHashMap<>();
      right.put("type", "value");
      right.put("expression", "1");
      clause.put("left", left);
      clause.put("op", "=");
      clause.put("right", right);
      return clause;
   }

   private static Map<String, Object> group(String op, boolean not, Object... items) {
      Map<String, Object> group = new LinkedHashMap<>();
      group.put("op", op);

      if(not) {
         group.put("not", true);
      }

      List<Object> list = new ArrayList<>();

      for(Object item : items) {
         list.add(item instanceof Map && ((Map<?, ?>) item).containsKey("level") ?
                     expected((String) ((Map<?, ?>) item).get("name")) : item);
      }

      group.put("items", list);
      return group;
   }
}
