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

import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.ColumnSelection;
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
 * #78155: boolean literals and valueSpec expressionType are validated at write time instead of
 * silently degrading (Boolean.valueOf("yes") == false; unknown expressionType == sql).
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetConditionValidationTest {
   private static BoundTableAssembly table() {
      BoundTableAssembly t = new BoundTableAssembly(new Worksheet(), "T");
      ColumnSelection cs = new ColumnSelection();
      ColumnRef flag = new ColumnRef(new AttributeRef(null, "flag"));
      flag.setDataType(XSchema.BOOLEAN);
      cs.addAttribute(flag);
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "s")));
      t.setColumnSelection(cs, false);
      return t;
   }

   private static Condition first(TableAssembly t) {
      ConditionList cl = (ConditionList) t.getPreConditionList();
      return (Condition) ((ConditionItem) cl.getItem(0)).getXCondition();
   }

   @Test
   void addFilterRejectsNonBooleanLiteral() {
      assertThrows(IllegalArgumentException.class,
                   () -> addFilter(table(), "flag", "=", "yes"));
   }

   @Test
   void addFilterNormalizesBooleanLiteral() {
      BoundTableAssembly t = table();
      addFilter(t, "flag", "=", " TRUE ");
      assertEquals(Boolean.TRUE, first(t).getValue(0));
   }

   @Test
   void setConditionsRejectsNonBooleanLiteral() {
      ConditionSpec spec = new ConditionSpec("flag", "=", List.of("yes"), false, null,
                                             null, null);
      assertThrows(IllegalArgumentException.class,
                   () -> setConditions(table(), List.of(new ConditionNode(spec, null, 0)),
                                       false));
   }

   @Test
   void booleanVariableReferenceStillAccepted() {
      BoundTableAssembly t = table();
      assertDoesNotThrow(() -> addFilter(t, "flag", "=", "$(f)"));
      assertNotNull(t.getPreConditionList());
   }

   private static void setExprType(String type) {
      ConditionValueSpec vs = new ConditionValueSpec("expression", null, "1", type);
      ConditionSpec spec = new ConditionSpec("s", "=", List.of(), false, null, List.of(vs), null);
      setConditions(table(), List.of(new ConditionNode(spec, null, 0)), false);
   }

   @Test
   void unknownExpressionTypeRefused() {
      IllegalArgumentException e =
         assertThrows(IllegalArgumentException.class, () -> setExprType("jss"));
      assertTrue(e.getMessage().contains("jss"));
   }

   @Test
   void knownOrBlankExpressionTypeAccepted() {
      for(String t : new String[] {null, "", "sql", "SQL", "js", "JavaScript"}) {
         assertDoesNotThrow(() -> setExprType(t), String.valueOf(t));
      }
   }
}
