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
package inetsoft.mv;

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression test for Redmine bug #80457. A table that runs on a physical MV must not carry a
 * correlated subquery condition into MVAssetQuery, which cannot evaluate it. The transformer
 * declines the MV with a non-recreate MVExecutionException so the asset query falls back to
 * the table without MV.
 */
@Tag("core")
class MVTransformerCorrelatedSubQueryTest {
   @Test
   void helperDetectsCorrelatedCondition() {
      assertTrue(MVTool.hasCorrelatedSubQuery(wrapper(true)));
   }

   @Test
   void helperIgnoresUncorrelatedNullAndNonAssetConditions() {
      assertFalse(MVTool.hasCorrelatedSubQuery(wrapper(false)));
      assertFalse(MVTool.hasCorrelatedSubQuery(null));
      assertFalse(MVTool.hasCorrelatedSubQuery(new ConditionList()));

      ConditionList conds = mock(ConditionList.class);
      when(conds.getSize()).thenReturn(1);
      when(conds.getXCondition(0)).thenReturn(mock(XCondition.class));
      ConditionListWrapper wrapper = mock(ConditionListWrapper.class);
      when(wrapper.getConditionList()).thenReturn(conds);
      assertFalse(MVTool.hasCorrelatedSubQuery(wrapper));
   }

   @Test
   void declinesCorrelatedPreRuntimeCondition() {
      TableAssembly table = table(true, wrapper(true), null);
      MVExecutionException ex = assertThrows(
         MVExecutionException.class, () -> MVTransformer.checkCorrelatedSubQuery(table));
      assertFalse(ex.isNeedRecreateMV());
   }

   @Test
   void declinesCorrelatedPostRuntimeCondition() {
      TableAssembly table = table(true, null, wrapper(true));
      assertThrows(MVExecutionException.class, () -> MVTransformer.checkCorrelatedSubQuery(table));
   }

   @Test
   void declinesWhenChildOfTransformedTreeIsAffected() {
      TableAssembly child = table(true, wrapper(true), null);
      ComposedTableAssembly root = mock(ComposedTableAssembly.class);
      when(root.getTableAssemblies(false)).thenReturn(new TableAssembly[] { child });
      assertThrows(MVExecutionException.class, () -> MVTransformer.checkCorrelatedSubQuery(root));
   }

   @Test
   void keepsMvForUncorrelatedCondition() {
      TableAssembly table = table(true, wrapper(false), wrapper(false));
      assertSame(table, MVTransformer.checkCorrelatedSubQuery(table));
   }

   @Test
   void keepsTableWithoutPhysicalMv() {
      // no runtime mv: nothing runs through MVAssetQuery, the condition is evaluated normally
      TableAssembly noMv = table(true, wrapper(true), null);
      when(noMv.getRuntimeMV()).thenReturn(null);
      assertSame(noMv, MVTransformer.checkCorrelatedSubQuery(noMv));

      TableAssembly logical = table(false, wrapper(true), null);
      assertSame(logical, MVTransformer.checkCorrelatedSubQuery(logical));
   }

   private static TableAssembly table(boolean physical, ConditionListWrapper pre,
                                      ConditionListWrapper post)
   {
      RuntimeMV rmv = mock(RuntimeMV.class);
      when(rmv.isPhysical()).thenReturn(physical);
      TableAssembly table = mock(TableAssembly.class);
      when(table.getName()).thenReturn("CUSTOMERS1");
      when(table.getRuntimeMV()).thenReturn(rmv);
      when(table.getPreRuntimeConditionList()).thenReturn(pre);
      when(table.getPostRuntimeConditionList()).thenReturn(post);
      return table;
   }

   private static ConditionListWrapper wrapper(boolean correlated) {
      SubQueryValue val = mock(SubQueryValue.class);
      when(val.isCorrelated()).thenReturn(correlated);
      AssetCondition cond = mock(AssetCondition.class);
      when(cond.getSubQueryValue()).thenReturn(val);
      ConditionList conds = mock(ConditionList.class);
      when(conds.getSize()).thenReturn(1);
      when(conds.getXCondition(0)).thenReturn(cond);
      ConditionListWrapper wrapper = mock(ConditionListWrapper.class);
      when(wrapper.getConditionList()).thenReturn(conds);
      return wrapper;
   }
}
