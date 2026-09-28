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

package inetsoft.sree.schedule;

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.asset.DependencyHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/*
 * Tier: [unit] — ScheduleManager constructed directly (no Spring) with mocked collaborators.
 *
 * Regression: Bug #77195 — lock-order inversion between the ScheduleManager monitor and
 * extensionLock. reloadExtensions() held extensionLock and then entered the synchronized
 * removeExtensionTasksOfOrg(), while save() holds the monitor and then calls
 * reloadExtensions() when an extension task's enabled flag changes.
 */
@Tag("core")
class ScheduleManagerLockOrderTest {
   @Test
   void saveAndReloadExtensionsDoNotDeadlock() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      // no orgs, so initMap() does not create any storage-backed ScheduleTaskMap
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[0]);
      ScheduleManager manager = new ScheduleManager(
         securityEngine, mock(Cluster.class), mock(ScheduleClient.class),
         mock(DependencyHandler.class));

      DataCycleManager.CycleInfo cycleInfo = mock(DataCycleManager.CycleInfo.class);
      when(cycleInfo.getOrgId()).thenReturn(ORG_ID);
      // a data cycle task that ended up in the regular task map
      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getTaskId()).thenReturn(TASK_ID);
      when(task.getCycleInfo()).thenReturn(cycleInfo);
      when(task.isEnabled()).thenReturn(true);

      CountDownLatch saveInMonitor = new CountDownLatch(1);
      CountDownLatch releaseSave = new CountDownLatch(1);
      ScheduleExt ext = mock(ScheduleExt.class);
      when(ext.containsTask(TASK_ID, ORG_ID)).thenReturn(true);
      when(ext.isEnable(TASK_ID, ORG_ID)).thenReturn(false);
      when(ext.setEnable(eq(TASK_ID), eq(ORG_ID), eq(true))).thenAnswer(inv -> {
         // the saving thread holds the ScheduleManager monitor here
         saveInMonitor.countDown();
         releaseSave.await(10, TimeUnit.SECONDS);
         return false;
      });
      when(ext.getTasks(ORG_ID)).thenReturn(List.of());
      manager.addScheduleExt(ext);

      Thread saveThread = new Thread(() -> {
         try {
            manager.save(List.of(task), ORG_ID);
         }
         catch(Exception ignore) {
         }
      }, "77195-save");
      Thread reloadThread = new Thread(() -> manager.reloadExtensions(ORG_ID), "77195-reload");
      // a regression leaves both threads deadlocked, they must not keep the JVM alive
      saveThread.setDaemon(true);
      reloadThread.setDaemon(true);

      saveThread.start();
      assertTrue(saveInMonitor.await(5, TimeUnit.SECONDS), "save() did not reach setEnable()");
      reloadThread.start();

      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

      while(reloadThread.getState() != Thread.State.BLOCKED &&
         reloadThread.getState() != Thread.State.TERMINATED && System.nanoTime() < deadline)
      {
         Thread.onSpinWait();
      }

      // save() now continues into reloadExtensions() while still holding the monitor
      releaseSave.countDown();
      reloadThread.join(5000);
      saveThread.join(5000);

      ThreadMXBean threads = ManagementFactory.getThreadMXBean();
      long[] deadlocked = threads.findDeadlockedThreads();
      assertNull(deadlocked, () -> "lock-order deadlock:\n" + describe(threads, deadlocked));
      assertFalse(reloadThread.isAlive(), "reloadExtensions() did not finish");
      assertFalse(saveThread.isAlive(), "save() did not finish");
      verify(ext).setEnable(TASK_ID, ORG_ID, true);
   }

   private static String describe(ThreadMXBean threads, long[] ids) {
      StringBuilder builder = new StringBuilder();

      for(ThreadInfo info : threads.getThreadInfo(ids, true, true)) {
         builder.append(info);
      }

      return builder.toString();
   }

   private static final String ORG_ID = "org1";
   private static final String TASK_ID = "sys__DataCycle Task: c1";
}
