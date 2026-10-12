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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.AdminExceptionHandler;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bug #77813, a POST to /api/em/schedule/move-folder with a task the caller may not move must
 * answer with the MessageException error body the EM shows (AdminExceptionHandler → GenericError),
 * not a 200, and must move nothing in the request. An all-allowed request still moves everything.
 *
 * Real Spring MVC dispatch and Jackson binding (standalone MockMvc), the real AdminExceptionHandler,
 * the real ScheduleTaskFolderService.getMovableTask and real ScheduleTask objects; only the move
 * itself is stubbed.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class EMScheduleTaskFolderControllerMoveRefusalHttpTest {
   @Mock private ScheduleService scheduleService;
   @Mock private ScheduleTaskService scheduleTaskService;
   @Mock private SecurityEngine securityEngine;
   @Mock private ScheduleManager scheduleManager;
   @Mock private LogManager logManager;

   private static final IdentityID ALICE = new IdentityID("alice", "orgx");
   private static final IdentityID BOB = new IdentityID("bob", "orgx");

   private final Principal principal = () -> ALICE.convertToKey();
   private final List<ScheduleTaskModel[]> movedTasks = new ArrayList<>();
   private final List<String[]> movedFolders = new ArrayList<>();
   private MockMvc mockMvc;
   private ScheduleTask taskA;
   private ScheduleTask taskB;
   private ScheduleTask taskC;

   @BeforeEach
   void setUp() throws Exception {
      ScheduleTaskFolderService folderService = spy(new ScheduleTaskFolderService(
         scheduleManager, securityEngine, null, null, null));
      lenient().doAnswer(inv -> {
         movedTasks.add(inv.getArgument(0));
         movedFolders.add(inv.getArgument(1));
         return null;
      }).when(folderService).moveScheduleItems(any(), any(), any(), any());

      EMScheduleTaskFolderController controller = new EMScheduleTaskFolderController(
         folderService, scheduleService, scheduleTaskService, securityEngine, scheduleManager);
      mockMvc = MockMvcBuilders.standaloneSetup(controller)
         .setControllerAdvice(new AdminExceptionHandler(logManager))
         .build();

      taskA = storedTask("A", ALICE);
      taskB = storedTask("B", BOB);
      taskC = storedTask("C", ALICE);

      lenient().when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                                    any(ResourceAction.class))).thenReturn(false);
      lenient().when(scheduleTaskService.canDeleteTask(taskA, principal)).thenReturn(true);
      lenient().when(scheduleTaskService.canDeleteTask(taskC, principal)).thenReturn(true);
   }

   @Test
   void moveFolder_deniedMiddleTaskWithFolder_answersMessageExceptionAndMovesNothing()
      throws Exception
   {
      when(scheduleTaskService.canDeleteTask(taskB, principal)).thenReturn(false);

      mockMvc.perform(post("/api/em/schedule/move-folder")
                         .principal(principal)
                         .contentType(MediaType.APPLICATION_JSON)
                         .accept(MediaType.APPLICATION_JSON)
                         .content(body(new String[] { "F1" }, model("A", ALICE), model("B", BOB),
                                       model("C", ALICE))))
         .andExpect(status().isInternalServerError())
         .andExpect(jsonPath("$.type").value("MessageException"))
         .andExpect(jsonPath("$.message").value("Write access denied: B"));

      assertTrue(movedTasks.isEmpty(), "a refused request must move no task and no folder");
      verify(scheduleTaskService, never()).canDeleteTask(taskC, principal);
   }

   @Test
   void moveFolder_allTasksAllowedWithFolder_movesEverything() throws Exception {
      when(scheduleTaskService.canDeleteTask(taskB, principal)).thenReturn(true);

      mockMvc.perform(post("/api/em/schedule/move-folder")
                         .principal(principal)
                         .contentType(MediaType.APPLICATION_JSON)
                         .accept(MediaType.APPLICATION_JSON)
                         .content(body(new String[] { "F1" }, model("A", ALICE), model("B", BOB),
                                       model("C", ALICE))))
         .andExpect(status().isOk());

      assertEquals(1, movedTasks.size());
      assertEquals(3, movedTasks.get(0).length);
      assertArrayEquals(new String[] { "F1" }, movedFolders.get(0));
   }

   private ScheduleTask storedTask(String name, IdentityID owner) {
      ScheduleTask task = new ScheduleTask(name, ScheduleTask.Type.NORMAL_TASK);
      task.setOwner(owner);
      task.setRemovable(true);
      lenient().when(scheduleManager.getScheduleTask(task.getTaskId())).thenReturn(task);
      return task;
   }

   private static ScheduleTaskModel model(String name, IdentityID owner) {
      return ScheduleTaskModel.builder()
         .name(name).owner(owner).ownerAlias(owner.name).path("/").label(name).description("")
         .editable(true).removable(true).enabled(true).schedule("").build();
   }

   private static String body(String[] folders, ScheduleTaskModel... tasks) throws Exception {
      MoveTaskFolderRequest request = new MoveTaskFolderRequest();
      request.setTarget(
         ContentRepositoryTreeNode.builder().label("Target").path("Target").type(0).build());
      request.setTasks(tasks);
      request.setFolders(folders);
      return new ObjectMapper().writeValueAsString(request);
   }
}
