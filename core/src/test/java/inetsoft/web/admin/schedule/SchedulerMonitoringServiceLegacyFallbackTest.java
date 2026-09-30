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
package inetsoft.web.admin.schedule;

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77358: JMX ScheduleMonitorMBean.runTask/stopTask go through
 * SchedulerMonitoringService with no context principal, so the REAL ScheduleManager looks the
 * name up in host-org. Its legacy ':' fallback resolves another org's job id
 * ("bob~;~orgB:Nightly") to host-org's bare-id internal task "Nightly"; the quartz job that is
 * run/stopped must be that resolved "Nightly", and another org's task that host-org cannot
 * resolve (here a disabled one) must be refused, never forwarded to the scheduler.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SchedulerMonitoringServiceLegacyFallbackTest {
   private static final String ORG_B = "jmxOrgB";
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);
   private static final String LEGACY_NAME = "jmx77358Nightly";
   private static final String OTHER_NAME = "jmx77358Report";
   private static final String VICTIM_JOB = BOB.convertToKey() + ":" + LEGACY_NAME;
   private static final String UNRESOLVED_JOB = BOB.convertToKey() + ":" + OTHER_NAME;

   @Autowired
   ScheduleManager scheduleManager;

   private String hostOrg;
   private ScheduleClient scheduleClient;
   private SchedulerMonitoringService service;

   @BeforeEach
   void setUp() throws Exception {
      ThreadContext.setContextPrincipal(null); // JMX connector thread: no principal
      hostOrg = Organization.getDefaultOrganizationID();

      // host-org: bare-id internal task, the target of the legacy fallback
      ScheduleTask legacy = new ScheduleTask(LEGACY_NAME, ScheduleTask.Type.INTERNAL_TASK);
      legacy.setOwner(new IdentityID("admin", hostOrg));
      legacy.setEnabled(true);
      assertEquals(LEGACY_NAME, legacy.getTaskId(), "precondition: bare id");
      scheduleManager.save(List.of(legacy), hostOrg);

      // org B: bob's tasks, both disabled
      ScheduleTask bobsNightly = new ScheduleTask(LEGACY_NAME);
      bobsNightly.setOwner(BOB);
      bobsNightly.setEnabled(false);
      ScheduleTask bobsReport = new ScheduleTask(OTHER_NAME);
      bobsReport.setOwner(BOB);
      bobsReport.setEnabled(false);
      assertEquals(VICTIM_JOB, bobsNightly.getTaskId());
      assertEquals(UNRESOLVED_JOB, bobsReport.getTaskId());
      scheduleManager.save(List.of(bobsNightly, bobsReport), ORG_B);

      scheduleClient = mock(ScheduleClient.class);
      when(scheduleClient.isReady()).thenReturn(true);
      service = new SchedulerMonitoringService(scheduleManager, null, null, null, null, null,
                                               scheduleClient);
   }

   @AfterEach
   void tearDown() {
      for(String org : new String[] { hostOrg, ORG_B }) {
         @SuppressWarnings("unchecked")
         Map<String, ScheduleTask> map =
            (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(org);
         map.values().removeIf(t -> t != null &&
            (LEGACY_NAME.equals(t.getName()) || OTHER_NAME.equals(t.getName())));
      }
   }

   @Test
   void runAndStop_otherOrgJobId_actOnlyOnFallbackResolvedHostTask() throws Exception {
      service.runTask(VICTIM_JOB);
      service.stopTask(VICTIM_JOB);

      verify(scheduleClient).runNow(LEGACY_NAME);
      verify(scheduleClient).stopNow(LEGACY_NAME);
      verify(scheduleClient, never()).runNow(VICTIM_JOB);
      verify(scheduleClient, never()).stopNow(VICTIM_JOB);
   }

   @Test
   void runAndStop_otherOrgDisabledTaskNotInHostOrg_refusedWithoutSchedulerCall() throws Exception {
      assertThrows(Exception.class, () -> service.runTask(UNRESOLVED_JOB));
      assertThrows(Exception.class, () -> service.stopTask(UNRESOLVED_JOB));

      verify(scheduleClient, never()).runNow(anyString());
      verify(scheduleClient, never()).stopNow(anyString());
   }
}
