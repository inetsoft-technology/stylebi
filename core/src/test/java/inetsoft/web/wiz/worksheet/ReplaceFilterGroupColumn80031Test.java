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

import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.replaceFilter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80031 (WSC-048): edit_condition on a group column with only a post-aggregate (HAVING)
 * condition silently ADDED a new pre-aggregate condition instead of replacing the existing one,
 * reported as success -- a group column is not an aggregate alias, so replaceFilter's own
 * pre/post routing (the same narrow routing removeFilter uses, see its Bug #76752 note) defaulted
 * it to the pre list, found no match there, and appended rather than checking the post list the
 * condition actually lives in.
 */
@Tag("core")
@WizAgentTestSupport
class ReplaceFilterGroupColumn80031Test {
   private static BoundTableAssembly groupedTable() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = new BoundTableAssembly(ws, "T");
      ColumnSelection cs = new ColumnSelection();
      ColumnRef customerId = new ColumnRef(new AttributeRef(null, "CUSTOMER_ID"));
      ColumnRef discount = new ColumnRef(new AttributeRef(null, "DISCOUNT"));
      cs.addAttribute(customerId);
      cs.addAttribute(discount);
      t.setColumnSelection(cs, false);

      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef(customerId));
      ColumnRef discountAlias = new ColumnRef(new AttributeRef(null, "DISCOUNT"));
      discountAlias.setAlias("TD");
      AggregateRef agg = new AggregateRef(discountAlias, AggregateFormula.SUM);
      ainfo.addAggregate(agg);
      t.setAggregateInfo(ainfo);

      // The group column's own post-aggregate (HAVING) condition: NOT CUSTOMER_ID = 1.
      ConditionList cl = new ConditionList();
      Condition c = new Condition(XSchema.STRING);
      c.setOperation(XCondition.EQUAL_TO);
      c.setNegated(true);
      c.addValue("1");
      cl.append(new ConditionItem(customerId, c, 0));
      t.setPostConditionList(cl);

      ws.addAssembly(t);
      return t;
   }

   @Test
   void refusesInsteadOfAddingASecondCondition() {
      BoundTableAssembly t = groupedTable();

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> replaceFilter(t, "CUSTOMER_ID", "=", "1"));

      assertTrue(e.getMessage().contains("set_post_conditions"), e.getMessage());
      assertTrue(e.getMessage().contains("\"CUSTOMER_ID\""), e.getMessage());
      // Nothing was changed: still exactly one condition, in the post list, untouched.
      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());
      assertEquals(1, t.getPostConditionList().getConditionSize());
   }

   @Test
   void aGroupColumnWithAGenuinePreConditionStillReplacesNormally() {
      BoundTableAssembly t = groupedTable();
      ConditionList pre = new ConditionList();
      Condition c = new Condition(XSchema.STRING);
      c.setOperation(XCondition.EQUAL_TO);
      c.addValue("2");
      pre.append(new ConditionItem(
         (ColumnRef) t.getColumnSelection().getAttribute("CUSTOMER_ID"), c, 0));
      t.setPreConditionList(pre);

      replaceFilter(t, "CUSTOMER_ID", "=", "1");

      assertEquals(1, t.getPreConditionList().getConditionSize());
      assertEquals(1, t.getPostConditionList().getConditionSize());
   }

   @Test
   void anOrdinaryColumnWithNoConditionAnywhereStillAppendsAsBefore() {
      BoundTableAssembly t = groupedTable();

      replaceFilter(t, "DISCOUNT", "=", "1");

      assertNotNull(t.getPreConditionList());
      assertEquals(1, t.getPreConditionList().getConditionSize());
      // The unrelated post condition on CUSTOMER_ID is untouched.
      assertEquals(1, t.getPostConditionList().getConditionSize());
   }
}
