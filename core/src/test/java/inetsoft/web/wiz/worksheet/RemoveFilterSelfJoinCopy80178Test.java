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

import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.removeFilter;
import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.replaceFilter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80178 (wbs-246): on a self-join the copy column CUSTOMER_ID_1 is the base column
 * CUSTOMER_ID under an alias, so its {@code getAttribute()} is also CUSTOMER_ID. remove_filter
 * matched by name OR attribute and removed both conditions when asked for CUSTOMER_ID.
 */
@Tag("core")
@WizAgentTestSupport
class RemoveFilterSelfJoinCopy80178Test {
   private static BoundTableAssembly table(boolean filterOriginal, boolean filterCopy) {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = new BoundTableAssembly(ws, "T");
      ColumnRef original = new ColumnRef(new AttributeRef(null, "CUSTOMER_ID"));
      ColumnRef copy = new ColumnRef(new AttributeRef(null, "CUSTOMER_ID"));
      copy.setAlias("CUSTOMER_ID_1");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(original);
      cs.addAttribute(copy);
      t.setColumnSelection(cs, false);

      ConditionList cl = new ConditionList();

      if(filterOriginal) {
         cl.append(item(original, "1"));
      }

      if(filterCopy) {
         if(!cl.isEmpty()) {
            cl.append(new JunctionOperator(JunctionOperator.AND, 0));
         }

         cl.append(item(copy, "2"));
      }

      t.setPreConditionList(cl);
      ws.addAssembly(t);
      return t;
   }

   private static ConditionItem item(ColumnRef ref, String value) {
      Condition c = new Condition(XSchema.STRING);
      c.setOperation(XCondition.EQUAL_TO);
      c.addValue(value);
      return new ConditionItem(ref, c, 0);
   }

   private static String remaining(BoundTableAssembly t) {
      ConditionListWrapper w = t.getPreConditionList();

      if(w == null || w.isEmpty()) {
         return "";
      }

      ConditionList cl = w.getConditionList();
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < cl.getSize(); i++) {
         if(cl.getItem(i) instanceof ConditionItem ci) {
            sb.append(ci.getAttribute().getName()).append(';');
         }
      }

      return sb.toString();
   }

   @Test
   void removingTheBaseNameKeepsTheCopysCondition() {
      BoundTableAssembly t = table(true, true);

      removeFilter(t, "CUSTOMER_ID");

      assertEquals("CUSTOMER_ID_1;", remaining(t));
   }

   @Test
   void removingTheCopyNameKeepsTheBaseCondition() {
      BoundTableAssembly t = table(true, true);

      removeFilter(t, "CUSTOMER_ID_1");

      assertEquals("CUSTOMER_ID;", remaining(t));
   }

   @Test
   void theAttributeStillMatchesWhenNoConditionCarriesTheRequestedName() {
      BoundTableAssembly t = table(false, true);

      removeFilter(t, "CUSTOMER_ID");

      assertEquals("", remaining(t));
   }

   @Test
   void replaceFilterStillRefusesWhenTwoConditionsMatch() {
      BoundTableAssembly t = table(true, true);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> replaceFilter(t, "CUSTOMER_ID", "=", "9"));

      assertTrue(e.getMessage().contains("2 conditions"), e.getMessage());
      assertEquals("CUSTOMER_ID;CUSTOMER_ID_1;", remaining(t));
   }
}
