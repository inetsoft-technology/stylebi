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
 *   - Only tasks that resolve in the caller's org and that the caller may export are used
 *
 * exportScheduledTasks() checks all the tasks through scheduleService before touching the
 * response, so a rejected export does not produce an empty or truncated download.
 *
 * Coverage scope:
 *   [task not found]                      scheduleManager returns null → dependency skipped → empty result
 *   [direct dependency missing]           dep not in selected set → TaskDependencyModel returned
 *   [dependency already selected]         dep in selected set → excluded from result
 *   [dependency not exportable]           caller can't export dep → excluded from result (Bug #77060)
 *   [dependency not found]                dep doesn't exist in caller's org → excluded (Bug #77060)
 *   [selected task not exportable]        dependencies of a hidden task are not revealed (Bug #77060)
 *   [export rejected]                     nothing is written to the response (Bug #77060)
 */

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.schedule.model.TaskDependencyModel;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ExportTaskControllerTest {
   private static final String ORG_A = "orgA";

   @Mock private ScheduleService scheduleService;
   @Mock private ScheduleManager scheduleManager;
   @Mock private ScheduleTask taskA;
   @Mock private ScheduleTask depTask;
   @Mock private Principal principal;
   @Mock private OrganizationManager organizationManager;
   @Mock private HttpServletResponse response;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private ExportTaskController controller;

   @BeforeEach
   void setUp() {
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      lenient().when(organizationManager.getCurrentOrgID(any(Principal.class))).thenReturn(ORG_A);
      controller = new ExportTaskController(scheduleService, scheduleManager);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   // -------------------------------------------------------------------------
   // getDependentTasks()
   // -------------------------------------------------------------------------

   // [task not found] scheduleManager returns null → no dependencies resolved → empty list
   @Test
   void getDependentTasks_taskNotFound_returnsEmpty() {
      stubTask("missingTask", null, false);

      List<TaskDependencyModel> result = controller.getDependentTasks("missingTask", principal);

      assertTrue(result.isEmpty());
   }

   // [direct dependency missing] taskA depends on "depTask" which is not in selected set
   @Test
   void getDependentTasks_directDependency_includesMissingDependency() {
      stubTask("taskA", taskA, true);
      stubTask("depTask", depTask, true);
      when(taskA.getDependency()).thenReturn(Collections.enumeration(List.of("depTask")));
      when(depTask.getDependency()).thenReturn(Collections.emptyEnumeration());

      List<TaskDependencyModel> result = controller.getDependentTasks("taskA", principal);

      assertEquals(1, result.size());
      assertEquals("depTask", result.get(0).task());
      assertEquals("taskA", result.get(0).dependency());
   }

   // [dependency already selected] taskA depends on depTask, but depTask is also in the selected set
   @Test
   void getDependentTasks_dependencyAlreadySelected_isExcluded() {
      stubTask("taskA", taskA, true);
      stubTask("depTask", depTask, true);
      when(taskA.getDependency()).thenReturn(Collections.enumeration(List.of("depTask")));
      when(depTask.getDependency()).thenReturn(Collections.emptyEnumeration());

      List<TaskDependencyModel> result = controller.getDependentTasks("taskA,depTask", principal);

      assertTrue(result.isEmpty(), "depTask is already selected, so no unresolved dependency");
   }

   // [dependency not exportable] the dependency exists but the caller may not export it, so it
   // must not be offered, otherwise adding it would make the whole export fail
   @Test
   void getDependentTasks_dependencyNotExportable_isExcluded() {
      stubTask("taskA", taskA, true);
      stubTask("depTask", depTask, false);
      when(taskA.getDependency()).thenReturn(Collections.enumeration(List.of("depTask")));
      lenient().when(depTask.getDependency()).thenReturn(Collections.emptyEnumeration());

      List<TaskDependencyModel> result = controller.getDependentTasks("taskA", principal);

      assertTrue(result.isEmpty(), "a dependency the caller can't export must not be listed");
   }

   // [dependency not found] the dependency doesn't exist in the caller's org
   @Test
   void getDependentTasks_dependencyNotFound_isExcluded() {
      stubTask("taskA", taskA, true);
      stubTask("depTask", null, false);
      when(taskA.getDependency()).thenReturn(Collections.enumeration(List.of("depTask")));

      List<TaskDependencyModel> result = controller.getDependentTasks("taskA", principal);

      assertTrue(result.isEmpty(), "a dependency that doesn't exist must not be listed");
   }

   // [selected task not exportable] the dependencies of a task the caller can't see are not revealed
   @Test
   void getDependentTasks_selectedTaskNotExportable_returnsEmpty() {
      stubTask("taskA", taskA, false);
      stubTask("depTask", depTask, true);
      lenient().when(taskA.getDependency())
         .thenReturn(Collections.enumeration(List.of("depTask")));
      lenient().when(depTask.getDependency()).thenReturn(Collections.emptyEnumeration());

      List<TaskDependencyModel> result = controller.getDependentTasks("taskA", principal);

      assertTrue(result.isEmpty(), "dependencies of a hidden task must not be listed");
   }

   // -------------------------------------------------------------------------
   // exportScheduledTasks()
   // -------------------------------------------------------------------------

   // [export rejected] the check fails before anything is written to the response
   @Test
   void exportScheduledTasks_rejected_writesNothing() throws Exception {
      when(scheduleService.getExportTasks(new String[] { "taskA", "__asset file backup__" },
                                          principal))
         .thenThrow(new SecurityException("denied"));

      assertThrows(SecurityException.class, () -> controller.exportScheduledTasks(
         "taskA,__asset file backup__", principal, response));
      verify(response, never()).getOutputStream();
      verify(response, never()).setHeader(anyString(), anyString());
      verify(scheduleService, never()).exportScheduledTasks(anyList(), any());
   }

   // [export allowed] the checked tasks are written to the response
   @Test
   void exportScheduledTasks_allowed_writesCheckedTasks() throws Exception {
      ServletOutputStream output = mock(ServletOutputStream.class);
      when(response.getOutputStream()).thenReturn(output);
      when(scheduleService.getExportTasks(new String[] { "taskA" }, principal))
         .thenReturn(List.of(taskA));

      controller.exportScheduledTasks("taskA", principal, response);

      verify(scheduleService).exportScheduledTasks(List.of(taskA), output);
   }

   /**
    * Stubs the lookup of a task in the caller's org and whether the caller may export it. The
    * lookup without an org is stubbed too, so that an unchecked lookup would find the task.
    */
   private void stubTask(String name, ScheduleTask task, boolean exportable) {
      lenient().when(scheduleManager.getScheduleTask(name)).thenReturn(task);
      lenient().when(scheduleManager.getScheduleTask(name, ORG_A)).thenReturn(task);

      if(task != null) {
         lenient().when(scheduleService.canExportTask(task, principal)).thenReturn(exportable);
      }
   }
}
