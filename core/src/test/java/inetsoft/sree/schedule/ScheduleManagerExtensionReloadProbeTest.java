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
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.XPrincipal;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Tier: [unit] - Bug #79263 independent probe. Concurrent hammer of the unlocked getScheduleTask
 * against repeated reloads, a throwing regeneration, and org scoping of the swap.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleManagerExtensionReloadProbeTest {
   private static final String ORG = "host-org";
   private static final String OTHER = "other-org";

   private ScheduleExt ext;
   private ScheduleManager manager;

   @BeforeEach
   void setUp() {
      ext = mock(ScheduleExt.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.getOrganizationIDs()).thenReturn(new String[0]);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      manager = new ScheduleManager(securityEngine, mock(Cluster.class),
                                    mock(ScheduleClient.class), null);
      manager.addScheduleExt(ext);
   }

   @Test
   void concurrentReadersNeverSeeStableTaskMissing() throws Exception {
      String id = task("stable", ORG).getTaskId();
      // a fresh task object per call, as the real regeneration produces
      when(ext.getTasks(ORG)).thenAnswer(inv -> {
         Thread.sleep(2);
         return List.of(task("stable", ORG), task("churn" + (System.nanoTime() % 3), ORG));
      });
      manager.reloadExtensions(ORG);

      AtomicBoolean stop = new AtomicBoolean();
      AtomicInteger nulls = new AtomicInteger();
      AtomicInteger reads = new AtomicInteger();
      ExecutorService readers = Executors.newFixedThreadPool(4);
      List<Future<?>> fs = new java.util.ArrayList<>();

      for(int i = 0; i < 4; i++) {
         fs.add(readers.submit(() -> {
            while(!stop.get()) {
               reads.incrementAndGet();

               if(manager.getScheduleTask(id, ORG) == null) {
                  nulls.incrementAndGet();
               }
            }
         }));
      }

      for(int i = 0; i < 150; i++) {
         manager.reloadExtensions(ORG);
      }

      stop.set(true);

      for(Future<?> f : fs) {
         f.get(10, TimeUnit.SECONDS);
      }

      readers.shutdownNow();
      assertTrue(reads.get() > 0);
      assertEquals(0, nulls.get());
   }

   @Test
   void throwingRegenerationKeepsOldTasks() throws Exception {
      ScheduleTask old = task("c1", ORG);
      when(ext.getTasks(ORG)).thenReturn(List.of(old))
         .thenThrow(new IllegalStateException("boom"));
      manager.reloadExtensions(ORG);

      try {
         manager.reloadExtensions(ORG);
      }
      catch(RuntimeException ignore) {
      }

      assertSame(old, manager.getScheduleTask(old.getTaskId(), ORG));
   }

   @Test
   void reloadOfOneOrgLeavesOtherOrgAlone() throws Exception {
      ScheduleTask a = task("c1", ORG);
      ScheduleTask b = task("c1", OTHER);
      when(ext.getTasks(ORG)).thenReturn(List.of(a)).thenReturn(List.of());
      when(ext.getTasks(OTHER)).thenReturn(List.of(b));
      manager.reloadExtensions(ORG);
      manager.reloadExtensions(OTHER);
      assertSame(a, manager.getScheduleTask(a.getTaskId(), ORG));
      assertSame(b, manager.getScheduleTask(b.getTaskId(), OTHER));

      manager.reloadExtensions(ORG);
      assertNull(manager.getScheduleTask(a.getTaskId(), ORG));
      assertSame(b, manager.getScheduleTask(b.getTaskId(), OTHER));
   }

   @Test
   void overlappingReloadsSerializeAndEndConsistent() throws Exception {
      ScheduleTask t = task("c1", ORG);
      when(ext.getTasks(ORG)).thenAnswer(inv -> {
         Thread.sleep(50);
         return List.of(t);
      });
      ExecutorService ex = Executors.newFixedThreadPool(2);
      Future<?> f1 = ex.submit(() -> manager.reloadExtensions(ORG));
      Future<?> f2 = ex.submit(() -> manager.reloadExtensions(ORG));
      f1.get(10, TimeUnit.SECONDS);
      f2.get(10, TimeUnit.SECONDS);
      ex.shutdownNow();
      assertSame(t, manager.getScheduleTask(t.getTaskId(), ORG));
      assertEquals(1, manager.getScheduleTasks(ORG).stream()
         .filter(x -> x.getType() == ScheduleTask.Type.CYCLE_TASK).count());
   }

   private static ScheduleTask task(String name, String org) {
      ScheduleTask task = new ScheduleTask("DataCycle Task: " + name,
                                           ScheduleTask.Type.CYCLE_TASK);
      task.setOwner(new IdentityID(XPrincipal.SYSTEM, org));
      task.setCycleInfo(new DataCycleManager.CycleInfo(name, org));
      return task;
   }
}
