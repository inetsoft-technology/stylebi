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

   /**
    * Bug #79144: the named-group reference of a leaf table's GroupRef is keyed by assembly name,
    * not by entity, so it must follow a NamedGroupAssembly rename even on leaf tables.
    */
   @Test
   void renamingNamedGroupRewritesLeafTableGroupRef() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly leaf = boundTable(ws, "Leaf");
      AggregateInfo ginfo = aggregateInfo("Product");
      ginfo.getGroup(0).setNamedGroupAssembly("NG");
      leaf.setAggregateInfo(ginfo);
      ws.addAssembly(leaf);
      ws.addAssembly(new DefaultNamedGroupAssembly(ws, "NG"));

      assertTrue(ws.renameAssembly("NG", "NG2", true));

      assertEquals("NG2", ginfo.getGroup(0).getNamedGroupAssembly());
      assertEquals("Product", ginfo.getGroup(0).getDataRef().getEntity());
   }

   /**
    * Bug #79285: a leaf table's script expression that reads a worksheet table by name
    * ({@code Cats[...]}, {@code Cats.length}, {@code Cats['c'][row - 1]}, {@code worksheet['Cats']})
    * names a worksheet assembly, not an entity, so it must follow the rename on leaf tables.
    */
   @Test
   void renamingTableRewritesLeafTableScriptReads() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly cats = boundTable(ws, "Cats");
      BoundTableAssembly leaf = boundTable(ws, "Leaf");
      ExpressionRef lookup =
         addExpression(leaf, "Lookup", "Cats[\"Category? Total==field['Total']\"]");
      ExpressionRef length = addExpression(leaf, "Len", "Cats.length");
      ExpressionRef indexed = addExpression(leaf, "Idx", "Cats['Category'][row - 1]");
      ExpressionRef viaWorksheet = addExpression(leaf, "Via", "worksheet['Cats']['c'][row - 1]");
      ws.addAssembly(cats);
      ws.addAssembly(leaf);

      assertTrue(ws.renameAssembly("Cats", "Cats2", true));

      assertEquals("Cats2[\"Category? Total==field['Total']\"]", lookup.getExpression());
      assertEquals("Cats2.length", length.getExpression());
      assertEquals("Cats2['Category'][row - 1]", indexed.getExpression());
      assertEquals("worksheet['Cats2']['c'][row - 1]", viaWorksheet.getExpression());
   }

   @Test
   void renamingTableLeavesLeafTableNonTableReadsUntouched() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly cats = boundTable(ws, "Cats");
      BoundTableAssembly leaf = boundTable(ws, "Leaf");
      ExpressionRef sameNamedColumn = addExpression(leaf, "Col", "Leaf['Cats']");
      ExpressionRef entity = addExpression(leaf, "Ent", "field['Cats.Total']");
      ExpressionRef fieldColumn = addExpression(leaf, "Fld", "field['Cats']");
      ExpressionRef sql = addExpression(leaf, "Sql", "Cats.length", true);
      ws.addAssembly(cats);
      ws.addAssembly(leaf);

      assertTrue(ws.renameAssembly("Cats", "Cats2", true));

      assertEquals("Leaf['Cats']", sameNamedColumn.getExpression());
      assertEquals("field['Cats.Total']", entity.getExpression());
      assertEquals("field['Cats']", fieldColumn.getExpression());
      assertEquals("Cats.length", sql.getExpression());
   }

   @Test
   void renamingVariableRewritesLeafTableParameterRead() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly leaf = boundTable(ws, "Leaf");
      ExpressionRef exp = addExpression(leaf, "Param", "parameter['V']");
      ExpressionRef column = addExpression(leaf, "Col", "Leaf['V']");
      ws.addAssembly(leaf);
      DefaultVariableAssembly variable = new DefaultVariableAssembly(ws, "V");
      variable.setVariable(new AssetVariable("V"));
      ws.addAssembly(variable);

      assertTrue(ws.renameAssembly("V", "V2", true));

      assertEquals("parameter['V2']", exp.getExpression());
      assertEquals("Leaf['V']", column.getExpression());
   }

   /**
    * Bug #79340: the dot form worksheet.Cats / parameter.V names the assembly, so it follows the
    * rename; a name that is not an identifier is written as a quoted key, and a dot on any other
    * object is a property access that must stay untouched.
    */
   @Test
   void renamingTableRewritesDotFormAndQuotesNonIdentifierNames() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly cats = boundTable(ws, "Cats");
      BoundTableAssembly leaf = boundTable(ws, "Leaf");
      ExpressionRef dot = addExpression(leaf, "Dot", "worksheet.Cats['c'][0]");
      ExpressionRef other = addExpression(leaf, "Other", "obj.Cats['c'][0]");
      ExpressionRef literal = addExpression(leaf, "Lit", "'worksheet.Cats[0]'");
      ExpressionRef bare = addExpression(leaf, "Bare", "Cats['c'][0]");
      ws.addAssembly(cats);
      ws.addAssembly(leaf);

      assertTrue(ws.renameAssembly("Cats", "Cats2", true));

      assertEquals("worksheet.Cats2['c'][0]", dot.getExpression());
      assertEquals("obj.Cats['c'][0]", other.getExpression());
      assertEquals("'worksheet.Cats[0]'", literal.getExpression());

      assertTrue(ws.renameAssembly("Cats2", "Cat Names", true));

      assertEquals("worksheet['Cat Names']['c'][0]", dot.getExpression());
      assertEquals("worksheet['Cat Names']['c'][0]", bare.getExpression());
   }

   @Test
   void renamingTableRewritesJavascriptConditionValue() {
      ExpressionValue js = new ExpressionValue();
      js.setType(ExpressionValue.JAVASCRIPT);
      js.setExpression("worksheet.Cats['id'][1] + Cats['id'][0]");
      ExpressionValue sql = new ExpressionValue();
      sql.setType(ExpressionValue.SQL);
      sql.setExpression("Cats");
      AssetCondition cond = new AssetCondition();
      cond.addValue(js);
      cond.addValue(sql);

      cond.renameDepended("Cats", "Cat Names", new Worksheet());

      assertEquals("worksheet['Cat Names']['id'][1] + worksheet['Cat Names']['id'][0]",
                   js.getExpression());
      assertEquals("Cats", sql.getExpression());
   }

   /**
    * Bug #79340: a name holding a single quote is written as a double-quoted key, a further
    * rename still finds it, and whitespace after the dot is handled.
    */
   @Test
   void renamingTableToNameWithSingleQuoteUsesDoubleQuotedKey() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly cats = boundTable(ws, "Cats");
      BoundTableAssembly leaf = boundTable(ws, "Leaf");
      ExpressionRef bare = addExpression(leaf, "Bare", "Cats['c'][0]");
      ExpressionRef dot = addExpression(leaf, "Dot", "worksheet. Cats['c'][0]");
      ExpressionRef key = addExpression(leaf, "Key", "worksheet['Cats']['c'][0]");
      ws.addAssembly(cats);
      ws.addAssembly(leaf);

      assertTrue(ws.renameAssembly("Cats", "Bob's", true));

      assertEquals("worksheet[\"Bob's\"]['c'][0]", bare.getExpression());
      assertEquals("worksheet[\"Bob's\"]['c'][0]", dot.getExpression());
      assertEquals("worksheet[\"Bob's\"]['c'][0]", key.getExpression());

      assertTrue(ws.renameAssembly("Bob's", "Cat-Names", true));

      assertEquals("worksheet[\"Cat-Names\"]['c'][0]", bare.getExpression());
      assertEquals("worksheet[\"Cat-Names\"]['c'][0]", key.getExpression());
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
      return addExpression(table, name, script, false);
   }

   private static ExpressionRef addExpression(TableAssembly table, String name, String script,
                                              boolean sql)
   {
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(script);
      ColumnRef column = new ColumnRef(exp);
      column.setSQL(sql);
      table.getColumnSelection().addAttribute(column);
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
