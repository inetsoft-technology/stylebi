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

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.*;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.schedule.model.ScheduleConditionModel;
import inetsoft.web.admin.schedule.model.ScheduleCycleDialogModel;
import inetsoft.web.admin.schedule.model.TaskConditionPaneModel;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeConditionModel;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79236, editing a data cycle whose model has a time out of range is refused before anything
 * is committed: editCycle used to remove the stored cycle (and move its permission on a rename)
 * and then convert the conditions, so the refusal came after the cycle was deleted.
 */
@Tag("core")
class ScheduleCycleServiceEditRangeTest {
   private static final String ORG = "host-org";
   private DataCycleManager dataCycleManager;
   private SecurityEngine securityEngine;
   private ScheduleCycleService service;
   private Principal principal;
   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<OrganizationManager> orgStatic;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach
   void setUp() throws Exception {
      dataCycleManager = mock(DataCycleManager.class);
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      when(dataCycleManager.getDataCycles(ORG)).thenAnswer(inv -> java.util.Collections.emptyEnumeration());
      service = new ScheduleCycleService(dataCycleManager, new ScheduleConditionService(),
                                         mock(SchedulerMonitoringService.class),
                                         mock(ResourcePermissionService.class), securityEngine);
      principal = mock(Principal.class);
      when(principal.getName()).thenReturn(new IdentityID("admin", ORG).convertToKey());

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID(any(Principal.class))).thenReturn(ORG);
      orgStatic = mockStatic(OrganizationManager.class);
      orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), any(), any(), any()))
         .thenAnswer(inv -> new ActionRecord());
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
      sutilStatic.close();
      orgStatic.close();
   }

   private ScheduleCycleDialogModel model(String oldName, String newName, int hour) {
      TimeConditionModel condition = TimeConditionModel.builder()
         .label("t").type(TimeCondition.EVERY_DAY).hour(hour).minute(0).second(0).interval(2)
         .build();
      TaskConditionPaneModel pane = mock(TaskConditionPaneModel.class);
      when(pane.conditions()).thenReturn(List.<ScheduleConditionModel>of(condition));
      DataCycleManager.CycleInfo info = mock(DataCycleManager.CycleInfo.class);
      when(info.getName()).thenReturn(newName);
      ScheduleCycleDialogModel model = mock(ScheduleCycleDialogModel.class);
      when(model.name()).thenReturn(oldName);
      when(model.label()).thenReturn(newName);
      when(model.conditionPaneModel()).thenReturn(pane);
      when(model.cycleInfo()).thenReturn(info);
      return model;
   }

   @Test
   void editCycle_conditionOutOfRange_isRefusedAndTheStoredCycleIsUntouched() throws Exception {
      for(String newName : new String[] { "cycle", "renamed" }) {
         ScheduleCycleDialogModel bad = model("cycle", newName, -1);
         Exception refused = assertThrows(Exception.class, () -> service.editCycle(bad, principal));
         assertInstanceOf(MessageException.class, refused);
      }

      verify(dataCycleManager, never()).removeDataCycle(anyString(), any());
      verify(dataCycleManager, never()).setConditions(anyString(), any(), ArgumentMatchers.<List<inetsoft.sree.schedule.ScheduleCondition>>any());
      verify(dataCycleManager, never()).setCycleInfo(anyString(), any(), any());
      // a rename moved the permission of the cycle before the conditions were converted
      verify(securityEngine, never()).setPermission(any(ResourceType.class), anyString(), any());
   }

   @Test
   void editCycle_conditionInRange_replacesTheCycle() throws Exception {
      service.editCycle(model("cycle", "cycle", 5), principal);

      InOrder order = inOrder(dataCycleManager);
      order.verify(dataCycleManager).removeDataCycle("cycle", ORG);
      order.verify(dataCycleManager).setConditions(eq("cycle"), eq(ORG), ArgumentMatchers.<List<inetsoft.sree.schedule.ScheduleCondition>>any());
      order.verify(dataCycleManager).save();
   }
}
