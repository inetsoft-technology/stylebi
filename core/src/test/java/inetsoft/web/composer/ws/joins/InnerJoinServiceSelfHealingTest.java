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
package inetsoft.web.composer.ws.joins;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InnerJoinService.joinSourceAndTargetTables's no-auto-match append branch (dragging a table
 * with no matching column onto an existing join/merge-join) stores a transient Operator with
 * setOperation only, the same missing setLeftTable/setRightTable defect as WBS-064's
 * CrossJoinService bug. Unlike CrossJoinService, this transient operator does not survive:
 * editExistingJoinTable, called immediately afterward on the same assembly, removes and rebuilds
 * every edge, and its "tables no longer joined" recovery loop always sets real left/right table
 * names before storing. These tests lock that self-healing in so a future cleanup of the
 * recovery loop (it looks like dead code in isolation) can't silently reintroduce WBS-064's
 * symptom through this second producer.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class InnerJoinServiceSelfHealingTest {
   /**
    * Existing join's own edge is an ordinary keyed inner join. Dragging in an unrelated table
    * with no matching column reproduces the transient null-fielded operator that
    * joinSourceAndTargetTables stores at InnerJoinService.java:300-316; editExistingJoinTable
    * must still leave the new edge fully fielded.
    */
   @Test
   void draggingAnUnmatchedTableOntoAKeyedJoinSelfHeals() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly a = table(ws, "A", "id");
      TableAssembly b = table(ws, "B", "id");
      TableAssembly c = table(ws, "C", "value");

      TableAssemblyOperator existingEdge = new TableAssemblyOperator();
      existingEdge.addOperator(innerJoin("A", "id", "B", "id"));
      RelationalJoinTableAssembly joinTable = new RelationalJoinTableAssembly(
         ws, "JOINED", new TableAssembly[]{ a, b }, new TableAssemblyOperator[]{ existingEdge });
      ws.addAssembly(joinTable);

      InnerJoinService service = new InnerJoinService(null, null);
      TableAssemblyOperator noperator = service.getOperatorsOfJoinTable(joinTable);
      assertEquals(1, noperator.getOperatorCount(), "the pre-existing A-B edge must be captured");

      dragUnmatchedTableInPlace(joinTable, c);
      service.editExistingJoinTable(ws, joinTable, noperator, true);

      assertNewTableEdgeIsFullyFieldedCrossJoin(ws, "C");
      assertEveryStoredOperatorMatchesItsKey(ws);
   }

   /**
    * Existing join's own edge is a keyless CROSS join. getOperatorsOfJoinTable skips cross-join
    * pairs entirely, so noperator is empty and the reconstruction loop does nothing -- the
    * recovery loop alone must rebuild both the original edge and the new one.
    */
   @Test
   void draggingAnUnmatchedTableOntoAKeylessCrossJoinSelfHeals() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly a = table(ws, "A", "id");
      TableAssembly b = table(ws, "B", "id");
      TableAssembly c = table(ws, "C", "value");

      TableAssemblyOperator existingEdge = new TableAssemblyOperator();
      TableAssemblyOperator.Operator crossOp = new TableAssemblyOperator.Operator();
      crossOp.setLeftTable("A");
      crossOp.setRightTable("B");
      crossOp.setOperation(TableAssemblyOperator.CROSS_JOIN);
      existingEdge.addOperator(crossOp);
      RelationalJoinTableAssembly joinTable = new RelationalJoinTableAssembly(
         ws, "JOINED", new TableAssembly[]{ a, b }, new TableAssemblyOperator[]{ existingEdge });
      ws.addAssembly(joinTable);

      InnerJoinService service = new InnerJoinService(null, null);
      TableAssemblyOperator noperator = service.getOperatorsOfJoinTable(joinTable);
      assertEquals(0, noperator.getOperatorCount(),
                   "getOperatorsOfJoinTable skips cross-join pairs entirely");

      dragUnmatchedTableInPlace(joinTable, c);
      service.editExistingJoinTable(ws, joinTable, noperator, true);

      assertNewTableEdgeIsFullyFieldedCrossJoin(ws, "C");
      assertEveryStoredOperatorMatchesItsKey(ws);
   }

   /**
    * Replicates InnerJoinService.java:303-316's !autojoined branch verbatim: builds an Operator
    * with setOperation only (no setLeftTable/setRightTable) and stores it directly on the join
    * table, exactly as joinSourceAndTargetTables does before handing off to
    * editExistingJoinTable.
    */
   private static void dragUnmatchedTableInPlace(
      RelationalJoinTableAssembly targetJoinTable, TableAssembly sourceTable)
   {
      TableAssemblyOperator operator = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.INNER_JOIN);
      operator.addOperator(op);

      TableAssembly[] arr = targetJoinTable.getTableAssemblies(true);
      TableAssembly[] narr = new TableAssembly[arr.length + 1];
      System.arraycopy(arr, 0, narr, 0, arr.length);
      narr[arr.length] = sourceTable;
      targetJoinTable.setOperator(narr[narr.length - 2].getName(),
                                  narr[narr.length - 1].getName(), operator);
      targetJoinTable.setTableAssemblies(narr);
   }

   private static void assertNewTableEdgeIsFullyFieldedCrossJoin(Worksheet ws, String newTable) {
      RelationalJoinTableAssembly joinTable = joinTableOf(ws);

      for(Enumeration<?> e = joinTable.getOperatorTables(); e.hasMoreElements(); ) {
         String[] pair = (String[]) e.nextElement();

         if(newTable.equals(pair[0]) || newTable.equals(pair[1])) {
            TableAssemblyOperator top = joinTable.getOperator(pair[0], pair[1]);
            assertNotNull(top);
            TableAssemblyOperator.Operator op = top.getOperator(0);
            assertNotNull(op.getLeftTable(), "leftTable must not be null after self-healing");
            assertNotNull(op.getRightTable(), "rightTable must not be null after self-healing");
            assertEquals(TableAssemblyOperator.CROSS_JOIN, op.getOperation());
            return;
         }
      }

      fail("no edge to " + newTable + " found after editExistingJoinTable");
   }

   private static void assertEveryStoredOperatorMatchesItsKey(Worksheet ws) {
      RelationalJoinTableAssembly joinTable = joinTableOf(ws);

      for(Enumeration<?> e = joinTable.getOperatorTables(); e.hasMoreElements(); ) {
         String[] pair = (String[]) e.nextElement();
         TableAssemblyOperator top = joinTable.getOperator(pair[0], pair[1]);
         assertNotNull(top, "no operator for (" + pair[0] + "," + pair[1] + ")");
         assertNotNull(top.getOperator(0).getLeftTable());
         assertNotNull(top.getOperator(0).getRightTable());
      }
   }

   /** The join assembly, read back out of the worksheet because concatenateTable can replace it. */
   private static RelationalJoinTableAssembly joinTableOf(Worksheet ws) {
      RelationalJoinTableAssembly joinTable = null;

      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly instanceof RelationalJoinTableAssembly) {
            joinTable = (RelationalJoinTableAssembly) assembly;
         }
      }

      assertNotNull(joinTable, "no join table in the worksheet");
      return joinTable;
   }

   private static TableAssemblyOperator.Operator innerJoin(String lt, String lc,
                                                           String rt, String rc)
   {
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable(lt);
      op.setRightTable(rt);
      op.setLeftAttribute(new ColumnRef(new AttributeRef(lt, lc)));
      op.setRightAttribute(new ColumnRef(new AttributeRef(rt, rc)));
      op.setOperation(TableAssemblyOperator.INNER_JOIN);
      return op;
   }

   private static TableAssembly table(Worksheet ws, String name, String... columns) {
      EmbeddedTableAssembly assembly = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cols = new ColumnSelection();

      for(String column : columns) {
         cols.addAttribute(new ColumnRef(new AttributeRef(name, column)));
      }

      assembly.setColumnSelection(cols, false);
      assembly.setColumnSelection(cols, true);
      ws.addAssembly(assembly);
      return assembly;
   }
}
