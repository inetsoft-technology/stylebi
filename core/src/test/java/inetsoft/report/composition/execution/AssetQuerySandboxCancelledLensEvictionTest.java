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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.lens.DefaultTableLens;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82071: a cancelled lens cached in the sandbox is cut short, so the next
 * lookup must not serve it again.
 */
@Tag("core")
class AssetQuerySandboxCancelledLensEvictionTest {
   private static class CancelLens extends DefaultTableLens implements CancellableTableLens {
      volatile boolean cancelled;

      CancelLens() {
         super(new Object[][]{{"x"}, {"r1"}});
      }

      @Override
      public void cancel() {
         cancelled = true;
      }

      @Override
      public boolean isCancelled() {
         return cancelled;
      }
   }

   @Test
   void cancelledLensIsEvictedButHealthyLensIsKept() throws Exception {
      AssetQuerySandbox box = org.mockito.Mockito.mock(
         AssetQuerySandbox.class, org.mockito.Mockito.CALLS_REAL_METHODS);
      java.lang.reflect.Field f = AssetQuerySandbox.class.getDeclaredField("tmap");
      f.setAccessible(true);
      f.set(box, new java.util.concurrent.ConcurrentHashMap<>());

      Method put = AssetQuerySandbox.class.getDeclaredMethod(
         "putTableLens0", String.class, int.class, boolean.class, TableLens.class, int.class);
      Method get = AssetQuerySandbox.class.getDeclaredMethod(
         "getTableLens0", String.class, int.class, boolean.class, int.class);
      put.setAccessible(true);
      get.setAccessible(true);

      CancelLens lens = new CancelLens();
      put.invoke(box, "T", AssetQuerySandbox.RUNTIME_MODE, false, lens, 0);
      assertSame(lens, get.invoke(box, "T", AssetQuerySandbox.RUNTIME_MODE, false, 0));

      lens.cancel();
      assertNull(get.invoke(box, "T", AssetQuerySandbox.RUNTIME_MODE, false, 0));
   }
}
