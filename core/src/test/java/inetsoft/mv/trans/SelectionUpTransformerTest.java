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
package inetsoft.mv.trans;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XNode;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the behavior change in {@code SelectionUpTransformer.isMoveableToParent()}: a filter
 * bound to an input (dynamic) parameter is excluded from the MV at analysis/creation time (since
 * the value may change before the MV is used), but is allowed to move up into the MV query at
 * runtime so it can be applied when fetching data instead of in post-processing. This is scoped
 * to the specific branch that changed, not the full analyzer/query pipeline.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SelectionUpTransformerTest {
   @Test
   void excludesInputDynamicTableFilterAtAnalysisTime() throws Exception {
      assertFalse(isMoveableToParent(true, false));
   }

   @Test
   void allowsInputDynamicTableFilterAtRuntime() throws Exception {
      assertTrue(isMoveableToParent(true, true));
   }

   @Test
   void allowsNonDynamicTableFilterAtAnalysisTime() throws Exception {
      assertTrue(isMoveableToParent(false, false));
   }

   /**
    * @param inputDynamicTable whether the child table is bound to an input parameter.
    * @param runtime           whether this is a runtime query (vs. design-time analysis).
    */
   private static boolean isMoveableToParent(boolean inputDynamicTable, boolean runtime)
      throws Exception
   {
      // a MirrorTableAssembly parent reaches its own early "return true" once past the
      // isInputDynamicTable/isRuntime check, without needing to satisfy the rest of the
      // method's join/concatenated-table-specific branches.
      MirrorTableAssembly ptbl = mock(MirrorTableAssembly.class);
      when(ptbl.getName()).thenReturn("parent");
      when(ptbl.getAggregateInfo()).thenReturn(new AggregateInfo());
      when(ptbl.getInfo()).thenReturn(mock(AssemblyInfo.class));

      TableAssembly tbl = mock(TableAssembly.class);
      when(tbl.getName()).thenReturn("child");
      when(tbl.getInfo()).thenReturn(mock(AssemblyInfo.class));

      TableNode pnode = new TableNode(ptbl);
      TableNode node = new TableNode(tbl);

      TransformationDescriptor desc = mock(TransformationDescriptor.class);
      when(desc.getRankingSelectionColumns("child")).thenReturn(Collections.emptyList());
      when(desc.isInputDynamicTable("child")).thenReturn(inputDynamicTable);
      when(desc.isRuntime()).thenReturn(runtime);

      SelectionUpTransformer transformer = new SelectionUpTransformer();
      Method method = SelectionUpTransformer.class.getDeclaredMethod(
         "isMoveableToParent", TableNode.class, TableNode.class, TransformationDescriptor.class);
      method.setAccessible(true);

      return (boolean) method.invoke(transformer, pnode, node, desc);
   }

   /**
    * Bug #80445: a table with two parents (e.g. the same worksheet table joined twice) must have
    * its runtime selection condition moved up to every parent, not just the first one.
    */
   @Test
   void movesRuntimeConditionToEveryParent() throws Exception {
      TableAssembly tbl = table("child", new ColumnSelection());
      AtomicReference<ConditionListWrapper> pre = runtimeList(tbl);
      pre.set(condition("child", "REASON", "x"));

      // second parent exposes the child's column under an alias (REASON_1)
      ColumnSelection cols1 = new ColumnSelection();
      ColumnRef col1 = new ColumnRef(new AttributeRef("child", "REASON"));
      cols1.addAttribute(col1);
      ColumnSelection cols2 = new ColumnSelection();
      ColumnRef col2 = new ColumnRef(new AttributeRef("child", "REASON"));
      col2.setAlias("REASON_1");
      cols2.addAttribute(col2);

      TableAssembly p1 = table("p1", cols1);
      TableAssembly p2 = table("p2", cols2);
      AtomicReference<ConditionListWrapper> pre1 = runtimeList(p1);
      AtomicReference<ConditionListWrapper> pre2 = runtimeList(p2);

      SelectionUpTransformer transformer = new SelectionUpTransformer();
      Method method = SelectionUpTransformer.class.getDeclaredMethod(
         "moveSelectionUp0", XNode.class, XNode.class, TransformationDescriptor.class);
      TransformationDescriptor desc = mock(TransformationDescriptor.class);
      method.setAccessible(true);
      method.invoke(transformer, new TableNode(p1), new TableNode(tbl), desc);
      method.invoke(transformer, new TableNode(p2), new TableNode(tbl), desc);

      assertNotNull(pre1.get());
      assertNotNull(pre2.get(), "second parent must receive the condition");
      assertEquals(1, pre1.get().getConditionList().getSize());
      assertEquals(1, pre2.get().getConditionList().getSize());
      assertSame(col1, pre1.get().getConditionList().getAttribute(0));
      assertSame(col2, pre2.get().getConditionList().getAttribute(0));
      assertEquals(1, cols2.getAttributeCount(), "no hidden column added to aliased parent");
      assertEquals(1, cols1.getAttributeCount());
   }

   private static TableAssembly table(String name, ColumnSelection columns) {
      TableAssembly table = mock(TableAssembly.class);
      when(table.getName()).thenReturn(name);
      when(table.getAggregateInfo()).thenReturn(new AggregateInfo());
      when(table.getInfo()).thenReturn(mock(AssemblyInfo.class));
      when(table.getColumnSelection()).thenReturn(columns);
      when(table.getColumnSelection(true)).thenReturn(columns);
      return table;
   }

   private static AtomicReference<ConditionListWrapper> runtimeList(TableAssembly table) {
      AtomicReference<ConditionListWrapper> ref = new AtomicReference<>();
      when(table.getPreRuntimeConditionList()).thenAnswer(inv -> ref.get());
      doAnswer(inv -> {
         ref.set(inv.getArgument(0));
         return null;
      }).when(table).setPreRuntimeConditionList(any());
      return ref;
   }

   private static ConditionList condition(String entity, String attr, String value) {
      Condition cond = new Condition("string");
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new ColumnRef(new AttributeRef(entity, attr)), cond, 0));
      return list;
   }
}
