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
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.replaceFilterExpressionValue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81556: the worksheet condition-value pane wrote its text into the applied condition as a
 * literal, dropping the Expression value and its JavaScript/SQL type.
 */
@Tag("core")
@WizAgentTestSupport
class ConditionExpressionValue81556Test {
   private static BoundTableAssembly tableWith(Object value) {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = new BoundTableAssembly(ws, "T");
      ColumnSelection cs = new ColumnSelection();
      ColumnRef id = new ColumnRef(new AttributeRef(null, "CUSTOMER_ID"));
      cs.addAttribute(id);
      t.setColumnSelection(cs, false);

      ConditionList cl = new ConditionList();
      Condition c = new Condition(XSchema.INTEGER);
      c.setOperation(XCondition.EQUAL_TO);
      c.addValue(value);
      cl.append(new ConditionItem(id, c, 0));
      t.setPreConditionList(cl);
      ws.addAssembly(t);
      return t;
   }

   private static ExpressionValue expr(String type, String text) {
      ExpressionValue e = new ExpressionValue();
      e.setType(type);
      e.setExpression(text);
      return e;
   }

   private static Object firstValue(BoundTableAssembly t) {
      Condition c = (Condition) ((ConditionItem) t.getPreConditionList().getConditionList()
         .getItem(0)).getXCondition();
      return c.getValue(0);
   }

   @Test
   void javascriptExpressionStaysJavascript() {
      BoundTableAssembly t = tableWith(expr(ExpressionValue.JAVASCRIPT, "1+1"));
      replaceFilterExpressionValue(t, "CUSTOMER_ID", "2*1");

      assertTrue(firstValue(t) instanceof ExpressionValue);
      ExpressionValue v = (ExpressionValue) firstValue(t);
      assertEquals("2*1", v.getExpression());
      assertEquals(ExpressionValue.JAVASCRIPT, v.getType());
   }

   @Test
   void sqlExpressionStaysSql() {
      BoundTableAssembly t = tableWith(expr(ExpressionValue.SQL, "1+0"));
      replaceFilterExpressionValue(t, "CUSTOMER_ID", "2-1");

      assertTrue(firstValue(t) instanceof ExpressionValue);
      ExpressionValue v = (ExpressionValue) firstValue(t);
      assertEquals("2-1", v.getExpression());
      assertEquals(ExpressionValue.SQL, v.getType());
   }

   @Test
   void literalConditionIsRefusedNotConverted() {
      BoundTableAssembly t = tableWith("5");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> replaceFilterExpressionValue(t, "CUSTOMER_ID", "2*1"));

      assertTrue(e.getMessage().contains("Expression value"), e.getMessage());
      assertEquals("5", String.valueOf(firstValue(t)));
   }

   @Test
   void blankTextIsRefused() {
      BoundTableAssembly t = tableWith(expr(ExpressionValue.JAVASCRIPT, "1+1"));

      assertThrows(IllegalArgumentException.class,
                   () -> replaceFilterExpressionValue(t, "CUSTOMER_ID", "  "));
      assertEquals("1+1", ((ExpressionValue) firstValue(t)).getExpression());
   }
}
