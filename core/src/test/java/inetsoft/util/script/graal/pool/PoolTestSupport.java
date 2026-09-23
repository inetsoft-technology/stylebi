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
package inetsoft.util.script.graal.pool;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import org.mockito.Mockito;

import java.lang.reflect.Field;

/**
 * Shared helpers of the worksheet context pool tests (bug #76960).
 */
public final class PoolTestSupport {
   private PoolTestSupport() {
   }

   /**
    * A sandbox that runs the real sandbox methods without a worksheet (as
    * ConditionFilterBoxResetLockTest does), in the given pool mode.
    */
   public static AssetQuerySandbox poolBox(boolean pool) throws Exception {
      AssetQuerySandbox box = Mockito.mock(AssetQuerySandbox.class, Mockito.CALLS_REAL_METHODS);
      setField(box, "lock", new Object());
      setField(box, "scriptPoolMode", pool);
      return box;
   }

   static void setField(AssetQuerySandbox box, String name, Object value) throws Exception {
      Field field = AssetQuerySandbox.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(box, value);
   }
}
