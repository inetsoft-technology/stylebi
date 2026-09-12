/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

/*
 * Test strategy
 *
 * ExportTaskController has real in-controller logic in getDependentTasks():
 *   - Splits the comma-separated task list to build a "selected" set
 *   - Recursively resolves dependencies not already in the selected set
 *   - Returns one TaskDependencyModel per missing dependency, noting which task requires it
 *
 * exportScheduledTasks() delegates to scheduleService and writes to HttpServletResponse;
 * that is covered by E2E tests.
 *
 * Coverage scope:
 *   [task not found]                      scheduleManager returns null → dependency skipped → empty result
 *   [direct dependency missing]           dep not in selected set → TaskDependencyModel returned
 *   [dependency already selected]         dep in selected set → excluded from result
 */

import inetsoft.sree.schedule.*;
import inetsoft.web.admin.schedule.model.TaskDependencyModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ExportTaskControllerTest {

   @Mock private ScheduleService scheduleService;
   @Mock private ScheduleManager scheduleManager;
   @Mock private ScheduleTask taskA;
   @Mock private Principal principal;

   private ExportTaskController controller;

   @BeforeEach
   void setUp() {
      controller = new ExportTaskController(scheduleService, scheduleManager);
   }

   // -------------------------------------------------------------------------
   // getDependentTasks()
   // -------------------------------------------------------------------------

   // [task not found] scheduleManager returns null → no dependencies resolved → empty list
   @Test
   void getDependentTasks_taskNotFound_returnsEmpty() {
      when(scheduleManager.getScheduleTask("missingTask")).thenReturn(null);

      List<TaskDependencyModel> result = controller.getDependentTasks("missingTask", principal);

      assertTrue(result.isEmpty());
   }

   // [direct dependency missing] taskA depends on "depTask" which is not in selected set
   @Test
   void getDependentTasks_directDependency_includesMissingDependency() {
      when(scheduleManager.getScheduleTask("taskA")).thenReturn(taskA);
      when(taskA.getDependency()).thenReturn(Collections.enumeration(List.of("depTask")));
      // depTask not found → recursion stops; depTask is not in selected → appears in result

      List<TaskDependencyModel> result = controller.getDependentTasks("taskA", principal);

      assertEquals(1, result.size());
      assertEquals("depTask", result.get(0).task());
      assertEquals("taskA", result.get(0).dependency());
   }

   // [dependency already selected] taskA depends on depTask, but depTask is also in the selected set
   @Test
   void getDependentTasks_dependencyAlreadySelected_isExcluded() {
      when(scheduleManager.getScheduleTask("taskA")).thenReturn(taskA);
      when(taskA.getDependency()).thenReturn(Collections.enumeration(List.of("depTask")));
      // depTask is in the selected set → should be excluded from the result

      List<TaskDependencyModel> result = controller.getDependentTasks("taskA,depTask", principal);

      assertTrue(result.isEmpty(), "depTask is already selected, so no unresolved dependency");
   }
}
