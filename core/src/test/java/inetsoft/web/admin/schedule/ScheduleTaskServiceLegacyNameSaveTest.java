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
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.web.admin.schedule.model.ScheduleTaskEditorModel;
import inetsoft.web.admin.schedule.model.TaskOptionsPaneModel;
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
 * Bug #77356: the enable toggle and the task editor save must save the task under the id of
 * the task resolved by {@link ScheduleManager#getScheduleTask(String)}, not under the raw client
 * name. The legacy fallback resolves "bob:Nightly" to the task stored as "Nightly"; saving under
 * the raw name would write a second (ghost) task "bob:Nightly".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class ScheduleTaskServiceLegacyNameSaveTest {
   private static final String ORG = "orga";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final String RAW_NAME = "bob:Nightly";
   private static final String RESOLVED_ID = ALICE.convertToKey() + ":Nightly";

   @Mock
   private AnalyticRepository analyticRepository;
   @Mock
   private ScheduleManager scheduleManager;
   @Mock
   private ScheduleService scheduleService;
   @Mock
   private SecurityProvider securityProvider;
   @Mock
   private OrganizationManager organizationManager;
   @Mock
   private XPrincipal principal;

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

      // the task stored as "Nightly", its own id is "alice~;~orga:Nightly"; the legacy
      // fallback resolves the raw name to it
      ScheduleTask resolved = new ScheduleTask("Nightly");
      resolved.setOwner(ALICE);
      resolved.setIdentity(new User(ALICE));
      assertEquals(RESOLVED_ID, resolved.getTaskId());
      when(scheduleManager.getScheduleTask(anyString())).thenAnswer(inv -> resolved.clone());
      when(scheduleManager.getScheduleTask(isNull())).thenReturn(null);
      // not renamed: updateTaskName() returns the (decoded) old name
      when(scheduleService.updateTaskName(any(), any(), any(), any()))
         .thenAnswer(inv -> inv.getArgument(0));

      service = new ScheduleTaskService(analyticRepository, scheduleManager, scheduleService,
                                        null, securityProvider, null, null);
   }

   @AfterEach
   void tearDown() {
      sutilStatic.close();
      orgManagerStatic.close();
      OrganizationContextHolder.clear();
   }

   @Test
   void setTaskEnabled_rawLegacyName_savesUnderResolvedId() throws Exception {
      service.setTaskEnabled(RAW_NAME, false, principal);

      verify(scheduleService).saveTask(eq(RESOLVED_ID), argThat(t -> !t.isEnabled()), eq(principal));
      verify(scheduleService, never()).saveTask(eq(RAW_NAME), any(), any());
   }

   @Test
   void saveTask_rawLegacyNameNotRenamed_savesUnderResolvedId() throws Exception {
      ScheduleTaskEditorModel model = ScheduleTaskEditorModel.builder()
         .taskName(RAW_NAME)
         .oldTaskName(RAW_NAME)
         .options(TaskOptionsPaneModel.builder()
                     .enabled(true)
                     .deleteIfNotScheduledToRun(false)
                     .securityEnabled(true)
                     .owner(ALICE.getName())
                     .idName(ALICE.getName())
                     .idType(Identity.USER)
                     .build())
         .build();

      try {
         service.saveTask(model, "", principal, true);
      }
      catch(SecurityException e) {
         fail("unexpected security rejection: " + e.getMessage());
      }
      catch(Exception ignore) {
         // the dialog model built after the save is not wired
      }

      verify(scheduleService).saveTask(eq(RESOLVED_ID), any(), eq(principal));
      verify(scheduleService, never()).saveTask(eq(RAW_NAME), any(), any());
   }
}
