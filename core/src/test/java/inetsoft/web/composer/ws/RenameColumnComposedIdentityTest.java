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
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.CompositeTableAssemblyInfo;
import inetsoft.uql.erm.*;
import inetsoft.util.MessageException;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78234 (WBS-103): renaming a column of a table that feeds a composed table (concat, join,
 * merge join, cross join, mirror) must re-point the composed table's column that actually comes
 * FROM the renamed table, identified by source entity -- never "whichever column has the same bare
 * name", which on a multi-source table can belong to a different source.
 *
 * <p>Composed selections are built the way the product builds them: a mirror by its own
 * {@link MirrorTableAssembly#updateColumnSelection()}, a concat by
 * {@link ConcatenatedTableAssembly#initDefaultColumnSelection()}, and a join exactly as
 * {@code JoinQuery.getDefaultColumnSelection0} does ({@link AssetUtil#getOuterAttribute} per source
 * column, then {@link AssetUtil#fixAlias}) -- that builder needs a running query, and a join of
 * embedded tables is otherwise left with an empty selection outside a data refresh.</p>
 *
 * <p>Policy (decided for this bug): an existing composed-column alias is the composed table's
 * output name and is KEPT ({@code Product_1} stays {@code Product_1}, its attribute is re-pointed).
 * A rename that newly collides with a sibling gives ONLY the renamed column a unique alias.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RenameColumnComposedIdentityTest {
   // ---------------------------------------------------------------- 1. mirror (regression guard)

   @Test
   void mirrorStillFollowsARenameWithConditionSortAndGroup() {
      Worksheet ws = new Worksheet();
      TableAssembly t = source(ws, "T", "Region", "Amount");
      MirrorTableAssembly m = mirror(ws, "M", t);
      ColumnRef amount = col(m, "Amount");
      filter(m, amount);
      sort(m, amount);
      group(m, amount);

      rename(ws, t, "Amount", "Total");

      assertEquals(List.of("Region", "Total"), names(m));
      assertEquals("Total", filterAttr(m).getAttribute());
      assertEquals("Total", m.getSortInfo().getSorts()[0].getDataRef().getAttribute());
      assertEquals("Total", m.getAggregateInfo().getGroups()[0].getDataRef().getAttribute());
   }

   @Test
   void mirrorOfAMirrorChainFollowsARename() {
      Worksheet ws = new Worksheet();
      TableAssembly t = source(ws, "T", "Region", "Amount");
      MirrorTableAssembly m = mirror(ws, "M", t);
      MirrorTableAssembly mm = mirror(ws, "MM", m);
      filter(m, col(m, "Amount"));
      filter(mm, col(mm, "Amount"));
      sort(mm, col(mm, "Amount"));

      rename(ws, t, "Amount", "Total");

      assertEquals(List.of("Region", "Total"), names(m));
      assertEquals(List.of("Region", "Total"), names(mm));
      assertEquals("M", col(mm, "Total").getEntity());
      assertEquals("Total", filterAttr(m).getAttribute());
      assertEquals("Total", filterAttr(mm).getAttribute());
      assertEquals("Total", mm.getSortInfo().getSorts()[0].getDataRef().getAttribute());
   }

   // ------------------------------------------------------- 2-4. concat, non-first source renamed

   @ParameterizedTest
   @ValueSource(ints = { TableAssemblyOperator.UNION, TableAssemblyOperator.INTERSECT,
                         TableAssemblyOperator.MINUS })
   void concatIgnoresARenameOfANonFirstSource(int operation) {
      Worksheet ws = new Worksheet();
      TableAssembly a = source(ws, "A", "Region", "Product", "Amount");
      TableAssembly b = source(ws, "B", "Region", "Product", "Amount");
      ConcatenatedTableAssembly c = concat(ws, "C", operation, a, b);
      filter(c, col(c, "Product"));

      rename(ws, b, "Product", "Item");

      assertEquals(List.of("Region", "Product", "Amount"), names(c),
                   "a concat's output names follow its FIRST source only");
      assertEquals("A", col(c, "Product").getEntity());
      assertEquals("A", filterAttr(c).getEntity());
      assertEquals("Product", filterAttr(c).getAttribute(),
                   "the concat's filter on Product must survive a second-source rename");
   }

   // -------------------------------------------------------------- 5. concat, first source renamed

   @ParameterizedTest
   @ValueSource(ints = { TableAssemblyOperator.UNION, TableAssemblyOperator.INTERSECT,
                         TableAssemblyOperator.MINUS })
   void concatFollowsARenameOfItsFirstSource(int operation) {
      Worksheet ws = new Worksheet();
      TableAssembly a = source(ws, "A", "Region", "Product", "Amount");
      TableAssembly b = source(ws, "B", "Region", "Product", "Amount");
      ConcatenatedTableAssembly c = concat(ws, "C", operation, a, b);
      filter(c, col(c, "Product"));

      rename(ws, a, "Product", "Item");

      assertEquals(List.of("Region", "Item", "Amount"), names(c));
      assertEquals("Item", filterAttr(c).getAttribute());
   }

   // ---------------------------------------------------------- 6-7. concat -> downstream mirror

   @Test
   void concatsDownstreamMirrorIgnoresANonFirstSourceRename() {
      Worksheet ws = new Worksheet();
      TableAssembly a = source(ws, "A", "Region", "Product", "Amount");
      TableAssembly b = source(ws, "B", "Region", "Product", "Amount");
      ConcatenatedTableAssembly c = concat(ws, "C", TableAssemblyOperator.UNION, a, b);
      MirrorTableAssembly pd = mirror(ws, "PD", c);
      filter(pd, col(pd, "Product"));

      rename(ws, b, "Product", "Item");

      assertEquals(List.of("Region", "Product", "Amount"), names(pd),
                   "the mirror's columns and their order must be untouched");
      assertEquals("Product", filterAttr(pd).getAttribute());
   }

   /**
    * Also the WBS-094 shape: the concat's filter attribute is the concat's own live selection
    * column object, which the condition rename mutates in place -- the downstream recursion must
    * still find PD's column by the concat column's PRE-rename identity.
    */
   @Test
   void concatsDownstreamMirrorFollowsAFirstSourceRename() {
      Worksheet ws = new Worksheet();
      TableAssembly a = source(ws, "A", "Region", "Product", "Amount");
      TableAssembly b = source(ws, "B", "Region", "Product", "Amount");
      ConcatenatedTableAssembly c = concat(ws, "C", TableAssemblyOperator.UNION, a, b);
      filter(c, col(c, "Product"));
      MirrorTableAssembly pd = mirror(ws, "PD", c);
      filter(pd, col(pd, "Product"));

      rename(ws, a, "Product", "Item");

      assertEquals(List.of("Region", "Item", "Amount"), names(c));
      assertEquals(List.of("Region", "Item", "Amount"), names(pd));
      assertEquals("C", col(pd, "Item").getEntity());
      assertEquals("Item", filterAttr(c).getAttribute());
      assertEquals("Item", filterAttr(pd).getAttribute());
   }

   // ------------------------------------------------- 8, 11. join with a same-named column per side

   @ParameterizedTest
   @ValueSource(strings = { "inner", "left", "cross", "merge" })
   void joinRenamesOnlyTheRenamedSidesColumnAndKeepsItsAlias(String kind) {
      Worksheet ws = new Worksheet();
      TableAssembly ja = source(ws, "JA", "Region", "Product", "Amount");
      TableAssembly jb = source(ws, "JB", "Region", "Product");
      ComposedTableAssembly j = join(ws, "PJ", kind, ja, jb);
      assertEquals(List.of("Region", "Product", "Amount", "Region_1", "Product_1"), names(j),
                   "precondition: the builder aliases the second side's duplicates");
      filter(j, col(j, "Product"));

      rename(ws, jb, "Product", "Item");

      assertEquals(List.of("Region", "Product", "Amount", "Region_1", "Product_1"), names(j),
                   "output names and order unchanged: JA's Product untouched, JB's alias kept");
      ColumnRef jaProduct = col(j, "Product");
      assertEquals("JA", jaProduct.getEntity());
      assertEquals("Product", jaProduct.getAttribute());
      ColumnRef jbProduct = col(j, "Product_1");
      assertEquals("JB", jbProduct.getEntity());
      assertEquals("Item", jbProduct.getAttribute(),
                   "JB's column must be re-pointed to the renamed source column");
      assertEquals("JA", filterAttr(j).getEntity());
      assertEquals("Product", filterAttr(j).getAttribute(),
                   "the filter on JA's Product must survive a rename of JB's Product");
   }

   // ------------------------------------------------------------------ 9. join, first side renamed

   @Test
   void joinRenamesTheFirstSideWithoutTouchingTheAliasedSecondSide() {
      Worksheet ws = new Worksheet();
      TableAssembly ja = source(ws, "JA", "Region", "Product", "Amount");
      TableAssembly jb = source(ws, "JB", "Region", "Product");
      ComposedTableAssembly j = join(ws, "PJ", "inner", ja, jb);
      filter(j, col(j, "Product"));

      rename(ws, ja, "Product", "Item");

      // Pinned: fixAlias is not re-run over existing columns, so JB's Product_1 keeps its alias
      // even though the bare name "Product" is now free.
      assertEquals(List.of("Region", "Item", "Amount", "Region_1", "Product_1"), names(j));
      assertEquals("JA", col(j, "Item").getEntity());
      assertEquals("JB", col(j, "Product_1").getEntity());
      assertEquals("Product", col(j, "Product_1").getAttribute());
      assertEquals("JA", filterAttr(j).getEntity());
      assertEquals("Item", filterAttr(j).getAttribute());
   }

   // ------------------------------------------------------------------- 10. user alias preserved

   @Test
   void joinKeepsAUserAliasAndItsDownstreamMirrorIsUntouched() {
      Worksheet ws = new Worksheet();
      TableAssembly ja = source(ws, "JA", "Region", "Product");
      TableAssembly jb = source(ws, "JB", "Region", "Amount");
      ComposedTableAssembly j = join(ws, "PJ", "inner", ja, jb);
      col(j, "Product").setAlias("Prod");
      j.setColumnSelection(j.getColumnSelection(false).clone(), true);
      MirrorTableAssembly mj = mirror(ws, "MJ", j);
      filter(mj, col(mj, "Prod"));
      List<String> before = names(mj);

      rename(ws, ja, "Product", "Item");

      ColumnRef prod = col(j, "Prod");
      assertEquals("JA", prod.getEntity());
      assertEquals("Item", prod.getAttribute());
      assertEquals(before, names(mj), "the join's output name did not change: no propagation");
      assertEquals("Prod", filterAttr(mj).getAttribute());
   }

   // ------------------------------------------------------------- 12. rename collides in the join

   @Test
   void joinAliasesOnlyTheRenamedColumnOnACollisionAndDownstreamFollowsTheAlias() {
      Worksheet ws = new Worksheet();
      TableAssembly ja = source(ws, "JA", "Id", "Product");
      TableAssembly jb = source(ws, "JB", "Key", "X");
      ComposedTableAssembly j = join(ws, "PJ", "inner", ja, jb);
      MirrorTableAssembly mj = mirror(ws, "MJ", j);
      filter(mj, col(mj, "X"));
      ColumnRef mjProduct = col(mj, "Product");

      rename(ws, jb, "X", "Product");

      assertEquals(List.of("Id", "Product", "Key", "Product_1"), names(j),
                   "neither merged nor dropped: only the renamed column is aliased");
      assertEquals("JA", col(j, "Product").getEntity());
      assertEquals("JB", col(j, "Product_1").getEntity());
      assertEquals("Product", col(j, "Product_1").getAttribute());

      assertEquals(List.of("Id", "Product", "Key", "Product_1"), names(mj));
      assertSame(mjProduct, col(mj, "Product"), "JA's column downstream must be untouched");
      assertEquals("Product_1", filterAttr(mj).getAttribute(),
                   "downstream must follow the renamed column's FINAL name, not JA's Product");
   }

   /** (C): a user-reordered join must not have its non-renamed column re-aliased. */
   @Test
   void reorderedJoinAliasesOnlyTheRenamedColumn() {
      Worksheet ws = new Worksheet();
      TableAssembly ja = source(ws, "JA", "Id", "Product");
      TableAssembly jb = source(ws, "JB", "Key", "X");
      ComposedTableAssembly j = join(ws, "PJ", "inner", ja, jb);
      ColumnSelection cols = j.getColumnSelection(false);
      ColumnSelection reordered = new ColumnSelection();
      reordered.addAttribute(cols.getAttribute(2));
      reordered.addAttribute(cols.getAttribute(3));
      reordered.addAttribute(cols.getAttribute(0));
      reordered.addAttribute(cols.getAttribute(1));
      j.setColumnSelection(reordered, false);
      filter(j, col(j, "Product"));

      rename(ws, jb, "X", "Product");

      assertEquals(List.of("Key", "Product_1", "Id", "Product"), names(j));
      assertEquals("JB", col(j, "Product_1").getEntity());
      assertEquals("JA", col(j, "Product").getEntity());
      assertEquals("JA", filterAttr(j).getEntity());
      assertEquals("Product", filterAttr(j).getAttribute());
   }

   /**
    * Alias-first matching: the join holds T.Item (T's Product, renamed to Item earlier, so its
    * outer attribute is Item) next to a different T column whose attribute IS Product. Renaming
    * T.Item must re-point the Item column, not the Product one that a per-column attribute
    * fallback would hit. (ColumnSelection refuses a second "Product" attribute on T itself, so
    * this is driven through renameTableColumn with the pair a real rename would pass.)
    */
   @Test
   void aliasIsMatchedBeforeAnotherColumnsAttribute() {
      Worksheet ws = new Worksheet();
      TableAssembly t = source(ws, "T", "Key");
      TableAssembly u = source(ws, "U", "Code");
      ComposedTableAssembly j = join(ws, "J", "inner", t, u);
      ColumnSelection cs = j.getColumnSelection(false);
      cs.addAttribute(new ColumnRef(new AttributeRef("T", "Product")));
      cs.addAttribute(new ColumnRef(new AttributeRef("T", "Item")));
      j.setColumnSelection(cs, false);
      assertEquals(List.of("Key", "Code", "Product", "Item"), names(j));

      ColumnRef ocolumn = new ColumnRef(new AttributeRef(null, "Product"));
      ocolumn.setAlias("Item");
      ColumnRef ncolumn = new ColumnRef(new AttributeRef(null, "Product"));
      ncolumn.setAlias("Name");
      RenameColumnController.renameTableColumn(ws, t, ocolumn, ncolumn);

      assertEquals(List.of("Key", "Code", "Product", "Name"), names(j));
      assertEquals("T", col(j, "Product").getEntity());
   }

   // ------------------------------------------------------- 13. unrelated composed table untouched

   @Test
   void composedTablesNotListingTheRenamedTableAreUntouched() {
      Worksheet ws = new Worksheet();
      TableAssembly t = source(ws, "T", "Region", "Product");
      TableAssembly u = source(ws, "U", "Region", "Product");
      TableAssembly v = source(ws, "V", "Region", "Product");
      ConcatenatedTableAssembly c = concat(ws, "C", TableAssemblyOperator.UNION, u, v);
      ComposedTableAssembly j = join(ws, "J", "inner", u, v);
      filter(c, col(c, "Product"));
      filter(j, col(j, "Product_1"));
      List<String> cBefore = names(c);
      List<String> jBefore = names(j);

      rename(ws, t, "Product", "Item");

      assertEquals(cBefore, names(c));
      assertEquals(jBefore, names(j));
      assertEquals("Product", filterAttr(c).getAttribute());
      assertEquals("V", filterAttr(j).getEntity());
      assertEquals("Product", filterAttr(j).getAttribute());
   }

   // ------------------------------------------- edit_date_range_column shape on a join consumer

   /**
    * WorksheetEditService.editDateRangeColumn renames a range column in place and cascades with
    * {@code renameTableColumn(ws, t, preRenameClone, liveColumn)}; on a join it must re-point the
    * range column's own side only.
    */
   @Test
   void dateRangeReLevelReachesOnlyItsOwnSideOfAJoin() {
      Worksheet ws = new Worksheet();
      TableAssembly t = source(ws, "T", "Key");
      DateRangeRef range = new DateRangeRef("QuarterOfYear(orderDate)",
         new AttributeRef(null, "orderDate"), DateRangeRef.QUARTER_OF_YEAR_PART);
      ColumnRef rangeCol = new ColumnRef(range);
      t.getColumnSelection(false).addAttribute(rangeCol);
      t.getColumnSelection(true).addAttribute(rangeCol.clone());
      TableAssembly u = source(ws, "U", "Key", "QuarterOfYear(orderDate)");
      ComposedTableAssembly j = join(ws, "J", "inner", t, u);
      assertEquals(List.of("Key", "QuarterOfYear(orderDate)", "Key_1",
                           "QuarterOfYear(orderDate)_1"), names(j));
      filter(j, col(j, "QuarterOfYear(orderDate)_1"));

      ColumnRef original = rangeCol.clone();
      String newName = DateRangeRef.getName("orderDate", DateRangeRef.MONTH_OF_YEAR_PART);
      range.setDateOption(DateRangeRef.MONTH_OF_YEAR_PART);
      range.setName(newName);
      rangeCol.setDataRef(range);
      RenameColumnController.renameTableColumn(ws, t, original, rangeCol);

      assertEquals("T", col(j, newName).getEntity());
      ColumnRef uSide = col(j, "QuarterOfYear(orderDate)_1");
      assertEquals("U", uSide.getEntity());
      assertEquals("QuarterOfYear(orderDate)", uSide.getAttribute());
      assertEquals("U", filterAttr(j).getEntity());
   }

   /**
    * Bug #78527: re-levelling A's Year(d) must not touch K = B join C, which keys on its own
    * same-named Year(d) columns (A is in no join, so the wiz guard lets this through).
    */
   @Test
   void reLevelLeavesAnUnrelatedJoinOnASameNamedRangeKeyIntact() {
      Worksheet ws = new Worksheet();
      TableAssembly a = rangeSource(ws, "A");
      TableAssembly b = rangeSource(ws, "B");
      TableAssembly c = rangeSource(ws, "C");
      rangeJoin(ws, "K", b, c);

      reLevel(ws, a);

      assertEquals(List.of("B.Year(d)=C.Year(d)"), operators(ws, "K"));
   }

   /** Bug #78527: in J = A join B on Year(d), only A's side follows A's re-level. */
   @Test
   void reLevelRewritesOnlyTheRenamedSourcesSideOfAJoinOperator() {
      Worksheet ws = new Worksheet();
      TableAssembly a = rangeSource(ws, "A");
      TableAssembly b = rangeSource(ws, "B");
      rangeJoin(ws, "J", a, b);

      reLevel(ws, a);

      assertEquals(List.of("A.Quarter(d)=B.Year(d)"), operators(ws, "J"));
   }

   /**
    * Bug #78527: the scoped operator rewrite does not change a native rename of an expression
    * join key. Before and after the fix, renaming A's e under J = A join B is stopped by J's own
    * validity check (the key is gone from A) before any operator is rewritten, and J's
    * operators are left as they were.
    */
   @Test
   void expressionKeyRenameUnderAJoinIsStoppedBeforeTheOperatorsAsBefore() {
      Worksheet ws = new Worksheet();
      TableAssembly a = expressionSource(ws, "A");
      TableAssembly b = expressionSource(ws, "B");
      rangeJoin(ws, "J", a, b);

      MessageException ex = assertThrows(MessageException.class, () -> rename(ws, a, "e", "e2"));

      assertTrue(ex.getMessage().contains("cross join in J"), ex.getMessage());
      assertEquals(List.of("A.e=B.e"), operators(ws, "J"));
   }

   /**
    * Bug #78527 (refute B3): the same for J = A join M, M a mirror of A, on A's expression key:
    * no worse than before the fix.
    */
   @Test
   void expressionKeyRenameUnderAJoinWithItsOwnMirrorIsStoppedBeforeTheOperatorsAsBefore() {
      Worksheet ws = new Worksheet();
      TableAssembly a = expressionSource(ws, "A");
      MirrorTableAssembly m = mirror(ws, "M", a);
      rangeJoin(ws, "J", a, m);

      MessageException ex = assertThrows(MessageException.class, () -> rename(ws, a, "e", "e2"));

      assertTrue(ex.getMessage().contains("cross join in J"), ex.getMessage());
      assertEquals(List.of("A.e=M.e"), operators(ws, "J"));
   }

   // ----------------------------------------------------------------------------------- helpers

   /** An embedded table whose first column is a Year(d) range column, then d. */
   private static TableAssembly rangeSource(Worksheet ws, String name) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();
      DateRangeRef range = new DateRangeRef(
         DateRangeRef.getName("d", DateRangeRef.YEAR_INTERVAL), new AttributeRef(null, "d"),
         DateRangeRef.YEAR_INTERVAL);
      ColumnRef rangeCol = new ColumnRef(range);
      rangeCol.setDataType("integer");
      cs.addAttribute(rangeCol);
      ColumnRef d = new ColumnRef(new AttributeRef(null, "d"));
      d.setDataType("date");
      cs.addAttribute(d);
      table.setColumnSelection(cs, false);
      table.setColumnSelection(cs.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   /** An embedded table whose first column is an expression column e, then k. */
   private static TableAssembly expressionSource(Worksheet ws, String name) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();
      ExpressionRef expr = new ExpressionRef(null, "e");
      expr.setExpression("field['k'] + 1");
      ColumnRef e = new ColumnRef(expr);
      e.setDataType("integer");
      cs.addAttribute(e);
      ColumnRef k = new ColumnRef(new AttributeRef(null, "k"));
      k.setDataType("integer");
      cs.addAttribute(k);
      table.setColumnSelection(cs, false);
      table.setColumnSelection(cs.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   /** A join on each side's first column, with the operator holding copies, as addJoin does. */
   private static ComposedTableAssembly rangeJoin(Worksheet ws, String name, TableAssembly left,
                                                  TableAssembly right)
   {
      ComposedTableAssembly j = join(ws, name, "inner", left, right);
      CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo) j.getTableInfo();

      for(TableAssemblyOperator.Operator op :
         info.getOperator(left.getName(), right.getName()).getOperators())
      {
         op.setLeftAttribute(((ColumnRef) op.getLeftAttribute()).clone());
         op.setRightAttribute(((ColumnRef) op.getRightAttribute()).clone());
      }

      return j;
   }

   /** Re-levels the Year(d) range column of {@code table} to Quarter(d), as the wiz edit does. */
   private static void reLevel(Worksheet ws, TableAssembly table) {
      ColumnRef rangeCol = (ColumnRef) table.getColumnSelection(false).getAttribute(0);
      DateRangeRef range = (DateRangeRef) rangeCol.getDataRef();
      ColumnRef original = rangeCol.clone();
      range.setDateOption(DateRangeRef.QUARTER_INTERVAL);
      range.setName(DateRangeRef.getName("d", DateRangeRef.QUARTER_INTERVAL));
      rangeCol.setDataRef(range);
      RenameColumnController.renameTableColumn(ws, table, original, rangeCol);
   }

   private static List<String> operators(Worksheet ws, String join) {
      CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo)
         ((CompositeTableAssembly) ws.getAssembly(join)).getTableInfo();
      List<String> names = new ArrayList<>();
      Enumeration<?> iter = info.getOperatorTables();

      while(iter.hasMoreElements()) {
         String[] pair = (String[]) iter.nextElement();

         for(TableAssemblyOperator.Operator op : info.getOperator(pair[0], pair[1]).getOperators()) {
            names.add(op.getLeftTable() + "." + out((ColumnRef) op.getLeftAttribute()) + "=" +
                      op.getRightTable() + "." + out((ColumnRef) op.getRightAttribute()));
         }
      }

      return names;
   }


   private static void rename(Worksheet ws, TableAssembly table, String column, String newName) {
      ColumnSelection cs = table.getColumnSelection(false);
      ColumnRef live = null;

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         ColumnRef c = (ColumnRef) cs.getAttribute(i);

         if(column.equals(out(c))) {
            live = c;
            break;
         }
      }

      assertNotNull(live, "no column " + column + " on " + table.getName());
      boolean failed = RenameColumnController.renameColumn(
         ws, mock(CommandDispatcher.class), table, live, newName);
      assertFalse(failed, "the rename itself must succeed");
   }

   private static TableAssembly source(Worksheet ws, String name, String... cols) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();

      for(String col : cols) {
         ColumnRef ref = new ColumnRef(new AttributeRef(null, col));
         ref.setDataType("string");
         cs.addAttribute(ref);
      }

      table.setColumnSelection(cs, false);
      table.setColumnSelection(cs.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   private static MirrorTableAssembly mirror(Worksheet ws, String name, TableAssembly base) {
      MirrorTableAssembly m = new MirrorTableAssembly(ws, name, base);
      m.setColumnSelection(m.getColumnSelection(false).clone(), true);
      ws.addAssembly(m);
      return m;
   }

   private static ConcatenatedTableAssembly concat(Worksheet ws, String name, int operation,
                                                   TableAssembly... tables)
   {
      TableAssemblyOperator[] ops = new TableAssemblyOperator[tables.length - 1];

      for(int i = 0; i < ops.length; i++) {
         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setOperation(operation);
         ops[i] = new TableAssemblyOperator();
         ops[i].addOperator(op);
      }

      ConcatenatedTableAssembly c = new ConcatenatedTableAssembly(ws, name, tables, ops);
      ws.addAssembly(c);
      c.initDefaultColumnSelection();
      c.setColumnSelection(c.getColumnSelection(false).clone(), true);
      return c;
   }

   /** Same shape as JoinQuery.getDefaultColumnSelection0: outer attribute per column, fixAlias. */
   private static ComposedTableAssembly join(Worksheet ws, String name, String kind,
                                             TableAssembly left, TableAssembly right)
   {
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable(left.getName());
      op.setRightTable(right.getName());
      ColumnRef lcol = (ColumnRef) left.getColumnSelection(false).getAttribute(0);
      ColumnRef rcol = (ColumnRef) right.getColumnSelection(false).getAttribute(0);
      op.setLeftAttribute(lcol);
      op.setRightAttribute(rcol);
      op.setOperation(switch(kind) {
         case "left" -> TableAssemblyOperator.LEFT_JOIN;
         case "cross" -> TableAssemblyOperator.CROSS_JOIN;
         case "merge" -> TableAssemblyOperator.MERGE_JOIN;
         default -> TableAssemblyOperator.INNER_JOIN;
      });
      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);
      TableAssembly[] tables = { left, right };
      TableAssemblyOperator[] ops = { top };

      ComposedTableAssembly j = "merge".equals(kind) ?
         new MergeJoinTableAssembly(ws, name, tables, ops) :
         new RelationalJoinTableAssembly(ws, name, tables, ops);
      ColumnSelection columns = new ColumnSelection();

      for(TableAssembly table : tables) {
         ColumnSelection tcolumns = table.getColumnSelection(true);

         for(int i = 0; i < tcolumns.getAttributeCount(); i++) {
            ColumnRef tcolumn = (ColumnRef) tcolumns.getAttribute(i);
            ColumnRef column = new ColumnRef(AssetUtil.getOuterAttribute(table.getName(), tcolumn));
            column.setDataType(tcolumn.getDataType());
            columns.addAttribute(column);
         }

         AssetUtil.fixAlias(columns);
      }

      j.setColumnSelection(columns, false);
      j.setColumnSelection(columns.clone(), true);
      ws.addAssembly(j);
      return j;
   }

   /** Filter on the composed table's own LIVE column object (how addFilter builds it). */
   private static void filter(TableAssembly table, ColumnRef column) {
      Condition cond = new Condition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("A");
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(column, cond, 0));
      table.setPreConditionList(list);
   }

   private static void sort(TableAssembly table, ColumnRef column) {
      SortInfo sinfo = new SortInfo();
      sinfo.addSort(new SortRef(column.clone()));
      table.setSortInfo(sinfo);
   }

   private static void group(TableAssembly table, ColumnRef column) {
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroups(new GroupRef[] { new GroupRef(column.clone()) });
      table.setAggregateInfo(ainfo);
   }

   private static DataRef filterAttr(TableAssembly table) {
      ConditionList list = (ConditionList) table.getPreConditionList();
      assertTrue(list.getSize() > 0, "the filter on " + table.getName() + " was dropped");
      return list.getConditionItem(0).getAttribute();
   }

   private static ColumnRef col(TableAssembly table, String outName) {
      ColumnSelection cs = table.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         ColumnRef c = (ColumnRef) cs.getAttribute(i);

         if(outName.equals(out(c))) {
            return c;
         }
      }

      fail("no column " + outName + " on " + table.getName() + ": " + names(table));
      return null;
   }

   private static List<String> names(TableAssembly table) {
      ColumnSelection cs = table.getColumnSelection(false);
      List<String> names = new ArrayList<>();

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         names.add(out((ColumnRef) cs.getAttribute(i)));
      }

      return names;
   }

   private static String out(ColumnRef c) {
      return c.getAlias() != null ? c.getAlias() : c.getAttribute();
   }
}
