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
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssemblyOperator;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class CompositeTableAssemblyInfoTest {
   /**
    * Regression test for a query-corruption bug. getOperator(l, r) falls back to the reversed
    * key so a symmetric join can be looked up from either side -- but it used to return the
    * stored operator AS-IS, with its left/right attributes still belonging to the opposite
    * tables.
    *
    * Callers pair the attributes with the tables THEY named. JoinQuery walks the base tables
    * positionally (i < ri) and resolves getLeftAttribute() against tables[i], so a
    * reverse-oriented operator made it qualify the join key with the wrong table: merged SQL
    * came out as `WHERE projects.project_id = work_packages.id` and the database rejected it
    * with `column projects.project_id does not exist`, while the post-processing path found no
    * such column, dropped the condition, and silently produced a cross join.
    *
    * The visible symptom was a worksheet that failed (or returned wrong rows) purely because of
    * the ORDER its base tables happened to be declared in -- the joins themselves were correct.
    */
   @Test
   void reversedLookupReturnsReorientedOperator() {
      CompositeTableAssemblyInfo info = new CompositeTableAssemblyInfo();
      info.setOperator("WORK_PACKAGES", "PROJECTS", innerJoin(
         "WORK_PACKAGES", "project_id", "PROJECTS", "id"));

      // Asked for in the stored orientation: unchanged, and the very same instance.
      TableAssemblyOperator forward = info.getOperator("WORK_PACKAGES", "PROJECTS");
      assertNotNull(forward);
      assertEquals("project_id", attr(forward, true));
      assertEquals("id", attr(forward, false));

      // Asked for in the opposite orientation: the attributes must follow the tables the
      // caller named, otherwise `project_id` gets qualified with PROJECTS.
      TableAssemblyOperator reversed = info.getOperator("PROJECTS", "WORK_PACKAGES");
      assertNotNull(reversed, "a symmetric join must still be findable from either side");
      assertEquals("id", attr(reversed, true), "left attribute must belong to PROJECTS");
      assertEquals("project_id", attr(reversed, false), "right attribute must belong to WORK_PACKAGES");
      assertEquals("PROJECTS", reversed.getOperator(0).getLeftTable());
      assertEquals("WORK_PACKAGES", reversed.getOperator(0).getRightTable());
      assertEquals(TableAssemblyOperator.INNER_JOIN, reversed.getOperator(0).getOperation());
   }

   /** Reversing must not disturb what is stored -- the next lookup still sees the original. */
   @Test
   void reversedLookupLeavesTheStoredOperatorAlone() {
      CompositeTableAssemblyInfo info = new CompositeTableAssemblyInfo();
      info.setOperator("A", "B", innerJoin("A", "b_id", "B", "id"));

      info.getOperator("B", "A");

      TableAssemblyOperator stored = info.getOperator("A", "B");
      assertEquals("b_id", attr(stored, true));
      assertEquals("A", stored.getOperator(0).getLeftTable());
   }

   /** Every child of a multi-key join is reoriented, not just the first. */
   @Test
   void reversedLookupReorientsEveryOperator() {
      CompositeTableAssemblyInfo info = new CompositeTableAssemblyInfo();
      TableAssemblyOperator top = innerJoin("A", "k1", "B", "j1");
      top.addOperator(operator("A", "k2", "B", "j2", TableAssemblyOperator.INNER_JOIN));
      info.setOperator("A", "B", top);

      TableAssemblyOperator reversed = info.getOperator("B", "A");
      assertEquals(2, reversed.getOperatorCount());

      for(int i = 0; i < reversed.getOperatorCount(); i++) {
         assertEquals("B", reversed.getOperator(i).getLeftTable(), "child " + i);
         assertEquals("A", reversed.getOperator(i).getRightTable(), "child " + i);
      }

      assertEquals("j1", attr(reversed, 0, true));
      assertEquals("j2", attr(reversed, 1, true));
   }

   /**
    * An asymmetric join genuinely differs when the tables are swapped, so it must stay
    * unfindable from the reverse side -- reorienting must not turn that null into a result.
    */
   @Test
   void asymmetricJoinIsStillNotReturnedForTheReversedPair() {
      CompositeTableAssemblyInfo info = new CompositeTableAssemblyInfo();
      info.setOperator("A", "B",
                       wrap(operator("A", "b_id", "B", "id", TableAssemblyOperator.LEFT_JOIN)));

      assertNotNull(info.getOperator("A", "B"));
      assertNull(info.getOperator("B", "A"));
   }

   private static TableAssemblyOperator innerJoin(String lt, String lc, String rt, String rc) {
      return wrap(operator(lt, lc, rt, rc, TableAssemblyOperator.INNER_JOIN));
   }

   private static TableAssemblyOperator wrap(TableAssemblyOperator.Operator op) {
      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);
      return top;
   }

   private static TableAssemblyOperator.Operator operator(String lt, String lc, String rt,
                                                          String rc, int operation)
   {
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable(lt);
      op.setRightTable(rt);
      op.setLeftAttribute(new ColumnRef(new AttributeRef(lt, lc)));
      op.setRightAttribute(new ColumnRef(new AttributeRef(rt, rc)));
      op.setOperation(operation);
      return op;
   }

   private static String attr(TableAssemblyOperator top, boolean left) {
      return attr(top, 0, left);
   }

   private static String attr(TableAssemblyOperator top, int i, boolean left) {
      TableAssemblyOperator.Operator op = top.getOperator(i);
      return (left ? op.getLeftAttribute() : op.getRightAttribute()).getAttribute();
   }
}
