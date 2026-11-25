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
package inetsoft.mv.mr.internal;

import inetsoft.mv.mr.*;
import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82324: when one block of a multi-block MV job failed, the job marked every other
 * pending map task as expired, and {@link XJobStatus#getResult()} then picked the first
 * non-empty map-status reason from an unordered map, so the error usually named an
 * "expired" task instead of the real cause. A failed job must report its own failure
 * reason, which names the failing block and the original error.
 */
@Tag("core")
class XJobStatusFailureReasonTest {
   @Test
   void aFailedJobReportsTheFailingBlocksReason() throws Exception {
      XJobStatus status = newStatus();
      XReduceTask reducer = mock(XReduceTask.class);
      when(reducer.getResult()).thenThrow(new RuntimeException("Reduce task is not completed"));
      set(status, "reducer", reducer);

      // fail the block that the map-status map iterates last, so a reason taken from the
      // first map status would be one of the "expired" ones
      XMapStatus last = null;

      for(XMapStatus mstatus : mmap(status).keySet()) {
         last = mstatus;
      }

      XMapFailure failure = new XMapFailure();
      failure.setID(JOB_ID);
      failure.setXBlock(last.getXBlock());
      failure.setHost(last.getHost());
      failure.setReason(ORIGINAL);

      try(MockedStatic<SreeEnv> sree = mockStatic(SreeEnv.class);
          MockedStatic<XMapTaskPool> pool = mockStatic(XMapTaskPool.class))
      {
         status.addFailure(failure);
      }

      assertTrue(status.isCompleted());
      RuntimeException thrown = assertThrows(RuntimeException.class, status::getResult);
      assertTrue(thrown.getMessage().contains(ORIGINAL),
                 "the error must name the original cause: " + thrown.getMessage());
      assertTrue(thrown.getMessage().contains(last.getXBlock()),
                 "the error must name the failing block: " + thrown.getMessage());
   }

   private static XJobStatus newStatus() throws Exception {
      XJob job = mock(XJob.class);
      when(job.getID()).thenReturn(JOB_ID);
      when(job.getXFile()).thenReturn("mv-file");
      when(job.isCancelled()).thenReturn(false);

      XJobStatus status = new XJobStatus(JOB_ID);
      set(status, "job", job);
      set(status, "started", System.currentTimeMillis());
      set(status, "wperiod", Integer.MAX_VALUE);

      List<String> blocks = new ArrayList<>();

      for(int i = 0; i < 8; i++) {
         String bid = "mv-file_host-org-" + i;
         blocks.add(bid);
         XMapTask task = mock(XMapTask.class);
         when(task.getHost()).thenReturn("localhost");
         when(task.getXBlock()).thenReturn(bid);
         XMapStatus mstatus = new XMapStatus(task);
         mstatus.start();
         mmap(status).put(mstatus, mstatus);
      }

      set(status, "bset", new java.util.HashSet<>(blocks));
      return status;
   }

   @SuppressWarnings("unchecked")
   private static Map<XMapStatus, XMapStatus> mmap(XJobStatus status) throws Exception {
      Field mmap = XJobStatus.class.getDeclaredField("mmap");
      mmap.setAccessible(true);
      return (Map<XMapStatus, XMapStatus>) mmap.get(status);
   }

   private static void set(XJobStatus status, String name, Object value) throws Exception {
      Field field = XJobStatus.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(status, value);
   }

   private static final String JOB_ID = "job-1";
   private static final String ORIGINAL = "java.lang.RuntimeException: " +
      "inetsoft.mv.data.DefaultTableBlock$ColumnNotFoundException: Column[X] not found in []";
}
