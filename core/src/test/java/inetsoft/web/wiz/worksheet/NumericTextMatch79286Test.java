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

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.List;

import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79286: STARTING_WITH / CONTAINS / LIKE on a numeric column is refused whichever way the
 * field is spelled, because the server types the condition from the column it resolves.
 */
@Tag("core")
@WizAgentTestSupport
class NumericTextMatch79286Test {
   private static final String[] TEXT_MATCHES = { "STARTING_WITH", "CONTAINS", "LIKE" };

   /** Table "PRODUCTS" (entity SA.PRODUCTS): PRICE double, NAME string. */
   private static BoundTableAssembly table(Worksheet ws) {
      BoundTableAssembly t = new BoundTableAssembly(ws, "PRODUCTS");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(column("SA.PRODUCTS", "PRICE", XSchema.DOUBLE));
      cs.addAttribute(column("SA.PRODUCTS", "NAME", XSchema.STRING));
      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      return t;
   }

   private static ColumnRef column(String entity, String name, String type) {
      ColumnRef col = new ColumnRef(new AttributeRef(entity, name));
      col.setDataType(type);
      return col;
   }

   private static List<ConditionNode> cond(String field, String op, String... values) {
      return List.of(new ConditionNode(
         new ConditionSpec(field, op, Arrays.asList(values), false, null), null, 0));
   }

   private static void refused(Executable call, String... fragments) {
      Throwable e = assertThrows(IllegalArgumentException.class, call);

      for(String fragment : fragments) {
         assertTrue(e.getMessage().contains(fragment), e.getMessage() + " / " + fragment);
      }
   }

   private static boolean empty(ConditionListWrapper list) {
      return list == null || list.isEmpty();
   }

   @Test
   void textMatchOnNumericColumnRefusedForEverySpelling() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = table(ws);
      MirrorTableAssembly m = new MirrorTableAssembly(ws, "M", t);
      ws.addAssembly(m);
      m.update();

      for(String op : TEXT_MATCHES) {
         for(String field : new String[] { "PRICE", "PRODUCTS.PRICE", "SA.PRODUCTS.PRICE" }) {
            refused(() -> setConditions(t, cond(field, op, "1"), false),
                    "\"" + field + "\"", op, "double");
            refused(() -> setMVConditions(t, cond(field, op, "1"), null, null, null, null),
                    "double");
            refused(() -> addFilter(t, field, op, "1"), "\"" + field + "\"", "double");
         }

         String mirrored = m.getColumnSelection(false).getAttribute(0).getName();
         refused(() -> setConditions(m, cond(mirrored, op, "1"), false), "double");
         refused(() -> setConditions(m, cond("PRODUCTS.PRICE", op, "1"), false), "double");
      }

      assertTrue(empty(t.getPreConditionList()));
      assertTrue(empty(m.getPreConditionList()));
   }

   @Test
   void replaceFilterRefusedAndLeavesTheConditionAlone() {
      BoundTableAssembly t = table(new Worksheet());
      addFilter(t, "PRICE", "=", "5");

      for(String op : TEXT_MATCHES) {
         refused(() -> replaceFilter(t, "SA.PRODUCTS.PRICE", op, "1"), "double");
      }

      Condition kept = (Condition) ((ConditionItem) t.getPreConditionList()
         .getConditionList().getItem(0)).getXCondition();
      assertEquals(XCondition.EQUAL_TO, kept.getOperation());
   }

   @Test
   void textMatchOnAggregateAliasRefusedByItsAggregateType() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = table(ws);
      TestWorksheets.withGroupSumAndSort(t, "NAME", "PRICE");
      AggregateRef sum = t.getAggregateInfo().getAggregate(0);

      for(String op : TEXT_MATCHES) {
         refused(() -> setConditions(t, cond(sum.toView(), op, "1"), true), "double");
         refused(() -> addFilter(t, sum.toView(), op, "1"), "double");
      }

      assertTrue(empty(t.getPostConditionList()));
   }

   @Test
   void otherOperatorsAndStringColumnsStillAccepted() {
      BoundTableAssembly t = table(new Worksheet());

      setConditions(t, cond("SA.PRODUCTS.PRICE", "=", "5"), false);
      setConditions(t, cond("PRODUCTS.PRICE", "ONE_OF", "5", "6"), false);
      setConditions(t, cond("PRICE", "BETWEEN", "1", "9"), false);
      addFilter(t, "PRICE", ">", "2");

      for(String op : TEXT_MATCHES) {
         setConditions(t, cond("SA.PRODUCTS.NAME", op, "a"), false);
         addFilter(t, "NAME", op, "a");
      }

      assertFalse(empty(t.getPreConditionList()));
   }
}
