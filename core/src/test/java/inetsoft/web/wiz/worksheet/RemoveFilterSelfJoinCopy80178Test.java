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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.removeFilter;
import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.replaceFilter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80178 (wbs-246): on a self-join the copy column CUSTOMER_ID_1 is the base column
 * CUSTOMER_ID under an alias, so its {@code getAttribute()} is also CUSTOMER_ID. On a real join
 * the base column's {@code getName()} is entity-qualified (ORDERS.CUSTOMER_ID), so the
 * conditions are named by their output name (alias, else bare attribute), never by getName().
 */
@Tag("core")
@WizAgentTestSupport
class RemoveFilterSelfJoinCopy80178Test {
   private static final String BASE = "CUSTOMER_ID";
   private static final String COPY = "CUSTOMER_ID_1";

   /**
    * @param entities true builds the refs like a real join (entity = sub-table name on both),
    *                 false builds them with a null entity
    * @param conds    ordered conditions as "base:>:1", "copy:=:2"; AND-joined
    */
   private static BoundTableAssembly table(boolean entities, String... conds) {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = new BoundTableAssembly(ws, "T");
      ColumnRef original = new ColumnRef(new AttributeRef(entities ? "ORDERS" : null, BASE));
      ColumnRef copy = new ColumnRef(new AttributeRef(entities ? "ORDERS1" : null, BASE));
      copy.setAlias(COPY);
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(original);
      cs.addAttribute(copy);
      t.setColumnSelection(cs, false);

      ConditionList cl = new ConditionList();

      for(String spec : conds) {
         String[] p = spec.split(":");

         if(!cl.isEmpty()) {
            cl.append(new JunctionOperator(JunctionOperator.AND, 0));
         }

         cl.append(item("base".equals(p[0]) ? original : copy, Integer.parseInt(p[1]), p[2]));
      }

      t.setPreConditionList(cl);
      ws.addAssembly(t);
      return t;
   }

   private static ConditionItem item(ColumnRef ref, int op, String value) {
      Condition c = new Condition(XSchema.STRING);
      c.setOperation(op);
      c.addValue(value);
      return new ConditionItem(ref, c, 0);
   }

   /** Output names (alias else bare attribute) and operation of the survivors. */
   private static String remaining(BoundTableAssembly t) {
      ConditionListWrapper w = t.getPreConditionList();

      if(w == null || w.isEmpty()) {
         return "";
      }

      ConditionList cl = w.getConditionList();
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < cl.getSize(); i++) {
         if(cl.getItem(i) instanceof ConditionItem ci) {
            ColumnRef cr = (ColumnRef) ci.getAttribute();
            String out = cr.getAlias() != null ? cr.getAlias() : cr.getAttribute();
            sb.append(out).append(((Condition) ci.getXCondition()).getOperation())
               .append(';');
         }
      }

      return sb.toString();
   }

   private static final int EQ = XCondition.EQUAL_TO;
   private static final int GT = XCondition.GREATER_THAN;
   private static final int LT = XCondition.LESS_THAN;

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void removingTheBaseNameKeepsTheCopysConditionBothOrders(boolean entities) {
      BoundTableAssembly t = table(entities, "base:" + EQ + ":1", "copy:" + GT + ":2");
      removeFilter(t, BASE);
      assertEquals(COPY + GT + ";", remaining(t));

      t = table(entities, "copy:" + GT + ":2", "base:" + EQ + ":1");
      removeFilter(t, BASE);
      assertEquals(COPY + GT + ";", remaining(t));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void removingTheCopyNameKeepsTheBaseConditionBothOrders(boolean entities) {
      BoundTableAssembly t = table(entities, "base:" + EQ + ":1", "copy:" + GT + ":2");
      removeFilter(t, COPY);
      assertEquals(BASE + EQ + ";", remaining(t));

      t = table(entities, "copy:" + GT + ":2", "base:" + EQ + ":1");
      removeFilter(t, COPY);
      assertEquals(BASE + EQ + ";", remaining(t));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void removingTheBaseNameRemovesEveryBaseConditionAndKeepsTheCopy(boolean entities) {
      BoundTableAssembly t = table(entities,
         "base:" + GT + ":1", "base:" + LT + ":9", "copy:" + EQ + ":5");
      removeFilter(t, BASE);
      assertEquals(COPY + EQ + ";", remaining(t));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void aCopyOnlyFilterIsNotTakenForTheBaseColumn(boolean entities) {
      BoundTableAssembly t = table(entities, "copy:" + GT + ":2");
      removeFilter(t, BASE);
      assertEquals(COPY + GT + ";", remaining(t));
   }

   @Test
   void aBaseOnlyFilterIsRemovedByItsName() {
      BoundTableAssembly t = table(true, "base:" + EQ + ":1");
      removeFilter(t, BASE);
      assertEquals("", remaining(t));
   }

   @Test
   void theEntityQualifiedNameStillNamesTheBaseCondition() {
      BoundTableAssembly t = table(true, "base:" + EQ + ":1", "copy:" + GT + ":2");
      removeFilter(t, "ORDERS." + BASE);
      assertEquals(COPY + GT + ";", remaining(t));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void replaceFilterOnTheBaseNameEditsOnlyTheBaseCondition(boolean entities) {
      BoundTableAssembly t = table(entities, "base:" + EQ + ":1", "copy:" + GT + ":2");
      replaceFilter(t, BASE, ">", "9");
      assertEquals(BASE + GT + ";" + COPY + GT + ";", remaining(t));
   }

   @Test
   void replaceFilterOnTheCopyNameEditsOnlyTheCopyCondition() {
      BoundTableAssembly t = table(true, "base:" + EQ + ":1", "copy:" + GT + ":2");
      replaceFilter(t, COPY, "<", "9");
      assertEquals(BASE + EQ + ";" + COPY + LT + ";", remaining(t));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void replaceFilterWithOnlyTheCopyFilteredDoesNotMoveItToTheBaseColumn(boolean entities) {
      BoundTableAssembly t = table(entities, "copy:" + GT + ":2");
      replaceFilter(t, BASE, "=", "9");
      // The copy's condition is untouched; the base column gets its own new condition.
      assertEquals(COPY + GT + ";" + BASE + EQ + ";", remaining(t));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void replaceFilterStillRefusesWhenTwoConditionsOnTheSameColumnMatch(boolean entities) {
      BoundTableAssembly t = table(entities,
         "base:" + GT + ":1", "base:" + LT + ":9", "copy:" + EQ + ":5");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> replaceFilter(t, BASE, "=", "9"));

      assertTrue(e.getMessage().contains("2 conditions"), e.getMessage());
      assertEquals(BASE + GT + ";" + BASE + LT + ";" + COPY + EQ + ";", remaining(t));
   }

   /** A single column renamed by rename_column: alias CUST over attribute CUSTOMER_ID. */
   private static BoundTableAssembly renamed(boolean entity, int... ops) {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = new BoundTableAssembly(ws, "T");
      ColumnRef col = new ColumnRef(new AttributeRef(entity ? "ORDERS" : null, BASE));
      col.setAlias("CUST");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(col);
      t.setColumnSelection(cs, false);
      ConditionList cl = new ConditionList();

      for(int op : ops) {
         if(!cl.isEmpty()) {
            cl.append(new JunctionOperator(JunctionOperator.AND, 0));
         }

         cl.append(item(col, op, "1"));
      }

      t.setPreConditionList(cl);
      ws.addAssembly(t);
      return t;
   }

   @ParameterizedTest
   @ValueSource(strings = { BASE, "CUST" })
   void aRenamedColumnIsRemovedByItsOldAndItsNewName(String name) {
      BoundTableAssembly t = renamed(false, EQ);
      removeFilter(t, name);
      assertEquals("", remaining(t));
   }

   @ParameterizedTest
   @ValueSource(strings = { BASE, "CUST" })
   void aRenamedColumnIsReplacedInPlaceByItsOldAndItsNewName(String name) {
      BoundTableAssembly t = renamed(false, EQ);
      replaceFilter(t, name, ">", "9");
      assertEquals("CUST" + GT + ";", remaining(t));
   }

   @ParameterizedTest
   @ValueSource(strings = { BASE, "CUST" })
   void everyConditionOnARenamedColumnGoesByEitherName(String name) {
      BoundTableAssembly t = renamed(false, GT, LT);
      removeFilter(t, name);
      assertEquals("", remaining(t));
   }

   @Test
   void aRenamedColumnWithAnEntityIsRemovedByItsNewName() {
      BoundTableAssembly t = renamed(true, EQ);
      removeFilter(t, "CUST");
      assertEquals("", remaining(t));
   }
}
