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
package inetsoft.uql.asset;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CompositeTableAssemblyTest {
   /**
    * Operators are keyed by ADJACENT pair, so reordering the subtables strands every operator
    * whose two tables stopped being neighbours: {@code A UNION B MINUS C} reordered to
    * {@code C, A, B} leaves nothing stored for the new first pair {@code (C,A)}.
    *
    * <p>Both callers of this method used to patch that up themselves -- read the operators by
    * position, reorder, then write them back by position. That is the right intent (carry the
    * operators over positionally) implemented in the one place it cannot be done safely: writing
    * back does not remove what is already there, so the pre-reorder pairs survived alongside the
    * new ones and the map ended up holding more operators than {@code tnames} can index. Since
    * {@code getOperatorCount()} reports the map's size while {@code getOperator(int)} indexes
    * {@code tnames}, the two then disagree and any caller iterating them together walks off the
    * end of the array -- which reached users as an {@code ArrayIndexOutOfBoundsException} that
    * made an entire worksheet unreadable after a single reorder.</p>
    *
    * <p>So the carry-over belongs here, where the map can be rebuilt rather than added to.</p>
    */
   @Test
   void reorderingSubtablesCarriesOperatorsOverByPositionWithoutStrandingAny() {
      Worksheet ws = new Worksheet();
      TableAssembly a = table(ws, "A");
      TableAssembly b = table(ws, "B");
      TableAssembly c = table(ws, "C");
      // A UNION B MINUS C -- mixed, so a lost or misplaced operator is visible.
      ConcatenatedTableAssembly concat = concat(
         ws, "U", new int[]{ TableAssemblyOperator.UNION, TableAssemblyOperator.MINUS }, a, b, c);

      assertTrue(concat.reorderTableAssemblies(new TableAssembly[]{ c, a, b }));

      assertEquals(List.of("C", "A", "B"), List.of(concat.getTableNames()),
                   "the reorder itself must take effect");
      assertEquals(concat.getTableNames().length - 1, concat.getOperatorCount(),
                   "one operator per adjacent pair -- a larger count means a stale pair survived");

      // Position is what carries over: the first pair keeps the first operation whichever
      // tables now sit there.
      assertEquals(TableAssemblyOperator.UNION, operation(concat.getOperator(0)));
      assertEquals(TableAssemblyOperator.MINUS, operation(concat.getOperator(1)));
   }

   private static int operation(TableAssemblyOperator operator) {
      assertNotNull(operator, "every adjacent pair must have an operator after a reorder");
      return operator.getKeyOperator().getOperation();
   }

   private static TableAssembly table(Worksheet ws, String name) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "col")));
      table.setColumnSelection(cs, false);
      ws.addAssembly(table);

      return table;
   }

   /** One operation per adjacent pair, so a mixed concatenation can be built. */
   private static ConcatenatedTableAssembly concat(Worksheet ws, String name, int[] operations,
                                                   TableAssembly... sources)
   {
      TableAssemblyOperator[] operators = new TableAssemblyOperator[sources.length - 1];

      for(int i = 0; i < operators.length; i++) {
         TableAssemblyOperator top = new TableAssemblyOperator();
         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setOperation(operations[i]);
         top.addOperator(op);
         operators[i] = top;
      }

      ConcatenatedTableAssembly concat =
         new ConcatenatedTableAssembly(ws, name, sources, operators);
      ws.addAssembly(concat);

      return concat;
   }
}
