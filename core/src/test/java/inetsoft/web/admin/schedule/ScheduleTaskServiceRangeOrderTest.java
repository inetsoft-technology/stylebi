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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.MessageException;
import inetsoft.web.admin.schedule.model.ScheduleConditionModel;
import inetsoft.web.admin.schedule.model.ScheduleTaskEditorModel;
import inetsoft.web.admin.schedule.model.TaskOptionsPaneModel;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeConditionModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79236, saving a task with a time out of range is refused before the rename of the task
 * is committed (saveTask renamed the task, then applied the content), and the model of a stored
 * condition is converted without the range check so an old task can still be compared.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class ScheduleTaskServiceRangeOrderTest {
   private static final String ORG = "orga";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);

   @Mock private AnalyticRepository analyticRepository;
   @Mock private ScheduleManager scheduleManager;
   @Mock private ScheduleService scheduleService;
   @Mock private SecurityProvider securityProvider;
   @Mock private OrganizationManager organizationManager;
   @Mock private XPrincipal principal;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SUtil> sutilStatic;
   private ScheduleTaskService service;

   @BeforeEach
   void setUp() throws Exception {
      OrganizationContextHolder.clear();
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      when(organizationManager.getCurrentOrgID(any())).thenReturn(ORG);
      when(organizationManager.getCurrentOrgID()).thenReturn(ORG);

      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      sutilStatic.when(SUtil::loadLocaleProperties).thenReturn(new Properties());
      sutilStatic.when(() -> SUtil.getOwnerForNewTask(any())).thenAnswer(inv -> inv.getArgument(0));
      sutilStatic.when(() -> SUtil.getIdentity(any(), anyInt())).thenAnswer(inv -> {
         IdentityID id = inv.getArgument(0);
         return id == null ? null : securityProvider.getUser(id);
      });
      when(securityProvider.getUser(ALICE)).thenReturn(new User(ALICE));
      when(principal.getName()).thenReturn(ALICE.convertToKey());
      when(principal.getOrgId()).thenReturn(ORG);

      ScheduleTask stored = new ScheduleTask("Nightly");
      stored.setOwner(ALICE);
      stored.setIdentity(new User(ALICE));
      when(scheduleManager.getScheduleTask(anyString())).thenAnswer(inv -> stored.clone());
      when(scheduleManager.getScheduleTask(isNull())).thenReturn(null);
      when(scheduleService.updateTaskName(any(), any(), any(), any()))
         .thenAnswer(inv -> inv.getArgument(1));

      service = new ScheduleTaskService(analyticRepository, scheduleManager, scheduleService,
                                        new ScheduleConditionService(), securityProvider, null,
                                        null);
   }

   @AfterEach
   void tearDown() {
      sutilStatic.close();
      orgManagerStatic.close();
      OrganizationContextHolder.clear();
   }

   private ScheduleTaskEditorModel model(int hour) {
      return ScheduleTaskEditorModel.builder()
         .taskName("Renamed")
         .oldTaskName("Nightly")
         .addConditions(TimeConditionModel.builder()
            .label("t").type(TimeCondition.EVERY_DAY).hour(hour).minute(0).second(0).interval(2)
            .build())
         .options(TaskOptionsPaneModel.builder()
                     .enabled(true).deleteIfNotScheduledToRun(false).securityEnabled(true)
                     .owner(ALICE.getName()).idName(ALICE.getName())
                     .idType(inetsoft.uql.util.Identity.USER).build())
         .build();
   }

   @Test
   void saveTask_conditionOutOfRange_isRefusedBeforeTheRename() throws Exception {
      Exception refused =
         assertThrows(Exception.class, () -> service.saveTask(model(-30), "", principal, true));
      assertInstanceOf(MessageException.class, refused);

      verify(scheduleService, never()).updateTaskName(any(), any(), any(), any());
      verify(scheduleService, never()).saveTask(any(), any(), any());
   }

   @Test
   void saveTask_conditionInRange_renamesTheTask() throws Exception {
      try {
         service.saveTask(model(5), "", principal, true);
      }
      catch(MessageException e) {
         fail("unexpected refusal: " + e.getMessage());
      }
      catch(Exception ignore) {
         // the rest of the save is not wired
      }

      verify(scheduleService).updateTaskName(any(), any(), any(), any());
   }

   @Test
   void storedConditionModelIsConvertedWithoutTheRangeCheck() throws Exception {
      ScheduleConditionService conditions = new ScheduleConditionService();
      ScheduleConditionModel stored = TimeConditionModel.builder()
         .label("t").type(TimeCondition.EVERY_DAY).hour(-1).minute(0).second(0).interval(2)
         .build();

      assertThrows(MessageException.class, () -> conditions.getConditionFromModel(stored));
      TimeCondition condition = (TimeCondition) conditions.getConditionFromModel(stored, false);
      assertEquals(-1, condition.getHour());
   }
}
