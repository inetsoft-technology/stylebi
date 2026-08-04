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
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A join operator's left/right ATTRIBUTES must stay on the same side as the tables it is stored
 * under. If they diverge, JoinQuery resolves the left attribute against the positionally-left
 * table, misses, and qualifies the key with the wrong table -- emitting SQL like
 * `column projects.project_id does not exist` when the query is merged, and silently dropping
 * the condition into a cross join on the post-processing path.
 *
 * The divergence came from a DOUBLE FLIP: editExistingJoinTable snapshots the left/right table
 * names off the incoming operators, then concatenateTable re-orients those operator objects IN
 * PLACE (via exchange()) to match the assembly it builds. addOperator then compared the stale
 * snapshot against the subtable order, concluded it had to flip, and flipped an operator that
 * was already correct.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class InnerJoinServiceOperatorOrientationTest {
   /**
    * The regression. Base tables are declared [PROJECTS, WORK_PACKAGES, STATUSES] while both join
    * paths drive from WORK_PACKAGES, so the PROJECTS<->WORK_PACKAGES operator is the one whose
    * declared order runs opposite to the table array -- exactly the shape that failed live on
    * openproject. Whichever way round each operator ends up being stored, its attributes must
    * agree with its key.
    */
   @Test
   void operatorAttributesStayOnTheSameSideAsTheirTables() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly projects = table(ws, "PROJECTS", "id", "name");
      TableAssembly workPackages = table(ws, "WORK_PACKAGES", "id", "project_id", "status_id");
      TableAssembly statuses = table(ws, "STATUSES", "id", "status_name");

      // Declaration order puts PROJECTS first even though nothing joins FROM it.
      RelationalJoinTableAssembly joinTable = new RelationalJoinTableAssembly(
         ws, "JOINED", new TableAssembly[]{ projects, workPackages, statuses },
         new TableAssemblyOperator[0]);
      ws.addAssembly(joinTable);

      TableAssemblyOperator noperator = new TableAssemblyOperator();
      noperator.addOperator(innerJoin("WORK_PACKAGES", "status_id", "STATUSES", "id"));
      noperator.addOperator(innerJoin("WORK_PACKAGES", "project_id", "PROJECTS", "id"));

      new InnerJoinService(null, null).editExistingJoinTable(ws, joinTable, noperator, true);

      assertOperatorsConsistent(ws, 2);
   }

   /**
    * The orientation that always worked must keep working -- guards against "fixing" the bug by
    * flipping unconditionally, which would just move the breakage to the other declaration order.
    */
   @Test
   void alreadyConsistentDeclarationOrderIsUnaffected() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly workPackages = table(ws, "WORK_PACKAGES", "id", "project_id", "status_id");
      TableAssembly projects = table(ws, "PROJECTS", "id", "name");
      TableAssembly statuses = table(ws, "STATUSES", "id", "status_name");

      RelationalJoinTableAssembly joinTable = new RelationalJoinTableAssembly(
         ws, "JOINED", new TableAssembly[]{ workPackages, projects, statuses },
         new TableAssemblyOperator[0]);
      ws.addAssembly(joinTable);

      TableAssemblyOperator noperator = new TableAssemblyOperator();
      noperator.addOperator(innerJoin("WORK_PACKAGES", "status_id", "STATUSES", "id"));
      noperator.addOperator(innerJoin("WORK_PACKAGES", "project_id", "PROJECTS", "id"));

      new InnerJoinService(null, null).editExistingJoinTable(ws, joinTable, noperator, true);

      assertOperatorsConsistent(ws, 2);
   }

   /**
    * An outer join must keep preserving the same TABLE after the engine re-orients it. exchange()
    * used to swap the operands while leaving the operation alone, so `STATUSES LEFT JOIN
    * WORK_PACKAGES` declared as [WORK_PACKAGES, STATUSES] came back as `WORK_PACKAGES LEFT JOIN
    * STATUSES` -- no error, just silently wrong rows (verified live on openproject: 10 statuses
    * instead of 14, the four with no work packages dropped).
    *
    * Whichever way round it ends up stored, LEFT must still preserve STATUSES: either
    * `STATUSES LEFT JOIN WORK_PACKAGES` or the equivalent `WORK_PACKAGES RIGHT JOIN STATUSES`.
    */
   @Test
   void anOuterJoinKeepsPreservingTheSameTableAfterReorientation() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly workPackages = table(ws, "WORK_PACKAGES", "id", "status_id");
      TableAssembly statuses = table(ws, "STATUSES", "id", "status_name");

      // The referenced table is declared SECOND, which is what triggers the re-orientation.
      RelationalJoinTableAssembly joinTable = new RelationalJoinTableAssembly(
         ws, "JOINED", new TableAssembly[]{ workPackages, statuses },
         new TableAssemblyOperator[0]);
      ws.addAssembly(joinTable);

      TableAssemblyOperator.Operator leftJoin =
         innerJoin("STATUSES", "id", "WORK_PACKAGES", "status_id");
      leftJoin.setOperation(TableAssemblyOperator.LEFT_JOIN);
      TableAssemblyOperator noperator = new TableAssemblyOperator();
      noperator.addOperator(leftJoin);

      new InnerJoinService(null, null).editExistingJoinTable(ws, joinTable, noperator, true);

      assertOperatorsConsistent(ws, 1);

      TableAssemblyOperator.Operator stored = soleOperator(ws);
      assertTrue(stored.getOperation() == TableAssemblyOperator.LEFT_JOIN
                    || stored.getOperation() == TableAssemblyOperator.RIGHT_JOIN,
                 "the join must still be an outer join, not degraded to inner");

      String preserved = stored.getOperation() == TableAssemblyOperator.LEFT_JOIN
         ? stored.getLeftTable()
         : stored.getRightTable();
      assertEquals("STATUSES", preserved,
                   "LEFT/RIGHT must still preserve STATUSES, whichever side it ended up on");
   }

   /** The single stored operator, for a two-table join. */
   private static TableAssemblyOperator.Operator soleOperator(Worksheet ws) {
      RelationalJoinTableAssembly joinTable = joinTableOf(ws);
      Enumeration<?> e = joinTable.getOperatorTables();
      assertTrue(e.hasMoreElements(), "no operator pair stored");
      String[] pair = (String[]) e.nextElement();
      TableAssemblyOperator top = joinTable.getOperator(pair[0], pair[1]);
      assertNotNull(top);
      assertEquals(1, top.getOperatorCount());
      return top.getOperator(0);
   }

   /**
    * Walk every stored (ltable, rtable) pair and check the operator agrees with its own key: the
    * left attribute must be a column of the left table and the right attribute a column of the
    * right table. Reads the join assembly back out of the worksheet because concatenateTable can
    * replace it.
    */
   private static void assertOperatorsConsistent(Worksheet ws, int expectedConditions) {
      RelationalJoinTableAssembly joinTable = joinTableOf(ws);
      int checked = 0;

      for(Enumeration<?> e = joinTable.getOperatorTables(); e.hasMoreElements(); ) {
         String[] pair = (String[]) e.nextElement();
         TableAssemblyOperator top = joinTable.getOperator(pair[0], pair[1]);
         assertNotNull(top, "no operator for (" + pair[0] + "," + pair[1] + ")");

         for(int i = 0; i < top.getOperatorCount(); i++) {
            TableAssemblyOperator.Operator op = top.getOperator(i);
            String where = "operator " + i + " stored under (" + pair[0] + "," + pair[1] + ")";

            assertEquals(pair[0], op.getLeftTable(), where + ": leftTable");
            assertEquals(pair[1], op.getRightTable(), where + ": rightTable");
            // The attributes are what JoinQuery actually resolves; carrying the right table names
            // with swapped columns is the exact shape of the bug.
            assertEquals(pair[0], entityOf(op.getLeftAttribute()), where + ": left attribute owner");
            assertEquals(pair[1], entityOf(op.getRightAttribute()), where + ": right attribute owner");
            checked++;
         }
      }

      assertEquals(expectedConditions, checked, "expected every join condition to be stored");
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

   /** The table an attribute belongs to, as encoded by AttributeRef's entity. */
   private static String entityOf(DataRef ref) {
      return ref == null ? null : ref.getEntity();
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
}
