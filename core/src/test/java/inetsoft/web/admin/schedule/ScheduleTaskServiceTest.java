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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.MessageException;
import inetsoft.web.admin.schedule.model.ScheduleTaskList;
import inetsoft.web.admin.schedule.model.TaskOptionsPaneModel;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.security.Principal;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class ScheduleTaskServiceTest {

   @Mock
   private AnalyticRepository analyticRepository;
   @Mock
   private ScheduleManager scheduleManager;
   @Mock
   private ScheduleService scheduleService;
   @Mock
   private Principal principal;

   private ScheduleTaskService service;

   @BeforeEach
   void setUp() {
      service = new ScheduleTaskService(
         analyticRepository, scheduleManager, scheduleService, null, null, null, null);
   }

   // ── redistributeTasks ────────────────────────────────────────────────────

   @Test
   void redistributeTasks_noTimeConditions_throwsMessageException() throws Exception {
      ScheduleTask task = new ScheduleTask("task1");
      when(scheduleManager.getScheduleTask("task1")).thenReturn(task);

      MessageException ex = assertThrows(MessageException.class, () ->
         service.redistributeTasks(
            LocalTime.of(0, 0), LocalTime.of(23, 0), 4, List.of("task1"), principal));

      assertFalse(ex.getMessage().isEmpty(),
         "the localized message must not be empty -- ensures the em.schedule." +
         "distribution.noTimeConditions catalog key resolved rather than falling back");
      verify(scheduleService, never()).saveTask(any(), any(), any());
   }

   @Test
   void redistributeTasks_emptySelection_throwsMessageException() {
      assertThrows(MessageException.class, () ->
         service.redistributeTasks(
            LocalTime.of(0, 0), LocalTime.of(23, 0), 4, List.of(), principal));
   }

   @Test
   void redistributeTasks_hasTimeCondition_redistributesWithoutError() throws Exception {
      ScheduleTask task = new ScheduleTask("task1");
      TimeCondition tc = new TimeCondition();
      tc.setType(TimeCondition.EVERY_DAY);
      tc.setHour(1);
      tc.setMinute(0);
      task.addCondition(tc);

      when(scheduleManager.getScheduleTask("task1")).thenReturn(task);
      when(scheduleService.getScheduleTaskList("", "", principal))
         .thenReturn(mock(ScheduleTaskList.class));

      ScheduleTaskList result = service.redistributeTasks(
         LocalTime.of(0, 0), LocalTime.of(23, 0), 4, List.of("task1"), principal);

      assertNotNull(result);
      verify(scheduleService).saveTask("task1", task, principal);
   }

   // ── setTaskOptions (Bug #77120) ──────────────────────────────────────────

   @Test
   void setTaskOptions_unchangedUnresolvableIdentity_isKept() {
      IdentityID id = new IdentityID("g1", "host-org");
      Group placeholder = new Group(id);
      ScheduleTask task = new ScheduleTask("task1");
      task.setIdentity(placeholder);

      Identity result = applyOptions(task, "g1", Identity.GROUP);

      assertSame(placeholder, result,
                 "an edit that leaves execute-as unchanged must not clear an unresolved identity");
   }

   @Test
   void setTaskOptions_changedUnresolvableIdentity_isNotKept() {
      ScheduleTask task = new ScheduleTask("task1");
      task.setIdentity(new Group(new IdentityID("g1", "host-org")));

      assertNull(applyOptions(task, "g2", Identity.GROUP));
      task.setIdentity(new Group(new IdentityID("g1", "host-org")));
      assertNull(applyOptions(task, "g1", Identity.ROLE));
   }

   private Identity applyOptions(ScheduleTask task, String idName, int idType) {
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID(principal)).thenReturn("host-org");
      TaskOptionsPaneModel model = TaskOptionsPaneModel.builder()
         .enabled(true)
         .deleteIfNotScheduledToRun(false)
         .securityEnabled(true)
         .owner("admin")
         .idName(idName)
         .idType(idType)
         .build();

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<OrganizationManager> orgs = mockStatic(OrganizationManager.class))
      {
         orgs.when(OrganizationManager::getInstance).thenReturn(orgManager);
         sutil.when(() -> SUtil.getIdentity(any(), anyInt())).thenReturn(null);
         sutil.when(SUtil::loadLocaleProperties).thenReturn(new Properties());

         service.setTaskOptions(model, task, principal);
      }

      return task.getIdentity();
   }
}
