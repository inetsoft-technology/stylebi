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
package inetsoft.web.composer.ws;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RenameColumnControllerTest {
   /**
    * Renaming a column must carry into the join condition of every assembly built on it, or the
    * join goes on referencing a column name that no longer exists.
    *
    * <p>Written while investigating the table-pair loop in {@code renameTableColumn0}, whose bound
    * counts OPERATORS while its indices address TABLES -- one short, so the last table is never
    * reached and a two-table join never enters the body at all. The investigation concluded that
    * loop is <b>not</b> what makes this work: with it disabled outright, both tests here still
    * pass, so the propagation comes from elsewhere and the wrong bound has no observable effect.
    * These tests pin the <em>behaviour</em>, not that loop -- and they are what makes it safe to
    * leave the bound alone.</p>
    *
    * <p>Driven through the public entry point deliberately. A rename is applied as an ALIAS on the
    * existing column and the ref that gets propagated is the very object read back out of the base
    * table's selection, so hand-building a separate "renamed" ColumnRef tests a state the product
    * never produces -- the join validity check rightly rejects it as a cross join. Making exactly
    * that mistake is what first made the loop look guilty.</p>
    */
   @Test
   void renamingABaseColumnUpdatesATwoTableJoinsOperator() {
      Worksheet ws = new Worksheet();
      TableAssembly a = table(ws, "A", "col1");
      TableAssembly b = table(ws, "B", "col2");

      TableAssemblyOperator op = innerJoin("A", "col1", "B", "col2");
      join(ws, "J", new TableAssembly[]{ a, b }, op);

      boolean failed = RenameColumnController.renameColumn(
         ws, mock(CommandDispatcher.class), a, column("A", "col1"), "renamed");

      assertFalse(failed, "the rename itself must succeed");
      assertEquals("renamed", leftName(op),
                   "the join's left attribute must follow the rename");
   }

   /**
    * The three-table case, kept because it covers the pair the suspect loop could never reach even
    * in principle (with three tables its bound is 2, so it stops after pair {@code (0,1)}). It
    * passing is the second half of the evidence that the loop is not carrying this behaviour.
    */
   @Test
   void renamingABaseColumnUpdatesTheLastPairOfAThreeTableJoin() {
      Worksheet ws = new Worksheet();
      TableAssembly a = table(ws, "A", "col1");
      TableAssembly b = table(ws, "B", "col2");
      TableAssembly c = table(ws, "C", "col3");

      TableAssemblyOperator ab = innerJoin("A", "col1", "B", "col2");
      // The pair involving the LAST table, and the one carrying the column being renamed.
      TableAssemblyOperator bc = innerJoin("B", "col2", "C", "col3");
      join(ws, "J", new TableAssembly[]{ a, b, c }, ab, bc);

      boolean failed = RenameColumnController.renameColumn(
         ws, mock(CommandDispatcher.class), c, column("C", "col3"), "renamed");

      assertFalse(failed, "the rename itself must succeed");
      assertEquals("renamed", rightName(bc),
                   "the last pair's attribute must follow the rename too");
   }

   private static TableAssembly table(Worksheet ws, String name, String... cols) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      table.setColumnSelection(selection(name, cols), false);
      ws.addAssembly(table);

      return table;
   }

   /** The join must expose the renamed column, or renameTableColumn0 returns before the loop. */
   private static void join(Worksheet ws, String name, TableAssembly[] tables,
                            TableAssemblyOperator... operators)
   {
      RelationalJoinTableAssembly j =
         new RelationalJoinTableAssembly(ws, name, tables, operators);
      ColumnSelection cs = new ColumnSelection();

      for(TableAssembly table : tables) {
         ColumnSelection tcs = table.getColumnSelection();

         for(int i = 0; i < tcs.getAttributeCount(); i++) {
            cs.addAttribute(tcs.getAttribute(i));
         }
      }

      j.setColumnSelection(cs, false);
      ws.addAssembly(j);
   }

   private static ColumnSelection selection(String entity, String... cols) {
      ColumnSelection cs = new ColumnSelection();

      for(String col : cols) {
         cs.addAttribute(column(entity, col));
      }

      return cs;
   }

   private static ColumnRef column(String entity, String name) {
      return new ColumnRef(new AttributeRef(entity, name));
   }

   private static TableAssemblyOperator innerJoin(String lt, String lc, String rt, String rc) {
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable(lt);
      op.setRightTable(rt);
      op.setLeftAttribute(column(lt, lc));
      op.setRightAttribute(column(rt, rc));
      op.setOperation(TableAssemblyOperator.INNER_JOIN);

      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);

      return top;
   }

   private static String leftName(TableAssemblyOperator top) {
      return effectiveName(top.getOperator(0).getLeftAttribute());
   }

   private static String rightName(TableAssemblyOperator top) {
      return effectiveName(top.getOperator(0).getRightAttribute());
   }

   /**
    * A rename sets an alias and leaves the underlying attribute alone, so the alias is the name
    * that matters -- the same rule the production code applies when it decides what a column is
    * currently called.
    */
   private static String effectiveName(DataRef ref) {
      String alias = ref instanceof ColumnRef column ? column.getAlias() : null;
      return alias == null || alias.isEmpty() ? ref.getAttribute() : alias;
   }
}
