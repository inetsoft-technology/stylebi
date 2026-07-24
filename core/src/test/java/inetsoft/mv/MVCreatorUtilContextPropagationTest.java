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

import inetsoft.mv.data.MVColumnInfo;
import inetsoft.mv.data.XDimDictionary;
import inetsoft.uql.XTable;
import inetsoft.util.ThreadPool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link MVCreatorUtil#addRowToDictionaries} submits its per-column background
 * work to {@link ThreadPool#addOnDemand} as {@link ThreadPool.ContextRunnable} instances (via
 * {@code ThreadPool.AbstractContextRunnable}) rather than plain lambdas. Only a
 * {@code ContextRunnable} is recognized by {@code ThreadPool.add()}'s existing principal/org-id
 * capture logic -- a plain {@code Runnable} silently runs with no tenant identity attached, which
 * was the shape of the bug this changed.
 */
@Tag("core")
class MVCreatorUtilContextPropagationTest {
   @Test
   void submitsDictionaryAndMeasureWorkAsContextRunnables() {
      XTable lens = mock(XTable.class);
      // no rows to process -- we only care about what gets submitted, not the row scan itself.
      when(lens.moreRows(anyInt())).thenReturn(false);
      when(lens.getRowCount()).thenReturn(0);

      int[] dimensions = { 0 };
      int[] measures = { 0 };
      XDimDictionary[] dicts = new XDimDictionary[2];
      MVColumnInfo[] mvColumnInfos = new MVColumnInfo[2];
      boolean[] numbers = { true };
      boolean[] convertDates = { false };

      List<Runnable> submitted = new ArrayList<>();

      try(MockedStatic<ThreadPool> threadPoolStatic = mockStatic(ThreadPool.class)) {
         threadPoolStatic.when(() -> ThreadPool.addOnDemand(any())).thenAnswer(inv -> {
            Runnable cmd = inv.getArgument(0);
            submitted.add(cmd);
            cmd.run();
            return null;
         });

         MVCreatorUtil.addRowToDictionaries(lens, 0, 0, dimensions, measures,
            dicts, mvColumnInfos, numbers, convertDates);
      }

      // one task for the dimension column, one for the numeric measure column.
      assertEquals(2, submitted.size());
      submitted.forEach(cmd -> assertInstanceOf(ThreadPool.ContextRunnable.class, cmd));
   }
}
