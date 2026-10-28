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
package inetsoft.web.wiz.worksheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.WebConfig;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79328: a subquery condition that closes a dependency cycle, or that the engine cannot
 * evaluate (operator, value shape, column type), is refused before anything is stored.
 */
@Tag("core")
@WizAgentTestSupport
class SubqueryConditionGuards79328Test {
   private Worksheet ws;
   private BoundTableAssembly orders;
   private BoundTableAssembly other;

   private static ColumnRef column(String name, String type) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, name));

      if(type != null) {
         col.setDataType(type);
      }

      return col;
   }

   private BoundTableAssembly table(String name, String idType, String stateType) {
      BoundTableAssembly t = new BoundTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(column("ORDER_ID", idType));
      cs.addAttribute(column("STATE", stateType));
      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      return t;
   }

   private void setUp(String idType, String stateType) {
      ws = new Worksheet();
      orders = table("ORDERS", idType, stateType);
      other = table("R", XSchema.INTEGER, XSchema.STRING);
   }

   private void setUp() {
      setUp(XSchema.INTEGER, XSchema.STRING);
   }

   private MirrorTableAssembly mirror(String name, TableAssembly source) {
      MirrorTableAssembly m = new MirrorTableAssembly(ws, name, source);
      ws.addAssembly(m);
      return m;
   }

   private static ConditionValueSpec sub(String table, String column) {
      return new ConditionValueSpec("subquery", null, null, null, table, column, null, null);
   }

   private static List<ConditionNode> cond(String field, String op, List<String> values,
                                           ConditionValueSpec... specs)
   {
      return List.of(new ConditionNode(
         new ConditionSpec(field, op, values, false, null, List.of(specs)), null, 0));
   }

   private static void refused(Executable call, String... fragments) {
      Throwable e = assertThrows(Exception.class, call);
      assertTrue(e instanceof IllegalArgumentException || e instanceof PairingException,
                 e.toString());

      for(String fragment : fragments) {
         assertTrue(e.getMessage().contains(fragment), e.getMessage() + " / " + fragment);
      }
   }

   private static boolean empty(ConditionListWrapper list) {
      return list == null || list.isEmpty();
   }

   // 79328-2 -- dependency cycle

   @Test
   void subqueryOnMirrorOfOwnTableRefusedAndNothingStored() {
      setUp();
      mirror("M", orders);
      refused(() -> setConditions(orders,
                 cond("ORDER_ID", "ONE_OF", List.of(), sub("M", "ORDER_ID")), false),
              "\"ORDERS\"", "\"M\"", "dependency cycle");
      assertTrue(empty(orders.getPreConditionList()));
      assertDoesNotThrow(() -> ws.checkDependencies());
   }

   @Test
   void subqueryCycleRefusedForPostAndMvLists() {
      setUp();
      mirror("M", orders);
      refused(() -> setConditions(orders,
                 cond("ORDER_ID", "ONE_OF", List.of(), sub("M", "ORDER_ID")), true),
              "dependency cycle");
      refused(() -> setMVConditions(orders,
                 cond("ORDER_ID", "ONE_OF", List.of(), sub("M", "ORDER_ID")), null, null, null,
                 null), "dependency cycle");
      assertDoesNotThrow(() -> ws.checkDependencies());
   }

   @Test
   void transitiveSubqueryCycleRefused() {
      setUp();
      setConditions(other, cond("ORDER_ID", "ONE_OF", List.of(), sub("ORDERS", "ORDER_ID")),
                    false);
      refused(() -> setConditions(orders,
                 cond("ORDER_ID", "ONE_OF", List.of(), sub("R", "ORDER_ID")), false),
              "dependency cycle");
      assertTrue(empty(orders.getPreConditionList()));
   }

   @Test
   void subqueryOnIndependentTableNextToAMirrorAccepted() {
      setUp();
      mirror("M", orders);
      setConditions(orders, cond("ORDER_ID", "ONE_OF", List.of(), sub("R", "ORDER_ID")), false);
      assertFalse(empty(orders.getPreConditionList()));
      assertDoesNotThrow(() -> ws.checkDependencies());
   }

   // 79328-3 -- operator

   @Test
   void operatorsTheEngineCannotEvaluateWithASubqueryRefused() {
      setUp();

      for(String op : new String[] { "STARTING_WITH", "LIKE", "CONTAINS", "NULL" }) {
         refused(() -> setConditions(orders,
                    cond("STATE", op, List.of(), sub("R", "STATE")), false),
                 "subquery", op);
      }

      refused(() -> setConditions(orders,
                 cond("ORDER_ID", "BETWEEN", List.of("1"), sub("R", "ORDER_ID")), false),
              "subquery", "BETWEEN");
      refused(() -> setConditions(orders,
                 cond("STATE", "STARTING_WITH", List.of(), sub("R", "STATE")), true),
              "subquery", "STARTING_WITH");
      refused(() -> setMVConditions(orders,
                 cond("STATE", "LIKE", List.of(), sub("R", "STATE")), null, null, null,
                 null), "subquery", "LIKE");
      assertTrue(empty(orders.getPreConditionList()));
   }

   @Test
   void supportedOperatorsWithASingleSubqueryAccepted() {
      setUp();

      for(String op : new String[] { "ONE_OF", "NOT_ONE_OF", "=", "!=", "<", ">", "<=", ">=" }) {
         setConditions(orders, cond("ORDER_ID", op, List.of(), sub("R", "ORDER_ID")), false);
         assertFalse(empty(orders.getPreConditionList()), op);
      }
   }

   // 79328-3 -- value shape

   @Test
   void subqueryMixedWithLiteralOrAnotherSubqueryRefused() {
      setUp();
      refused(() -> setConditions(orders,
                 cond("ORDER_ID", "ONE_OF", List.of("1"), sub("R", "ORDER_ID")), false),
              "subquery", "only value");
      refused(() -> setConditions(orders,
                 cond("ORDER_ID", "ONE_OF", List.of(),
                      sub("R", "ORDER_ID"), sub("R", "ORDER_ID")), false),
              "subquery", "only value");
      assertTrue(empty(orders.getPreConditionList()));
   }

   // 79328-3 -- type family

   @Test
   void integerColumnAgainstStringSubqueryColumnRefused() {
      setUp();
      refused(() -> setConditions(orders,
                 cond("ORDER_ID", "ONE_OF", List.of(), sub("R", "STATE")), false),
              "subquery", "integer", "string");
      assertTrue(empty(orders.getPreConditionList()));
   }

   @Test
   void numericFamiliesAreCompatible() {
      setUp(XSchema.LONG, XSchema.STRING);
      setConditions(orders, cond("ORDER_ID", "ONE_OF", List.of(), sub("R", "ORDER_ID")), false);
      assertFalse(empty(orders.getPreConditionList()));
   }

   @Test
   void unknownTypesAreNotRefused() {
      setUp(null, null);
      // ORDERS.ORDER_ID has no type of its own, so the string default is not evidence
      setConditions(orders, cond("ORDER_ID", "ONE_OF", List.of(), sub("R", "STATE")), false);
      assertFalse(empty(orders.getPreConditionList()));
   }

   // 79328-4 backstop -- a misplaced key is refused by name instead of dropped

   @Test
   void flatCorrelationKeysOnAValueSpecAreRefusedByName() {
      ObjectMapper mapper = new WebConfig().objectMapper();
      UnrecognizedPropertyException e = assertThrows(UnrecognizedPropertyException.class,
         () -> mapper.readValue("""
            {"valueType":"subquery","subQueryName":"R","inSubQueryColumn":"ORDER_ID",
             "subQueryColumn":"ORDER_ID","currentTableColumn":"ORDER_ID"}
            """, ConditionValueSpec.class));
      assertTrue(e.getMessage().contains("subQueryColumn"), e.getMessage());
   }

   @Test
   void valueSpecWithWhereStillDeserializes() throws Exception {
      ObjectMapper mapper = new WebConfig().objectMapper();
      ConditionValueSpec spec = mapper.readValue("""
         {"index":0,"valueType":"subquery","subQueryName":"R","inSubQueryColumn":"ORDER_ID",
          "where":{"subQueryColumn":"ORDER_ID","currentTableColumn":"ORDER_ID"}}
         """, ConditionValueSpec.class);
      assertEquals("ORDER_ID", spec.where().subQueryColumn());
   }
}
