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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.GroupedThread;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79857: MVManager.add(mv, event, orgID) must write to the supplied organization, also on a
 * thread that has no context principal (where the current org falls back to the host org).
 */
@Tag("core")
class MVManagerOrgScopedAddTest {
   @Test
   void addWithOrgIdWritesToThatOrgOnPrincipalLessThread() throws Exception {
      MVDefMap defMap = mock(MVDefMap.class);
      Cluster cluster = mock(Cluster.class);
      MVManager manager = mock(MVManager.class, CALLS_REAL_METHODS);
      setField(manager, "mvs", defMap);
      setField(manager, "cluster", cluster);
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn("vs_T_1_0_orgx");

      ExecutorService pool = Executors.newCachedThreadPool(GroupedThread::new);

      try {
         pool.submit(() -> manager.add(def, false, "orgx")).get();
      }
      finally {
         pool.shutdownNow();
      }

      verify(defMap).put("vs_T_1_0_orgx", def, "orgx");
      verify(defMap, never()).put(anyString(), any(MVDef.class), eq("host-org"));
   }

   private static void setField(Object target, String name, Object value) throws Exception {
      Field field = MVManager.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }
}
