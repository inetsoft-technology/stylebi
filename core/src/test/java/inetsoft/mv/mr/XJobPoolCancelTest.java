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
package inetsoft.mv.mr;

import inetsoft.mv.mr.internal.XJobStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A cancelled job never becomes COMPLETED (XJobStatus.complete() returns early for a cancelled
 * job), so XJobPool.removeJob() must remove it on cancel instead of re-queueing itself forever,
 * which kept the job status in the pool's map.
 */
@Tag("core")
class XJobPoolCancelTest {
   @Test
   void cancelledJobIsRemovedFromThePool() throws Exception {
      AtomicBoolean cancelled = new AtomicBoolean();
      XJob job = mock(XJob.class);
      when(job.getID()).thenReturn("job-cancel");
      when(job.isCancelled()).thenAnswer(inv -> cancelled.get());
      doAnswer(inv -> {
         cancelled.set(true);
         return null;
      }).when(job).cancel();

      XJobStatus status = new XJobStatus("job-cancel");
      set(status, "job", job);

      XJobPool pool = newPool();
      Map<String, XJobStatus> smap = smap(pool);
      smap.put(status.getID(), status);

      status.cancel();
      assertTrue(status.isCancelled());
      assertFalse(status.isCompleted(), "a cancelled job is not completed");

      removeJob(pool, status);

      assertTrue(smap.isEmpty(), "a cancelled job must not stay in the pool");
   }

   @Test
   void completedJobIsRemovedFromThePool() throws Exception {
      XJobStatus status = new XJobStatus("job-done");
      set(status, "state", 8 | 4);

      XJobPool pool = newPool();
      Map<String, XJobStatus> smap = smap(pool);
      smap.put(status.getID(), status);

      removeJob(pool, status);

      assertTrue(smap.isEmpty());
   }

   private static XJobPool newPool() throws Exception {
      // not started: only the status map and removeJob() are exercised
      Constructor<XJobPool> ctor = XJobPool.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      return ctor.newInstance();
   }

   @SuppressWarnings("unchecked")
   private static Map<String, XJobStatus> smap(XJobPool pool) throws Exception {
      Field f = XJobPool.class.getDeclaredField("smap");
      f.setAccessible(true);
      return (Map<String, XJobStatus>) f.get(pool);
   }

   private static void removeJob(XJobPool pool, XJobStatus status) throws Exception {
      Method m = XJobPool.class.getDeclaredMethod("removeJob", XJobStatus.class);
      m.setAccessible(true);
      m.invoke(pool, status);
   }

   private static void set(XJobStatus status, String name, Object value) throws Exception {
      Field f = XJobStatus.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(status, value);
   }
}
