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

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79834 (item 3): set_sort targets exactly the named column. On a self-join the right copy
 * is aliased CUSTOMER_ID_1 but keeps the attribute CUSTOMER_ID, so naming CUSTOMER_ID must not
 * touch the copy's sort.
 */
@Tag("core")
@WizAgentTestSupport
class SetSortExactName79834Test {
   @Test
   void noneOnPlainColumnLeavesTheAliasedCopysSort() {
      EmbeddedTableAssembly t = selfJoinTable();
      addSort(t, t.getColumnSelection().getAttribute(2), XConstants.SORT_DESC);

      assertFalse(WorksheetMutationSupport.setSort(t, "CUSTOMER_ID", "NONE"));

      assertEquals(1, t.getSortInfo().getSortCount());
      assertEquals("CUSTOMER_ID_1", t.getSortInfo().getSorts()[0].getName());
   }

   @Test
   void ascOnPlainColumnAddsASortAndKeepsTheAliasedCopysDirection() {
      EmbeddedTableAssembly t = selfJoinTable();
      addSort(t, t.getColumnSelection().getAttribute(2), XConstants.SORT_DESC);

      assertTrue(WorksheetMutationSupport.setSort(t, "CUSTOMER_ID", "ASC"));

      SortRef[] sorts = t.getSortInfo().getSorts();
      assertEquals(2, sorts.length);
      SortRef alias = find(sorts, "CUSTOMER_ID_1");
      SortRef plain = find(sorts, "CUSTOMER_ID");
      assertNotNull(alias);
      assertNotNull(plain);
      assertEquals(XConstants.SORT_DESC, alias.getOrder());
      assertEquals(XConstants.SORT_ASC, plain.getOrder());
   }

   @Test
   void noneOnTheAliasRemovesOnlyThatSort() {
      EmbeddedTableAssembly t = selfJoinTable();
      addSort(t, t.getColumnSelection().getAttribute(0), XConstants.SORT_ASC);
      addSort(t, t.getColumnSelection().getAttribute(1), XConstants.SORT_ASC);
      addSort(t, t.getColumnSelection().getAttribute(2), XConstants.SORT_DESC);

      assertTrue(WorksheetMutationSupport.setSort(t, "CUSTOMER_ID_1", "NONE"));

      SortRef[] sorts = t.getSortInfo().getSorts();
      assertEquals(2, sorts.length);
      assertNull(find(sorts, "CUSTOMER_ID_1"));
      assertNotNull(find(sorts, "CUSTOMER_ID"));
   }

   @Test
   void ascOnTheAliasFlipsOnlyThatSort() {
      EmbeddedTableAssembly t = selfJoinTable();
      addSort(t, t.getColumnSelection().getAttribute(1), XConstants.SORT_ASC);
      addSort(t, t.getColumnSelection().getAttribute(2), XConstants.SORT_DESC);

      assertTrue(WorksheetMutationSupport.setSort(t, "CUSTOMER_ID_1", "ASC"));

      SortRef[] sorts = t.getSortInfo().getSorts();
      assertEquals(2, sorts.length);
      assertEquals(XConstants.SORT_ASC, find(sorts, "CUSTOMER_ID_1").getOrder());
      assertEquals(XConstants.SORT_ASC, find(sorts, "CUSTOMER_ID").getOrder());
   }

   @Test
   void plainNameSortIsStillMatched() {
      EmbeddedTableAssembly t = selfJoinTable();
      addSort(t, t.getColumnSelection().getAttribute(0), XConstants.SORT_ASC);

      assertTrue(WorksheetMutationSupport.setSort(t, "ORDER_ID", "DESC"));
      assertEquals(1, t.getSortInfo().getSortCount());
      assertEquals(XConstants.SORT_DESC, t.getSortInfo().getSorts()[0].getOrder());

      assertTrue(WorksheetMutationSupport.setSort(t, "ORDER_ID", "NONE"));
      assertEquals(0, t.getSortInfo().getSortCount());
   }

   @Test
   void aliasNotAppliedIsMatchedByItsUnderlyingName() {
      EmbeddedTableAssembly t = selfJoinTable();
      ColumnRef cr = (ColumnRef) t.getColumnSelection().getAttribute(2);
      cr.setApplyingAlias(false);
      addSort(t, cr, XConstants.SORT_DESC);

      assertTrue(WorksheetMutationSupport.setSort(t, "CUSTOMER_ID_1", "ASC"));

      assertEquals(1, t.getSortInfo().getSortCount());
      assertEquals(XConstants.SORT_ASC, t.getSortInfo().getSorts()[0].getOrder());
   }

   @Test
   void dateRangeColumnIsMatchedByItsFullName() {
      EmbeddedTableAssembly t = selfJoinTable();
      ColumnRef year = new ColumnRef(new DateRangeRef(
         "Year(ORDER_DATE)", new AttributeRef(null, "ORDER_DATE"), DateRangeRef.YEAR_INTERVAL));
      year.setDataType(XSchema.INTEGER);
      t.getColumnSelection().addAttribute(year);
      addSort(t, year, XConstants.SORT_ASC);

      assertTrue(WorksheetMutationSupport.setSort(t, "Year(ORDER_DATE)", "DESC"));
      assertEquals(1, t.getSortInfo().getSortCount());
      assertEquals(XConstants.SORT_DESC, t.getSortInfo().getSorts()[0].getOrder());

      assertTrue(WorksheetMutationSupport.setSort(t, "Year(ORDER_DATE)", "NONE"));
      assertEquals(0, t.getSortInfo().getSortCount());
   }

   @Test
   void noneOnAColumnWithNoSortIsANoOp() {
      EmbeddedTableAssembly t = selfJoinTable();
      assertFalse(WorksheetMutationSupport.setSort(t, "CUSTOMER_ID", "NONE"));
   }

   private static EmbeddedTableAssembly selfJoinTable() {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(new Worksheet(), "J");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(column("ORDER_ID", null));
      cs.addAttribute(column("CUSTOMER_ID", null));
      cs.addAttribute(column("CUSTOMER_ID", "CUSTOMER_ID_1"), false);
      t.setColumnSelection(cs, false);
      return t;
   }

   private static ColumnRef column(String attr, String alias) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, attr));
      ref.setDataType(XSchema.INTEGER);

      if(alias != null) {
         ref.setAlias(alias);
      }

      return ref;
   }

   private static void addSort(EmbeddedTableAssembly t, inetsoft.uql.erm.DataRef col, int order) {
      SortInfo si = t.getSortInfo() == null ? new SortInfo() : t.getSortInfo();
      SortRef sr = new SortRef(col);
      sr.setOrder(order);
      si.appendSort(sr);
      t.setSortInfo(si);
   }

   private static SortRef find(SortRef[] sorts, String name) {
      for(SortRef sr : sorts) {
         if(name.equals(sr.getName())) {
            return sr;
         }
      }

      return null;
   }
}
