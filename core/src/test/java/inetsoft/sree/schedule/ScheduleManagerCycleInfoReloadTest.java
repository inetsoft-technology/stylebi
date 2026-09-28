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

import inetsoft.sree.internal.DataCycleManager.CycleInfo;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/*
 * Tier: [unit] - real ScheduleManager with a mock ScheduleClient and a mock ScheduleExt.
 *
 * Regression: Bug #77191. A data cycle task whose only change is in its CycleInfo
 * (notification recipients/flags/threshold) must be re-registered with the scheduler,
 * otherwise the Quartz JobDataMap keeps the stale task and mails the old recipients.
 *
 * ScheduleTask.equals() ignores cycleInfo, so taskAdded() is verified by identity or by
 * call count - never with verify(...).taskAdded(task), which Mockito would match via equals.
 */
@Tag("core")
class ScheduleManagerCycleInfoReloadTest {
   private static final String ORG = "host-org";

   private ScheduleClient scheduleClient;
   private ScheduleExt ext;
   private ScheduleManager manager;

   @BeforeEach
   void setUp() {
      scheduleClient = mock(ScheduleClient.class);
      ext = mock(ScheduleExt.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.getOrganizationIDs()).thenReturn(new String[0]);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      manager = new ScheduleManager(securityEngine, mock(Cluster.class), scheduleClient, null);
      manager.addScheduleExt(ext);
   }

   @Test
   void reloadReRegistersTaskWhenOnlyCycleInfoEmailChanged() throws Exception {
      ScheduleTask a = createCycleTask("alice@example.com");
      ScheduleTask aPrime = createCycleTask("bob@example.com");
      when(ext.getTasks(ORG)).thenReturn(List.of(a), List.of(aPrime));

      manager.reloadExtensions(ORG);
      manager.reloadExtensions(ORG);

      verify(scheduleClient, times(1)).taskAdded(argThat((ScheduleTask t) -> t == a));
      verify(scheduleClient, times(1)).taskAdded(argThat((ScheduleTask t) -> t == aPrime));
      verify(scheduleClient, times(2)).taskAdded(any(ScheduleTask.class));
   }

   @Test
   void reloadSkipsTaskWhenCycleInfoValuesAreUnchanged() throws Exception {
      ScheduleTask a = createCycleTask("alice@example.com");
      ScheduleTask aCopy = createCycleTask("alice@example.com");
      when(ext.getTasks(ORG)).thenReturn(List.of(a), List.of(aCopy));

      manager.reloadExtensions(ORG);
      manager.reloadExtensions(ORG);

      verify(scheduleClient, times(1)).taskAdded(any(ScheduleTask.class));
      verify(scheduleClient, never()).taskAdded(argThat((ScheduleTask t) -> t == aCopy));
   }

   // mirrors DataCycleManager.generateTasks(): a fresh task with a freshly built CycleInfo
   private static ScheduleTask createCycleTask(String endEmail) {
      ScheduleTask task = new ScheduleTask("DataCycle Task: c1", ScheduleTask.Type.CYCLE_TASK);
      task.setEditable(false);
      task.setRemovable(false);
      task.setEnabled(true);
      task.setOwner(new IdentityID(XPrincipal.SYSTEM, ORG));

      CycleInfo info = new CycleInfo("c1", ORG);
      info.setEndNotify(true);
      info.setEndEmail(endEmail);
      task.setCycleInfo(info);
      return task;
   }
}
