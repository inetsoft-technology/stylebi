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
 * ScheduleCycleController has real in-controller logic in two areas:
 *   subscribeToDataCycleNames — permission guard via securityEngine.getSecurityProvider().checkPermission()
 *   editDataCycle             — two-step: editCycle() then re-fetches model via getDialogModel()
 *
 * Coverage scope:
 *   [subscribeToDataCycleNames: no permission]  permission denied → SecurityException
 *   [editDataCycle: two-step delegation]        editCycle called, then model re-fetched
 */

import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.admin.schedule.model.ScheduleCycleDialogModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleCycleControllerTest {

   @Mock private ScheduleCycleService scheduleCycleService;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private ScheduleCycleDialogModel dialogModel;
   @Mock private Principal principal;

   private ScheduleCycleController controller;

   @BeforeEach
   void setUp() {
      controller = new ScheduleCycleController(
         scheduleCycleService, monitoringDataService, securityEngine);

      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
   }

   // -------------------------------------------------------------------------
   // subscribeToDataCycleNames()
   // -------------------------------------------------------------------------

   // [no permission] checkPermission returns false → SecurityException thrown
   @Test
   void subscribeToDataCycleNames_noPermission_throwsSecurityException() {
      when(securityProvider.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/schedule/cycles", ResourceAction.ACCESS))
         .thenReturn(false);

      assertThrows(SecurityException.class,
         () -> controller.subscribeToDataCycleNames((StompHeaderAccessor) null, principal));

      verify(monitoringDataService, never()).addSubscriber(any(), any());
   }

   // -------------------------------------------------------------------------
   // editDataCycle()
   // -------------------------------------------------------------------------

   // [two-step delegation] editCycle called first, then model re-fetched via getDialogModel()
   @Test
   void editDataCycle_editsThenRefetchesModel() throws Exception {
      ScheduleCycleDialogModel model = mock(ScheduleCycleDialogModel.class);
      when(model.label()).thenReturn("MyCycle");
      when(scheduleCycleService.getDialogModel("MyCycle", principal)).thenReturn(dialogModel);

      ScheduleCycleDialogModel result = controller.editDataCycle(model, principal);

      verify(scheduleCycleService).editCycle(model, principal);
      verify(scheduleCycleService).getDialogModel("MyCycle", principal);
      assertSame(dialogModel, result);
   }
}
