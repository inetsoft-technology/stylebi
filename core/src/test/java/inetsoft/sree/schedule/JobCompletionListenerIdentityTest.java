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

import inetsoft.sree.security.Group;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.security.Principal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static inetsoft.sree.schedule.Scheduler.Status.FINISHED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77168 -- {@code JobCompletionListener.jobWasExecuted()} builds the principal used both for
 * the task-finish audit record and (via {@code removeTask()}) the permission context for
 * {@code deleteIfNoMoreRun} cleanup. Like {@code ScheduleTaskJob}/{@code ClusterJobStore}, it must
 * not build that principal from a kept-but-unresolved execute-as placeholder identity (Bug
 * #77120/#77168) while security is disabled -- it must fall back to the task owner.
 *
 * <p>Uses {@link SchedulerTestHarness}, which runs a real in-process Quartz scheduler with
 * {@code JobCompletionListener} wired in and a mocked {@code SecurityEngine} whose
 * {@code isSecurityEnabled()} defaults to {@code false} (Mockito's default for an unstubbed
 * boolean), i.e. exactly the "security disabled" scenario this bug is about.
 */
@Tag("core")
class JobCompletionListenerIdentityTest {
   private SchedulerTestHarness harness;
   private Field auditInstanceField;
   private Audit originalAudit;

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();

      auditInstanceField = Audit.class.getDeclaredField("INSTANCE");
      auditInstanceField.setAccessible(true);
      originalAudit = (Audit) auditInstanceField.get(null);
   }

   @AfterEach
   void tearDown() throws Exception {
      auditInstanceField.set(null, originalAudit);
      harness.close();
   }

   @Test
   void jobWasExecuted_securityDisabled_placeholderIdentity_auditsAsOwnerNotPlaceholder()
      throws Exception
   {
      AtomicReference<Principal> capturedPrincipal = new AtomicReference<>();
      Audit capturingAudit = new Audit() {
         @Override
         public void auditAction(ActionRecord record, Principal principal) {
            capturedPrincipal.set(principal);
         }
      };
      auditInstanceField.set(null, capturingAudit);

      ScheduleTask task = new ScheduleTask("t1-placeholder-identity");
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      // Simulates the kept-but-unresolved placeholder that ScheduleTask.parseXML now keeps
      // regardless of security state (Bug #77120/#77168), instead of dropping it back to null.
      task.setIdentity(new Group(new IdentityID("phantom-group", "host")));
      task.addAction(mock(ScheduleAction.class));

      harness.registerTask(task);
      harness.triggerNow(task.getTaskId());
      harness.waitForStatus(task.getTaskId(), FINISHED, Duration.ofSeconds(5));

      Principal principal = capturedPrincipal.get();
      assertNotNull(principal, "the task-finish audit action must have been recorded");

      // SUtil.getPrincipal(Identity, ...) -- the group/placeholder-identity path -- is the only
      // one of the two branches that stamps "identity_type"="group" on the built principal (see
      // SUtil.java); the owner-fallback path (SUtil.getScheduleTaskOwnerPrincipal ->
      // SUtil.getPrincipal(IdentityID, ...)) never sets it. This distinguishes which branch
      // JobCompletionListener actually took regardless of how the harness's minimal security
      // mock happens to resolve the owner identity itself.
      assertInstanceOf(XPrincipal.class, principal);
      assertNotEquals("group", ((XPrincipal) principal).getProperty("identity_type"),
         "with security disabled, the principal must come from the owner-fallback branch, " +
         "not from SUtil.getPrincipal(Identity, ...) built off the unresolved placeholder");
   }
}
