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
import inetsoft.uql.erm.ExpressionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78997, renaming a bound table rewrote the entity-qualified refs of every other table:
 * a leaf table's {@code field['Product.Total']} expression and its group/aggregate refs name a
 * logical-model entity, which only coincides with the renamed assembly's name. Composed tables
 * (mirror/join/...) qualify refs by their source assembly name and must keep being rewritten.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WorksheetRenameEntityCollisionTest {
   @Test
   void renamingBoundTableLeavesLeafTablesEntityRefsUntouched() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly product = boundTable(ws, "Product");
      ExpressionRef exp = addExpression(product, "Total_1", "field['Product.Total']");
      BoundTableAssembly returns = boundTable(ws, "ReturnCategories");
      AggregateInfo ginfo = aggregateInfo("Product");
      returns.setAggregateInfo(ginfo);
      ws.addAssembly(product);
      ws.addAssembly(returns);

      assertTrue(ws.renameAssembly("Product", "LeadRenamed", true));

      assertEquals("field['Product.Total']", exp.getExpression());
      assertEquals("Product", ginfo.getGroup(0).getDataRef().getEntity());
      assertEquals("Product", ginfo.getAggregate(0).getDataRef().getEntity());
      assertEquals(1, ginfo.getGroupCount());
      assertEquals(1, ginfo.getAggregateCount());
   }

   @Test
   void renamingSourceStillRewritesMirrorRefs() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly base = boundTable(ws, "Base");
      ws.addAssembly(base);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M1", base);
      ExpressionRef exp = addExpression(mirror, "Calc", "field['Base.Total']");
      AggregateInfo ginfo = aggregateInfo("Base");
      mirror.setAggregateInfo(ginfo);
      ws.addAssembly(mirror);

      assertTrue(ws.renameAssembly("Base", "BaseRenamed", true));

      assertEquals("field['BaseRenamed.Total']", exp.getExpression());
      assertEquals("BaseRenamed", ginfo.getGroup(0).getDataRef().getEntity());
      assertEquals("BaseRenamed", ginfo.getAggregate(0).getDataRef().getEntity());
   }

   private static BoundTableAssembly boundTable(Worksheet ws, String name) {
      BoundTableAssembly table = new BoundTableAssembly(ws, name);
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef("Product", "Category")));
      columns.addAttribute(new ColumnRef(new AttributeRef("Product", "Total")));
      table.setColumnSelection(columns);
      return table;
   }

   private static ExpressionRef addExpression(TableAssembly table, String name, String script) {
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(script);
      table.getColumnSelection().addAttribute(new ColumnRef(exp));
      return exp;
   }

   private static AggregateInfo aggregateInfo(String entity) {
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(new ColumnRef(new AttributeRef(entity, "Category"))));
      info.addAggregate(new AggregateRef(new ColumnRef(new AttributeRef(entity, "Total")),
                                         AggregateFormula.COUNT_ALL));
      return info;
   }
}
