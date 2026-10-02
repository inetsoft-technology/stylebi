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
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77359: {@code ScheduleManager.setScheduleTask} refuses a task whose owner is in another
 * organization than the one it's stored in, unless the stored task already is. The public API
 * update (enterprise {@code ScheduleApiService}) and the EM rename remove the stored task before
 * they save it, so the check must be done before the task is removed or the task is lost.
 *
 * Uses the real SecurityEngine (SecurityTestDataBuilder) and the real ScheduleManager bean.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleTaskOwnerOrgReplaceTest {
   private static final String ORG_A = "rpoorga";
   private static final String ORG_B = "rpoorgb";
   // a site admin of org A
   private static final IdentityID SADM = new IdentityID("sadm", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);
   // stored in org B with an owner of org A, e.g. created through the public API by the site
   // admin working in org B with its own identity as the owner, before the owner org check
   private static final String FOREIGN_JOB = SADM.convertToKey() + ":Report";
   private static final String OWN_JOB = BOB.convertToKey() + ":Nightly";
   private static final Set<String> NAMES = Set.of("Report", "Nightly", "Renamed");

   private static SecurityTestDataBuilder builder;

   @Autowired
   ScheduleManager scheduleManager;

   private MockedStatic<SUtil> sutilStatic;
   private ScheduleClient scheduleClient;
   private ScheduleService service;
   private SRPrincipal siteAdminInB; // site admin switched into org B
   private SRPrincipal dave;         // org admin of org B

   @BeforeAll
   static void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("rpoOrgA", ORG_A)
         .addOrg("rpoOrgB", ORG_B)
         .addOrgAdminRole("rpoOrgAdminB", ORG_B)
         .addSysAdminRole("rpoSiteAdmin", ORG_A)
         .addUser("sadm", ORG_A, "password")
         .addUser("bob", ORG_B, "password")
         .addUser("dave", ORG_B, "password")
         .addUserToRole("sadm", "rpoSiteAdmin", ORG_A)
         .addUserToRole("dave", "rpoOrgAdminB", ORG_B);
      builder.setup();
   }

   @AfterAll
   static void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      siteAdminInB = loginPrincipalOf("sadm", ORG_A);
      siteAdminInB.setProperty("curr_org_id", ORG_B);
      dave = loginPrincipalOf("dave", ORG_B);

      ScheduleTask foreign = new ScheduleTask("Report");
      foreign.setOwner(SADM);
      ScheduleTask own = new ScheduleTask("Nightly");
      own.setOwner(BOB);
      assertEquals(FOREIGN_JOB, foreign.getTaskId());
      assertEquals(OWN_JOB, own.getTaskId());

      // stored directly, as a task saved before the owner org check
      withContext(dave, () -> scheduleManager.save(List.of(foreign, own), ORG_B));

      scheduleClient = (ScheduleClient) ReflectionTestUtils.getField(scheduleManager, "scheduleClient");
      reset(scheduleClient);
      when(scheduleClient.isReady()).thenReturn(true);

      ScheduleTaskFolderService folderService = mock(ScheduleTaskFolderService.class);
      service = new ScheduleService(null, scheduleManager, scheduleClient, null, null, null,
                                    null, null, null, folderService, null, null, null);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      sutilStatic.close();

      for(String org : new String[] { ORG_A, ORG_B }) {
         @SuppressWarnings("unchecked")
         Map<String, ScheduleTask> map =
            (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(org);
         map.values().removeIf(t -> t != null && NAMES.contains(t.getName()));
      }
   }

   // ---- public API update (remove then save under the same id) ----

   @Test
   void apiUpdate_storedForeignOwnerTask_isSavedNotLost() throws Exception {
      withContextPrincipal(siteAdminInB);
      ScheduleTask modified = (ScheduleTask) stored(FOREIGN_JOB).clone();
      modified.setDescription("edited");

      apiUpdate(FOREIGN_JOB, modified, siteAdminInB);

      ScheduleTask saved = stored(FOREIGN_JOB);
      assertNotNull(saved, "the edited task is not lost");
      assertEquals("edited", saved.getDescription());
   }

   @Test
   void apiUpdate_foreignOwnerTaskUnderNewId_isRefusedAndKept() throws Exception {
      // a new id with an owner of another organization is not a stored task, refused
      withContextPrincipal(siteAdminInB);
      ScheduleTask renamed = (ScheduleTask) stored(FOREIGN_JOB).clone();
      renamed.setName("Renamed");

      assertThrows(Exception.class, () -> apiUpdate(FOREIGN_JOB, renamed, siteAdminInB));

      verify(scheduleClient, never()).taskRemoved(anyString());
      assertNotNull(stored(FOREIGN_JOB), "the original task is kept");
      assertNull(scheduleManager.getScheduleTask(renamed.getTaskId(), ORG_B));
   }

   @Test
   void apiUpdate_ownOrgTask_isSaved() throws Exception {
      withContextPrincipal(dave);
      ScheduleTask modified = (ScheduleTask) stored(OWN_JOB).clone();
      modified.setDescription("edited");

      apiUpdate(OWN_JOB, modified, dave);

      assertEquals("edited", stored(OWN_JOB).getDescription());
   }

   // ---- EM rename (remove the old id, save the new id) ----

   @Test
   void emRename_storedForeignOwnerTask_isRefusedAndKept() throws Exception {
      withContextPrincipal(siteAdminInB);

      assertThrows(Exception.class, () -> ReflectionTestUtils.invokeMethod(
         service, "renameTask", FOREIGN_JOB, SADM.convertToKey() + ":Renamed", SADM,
         siteAdminInB));

      verify(scheduleClient, never()).taskRemoved(anyString());
      assertNotNull(stored(FOREIGN_JOB), "the original task is kept");
   }

   @Test
   void emOwnerChange_storedForeignOwnerTaskToOwnOrgOwner_isSavedUnderNewId() throws Exception {
      // the EM owner change of a stored foreign-owner task to an owner of its organization
      // renames it to the new owner's id, the check is done with the owner it's saved with
      withContextPrincipal(siteAdminInB);
      String newId = BOB.convertToKey() + ":Report";

      ReflectionTestUtils.invokeMethod(service, "renameTask", FOREIGN_JOB, newId, BOB,
                                       siteAdminInB);

      assertNull(stored(FOREIGN_JOB));
      ScheduleTask saved = scheduleManager.getScheduleTask(newId, ORG_B);
      assertNotNull(saved, "the task is saved under the new owner's id");
      assertEquals(BOB, saved.getOwner());
      assertEquals(newId, saved.getTaskId());
   }

   @Test
   void emOwnerChange_ownOrgTaskToForeignOwner_isRefusedAndKept() throws Exception {
      // a new owner of another organization is still refused before the task is removed, and
      // the stored task is not changed
      withContextPrincipal(siteAdminInB);
      String newId = SADM.convertToKey() + ":Nightly";

      assertThrows(Exception.class, () -> ReflectionTestUtils.invokeMethod(
         service, "renameTask", OWN_JOB, newId, SADM, siteAdminInB));

      verify(scheduleClient, never()).taskRemoved(anyString());
      ScheduleTask kept = stored(OWN_JOB);
      assertNotNull(kept, "the original task is kept");
      assertEquals(BOB, kept.getOwner());
      assertNull(scheduleManager.getScheduleTask(newId, ORG_B));
   }

   @Test
   void emRename_ownOrgTask_isRenamed() throws Exception {
      withContextPrincipal(dave);
      String newId = BOB.convertToKey() + ":Renamed";

      ReflectionTestUtils.invokeMethod(service, "renameTask", OWN_JOB, newId, BOB, dave);

      assertNull(stored(OWN_JOB));
      assertNotNull(scheduleManager.getScheduleTask(newId, ORG_B));
   }

   // ---- helpers ----

   // the public API update, enterprise ScheduleApiService.updateTaskInScheduleManager()
   private void apiUpdate(String oldTaskId, ScheduleTask task, Principal principal)
      throws Exception
   {
      scheduleManager.replaceScheduleTask(oldTaskId, task, null, principal);
   }

   private ScheduleTask stored(String taskId) {
      return scheduleManager.getScheduleTask(taskId, ORG_B);
   }

   private static SRPrincipal loginPrincipalOf(String name, String orgID) {
      SRPrincipal principal = builder.principalOf(name, orgID);
      principal.setProperty("__internal__", "true");
      return principal;
   }

   private static void withContextPrincipal(Principal principal) {
      ThreadContext.setContextPrincipal(principal);
   }

   private static void withContext(Principal principal, ThrowingRunnable runnable) throws Exception {
      Principal old = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      try {
         runnable.run();
      }
      finally {
         ThreadContext.setContextPrincipal(old);
      }
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }
}
