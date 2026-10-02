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
import inetsoft.sree.RepletEngine;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityException;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77060: exporting schedule tasks must only include tasks that resolve in the caller's
 * organization and that the caller is allowed to see, and must reject the whole export before
 * anything is written when any of the tasks fails that check.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class ScheduleServiceExportTest {
   private static final String ORG_A = "orgA";
   private static final String INTERNAL_TASK = "__asset file backup__";
   private static final String OTHER_USER_TASK = "bob~;~orgA:Payroll";
   private static final String OWN_TASK = "alice~;~orgA:Daily";
   private static final String UNKNOWN_TASK = "alice~;~orgA:Missing";

   @Mock
   private AnalyticRepository analyticRepository;
   @Mock
   private RepletEngine repletEngine;
   @Mock
   private ScheduleManager scheduleManager;
   @Mock
   private Principal principal;
   @Mock
   private OrganizationManager organizationManager;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<TimeRange> timeRangeStatic;
   private ScheduleService service;

   @BeforeEach
   void setUp() {
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      lenient().when(organizationManager.getCurrentOrgID(any(Principal.class))).thenReturn(ORG_A);
      timeRangeStatic = mockStatic(TimeRange.class);
      timeRangeStatic.when(TimeRange::getTimeRanges).thenReturn(new ArrayList<>());
      lenient().when(analyticRepository.isWrapperFor(RepletEngine.class)).thenReturn(true);
      lenient().when(analyticRepository.unwrap(RepletEngine.class)).thenReturn(repletEngine);

      service = new ScheduleService(analyticRepository, scheduleManager, null, null, null, null,
                                    null, null, null, null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      timeRangeStatic.close();
      orgManagerStatic.close();
   }

   // an org admin is not allowed to see the host-org internal tasks, which the schedule manager
   // resolves regardless of the caller's org
   @Test
   void getExportTasks_internalTaskNotVisible_throwsSecurityException() {
      ScheduleTask task = task(INTERNAL_TASK, false);

      assertThrows(SecurityException.class,
                   () -> service.getExportTasks(new String[] { INTERNAL_TASK }, principal));
      verify(repletEngine).hasTaskPermission(task, principal);
   }

   @Test
   void getExportTasks_otherUsersTaskNotVisible_throwsSecurityException() {
      task(OTHER_USER_TASK, false);

      assertThrows(SecurityException.class,
                   () -> service.getExportTasks(new String[] { OTHER_USER_TASK }, principal));
   }

   @Test
   void getExportTasks_unknownTask_throwsTaskNotFound() {
      task(OWN_TASK, true);
      when(scheduleManager.getScheduleTask(UNKNOWN_TASK, ORG_A)).thenReturn(null);

      MessageException ex = assertThrows(
         MessageException.class,
         () -> service.getExportTasks(new String[] { OWN_TASK, UNKNOWN_TASK }, principal));
      assertTrue(ex.getMessage().contains("Missing"), ex.getMessage());
   }

   // the lookup must use the caller's org, not the thread's current org
   @Test
   void getExportTasks_visibleTask_resolvedInCallersOrg() throws Exception {
      ScheduleTask task = task(OWN_TASK, true);

      List<ScheduleTask> tasks = service.getExportTasks(new String[] { OWN_TASK }, principal);

      assertEquals(List.of(task), tasks);
      verify(scheduleManager).getScheduleTask(OWN_TASK, ORG_A);
   }

   @Test
   void exportScheduledTasks_visibleTask_writesTaskXml() throws Exception {
      ScheduleTask task = task(OWN_TASK, true);
      doAnswer(inv -> {
         inv.<PrintWriter>getArgument(0).write("<Task name=\"Daily\"/>");
         return null;
      }).when(task).writeXML(any(PrintWriter.class));
      ByteArrayOutputStream output = new ByteArrayOutputStream();

      service.exportScheduledTasks(
         service.getExportTasks(new String[] { OWN_TASK }, principal), output);

      String xml = output.toString(StandardCharsets.UTF_8);
      assertTrue(xml.contains("<schedule><Task name=\"Daily\"/><timeRanges>"), xml);
   }

   private ScheduleTask task(String name, boolean visible) {
      ScheduleTask task = mock(ScheduleTask.class);
      // an unchecked lookup would find the task too
      lenient().when(scheduleManager.getScheduleTask(name)).thenReturn(task);
      lenient().when(scheduleManager.getScheduleTask(name, ORG_A)).thenReturn(task);
      lenient().when(repletEngine.hasTaskPermission(task, principal)).thenReturn(visible);
      return task;
   }
}
