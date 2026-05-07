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
 * EMScheduleController has real in-controller logic in three areas:
 *   getScheduledTasksByFolder — builds an AssetEntry from parentInfo before delegating
 *   runTasks                  — catches service exceptions and re-wraps as MessageException
 *   stopTasks                 — same pattern as runTasks
 *
 * getScheduledTasks is a pure-delegation method; tests verify correct parameter forwarding.
 *
 * Coverage scope:
 *   [getScheduledTasks: explicit params]       selectStr+filter forwarded unchanged
 *   [getScheduledTasks: empty optionals]       absent optionals default to empty string
 *   [getScheduledTasksByFolder: null owner]    null owner → GLOBAL_SCOPE AssetEntry
 *   [getScheduledTasksByFolder: null parent]   null parentInfo → null entry passed to service
 *   [runTasks: service throws]                 exception wrapped as MessageException
 *   [stopTasks: delegation]                    calls stopScheduledTask for each task name
 */

import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.MessageException;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class EMScheduleControllerTest {

   @Mock private ScheduleService scheduleService;
   @Mock private ScheduleTaskService taskService;
   @Mock private ScheduleUsersChangeService usersChangeService;
   @Mock private CustomThemesManager customThemesManager;
   @Mock private ScheduleTaskList taskList;
   @Mock private Principal principal;

   private EMScheduleController controller;

   @BeforeEach
   void setUp() {
      controller = new EMScheduleController(
         scheduleService, taskService, usersChangeService, customThemesManager);
   }

   // -------------------------------------------------------------------------
   // getScheduledTasks()
   // -------------------------------------------------------------------------

   // [explicit params] selectStr and filter forwarded to service unchanged
   @Test
   void getScheduledTasks_explicitParams_forwardsToService() throws Exception {
      when(scheduleService.getScheduleTaskList("mySelect", "myFilter", principal))
         .thenReturn(taskList);

      ScheduleTaskList result =
         controller.getScheduledTasks(Optional.of("mySelect"), Optional.of("myFilter"), principal);

      assertSame(taskList, result);
      verify(scheduleService).getScheduleTaskList("mySelect", "myFilter", principal);
   }

   // [empty optionals] absent params default to empty string
   @Test
   void getScheduledTasks_emptyOptionals_passesEmptyStrings() throws Exception {
      when(scheduleService.getScheduleTaskList("", "", principal)).thenReturn(taskList);

      controller.getScheduledTasks(Optional.empty(), Optional.empty(), principal);

      verify(scheduleService).getScheduleTaskList("", "", principal);
   }

   // -------------------------------------------------------------------------
   // getScheduledTasksByFolder()
   // -------------------------------------------------------------------------

   // [null owner] parentInfo with null owner → GLOBAL_SCOPE entry passed to service
   @Test
   void getScheduledTasksByFolder_nullOwner_usesGlobalScopeEntry() throws Exception {
      ContentRepositoryTreeNode parentInfo = ContentRepositoryTreeNode.builder()
         .label("Monthly")
         .path("Monthly")
         .type(0)
         .build();
      when(scheduleService.getScheduleTaskList(eq(""), eq(""), argThat(entry ->
         entry != null &&
         entry.getScope() == AssetRepository.GLOBAL_SCOPE &&
         "Monthly".equals(entry.getPath())), eq(principal)))
         .thenReturn(taskList);

      ScheduleTaskList result = controller.getScheduledTasksByFolder(
         Optional.empty(), Optional.empty(), parentInfo, principal);

      assertSame(taskList, result);
   }

   // [null parentInfo] null parentInfo → null entry passed to service
   @Test
   void getScheduledTasksByFolder_nullParent_passesNullEntry() throws Exception {
      when(scheduleService.getScheduleTaskList("", "", (inetsoft.uql.asset.AssetEntry) null, principal))
         .thenReturn(taskList);

      ScheduleTaskList result = controller.getScheduledTasksByFolder(
         Optional.empty(), Optional.empty(), null, principal);

      assertSame(taskList, result);
      verify(scheduleService).getScheduleTaskList("", "", (inetsoft.uql.asset.AssetEntry) null, principal);
   }

   // -------------------------------------------------------------------------
   // runTasks()
   // -------------------------------------------------------------------------

   // [service throws] exception from service wrapped as MessageException
   @Test
   void runTasks_serviceThrows_wrapsAsMessageException() throws Exception {
      TaskListModel list = TaskListModel.builder().taskNames(List.of("myTask")).build();
      doThrow(new RuntimeException("connection lost"))
         .when(scheduleService).runScheduledTask("myTask", principal);

      assertThrows(MessageException.class,
         () -> controller.runTasks(list, principal));
   }

   // -------------------------------------------------------------------------
   // stopTasks()
   // -------------------------------------------------------------------------

   // [delegation] calls stopScheduledTask for each task in the list
   @Test
   void stopTasks_delegatesToService() throws Exception {
      TaskListModel list = TaskListModel.builder()
         .taskNames(List.of("task1", "task2"))
         .build();

      controller.stopTasks(list, principal);

      verify(scheduleService).stopScheduledTask("task1", principal);
      verify(scheduleService).stopScheduledTask("task2", principal);
   }
}
