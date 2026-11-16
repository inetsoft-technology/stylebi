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
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81446: a pre-aggregate condition on a logical-model attribute the table does not select is
 * accepted, as the native condition dialog accepts it; the attribute is added to the table's
 * private selection as an invisible column.
 */
@Tag("core")
@WizAgentTestSupport
class SourceAttributeCondition81446Test {
   @Test
   void uniqueBareNameIsAdmittedAsAnInvisibleColumn() {
      BoundTableAssembly t = customerTable();

      WorksheetMutationSupport.SourceAdmission admission =
         WorksheetMutationSupport.admitSourceFields(t, List.of("Discount"), this::source);

      ColumnSelection cs = t.getColumnSelection(false);
      assertEquals(3, cs.getAttributeCount());
      ColumnRef hidden = (ColumnRef) cs.getAttribute("Order.Discount");
      assertNotNull(hidden);
      assertFalse(hidden.isVisible());
      assertNotNull(admission);
      assertNotNull(WorksheetMutationSupport.resolveFieldOrNull(t, "Discount", false));
   }

   @Test
   void qualifiedNameResolvesAmongSeveralEntitiesWithTheSameAttribute() {
      BoundTableAssembly t = new BoundTableAssembly(new Worksheet(), "T");
      t.setColumnSelection(selection(column("Customer", "Name", XSchema.STRING)), false);

      WorksheetMutationSupport.admitSourceFields(t, List.of("Supplier.State"), this::source);

      assertNotNull(t.getColumnSelection(false).getAttribute("Supplier.State"));
   }

   @Test
   void ambiguousBareNameIsRefusedAndNothingIsAdded() {
      BoundTableAssembly t = new BoundTableAssembly(new Worksheet(), "T");
      t.setColumnSelection(selection(column("Customer", "Name", XSchema.STRING)), false);
      ColumnSelection before = t.getColumnSelection(false);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> WorksheetMutationSupport.admitSourceFields(t, List.of("State"), this::source));

      assertTrue(e.getMessage().contains("Customer.State"));
      assertTrue(e.getMessage().contains("Supplier.State"));
      assertSame(before, t.getColumnSelection(false));
      assertEquals(1, t.getColumnSelection(false).getAttributeCount());
   }

   @Test
   void tablesOwnColumnWinsOverTheSource() {
      BoundTableAssembly t = customerTable();

      WorksheetMutationSupport.SourceAdmission admission =
         WorksheetMutationSupport.admitSourceFields(t, List.of("State"), this::source);

      assertEquals(2, t.getColumnSelection(false).getAttributeCount());
      admission.rollback();
      assertEquals(2, t.getColumnSelection(false).getAttributeCount());
   }

   @Test
   void nameThatIsNowhereIsLeftForTheNotFoundRefusal() {
      BoundTableAssembly t = customerTable();

      WorksheetMutationSupport.admitSourceFields(t, List.of("NO_SUCH_COL"), this::source);

      assertEquals(2, t.getColumnSelection(false).getAttributeCount());
      assertFalse(WorksheetMutationSupport.fieldExists(t, "NO_SUCH_COL", false));
   }

   @Test
   void rollbackRestoresTheSelectionTheTableHadBefore() {
      BoundTableAssembly t = customerTable();
      ColumnSelection before = t.getColumnSelection(false);

      WorksheetMutationSupport.SourceAdmission admission =
         WorksheetMutationSupport.admitSourceFields(t, List.of("Discount"), this::source);
      assertEquals(3, t.getColumnSelection(false).getAttributeCount());

      admission.rollback();

      assertSame(before, t.getColumnSelection(false));
      assertEquals(2, t.getColumnSelection(false).getAttributeCount());
   }

   @Test
   void addFilterOnAdmittedAttributeStoresASourceRefCondition() {
      BoundTableAssembly t = customerTable();
      WorksheetMutationSupport.admitSourceFields(t, List.of("Discount"), this::source);

      WorksheetMutationSupport.addFilter(t, "Discount", "=", "0");

      ConditionList pre = t.getPreConditionList().getConditionList();
      assertEquals(1, pre.getSize());
      assertEquals("Order.Discount", ((ConditionItem) pre.getItem(0)).getAttribute().getName());
   }

   @Test
   void tableWithoutSourceAttributesIsUntouched() {
      BoundTableAssembly t = customerTable();

      WorksheetMutationSupport.admitSourceFields(
         t, List.of("Discount"), () -> new ColumnSelection());

      assertEquals(2, t.getColumnSelection(false).getAttributeCount());
      assertFalse(WorksheetMutationSupport.fieldExists(t, "Discount", false));
   }

   private static BoundTableAssembly customerTable() {
      BoundTableAssembly t = new BoundTableAssembly(new Worksheet(), "T");
      t.setColumnSelection(selection(column("Customer", "State", XSchema.STRING),
                                     column("Customer", "Company", XSchema.STRING)), false);
      return t;
   }

   private ColumnSelection source() {
      return selection(column("Customer", "State", XSchema.STRING),
                       column("Customer", "Company", XSchema.STRING),
                       column("Supplier", "State", XSchema.STRING),
                       column("Order", "Discount", XSchema.DOUBLE));
   }

   private static ColumnSelection selection(ColumnRef... columns) {
      ColumnSelection cs = new ColumnSelection();

      for(ColumnRef column : columns) {
         cs.addAttribute(column);
      }

      return cs;
   }

   private static ColumnRef column(String entity, String attribute, String type) {
      ColumnRef ref = new ColumnRef(new AttributeRef(entity, attribute));
      ref.setDataType(type);
      return ref;
   }
}
