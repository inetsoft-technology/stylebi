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
package inetsoft.util;

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Bug: copying an organization left a task's "Run After" completion condition pointing at a
 * Data Cycle task blank in the new org. Root cause: MigrateScheduleTask.processAssemblies()
 * rewrites a CompletionCondition's referenced task id via
 * MigrateUtil.getNewOrgTaskName(completionTask, oldOrgId, newOrgId) during org copy, but that
 * method located the owner/task-name split with taskName.indexOf(":"). Cycle-task ids are built
 * as "<owner>__<name>" (ScheduleTask.getTaskId(), Type.CYCLE_TASK branch), and the name itself
 * ("DataCycle Task: Cycle1") contains its own colon, so the first colon found was the one inside
 * the task name -- not the real "__" delimiter. That corrupted the parsed owner org id so the
 * "oorgID.equals(identityID.orgID)" guard never matched, the rewrite was skipped entirely, and
 * the condition kept referencing the OLD org's task id, which does not exist in the new org.
 */
@Tag("core")
class MigrateUtilTest {

   @Test
   void getNewOrgTaskName_cycleTaskCompletionReference_rewritesOwnerOrgPreservingTaskName() {
      String oldOrgId = "host-org";
      String newOrgId = "organization0";
      String cycleTaskId = new IdentityID("INETSOFT_SYSTEM", oldOrgId).convertToKey() +
         "__" + DataCycleManager.TASK_PREFIX + "Cycle1";

      String result = MigrateUtil.getNewOrgTaskName(cycleTaskId, oldOrgId, newOrgId);

      String expected = new IdentityID("INETSOFT_SYSTEM", newOrgId).convertToKey() +
         "__" + DataCycleManager.TASK_PREFIX + "Cycle1";
      assertEquals(expected, result);
   }

   @Test
   void getNewOrgTaskName_normalTaskCompletionReference_rewritesOwnerOrg() {
      String oldOrgId = "host-org";
      String newOrgId = "organization0";
      String parentTaskId = new IdentityID("admin", oldOrgId).convertToKey() + ":parent task";

      String result = MigrateUtil.getNewOrgTaskName(parentTaskId, oldOrgId, newOrgId);

      String expected = new IdentityID("admin", newOrgId).convertToKey() + ":parent task";
      assertEquals(expected, result);
   }

   @Test
   void getNewOrgTaskName_sameOrg_returnsUnchanged() {
      String taskId = new IdentityID("admin", "host-org").convertToKey() + ":task1";

      assertEquals(taskId, MigrateUtil.getNewOrgTaskName(taskId, "host-org", "host-org"));
   }

   @Test
   void getNewOrgTaskName_noOwnerDelimiter_returnsUnchanged() {
      // internal/plain task names have no IdentityID.KEY_DELIMITER prefix
      assertEquals("__balance tasks__",
         MigrateUtil.getNewOrgTaskName("__balance tasks__", "host-org", "organization0"));
   }

   @Test
   void getNewOrgTaskName_ownerOrgDoesNotMatchOldOrg_returnsUnchanged() {
      String taskId = new IdentityID("admin", "some-other-org").convertToKey() + ":task1";

      assertEquals(taskId, MigrateUtil.getNewOrgTaskName(taskId, "host-org", "organization0"));
   }
}
