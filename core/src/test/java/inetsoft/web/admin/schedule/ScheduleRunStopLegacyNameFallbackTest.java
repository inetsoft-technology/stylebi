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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77262: run/stop must pass the resolved task id to the scheduler, not the raw client
 * name. Bug #77356: the legacy pre-13.1 fallback in
 * {@link ScheduleManager#getScheduleTask(String, String)} no longer resolves another org's id
 * "bob~;~orgB:Nightly" in org A to org A's owner-less task "Nightly", so run/stop of that id in
 * org A fails with task-not-found and neither "Nightly" nor bob's job is triggered or stopped.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleRunStopLegacyNameFallbackTest {
   private static final String ORG_A = "fbOrgA";
   private static final String ORG_B = "fbOrgB";
   private static final IdentityID ALICE = new IdentityID("alice", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);
   private static final String LEGACY_JOB = "Nightly";
   private static final String VICTIM_JOB = BOB.convertToKey() + ":Nightly";
   private static final String OWN_JOB = ALICE.convertToKey() + ":Daily";

   @Autowired
   ScheduleManager scheduleManager;

   private SRPrincipal alice;
   private ScheduleClient scheduleClient;
   private ScheduleService service;
   private MockedStatic<ScheduleManager> managerStatic;
   private MockedStatic<SUtil> sutilStatic;

   @BeforeEach
   void setUp() throws Exception {
      // org A: a task whose id carries no owner prefix ("Nightly"), owned by alice
      ScheduleTask legacy = new ScheduleTask(LEGACY_JOB, ScheduleTask.Type.INTERNAL_TASK);
      legacy.setOwner(ALICE);
      legacy.setEnabled(true);
      assertEquals(LEGACY_JOB, legacy.getTaskId(), "precondition: owner-less id");

      // org A: an ordinary owner-prefixed task, used as the positive control
      ScheduleTask own = new ScheduleTask("Daily");
      own.setOwner(ALICE);
      own.setEnabled(true);
      assertEquals(OWN_JOB, own.getTaskId());
      scheduleManager.save(List.of(legacy, own), ORG_A);

      // org B: bob's ordinary task, quartz JobKey "bob~;~fbOrgB:Nightly"
      ScheduleTask bobs = new ScheduleTask("Nightly");
      bobs.setOwner(BOB);
      bobs.setEnabled(true);
      assertEquals(VICTIM_JOB, bobs.getTaskId());
      scheduleManager.save(List.of(bobs), ORG_B);

      alice = new SRPrincipal(ALICE, new IdentityID[0], new String[0], ORG_A,
                              Tool.getSecureRandom().nextLong());
      alice.setIgnoreLogin(true);
      ThreadContext.setContextPrincipal(alice);

      // real hasTaskPermission() NPEs for unregistered fixture orgs; stub to production result:
      // only alice's own tasks are permitted, bob's never
      managerStatic = mockStatic(ScheduleManager.class, CALLS_REAL_METHODS);
      managerStatic.when(() -> ScheduleManager.hasTaskPermission(
         any(IdentityID.class), any(Principal.class), any(ResourceAction.class)))
         .thenAnswer(inv -> ALICE.equals(inv.getArgument(0)));
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(() -> SUtil.getActionRecord(
         any(Principal.class), anyString(), anyString(), anyString()))
         .thenReturn(mock(ActionRecord.class));

      scheduleClient = mock(ScheduleClient.class);
      when(scheduleClient.isReady()).thenReturn(true);
      service = new ScheduleService(null, scheduleManager, scheduleClient, null, null, null,
                                    null, null, null, null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      sutilStatic.close();
      managerStatic.close();
      ThreadContext.setContextPrincipal(null);

      for(String org : new String[] { ORG_A, ORG_B }) {
         @SuppressWarnings("unchecked")
         Map<String, ScheduleTask> map =
            (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(org);
         map.values().removeIf(t -> t != null &&
            ("Nightly".equals(t.getName()) || "Daily".equals(t.getName())));
      }
   }

   @Test
   void runScheduledTask_otherOrgJobName_isNotFound() throws Exception {
      assertThrows(MessageException.class, () -> service.runScheduledTask(VICTIM_JOB, alice));

      verify(scheduleClient, never()).runNow(VICTIM_JOB);
      verify(scheduleClient, never()).runNow(LEGACY_JOB);
   }

   @Test
   void stopScheduledTask_otherOrgJobName_isNotFound() throws Exception {
      assertThrows(MessageException.class, () -> service.stopScheduledTask(VICTIM_JOB, alice));

      verify(scheduleClient, never()).stopNow(VICTIM_JOB);
      verify(scheduleClient, never()).stopNow(LEGACY_JOB);
   }

   @Test
   void runScheduledTask_ownOrgPrefixedLegacyName_runsResolvedTask() throws Exception {
      service.runScheduledTask(ALICE.convertToKey() + ":" + LEGACY_JOB, alice);

      verify(scheduleClient).runNow(LEGACY_JOB);
   }

   @Test
   void runScheduledTask_ownPrefixedTask_runsWithItsId() throws Exception {
      service.runScheduledTask(OWN_JOB, alice);

      verify(scheduleClient).runNow(OWN_JOB);
   }

   @Test
   void stopScheduledTask_ownPrefixedTask_stopsWithItsId() throws Exception {
      service.stopScheduledTask(OWN_JOB, alice);

      verify(scheduleClient).stopNow(OWN_JOB);
   }
}
