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

import java.util.List;

import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80063: the read_worksheet_model readback of a boolean column's field/expression condition
 * ("T.B" / "Expression[Javascript:true]" in values, the real value in valueSpecs[index]) is
 * accepted again by set_conditions. The values() slot a valueSpec overwrites is display text and
 * was judged as a boolean literal; a real literal is still refused.
 */
@Tag("core")
@WizAgentTestSupport
class BooleanValueSpecReadback80063Test {
   private static BoundTableAssembly table() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = new BoundTableAssembly(ws, "T");
      ColumnSelection cs = new ColumnSelection();

      for(String name : new String[] { "Flag", "B" }) {
         ColumnRef col = new ColumnRef(new AttributeRef(null, name));
         col.setDataType(XSchema.BOOLEAN);
         cs.addAttribute(col);
      }

      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      return t;
   }

   private static List<ConditionNode> cond(List<String> values, ConditionValueSpec... specs) {
      return List.of(new ConditionNode(
         new ConditionSpec("Flag", "=", values, false, null, List.of(specs)), null, 0));
   }

   @Test
   void fieldValueSpecReadbackOnABooleanColumnIsAccepted() {
      BoundTableAssembly t = table();

      setConditions(t, cond(List.of("T.B"),
                            new ConditionValueSpec("field", "T.B", null, null, 0)), false);

      assertEquals(1, t.getPreConditionList().getConditionSize());
   }

   @Test
   void expressionValueSpecReadbackOnABooleanColumnIsAccepted() {
      BoundTableAssembly t = table();

      setConditions(t, cond(List.of("Expression[Javascript:true]"),
                            new ConditionValueSpec("expression", null, "true", "js", 0)), false);

      assertEquals(1, t.getPreConditionList().getConditionSize());
   }

   @Test
   void aBadLiteralOnABooleanColumnIsStillRefused() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         setConditions(table(), cond(List.of("yes")), false));
      assertTrue(e.getMessage().contains("not a boolean value"), e.getMessage());
   }

   @Test
   void aBadLiteralAtANonReplacedSlotIsStillRefused() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         setConditions(table(), List.of(new ConditionNode(new ConditionSpec(
            "Flag", "one_of", List.of("T.B", "yes"), false, null,
            List.of(new ConditionValueSpec("field", "T.B", null, null, 0))), null, 0)), false));
      assertTrue(e.getMessage().contains("\"yes\"") || e.getMessage().contains("'yes'"),
                 e.getMessage());
   }
}
