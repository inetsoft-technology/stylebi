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
package inetsoft.report.composition.event;

import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81222: the exceeded-rows notice must follow the mode the table was executed in, not
 * only {@code TableAssembly.isRuntime()}.
 */
@Tag("core")
class AssetEventUtilExceededMsgTest {
   private static TableAssembly table(int maxRows) {
      TableAssembly table = mock(TableAssembly.class);
      when(table.getMaxRows()).thenReturn(maxRows);
      when(table.getWorksheet()).thenReturn(mock(Worksheet.class));
      return table;
   }

   @Test
   void runtimeVariantReportsTheTableMaxRows() {
      String msg = AssetEventUtil.getExceededMsg(table(10), 10, true);

      assertNotNull(msg);
      assertTrue(msg.contains("10"), msg);
   }

   @Test
   void runtimeVariantIsQuietBelowTheMaxRows() {
      assertNull(AssetEventUtil.getExceededMsg(table(10), 9, true));
   }

   @Test
   void runtimeVariantIgnoresTheDesignDisplayLimit() {
      // max display rows only apply to a design-mode (Composer) preview
      TableAssembly table = table(0);
      when(table.getMaxDisplayRows()).thenReturn(5);

      assertNull(AssetEventUtil.getExceededMsg(table, 5, true));
      assertNotNull(AssetEventUtil.getExceededMsg(table, 5, false),
                    "design mode keeps the display limit notice");
   }

   @Test
   void twoArgumentOverloadStillFollowsTheTableMode() {
      TableAssembly design = table(10);
      assertEquals(AssetEventUtil.getExceededMsg(design, 10, false),
                   AssetEventUtil.getExceededMsg(design, 10));

      TableAssembly runtime = table(10);
      when(runtime.isRuntime()).thenReturn(true);
      assertEquals(AssetEventUtil.getExceededMsg(runtime, 10, true),
                   AssetEventUtil.getExceededMsg(runtime, 10));
   }

   @Test
   void nullTableHasNoLimit() {
      assertNull(AssetEventUtil.getExceededMsg(null, 10, true));
      assertNull(AssetEventUtil.getExceededLimit(null, n -> true, true));
   }
}
