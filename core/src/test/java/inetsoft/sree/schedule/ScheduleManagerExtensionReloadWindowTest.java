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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Tier: [unit] - real ScheduleManager with a mock ScheduleClient and a mock ScheduleExt whose
 * getTasks blocks, standing in for the slow DataCycleManager regeneration.
 *
 * Regression: Bug #79263. reloadExtensions emptied extensionTasks before regenerating, so the
 * unlocked getScheduleTask (run-tasks) returned null for a cycle task the list still showed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleManagerExtensionReloadWindowTest {
   private static final String ORG = "host-org";

   private ScheduleExt ext;
   private ScheduleManager manager;
   private ExecutorService executor;

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
      executor = Executors.newSingleThreadExecutor();
   }

   @AfterEach
   void tearDown() {
      executor.shutdownNow();
   }

   @Test
   void taskStaysVisibleWhileReloadRegenerates() throws Exception {
      ScheduleTask oldTask = createCycleTask("c1");
      ScheduleTask newTask = createCycleTask("c1");
      String c1Id = oldTask.getTaskId();
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      when(ext.getTasks(ORG)).thenReturn(List.of(oldTask)).thenAnswer(inv -> {
         entered.countDown();
         assertTrue(release.await(10, TimeUnit.SECONDS));
         return List.of(newTask);
      });

      manager.reloadExtensions(ORG);
      assertSame(oldTask, manager.getScheduleTask(c1Id, ORG));

      Future<?> reload = executor.submit(() -> manager.reloadExtensions(ORG));
      assertTrue(entered.await(10, TimeUnit.SECONDS));

      // during the regeneration the old task must still resolve
      assertSame(oldTask, manager.getScheduleTask(c1Id, ORG));

      release.countDown();
      reload.get(10, TimeUnit.SECONDS);
      assertSame(newTask, manager.getScheduleTask(c1Id, ORG));
   }

   @Test
   void taskNoLongerGeneratedIsRemovedAfterReload() throws Exception {
      ScheduleTask c1 = createCycleTask("c1");
      ScheduleTask c2 = createCycleTask("c2");
      String c1Id = c1.getTaskId();
      when(ext.getTasks(ORG)).thenReturn(List.of(c1, c2)).thenReturn(List.of(c2));

      manager.reloadExtensions(ORG);
      assertNotNull(manager.getScheduleTask(c1Id, ORG));

      manager.reloadExtensions(ORG);
      assertNull(manager.getScheduleTask(c1Id, ORG));
      assertSame(c2, manager.getScheduleTask(c2.getTaskId(), ORG));
      assertEquals(1, manager.getScheduleTasks(ORG).stream()
         .filter(t -> t.getType() == ScheduleTask.Type.CYCLE_TASK).count());
   }

   private static ScheduleTask createCycleTask(String name) {
      ScheduleTask task = new ScheduleTask("DataCycle Task: " + name,
                                           ScheduleTask.Type.CYCLE_TASK);
      task.setOwner(new IdentityID(XPrincipal.SYSTEM, ORG));
      task.setCycleInfo(new DataCycleManager.CycleInfo(name, ORG));
      return task;
   }
}
