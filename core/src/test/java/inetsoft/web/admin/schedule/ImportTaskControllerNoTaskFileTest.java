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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.admin.schedule.model.ImportTaskResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77360, POST /api/em/content/schedule/import/{overwriting} without a prior set-task-file
 * (or a second import after the session attribute was already consumed) must not throw an NPE
 * (HTTP 500). It returns a failed response listing the selected tasks instead.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportTaskControllerNoTaskFileTest {
   @Mock private ScheduleManager scheduleManager;
   @Mock private ScheduleTaskFolderService scheduleTaskFolderService;
   @Mock private AnalyticRepository analyticRepository;
   @Mock private SecurityEngine securityEngine;
   @Mock private HttpServletRequest request;
   @Mock private HttpSession session;
   @Mock private Principal principal;

   private ImportTaskController controller;

   @BeforeEach
   void setUp() {
      controller = new ImportTaskController(
         scheduleManager, scheduleTaskFolderService, analyticRepository, securityEngine);
      when(request.getSession(true)).thenReturn(session);
   }

   @Test
   void importScheduleTask_noTaskFileInSession_returnsFailedResponse() throws Exception {
      when(session.getAttribute(anyString())).thenReturn(null); // empty session

      ImportTaskResponse result = assertDoesNotThrow(() -> controller.importScheduleTask(
         List.of("myTask"), request, false, "http://host", principal));

      assertNotNull(result);
      assertTrue(result.failed());
      assertEquals(List.of("myTask"), result.failedTasks());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }

   @Test
   void importScheduleTask_calledTwice_secondCallReturnsFailedResponse() throws Exception {
      Map<String, Object> attrs = new HashMap<>();
      attrs.put(ImportTaskController.INFO_ATTR, new ArrayList<>());
      when(session.getAttribute(anyString())).thenAnswer(inv -> attrs.get(inv.<String>getArgument(0)));
      doAnswer(inv -> attrs.remove(inv.<String>getArgument(0)))
         .when(session).removeAttribute(anyString());

      ImportTaskResponse first = controller.importScheduleTask(
         List.of("myTask"), request, true, "http://host", principal);
      assertFalse(first.failed());

      ImportTaskResponse second = assertDoesNotThrow(() -> controller.importScheduleTask(
         List.of("myTask"), request, true, "http://host", principal));

      assertTrue(second.failed());
      assertEquals(List.of("myTask"), second.failedTasks());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }
}
