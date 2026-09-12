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

import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.internal.AssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the behavior change in {@code SelectionUpTransformer.isMoveableToParent()}: a filter
 * bound to an input (dynamic) parameter is excluded from the MV at analysis/creation time (since
 * the value may change before the MV is used), but is allowed to move up into the MV query at
 * runtime so it can be applied when fetching data instead of in post-processing. This is scoped
 * to the specific branch that changed, not the full analyzer/query pipeline.
 */
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
}
